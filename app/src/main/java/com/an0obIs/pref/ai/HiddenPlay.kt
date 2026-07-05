package com.an0obIs.pref.ai

import com.an0obIs.pref.model.Card
import com.an0obIs.pref.model.Game
import com.an0obIs.pref.model.GameType
import kotlin.random.Random

abstract class HiddenPlay {

    private fun getNextTurn(game: Game): Int? {
        if (game.deal.inPlay.containsKey(-1) && game.deal.prikup.cards.isNotEmpty()) {
            // Первый заход с прикупом - ход остаётся у первого игрока
            val myNum = game.playerInTurn
            val prevNum = game.getPrevPlayer()
            val nextNum = game.getNextPlayer()
            val num = game.getFirstPlayer()
            when (num) {
                myNum -> return 0
                prevNum -> return -1
                nextNum -> return 1
            }
        }
        return null
    }

    private fun getResult(
        myMove: Card?, nextMove: Card?, prevMove: Card?, takes: Estimation, playColor: Int, trump: Int,
        rasklad: ColorRasklad?, aRasklad: Rasklad?, nextTurn: Int?, taken: List<AIInfo.Take>,
        firstMovePerformer: Int, contractor: Int?
    ): Double {
        takes.move = Move().also {
            it.myMove = myMove
            it.nextMove = nextMove
            it.prevMove = prevMove
            it.firstMovePerformer = firstMovePerformer
        }

        takes.playHistory = PlayHistory(taken, takes.move!!)

        takes.trump = trump
        takes.contractor = contractor
        takes.cardsLeft = 9 - taken.size

        if (aRasklad != null) {
            aRasklad.remove(takes.move!!)

            takes.calcRasklad(aRasklad)

            aRasklad.restore()
        } else {
            val move = Helper.getColorMove(takes.move!!, playColor)

            rasklad!!.remove(move)

            takes.calcRasklad(rasklad)

            rasklad.restore()
        }

        takes.turn = Helper.getTaker(myMove, prevMove, nextMove, playColor, trump)
        takes.prevTakes = 0.0
        takes.nextTakes = 0.0
        takes.myTakes = 0.0
        when (takes.turn) {
            -1 -> takes.prevTakes = 1.0
            1 -> takes.nextTakes = 1.0
            0 -> takes.myTakes = 1.0
        }

        if (nextTurn != null) {
            takes.turn = nextTurn
        }

        return takes.getIntegral()
    }

    private fun getEnumerator(info: AIInfo, color: Int, moveCard: Card?, game: Game): ColorRaskladEnumerator {
        val outCards = info.outOfPlayByColor[color]!!.map { it.value }
        val myCards = info.myHand.visibleHand!!.cardByCoat[color]!!.map { it.value }

        var prevMax = info.prevHand.cardsCount
        var nextMax = info.nextHand.cardsCount

        for (i in 0 until 4) {
            if (i == color)
                continue
            if (info.nextHand.colorNotExists.contains(i)) {
                // На след. руке нет: значит все оставшиеся в игре карты на предыдущей руке или в прикупе
                var atHand = 8 - info.outOfPlayByColor[i]!!.size - info.myHand.visibleHand!!.cardByCoat[i]!!.size
                atHand -= game.deal.prikup.cards.size
                if (atHand > 0)
                    prevMax -= atHand
            }
            if (info.prevHand.colorNotExists.contains(i)) {
                var atHand = 8 - info.outOfPlayByColor[i]!!.size - info.myHand.visibleHand!!.cardByCoat[i]!!.size
                atHand -= game.deal.prikup.cards.size
                if (atHand > 0)
                    nextMax -= atHand
            }
        }

        if (info.nextHand.colorNotExists.contains(color))
            nextMax = 0
        if (info.prevHand.colorNotExists.contains(color))
            prevMax = 0
        val rEnumerator = ColorRaskladEnumerator(myCards, outCards, color, nextMax, prevMax)
        info.myHand.currentMove = moveCard
        return rEnumerator
    }

    abstract fun createEstimation(): Estimation

