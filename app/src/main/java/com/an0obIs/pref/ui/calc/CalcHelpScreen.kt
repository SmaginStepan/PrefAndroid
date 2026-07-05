package com.an0obIs.pref.ui.calc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.an0obIs.pref.R
import com.an0obIs.pref.ui.AccentGold

/** Port of CalcHelp.xaml. */
@Composable
fun CalcHelpScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.help_title),
            fontSize = 40.sp,
            color = AccentGold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(stringResource(R.string.help_q), fontSize = 20.sp, modifier = Modifier.padding(bottom = 12.dp))
        Text(stringResource(R.string.help_tap), modifier = Modifier.padding(bottom = 12.dp))
        Text(stringResource(R.string.help_record, stringResource(R.string.sheet_record)), modifier = Modifier.padding(bottom = 12.dp))
        Text(stringResource(R.string.help_calc, stringResource(R.string.sheet_calc)), modifier = Modifier.padding(bottom = 12.dp))
        Text(stringResource(R.string.help_save, stringResource(R.string.sheet_save)), modifier = Modifier.padding(bottom = 12.dp))
        Text(stringResource(R.string.help_dealer))
    }
}
