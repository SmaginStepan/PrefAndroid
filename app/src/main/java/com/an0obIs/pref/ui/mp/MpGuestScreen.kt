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
                    vm.onState(msg)
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
            st.badMove -> stringResource(R.string.mp_bad_move)
            st.ended -> stringResource(R.string.mp_game_over)
            else -> strings.hint
        }

        val shownField = if (vm.showLayout) st.layout ?: st.field else st.field
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

        // bottom-left action buttons (parity with the host table)
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
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
            OutlinedButton(onClick = { vm.autoConfirmDeal = !vm.autoConfirmDeal }) {
                Text(
                    stringResource(R.string.game_btn_auto),
                    fontSize = 12.sp,
                    color = if (vm.autoConfirmDeal) Color(0xFFFFB100) else Color.White
                )
            }
            if (com.an0obIs.pref.mp.Agreements.canOffer(st.info)) {
                OutlinedButton(onClick = { offerStep = 1 }) {
                    Text(stringResource(R.string.game_btn_offer), fontSize = 12.sp, color = Color.White)
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
