package com.an0obIs.pref.ai

import java.util.ArrayDeque

class ColorRasklad {

    fun remove(move: ColoredMove) {
        remove(move.myMove, move.nextMove, move.prevMove)
    }

    fun remove(myCard: Int, nextCard: Int, prevCard: Int) {
        val move = ColoredMove().also {
            it.myMove = myCard
            it.nextMove = nextCard
            it.prevMove = prevCard
        }
        if (prevHand.contains(prevCard))
            prevHand.remove(prevCard)
        else
            move.prevMove = 0
        if (myHand.contains(myCard))
            myHand.remove(myCard)
        else
            move.myMove = 0
        if (nextHand.contains(nextCard))
            nextHand.remove(nextCard)
        else
            move.nextMove = 0
        removed.push(move)
    }

    fun popLast() {
        val move = removed.pop()
        restoreMove(move)
    }

    private fun restoreMove(move: ColoredMove) {
        if (move.prevMove > 0 && !prevHand.contains(move.prevMove))
            prevHand.add(move.prevMove)
        if (move.myMove > 0 && !myHand.contains(move.myMove))
            myHand.add(move.myMove)
        if (move.nextMove > 0 && !nextHand.contains(move.nextMove))
            nextHand.add(move.nextMove)
    }

    fun restore() {
        for (move in removed) {
            restoreMove(move)
        }
        removed.clear()
    }

    private val removed = ArrayDeque<ColoredMove>()

    var coatColor: Int = 0
    var prevHand: MutableList<Int> = mutableListOf()
    var myHand: MutableList<Int> = mutableListOf()
    var nextHand: MutableList<Int> = mutableListOf()
    var probability: Double = 0.0

    fun getRaskladForContractor(contractor: Int): ColorRasklad {
        if (contractor == 0) {
            return this
        }
        if (contractor == -1)
            return ColorRasklad().also {
                it.probability = probability
                it.myHand = prevHand
                it.prevHand = nextHand
                it.nextHand = myHand
            }
        if (contractor == 1)
            return ColorRasklad().also {
                it.probability = probability
                it.myHand = nextHand
                it.prevHand = myHand
                it.nextHand = prevHand
            }
        throw Exception("Неправильно задан играющий!")
    }
}
