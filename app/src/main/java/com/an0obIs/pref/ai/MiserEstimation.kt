package com.an0obIs.pref.ai

import kotlin.math.min

class MiserEstimation : Estimation() {

    class Color {
        var risk: Double = 0.0
        var exit: Double = 0.0
        var exit2: Double = 0.0
        var exit3: Double = 0.0
        var exit4: Double = 0.0
        var intercept: Double = 0.0
        var take: Double = 0.0
        var take2: Double = 0.0
        var take3: Double = 0.0
        var take4: Double = 0.0
        val takes: Double
            get() = take + take2 + take3 + take4

        var prevDiscards: Double = 0.0
        var nextDiscards: Double = 0.0

        var prevNeedToBeDiscarded: Double = 0.0
        var nextNeedToBeDiscarded: Double = 0.0

        var prevCanBeDiscarded: Double = 0.0
        var nextCanBeDiscarded: Double = 0.0

        var scissorsTakes: Double = 0.0
        var scissorsPrevDiscards: Double = 0.0

        var prevTakes: Double = 0.0
        var nextTakes: Double = 0.0
        var prevToNext: Double = 0.0
        var nextToPrev: Double = 0.0
    }

    var colors: MutableMap<Int, Color> = mutableMapOf()

    var isHidden: Boolean = false

    var iamSure: Boolean = true

    init {
        for (i in 0 until 4) {
            colors[i] = Color()
        }
    }

    override fun calcRasklad(givenRasklad: ColorRasklad) {
        val cnts = Color()
        forRasklad(cnts, givenRasklad)
        colors[givenRasklad.coatColor] = cnts
    }

    override fun calcAllRasklads(color: Int, rEnumerator: ColorRaskladEnumerator) {
        val cnts = Color()

        var rasklad = rEnumerator.getNext()
        var rCnt = 0.0
        while (rasklad != null) {
            forRasklad(cnts, rasklad)
            rCnt += rasklad.probability
            rasklad = rEnumerator.getNext()
        }
        cnts.risk /= rCnt
        cnts.exit /= rCnt
        cnts.exit2 /= rCnt
        cnts.exit3 /= rCnt
        cnts.exit4 /= rCnt
        cnts.intercept /= rCnt
        cnts.take /= rCnt
        cnts.take2 /= rCnt
        cnts.take3 /= rCnt
        cnts.take4 /= rCnt

        cnts.prevTakes /= rCnt
        cnts.nextTakes /= rCnt
        cnts.nextToPrev /= rCnt
        // NOTE: preserved from the original C# (prevToNext was assigned from nextToPrev)
        cnts.prevToNext = cnts.nextToPrev / rCnt

        cnts.nextDiscards /= rCnt
        cnts.prevDiscards /= rCnt
        cnts.nextNeedToBeDiscarded /= rCnt
        cnts.prevNeedToBeDiscarded /= rCnt

        cnts.scissorsPrevDiscards /= rCnt
        cnts.scissorsTakes /= rCnt
        cnts.nextNeedToBeDiscarded /= rCnt
        cnts.nextCanBeDiscarded /= rCnt
        cnts.prevNeedToBeDiscarded /= rCnt
        cnts.prevCanBeDiscarded /= rCnt

        colors[color] = cnts
    }

