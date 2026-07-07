package com.an0obIs.pref.model

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

enum class ScoreValueType { Gora, Pulya, Visty }
enum class GameType { Raspasy, Miser, Normal, Custom }

@Serializable
class Calculation {

    @Serializable
    class Player {
        var name: String = ""
        var gora: Int = 0
        var pulya: Int = 0
        var visty: MutableMap<Int, Int> = mutableMapOf()
        var score: Double = 0.0
    }

    @Serializable
    class ScoreEntry {
        var scoreType: ScoreValueType = ScoreValueType.Gora
        var playerNum: Int = 0
        var refPlayerNum: Int = 0
        var value: Int = 0
    }

    @Serializable
    class GameResult {
        var gameType: GameType = GameType.Raspasy
        var dealer: Int = 0
        var contractor: Int = 0
        var contract: Int = 0
        var taken: MutableMap<Int, Int> = mutableMapOf()
        var visters: MutableList<Int> = mutableListOf()
        var multiplier: Int = 1
        var halfWithDealer: Boolean = false
        var customScore: ScoreEntry? = null

        val isSuccessful: Boolean
            get() {
                if (gameType == GameType.Normal) {
                    return (taken[contractor] ?: 0) >= contract
                }
                if (gameType == GameType.Miser) {
                    return (taken[contractor] ?: 0) == 0
                }
                return false
            }

        val sumVistNeeded: Int
            get() = when (contract) {
                6 -> 4
                7 -> 2
                8 -> 1
                9 -> 1
                else -> 0
            }

        val singleVistNeeded: Int
            get() = when (contract) {
                6 -> 2
                7 -> 1
                8 -> 1
                9 -> 1
                else -> 0
            }
    }

    var limit: Int = 40
    var scores: MutableList<Player> = mutableListOf()
    var created: Long = 0L
    var gameLog: MutableList<GameResult> = mutableListOf()
    var log: MutableList<ScoreEntry> = mutableListOf()
    var dealer: Int = 0
    var rules: GameRules = GameRules()

    constructor()

    constructor(playersCount: Int, limit: Int = 40) : this() {
        this.limit = limit
        created = System.currentTimeMillis()
        rules = AppSettings().rules.clone()
        dealer = Random.Default.nextInt(playersCount)
        for (i in 0 until playersCount) {
            scores.add(Player().also {
                it.name = "Игрок " + (i + 1)
            })
        }
        for (i in 0 until playersCount) {
            for (j in 0 until playersCount)
                if (i != j)
                    scores[i].visty[j] = 0
        }
    }

    val playersCount: Int
        get() = scores.size

    //region Current raspasy progression
    val raspasyLength: Int
        get() {
            var len = 0
            var raspasyFound = false
            for (i in gameLog.indices.reversed()) {
                val game = gameLog[i]
                if (game.gameType == GameType.Raspasy)
                    raspasyFound = true
                if (game.isSuccessful || (rules.miserRaspExit && game.gameType == GameType.Miser))
                    break
                if (game.gameType == GameType.Raspasy)
                    len++
            }
            return if (raspasyFound) len else 0
        }

    val currentRaspasyMultiplier: Int
        get() {
            val length = raspasyLength
            val progress = when (rules.raspasyProgression) {
                RaspasyProgression.NoProgression1 -> return 1
                RaspasyProgression.Arifm1233 -> intArrayOf(1, 2, 3, 3)
                RaspasyProgression.Geom1244 -> intArrayOf(1, 2, 4, 4)
            }
            if (length >= 3)
                return progress[3]
            return progress[length]
        }

    val currentRaspasyExit: Int
        get() {
            val length = raspasyLength
            val progress = when (rules.raspasyExit) {
                RaspasyExit.Easy6 -> intArrayOf(6, 6, 6)
                RaspasyExit.Med677 -> intArrayOf(6, 7, 7)
                RaspasyExit.Hard678 -> intArrayOf(6, 7, 8)
            }
            if (length >= 2)
                return progress[2]
            return progress[length]
        }
    //endregion

