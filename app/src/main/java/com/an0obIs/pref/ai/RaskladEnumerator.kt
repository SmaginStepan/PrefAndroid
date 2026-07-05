package com.an0obIs.pref.ai

import com.an0obIs.pref.model.Card

class RaskladEnumerator(
    private val myCards: List<Card>,
    private val outOfPlayCards: List<Card>,
    private val nextMax: Int,
    private val prevMax: Int,
    private val nextNotExists: List<Int>,
    private val prevNotExists: List<Int>,
    private val unknownPrikupLength: Int,
    private val contractor: Int?
) {
    private val enumerators: MutableMap<Int, ColorRaskladEnumerator> = mutableMapOf()
    private var current: Rasklad? = null

    init {
        for (i in 0 until 4) {
            val cards = myCards.filter { it.coatColor == i }.sortedBy { it.value }.map { it.value }
            val outOfPlay = outOfPlayCards.filter { it.coatColor == i }.sortedBy { it.value }.map { it.value }
            var next = nextMax
            var prev = prevMax
            if (nextNotExists.contains(i))
                next = 0
            if (prevNotExists.contains(i))
                prev = 0
            val enumerator = ColorRaskladEnumerator(cards, outOfPlay, i, next, prev)
            enumerators[i] = enumerator
        }
        reset()
    }

    private fun internalNext(): Rasklad? {
        if (current == null)
            return null

        var next: Rasklad? = Rasklad()
        for (j in 1 until 4) {
            next!!.byColor[j] = current!!.byColor[j]!!
        }
        var stop = false
        var i = 0
        while (!stop && i < 4) {
            stop = true
            var cr = enumerators[i]!!.getNext()
            if (cr != null) {
                next!!.byColor[i] = cr
            } else {
                enumerators[i]!!.reset()
                cr = enumerators[i]!!.getNext()
                if (cr != null)
                    next!!.byColor[i] = cr
                i++
                stop = false
            }
        }
        if (i == 4)
            next = null

        val res = current
        current = next
        return res
    }

    private fun checkRaskad(rasklad: Rasklad): Boolean {
        var prevLen = 0
        var nextLen = 0
        var overdraft = 0
        for ((key, cr) in rasklad.byColor) {
            nextLen += cr.nextHand.size
            prevLen += cr.prevHand.size
            if (contractor == -1 && prevNotExists.contains(key) && !nextNotExists.contains(key))
                overdraft = unknownPrikupLength
            if (contractor == 1 && nextNotExists.contains(key) && !prevNotExists.contains(key))
                overdraft = unknownPrikupLength
        }
        if (prevLen > (prevMax + unknownPrikupLength) || nextLen > (nextMax + unknownPrikupLength))
            return false
        if (contractor == -1 && nextLen > (nextMax + overdraft))
            return false // играет предыдущий, значит следующий не может иметь лишних карт
        if (contractor == 1 && prevLen > (prevMax + overdraft))
            return false // играет следующий, значит предыдущий не может иметь лишних карт

        return true
    }

    fun getNext(): Rasklad? {
        var res = internalNext()
        while (res != null && !checkRaskad(res)) {
            res = internalNext()
        }
        return res
    }

    fun reset() {
        current = Rasklad()
        for ((key, er) in enumerators) {
            er.reset()
            val r = er.getNext()
            if (r != null)
                current!!.byColor[key] = r
        }
    }
}
