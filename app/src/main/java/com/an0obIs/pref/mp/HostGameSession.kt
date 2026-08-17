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
 * Confirmation phases (opened prikup, finished trick, deal result, score
 * sheet) are handled as ORDER-INDEPENDENT stops: every human — including the
 * 4-player sitting dealer — confirms in any order; once a player confirmed,
 * their view moves on (trick hidden, score sheet dismissed) and shows who is
 * still being waited for. The engine's own turn-ordered confirm cycle is
 * applied in one go when the last human confirms. Only real moves (bids,
 * whist, discards, cards) stay with the seat that owns them.
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

    // ---- order-independent confirm stops -----------------------------------

    private val confirmPhases = setOf(
        GamePhase.PrikupOpened, GamePhase.EndTurn, GamePhase.EndPlay, GamePhase.ScoreView
    )

    /** Real seats that confirmed the current stop. */
    private val stopConfirmed = mutableSetOf<Int>()
    private var stopId: String? = null

    /** Bumped whenever a new stop begins; used by the auto-confirm timer. */
    var stopKey = 0L
        private set

    private fun humanSeats(): List<Int> =
        seats.indices.filter { seats[it] != SeatKind.BOT }

    /** The real seat whose own action produced the stop — no confirm needed:
     *  the last card of the trick, or the contractor at the opened prikup. */
    private fun stopMover(): Int = when (game.phase) {
        // after the closing card, playCard advanced playerInTurn one step back
        GamePhase.EndTurn -> dealMap.getOrElse((game.playerInTurn + 1) % 3) { -1 }
        GamePhase.PrikupOpened -> dealMap.getOrElse(game.contractor) { -1 }
        else -> -1
    }

    private fun currentStopId(): String? {
        val phase = game.phase
        if (phase !in confirmPhases) return null
        val deal = System.identityHashCode(game.deal)
        return "$deal-$phase-${game.deal.totalTaken}"
    }

    /** True while the session waits for confirmations. */
    val atConfirmStop: Boolean
        get() = stopId != null && stopId == currentStopId()

    fun hasConfirmed(seat: Int): Boolean = atConfirmStop && seat in stopConfirmed

    /** Names of the humans everyone is waiting for at the current stop. */
    fun waitingNames(): List<String> =
        if (atConfirmStop) humanSeats().filter { it !in stopConfirmed }.map { names[it] }
        else emptyList()

    /** A human confirmed the current stop (any order). */
    fun confirmSeat(seat: Int) {
        if (game.phase !in confirmPhases) return
        if (seats.getOrNull(seat) == SeatKind.BOT) return
        if (stopId != currentStopId()) return // stale tap from a previous stop
        stopConfirmed.add(seat)
        pump()
    }

    /** Auto-confirm timer fired: everyone still pending is confirmed. */
    fun confirmAll() {
        if (game.phase !in confirmPhases) return
        stopConfirmed.addAll(humanSeats())
        pump()
    }

    /** Run the engine's whole confirm cycle for the finished stop. */
    private fun applyStop() {
        when (game.phase) {
            GamePhase.PrikupOpened -> while (game.phase == GamePhase.PrikupOpened) {
                game.prikupClose()
                game.next()
            }
            GamePhase.EndTurn -> while (game.phase == GamePhase.EndTurn) {
                game.turnClose()
                game.next()
            }
            GamePhase.EndPlay -> while (game.phase == GamePhase.EndPlay) {
                game.endConfirm()
                game.next()
            }
            GamePhase.ScoreView -> while (game.phase == GamePhase.ScoreView) {
                game.scoreClose()
                game.next()
            }
            else -> {}
        }
        stopId = null
        stopConfirmed.clear()
    }

    // ------------------------------------------------------------------------

    // ---- agreement offers («расписать») ------------------------------------

    private class PendingOffer(
        val byReal: Int,
        /** absolute game-seat -> agreed final takes */
        val takenGame: Map<Int, Int>,
        /** real seats still to answer */
        val awaiting: MutableSet<Int>
    )

    private var pendingOffer: PendingOffer? = null
    val offerPending: Boolean get() = pendingOffer != null

    /** Who declined the last offer; carried in the next broadcast and shown
     *  as a hint on the host once consumed. */
    private var declineNotice: String? = null

    fun consumeDeclineNotice(): String? {
        val n = declineNotice
        declineNotice = null
        return n
    }

    fun offerSnapFor(realSeat: Int): OfferSnap? = pendingOffer?.let { o ->
        val g = gameSeatOf(realSeat).coerceAtLeast(0)
        OfferSnap(
            by = names[o.byReal],
            taken = List(3) { rel -> o.takenGame[(rel + g) % 3] ?: 0 },
            youRespond = realSeat in o.awaiting
        )
    }

    /**
     * «Остальные мои»: the offerer takes every remaining trick, everyone else
     * keeps their resolved count. On распасы, and on мизер from the declarer,
     * it applies instantly; from a мизер catcher, the other HUMAN non-declarer
     * players must confirm (bots are excluded from this negotiation).
     */
    fun makeRestMineOffer(realSeat: Int) {
        if (matchEnded || pendingOffer != null) return
        if (game.phase != GamePhase.Playing) return
        val raspasy = game.currentGameType == GameType.Raspasy
        val miser = game.currentGameType == GameType.Miser
        if (!raspasy && !miser) return
        val g = gameSeatOf(realSeat)
        if (g < 0) return // the sitting dealer never offers
        val remaining = 10 - game.deal.totalTaken
        val taken = (0..2).associateWith {
            game.deal.hands[it].taken + if (it == g) remaining else 0
        }
        if (raspasy || g == game.contractor) {
            applyOffer(taken, noVists = false) // unilateral
            return
        }
        // мизер catcher: human non-declarer players confirm, declarer doesn't
        val responders = mutableSetOf<Int>()
        for (r in seats.indices) {
            if (r == realSeat || seats[r] == SeatKind.BOT) continue
            if (gameSeatOf(r) == game.contractor) continue
            responders.add(r)
        }
        if (responders.isEmpty()) {
            applyOffer(taken, noVists = false)
            return
        }
        pendingOffer = PendingOffer(realSeat, taken, responders)
        broadcast()
        onLocalTurn()
    }

    /** An involved player proposes the deal's final trick counts. */
    fun makeOffer(realSeat: Int, takenGame: Map<Int, Int>) {
        if (matchEnded || pendingOffer != null) return
        if (game.phase != GamePhase.Playing) return
        val miser = game.currentGameType == GameType.Miser
        if (!miser && game.currentGameType != GameType.Normal) return
        val g = gameSeatOf(realSeat)
        if (g < 0) return // the sitting dealer never offers
        if (!miser) {
            if (game.isVister.none { it.value }) return // nobody to agree with
            if (g != game.contractor && game.isVister[g] != true) return
        }
        // sanity: full distribution respecting resolved takes
        if ((0..2).sumOf { takenGame[it] ?: -100 } != 10) return
        for (s in 0..2) if ((takenGame[s] ?: return) < game.deal.hands[s].taken) return

        // «без 3»: the declarer surrenders unilaterally, whists are voided
        if (!miser && g == game.contractor && takenGame[g] == game.contract - 3) {
            applyOffer(takenGame, noVists = true)
            return
        }
        val responders = mutableSetOf<Int>()
        if (miser) {
            // everyone at the table answers, the sitting dealer included
            for (r in seats.indices) if (r != realSeat) responders.add(r)
        } else {
            for (ig in game.isVister.filterValues { it }.keys + game.contractor) {
                val r = realOf(ig)
                if (r != realSeat) responders.add(r)
            }
        }
        val offer = PendingOffer(realSeat, takenGame, responders)
        // bots answer instantly with the conservative rule
        for (r in responders.toList()) {
            if (seats[r] != SeatKind.BOT) continue
            val bg = gameSeatOf(r)
            val accepts = bg < 0 || Agreements.botAccepts(bg, game, takenGame)
            if (!accepts) {
                declineNotice = names[r]
                broadcast() // declined: the table resumes with the notice
                onLocalTurn()
                return
            }
            offer.awaiting.remove(r)
        }
        if (offer.awaiting.isEmpty()) {
            applyOffer(takenGame, noVists = false)
            return
        }
        pendingOffer = offer
        broadcast()
        onLocalTurn()
    }

    /** A responder answered the pending offer. */
    fun respondOffer(realSeat: Int, agree: Boolean) {
        val o = pendingOffer ?: return
        if (realSeat !in o.awaiting) return
        if (!agree) {
            pendingOffer = null // play resumes exactly where it was
            declineNotice = names[realSeat]
            broadcast()
            onLocalTurn()
            return
        }
        o.awaiting.remove(realSeat)
        if (o.awaiting.isEmpty()) applyOffer(o.takenGame, noVists = false)
        else {
            broadcast()
            onLocalTurn()
        }
    }

    private fun applyOffer(takenGame: Map<Int, Int>, noVists: Boolean) {
        pendingOffer = null
        game.applyAgreement(takenGame, noVists)
        game.next() // EndPlay: the usual result screen + confirm stop take over
        pump()
    }

    // ------------------------------------------------------------------------

    // Animations produced by other seats' moves, replayed by the host UI.
    // Guests still get plain state snapshots.
    private val pendingAnims = ArrayDeque<Game.Animation>()

    /** Take (and clear) the queued animations. Call under the session lock. */
    fun drainAnims(): List<Game.Animation> {
        val res = pendingAnims.toList()
        pendingAnims.clear()
        return res
    }

    init {
        if (!four) {
            game.calc = matchCalc
            game.externalDriver = true
        }
    }

    private fun realOf(gameSeat: Int) = dealMap[gameSeat]
    private fun gameSeatOf(real: Int) = dealMap.indexOf(real)

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
        stopId = null
        stopConfirmed.clear()
        pendingOffer = null
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

    /** Advance until a human must act, playing bots inline. */
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
                if (matchCalc.isFinished) {
                    matchEnded = true
                    break
                }
                newDeal4()
                continue
            }
            if (game.phase in confirmPhases) {
                val id = currentStopId()
                if (stopId != id) {
                    stopId = id
                    stopConfirmed.clear()
                    // whoever made the move that produced this stop saw it happen
                    stopMover().takeIf { it >= 0 }?.let { stopConfirmed.add(it) }
                    stopKey++
                }
                if (humanSeats().all { it in stopConfirmed }) {
                    applyStop()
                    continue
                }
                broadcast()
                onLocalTurn()
                return
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
        val ended = if (four) matchEnded else game.phase == GamePhase.Ended
        val atStop = atConfirmStop
        val waiting = waitingNames()
        val offerActive = pendingOffer != null
        val withScores = game.phase == GamePhase.ScoreView || game.phase == GamePhase.Ended
        for (seat in seats.indices) {
            if (seats[seat] != SeatKind.REMOTE) continue
            val confirmed = seat in stopConfirmed && atStop
            // a confirmed viewer already dismissed the score sheet; final
            // standings at game end always show
            val scoresFor = if (withScores && !(confirmed && !ended))
                RemoteViews.buildScoresFrom(matchCalc, seat) else null
            val g = gameSeatOf(seat)
            val yourTurn = !ended && !offerActive && if (atStop) !confirmed
            else g >= 0 && game.phase != GamePhase.Ended && game.turnController() == g
            val ask = when {
                !yourTurn -> null
                atStop -> Ask("confirm")
                else -> RemoteViews.buildAsk(game)
            }
            val offerFor = offerSnapFor(seat)
            val standings = RemoteViews.buildScoresFrom(matchCalc, seat)
            if (g >= 0) {
                val fieldFor = RemoteViews.buildFieldFor(game, g)
                    .let { f ->
                        if (confirmed && game.phase == GamePhase.EndTurn)
                            f.filter { !it.isInPlay } else f
                    }
                sendToSeat(
                    seat,
                    GameMsg.State(
                        field = fieldFor,
                        info = RemoteViews.buildTableInfoFor(
                            game, g, sitOutName = sitOutName,
                            waitingFor = waiting, youConfirmed = confirmed
                        ),
                        yourTurn = yourTurn,
                        ask = ask,
                        badMove = seat == badMoveFor,
                        ended = ended,
                        scores = scoresFor,
                        takes = RemoteViews.buildTakesFor(game, g),
                        layout = RemoteViews.buildLayoutFor(game, g),
                        standings = standings,
                        offer = offerFor,
                        offerDeclined = declineNotice
                    )
                )
            } else {
                // the sitting dealer spectates; they confirm the same stops
                sendToSeat(
                    seat,
                    GameMsg.State(
                        field = RemoteViews.buildFieldFor(game, 0, spectator = true),
                        info = RemoteViews.buildTableInfoFor(
                            game, 0, watching = true, sitOutName = sitOutName,
                            waitingFor = waiting, youConfirmed = confirmed
                        ),
                        yourTurn = yourTurn,
                        ask = ask,
                        badMove = false,
                        ended = ended,
                        scores = scoresFor,
                        takes = RemoteViews.buildTakesFor(game, 0),
                        standings = standings,
                        offer = offerFor,
                        offerDeclined = declineNotice
                    )
                )
            }
        }
    }

    /** Host ends the match early (after saving the pulka): everyone gets a
     *  final ended state with the standings. */
    fun abortMatch() {
        pendingOffer = null
        matchEnded = true
        game.phase = GamePhase.Ended
        broadcast()
    }

    /** Resend every guest's snapshot, e.g. after one of them reconnected. */
    fun rebroadcast() = broadcast()

    /** Apply a remote player's answer. Ignores messages from the wrong seat. */
    fun onRemoteAct(seat: Int, act: GameMsg.Act) {
        if (seats.getOrNull(seat) != SeatKind.REMOTE) return
        if (matchEnded) return
        if (act.confirm == true && game.phase in confirmPhases) {
            confirmSeat(seat)
            return
        }
        // agreement offers and answers bypass the turn gate
        act.agree?.let {
            respondOffer(seat, it)
            return
        }
        if (act.restMine == true) {
            makeRestMineOffer(seat)
            return
        }
        act.offer?.let { rel ->
            if (rel.size == 3) {
                val g = gameSeatOf(seat)
                if (g >= 0) makeOffer(seat, (0..2).associate { r -> (r + g) % 3 to rel[r] })
            }
            return
        }
        if (pendingOffer != null) return // table frozen while an offer is pending
        if (four && seat == sittingOut) return // the spectator has no other input
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