    fun writeGame(game: GameResult) {
        if (game.gameType == GameType.Raspasy) {
            // РАСПАСЫ: определяем кто взял меньше всех
            var pMin = -1
            var pMin2 = -1
            var tMin = 100
            var tMin2 = 100
            for (pNum in game.taken.keys) {
                if (game.taken[pNum]!! < tMin) {
                    // Глобальный минимум для списания
                    tMin = game.taken[pNum]!!
                }
                if (pNum != game.dealer || playersCount == 3) {
                    // Минимумы по игрокам не сидящим на прикупе
                    if (game.taken[pNum]!! < tMin2) {
                        pMin = pNum
                        pMin2 = -1
                        tMin2 = game.taken[pNum]!!
                    } else if (game.taken[pNum]!! == tMin2) {
                        pMin2 = pNum
                    }
                }
            }

            if (rules.gameType != RulesGameType.Rostov) {
                // Пишем в горку
                for (pNum in game.taken.keys) {
                    val toAdd = game.multiplier * (game.taken[pNum]!! - tMin)
                    if (toAdd > 0)
                        addValue(ScoreValueType.Gora, toAdd, pNum)
                }
            } else {
                // Пишем висты
                if (pMin2 < 0) {
                    // Один взял меньше всех
                    for (pNum in game.taken.keys) {
                        if (pNum != pMin && (pNum != game.dealer || playersCount == 3)) {
                            val v = rules.vistTakeOnRaspas * (game.taken[pNum]!! - tMin2)
                            if (pMin2 < 0) {
                                addValue(ScoreValueType.Visty, v, pMin, pNum)
                            } else {
                                addValue(ScoreValueType.Visty, v / 2, pMin, pNum)
                                addValue(ScoreValueType.Visty, v / 2, pMin2, pNum)
                            }
                        }
                    }
                }

                // Пишем в пулю сдающему за невзятие
                if (game.taken[game.dealer] == 0) {
                    addValue(ScoreValueType.Pulya, game.multiplier, game.dealer)
                }
            }

            // Пишем в пулю за невзятие
            if (tMin2 == 0) {
                addValue(ScoreValueType.Pulya, game.multiplier, pMin)
                if (pMin2 >= 0)
                    addValue(ScoreValueType.Pulya, game.multiplier, pMin2)
            }
        } else if (game.gameType == GameType.Miser) {
            // МИЗЕР
            if (game.isSuccessful) {
                if (game.halfWithDealer) {
                    addValue(ScoreValueType.Pulya, 5, game.contractor)
                    addValue(ScoreValueType.Pulya, 5, game.dealer)
                } else {
                    addValue(ScoreValueType.Pulya, 10, game.contractor)
                }
            } else {
                if (game.halfWithDealer) {
                    addValue(ScoreValueType.Gora, 5 * game.taken[game.contractor]!!, game.contractor)
                    addValue(ScoreValueType.Gora, 5 * game.taken[game.contractor]!!, game.dealer)
                } else {
                    addValue(ScoreValueType.Gora, 10 * game.taken[game.contractor]!!, game.contractor)
                }
            }
        } else if (game.gameType == GameType.Normal) {
            // ИГРА
            val gameValue = (game.contract - 5) * 2
            var mulct = gameValue
            if (rules.vist == VistType.HalfResponsibility)
                mulct /= 2
            val vistSum = 10 - game.taken[game.contractor]!!

            if (game.isSuccessful) {
                addValue(ScoreValueType.Pulya, gameValue, game.contractor)

                if (game.visters.isEmpty()) {
                    // Никто не вистовал
                    if (game.contract < 8 && game.taken[game.contractor] == game.contract) {
                        // Пишем полвиста
                        for (pNum in game.taken.keys) {
                            if (pNum != game.contractor) {
                                if ((game.contract == 6 && game.taken[pNum] == 2) || (game.contract == 7 && game.taken[pNum] == 1)) {
                                    addValue(ScoreValueType.Visty, 4, pNum, game.contractor)
                                }
                            }
                        }
                    }
                } else if (game.visters.size == 1) {
                    // Один вистующий
                    addValue(ScoreValueType.Visty, gameValue * vistSum, game.visters[0], game.contractor)

                    if (vistSum < game.sumVistNeeded) {
                        // Пишем гору вистующему
                        addValue(ScoreValueType.Gora, mulct * (game.sumVistNeeded - vistSum), game.visters[0])
                    }
                } else if (game.visters.size == 2) {
                    // Два виста
                    addValue(ScoreValueType.Visty, game.taken[game.visters[0]]!! * gameValue, game.visters[0], game.contractor)
                    addValue(ScoreValueType.Visty, game.taken[game.visters[1]]!! * gameValue, game.visters[1], game.contractor)

                    if (vistSum < game.sumVistNeeded) {
                        // Пишем гору вистующим
                        for (vister in game.visters) {
                            val taken = game.taken[vister]!!
                            if (taken < game.singleVistNeeded)
                                addValue(ScoreValueType.Gora, mulct * (game.singleVistNeeded - taken), vister)
                        }
                    }
                }
            } else {
                val left = game.contract - game.taken[game.contractor]!!
                addValue(ScoreValueType.Gora, (game.contract - 5) * 2 * left, game.contractor)

                // Консоляция и висты вистующим
                var consolation = gameValue
                if (rules.consolationBonus == ConsolationSum.Max10)
                    consolation = 10

                if (game.visters.size == 1) {
                    // Один вистующий: определяем пасующего
                    var passer = -1
                    for (pNum in 0 until playersCount) {
                        if ((playersCount == 3 || (game.visters[0] != game.dealer && pNum != dealer)) && pNum != game.visters[0] && pNum != game.contractor) {
                            passer = pNum
                        }
                    }

                    if (passer < 0) {
                        // пасующего нет - вистует "прикупщик" - он пишет все висты на себя
                        addValue(ScoreValueType.Visty, vistSum * gameValue + left * consolation, game.visters[0], game.contractor)
                        // Пишем консоляцию оставшимся
                        for (pNum in 0 until playersCount) {
                            if (pNum != game.contractor && pNum != game.visters[0]) {
                                if (rules.prikupConsolation)
                                    addValue(ScoreValueType.Visty, left * consolation, pNum, game.contractor)
                                else
                                // Если по правилам консоляция прикупщику не положена, то делим остаток пополам
                                    addValue(ScoreValueType.Visty, left * consolation / 2, pNum, game.contractor)
                            }
                        }
                    } else {
                        if (rules.consolation == ConsolationType.Gentlemen) {
                            // Джентельменский вист
                            addValue(ScoreValueType.Visty, vistSum * gameValue / 2 + left * consolation, game.visters[0], game.contractor)
                            addValue(ScoreValueType.Visty, vistSum * gameValue / 2 + left * consolation, passer, game.contractor)
                        } else {
                            // Жлобский вист
                            addValue(ScoreValueType.Visty, vistSum * gameValue + left * consolation, game.visters[0], game.contractor)
                            addValue(ScoreValueType.Visty, left * consolation, passer, game.contractor)
                        }

                        if (rules.prikupConsolation && playersCount == 4) {
                            // Пишем консоляцию "прикупщику"
                            addValue(ScoreValueType.Visty, left * consolation, game.dealer, game.contractor)
                        }
                    }
                } else if (game.visters.size == 2) {
                    // Два виста
                    addValue(ScoreValueType.Visty, game.taken[game.visters[0]]!! * gameValue + left * consolation, game.visters[0], game.contractor)
                    addValue(ScoreValueType.Visty, game.taken[game.visters[1]]!! * gameValue + left * consolation, game.visters[1], game.contractor)

                    if (rules.prikupConsolation && playersCount == 4) {
                        // Пишем консоляцию "прикупщику"
                        addValue(ScoreValueType.Visty, left * consolation, game.dealer, game.contractor)
                    }
                }
            }
        }

        gameLog.add(game)
        if (game.isSuccessful || raspasyLength == 0 || game.gameType == GameType.Raspasy)
            dealer++
        if (dealer >= playersCount)
            dealer = 0
    }

