package com.an0obIs.pref.ui.mp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.an0obIs.pref.R
import com.an0obIs.pref.model.Card
import com.an0obIs.pref.model.Game
import com.an0obIs.pref.mp.GameMsg
import com.an0obIs.pref.mp.gameJson
import com.an0obIs.pref.ui.game.CardImages
import com.an0obIs.pref.ui.game.GameTexts
import com.an0obIs.pref.ui.game.TableLayout
import com.an0obIs.pref.ui.game.buildTableStrings

class GuestGameViewModel : ViewModel() {
    var state by mutableStateOf<GameMsg.State?>(null)
        private set
    var selectedBid by mutableStateOf<Game.Bid?>(null)
    val discardSel = mutableStateListOf<Card>()

    // one pulka file per guest session, overwritten on every save
    private val calcCreated = System.currentTimeMillis()

    /** Auto-confirm everything until the deal's score sheet appears. */
    var autoConfirmDeal by mutableStateOf(false)
    var showLayout by mutableStateOf(false)
    var showTakes by mutableStateOf(false)
    var scorePeek by mutableStateOf(false)
    private var autoSaved = false

    fun onState(s: GameMsg.State) {
        val prevKind = state?.ask?.kind
        state = s
        if (s.ask?.kind != prevKind) selectedBid = null
        if (s.ask?.kind != "discard") discardSel.clear()
        if (s.layout == null) showLayout = false
        if (s.takes == null) showTakes = false
        if (s.scores != null) {
            autoConfirmDeal = false // the score sheet waits for a real tap
            scorePeek = false
            if (s.ended && !autoSaved) {
                autoSaved = true
                saveScoreSheet(s.scores)
            }
        }
    }

    // ---- snapshot-diff animations: the guest has no engine, so card flights,
    // trick collection and say bubbles are reconstructed from consecutive
    // host states ------------------------------------------------------------

    /** The field being drawn while a transition animates; null = state.field. */
    var dispField by mutableStateOf<List<com.an0obIs.pref.ui.game.PlacedCard>?>(null)
        private set
    var cardAnim by mutableStateOf<com.an0obIs.pref.ui.game.CardAnim?>(null)
        private set
    var trickAnim by mutableStateOf<com.an0obIs.pref.ui.game.TrickAnim?>(null)
        private set
    var say by mutableStateOf<com.an0obIs.pref.ui.game.SayEvent?>(null)
        private set
    var animProgress by mutableStateOf(0f)
        private set

    /** Tricks already shown collected on this table (visual, not engine). */
    private var seenTricks = 0

    private suspend fun runAnim(durationMs: Long = 360) {
        val start = android.os.SystemClock.uptimeMillis()
        animProgress = 0f
        while (true) {
            val t = (android.os.SystemClock.uptimeMillis() - start).toFloat() / durationMs
            if (t >= 1f) break
            animProgress = t
            kotlinx.coroutines.delay(16)
        }
        animProgress = 1f
    }

    /** Applies a new state, first animating the difference from the last one. */
    suspend fun applyState(s: GameMsg.State, ctx: android.content.Context) {
        val prev = state
        if (prev != null) {
            try {
                animateTransition(prev, s, ctx)
            } catch (e: Exception) {
                android.util.Log.w("Pref", "guest anim skipped", e)
            } finally {
                cardAnim = null
                trickAnim = null
                say = null
            }
        }
        onState(s)
        dispField = null
    }

    private suspend fun sayOut(e: com.an0obIs.pref.ui.game.SayEvent) {
        say = e
        runAnim(960)
        kotlinx.coroutines.delay(300)
        say = null
    }

