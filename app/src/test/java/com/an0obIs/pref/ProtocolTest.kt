package com.an0obIs.pref

import com.an0obIs.pref.net.ClientMsg
import com.an0obIs.pref.net.ServerMsg
import com.an0obIs.pref.net.protocolJson
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {

    @Test
    fun encodesClientMessagesWithTypeDiscriminator() {
        val hello = protocolJson.encodeToString(
            ClientMsg.serializer(),
            ClientMsg.Hello(playerId = "abc12345", name = "Степан")
        )
        assertTrue(hello.contains("\"type\":\"hello\""))
        assertTrue(hello.contains("\"playerId\":\"abc12345\""))

        val create = protocolJson.encodeToString(
            ClientMsg.serializer(),
            ClientMsg.CreateRoom(
                name = "Игра",
                rules = buildJsonObject { put("limit", 10) },
                maxSeats = 4,
                password = null
            )
        )
        assertTrue(create.contains("\"type\":\"create_room\""))
        assertTrue(create.contains("\"maxSeats\":4"))
        // zod .optional() rejects explicit null — the field must be absent
        assertTrue("null password must be omitted", !create.contains("password"))

        val addBot = protocolJson.encodeToString(
            ClientMsg.serializer(),
            ClientMsg.AddBot(seat = null)
        )
        assertTrue("null seat must be omitted", !addBot.contains("seat"))
    }

    @Test
    fun decodesServerMessages() {
        // samples captured from the real pref-server
        val rooms = protocolJson.decodeFromString(
            ServerMsg.serializer(),
            """{"type":"rooms","rooms":[{"id":"R7DCDW","name":"Test game","rules":{"gameType":"Sochy","limit":10},"maxSeats":3,"phase":"open","hasPassword":true,"hostName":"Host","seats":[{"name":"Host","kind":"human","connected":true},null,null]}]}"""
        ) as ServerMsg.Rooms
        assertEquals(1, rooms.rooms.size)
        assertEquals("R7DCDW", rooms.rooms[0].id)
        assertEquals(3, rooms.rooms[0].seats.size)
        assertEquals("Host", rooms.rooms[0].seats[0]?.name)

        val joined = protocolJson.decodeFromString(
            ServerMsg.serializer(),
            """{"type":"joined","roomId":"R7DCDW","seat":1}"""
        ) as ServerMsg.Joined
        assertEquals(1, joined.seat)

        val err = protocolJson.decodeFromString(
            ServerMsg.serializer(),
            """{"type":"error","code":"bad_password","message":"Wrong password"}"""
        ) as ServerMsg.Error
        assertEquals("bad_password", err.code)

        val relayed = protocolJson.decodeFromString(
            ServerMsg.serializer(),
            """{"type":"player_msg","fromSeat":2,"data":{"action":"bid","contract":6}}"""
        ) as ServerMsg.PlayerMsg
        assertEquals(2, relayed.fromSeat)
    }
}
