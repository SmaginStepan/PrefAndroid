package com.an0obIs.pref.ai

import com.an0obIs.pref.model.Card
import com.an0obIs.pref.model.Game
import java.util.ArrayDeque

class Rasklad {
    var byColor: MutableMap<Int, ColorRasklad> = mutableMapOf()
    var probability: Double = 1.0

    fun fromGameCheat(game: Game) {
        probability = 1.0
        byColor = mutableMapOf()
        for (i in 0 until 4) {
            val cr = ColorRasklad().also {
                it.coatColor = i
                it.probability = 1.0
                it.myHand = game.deal.hands[game.playerInTurn].cardByCoat[i]!!.map { c -> c.value }.sorted().toMutableList()
                it.nextHand = game.deal.hands[game.getNextPlayer()].cardByCoat[i]!!.map { c -> c.value }.sorted().toMutableList()
                it.prevHand = game.deal.hands[game.getPrevPlayer()].cardByCoat[i]!!.map { c -> c.value }.sorted().toMutableList()
            }
            byColor[i] = cr
        }
    }

    fun fromPlay(info: AIInfo) {
        probability = 1.0
        byColor = mutableMapOf()
        for (i in 0 until 4) {
            var prevHand: MutableList<Int>? = if (info.prevHand.isVisible)
                info.prevHand.visibleHand!!.cardByCoat[i]!!.map { c -> c.value }.sorted().toMutableList()
            else null
            var nextHand: MutableList<Int>? = if (info.nextHand.isVisible)
                info.nextHand.visibleHand!!.cardByCoat[i]!!.map { c -> c.value }.sorted().toMutableList()
            else null
            val myHand = info.myHand.visibleHand!!.cardByCoat[i]!!.map { c -> c.value }.sorted().toMutableList()

            if (prevHand == null) {
                val list = mutableListOf<Int>()
                for (v in 7 until 15) {
                    if (!myHand.contains(v) && !nextHand!!.contains(v) && !info.prevHand.colorNotExists.contains(i) && info.outOfPlayByColor[i]!!.firstOrNull { c -> c.value == v } == null)
                        list.add(v)
                }
                prevHand = list
            } else if (nextHand == null) {
                val list = mutableListOf<Int>()
                for (v in 7 until 15) {
                    if (!myHand.contains(v) && !prevHand.contains(v) && !info.nextHand.colorNotExists.contains(i) && info.outOfPlayByColor[i]!!.firstOrNull { c -> c.value == v } == null)
                        list.add(v)
                }
                nextHand = list
            }
            val cr = ColorRasklad().also {
                it.coatColor = i
                it.probability = 1.0
                it.myHand = myHand
                it.nextHand = nextHand!!
                it.prevHand = prevHand
            }
            byColor[i] = cr
        }
    }

    val removed: List<Move>
        get() = removedStack.toList()

    private val removedStack = ArrayDeque<Move>()

    fun remove(move: Move, cardsAlreadyRemoved: Boolean = false) {
        if (!cardsAlreadyRemoved) {
            for (color in byColor.keys) {
                val cmove = Helper.getColorMove(move.myMove, move.prevMove, move.nextMove, color)
                byColor[color]!!.remove(cmove)
            }
        }
        removedStack.push(move)
    }

    fun removeCard(card: Card, hand: Int): Boolean {
        return when (hand) {
            0 -> byColor[card.coatColor]!!.myHand.remove(card.value)
            -1 -> byColor[card.coatColor]!!.prevHand.remove(card.value)
            1 -> byColor[card.coatColor]!!.nextHand.remove(card.value)
            else -> throw Exception("Неверно указана рука!")
        }
    }

    fun addCard(card: Card, hand: Int) {
        val list: MutableList<Int> = when (hand) {
            0 -> byColor[card.coatColor]!!.myHand
            -1 -> byColor[card.coatColor]!!.prevHand
            1 -> byColor[card.coatColor]!!.nextHand
            else -> throw Exception("Неверно указана рука!")
        }
        var pos = 0
        while (pos < list.size) {
            if (list[pos] > card.value)
                break
            pos++
        }
        list.add(pos, card.value)
    }

