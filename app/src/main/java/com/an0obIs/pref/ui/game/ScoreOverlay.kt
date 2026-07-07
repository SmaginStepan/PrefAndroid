package com.an0obIs.pref.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.an0obIs.pref.model.ScoreValueType
import com.an0obIs.pref.ui.calc.CELLS_3
import com.an0obIs.pref.ui.calc.CELLS_4
import com.an0obIs.pref.ui.calc.LINES_3
import com.an0obIs.pref.ui.calc.LINES_4
import com.an0obIs.pref.ui.calc.NAMES_3
import com.an0obIs.pref.ui.calc.NAMES_4
import com.an0obIs.pref.ui.calc.SHEET_H
import com.an0obIs.pref.ui.calc.SHEET_W

/**
 * Between-deals score for multiplayer, drawn as the traditional pulka sheet
 * (same 480x550 geometry as the score calculator, 3 or 4 players). Read-only;
 * tap to continue. [onSave] writes the standing as a regular pulka file.
 */
@Composable
fun ScoreOverlay(
    snap: ScoreSnap,
    modifier: Modifier = Modifier,
    onSave: (() -> Boolean)? = null,
    onTap: () -> Unit
) {
    val n = snap.names.size
    val cells = if (n == 4) CELLS_4 else CELLS_3
    val nameLabels = if (n == 4) NAMES_4 else NAMES_3
    val lines = if (n == 4) LINES_4 else LINES_3

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
        val kx = maxWidth / SHEET_W.toFloat()
        val ky = maxHeight / SHEET_H.toFloat()
        fun ux(x: Double): Dp = kx * x.toFloat()
        fun uy(y: Double): Dp = ky * y.toFloat()

        Canvas(modifier = Modifier.fillMaxSize()) {
            for (l in lines) {
                drawLine(
                    color = Color.White,
                    start = Offset((l.x1 * size.width).toFloat(), (l.y1 * size.height).toFloat()),
                    end = Offset((l.x2 * size.width).toFloat(), (l.y2 * size.height).toFloat()),
                    strokeWidth = 2.5f
                )
            }
        }

        for (cell in cells) {
            val value = when (cell.type) {
                ScoreValueType.Gora -> snap.gora[cell.player]
                ScoreValueType.Pulya -> snap.pulya[cell.player]
                ScoreValueType.Visty -> snap.visty[cell.player][cell.refPlayer]
            }
            Text(
                text = value.toString(),
                fontSize = 17.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.offset(x = ux(cell.x), y = uy(cell.y)).width(ux(cell.w))
            )
        }

        Text(
            text = snap.limit.toString(),
            fontSize = 20.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.offset(x = ux(190.0), y = uy(253.0)).width(ux(101.0))
        )

        for (label in nameLabels) {
            val dealer = label.player == snap.dealer
            Text(
                text = (if (dealer) "▸ " else "") + snap.names[label.player],
                fontSize = 13.sp,
                color = Color(0xFFD4AF37),
                textAlign = label.align,
                maxLines = 1,
                modifier = Modifier.offset(x = ux(label.x), y = uy(label.y)).width(ux(label.w))
            )
        }

        Button(
            onClick = onTap,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
        ) {
            Text(stringResource(R.string.sheet_continue), fontSize = 12.sp)
        }

        if (onSave != null) {
            var saved by remember(snap) { mutableStateOf(false) }
            OutlinedButton(
                onClick = { saved = onSave() },
                enabled = !saved,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
            ) {
                Text(
                    text = stringResource(
                        if (saved) R.string.game_score_saved else R.string.game_btn_save_score
                    ),
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
        }
    }
}
