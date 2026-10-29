package com.an0obIs.pref.ui.misc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.an0obIs.pref.PrefApp
import com.an0obIs.pref.R
import com.an0obIs.pref.model.AppSettings
import com.an0obIs.pref.model.Seasons
import com.an0obIs.pref.ui.AccentGold
import com.an0obIs.pref.ui.RulesLines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

/**
 * The monthly season: this month's rules (fixed for everyone), your run, the
 * season board and the Play button. The run itself is offline; the network is
 * used to learn the season once, to post the total and to show the board.
 */
@Composable
fun SeasonScreen(app: PrefApp, onPlay: () -> Unit, onToMenu: () -> Unit) {
    var state by remember { mutableStateOf(Seasons.load()) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    val defaultName = stringResource(R.string.default_player_name)
    val ai1 = stringResource(R.string.ai_name_1)
    val ai2 = stringResource(R.string.ai_name_2)
    val settings = remember { AppSettings() }
    val playerName = if (settings.isDefaultPlayerName) defaultName else settings.playerName
    val myId = remember { settings.playerId }
    val fmt = remember { DecimalFormat("0.#") }

    LaunchedEffect(reload) {
        loading = true
        withContext(Dispatchers.IO) {
            val s = Seasons.refresh()
            if (s != null) {
                Seasons.submitPending(s, playerName)
                Seasons.refreshBoard(s)
            }
        }
        val fresh = Seasons.load()
        // a new season has started: last month's unfinished pool is void
        if (fresh != null && app.seasonGame?.seasonSeed?.let { it != fresh.seed } == true)
            app.seasonGame = null
        state = fresh
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.menu_season),
            fontSize = 40.sp,
            color = AccentGold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val s = state
        if (s == null) {
            Text(
                text = stringResource(if (loading) R.string.season_loading else R.string.season_need_network),
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            if (!loading) {
                Button(onClick = { reload++ }) { Text(stringResource(R.string.season_retry)) }
            }
        } else {
            val over = Seasons.isOver(s)
            val days = Seasons.daysLeft(s)
            Text(
                text = "${s.seasonId} · " + when {
                    over -> stringResource(R.string.season_over)
                    days <= 1 -> stringResource(R.string.season_last_day)
                    else -> stringResource(R.string.season_days_left_fmt, days)
                },
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(stringResource(R.string.season_rules_title), fontSize = 18.sp, color = AccentGold)
            val cfg = remember(s.seed) { Seasons.config(s) }
            RulesLines(cfg.rules, cfg.limit, full = true)

            Text(
                text = stringResource(R.string.season_progress_fmt, s.poolsClosed, s.nextDealIndex),
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = stringResource(R.string.season_total_fmt, fmt.format(s.total10 / 10.0)),
                fontSize = 20.sp,
                color = AccentGold,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            Text(
                text = stringResource(R.string.season_note),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (!over) {
                Button(onClick = {
                    if (app.seasonGame == null)
                        app.seasonGame = Seasons.newPoolGame(s, playerName, ai1, ai2)
                    app.seasonMode = true
                    onPlay()
                }) {
                    // a run in progress (a started pool or any closed one) continues
                    val resuming = s.nextDealIndex > 0 ||
                            (app.seasonGame?.seasonDealIndex ?: 0) > 0
                    Text(stringResource(if (resuming) R.string.menu_continue else R.string.season_play))
                }
            }

            Text(
                text = stringResource(R.string.season_board_title),
                fontSize = 18.sp,
                color = AccentGold,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
            if (s.board.isEmpty()) {
                Text(
                    text = stringResource(R.string.season_board_empty),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            for ((i, row) in s.board.withIndex()) {
                val mine = row.playerId == myId
                val color = if (mine) Color(0xFFFFEB3B) else Color.Gray
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text("${i + 1}.", fontSize = 18.sp, color = color, modifier = Modifier.padding(end = 8.dp))
                    Text(row.name, fontSize = 18.sp, color = color, modifier = Modifier.weight(1f))
                    Text(
                        fmt.format(row.score),
                        fontSize = 18.sp,
                        color = color,
                        textAlign = TextAlign.Right
                    )
                }
            }
        }

        OutlinedButton(onClick = onToMenu, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(R.string.hs_to_menu))
        }
    }
}
