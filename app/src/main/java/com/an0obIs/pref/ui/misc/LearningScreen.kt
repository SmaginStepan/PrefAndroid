package com.an0obIs.pref.ui.misc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.an0obIs.pref.R
import com.an0obIs.pref.ui.AccentGold
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Picks the course asset for the app's current language (ru is the original file). */
private fun courseAssetName(ctx: android.content.Context, name: String): String {
    val language = ctx.resources.configuration.locales[0].language
    return when (language) {
        "ru" -> "$name.xml"
        "es" -> "${name}_es.xml"
        else -> "${name}_en.xml"
    }
}

/** Loads the tutorial course (DataContract XML): a sequence of <Text> stages. */
private fun loadCourse(ctx: android.content.Context, name: String): List<String> {
    val stages = mutableListOf<String>()
    ctx.assets.open(courseAssetName(ctx, name)).use { stream ->
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(stream, null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "Text") {
                stages.add(parser.nextText().trim())
            }
            event = parser.next()
        }
    }
    return stages
}

/** Port of Learning.xaml.cs: paged tutorial course. */
@Composable
fun LearningScreen(onFinished: () -> Unit) {
    val ctx = LocalContext.current
    val stages = remember { loadCourse(ctx, "tutorial") }
    var position by remember { mutableIntStateOf(1) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = stringResource(R.string.learn_title),
            fontSize = 40.sp,
            color = AccentGold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (stages.isNotEmpty())
                Text(text = stages[position - 1], fontSize = 18.sp)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { position-- },
                enabled = position > 1
            ) { Text(stringResource(R.string.learn_prev)) }
            Text(text = "$position/${stages.size}")
            Button(onClick = {
                if (position == stages.size) onFinished() else position++
            }) {
                Text(stringResource(if (position == stages.size) R.string.learn_end else R.string.learn_next))
            }
        }
    }
}
