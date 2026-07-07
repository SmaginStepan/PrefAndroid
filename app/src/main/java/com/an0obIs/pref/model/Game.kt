package com.an0obIs.pref.model

import com.an0obIs.pref.ai.AI
import com.an0obIs.pref.ai.AIInfo
import com.an0obIs.pref.ai.Helper
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.random.Random

enum class GamePhase { NotStarted, Negotiations, VistNegotiations, PrikupOpened, Discarding, GameChoose, OpeningChoose, Playing, EndTurn, EndPlay, ScoreView, Ended }

@Serializable
class Game {

    //region Данные
    var deal: Deal = Deal()
    var calc: Calculation = Calculation()
    var phase: GamePhase = GamePhase.NotStarted
    var playerInTurn: Int = 0
    var contractor: Int = 0
    var contract: Int = 0
    var trump: Int = 0
    var currentGameType: GameType = GameType.Raspasy
    var isVister: MutableMap<Int, Boolean> = mutableMapOf()
    var playersToWait: Int = 0

    @Serializable
    class Bid {
        var trump: Int = 0
        var contract: Int = 0
        var pas: Boolean = false
        var miser: Boolean = false

        override fun toString(): String = title

        // Note: display text; the Compose UI localizes bids itself, this stays for logs.
        val title: String
            get() {
                if (pas)
                    return "Пас"
                if (miser)
                    return "Мизер!"
                val trumpName = when (trump) {
                    0 -> "♠ пик"
                    1 -> "♣ треф"
                    2 -> "♦ бубей"
                    3 -> "♥ червей"
                    else -> "без козыря"
                }
                return "$contract $trumpName"
            }
    }

    var curentBids: MutableMap<Int, Bid> = mutableMapOf()
    var maxBid: Bid? = null
    var aIs: MutableMap<Int, AIInfo> = mutableMapOf()

    /** Replacement for the WP7 BackgroundWorker.ReportProgress: UI refresh signal. */
    @Transient
    var onProgress: (() -> Unit)? = null

    /**
     * Multiplayer hosting: when true, the loop never auto-plays anyone —
     * every seat waits for the external driver (HostGameSession), which
     * dispatches to the local UI, the AI, or a remote player.
     */
    @Transient
    var externalDriver: Boolean = false

    /**
     * The game plays exactly one deal and then Ends (instead of dealing the
     * next). Used by 4-player multiplayer, where every deal is a 3-player
     * game among the non-dealers and the session owns the match.
     */
    @Transient
    var singleDealMode: Boolean = false

    class Animation {
        var player: Int = 0
        var card: Card? = null
        var bid: Bid? = null
        var text: String? = null
    }

    @Transient
    var animations: ArrayDeque<Animation> = ArrayDeque()

    private fun addAnimation(animation: Animation) {
        animations.addLast(animation)
    }

    @Transient
    private val rnd = Random.Default
    //endregion

    //region Очерёдность игроков и определение ИИ
    // Передаём очерёдность хода следующему игроку
    fun getNextPlayer(): Int {
        var player = playerInTurn
        player++
        if (player >= 3)
            player = 0
        return player
    }

    fun getPrevPlayer(): Int {
        var player = playerInTurn
        player--
        if (player < 0)
            player = 2
        return player
    }

    private fun nextPlayer() {
        playerInTurn = getNextPlayer()
    }

    // Передаём очерёдность хода первому игроку (следующему за сдающим)
    private fun firstPlayer() {
        playerInTurn = calc.dealer
        nextPlayer()
    }

    fun getFirstPlayer(): Int {
        var num = calc.dealer
        num++
        if (num >= calc.playersCount)
            num = 0
        return num
    }

    val isOpened: Boolean
        get() = deal.hands.count { it.isVisible } > 1

    // Является ли текущий игрок ИИ?
    private fun isAI(): Boolean {
        if (externalDriver)
            return false
        val isOpened = isOpened
        val playerIsVister = contractor != 0 && isVister.containsKey(0) && isVister[0] == true
        val isVistPlaying = contractor != playerInTurn && phase == GamePhase.Playing && currentGameType == GameType.Normal
        if (isVistPlaying && playerIsVister && isOpened)
            return false // Если игрок является вистующим или пасующим и играем в открытую, то он решает как ходить
        if (phase == GamePhase.Playing && currentGameType == GameType.Miser && contractor != 0 && playerInTurn != contractor)
            return false // Игрок всегда сам решает как ловить мизер
        if (playerInTurn > 0)
            return true
        if (isVistPlaying && !playerIsVister && isOpened)
            return true // Если игрок спасовал, то он не ходит сам
        return false
    }