    val isFinished: Boolean
        get() {
            var finished = true
            var sum = 0
            for (score in scores) {
                if (rules.ending == EndingType.Each) {
                    finished = finished && score.pulya == limit
                }
                sum += score.pulya
            }
            if (rules.ending == EndingType.Sum) {
                return sum >= limit
            }
            return finished
        }

    fun addValue(scoreType: ScoreValueType, value: Int, playerNum: Int, refPlayerNum: Int = 0) {
        @Suppress("NAME_SHADOWING")
        var value = value
        var pulyaToGora = 1
        if (rules.scoring == ScoreType.Leningrad)
            pulyaToGora = 2

        if (scoreType == ScoreValueType.Gora) {
            value = scores[playerNum].gora + pulyaToGora * value
            scores[playerNum].gora = value
        } else if (scoreType == ScoreValueType.Pulya) {
            value += scores[playerNum].pulya

            // Проверяем закрытие
            if (rules.ending == EndingType.Each && value > limit) {
                var overdraft = value - limit
                value = limit
                while (overdraft > 0) {
                    var extrem = Int.MIN_VALUE
                    var donor = -1
                    var pNum = playerNum
                    for (i in 0 until playersCount) {
                        pNum++
                        if (pNum >= playersCount)
                            pNum = 0
                        if (pNum != playerNum && (playersCount == 3 || pNum != dealer) && scores[pNum].pulya < limit) {
                            val v = scores[pNum].pulya
                            if (v > extrem) {
                                extrem = v
                                donor = pNum
                            }
                        }
                    }

                    if (donor >= 0) {
                        var toWrite = scores[donor].pulya + overdraft
                        var part = overdraft
                        overdraft = 0
                        if (toWrite > limit) {
                            overdraft = toWrite - limit
                            part -= overdraft
                            toWrite = limit
                        }
                        setValue(ScoreValueType.Pulya, toWrite, donor, 0, false)
                        addValue(ScoreValueType.Visty, part * 10, playerNum, donor)
                    } else {
                        addValue(ScoreValueType.Gora, -overdraft, playerNum)
                        overdraft = 0
                    }
                }
            }

            scores[playerNum].pulya = value
        } else if (scoreType == ScoreValueType.Visty) {
            value = scores[playerNum].visty[refPlayerNum]!! + pulyaToGora * value
            scores[playerNum].visty[refPlayerNum] = value
        }

        log.add(ScoreEntry().also {
            it.scoreType = scoreType
            it.playerNum = playerNum
            it.refPlayerNum = refPlayerNum
            it.value = value
        })
    }

