package com.an0obIs.pref.ui.game

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.lifecycle.viewmodel.compose.viewModel
import com.an0obIs.pref.PrefApp
import com.an0obIs.pref.R
import com.an0obIs.pref.model.Card
import com.an0obIs.pref.model.GamePhase
import com.an0obIs.pref.model.GameType

/** Loads and caches card sprites (one soft-upscaled set for all devices). */
class CardImages(private val ctx: Context) {
    private val cache = mutableMapOf<String, ImageBitmap>()

    fun get(card: Card?): ImageBitmap {
        val cid = if (card == null) "0" else "${card.value}${"scdh"[card.coatColor]}"
        return cache.getOrPut(cid) {
            ctx.assets.open("cards/$cid.png").use {
                BitmapFactory.decodeStream(it).asImageBitmap()
            }
        }
    }

    fun background(): ImageBitmap = cache.getOrPut("greencloth") {
        ctx.assets.open("cards/greencloth.png").use {
            BitmapFactory.decodeStream(it).asImageBitmap()
        }
    }
}

internal data class TableStrings(
    val p0: String = "",
    val p1: String = "",
    val p2: String = "",
    val gameInfo: String = "",
    val hint: String = "",
    val result: String = ""
)

/** Port of DrawField's text section. Shared with the multiplayer guest screen. */
internal fun buildTableStrings(ctx: Context, info: TableInfo, mp: Boolean = false): TableStrings {
    val base = buildTableStringsInner(ctx, info)
    // In multiplayer, action hints belong only to the player who controls the
    // turn; everyone else sees whose move the table is waiting for.
    if (mp && info.controller != 0 && info.phase != GamePhase.Ended) {
        return base.copy(hint = ctx.getString(R.string.mp_waiting_for, info.names[info.controller]))
    }
    return base
}

private fun buildTableStringsInner(ctx: Context, info: TableInfo): TableStrings {
    var p0 = GameTexts.playerInfo(ctx, info, 0)
    var p1 = GameTexts.playerInfo(ctx, info, 1)
    var p2 = GameTexts.playerInfo(ctx, info, 2)
    var gameInfo = ""
    var hint = ""
    var result = ""

    fun writeGameInfo() {
        p1 += ":${info.taken[1]}"
        p2 += ":${info.taken[2]}"
        p0 += ":${info.taken[0]}"
        gameInfo = if (info.currentGameType != GameType.Raspasy)
            ctx.getString(R.string.game_playing_fmt, info.maxBid?.let { GameTexts.bidTitle(ctx, it) } ?: "")
        else
            ctx.getString(R.string.game_playing_raspasy)
    }

    when (info.phase) {
        GamePhase.Discarding -> {
            hint = ctx.getString(R.string.game_hint_discard)
        }
        GamePhase.EndTurn -> {
            hint = if (info.playerToTake == 0)
                ctx.getString(R.string.game_hint_you_take)
            else
                ctx.getString(R.string.game_hint_takes, info.names[info.playerToTake])
            writeGameInfo()
        }
        GamePhase.Playing -> {
            hint = if (info.playerInTurn != 0)
                ctx.getString(R.string.game_hint_move_ai, info.names[info.playerInTurn])
            else
                ctx.getString(R.string.game_hint_your_move)
            writeGameInfo()
        }
        GamePhase.PrikupOpened -> {
            hint = ctx.getString(R.string.game_hint_prikup)
        }
        GamePhase.Negotiations -> {
            info.curentBids[1]?.let { p1 += ":" + GameTexts.bidTitle(ctx, it) }
            info.curentBids[2]?.let { p2 += ":" + GameTexts.bidTitle(ctx, it) }
            info.curentBids[0]?.let { p0 += ":" + GameTexts.bidTitle(ctx, it) }
            hint = ctx.getString(R.string.game_hint_bid)
        }
        GamePhase.VistNegotiations -> {
            gameInfo = ctx.getString(R.string.game_playing_fmt, info.maxBid?.let { GameTexts.bidTitle(ctx, it) } ?: "")
            info.isVister[1]?.let { p1 += ":" + ctx.getString(if (it) R.string.game_vist_say else R.string.game_pass_say) }
            info.isVister[2]?.let { p2 += ":" + ctx.getString(if (it) R.string.game_vist_say else R.string.game_pass_say) }
            info.isVister[0]?.let { p0 += ":" + ctx.getString(if (it) R.string.game_vist_say else R.string.game_pass_say) }
            if (info.contractor == 1)
                p1 += ":" + (info.maxBid?.let { GameTexts.bidTitle(ctx, it) } ?: "")
            else if (info.contractor == 2)
                p2 += ":" + (info.maxBid?.let { GameTexts.bidTitle(ctx, it) } ?: "")
            hint = ctx.getString(R.string.game_hint_vist)
        }
        GamePhase.GameChoose -> {
            p0 += ":?"
            hint = ctx.getString(R.string.game_hint_choose)
        }
        GamePhase.OpeningChoose -> {
            hint = ctx.getString(R.string.game_hint_opening)
        }
        GamePhase.EndPlay -> {
            writeGameInfo()
            info.gameResult?.let { result = GameTexts.resultText(ctx, it, info.names) }
            hint = ctx.getString(R.string.game_hint_end)
        }
        GamePhase.ScoreView -> {
            hint = ctx.getString(R.string.game_hint_end)
        }
        else -> {}
    }
    return TableStrings(p0, p1, p2, gameInfo, hint, result)
}

