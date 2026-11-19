package com.example.data.local

import android.content.Context
import java.io.File

/**
 * File-backed storage for wordlist line payloads so large wordlists never exceed SQLite CursorWindow limits.
 */
class WordlistFileStore(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "wordlists")

    private fun fileFor(id: String): File {
        val safeId = id.replace(Regex("""[^a-zA-Z0-9._-]"""), "_").ifEmpty { "default" }
        return File(dir, "$safeId.txt")
    }

    fun writeLines(id: String, lines: List<String>) {
        if (!dir.exists()) dir.mkdirs()
        val target = fileFor(id)
        target.bufferedWriter(Charsets.UTF_8).use { writer ->
            lines.forEachIndexed { index, line ->
                if (index > 0) writer.newLine()
                writer.write(line)
            }
        }
    }

    fun readLines(id: String, legacyLinesRaw: String = ""): List<String> {
        val target = fileFor(id)
        if (target.exists() && target.isFile) {
            return runCatching {
                target.bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readLines()
                }
            }.getOrElse {
                if (legacyLinesRaw.isEmpty()) emptyList() else legacyLinesRaw.split("\n")
            }
        }
        return if (legacyLinesRaw.isEmpty()) emptyList() else legacyLinesRaw.split("\n")
    }

    fun delete(id: String) {
        runCatching {
            val target = fileFor(id)
            if (target.exists()) target.delete()
        }
    }

    fun clearAll() {
        runCatching {
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
        }
    }
}
