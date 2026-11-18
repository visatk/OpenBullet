package com.example.network

import com.example.model.ConfigItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class ConfigExportFormat(val extension: String, val label: String) {
    LOLI("loli", ".loli"),
    JSON("json", ".json")
}

object ConfigFileCodec {

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
        val settingsJson = JSONObject().apply {
            put("Name", config.name)
            put("Author", config.author)
            put("Category", config.category)
            put("AllowedWordlist1", config.wordlistType)
            put("NeedsProxies", config.needsProxies)
            put("LastModified", config.lastModified)
            put("Version", "1.0.0")
        }
        return buildString {
            appendLine("[SETTINGS]")
            appendLine(settingsJson.toString(2))
            appendLine()
            appendLine("[SCRIPT]")
            append(config.scriptPreview.trim())
        }
    }

    fun serializeToJson(config: ConfigItem): String {
        return configToJsonObject(config).toString(2)
    }

    fun serializeListToJson(configs: List<ConfigItem>): String {
        val array = JSONArray()
        configs.forEach { cfg ->
            array.put(configToJsonObject(cfg))
        }
        return array.toString(2)
    }

    private fun configToJsonObject(config: ConfigItem): JSONObject {
        return JSONObject().apply {
            put("id", config.id)
            put("name", config.name)
            put("author", config.author)
            put("category", config.category)
            put("wordlistType", config.wordlistType)
            put("needsProxies", config.needsProxies)
            put("lastModified", config.lastModified)
            put("script", config.scriptPreview)
        }
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
                val arr = JSONArray(trimmed)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        add(parseJsonObject(obj, "$fallbackName #${i + 1}"))
                    }
                }
            }.getOrNull()
            if (!parsedList.isNullOrEmpty()) return parsedList
        }

        // 3. Check for single JSON Object (either our JSON format or raw [SETTINGS] JSON)
        if (trimmed.startsWith("{")) {
            val single = runCatching {
                val obj = JSONObject(trimmed)
                parseJsonObject(obj, fallbackName)
            }.getOrNull()
            if (single != null) return listOf(single)
        }

        // 4. Fallback: treat as raw LoliScript code
        val cleanName = fallbackName
            .removeSuffix(".loli")
            .removeSuffix(".json")
            .removeSuffix(".txt")
            .trim()
            .ifEmpty { "Imported Config" }

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
            scriptIdx >= 0 -> text.substring(scriptIdx + "[SCRIPT]".length).trim()
            else -> ""
        }

        val settingsObj = runCatching {
            val braceStart = settingsRaw.indexOf('{')
            val braceEnd = settingsRaw.lastIndexOf('}')
            if (braceStart >= 0 && braceEnd > braceStart) {
                JSONObject(settingsRaw.substring(braceStart, braceEnd + 1))
            } else null
        }.getOrNull()

        val cleanFallback = fallbackName
            .removeSuffix(".loli")
            .removeSuffix(".json")
            .trim()
            .ifEmpty { "Imported .loli Config" }

        val name = settingsObj?.optString("Name")?.takeIf { it.isNotBlank() }
            ?: settingsObj?.optString("name")?.takeIf { it.isNotBlank() }
            ?: cleanFallback
        val author = settingsObj?.optString("Author")?.takeIf { it.isNotBlank() }
            ?: settingsObj?.optString("author")?.takeIf { it.isNotBlank() }
            ?: "LocalUser"
        val category = settingsObj?.optString("Category")?.takeIf { it.isNotBlank() }
            ?: settingsObj?.optString("category")?.takeIf { it.isNotBlank() }
            ?: "Default"
        val wordlistType = settingsObj?.optString("AllowedWordlist1")?.takeIf { it.isNotBlank() }
            ?: settingsObj?.optString("wordlistType")?.takeIf { it.isNotBlank() }
            ?: "Mail:Pass"
        val needsProxies = when {
            settingsObj == null -> true
            settingsObj.has("NeedsProxies") -> settingsObj.optBoolean("NeedsProxies", true)
            settingsObj.has("needsProxies") -> settingsObj.optBoolean("needsProxies", true)
            else -> true
        }
        val lastModified = settingsObj?.optString("LastModified")?.takeIf { it.isNotBlank() }
            ?: todayString()

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

    private fun parseJsonObject(obj: JSONObject, fallbackName: String): ConfigItem {
        val cleanFallback = fallbackName
            .removeSuffix(".loli")
            .removeSuffix(".json")
            .trim()
            .ifEmpty { "Imported JSON Config" }

        val name = obj.optString("name").takeIf { it.isNotBlank() }
            ?: obj.optString("Name").takeIf { it.isNotBlank() }
            ?: cleanFallback
        val author = obj.optString("author").takeIf { it.isNotBlank() }
            ?: obj.optString("Author").takeIf { it.isNotBlank() }
            ?: "LocalUser"
        val category = obj.optString("category").takeIf { it.isNotBlank() }
            ?: obj.optString("Category").takeIf { it.isNotBlank() }
            ?: "Default"
        val wordlistType = obj.optString("wordlistType").takeIf { it.isNotBlank() }
            ?: obj.optString("AllowedWordlist1").takeIf { it.isNotBlank() }
            ?: "Mail:Pass"
        val needsProxies = when {
            obj.has("needsProxies") -> obj.optBoolean("needsProxies", true)
            obj.has("NeedsProxies") -> obj.optBoolean("NeedsProxies", true)
            else -> true
        }
        val lastModified = obj.optString("lastModified").takeIf { it.isNotBlank() }
            ?: obj.optString("LastModified").takeIf { it.isNotBlank() }
            ?: todayString()
        val script = obj.optString("script").takeIf { it.isNotBlank() }
            ?: obj.optString("scriptPreview").takeIf { it.isNotBlank() }
            ?: obj.optString("Script").takeIf { it.isNotBlank() }
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

    private fun todayString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
