package com.an0obIs.pref.ai

class RaspasyEstimation : Estimation() {

    class Color {
        var risk: Double = 0.0
        var exit: Double = 0.0
        var exit2: Double = 0.0
        var exit3: Double = 0.0
        var intercept: Double = 0.0
        var renons: Double = 0.0
        var renons2: Double = 0.0
        var take: Double = 0.0
        var take2: Double = 0.0
        var take3: Double = 0.0

        var next: Color? = null
        var prev: Color? = null
        var prevToNext: Double = 0.0
        var nextToPrev: Double = 0.0
    }

    var colors: MutableMap<Int, Color> = mutableMapOf()

    val alreadyTaken: Double
        get() = myTakes

    override fun getIntegral(): Double {
        // Функция полезности
        var res = 0.0
        var totalRisk = 0.0
        var totalIntercepts = 0.0
        var totalRenons = 0.0
        var totalExit = 0.0
        var totalTakes = 0.0
        var totalNextToPrev = 0.0
        var totalPrevToNext = 0.0
        var nextTakesSum = 0.0
        var prevTakesSum = 0.0

        for (cnts in colors.values) {
            totalRenons += cnts.renons + 0.5 * cnts.renons2
            totalNextToPrev += cnts.nextToPrev
            totalPrevToNext += cnts.prevToNext

            totalRisk += cnts.risk
            totalIntercepts += cnts.intercept
            totalExit += cnts.exit + 0.75 * cnts.exit2 + 0.25 * cnts.exit3
            totalTakes += cnts.take + 0.75 * cnts.take2 + 0.25 * cnts.take3

            nextTakesSum += cnts.next!!.take + cnts.next!!.take2 + cnts.next!!.take3
            prevTakesSum += cnts.prev!!.take + cnts.prev!!.take2 + cnts.prev!!.take3
        }

        // За взятую взятку:
        res -= alreadyTaken

        if (totalIntercepts > 1)
            totalIntercepts = 1.0

        val risk = (1 + totalRisk) / ((1 + 3 * totalIntercepts * totalIntercepts) * (1 + totalRenons))

        if (totalNextToPrev < 0.05 && nextTakesSum < 0.05 && turn == 1) {
            return res + cardsLeft + (0.5 * totalRenons) / risk
        }
        if (totalPrevToNext < 0.05 && prevTakesSum < 0.05 && turn == -1) {
            return res + cardsLeft + (0.5 * totalRenons) / risk
        }
        if (turn == 0 && totalExit == 0.0)
            return res - cardsLeft

        res += 1.5 * totalExit
        res += 0.5 * totalRenons
        res += 0.1 * totalIntercepts

        res -= 0.75 * totalTakes

        if (turn == 1) {
            var t = 0.25 * totalRenons
            if (cardsLeft > 4)
                t += 0.5
            t /= risk
            res += t
        } else if (turn == -1) {
            var t = 0.25 * totalRenons
            t /= risk
            res += t
        }
        // За ренонс оппонента:
        val move = move!!
        if (move.nextMove == null || move.nextMove!!.coatColor != move.playColor)
            res -= 0.75
        if (move.prevMove == null || move.prevMove!!.coatColor != move.playColor)
            res -= 0.75
        return res
    }

    override fun calcRasklad(givenRasklad: ColorRasklad) {
        val cnts = Color()
        cnts.next = Color()
        cnts.prev = Color()
        forRasklad(cnts, givenRasklad)
        colors[givenRasklad.coatColor] = cnts
    }

    override fun calcAllRasklads(color: Int, rEnumerator: ColorRaskladEnumerator) {
        val cnts = Color()
        cnts.next = Color()
        cnts.prev = Color()
        var rasklad = rEnumerator.getNext()
        var rCnt = 0.0
        while (rasklad != null) {
            forRasklad(cnts, rasklad)
            rCnt += rasklad.probability
            rasklad = rEnumerator.getNext()
        }
        cnts.risk /= rCnt
        cnts.renons /= rCnt
        cnts.renons2 /= rCnt
        cnts.exit /= rCnt
        cnts.exit2 /= rCnt
        cnts.exit3 /= rCnt
        cnts.intercept /= rCnt
        cnts.take /= rCnt
        cnts.take2 /= rCnt
        cnts.take3 /= rCnt

        cnts.next!!.take /= rCnt
        cnts.next!!.take2 /= rCnt
        cnts.next!!.take3 /= rCnt
        cnts.next!!.exit /= rCnt
        cnts.next!!.exit2 /= rCnt
        cnts.next!!.exit3 /= rCnt
        cnts.next!!.intercept /= rCnt

        cnts.prev!!.take /= rCnt
        cnts.prev!!.take2 /= rCnt
        cnts.prev!!.take3 /= rCnt
        cnts.prev!!.exit /= rCnt
        cnts.prev!!.exit2 /= rCnt
        cnts.prev!!.exit3 /= rCnt
        cnts.prev!!.intercept /= rCnt

        cnts.nextToPrev /= rCnt
        // NOTE: preserved from the original C# (prevToNext was assigned from nextToPrev)
        cnts.prevToNext = cnts.nextToPrev / rCnt

        colors[color] = cnts
    }

