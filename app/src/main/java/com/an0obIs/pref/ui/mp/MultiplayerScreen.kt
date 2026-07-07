package com.an0obIs.pref.ui.mp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.an0obIs.pref.R
import com.an0obIs.pref.model.RulesGameType
import com.an0obIs.pref.net.ConnState
import com.an0obIs.pref.net.RoomInfo
import com.an0obIs.pref.ui.AccentGold

@Composable
private fun noticeText(code: String): String = when (code) {
    "kicked" -> stringResource(R.string.mp_err_kicked)
    "room_closed" -> stringResource(R.string.mp_err_room_closed)
    "bad_password" -> stringResource(R.string.mp_err_bad_password)
    "room_full" -> stringResource(R.string.mp_err_room_full)
    "playing" -> stringResource(R.string.mp_err_playing)
    else -> stringResource(R.string.mp_err_generic, code)
}

@Composable
private fun rulesSummary(vm: LobbyViewModel, room: RoomInfo): String {
    val parsed = vm.parseRules(room.rules) ?: return ""
    val type = when (parsed.gameRules.gameType) {
        RulesGameType.Sochy -> stringResource(R.string.settings_game_sochy)
        RulesGameType.Leningrad -> stringResource(R.string.settings_game_leningrad)
        RulesGameType.Rostov -> stringResource(R.string.settings_game_rostov)
    }
    return "$type · ${parsed.limit}"
}

@Composable
fun MultiplayerScreen(onBack: () -> Unit) {
    val vm: LobbyViewModel = viewModel()
    LaunchedEffect(Unit) { vm.start() }

    val room = vm.currentRoom
    when {
        room == null -> LobbyView(vm)
        vm.started -> if (vm.isHost) MpHostScreen(vm, room) else MpGuestScreen(vm)
        else -> RoomView(vm, room, onBack)
    }

    vm.notice?.let { code ->
        AlertDialog(
            onDismissRequest = { vm.notice = null },
            text = { Text(noticeText(code)) },
            confirmButton = {
                Button(onClick = { vm.notice = null }) { Text(stringResource(R.string.close)) }
            }
        )
    }
}

/** The host runs the real game table on top of HostGameSession. */
@Composable
private fun MpHostScreen(lobbyVm: LobbyViewModel, room: RoomInfo) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext
            as com.an0obIs.pref.PrefApp
    val config = remember(room.id) {
        val names = (0 until room.maxSeats).map { i -> room.seats.getOrNull(i)?.name ?: "?" }
        val kinds = (0 until room.maxSeats).map { i ->
            val seat = room.seats.getOrNull(i)
            when {
                i == 0 -> com.an0obIs.pref.mp.SeatKind.LOCAL
                seat?.kind == "bot" -> com.an0obIs.pref.mp.SeatKind.BOT
                else -> com.an0obIs.pref.mp.SeatKind.REMOTE
            }
        }
        val roomRules = lobbyVm.parseRules(room.rules)
        com.an0obIs.pref.ui.game.HostedConfig(
            names = names,
            seatKinds = kinds,
            initialCalc = lobbyVm.loadedCalc,
            rules = roomRules?.gameRules,
            limit = roomRules?.limit,
            sendToSeat = { seat, state ->
                lobbyVm.sendGameToSeat(
                    seat,
                    com.an0obIs.pref.mp.gameJson.encodeToJsonElement(
                        com.an0obIs.pref.mp.GameMsg.serializer(), state
                    )
                )
            },
            acts = kotlinx.coroutines.flow.flow {
                lobbyVm.playerActs.collect { (seat, el) ->
                    try {
                        val msg = com.an0obIs.pref.mp.gameJson.decodeFromJsonElement(
                            com.an0obIs.pref.mp.GameMsg.serializer(), el
                        )
                        if (msg is com.an0obIs.pref.mp.GameMsg.Act) emit(seat to msg)
                    } catch (e: Exception) {
                        android.util.Log.w("PrefNet", "bad act payload", e)
                    }
                }
            }
        )
    }
    com.an0obIs.pref.ui.game.GameScreen(app = app, onShowScore = {}, hostedConfig = config)
}

