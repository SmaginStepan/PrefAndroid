package com.an0obIs.pref

import android.app.Application
import com.an0obIs.pref.model.Calculation
import com.an0obIs.pref.model.Game
import com.an0obIs.pref.model.PrefStorage

/**
 * Port of the WP7 App.xaml.cs: holds the current game and score sheet,
 * loads them on launch and saves them when the app goes to background.
 *
 * A normal single-player game and a season run are kept side by side, so
 * playing one never overwrites the other. [game] is whichever the player is in
 * right now ([seasonMode]); the main menu always switches back to the normal one.
 */
class PrefApp : Application() {

    var normalGame: Game? = null
    var seasonGame: Game? = null

    /**
     * True while the player is inside a season run. It is remembered across a
     * process restart, so a restored screen stack lands in the right game.
     */
    @Volatile
    var seasonMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                try {
                    PrefStorage.writeText(SEASON_MODE_FILE, value.toString())
                } catch (e: Exception) {
                    android.util.Log.w("Pref", "season mode save failed", e)
                }
            }
        }

    var game: Game?
        get() = if (seasonMode) seasonGame else normalGame
        set(value) {
            if (seasonMode) seasonGame = value else normalGame = value
        }

    var calc: Calculation? = null

    override fun onCreate() {
        super.onCreate()
        PrefStorage.init(filesDir)
        calc = try {
            Calculation.loadLast()
        } catch (e: Exception) {
            null
        }
        normalGame = Game.loadLast()
        seasonGame = Game.loadSeason()
        seasonMode = try {
            PrefStorage.readText(SEASON_MODE_FILE) == "true"
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        const val SEASON_MODE_FILE = "seasonmode.txt"
    }

    fun saveAll() {
        calc?.saveLast()
        normalGame?.saveLast()
        seasonGame?.saveLast()
    }
}
