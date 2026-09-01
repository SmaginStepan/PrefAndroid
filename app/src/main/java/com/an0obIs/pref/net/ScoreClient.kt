package com.an0obIs.pref.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Client for the global leaderboard (ScoreServer on the VPS). Reads need the
 * API key; writes are HMAC-signed. All calls are blocking — run them on IO.
 */
object ScoreClient {

    private const val BASE = "https://scores.preferansmaster.com"
    private const val GAME = "pref"
    private const val BOARD = "alltime"
    private const val API_KEY = "1711193a9bc38cf8ec25876c09981f7d"
    private const val SECRET = "9bc01938a5d526e89f1cc67abed8e599924537d84d0217e0e3f67439a1dd27d6"

    @Serializable
    data class Entry(val rank: Int, val player_id: String, val name: String, val score: Long)

    @Serializable
    private data class TopResponse(val entries: List<Entry> = emptyList())

    @Serializable
    private data class SubmitBody(
        val player_id: String,
        val name: String,
        val score: Long,
        val device_ts: Long
    )

    @Serializable
    private data class SubmitResponse(
        val accepted: Boolean = false,
        val reason: String? = null,
        val best: Long = 0,
        val rank: Int? = null
    )

    /** What to do with a queued submission after one attempt. */
    enum class SubmitOutcome { ACCEPTED, DROP, RETRY }

    private val json = Json { ignoreUnknownKeys = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** The global top list, or null when the server is unreachable. */
    fun fetchTop(limit: Int = 10): List<Entry>? = try {
        val req = Request.Builder()
            .url("$BASE/v1/$GAME/boards/$BOARD/top?limit=$limit")
            .header("X-Api-Key", API_KEY)
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null
            else json.decodeFromString(TopResponse.serializer(), resp.body!!.string()).entries
        }
    } catch (e: Exception) {
        android.util.Log.w("PrefNet", "score top failed: ${e.message}")
        null
    }

    /**
     * Submits one score (already in the x10 integer scale). A rejection the
     * server calls permanent (out of range) is DROP; rate limiting and network
     * failures are RETRY.
     */
    fun submit(playerId: String, name: String, score10: Long, deviceTs: Long): SubmitOutcome = try {
        val path = "/v1/$GAME/boards/$BOARD/scores"
        val body = json.encodeToString(
            SubmitBody.serializer(),
            SubmitBody(playerId, name, score10, deviceTs)
        ).toByteArray(Charsets.UTF_8)
        val ts = (System.currentTimeMillis() / 1000).toString()
        val req = Request.Builder()
            .url("$BASE$path")
            .header("X-Api-Key", API_KEY)
            .header("X-Ts", ts)
            .header("X-Sig", sign(SECRET, signaturePayload(ts, "POST", path, body)))
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            when {
                !resp.isSuccessful -> SubmitOutcome.RETRY // 5xx, 401 clock drift…
                else -> {
                    val r = json.decodeFromString(SubmitResponse.serializer(), resp.body!!.string())
                    when {
                        r.accepted -> SubmitOutcome.ACCEPTED
                        r.reason == "submitting too fast" -> SubmitOutcome.RETRY
                        else -> SubmitOutcome.DROP // out of range / above maximum
                    }
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.w("PrefNet", "score submit failed: ${e.message}")
        SubmitOutcome.RETRY
    }

    // sig = HMAC-SHA256(secret, "{ts}\n{METHOD}\n{path}\n{sha256hex(body)}"), lowercase hex
    internal fun signaturePayload(ts: String, method: String, path: String, body: ByteArray): String {
        val bodyHash = MessageDigest.getInstance("SHA-256").digest(body).toHexLower()
        return "$ts\n${method.uppercase()}\n$path\n$bodyHash"
    }

    internal fun sign(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).toHexLower()
    }

    private fun ByteArray.toHexLower(): String =
        joinToString("") { "%02x".format(it) }
}
