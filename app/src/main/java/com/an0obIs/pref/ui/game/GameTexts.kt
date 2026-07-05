package com.an0obIs.pref.ui.game

import android.content.Context
import com.an0obIs.pref.R
import com.an0obIs.pref.model.Calculation
import com.an0obIs.pref.model.Game
import com.an0obIs.pref.model.GamePhase
import com.an0obIs.pref.model.GameType
import com.an0obIs.pref.model.ScoreValueType

/** Localized text builders for the game table (ports of Bid.Title, PlayerInfo, GameLog.GetResult). */
object GameTexts {

    fun trumpName(ctx: Context, trump: Int): String = when (trump) {
        0 -> ctx.getString(R.string.trump_spades)
        1 -> ctx.getString(R.string.trump_clubs)
        2 -> ctx.getString(R.string.trump_diamonds)
        3 -> ctx.getString(R.string.trump_hearts)
        else -> ctx.getString(R.string.trump_nt)
    }

    fun bidTitle(ctx: Context, bid: Game.Bid): String {
        if (bid.pas)
            return ctx.getString(R.string.bid_pas)
        if (bid.miser)
            return ctx.getString(R.string.bid_miser)
        return "${bid.contract} ${trumpName(ctx, bid.trump)}"
    }

    /** Port of GameMain.PlayerInfo. */
    fun playerInfo(ctx: Context, info: TableInfo, player: Int): String {
        var res = ""
        if (player == info.dealer)
            res += ">"
        res += info.names[player]
        if (info.currentGameType == GameType.Normal || info.currentGameType == GameType.Miser) {
            if (info.phase == GamePhase.EndTurn || info.phase == GamePhase.Playing || info.phase == GamePhase.OpeningChoose) {
                if (info.contractor == player)
                    res += ctx.getString(R.string.game_role_contract)
                else if (info.isVister[player] == true)
                    res += ctx.getString(R.string.game_role_whist)
            }
        }
        return res
    }

    private fun getMiser(ctx: Context, game: Calculation.GameResult, names: List<String>): String {
        var res = if (game.isSuccessful)
            "   " + ctx.getString(R.string.result_miser_ok, names[game.contractor])
        else
            "   " + ctx.getString(R.string.result_miser_fail, names[game.contractor], game.taken[game.contractor] ?: 0)
        if (game.halfWithDealer)
            res += ctx.getString(R.string.result_half_dealer, names[game.dealer])
        res += "."
        return res
    }

    private fun getRaspasy(ctx: Context, game: Calculation.GameResult, names: List<String>): String {
        var res = "   " + ctx.getString(R.string.result_raspasy, game.multiplier)
        var first = true
        for (p in game.taken.keys) {
            if (!first)
                res += ","
            first = false
            res += " " + ctx.getString(R.string.result_raspasy_taken, names[p], game.taken[p] ?: 0)
        }
        res += "."
        return res
    }

    private fun getNormal(ctx: Context, game: Calculation.GameResult, names: List<String>): String {
        var res = if (game.isSuccessful)
            "   " + ctx.getString(R.string.result_normal_ok, names[game.contractor], game.contract)
        else
            "   " + ctx.getString(R.string.result_normal_fail, names[game.contractor], game.contract)
        res += ctx.getString(R.string.result_taken, game.taken[game.contractor] ?: 0)
        when (game.visters.size) {
            0 -> {
                for (pNum in game.taken.keys) {
                    if (pNum != game.contractor && (game.taken[pNum] ?: 0) > 0) {
                        res += ctx.getString(R.string.result_halfvist, game.taken[pNum] ?: 0, names[pNum])
                    }
                }
            }
            1 -> {
                res += ctx.getString(R.string.result_vister, names[game.visters[0]])
            }
            2 -> {
                val v1 = game.visters[0]
                val v2 = game.visters[1]
                res += ctx.getString(R.string.result_visters, names[v1], names[v2])
                res += ctx.getString(R.string.result_visters_taken, game.taken[v1] ?: 0, game.taken[v2] ?: 0)
            }
        }
        res += "."
        return res
    }

    private fun getCustom(ctx: Context, game: Calculation.GameResult, names: List<String>): String {
        val score = game.customScore ?: return ""
        var res = "   " + ctx.getString(R.string.result_custom, names[score.playerNum], score.value)
        res += when (score.scoreType) {
            ScoreValueType.Gora -> ctx.getString(R.string.result_to_gora)
            ScoreValueType.Pulya -> ctx.getString(R.string.result_to_pulya)
            ScoreValueType.Visty -> ctx.getString(R.string.result_to_visty, names[score.refPlayerNum])
        }
        res += "."
        return res
    }

    /** Port of GameLog.GetResult. */
    fun resultText(ctx: Context, game: Calculation.GameResult, names: List<String>): String = when (game.gameType) {
        GameType.Miser -> getMiser(ctx, game, names)
        GameType.Raspasy -> getRaspasy(ctx, game, names)
        GameType.Normal -> getNormal(ctx, game, names)
        GameType.Custom -> getCustom(ctx, game, names)
    }

    fun resultText(ctx: Context, game: Calculation.GameResult, calc: Calculation): String =
        resultText(ctx, game, calc.scores.map { it.name })

    /** Maps the model's say-animation literals to localized strings. */
    fun sayText(ctx: Context, say: SayEvent): String {
        say.bid?.let { return bidTitle(ctx, it) }
        return when (say.text) {
            "Вист!" -> ctx.getString(R.string.game_say_whist)
            "Пас" -> ctx.getString(R.string.game_say_pass)
            "В открытую!" -> ctx.getString(R.string.game_say_open)
            "В закрытую" -> ctx.getString(R.string.game_say_closed)
            else -> say.text ?: ""
        }
    }
}
