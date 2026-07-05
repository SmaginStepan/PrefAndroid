package com.an0obIs.pref.ui.calc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.an0obIs.pref.R
import com.an0obIs.pref.model.Calculation
import com.an0obIs.pref.ui.AccentGold

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

/** Port of Calc.xaml: the score-sheet section menu. */
@Composable
fun CalcMenuScreen(
    calc: Calculation?,
    onLoad: () -> Unit,
    onNew3: () -> Unit,
    onNew4: () -> Unit,
    onContinue: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(
            text = stringResource(R.string.calc_title),
            fontSize = 40.sp,
            color = AccentGold,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        MenuItem(stringResource(R.string.calc_load), stringResource(R.string.calc_load_sub), onLoad)
        MenuItem(stringResource(R.string.calc_3p), stringResource(R.string.calc_3p_sub), onNew3)
        MenuItem(stringResource(R.string.calc_4p), stringResource(R.string.calc_4p_sub), onNew4)
        if (calc != null) {
            MenuItem(stringResource(R.string.calc_continue), stringResource(R.string.calc_continue_sub), onContinue)
        }
    }
}
