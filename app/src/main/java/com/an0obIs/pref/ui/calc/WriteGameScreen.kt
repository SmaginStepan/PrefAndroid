package com.an0obIs.pref.ui.calc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.an0obIs.pref.R
import com.an0obIs.pref.model.Calculation
import com.an0obIs.pref.model.GameType
import com.an0obIs.pref.ui.AccentGold

private enum class WgStep { Choose, Raspasy, Miser, GamePlayer, GameResult }

@Composable
private fun Counter(label: String, value: Int, onChange: (Int) -> Unit, marker: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = (if (marker) "► " else "") + label,
            modifier = Modifier.weight(1f),
            fontSize = 17.sp
        )
        TextButton(onClick = { onChange(value - 1) }) { Text("−", fontSize = 24.sp) }
        Text(text = value.toString(), fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.width(40.dp))
        TextButton(onClick = { onChange(value + 1) }) { Text("+", fontSize = 24.sp) }
    }
}

/** Port of WriteGame.xaml.cs: record a played deal into the score sheet by hand. */
@Composable
fun WriteGameScreen(calc: Calculation, onDone: () -> Unit) {
    var step by remember { mutableStateOf(WgStep.Choose) }
    var dealer by remember { mutableIntStateOf(calc.dealer) }
    var error by remember { mutableStateOf(false) }

    // Raspasy state
    var raspMult by remember { mutableIntStateOf(1) }
    var raspTaken by remember { mutableStateOf(List(calc.playersCount) { 0 }) }

    // Miser state
    var miserContractor by remember { mutableIntStateOf(-1) }
    var miserTaken by remember { mutableIntStateOf(0) }
    var halfMiser by remember { mutableStateOf(false) }

    // Game state
    var contract by remember { mutableIntStateOf(0) }
    var contractor by remember { mutableIntStateOf(-1) }
    var playerTaken by remember { mutableIntStateOf(0) }
    var vistTaken by remember { mutableStateOf(List(3) { 0 }) }
    var vistChecked by remember { mutableStateOf(List(3) { false }) }

    val vists = remember(contractor) {
        (0 until calc.playersCount).filter { it != contractor }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.wg_title),
            fontSize = 34.sp,
            color = AccentGold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        when (step) {
            WgStep.Choose -> {
                Text(stringResource(R.string.sheet_dealer_label), fontSize = 18.sp)
                for (i in 0 until calc.playersCount) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = dealer == i, onClick = { dealer = i })
                    ) {
                        RadioButton(selected = dealer == i, onClick = { dealer = i })
                        Text(calc.scores[i].name, fontSize = 17.sp)
                    }
                }

                val choices = listOf(
                    stringResource(R.string.wg_raspasy) to 0,
                    stringResource(R.string.wg_miser) to 1,
                    stringResource(R.string.wg_game_6) to 6,
                    stringResource(R.string.wg_game_7) to 7,
                    stringResource(R.string.wg_game_8) to 8,
                    stringResource(R.string.wg_game_9) to 9,
                    stringResource(R.string.wg_game_10) to 10
                )
                for ((label, code) in choices) {
                    Text(
                        text = label,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                calc.dealer = dealer
                                error = false
                                when (code) {
                                    0 -> {
                                        raspMult = calc.currentRaspasyMultiplier
                                        raspTaken = List(calc.playersCount) { 0 }
                                        step = WgStep.Raspasy
                                    }
                                    1 -> {
                                        miserContractor = -1
                                        miserTaken = 0
                                        halfMiser = false
                                        step = WgStep.Miser
                                    }
                                    else -> {
                                        contract = code
                                        contractor = -1
                                        step = WgStep.GamePlayer
                                    }
                                }
                            }
                            .padding(vertical = 10.dp)
                    )
                }
            }

            WgStep.Raspasy -> {
                Text(stringResource(R.string.wg_raspasy), fontSize = 24.sp)
                Counter(stringResource(R.string.wg_multiplier), raspMult, { v ->
                    raspMult = if (v < 1) 1 else v
                })
                for (i in 0 until calc.playersCount) {
                    Counter(calc.scores[i].name, raspTaken[i], { v ->
                        var nv = v
                        if (nv < 0) nv = 0
                        // на прикупе в 4-х игроках можно взять максимум 2
                        if (i == calc.dealer && calc.playersCount == 4 && nv > 2) nv = 2
                        if (raspTaken.sum() - raspTaken[i] + nv <= 10)
                            raspTaken = raspTaken.toMutableList().also { it[i] = nv }
                    }, marker = i == calc.dealer)
                }
                if (error)
                    Text(stringResource(R.string.wg_error), color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = {
                        if (raspTaken.sum() != 10 || raspMult < 1) {
                            error = true
                        } else {
                            calc.writeGame(Calculation.GameResult().also { r ->
                                r.gameType = GameType.Raspasy
                                r.dealer = calc.dealer
                                r.taken = raspTaken.withIndex().associate { (i, v) -> i to v }.toMutableMap()
                                r.multiplier = raspMult
                            })
                            onDone()
                        }
                    },
                    modifier = Modifier.padding(top = 16.dp)
                ) { Text(stringResource(R.string.save)) }
            }

            WgStep.Miser -> {
                Text(stringResource(R.string.wg_miser), fontSize = 24.sp)
                if (calc.playersCount == 4)
                    Text(stringResource(R.string.wg_dealer_fmt, calc.scores[calc.dealer].name))
                Text(stringResource(R.string.wg_who_played), fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
                for (i in 0 until calc.playersCount) {
                    if (calc.playersCount == 4 && i == calc.dealer)
                        continue
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = miserContractor == i, onClick = { miserContractor = i })
                    ) {
                        RadioButton(selected = miserContractor == i, onClick = { miserContractor = i })
                        Text(calc.scores[i].name, fontSize = 17.sp)
                    }
                }
                Counter(stringResource(R.string.wg_taken), miserTaken, { v ->
                    miserTaken = v.coerceIn(0, 10)
                })
                if (calc.playersCount == 4) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = halfMiser, onCheckedChange = { halfMiser = it })
                        Text(stringResource(R.string.wg_half_miser))
                    }
                }
                if (error)
                    Text(stringResource(R.string.wg_must_select), color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = {
                        if (miserContractor < 0) {
                            error = true
                        } else {
                            calc.writeGame(Calculation.GameResult().also { r ->
                                r.gameType = GameType.Miser
                                r.dealer = calc.dealer
                                r.contractor = miserContractor
                                r.taken = mutableMapOf(miserContractor to miserTaken)
                                r.halfWithDealer = halfMiser
                            })
                            onDone()
                        }
                    },
                    modifier = Modifier.padding(top = 16.dp)
                ) { Text(stringResource(R.string.save)) }
            }

            WgStep.GamePlayer -> {
                Text(
                    text = stringResource(
                        when (contract) {
                            6 -> R.string.wg_game_6
                            7 -> R.string.wg_game_7
                            8 -> R.string.wg_game_8
                            9 -> R.string.wg_game_9
                            else -> R.string.wg_game_10
                        }
                    ),
                    fontSize = 24.sp
                )
                Text(stringResource(R.string.wg_who_played), fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
                for (i in 0 until calc.playersCount) {
                    if (calc.playersCount == 4 && i == calc.dealer)
                        continue
                    Text(
                        text = calc.scores[i].name,
                        fontSize = 22.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                contractor = i
                                playerTaken = contract
                                vistTaken = List(3) { 0 }
                                vistChecked = List(3) { false }
                                error = false
                                step = WgStep.GameResult
                            }
                            .padding(vertical = 10.dp)
                    )
                }
            }

            WgStep.GameResult -> {
                Text(
                    text = stringResource(R.string.wg_contractor) + " " + calc.scores[contractor].name,
                    fontSize = 20.sp
                )
                Counter(
                    calc.scores[contractor].name,
                    playerTaken,
                    { v -> playerTaken = v.coerceIn(0, 10) },
                    marker = calc.dealer == contractor
                )
                for ((idx, v) in vists.withIndex()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = vistChecked[idx],
                            onCheckedChange = { c ->
                                vistChecked = vistChecked.toMutableList().also { it[idx] = c }
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Counter(
                                calc.scores[v].name + " (" + stringResource(R.string.wg_whisted) + ")",
                                vistTaken[idx],
                                { nv -> vistTaken = vistTaken.toMutableList().also { it[idx] = nv.coerceIn(0, 10) } },
                                marker = calc.dealer == v
                            )
                        }
                    }
                }
                if (error)
                    Text(stringResource(R.string.wg_error), color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = {
                        // Port of GameSums.isValid
                        val visters = vists.indices.filter { vistChecked[it] }.map { vists[it] }
                        val sumOther = vistTaken.take(vists.size).sum()
                        val sum = playerTaken + sumOther
                        var valid = playerTaken in 0..10 && vistTaken.take(vists.size).all { it in 0..10 } && sum <= 10
                        if (valid) {
                            valid = if (sum == 10 && visters.isNotEmpty()) {
                                true // сумма взяток = 10 и кто-то вистовал
                            } else if (sumOther == 0 && visters.isEmpty() && playerTaken < contract) {
                                true // игрок "стреляется"
                            } else playerTaken == contract
                                    && (sumOther == 0 || (playerTaken == 6 && sumOther == 2) || (playerTaken == 7 && sumOther == 1))
                                    && visters.isEmpty()
                        }
                        if (valid && visters.size > 2)
                            valid = false
                        if (valid && visters.size == 2 && calc.playersCount == 4 && visters.any { it == calc.dealer })
                            valid = false

                        if (!valid) {
                            error = true
                        } else {
                            calc.writeGame(Calculation.GameResult().also { r ->
                                r.gameType = GameType.Normal
                                r.contract = contract
                                r.contractor = contractor
                                r.dealer = calc.dealer
                                r.visters = visters.toMutableList()
                                r.taken = mutableMapOf(contractor to playerTaken)
                                for (i in vists.indices)
                                    r.taken[vists[i]] = vistTaken[i]
                            })
                            onDone()
                        }
                    },
                    modifier = Modifier.padding(top = 16.dp)
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
}
