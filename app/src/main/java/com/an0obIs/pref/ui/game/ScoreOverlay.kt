package com.an0obIs.pref.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.an0obIs.pref.R
import com.an0obIs.pref.mp.ScoreSnap

/**
 * Between-deals score for multiplayer, drawn as the traditional pulka sheet
 * (same 480x550 geometry as CalcSheet3a). Read-only; tap to continue.
 */
@Composable
fun ScoreOverlay(snap: ScoreSnap, modifier: Modifier = Modifier, onTap: () -> Unit) {
    // line fractions from CalcSheet3a.DrawLines
    val s1 = 0.15f; val s2 = 0.30f; val s3 = 0.45f
    val e1 = 0.85f; val e2 = 0.70f; val e3 = 0.55f
    val lines = remember {
        listOf(
            floatArrayOf(0.5f, 0.1f, 0.5f, s3),
            floatArrayOf(0f, 1f, s3, e3),
            floatArrayOf(e3, e3, 1f, 1f),
            floatArrayOf(s1, 0.1f, s1, e1),
            floatArrayOf(e1, 0.1f, e1, e1),
            floatArrayOf(s1, e1, e1, e1),
            floatArrayOf(s2, 0.1f, s2, e2),
            floatArrayOf(e2, 0.1f, e2, e2),
            floatArrayOf(s2, e2, e2, e2),
            floatArrayOf(0f, 0.5f, s1, 0.5f),
            floatArrayOf(1f, 0.5f, e1, 0.5f),
            floatArrayOf(0.5f, 0.95f, 0.5f, e1)
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .background(Color(0xF2103814), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            )
    ) {
        val kx = maxWidth / 480f
        val ky = maxHeight / 550f
        fun ux(x: Double): Dp = kx * x.toFloat()
        fun uy(y: Double): Dp = ky * y.toFloat()

        Canvas(modifier = Modifier.fillMaxSize()) {
            for (l in lines) {
                drawLine(
                    color = Color.White,
                    start = Offset(l[0] * size.width, l[1] * size.height),
                    end = Offset(l[2] * size.width, l[3] * size.height),
                    strokeWidth = 2.5f
                )
            }
        }

        @Composable
        fun cell(text: String, x: Double, y: Double, w: Double, size: Int = 17, gold: Boolean = false, align: TextAlign = TextAlign.Center) {
            Text(
                text = text,
                fontSize = size.sp,
                color = if (gold) Color(0xFFD4AF37) else Color.White,
                textAlign = align,
                maxLines = 1,
                modifier = Modifier.offset(x = ux(x), y = uy(y)).width(ux(w))
            )
        }

        // positions from CalcSheet3a.xaml (480x550 canvas)
        cell(snap.limit.toString(), 190.0, 253.0, 101.0, size = 20)
        cell(snap.gora[0].toString(), 201.0, 324.0, 80.0)
        cell(snap.gora[1].toString(), 139.0, 201.0, 95.0)
        cell(snap.gora[2].toString(), 252.0, 201.0, 93.0)
        cell(snap.pulya[0].toString(), 190.0, 414.0, 101.0)
        cell(snap.pulya[1].toString(), 83.0, 320.0, 57.0)
        cell(snap.pulya[2].toString(), 332.0, 320.0, 71.0)
        cell(snap.visty[0][1].toString(), 110.0, 492.0, 80.0)
        cell(snap.visty[0][2].toString(), 300.0, 492.0, 80.0)
        cell(snap.visty[1][0].toString(), 0.0, 362.0, 71.0)
        cell(snap.visty[1][2].toString(), 0.0, 119.0, 71.0)
        cell(snap.visty[2][0].toString(), 387.0, 356.0, 95.0)
        cell(snap.visty[2][1].toString(), 396.0, 119.0, 80.0)
        cell(snap.names[0], 83.0, 530.0, 320.0, size = 13, gold = true)
        cell(snap.names[1], 4.0, 17.0, 214.0, size = 13, gold = true, align = TextAlign.Left)
        cell(snap.names[2], 262.0, 17.0, 214.0, size = 13, gold = true, align = TextAlign.Right)

        Text(
            text = stringResource(R.string.game_hint_end),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(y = uy(275.0)).width(ux(480.0))
        )
    }
}
