package com.an0obIs.pref.ai

import com.an0obIs.pref.model.Card

class Move {
    var prevMove: Card? = null
    var myMove: Card? = null
    var nextMove: Card? = null

    val playColor: Int
        get() = firstMove?.coatColor ?: -1

    var firstMovePerformer: Int = 0

    var firstMove: Card?
        get() = when (firstMovePerformer) {
            -1 -> prevMove
            0 -> myMove
            1 -> nextMove
            else -> null
        }
        set(value) {
            when (firstMovePerformer) {
                -1 -> prevMove = value
                0 -> myMove = value
                1 -> nextMove = value
            }
        }

    var secondMove: Card?
        get() = when (firstMovePerformer) {
            1 -> prevMove
            -1 -> myMove
            0 -> nextMove
            else -> null
        }
        set(value) {
            when (firstMovePerformer) {
                1 -> prevMove = value
                -1 -> myMove = value
                0 -> nextMove = value
            }
        }

    var thirdMove: Card?
        get() = when (firstMovePerformer) {
            0 -> prevMove
            1 -> myMove
            -1 -> nextMove
            else -> null
        }
        set(value) {
            when (firstMovePerformer) {
                0 -> prevMove = value
                1 -> myMove = value
                -1 -> nextMove = value
            }
        }

    var estimation: Double = 0.0

    fun clone(): Move = Move().also {
        it.firstMovePerformer = firstMovePerformer
        it.prevMove = prevMove
        it.myMove = myMove
        it.nextMove = nextMove
        it.estimation = estimation
    }

    fun getTaker(trump: Int): Int = Helper.getTaker(myMove, prevMove, nextMove, playColor, trump)

    fun getMaxCard(trump: Int): Card? = Helper.getMaxCard(myMove, prevMove, nextMove, playColor, trump)
}

class ColoredMove {
    var prevMove: Int = 0
    var myMove: Int = 0
    var nextMove: Int = 0
}