    private suspend fun animateTransition(prev: GameMsg.State, s: GameMsg.State, ctx: android.content.Context) {
        val oldTricks = prev.info.taken.sum()
        val newTricks = s.info.taken.sum()
        // a fresh deal (or a whole game) replaced the table: just snap
        if (newTricks < oldTricks ||
            (s.info.phase == com.an0obIs.pref.model.GamePhase.Negotiations &&
                    prev.info.phase != com.an0obIs.pref.model.GamePhase.Negotiations)
        ) {
            seenTricks = 0
            return
        }
        if (showLayout || s.ended) return

        // bid and whist announcements; Bid has no structural equals, so
        // compare by value or every snapshot would replay old announcements
        fun sameBid(a: Game.Bid?, b: Game.Bid?): Boolean =
            (a == null && b == null) || (a != null && b != null &&
                    a.pas == b.pas && a.miser == b.miser &&
                    a.contract == b.contract && a.trump == b.trump)
        for (p in 0..2) {
            val nb = s.info.curentBids[p]
            if (nb != null && !sameBid(nb, prev.info.curentBids[p]))
                sayOut(com.an0obIs.pref.ui.game.SayEvent(p, nb, null))
        }
        for (p in 0..2) {
            val nv = s.info.isVister[p]
            if (nv != null && nv != prev.info.isVister[p])
                sayOut(
                    com.an0obIs.pref.ui.game.SayEvent(
                        p, null,
                        ctx.getString(if (nv) R.string.game_say_whist else R.string.game_say_pass)
                    )
                )
        }

        var field = prev.field
        fun lying() = field.filter { it.isInPlay && it.card != null }

        suspend fun flyCard(hand: Int, card: Card, tx: Double, ty: Double) {
            val from = field.firstOrNull { !it.isInPlay && it.card?.id == card.id }
                ?: field.firstOrNull { !it.isInPlay && it.card == null && it.hand == hand }
            val (fx, fy) = if (from != null) Pair(from.x, from.y)
            else TableLayout.hiddenStartCoords(hand)
            if (from != null) field = field - from
            dispField = field
            cardAnim = com.an0obIs.pref.ui.game.CardAnim(card, fx, fy, tx, ty)
            runAnim()
            cardAnim = null
            field = field + com.an0obIs.pref.ui.game.PlacedCard(
                card = card, hand = hand, x = tx, y = ty, isInPlay = true
            )
            dispField = field
        }

        suspend fun collect(taker: Int) {
            val cards = lying()
            if (cards.isEmpty()) return
            val (tx, ty) = TableLayout.outOfPlayCoords(taker)
            field = field.filter { !it.isInPlay }
            dispField = field
            trickAnim = com.an0obIs.pref.ui.game.TrickAnim(cards, tx, ty)
            runAnim()
            trickAnim = null
        }

        // -1/0/1 seat codes of TakeSnap -> viewer-relative hands 1/0/2
        fun handOf(code: Int) = when (code) {
            -1 -> 1
            1 -> 2
            else -> 0
        }

        fun cardOf(t: com.an0obIs.pref.mp.TakeSnap, hand: Int): Card? = when (hand) {
            1 -> t.prev
            2 -> t.next
            else -> t.my
        }

        if (seenTricks > newTricks) seenTricks = newTricks // safety after resync

        // tricks completed since the last state: finish their plays, collect
        val takes = s.takes
        if (newTricks > seenTricks && takes != null && takes.size >= newTricks) {
            for (t in seenTricks until newTricks) {
                val take = takes[t]
                val lead = handOf(take.first)
                for (i in 0..2) {
                    val h = (lead + i) % 3
                    val card = cardOf(take, h) ?: continue
                    if (lying().any { it.card!!.id == card.id }) continue
                    val (tx, ty) = TableLayout.inPlayCoords(h)
                    flyCard(h, card, tx, ty)
                }
                collect(handOf(take.taker))
            }
            seenTricks = newTricks
        }

        val targetLying = s.field.filter { it.isInPlay && it.card != null }

        // this viewer confirmed the finished trick: the host hides it from
        // their next snapshot before the engine counts it — collect it now
        if (targetLying.isEmpty() && lying().isNotEmpty() &&
            newTricks == oldTricks && seenTricks == newTricks &&
            s.info.phase == com.an0obIs.pref.model.GamePhase.EndTurn
        ) {
            collect(s.info.playerToTake)
            seenTricks = newTricks + 1
        }

        // cards newly played into the current trick
        val added = targetLying
            .filter { n -> lying().none { it.card!!.id == n.card!!.id } }
            .sortedBy { (it.hand - prev.info.controller + 3) % 3 }
        for (pc in added) flyCard(pc.hand, pc.card!!, pc.x, pc.y)
    }