    /**
     * Who decides the current move. Normally the player in turn, but in an
     * open normal game the whister also plays the passing player's cards
     * (same rule isAI() applies in single player; hosted multiplayer needs
     * it explicitly because externalDriver disables isAI()).
     */
    fun turnController(): Int {
        if (phase == GamePhase.Playing && currentGameType == GameType.Normal &&
            playerInTurn != contractor && isOpened && isVister[playerInTurn] != true
        ) {
            val whister = isVister.entries.firstOrNull { it.value }?.key
            if (whister != null)
                return whister
        }
        return playerInTurn
    }
    //endregion

    //region Начало игры
    companion object {
        /** Port of the C# Game() constructor (which read settings and created players). */
        fun create(ai1Name: String = "Первый", ai2Name: String = "Второй"): Game {
            val game = Game()
            val settings = AppSettings()
            game.calc = Calculation(3, settings.limit)
            game.calc.scores[0].name = settings.playerName
            game.calc.scores[1].name = ai1Name
            game.calc.scores[2].name = ai2Name
            game.calc.dealer = Random.Default.nextInt(3)
            game.aIs = mutableMapOf()
            game.phase = GamePhase.NotStarted
            return game
        }

        fun load(name: String): Game? {
            val text = PrefStorage.readText(name) ?: return null
            val game = PrefStorage.json.decodeFromString(serializer(), text)
            game.deal.restoreAfterLoad()
            game.aIs.values.forEach { it.restoreAfterLoad() }
            return game
        }

        fun loadLast(): Game? {
            return try {
                load("lastgame.json")
            } catch (e: Exception) {
                null
            }
        }
    }

    // Начало новой раздачи
    fun newDeal() {
        curentBids = mutableMapOf()
        isVister = mutableMapOf()
        deal = Deal()
        // in single player seat 0 is the local human; in hosted multiplayer
        // no hand is publicly visible until the play opens it
        deal.hands[0].isVisible = !externalDriver
        phase = GamePhase.Negotiations
        maxBid = null
        trump = -1
        // Создаём информацию для ИИ
        firstPlayer()
        for (i in 0 until 3) {
            AIInfo.create(this)
            nextPlayer()
        }

        firstPlayer()
        onProgress?.invoke()
        next()
    }
    //endregion

    //region Основной цикл
    fun next() {
        when (phase) {
            GamePhase.NotStarted -> newDeal()
            GamePhase.Negotiations -> negotiationsNext()
            GamePhase.PrikupOpened -> prikupNext()
            GamePhase.Discarding -> discardingNext()
            GamePhase.GameChoose -> chooseNext()
            GamePhase.VistNegotiations -> vistNext()
            GamePhase.Playing -> playNext()
            GamePhase.EndPlay -> endNext()
            GamePhase.ScoreView -> scoreNext()
            GamePhase.EndTurn -> turnNext()
            GamePhase.OpeningChoose -> openingChooseNext()
            GamePhase.Ended -> {}
        }
    }
    //endregion

    //region Загрузка и сохранение
    fun save(name: String) {
        PrefStorage.deleteFamily(name)
        PrefStorage.writeText(name, PrefStorage.json.encodeToString(serializer(), this))
    }

    fun saveLast() {
        save("lastgame.json")
    }
    //endregion

    //region Торговля
    private fun incBid(bid: Bid): Bid? {
        val res = Bid().also {
            it.contract = bid.contract
            it.trump = bid.trump + 1
        }
        if (res.trump > 4) {
            res.trump = 0
            res.contract++
        }
        if (res.contract > 10) {
            return null
        }
        return res
    }

    // Возвращаем все допустимые варианты объявления игры
    fun getAllowedBids(): MutableList<Bid> {
        val list = mutableListOf<Bid>()
        var curBid: Bid?
        val maxBid = maxBid
        if (maxBid == null) {
            curBid = Bid().also {
                it.contract = calc.currentRaspasyExit
                it.trump = 0
            }
        } else if (maxBid.miser) {
            curBid = Bid().also {
                it.contract = 9
                it.trump = 0
            }
        } else {
            curBid = maxBid
            if (phase == GamePhase.Negotiations) {
                var prevBid: Bid? = null
                if (curentBids.containsKey(getPrevPlayer()))
                    prevBid = curentBids[getPrevPlayer()]
                if (prevBid == null || !prevBid.pas)
                    curBid = incBid(curBid)
            }
        }
        if (phase == GamePhase.Negotiations)
            list.add(Bid().also { it.pas = true })
        if (curBid != null && curBid.contract < 9 && !curentBids.containsKey(playerInTurn)) {
            list.add(Bid().also { it.miser = true })
        }
        while (curBid != null) {
            list.add(curBid)
            curBid = incBid(curBid)
        }
        return list
    }

