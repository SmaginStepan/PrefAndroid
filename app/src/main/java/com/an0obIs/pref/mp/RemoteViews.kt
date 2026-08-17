package com.an0obIs.pref.mp

import com.an0obIs.pref.model.Calculation
import com.an0obIs.pref.model.Game
import com.an0obIs.pref.model.GamePhase
import com.an0obIs.pref.model.GameType
import com.an0obIs.pref.ui.game.PlacedCard
import com.an0obIs.pref.ui.game.TableInfo
import com.an0obIs.pref.ui.game.TableLayout

/**
 * Builds per-viewer snapshots of a hosted game. Seats are ROTATED so every
 * viewer sees themselves as seat 0 (bottom of the table), and REDACTED so a
 * hand is face-up only for its owner or when the play has opened it.
 */
object RemoteViews {

    fun rot(seat: Int, viewer: Int): Int = (seat - viewer + 3) % 3

    /** Port of TableLayout.computeField from one viewer's perspective.
     *  [spectator]: no hand is "own" (the sitting 4p dealer watches). */
    fun buildFieldFor(game: Game, viewer: Int, spectator: Boolean = false): List<PlacedCard> {
        val res = mutableListOf<PlacedCard>()
        val deal = game.deal

        for (hand in 0 until 3) {
            val faceUp = (!spectator && hand == viewer) || deal.hands[hand].isVisible
            res.addAll(
                TableLayout.handPlacements(
                    deal.hands[hand].cards,
                    rot(hand, viewer),
                    special = false,
                    hidden = !faceUp
                )
            )
        }

        for ((key, card) in deal.inPlay) {
            val relKey = if (key < 0) key else rot(key, viewer)
            val c = TableLayout.inPlayCoords(relKey)
            res.add(PlacedCard(card = card, hand = relKey, x = c.first, y = c.second, isInPlay = true))
        }

        if (deal.prikup.isVisible) {
            var k = 0
            for (card in deal.prikup.cards) {
                val x = (TableLayout.W / 2) - TableLayout.S1 / 1.36 + k * TableLayout.S1 / 1.36
                val y = (TableLayout.H / 2) - (TableLayout.S1 / 2)
                res.add(PlacedCard(card = card, hand = 3, x = x, y = y, isPrikup = true))
                k++
            }
        }
        return res
    }

    private fun rotResult(r: Calculation.GameResult, viewer: Int): Calculation.GameResult =
        Calculation.GameResult().also { out ->
            out.gameType = r.gameType
            out.dealer = rot(r.dealer, viewer)
            out.contractor = rot(r.contractor, viewer)
            out.contract = r.contract
            out.taken = r.taken.entries.associate { (k, v) -> rot(k, viewer) to v }.toMutableMap()
            out.visters = r.visters.map { rot(it, viewer) }.toMutableList()
            out.multiplier = r.multiplier
            out.halfWithDealer = r.halfWithDealer
        }

    /** Port of GameViewModel.buildTableInfo from one viewer's perspective. */
    fun buildTableInfoFor(
        game: Game,
        viewer: Int,
        watching: Boolean = false,
        sitOutName: String? = null,
        waitingFor: List<String> = emptyList(),
        youConfirmed: Boolean = false
    ): TableInfo {
        fun <T> rotList(src: List<T>): List<T> = List(3) { rel -> src[(rel + viewer) % 3] }
        return TableInfo(
            phase = game.phase,
            names = rotList(game.calc.scores.map { it.name }),
            dealer = rot(game.calc.dealer, viewer),
            taken = rotList(game.deal.hands.map { it.taken }),
            currentGameType = game.currentGameType,
            contractor = rot(game.contractor, viewer),
            isVister = game.isVister.entries.associate { (k, v) -> rot(k, viewer) to v },
            curentBids = game.curentBids.entries.associate { (k, v) -> rot(k, viewer) to v },
            maxBid = game.maxBid,
            playerToTake = rot(game.playerToTake, viewer),
            playerInTurn = rot(game.playerInTurn, viewer),
            controller = rot(game.turnController(), viewer),
            watching = watching,
            sitOutName = sitOutName,
            waitingFor = waitingFor,
            youConfirmed = youConfirmed,
            gameResult = if (game.phase == GamePhase.EndPlay) rotResult(game.getGameResult(), viewer) else null,
            showPrikupBtn1 = false,
            showPrikupBtn2 = false,
            showTricksBtn = game.phase == GamePhase.Playing || game.phase == GamePhase.EndTurn
        )
    }

