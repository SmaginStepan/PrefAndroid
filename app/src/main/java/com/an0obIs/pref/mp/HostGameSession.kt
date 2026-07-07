package com.an0obIs.pref.mp

import com.an0obIs.pref.ai.AI
import com.an0obIs.pref.model.Calculation
import com.an0obIs.pref.model.Game
import com.an0obIs.pref.model.GamePhase
import com.an0obIs.pref.model.GameType

enum class SeatKind { LOCAL, BOT, REMOTE }

/**
 * Runs a hosted multiplayer game on top of the untouched engine.
 *
 * The engine's isAI() is disabled via game.externalDriver, so game.next()
 * stops at EVERY input point; this class dispatches each stop to the seat's
 * driver: the local UI, the built-in AI, or a remote player over the relay.
 *
 * 3 seats: [game] runs the whole match on [matchCalc] directly.
 * 4 seats: the dealer sits out; every deal is a fresh single-deal 3-player
 * game among the other three, and this session maps its seats onto the real
 * ones and writes each result into the authoritative 4-column [matchCalc].
 *
 * Single-threaded by design: the caller must serialize calls (the app runs
 * it on one dispatcher; the unit test calls it directly).
 */
class HostGameSession(
    private val seats: List<SeatKind>,
    private val names: List<String>,
    /** Authoritative pulka for the match; 3 or 4 columns matching [seats]. */
    val matchCalc: Calculation,
    /** Deliver a state message to a REMOTE seat (absolute/real seat number). */
    private val sendToSeat: (seat: Int, msg: GameMsg.State) -> Unit,
    /** The LOCAL seat's UI should refresh (its turn, or the table changed). */
    private val onLocalTurn: () -> Unit
) {

    private val four = seats.size == 4

    var game: Game = Game.create()
        private set

    /** game seat -> real seat for the current deal. */
    private var dealMap: List<Int> = listOf(0, 1, 2)

    /** Real seat sitting out the current deal (-1 in 3-player games). */
    var sittingOut = -1
        private set
    val sitOutName: String?
        get() = if (sittingOut >= 0) names[sittingOut] else null

    /** False while the host itself sits out (deals) in a 4-player match. */
    val hostActive: Boolean
        get() = sittingOut != 0

    var matchEnded = false
        private set

    private var pendingResult: Calculation.GameResult? = null
    private var scoreWritten = false
    private var dealerConfirmed = true

    // Animations produced by other seats' moves, replayed by the host UI.
    // Guests still get plain state snapshots.
    private val pendingAnims = ArrayDeque<Game.Animation>()

    /** Take (and clear) the queued animations. Call under the session lock. */
    fun drainAnims(): List<Game.Animation> {
        val res = pendingAnims.toList()
        pendingAnims.clear()
        return res
    }

    // Seats (real) that already confirmed the trick being shown in EndTurn:
    // the engine keeps it in deal.inPlay until everyone confirmed, but a
    // player who tapped through shouldn't see it come back.
    private val trickConfirmed = mutableSetOf<Int>()

    init {
        if (!four) {
            game.calc = matchCalc
            game.externalDriver = true
        }
    }

    private fun realOf(gameSeat: Int) = dealMap[gameSeat]
    private fun gameSeatOf(real: Int) = dealMap.indexOf(real)

    /** True when the sitting dealer still has to confirm the deal's score. */
    val awaitingDealerConfirm: Boolean
        get() = four && scoreWritten && !dealerConfirmed &&
                (game.phase == GamePhase.ScoreView || game.phase == GamePhase.Ended)

    fun start() {
        if (four) newDeal4() else game.next()
        pump()
    }

    /** Deal the next 4-player round: matchCalc.dealer sits out. */
    private fun newDeal4() {
        val d = matchCalc.dealer
        sittingOut = d
        // the three actives in real seating order, starting left of the dealer
        val around = listOf((d + 1) % 4, (d + 2) % 4, (d + 3) % 4)
        val h = around.indexOf(0).coerceAtLeast(0)
        val a = around.drop(h) + around.take(h) // host (real 0) first when active
        // the engine's turn order goes 0 -> 2 -> 1, so seat the circle to match
        dealMap = listOf(a[0], a[2], a[1])

        val c3 = Calculation(3, matchCalc.limit)
        c3.rules = matchCalc.rules.clone()
        c3.created = matchCalc.created
        // raspasy progression only looks at game types/success, not indices
        c3.gameLog = matchCalc.gameLog.toMutableList()
        for (g in 0..2) {
            val r = dealMap[g]
            c3.scores[g].name = names[r]
            c3.scores[g].pulya = matchCalc.scores[r].pulya
            c3.scores[g].gora = matchCalc.scores[r].gora
            for (g2 in 0..2)
                if (g2 != g)
                    c3.scores[g].visty[g2] = matchCalc.scores[r].visty[dealMap[g2]] ?: 0
        }
        // the first bid belongs to the player left of the sitting dealer;
        // the engine gives it to (calc.dealer - 1 + 3) % 3
        c3.dealer = (gameSeatOf(around[0]) + 1) % 3

        val g = Game.create()
        g.calc = c3
        g.externalDriver = true
        g.singleDealMode = true
        game = g
        pendingResult = null
        scoreWritten = false
        dealerConfirmed = seats[d] == SeatKind.BOT
        trickConfirmed.clear()
        game.next()
    }

    /** Write the finished deal into the authoritative 4-player pulka. */
    private fun writeDealToMatch() {
        val r = pendingResult ?: return
        val m = Calculation.GameResult().also { n ->
            n.gameType = r.gameType
            n.contract = r.contract
            n.multiplier = r.multiplier
            n.dealer = sittingOut
            n.contractor = dealMap.getOrElse(r.contractor) { 0 }
            n.taken = r.taken.entries.associate { (k, v) -> dealMap[k] to v }.toMutableMap()
            n.visters = r.visters.map { dealMap[it] }.toMutableList()
        }
        // engine convention: the prikup card never wins a trick, so the
        // sitting dealer takes 0 on raspasy (and scores the non-taking pulya)
        if (m.gameType == GameType.Raspasy)
            m.taken[sittingOut] = 0
        matchCalc.writeGame(m)
        scoreWritten = true
    }

    /** Advance until a human (local or remote) must act, playing bots inline. */
    fun pump() {
        while (true) {
            pendingAnims += game.animations // kept for the host UI to replay
            game.animations.clear()
            if (four && game.phase == GamePhase.EndPlay && pendingResult == null)
                pendingResult = game.getGameResult() // before writeGame skews the multiplier
            if (four && game.phase == GamePhase.ScoreView && !scoreWritten)
                writeDealToMatch()
            if (game.phase == GamePhase.Ended) {
                if (!four) break // 3p: the match itself is over
                if (!dealerConfirmed) break // hold until the sitting dealer taps through
                if (matchCalc.isFinished) {
                    matchEnded = true
                    break
                }
                newDeal4()
                continue
            }
            when (seats[realOf(game.turnController())]) {
                SeatKind.BOT -> {
                    try {
                        AI.makeMove(game)
                    } catch (e: Exception) {
                        // Same rare positions the original swallowed: if the AI
                        // gives up while playing, make any legal move instead.
                        if (game.phase == GamePhase.Playing) {
                            game.playCard(game.getAllowedMoves().first())
                            game.next()
                        } else {
                            throw e
                        }
                    }
                }
                SeatKind.LOCAL -> {
                    broadcast()
                    onLocalTurn()
                    return
                }
                SeatKind.REMOTE -> {
                    broadcast()
                    onLocalTurn() // host UI keeps up while others act / it spectates
                    return
                }
            }
        }
        broadcast()
        onLocalTurn()
    }

    /** Send every REMOTE seat its personal view of the current state. */
    private fun broadcast(badMoveFor: Int = -1) {
        if (game.phase != GamePhase.EndTurn) trickConfirmed.clear()
        val ended = if (four) matchEnded else game.phase == GamePhase.Ended
        val withScores = game.phase == GamePhase.ScoreView || game.phase == GamePhase.Ended
        for (seat in seats.indices) {
            if (seats[seat] != SeatKind.REMOTE) continue
            val g = gameSeatOf(seat)
            if (g >= 0) {
                val yourTurn = !ended && game.phase != GamePhase.Ended && game.turnController() == g
                val fieldFor = RemoteViews.buildFieldFor(game, g)
                    .let { f -> if (seat in trickConfirmed) f.filter { !it.isInPlay } else f }
                sendToSeat(
                    seat,
                    GameMsg.State(
                        field = fieldFor,
                        info = RemoteViews.buildTableInfoFor(game, g, sitOutName = sitOutName),
                        yourTurn = yourTurn,
                        ask = if (yourTurn) RemoteViews.buildAsk(game) else null,
                        badMove = seat == badMoveFor,
                        ended = ended,
                        scores = if (withScores) RemoteViews.buildScoresFrom(matchCalc, seat) else null
                    )
                )
            } else {
                // the sitting dealer spectates; between deals they confirm the score
                val confirm = !ended && awaitingDealerConfirm
                sendToSeat(
                    seat,
                    GameMsg.State(
                        field = RemoteViews.buildFieldFor(game, 0, spectator = true),
                        info = RemoteViews.buildTableInfoFor(game, 0, watching = true, sitOutName = sitOutName),
                        yourTurn = confirm,
                        ask = if (confirm) Ask("confirm") else null,
                        badMove = false,
                        ended = ended,
                        scores = if (withScores) RemoteViews.buildScoresFrom(matchCalc, seat) else null
                    )
                )
            }
        }
    }

    /** The sitting dealer confirmed the deal's score. */
    fun dealerConfirm() {
        if (!awaitingDealerConfirm) return
        dealerConfirmed = true
        if (game.phase == GamePhase.Ended) pump()
    }

    /** Apply a remote player's answer. Ignores messages from the wrong seat. */
    fun onRemoteAct(seat: Int, act: GameMsg.Act) {
        if (seats.getOrNull(seat) != SeatKind.REMOTE) return
        if (matchEnded) return
        if (four && seat == sittingOut) {
            if (act.confirm == true) dealerConfirm()
            return
        }
        val g = gameSeatOf(seat)
        if (g < 0 || game.phase == GamePhase.Ended || game.turnController() != g) return

        var ok = true
        when (game.phase) {
            GamePhase.Negotiations -> {
                val bid = act.bid ?: return
                game.makeBid(bid)
            }
            GamePhase.GameChoose -> {
                val bid = act.contract ?: return
                game.setContract(bid)
            }
            GamePhase.VistNegotiations -> {
                game.setVist(act.vist ?: return)
            }
            GamePhase.OpeningChoose -> {
                game.setOpeningChoice(act.opening ?: return)
            }
            GamePhase.Discarding -> {
                val discard = act.discard ?: return
                val hand = game.deal.hands[g].cards
                val distinct = discard.size == 2 &&
                        !(discard[0].value == discard[1].value && discard[0].coatColor == discard[1].coatColor)
                val present = distinct && discard.all { d ->
                    hand.any { it.value == d.value && it.coatColor == d.coatColor }
                }
                if (present) {
                    game.discardCard(discard[0])
                    game.discardCard(discard[1])
                }
                if (game.deal.hands[g].cards.size != 10) ok = false
            }
            GamePhase.Playing -> {
                val card = act.play ?: return
                if (!game.playCard(card)) ok = false
            }
            GamePhase.PrikupOpened -> {
                if (act.confirm != true) return
                game.prikupClose()
            }
            GamePhase.EndTurn -> {
                if (act.confirm != true) return
                trickConfirmed.add(seat)
                game.turnClose()
            }
            GamePhase.EndPlay -> {
                if (act.confirm != true) return
                game.endConfirm()
            }
            GamePhase.ScoreView -> {
                if (act.confirm != true) return
                game.scoreClose()
            }
            else -> return
        }

        if (!ok) {
            broadcast(badMoveFor = seat)
            return
        }
        game.next()
        pump()
    }

    /** The LOCAL seat acted through the normal UI path; continue the loop. */
    fun onLocalActed() {
        game.next()
        pump()
    }
}
