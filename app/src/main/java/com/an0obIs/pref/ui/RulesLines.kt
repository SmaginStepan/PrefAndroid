package com.an0obIs.pref.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.an0obIs.pref.R
import com.an0obIs.pref.model.ConsolationType
import com.an0obIs.pref.model.GameRules
import com.an0obIs.pref.model.RaspasyExit
import com.an0obIs.pref.model.RaspasyProgression
import com.an0obIs.pref.model.RulesGameType
import com.an0obIs.pref.model.VistType

/**
 * The rules of a game as plain "label: value" lines. [full] adds the
 * remaining switches (a season randomizes all of them).
 */
@Composable
fun RulesLines(rules: GameRules, limit: Int, full: Boolean = false) {
    @Composable
    fun line(label: String, value: String) {
        val sep = if (label.endsWith(":")) " " else ": "
        Text(
            text = "$label$sep$value",
            fontSize = 15.sp,
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }

    line(
        stringResource(R.string.settings_game_type),
        stringResource(
            when (rules.gameType) {
                RulesGameType.Sochy -> R.string.settings_game_sochy
                RulesGameType.Leningrad -> R.string.settings_game_leningrad
                RulesGameType.Rostov -> R.string.settings_game_rostov
            }
        )
    )
    line(stringResource(R.string.settings_limit), limit.toString())
    line(
        stringResource(R.string.settings_vist),
        stringResource(
            when (rules.vist) {
                VistType.FullResponsibility -> R.string.settings_vist_full
                VistType.HalfResponsibility -> R.string.settings_vist_half
            }
        )
    )
    line(
        stringResource(R.string.settings_raspasy),
        stringResource(
            when (rules.raspasyProgression) {
                RaspasyProgression.NoProgression1 -> R.string.settings_progression_none
                RaspasyProgression.Arifm1233 -> R.string.settings_progression_arifm
                RaspasyProgression.Geom1244 -> R.string.settings_progression_geom
            }
        )
    )
    line(
        stringResource(R.string.settings_exit),
        stringResource(
            when (rules.raspasyExit) {
                RaspasyExit.Easy6 -> R.string.settings_exit_easy
                RaspasyExit.Med677 -> R.string.settings_exit_med
                RaspasyExit.Hard678 -> R.string.settings_exit_hard
            }
        )
    )
    if (full) {
        val yes = stringResource(R.string.season_yes)
        val no = stringResource(R.string.season_no)
        line(stringResource(R.string.settings_miser_exit), if (rules.miserRaspExit) yes else no)
        line(
            stringResource(R.string.season_consolation),
            stringResource(
                when (rules.consolation) {
                    ConsolationType.Zlob -> R.string.settings_consolation_zlob
                    ConsolationType.Gentlemen -> R.string.settings_consolation_gentlemen
                }
            )
        )
        line(stringResource(R.string.settings_prikup_consolation), if (rules.prikupConsolation) yes else no)
    }
}
