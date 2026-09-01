package com.an0obIs.pref.model

import kotlinx.serialization.Serializable

/**
 * Replacement for WP7 IsolatedStorageSettings: a small JSON file in app storage.
 */
class AppSettings {

    @Serializable
    private class Data {
        var rules: GameRules = GameRules()
        var playerName: String = "Игрок"
        var limit: Int = 40
        var playerId: String = ""
    }

    private var data: Data = load()

    private fun load(): Data {
        return try {
            val text = PrefStorage.readText(FILE_NAME) ?: return Data()
            PrefStorage.json.decodeFromString(Data.serializer(), text)
        } catch (e: Exception) {
            Data()
        }
    }

    private fun save() {
        PrefStorage.writeText(FILE_NAME, PrefStorage.json.encodeToString(Data.serializer(), data))
    }

    var rules: GameRules
        get() = data.rules
        set(value) {
            data.rules = value
            save()
        }

    var playerName: String
        get() = data.playerName
        set(value) {
            data.playerName = value
            save()
        }

    var limit: Int
        get() = if (data.limit < 1) 40 else clampLimit(data.limit)
        set(value) {
            data.limit = clampLimit(value)
            save()
        }

    /** Stable device identity for multiplayer; doubles as the reconnect token. */
    val playerId: String
        get() {
            if (data.playerId.isEmpty()) {
                data.playerId = java.util.UUID.randomUUID().toString()
                save()
            }
            return data.playerId
        }

    /** True while the user never typed their own name. */
    val isDefaultPlayerName: Boolean
        get() = isDefaultName(data.playerName)

    companion object {
        private const val FILE_NAME = "settings.json"

        // Per-locale placeholder names; treated as "not customized" so the
        // display can follow the app language (QA M-01).
        private val DEFAULT_NAMES = setOf("", "Игрок", "Player", "Jugador")

        fun isDefaultName(name: String) = name.trim() in DEFAULT_NAMES

        // Pool limit bounds for new games and sheets (existing saved pulkas
        // keep whatever limit they were created with).
        const val MIN_LIMIT = 10
        const val MAX_LIMIT = 100

        fun clampLimit(value: Int) = value.coerceIn(MIN_LIMIT, MAX_LIMIT)
    }
}