    /** Save the host's score snapshot as a regular pulka file (guest view: self = player 0). */
    fun saveScoreSheet(snap: com.an0obIs.pref.mp.ScoreSnap): Boolean = try {
        val n = snap.names.size
        val c = com.an0obIs.pref.model.Calculation(n, snap.limit)
        c.created = calcCreated
        c.dealer = snap.dealer
        for (i in 0 until n) {
            c.scores[i].name = snap.names[i]
            c.scores[i].pulya = snap.pulya[i]
            c.scores[i].gora = snap.gora[i]
            for (j in 0 until n)
                if (i != j) c.scores[i].visty[j] = snap.visty[i][j]
        }
        c.save()
        true
    } catch (e: Exception) {
        android.util.Log.e("Pref", "guest score save failed", e)
        false
    }
}

/** Thin client: renders the host's per-viewer snapshots and answers Asks. */
@Composable
fun MpGuestScreen(lobbyVm: LobbyViewModel) {
    // a fresh ViewModel per started game (the previous match's state must not leak)
    val vm: GuestGameViewModel = viewModel(key = "mp-guest-${lobbyVm.gameGeneration}")
    val ctx = LocalContext.current
    val images = remember { CardImages(ctx.applicationContext) }

    fun act(a: GameMsg.Act) {
        lobbyVm.sendGameToHost(gameJson.encodeToJsonElement(GameMsg.serializer(), a))
    }

    LaunchedEffect(Unit) {
        lobbyVm.hostStates.collect { el ->
            try {
                val msg = gameJson.decodeFromJsonElement(GameMsg.serializer(), el)
                if (msg is GameMsg.State) {
                    val wasAuto = vm.autoConfirmDeal
                    vm.applyState(msg, ctx)
                    // auto-confirm switched itself off at the score sheet:
                    // let the host show us in the waiting list again
                    if (wasAuto && !vm.autoConfirmDeal) act(GameMsg.Act(autoMode = false))
                    // player-side auto-confirm: everything except the score sheet
                    if (vm.autoConfirmDeal && msg.scores == null &&
                        msg.yourTurn && msg.ask?.kind == "confirm"
                    ) {
                        act(GameMsg.Act(confirm = true))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("PrefNet", "bad game payload", e)
            }
        }
    }

    val st = vm.state
    if (st == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.mp_waiting_host))
        }
        return
    }
    val ask = if (st.yourTurn) st.ask else null
    var offerStep by remember { mutableStateOf(0) }
    var offerN by remember { mutableStateOf(0) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (ask?.kind == "confirm") act(GameMsg.Act(confirm = true))
            }
    ) {
        val kx = maxWidth / TableLayout.W.toFloat()
        val ky = maxHeight / TableLayout.H.toFloat()
        fun ux(x: Double): Dp = kx * x.toFloat()
        fun uy(y: Double): Dp = ky * y.toFloat()
        val cardSize = ux(TableLayout.S0)

        Image(
            bitmap = images.background(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        val strings = buildTableStrings(ctx, st.info, mp = true)
        val hintText = when {
            st.offerDeclined != null -> stringResource(R.string.offer_declined_fmt, st.offerDeclined!!)
            st.badMove -> stringResource(R.string.mp_bad_move)
            st.ended -> stringResource(R.string.mp_game_over)
            else -> strings.hint
        }

        val shownField =
            if (vm.showLayout) st.layout ?: st.field
            else vm.dispField ?: st.field
        for (pc in shownField) {
            val selected = pc.card != null && vm.discardSel.any { it.id == pc.card!!.id }
            Image(
                bitmap = images.get(pc.card),
                filterQuality = FilterQuality.High,
                contentDescription = pc.card?.toString(),
                modifier = Modifier
                    .offset(x = ux(pc.x), y = uy(pc.y) - (if (selected) 14.dp else 0.dp))
                    .size(cardSize)
                    .clickable(
                        interactionSource = remember(pc) { MutableInteractionSource() },
                        indication = null
                    ) {
                        // confirm asks advance on a tap anywhere, including on cards
                        if (ask?.kind == "confirm") {
                            act(GameMsg.Act(confirm = true))
                            return@clickable
                        }
                        val card = pc.card ?: return@clickable
                        if (pc.isInPlay || pc.isPrikup) return@clickable
                        when (ask?.kind) {
                            // own cards, or the passer's when whisting an open
                            // game (the host lists them in ask.allowed)
                            "play" -> if (pc.hand == 0 ||
                                ask.allowed?.any { it.id == card.id } == true
                            ) act(GameMsg.Act(play = card))
                            "discard" -> if (pc.hand == 0) {
                                val existing = vm.discardSel.firstOrNull { it.id == card.id }
                                if (existing != null) vm.discardSel.remove(existing)
                                else if (vm.discardSel.size < 2) vm.discardSel.add(card)
                            }
                            else -> {}
                        }
                    }
            )
        }

        // flying card
        vm.cardAnim?.let { anim ->
            val t = vm.animProgress
            val x = anim.fromX + (anim.toX - anim.fromX) * t
            val y = anim.fromY + (anim.toY - anim.fromY) * t
            Image(
                bitmap = images.get(anim.card),
                filterQuality = FilterQuality.High,
                contentDescription = null,
                modifier = Modifier.offset(x = ux(x), y = uy(y)).size(cardSize)
            )
        }

        // trick collection (cards fly to the taker and shrink)
        vm.trickAnim?.let { anim ->
            val t = vm.animProgress
            val s = cardSize * (1f - t)
            for (pc in anim.cards) {
                val x = pc.x + (anim.toX - pc.x) * t
                val y = pc.y + (anim.toY - pc.y) * t
                Image(
                    bitmap = images.get(pc.card),
                    filterQuality = FilterQuality.High,
                    contentDescription = null,
                    modifier = Modifier.offset(x = ux(x), y = uy(y)).size(s)
                )
            }
        }

        // say bubble: grows while flying from the sayer to the table center
        vm.say?.let { say ->
            val t = vm.animProgress
            val move = 1f - (1f - t) * (1f - t)
            val (sx, sy) = when (say.player) {
                1 -> 80.0 to 95.0
                2 -> 400.0 to 95.0
                else -> 240.0 to 600.0
            }
            val cx = sx + (240.0 - sx) * move
            val cy = sy + (300.0 - sy) * move
            Box(
                modifier = Modifier.offset(x = ux(cx - 150.0), y = uy(cy)).width(ux(300.0)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = GameTexts.sayText(ctx, say),
                    color = Color(0xFFFFB100),
                    fontWeight = FontWeight.Bold,
                    fontSize = (15 + 19 * t).sp,
                    maxLines = 1
                )
            }
        }

        Text(strings.p1, color = Color.White, fontSize = 13.sp,
            modifier = Modifier.offset(x = ux(20.0), y = uy(10.0)).width(ux(196.0)))
        Text(strings.p2, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Right,
            modifier = Modifier.offset(x = ux(266.0), y = uy(10.0)).width(ux(196.0)))
        Text(strings.p0, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Right,
            modifier = Modifier.offset(x = ux(177.0), y = uy(664.0)).width(ux(285.0)))
        Text(strings.gameInfo, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Right,
            modifier = Modifier.offset(x = ux(177.0), y = uy(694.0)).width(ux(285.0)))

        val sitOut = st.info.sitOutName
        if (sitOut != null) {
            com.an0obIs.pref.ui.game.SitOutBadge(sitOut, ::ux, ::uy)
        } else if (st.info.names[st.info.dealer].isNotEmpty()) {
            com.an0obIs.pref.ui.game.DealerBadge(st.info.dealer, ::ux, ::uy)
        }

        if (hintText.isNotEmpty()) {
            Text(
                text = hintText, color = Color.White, fontSize = 13.sp,
                modifier = Modifier
                    .offset(x = ux(16.0), y = uy(545.0)).width(ux(150.0))
                    .background(Color(0x66000000), RoundedCornerShape(8.dp)).padding(6.dp)
            )
        }

        st.scores?.let { snap ->
            com.an0obIs.pref.ui.game.ScoreOverlay(
                snap = snap,
                modifier = Modifier.fillMaxSize(),
                onSave = { vm.saveScoreSheet(snap) },
                onTap = { if (ask?.kind == "confirm") act(GameMsg.Act(confirm = true)) }
            )
        }

        if (strings.result.isNotEmpty()) {
            Text(
                text = strings.result, color = Color.White, fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(x = ux(63.0), y = uy(374.0)).width(ux(353.0))
                    .background(Color(0x88000000), RoundedCornerShape(8.dp)).padding(8.dp)
            )
        }

        // bid / contract menu
        if (ask != null && (ask.kind == "bid" || ask.kind == "contract") && !ask.bids.isNullOrEmpty()) {
            val choices = ask.bids.filter { !it.pas }
            LazyColumn(
                modifier = Modifier
                    .offset(x = ux(139.0), y = uy(37.0))
                    .width(ux(203.0)).height(uy(286.0))
                    .background(Color(0x66123B16))
                    .border(1.dp, Color(0x662E7D32))
            ) {
                items(choices.reversed()) { bid ->
                    Text(
                        text = GameTexts.bidTitle(ctx, bid),
                        color = if (vm.selectedBid === bid) Color(0xFFFFB100) else Color.White,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.selectedBid = bid }
                            .padding(10.dp)
                    )
                }
            }
        }

        // ask buttons
        if (ask != null) {
            val btn1: Pair<String, () -> Unit>? = when (ask.kind) {
                "bid" -> vm.selectedBid?.let {
                    GameTexts.bidTitle(ctx, it) to { act(GameMsg.Act(bid = it)) }
                }
                "vist" -> stringResource(R.string.game_btn_whist) to { act(GameMsg.Act(vist = true)) }
                "opening" -> stringResource(R.string.game_btn_open) to { act(GameMsg.Act(opening = true)) }
                else -> null
            }
            val btn2: Triple<String, Boolean, () -> Unit>? = when (ask.kind) {
                "bid" -> Triple(stringResource(R.string.game_btn_pass), true) {
                    act(GameMsg.Act(bid = ask.bids?.firstOrNull { it.pas } ?: Game.Bid().also { it.pas = true }))
                }
                "vist" -> Triple(stringResource(R.string.game_btn_pass), true) { act(GameMsg.Act(vist = false)) }
                "opening" -> Triple(stringResource(R.string.game_btn_closed), true) { act(GameMsg.Act(opening = false)) }
                "contract" -> Triple(
                    vm.selectedBid?.let { GameTexts.bidTitle(ctx, it) }
                        ?: stringResource(R.string.game_btn_not_selected),
                    vm.selectedBid != null
                ) { vm.selectedBid?.let { act(GameMsg.Act(contract = it)) } }
                "discard" -> Triple(stringResource(R.string.game_btn_discard), vm.discardSel.size == 2) {
                    act(GameMsg.Act(discard = vm.discardSel.toList()))
                }
                else -> null
            }
            if (btn1 != null) {
                Button(
                    onClick = btn1.second,
                    modifier = Modifier.offset(x = ux(152.0), y = uy(330.0)).width(ux(176.0))
                ) { Text(btn1.first, maxLines = 1) }
            }
            if (btn2 != null) {
                Button(
                    onClick = btn2.third,
                    enabled = btn2.second,
                    modifier = Modifier.offset(x = ux(152.0), y = uy(385.0)).width(ux(176.0))
                ) { Text(btn2.first, maxLines = 1) }
            }
        }

        // bottom-left action buttons (parity with the host table); the offer
        // button sits on its own line right above the row
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)) {
            if (com.an0obIs.pref.mp.Agreements.canOffer(st.info)) {
                OutlinedButton(onClick = { offerStep = 1 }) {
                    Text(stringResource(R.string.game_btn_offer), fontSize = 12.sp, color = Color.White)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (st.takes != null && st.info.showTricksBtn) {
                    OutlinedButton(onClick = { vm.showTakes = true }) {
                        Text(stringResource(R.string.game_btn_tricks), fontSize = 12.sp, color = Color.White)
                    }
                }
                if (st.standings != null && st.scores == null) {
                    OutlinedButton(onClick = { vm.scorePeek = true }) {
                        Text(stringResource(R.string.game_btn_score), fontSize = 12.sp, color = Color.White)
                    }
                }
                OutlinedButton(onClick = {
                    vm.autoConfirmDeal = !vm.autoConfirmDeal
                    act(GameMsg.Act(autoMode = vm.autoConfirmDeal))
                }) {
                    Text(
                        stringResource(R.string.game_btn_auto),
                        fontSize = 12.sp,
                        lineHeight = 13.sp,
                        textAlign = TextAlign.Center,
                        color = if (vm.autoConfirmDeal) Color(0xFFFFB100) else Color.White
                    )
                }
            }
        }

        // agreement offer menus + pending dialog (shared with the host table)
        com.an0obIs.pref.ui.game.AgreementUi(
            info = st.info,
            offerStep = offerStep,
            offerN = offerN,
            onStep = { step, n -> offerStep = step; offerN = n },
            onOffer = { taken -> offerStep = 0; act(GameMsg.Act(offer = taken)) },
            onRestMine = { act(GameMsg.Act(restMine = true)) },
            pending = st.offer,
            onRespond = { agree -> act(GameMsg.Act(agree = agree)) },
            ux = ::ux, uy = ::uy
        )

        // layout-and-discard toggle sits where the host has it (top center)
        if (st.layout != null) {
            OutlinedButton(
                onClick = { vm.showLayout = !vm.showLayout },
                modifier = Modifier.offset(x = ux(192.0), y = uy(30.0))
            ) {
                Text(
                    stringResource(
                        if (vm.showLayout) R.string.game_btn_hide_prikup
                        else R.string.game_btn_show_prikup
                    ),
                    fontSize = 11.sp, color = Color.White
                )
            }
        }

        // on-demand standings peek
        if (vm.scorePeek && st.scores == null) {
            st.standings?.let { snap ->
                com.an0obIs.pref.ui.game.ScoreOverlay(
                    snap = snap,
                    modifier = Modifier.fillMaxSize(),
                    onTap = { vm.scorePeek = false }
                )
            }
        }

        // past tricks popup (same rules as the host: earlier tricks face-down
        // until the deal's play is over)
        if (vm.showTakes && st.takes != null) {
            val takes = st.takes
            val allFaceUp = st.info.phase == com.an0obIs.pref.model.GamePhase.EndPlay
            val names = mapOf(-1 to st.info.names[1], 0 to st.info.names[0], 1 to st.info.names[2])
            Column(
                modifier = Modifier
                    .offset(x = ux(24.0), y = uy(18.0))
                    .width(ux(432.0)).height(uy(500.0))
                    .background(Color(0xFF009B00), RoundedCornerShape(6.dp))
                    .border(1.dp, Color.White, RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.game_trick_led), color = Color.White,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Box(modifier = Modifier.weight(1.6f))
                    Text(stringResource(R.string.game_trick_took), color = Color.White,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(
                            rememberScrollState())
                ) {
                    for ((idx, take) in takes.withIndex()) {
                        val faceDown = !allFaceUp && idx < takes.size - 1
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(names[take.first] ?: "", color = Color.White, fontSize = 12.sp,
                                modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            Row(
                                modifier = Modifier.weight(1.6f),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                take.prikup?.let {
                                    Image(bitmap = images.get(if (faceDown) null else it),
                                        filterQuality = FilterQuality.High, contentDescription = null,
                                        modifier = Modifier.size(34.dp))
                                }
                                Image(bitmap = images.get(if (faceDown) null else take.next),
                                    filterQuality = FilterQuality.High, contentDescription = null,
                                    modifier = Modifier.size(34.dp))
                                Image(bitmap = images.get(if (faceDown) null else take.prev),
                                    filterQuality = FilterQuality.High, contentDescription = null,
                                    modifier = Modifier.size(34.dp))
                                Image(bitmap = images.get(if (faceDown) null else take.my),
                                    filterQuality = FilterQuality.High, contentDescription = null,
                                    modifier = Modifier.size(34.dp))
                            }
                            Text(names[take.taker] ?: "", color = Color.White, fontSize = 12.sp,
                                modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        }
                    }
                }
                Button(
                    onClick = { vm.showTakes = false },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text(stringResource(R.string.game_btn_close)) }
            }
        }

        if (st.ended) {
            OutlinedButton(
                onClick = { lobbyVm.leave() },
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
            ) { Text(stringResource(R.string.mp_leave), color = Color.White) }
        }
    }
}
