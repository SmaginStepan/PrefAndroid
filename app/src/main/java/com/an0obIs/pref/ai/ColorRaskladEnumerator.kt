package com.an0obIs.pref.ai

class ColorRaskladEnumerator(
    myCards: List<Int>,
    outOfPlayCards: List<Int>,
    private val color: Int,
    private val nextMax: Int,
    private val prevMax: Int
) {
    private var opponentCards: MutableList<Int>
    private val myCards: MutableList<Int> = myCards.sorted().toMutableList()
    private var pos = 0
    private var max = 0

    private var current: ColorRasklad? = null

    init {
        opponentCards = mutableListOf()
        for (v in 7 until 15) {
            if (myCards.none { it == v } && outOfPlayCards.none { it == v }) {
                opponentCards.add(v)
            }
        }
        opponentCards = opponentCards.sorted().toMutableList()
        max = 1
        for (i in opponentCards.indices)
            max *= 2
        reset()
    }

    private fun internalNext(): ColorRasklad? {
        if (current == null)
            return null
        var next: ColorRasklad? = ColorRasklad().also {
            it.coatColor = color
            it.myHand = myCards.toMutableList()
            it.nextHand = mutableListOf()
            it.prevHand = mutableListOf()
            it.probability = 1.0
        }
        pos++
        if (pos >= max)
            next = null
        else if (prevMax == 0 || nextMax == 0)
            next = null
        else {
            var m = 1
            var n = pos
            for (i in opponentCards.indices) {
                m *= 2
                if (n % m == 0) {
                    next!!.nextHand.add(opponentCards[i])
                } else {
                    next!!.prevHand.add(opponentCards[i])
                }
                n -= n % m
            }
        }
        val res = current
        current = next
        return res
    }

    fun getNext(): ColorRasklad? = internalNext()

    fun reset() {
        current = ColorRasklad().also {
            it.coatColor = color
            it.myHand = myCards
            it.nextHand = opponentCards
            it.prevHand = mutableListOf()
            it.probability = 1.0
        }
        if (nextMax == 0) {
            current!!.prevHand = current!!.nextHand
            current!!.nextHand = mutableListOf()
        }
        pos = 0
    }

    fun moveNext(): Boolean = getNext() != null
}
