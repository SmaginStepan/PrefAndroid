package com.an0obIs.pref.ai

import com.an0obIs.pref.model.Card
import com.an0obIs.pref.model.Game
import kotlin.random.Random

abstract class OpenPlay {

    abstract fun createExtremum(firstMovePerformer: Int, contractor: Int): Extremums

    abstract fun isMaximizing(player: Int, contractor: Int): Boolean

    abstract fun createEstimation(): Estimation

    open fun getPotentialDiscard(info: AIInfo): List<PotentialDiscard>? {
        return null
    }

    var iamSure = true

    private fun getRaskladHash(rasklad: Rasklad, discard: PotentialDiscard, hand: Int): String {
        val cards = rasklad.getCardsInHand(hand, false).map { it.id }.sorted()
        var id = discard.firstCard!!.id
        while (cards.contains(id)) {
            id--
        }
        var res = id.toString()
        id = discard.secondCard!!.id
        while (cards.contains(id)) {
            id--
        }
        res += ":$id"
        return res
    }

    fun play(info: AIInfo, game: Game, allowedMoves: List<Card>): Card? {
        if (allowedMoves.size == 1) {
            // Думать нечего
            return allowedMoves[0]
        }
        val rnd = Random.Default
        val situation = GameSituation(game, info, this)
        val distinctMoves = situation.getDistinctMoves()
        if (distinctMoves.isEmpty()) {
            // Думать всё равно нечего...
            return allowedMoves[rnd.nextInt(allowedMoves.size)]
        }
        // Ухх... придётся таки думать...
        val contractHand = situation.contractor
        var contractHandLength = 10 - game.deal.totalTaken
        if (game.deal.inPlay.containsKey(game.contractor))
            contractHandLength--

        AiDebug.log("----------------------------------")
        AiDebug.log("CONTRACT = $contractHand")
        if (AiDebug.enabled) Helper.logRasklad(situation.rasklad)

        val potentialDiscard = info.potentialDiscard
        if (potentialDiscard != null && potentialDiscard.isNotEmpty() && situation.rasklad.getCardsInHand(contractHand, false).size > contractHandLength) {
            //region Перебираем сброс: ищем те варианты, которые ловятся
            var catched = mutableListOf<PotentialDiscard>()
            val semanticProbability = mutableMapOf<String, Double>()
            var pSum = 0.0
            var pMax = 0.0
            for (discard in situation.potentialDiscards!!) {
                discard.hash = getRaskladHash(situation.rasklad, discard, contractHand)
                val firstRemoved = situation.rasklad.removeCard(discard.firstCard!!, contractHand)
                val secondRemoved = situation.rasklad.removeCard(discard.secondCard!!, contractHand)

                val est = situation.getEstimation(-Double.MAX_VALUE, Double.MAX_VALUE, true, true)!!.estimation
                if (est > 0) {
                    catched.add(discard)
                    pSum += discard.probability
                    if (pMax < discard.probability)
                        pMax = discard.probability
                    val hash = discard.hash!!
                    semanticProbability[hash] = (semanticProbability[hash] ?: 0.0) + discard.probability
                }

                if (firstRemoved)
                    situation.rasklad.addCard(discard.firstCard!!, contractHand)
                if (secondRemoved)
                    situation.rasklad.addCard(discard.secondCard!!, contractHand)
            }
            //endregion
            if (catched.isEmpty()) {
                //region Ничего не можем поймать :(
                AiDebug.log("----------------------------------")
                AiDebug.log("Ничего не можем поймать :(")
                return allowedMoves[rnd.nextInt(allowedMoves.size)]
                //endregion
            } else {
                // Исключаем маловероятные варианты
                val pMin = pMax / 5
                catched = catched.filter { it.probability > pMin }.toMutableList()
                //region Проверяем, можем ли мы поймать гарантированно (при любом сносе из тех, что мы ловим)
                val removed = mutableListOf<Card>()

                for (discard in catched) {
                    if (situation.rasklad.removeCard(discard.firstCard!!, contractHand))
                        removed.add(discard.firstCard!!)
                    if (situation.rasklad.removeCard(discard.secondCard!!, contractHand))
                        removed.add(discard.secondCard!!)
                }
                this.iamSure = true
                if (situation.rasklad.getCardsInHand(contractHand, false).isNotEmpty()) {
                    val all = situation.getEstimation(-Double.MAX_VALUE, Double.MAX_VALUE, true, preEstimate = true)!!
                    for (card in removed) {
                        situation.rasklad.addCard(card, contractHand)
                    }
                    if (all.estimation > 0) {
                        AiDebug.log("----------------------------------")
                        AiDebug.log("Можем поймать при всех вариантах сноса :)")
                        if (AiDebug.enabled) Helper.logRasklad(situation.rasklad)
                        return all.bestCard
                    }
                }
                //endregion

                var cPos = 0.0
                pSum = catched.sumOf { it.probability }
                val pos = rnd.nextDouble() * pSum
                for (discard in catched) {
                    cPos += discard.probability
                    if (pos <= cPos) {
                        //region Ловим данный расклад
                        val probability = semanticProbability[discard.hash!!]!! / pSum
                        this.iamSure = probability >= 0.45

                        val firstRemoved = situation.rasklad.removeCard(discard.firstCard!!, contractHand)
                        val secondRemoved = situation.rasklad.removeCard(discard.secondCard!!, contractHand)

                        val res = if (iamSure)
                            situation.getEstimation(-Double.MAX_VALUE, Double.MAX_VALUE, true, preEstimate = false)!!
                        else
                            situation.getEstimation(-Double.MAX_VALUE, Double.MAX_VALUE, true, preEstimate = true)!!

                        AiDebug.log("----------------------------------")
                        AiDebug.log("Ловим ${discard.firstCard} ${discard.secondCard} ${probability * 100.0}%")
                        AiDebug.log("EST = ${res.estimation}")
                        if (AiDebug.enabled) {
                            Helper.logRasklad(situation.rasklad)
                            Helper.logDiscards(catched)
                        }

                        if (firstRemoved)
                            situation.rasklad.addCard(discard.firstCard!!, contractHand)
                        if (secondRemoved)
                            situation.rasklad.addCard(discard.secondCard!!, contractHand)
                        return res.bestCard
                        //endregion
                    }
                }
                throw Exception("Неправильный расчёт вероятностей")
            }
        } else {
            this.iamSure = true
            situation.maxwidth = 0
            val res = situation.getEstimation(-Double.MAX_VALUE, Double.MAX_VALUE, true, preEstimate = false, width = 1)!!
            AiDebug.log("----------------------------------")
            AiDebug.log("Знаем расклад")
            AiDebug.log("EST = ${res.estimation}")
            if (AiDebug.enabled) {
                Helper.logRasklad(situation.rasklad)
                AiDebug.log("calculated=${situation.calculated}  skipped=${situation.skipped}  maxwidth=${situation.maxwidth}")
            }
            return res.bestCard
        }
    }
}
