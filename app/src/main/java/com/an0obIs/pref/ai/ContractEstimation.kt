package com.an0obIs.pref.ai

class ContractEstimation : Estimation() {

    class Color {
        var myTakes: Double = 0.0
        var myGives: Double = 0.0

        var prevTakes: Double = 0.0
        var prevGives: Double = 0.0

        var nextTakes: Double = 0.0
        var nextGives: Double = 0.0

        var myCards: Double = 0.0
        var prevCards: Double = 0.0
        var nextCards: Double = 0.0
    }

    var isHidden: Boolean = false

    var colors: MutableMap<Int, Color> = mutableMapOf()

    init {
        for (i in 0 until 4) {
            colors[i] = Color()
        }
    }

    private fun forRasklad(cnts: Color, rasklad: ColorRasklad) {
        var myHand = rasklad.myHand.sortedDescending().toMutableList()
        var nextHand = rasklad.nextHand.sortedDescending().toMutableList()
        var prevHand = rasklad.prevHand.sortedDescending().toMutableList()

        cnts.myCards += myHand.size * rasklad.probability
        cnts.nextCards += nextHand.size * rasklad.probability
        cnts.prevCards += prevHand.size * rasklad.probability

        //region Разыгрываем мы
        var myTmpHand = myHand.toMutableList()
        var nextTmpHand = nextHand.toMutableList()
        var prevTmpHand = prevHand.toMutableList()
        while (myHand.size > 0 || (rasklad.coatColor == trump && (nextHand.size > 0 || prevHand.size > 0))) {
            val myMax = myHand.firstOrNull() ?: 0
            val nextMax = nextHand.firstOrNull() ?: 0
            val prevMax = prevHand.firstOrNull() ?: 0

            if (myMax > prevMax && myMax > nextMax) {
                // Старшая у игрока (нас)
                cnts.myTakes += rasklad.probability
                // Удаляем у нас старшую, а у вистующих младшие карты
                if (myHand.size > 0)
                    myHand.removeAt(0)
                if (nextHand.size > 0)
                    nextHand.removeAt(nextHand.size - 1)
                if (prevHand.size > 0)
                    prevHand.removeAt(prevHand.size - 1)
            } else {
                // Старшая у вистующих
                cnts.myGives += rasklad.probability

                var givePrev = false
                var giveNext = false

                // Если у вистующих только одна старшая, кладём её:
                if (prevMax > myMax && myMax > nextMax)
                    givePrev = true
                else if (nextMax > myMax && myMax > prevMax)
                    giveNext = true
                else {
                    // У вистующих обе старшие: кладём всегда с более короткой руки, если руки одинаковы, то младшую
                    if (prevHand.size < nextHand.size)
                        givePrev = true
                    else if (nextHand.size < prevHand.size)
                        giveNext = true
                    else if (prevMax < nextMax)
                        givePrev = true
                    else if (nextMax < prevMax)
                        giveNext = true
                }
                if (givePrev) {
                    if (myHand.size > 0)
                        myHand.removeAt(0)
                    if (nextHand.size > 0)
                        nextHand.removeAt(nextHand.size - 1)
                    if (prevHand.size > 0)
                        prevHand.removeAt(0)
                } else if (giveNext) {
                    if (myHand.size > 0)
                        myHand.removeAt(0)
                    if (nextHand.size > 0)
                        nextHand.removeAt(0)
                    if (prevHand.size > 0)
                        prevHand.removeAt(prevHand.size - 1)
                } else
                    throw Exception("Не может быть!")
            }
        }
        myHand = myTmpHand
        nextHand = nextTmpHand
        prevHand = prevTmpHand
        //endregion

        //region Разыгрывает первый вистующий
        // Второго вистующего игнорируем
        myTmpHand = myHand.toMutableList()
        nextTmpHand = nextHand.toMutableList()
        prevTmpHand = prevHand.toMutableList()
        while ((myHand.size > 0 || rasklad.coatColor == trump) && prevHand.size > 0) {
            val myMax = myHand.firstOrNull() ?: 0
            val prevMax = prevHand.firstOrNull() ?: 0

            if (prevMax > myMax) {
                // Старшая у вистующего
                cnts.prevTakes += rasklad.probability
                // Удаляем у вистующего старшую, а у нас младшую карту
                if (myHand.size > 0)
                    myHand.removeAt(myHand.size - 1)
                if (prevHand.size > 0)
                    prevHand.removeAt(0)
            } else {
                // Старшая у игрока (нас)
                cnts.prevGives += rasklad.probability
                if (myHand.size > 0)
                    myHand.removeAt(0)
                if (prevHand.size > 0)
                    prevHand.removeAt(0)
            }
        }
        myHand = myTmpHand
        nextHand = nextTmpHand
        prevHand = prevTmpHand
        //endregion

        //region Разыгрывает второй вистующий
        // Первого вистующего игнорируем
        while ((myHand.size > 0 || rasklad.coatColor == trump) && nextHand.size > 0) {
            val nextMax = nextHand.firstOrNull() ?: 0
            val myMax = myHand.firstOrNull() ?: 0

            if (nextMax > myMax) {
                // Старшая у вистующего
                cnts.nextTakes += rasklad.probability
                // Удаляем у вистующего старшую, а у нас младшую карту
                if (nextHand.size > 0)
                    nextHand.removeAt(nextHand.size - 1)
                if (myHand.size > 0)
                    myHand.removeAt(0)
            } else {
                // Старшая у игрока (нас)
                cnts.nextGives += rasklad.probability

                if (nextHand.size > 0)
                    nextHand.removeAt(0)
                if (myHand.size > 0)
                    myHand.removeAt(0)
            }
        }
        //endregion
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
        cnts.myTakes /= rCnt
        cnts.myGives /= rCnt

        cnts.prevTakes /= rCnt
        cnts.prevGives /= rCnt

        cnts.nextTakes /= rCnt
        cnts.nextGives /= rCnt

        cnts.myCards /= rCnt
        cnts.prevCards /= rCnt
        cnts.nextCards /= rCnt

        // NOTE: preserved from the original C#: unlike MiserEstimation, the result is
        // never stored into Colors here, so other suits keep their previous/default values.
    }

    override fun getIntegral(): Double {
        var res = 0.0
        val trumpK = 0.95
        val otherK = 0.9
        var myTrumps = 0.0
        var myMinTakes = 10.0
        var myMaxGives = 0.0
        for (color in colors.keys) {
            val cnts = colors[color]!!

            if (color == trump) {
                // Козыри
                res += trumpK * (cnts.myTakes - cnts.myGives)
                res -= 0.05 * trumpK * (cnts.prevTakes - cnts.prevGives)
                res -= 0.05 * trumpK * (cnts.nextTakes - cnts.nextGives)
                // Козыри бережем!
                res += 0.1 * (cnts.myCards - (cnts.prevCards + cnts.nextCards))
                myTrumps += cnts.myCards
            } else {
                // Остальные
                res += otherK * (cnts.myTakes - cnts.myGives)
                res -= 0.05 * otherK * (cnts.prevTakes - cnts.prevGives)
                res -= 0.05 * otherK * (cnts.nextTakes - cnts.nextGives)

                if (cnts.myTakes < myMinTakes)
                    myMinTakes = cnts.myTakes
                if (myMaxGives < cnts.myGives)
                    myMaxGives = cnts.myGives
            }
        }
        if (myTrumps == 0.0) {
            // Бескозырка: бережём концы!
            res += 1 * myMinTakes
        }
        res += myTakes
        res -= prevTakes
        res -= nextTakes
        return res
    }
}
