package com.an0obIs.pref.model

import kotlinx.serialization.Serializable

@Serializable
class HighScoresTable {

    @Serializable
    class PlayerScore {
        var playerName: String = ""
        var score: Double = 0.0
        var lastAdded: Boolean = false
    }

    var scores: MutableList<PlayerScore> = mutableListOf()

    val minScore: Double
        get() = scores.minOfOrNull { it.score } ?: 0.0

    fun fillDefaults(): HighScoresTable {
        addScore("Эйнштейн", 1000.0)
        addScore("Да Винчи", 750.0)
        addScore("Перельман", 500.0)
        addScore("Вован", 300.0)
        addScore("Настасья", 250.0)
        addScore("Алексей", 200.0)
        addScore("Андрей", 150.0)
        addScore("Григорий", 100.0)
        addScore("Ирина", 50.0)
        addScore("Степан", 0.0)
        return this
    }

    fun addScore(playerName: String, score: Double): Boolean {
        if (scores.size >= 10 && score < minScore)
            return false
        scores.add(PlayerScore().also {
            it.playerName = playerName
            it.score = score
            it.lastAdded = true
        })
        scores = scores.sortedByDescending { it.score }.take(10).toMutableList()
        return true
    }

    fun save() {
        PrefStorage.writeText(FILE_NAME, PrefStorage.json.encodeToString(serializer(), this))
    }

    companion object {
        private const val FILE_NAME = "highscores.json"

        fun load(): HighScoresTable {
            val res = if (!PrefStorage.exists(FILE_NAME)) {
                HighScoresTable().fillDefaults()
            } else {
                val text = PrefStorage.readText(FILE_NAME)!!
                PrefStorage.json.decodeFromString(serializer(), text)
            }
            for (sc in res.scores) {
                sc.lastAdded = false
            }
            return res
        }
    }
}
