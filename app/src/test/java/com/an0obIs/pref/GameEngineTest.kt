package com.an0obIs.pref

import com.an0obIs.pref.ai.AI
import com.an0obIs.pref.model.Game
import com.an0obIs.pref.model.GamePhase
import com.an0obIs.pref.model.PrefStorage
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * Engine smoke test: plays full games with all three seats driven by the AI.
 * The human seat is driven by the same AI.makeMove dispatcher the game uses
 * for computer players, which exercises bidding, discarding, all game types
 * (contract, miser, raspasy) and scoring end to end.
 */
class GameEngineTest {

    @Before
    fun setUp() {
        PrefStorage.init(Files.createTempDirectory("pref-test").toFile())
    }

    @Test
    fun playsFullGamesWithoutCrashing() {
        repeat(3) { round ->
            val game = Game.create()
            game.calc.limit = 4 // short game
            var steps = 0
            game.next()
            while (game.phase != GamePhase.Ended && steps < 20000) {
                try {
                    AI.makeMove(game)
                } catch (e: Exception) {
                    // Paths where the original game waits for the human (e.g. the
                    // player catches a misère or leads open-whist hands): play like
                    // a human tapping the first allowed card.
                    if (game.phase == GamePhase.Playing) {
                        val moves = game.getAllowedMoves()
                        game.playCard(moves.first())
                        game.next()
                    } else {
                        throw e
                    }
                }
                steps++
            }
            println("Round $round: phase=${game.phase} deals=${game.calc.gameLog.size} steps=$steps")
            assertTrue("game must progress (deals played)", game.calc.gameLog.isNotEmpty())
            assertTrue("game must finish in a bounded number of steps", game.phase == GamePhase.Ended)
        }
    }

    @Test
    fun savesAndLoadsGame() {
        val game = Game.create()
        game.next() // deals cards, runs negotiations until human input is needed
        game.saveLast()
        val loaded = Game.loadLast()
        assertTrue(loaded != null)
        assertTrue(loaded!!.calc.playersCount == 3)
        assertTrue(loaded.deal.hands.sumOf { it.cards.size } + loaded.deal.prikup.cards.size == 32)
    }
}