    // Действие торговли
    // Если объявляется игра, которая недопустима по правилам, то возвращается false
    fun makeBid(bid: Bid): Boolean {
        val player = playerInTurn
        // Проверяем, можно ли объявить такую игру по правилам
        if (!bid.pas) {
            maxBid = bid
            contractor = player
        }

        addAnimation(Animation().also {
            it.player = playerInTurn
            it.bid = bid
        })

        curentBids[player] = bid

        nextPlayer()
        return true
    }

    // Проверка окончания торгов
    private fun negitiationsEnded(): Boolean {
        var cntPas = 0
        for (cbid in curentBids.values) {
            if (cbid.pas)
                cntPas++
        }
        if (cntPas == 2 && curentBids.size == 3) {
            if (maxBid!!.miser) {
                // Начинаем мизер
                currentGameType = GameType.Miser
                isVister.clear()
                isVister[getNextPlayer()] = false
                isVister[getPrevPlayer()] = false
                opening = true
                // Contractor уже установлен
                phase = GamePhase.PrikupOpened
                playersToWait = 3
                next()
            } else {
                // Начинаем игру
                opening = false
                currentGameType = GameType.Normal
                phase = GamePhase.PrikupOpened
                playersToWait = 3
                next()
            }
        } else if (cntPas == 3) {
            // Начинаем распасы
            currentGameType = GameType.Raspasy
            trump = -1
            phase = GamePhase.Playing
            firstMovePerformer = -1
            opening = false
            raspasyPrikup()
            next()
        } else {
            return false
        }
        return true
    }

    private fun negotiationsNext() {
        // Проверяем, окончены ли торги?
        if (!negitiationsEnded()) {
            // Торги не окончены
            if (curentBids.containsKey(playerInTurn) && curentBids[playerInTurn]!!.pas) {
                // Игрок уже спасовал
                nextPlayer()
                next()
                return
            }
            if (isAI()) {
                AI.makeMove(this)
            } else {
                // Ждём ответа игрока
            }
        }
    }
    //endregion

    //region Открытие прикупа
    /** Игрок посмотрел прикуп */
    fun prikupClose() {
        playersToWait--
        nextPlayer()
    }

    fun prikupNext() {
        deal.prikup.isVisible = true
        if (playersToWait == 0) {
            // Все посмотрели прикуп
            deal.prikup.isVisible = false
            playerInTurn = contractor
            for (card in deal.prikup.cards) {
                deal.hands[playerInTurn].cards.add(card)
            }
            deal.prikup.cards.clear()
            deal.hands[playerInTurn].sort()
            phase = GamePhase.Discarding

            next()
        } else if (isAI()) {
            AI.makeMove(this)
        } else {
            // Ждём ответа игрока
        }
    }
    //endregion

    //region Сброс лишних карт
    fun discardCard(card: Card) {
        val cards = deal.hands[playerInTurn].cards
        var pos = -1
        for (i in cards.indices) {
            if (cards[i].coatColor == card.coatColor && cards[i].value == card.value) {
                pos = i
            }
        }
        if (pos >= 0) {
            deal.prikup.cards.add(card)
            deal.prikup.sort()
            deal.hands[playerInTurn].cards.removeAt(pos)
            deal.hands[playerInTurn].sort()
        }
    }

    fun discardingNext() {
        if (deal.hands[playerInTurn].cards.size == 12) {
            val ai = aIs[playerInTurn]!!
            val disc = Helper.getPotentialDiscards(ai, this)
            for (ainfo in aIs.values) {
                ainfo.potentialDiscard = disc
            }
        }
        if (deal.hands[playerInTurn].cards.size > 10) {
            if (isAI()) {
                AI.makeMove(this)
            } else {
                // Ждём игрока
            }
        } else {
            onProgress?.invoke()
            if (currentGameType != GameType.Miser) {
                // переходим к фазе объявления игры
                phase = GamePhase.GameChoose
                playerInTurn = contractor
                next()
            } else {
                // Мизер: играем
                phase = GamePhase.Playing
                firstMovePerformer = -1
                opening = true
                firstPlayer()
                next()
            }
        }
    }
    //endregion

