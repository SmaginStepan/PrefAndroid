package com.an0obIs.pref.ai

import com.an0obIs.pref.model.Card
import com.an0obIs.pref.model.Game
import com.an0obIs.pref.model.GamePhase
import com.an0obIs.pref.model.GameType
import com.an0obIs.pref.model.Hand
import kotlinx.serialization.Serializable

@Serializable
class AIInfo {

    @Serializable
    class Take {
        var takenBy: Int = 0
        var firstMovePerformer: Int = 0
        var prevMove: Card? = null
        var myMove: Card? = null
        var nextMove: Card? = null
        var prikupMove: Card? = null

        val playColor: Int
            get() = when (firstMovePerformer) {
                0 -> myMove!!.coatColor
                1 -> nextMove!!.coatColor
                -1 -> prevMove!!.coatColor
                else -> throw Exception("Не определён заход!")
            }
    }

    @Serializable
    class GameInfo {
        var openedPrikup: Hand? = null
        var discardedCards: Hand? = null
        var gameType: GameType = GameType.Raspasy
        var contract: Int = 0
        var contractor: Int = 0
    }

    @Serializable
    class HandInfo {
        var isVister: Boolean = false
        var isContractor: Boolean = false
        var isMiserist: Boolean = false
        var isVisible: Boolean = false
        var visibleHand: Hand? = null
        var colorNotExists: MutableList<Int> = mutableListOf()
        var taken: Int = 0
        var currentMove: Card? = null
        var cardsCount: Int = 10
    }

    var firstMove: Int = 0

    val myFirstMove: Boolean
        get() = firstMove == 0

    var game: GameInfo = GameInfo()
    var outOfPlay: MutableList<Take> = mutableListOf()
    var outOfPlayByColor: MutableMap<Int, MutableList<Card>> = mutableMapOf()
    var myHand: HandInfo = HandInfo()
    var prevHand: HandInfo = HandInfo()
    var nextHand: HandInfo = HandInfo()
    var currentPrikup: Card? = null
    var knownPrikup: MutableList<Card> = mutableListOf()
    var potentialDiscard: MutableList<PotentialDiscard>? = null

    /** Rebuild transient per-suit indexes of nested hands after deserialization. */
    fun restoreAfterLoad() {
        myHand.visibleHand?.sort()
        prevHand.visibleHand?.sort()
        nextHand.visibleHand?.sort()
        game.openedPrikup?.sort()
        game.discardedCards?.sort()
    }

    private fun checkExists(player: Int, card: Card, trump: Int, playColor: Int, hand: HandInfo) {
        if (card.coatColor == playColor) {
            return
        }
        if (card.coatColor == trump) {
            if (!hand.colorNotExists.contains(playColor))
                hand.colorNotExists.add(playColor)
        } else if (trump in 0..3) {
            if (!hand.colorNotExists.contains(playColor))
                hand.colorNotExists.add(playColor)
            if (!hand.colorNotExists.contains(trump))
                hand.colorNotExists.add(trump)
        } else {
            if (!hand.colorNotExists.contains(playColor))
                hand.colorNotExists.add(playColor)
        }
    }

    private fun addOutOfPlayByColor(card: Card) {
        val list = outOfPlayByColor[card.coatColor]!!
        if (list.firstOrNull { it.coatColor == card.coatColor && it.value == card.value } == null)
            list.add(card)
    }

    private fun addKnownPrikup(prikupCard: Card) {
        if (knownPrikup.firstOrNull { it.coatColor == prikupCard.coatColor && it.value == prikupCard.value } == null)
            knownPrikup.add(prikupCard)
    }

