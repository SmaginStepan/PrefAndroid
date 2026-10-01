package com.an0obIs.pref

import com.an0obIs.pref.model.GamePhase
import com.an0obIs.pref.ui.game.GameTexts
import com.an0obIs.pref.ui.game.TableInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class TurnMarkerTest {

    private fun info(phase: GamePhase, inTurn: Int = 1, toTake: Int = 2, contractor: Int = 0) =
        TableInfo(phase = phase, playerInTurn = inTurn, playerToTake = toTake, contractor = contractor)

    @Test
    fun followsThePlayerInTurnWhilePlaying() {
        assertEquals(1, GameTexts.turnMarker(info(GamePhase.Playing)))
        assertEquals(1, GameTexts.turnMarker(info(GamePhase.Negotiations)))
    }

    @Test
    fun finishedTrickPointsAtTheNextLeader() {
        // playerInTurn is stale (the old leader); the taker leads next
        assertEquals(2, GameTexts.turnMarker(info(GamePhase.EndTurn, inTurn = 1, toTake = 2)))
    }

    @Test
    fun openedTalonPointsAtTheContractor() {
        assertEquals(2, GameTexts.turnMarker(info(GamePhase.PrikupOpened, contractor = 2)))
    }

    @Test
    fun resultAndScoreScreensShowNoMarker() {
        assertEquals(-1, GameTexts.turnMarker(info(GamePhase.EndPlay)))
        assertEquals(-1, GameTexts.turnMarker(info(GamePhase.ScoreView)))
        assertEquals(-1, GameTexts.turnMarker(info(GamePhase.Ended)))
        assertEquals(-1, GameTexts.turnMarker(info(GamePhase.NotStarted)))
    }

    @Test
    fun animatingPlayerOverridesEverything() {
        assertEquals(0, GameTexts.turnMarker(info(GamePhase.Playing), override = 0))
        assertEquals(1, GameTexts.turnMarker(info(GamePhase.EndTurn, toTake = 2), override = 1))
        assertEquals(2, GameTexts.turnMarker(info(GamePhase.EndPlay), override = 2))
        // a table-level animation (raspasy talon card, player -1) changes nothing
        assertEquals(1, GameTexts.turnMarker(info(GamePhase.Playing), override = -1))
    }
}
