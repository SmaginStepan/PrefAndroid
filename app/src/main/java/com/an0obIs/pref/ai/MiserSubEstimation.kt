package com.an0obIs.pref.ai

class MiserSubEstimation(private val rasklad: ColorRasklad) {
    var myMin = 0
    var nextMin = 0
    var prevMin = 0
    var myMin2 = 0
    var nextMin2 = 0
    var prevMin2 = 0
    var myMin3 = 0
    var nextMin3 = 0
    var prevMin3 = 0
    var myMin4 = 0
    var nextMin4 = 0
    var prevMin4 = 0
    private var myCnt = 0
    private var prevCnt = 0
    private var nextCnt = 0

    init {
        reset()
        initMins()
    }

    private fun initMins() {
        myMin = if (myCnt > 0) rasklad.myHand[0] else 0
        nextMin = if (nextCnt <= 0) 0 else rasklad.nextHand[0]
        prevMin = if (prevCnt <= 0) 0 else rasklad.prevHand[0]

        myMin2 = if (myCnt > 1) rasklad.myHand[1] else 0
        nextMin2 = if (nextCnt <= 1) 0 else rasklad.nextHand[1]
        prevMin2 = if (prevCnt <= 1) 0 else rasklad.prevHand[1]

        myMin3 = if (myCnt > 2) rasklad.myHand[2] else 0
        nextMin3 = if (nextCnt <= 2) 0 else rasklad.nextHand[2]
        prevMin3 = if (prevCnt <= 2) 0 else rasklad.prevHand[2]

        myMin4 = if (myCnt > 3) rasklad.myHand[3] else 0
        nextMin4 = if (nextCnt <= 3) 0 else rasklad.nextHand[3]
        prevMin4 = if (prevCnt <= 3) 0 else rasklad.prevHand[3]
    }

    fun getTake2(): Boolean {
        // Могут ли нам всучить за два хода (ровно за два)
        if (nextMin2 < myMin2 && prevMin2 < myMin2
            && (nextMin2 > 0 || nextMin < myMin2)
            && (prevMin2 > 0 || prevMin < myMin2)
        )
            return true // Младшая вторая у них
        if (nextMin < myMin && nextMin2 > 0 && prevMin2 == 0 && prevMin > myMin)
            return true // Младшая первая у них на след. длинной руке
        if (prevMin < myMin && prevMin2 > 0 && nextMin2 == 0 && nextMin > myMin)
            return true // Младшая первая у них на пред. длинной руке
        return false
    }

    fun getTake3(): Boolean {
        // Могут ли нам всучить за три хода (ровно за три)?
        if (nextMin3 < myMin3 && prevMin3 < myMin3
            && (nextMin3 > 0 || (nextMin2 < myMin3 && nextMin2 > 0) || nextMin < myMin3)
            && (prevMin3 > 0 || (prevMin2 < myMin3 && prevMin2 > 0) || prevMin < myMin3)
        )
            return true // Младшая третья у них

        if (nextMin < myMin && nextMin3 > 0 && prevMin > myMin && prevMin2 > myMin2)
            return true // Младшая первая у них на след. длинной руке
        if (prevMin < myMin && prevMin3 > 0 && nextMin > myMin && nextMin2 > myMin2)
            return true // Младшая первая у них на пред. длинной руке

        if (nextMin2 < myMin2 && nextMin3 > 0 && prevMin > myMin) {
            // Младшая вторая у них на след. длинной руке:
            if (nextMin2 < myMin2 && prevMin < myMin2
                && (nextMin2 > 0 || nextMin < myMin2)
                && (prevMin > 0 || prevMin < myMin2)
            )
                return true
        }
        if (prevMin2 < myMin2 && prevMin3 > 0 && nextMin > myMin) {
            // Младшая вторая у них на пред. длинной руке
            if (nextMin < myMin2 && prevMin2 < myMin2
                && (nextMin > 0 || nextMin < myMin2)
                && (prevMin2 > 0 || prevMin < myMin2)
            )
                return true
        }

        return false
    }