    fun setValue(scoreType: ScoreValueType, value: Int, playerNum: Int, refPlayerNum: Int = 0, custom: Boolean = true) {
        var diff = 0
        if (scoreType == ScoreValueType.Gora) {
            diff = value - scores[playerNum].gora
            scores[playerNum].gora = value
        } else if (scoreType == ScoreValueType.Pulya) {
            diff = value - scores[playerNum].pulya
            scores[playerNum].pulya = value
        } else if (scoreType == ScoreValueType.Visty) {
            diff = value - scores[playerNum].visty[refPlayerNum]!!
            scores[playerNum].visty[refPlayerNum] = value
        }
        if (custom)
            gameLog.add(GameResult().also {
                it.gameType = GameType.Custom
                it.customScore = ScoreEntry().also { e ->
                    e.scoreType = scoreType
                    e.playerNum = playerNum
                    e.refPlayerNum = refPlayerNum
                    e.value = diff
                }
            })
        log.add(ScoreEntry().also {
            it.scoreType = scoreType
            it.playerNum = playerNum
            it.refPlayerNum = refPlayerNum
            it.value = value
        })
    }

    fun getValueHistory(scoreType: ScoreValueType, playerNum: Int, refPlayerNum: Int = 0): String {
        var vals = log.filter { it.scoreType == scoreType && it.playerNum == playerNum && it.refPlayerNum == refPlayerNum }
            .map { it.value }

        var res = ""
        if (vals.size > 10) {
            res = "..."
            vals = vals.drop(vals.size - 10)
        }
        var f = true
        for (v in vals) {
            if (!f)
                res += "."
            f = false
            res += v.toString()
        }
        return res
    }

    /** Final settlement: distribute pulya and gora into each player's score. */
    fun calc() {
        // Копируем данные
        val tmp = mutableListOf<Player>()

        var sumPulya = 0
        for (sc in scores) {
            val pl = Player().also {
                it.gora = sc.gora
                it.pulya = sc.pulya
            }
            sumPulya += pl.pulya
            for (p in sc.visty.keys) {
                pl.visty[p] = sc.visty[p]!!
            }
            tmp.add(pl)
        }

        // Расписываем пулю
        var pulyaToGora = 1
        if (rules.scoring == ScoreType.Leningrad)
            pulyaToGora = 2

        val avgPulya = sumPulya / playersCount
        var sumGora = 0
        for (i in 0 until playersCount) {
            tmp[i].gora += pulyaToGora * (avgPulya - tmp[i].pulya)
            sumGora += tmp[i].gora
        }

        // Расписываем гору
        val avgGora = (10 * sumGora.toDouble()) / playersCount
        for (i in 0 until playersCount) {
            tmp[i].score = avgGora - (tmp[i].gora * 10)
            // Подсчитываем висты
            for (j in 0 until playersCount) {
                if (i != j) {
                    tmp[i].score += (tmp[i].visty[j]!! - tmp[j].visty[i]!!).toDouble()
                }
            }
            scores[i].score = tmp[i].score
        }
    }

