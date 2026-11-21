package com.example.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.OpenBulletRepository
import com.example.di.DispatcherProvider
import com.example.domain.usecase.CleanWordlistUseCase
import com.example.domain.usecase.CreateWordlistUseCase
import com.example.domain.usecase.CryptoAndRegexUseCase
import com.example.domain.usecase.FormatCombosUseCase
import com.example.domain.usecase.RunNativeDiagnosticUseCase
import com.example.model.ToolsSection
import com.example.model.ToolsState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ToolsViewModel(
    private val repository: OpenBulletRepository,
    private val cleanWordlistUseCase: CleanWordlistUseCase,
    private val formatCombosUseCase: FormatCombosUseCase,
    private val cryptoAndRegexUseCase: CryptoAndRegexUseCase,
    private val dispatchers: DispatcherProvider,
    private val runNativeDiagnosticUseCase: RunNativeDiagnosticUseCase = RunNativeDiagnosticUseCase(dispatchers),
    private val createWordlistUseCase: CreateWordlistUseCase = CreateWordlistUseCase(repository),
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val initialSection = savedStateHandle.get<String>(KEY_ACTIVE_SECTION)
        ?.let { name -> runCatching { ToolsSection.valueOf(name) }.getOrNull() }
        ?: ToolsSection.CLEANER

    private val _uiState = MutableStateFlow(ToolsState(activeSection = initialSection))
    val uiState: StateFlow<ToolsState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var regexJob: Job? = null
    private var cryptoJob: Job? = null

    fun selectSection(section: ToolsSection) {
        savedStateHandle[KEY_ACTIVE_SECTION] = section.name
        _uiState.update { it.copy(activeSection = section) }
    }

    fun updateCleanerInput(text: String) {
        _uiState.update { it.copy(cleanerInput = text) }
    }

    fun toggleCleanerDuplicates(enabled: Boolean) {
        _uiState.update { it.copy(cleanerRemoveDuplicates = enabled) }
    }

    fun toggleCleanerTrim(enabled: Boolean) {
        _uiState.update { it.copy(cleanerTrimWhitespace = enabled) }
    }

    fun toggleCleanerEmpty(enabled: Boolean) {
        _uiState.update { it.copy(cleanerRemoveEmpty = enabled) }
    }

    fun runListCleaner() {
        val current = _uiState.value
        viewModelScope.launch {
            val result = withContext(dispatchers.default) {
                cleanWordlistUseCase(
                    rawInput = current.cleanerInput,
                    trimWhitespace = current.cleanerTrimWhitespace,
                    removeEmpty = current.cleanerRemoveEmpty,
                    removeDuplicates = current.cleanerRemoveDuplicates
                )
            }
            val statsText = "${result.inputCount} in -> ${result.outputCount} out (${result.removedCount} removed)"
            _uiState.update {
                it.copy(cleanerOutput = result.output, cleanerStats = statsText)
            }
        }
    }

    fun saveCleanedAsWordlist() {
        val output = _uiState.value.cleanerOutput
        if (output.isBlank()) return
        saveGeneratedWordlist(prefix = "cleaned", purpose = "Tools Cleaner", rawText = output)
    }

    fun updateFormatterInput(text: String) {
        _uiState.update { it.copy(formatterInput = text) }
    }

    fun updateFormatterSeparator(sep: String) {
        _uiState.update { it.copy(formatterSeparator = sep) }
    }

    fun runComboFormatter(swapOrder: Boolean) {
        val current = _uiState.value
        viewModelScope.launch {
            val result = withContext(dispatchers.default) {
                formatCombosUseCase(
                    rawInput = current.formatterInput,
                    targetSeparator = current.formatterSeparator,
                    swapOrder = swapOrder
                )
            }
            val statsText = "${result.formattedCount} formatted (${result.skippedCount} skipped)"
            _uiState.update {
                it.copy(formatterOutput = result.output, formatterStats = statsText)
            }
        }
    }

    fun saveFormattedAsWordlist() {
        val output = _uiState.value.formatterOutput
        if (output.isBlank()) return
        saveGeneratedWordlist(prefix = "formatted", purpose = "Combo Formatter", rawText = output)
    }

    private fun saveGeneratedWordlist(prefix: String, purpose: String, rawText: String) {
        val name = "${prefix}_${System.currentTimeMillis() % 10000}.txt"
        viewModelScope.launch {
            val saved = createWordlistUseCase(
                rawText = rawText,
                name = name,
                type = "Default",
                purpose = purpose,
                ignoreEmptyLines = true,
                requireNonEmpty = true
            ) ?: return@launch
            _events.tryEmit(
                UiEvent.ShowSnackbar("Saved '${saved.name}' (${saved.lineCount} lines) to Wordlists")
            )
        }
    }

    fun updateRegexInputs(pattern: String? = null, testInput: String? = null) {
        val cur = _uiState.value
        val nextPattern = pattern ?: cur.regexPattern
        val nextInput = testInput ?: cur.regexTestInput
        _uiState.update { it.copy(regexPattern = nextPattern, regexTestInput = nextInput) }

        regexJob?.cancel()
        regexJob = viewModelScope.launch {
            val res = withContext(dispatchers.default) {
                cryptoAndRegexUseCase.extractRegex(nextPattern, nextInput)
            }
            _uiState.update {
                it.copy(regexMatches = res.matches, regexError = res.error)
            }
        }
    }

    fun updateHashInput(text: String) {
        _uiState.update { it.copy(hashInput = text) }
        cryptoJob?.cancel()
        cryptoJob = viewModelScope.launch {
            val out = withContext(dispatchers.default) {
                cryptoAndRegexUseCase.computeCrypto(text)
            }
            _uiState.update {
                it.copy(
                    base64Output = out.base64,
                    md5Output = out.md5,
                    sha256Output = out.sha256,
                    urlEncodedOutput = out.urlEncoded,
                    urlDecodedOutput = out.urlDecoded
                )
            }
        }
    }

    fun updateProbeUrl(url: String) {
        _uiState.update { it.copy(probeUrl = url) }
    }

    fun runNetworkProbe() {
        val url = _uiState.value.probeUrl.trim()
        if (url.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProbing = true, probeResult = "Probing $url...") }
            repository.runHttpProbe(url)
                .onSuccess { res ->
                    _uiState.update { it.copy(isProbing = false, probeResult = res) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isProbing = false,
                            probeResult = "Error: ${err.message ?: err.javaClass.simpleName}"
                        )
                    }
                }
        }
    }

    fun updateNativeDiagnosticFields(
        sampleBody: String? = null,
        successKey: String? = null,
        failureKey: String? = null
    ) {
        _uiState.update { cur ->
            cur.copy(
                nativeSampleBody = sampleBody ?: cur.nativeSampleBody,
                nativeSuccessKey = successKey ?: cur.nativeSuccessKey,
                nativeFailureKey = failureKey ?: cur.nativeFailureKey
            )
        }
    }

    fun runNativeScannerTest() {
        val current = _uiState.value
        viewModelScope.launch {
            val summary = runNativeDiagnosticUseCase(
                sampleBody = current.nativeSampleBody,
                successKey = current.nativeSuccessKey,
                failureKey = current.nativeFailureKey
            )
            _uiState.update { it.copy(nativeScanSummary = summary) }
        }
    }

    fun copyText(label: String, text: String) {
        if (text.isBlank()) return
        _events.tryEmit(UiEvent.CopyText(label = label, text = text, feedbackMessage = "Copied $label"))
    }

    companion object {
        private const val KEY_ACTIVE_SECTION = "tools_active_section"
    }
}
