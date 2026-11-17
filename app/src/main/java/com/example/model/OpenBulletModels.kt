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
    REGEX_HASH("Regex & Hash")
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
    val accentHex: Long = 0xFF2F94E8
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
    val sha256Output: String = ""
)

sealed interface ActiveDialog {
    data object None : ActiveDialog
    data object SelectConfig : ActiveDialog
    data object SelectWordlist : ActiveDialog
    data object AddProxies : ActiveDialog
    data object AddWordlist : ActiveDialog
    data object AddConfig : ActiveDialog
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
    val settings: SettingsState = SettingsState(),
    val tools: ToolsState = ToolsState(),
    val activeDialog: ActiveDialog = ActiveDialog.None,
    val statusBannerMessage: String? = null
)
