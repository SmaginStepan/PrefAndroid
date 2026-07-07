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

    /** Port of TableLayout.computeField from one viewer's perspective. */
    fun buildFieldFor(game: Game, viewer: Int): List<PlacedCard> {
        val res = mutableListOf<PlacedCard>()
        val deal = game.deal

        for (hand in 0 until 3) {
            val faceUp = hand == viewer || deal.hands[hand].isVisible
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
    fun buildTableInfoFor(game: Game, viewer: Int): TableInfo {
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
            gameResult = if (game.phase == GamePhase.EndPlay) rotResult(game.getGameResult(), viewer) else null,
            showPrikupBtn1 = false,
            showPrikupBtn2 = false,
            showTricksBtn = game.phase == GamePhase.Playing || game.phase == GamePhase.EndTurn
        )
    }

    /** Score standing rotated for one viewer, with the full whist matrix. */
    fun buildScoresFor(game: Game, viewer: Int): ScoreSnap {
        fun idx(rel: Int) = (rel + viewer) % 3
        val sc = game.calc.scores
        return ScoreSnap(
            names = List(3) { sc[idx(it)].name },
            pulya = List(3) { sc[idx(it)].pulya },
            gora = List(3) { sc[idx(it)].gora },
            visty = List(3) { i ->
                List(3) { j -> if (i == j) 0 else (sc[idx(i)].visty[idx(j)] ?: 0) }
            },
            limit = game.calc.limit,
            // at ScoreView calc.dealer already points at the next deal's dealer
            dealer = rot(game.calc.dealer, viewer)
        )
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
