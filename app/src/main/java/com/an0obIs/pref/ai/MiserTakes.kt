package com.an0obIs.pref.ai

import com.an0obIs.pref.model.Card

class MiserTakes(ai: AIInfo) {

    var takes: MutableMap<Int, MiserTake> = mutableMapOf()

    var firstMoveTakes: Double = 0.0

    class MiserTake {
        var takes: Double = 0.0
        var risk: Double = 0.0
        var haveMove: Boolean = false
    }

    val totalTakes: Double
        get() = takes.values.sumOf { it.takes } + firstMoveTakes

    val totalRisk: Double
        get() = takes.values.sumOf { it.risk }

    val haveMove: Boolean
        get() = takes.values.count { it.haveMove } > 0

    private fun calc1(cards: List<Card>): MiserTake {
        val opponentTotal = 8 - cards.size
        var v = 7
        var notMyTakes = 0
        var myTakes = 0
        var takes = 0.0
        var opponent = opponentTotal
        var my = cards.size
        var risk = (my - 2).toDouble()
        var haveMove = false
        if (risk < 0)
            risk = 0.0
        while (v < 15) {
            if (cards.firstOrNull { it.value == v } != null) {
                if (myTakes > 0) {
                    myTakes--
                    val opp = opponent + myTakes
                    if (v == 8) {
                        takes += 0.5
                        haveMove = true
                        firstMoveTakes = -0.5
                    } else if (my == 1 && v == 9) {
                        takes += 1
                        haveMove = true
                        firstMoveTakes = -0.5
                    } else if (opp == 0 && opponentTotal == 4)
                        takes += 0.25
                    else if (opp == 1 && opponentTotal >= 4)
                        takes += 0.5
                    else if (opp == 0 && opponentTotal == 3)
                        takes += 0.5
                    else
                        takes++
                } else {
                    notMyTakes++
                    if (v == 8)
                        haveMove = true
                }
                my--
            } else {
                if (notMyTakes > 0) {
                    notMyTakes--
                } else {
                    myTakes++
                }
                opponent--
            }
            v++
        }

        return MiserTake().also {
            it.haveMove = haveMove
            it.takes = takes
            it.risk = risk
        }
    }

    init {
        firstMoveTakes = 0.0
        for (i in 0 until 4) {
            var cards: List<Card> = ai.myHand.visibleHand!!.cardByCoat[i]!!
            if (cards.isNotEmpty()) {
                val mt = calc1(cards)
                var mt1 = mt
                var mt2: MiserTake
                while (mt1.takes >= 0.5 && cards.size > 1) {
                    cards = cards.sortedByDescending { it.value }.drop(1)
                    mt2 = calc1(cards)
                    if (mt1.takes == mt2.takes)
                        mt.takes++
                    mt1 = mt2
                }
                takes[i] = mt
            } else
                takes[i] = MiserTake()
        }

        if (ai.myFirstMove && !haveMove) {
            // Анализ захода
            firstMoveTakes = 0.5
        }
        if (!ai.myFirstMove) {
            firstMoveTakes = 0.0
        }
    }
}
