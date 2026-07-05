package com.an0obIs.pref.model

import kotlinx.serialization.json.Json
import java.io.File

/**
 * Replacement for WP7 IsolatedStorage: plain files in the app's private files dir.
 * Must be initialized once with [init] before any model class is used.
 */
object PrefStorage {
    lateinit var dir: File
        private set

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowStructuredMapKeys = true
    }

    fun init(filesDir: File) {
        dir = filesDir
    }

    fun exists(name: String): Boolean = File(dir, name).exists()

    fun readText(name: String): String? {
        val f = File(dir, name)
        return if (f.exists()) f.readText() else null
    }

    fun writeText(name: String, text: String) {
        File(dir, name).writeText(text)
    }

    fun delete(name: String) {
        File(dir, name).delete()
    }

    fun listFiles(prefix: String): List<String> =
        dir.listFiles()?.map { it.name }?.filter { it.startsWith(prefix) } ?: emptyList()

    /** Original Save() deleted all files sharing the name's prefix up to the last '_'. */
    fun deleteFamily(name: String) {
        val index = name.lastIndexOf('_')
        if (index > 0) {
            val prefix = name.substring(0, index)
            listFiles(prefix).forEach { delete(it) }
        }
    }
}
