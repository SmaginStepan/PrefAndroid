package com.an0obIs.pref.ui.mp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.an0obIs.pref.model.AppSettings
import com.an0obIs.pref.model.ConsolationSum
import com.an0obIs.pref.model.ConsolationType
import com.an0obIs.pref.model.EndingType
import com.an0obIs.pref.model.GameRules
import com.an0obIs.pref.model.RaspasyProgression
import com.an0obIs.pref.model.RulesGameType
import com.an0obIs.pref.model.ScoreType
import com.an0obIs.pref.model.VistType
import com.an0obIs.pref.net.ClientMsg
import com.an0obIs.pref.net.ConnState
import com.an0obIs.pref.net.LobbyClient
import com.an0obIs.pref.net.RoomInfo
import com.an0obIs.pref.net.ServerMsg
import com.an0obIs.pref.net.protocolJson
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

/** Client-owned payload stored in the room's opaque `rules` field. */
@Serializable
data class RoomRules(
    val gameRules: GameRules = GameRules(),
    val limit: Int = 10,
    /** confirmations auto-continue after this many seconds (0 = off) */
    val autoConfirmSec: Int = 0
)

class LobbyViewModel : ViewModel() {

    private val client = LobbyClient()

    var conn by mutableStateOf(ConnState.Disconnected)
        private set
    var rooms by mutableStateOf<List<RoomInfo>>(emptyList())
        private set
    var currentRoom by mutableStateOf<RoomInfo?>(null)
        private set
    var mySeat by mutableStateOf<Int?>(null)
        private set
    var started by mutableStateOf(false)
        private set

    /** Increments for every freshly started game so the game screens get a
     *  new ViewModel instead of the previous match's one (stale-state bug). */
    var gameGeneration by mutableStateOf(0)
        private set

    /** Transient server error / event code; the UI maps it to a localized text. */
    var notice by mutableStateOf<String?>(null)

    /** A saved pulka the host wants to resume from when the game starts. */
    var loadedCalc by mutableStateOf<com.an0obIs.pref.model.Calculation?>(null)

    var myName by mutableStateOf("")
        private set

