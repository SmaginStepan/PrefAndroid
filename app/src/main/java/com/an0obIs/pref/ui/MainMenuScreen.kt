package com.an0obIs.pref.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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

@Composable
fun MainMenuScreen(
    hasSavedGame: Boolean,
    onNewGame: () -> Unit,
    onContinue: () -> Unit,
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
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 56.sp,
            fontFamily = FontFamily.Serif,
            color = AccentGold,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        MenuItem(stringResource(R.string.menu_new_game), stringResource(R.string.menu_new_game_sub), onNewGame)
        if (hasSavedGame) {
            MenuItem(stringResource(R.string.menu_continue), stringResource(R.string.menu_continue_sub), onContinue)
        }
        MenuItem(stringResource(R.string.menu_learning), stringResource(R.string.menu_learning_sub), onLearning)
        MenuItem(stringResource(R.string.menu_pulka), stringResource(R.string.menu_pulka_sub), onCalc)
        MenuItem(stringResource(R.string.menu_settings), stringResource(R.string.menu_settings_sub), onSettings)
        MenuItem(stringResource(R.string.menu_records), stringResource(R.string.menu_records_sub), onHighScores)
        MenuItem(stringResource(R.string.menu_dictionary), stringResource(R.string.menu_dictionary_sub), onDictionary)
        MenuItem(stringResource(R.string.menu_about), stringResource(R.string.menu_about_sub), onAbout)
    }
}
