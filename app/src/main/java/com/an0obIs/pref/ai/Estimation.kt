package com.an0obIs.pref.ai

abstract class Estimation {
    var myTakes: Double = 0.0
    var prevTakes: Double = 0.0
    var nextTakes: Double = 0.0
    var turn: Int = 0
    var contractor: Int? = -1
    var cardsLeft: Int = 0
    var trump: Int = -1
    var move: Move? = null
    var playHistory: PlayHistory? = null

    open fun calcRasklad(givenRasklad: Rasklad) {
        for (cr in givenRasklad.byColor.values) {
            calcRasklad(cr)
            givenRasklad.probability *= cr.probability
        }
    }

    abstract fun calcRasklad(givenRasklad: ColorRasklad)

    abstract fun calcAllRasklads(color: Int, rEnumerator: ColorRaskladEnumerator)

    abstract fun getIntegral(): Double

    open fun getDebug(): String? = null
}

/** Replacement for the C# `#if DEBUG / Debug.WriteLine` blocks. */
object AiDebug {
    var enabled: Boolean = false

    fun log(message: String) {
        if (enabled)
            println(message)
    }
}