    //region Объявление игры
    /** Игрок объявляет игру */
    fun setContract(contract: Bid) {
        addAnimation(Animation().also {
            it.player = playerInTurn
            it.bid = contract
        })
        this.contract = contract.contract
        trump = contract.trump
        maxBid = contract
        isVister.clear()
        // Переходим к объявлению вистующих
        phase = GamePhase.VistNegotiations
        nextPlayer()
        if (calc.rules.stalindgrad && contract.contract == 6 && contract.trump == 0) {
            // Сталинград: все вистуют
            for (i in 0 until 3) {
                if (i != contractor)
                    isVister[i] = true
            }
            phase = GamePhase.Playing
            firstMovePerformer = -1
            firstPlayer()
        }
    }

    fun chooseNext() {
        if (isAI()) {
            AI.makeMove(this)
        } else {
            // Ждём действий игрока
        }
    }
    //endregion

    //region Определение вистующих
    // Действие определения вистующих
    fun setVist(isVist: Boolean) {
        val player = playerInTurn
        addAnimation(Animation().also {
            it.player = playerInTurn
            it.text = if (isVist) "Вист!" else "Пас"
        })
        isVister[player] = isVist
        nextPlayer()
    }

    private fun vistEnded(): Boolean {
        if (isVister.size == 2) {
            if (isVister.values.count { it } == 0 && getPrevPlayer() == contractor) {
                // Если никто не вистовал, даём ещё один шанс первому вистующему:
                return false
            }
            return true
        }
        return false
    }

    private fun vistNext() {
        if (playerInTurn == contractor) {
            nextPlayer()
        }
        // Определяем, что висты закончены
        if (!vistEnded()) {
            // Висты не окончены
            if (isAI()) {
                AI.makeMove(this)
            } else {
                // Ждём ответа игрока
            }
        } else {
            val vistersCount = isVister.values.count { it }
            if (vistersCount == 0) {
                // Сразу записываем результат
                deal.hands[contractor].taken = contract
                // Полвиста:
                if (contract < 8)
                    deal.hands[playerInTurn].taken = Calculation.GameResult().also {
                        it.contract = contract
                    }.singleVistNeeded
                writeGameResult()
                next()
            } else if (vistersCount == 1) {
                // Определяем, как будем играть: в открытую или в закрытую
                phase = GamePhase.OpeningChoose
                playerInTurn = isVister.filter { it.value }.keys.first()
                next()
            } else {
                // Играем
                phase = GamePhase.Playing
                firstMovePerformer = -1
                firstPlayer()
                next()
            }
        }
    }
    //endregion

    //region Определение режима вистования (в открытую\в закрытую)
    var opening: Boolean = false

    fun setOpeningChoice(open: Boolean) {
        addAnimation(Animation().also {
            it.player = playerInTurn
            it.text = if (open) "В открытую!" else "В закрытую"
        })
        opening = open
        phase = GamePhase.Playing
        firstMovePerformer = -1
        firstPlayer()
    }

    private fun openingChooseNext() {
        if (isAI()) {
            AI.makeMove(this)
        } else {
            // Ждём игрока
        }
    }
    //endregion

    //region Игра
    var firstMovePerformer: Int = 0

    fun getAllowedMoves(): MutableList<Card> {
        val list = mutableListOf<Card>()
        for (card in deal.hands[playerInTurn].cards) {
            if (playCard(card, true))
                list.add(card)
        }
        return list
    }

    // Действие игры: возвращает false, если действие недопустимо
    fun playCard(card: Card, onlyCheck: Boolean = false): Boolean {
        val player = playerInTurn
        val hand = deal.hands[player]
        if (!onlyCheck && firstMovePerformer < 0)
            firstMovePerformer = player
        for (c in hand.cards.toList()) {
            if (c.coatColor == card.coatColor && c.value == card.value) {
                if (deal.inPlay.isEmpty()) {
                    deal.inPlayCoatColor = card.coatColor
                }
                val hasColor = hand.hasCoatColor(deal.inPlayCoatColor)
                val hasTrump = hand.hasCoatColor(trump)
                if (hasColor && deal.inPlayCoatColor != card.coatColor) {
                    // Нельзя класть карту не в масть, если есть карта в масть
                    return false
                }
                if (!hasColor && hasTrump && card.coatColor != trump) {
                    // Нельзя класть не козыря, если есть козырь
                    return false
                }
                if (!onlyCheck) {
                    if (isAI()) {
                        addAnimation(Animation().also {
                            it.player = playerInTurn
                            it.card = card
                        })
                    }

                    deal.hands[player].cards.remove(c)
                    deal.hands[player].sort()
                    deal.inPlay[player] = card
                    nextPlayer()
                }

                return true
            }
        }
        return false
    }