    fun writeOutOfPlay(game: Game) {
        val myNum = game.playerInTurn
        val prevNum = game.getPrevPlayer()
        val nextNum = game.getNextPlayer()

        //region Записываем все ранее вышедшие из игры карты
        outOfPlayByColor = mutableMapOf()
        for (i in 0 until 4) {
            outOfPlayByColor[i] = mutableListOf()
        }
        for (prikupCard in knownPrikup) {
            addOutOfPlayByColor(prikupCard)
        }
        for (take in outOfPlay) {
            addOutOfPlayByColor(take.prevMove!!)
            addOutOfPlayByColor(take.myMove!!)
            addOutOfPlayByColor(take.nextMove!!)
            take.prikupMove?.let { addOutOfPlayByColor(it) }
        }
        //endregion

        if (game.phase == GamePhase.EndTurn) {
            //region Конец хода: записываем взятку
            val take = Take()
            when (game.firstMovePerformer) {
                prevNum -> take.firstMovePerformer = -1
                myNum -> take.firstMovePerformer = 0
                nextNum -> take.firstMovePerformer = 1
            }

            take.prevMove = game.deal.inPlay[prevNum]
            take.myMove = game.deal.inPlay[myNum]
            take.nextMove = game.deal.inPlay[nextNum]
            when (game.playerToTake) {
                prevNum -> {
                    take.takenBy = -1
                    prevHand.taken++
                }
                myNum -> {
                    take.takenBy = 0
                    myHand.taken++
                }
                nextNum -> {
                    take.takenBy = 1
                    nextHand.taken++
                }
            }
            if (game.deal.inPlay.containsKey(-1))
                take.prikupMove = game.deal.inPlay[-1]
            outOfPlay.add(take)

            firstMove = take.takenBy
            //endregion
        }

        //region Записываем карты, находящиеся в игре
        prevHand.currentMove = game.deal.inPlay[prevNum]
        nextHand.currentMove = game.deal.inPlay[nextNum]
        currentPrikup = game.deal.inPlay[-1]
        //endregion

        if (game.currentGameType != GameType.Raspasy && game.contractor == game.playerInTurn && game.deal.totalTaken == 0) {
            //region Мы знаем, что мы сбросили: записываем как вышедшие из игры карты и в известный прикуп
            var prikupCard = game.deal.prikup.cards[0]
            addKnownPrikup(prikupCard)
            addOutOfPlayByColor(prikupCard)
            prikupCard = game.deal.prikup.cards[1]
            addKnownPrikup(prikupCard)
            addOutOfPlayByColor(prikupCard)
            //endregion
        }

        for (h in game.deal.inPlay.keys) {
            //region Записываем карты на столе как вышедшие и проверяем ренонс
            val card = game.deal.inPlay[h]!!
            addOutOfPlayByColor(card)
            val hand: HandInfo = when (h) {
                prevNum -> prevHand
                myNum -> myHand
                nextNum -> nextHand
                else -> continue
            }
            checkExists(h, card, game.trump, game.deal.inPlayCoatColor, hand)
            //endregion
        }

        //region Обновляем карты рук
        myHand.visibleHand = game.deal.hands[myNum].clone()
        myHand.isVisible = true
        myHand.currentMove = null
        myHand.cardsCount = game.deal.hands[myNum].cards.size
        if (game.deal.hands[nextNum].isVisible && game.isVister.containsKey(nextNum)) {
            nextHand.isVisible = true
            nextHand.visibleHand = game.deal.hands[nextNum].clone()
        } else {
            nextHand.isVisible = false
            nextHand.visibleHand = null
        }
        nextHand.cardsCount = game.deal.hands[nextNum].cards.size
        if (game.deal.hands[prevNum].isVisible && game.isVister.containsKey(prevNum)) {
            prevHand.isVisible = true
            prevHand.visibleHand = game.deal.hands[prevNum].clone()
        } else {
            prevHand.isVisible = false
            prevHand.visibleHand = null
        }
        // NOTE: preserved from the original C# (it read hands[nextNum] here, not prevNum)
        prevHand.cardsCount = game.deal.hands[nextNum].cards.size
        //endregion

        //region Зафиксировать распределение мастей
        var updated = true
        while (updated) {
            updated = false

            //region Фиксируем, если карт масти не осталось в игре
            for (i in 0 until 4) {
                val inPlayCnt = 8 - myHand.visibleHand!!.cardByCoat[i]!!.size - outOfPlayByColor[i]!!.size
                val prevNotExist = prevHand.colorNotExists.contains(i)
                val nextNotExist = nextHand.colorNotExists.contains(i)
                if (inPlayCnt == 0) {
                    if (!nextNotExist) {
                        nextHand.colorNotExists.add(i)
                        if (nextHand.colorNotExists.size == 4 && nextHand.cardsCount > 0 && !(nextHand.currentMove != null && nextHand.cardsCount == 1))
                            throw Exception("Неверный расчёт вышедших взяток!")
                        updated = true
                    }
                    if (!prevNotExist) {
                        prevHand.colorNotExists.add(i)
                        if (prevHand.colorNotExists.size == 4 && prevHand.cardsCount > 0 && !(prevHand.currentMove != null && prevHand.cardsCount == 1))
                            throw Exception("Неверный расчёт вышедших взяток!")
                        updated = true
                    }
                    continue
                } else if (inPlayCnt > 0 && prevNotExist && nextNotExist) {
                    //region Добавляем в известный прикуп, и в вышедшие из игры карты
                    for (v in 7 until 15) {
                        if (myHand.visibleHand!!.cardByCoat[i]!!.firstOrNull { it.value == v } != null)
                            continue
                        if (outOfPlayByColor[i]!!.firstOrNull { it.value == v } != null)
                            continue
                        val card = Card(value = v, coatColor = i)
                        addKnownPrikup(card)
                        addOutOfPlayByColor(card)
                    }
                    updated = true
                    //endregion
                }
            }
            //endregion

            //region Фиксируем масти, строго разложившиеся по разным рукам
            var prevCnt = 0 // Кол-во карт, которые точно должны быть на пред. руке
            var nextCnt = 0 // Кол-во карт, которые точно должны быть на след. руке
            val nextMustHave = mutableListOf<Int>() // Масти, которые точно есть на след. руке
            val prevMustHave = mutableListOf<Int>() // Масти, которые точно есть на пред. руке
            for (i in 0 until 4) {
                val inPlayCnt = 8 - myHand.visibleHand!!.cardByCoat[i]!!.size - outOfPlayByColor[i]!!.size
                val prevNotExist = prevHand.colorNotExists.contains(i)
                val nextNotExist = nextHand.colorNotExists.contains(i)
                if (inPlayCnt > 0 && nextNotExist) {
                    // Если карты есть в игре, но их нет на след. руке, значит все они на пред. руке!
                    prevCnt += inPlayCnt
                    prevMustHave.add(i)
                } else if (inPlayCnt > 0 && prevNotExist) {
                    // Если карты есть в игре, но их нет на пред. руке, значит все они на след. руке!
                    nextCnt += inPlayCnt
                    nextMustHave.add(i)
                }
            }
            val notKnown = game.deal.prikup.cards.size - knownPrikup.size
            if (notKnown < 0)
                throw Exception("Нашелся прикуп (сброс), которого не было!")

            prevCnt -= notKnown
            nextCnt -= notKnown

            val prevFull = prevHand.cardsCount <= prevCnt // Пред. рука полна: количество карт, которые точно есть на ней равно её длине
            val nextFull = nextHand.cardsCount <= nextCnt // След. рука полна: количество карт, которые точно есть на ней равно её длине
            for (i in 0 until 4) {
                val prevNotExist = prevHand.colorNotExists.contains(i)
                val nextNotExist = nextHand.colorNotExists.contains(i)
                if (prevFull && !prevMustHave.contains(i) && !prevNotExist) {
                    // На предыдущей только те карты, которых нет на след. руке
                    prevHand.colorNotExists.add(i)
                    if (prevHand.colorNotExists.size == 4 && prevHand.cardsCount > 0 && !(prevHand.currentMove != null && prevHand.cardsCount == 1))
                        throw Exception("Неверный расчёт вышедших взяток!")
                    updated = true
                }
                if (nextFull && !nextMustHave.contains(i) && !nextNotExist) {
                    // На следующей только те карты, которых нет на пред. руке
                    nextHand.colorNotExists.add(i)
                    if (nextHand.colorNotExists.size == 4 && nextHand.cardsCount > 0 && !(nextHand.currentMove != null && nextHand.cardsCount == 1))
                        throw Exception("Неверный расчёт вышедших взяток!")
                    updated = true
                }
            }
            //endregion
        }
        //endregion
    }

    companion object {
        fun create(game: Game) {
            val ai = AIInfo()
            ai.game = GameInfo()
            ai.outOfPlay = mutableListOf()
            ai.outOfPlayByColor = mutableMapOf()
            ai.myHand = HandInfo()
            ai.knownPrikup = mutableListOf()

            ai.myHand.visibleHand = game.deal.hands[game.playerInTurn].clone()
            ai.myHand.isVisible = true

            ai.nextHand = HandInfo()
            ai.prevHand = HandInfo()
            when (game.calc.dealer) {
                game.getPrevPlayer() -> ai.firstMove = 0
                game.playerInTurn -> ai.firstMove = 1
                game.getNextPlayer() -> ai.firstMove = -1
            }
            game.aIs[game.playerInTurn] = ai
        }
    }
}
