package com.an0obIs.pref.ai

import com.an0obIs.pref.model.Card
import kotlinx.serialization.Serializable

class Taken {
    var prevTakes: Double = 0.0
    var myTakes: Double = 0.0
    var nextTakes: Double = 0.0

    fun take(taker: Int): Taken {
        val res = Taken().also {
            it.prevTakes = prevTakes
            it.nextTakes = nextTakes
            it.myTakes = myTakes
        }
        when (taker) {
            0 -> res.myTakes++
            1 -> res.nextTakes++
            -1 -> res.prevTakes++
        }
        return res
    }

    fun addTake(taker: Int) {
        when (taker) {
            0 -> myTakes++
            1 -> nextTakes++
            -1 -> prevTakes++
            else -> throw Exception("Неверно задан берущий")
        }
    }

    fun removeTake(taker: Int) {
        when (taker) {
            0 -> myTakes--
            1 -> nextTakes--
            -1 -> prevTakes--
            else -> throw Exception("Неверно задан берущий")
        }
    }
}

class Extremums {
    var firstMaximizing: Boolean = false
    var secondMaximizing: Boolean = false
    var thirdMaximizing: Boolean = false
}

@Serializable
class PotentialDiscard {
    var firstCard: Card? = null
    var secondCard: Card? = null
    var probability: Double = 0.0
    var hash: String? = null
}

class EstimationWithCard {
    var estimation: Double = 0.0
    var bestCard: Card? = null
}
