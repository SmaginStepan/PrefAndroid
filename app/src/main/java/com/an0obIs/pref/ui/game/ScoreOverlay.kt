package com.an0obIs.pref.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.an0obIs.pref.R
import com.an0obIs.pref.mp.ScoreSnap
import com.an0obIs.pref.model.ScoreValueType
import com.an0obIs.pref.ui.AccentGold
import com.an0obIs.pref.ui.calc.ARROWS_3
import com.an0obIs.pref.ui.calc.ARROWS_4
import com.an0obIs.pref.ui.calc.CELLS_3
import com.an0obIs.pref.ui.calc.CELLS_4
import com.an0obIs.pref.ui.calc.LINES_3
import com.an0obIs.pref.ui.calc.LINES_4
import com.an0obIs.pref.ui.calc.NAMES_3
import com.an0obIs.pref.ui.calc.NAMES_4
import com.an0obIs.pref.ui.calc.SHEET_H
import com.an0obIs.pref.ui.calc.SHEET_W

/**
 * Between-deals score for multiplayer, drawn exactly like the single-player
 * score sheet: title, the sheet itself, and the action row underneath it.
 * Read-only; tap the sheet (or Continue) to go on. [onSave] writes the
 * standing as a regular pulka file, [onFinish] (host) ends the match.
 */
@Composable
fun ScoreOverlay(
    snap: ScoreSnap,
    modifier: Modifier = Modifier,
    onSave: (() -> Boolean)? = null,
    /** Host only: save the pulka and end the match for everyone. */
    onFinish: (() -> Unit)? = null,
    onTap: () -> Unit
) {
    val n = snap.names.size
    val cells = if (n == 4) CELLS_4 else CELLS_3
    val nameLabels = if (n == 4) NAMES_4 else NAMES_3
    val arrows = if (n == 4) ARROWS_4 else ARROWS_3
    val lines = if (n == 4) LINES_4 else LINES_3

    // a stray second tap right after closing the results view must not fall
    // through onto the sheet's tap-to-continue surface
    var tapShieldUntil by remember { mutableStateOf(0L) }
    fun shieldedTap() {
        if (android.os.SystemClock.uptimeMillis() >= tapShieldUntil) onTap()
    }

    var showResults by remember(snap) { mutableStateOf(false) }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(if (n == 4) R.string.sheet4_title else R.string.sheet3_title),
                fontSize = 30.sp,
                color = AccentGold,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { shieldedTap() }
            ) {
                val kx = maxWidth / SHEET_W.toFloat()
                val ky = maxHeight / SHEET_H.toFloat()
                fun ux(x: Double): Dp = kx * x.toFloat()
                fun uy(y: Double): Dp = ky * y.toFloat()

                val lineColor = MaterialTheme.colorScheme.onBackground
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (l in lines) {
                        drawLine(
                            color = lineColor,
                            start = Offset((l.x1 * size.width).toFloat(), (l.y1 * size.height).toFloat()),
                            end = Offset((l.x2 * size.width).toFloat(), (l.y2 * size.height).toFloat()),
                            strokeWidth = 3f
                        )
                    }
                }

                Text(
                    text = snap.limit.toString(),
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    modifier = Modifier.offset(x = ux(190.0), y = uy(253.0)).width(ux(101.0))
                )

                for (cell in cells) {
                    val value = when (cell.type) {
                        ScoreValueType.Gora -> snap.gora[cell.player]
                        ScoreValueType.Pulya -> snap.pulya[cell.player]
                        ScoreValueType.Visty -> snap.visty[cell.player][cell.refPlayer]
                    }
                    Text(
                        text = value.toString(),
                        fontSize = 19.sp,
                        textAlign = cell.align,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        modifier = Modifier.offset(x = ux(cell.x), y = uy(cell.y)).width(ux(cell.w))
                    )
                }

                for (label in nameLabels) {
                    Text(
                        text = snap.names[label.player],
                        fontSize = 15.sp,
                        textAlign = label.align,
                        color = AccentGold,
                        maxLines = 1,
                        modifier = Modifier.offset(x = ux(label.x), y = uy(label.y)).width(ux(label.w))
                    )
                }

                for (a in arrows) {
                    if (snap.dealer == a.player) {
                        Text(
                            text = if (a.up) "▲" else "▼",
                            fontSize = 22.sp,
                            color = AccentGold,
                            modifier = Modifier.offset(x = ux(a.x), y = uy(a.y))
                        )
                    }
                }
            }

            // action row under the sheet, like the single-player screen
            val btnPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = ::shieldedTap, contentPadding = btnPadding) {
                    Text(stringResource(R.string.sheet_continue), fontSize = 12.sp, maxLines = 1)
                }
                OutlinedButton(onClick = { showResults = true }, contentPadding = btnPadding) {
                    Text(stringResource(R.string.sheet_score_btn), fontSize = 12.sp, maxLines = 1)
                }
                if (onFinish != null) {
                    OutlinedButton(onClick = onFinish, contentPadding = btnPadding) {
                        Text(stringResource(R.string.mp_save_finish), fontSize = 12.sp, maxLines = 1)
                    }
                }
                if (onSave != null) {
                    var saved by remember(snap) { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { saved = onSave() },
                        enabled = !saved,
                        contentPadding = btnPadding
                    ) {
                        Text(
                            text = stringResource(
                                if (saved) R.string.game_score_saved else R.string.game_btn_save_score
                            ),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // the same final-settlement view the single-player sheet opens
        if (showResults) {
            val calc = remember(snap) { snapToCalc(snap) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* swallow taps so they don't hit the sheet below */ }
            ) {
                com.an0obIs.pref.ui.calc.CalcResultsScreen(calc) {
                    showResults = false
                    tapShieldUntil = android.os.SystemClock.uptimeMillis() + 400
                }
            }
        }
    }
}

/** The snapshot as a throwaway Calculation, enough for the final settlement. */
private fun snapToCalc(snap: ScoreSnap): com.an0obIs.pref.model.Calculation {
    val n = snap.names.size
    val c = com.an0obIs.pref.model.Calculation(n, snap.limit)
    if (snap.leningrad) c.rules.scoring = com.an0obIs.pref.model.ScoreType.Leningrad
    for (i in 0 until n) {
        c.scores[i].name = snap.names[i]
        c.scores[i].pulya = snap.pulya[i]
        c.scores[i].gora = snap.gora[i]
        for (j in 0 until n)
            if (i != j) c.scores[i].visty[j] = snap.visty[i][j]
    }
    return c
}
