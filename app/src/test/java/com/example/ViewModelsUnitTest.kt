package com.example

import androidx.lifecycle.SavedStateHandle
import com.example.data.OpenBulletRepository
import com.example.di.DispatcherProvider
import com.example.domain.usecase.CleanWordlistUseCase
import com.example.domain.usecase.CryptoAndRegexUseCase
import com.example.domain.usecase.ExportFormatterUseCase
import com.example.domain.usecase.FormatCombosUseCase
import com.example.domain.usecase.ParseProxiesUseCase
import com.example.model.AppThemeMode
import com.example.model.ConfigItem
import com.example.model.HitDbItem
import com.example.model.MainTab
import com.example.model.PersistedSettingsSnapshot
import com.example.model.ProxyCheckWorkSnapshot
import com.example.model.ProxyItem
import com.example.model.ProxyMode
import com.example.model.ProxyProtocol
import com.example.model.RunnerSessionSnapshot
import com.example.model.RunnerWorkSnapshot
import com.example.model.WorkExecutionState
import com.example.model.WordlistItem
import com.example.util.SoundNotifier
import com.example.viewmodel.AppShellViewModel
import com.example.viewmodel.ConfigsViewModel
import com.example.viewmodel.ProxiesViewModel
import com.example.viewmodel.RunnerViewModel
import com.example.viewmodel.SettingsViewModel
import com.example.viewmodel.ToolsViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsUnitTest {

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    private class TestDispatcherProvider(
        private val dispatcher: CoroutineDispatcher
    ) : DispatcherProvider {
        override val io: CoroutineDispatcher get() = dispatcher
        override val default: CoroutineDispatcher get() = dispatcher
        override val main: CoroutineDispatcher get() = dispatcher
    }

    private class FakeSoundNotifier : SoundNotifier {
        var hitTonesPlayed = 0
        var finishTonesPlayed = 0
        override fun playAlertTone(isHit: Boolean) {
            if (isHit) hitTonesPlayed++ else finishTonesPlayed++
        }
        override fun release() = Unit
    }

    private class FakeOpenBulletRepository : OpenBulletRepository {
        val configsState = MutableStateFlow<List<ConfigItem>>(emptyList())
        val wordlistsState = MutableStateFlow<List<WordlistItem>>(emptyList())
        val proxiesState = MutableStateFlow<List<ProxyItem>>(emptyList())
        val hitsState = MutableStateFlow<List<HitDbItem>>(emptyList())
        val settingsState = MutableStateFlow<PersistedSettingsSnapshot?>(PersistedSettingsSnapshot())
        val sessionState = MutableStateFlow<RunnerSessionSnapshot?>(null)
        val runnerWorkState = MutableStateFlow<RunnerWorkSnapshot?>(null)
        val proxyWorkState = MutableStateFlow<ProxyCheckWorkSnapshot?>(null)

        var enqueuedRunnerCount = 0
        var cancelledRunnerCount = 0

        override val configsFlow: Flow<List<ConfigItem>> = configsState
        override val wordlistsFlow: Flow<List<WordlistItem>> = wordlistsState
        override val proxiesFlow: Flow<List<ProxyItem>> = proxiesState
        override val hitsDbFlow: Flow<List<HitDbItem>> = hitsState
        override val settingsFlow: Flow<PersistedSettingsSnapshot?> = settingsState
        override val runnerSessionFlow: Flow<RunnerSessionSnapshot?> = sessionState

        override suspend fun saveConfig(config: ConfigItem) {
            configsState.value = configsState.value.filterNot { it.id == config.id } + config
        }

        override suspend fun saveConfigs(configs: List<ConfigItem>) {
            val map = configsState.value.associateBy { it.id }.toMutableMap()
            configs.forEach { map[it.id] = it }
            configsState.value = map.values.toList()
        }

        override suspend fun deleteConfig(id: String) {
            configsState.value = configsState.value.filterNot { it.id == id }
        }

        override suspend fun saveWordlist(wordlist: WordlistItem) {
            wordlistsState.value = wordlistsState.value.filterNot { it.id == wordlist.id } + wordlist
        }

        override suspend fun loadWordlistLines(wordlistId: String): List<String> =
            wordlistsState.value.firstOrNull { it.id == wordlistId }?.lines.orEmpty()

        override suspend fun deleteWordlist(id: String) {
            wordlistsState.value = wordlistsState.value.filterNot { it.id == id }
        }

        override suspend fun clearWordlists() {
            wordlistsState.value = emptyList()
        }

        override suspend fun saveProxies(proxies: List<ProxyItem>) {
            proxiesState.value = proxiesState.value + proxies
        }

        override suspend fun deleteProxy(id: String) {
            proxiesState.value = proxiesState.value.filterNot { it.id == id }
        }

        override suspend fun deleteBadAndBannedProxies() = Unit

        override suspend fun clearProxies() {
            proxiesState.value = emptyList()
        }

        override suspend fun saveHit(hit: HitDbItem) {
            hitsState.value = hitsState.value + hit
        }

        override suspend fun deleteHit(id: String) {
            hitsState.value = hitsState.value.filterNot { it.id == id }
        }

        override suspend fun clearHitsDb() {
            hitsState.value = emptyList()
        }

        override suspend fun saveSettings(update: (PersistedSettingsSnapshot) -> PersistedSettingsSnapshot) {
            val cur = settingsState.value ?: PersistedSettingsSnapshot()
            settingsState.value = update(cur)
        }

        override suspend fun clearRunnerSession() {
            sessionState.value = null
        }

        override suspend fun seedStarterWorkspace(): Pair<ConfigItem, WordlistItem> {
            val cfg = ConfigItem("cfg-seed", "Local Combo Validator", "OpenBullet", "Utility", "Mail:Pass", false, "2026-11-19", "KEYCHECK")
            val list = WordlistItem("list-seed", "starter_combos.txt", "files://list-seed.txt", "Mail:Pass", listOf("a@b.com:1", "c@d.com:2"), "Demo")
            saveConfig(cfg)
            saveWordlist(list)
            saveSettings { it.copy(selectedConfigId = cfg.id, selectedWordlistId = list.id) }
            return cfg to list
        }

        override suspend fun fetchRemoteText(url: String): Result<String> = Result.success("")
        override suspend fun runHttpProbe(url: String): Result<String> = Result.success("HTTP 200")

        override fun enqueueRunnerJob(
            configId: String,
            wordlistId: String,
            startLine: Int,
            botsCount: Int,
            proxyMode: ProxyMode,
            initialHits: Int,
            initialCustom: Int,
            initialBad: Int,
            initialRetries: Int,
            initialToCheck: Int,
            initialElapsedSec: Long
        ) {
            enqueuedRunnerCount++
        }

        override fun cancelRunnerJob() {
            cancelledRunnerCount++
        }

        override fun observeRunnerWork(): Flow<RunnerWorkSnapshot?> = runnerWorkState
        override fun enqueueProxyCheck(probeUrl: String) = Unit
        override fun cancelProxyCheck() = Unit
        override fun observeProxyCheckWork(): Flow<ProxyCheckWorkSnapshot?> = proxyWorkState
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `runnerViewModel validates missing config and loads starter sample`() = runTest {
        val repo = FakeOpenBulletRepository()
        val sound = FakeSoundNotifier()
        val vm = RunnerViewModel(repo, sound)
        advanceUntilIdle()

        vm.toggleStartStop()
        assertEquals(0, repo.enqueuedRunnerCount)
        assertFalse(vm.uiState.value.isRunning)

        vm.loadStarterSample()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.selectedConfig)
        assertNotNull(vm.uiState.value.selectedWordlist)
        assertEquals(2, vm.uiState.value.progressTotal)

        vm.toggleStartStop()
        assertEquals(1, repo.enqueuedRunnerCount)
        assertTrue(vm.uiState.value.isRunning)
    }

    @Test
    fun `runnerViewModel baselines historical hits on attach without false chime`() = runTest {
        val repo = FakeOpenBulletRepository()
        val sound = FakeSoundNotifier()
        val vm = RunnerViewModel(repo, sound)
        advanceUntilIdle()

        // Attaching to an already-running job with 3 historical hits must NOT trigger hit chime
        repo.runnerWorkState.value = RunnerWorkSnapshot(
            workId = "work-1",
            state = WorkExecutionState.RUNNING,
            progressCurrent = 5,
            progressTotal = 10,
            hits = 3
        )
        advanceUntilIdle()
        assertEquals(0, sound.hitTonesPlayed)

        // Subsequent hit increase from 3 -> 4 MUST chime once
        repo.runnerWorkState.value = RunnerWorkSnapshot(
            workId = "work-1",
            state = WorkExecutionState.RUNNING,
            progressCurrent = 6,
            progressTotal = 10,
            hits = 4
        )
        advanceUntilIdle()
        assertEquals(1, sound.hitTonesPlayed)
    }

    @Test
    fun `proxiesViewModel deduplicates imported proxies`() = runTest {
        val repo = FakeOpenBulletRepository()
        val vm = ProxiesViewModel(repo, ParseProxiesUseCase(), ExportFormatterUseCase())
        advanceUntilIdle()

        vm.addProxies("1.1.1.1:8080\n1.1.1.1:8080\n2.2.2.2:1080", ProxyProtocol.HTTP)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.proxies.size)
    }

    @Test
    fun `configsViewModel tracks dirty editor state and saves to runner`() = runTest {
        val repo = FakeOpenBulletRepository()
        val vm = ConfigsViewModel(repo, ExportFormatterUseCase(), TestDispatcherProvider(testDispatcher))
        advanceUntilIdle()

        vm.openEditor(null)
        assertFalse(vm.uiState.value.editor!!.hasUnsavedChanges)

        vm.updateEditorMetadata(name = "My API Check")
        assertTrue(vm.uiState.value.editor!!.hasUnsavedChanges)

        val loadedInRunner = vm.saveEditedConfig(loadInRunner = true)
        advanceUntilIdle()
        assertTrue(loadedInRunner)
        assertEquals("My API Check", repo.configsState.value.first().name)
        assertEquals(repo.configsState.value.first().id, repo.settingsState.value?.selectedConfigId)
    }

    @Test
    fun `toolsViewModel and settingsViewModel update states cleanly`() = runTest {
        val repo = FakeOpenBulletRepository()
        val sound = FakeSoundNotifier()
        val dispatchers = TestDispatcherProvider(testDispatcher)
        val toolsVm = ToolsViewModel(repo, CleanWordlistUseCase(), FormatCombosUseCase(), CryptoAndRegexUseCase(), dispatchers)
        val settingsVm = SettingsViewModel(repo, sound)

        toolsVm.updateCleanerInput("a:b\na:b\n")
        toolsVm.runListCleaner()
        advanceUntilIdle()
        assertEquals("a:b", toolsVm.uiState.value.cleanerOutput)

        settingsVm.selectThemeMode(AppThemeMode.LIGHT)
        advanceUntilIdle()
        assertEquals(AppThemeMode.LIGHT, repo.settingsState.value?.themeMode)
    }

    @Test
    fun `appShellViewModel restores saved tab and handles back stack`() = runTest {
        val repo = FakeOpenBulletRepository()
        val savedState = SavedStateHandle(mapOf("active_tab" to MainTab.CONFIGS.name))
        val vm = AppShellViewModel(repo, savedState)
        advanceUntilIdle()

        assertEquals(MainTab.CONFIGS, vm.uiState.value.activeTab)
        vm.selectTab(MainTab.TOOLS)
        assertEquals(MainTab.TOOLS, vm.uiState.value.activeTab)

        assertTrue(vm.navigateBack(hasOpenEditor = false, closeEditor = {}))
        assertEquals(MainTab.CONFIGS, vm.uiState.value.activeTab)
    }
}
