package com.an0obIs.pref.ui.calc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.an0obIs.pref.PrefApp
import com.an0obIs.pref.R
import com.an0obIs.pref.model.Calculation
import com.an0obIs.pref.model.ScoreValueType
import com.an0obIs.pref.ui.AccentGold

// Cell geometry in the original 480x550 canvas units.
internal data class Cell(
    val type: ScoreValueType,
    val player: Int,
    val refPlayer: Int,
    val x: Double,
    val y: Double,
    val w: Double,
    val align: TextAlign = TextAlign.Center
)

internal data class NameLabel(val player: Int, val x: Double, val y: Double, val w: Double, val align: TextAlign)
internal data class DealerArrow(val player: Int, val x: Double, val y: Double, val up: Boolean)
internal data class SheetLine(val x1: Double, val y1: Double, val x2: Double, val y2: Double)

internal val CELLS_3 = listOf(
    Cell(ScoreValueType.Visty, 1, 0, 0.0, 362.0, 71.0),
    Cell(ScoreValueType.Visty, 1, 2, 0.0, 119.0, 71.0),
    Cell(ScoreValueType.Pulya, 1, 0, 83.0, 320.0, 57.0),
    Cell(ScoreValueType.Gora, 1, 0, 139.0, 201.0, 95.0),
    Cell(ScoreValueType.Pulya, 0, 0, 190.0, 414.0, 101.0),
    Cell(ScoreValueType.Pulya, 2, 0, 332.0, 320.0, 71.0),
    Cell(ScoreValueType.Gora, 0, 0, 201.0, 324.0, 80.0),
    Cell(ScoreValueType.Gora, 2, 0, 252.0, 201.0, 93.0),
    Cell(ScoreValueType.Visty, 0, 1, 110.0, 492.0, 80.0),
    Cell(ScoreValueType.Visty, 0, 2, 300.0, 492.0, 80.0),
    Cell(ScoreValueType.Visty, 2, 0, 387.0, 356.0, 95.0),
    Cell(ScoreValueType.Visty, 2, 1, 396.0, 119.0, 80.0)
)

internal val NAMES_3 = listOf(
    NameLabel(0, 83.0, 536.0, 320.0, TextAlign.Center),
    NameLabel(1, 0.0, 17.0, 214.0, TextAlign.Left),
    NameLabel(2, 283.0, 17.0, 186.0, TextAlign.Right)
)

internal val ARROWS_3 = listOf(
    DealerArrow(0, 209.0, 510.0, up = false),
    DealerArrow(1, 7.0, 43.0, up = true),
    DealerArrow(2, 404.0, 43.0, up = true)
)

internal val LINES_3: List<SheetLine> = run {
    val s1 = 0.15; val s2 = 0.30; val s3 = 0.45
    val e1 = 0.85; val e2 = 0.70; val e3 = 0.55
    listOf(
        SheetLine(0.5, 0.1, 0.5, s3),
        SheetLine(0.0, 1.0, s3, e3),
        SheetLine(e3, e3, 1.0, 1.0),
        SheetLine(s1, 0.1, s1, e1),
        SheetLine(e1, 0.1, e1, e1),
        SheetLine(s1, e1, e1, e1),
        SheetLine(s2, 0.1, s2, e2),
        SheetLine(e2, 0.1, e2, e2),
        SheetLine(s2, e2, e2, e2),
        SheetLine(0.0, 0.5, s1, 0.5),
        SheetLine(1.0, 0.5, e1, 0.5),
        SheetLine(0.5, 0.95, 0.5, e1)
    )
}

