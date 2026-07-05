package com.an0obIs.pref.ui.misc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.an0obIs.pref.R
import com.an0obIs.pref.ui.AccentGold
import java.nio.charset.StandardCharsets

private data class DictItem(val word: String, val description: String)

private fun loadDictionary(ctx: android.content.Context): List<DictItem> {
    val bytes = ctx.assets.open("dictionary.txt").use { it.readBytes() }
    // The original file is UTF-16 ("Encoding.Unicode"); detect BOM.
    val text = when {
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        else -> String(bytes, StandardCharsets.UTF_16LE)
    }.replace('\r', ' ')
    return text.split('\n').mapNotNull { line ->
        val w = line.split('=')
        if (w.size != 2) null else DictItem(w[0].uppercase(), w[1])
    }
}

private fun formatDescription(description: String): String {
    var d = description
    if (d.startsWith("1.")) {
        for (i in 2..9)
            d = d.replace("$i.", "\n$i.")
    }
    d = d.replace("Этимология", "\nЭтимология")
    return d
}

/** Port of Dictionary.xaml.cs: search the glossary of Preferans terms. */
@Composable
fun DictionaryScreen() {
    val ctx = LocalContext.current
    val dict = remember { loadDictionary(ctx.applicationContext) }
    var search by remember { mutableStateOf("") }

    val results = remember(search) {
        val s = search.uppercase()
        if (s.isEmpty()) emptyList()
        else dict.filter { it.word.startsWith(s) }.sortedBy { it.word }.take(10)
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = stringResource(R.string.dict_title),
            fontSize = 40.sp,
            color = AccentGold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            singleLine = true,
            label = { Text(stringResource(R.string.dict_search)) },
            modifier = Modifier.fillMaxWidth()
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp)
        ) {
            for (item in results) {
                Text(text = item.word, fontSize = 20.sp, modifier = Modifier.padding(top = 10.dp))
                Text(text = formatDescription(item.description), fontSize = 15.sp)
            }
        }
    }
}