/** Small badge marking the dealing player, anchored to their table position. */
@Composable
internal fun DealerBadge(dealer: Int, ux: (Double) -> Dp, uy: (Double) -> Dp) {
    // seat -> (x, y, width, alignment); seat 3 = top center (future 4-player)
    val (x, y, w, align) = when (dealer) {
        0 -> listOf(312.0, 677.0, 150.0, Alignment.CenterEnd) // right, under own name
        1 -> listOf(20.0, 23.0, 150.0, Alignment.CenterStart)
        2 -> listOf(312.0, 23.0, 150.0, Alignment.CenterEnd)
        else -> listOf(165.0, 23.0, 150.0, Alignment.Center)
    }
    Box(
        modifier = Modifier
            .offset(x = ux(x as Double), y = uy(y as Double))
            .width(ux(w as Double)),
        contentAlignment = align as Alignment
    ) {
        Text(
            text = "(" + stringResource(R.string.dealer_badge) + ")",
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1
        )
    }
}

/** Everything the table needs to run as a multiplayer host. */
class HostedConfig(
    val names: List<String>,
    val seatKinds: List<com.an0obIs.pref.mp.SeatKind>,
    val sendToSeat: (Int, com.an0obIs.pref.mp.GameMsg.State) -> Unit,
    val acts: kotlinx.coroutines.flow.Flow<Pair<Int, com.an0obIs.pref.mp.GameMsg.Act>>
)