    /**
     * Copy with the player columns rearranged: new index i takes old column
     * order[i]. Every player reference (visty keys, game log, score log,
     * dealer) is remapped, so a saved pulka can seat its players differently
     * when a multiplayer game resumes from it.
     */
    fun reordered(order: List<Int>): Calculation {
        val inv = IntArray(playersCount)
        for (i in order.indices) inv[order[i]] = i
        fun mp(p: Int) = if (p in 0 until playersCount) inv[p] else p
        fun mpEntry(c: ScoreEntry) = ScoreEntry().also {
            it.scoreType = c.scoreType
            it.playerNum = mp(c.playerNum)
            it.refPlayerNum = mp(c.refPlayerNum)
            it.value = c.value
        }
        val out = Calculation()
        out.limit = limit
        out.created = created
        out.dealer = mp(dealer)
        out.rules = rules.clone()
        out.scores = order.map { old ->
            val s = scores[old]
            Player().also { n ->
                n.name = s.name
                n.gora = s.gora
                n.pulya = s.pulya
                n.score = s.score
                for ((k, v) in s.visty) n.visty[mp(k)] = v
            }
        }.toMutableList()
        out.gameLog = gameLog.map { g ->
            GameResult().also { n ->
                n.gameType = g.gameType
                n.dealer = mp(g.dealer)
                n.contractor = mp(g.contractor)
                n.contract = g.contract
                n.taken = g.taken.entries.associate { (k, v) -> mp(k) to v }.toMutableMap()
                n.visters = g.visters.map { mp(it) }.toMutableList()
                n.multiplier = g.multiplier
                n.halfWithDealer = g.halfWithDealer
                n.customScore = g.customScore?.let { mpEntry(it) }
            }
        }.toMutableList()
        out.log = log.map { mpEntry(it) }.toMutableList()
        return out
    }

    fun save() {
        val name = getFileName(created, playersCount, limit)
        save(name)
    }

    fun save(name: String) {
        PrefStorage.deleteFamily(name)
        PrefStorage.writeText(name, PrefStorage.json.encodeToString(serializer(), this))
    }

    fun saveLast() {
        save("lastcalc.json")
    }

    companion object {
        private fun fileDate(created: Long): String =
            SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date(created))

        fun getFileName(created: Long, playersCount: Int, limit: Int): String =
            "pulya_${fileDate(created)}_${playersCount}_${limit}.json"

        fun parseFileDate(s: String): Long =
            SimpleDateFormat("yyyyMMddHHmmss", Locale.US).parse(s)?.time ?: 0L

        fun load(created: Long, playersCount: Int, limit: Int): Calculation? {
            val name = getFileName(created, playersCount, limit)
            return load(name)
        }

        fun load(name: String): Calculation? {
            val text = PrefStorage.readText(name) ?: return null
            return PrefStorage.json.decodeFromString(serializer(), text)
        }

        fun loadLast(): Calculation? {
            return try {
                load("lastcalc.json")
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Which pulka column each seat should take: match by name first
         * (trimmed, case-insensitive), the rest keep their relative order.
         */
        fun seatOrder(seatNames: List<String>, calc: Calculation): List<Int> {
            val n = calc.playersCount
            val taken = BooleanArray(n)
            val order = IntArray(minOf(seatNames.size, n)) { -1 }
            for (i in order.indices) {
                val name = seatNames[i].trim().lowercase()
                val hit = (0 until n).firstOrNull {
                    !taken[it] && calc.scores[it].name.trim().lowercase() == name
                }
                if (hit != null) {
                    order[i] = hit
                    taken[hit] = true
                }
            }
            for (i in order.indices) {
                if (order[i] < 0) {
                    val free = (0 until n).first { !taken[it] }
                    order[i] = free
                    taken[free] = true
                }
            }
            return order.toList()
        }
    }
}
