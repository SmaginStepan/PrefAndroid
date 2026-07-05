package com.an0obIs.pref.ui.calc

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.an0obIs.pref.R
import com.an0obIs.pref.model.CalcList
import com.an0obIs.pref.ui.AccentGold
import java.text.DateFormat
import java.util.Date

/** Port of LoadCalc.xaml.cs: pick a previously saved score sheet. */
@Composable
fun LoadCalcScreen(onLoad: (created: Long, playersCount: Int, limit: Int) -> Unit) {
    val calcs = remember {
        CalcList().also { it.load() }.calcs
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.load_title),
            fontSize = 40.sp,
            color = AccentGold,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
        for (calc in calcs) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLoad(calc.created, calc.playersCount, calc.limit) }
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.load_from, df.format(Date(calc.created))),
                    fontSize = 20.sp
                )
                Text(
                    text = stringResource(R.string.load_players_fmt, calc.playersCount, calc.limit),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }
}