    /** Score standing rotated for one viewer, with the full whist matrix.
     *  Works for 3- and 4-column pulkas; [viewer] indexes the calc's players. */
    fun buildScoresFrom(calc: Calculation, viewer: Int): ScoreSnap {
        val n = calc.playersCount
        fun idx(rel: Int) = (rel + viewer) % n
        val sc = calc.scores
        return ScoreSnap(
            names = List(n) { sc[idx(it)].name },
            pulya = List(n) { sc[idx(it)].pulya },
            gora = List(n) { sc[idx(it)].gora },
            visty = List(n) { i ->
                List(n) { j -> if (i == j) 0 else (sc[idx(i)].visty[idx(j)] ?: 0) }
            },
            limit = calc.limit,
            // at ScoreView calc.dealer already points at the next deal's dealer
            dealer = (calc.dealer - viewer + n) % n
        )
    }

    fun buildScoresFor(game: Game, viewer: Int): ScoreSnap = buildScoresFrom(game.calc, viewer)

    /** The deal's completed tricks from one viewer's perspective. */
    fun buildTakesFor(game: Game, viewer: Int): List<TakeSnap>? {
        val ai = game.aIs[viewer] ?: return null
        if (ai.outOfPlay.isEmpty()) return null
        return ai.outOfPlay.map { t ->
            TakeSnap(t.firstMovePerformer, t.takenBy, t.myMove, t.prevMove, t.nextMove, t.prikupMove)
        }
    }

    /** Port of computeField's layout-and-discard mode for one viewer: the
     *  contractor's open hand together with the talon cards this viewer
     *  cannot rule out. Null when the view does not apply. */
    fun buildLayoutFor(game: Game, viewer: Int): List<PlacedCard>? {
        if (game.phase != GamePhase.Playing && game.phase != GamePhase.EndTurn) return null
        if (game.currentGameType != GameType.Normal && game.currentGameType != GameType.Miser) return null
        val contractor = game.contractor
        if (contractor == viewer || !game.opening) return null
        val ai = game.aIs[viewer] ?: return null
        val colorNotExists =
            if (contractor == (viewer + 1) % 3) ai.prevHand.colorNotExists
            else ai.nextHand.colorNotExists
        val res = mutableListOf<PlacedCard>()
        val deal = game.deal
        for (hand in 0 until 3) {
            if (hand == contractor) {
                val list = deal.hands[hand].cards.toMutableList()
                for (card in deal.prikup.cards)
                    if (!colorNotExists.contains(card.coatColor)) list.add(card)
                res.addAll(TableLayout.handPlacements(list, rot(hand, viewer), special = true, hidden = false))
            } else {
                val faceUp = hand == viewer || deal.hands[hand].isVisible
                res.addAll(
                    TableLayout.handPlacements(
                        deal.hands[hand].cards, rot(hand, viewer), special = false, hidden = !faceUp
                    )
                )
            }
        }
        for ((key, card) in deal.inPlay) {
            val relKey = if (key < 0) key else rot(key, viewer)
            val c = TableLayout.inPlayCoords(relKey)
            res.add(PlacedCard(card = card, hand = relKey, x = c.first, y = c.second, isInPlay = true))
        }
        return res
    }

    /** What the current actor must answer, by phase. */
    fun buildAsk(game: Game): Ask = when (game.phase) {
        GamePhase.Negotiations -> Ask("bid", bids = game.getAllowedBids())
        GamePhase.GameChoose -> Ask("contract", bids = game.getAllowedBids())
        GamePhase.VistNegotiations -> Ask("vist")
        GamePhase.OpeningChoose -> Ask("opening")
        GamePhase.Discarding -> Ask("discard")
        GamePhase.Playing -> Ask("play", allowed = game.getAllowedMoves())
        else -> Ask("confirm") // PrikupOpened, EndTurn, EndPlay, ScoreView
    }
}