internal val CELLS_4 = listOf(
    Cell(ScoreValueType.Visty, 1, 0, 0.0, 410.0, 71.0),
    Cell(ScoreValueType.Visty, 1, 2, 0.0, 91.0, 71.0),
    Cell(ScoreValueType.Visty, 1, 3, 0.0, 266.0, 71.0),
    Cell(ScoreValueType.Pulya, 1, 0, 67.0, 302.0, 57.0),
    Cell(ScoreValueType.Gora, 1, 0, 110.0, 219.0, 95.0),
    Cell(ScoreValueType.Pulya, 0, 0, 190.0, 423.0, 101.0),
    Cell(ScoreValueType.Pulya, 2, 0, 183.0, 89.0, 115.0),
    Cell(ScoreValueType.Pulya, 3, 0, 357.0, 216.0, 57.0),
    Cell(ScoreValueType.Gora, 0, 0, 200.0, 320.0, 80.0),
    Cell(ScoreValueType.Gora, 2, 0, 196.0, 201.0, 93.0),
    Cell(ScoreValueType.Gora, 3, 0, 280.0, 300.0, 79.0),
    Cell(ScoreValueType.Visty, 0, 1, 72.0, 492.0, 80.0),
    Cell(ScoreValueType.Visty, 0, 2, 200.0, 492.0, 80.0),
    Cell(ScoreValueType.Visty, 3, 0, 404.0, 410.0, 76.0),
    Cell(ScoreValueType.Visty, 3, 1, 404.0, 265.0, 76.0),
    Cell(ScoreValueType.Visty, 3, 2, 404.0, 91.0, 76.0),
    Cell(ScoreValueType.Visty, 0, 3, 323.0, 492.0, 83.0),
    Cell(ScoreValueType.Visty, 2, 0, 193.0, 17.0, 95.0),
    Cell(ScoreValueType.Visty, 2, 1, 72.0, 17.0, 80.0),
    Cell(ScoreValueType.Visty, 2, 3, 323.0, 17.0, 83.0)
)

internal val NAMES_4 = listOf(
    NameLabel(0, 187.0, 362.0, 112.0, TextAlign.Center),
    NameLabel(1, 72.0, 263.0, 133.0, TextAlign.Center),
    NameLabel(2, 187.0, 164.0, 118.0, TextAlign.Center),
    NameLabel(3, 280.0, 264.0, 137.0, TextAlign.Center)
)

internal val ARROWS_4 = listOf(
    DealerArrow(0, 210.0, 376.0, up = true),
    DealerArrow(1, 126.0, 284.0, up = true),
    DealerArrow(2, 211.0, 134.0, up = false),
    DealerArrow(3, 296.0, 239.0, up = false)
)

internal val LINES_4: List<SheetLine> = run {
    val s1 = 0.15; val s2 = 0.25; val s3 = 0.45
    val e1 = 0.85; val e2 = 0.75; val e3 = 0.55
    listOf(
        SheetLine(0.0, 0.0, s3, s3),
        SheetLine(1.0, 1.0, e3, e3),
        SheetLine(0.0, 1.0, s3, e3),
        SheetLine(e3, s3, 1.0, 0.0),
        SheetLine(s1, s1, s1, e1),
        SheetLine(e1, s1, e1, e1),
        SheetLine(s1, s1, e1, s1),
        SheetLine(s1, e1, e1, e1),
        SheetLine(s2, s2, s2, s3),
        SheetLine(s2, e3, s2, e2),
        SheetLine(e2, s2, e2, s3),
        SheetLine(e2, e3, e2, e2),
        SheetLine(s2, s2, e2, s2),
        SheetLine(s2, e2, e2, e2),
        SheetLine(0.0, 0.333, s1, 0.333),
        SheetLine(0.0, 0.666, s1, 0.666),
        SheetLine(1.0, 0.333, e1, 0.333),
        SheetLine(1.0, 0.666, e1, 0.666),
        SheetLine(0.333, 0.0, 0.333, s1),
        SheetLine(0.666, 0.0, 0.666, s1),
        SheetLine(0.333, 1.0, 0.333, e1),
        SheetLine(0.666, 1.0, 0.666, e1)
    )
}

internal const val SHEET_W = 480.0
internal const val SHEET_H = 550.0

private fun cellValue(calc: Calculation, cell: Cell): String = when (cell.type) {
    ScoreValueType.Gora -> calc.scores[cell.player].gora.toString()
    ScoreValueType.Pulya -> calc.scores[cell.player].pulya.toString()
    ScoreValueType.Visty -> (calc.scores[cell.player].visty[cell.refPlayer] ?: 0).toString()
}

/**
 * Port of CalcSheet3a/CalcSheet4a: the pulka sheet with editable cells.
 * @param fromGame read-only mode reached from the game table (3 players).
 */