    /** Relayed game payloads: host receives (fromSeat, data); guests receive data. */
    val playerActs = MutableSharedFlow<Pair<Int, JsonElement>>(
        extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val hostStates = MutableSharedFlow<JsonElement>(
        extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun sendGameToSeat(seat: Int, data: JsonElement) {
        client.send(ClientMsg.Send(toSeat = seat, data = data))
    }

    fun sendGameToHost(data: JsonElement) {
        client.send(ClientMsg.Send(data = data))
    }

    private var startedOnce = false

    fun start() {
        if (startedOnce) return
        startedOnce = true
        val settings = AppSettings()
        // blank = never customized; the UI substitutes the localized default
        myName = if (settings.isDefaultPlayerName) "" else settings.playerName

        viewModelScope.launch {
            client.state.collect { conn = it }
        }
        viewModelScope.launch {
            client.messages.collect { onMessage(it) }
        }
        // connection keeper + lobby polling
        viewModelScope.launch {
            while (true) {
                when {
                    conn == ConnState.Disconnected -> {
                        val s = AppSettings()
                        client.connect(s.playerId, s.playerName)
                    }
                    conn == ConnState.Connected && currentRoom == null -> {
                        client.send(ClientMsg.ListRooms)
                    }
                }
                delay(4000)
            }
        }
    }

    private fun onMessage(msg: ServerMsg) {
        when (msg) {
            is ServerMsg.Welcome -> client.send(ClientMsg.ListRooms)
            is ServerMsg.Rooms -> rooms = msg.rooms
            is ServerMsg.RoomCreated -> mySeat = 0
            is ServerMsg.Joined -> mySeat = msg.seat
            is ServerMsg.RoomState -> {
                val prev = currentRoom
                currentRoom = msg.room
                // a newcomer while a pulka is loaded: seat them on their column
                if (loadedCalc != null && isHost && !started) {
                    val prevNames = prev?.seats?.mapNotNull { it?.name }?.toSet() ?: emptySet()
                    if (msg.room.seats.any { it != null && it.name !in prevNames })
                        arrangeByPulka()
                }
            }
            is ServerMsg.Started -> {
                // only a real start bumps the generation; the reconnect replay
                // of "started" must keep the running game's ViewModel
                if (!started) gameGeneration++
                started = true
            }
            is ServerMsg.Left -> {
                currentRoom = null; mySeat = null; started = false; loadedCalc = null
            }
            is ServerMsg.Kicked -> {
                currentRoom = null; mySeat = null; started = false; loadedCalc = null; notice = "kicked"
            }
            is ServerMsg.RoomClosed -> {
                currentRoom = null; mySeat = null; started = false; loadedCalc = null
                notice = if (msg.reason == "host_disconnected") "host_disconnected" else "room_closed"
            }
            is ServerMsg.Error -> {
                // transient throttling must not interrupt play with a dialog
                if (msg.code == "rate_limited")
                    android.util.Log.w("PrefNet", "rate limited: ${msg.message}")
                else notice = msg.code
            }
            is ServerMsg.HostMsg -> hostStates.tryEmit(msg.data)
            is ServerMsg.PlayerMsg -> playerActs.tryEmit(msg.fromSeat to msg.data)
        }
    }

    val isHost: Boolean
        get() = mySeat == 0

    /** Persist a changed nickname and re-announce it before create/join. */
    private fun ensureName(name: String) {
        val n = name.trim().take(24)
        if (n.isEmpty() || n == myName) return
        val settings = AppSettings()
        settings.playerName = n
        myName = n
        client.send(ClientMsg.Hello(settings.playerId, n))
    }

    fun refresh() {
        if (conn == ConnState.Connected) client.send(ClientMsg.ListRooms)
    }

    fun createRoom(
        playerName: String,
        roomName: String,
        maxSeats: Int,
        password: String?,
        preset: RulesGameType,
        limit: Int,
        autoConfirmSec: Int = 0
    ) {
        ensureName(playerName)
        val rules = GameRules().also {
            it.gameType = preset
            when (preset) {
                RulesGameType.Sochy -> {
                    it.vist = VistType.FullResponsibility
                    it.consolation = ConsolationType.Zlob
                    it.ending = EndingType.Each
                    it.scoring = ScoreType.Normal
                    it.consolationBonus = ConsolationSum.Normal
                }
                RulesGameType.Leningrad -> {
                    it.vist = VistType.HalfResponsibility
                    it.consolation = ConsolationType.Gentlemen
                    it.ending = EndingType.Sum
                    it.scoring = ScoreType.Leningrad
                    it.consolationBonus = ConsolationSum.Normal
                }
                RulesGameType.Rostov -> {
                    it.raspasyProgression = RaspasyProgression.NoProgression1
                    it.vist = VistType.HalfResponsibility
                    it.consolation = ConsolationType.Gentlemen
                    it.ending = EndingType.Each
                    it.scoring = ScoreType.Normal
                    it.consolationBonus = ConsolationSum.Max10
                }
            }
        }
        val payload: JsonElement = protocolJson.encodeToJsonElement(RoomRules(rules, limit, autoConfirmSec))
        client.send(
            ClientMsg.CreateRoom(
                name = roomName,
                rules = payload,
                maxSeats = maxSeats,
                password = password?.takeIf { it.isNotBlank() }
            )
        )
    }

    fun join(roomId: String, password: String?, playerName: String) {
        ensureName(playerName)
        client.send(ClientMsg.Join(roomId, password?.takeIf { it.isNotBlank() }))
    }

    fun leave() {
        client.send(ClientMsg.Leave)
    }

    fun kick(seat: Int) {
        client.send(ClientMsg.Kick(seat))
    }

    fun addBot() {
        client.send(ClientMsg.AddBot())
    }

    fun swapSeats(a: Int, b: Int) {
        if (a != b && a > 0 && b > 0) client.send(ClientMsg.SwapSeats(a, b))
    }

    /** Move name-matched players onto their saved-pulka columns (visual order). */
    fun arrangeByPulka() {
        val calc = loadedCalc ?: return
        val room = currentRoom ?: return
        if (!isHost || started) return
        // simulate on a copy, emit the swap sequence that realizes it
        val sim = room.seats.toMutableList()
        val n = minOf(room.maxSeats, calc.playersCount)
        for (col in 1 until n) {
            val want = calc.scores[col].name.trim().lowercase()
            val cur = sim.getOrNull(col)?.name?.trim()?.lowercase()
            if (cur == want) continue
            val from = (1 until room.maxSeats).firstOrNull { j ->
                j != col && sim.getOrNull(j)?.name?.trim()?.lowercase() == want
            } ?: continue
            val tmp = sim[col]; sim[col] = sim[from]; sim[from] = tmp
            swapSeats(col, from)
        }
    }

    fun startGame() {
        client.send(ClientMsg.Start)
    }

    /** Lenient parse of a room's opaque rules payload for lobby display. */
    fun parseRules(rules: JsonElement?): RoomRules? = try {
        if (rules == null) null
        else protocolJson.decodeFromJsonElement(RoomRules.serializer(), rules)
    } catch (e: Exception) {
        null
    }

    override fun onCleared() {
        client.disconnect()
    }
}
