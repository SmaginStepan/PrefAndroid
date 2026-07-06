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
    if (room == null) {
        LobbyView(vm)
    } else {
        RoomView(vm, room, onBack)
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

@Composable
private fun LobbyView(vm: LobbyViewModel) {
    var showCreate by remember { mutableStateOf(false) }
    var passwordFor by remember { mutableStateOf<RoomInfo?>(null) }

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
                                    .clickable(enabled = r.phase == "open") {
                                        if (r.hasPassword) passwordFor = r
                                        else vm.join(r.id, null)
                                    }
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
            defaultName = vm.myName,
            onDismiss = { showCreate = false },
            onCreate = { name, seats, password, preset, limit ->
                vm.createRoom(name, seats, password, preset, limit)
                showCreate = false
            }
        )
    }

    passwordFor?.let { r ->
        var pwd by remember(r.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { passwordFor = null },
            title = { Text(stringResource(R.string.mp_password_title), fontSize = 17.sp) },
            text = {
                OutlinedTextField(
                    value = pwd,
                    onValueChange = { pwd = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.mp_password_label)) }
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.join(r.id, pwd)
                    passwordFor = null
                }) { Text(stringResource(R.string.mp_join)) }
            },
            dismissButton = {
                TextButton(onClick = { passwordFor = null }) { Text(stringResource(R.string.close)) }
            }
        )
    }
}

@Composable
private fun CreateRoomDialog(
    defaultName: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, seats: Int, password: String?, preset: RulesGameType, limit: Int) -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }
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
                enabled = name.isNotBlank(),
                onClick = {
                    onCreate(name.trim(), seats, password, preset, limitText.toIntOrNull() ?: 10)
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

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 16.dp)
        ) {
            if (vm.isHost && !vm.started) {
                val full = room.seats.count { it != null } == room.maxSeats &&
                        room.seats.all { it == null || it.connected }
                OutlinedButton(
                    onClick = { vm.addBot() },
                    enabled = room.seats.count { it != null } < room.maxSeats
                ) { Text(stringResource(R.string.mp_add_bot)) }
                Button(onClick = { vm.startGame() }, enabled = full) {
                    Text(stringResource(R.string.mp_start))
                }
            }
            OutlinedButton(onClick = { vm.leave() }) {
                Text(stringResource(R.string.mp_leave))
            }
        }
    }
}
