package com.an0obIs.pref.model

import com.an0obIs.pref.net.ScoreClient
import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

/**
 * Everything about a season that every player must agree on is derived from
 * the season seed the server hands out, so no rules ever travel separately.
 * The draw order is part of the contract (the iOS port repeats it exactly):
 *
 *   limit(4: 10,20,40,60) · game type(3) · all-pass progression(3) ·
 *   all-pass exit(3) · exit after a played misère(2) · whist(2) ·
 *   consolation(2) · talon consolation(2) · first dealer(3)
 *
 * Each draw is SeasonRandom(seed,"config",0).nextInt(n) indexing the enum's
 * declaration order (or false/true). The game type applies the same presets as
 * the settings screen; under Rostov the progression is forced to "none" (the
 * draw is still consumed, so the sequence never shifts).
 */
object SeasonConfig {

    val LIMITS = listOf(10, 20, 40, 60)

    class Config(val limit: Int, val rules: GameRules, val firstDealer: Int)

    fun derive(seedHex: String): Config {
        val rng = SeasonRandom.forPurpose(seedHex, "config", 0)
        val limit = LIMITS[rng.nextInt(LIMITS.size)]
        val type = RulesGameType.values()[rng.nextInt(3)]
        val progression = RaspasyProgression.values()[rng.nextInt(3)]
        val exit = RaspasyExit.values()[rng.nextInt(3)]
        val miserExit = rng.nextInt(2) == 1
        val vist = VistType.values()[rng.nextInt(2)]
        val consolation = ConsolationType.values()[rng.nextInt(2)]
        val talonConsolation = rng.nextInt(2) == 1
        val firstDealer = rng.nextInt(3)

        val rules = GameRules()
        rules.gameType = type
        when (type) {
            RulesGameType.Sochy -> {
                rules.ending = EndingType.Each
                rules.scoring = ScoreType.Normal
                rules.consolationBonus = ConsolationSum.Normal
            }
            RulesGameType.Leningrad -> {
                rules.ending = EndingType.Sum
                rules.scoring = ScoreType.Leningrad
                rules.consolationBonus = ConsolationSum.Normal
            }
            RulesGameType.Rostov -> {
                rules.ending = EndingType.Each
                rules.scoring = ScoreType.Normal
                rules.consolationBonus = ConsolationSum.Max10
            }
        }
        rules.raspasyProgression =
            if (type == RulesGameType.Rostov) RaspasyProgression.NoProgression1 else progression
        rules.raspasyExit = exit
        rules.miserRaspExit = miserExit
        rules.vist = vist
        rules.consolation = consolation
        rules.prikupConsolation = talonConsolation
        return Config(limit, rules, firstDealer)
    }
}

@Serializable
data class SeasonRow(val playerId: String, val name: String, val score10: Long) {
    val score: Double get() = score10 / 10.0
}

/** The player's run through the current season, kept on the device. */
@Serializable
class SeasonState {
    var seasonId: String = ""
    var seed: String = ""
    var startsAt: Long = 0
    var endsAt: Long = 0

    /** first deal of the next pool, and who deals it */
    var nextDealIndex: Int = 0
    var nextDealer: Int = 0

    /** sum of every closed pool's settlement, in tenths of a whist (may be negative) */
    var total10: Long = 0
    var poolsClosed: Int = 0

    /** the best total the server has accepted so far */
    var submittedBest10: Long = 0

    /** the board as last fetched, for offline viewing */
    var board: List<SeasonRow> = emptyList()
}

/**
 * The season run: one continuous walk through the shared deal sequence, played
 * as consecutive pools. Nothing here needs the network except learning the
 * season seed once per season and posting the total.
 */
object Seasons {

    private const val FILE = "season.json"

    fun load(): SeasonState? = try {
        PrefStorage.readText(FILE)?.let { PrefStorage.json.decodeFromString(SeasonState.serializer(), it) }
    } catch (e: Exception) {
        null
    }

    fun save(state: SeasonState) = try {
        PrefStorage.writeText(FILE, PrefStorage.json.encodeToString(SeasonState.serializer(), state))
    } catch (e: Exception) {
        android.util.Log.w("Pref", "season save failed", e)
    }

    fun boardName(state: SeasonState) = "season:${state.seasonId}"

    fun config(state: SeasonState) = SeasonConfig.derive(state.seed)

    /** Whole days left (rounded up); 0 once the season is over. */
    fun daysLeft(state: SeasonState, nowS: Long = System.currentTimeMillis() / 1000): Int =
        if (nowS >= state.endsAt) 0 else (((state.endsAt - nowS) + 86399) / 86400).toInt()

    fun isOver(state: SeasonState, nowS: Long = System.currentTimeMillis() / 1000) = nowS >= state.endsAt

    /**
     * Asks the server which season is running. A new season id starts a fresh
     * run (and drops the previous season's unfinished game); the same id keeps
     * the player's progress. Returns the saved state, or null when there is
     * none and the server could not be reached. Blocking.
     */
    fun refresh(): SeasonState? {
        val old = load()
        val info = ScoreClient.fetchSeason() ?: return old
        if (old != null && old.seasonId == info.seasonId) {
            old.startsAt = info.startsAt
            old.endsAt = info.endsAt
            save(old)
            return old
        }
        val fresh = SeasonState().also {
            it.seasonId = info.seasonId
            it.seed = info.seed
            it.startsAt = info.startsAt
            it.endsAt = info.endsAt
            it.nextDealer = SeasonConfig.derive(info.seed).firstDealer
        }
        PrefStorage.deleteFamily(Game.SEASON_GAME_FILE)
        save(fresh)
        return fresh
    }

    /** A new pool that continues the run's deal sequence. */
    fun newPoolGame(state: SeasonState, playerName: String, ai1Name: String, ai2Name: String): Game {
        val cfg = config(state)
        val game = Game()
        game.calc = Calculation(3, cfg.limit)
        game.calc.rules = cfg.rules.clone()
        game.calc.scores[0].name = playerName
        game.calc.scores[1].name = ai1Name
        game.calc.scores[2].name = ai2Name
        game.calc.dealer = state.nextDealer
        game.aIs = mutableMapOf()
        game.phase = GamePhase.NotStarted
        game.seasonSeed = state.seed
        game.seasonDealIndex = state.nextDealIndex
        return game
    }

    /**
     * A pool closed with [settlement] whists for the player. Counted once per
     * pool, even if the result screen is closed twice.
     */
    fun poolClosed(state: SeasonState, game: Game, settlement: Double) {
        if (game.seasonDealIndex <= state.nextDealIndex) return
        state.total10 += (settlement * 10).roundToLong()
        state.poolsClosed++
        state.nextDealIndex = game.seasonDealIndex
        state.nextDealer = game.calc.dealer
        save(state)
    }

    /** Posts the total if it is a new best; blocking, safe to call any time. */
    fun submitPending(state: SeasonState, playerName: String) {
        if (state.total10 <= 0 || state.total10 <= state.submittedBest10) return
        val outcome = ScoreClient.submit(
            AppSettings().playerId, playerName, state.total10,
            System.currentTimeMillis() / 1000, board = boardName(state)
        )
        if (outcome != ScoreClient.SubmitOutcome.RETRY) {
            state.submittedBest10 = state.total10
            save(state)
        }
    }

    /** Refreshes the cached season board. Blocking. */
    fun refreshBoard(state: SeasonState) {
        val top = ScoreClient.fetchTop(10, boardName(state)) ?: return
        state.board = top.map { SeasonRow(it.player_id, it.name, it.score) }
        save(state)
    }
}
