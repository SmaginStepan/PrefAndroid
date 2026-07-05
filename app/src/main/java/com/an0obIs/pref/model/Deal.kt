package com.an0obIs.pref.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.random.Random

@Serializable
class Card(
    var value: Int = 0,
    var coatColor: Int = 0
) {
    @Transient
    var estimation: Double = 0.0

    val id: Int
        get() = value + coatColor * 100

    fun greaterThan(card: Card, trump: Int, initColor: Int): Boolean {
        if (this.coatColor != trump && card.coatColor == trump)
            return false
        if (this.coatColor == trump && card.coatColor != trump)
            return true
        if (this.coatColor != initColor && card.coatColor == initColor)
            return false
        if (this.coatColor == initColor && card.coatColor != initColor)
            return true
        return this.value > card.value
    }

    override fun toString(): String {
        var res = when (value) {
            14 -> "Т"
            13 -> "K"
            12 -> "Д"
            11 -> "В"
            else -> value.toString()
        }
        res += when (coatColor) {
            0 -> "♠"
            1 -> "♣"
            2 -> "♦"
            3 -> "♥"
            else -> ""
        }
        return res
    }
}

@Serializable
class Hand {
    var isVisible: Boolean = false
    var cards: MutableList<Card> = mutableListOf()
    var taken: Int = 0

    @Transient
    var cardByCoat: MutableMap<Int, MutableList<Card>> = mutableMapOf()

    fun hasCoatColor(coatColor: Int): Boolean {
        val list = cardByCoat[coatColor] ?: return false
        return list.isNotEmpty()
    }

    fun sort() {
        cardByCoat.clear()
        for (i in 0 until 4) {
            cardByCoat[i] = mutableListOf()
        }
        for (card in cards) {
            cardByCoat[card.coatColor]!!.add(card)
        }
        for (i in 0 until 4) {
            cardByCoat[i] = cardByCoat[i]!!.sortedByDescending { it.value }.toMutableList()
        }
    }

    fun clone(): Hand {
        val res = Hand()
        res.cards = this.cards.toMutableList()
        res.isVisible = this.isVisible
        res.taken = this.taken
        res.sort()
        return res
    }
}

@Serializable
class Deal {
    var hands: MutableList<Hand> = mutableListOf()
    var prikup: Hand = Hand()
    var inPlay: MutableMap<Int, Card> = mutableMapOf()
    var inPlayCoatColor: Int = 0

    init {
        // The C# constructor always shuffled; deserialization overwrites this.
        shuffle()
    }

    /** Rebuild transient per-suit indexes after deserialization. */
    fun restoreAfterLoad() {
        hands.forEach { it.sort() }
        prikup.sort()
    }

    val totalTaken: Int
        get() = hands.sumOf { it.taken }

    fun shuffle() {
        prikup = Hand()
        hands = mutableListOf()
        inPlay = mutableMapOf()
        for (i in 7..14) {
            for (j in 0 until 4) {
                prikup.cards.add(Card(value = i, coatColor = j))
            }
        }
        val rnd = Random.Default
        for (i in 0 until 3) {
            val hand = Hand()
            hands.add(hand)
            for (j in 0 until 10) {
                val pos = rnd.nextInt(prikup.cards.size)
                hand.cards.add(prikup.cards[pos])
                prikup.cards.removeAt(pos)
            }
            hand.sort()
        }
    }
}
