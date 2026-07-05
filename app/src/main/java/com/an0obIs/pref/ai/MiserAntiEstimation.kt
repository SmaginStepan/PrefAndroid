package com.an0obIs.pref.ai

class MiserAntiEstimation : Estimation() {

    private val internal: MiserEstimation = MiserEstimation().also { it.isHidden = false }

    var iamSure: Boolean = true

    private fun fillData() {
        when (contractor) {
            0 -> {
                internal.myTakes = myTakes
                internal.prevTakes = prevTakes
                internal.nextTakes = nextTakes

                internal.turn = turn
            }
            -1 -> {
                internal.prevTakes = nextTakes
                internal.myTakes = prevTakes
                internal.nextTakes = myTakes

                internal.turn = turn + 1
                if (internal.turn > 1)
                    internal.turn = -1
            }
            1 -> {
                internal.myTakes = nextTakes
                internal.prevTakes = myTakes
                internal.nextTakes = prevTakes

                internal.turn = turn - 1
                if (internal.turn < -1)
                    internal.turn = 1
            }
        }
        if (internal.myTakes > 0)
            internal.iamSure = iamSure
        internal.move = move
        internal.playHistory = playHistory
        internal.contractor = contractor
        internal.cardsLeft = cardsLeft
        internal.trump = trump
        internal.iamSure = iamSure
    }

    override fun calcRasklad(givenRasklad: ColorRasklad) {
        fillData()
        internal.calcRasklad(givenRasklad)
    }

    override fun calcAllRasklads(color: Int, rEnumerator: ColorRaskladEnumerator) {
        fillData()
        internal.calcAllRasklads(color, rEnumerator)
    }

    override fun getIntegral(): Double {
        fillData()
        return -internal.getIntegral()
    }

    override fun getDebug(): String {
        var res = ""

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

        for ((color, cnts) in internal.colors) {
            res += "$color:" + System.lineSeparator()
            res += "Exits: ${cnts.exit} ${cnts.exit2} ${cnts.exit3} ${cnts.exit4} intercepts=${cnts.intercept} risk=${cnts.risk} \r\n"
            res += "Takes: ${cnts.take} ${cnts.take2} ${cnts.take3} ${cnts.take4} = ${cnts.takes}\r\n"
            res += "Prev: to next=${cnts.prevToNext}  needed=${cnts.prevNeedToBeDiscarded}  can=${cnts.prevCanBeDiscarded}  discards=${cnts.prevDiscards}  takes=${cnts.prevTakes}\r\n"
            res += "Next: to prev=${cnts.nextToPrev}  needed=${cnts.nextNeedToBeDiscarded}  can=${cnts.nextCanBeDiscarded}  discards=${cnts.nextDiscards}  takes=${cnts.nextTakes}\r\n"
            res += "Scissor: ${cnts.scissorsTakes} ${cnts.scissorsPrevDiscards} \r\n"

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
        res += System.lineSeparator()
        res += "Alredy: prev=$prevTakes  next=$nextTakes  my=$myTakes  turn=$turn  contractor=$contractor"
        res += System.lineSeparator()
        res += "NextToPrev=$totalNextToPrev  PrevToNext=$totalPrevToNext  Takes=$totalTakes  NextDiscards=$totalNextDiscards  PrevDiscards=$totalPrevDiscards"
        return res
    }
}