    private fun forRasklad(cnts: Color, iRasklad: ColorRasklad) {
        val rasklad = if (contractor!! == 0) iRasklad else iRasklad.getRaskladForContractor(contractor!!)

        if (!isHidden)
            rasklad.probability = 1.0

        // Длина возможного "паровозика"
        if (rasklad.nextHand.size >= rasklad.prevHand.size) {
            if (rasklad.myHand.size >= rasklad.nextHand.size)
                cnts.risk += rasklad.myHand.size - rasklad.nextHand.size + 1
        } else {
            if (rasklad.myHand.size >= rasklad.prevHand.size)
                cnts.risk += rasklad.myHand.size - rasklad.prevHand.size + 1
        }

        if (rasklad.myHand.size >= 2 && (rasklad.nextHand.size >= 2 || rasklad.prevHand.size >= 2)) {
            // Есть ли перехват (можно гарантированно взять а затем гарантированно отдаться)
            val myMin1 = if (rasklad.myHand.isNotEmpty()) rasklad.myHand[0] else 0
            val myMax = rasklad.myHand.last()
            val nextMax = if (rasklad.nextHand.isEmpty()) 0 else rasklad.nextHand.last()
            val prevMax = if (rasklad.prevHand.isEmpty()) 0 else rasklad.prevHand.last()
            var intercept = 0
            var nextIntercept = 0
            var prevIntercept = 0

            if (nextMax < myMax && prevMax < myMax)
                intercept = 1
            if (nextMax < myMax)
                nextIntercept = 1
            if (prevMax < myMax)
                prevIntercept = 1

            if (intercept > 0 || nextIntercept > 0 || prevIntercept > 0) {
                val nextMin1 = if (rasklad.nextHand.size <= 1) 0 else rasklad.nextHand[0]
                // NOTE: preserved from the original C# (prevMin1 also read from nextHand)
                val prevMin1 = if (rasklad.nextHand.size <= 1) 0 else rasklad.nextHand[0]
                if (intercept > 0 && (nextMin1 > myMin1 || prevMin1 > myMin1))
                    cnts.intercept = rasklad.probability
            }
        }

        // Считаем взятки и отцепки
        val subEst = MiserSubEstimation(rasklad)
        subEst.calcColor(cnts)

        // Считаем возможность передать заход
        if (rasklad.nextHand.size >= 2 && rasklad.prevHand.size >= 2 && rasklad.myHand.size >= 2) {
            val next = rasklad.nextHand[1]
            val prev = rasklad.prevHand[1]
            if (next > prev)
                cnts.prevToNext += rasklad.probability
            else
                cnts.nextToPrev += rasklad.probability
        } else if (rasklad.nextHand.isNotEmpty() && rasklad.prevHand.isNotEmpty() && rasklad.myHand.isNotEmpty()) {
            val next = rasklad.nextHand[0]
            val prev = rasklad.prevHand[0]
            if (next > prev)
                cnts.prevToNext += rasklad.probability
            else
                cnts.nextToPrev += rasklad.probability
        }

        if (!isHidden) {
            // Возможность проноса
            cnts.nextDiscards = (min(rasklad.prevHand.size, rasklad.myHand.size) - rasklad.nextHand.size).toDouble()
            cnts.prevDiscards = (min(rasklad.nextHand.size, rasklad.myHand.size) - rasklad.prevHand.size).toDouble()

            if (cnts.nextDiscards < 0)
                cnts.nextDiscards = 0.0
            if (cnts.prevDiscards < 0)
                cnts.prevDiscards = 0.0

            cnts.nextNeedToBeDiscarded = 0.0
            cnts.prevNeedToBeDiscarded = 0.0

            // Взятки на "подрезке"
            if (cnts.takes == 0.0 && rasklad.myHand.size >= 2 && rasklad.nextHand.size >= 2 && rasklad.prevHand.isNotEmpty()) {
                if (subEst.nextMin < subEst.myMin2 && subEst.prevMin < subEst.myMin2) {
                    while (subEst.prevMin2 > subEst.myMin2) {
                        subEst.discardPrev()
                        cnts.scissorsPrevDiscards++
                    }
                    cnts.scissorsTakes++

                    if (cnts.scissorsPrevDiscards > 0) {
                        subEst.reset()
                    }
                }
            }

            // Необходимость проноса
            if (cnts.prevTakes > 0 && cnts.takes == 0.0) {
                var takes = cnts.takes
                while (takes == 0.0) {
                    subEst.discardNext()
                    takes = subEst.calcTakes()
                    cnts.nextNeedToBeDiscarded++
                }
            }
            subEst.reset()
            if (cnts.nextTakes > 0 && cnts.takes == 0.0) {
                var takes = cnts.takes
                while (takes == 0.0) {
                    subEst.discardPrev()
                    takes = subEst.calcTakes()
                    cnts.prevNeedToBeDiscarded++
                }
            }
        }
    }

