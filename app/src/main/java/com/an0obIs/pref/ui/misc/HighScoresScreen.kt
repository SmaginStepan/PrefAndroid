package com.an0obIs.pref.ui.misc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.an0obIs.pref.model.GlobalScores
import com.an0obIs.pref.ui.AccentGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

// The seeded leaderboard names are stored in Russian; outside the Russian
// locale they are shown transliterated (QA M-02).
private val SEEDED_NAME_TRANSLIT = mapOf(
    "Эйнштейн" to "Einstein",
    "Да Винчи" to "Da Vinci",
    "Перельман" to "Perelman",
    "Вован" to "Vovan",
    "Настасья" to "Nastasya",
    "Алексей" to "Alexey",
    "Андрей" to "Andrey",
    "Григорий" to "Grigory",
    "Ирина" to "Irina"
)

/**
 * The global leaderboard (ScoreServer): the last cached board shows
 * immediately — the classic seeded names before the first fetch — and a
 * background sync flushes queued scores and refreshes the list. The game
 * itself stays fully offline; scores queue until the network allows.
 */
@Composable
fun HighScoresScreen(app: PrefApp, playerScore: Double?, onToMenu: () -> Unit) {
    var rows by remember { mutableStateOf(GlobalScores.cached()) }
    var syncTick by remember { mutableIntStateOf(0) }
    val myId = remember { AppSettings().playerId }
    val defaultName = stringResource(R.string.default_player_name)
    var playerName by remember {
        mutableStateOf(AppSettings().let { if (it.isDefaultPlayerName) defaultName else it.playerName })
    }
    // a winning score that beats the visible board asks for the name first;
    // anything else queues right away under the current name
    var showNewRecord by remember {
        mutableStateOf(
            playerScore != null && playerScore >= GlobalScores.MIN_SCORE &&
                    (rows.size < 10 || playerScore * 10 > rows.minOf { it.score10 })
        )
    }
    LaunchedEffect(Unit) {
        if (playerScore != null && playerScore >= GlobalScores.MIN_SCORE && !showNewRecord) {
            GlobalScores.enqueue(playerName, playerScore)
            syncTick++
        } else if (playerScore == null || playerScore < GlobalScores.MIN_SCORE) {
            syncTick++
        }
    }
    LaunchedEffect(syncTick) {
        if (syncTick > 0) {
            val fresh = withContext(Dispatchers.IO) { GlobalScores.sync() }
            rows = fresh ?: GlobalScores.cached()
        }
    }

    val isRussian = androidx.compose.ui.platform.LocalConfiguration.current
        .locales[0].language == "ru"
    val fmt = remember { DecimalFormat("0.#") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.hs_title),
            fontSize = 40.sp,
            color = AccentGold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (showNewRecord) {
            Text(stringResource(R.string.hs_new_record), fontSize = 18.sp)
            OutlinedTextField(
                value = playerName,
                onValueChange = { playerName = it },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
            Button(onClick = {
                val settings = AppSettings()
                settings.playerName = playerName
                app.game?.calc?.scores?.get(0)?.name = playerName
                GlobalScores.enqueue(playerName, playerScore ?: 0.0)
                showNewRecord = false
                syncTick++
            }) { Text(stringResource(R.string.save)) }
        }

        for (row in rows) {
            val mine = row.playerId == myId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = if (isRussian) row.name
                    else SEEDED_NAME_TRANSLIT[row.name] ?: row.name,
                    fontSize = 22.sp,
                    color = if (mine) Color(0xFFFFEB3B) else Color.Gray,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = fmt.format(row.score),
                    fontSize = 22.sp,
                    textAlign = TextAlign.Right,
                    color = if (mine) Color(0xFFFFEB3B) else Color.Gray
                )
            }
        }

        OutlinedButton(onClick = onToMenu, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(R.string.hs_to_menu))
        }
    }
}