    private fun forRasklad(cnts: Color, rasklad: ColorRasklad) {
        rasklad.myHand = rasklad.myHand.sorted().toMutableList()
        rasklad.nextHand = rasklad.nextHand.sorted().toMutableList()
        rasklad.prevHand = rasklad.prevHand.sorted().toMutableList()

        // Длина возможного "паровозика"
        if (rasklad.nextHand.size >= rasklad.prevHand.size) {
            if (rasklad.myHand.size >= rasklad.nextHand.size)
                cnts.risk += rasklad.myHand.size - rasklad.nextHand.size + 1
        } else {
            if (rasklad.myHand.size >= rasklad.prevHand.size)
                cnts.risk += rasklad.myHand.size - rasklad.prevHand.size + 1
        }

        if (rasklad.myHand.isEmpty()) {
            // Шанс пронестись при ренонсе
            if (rasklad.nextHand.isNotEmpty() && rasklad.prevHand.isNotEmpty())
                cnts.renons += rasklad.probability
        } else {
            // Шанс пронестись при ренонсе
            if (rasklad.nextHand.size > rasklad.myHand.size && rasklad.prevHand.size > rasklad.myHand.size)
                cnts.renons2++

            // Есть ли отход (можно ли отцепиться в эту масть с первого захода)
            val myMin = rasklad.myHand[0]
            val nextMin = if (rasklad.nextHand.isEmpty()) 0 else rasklad.nextHand[0]
            val prevMin = if (rasklad.prevHand.isEmpty()) 0 else rasklad.prevHand[0]

            if (nextMin > myMin || prevMin > myMin)
                cnts.exit += rasklad.probability
            if (nextMin > myMin && nextMin > 0)
                cnts.next!!.exit += rasklad.probability
            if (prevMin > myMin && prevMin > 0)
                cnts.prev!!.exit += rasklad.probability

            // Могут ли нам всучить?
            if (nextMin < myMin && prevMin < myMin && (nextMin > 0 || prevMin > 0))
                cnts.take += rasklad.probability
            if (nextMin < myMin)
                cnts.next!!.take += rasklad.probability
            if (prevMin < myMin)
                cnts.prev!!.take += rasklad.probability

            // Есть ли перехват (можно гарантированно взять а затем гарантированно отдаться)
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
                if (intercept > 0 && (nextMin1 > myMin || prevMin1 > myMin))
                    cnts.intercept = rasklad.probability
                if (nextIntercept > 0 && nextMin1 > myMin)
                    cnts.next!!.intercept = rasklad.probability
                if (prevIntercept > 0 && prevMin1 > myMin)
                    cnts.prev!!.intercept = rasklad.probability
            }

            if (rasklad.myHand.size >= 2 && (rasklad.nextHand.size >= 2 || rasklad.prevHand.size >= 2)) {
                // Можно ли отцепиться за два хода?
                val myMin2 = rasklad.myHand[1]
                val nextMin2 = if (rasklad.nextHand.size <= 1) 0 else rasklad.nextHand[1]
                val prevMin2 = if (rasklad.prevHand.size <= 1) 0 else rasklad.prevHand[1]
                if (nextMin2 > myMin2 || prevMin2 > myMin2)
                    cnts.exit2 += rasklad.probability
                if (nextMin2 > myMin2)
                    cnts.next!!.exit2 += rasklad.probability
                if (prevMin2 > myMin2)
                    cnts.prev!!.exit2 += rasklad.probability
                // Могут ли нам всучить за два хода?
                if (nextMin2 < myMin2 && prevMin2 < myMin2)
                    cnts.take2 += rasklad.probability
                if (nextMin2 < myMin2 && nextMin2 > 0)
                    cnts.next!!.take2 += rasklad.probability
                if (prevMin2 < myMin2 && prevMin2 > 0)
                    cnts.prev!!.take2 += rasklad.probability
            }

            if (rasklad.myHand.size >= 3 && (rasklad.nextHand.size >= 3 || rasklad.prevHand.size >= 3)) {
                // Можно ли отцепиться за три хода?
                val myMin3 = rasklad.myHand[2]
                val nextMin3 = if (rasklad.nextHand.size <= 2) 0 else rasklad.nextHand[2]
                val prevMin3 = if (rasklad.prevHand.size <= 2) 0 else rasklad.prevHand[2]
                if (nextMin3 > myMin3 || prevMin3 > myMin3)
                    cnts.exit3 += rasklad.probability
                if (nextMin3 > myMin3)
                    cnts.next!!.exit3 += rasklad.probability
                if (prevMin3 > myMin3)
                    cnts.prev!!.exit3 += rasklad.probability
                // Могут ли нам всучить за три хода?
                if (nextMin3 < myMin3 && prevMin3 < myMin3)
                    cnts.take3 += rasklad.probability
                if (nextMin3 < myMin3 && nextMin3 > 0)
                    cnts.next!!.take += rasklad.probability
                if (prevMin3 < myMin3 && prevMin3 > 0)
                    cnts.prev!!.take += rasklad.probability
            }
        }

        // Считаем возможность отдаться
        if (rasklad.nextHand.size >= 3 && rasklad.prevHand.size >= 3) {
            val next = rasklad.nextHand[2]
            val prev = rasklad.prevHand[2]
            if (next > prev)
                cnts.prevToNext += rasklad.probability
            else
                cnts.nextToPrev += rasklad.probability
        } else if (rasklad.nextHand.size >= 2 && rasklad.prevHand.size >= 2) {
            val next = rasklad.nextHand[1]
            val prev = rasklad.prevHand[1]
            if (next > prev)
                cnts.prevToNext += rasklad.probability
            else
                cnts.nextToPrev += rasklad.probability
        } else if (rasklad.nextHand.isNotEmpty() && rasklad.prevHand.isNotEmpty()) {
            val next = rasklad.nextHand[0]
            val prev = rasklad.prevHand[0]
            if (next > prev)
                cnts.prevToNext += rasklad.probability
            else
                cnts.nextToPrev += rasklad.probability
        }
    }
}
