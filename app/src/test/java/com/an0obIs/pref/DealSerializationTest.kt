package com.an0obIs.pref

import com.an0obIs.pref.model.Deal
import com.an0obIs.pref.model.PrefStorage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression for the reshuffle-on-load bug: Deal used to call shuffle() from an
 * init block, and kotlinx runs init blocks after assigning the decoded fields,
 * so every restored deal came back reshuffled with all trick counts at zero.
 */
class DealSerializationTest {

    @Test
    fun dealRoundtripKeepsCardsAndTaken() {
        val d = Deal().also { it.shuffle() }
        d.hands[0].taken = 3
        d.hands[1].taken = 7
        val srcCards = d.hands.map { h -> h.cards.map { it.id } }
        val s = PrefStorage.json.encodeToString(Deal.serializer(), d)
        val back = PrefStorage.json.decodeFromString(Deal.serializer(), s)
        assertEquals("cards must match source", srcCards, back.hands.map { h -> h.cards.map { it.id } })
        assertEquals(listOf(3, 7, 0), back.hands.map { it.taken })
    }

    @Test
    fun dealDecodesEveryFieldFromJson() {
        val s = """{"hands":[{"isVisible":true,"cards":[{"value":7,"coatColor":0}],"taken":3},""" +
                """{"isVisible":false,"cards":[],"taken":7},""" +
                """{"isVisible":false,"cards":[],"taken":0}],""" +
                """"prikup":{"isVisible":false,"cards":[],"taken":0},""" +
                """"inPlay":{"1":{"value":8,"coatColor":1}},"inPlayCoatColor":2}"""
        val back = PrefStorage.json.decodeFromString(Deal.serializer(), s)
        assertEquals(3, back.hands.size)
        assertEquals(1, back.hands[0].cards.size)
        assertEquals(listOf(3, 7, 0), back.hands.map { it.taken })
        assertEquals(0, back.prikup.cards.size)
        assertEquals(1, back.inPlay.size)
        assertEquals(2, back.inPlayCoatColor)
    }
}
