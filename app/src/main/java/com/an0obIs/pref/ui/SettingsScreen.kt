package com.an0obIs.pref.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.core.os.LocaleListCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.an0obIs.pref.R
import com.an0obIs.pref.model.AppSettings
import com.an0obIs.pref.model.Calculation
import com.an0obIs.pref.model.ConsolationSum
import com.an0obIs.pref.model.ConsolationType
import com.an0obIs.pref.model.EndingType
import com.an0obIs.pref.model.Game
import com.an0obIs.pref.model.GameRules
import com.an0obIs.pref.model.RaspasyExit
import com.an0obIs.pref.model.RaspasyProgression
import com.an0obIs.pref.model.RulesGameType
import com.an0obIs.pref.model.ScoreType
import com.an0obIs.pref.model.VistType

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun <T> RadioGroup(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        for ((value, label) in options) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = value == selected, onClick = { onSelect(value) })
            ) {
                RadioButton(selected = value == selected, onClick = { onSelect(value) })
                Text(text = label, fontSize = 17.sp)
            }
        }
    }
}

/**
 * Port of Settings.xaml(.cs). Two modes:
 *  - forCalc = false: app settings (player name, limit, default rules)
 *  - forCalc = true: rules of the current score sheet (calc)
 */
@Composable
fun SettingsScreen(
    forCalc: Boolean,
    calc: Calculation?,
    game: Game?,
    onSaved: () -> Unit
) {
    val settings = remember { if (forCalc) null else AppSettings() }
    val sourceRules = remember {
        if (forCalc && calc != null) calc.rules else settings?.rules ?: GameRules()
    }

    var language by remember {
        mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',').substringBefore('-'))
    }
    var playerName by remember { mutableStateOf(settings?.playerName ?: "") }
    var limitText by remember { mutableStateOf((settings?.limit ?: 40).toString()) }
    var gameType by remember { mutableStateOf(sourceRules.gameType) }
    var raspProgression by remember { mutableStateOf(sourceRules.raspasyProgression) }
    var raspExit by remember { mutableStateOf(sourceRules.raspasyExit) }
    var miserRaspExit by remember { mutableStateOf(sourceRules.miserRaspExit) }
    var consolation by remember { mutableStateOf(sourceRules.consolation) }
    var vistType by remember { mutableStateOf(sourceRules.vist) }
    var prikupConsolation by remember { mutableStateOf(sourceRules.prikupConsolation) }
    // hidden values driven by the game-type presets
    var ending by remember { mutableStateOf(sourceRules.ending) }
    var scoring by remember { mutableStateOf(sourceRules.scoring) }
    var consolationBonus by remember { mutableStateOf(sourceRules.consolationBonus) }

    fun applyGameTypePreset(type: RulesGameType) {
        gameType = type
        when (type) {
            RulesGameType.Sochy -> {
                vistType = VistType.FullResponsibility
                consolation = ConsolationType.Zlob
                ending = EndingType.Each
                scoring = ScoreType.Normal
                consolationBonus = ConsolationSum.Normal
            }
            RulesGameType.Leningrad -> {
                vistType = VistType.HalfResponsibility
                consolation = ConsolationType.Gentlemen
                ending = EndingType.Sum
                scoring = ScoreType.Leningrad
                consolationBonus = ConsolationSum.Normal
            }
            RulesGameType.Rostov -> {
                raspProgression = RaspasyProgression.NoProgression1
                vistType = VistType.HalfResponsibility
                consolation = ConsolationType.Gentlemen
                ending = EndingType.Each
                scoring = ScoreType.Normal
                consolationBonus = ConsolationSum.Max10
            }
        }
    }

    fun save() {
        val rules = GameRules().also {
            it.gameType = gameType
            it.raspasyProgression = raspProgression
            it.raspasyExit = raspExit
            it.miserRaspExit = miserRaspExit
            it.consolation = consolation
            it.vist = vistType
            it.prikupConsolation = prikupConsolation
            it.ending = ending
            it.scoring = scoring
            it.consolationBonus = consolationBonus
            it.vistTakeOnRaspas = sourceRules.vistTakeOnRaspas
            it.stalindgrad = sourceRules.stalindgrad
        }
        if (settings != null) {
            settings.rules = rules
            limitText.toIntOrNull()?.let { settings.limit = it }
            if (settings.playerName != playerName && playerName.isNotBlank()) {
                settings.playerName = playerName
                game?.calc?.scores?.get(0)?.name = playerName
            }
        }
        if (forCalc && calc != null) {
            calc.rules = rules
        }
        onSaved()
        if (!forCalc) {
            // Apply the per-app language last: this may recreate the activity.
            val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',').substringBefore('-')
            if (current != language) {
                AppCompatDelegate.setApplicationLocales(
                    if (language.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                    else LocaleListCompat.forLanguageTags(language)
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = stringResource(if (forCalc) R.string.rules_title else R.string.settings_title),
            fontSize = 40.sp,
            color = AccentGold
        )

        if (!forCalc) {
            SectionLabel(stringResource(R.string.settings_language))
            RadioGroup(
                options = listOf(
                    "" to stringResource(R.string.language_system),
                    "en" to "English",
                    "ru" to "Русский",
                    "es" to "Español"
                ),
                selected = language,
                onSelect = { language = it }
            )

            SectionLabel(stringResource(R.string.settings_player_name))
            OutlinedTextField(
                value = playerName,
                onValueChange = { playerName = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            SectionLabel(stringResource(R.string.settings_limit))
            OutlinedTextField(
                value = limitText,
                onValueChange = { limitText = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        SectionLabel(stringResource(R.string.settings_game_type))
        RadioGroup(
            options = listOf(
                RulesGameType.Sochy to stringResource(R.string.settings_game_sochy),
                RulesGameType.Leningrad to stringResource(R.string.settings_game_leningrad),
                RulesGameType.Rostov to stringResource(R.string.settings_game_rostov)
            ),
            selected = gameType,
            onSelect = { applyGameTypePreset(it) }
        )

        SectionLabel(stringResource(R.string.settings_raspasy))
        if (gameType != RulesGameType.Rostov) {
            RadioGroup(
                options = listOf(
                    RaspasyProgression.NoProgression1 to stringResource(R.string.settings_progression_none),
                    RaspasyProgression.Arifm1233 to stringResource(R.string.settings_progression_arifm),
                    RaspasyProgression.Geom1244 to stringResource(R.string.settings_progression_geom)
                ),
                selected = raspProgression,
                onSelect = { raspProgression = it }
            )
        }

        SectionLabel(stringResource(R.string.settings_exit))
        RadioGroup(
            options = listOf(
                RaspasyExit.Easy6 to stringResource(R.string.settings_exit_easy),
                RaspasyExit.Med677 to stringResource(R.string.settings_exit_med),
                RaspasyExit.Hard678 to stringResource(R.string.settings_exit_hard)
            ),
            selected = raspExit,
            onSelect = { raspExit = it }
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = miserRaspExit, onCheckedChange = { miserRaspExit = it })
            Text(text = stringResource(R.string.settings_miser_exit), fontSize = 17.sp)
        }

        SectionLabel(stringResource(R.string.settings_vist))
        RadioGroup(
            options = listOf(
                ConsolationType.Zlob to stringResource(R.string.settings_consolation_zlob),
                ConsolationType.Gentlemen to stringResource(R.string.settings_consolation_gentlemen)
            ),
            selected = consolation,
            onSelect = { consolation = it }
        )
        RadioGroup(
            options = listOf(
                VistType.FullResponsibility to stringResource(R.string.settings_vist_full),
                VistType.HalfResponsibility to stringResource(R.string.settings_vist_half)
            ),
            selected = vistType,
            onSelect = { vistType = it }
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = prikupConsolation, onCheckedChange = { prikupConsolation = it })
            Text(text = stringResource(R.string.settings_prikup_consolation), fontSize = 17.sp)
        }

        Button(
            onClick = { save() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Text(text = stringResource(R.string.save))
        }
    }
}