    fun play(info: AIInfo, game: Game, allowedMoves: List<Card>): Card {
        if (allowedMoves.size == 1) {
            // Думать нечего
            return allowedMoves[0]
        }

        //region Инициализируем переменные
        val myFirstMove = game.firstMovePerformer < 0
        val prikupInPlay = game.deal.inPlay.containsKey(-1)
        val myLastTurn = game.deal.inPlay.size == 2 && !prikupInPlay || game.deal.inPlay.size == 3 && prikupInPlay
        val calcAll = info.outOfPlay.size >= 5
        val nextTurn = getNextTurn(game)

        var color = game.deal.inPlayCoatColor

        val estimation = LinkedHashMap<Card, Double>()
        val turns = mutableListOf<Card>()

        var rEnumerator: ColorRaskladEnumerator?
        var aEnumerator: RaskladEnumerator? = null
        var takes: Estimation
        val allowedMovesByColor = LinkedHashMap<Int, MutableList<Card>>()
        for (card in allowedMoves) {
            allowedMovesByColor.getOrPut(card.coatColor) { mutableListOf() }.add(card)
        }

        val taken = info.outOfPlay.toList()

        var unknownPrikup = game.deal.prikup.cards.size - info.knownPrikup.size
        if (unknownPrikup < 0)
            unknownPrikup = 0

        var contractor: Int? = null
        if (game.currentGameType != GameType.Raspasy) {
            contractor = when (game.contractor) {
                game.getPrevPlayer() -> -1
                game.playerInTurn -> 0
                game.getNextPlayer() -> 1
                else -> null
            }
        }
        //endregion

        for (coat in allowedMovesByColor.keys) {
            val moves = allowedMovesByColor[coat]!!
            if (myFirstMove)
                color = coat

            takes = createEstimation()

            if (!calcAll) {
                //region Рассчитываем для всех раскладов, кроме расклада по цвету захода и цвету выбранной карты
                for (ci in 0 until 4) {
                    if (ci == color || ci == coat)
                        continue
                    rEnumerator = getEnumerator(info, ci, null, game)
                    takes.calcAllRasklads(ci, rEnumerator)
                }
                //endregion
            }

            for (card in moves) {
                var integral = 0.0
                var nextMove: Card?
                var prevMove: Card?
                var rCnt = 0.0

                if (!calcAll) {
                    //region Рассчитываем расклады для цвета сбрасываемой карты
                    if (color != coat) {
                        rEnumerator = getEnumerator(info, coat, card, game)
                        takes.calcAllRasklads(coat, rEnumerator)
                    }
                    //endregion
                }

                val myMove: Card = card
                info.myHand.currentMove = card

                //region Создаём перечисление раскладов
                var rasklad: ColorRasklad? = null
                var aRasklad: Rasklad? = null
                var colorEnumerator: ColorRaskladEnumerator? = null
                if (!calcAll) {
                    colorEnumerator = getEnumerator(info, color, card, game)
                    rasklad = colorEnumerator.getNext()
                } else {
                    val myCards = game.deal.hands[game.playerInTurn].cards.toList()
                    val outCards = mutableListOf<Card>()
                    for (i in 0 until 4) {
                        for (outCard in info.outOfPlayByColor[i]!!)
                            outCards.add(outCard)
                    }
                    aEnumerator = RaskladEnumerator(
                        myCards, outCards, info.nextHand.cardsCount, info.prevHand.cardsCount,
                        info.nextHand.colorNotExists.toList(), info.prevHand.colorNotExists.toList(),
                        unknownPrikup, contractor
                    )
                    aRasklad = aEnumerator.getNext()
                }

                if (rasklad == null && aRasklad == null)
                    throw Exception("Расклад не найден!")
                //endregion

                //region Перебираем все расклады
                while (rasklad != null || aRasklad != null) {
                    if (aRasklad != null) {
                        rasklad = aRasklad.byColor[color]
                        colorEnumerator = null
                    }
                    var res = 0.0
                    if (myLastTurn) {
                        //region Мы ходим последние
                        nextMove = game.deal.inPlay[game.getNextPlayer()]
                        prevMove = game.deal.inPlay[game.getPrevPlayer()]
                        res = getResult(myMove, nextMove, prevMove, takes, color, game.trump, rasklad, aRasklad, nextTurn, taken, game.firstMovePerformer, contractor)
                        //endregion
                    } else if (myFirstMove) {
                        //region Мы ходим первые
                        var worstRes = Double.MAX_VALUE

                        if (rasklad!!.nextHand.isEmpty()) {
                            nextMove = null
                            if (rasklad.prevHand.isEmpty()) {
                                res = -100000000000000.0 // Никогда не ходим когда у противника ничего нет!
                                worstRes = res
                            } else {
                                // Перебираем все возможные ходы второго оппонента:
                                for (prev in rasklad.prevHand.toList()) {
                                    prevMove = Card(value = prev, coatColor = color)
                                    val cres = getResult(myMove, nextMove, prevMove, takes, color, game.trump, rasklad, aRasklad, nextTurn, taken, game.firstMovePerformer, contractor)
                                    res += cres
                                    if (worstRes > cres)
                                        worstRes = cres
                                }
                            }
                        } else {
                            // Перебираем все возможные ходы первого оппонента:
                            for (next in rasklad.nextHand.toList()) {
                                nextMove = Card(value = next, coatColor = color)
                                if (rasklad.prevHand.isEmpty()) {
                                    prevMove = null
                                    val cres = getResult(myMove, nextMove, prevMove, takes, color, game.trump, rasklad, aRasklad, nextTurn, taken, game.firstMovePerformer, contractor)
                                    res += cres
                                    if (worstRes > cres)
                                        worstRes = cres
                                } else {
                                    // Перебираем все возможные ходы второго оппонента:
                                    for (prev in rasklad.prevHand.toList()) {
                                        prevMove = Card(value = prev, coatColor = color)
                                        val cres = getResult(myMove, nextMove, prevMove, takes, color, game.trump, rasklad, aRasklad, nextTurn, taken, game.firstMovePerformer, contractor)
                                        res += cres
                                        if (worstRes > cres)
                                            worstRes = cres
                                    }
                                }
                            }
                        }
                        // За результат считаем худший для нас ответ противника.
                        res = worstRes
                        //endregion
                    } else {
                        //region Мы ходим вторые
                        prevMove = game.deal.inPlay[game.getPrevPlayer()]
                        if (rasklad!!.nextHand.isEmpty()) {
                            nextMove = null
                            res = getResult(myMove, nextMove, prevMove, takes, color, game.trump, rasklad, aRasklad, nextTurn, taken, game.firstMovePerformer, contractor)
                        } else {
                            var worstRes = Double.MAX_VALUE
                            // Перебираем все возможные ходы второго оппонента:
                            for (next in rasklad.nextHand.toList()) {
                                nextMove = Card(value = next, coatColor = color)
                                val cres = getResult(myMove, nextMove, prevMove, takes, color, game.trump, rasklad, aRasklad, nextTurn, taken, game.firstMovePerformer, contractor)
                                res += cres
                                if (worstRes > cres)
                                    worstRes = cres
                            }
                            res = worstRes
                        }
                        //endregion
                    }

                    if (aRasklad != null) {
                        rCnt += aRasklad.probability
                        aRasklad = aEnumerator!!.getNext()
                        if (aRasklad == null)
                            rasklad = null
                    } else if (rasklad != null && colorEnumerator != null) {
                        rCnt += rasklad.probability
                        rasklad = colorEnumerator.getNext()
                    }
                    integral += res
                }
                //endregion

                integral /= rCnt

                estimation[card] = integral
            }
        }

        val list = estimation.entries.sortedByDescending { it.value }
        val min = list.first().value
        for (pair in list) {
            if (pair.value == min)
                turns.add(pair.key)
        }
        val rnd = Random.Default
        return turns[rnd.nextInt(turns.size)]
    }
}
