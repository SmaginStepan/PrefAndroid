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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

/** Client-owned payload stored in the room's opaque `rules` field. */
@Serializable
data class RoomRules(val gameRules: GameRules = GameRules(), val limit: Int = 10)

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

    /** Transient server error / event code; the UI maps it to a localized text. */
    var notice by mutableStateOf<String?>(null)

    var myName by mutableStateOf("")
        private set

    private var startedOnce = false

    fun start() {
        if (startedOnce) return
        startedOnce = true
        val settings = AppSettings()
        myName = settings.playerName

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
            is ServerMsg.RoomState -> currentRoom = msg.room
            is ServerMsg.Started -> started = true
            is ServerMsg.Left -> {
                currentRoom = null; mySeat = null; started = false
            }
            is ServerMsg.Kicked -> {
                currentRoom = null; mySeat = null; started = false; notice = "kicked"
            }
            is ServerMsg.RoomClosed -> {
                currentRoom = null; mySeat = null; started = false; notice = "room_closed"
            }
            is ServerMsg.Error -> notice = msg.code
            is ServerMsg.HostMsg, is ServerMsg.PlayerMsg -> {
                // game-phase messages: handled in the next milestone
            }
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
        limit: Int
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
        val payload: JsonElement = protocolJson.encodeToJsonElement(RoomRules(rules, limit))
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
