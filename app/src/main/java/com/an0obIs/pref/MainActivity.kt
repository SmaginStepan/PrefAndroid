package com.an0obIs.pref

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.an0obIs.pref.model.Calculation
import com.an0obIs.pref.model.Game
import com.an0obIs.pref.ui.MainMenuScreen
import com.an0obIs.pref.ui.PlaceholderScreen
import com.an0obIs.pref.ui.PrefTheme
import com.an0obIs.pref.ui.SettingsScreen
import com.an0obIs.pref.ui.calc.CalcHelpScreen
import com.an0obIs.pref.ui.calc.CalcMenuScreen
import com.an0obIs.pref.ui.calc.CalcResultsScreen
import com.an0obIs.pref.ui.calc.CalcSheetScreen
import com.an0obIs.pref.ui.calc.LoadCalcScreen
import com.an0obIs.pref.ui.calc.WriteGameScreen
import com.an0obIs.pref.ui.game.GameScreen
import com.an0obIs.pref.ui.misc.AboutScreen
import com.an0obIs.pref.ui.misc.DictionaryScreen
import com.an0obIs.pref.ui.misc.GameLogScreen
import com.an0obIs.pref.ui.misc.HighScoresScreen
import com.an0obIs.pref.ui.misc.LearningScreen
import com.an0obIs.pref.ui.mp.MultiplayerScreen

object Routes {
    const val MENU = "menu"
    const val GAME = "game"
    const val SETTINGS = "settings"
    const val CALC_RULES = "calcrules"
    const val CALC = "calc"
    const val SHEET = "sheet" // sheet/{players}?setup={setup}
    const val SHEET_GAME = "sheetgame"
    const val WRITE_GAME = "writegame"
    const val RESULTS = "results"
    const val RESULTS_HIGH = "resultshigh"
    const val LOAD_CALC = "loadcalc"
    const val CALC_HELP = "calchelp"
    const val HIGH_SCORES = "highscores"
    const val DICTIONARY = "dictionary"
    const val LEARNING = "learning"
    const val ABOUT = "about"
    const val GAME_LOG = "gamelog"
    const val MULTIPLAYER = "multiplayer"
}

class MainActivity : AppCompatActivity() {

