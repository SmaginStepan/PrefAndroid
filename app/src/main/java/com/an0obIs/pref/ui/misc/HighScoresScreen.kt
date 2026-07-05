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
import com.an0obIs.pref.model.HighScoresTable
import com.an0obIs.pref.ui.AccentGold
import java.text.DecimalFormat

/** Port of HighScores.xaml.cs. */
@Composable
fun HighScoresScreen(app: PrefApp, playerScore: Double?, onToMenu: () -> Unit) {
    val table = remember { HighScoresTable.load() }
    var version by remember { mutableIntStateOf(0) }
    var showNewRecord by remember {
        mutableStateOf(playerScore != null && table.minScore < playerScore)
    }
    var playerName by remember { mutableStateOf(AppSettings().playerName) }
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
                table.addScore(playerName, playerScore ?: 0.0)
                val settings = AppSettings()
                settings.playerName = playerName
                app.game?.calc?.scores?.get(0)?.name = playerName
                table.save()
                showNewRecord = false
                version++
            }) { Text(stringResource(R.string.save)) }
        }

        version
        for (score in table.scores) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = score.playerName,
                    fontSize = 22.sp,
                    color = if (score.lastAdded) Color(0xFFFFEB3B) else Color.Gray,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = fmt.format(score.score),
                    fontSize = 22.sp,
                    textAlign = TextAlign.Right,
                    color = if (score.lastAdded) Color(0xFFFFEB3B) else Color.Gray
                )
            }
        }

        OutlinedButton(onClick = onToMenu, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(R.string.hs_to_menu))
        }
    }
}
