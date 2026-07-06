package com.an0obIs.pref

import com.an0obIs.pref.model.Game
import com.an0obIs.pref.model.GamePhase
import com.an0obIs.pref.model.PrefStorage
import com.an0obIs.pref.mp.GameMsg
import com.an0obIs.pref.mp.HostGameSession
import com.an0obIs.pref.mp.SeatKind
import com.an0obIs.pref.mp.gameJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * Milestone test for hosted multiplayer: all three seats are simulated REMOTE
 * clients that act purely on the protocol messages they receive — exactly what
 * real guests will do. Verifies the pump loop, action application, JSON
 * round-tripping of every message, and that no snapshot ever leaks a hidden card.
 */
class HostGameSessionTest {

    @Before
    fun setUp() {
        PrefStorage.init(Files.createTempDirectory("pref-mp-test").toFile())
    }

    private class RemotePlayer {
        var hasBidThisDeal = false
    }

    @Test
    fun hostSeesOwnHandInHostedGame() {
        val game = Game.create()
        game.externalDriver = true
        game.next() // deals; waits for seat input
        val field = com.an0obIs.pref.ui.game.TableLayout.computeField(game)
        val ownFaceUp = field.count { it.hand == 0 && !it.isInPlay && !it.isPrikup && it.card != null }
        val othersFaceDown = field.count { it.hand in 1..2 && !it.isInPlay && !it.isPrikup && it.card == null }
        assertEquals("host sees all 10 own cards", 10, ownFaceUp)
        assertEquals("opponents stay face-down", 20, othersFaceDown)
    }

    @Test
    fun threeRemotePlayersFinishGamesWithoutLeaks() {
        repeat(3) { round ->
            val game = Game.create()
            game.calc.limit = 4
            val seats = listOf(SeatKind.REMOTE, SeatKind.REMOTE, SeatKind.REMOTE)
            val players = List(3) { RemotePlayer() }
            val pending = ArrayDeque<Pair<Int, GameMsg.State>>()
            var leaks = 0
            var statesSent = 0

            lateinit var session: HostGameSession
            session = HostGameSession(
                game = game,
                seats = seats,
                sendToSeat = { seat, msg ->
                    statesSent++
                    // wire round-trip: everything must survive JSON
                    val wire = gameJson.encodeToString(GameMsg.serializer(), msg)
                    val decoded = gameJson.decodeFromString(GameMsg.serializer(), wire) as GameMsg.State
                    // redaction check: relative hands 1..2 must be face-down
                    // unless the absolute hand is opened by play
                    for (pc in decoded.field) {
                        if (pc.hand in 1..2 && !pc.isInPlay && !pc.isPrikup && pc.card != null) {
                            val absolute = (pc.hand + seat) % 3
                            if (!game.deal.hands[absolute].isVisible) leaks++
                        }
                    }
                    pending.addLast(seat to decoded)
                },
                onLocalTurn = { }
            )
            session.start()

            var steps = 0
            while (game.phase != GamePhase.Ended && steps++ < 200_000) {
                val (seat, st) = pending.removeFirstOrNull() ?: break
                if (!st.yourTurn) continue
                val ask = st.ask ?: continue
                if (st.info.phase == GamePhase.Negotiations && st.info.curentBids.isEmpty())
                    players.forEach { it.hasBidThisDeal = false }
                val me = players[seat]
                val act: GameMsg.Act? = when (ask.kind) {
                    "bid" -> {
                        val bids = ask.bids ?: emptyList()
                        val real = bids.firstOrNull { !it.pas && !it.miser }
                        if (!me.hasBidThisDeal && real != null) {
                            me.hasBidThisDeal = true
                            GameMsg.Act(bid = real)
                        } else {
                            GameMsg.Act(bid = bids.first { it.pas })
                        }
                    }
                    "contract" -> GameMsg.Act(contract = ask.bids!!.first())
                    "vist" -> GameMsg.Act(vist = true)
                    "opening" -> GameMsg.Act(opening = true)
                    "discard" -> {
                        val mine = st.field.filter {
                            it.hand == 0 && it.card != null && !it.isInPlay && !it.isPrikup
                        }.map { it.card!! }
                        GameMsg.Act(discard = mine.take(2))
                    }
                    "play" -> GameMsg.Act(play = ask.allowed!!.first())
                    "confirm" -> GameMsg.Act(confirm = true)
                    else -> null
                }
                if (act != null) {
                    // wire round-trip for the act too
                    val wire = gameJson.encodeToString(GameMsg.serializer(), act)
                    session.onRemoteAct(seat, gameJson.decodeFromString(GameMsg.serializer(), wire) as GameMsg.Act)
                }
            }

            println("Round $round: phase=${game.phase} deals=${game.calc.gameLog.size} states=$statesSent steps=$steps")
            assertEquals("no hidden cards may leak", 0, leaks)
            assertTrue("deals were played", game.calc.gameLog.isNotEmpty())
            assertEquals("game finished", GamePhase.Ended, game.phase)
        }
    }
}
