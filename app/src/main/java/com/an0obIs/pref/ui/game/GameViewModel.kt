package com.an0obIs.pref.ui.game

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.an0obIs.pref.PrefApp
import com.an0obIs.pref.R
import com.an0obIs.pref.ai.AI
import com.an0obIs.pref.ai.AIInfo
import com.an0obIs.pref.mp.GameMsg
import com.an0obIs.pref.mp.HostGameSession
import com.an0obIs.pref.mp.RemoteViews
import com.an0obIs.pref.mp.ScoreSnap
import com.an0obIs.pref.mp.SeatKind
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.an0obIs.pref.model.Calculation
import com.an0obIs.pref.model.Card
import com.an0obIs.pref.model.Game
import com.an0obIs.pref.model.GamePhase
import com.an0obIs.pref.model.GameType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Immutable snapshot of everything the table UI needs to render texts. */
@kotlinx.serialization.Serializable
data class TableInfo(
    val phase: GamePhase = GamePhase.NotStarted,
    val names: List<String> = listOf("", "", ""),
    val dealer: Int = 0,
    val taken: List<Int> = listOf(0, 0, 0),
    val currentGameType: GameType = GameType.Raspasy,
    val contractor: Int = 0,
    val isVister: Map<Int, Boolean> = emptyMap(),
    val curentBids: Map<Int, Game.Bid> = emptyMap(),
    val maxBid: Game.Bid? = null,
    val playerToTake: Int = 0,
    val playerInTurn: Int = 0,
    /** who acts now; differs from playerInTurn when the whister moves for the passer */
    val controller: Int = 0,
    val gameResult: Calculation.GameResult? = null,
    val showPrikupBtn1: Boolean = false,
    val showPrikupBtn2: Boolean = false,
    val showTricksBtn: Boolean = false
)

data class CardAnim(val card: Card, val fromX: Double, val fromY: Double, val toX: Double, val toY: Double)
data class TrickAnim(val cards: List<PlacedCard>, val toX: Double, val toY: Double)
data class SayEvent(val player: Int, val bid: Game.Bid?, val text: String?)

class GameViewModel : ViewModel() {

    lateinit var game: Game
        private set
    private lateinit var app: PrefApp

    var field by mutableStateOf<List<PlacedCard>>(emptyList())
        private set
    val pinnedOverlays = mutableStateListOf<PlacedCard>()
    var info by mutableStateOf(TableInfo())
        private set

    var thinking by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set

    var cardAnim by mutableStateOf<CardAnim?>(null)
        private set
    var trickAnim by mutableStateOf<TrickAnim?>(null)
        private set
    var animProgress by mutableStateOf(0f)
        private set

    /**
     * Drives animation progress 0→1 manually. (Compose's Animatable cannot be
     * used from viewModelScope: it needs a MonotonicFrameClock that only exists
     * in composition-launched coroutines.)
     */
    private suspend fun runAnim(durationMs: Long = 300) {
        val start = SystemClock.uptimeMillis()
        animProgress = 0f
        while (true) {
            val t = (SystemClock.uptimeMillis() - start).toFloat() / durationMs
            if (t >= 1f) break
            animProgress = t
            kotlinx.coroutines.delay(16)
        }
        animProgress = 1f
    }

    var say by mutableStateOf<SayEvent?>(null)
        private set

    var menuBids by mutableStateOf<List<Game.Bid>>(emptyList())
        private set
    var selectedBid by mutableStateOf<Game.Bid?>(null)
    val cardsToDiscard = mutableStateListOf<Card>()

    var transientHint by mutableStateOf<((Context) -> String)?>(null)
    var showTricks by mutableStateOf(false)
    var tricks by mutableStateOf<List<AIInfo.Take>>(emptyList())
        private set
    var tricksNames by mutableStateOf<Map<Int, String>>(emptyMap())
        private set
    private var showPrikupHand: Int? = null

    var onShowScore: (() -> Unit)? = null

    private var started = false
    private var loopRunning = false

