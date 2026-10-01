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
        g.currentGameType = com.an0obIs.pref.model.GameType.Normal
        g.phase = GamePhase.EndTurn
        g.firstMovePerformer = 1
        g.playerInTurn = 1
        g.playerToTake = 2
        assertEquals(2, g.leaderSeat())
    }

    @Test
    fun allPassTalonTricksStartWithTheFirstHand() {
        // regression (found in a live multiplayer test): the trick that follows
        // the first review opens a talon card, and then the first hand moves
        // first, so that review must not point at the taker
        val g = game(dealer = 0) // first hand = seat 1
        g.phase = GamePhase.EndTurn
        g.currentGameType = com.an0obIs.pref.model.GameType.Raspasy
        g.playerToTake = 2
        assertEquals("review of trick 1", 1, g.leaderSeat())
        g.deal.hands[0].taken = 1 // trick 2 on view: trick 3 has no talon card
        assertEquals("review of trick 2", 2, g.leaderSeat())
        g.deal.hands[0].taken = 5
        assertEquals("a late review", 2, g.leaderSeat())
    }

    @Test
    fun movingMarkerFollowsTheLeaderInTheTrickReview() {
        val info = com.an0obIs.pref.ui.game.TableInfo(
            phase = GamePhase.EndTurn, playerToTake = 2, leader = 1
        )
        assertEquals(1, com.an0obIs.pref.ui.game.GameTexts.turnMarker(info))
        // an older host sends no leader: fall back to the taker
        assertEquals(2, com.an0obIs.pref.ui.game.GameTexts.turnMarker(info.copy(leader = -1)))
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
