package com.an0obIs.pref.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.an0obIs.pref.R
import com.an0obIs.pref.mp.ScoreSnap

/** Between-deals score standing for multiplayer (host and guests). */
@Composable
fun ScoreOverlay(snap: ScoreSnap, modifier: Modifier = Modifier, onTap: () -> Unit) {
    Column(
        modifier = modifier
            .background(Color(0xE6103814), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            )
            .padding(14.dp)
    ) {
        Text(
            text = stringResource(R.string.score_title_fmt, snap.limit),
            color = Color(0xFFD4AF37),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("", modifier = Modifier.weight(1.4f))
            Text(stringResource(R.string.sheet_pulya), color = Color.White, fontSize = 12.sp,
                textAlign = TextAlign.Right, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.sheet_gora), color = Color.White, fontSize = 12.sp,
                textAlign = TextAlign.Right, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.score_whists), color = Color.White, fontSize = 12.sp,
                textAlign = TextAlign.Right, modifier = Modifier.weight(1f))
        }
        for (i in snap.names.indices) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(snap.names[i], color = Color.White, fontSize = 15.sp,
                    maxLines = 1, modifier = Modifier.weight(1.4f))
                Text("${snap.pulya[i]}", color = Color.White, fontSize = 15.sp,
                    textAlign = TextAlign.Right, modifier = Modifier.weight(1f))
                Text("${snap.gora[i]}", color = Color.White, fontSize = 15.sp,
                    textAlign = TextAlign.Right, modifier = Modifier.weight(1f))
                Text("${snap.whists[i]}", color = Color.White, fontSize = 15.sp,
                    textAlign = TextAlign.Right, modifier = Modifier.weight(1f))
            }
        }
        Text(
            text = stringResource(R.string.game_hint_end),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}