    private val app: PrefApp
        get() = application as PrefApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PrefTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        // Keep all screens out from under the status bar, the
                        // navigation bar/gesture area and display cutouts.
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    PrefNavHost()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        app.saveAll()
    }

    @Composable
    private fun PrefNavHost() {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = Routes.MENU) {
            composable(Routes.MENU) {
                MainMenuScreen(
                    hasSavedGame = app.game != null,
                    onNewGame = {
                        app.game = Game.create(
                            getString(R.string.ai_name_1),
                            getString(R.string.ai_name_2)
                        )
                        navController.navigate(Routes.GAME)
                    },
                    onContinue = { navController.navigate(Routes.GAME) },
                    onMultiplayer = { navController.navigate(Routes.MULTIPLAYER) },
                    onLearning = { navController.navigate(Routes.LEARNING) },
                    onCalc = { navController.navigate(Routes.CALC) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onHighScores = { navController.navigate(Routes.HIGH_SCORES) },
                    onDictionary = { navController.navigate(Routes.DICTIONARY) },
                    onAbout = { navController.navigate(Routes.ABOUT) }
                )
            }

            composable(Routes.GAME) {
                GameScreen(
                    app = app,
                    onShowScore = { navController.navigate(Routes.SHEET_GAME) }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    forCalc = false,
                    calc = app.calc,
                    game = app.game,
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(Routes.CALC_RULES) {
                SettingsScreen(
                    forCalc = true,
                    calc = app.calc,
                    game = app.game,
                    onSaved = { navController.popBackStack() }
                )
            }

            composable(Routes.CALC) {
                CalcMenuScreen(
                    calc = app.calc,
                    onLoad = { navController.navigate(Routes.LOAD_CALC) },
                    onNew3 = {
                        app.calc = null
                        navController.navigate("${Routes.SHEET}/3?setup=true")
                    },
                    onNew4 = {
                        app.calc = null
                        navController.navigate("${Routes.SHEET}/4?setup=true")
                    },
                    onContinue = {
                        val pc = app.calc?.playersCount ?: 3
                        navController.navigate("${Routes.SHEET}/$pc?setup=false")
                    }
                )
            }

            composable(
                route = "${Routes.SHEET}/{players}?setup={setup}",
                arguments = listOf(
                    navArgument("players") { type = NavType.IntType },
                    navArgument("setup") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { entry ->
                val players = entry.arguments?.getInt("players") ?: 3
                val setup = entry.arguments?.getBoolean("setup") ?: false
                CalcSheetScreen(
                    app = app,
                    playersCount = players,
                    fromGame = false,
                    startWithSetup = setup,
                    onHelp = { navController.navigate(Routes.CALC_HELP) },
                    onResults = { navController.navigate(Routes.RESULTS) },
                    onResultsHighscores = { navController.navigate(Routes.RESULTS_HIGH) },
                    onRecordGame = { navController.navigate(Routes.WRITE_GAME) },
                    onHistory = { navController.navigate(Routes.GAME_LOG) },
                    onRules = { navController.navigate(Routes.CALC_RULES) },
                    onContinueGame = { navController.popBackStack() }
                )
            }

            composable(Routes.SHEET_GAME) {
                CalcSheetScreen(
                    app = app,
                    playersCount = 3,
                    fromGame = true,
                    startWithSetup = false,
                    onHelp = { navController.navigate(Routes.CALC_HELP) },
                    onResults = { navController.navigate(Routes.RESULTS) },
                    onResultsHighscores = {
                        navController.navigate(Routes.RESULTS_HIGH) {
                            popUpTo(Routes.MENU)
                        }
                    },
                    onRecordGame = { navController.navigate(Routes.WRITE_GAME) },
                    onHistory = { navController.navigate(Routes.GAME_LOG) },
                    onRules = { navController.navigate(Routes.CALC_RULES) },
                    onContinueGame = { navController.popBackStack() }
                )
            }

            composable(Routes.WRITE_GAME) {
                val calc = app.calc
                if (calc == null) {
                    PlaceholderScreen("")
                } else {
                    WriteGameScreen(calc = calc, onDone = { navController.popBackStack() })
                }
            }

            composable(Routes.RESULTS) {
                val calc = app.calc ?: app.game?.calc
                if (calc == null) {
                    PlaceholderScreen("")
                } else {
                    CalcResultsScreen(calc = calc, onClose = { navController.popBackStack() })
                }
            }

            composable(Routes.RESULTS_HIGH) {
                val game = app.game
                val calc = game?.calc
                if (calc == null) {
                    PlaceholderScreen("")
                } else {
                    CalcResultsScreen(
                        calc = calc,
                        onClose = {
                            val score = calc.scores[0].score
                            app.game = null
                            navController.navigate("${Routes.HIGH_SCORES}?score=$score") {
                                popUpTo(Routes.MENU)
                            }
                        }
                    )
                }
            }

            composable(Routes.LOAD_CALC) {
                LoadCalcScreen(onLoad = { created, players, limit ->
                    val loaded = Calculation.load(created, players, limit)
                    if (loaded != null) {
                        app.calc = loaded
                        navController.navigate("${Routes.SHEET}/$players?setup=false") {
                            popUpTo(Routes.CALC)
                        }
                    }
                })
            }

            composable(Routes.CALC_HELP) { CalcHelpScreen() }

            composable(
                route = "${Routes.HIGH_SCORES}?score={score}",
                arguments = listOf(navArgument("score") {
                    type = NavType.StringType
                    defaultValue = ""
                })
            ) { entry ->
                val score = entry.arguments?.getString("score")?.toDoubleOrNull()
                HighScoresScreen(
                    app = app,
                    playerScore = score,
                    onToMenu = {
                        navController.popBackStack(Routes.MENU, inclusive = false)
                    }
                )
            }
            composable(Routes.DICTIONARY) { DictionaryScreen() }
            composable(Routes.LEARNING) {
                LearningScreen(onFinished = { navController.popBackStack() })
            }
            composable(Routes.ABOUT) {
                val version = try {
                    packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
                } catch (e: Exception) {
                    "1.0"
                }
                AboutScreen(versionName = version)
            }
            composable(Routes.MULTIPLAYER) {
                MultiplayerScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.GAME_LOG) {
                val calc = app.calc ?: app.game?.calc
                if (calc == null) {
                    PlaceholderScreen("")
                } else {
                    GameLogScreen(calc = calc)
                }
            }
        }
    }
}