    fun calcColor(cnts: MiserEstimation.Color) {
        if (myCnt > 0 && (nextCnt > 0 || prevCnt > 0)) {
            // Есть ли отход (можно ли отцепиться в эту масть с первого захода)
            if (nextMin > myMin || prevMin > myMin)
                cnts.exit += rasklad.probability

            // Могут ли нам всучить?
            if (nextMin < myMin && prevMin < myMin)
                cnts.take += rasklad.probability
            if (nextMin < myMin && myMin > 0 && nextMin > 0)
                cnts.nextTakes += rasklad.probability
            if (prevMin < myMin && myMin > 0 && prevMin > 0)
                cnts.prevTakes += rasklad.probability

            if (myCnt >= 2 && (nextCnt >= 2 || prevCnt >= 2)) {
                // Можно ли отцепиться за два хода?
                if (nextMin2 > myMin2 || prevMin2 > myMin2)
                    cnts.exit2 += rasklad.probability

                // Могут ли нам всучить за два хода?
                if (getTake2())
                    cnts.take2 += rasklad.probability

                if (nextMin2 < myMin2 && myMin2 > 0 && nextMin2 > 0)
                    cnts.nextTakes += rasklad.probability
                if (prevMin2 < myMin2 && myMin2 > 0 && prevMin2 > 0)
                    cnts.prevTakes += rasklad.probability

                if (myCnt >= 3 && (nextCnt >= 3 || prevCnt >= 3)) {
                    // Можно ли отцепиться за три хода?
                    // (precedence preserved from the original C# expression)
                    if (nextMin3 > myMin3 || (prevMin3 > myMin3
                                && nextMin < myMin3 && prevMin < myMin3
                                && nextMin2 < myMin3 && prevMin2 < myMin3)
                    )
                        cnts.exit3 += rasklad.probability

                    // Могут ли нам всучить за три хода?
                    if (getTake3())
                        cnts.take3 += rasklad.probability

                    if (nextMin3 < myMin3 && myMin3 > 0 && nextMin3 > 0)
                        cnts.nextTakes += rasklad.probability
                    if (prevMin3 < myMin3 && myMin3 > 0 && prevMin3 > 0)
                        cnts.prevTakes += rasklad.probability

                    if (myCnt >= 4 && (nextCnt >= 4 || prevCnt >= 4)) {
                        // Можно ли отцепиться за четыре хода?
                        if (nextMin4 > myMin4 || prevMin4 > myMin4)
                            cnts.exit4 += rasklad.probability

                        // Могут ли нам всучить за четыре хода?
                        if (nextMin4 < myMin4 && prevMin4 < myMin4)
                            cnts.take += rasklad.probability
                    }
                }
            }
        }
    }

    fun calcTakes(): Double {
        var res = 0.0
        if (myCnt > 0 && (nextCnt > 0 || prevCnt > 0)) {
            // Могут ли нам всучить?
            if (nextMin < myMin && prevMin < myMin)
                res += rasklad.probability

            if (myCnt >= 2 && (nextCnt >= 2 || prevCnt >= 2)) {
                // Могут ли нам всучить за два хода?
                if (getTake2())
                    res += rasklad.probability

                if (myCnt >= 3 && (nextCnt >= 3 || prevCnt >= 3)) {
                    // Могут ли нам всучить за три хода?
                    if (getTake3())
                        res += rasklad.probability

                    if (myCnt >= 4 && (nextCnt >= 4 || prevCnt >= 4)) {
                        // Могут ли нам всучить за четыре хода?
                        if (nextMin4 < myMin4 && prevMin4 < myMin4)
                            res += rasklad.probability
                    }
                }
            }
        }
        return res
    }

    fun reset() {
        myCnt = rasklad.myHand.size
        prevCnt = rasklad.prevHand.size
        nextCnt = rasklad.nextHand.size
    }

    fun discardNext() {
        nextCnt--
        initMins()
    }

    fun discardPrev() {
        prevCnt--
        initMins()
    }
}
