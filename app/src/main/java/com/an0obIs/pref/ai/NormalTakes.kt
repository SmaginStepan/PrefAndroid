package com.an0obIs.pref.ai

import com.an0obIs.pref.model.Card
import com.an0obIs.pref.model.Hand
import kotlin.math.floor

class NormalTakes(ai: AIInfo) {

    class ColorCalc {
        var turns: Double = 0.0
        var takes: Double = 0.0
    }

    class PotentialContract {
        var maxContract: Int = 0
        var takes: Double = 0.0
        var turns: Double = 0.0
    }

    var takes: MutableMap<Int, ColorCalc> = mutableMapOf()

    fun prikupPedict(takes: Double, hand: Hand, nonTrump: Boolean): Int {
        @Suppress("NAME_SHADOWING")
        var takes = takes
        // Эвристики оценки прикупа
        if (!nonTrump) {
            // Оценивается не бескозырка
            var cntAces = 0
            var cntLong = 0
            for (color in hand.cardByCoat.keys) {
                val cards = hand.cardByCoat[color]!!
                if (cards.firstOrNull { it.value == 14 } != null)
                    cntAces++
                if (cards.size >= 3)
                    cntLong++
            }
            if (cntLong >= 3)
                takes += 0.5 // Добавляем за 3 длинные масти
            if (cntAces >= 3)
                takes += 0.5 // Добавляем за 3х тузов
        }
        if (takes < 4.5)
            return 0
        if (takes < 6)
            return 6
        if (takes < 7)
            return 7
        return floor(takes).toInt()
    }

    fun getMaxContract(trump: Int, hand: Hand, myFirstTurn: Boolean, countAdditional: Boolean): PotentialContract {
        val minTakes = takes.values.minOf { it.takes }
        val maxTurns = takes.values.maxOf { it.turns }
        var takesSum = takes.values.sumOf { it.takes }
        if (trump < 0 || trump > 3) {
            // Бескозырка
            if (minTakes > 0 && maxTurns < 2) {
                return PotentialContract().also {
                    it.takes = takesSum
                    it.turns = 0.0
                    it.maxContract = prikupPedict(takesSum, hand, true)
                }
            }
            return PotentialContract().also {
                it.takes = 0.0
                it.turns = 0.0
                it.maxContract = 0
            }
        }
        var neededTurns = takes.filter { it.key != trump }.values.sumOf { it.turns }

        var cards = hand.cardByCoat[trump]!!
        var havingTurns = cards.size.toDouble()
        var v = 14
        while (cards.firstOrNull { it.value == v } == null && v > 6) {
            v--
            if (havingTurns > 0)
                havingTurns--
        }
        havingTurns += minTakes

        if (myFirstTurn)
            havingTurns++

        while (havingTurns < neededTurns) {
            takesSum -= 1.5
            neededTurns--
        }

        if (!myFirstTurn) {
            // Заложиться на розыгрыш неиграющего козыря
            for (color in hand.cardByCoat.keys) {
                if (trump == color || cards.size <= 1)
                    continue
                var notMyCards = 0
                cards = hand.cardByCoat[color]!!
                v = 14
                while (v > 6 && cards.firstOrNull { it.value == v } == null) {
                    notMyCards++
                    v--
                }
                val take = takes[color]!!
                if (take.takes > 0 && take.takes == (cards.size - notMyCards).toDouble()) {
                    takesSum -= 0.5
                    break
                }
            }
        }
        if (countAdditional) {
            // Прибавить копеечку за каждую длинную масть, второго короля и третью даму... нужно для определения лучшего сброса.
            for (color in hand.cardByCoat.keys) {
                cards = hand.cardByCoat[color]!!
                if (cards.size >= 3)
                    takesSum += 0.001 // Длинная масть

                if (cards.size == 3 && takes[color]!!.takes == 0.0 && cards.firstOrNull { it.value == 12 } != null) {
                    // Третья дама
                    takesSum += 0.001
                }
                if (cards.size == 2 && takes[color]!!.takes == 0.0 && cards.firstOrNull { it.value == 13 } != null) {
                    // Второй король
                    takesSum += 0.0005
                }
            }
        } else {
            // Если у нас нет длинного козыря - это плохо!
            if (hand.cardByCoat[trump]!!.size < 4) {
                takesSum -= (4 - hand.cardByCoat[trump]!!.size) * 0.5
            }
        }
        return PotentialContract().also {
            it.takes = takesSum
            it.turns = havingTurns - neededTurns
            it.maxContract = prikupPedict(takesSum, hand, false)
        }
    }

    private fun calc1(calc: ColorCalc, opponentStart: Int, cards: List<Card>) {
        var opponent = opponentStart
        var notMy = 0
        var v = 14
        calc.takes = 0.0
        calc.turns = 0.0
        var turns = 0
        while (v > 6) {
            if (cards.firstOrNull { it.value == v } != null) {
                // Карта есть
                notMy--
                if (notMy < 0) {
                    notMy = 0
                    calc.takes++
                    calc.turns += turns
                    turns = 0
                    opponent--
                }
            } else {
                if (opponent > 0) {
                    // Карты нет, но есть у оппонента
                    notMy++
                    turns++
                    opponent--
                }
            }
            v--
        }
    }

    init {
        for (i in 0 until 4) {
            val cards = ai.myHand.visibleHand!!.cardByCoat[i]!!
            var opponent = 8 - cards.size
            if (opponent == 4)
                opponent = 3 // Не закладываемся на 4 карты на одной руке
            val calc = ColorCalc()
            calc1(calc, opponent, cards)
            if (opponent == 3) {
                val calc2 = ColorCalc()
                calc1(calc2, 2, cards)
                calc.turns += (calc2.turns - calc.turns) / 2
                calc.takes += (calc2.takes - calc.takes) / 2
            }

            takes[i] = calc
        }
    }
}
