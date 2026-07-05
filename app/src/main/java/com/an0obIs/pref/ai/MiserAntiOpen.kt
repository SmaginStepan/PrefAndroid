package com.an0obIs.pref.ai

class MiserAntiOpen : OpenPlay() {
    override fun createEstimation(): Estimation {
        val est = MiserAntiEstimation()
        est.iamSure = this.iamSure
        return est
    }

    override fun isMaximizing(player: Int, contractor: Int): Boolean {
        return player != contractor
    }

    override fun createExtremum(firstMovePerformer: Int, contractor: Int): Extremums {
        when (firstMovePerformer) {
            0 -> { // Мы ходим первые
                return when (contractor) {
                    -1 -> Extremums().also { // Играющий сидит перед нами
                        it.firstMaximizing = true
                        it.secondMaximizing = true
                        it.thirdMaximizing = false
                    }
                    1 -> Extremums().also { // Играющий сидит после нас
                        it.firstMaximizing = true
                        it.secondMaximizing = false
                        it.thirdMaximizing = true
                    }
                    else -> throw Exception("Кто второй вистующий - неясно!")
                }
            }
            -1 -> { // Мы ходим вторые
                return when (contractor) {
                    -1 -> Extremums().also {
                        it.firstMaximizing = false
                        it.secondMaximizing = true
                        it.thirdMaximizing = true
                    }
                    1 -> Extremums().also {
                        it.firstMaximizing = true
                        it.secondMaximizing = true
                        it.thirdMaximizing = false
                    }
                    else -> throw Exception("Кто второй вистующий - неясно!")
                }
            }
            1 -> { // Мы ходим третьи
                return when (contractor) {
                    -1 -> Extremums().also {
                        it.firstMaximizing = true
                        it.secondMaximizing = false
                        it.thirdMaximizing = true
                    }
                    1 -> Extremums().also {
                        it.firstMaximizing = false
                        it.secondMaximizing = true
                        it.thirdMaximizing = true
                    }
                    else -> throw Exception("Кто второй вистующий - неясно!")
                }
            }
            else -> throw Exception("Кто ходит первым - непонятно!")
        }
    }

    override fun getPotentialDiscard(info: AIInfo): List<PotentialDiscard>? {
        return info.potentialDiscard
    }
}