@Composable
fun CalcSheetScreen(
    app: PrefApp,
    playersCount: Int,
    fromGame: Boolean,
    startWithSetup: Boolean,
    onHelp: () -> Unit,
    onResults: () -> Unit,
    onResultsHighscores: () -> Unit,
    onRecordGame: () -> Unit,
    onHistory: () -> Unit,
    onRules: () -> Unit,
    onContinueGame: () -> Unit
) {
    val calc = remember {
        if (fromGame) {
            app.game!!.calc
        } else {
            val existing = app.calc
            if (existing != null && existing.playersCount == playersCount) existing
            else Calculation(playersCount, 10).also { app.calc = it }
        }
    }
    var version by remember { mutableIntStateOf(0) }

    // Value-edit popup state
    var editCell by remember { mutableStateOf<Cell?>(null) }
    var editValue by remember { mutableStateOf("") }
    // Setup popup (limit + names): shown on first open of a new sheet
    var showSetup by remember { mutableStateOf(startWithSetup && !fromGame) }
    var showSaved by remember { mutableStateOf(false) }
    var showDealer by remember { mutableStateOf(false) }
    var setupNames by remember { mutableStateOf(calc.scores.map { it.name }) }
    var setupLimit by remember { mutableStateOf(calc.limit.toString()) }

    LaunchedEffect(fromGame) {
        if (fromGame && calc.isFinished) {
            onResultsHighscores()
        }
    }

    val cells = if (playersCount == 3) CELLS_3 else CELLS_4
    val names = if (playersCount == 3) NAMES_3 else NAMES_4
    val arrows = if (playersCount == 3) ARROWS_3 else ARROWS_4
    val lines = if (playersCount == 3) LINES_3 else LINES_4
    val ctx = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(if (playersCount == 3) R.string.sheet3_title else R.string.sheet4_title),
            fontSize = 30.sp,
            color = AccentGold,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            version // recompose on model change
            val kx = maxWidth / SHEET_W.toFloat()
            val ky = maxHeight / SHEET_H.toFloat()
            fun ux(x: Double): Dp = kx * x.toFloat()
            fun uy(y: Double): Dp = ky * y.toFloat()

            val lineColor = MaterialTheme.colorScheme.onBackground
            Canvas(modifier = Modifier.fillMaxSize()) {
                for (l in lines) {
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset((l.x1 * size.width).toFloat(), (l.y1 * size.height).toFloat()),
                        end = androidx.compose.ui.geometry.Offset((l.x2 * size.width).toFloat(), (l.y2 * size.height).toFloat()),
                        strokeWidth = 3f
                    )
                }
            }

            // Limit cell (opens setup)
            Text(
                text = calc.limit.toString(),
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .offset(x = ux(190.0), y = uy(253.0))
                    .width(ux(101.0))
                    .clickable(enabled = !fromGame) { showSetup = true }
            )

            for (cell in cells) {
                Text(
                    text = cellValue(calc, cell),
                    fontSize = 19.sp,
                    textAlign = cell.align,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .offset(x = ux(cell.x), y = uy(cell.y))
                        .width(ux(cell.w))
                        .clickable(enabled = !fromGame) {
                            editCell = cell
                            editValue = cellValue(calc, cell)
                        }
                )
            }

            for (n in names) {
                Text(
                    text = calc.scores[n.player].name,
                    fontSize = 15.sp,
                    textAlign = n.align,
                    color = AccentGold,
                    modifier = Modifier
                        .offset(x = ux(n.x), y = uy(n.y))
                        .width(ux(n.w))
                        .clickable(enabled = !fromGame) { showSetup = true }
                )
            }

            for (a in arrows) {
                if (calc.dealer == a.player) {
                    Text(
                        text = if (a.up) "▲" else "▼",
                        fontSize = 22.sp,
                        color = AccentGold,
                        modifier = Modifier.offset(x = ux(a.x), y = uy(a.y))
                    )
                }
            }
        }

        // Bottom action bar
        if (fromGame) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = onContinueGame) { Text(stringResource(R.string.sheet_continue)) }
                OutlinedButton(onClick = onResults) { Text(stringResource(R.string.sheet_score_btn)) }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(onClick = onHelp) { Text(stringResource(R.string.sheet_help), fontSize = 12.sp) }
                OutlinedButton(onClick = {
                    calc.save()
                    showSaved = true
                }) { Text(stringResource(R.string.sheet_save), fontSize = 12.sp) }
                OutlinedButton(onClick = onResults) { Text(stringResource(R.string.sheet_calc), fontSize = 12.sp) }
                OutlinedButton(onClick = onRecordGame) { Text(stringResource(R.string.sheet_record), fontSize = 12.sp) }
                OutlinedButton(onClick = onHistory) { Text(stringResource(R.string.sheet_history), fontSize = 12.sp) }
            }
        }
    }

    // Value editor popup
    editCell?.let { cell ->
        val title = when (cell.type) {
            ScoreValueType.Gora -> calc.scores[cell.player].name + " " + stringResource(R.string.sheet_gora)
            ScoreValueType.Pulya -> calc.scores[cell.player].name + " " + stringResource(R.string.sheet_pulya)
            ScoreValueType.Visty -> calc.scores[cell.player].name + " " +
                    stringResource(R.string.sheet_visty_on, calc.scores[cell.refPlayer].name)
        }
        val history = calc.getValueHistory(cell.type, cell.player, cell.refPlayer)
        AlertDialog(
            onDismissRequest = { editCell = null },
            title = {
                Column {
                    Text(stringResource(R.string.sheet_enter_value), fontSize = 14.sp)
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(history, fontSize = 12.sp)
                }
            },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        editValue = ((editValue.toIntOrNull() ?: 0) - 1).toString()
                    }) { Text("−", fontSize = 26.sp) }
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { editValue = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        editValue = ((editValue.toIntOrNull() ?: 0) + 1).toString()
                    }) { Text("+", fontSize = 26.sp) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    editValue.toIntOrNull()?.let {
                        calc.setValue(cell.type, it, cell.player, cell.refPlayer)
                        version++
                    }
                    editCell = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { editCell = null }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    // Setup popup (limit + names + rules)
    if (showSetup) {
        AlertDialog(
            onDismissRequest = { showSetup = false },
            title = { Text(stringResource(R.string.sheet_limit_label) + " " + stringResource(R.string.sheet_limit_hint), fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = setupLimit,
                        onValueChange = { setupLimit = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text(stringResource(R.string.sheet_limit_label)) }
                    )
                    Text(stringResource(R.string.sheet_names_label))
                    for (i in 0 until playersCount) {
                        OutlinedTextField(
                            value = setupNames[i],
                            onValueChange = { v ->
                                setupNames = setupNames.toMutableList().also { it[i] = v }
                            },
                            singleLine = true
                        )
                    }
                    OutlinedButton(onClick = {
                        for (i in 0 until playersCount)
                            calc.scores[i].name = setupNames[i]
                        setupLimit.toIntOrNull()?.let { calc.limit = it }
                        showSetup = false
                        onRules()
                    }) { Text(stringResource(R.string.sheet_rules_btn)) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    for (i in 0 until playersCount)
                        calc.scores[i].name = setupNames[i]
                    setupLimit.toIntOrNull()?.let { calc.limit = it }
                    version++
                    showSetup = false
                    showDealer = true
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSetup = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    // Saved confirmation
    if (showSaved) {
        AlertDialog(
            onDismissRequest = { showSaved = false },
            text = { Text(stringResource(R.string.sheet_saved)) },
            confirmButton = {
                Button(onClick = { showSaved = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    // Dealer selection
    if (showDealer) {
        AlertDialog(
            onDismissRequest = { showDealer = false },
            title = { Text(stringResource(R.string.sheet_dealer_label)) },
            text = {
                Column {
                    for (i in 0 until playersCount) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = calc.dealer == i, onClick = {
                                    calc.dealer = i
                                    version++
                                })
                        ) {
                            RadioButton(selected = calc.dealer == i, onClick = {
                                calc.dealer = i
                                version++
                            })
                            Text(calc.scores[i].name)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showDealer = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }
}
