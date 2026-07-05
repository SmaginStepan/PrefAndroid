package com.an0obIs.pref.ai

class VistyEstimation : Estimation() {

    class Color {
        var contractTakes: Double = 0.0
        var contractGives: Double = 0.0

        var myTakes: Double = 0.0
        var myGives: Double = 0.0
        var myCardValues: Double = 0.0

        var otherTakes: Double = 0.0
        var otherGives: Double = 0.0

        var contractCards: Double = 0.0
        var myCards: Double = 0.0
        var otherCards: Double = 0.0
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

        var contractHand: MutableList<Int>
        var otherHand: MutableList<Int>

        when (contractor) {
            -1 -> {
                contractHand = rasklad.prevHand.sortedDescending().toMutableList()
                otherHand = rasklad.nextHand.sortedDescending().toMutableList()
            }
            1 -> {
                contractHand = rasklad.nextHand.sortedDescending().toMutableList()
                otherHand = rasklad.prevHand.sortedDescending().toMutableList()
            }
            else -> throw Exception("Не указан игрок!")
        }

        if (isHidden) {
            //region Вероятность расклада
            if (rasklad.coatColor == trump) {
                var ourTrump = myHand.size + otherHand.size

                // Считаем расклады, при которых у нас больше козырей, чем у игрока маловероятными
                while (ourTrump > contractHand.size) {
                    rasklad.probability /= 3
                    ourTrump--
                }
            }
            //endregion
        }

        for (card in myHand) {
            cnts.myCardValues += card * rasklad.probability // Считаем суммарную силу карт
        }

        cnts.myCards += myHand.size * rasklad.probability
        cnts.otherCards += otherHand.size * rasklad.probability
        cnts.contractCards += contractHand.size * rasklad.probability

        //region Разыгрывает игрок
        var myTmpHand = myHand.toMutableList()
        var otherTmpHand = otherHand.toMutableList()
        var contractTmpHand = contractHand.toMutableList()
        while (contractHand.size > 0 || (rasklad.coatColor == trump && (myHand.size > 0 || otherHand.size > 0))) {
            val myMax = myHand.firstOrNull() ?: 0
            val otherMax = otherHand.firstOrNull() ?: 0
            val contractMax = contractHand.firstOrNull() ?: 0

            if (contractMax > myMax && contractMax > otherMax) {
                // Старшая у игрока
                cnts.contractTakes += rasklad.probability
                // Удаляем у игрока старшую, а у нас младшие карты
                if (myHand.size > 0)
                    myHand.removeAt(myHand.size - 1)
                if (otherHand.size > 0)
                    otherHand.removeAt(otherHand.size - 1)
                if (contractHand.size > 0)
                    contractHand.removeAt(0)
            } else {
                // Старшая у нас
                cnts.contractGives += rasklad.probability

                var giveMy = false
                var giveOther = false

                // Если у нас только одна старшая, кладём её:
                if (myMax > contractMax && contractMax > otherMax)
                    giveMy = true
                else if (otherMax > contractMax && contractMax > myMax)
                    giveOther = true
                else {
                    // У нас обе старшие: кладём всегда с более короткой руки, если руки одинаковы, то младшую
                    if (myHand.size < otherHand.size)
                        giveMy = true
                    else if (otherHand.size < myHand.size)
                        giveOther = true
                    else if (myMax < otherMax)
                        giveMy = true
                    else if (otherMax < myMax)
                        giveOther = true
                }
                if (giveMy) {
                    if (myHand.size > 0)
                        myHand.removeAt(0)
                    if (otherHand.size > 0)
                        otherHand.removeAt(otherHand.size - 1)
                    if (contractHand.size > 0)
                        contractHand.removeAt(0)
                } else if (giveOther) {
                    if (myHand.size > 0)
                        myHand.removeAt(myHand.size - 1)
                    if (otherHand.size > 0)
                        otherHand.removeAt(0)
                    if (contractHand.size > 0)
                        contractHand.removeAt(0)
                } else
                    throw Exception("Не может быть!")
            }
        }
        myHand = myTmpHand
        otherHand = otherTmpHand
        contractHand = contractTmpHand
        //endregion