    fun popLast(cardsAlreadyRemoved: Boolean = false): Move {
        if (!cardsAlreadyRemoved) {
            for (r in byColor.values) {
                r.popLast()
            }
        }
        return removedStack.pop()
    }

    fun restore() {
        for (r in byColor.values) {
            r.restore()
        }
    }

    private fun checkColorInHand(hand: Int, color: Int): Boolean {
        val list: List<Int> = when (hand) {
            -1 -> byColor[color]!!.prevHand
            0 -> byColor[color]!!.myHand
            1 -> byColor[color]!!.nextHand
            else -> throw Exception("Неверно задана рука!")
        }
        for (i in list.indices) {
            if (list[i] > 0)
                return true
        }
        return false
    }

    /**
     * Получаем карты на руке
     * @param hand рука
     * @param distinct если true - получаем только те, между которыми есть карты на других руках
     */
    fun getCardsInHand(hand: Int, distinct: Boolean, color: Int? = null, maxCard: Card? = null): MutableList<Card> {
        val allCardInHand = mutableListOf<Card>()
        for (c in 0 until 4) {
            if (color != null && color != c)
                continue
            val cr = byColor[c]!!
            var list: MutableList<Int> = mutableListOf()
            var others: MutableList<Int>? = null
            if (hand == -1) {
                list = cr.prevHand
                if (distinct) {
                    others = cr.myHand.toMutableList()
                    others.addAll(cr.nextHand)
                }
            } else if (hand == 0) {
                list = cr.myHand
                if (distinct) {
                    others = cr.prevHand.toMutableList()
                    others.addAll(cr.nextHand)
                }
            } else if (hand == 1) {
                list = cr.nextHand
                if (distinct) {
                    others = cr.prevHand.toMutableList()
                    others.addAll(cr.myHand)
                }
            }
            if (distinct) {
                if (maxCard != null && maxCard.coatColor == c)
                    others!!.add(maxCard.value)
                list = list.sorted().toMutableList()
            }
            var v0 = 0
            for (v in list) {
                if (v > 0) {
                    var skip = false
                    if (distinct && others != null && v0 > 0) {
                        skip = true
                        for (i in v0 + 1 until v) {
                            if (others.contains(i)) {
                                skip = false
                                break
                            }
                        }
                    }
                    if (!skip) {
                        allCardInHand.add(Card(value = v, coatColor = c))
                        v0 = v
                    }
                }
            }
        }
        return allCardInHand
    }

    fun getAllowedMoves(currentMove: Move, trump: Int, distict: Boolean): MutableList<Card> {
        //region Определяем цвет и руку, с которой надо ходить
        var color: Int? = null
        var hand = 0
        if (currentMove.firstMove == null) {
            hand = when (currentMove.firstMovePerformer) {
                -1 -> -1
                0 -> 0
                1 -> 1
                else -> 0
            }
        } else if (currentMove.secondMove == null) {
            color = currentMove.firstMove!!.coatColor
            hand = when (currentMove.firstMovePerformer) {
                -1 -> 0
                0 -> 1
                1 -> -1
                else -> 0
            }
        } else if (currentMove.thirdMove == null) {
            color = currentMove.firstMove!!.coatColor
            hand = when (currentMove.firstMovePerformer) {
                -1 -> 1
                0 -> -1
                1 -> 0
                else -> 0
            }
        } else
            throw Exception("Все ходы использованы!")
        //endregion

        val maxCard: Card?

        if (color != null) {
            if (!checkColorInHand(hand, color)) {
                color = trump
                if (trump < 0 || trump > 3 || !checkColorInHand(hand, trump))
                    color = null
            }
        }
        maxCard = if (color == null) null else currentMove.getMaxCard(trump)
        return getCardsInHand(hand, distict, color, maxCard)
    }
}
