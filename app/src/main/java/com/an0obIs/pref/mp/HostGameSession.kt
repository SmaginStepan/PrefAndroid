package com.an0obIs.pref.mp

import com.an0obIs.pref.ai.AI
import com.an0obIs.pref.model.Game
import com.an0obIs.pref.model.GamePhase

enum class SeatKind { LOCAL, BOT, REMOTE }

/**
 * Runs a hosted multiplayer game on top of the untouched engine.
 *
 * The engine's isAI() is disabled via game.externalDriver, so game.next()
 * stops at EVERY input point; this class dispatches each stop to the seat's
 * driver: the local UI, the built-in AI, or a remote player over the relay.
 *
 * Single-threaded by design: the caller must serialize calls (the app runs
 * it on one dispatcher; the unit test calls it directly).
 */
class HostGameSession(
    val game: Game,
    private val seats: List<SeatKind>,
    /** Deliver a state message to a REMOTE seat (absolute seat number). */
    private val sendToSeat: (seat: Int, msg: GameMsg.State) -> Unit,
    /** The LOCAL seat (host UI) should refresh / take its turn. */
    private val onLocalTurn: () -> Unit
) {

    init {
        game.externalDriver = true
    }

    fun start() {
        game.next()
        pump()
    }

    /** Advance until a human (local or remote) must act, playing bots inline. */
    fun pump() {
        while (game.phase != GamePhase.Ended) {
            game.animations.clear() // multiplayer sends state snapshots instead
            when (seats[game.playerInTurn]) {
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
                    return
                }
            }
        }
        broadcast()
    }

    /** Send every REMOTE seat its personal view of the current state. */
    private fun broadcast(badMoveFor: Int = -1) {
        val ended = game.phase == GamePhase.Ended
        val withScores = ended || game.phase == GamePhase.ScoreView
        for (seat in seats.indices) {
            if (seats[seat] != SeatKind.REMOTE) continue
            val yourTurn = !ended && game.playerInTurn == seat
            sendToSeat(
                seat,
                GameMsg.State(
                    field = RemoteViews.buildFieldFor(game, seat),
                    info = RemoteViews.buildTableInfoFor(game, seat),
                    yourTurn = yourTurn,
                    ask = if (yourTurn) RemoteViews.buildAsk(game) else null,
                    badMove = seat == badMoveFor,
                    ended = ended,
                    scores = if (withScores) RemoteViews.buildScoresFor(game, seat) else null
                )
            )
        }
    }

    /** Apply a remote player's answer. Ignores messages from the wrong seat. */
    fun onRemoteAct(seat: Int, act: GameMsg.Act) {
        if (seats.getOrNull(seat) != SeatKind.REMOTE) return
        if (game.phase == GamePhase.Ended || game.playerInTurn != seat) return

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
                val hand = game.deal.hands[seat].cards
                val distinct = discard.size == 2 &&
                        !(discard[0].value == discard[1].value && discard[0].coatColor == discard[1].coatColor)
                val present = distinct && discard.all { d ->
                    hand.any { it.value == d.value && it.coatColor == d.coatColor }
                }
                if (present) {
                    game.discardCard(discard[0])
                    game.discardCard(discard[1])
                }
                if (game.deal.hands[seat].cards.size != 10) ok = false
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
