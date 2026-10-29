package com.an0obIs.pref.ui.misc

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.an0obIs.pref.R
import com.an0obIs.pref.monetization.BillingManager

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Opens this app's store page, where the player can leave a review. */
fun openStorePage(context: Context) {
    val pkg = context.packageName
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: ActivityNotFoundException) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** A gentle review request; it never blocks the score table underneath. */
@Composable
fun RateBanner(onRate: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(Color(0x33D4AF37), RoundedCornerShape(10.dp))
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.rate_banner_text),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Button(onClick = onRate) { Text(stringResource(R.string.rate_banner_rate), fontSize = 13.sp) }
        TextButton(onClick = onLater) {
            Text(
                stringResource(R.string.rate_banner_later),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

/** The one-time "Remove ads" purchase, with restore. */
@Composable
fun RemoveAdsDialog(onDismiss: () -> Unit) {
    val adFree by BillingManager.adFree.collectAsState()
    val price by BillingManager.price.collectAsState()
    val activity = LocalContext.current.findActivity()
    var unavailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { BillingManager.refresh() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_remove_ads)) },
        text = {
            Column {
                Text(stringResource(if (adFree) R.string.ads_thanks else R.string.ads_dialog_text), fontSize = 15.sp)
                if (unavailable && !adFree) {
                    Text(
                        text = stringResource(R.string.ads_unavailable),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (!adFree) {
                Button(onClick = {
                    unavailable = !(activity?.let { BillingManager.buy(it) } ?: false)
                }) {
                    Text(
                        price?.let { stringResource(R.string.ads_buy_fmt, it) }
                            ?: stringResource(R.string.ads_buy_plain)
                    )
                }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        },
        dismissButton = {
            Row {
                if (!adFree) {
                    TextButton(onClick = { BillingManager.refresh() }) {
                        Text(stringResource(R.string.ads_restore))
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
            }
        }
    )
}
