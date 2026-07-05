package com.an0obIs.pref.ai

import com.an0obIs.pref.model.Card
import com.an0obIs.pref.model.Game
import com.an0obIs.pref.model.GameType

object Helper {

    fun getTaker(move: Move, trump: Int = -1): Int {
        return getTaker(move.myMove, move.prevMove, move.nextMove, move.playColor, trump)
    }

    fun getTaker(myMove: Card?, prevMove: Card?, nextMove: Card?, playColor: Int, trump: Int = -1): Int {
        val myVal = if (myMove == null) 0 else if (myMove.coatColor == trump) myMove.value + 10 else if (myMove.coatColor == playColor) myMove.value else 0
        val prevVal = if (prevMove == null) 0 else if (prevMove.coatColor == trump) prevMove.value + 10 else if (prevMove.coatColor == playColor) prevMove.value else 0
        val nextVal = if (nextMove == null) 0 else if (nextMove.coatColor == trump) nextMove.value + 10 else if (nextMove.coatColor == playColor) nextMove.value else 0
        if (myVal > prevVal && myVal > nextVal)
            return 0
        if (prevVal > myVal && prevVal > nextVal)
            return -1
        if (nextVal > myVal && nextVal > prevVal)
            return 1
        throw Exception("Не может быть одинаковых карт!")
    }

    fun getMaxCard(myMove: Card?, prevMove: Card?, nextMove: Card?, playColor: Int, trump: Int = -1): Card? {
        val myVal = if (myMove == null) 0 else if (myMove.coatColor == trump) myMove.value + 10 else if (myMove.coatColor == playColor) myMove.value else 0
        val prevVal = if (prevMove == null) 0 else if (prevMove.coatColor == trump) prevMove.value + 10 else if (prevMove.coatColor == playColor) prevMove.value else 0
        val nextVal = if (nextMove == null) 0 else if (nextMove.coatColor == trump) nextMove.value + 10 else if (nextMove.coatColor == playColor) nextMove.value else 0
        if (myVal > prevVal && myVal > nextVal)
            return myMove
        if (prevVal > myVal && prevVal > nextVal)
            return prevMove
        if (nextVal > myVal && nextVal > prevVal)
            return nextMove
        return null
    }

    fun getColorMove(move: Move, color: Int): ColoredMove {
        return getColorMove(move.myMove, move.prevMove, move.nextMove, color)
    }

    fun getColorMove(myMove: Card?, prevMove: Card?, nextMove: Card?, color: Int): ColoredMove {
        val move = ColoredMove()
        move.myMove = if (myMove == null || myMove.coatColor != color) 0 else myMove.value
        move.prevMove = if (prevMove == null || prevMove.coatColor != color) 0 else prevMove.value
        move.nextMove = if (nextMove == null || nextMove.coatColor != color) 0 else nextMove.value
        return move
    }

    private fun getMiserCoatTakes(info: AIInfo, color: Int): Double {
        val est = MiserEstimation()
        est.cardsLeft = 10
        est.contractor = 0
        est.isHidden = true
        est.trump = -1
        est.turn = 0
        val myCards = info.myHand.visibleHand!!.cardByCoat[color]!!.map { it.value }
        est.calcAllRasklads(color, ColorRaskladEnumerator(myCards, listOf(), color, 10, 10))
        return est.colors[color]!!.takes
    }

    private class MiserIntegralResult {
        var integral: Double = 0.0
        var notNeeded: Boolean = false
        var unreal: Boolean = false
    }

    private fun getMiserIntegral(cards: List<Card>, firstCard: Card, secondCard: Card): MiserIntegralResult {
        val result = MiserIntegralResult()
        val est = MiserEstimation()
        est.cardsLeft = 10
        est.contractor = 0
        est.isHidden = true
        est.trump = -1
        est.turn = 0
        for (c in 0 until 4) {
            var myCards = cards.filter { it.coatColor == c }.map { it.value }.sorted().toMutableList()
            val outCards = mutableListOf<Int>()
            est.calcAllRasklads(c, ColorRaskladEnumerator(myCards, outCards, c, 10, 10))
            val takes = est.colors[c]!!.takes
            if (firstCard.coatColor == c || secondCard.coatColor == c) {
                if (firstCard.coatColor == c)
                    outCards.add(firstCard.value)
                if (secondCard.coatColor == c)
                    outCards.add(secondCard.value)
                myCards = myCards.filter { v -> (v != firstCard.value || firstCard.coatColor != c) && (v != secondCard.value || secondCard.coatColor != c) }.toMutableList()
                est.calcAllRasklads(c, ColorRaskladEnumerator(myCards, outCards, c, 10, 10))
                val outTakes = est.colors[c]!!.takes
                if (takes <= outTakes) {
                    result.notNeeded = true
                    if (takes < outTakes)
                        result.unreal = true
                    else if (outCards.size == 2) {
                        // Попробуем скинуть только старшую
                        val sortedOut = outCards.sorted().toMutableList()
                        myCards.add(sortedOut[0])
                        est.calcAllRasklads(c, ColorRaskladEnumerator(myCards, sortedOut, c, 10, 10))
                        val oneTakes = est.colors[c]!!.takes
                        if (oneTakes <= outTakes)
                            result.notNeeded = true
                    }
                }
            }
        }
        result.integral = est.getIntegral()
        return result
    }