        //region Разыгрываем мы
        // Второго игрока игнорируем
        myTmpHand = myHand.toMutableList()
        otherTmpHand = otherHand.toMutableList()
        contractTmpHand = contractHand.toMutableList()
        while (myHand.size > 0 && (contractHand.size > 0 || rasklad.coatColor == trump)) {
            val myMax = myHand.firstOrNull() ?: 0
            val contractMax = contractHand.firstOrNull() ?: 0

            if (myMax > contractMax) {
                // Старшая у нас
                cnts.myTakes += rasklad.probability
                // Удаляем у нас старшую, а у игрока младшую карту
                if (myHand.size > 0)
                    myHand.removeAt(0)
                if (contractHand.size > 0)
                    contractHand.removeAt(contractHand.size - 1)
            } else {
                // Старшая у игрока
                cnts.myGives += rasklad.probability

                if (myHand.size > 0)
                    myHand.removeAt(0)
                if (contractHand.size > 0)
                    contractHand.removeAt(0)
            }
        }
        myHand = myTmpHand
        otherHand = otherTmpHand
        contractHand = contractTmpHand
        //endregion

        //region Разыгрывает второй вистующий
        // Нас игнорируем
        while (otherHand.size > 0 && (contractHand.size > 0 || rasklad.coatColor == trump)) {
            val otherMax = otherHand.firstOrNull() ?: 0
            val contractMax = contractHand.firstOrNull() ?: 0

            if (otherMax > contractMax) {
                cnts.otherTakes += rasklad.probability
                if (otherHand.size > 0)
                    otherHand.removeAt(0)
                if (contractHand.size > 0)
                    contractHand.removeAt(contractHand.size - 1)
            } else {
                cnts.otherGives += rasklad.probability

                if (otherHand.size > 0)
                    otherHand.removeAt(0)
                if (contractHand.size > 0)
                    contractHand.removeAt(0)
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
        cnts.contractTakes /= rCnt
        cnts.contractGives /= rCnt

        cnts.myTakes /= rCnt
        cnts.myGives /= rCnt

        cnts.otherTakes /= rCnt
        cnts.otherGives /= rCnt

        cnts.contractCards /= rCnt
        cnts.myCards /= rCnt
        cnts.otherCards /= rCnt

        cnts.myCardValues /= rCnt

        // NOTE: preserved from the original C#: the result is never stored into Colors here.
    }

    override fun getIntegral(): Double {
        var res = 0.0
        val trumpK = 0.95
        val otherK = 0.9
        var allTrumps = 0.0
        for (color in colors.keys) {
            val cnts = colors[color]!!

            if (color == trump) {
                // Козыри
                res -= trumpK * (cnts.contractTakes - cnts.contractGives)
                res += 0.05 * trumpK * (cnts.myTakes - cnts.myGives)
                res += 0.05 * trumpK * (cnts.otherTakes - cnts.otherGives)
                // Козыри бережем!
                res += 0.1 * (cnts.myCards + cnts.otherCards - cnts.contractCards)
                allTrumps = cnts.myCards + cnts.otherCards + cnts.contractCards
            } else {
                // Остальные
                res -= otherK * (cnts.contractTakes - cnts.contractGives)
                res += 0.05 * otherK * (cnts.myTakes - cnts.myGives)
                res += 0.05 * otherK * (cnts.otherTakes - cnts.otherGives)
            }

            if (isHidden) {
                // Чтобы не вводить второго вистующего в сомнение, всегда стараемся взять или сбросить минимальную карту...
                res += 0.0001 * cnts.myCardValues
            }
        }
        if (allTrumps > 0) {
            // Обычно разыгрывать не выгодно:
            if (contractor == turn) {
                res += 0.1
            }
            // Передавать ход в общем случае выгоднее всего игроку, сидящему перед игроком:
            if (contractor == -1 && turn == 1) {
                res += 0.05
            } else if (contractor == 1 && turn == 0) {
                res += 0.05
            }
        } else {
            // Бескозырка: стараемся ходить сами
            if (contractor != turn) {
                res += 0.1
            }
            if (contractor == -1 && turn == 1) {
                res += 0.05
            } else if (contractor == 1 && turn == 0) {
                res += 0.05
            }
        }

        res += myTakes * 1.001 // Себя любим чууточку больше

        if (contractor == -1)
            res -= prevTakes
        else
            res += prevTakes
        if (contractor == 1)
            res -= nextTakes
        else
            res += nextTakes

        return res
    }
}
