package com.an0obIs.pref.model

import com.an0obIs.pref.net.ScoreClient
import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

/**
 * The global leaderboard, offline-friendly: finished-game scores queue in a
 * local file and flush whenever the network allows; the last fetched board is
 * cached for offline viewing, with the classic seeded names as the fallback
 * before the first successful fetch.
 */
object GlobalScores {

    @Serializable
    data class Row(
        val playerId: String,
        val name: String,
        /** score in tenths of a whist (server stores integers) */
        val score10: Long
    ) {
        val score: Double get() = score10 / 10.0
    }

    @Serializable
    private data class Cache(var rows: List<Row> = emptyList())

    @Serializable
    private data class PendingScore(var name: String, var score10: Long, var deviceTs: Long)

    @Serializable
    private data class PendingList(var items: MutableList<PendingScore> = mutableListOf())

    private const val CACHE_FILE = "global_scores.json"
    private const val PENDING_FILE = "pending_scores.json"

    /** Only wins of at least this many whists reach the global board. */
    const val MIN_SCORE = 10.0

    /** The classic table, shown until the first real fetch succeeds. */
    fun seededFallback(): List<Row> = listOf(
        Row("seed", "Эйнштейн", 1000),
        Row("seed", "Да Винчи", 750),
        Row("seed", "Перельман", 500),
        Row("seed", "Вован", 300),
        Row("seed", "Настасья", 250),
        Row("seed", "Алексей", 200),
        Row("seed", "Андрей", 150),
        Row("seed", "Григорий", 100),
        Row("seed", "Ирина", 50),
        Row("seed", "Степан", 0)
    )

    fun cached(): List<Row> = try {
        PrefStorage.readText(CACHE_FILE)
            ?.let { PrefStorage.json.decodeFromString(Cache.serializer(), it).rows }
            ?.takeIf { it.isNotEmpty() }
            ?: seededFallback()
    } catch (e: Exception) {
        seededFallback()
    }

    private fun saveCache(rows: List<Row>) = try {
        PrefStorage.writeText(
            CACHE_FILE,
            PrefStorage.json.encodeToString(Cache.serializer(), Cache(rows))
        )
    } catch (e: Exception) {
        android.util.Log.w("Pref", "score cache save failed", e)
    }

    private fun loadPending(): PendingList = try {
        PrefStorage.readText(PENDING_FILE)
            ?.let { PrefStorage.json.decodeFromString(PendingList.serializer(), it) }
            ?: PendingList()
    } catch (e: Exception) {
        PendingList()
    }

    private fun savePending(p: PendingList) = try {
        PrefStorage.writeText(
            PENDING_FILE,
            PrefStorage.json.encodeToString(PendingList.serializer(), p)
        )
    } catch (e: Exception) {
        android.util.Log.w("Pref", "pending score save failed", e)
    }

    /** Queues a finished game's result; wins under [MIN_SCORE] never board. */
    fun enqueue(name: String, score: Double) {
        if (score < MIN_SCORE) return
        val score10 = (score * 10).roundToLong()
        val p = loadPending()
        p.items.add(PendingScore(name, score10, System.currentTimeMillis() / 1000))
        savePending(p)
    }

    /** Sends every queued score; keeps the ones worth retrying. Blocking. */
    fun flushPending() {
        val p = loadPending()
        if (p.items.isEmpty()) return
        val playerId = AppSettings().playerId
        val remaining = mutableListOf<PendingScore>()
        for (item in p.items) {
            when (ScoreClient.submit(playerId, item.name, item.score10, item.deviceTs)) {
                ScoreClient.SubmitOutcome.ACCEPTED -> {}
                ScoreClient.SubmitOutcome.DROP -> {}
                ScoreClient.SubmitOutcome.RETRY -> remaining.add(item)
            }
        }
        p.items = remaining
        savePending(p)
    }

    /**
     * Flushes the queue, then fetches the fresh top list and caches it.
     * Returns null when the server is unreachable (show [cached]). Blocking.
     */
    fun sync(): List<Row>? {
        flushPending()
        val top = ScoreClient.fetchTop(10) ?: return null
        val rows = top.map { Row(it.player_id, it.name, it.score) }
        saveCache(rows)
        return rows
    }
}
