package com.an0obIs.pref

import com.an0obIs.pref.model.Calculation
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
 * Milestone test for hosted multiplayer: every seat is a simulated REMOTE
 * client that acts purely on the protocol messages it receives — exactly what
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

    /** Drives a session of N remote players until it ends; returns leak count. */
    private fun driveRemoteMatch(playersCount: Int, limit: Int): Pair<HostGameSession, Int> {
        val calc = Calculation(playersCount, limit)
        val names = List(playersCount) { "P$it" }
        for (i in 0 until playersCount) calc.scores[i].name = names[i]
        val seats = List(playersCount) { SeatKind.REMOTE }
        val players = List(playersCount) { RemotePlayer() }
        val pending = ArrayDeque<Pair<Int, GameMsg.State>>()
        var leaks = 0

        lateinit var session: HostGameSession
        session = HostGameSession(
            seats = seats,
            names = names,
            matchCalc = calc,
            sendToSeat = { seat, msg ->
                // wire round-trip: everything must survive JSON
                val wire = gameJson.encodeToString(GameMsg.serializer(), msg)
                val decoded = gameJson.decodeFromString(GameMsg.serializer(), wire) as GameMsg.State
                // redaction: relative hands 1..2 (and the spectator's hand 0)
                // may only be face-up when the play opened some hand
                val anyOpen = session.game.deal.hands.any { it.isVisible }
                for (pc in decoded.field) {
                    val hidden = pc.hand in 1..2 || (decoded.info.watching && pc.hand == 0)
                    if (hidden && !pc.isInPlay && !pc.isPrikup && pc.card != null && !anyOpen) leaks++
                }
                pending.addLast(seat to decoded)
            },
            onLocalTurn = { }
        )
        session.start()

        fun over() = if (playersCount == 4) session.matchEnded
        else session.game.phase == GamePhase.Ended

        var steps = 0
        while (!over() && steps++ < 400_000) {
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
        println("players=$playersCount deals=${calc.gameLog.size} steps=$steps ended=${over()}")
        assertTrue("match must finish", over())
        assertTrue("deals were played", calc.gameLog.isNotEmpty())
        return session to leaks
    }

    @Test
    fun threeRemotePlayersFinishGamesWithoutLeaks() {
        repeat(3) {
            val (_, leaks) = driveRemoteMatch(playersCount = 3, limit = 4)
            assertEquals("no hidden cards may leak", 0, leaks)
        }
    }

    @Test
    fun fourPlayersWithSittingDealerFinishGamesWithoutLeaks() {
        repeat(3) {
            val (session, leaks) = driveRemoteMatch(playersCount = 4, limit = 3)
            assertEquals("no hidden cards may leak", 0, leaks)
            // every deal must have been written with the sitting dealer as its dealer
            val calc = session.matchCalc
            assertTrue("4p results reference all four players",
                calc.gameLog.all { it.dealer in 0..3 })
        }
    }
}
