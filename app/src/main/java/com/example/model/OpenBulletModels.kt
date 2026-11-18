package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.ObColors

enum class MainTab(val title: String) {
    RUNNER("Runner"),
    PROXIES("Proxies"),
    WORDLISTS("Wordlists"),
    CONFIGS("Configs"),
    HITS_DB("Hits DB"),
    TOOLS("Tools"),
    SETTINGS("Settings"),
    ABOUT("About")
}

enum class ProxyMode(val label: String) {
    DEF("DEF"),
    ON("ON"),
    OFF("OFF")
}

enum class ResultTab(val label: String) {
    HITS("Hits"),
    CUSTOM("Custom"),
    TO_CHECK("ToCheck")
}

enum class ProxyProtocol(val label: String) {
    HTTP("HTTP"),
    SOCKS4("SOCKS4"),
    SOCKS5("SOCKS5")
}

enum class ProxyStatus(val label: String, val color: Color) {
    UNTESTED("UNTESTED", ObColors.TextSecondary),
    AVAILABLE("ALIVE", ObColors.StatusHitGreen),
    BANNED("BANNED", ObColors.StatusBannedPurple),
    BAD("BAD", ObColors.StatusBadRed)
}

enum class SettingsSection(val label: String) {
    GENERAL("General"),
    RURILIB("RuriLib"),
    SOUNDS("Sounds"),
    THEMES("Themes")
}

enum class ToolsSection(val label: String) {
    CLEANER("List Cleaner"),
    FORMATTER("Combo Formatter"),
    REGEX_HASH("Regex & Hash"),
    DIAGNOSTICS("Native & Net")
}

data class WorkerRow(
    val id: Int,
    val data: String,
    val proxy: String,
    val status: String,
    val statusColor: Color = ObColors.TextPrimary
)

data class ResultRow(
    val time: String,
    val data: String,
    val proxy: String,
    val capturedData: String,
    val category: ResultTab
)

data class ConfigItem(
    val id: String,
    val name: String,
    val author: String,
    val category: String,
    val wordlistType: String,
    val needsProxies: Boolean,
    val lastModified: String,
    val scriptPreview: String
)

data class WordlistItem(
    val id: String,
    val name: String,
    val path: String,
    val type: String,
    val lines: List<String>,
    val purpose: String
) {
    val lineCount: Int get() = lines.size
}

data class ProxyItem(
    val id: String,
    val host: String,
    val port: Int,
    val protocol: ProxyProtocol,
    val pingMs: Int?,
    val country: String,
    val status: ProxyStatus
)

data class HitDbItem(
    val id: String,
    val date: String,
    val data: String,
    val type: ResultTab,
    val configName: String,
    val wordlistName: String,
    val capturedData: String
)

data class RunnerStats(
    val dataTotal: Int = 0,
    val dataHits: Int = 0,
    val dataCustom: Int = 0,
    val dataBad: Int = 0,
    val dataRetries: Int = 0,
    val dataToCheck: Int = 0,
    val proxiesTotal: Int = 0,
    val proxiesAlive: Int = 0,
    val proxiesBanned: Int = 0,
    val proxiesBad: Int = 0,
    val cpm: Int = 0,
    val credit: String = "$0"
)

data class SettingsState(
    val activeSection: SettingsSection = SettingsSection.GENERAL,
    val requestTimeoutSec: Int = 10,
    val maxRetries: Int = 3,
    val autoSaveHits: Boolean = true,
    val ignoreEmptyLines: Boolean = true,
    val showTimestampsInConsole: Boolean = false,
    val soundOnHit: Boolean = true,
    val soundOnFinish: Boolean = false,
    val keepAwakeEnabled: Boolean = true,
    val proxyJudgeUrl: String = "https://httpbin.org/ip",
    val accentHex: Long = 0xFF2F94E8
)

enum class ConfigEditorSubTab(val label: String) {
    STACKER("Stacker"),
    SCRIPT("LoliScript"),
    DEBUGGER("Debugger")
}

enum class ParseMode(val label: String) {
    LR("LR"),
    REGEX("REGEX"),
    JSON("JSON")
}

enum class ScriptFunctionType(val label: String, val loliKeyword: String) {
    SHA256("SHA-256", "SHA256"),
    MD5("MD5", "MD5"),
    BASE64_ENCODE("Base64 Encode", "BASE64ENCODE"),
    BASE64_DECODE("Base64 Decode", "BASE64DECODE"),
    URL_ENCODE("URL Encode", "URLENCODE"),
    URL_DECODE("URL Decode", "URLDECODE"),
    UPPERCASE("Uppercase", "UPPERCASE"),
    LOWERCASE("Lowercase", "LOWERCASE")
}

sealed interface ConfigBlock {
    val id: String

    data class RequestBlock(
        override val id: String,
        val method: String = "GET",
        val url: String = "https://httpbin.org/get?user=<USER>",
        val headersText: String = "Accept: application/json",
        val contentType: String = "application/x-www-form-urlencoded",
        val postBody: String = "",
        val followRedirects: Boolean = true
    ) : ConfigBlock

