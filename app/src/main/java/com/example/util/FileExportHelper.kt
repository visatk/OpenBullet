package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Safe file export (via FileProvider) and SAF document reading utilities.
 */
object FileExportHelper {

    private const val EXPORTS_DIR = "exports"
    private const val MAX_INLINE_SHARE_BYTES = 16 * 1024

    fun copyToClipboard(context: Context, label: String, text: String) {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText(label, text))
        }
    }

    /**
     * Shares exported text safely. Writes large or structured exports to cache/exports/ on [Dispatchers.IO]
     * and shares via FileProvider URI to avoid Binder TransactionTooLargeException and main-thread disk I/O.
     */
    suspend fun shareTextFile(
        context: Context,
        fileName: String,
        content: String,
        mimeType: String = "text/plain",
        alsoCopyToClipboard: Boolean = false
    ): Boolean {
        if (alsoCopyToClipboard) {
            copyToClipboard(context, fileName, content)
        }

        val safeName = sanitizeFileName(fileName)
        val fileUri = withContext(Dispatchers.IO) {
            runCatching {
                val exportDir = File(context.cacheDir, EXPORTS_DIR).apply { mkdirs() }
                val outFile = File(exportDir, safeName)
                outFile.writeText(content, Charsets.UTF_8)
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    outFile
                )
            }.getOrNull()
        }

        val sendIntent = if (fileUri != null) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_SUBJECT, safeName)
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            val bounded = if (content.toByteArray(Charsets.UTF_8).size > MAX_INLINE_SHARE_BYTES) {
                content.take(MAX_INLINE_SHARE_BYTES / 2)
            } else {
                content
            }
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, safeName)
                putExtra(Intent.EXTRA_TEXT, bounded)
            }
        }

        return runCatching {
            val chooser = Intent.createChooser(sendIntent, safeName).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    private const val MAX_IMPORT_CHARS = 8 * 1024 * 1024 // 8 MB safety bound for low-RAM devices

    /**
     * Reads UTF-8 text and display filename from a SAF document URI on [Dispatchers.IO]
     * with an 8 MB safety cap to prevent OutOfMemoryError on low-memory devices.
     */
    suspend fun readTextFromUri(
        context: Context,
        uri: Uri,
        fallbackName: String = "imported.txt"
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = resolveDisplayName(context, uri) ?: fallbackName
            val content = context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { reader ->
                    val sb = StringBuilder()
                    val buf = CharArray(8192)
                    var read: Int
                    while (reader.read(buf).also { read = it } != -1) {
                        val remaining = MAX_IMPORT_CHARS - sb.length
                        if (remaining <= 0) break
                        sb.append(buf, 0, minOf(read, remaining))
                    }
                    sb.toString()
                }
                .orEmpty()
            if (content.isEmpty()) null else displayName to content
        }.getOrNull()
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)?.trim()
                        if (!name.isNullOrEmpty()) return name
                    }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    }

    private fun sanitizeFileName(raw: String): String {
        val cleaned = raw.trim().replace(Regex("""[^a-zA-Z0-9._-]"""), "_")
        return cleaned.ifEmpty { "openbullet_export.txt" }
    }
}
