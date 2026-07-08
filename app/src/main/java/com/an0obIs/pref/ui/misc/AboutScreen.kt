package com.an0obIs.pref.ui.misc

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.an0obIs.pref.R
import com.an0obIs.pref.ui.AccentGold

/** Port of About.xaml. */
@Composable
fun AboutScreen(versionName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            color = AccentGold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(stringResource(R.string.about_author), fontSize = 20.sp, modifier = Modifier.padding(vertical = 4.dp))
        Text(stringResource(R.string.about_designer), fontSize = 20.sp, modifier = Modifier.padding(vertical = 4.dp))
        Text(stringResource(R.string.about_version, versionName), fontSize = 20.sp, modifier = Modifier.padding(vertical = 4.dp))
        Text(stringResource(R.string.about_desc), modifier = Modifier.padding(top = 16.dp))
        Text(stringResource(R.string.about_features), modifier = Modifier.padding(top = 12.dp))
        Text(stringResource(R.string.about_f1), modifier = Modifier.padding(start = 16.dp, top = 6.dp))
        Text(stringResource(R.string.about_f2), modifier = Modifier.padding(start = 16.dp, top = 6.dp))
        Text(stringResource(R.string.about_f3), modifier = Modifier.padding(start = 16.dp, top = 6.dp))
        Text(stringResource(R.string.about_f4), modifier = Modifier.padding(start = 16.dp, top = 6.dp))
        LinkRow(stringResource(R.string.about_privacy), "https://preferansmaster.com/privacy",
            topPadding = 24.dp)
        LinkRow(stringResource(R.string.about_support), "https://preferansmaster.com/support")
    }
}

@Composable
private fun LinkRow(label: String, url: String, topPadding: androidx.compose.ui.unit.Dp = 8.dp) {
    val ctx = LocalContext.current
    Text(
        text = label,
        fontSize = 17.sp,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .padding(top = topPadding)
            .clickable {
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                } catch (e: Exception) {
                    // no browser installed: nothing sensible to do
                }
            }
    )
}