@Composable
fun GameScreen(app: PrefApp, onShowScore: () -> Unit, hostedConfig: HostedConfig? = null) {
    val vm: GameViewModel = viewModel()
    val ctx = LocalContext.current
    val images = remember { CardImages(ctx.applicationContext) }

    val ai1 = stringResource(R.string.ai_name_1)
    val ai2 = stringResource(R.string.ai_name_2)
    LaunchedEffect(Unit) {
        vm.onShowScore = onShowScore
        if (hostedConfig != null) {
            vm.startHosted(hostedConfig.names, hostedConfig.seatKinds, hostedConfig.sendToSeat)
        } else {
            vm.start(app, ai1, ai2)
        }
    }
    if (hostedConfig != null) {
        LaunchedEffect(Unit) {
            hostedConfig.acts.collect { (seat, act) -> vm.onRemoteAct(seat, act) }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { vm.onCanvasTap() }
    ) {
        val kx = maxWidth / TableLayout.W.toFloat()
        val ky = maxHeight / TableLayout.H.toFloat()
        fun ux(x: Double): Dp = kx * x.toFloat()
        fun uy(y: Double): Dp = ky * y.toFloat()
        val cardSize = ux(TableLayout.S0)

        // Background
        Image(
            bitmap = images.background(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        val info = vm.info
        val strings = buildTableStrings(ctx, info, mp = vm.hosted)
        val hintText = vm.transientHint?.invoke(ctx)
            ?: (if (vm.thinking) stringResource(R.string.game_thinking) else strings.hint)

        // Cards on the table
        for (pc in vm.field + vm.pinnedOverlays) {
            Image(
                bitmap = images.get(pc.card),
                filterQuality = FilterQuality.High,
                contentDescription = pc.card?.toString(),
                modifier = Modifier
                    .offset(x = ux(pc.x), y = uy(pc.y))
                    .size(cardSize)
                    .clickable(
                        interactionSource = remember(pc) { MutableInteractionSource() },
                        indication = null
                    ) { vm.onCardTap(pc) }
            )
        }

        // Flying card animation
        vm.cardAnim?.let { anim ->
            val t = vm.animProgress
            val x = anim.fromX + (anim.toX - anim.fromX) * t
            val y = anim.fromY + (anim.toY - anim.fromY) * t
            Image(
                bitmap = images.get(anim.card),
                filterQuality = FilterQuality.High,
                contentDescription = null,
                modifier = Modifier
                    .offset(x = ux(x), y = uy(y))
                    .size(cardSize)
            )
        }

        // Trick collection animation (cards fly to taker and shrink)
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
                    modifier = Modifier
                        .offset(x = ux(x), y = uy(y))
                        .size(s)
                )
            }
        }

        // Player labels
        Text(
            text = strings.p1,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier
                .offset(x = ux(20.0), y = uy(10.0))
                .width(ux(196.0))
        )
        Text(
            text = strings.p2,
            color = Color.White,
            fontSize = 13.sp,
            textAlign = TextAlign.Right,
            modifier = Modifier
                .offset(x = ux(266.0), y = uy(10.0))
                .width(ux(196.0))
        )
        Text(
            text = strings.p0,
            color = Color.White,
            fontSize = 13.sp,
            textAlign = TextAlign.Right,
            modifier = Modifier
                .offset(x = ux(177.0), y = uy(664.0))
                .width(ux(285.0))
        )
        Text(
            text = strings.gameInfo,
            color = Color.White,
            fontSize = 13.sp,
            textAlign = TextAlign.Right,
            modifier = Modifier
                .offset(x = ux(177.0), y = uy(694.0))
                .width(ux(285.0))
        )

        // Dealer marker
        if (info.names[info.dealer].isNotEmpty()) {
            DealerBadge(info.dealer, ::ux, ::uy)
        }

        // Hint text (bottom-left "advice bubble" area)
        if (hintText.isNotEmpty()) {
            Text(
                text = hintText,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .offset(x = ux(16.0), y = uy(545.0))
                    .width(ux(150.0))
                    .background(Color(0x66000000), RoundedCornerShape(8.dp))
                    .padding(6.dp)
            )
        }

        // Deal result (EndPlay)
        if (strings.result.isNotEmpty()) {
            Text(
                text = strings.result,
                color = Color.White,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(x = ux(63.0), y = uy(374.0))
                    .width(ux(353.0))
                    .background(Color(0x88000000), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            )
        }

        // Thinking indicator
        if (vm.thinking) {
            CircularProgressIndicator(
                color = AccentYellow,
                modifier = Modifier
                    .offset(x = ux(224.0), y = uy(470.0))
                    .size(32.dp)
            )
        }

        // Say bubbles: the bid appears at the bidder's side, then grows while
        // flying to the center of the table
        vm.say?.let { say ->
            val t = vm.animProgress
            val move = 1f - (1f - t) * (1f - t) // ease-out for the flight
            val (sx, sy) = when (say.player) {
                1 -> 80.0 to 95.0    // left player
                2 -> 400.0 to 95.0   // right player
                else -> 240.0 to 600.0 // local player (bottom)
            }
            val cx = sx + (240.0 - sx) * move
            val cy = sy + (300.0 - sy) * move
            Box(
                modifier = Modifier
                    .offset(x = ux(cx - 150.0), y = uy(cy))
                    .width(ux(300.0)),
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

        // Bid menu
        if (!vm.busy && vm.menuBids.isNotEmpty() &&
            (info.phase == GamePhase.Negotiations || info.phase == GamePhase.GameChoose)
        ) {
            val listState = rememberLazyListState()
            val reversed = remember(vm.menuBids) { vm.menuBids.reversed() }
            LaunchedEffect(reversed) {
                if (reversed.isNotEmpty())
                    listState.scrollToItem(reversed.size - 1)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .offset(x = ux(139.0), y = uy(37.0))
                    .width(ux(203.0))
                    .height(uy(286.0))
                    .background(Color(0x66123B16))
                    .border(1.dp, Color(0x662E7D32))
            ) {
                items(reversed) { bid ->
                    val selected = vm.selectedBid === bid
                    Text(
                        text = GameTexts.bidTitle(ctx, bid),
                        color = if (selected) AccentYellow else Color.White,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.onChoiceSelected(bid) }
                            .padding(10.dp)
                    )
                }
            }
        }

        // Choice buttons (in hosted games: only on the local player's turn)
        if (!vm.busy && (!vm.hosted || info.playerInTurn == 0)) {
            val phase = info.phase
            val btn1Label: String? = when (phase) {
                GamePhase.Negotiations -> vm.selectedBid?.let { GameTexts.bidTitle(ctx, it) }
                GamePhase.VistNegotiations -> stringResource(R.string.game_btn_whist)
                GamePhase.OpeningChoose -> stringResource(R.string.game_btn_open)
                else -> null
            }
            val btn2Label: String? = when (phase) {
                GamePhase.Negotiations -> stringResource(R.string.game_btn_pass)
                GamePhase.VistNegotiations -> stringResource(R.string.game_btn_pass)
                GamePhase.OpeningChoose -> stringResource(R.string.game_btn_closed)
                GamePhase.GameChoose -> vm.selectedBid?.let { GameTexts.bidTitle(ctx, it) }
                    ?: stringResource(R.string.game_btn_not_selected)
                GamePhase.Discarding -> stringResource(R.string.game_btn_discard)
                else -> null
            }
            val btn2Enabled = when (phase) {
                GamePhase.GameChoose -> vm.selectedBid != null
                GamePhase.Discarding -> vm.cardsToDiscard.size == 2
                else -> true
            }
            if (btn1Label != null) {
                Button(
                    onClick = { vm.onButton1() },
                    modifier = Modifier
                        .offset(x = ux(152.0), y = uy(330.0))
                        .width(ux(176.0))
                ) {
                    Text(btn1Label, maxLines = 1)
                }
            }
            if (btn2Label != null) {
                Button(
                    onClick = { vm.onButton2() },
                    enabled = btn2Enabled,
                    modifier = Modifier
                        .offset(x = ux(152.0), y = uy(385.0))
                        .width(ux(176.0))
                ) {
                    Text(btn2Label, maxLines = 1)
                }
            }
        }

        // "Layout and discard" buttons (open play against a contractor AI)
        if (!vm.busy && info.showPrikupBtn1) {
            OutlinedButton(
                onClick = { vm.showHandWithPrikup(1) },
                modifier = Modifier.offset(x = ux(192.0), y = uy(0.0))
            ) {
                Text(stringResource(R.string.game_btn_show_prikup), fontSize = 11.sp, color = Color.White)
            }
        }
        if (!vm.busy && info.showPrikupBtn2) {
            OutlinedButton(
                onClick = { vm.showHandWithPrikup(2) },
                modifier = Modifier.offset(x = ux(192.0), y = uy(0.0))
            ) {
                Text(stringResource(R.string.game_btn_show_prikup), fontSize = 11.sp, color = Color.White)
            }
        }

        // Bottom-left action buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!vm.hosted) {
                OutlinedButton(onClick = { vm.requestAdvice() }) {
                    Text(stringResource(R.string.game_btn_hint), fontSize = 12.sp, color = Color.White)
                }
            }
            if (info.showTricksBtn) {
                OutlinedButton(onClick = { vm.openTricks() }) {
                    Text(stringResource(R.string.game_btn_tricks), fontSize = 12.sp, color = Color.White)
                }
            }
        }

        // Multiplayer: score standing between deals / at game end
        vm.scoresOverlay?.let { snap ->
            ScoreOverlay(
                snap = snap,
                modifier = Modifier.fillMaxSize(),
                onTap = { vm.onCanvasTap() }
            )
        }

        // Past tricks popup
        if (vm.showTricks) {
            Column(
                modifier = Modifier
                    .offset(x = ux(24.0), y = uy(18.0))
                    .width(ux(432.0))
                    .height(uy(500.0))
                    .background(Color(0xFF009B00), RoundedCornerShape(6.dp))
                    .border(1.dp, Color.White, RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.game_trick_led),
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Box(modifier = Modifier.weight(1.6f))
                    Text(
                        text = stringResource(R.string.game_trick_took),
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    for ((idx, take) in vm.tricks.withIndex()) {
                        // only the last trick may be reviewed until the deal ends
                        val faceDown = vm.hidePastTricks && idx < vm.tricks.size - 1
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = vm.tricksNames[take.firstMovePerformer] ?: "",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                            Row(
                                modifier = Modifier.weight(1.6f),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                take.prikupMove?.let {
                                    Image(bitmap = images.get(if (faceDown) null else it), filterQuality = FilterQuality.High, contentDescription = null, modifier = Modifier.size(34.dp))
                                }
                                Image(bitmap = images.get(if (faceDown) null else take.nextMove), filterQuality = FilterQuality.High, contentDescription = null, modifier = Modifier.size(34.dp))
                                Image(bitmap = images.get(if (faceDown) null else take.prevMove), filterQuality = FilterQuality.High, contentDescription = null, modifier = Modifier.size(34.dp))
                                Image(bitmap = images.get(if (faceDown) null else take.myMove), filterQuality = FilterQuality.High, contentDescription = null, modifier = Modifier.size(34.dp))
                            }
                            Text(
                                text = vm.tricksNames[take.takenBy] ?: "",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Button(
                    onClick = { vm.showTricks = false },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(R.string.game_btn_close))
                }
            }
        }
    }
}

private val AccentYellow = Color(0xFFFFB100)
