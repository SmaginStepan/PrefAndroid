package com.an0obIs.pref

import com.an0obIs.pref.model.Game
import com.an0obIs.pref.model.GamePhase
import com.an0obIs.pref.model.PrefStorage
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/** The lead marker: first hand until play starts, then the round's leader. */
class LeaderSeatTest {

    @Before
    fun setUp() {
        PrefStorage.init(Files.createTempDirectory("pref-leader-test").toFile())
    }

    private fun game(dealer: Int): Game {
        val g = Game.create()
        g.calc.dealer = dealer
        return g
    }

    @Test
    fun firstHandIsLeftOfTheDealerBeforePlay() {
        for (dealer in 0..2) {
            val g = game(dealer)
            for (phase in listOf(
                GamePhase.Negotiations, GamePhase.VistNegotiations, GamePhase.PrikupOpened,
                GamePhase.Discarding, GamePhase.GameChoose, GamePhase.OpeningChoose
            )) {
                g.phase = phase
                assertEquals("dealer $dealer, $phase", (dealer + 1) % 3, g.leaderSeat())
            }
        }
    }

    @Test
    fun trickLeaderStaysMarkedWhileOthersPlay() {
        val g = game(dealer = 2)
        g.phase = GamePhase.Playing
        g.firstMovePerformer = 1   // seat 1 led this trick
        g.playerInTurn = 0         // and it is seat 0's turn now
        assertEquals(1, g.leaderSeat())
    }

    @Test
    fun betweenTricksTheNextLeaderIsMarked() {
        val g = game(dealer = 0)
        g.phase = GamePhase.Playing
        g.firstMovePerformer = -1  // nobody has played to the new trick yet
        g.playerInTurn = 2         // the previous taker leads
        assertEquals(2, g.leaderSeat())
    }

    @Test
    fun finishedTrickMarksTheTaker() {
        val g = game(dealer = 0)
        g.phase = GamePhase.EndTurn
        g.firstMovePerformer = 1
        g.playerInTurn = 1
        g.playerToTake = 2
        assertEquals(2, g.leaderSeat())
    }

    @Test
    fun resultAndScoreScreensHaveNoLeader() {
        val g = game(dealer = 0)
        for (phase in listOf(GamePhase.EndPlay, GamePhase.ScoreView, GamePhase.Ended, GamePhase.NotStarted)) {
            g.phase = phase
            assertEquals(phase.toString(), -1, g.leaderSeat())
        }
    }
}