    fun getPotentialDiscards(info: AIInfo, game: Game): MutableList<PotentialDiscard>? {
        if (game.currentGameType != GameType.Miser)
            return null
        val cards = game.deal.hands[game.playerInTurn].cards.sortedBy { it.id }
        val list = mutableListOf<PotentialDiscard>()
        var firstCard: Card
        var secondCard: Card
        for (i in 0 until 11) {
            firstCard = cards[i]
            if (info.myHand.colorNotExists.contains(firstCard.coatColor))
                continue
            for (j in i + 1 until 12) {
                secondCard = cards[j]
                if (info.myHand.colorNotExists.contains(secondCard.coatColor))
                    continue

                val discard = PotentialDiscard().also {
                    it.firstCard = firstCard
                    it.secondCard = secondCard
                    it.probability = 1.0
                }
                val res = getMiserIntegral(cards, firstCard, secondCard)
                if (res.unreal)
                    continue
                if (res.integral < 0) {
                    discard.probability = 1 / (res.integral * res.integral)
                    if (res.notNeeded)
                        discard.probability /= 10000
                } else {
                    // Если есть расклад при котором мы гарантированно не берём взяток...
                    discard.probability = 1.0
                    list.clear()
                    list.add(discard)
                    // Возвращаем его
                    return list
                }

                list.add(discard)
            }
        }

        if (AiDebug.enabled)
            logDiscards(list)

        return list
    }

    fun logDiscards(discards: List<PotentialDiscard>) {
        var res = ""
        val sum = discards.sumOf { it.probability }
        for (discard in discards.sortedByDescending { it.probability }) {
            res += "${discard.firstCard} ${discard.secondCard} ${discard.probability} ${discard.probability * 100.0 / sum}%  hash=${discard.hash ?: "?"}\r\n"
        }
        val ss = res.replace('\r', ' ').split('\n')
        for (s in ss) {
            AiDebug.log(s)
        }
    }

    fun getCards(cards: List<Card>): String {
        var res = ""
        for (card in cards.sortedBy { it.coatColor * 10 + it.value }) {
            res += "%4s".format(card.toString())
        }
        return res
    }

    fun getTable(moves: List<Move>, rasklad: Rasklad, contractor: Int): String {
        var res = ""
        val extrParams = MiserOpen().createExtremum(moves.first().firstMovePerformer, contractor)
        for (move in moves) {
            rasklad.remove(move)
            val prevHand = getCards(rasklad.getCardsInHand(-1, false))
            val myHand = getCards(rasklad.getCardsInHand(0, false))
            val nextHand = getCards(rasklad.getCardsInHand(1, false))
            var firstHand = ""
            var secondHand = ""
            var thirdHand = ""
            when (move.firstMove!!.id) {
                move.myMove?.id -> firstHand = myHand
                move.prevMove?.id -> firstHand = prevHand
                move.nextMove?.id -> firstHand = nextHand
            }
            when (move.secondMove!!.id) {
                move.myMove?.id -> secondHand = myHand
                move.prevMove?.id -> secondHand = prevHand
                move.nextMove?.id -> secondHand = nextHand
            }
            when (move.thirdMove!!.id) {
                move.myMove?.id -> thirdHand = myHand
                move.prevMove?.id -> thirdHand = prevHand
                move.nextMove?.id -> thirdHand = nextHand
            }

            res += "%10.4f = %s%3s %s%3s %s%3s 1 = %20s, 2 = %20s, 3 = %20s".format(
                move.estimation,
                if (extrParams.firstMaximizing) '+' else '-', move.firstMove.toString(),
                if (extrParams.secondMaximizing) '+' else '-', move.secondMove.toString(),
                if (extrParams.thirdMaximizing) '+' else '-', move.thirdMove.toString(),
                firstHand, secondHand, thirdHand
            )
            res += System.lineSeparator()
            rasklad.popLast()
        }

        return res
    }

    fun logRasklad(rasklad: Rasklad) {
        val ss = getRasklad(rasklad).replace('\r', ' ').split('\n')
        for (s in ss) {
            AiDebug.log(s)
        }
    }

    fun getRasklad(rasklad: Rasklad): String {
        var res = ""
        val prevHand0 = getCards(rasklad.getCardsInHand(-1, false, 0))
        val myHand0 = getCards(rasklad.getCardsInHand(0, false, 0))
        val nextHand0 = getCards(rasklad.getCardsInHand(1, false, 0))

        val prevHand1 = getCards(rasklad.getCardsInHand(-1, false, 1))
        val myHand1 = getCards(rasklad.getCardsInHand(0, false, 1))
        val nextHand1 = getCards(rasklad.getCardsInHand(1, false, 1))

        val prevHand2 = getCards(rasklad.getCardsInHand(-1, false, 2))
        val myHand2 = getCards(rasklad.getCardsInHand(0, false, 2))
        val nextHand2 = getCards(rasklad.getCardsInHand(1, false, 2))

        val prevHand3 = getCards(rasklad.getCardsInHand(-1, false, 3))
        val myHand3 = getCards(rasklad.getCardsInHand(0, false, 3))
        val nextHand3 = getCards(rasklad.getCardsInHand(1, false, 3))

        res += "-1 = %20s %20s %20s %120s\r\n".format(prevHand0, prevHand1, prevHand2, prevHand3)
        res += " 0 = %20s %20s %20s %20s \r\n".format(myHand0, myHand1, myHand2, myHand3)
        res += " 1 = %20s %20s %20s %20s \r\n".format(nextHand0, nextHand1, nextHand2, nextHand3)
        res += System.lineSeparator()
        return res
    }
}
