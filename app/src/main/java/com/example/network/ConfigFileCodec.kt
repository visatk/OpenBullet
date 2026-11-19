package com.example.network

import com.example.model.ConfigItem
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class ConfigExportFormat(val extension: String, val label: String) {
    LOLI("loli", ".loli"),
    JSON("json", ".json")
}

object ConfigFileCodec {

    private val moshi: Moshi by lazy { Moshi.Builder().build() }

    private val mapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        Any::class.java
    )

    private val listOfMapsType = Types.newParameterizedType(
        List::class.java,
        mapType
    )

    private val prettyMapAdapter: JsonAdapter<Map<String, Any?>> by lazy {
        moshi.adapter<Map<String, Any?>>(mapType).indent("  ")
    }

    private val prettyListAdapter: JsonAdapter<List<Map<String, Any?>>> by lazy {
        moshi.adapter<List<Map<String, Any?>>>(listOfMapsType).indent("  ")
    }

    private val lenientAnyAdapter: JsonAdapter<Any> by lazy {
        moshi.adapter(Any::class.java).lenient()
    }

    fun detectFormatLabel(rawText: String): String {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return "Empty"
        return when {
            trimmed.contains("[SETTINGS]", ignoreCase = true) ||
                trimmed.contains("[SCRIPT]", ignoreCase = true) -> "OpenBullet .loli"
            trimmed.startsWith("[") -> "JSON Config Bundle (Array)"
            trimmed.startsWith("{") -> "JSON Config Object"
            else -> "Raw LoliScript"
        }
    }

    fun serializeToLoli(config: ConfigItem): String {
        val settingsMap = linkedMapOf<String, Any?>(
            "Name" to config.name,
            "Author" to config.author,
            "Category" to config.category,
            "AllowedWordlist1" to config.wordlistType,
            "NeedsProxies" to config.needsProxies,
            "LastModified" to config.lastModified,
            "Version" to "1.0.0"
        )
        return buildString {
            appendLine("[SETTINGS]")
            appendLine(prettyMapAdapter.toJson(settingsMap))
            appendLine()
            appendLine("[SCRIPT]")
            append(config.scriptPreview.trim())
        }
    }

    fun serializeToJson(config: ConfigItem): String {
        return prettyMapAdapter.toJson(configToMap(config))
    }

    fun serializeListToJson(configs: List<ConfigItem>): String {
        return prettyListAdapter.toJson(configs.map { configToMap(it) })
    }

    private fun configToMap(config: ConfigItem): Map<String, Any?> {
        return linkedMapOf(
            "id" to config.id,
            "name" to config.name,
            "author" to config.author,
            "category" to config.category,
            "wordlistType" to config.wordlistType,
            "needsProxies" to config.needsProxies,
            "lastModified" to config.lastModified,
            "script" to config.scriptPreview
        )
    }

    fun parseImportPayload(
        rawText: String,
        fallbackName: String = "Imported Config"
    ): List<ConfigItem> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1. Check for standard OpenBullet .loli format ([SETTINGS] ... [SCRIPT] ...)
        if (trimmed.contains("[SETTINGS]", ignoreCase = true) ||
            trimmed.contains("[SCRIPT]", ignoreCase = true)
        ) {
            return listOf(parseLoliFormat(trimmed, fallbackName))
        }

        // 2. Check for JSON Array of configs
        if (trimmed.startsWith("[")) {
            val parsedList = runCatching {
                val arr = lenientAnyAdapter.fromJson(trimmed) as? List<*> ?: return@runCatching null
                arr.mapIndexedNotNull { index, item ->
                    val obj = item as? Map<*, *> ?: return@mapIndexedNotNull null
                    parseJsonMap(obj, "$fallbackName #${index + 1}")
                }
            }.getOrNull()
            if (!parsedList.isNullOrEmpty()) return parsedList
        }

        // 3. Check for single JSON Object (either our JSON format or raw [SETTINGS] JSON)
        if (trimmed.startsWith("{")) {
            val single = runCatching {
                val obj = lenientAnyAdapter.fromJson(trimmed) as? Map<*, *> ?: return@runCatching null
                parseJsonMap(obj, fallbackName)
            }.getOrNull()
            if (single != null) return listOf(single)
        }

        // 4. Fallback: treat as raw LoliScript code
        val cleanName = stripConfigExtension(fallbackName, "Imported Config")

        return listOf(
            ConfigItem(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                author = "Imported",
                category = "Default",
                wordlistType = "Mail:Pass",
                needsProxies = true,
                lastModified = todayString(),
                scriptPreview = trimmed
            )
        )
    }

    private fun parseLoliFormat(text: String, fallbackName: String): ConfigItem {
        val settingsIdx = text.indexOf("[SETTINGS]", ignoreCase = true)
        val scriptIdx = text.indexOf("[SCRIPT]", ignoreCase = true)

        val settingsRaw = when {
            settingsIdx >= 0 && scriptIdx > settingsIdx ->
                text.substring(settingsIdx + "[SETTINGS]".length, scriptIdx).trim()
            settingsIdx >= 0 ->
                text.substring(settingsIdx + "[SETTINGS]".length).trim()
            else -> ""
        }

        val scriptRaw = when {
            scriptIdx >= 0 && settingsIdx > scriptIdx ->
                text.substring(scriptIdx + "[SCRIPT]".length, settingsIdx).trim()
            scriptIdx >= 0 ->
                text.substring(scriptIdx + "[SCRIPT]".length).trim()
            else -> ""
        }

        val settingsMap = runCatching {
            val braceStart = settingsRaw.indexOf('{')
            val braceEnd = settingsRaw.lastIndexOf('}')
            if (braceStart >= 0 && braceEnd > braceStart) {
                lenientAnyAdapter.fromJson(settingsRaw.substring(braceStart, braceEnd + 1)) as? Map<*, *>
            } else null
        }.getOrNull()

        val cleanFallback = stripConfigExtension(fallbackName, "Imported .loli Config")

        val name = settingsMap?.optNonBlankString("Name", "name") ?: cleanFallback
        val author = settingsMap?.optNonBlankString("Author", "author") ?: "LocalUser"
        val category = settingsMap?.optNonBlankString("Category", "category") ?: "Default"
        val wordlistType = settingsMap?.optNonBlankString("AllowedWordlist1", "wordlistType") ?: "Mail:Pass"
        val needsProxies = settingsMap?.optBoolean("NeedsProxies", "needsProxies", default = true) ?: true
        val lastModified = settingsMap?.optNonBlankString("LastModified", "lastModified") ?: todayString()

        return ConfigItem(
            id = UUID.randomUUID().toString(),
            name = name,
            author = author,
            category = category,
            wordlistType = wordlistType,
            needsProxies = needsProxies,
            lastModified = lastModified,
            scriptPreview = scriptRaw.ifBlank { ConfigTemplates.presets.first().script }
        )
    }

    private fun parseJsonMap(obj: Map<*, *>, fallbackName: String): ConfigItem {
        val cleanFallback = stripConfigExtension(fallbackName, "Imported JSON Config")

        val name = obj.optNonBlankString("name", "Name") ?: cleanFallback
        val author = obj.optNonBlankString("author", "Author") ?: "LocalUser"
        val category = obj.optNonBlankString("category", "Category") ?: "Default"
        val wordlistType = obj.optNonBlankString("wordlistType", "AllowedWordlist1") ?: "Mail:Pass"
        val needsProxies = obj.optBoolean("needsProxies", "NeedsProxies", default = true)
        val lastModified = obj.optNonBlankString("lastModified", "LastModified") ?: todayString()
        val script = obj.optNonBlankString("script", "scriptPreview", "Script")
            ?: ConfigTemplates.presets.first().script

        return ConfigItem(
            id = UUID.randomUUID().toString(),
            name = name,
            author = author,
            category = category,
            wordlistType = wordlistType,
            needsProxies = needsProxies,
            lastModified = lastModified,
            scriptPreview = script
        )
    }

    private fun Map<*, *>.optNonBlankString(vararg keys: String): String? {
        for (key in keys) {
            val value = this[key] ?: continue
            val text = value.toString().trim()
            if (text.isNotEmpty()) return text
        }
        return null
    }

    private fun Map<*, *>.optBoolean(vararg keys: String, default: Boolean): Boolean {
        for (key in keys) {
            if (!this.containsKey(key)) continue
            return when (val value = this[key]) {
                is Boolean -> value
                is String -> value.equals("true", ignoreCase = true)
                is Number -> value.toInt() != 0
                else -> default
            }
        }
        return default
    }

    private fun stripConfigExtension(rawName: String, defaultName: String): String {
        val stripped = rawName.trim()
            .replace(Regex("""\.(loli|json|txt)$""", RegexOption.IGNORE_CASE), "")
            .trim()
        return stripped.ifEmpty { defaultName }
    }

    private fun todayString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