    private fun writeGameResult() {
        phase = GamePhase.EndPlay
        playersToWait = 3
    }

    private fun playNext() {
        // Открываем карты
        if (deal.totalTaken == 0 && opening && playerInTurn != contractor) {
            for (i in 0 until calc.playersCount) {
                if (i == contractor)
                    continue
                deal.hands[i].isVisible = true
            }
        }

        val isPrik4 = isTurnWithPrikup()
        if (deal.totalTaken == 10) {
            // Игра закончена
            writeGameResult()

            next()
        } else if ((isPrik4 && deal.inPlay.size == 4) || (!isPrik4 && deal.inPlay.size == 3)) {
            // Розыгрыш закончен
            phase = GamePhase.EndTurn
            playersToWait = 3
            // Берёт игрок со старшей картой
            var maxCard: Card? = null
            var maxPlayer = -1
            for (player in deal.inPlay.keys) {
                if (player < 0 || player > 2)
                    continue
                val card = deal.inPlay[player]!!

                if (maxCard == null || card.greaterThan(maxCard, trump, deal.inPlayCoatColor)) {
                    maxPlayer = player
                    maxCard = card
                }
            }
            playerToTake = maxPlayer
            next()
        } else if (isAI()) {
            AI.makeMove(this)
        } else {
            val info = aIs[playerInTurn]!!
            info.writeOutOfPlay(this)
            // Ждём игрока
        }
    }
    //endregion

    //region Просмотр взятки
    /** Игрок посмотрел взятку */
    fun turnClose() {
        playersToWait--
        nextPlayer()
    }

    var playerToTake: Int = 0

    private fun turnNext() {
        if (playersToWait == 0) {
            // Все посмотрели результат
            phase = GamePhase.Playing
            firstMovePerformer = -1
            // Берёт игрок со старшей картой...

            deal.inPlay.clear()
            deal.hands[playerToTake].taken++
            playerInTurn = playerToTake
            raspasyPrikup()
            next()
        } else {
            val info = aIs[playerInTurn]!!
            info.writeOutOfPlay(this)
            if (isAI()) {
                AI.makeMove(this)
            } else {
                // Ждём игрока
            }
        }
    }

    private fun isTurnWithPrikup(): Boolean {
        return currentGameType == GameType.Raspasy && calc.rules.gameType != RulesGameType.Rostov && deal.totalTaken < 2
    }

    private fun raspasyPrikup() {
        if (isTurnWithPrikup()) {
            // Открываем масть из прикупа
            addAnimation(Animation().also {
                it.player = -1
                it.card = deal.prikup.cards[0]
            })
            deal.inPlayCoatColor = deal.prikup.cards[0].coatColor
            deal.inPlay[-1] = deal.prikup.cards[0]
            deal.prikup.cards.removeAt(0)
            firstPlayer()
        }
    }
    //endregion

    //region Конец розыгрыша
    /** Игрок посмотрел результат игры */
    fun endConfirm() {
        playersToWait--
        nextPlayer()
    }

    fun getGameResult(): Calculation.GameResult {
        val result = Calculation.GameResult().also {
            it.gameType = currentGameType
            it.contract = contract
            it.contractor = contractor
            it.dealer = calc.dealer
            it.multiplier = calc.currentRaspasyMultiplier
            it.visters = isVister.filter { v -> v.value }.keys.toMutableList()
        }
        result.taken = mutableMapOf()
        for (i in 0 until 3) {
            val hand = deal.hands[i]
            result.taken[i] = hand.taken
        }
        return result
    }

    private fun endNext() {
        if (playersToWait == 0) {
            // Все посмотрели счёт
            val result = getGameResult()
            calc.writeGame(result)
            phase = GamePhase.ScoreView
            playersToWait = 3
            next()
        } else if (isAI()) {
            AI.makeMove(this)
        } else {
            // Ждём игрока
        }
    }
    //endregion

    //region Просмотр результата
    /** Игрок посмотрел результат игры */
    fun scoreClose() {
        playersToWait--
        nextPlayer()
    }

    private fun scoreNext() {
        if (playersToWait == 0) {
            // Все посмотрели счёт
            if (singleDealMode) {
                // 4-player match: the session owns the deal cycle
                phase = GamePhase.Ended
            } else if (!calc.isFinished) {
                // Новый розыгрыш
                newDeal()
            } else {
                // Игра окончена!
                phase = GamePhase.Ended
            }
        } else if (isAI()) {
            AI.makeMove(this)
        } else {
            // Ждём игрока
        }
    }
    //endregion
}
