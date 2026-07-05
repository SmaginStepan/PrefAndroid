package com.an0obIs.pref

import android.app.Application
import com.an0obIs.pref.model.Calculation
import com.an0obIs.pref.model.Game
import com.an0obIs.pref.model.PrefStorage

/**
 * Port of the WP7 App.xaml.cs: holds the current game and score sheet,
 * loads them on launch and saves them when the app goes to background.
 */
class PrefApp : Application() {

    var game: Game? = null
    var calc: Calculation? = null

    override fun onCreate() {
        super.onCreate()
        PrefStorage.init(filesDir)
        calc = try {
            Calculation.loadLast()
        } catch (e: Exception) {
            null
        }
        game = Game.loadLast()
    }

    fun saveAll() {
        calc?.saveLast()
        game?.saveLast()
    }
}