    // hosted multiplayer: the session (not this VM) drives the loop
    var hosted = false
        private set
    var scoresOverlay by mutableStateOf<ScoreSnap?>(null)
        private set
    private var session: HostGameSession? = null
    private val mpMutex = Mutex()

    /** Host side of a multiplayer game. Seat 0 is the local player. */
    fun startHosted(
        names: List<String>,
        seatKinds: List<SeatKind>,
        sendToSeat: (Int, GameMsg.State) -> Unit,
        initialCalc: Calculation? = null,
        rules: com.an0obIs.pref.model.GameRules? = null,
        limit: Int? = null
    ) {
        if (started) return
        started = true
        hosted = true
        game = Game.create()
        if (initialCalc != null) {
            // resume a saved pulka: seat its columns to match the room players
            game.calc = initialCalc.reordered(Calculation.seatOrder(names, initialCalc))
        } else {
            rules?.let { game.calc.rules = it.clone() }
            limit?.let { game.calc.limit = it }
        }
        for (i in names.indices.take(3))
            game.calc.scores[i].name = names[i]
        val s = HostGameSession(
            game = game,
            seats = seatKinds,
            sendToSeat = sendToSeat,
            onLocalTurn = {
                viewModelScope.launch {
                    buildMenu()
                    refresh()
                }
            }
        )
        session = s
        viewModelScope.launch {
            busy = true
            thinking = true
            withContext(Dispatchers.Default) {
                mpMutex.withLock {
                    try {
                        s.start()
                    } catch (e: Exception) {
                        android.util.Log.e("Pref", "hosted start error", e)
                    }
                }
            }
            thinking = false
            busy = false
            buildMenu()
            refresh()
        }
    }

    /** Save the running multiplayer standings as a regular pulka file. */
    fun saveScoreSheet(): Boolean = try {
        game.calc.save()
        true
    } catch (e: Exception) {
        android.util.Log.e("Pref", "score save failed", e)
        false
    }

