package com.an0obIs.pref

import com.an0obIs.pref.model.GlobalScores
import com.an0obIs.pref.model.PrefStorage
import com.an0obIs.pref.net.ScoreClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class GlobalScoresTest {

    @Before
    fun setUp() {
        PrefStorage.init(Files.createTempDirectory("pref-scores-test").toFile())
    }

    @Test
    fun signatureMatchesServerScheme() {
        // reference values computed independently (.NET HMACSHA256)
        val body = """{"player_id":"p1","name":"Step","score":123,"device_ts":1700000000}"""
            .toByteArray(Charsets.UTF_8)
        val payload = ScoreClient.signaturePayload(
            "1700000000", "post", "/v1/pref/boards/alltime/scores", body
        )
        assertEquals(
            "1700000000\nPOST\n/v1/pref/boards/alltime/scores\n" +
                    "6da3de2babbd13f125d4e8525247dd57e4170fbe0797b66797e8a91ed3741511",
            payload
        )
        assertEquals(
            "30e4698209eaa50fa93423f058017f3c6f12bf03b049cabb98601f9796a9a12f",
            ScoreClient.sign("s3cret", payload)
        )
    }

    @Test
    fun fallbackShowsTheClassicTable() {
        val rows = GlobalScores.cached() // nothing fetched yet
        assertEquals(10, rows.size)
        assertEquals("Эйнштейн", rows[0].name)
        assertEquals(1000.0, rows[0].score, 0.0)
        assertEquals(0.0, rows[9].score, 0.0)
    }

    @Test
    fun losingScoresNeverQueue() {
        GlobalScores.enqueue("Loser", -12.5)
        assertTrue(PrefStorage.readText("pending_scores.json")?.contains("Loser") != true)
        GlobalScores.enqueue("Winner", 42.5) // x10 rounding keeps the half-whist
        val pending = PrefStorage.readText("pending_scores.json") ?: ""
        assertTrue(pending.contains("Winner"))
        assertTrue(pending.contains("425"))
    }
}