@Composable
private fun LobbyView(vm: LobbyViewModel) {
    var showCreate by remember { mutableStateOf(false) }
    var joinFor by remember { mutableStateOf<RoomInfo?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = stringResource(R.string.mp_title),
            fontSize = 40.sp,
            color = AccentGold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        when (vm.conn) {
            ConnState.Connecting, ConnState.Disconnected -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    Text(
                        stringResource(
                            if (vm.conn == ConnState.Connecting) R.string.mp_connecting
                            else R.string.mp_disconnected
                        )
                    )
                }
            }
            ConnState.Connected -> {
                Button(
                    onClick = { showCreate = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) { Text(stringResource(R.string.mp_create)) }

                if (vm.rooms.isEmpty()) {
                    Text(
                        stringResource(R.string.mp_no_rooms),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 16.dp)
                    )
                } else {
                    LazyColumn {
                        items(vm.rooms, key = { it.id }) { r ->
                            val occupied = r.seats.count { it != null }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = r.phase == "open") { joinFor = r }
                                    .padding(vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = (if (r.hasPassword) "🔒 " else "") + r.name,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = stringResource(R.string.mp_players_fmt, occupied, r.maxSeats),
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.mp_host_fmt, r.hostName) +
                                            "  ·  " + rulesSummary(vm, r),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateRoomDialog(
            defaultPlayerName = vm.myName,
            onDismiss = { showCreate = false },
            onCreate = { playerName, name, seats, password, preset, limit ->
                vm.createRoom(playerName, name, seats, password, preset, limit)
                showCreate = false
            }
        )
    }

    joinFor?.let { r ->
        var playerName by remember(r.id) { mutableStateOf(vm.myName) }
        var pwd by remember(r.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { joinFor = null },
            title = { Text(r.name, fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = playerName,
                        onValueChange = { playerName = it.take(24) },
                        singleLine = true,
                        label = { Text(stringResource(R.string.mp_your_name)) }
                    )
                    if (r.hasPassword) {
                        Text(stringResource(R.string.mp_password_title), fontSize = 14.sp)
                        OutlinedTextField(
                            value = pwd,
                            onValueChange = { pwd = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.mp_password_label)) }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = playerName.isNotBlank(),
                    onClick = {
                        vm.join(r.id, pwd, playerName)
                        joinFor = null
                    }
                ) { Text(stringResource(R.string.mp_join)) }
            },
            dismissButton = {
                TextButton(onClick = { joinFor = null }) { Text(stringResource(R.string.close)) }
            }
        )
    }
}

@Composable
private fun CreateRoomDialog(
    defaultPlayerName: String,
    onDismiss: () -> Unit,
    onCreate: (playerName: String, name: String, seats: Int, password: String?, preset: RulesGameType, limit: Int) -> Unit
) {
    var playerName by remember { mutableStateOf(defaultPlayerName) }
    var name by remember { mutableStateOf(defaultPlayerName) }
    var seats by remember { mutableIntStateOf(3) }
    var password by remember { mutableStateOf("") }
    var preset by remember { mutableStateOf(RulesGameType.Sochy) }
    var limitText by remember { mutableStateOf("10") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mp_create), fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = playerName,
                    onValueChange = { playerName = it.take(24) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.mp_your_name)) }
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(32) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.mp_room_name)) }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.mp_seats), modifier = Modifier.padding(end = 8.dp))
                    for (n in listOf(3, 4)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.selectable(selected = seats == n, onClick = { seats = n })
                        ) {
                            RadioButton(selected = seats == n, onClick = { seats = n })
                            Text("$n")
                        }
                    }
                }
                Column {
                    for (p in RulesGameType.entries) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = preset == p, onClick = { preset = p })
                        ) {
                            RadioButton(selected = preset == p, onClick = { preset = p })
                            Text(
                                when (p) {
                                    RulesGameType.Sochy -> stringResource(R.string.settings_game_sochy)
                                    RulesGameType.Leningrad -> stringResource(R.string.settings_game_leningrad)
                                    RulesGameType.Rostov -> stringResource(R.string.settings_game_rostov)
                                },
                                fontSize = 15.sp
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text(stringResource(R.string.sheet_limit_label)) }
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(32) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.mp_password_optional)) }
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && playerName.isNotBlank(),
                onClick = {
                    onCreate(playerName.trim(), name.trim(), seats, password, preset, limitText.toIntOrNull() ?: 10)
                }
            ) { Text(stringResource(R.string.mp_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

@Composable
private fun RoomView(vm: LobbyViewModel, room: RoomInfo, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(text = room.name, fontSize = 32.sp, color = AccentGold)
        Text(
            text = stringResource(R.string.mp_room_code_fmt, room.id) + "  ·  " + rulesSummary(vm, room),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        for (i in 0 until room.maxSeats) {
            val seat = room.seats.getOrNull(i)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                Text(
                    text = when {
                        seat == null -> "—"
                        else -> buildString {
                            append(seat.name)
                            if (i == vm.mySeat) append(" " + stringResource(R.string.mp_you))
                            if (seat.kind == "bot") append(" · " + stringResource(R.string.mp_bot))
                            if (seat.kind == "human" && !seat.connected)
                                append(" · " + stringResource(R.string.mp_offline))
                        }
                    },
                    fontSize = 19.sp,
                    color = if (seat == null)
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                if (vm.isHost && i > 0 && seat != null && !vm.started) {
                    TextButton(onClick = { vm.kick(i) }) {
                        Text(stringResource(R.string.mp_kick), fontSize = 13.sp)
                    }
                }
            }
        }

        if (vm.started) {
            Text(
                text = stringResource(R.string.mp_started_stub),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else if (!vm.isHost) {
            Text(
                text = stringResource(R.string.mp_waiting_host),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 24.dp)
            )
        }

        // resume from a saved pulka with the same number of players
        var showPicker by remember { mutableStateOf(false) }
        if (vm.isHost && !vm.started) {
            val loaded = vm.loadedCalc
            if (loaded == null) {
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text(stringResource(R.string.mp_load_scores)) }
            } else {
                val df = remember { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                R.string.mp_scores_loaded_fmt,
                                df.format(java.util.Date(loaded.created))
                            ),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = loaded.scores.joinToString(", ") { "${it.name} ${it.pulya}/${it.gora}" },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                    TextButton(onClick = { vm.loadedCalc = null }) {
                        Text(stringResource(R.string.mp_clear), fontSize = 13.sp)
                    }
                }
            }
        }
        if (showPicker) {
            LoadScoresDialog(
                playersCount = room.maxSeats,
                onDismiss = { showPicker = false },
                onLoad = { calc ->
                    vm.loadedCalc = calc
                    showPicker = false
                }
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            if (vm.isHost && !vm.started) {
                val full = room.seats.count { it != null } == room.maxSeats &&
                        room.seats.all { it == null || it.connected }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.addBot() },
                        enabled = room.seats.count { it != null } < room.maxSeats,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.mp_add_bot), maxLines = 1) }
                    Button(
                        onClick = { vm.startGame() },
                        enabled = full,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.mp_start), maxLines = 1) }
                }
            }
            OutlinedButton(
                onClick = { vm.leave() },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.mp_leave)) }
        }
    }
}

/** Pick a saved pulka (same files the score calculator writes). */
@Composable
private fun LoadScoresDialog(
    playersCount: Int,
    onDismiss: () -> Unit,
    onLoad: (com.an0obIs.pref.model.Calculation) -> Unit
) {
    val calcs = remember {
        com.an0obIs.pref.model.CalcList().also { it.load() }.calcs
            .filter { it.playersCount == playersCount }
            .mapNotNull { entry ->
                try {
                    com.an0obIs.pref.model.Calculation.load(entry.created, entry.playersCount, entry.limit)
                } catch (e: Exception) {
                    null
                }
            }
            .filter { !it.isFinished }
    }
    val df = remember { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mp_load_scores), fontSize = 18.sp) },
        text = {
            if (calcs.isEmpty()) {
                Text(stringResource(R.string.mp_no_saved_scores))
            } else {
                LazyColumn {
                    items(calcs, key = { it.created }) { calc ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLoad(calc) }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.load_from, df.format(java.util.Date(calc.created))),
                                fontSize = 16.sp
                            )
                            Text(
                                text = calc.scores.joinToString(", ") { "${it.name} ${it.pulya}/${it.gora}" } +
                                        "  ·  " + calc.limit,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}
