package com.an0obIs.pref.ui.game

import com.an0obIs.pref.model.Card
import com.an0obIs.pref.model.Game
import com.an0obIs.pref.model.GamePhase

/**
 * A card placed on the virtual 480x716 table (the original WP7 canvas size).
 * All geometry below is a direct port of GameMain.xaml.cs drawing code;
 * the composable scales these units to the actual screen.
 */
data class PlacedCard(
    val card: Card?, // null = face down
    val hand: Int,
    val x: Double,
    val y: Double,
    val isInPlay: Boolean = false,
    val isPrikup: Boolean = false
)

object TableLayout {
    const val W = 480.0
    const val H = 716.0
    const val S0 = 70.0
    const val S1 = 79.0

    fun inPlayCoords(hand: Int): Pair<Double, Double> = when (hand) {
        1 -> Pair((W / 2) - S1 / 1.36, (H / 2) - S1)
        2 -> Pair(W / 2, (H / 2) - S1)
        0 -> Pair((W / 2) - (S1 / 2 / 1.36), H / 2)
        -10 -> Pair((W / 2) - S1 / 1.36, (H / 2) - S1) // первая карта из сброса
        -20 -> Pair(W / 2, (H / 2) - S1) // вторая карта из сброса
        else -> Pair((W / 2) - (S1 / 2 / 1.36), (H / 2) - 2 * S1) // карта из прикупа
    }

    fun outOfPlayCoords(hand: Int): Pair<Double, Double> = when (hand) {
        1 -> Pair(0.0, 0.0)
        2 -> Pair(W, 0.0)
        0 -> Pair(S1, H)
        else -> Pair(0.0, 0.0)
    }

    /** Start position for a card animated out of an invisible hand (port of AnimateCard). */
    fun hiddenStartCoords(player: Int): Pair<Double, Double> = when (player) {
        2 -> Pair(W - S1, 3.0)
        1 -> Pair(3.0, 3.0)
        else -> Pair((W - S1) / 2, 3.0)
    }

    /** Port of DrawOpenHand: lays out one hand's cards grouped by suit. */
    fun handPlacements(cards: List<Card>, hand: Int, special: Boolean, hidden: Boolean): List<PlacedCard> {
        val res = mutableListOf<PlacedCard>()
        var row = 0.0
        var col = 0.0
        val max: Double
        var k = 1.36
        var coats = intArrayOf(0, 2, 1, 3)
        if (cards.firstOrNull { it.coatColor == 2 } == null) {
            coats = intArrayOf(0, 3, 1, 2)
        }
        if (cards.firstOrNull { it.coatColor == 1 } == null) {
            coats = intArrayOf(1, 2, 0, 3)
        }
        val dx: Double
        val dy: Double
        when (hand) {
            1 -> {
                dx = 10.0; dy = 42.0; max = 2.0
            }
            2 -> {
                dx = 345.0; dy = 42.0; max = 2.0
            }
            else -> {
                if (cards.size > 10) {
                    dx = 155.0; dy = 512.0; max = 6.0; k = 1.5
                } else {
                    dx = 171.0; dy = 512.0; max = 5.0
                }
            }
        }
        for (i in 0 until 4) {
            val coat = coats[i]
            val list = cards.filter { it.coatColor == coat }.sortedByDescending { it.value }
            if (list.isEmpty())
                continue
            for (card in list) {
                res.add(
                    PlacedCard(
                        card = if (hidden) null else card,
                        hand = hand,
                        x = dx + row * S1 / k,
                        y = dy + col * S1,
                        isPrikup = special
                    )
                )
                row++
                if (row >= max) {
                    row = 0.0
                    col++
                }
            }
        }
        return res
    }

    /**
     * Port of DrawField's card layer.
     * @param discardSelection cards the player has moved to the discard spots
     * @param showPrikupHand if 1 or 2, that hand is drawn together with possible talon cards
     */
    fun computeField(
        game: Game,
        discardSelection: List<Card> = emptyList(),
        showPrikupHand: Int? = null
    ): List<PlacedCard> {
        val res = mutableListOf<PlacedCard>()
        val deal = game.deal

        // Карты рук
        for (hand in 0 until 3) {
            if (hand == showPrikupHand) {
                val list = deal.hands[hand].cards.toMutableList()
                val colorNotExists =
                    if (game.contractor == game.getPrevPlayer())
                        game.aIs[game.playerInTurn]!!.prevHand.colorNotExists
                    else
                        game.aIs[game.playerInTurn]!!.nextHand.colorNotExists
                for (card in deal.prikup.cards) {
                    if (!colorNotExists.contains(card.coatColor))
                        list.add(card)
                }
                res.addAll(handPlacements(list, hand, special = true, hidden = false))
            } else {
                res.addAll(handPlacements(deal.hands[hand].cards, hand, special = false, hidden = !deal.hands[hand].isVisible))
            }
        }

        // Отобранные для сброса карты — переносим в центр
        if (game.phase == GamePhase.Discarding && discardSelection.isNotEmpty()) {
            for ((idx, card) in discardSelection.withIndex()) {
                val pos = res.indexOfFirst { it.hand == game.playerInTurn && it.card?.id == card.id }
                if (pos >= 0) {
                    val c = inPlayCoords((idx + 1) * -10)
                    res[pos] = res[pos].copy(x = c.first, y = c.second)
                }
            }
        }

        // Карты в игре
        for ((key, card) in game.deal.inPlay) {
            val c = inPlayCoords(key)
            res.add(PlacedCard(card = card, hand = key, x = c.first, y = c.second, isInPlay = true))
        }

        // Прикуп
        if (game.deal.prikup.isVisible) {
            var k = 0
            for (card in game.deal.prikup.cards) {
                val x = (W / 2) - S1 / 1.36 + k * S1 / 1.36
                val y = (H / 2) - (S1 / 2)
                res.add(PlacedCard(card = card, hand = 3, x = x, y = y, isPrikup = true))
                k++
            }
        }
        return res
    }
}
