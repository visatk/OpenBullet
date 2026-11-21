package com.example.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.OpenBulletRepository
import com.example.di.DispatcherProvider
import com.example.domain.usecase.DebugConfigScriptUseCase
import com.example.domain.usecase.ExportFormatterUseCase
import com.example.domain.usecase.ImportConfigsUseCase
import com.example.model.ConfigBlock
import com.example.model.ConfigEditorSubTab
import com.example.model.ConfigItem
import com.example.model.ConfigsUiState
import com.example.model.ProxyItem
import com.example.model.ProxyStatus
import com.example.network.ConfigExportFormat
import com.example.network.ConfigTemplates
import com.example.util.DateTimeFormats
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConfigsViewModel(
    private val repository: OpenBulletRepository,
    private val exportFormatterUseCase: ExportFormatterUseCase,
    private val dispatchers: DispatcherProvider,
    private val importConfigsUseCase: ImportConfigsUseCase = ImportConfigsUseCase(repository),
    private val debugConfigScriptUseCase: DebugConfigScriptUseCase = DebugConfigScriptUseCase(dispatchers),
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ConfigsUiState(
            searchQuery = savedStateHandle.get<String>(KEY_SEARCH_QUERY).orEmpty(),
            categoryFilter = savedStateHandle.get<String>(KEY_CATEGORY_FILTER)
        )
    )
    val uiState: StateFlow<ConfigsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var currentProxies: List<ProxyItem> = emptyList()
    private var requestTimeoutSec: Int = 10

    init {
        viewModelScope.launch {
            combine(
                repository.configsFlow,
                repository.proxiesFlow,
                repository.settingsFlow
            ) { configs, proxies, settings ->
                Triple(configs, proxies, settings)
            }.collect { (configs, proxies, settings) ->
                currentProxies = proxies
                val usable = proxies.count {
                    it.status == ProxyStatus.AVAILABLE || it.status == ProxyStatus.UNTESTED
                }
                if (settings != null) {
                    requestTimeoutSec = settings.requestTimeoutSec
                }
                _uiState.update { current ->
                    val updatedPreviewId = current.selectedPreviewId?.takeIf { id ->
                        configs.any { it.id == id }
                    } ?: configs.firstOrNull()?.id
                    current.copy(
                        configs = configs,
                        selectedPreviewId = updatedPreviewId,
                        usableProxyCount = usable,
                        activeRunnerConfigId = if (settings != null) {
                            settings.selectedConfigId
                        } else {
                            current.activeRunnerConfigId
                        }
                    ).withDerivedConfigs(configs = configs)
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        savedStateHandle[KEY_SEARCH_QUERY] = query
        _uiState.update { it.withDerivedConfigs(searchQuery = query) }
    }

    fun setCategoryFilter(category: String?) {
        savedStateHandle[KEY_CATEGORY_FILTER] = category
        _uiState.update { it.withDerivedConfigs(categoryFilter = category, updateCategoryFilter = true) }
    }

    fun selectPreview(id: String) {
        _uiState.update { it.copy(selectedPreviewId = id) }
    }

    private fun ConfigsUiState.withDerivedConfigs(
        configs: List<ConfigItem> = this.configs,
        searchQuery: String = this.searchQuery,
        categoryFilter: String? = this.categoryFilter,
        updateCategoryFilter: Boolean = false
    ): ConfigsUiState {
        val resolvedCategory = if (updateCategoryFilter) categoryFilter else this.categoryFilter
        val categories = configs.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
        val q = searchQuery.trim()
        val filtered = configs.filter { cfg ->
            val matchCat = resolvedCategory == null || cfg.category.equals(resolvedCategory, ignoreCase = true)
            val matchQuery = q.isEmpty() ||
                cfg.name.contains(q, ignoreCase = true) ||
                cfg.author.contains(q, ignoreCase = true) ||
                cfg.category.contains(q, ignoreCase = true)
            matchCat && matchQuery
        }
        return copy(
            configs = configs,
            filteredConfigs = filtered,
            availableCategories = categories,
            searchQuery = searchQuery,
            categoryFilter = resolvedCategory
        )
    }

    fun useInRunner(config: ConfigItem) {
        _uiState.update { it.copy(activeRunnerConfigId = config.id) }
        val suggestedDoP = config.suggestedBots.coerceIn(1, 200)
        viewModelScope.launch {
            repository.saveSettings {
                it.copy(
                    selectedConfigId = config.id,
                    botsCount = suggestedDoP
                )
            }
        }
        _events.tryEmit(
            UiEvent.ShowSnackbar("Loaded '${config.name}' (v${config.version}, DoP=$suggestedDoP) into Runner")
        )
    }

    fun addConfig(
        name: String,
        author: String,
        category: String,
        wordlistType: String,
        needsProxies: Boolean,
        scriptPreview: String
    ) {
        val cleanName = name.trim().ifEmpty { "Untitled Config" }
        val config = ConfigItem(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            author = author.trim().ifEmpty { "LocalUser" },
            category = category.trim().ifEmpty { "Default" },
            wordlistType = wordlistType.trim().ifEmpty { "Default" },
            needsProxies = needsProxies,
            lastModified = DateTimeFormats.formatDateNow(),
            scriptPreview = scriptPreview.ifBlank { ConfigTemplates.presets.first().script }
        )
        viewModelScope.launch { repository.saveConfig(config) }
        _uiState.update { it.copy(selectedPreviewId = config.id) }
        _events.tryEmit(UiEvent.ShowSnackbar("Saved config '$cleanName'"))
    }

    fun duplicateConfig(config: ConfigItem) {
        val copy = config.copy(
            id = UUID.randomUUID().toString(),
            name = "${config.name} (Copy)",
            lastModified = DateTimeFormats.formatDateNow()
        )
        viewModelScope.launch { repository.saveConfig(copy) }
        _uiState.update { it.copy(selectedPreviewId = copy.id) }
        _events.tryEmit(UiEvent.ShowSnackbar("Duplicated '${config.name}'"))
    }

    fun deleteConfig(id: String) {
        viewModelScope.launch {
            repository.deleteConfig(id)
            if (_uiState.value.activeRunnerConfigId == id) {
                repository.saveSettings { it.copy(selectedConfigId = null) }
            }
        }
    }

    fun importConfigPayload(
        rawText: String,
        fallbackName: String = "Imported Config",
        openInEditorAfterImport: Boolean = false
    ) {
        viewModelScope.launch {
            val parsedList = importConfigsUseCase(rawPayload = rawText, fallbackName = fallbackName)
            if (parsedList.isEmpty()) {
                _events.tryEmit(UiEvent.ShowSnackbar("Nothing to import: empty or invalid payload"))
                return@launch
            }
            val firstCfg = parsedList.first()
            _uiState.update { current ->
                current.copy(
                    selectedPreviewId = firstCfg.id,
                    editor = if (openInEditorAfterImport) {
                        ConfigEditorHelper.createInitialState(firstCfg)
                    } else {
                        current.editor
                    }
                )
            }
            _events.tryEmit(UiEvent.ShowSnackbar("Imported ${parsedList.size} config(s) (${firstCfg.name})"))
        }
    }

    fun importConfigFromUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        val fallbackName = trimmed.substringAfterLast('/').ifBlank { "Remote Config" }
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingFromUrl = true) }
            _events.tryEmit(UiEvent.ShowSnackbar("Fetching config from URL..."))
            repository.fetchRemoteText(trimmed)
                .onSuccess { text ->
                    _uiState.update { it.copy(isImportingFromUrl = false) }
                    importConfigPayload(text, fallbackName = fallbackName, openInEditorAfterImport = false)
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isImportingFromUrl = false) }
                    _events.tryEmit(
                        UiEvent.ShowSnackbar("Config fetch failed: ${err.message ?: "Network error"}")
                    )
                }
        }
    }

    fun exportSingleConfig(config: ConfigItem, format: ConfigExportFormat) {
        val content = exportFormatterUseCase.formatConfig(config, format)
        val safeFileName = config.name.trim().ifEmpty { "config" }
            .replace(Regex("""[^a-zA-Z0-9._-]"""), "_") + ".${format.extension}"
        val mime = if (format == ConfigExportFormat.JSON) "application/json" else "text/plain"
        _events.tryEmit(
            UiEvent.ShareFile(
                fileName = safeFileName,
                content = content,
                mimeType = mime,
                alsoCopyToClipboard = true
            )
        )
        _events.tryEmit(UiEvent.ShowSnackbar("Exported & copied $safeFileName"))
    }

    fun exportAllConfigsJson() {
        val all = _uiState.value.configs
        if (all.isEmpty()) {
            _events.tryEmit(UiEvent.ShowSnackbar("No configs to export"))
            return
        }
        val content = exportFormatterUseCase.formatConfigListJson(all)
        _events.tryEmit(
            UiEvent.ShareFile(
                fileName = "openbullet_configs_backup.json",
                content = content,
                mimeType = "application/json",
                alsoCopyToClipboard = true
            )
        )
        _events.tryEmit(UiEvent.ShowSnackbar("Exported ${all.size} configs (.json)"))
    }

    // --- Config Editor / Maker ---
    fun openEditor(existing: ConfigItem? = null) {
        _uiState.update {
            it.copy(editor = ConfigEditorHelper.createInitialState(existing))
        }
    }

    fun closeEditor() {
        _uiState.update { it.copy(editor = null) }
    }

    fun applyPresetToEditor(preset: ConfigTemplates.TemplatePreset) {
        _uiState.update { current ->
            val ed = current.editor ?: return@update current
            current.copy(editor = ConfigEditorHelper.applyPreset(ed, preset).copy(hasUnsavedChanges = true))
        }
        _events.tryEmit(UiEvent.ShowSnackbar("Loaded template: ${preset.title}"))
    }

    fun updateEditorMetadata(
        name: String? = null,
        author: String? = null,
        category: String? = null,
        wordlistType: String? = null,
        needsProxies: Boolean? = null,
        version: String? = null,
        description: String? = null
    ) {
        _uiState.update { current ->
            val ed = current.editor ?: return@update current
            current.copy(
                editor = ed.copy(
                    name = name ?: ed.name,
                    author = author ?: ed.author,
                    category = category ?: ed.category,
                    wordlistType = wordlistType ?: ed.wordlistType,
                    needsProxies = needsProxies ?: ed.needsProxies,
                    version = version ?: ed.version,
                    description = description ?: ed.description,
                    hasUnsavedChanges = true
                )
            )
        }
    }

    fun updateEditorExecutionSettings(
        suggestedBots: Int? = null,
        maxRetries: Int? = null,
        timeoutSec: Int? = null,
        customUserAgent: String? = null,
        maxProxyUses: Int? = null
    ) {
        _uiState.update { current ->
            val ed = current.editor ?: return@update current
            current.copy(
                editor = ed.copy(
                    suggestedBots = (suggestedBots ?: ed.suggestedBots).coerceIn(1, 200),
                    maxRetries = (maxRetries ?: ed.maxRetries).coerceIn(0, 15),
                    timeoutSec = (timeoutSec ?: ed.timeoutSec).coerceIn(1, 120),
                    customUserAgent = customUserAgent ?: ed.customUserAgent,
                    maxProxyUses = (maxProxyUses ?: ed.maxProxyUses).coerceAtLeast(0),
                    hasUnsavedChanges = true
                )
            )
        }
    }

    fun selectEditorSubTab(subTab: ConfigEditorSubTab) {
        _uiState.update { current ->
            val ed = current.editor ?: return@update current
            current.copy(editor = ConfigEditorHelper.switchSubTab(ed, subTab))
        }
    }

    fun updateEditorRawScript(script: String) {
        _uiState.update { current ->
            val ed = current.editor ?: return@update current
            current.copy(editor = ed.copy(rawScript = script, hasUnsavedChanges = true))
        }
    }

    fun addBlockToEditor(kind: String) {
        _uiState.update { current ->
            val ed = current.editor ?: return@update current
            val newBlock = ConfigEditorHelper.createDefaultBlock(kind)
            current.copy(editor = ed.copy(blocks = ed.blocks + newBlock, hasUnsavedChanges = true))
        }
    }

    fun updateBlockInEditor(index: Int, updated: ConfigBlock) {
        _uiState.update { current ->
            val ed = current.editor ?: return@update current
            if (index !in ed.blocks.indices) return@update current
            val nextBlocks = ed.blocks.toMutableList().apply { set(index, updated) }
            current.copy(editor = ed.copy(blocks = nextBlocks, hasUnsavedChanges = true))
        }
    }

    fun moveBlockInEditor(fromIndex: Int, toIndex: Int) {
        _uiState.update { current ->
            val ed = current.editor ?: return@update current
            if (fromIndex !in ed.blocks.indices || toIndex !in ed.blocks.indices) return@update current
            val list = ed.blocks.toMutableList()
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            current.copy(editor = ed.copy(blocks = list, hasUnsavedChanges = true))
        }
    }

    fun deleteBlockFromEditor(index: Int) {
        _uiState.update { current ->
            val ed = current.editor ?: return@update current
            if (index !in ed.blocks.indices) return@update current
            val list = ed.blocks.toMutableList().apply { removeAt(index) }
            current.copy(editor = ed.copy(blocks = list, hasUnsavedChanges = true))
        }
    }

    fun updateEditorDebugInputs(testData: String? = null, useProxy: Boolean? = null) {
        _uiState.update { current ->
            val ed = current.editor ?: return@update current
            current.copy(
                editor = ed.copy(
                    testDataInput = testData ?: ed.testDataInput,
                    debugUseProxy = useProxy ?: ed.debugUseProxy
                )
            )
        }
    }

    fun runConfigDebug() {
        val ed = _uiState.value.editor ?: return
        val synced = ConfigEditorHelper.switchSubTab(ed, ConfigEditorSubTab.DEBUGGER)
        val scriptText = ConfigEditorHelper.syncScriptForSave(ed)

        _uiState.update {
            it.copy(editor = synced.copy(isDebugging = true, debugResult = null))
        }

        viewModelScope.launch {
            val effectiveTimeout = if (ed.timeoutSec > 0) ed.timeoutSec else requestTimeoutSec
            val result = debugConfigScriptUseCase(
                rawTestInput = ed.testDataInput,
                scriptText = scriptText,
                timeoutSec = effectiveTimeout,
                useProxy = ed.debugUseProxy,
                availableProxies = currentProxies,
                customUserAgent = ed.customUserAgent
            )
            _uiState.update { cur ->
                val curEd = cur.editor ?: return@update cur
                cur.copy(editor = curEd.copy(isDebugging = false, debugResult = result))
            }
        }
    }

    fun exportActiveEditorLoli() {
        val ed = _uiState.value.editor ?: return
        val finalScript = ConfigEditorHelper.syncScriptForSave(ed)
        val cleanName = ed.name.trim().ifEmpty { "Untitled_Config" }
        val tempConfig = ConfigItem(
            id = ed.configId,
            name = cleanName,
            author = ed.author.trim().ifEmpty { "LocalUser" },
            category = ed.category.trim().ifEmpty { "Default" },
            wordlistType = ed.wordlistType.trim().ifEmpty { "Default" },
            needsProxies = ed.needsProxies,
            lastModified = DateTimeFormats.formatDateNow(),
            scriptPreview = finalScript,
            version = ed.version.trim().ifEmpty { "1.0.0" },
            description = ed.description.trim(),
            suggestedBots = ed.suggestedBots.coerceIn(1, 200),
            maxRetries = ed.maxRetries.coerceIn(0, 15),
            timeoutSec = ed.timeoutSec.coerceIn(1, 120),
            customUserAgent = ed.customUserAgent.trim(),
            maxProxyUses = ed.maxProxyUses.coerceAtLeast(0)
        )
        exportSingleConfig(tempConfig, ConfigExportFormat.LOLI)
    }

    fun saveEditedConfig(loadInRunner: Boolean): Boolean {
        val ed = _uiState.value.editor ?: return false
        val finalScript = ConfigEditorHelper.syncScriptForSave(ed)
        val cleanName = ed.name.trim().ifEmpty { "Untitled Config" }
        val config = ConfigItem(
            id = ed.configId,
            name = cleanName,
            author = ed.author.trim().ifEmpty { "LocalUser" },
            category = ed.category.trim().ifEmpty { "Default" },
            wordlistType = ed.wordlistType.trim().ifEmpty { "Default" },
            needsProxies = ed.needsProxies,
            lastModified = DateTimeFormats.formatDateNow(),
            scriptPreview = finalScript,
            version = ed.version.trim().ifEmpty { "1.0.0" },
            description = ed.description.trim(),
            suggestedBots = ed.suggestedBots.coerceIn(1, 200),
            maxRetries = ed.maxRetries.coerceIn(0, 15),
            timeoutSec = ed.timeoutSec.coerceIn(1, 120),
            customUserAgent = ed.customUserAgent.trim(),
            maxProxyUses = ed.maxProxyUses.coerceAtLeast(0)
        )

        viewModelScope.launch {
            repository.saveConfig(config)
            if (loadInRunner) {
                repository.saveSettings {
                    it.copy(
                        selectedConfigId = config.id,
                        botsCount = config.suggestedBots.coerceIn(1, 200)
                    )
                }
            }
        }

        _uiState.update { current ->
            current.copy(
                selectedPreviewId = config.id,
                activeRunnerConfigId = if (loadInRunner) config.id else current.activeRunnerConfigId,
                editor = null
            )
        }
        _events.tryEmit(
            UiEvent.ShowSnackbar(
                if (loadInRunner) "Saved & loaded '$cleanName' (DoP=${config.suggestedBots})" else "Saved config '$cleanName'"
            )
        )
        return loadInRunner
    }

    companion object {
        private const val KEY_SEARCH_QUERY = "configs_search_query"
        private const val KEY_CATEGORY_FILTER = "configs_category_filter"
    }
}