    /** A remote player's action arrived over the relay. */
    fun onRemoteAct(seat: Int, act: GameMsg.Act) {
        val s = session ?: return
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                mpMutex.withLock {
                    try {
                        s.onRemoteAct(seat, act)
                    } catch (e: Exception) {
                        android.util.Log.e("Pref", "remote act error", e)
                    }
                }
            }
            buildMenu()
            refresh()
        }
    }

    fun start(app: PrefApp, ai1Name: String, ai2Name: String) {
        this.app = app
        if (!started) {
            game = app.game ?: Game.create(ai1Name, ai2Name).also { app.game = it }
            app.game = game
            started = true
            refresh()
        }
        // The original page always kicked the game loop on navigation (including
        // returning from the score sheet).
        gameNext()
    }

    private fun buildTableInfo(): TableInfo = TableInfo(
        phase = game.phase,
        names = game.calc.scores.map { it.name },
        dealer = game.calc.dealer,
        taken = game.deal.hands.map { it.taken },
        currentGameType = game.currentGameType,
        contractor = game.contractor,
        isVister = game.isVister.toMap(),
        curentBids = game.curentBids.toMap(),
        maxBid = game.maxBid,
        playerToTake = game.playerToTake,
        playerInTurn = game.playerInTurn,
        controller = game.turnController(),
        gameResult = if (game.phase == GamePhase.EndPlay) game.getGameResult() else null,
        showPrikupBtn1 = (game.phase == GamePhase.Playing || game.phase == GamePhase.EndTurn)
                && (game.currentGameType == GameType.Normal || game.currentGameType == GameType.Miser)
                && game.contractor == 1 && game.opening && showPrikupHand != 1,
        showPrikupBtn2 = (game.phase == GamePhase.Playing || game.phase == GamePhase.EndTurn)
                && (game.currentGameType == GameType.Normal || game.currentGameType == GameType.Miser)
                && game.contractor == 2 && game.opening && showPrikupHand != 2,
        showTricksBtn = game.phase == GamePhase.Playing || game.phase == GamePhase.EndTurn
    )

    // The engine keeps a finished trick in deal.inPlay until every player has
    // confirmed it; once the local player confirmed, keep it off the table so
    // it doesn't reappear while the remote players are still looking.
    private var trickCollected = false

    /** Recompute all published render state from the (quiescent) game. */
    private fun refresh() {
        showPrikupHand = null
        if (game.phase != GamePhase.EndTurn) trickCollected = false
        val f = TableLayout.computeField(game, cardsToDiscard.toList(), null)
        field = if (trickCollected) f.filter { !it.isInPlay } else f
        pinnedOverlays.clear()
        info = buildTableInfo()
        scoresOverlay = if (hosted && (game.phase == GamePhase.ScoreView || game.phase == GamePhase.Ended))
            RemoteViews.buildScoresFor(game, 0)
        else null
    }

    fun gameNext() {
        if (loopRunning) return
        val s = session
        if (hosted && s != null) {
            // hosted: the local action was already applied; the session runs
            // next() + bots + remote broadcasting
            viewModelScope.launch {
                loopRunning = true
                busy = true
                thinking = true
                transientHint = null
                withContext(Dispatchers.Default) {
                    mpMutex.withLock {
                        try {
                            s.onLocalActed()
                        } catch (e: Exception) {
                            android.util.Log.e("Pref", "hosted loop error (phase=${game.phase})", e)
                        }
                    }
                }
                thinking = false
                busy = false
                buildMenu()
                refresh()
                loopRunning = false
            }
            return
        }
        viewModelScope.launch {
            loopRunning = true
            busy = true
            thinking = true
            transientHint = null
            val error: Exception? = withContext(Dispatchers.Default) {
                game.onProgress = {
                    // Called from the game loop at safe points; compute on this
                    // thread while state is consistent, publish on main.
                    val f = TableLayout.computeField(game)
                    val i = buildTableInfo()
                    viewModelScope.launch {
                        field = f
                        pinnedOverlays.clear()
                        info = i
                    }
                }
                try {
                    game.next()
                    null
                } catch (e: Exception) {
                    // The original WP7 app swallowed engine/AI exceptions inside its
                    // BackgroundWorker; do the same but log them for diagnosis.
                    android.util.Log.e("Pref", "Game loop error (phase=${game.phase}, player=${game.playerInTurn})", e)
                    e
                }
            }
            thinking = false
            if (error != null) {
                val msg = "${error.javaClass.simpleName}: ${error.message}"
                transientHint = { msg }
            }
            processAnimations()
            busy = false
            buildMenu()
            refresh()
            if (game.phase == GamePhase.ScoreView) {
                game.scoreClose()
                game.saveLast()
                onShowScore?.invoke()
            }
            loopRunning = false
        }
    }

    val isGameEnded: Boolean
        get() = game.phase == GamePhase.Ended

    private suspend fun processAnimations() {
        while (true) {
            val a = if (game.animations.isNotEmpty()) game.animations.removeFirst() else break
            val card = a.card
            if (card != null) {
                val from = field.firstOrNull { it.hand == a.player && it.card?.id == card.id }
                val (fx, fy) = if (from != null) Pair(from.x, from.y) else TableLayout.hiddenStartCoords(a.player)
                val (tx, ty) = TableLayout.inPlayCoords(a.player)
                // hide the source card while it flies
                if (from != null)
                    field = field.filter { it !== from }
                cardAnim = CardAnim(card, fx, fy, tx, ty)
                runAnim()
                cardAnim = null
                pinnedOverlays.add(PlacedCard(card = card, hand = a.player, x = tx, y = ty, isInPlay = true))
            } else {
                // bid announcement: grows while flying from the bidder to center
                say = SayEvent(a.player, a.bid, a.text)
                runAnim(800)
                kotlinx.coroutines.delay(300)
                say = null
            }
        }
    }

    /** In hosted games the local player may only act on turns they control
     *  (their own, or the passer's when whisting an open game). */
    private val localTurnAllowed: Boolean
        get() = !hosted || game.turnController() == 0

    /** Port of Draw()'s menu construction. */
    private fun buildMenu() {
        if (!localTurnAllowed) {
            menuBids = emptyList()
            selectedBid = null
            return
        }
        when (game.phase) {
            GamePhase.Negotiations -> {
                val bids = game.getAllowedBids().filter { !it.pas }
                menuBids = bids
                selectedBid = bids.firstOrNull { !it.miser } ?: bids.firstOrNull()
            }
            GamePhase.GameChoose -> {
                menuBids = game.getAllowedBids()
                selectedBid = null
            }
            GamePhase.Discarding -> {
                menuBids = emptyList()
                selectedBid = null
                cardsToDiscard.clear()
            }
            else -> {
                menuBids = emptyList()
                selectedBid = null
            }
        }
    }

    // region interactions

    fun onCardTap(pc: PlacedCard) {
        if (busy || !localTurnAllowed) return
        val card = pc.card ?: return
        if (pc.isInPlay || pc.isPrikup) return
        if (game.phase == GamePhase.Playing) {
            if (game.playCard(card, true)) {
                transientHint = null
                viewModelScope.launch {
                    busy = true
                    val (tx, ty) = TableLayout.inPlayCoords(game.playerInTurn)
                    field = field.filter { it !== pc }
                    cardAnim = CardAnim(card, pc.x, pc.y, tx, ty)
                    runAnim()
                    cardAnim = null
                    pinnedOverlays.add(PlacedCard(card = card, hand = game.playerInTurn, x = tx, y = ty, isInPlay = true))
                    busy = false
                    game.playCard(card)
                    gameNext()
                }
            } else {
                if (pc.hand != game.playerInTurn) {
                    val aiName = game.calc.scores[game.playerInTurn].name
                    val mine = game.playerInTurn == 0
                    transientHint = { ctx ->
                        ctx.getString(R.string.game_wrong_hand) +
                                if (mine) ctx.getString(R.string.game_wrong_hand_yours)
                                else ctx.getString(R.string.game_wrong_hand_ai, aiName)
                    }
                } else {
                    val moves = game.getAllowedMoves()
                    val color = moves.first().coatColor
                    val cardText = card.toString()
                    transientHint = { ctx ->
                        ctx.getString(R.string.game_must_play, cardText, GameTexts.trumpName(ctx, color))
                    }
                }
            }
        } else if (game.phase == GamePhase.Discarding) {
            toggleDiscard(card)
        }
    }

    private fun toggleDiscard(card: Card) {
        val existing = cardsToDiscard.firstOrNull { it.id == card.id }
        if (existing != null) {
            cardsToDiscard.remove(existing)
        } else {
            if (cardsToDiscard.size >= 2) return
            cardsToDiscard.add(card)
        }
        field = TableLayout.computeField(game, cardsToDiscard.toList(), null)
    }

    fun onCanvasTap() {
        if (busy) return
        if (showTricks) {
            showTricks = false
            return
        }
        if (!localTurnAllowed) return
        when (game.phase) {
            GamePhase.PrikupOpened -> {
                game.prikupClose()
                gameNext()
            }
            GamePhase.EndTurn -> hideDeal()
            GamePhase.EndPlay -> {
                game.endConfirm()
                gameNext()
            }
            GamePhase.ScoreView -> {
                // hosted games treat the score view as a confirm turn
                if (hosted) {
                    game.scoreClose()
                    gameNext()
                }
            }
            else -> {}
        }
    }

    /** Trick collection animation (port of HideDeal). */
    private fun hideDeal() {
        val take = field.filter { it.isInPlay && it.card != null }
        val (tx, ty) = TableLayout.outOfPlayCoords(game.playerToTake)
        viewModelScope.launch {
            busy = true
            field = field.filter { !it.isInPlay }
            trickAnim = TrickAnim(take, tx, ty)
            runAnim()
            trickAnim = null
            busy = false
            trickCollected = true
            game.turnClose()
            gameNext()
        }
    }

    fun onChoiceSelected(bid: Game.Bid) {
        if (busy) return
        if (game.phase == GamePhase.Negotiations || game.phase == GamePhase.GameChoose) {
            selectedBid = bid
        }
    }

    /** Port of btnChoice1_Tap. */
    fun onButton1() {
        if (busy || !localTurnAllowed) return
        when (game.phase) {
            GamePhase.Negotiations -> {
                val bid = selectedBid ?: return
                game.makeBid(bid)
                gameNext()
            }
            GamePhase.VistNegotiations -> {
                game.setVist(true)
                gameNext()
            }
            GamePhase.GameChoose -> {
                val bid = selectedBid ?: return
                game.setContract(bid)
                gameNext()
            }
            GamePhase.OpeningChoose -> {
                game.setOpeningChoice(true)
                gameNext()
            }
            else -> {}
        }
        menuBids = emptyList()
    }

    /** Port of btnChoice2_Tap. */
    fun onButton2() {
        if (busy || !localTurnAllowed) return
        when (game.phase) {
            GamePhase.Negotiations -> {
                game.makeBid(Game.Bid().also { it.pas = true })
                gameNext()
            }
            GamePhase.VistNegotiations -> {
                game.setVist(false)
                gameNext()
            }
            GamePhase.GameChoose -> {
                val bid = selectedBid ?: return
                game.setContract(bid)
                gameNext()
            }
            GamePhase.OpeningChoose -> {
                game.setOpeningChoice(false)
                gameNext()
            }
            GamePhase.Discarding -> {
                doDiscard()
            }
            else -> {}
        }
        menuBids = emptyList()
    }

    private fun doDiscard() {
        if (cardsToDiscard.size != 2) return
        game.discardCard(cardsToDiscard[0])
        game.discardCard(cardsToDiscard[1])
        cardsToDiscard.clear()
        gameNext()
    }

    /** Port of btnHint_Tap — runs the AI on behalf of the player (may take a moment). */
    fun requestAdvice() {
        if (busy || hosted || !localTurnAllowed) return
        viewModelScope.launch {
            busy = true
            thinking = true
            val hintFn: ((Context) -> String)? = withContext(Dispatchers.Default) {
                try {
                    val ai = game.aIs[game.playerInTurn]!!
                    when (game.phase) {
                        GamePhase.Playing -> {
                            val card = AI.playCard(ai, game)!!
                            val text = card.toString();
                            { ctx: Context -> ctx.getString(R.string.game_advise_play, text) }
                        }
                        GamePhase.GameChoose -> {
                            val contract = AI.getContract(ai, game.getAllowedBids());
                            { ctx: Context -> ctx.getString(R.string.game_advise_contract, GameTexts.bidTitle(ctx, contract)) }
                        }
                        GamePhase.Negotiations -> {
                            val bid = AI.getBid(ai, game.getAllowedBids());
                            { ctx: Context -> ctx.getString(R.string.game_advise_bid, GameTexts.bidTitle(ctx, bid)) }
                        }
                        GamePhase.Discarding -> {
                            val discard = AI.getDiscard(ai, game)
                            val t1 = discard.first.toString()
                            val t2 = discard.second.toString();
                            { ctx: Context -> ctx.getString(R.string.game_advise_discard, t1, t2) }
                        }
                        else -> null
                    }
                } catch (e: Exception) {
                    null
                }
            }
            thinking = false
            busy = false
            if (hintFn != null)
                transientHint = hintFn
        }
    }

    /** While the deal is still played, earlier tricks show as card backs. */
    var hidePastTricks by mutableStateOf(false)
        private set

    fun openTricks() {
        if (busy) return
        val ai = game.aIs[game.playerInTurn] ?: return
        tricks = ai.outOfPlay.toList()
        hidePastTricks = game.deal.totalTaken < 10
        tricksNames = mapOf(
            -1 to game.calc.scores[game.getPrevPlayer()].name,
            0 to game.calc.scores[game.playerInTurn].name,
            1 to game.calc.scores[game.getNextPlayer()].name
        )
        showTricks = true
    }

    /** Port of bntShowWithPrikup: reveal contractor's hand together with possible talon cards. */
    fun showHandWithPrikup(hand: Int) {
        if (busy) return
        showPrikupHand = hand
        field = TableLayout.computeField(game, cardsToDiscard.toList(), hand)
        info = buildTableInfo()
    }

    // endregion
}
