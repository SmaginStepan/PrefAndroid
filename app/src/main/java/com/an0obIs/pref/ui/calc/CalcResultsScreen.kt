package com.an0obIs.pref.ui.calc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.an0obIs.pref.R
import com.an0obIs.pref.model.Calculation
import com.an0obIs.pref.ui.AccentGold
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/** Port of CalcResults.xaml.cs: the final whist totals per player. */
@Composable
fun CalcResultsScreen(calc: Calculation, onClose: () -> Unit) {
    remember(calc) {
        calc.calc()
        calc
    }
    // The original C# pattern "### ### ##0.#" used literal spaces, which ICU
    // rejects; use grouping with a space separator instead.
    val fmt = remember {
        DecimalFormat("#,##0.#", DecimalFormatSymbols().apply { groupingSeparator = ' ' })
    }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = stringResource(R.string.results_title),
            fontSize = 40.sp,
            color = AccentGold,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        for (score in calc.scores) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(text = score.name, fontSize = 24.sp, modifier = Modifier.weight(1f))
                Text(
                    text = fmt.format(score.score),
                    fontSize = 24.sp,
                    textAlign = TextAlign.Right,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Button(onClick = onClose, modifier = Modifier.padding(top = 32.dp)) {
            Text(stringResource(R.string.close))
        }
    }
}