    override fun getIntegral(): Double {
        // Функция полезности
        var res = 0.0
        var totalRisk = 0.0
        var totalIntercepts = 0.0
        var totalExit = 0.0
        var totalTakes = 0.0
        var totalNextToPrev = 0.0
        var totalPrevToNext = 0.0
        var nextTakesSum = 0.0
        var prevTakesSum = 0.0
        var totalNextDiscards = 0.0
        var totalPrevDiscards = 0.0
        var totalNextNeedToBeDiscarded = 0.0
        var totalPrevNeedToBeDiscarded = 0.0
        var totalScissorsTakes = 0.0
        var totalScissorsPrevNeedToBeDiscarded = 0.0

        for (cnts in colors.values) {
            totalNextToPrev += cnts.nextToPrev
            totalPrevToNext += cnts.prevToNext

            totalRisk += cnts.risk
            totalIntercepts += cnts.intercept
            totalExit += cnts.exit + 0.75 * cnts.exit2 + 0.25 * cnts.exit3 + 0.1 * cnts.exit4
            totalTakes += cnts.take + cnts.take2 + cnts.take3 + cnts.take4
            totalNextDiscards += cnts.nextDiscards
            totalPrevDiscards += cnts.prevDiscards

            nextTakesSum += cnts.nextTakes
            prevTakesSum += cnts.prevTakes
        }

        for (cnts in colors.values) {
            cnts.prevCanBeDiscarded = totalPrevDiscards - cnts.prevDiscards
            cnts.nextCanBeDiscarded = totalNextDiscards - cnts.nextDiscards
            val notThisColorNextToPrev = totalNextToPrev - cnts.nextToPrev

            if (cnts.prevNeedToBeDiscarded <= cnts.prevCanBeDiscarded && cnts.prevNeedToBeDiscarded > 0)
                totalPrevNeedToBeDiscarded += cnts.prevNeedToBeDiscarded

            if (cnts.nextNeedToBeDiscarded <= cnts.nextCanBeDiscarded && cnts.nextNeedToBeDiscarded > 0)
                totalNextNeedToBeDiscarded += cnts.nextNeedToBeDiscarded

            if (cnts.scissorsPrevDiscards <= cnts.prevCanBeDiscarded && cnts.scissorsTakes > 0 && (turn == -1 || notThisColorNextToPrev > cnts.scissorsPrevDiscards)) {
                totalScissorsPrevNeedToBeDiscarded += cnts.scissorsPrevDiscards
                totalScissorsTakes += cnts.scissorsTakes
            }
        }

        if (totalIntercepts > 1)
            totalIntercepts = 1.0

        if (iamSure) {
            // Возможности отдаться и перехватить полезны - отжирать их надо только если уверены
            res += 0.0001 * totalExit
            res += 0.0001 * totalIntercepts
        }

        // За взятую взятку:
        if (!iamSure) {
            res -= 500 * myTakes // TODO: Проверить влияние
        } else if (contractor == 0)
            res -= 1.5 * myTakes // Мизерующий не хочет брать
        else
            res -= 1 * myTakes // Ловящие не спешат давать

        if (totalNextToPrev < 0.05 && nextTakesSum < 0.05 && turn == 1)
            return res
        if (totalPrevToNext < 0.05 && prevTakesSum < 0.05 && turn == -1)
            return res

        if (totalExit < 0.05 && turn == 0)
            return res - cardsLeft

        // Возможности передаться
        res -= 0.000001 * totalNextToPrev
        res -= 0.000001 * totalPrevToNext

        if (totalScissorsTakes > 0) {
            res += 0.01 * totalScissorsPrevNeedToBeDiscarded
            res -= 0.5
        }

        if (totalNextNeedToBeDiscarded > 0 && (turn == -1 || (turn == 1 && totalNextToPrev >= 1))) {
            res += 0.01 * totalNextNeedToBeDiscarded
            res -= 0.5
        }
        if (totalPrevNeedToBeDiscarded > 0 && (turn == 1 || (turn == -1 && totalPrevToNext >= 1))) {
            res += 0.01 * totalPrevNeedToBeDiscarded
            res -= 0.5
        }

        res -= 0.00000001 * totalRisk

        res -= 1 * totalTakes

        if (isHidden) {
            res -= 0.1 * prevTakesSum
            res -= 0.1 * nextTakesSum
        }

        if (turn == -1)
            res -= 0.000000000001 // Заход хуже всего на руке перед игроком.

        return res
    }
}