    data class ParseBlock(
        override val id: String,
        val sourceVar: String = "<SOURCE>",
        val mode: ParseMode = ParseMode.LR,
        val leftOrPattern: String = "\"origin\": \"",
        val right: String = "\"",
        val outputName: String = "Origin",
        val isCapture: Boolean = true
    ) : ConfigBlock

    data class KeyCheckBlock(
        override val id: String,
        val successKeys: List<String> = listOf("HTTP/200"),
        val customKeys: List<String> = emptyList(),
        val failureKeys: List<String> = listOf("Unauthorized", "Invalid"),
        val retryKeys: List<String> = listOf("HTTP/429", "Too Many Requests", "Cloudflare")
    ) : ConfigBlock

    data class FunctionBlock(
        override val id: String,
        val functionType: ScriptFunctionType = ScriptFunctionType.BASE64_ENCODE,
        val inputTemplate: String = "<USER>:<PASS>",
        val outputVar: String = "AUTH_B64"
    ) : ConfigBlock
}

data class ConfigDebugResult(
    val outcomeType: String,
    val statusLabel: String,
    val httpCode: Int,
    val latencyMs: Long,
    val proxyUsed: String,
    val captures: Map<String, String>,
    val variables: Map<String, String>,
    val traceLogs: List<String>,
    val responseBodyPreview: String
)

data class ConfigEditorState(
    val configId: String,
    val isNew: Boolean = true,
    val name: String = "",
    val author: String = "LocalUser",
    val category: String = "Default",
    val wordlistType: String = "Mail:Pass",
    val needsProxies: Boolean = true,
    val activeSubTab: ConfigEditorSubTab = ConfigEditorSubTab.STACKER,
    val blocks: List<ConfigBlock> = emptyList(),
    val rawScript: String = "",
    val testDataInput: String = "alice@example.com:secret123",
    val debugUseProxy: Boolean = false,
    val isDebugging: Boolean = false,
    val debugResult: ConfigDebugResult? = null
)

data class ToolsState(
    val activeSection: ToolsSection = ToolsSection.CLEANER,
    val cleanerInput: String = "",
    val cleanerOutput: String = "",
    val cleanerRemoveDuplicates: Boolean = true,
    val cleanerTrimWhitespace: Boolean = true,
    val cleanerRemoveEmpty: Boolean = true,
    val formatterInput: String = "",
    val formatterSeparator: String = ":",
    val formatterOutput: String = "",
    val regexPattern: String = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
    val regexTestInput: String = "",
    val regexMatches: List<String> = emptyList(),
    val hashInput: String = "",
    val base64Output: String = "",
    val sha256Output: String = "",
    val probeUrl: String = "https://httpbin.org/get",
    val probeResult: String = "",
    val isProbing: Boolean = false,
    val nativeSampleBody: String = "",
    val nativeSuccessKey: String = "HTTP/200",
    val nativeFailureKey: String = "Unauthorized",
    val nativeScanSummary: String = ""
)

sealed interface ActiveDialog {
    data object None : ActiveDialog
    data object SelectConfig : ActiveDialog
    data object SelectWordlist : ActiveDialog
    data object AddProxies : ActiveDialog
    data object AddWordlist : ActiveDialog
    data object AddConfig : ActiveDialog
    data object ImportConfig : ActiveDialog
    data object EditStartLine : ActiveDialog
    data object EditBotsCount : ActiveDialog
}

data class OpenBulletUiState(
    val activeTab: MainTab = MainTab.RUNNER,
    val tabHistory: List<MainTab> = emptyList(),
    val startLine: Int = 1,
    val botsCount: Int = 1,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val proxyMode: ProxyMode = ProxyMode.DEF,
    val isRunning: Boolean = false,
    val isCheckingProxies: Boolean = false,
    val proxyCheckStatusText: String = "",
    val elapsedSeconds: Long = 0L,
    val selectedResultTab: ResultTab = ResultTab.HITS,
    val selectedConfig: ConfigItem? = null,
    val selectedWordlist: WordlistItem? = null,
    val workerRows: List<WorkerRow> = emptyList(),
    val resultRows: List<ResultRow> = emptyList(),
    val consoleLogs: List<String> = listOf("Runner initialized succesfully!"),
    val stats: RunnerStats = RunnerStats(),
    val configs: List<ConfigItem> = emptyList(),
    val wordlists: List<WordlistItem> = emptyList(),
    val proxies: List<ProxyItem> = emptyList(),
    val hitsDb: List<HitDbItem> = emptyList(),
    val proxyFilter: ProxyProtocol? = null,
    val hitsSearchQuery: String = "",
    val hitsTypeFilter: ResultTab? = null,
    val selectedConfigPreviewId: String? = null,
    val configEditor: ConfigEditorState? = null,
    val settings: SettingsState = SettingsState(),
    val tools: ToolsState = ToolsState(),
    val nativeEngineBanner: String = "",
    val activeDialog: ActiveDialog = ActiveDialog.None,
    val statusBannerMessage: String? = null
)
