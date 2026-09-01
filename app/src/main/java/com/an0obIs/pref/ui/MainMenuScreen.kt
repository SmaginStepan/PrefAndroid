package com.an0obIs.pref.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.an0obIs.pref.R

@Composable
private fun MenuItem(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(text = title, fontSize = 30.sp, color = MaterialTheme.colorScheme.onBackground)
        Text(text = subtitle, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    }
}

/** The rules a fresh single-player game will use, with a link to change them. */
@Composable
private fun NewGameDialog(onStart: () -> Unit, onSettings: () -> Unit, onDismiss: () -> Unit) {
    val settings = remember { com.an0obIs.pref.model.AppSettings() }
    val r = settings.rules

    @Composable
    fun line(label: String, value: String) {
        val sep = if (label.endsWith(":")) " " else ": "
        Text(
            text = "$label$sep$value",
            fontSize = 15.sp,
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_new_game)) },
        text = {
            Column {
                line(
                    stringResource(R.string.settings_game_type),
                    stringResource(
                        when (r.gameType) {
                            com.an0obIs.pref.model.RulesGameType.Sochy -> R.string.settings_game_sochy
                            com.an0obIs.pref.model.RulesGameType.Leningrad -> R.string.settings_game_leningrad
                            com.an0obIs.pref.model.RulesGameType.Rostov -> R.string.settings_game_rostov
                        }
                    )
                )
                line(stringResource(R.string.settings_limit), settings.limit.toString())
                line(
                    stringResource(R.string.settings_vist),
                    stringResource(
                        when (r.vist) {
                            com.an0obIs.pref.model.VistType.FullResponsibility -> R.string.settings_vist_full
                            com.an0obIs.pref.model.VistType.HalfResponsibility -> R.string.settings_vist_half
                        }
                    )
                )
                line(
                    stringResource(R.string.settings_raspasy),
                    stringResource(
                        when (r.raspasyProgression) {
                            com.an0obIs.pref.model.RaspasyProgression.NoProgression1 -> R.string.settings_progression_none
                            com.an0obIs.pref.model.RaspasyProgression.Arifm1233 -> R.string.settings_progression_arifm
                            com.an0obIs.pref.model.RaspasyProgression.Geom1244 -> R.string.settings_progression_geom
                        }
                    )
                )
                line(
                    stringResource(R.string.settings_exit),
                    stringResource(
                        when (r.raspasyExit) {
                            com.an0obIs.pref.model.RaspasyExit.Easy6 -> R.string.settings_exit_easy
                            com.an0obIs.pref.model.RaspasyExit.Med677 -> R.string.settings_exit_med
                            com.an0obIs.pref.model.RaspasyExit.Hard678 -> R.string.settings_exit_hard
                        }
                    )
                )
            }
        },
        confirmButton = {
            Button(onClick = onStart) { Text(stringResource(R.string.new_game_start)) }
        },
        dismissButton = {
            TextButton(onClick = onSettings) { Text(stringResource(R.string.menu_settings)) }
        }
    )
}

@Composable
fun MainMenuScreen(
    hasSavedGame: Boolean,
    onNewGame: () -> Unit,
    onContinue: () -> Unit,
    onMultiplayer: () -> Unit,
    onLearning: () -> Unit,
    onCalc: () -> Unit,
    onSettings: () -> Unit,
    onHighScores: () -> Unit,
    onDictionary: () -> Unit,
    onAbout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        var titleSize by remember { mutableStateOf(56.sp) }
        Text(
            text = stringResource(R.string.app_name),
            fontSize = titleSize,
            fontFamily = FontFamily.Serif,
            color = AccentGold,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { if (it.didOverflowWidth) titleSize *= 0.92f },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(R.string.menu_subtitle),
            fontSize = 22.sp,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            letterSpacing = 6.sp,
            color = AccentGold.copy(alpha = 0.75f),
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp, bottom = 24.dp)
        )
        var showNewGame by remember { mutableStateOf(false) }
        MenuItem(stringResource(R.string.menu_new_game), stringResource(R.string.menu_new_game_sub)) {
            showNewGame = true
        }
        if (showNewGame) {
            NewGameDialog(
                onStart = { showNewGame = false; onNewGame() },
                onSettings = { showNewGame = false; onSettings() },
                onDismiss = { showNewGame = false }
            )
        }
        if (hasSavedGame) {
            MenuItem(stringResource(R.string.menu_continue), stringResource(R.string.menu_continue_sub), onContinue)
        }
        MenuItem(stringResource(R.string.mp_menu), stringResource(R.string.mp_menu_sub), onMultiplayer)
        MenuItem(stringResource(R.string.menu_learning), stringResource(R.string.menu_learning_sub), onLearning)
        MenuItem(stringResource(R.string.menu_pulka), stringResource(R.string.menu_pulka_sub), onCalc)
        MenuItem(stringResource(R.string.menu_settings), stringResource(R.string.menu_settings_sub), onSettings)
        MenuItem(stringResource(R.string.menu_records), stringResource(R.string.menu_records_sub), onHighScores)
        MenuItem(stringResource(R.string.menu_dictionary), stringResource(R.string.menu_dictionary_sub), onDictionary)
        MenuItem(stringResource(R.string.menu_about), stringResource(R.string.menu_about_sub), onAbout)
    }
}
