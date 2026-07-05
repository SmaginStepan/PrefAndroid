package com.an0obIs.pref.ui.misc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.an0obIs.pref.R
import com.an0obIs.pref.model.Calculation
import com.an0obIs.pref.ui.AccentGold
import com.an0obIs.pref.ui.game.GameTexts

/** Port of GameLog.xaml.cs: chronological list of recorded deals. */
@Composable
fun GameLogScreen(calc: Calculation) {
    val ctx = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.log_title),
            fontSize = 40.sp,
            color = AccentGold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        for (game in calc.gameLog.reversed()) {
            Text(
                text = GameTexts.resultText(ctx, game, calc),
                fontSize = 15.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}
