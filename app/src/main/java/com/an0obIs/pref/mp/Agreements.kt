package com.an0obIs.pref.mp

import com.an0obIs.pref.model.Game
import com.an0obIs.pref.model.GamePhase
import com.an0obIs.pref.model.GameType
import com.an0obIs.pref.ui.game.TableInfo

/**
 * Agreement («расписать») rules shared by the host, guests and single player.
 *
 * All functions here work in the VIEWER-RELATIVE frame of a [TableInfo]
 * (seat 0 = the viewer), which is identical to the absolute frame on the
 * host/single-player table.
 */
object Agreements {

    /** May this viewer open the offer menu right now? */
    fun canOffer(info: TableInfo): Boolean {
        if (info.phase != GamePhase.Playing) return false
        return when (info.currentGameType) {
            GameType.Miser ->
                !info.watching // any of the three players; the sitting dealer only responds
            GameType.Normal -> {
                val whisters = info.isVister.filterValues { it }.keys
                if (whisters.isEmpty()) return false // nobody to agree with
                !info.watching && (info.contractor == 0 || 0 in whisters)
            }
            else -> false
        }
    }

    /** Remaining (unresolved) tricks; the unfinished trick counts as remaining. */
    private fun remaining(info: TableInfo): Int = 10 - info.taken.sum()

    /**
     * Step 1: possible final trick counts for the declarer.
     * Normal games: contract-3 .. 10 clipped to what is still reachable and
     * to what the other hands' current takes allow. Misère: 1..3.
     */
    fun declarerOptions(info: TableInfo): List<Int> {
        val c = info.contractor
        val tC = info.taken.getOrElse(c) { 0 }
        val r = remaining(info)
        val othersTaken = info.taken.sum() - tC
        return if (info.currentGameType == GameType.Miser) {
            (1..3).filter { it >= tC && it <= tC + r }
        } else {
            val contract = info.maxBid?.contract ?: return emptyList()
            ((contract - 3).coerceAtLeast(0)..10).filter { n ->
                n >= tC && n <= tC + r && 10 - n >= othersTaken
            }
        }
    }

    /** Whister seats (viewer-relative). */
    fun whisters(info: TableInfo): List<Int> =
        info.isVister.filterValues { it }.keys.sorted()

    /**
     * Step 2 (only with two whisters in a normal game): the possible final
     * (first whister, second whister) splits for a given declarer count.
     */
    fun whistSplits(info: TableInfo, declarerTakes: Int): List<Pair<Int, Int>> {
        val w = whisters(info)
        if (w.size != 2) return emptyList()
        val (a, b) = w[0] to w[1]
        val tA = info.taken.getOrElse(a) { 0 }
        val tB = info.taken.getOrElse(b) { 0 }
        val r = remaining(info)
        val pool = 10 - declarerTakes
        return (tA..(tA + r)).mapNotNull { fa ->
            val fb = pool - fa
            if (fb in tB..(tB + r) && fa + fb == pool) fa to fb else null
        }
    }

    /**
     * The full final-taken list (viewer-relative, 3 entries) for an offer.
     * With one whister the passer keeps their current takes and the whister
     * gets the rest; их судьбу решает вистующий.
     */
    fun buildTaken(info: TableInfo, declarerTakes: Int, split: Pair<Int, Int>? = null): List<Int> {
        val res = info.taken.toMutableList()
        val c = info.contractor
        res[c] = declarerTakes
        if (info.currentGameType == GameType.Miser) {
            // catchers' individual counts don't affect miser scoring; hand the
            // remainder to the first catcher for a consistent total of 10
            val catchers = (0..2).filter { it != c }
            res[catchers[1]] = info.taken[catchers[1]]
            res[catchers[0]] = 10 - declarerTakes - res[catchers[1]]
        } else {
            val w = whisters(info)
            when (w.size) {
                1 -> {
                    val passer = (0..2).first { it != c && it != w[0] }
                    res[passer] = info.taken[passer]
                    res[w[0]] = 10 - declarerTakes - res[passer]
                }
                2 -> {
                    val s = split ?: return res
                    res[w[0]] = s.first
                    res[w[1]] = s.second
                }
            }
        }
        return res
    }

    /** Is this the declarer's unilateral surrender («без 3, застрелиться»)? */
    fun isSurrender(info: TableInfo, offererRel: Int, declarerTakes: Int): Boolean {
        if (info.currentGameType != GameType.Normal) return false
        val contract = info.maxBid?.contract ?: return false
        return offererRel == info.contractor && declarerTakes == contract - 3
    }

    /**
     * The conservative bot rule: accept only if the offer is at least as good
     * as the bot's best possible outcome over ALL continuations (zero-sum:
     * an opponent's gain counts as the bot's loss). Absolute game frame.
     */
    fun botAccepts(botSeat: Int, game: Game, finalTaken: Map<Int, Int>): Boolean {
        val c = game.contractor
        val offered = finalTaken[c] ?: return false
        val tC = game.deal.hands[c].taken
        val r = 10 - game.deal.totalTaken
        return if (game.currentGameType == GameType.Miser) {
            if (botSeat == c) offered <= tC // best case: catches nothing more
            else offered >= tC + r // best case: the misère catches everything
        } else {
            if (botSeat == c) offered >= tC + r // best case: takes all the rest
            else {
                val own = finalTaken[botSeat] ?: return false
                // best case for a whister: the declarer is stopped cold AND
                // the whister itself takes every remaining trick
                offered <= tC && own >= game.deal.hands[botSeat].taken + r
            }
        }
    }
}
