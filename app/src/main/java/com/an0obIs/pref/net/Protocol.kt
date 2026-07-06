package com.an0obIs.pref.net

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement

/** Mirrors PrefServer/src/protocol.ts. `rules` and relayed `data` are opaque JSON. */

@OptIn(ExperimentalSerializationApi::class)
val protocolJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    // zod's .optional() on the server accepts a MISSING field but rejects null;
    // omit null fields instead of emitting "field":null
    explicitNulls = false
    classDiscriminator = "type"
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface ClientMsg {
    @Serializable
    @SerialName("hello")
    data class Hello(val playerId: String, val name: String) : ClientMsg

    @Serializable
    @SerialName("list_rooms")
    data object ListRooms : ClientMsg

    @Serializable
    @SerialName("create_room")
    data class CreateRoom(
        val name: String,
        val rules: JsonElement,
        val maxSeats: Int,
        val password: String? = null
    ) : ClientMsg

    @Serializable
    @SerialName("reopen_room")
    data class ReopenRoom(
        val roomId: String,
        val name: String,
        val rules: JsonElement,
        val maxSeats: Int,
        val password: String? = null,
        val seats: List<ReopenSeat?>
    ) : ClientMsg

    @Serializable
    data class ReopenSeat(val playerId: String? = null, val name: String, val kind: String)

    @Serializable
    @SerialName("join")
    data class Join(val roomId: String, val password: String? = null) : ClientMsg

    @Serializable
    @SerialName("leave")
    data object Leave : ClientMsg

    @Serializable
    @SerialName("kick")
    data class Kick(val seat: Int) : ClientMsg

    @Serializable
    @SerialName("add_bot")
    data class AddBot(val seat: Int? = null) : ClientMsg

    @Serializable
    @SerialName("start")
    data object Start : ClientMsg

    @Serializable
    @SerialName("send")
    data class Send(val toSeat: Int? = null, val data: JsonElement) : ClientMsg
}

@Serializable
data class SeatInfo(val name: String, val kind: String, val connected: Boolean)

@Serializable
data class RoomInfo(
    val id: String,
    val name: String,
    val rules: JsonElement? = null,
    val maxSeats: Int,
    val phase: String,
    val hasPassword: Boolean = false,
    val hostName: String = "",
    val seats: List<SeatInfo?> = emptyList()
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface ServerMsg {
    @Serializable
    @SerialName("welcome")
    data object Welcome : ServerMsg

    @Serializable
    @SerialName("rooms")
    data class Rooms(val rooms: List<RoomInfo>) : ServerMsg

    @Serializable
    @SerialName("room_created")
    data class RoomCreated(val roomId: String) : ServerMsg

    @Serializable
    @SerialName("room_state")
    data class RoomState(val room: RoomInfo) : ServerMsg

    @Serializable
    @SerialName("joined")
    data class Joined(val roomId: String, val seat: Int) : ServerMsg

    @Serializable
    @SerialName("started")
    data object Started : ServerMsg

    @Serializable
    @SerialName("kicked")
    data class Kicked(val roomId: String? = null) : ServerMsg

    @Serializable
    @SerialName("room_closed")
    data class RoomClosed(val roomId: String? = null, val reason: String? = null) : ServerMsg

    @Serializable
    @SerialName("left")
    data object Left : ServerMsg

    @Serializable
    @SerialName("host_msg")
    data class HostMsg(val data: JsonElement) : ServerMsg

    @Serializable
    @SerialName("player_msg")
    data class PlayerMsg(val fromSeat: Int, val data: JsonElement) : ServerMsg

    @Serializable
    @SerialName("error")
    data class Error(val code: String, val message: String) : ServerMsg
}
