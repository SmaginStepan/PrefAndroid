package com.an0obIs.pref.ai

import com.an0obIs.pref.model.Card
import com.an0obIs.pref.model.Game
import java.util.ArrayDeque
import kotlin.random.Random

class GameSituation(game: Game, info: AIInfo, gamePlay: OpenPlay) {

    var potentialDiscards: List<PotentialDiscard>? = null

    private var move: Move

    var rasklad: Rasklad

    var contractor: Int = 0

    private val gamePlay: OpenPlay

    private val contractorColorNotExists: MutableList<Int>

    private var playerToMove: Int

    private val alreadyTaken: Taken

    private val trump: Int

    private val rnd = Random.Default

    var calculated = 0
    var skipped = 0
    var maxwidth = 0
    private var cardsLeft = 0

    private fun nextPlayer() {
        playerToMove++
        if (playerToMove > 1)
            playerToMove = -1
    }

    private fun prevPlayer() {
        playerToMove--
        if (playerToMove < -1)
            playerToMove = 1
    }

    private val cardMoves: ArrayDeque<Card>

    var maxDepth: Int = 0

    private fun moveCard(card: Card) {
        if (move.firstMove == null) {
            move.firstMove = card
            rasklad.removeCard(card, playerToMove)
            cardMoves.push(card)
            nextPlayer()
        } else if (move.secondMove == null) {
            move.secondMove = card
            rasklad.removeCard(card, playerToMove)
            cardMoves.push(card)
            nextPlayer()
        } else if (move.thirdMove == null) {
            move.thirdMove = card
            rasklad.removeCard(card, playerToMove)
            cardMoves.push(card)
            // Текущий круг закончен - переходим к следующему...
            maxDepth--
            cardsLeft--
            rasklad.remove(move, true)
            val next = Helper.getTaker(move, trump)
            alreadyTaken.addTake(next)
            move = Move()
            move.firstMovePerformer = next
            playerToMove = next
        }
    }

    private fun undoCard() {
        val lastCard = cardMoves.pop()
        if (move.thirdMove != null) {
            throw Exception("Это невозможно, так как при заполнении хода мы сразу переходим к следующему!")
        } else if (move.secondMove != null) {
            move.secondMove = null
            prevPlayer()
            rasklad.addCard(lastCard, playerToMove)
        } else if (move.firstMove != null) {
            move.firstMove = null
            prevPlayer()
            rasklad.addCard(lastCard, playerToMove)
        } else {
            maxDepth++
            cardsLeft++
            // Вытаскиваем последний ход
            move = rasklad.popLast(true)
            alreadyTaken.removeTake(move.getTaker(trump))
            move.thirdMove = null
            playerToMove = move.firstMovePerformer
            prevPlayer()
            rasklad.addCard(lastCard, playerToMove)
        }
    }

    val isMaximizing: Boolean
        get() = gamePlay.isMaximizing(playerToMove, contractor)

    private fun calcEstimation(): Double {
        val est = gamePlay.createEstimation()
        est.contractor = contractor
        est.calcRasklad(rasklad)
        est.turn = playerToMove
        est.myTakes = alreadyTaken.myTakes
        est.nextTakes = alreadyTaken.nextTakes
        est.prevTakes = alreadyTaken.prevTakes
        est.trump = trump
        est.cardsLeft = cardsLeft
        return est.getIntegral()
    }

    fun getDistinctMoves(): List<Card> {
        return rasklad.getAllowedMoves(move, trump, true)
    }

    fun sortByEstimation(cards: List<Card>, descending: Boolean): List<Card> {
        return if (descending)
            cards.sortedByDescending { it.estimation }
        else
            cards.sortedBy { it.estimation }
    }

    fun getEstimation(min: Double, max: Double, firstLevel: Boolean, preEstimate: Boolean = false, width: Int = 0): EstimationWithCard? {
        val cards = rasklad.getAllowedMoves(move, trump, true)
        val isMaximizing = isMaximizing
        if (cards.isEmpty())
            throw Exception("Что-то у нас карт не хватает... не к добру")

        val res = EstimationWithCard()
        var stop = false
        if (move.secondMove != null && move.thirdMove == null) {
            // Это третья рука - обязательное условие для оценки!
            if (maxDepth == 0 || preEstimate) {
                // Усё... время вышло, пора оценивать
                stop = true
            }
        }

        res.estimation = if (isMaximizing) -Double.MAX_VALUE else Double.MAX_VALUE
        var bestCards: MutableList<Card>? = null
        if (firstLevel)
            bestCards = mutableListOf()

        var newMax = max
        var newMin = min

        for (card in cards) {
            var est: Double
            if (stop) {
                calculated++
                moveCard(card)
                est = calcEstimation()
                undoCard()
                if (maxwidth < width * cards.size)
                    maxwidth = width * cards.size
            } else {
                moveCard(card)
                val ewc = getEstimation(-Double.MAX_VALUE, Double.MAX_VALUE, false, preEstimate, width * cards.size)
                undoCard()
                if (ewc == null) {
                    if (preEstimate || calculated == 0)
                        throw Exception("Это предварительный расчёт!!!")
                    continue
                }
                est = ewc.estimation
                if (!preEstimate) {
                    if ((est > max && isMaximizing) || (est < min && !isMaximizing)) {
                        skipped++
                        if (firstLevel || calculated == 0)
                            throw Exception("NULL на первом уровне WTF!")
                        return null
                    }
                }
            }

            if (isMaximizing && est > res.estimation) {
                if (!preEstimate && est > newMin)
                    newMin = est
                res.estimation = est
                if (firstLevel) {
                    bestCards!!.clear()
                    bestCards.add(card)
                }
            } else if (!isMaximizing && est < res.estimation) {
                if (!preEstimate && est < newMax)
                    newMax = est
                res.estimation = est
                if (firstLevel) {
                    bestCards!!.clear()
                    bestCards.add(card)
                }
            } else if (firstLevel && est == res.estimation) {
                bestCards!!.add(card)
            }
        }

        if (firstLevel) {
            res.bestCard = bestCards!![rnd.nextInt(bestCards.size)]
            // Карты надо вернуть только для первого уровня
        }
        if (res.estimation == -Double.MAX_VALUE || res.estimation == Double.MAX_VALUE) {
            if (preEstimate || firstLevel || calculated == 0)
                throw Exception("Не могли пропустить вариант!")
            return null // Мы пропустили все варианты...
        }
        return res
    }

    init {
        this.gamePlay = gamePlay

        contractor = when {
            game.playerInTurn == game.contractor -> 0
            game.contractor == game.getPrevPlayer() -> -1
            game.contractor == game.getNextPlayer() -> 1
            else -> 0
        }

        potentialDiscards = gamePlay.getPotentialDiscard(info)

        trump = game.trump

        rasklad = Rasklad()
        rasklad.fromPlay(info)

        alreadyTaken = Taken()

        contractorColorNotExists = info.myHand.colorNotExists.toMutableList()

        move = Move()
        move.prevMove = info.prevHand.currentMove
        move.myMove = null
        move.nextMove = info.nextHand.currentMove
        move.firstMovePerformer = 0
        if (move.prevMove != null)
            move.firstMovePerformer = -1
        if (move.nextMove != null)
            move.firstMovePerformer = 1

        playerToMove = 0
        cardsLeft = 10 - game.deal.totalTaken
        maxDepth = 9 - game.deal.totalTaken
        if (maxDepth > 4)
            maxDepth = 2

        cardMoves = ArrayDeque()
    }
}
