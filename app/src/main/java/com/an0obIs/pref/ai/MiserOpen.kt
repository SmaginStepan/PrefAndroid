package com.an0obIs.pref.ai

class MiserOpen : OpenPlay() {
    override fun createEstimation(): Estimation {
        val est = MiserEstimation()
        est.isHidden = false
        return est
    }

    override fun isMaximizing(player: Int, contractor: Int): Boolean {
        return player == contractor
    }

    override fun createExtremum(firstMovePerformer: Int, contractor: Int): Extremums {
        return when (firstMovePerformer) {
            0 -> Extremums().also { // Мы ходим первые
                it.firstMaximizing = true
                it.secondMaximizing = false
                it.thirdMaximizing = false
            }
            -1 -> Extremums().also { // Мы ходим вторые
                it.firstMaximizing = false
                it.secondMaximizing = true
                it.thirdMaximizing = false
            }
            1 -> Extremums().also { // Мы ходим третьи
                it.firstMaximizing = false
                it.secondMaximizing = false
                it.thirdMaximizing = true
            }
            else -> throw Exception("Кто ходит первым - непонятно!")
        }
    }

    override fun getPotentialDiscard(info: AIInfo): List<PotentialDiscard>? {
        return info.potentialDiscard
    }
}
