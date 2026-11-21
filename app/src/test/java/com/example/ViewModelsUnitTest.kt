package com.example

import androidx.lifecycle.SavedStateHandle
import com.example.data.DefaultOpenBulletRepository
import com.example.data.OpenBulletRepository
import com.example.data.source.BackgroundWorkDataSource
import com.example.data.source.LocalWorkspaceDataSource
import com.example.data.source.RemoteTextDataSource
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
import com.example.model.ResultTab
import com.example.model.RunnerSessionSnapshot
import com.example.model.RunnerWorkSnapshot
import com.example.model.SettingsSection
import com.example.model.ToolsSection
import com.example.model.WorkExecutionState
import com.example.model.WordlistItem
import com.example.util.SoundNotifier
import com.example.viewmodel.AppShellViewModel
import com.example.viewmodel.ConfigsViewModel
import com.example.viewmodel.HitsDbViewModel
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

    private class FakeLocalWorkspaceDataSource : LocalWorkspaceDataSource {
        val configsState = MutableStateFlow<List<ConfigItem>>(emptyList())
        val wordlistsState = MutableStateFlow<List<WordlistItem>>(emptyList())
        val proxiesState = MutableStateFlow<List<ProxyItem>>(emptyList())
        val hitsState = MutableStateFlow<List<HitDbItem>>(emptyList())
        val settingsState = MutableStateFlow<PersistedSettingsSnapshot?>(PersistedSettingsSnapshot())
        val sessionState = MutableStateFlow<RunnerSessionSnapshot?>(null)

        override val configsFlow: Flow<List<ConfigItem>> = configsState
        override val wordlistsFlow: Flow<List<WordlistItem>> = wordlistsState
        override val proxiesFlow: Flow<List<ProxyItem>> = proxiesState
        override val hitsDbFlow: Flow<List<HitDbItem>> = hitsState
        override val settingsFlow: Flow<PersistedSettingsSnapshot?> = settingsState
        override val runnerSessionFlow: Flow<RunnerSessionSnapshot?> = sessionState

        override suspend fun getConfigById(id: String): ConfigItem? =
            configsState.value.firstOrNull { it.id == id }

        override suspend fun upsertConfig(config: ConfigItem) {
            configsState.value = configsState.value.filterNot { it.id == config.id } + config
        }

        override suspend fun insertConfigs(configs: List<ConfigItem>) {
            val map = configsState.value.associateBy { it.id }.toMutableMap()
            configs.forEach { map[it.id] = it }
            configsState.value = map.values.toList()
        }

        override suspend fun deleteConfig(id: String) {
            configsState.value = configsState.value.filterNot { it.id == id }
        }

        override suspend fun getWordlistById(id: String): WordlistItem? =
            wordlistsState.value.firstOrNull { it.id == id }

        override suspend fun upsertWordlist(wordlist: WordlistItem) {
            wordlistsState.value = wordlistsState.value.filterNot { it.id == wordlist.id } + wordlist
        }

        override suspend fun readWordlistLines(wordlistId: String): List<String> =
            wordlistsState.value.firstOrNull { it.id == wordlistId }?.lines.orEmpty()

        override suspend fun deleteWordlist(id: String) {
            wordlistsState.value = wordlistsState.value.filterNot { it.id == id }
        }

        override suspend fun clearWordlists() {
            wordlistsState.value = emptyList()
        }

        override suspend fun getAllProxiesSnapshot(): List<ProxyItem> = proxiesState.value

        override suspend fun insertProxies(proxies: List<ProxyItem>) {
            proxiesState.value = proxiesState.value + proxies
        }

        override suspend fun updateProxyCheckResult(id: String, status: String, pingMs: Int?, country: String) = Unit

        override suspend fun updateProxyCheckResultsBatch(updates: List<com.example.data.local.ProxyCheckUpdate>) = Unit

        override suspend fun deleteProxy(id: String) {
            proxiesState.value = proxiesState.value.filterNot { it.id == id }
        }

        override suspend fun deleteBadAndBannedProxies() = Unit

        override suspend fun clearProxies() {
            proxiesState.value = emptyList()
        }

        override suspend fun insertHit(hit: HitDbItem) {
            hitsState.value = hitsState.value + hit
        }

        override suspend fun insertHitsBatch(hits: List<HitDbItem>) {
            hitsState.value = hitsState.value + hits
        }

        override suspend fun deleteHit(id: String) {
            hitsState.value = hitsState.value.filterNot { it.id == id }
        }

        override suspend fun clearHitsDb() {
            hitsState.value = emptyList()
        }

        override suspend fun getSettingsSnapshot(): PersistedSettingsSnapshot? = settingsState.value

        override suspend fun updateSettings(transform: (PersistedSettingsSnapshot) -> PersistedSettingsSnapshot) {
            val cur = settingsState.value ?: PersistedSettingsSnapshot()
            settingsState.value = transform(cur)
        }

        override suspend fun upsertRunnerSession(session: RunnerSessionSnapshot) {
            sessionState.value = session
        }

        override suspend fun clearRunnerSession() {
            sessionState.value = null
        }
    }

    private class FakeRemoteTextDataSource : RemoteTextDataSource {
        var remotePayload: Result<String> = Result.success("")
        override suspend fun fetchRemoteText(url: String): Result<String> = remotePayload
        override suspend fun runHttpProbe(url: String): Result<String> = Result.success("HTTP 200")
    }

    private class FakeBackgroundWorkDataSource : BackgroundWorkDataSource {
        val runnerWorkState = MutableStateFlow<RunnerWorkSnapshot?>(null)
        val proxyWorkState = MutableStateFlow<ProxyCheckWorkSnapshot?>(null)
        var enqueuedRunnerCount = 0
        var cancelledRunnerCount = 0

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
    fun `defaultOpenBulletRepository coordinates local remote and background work data sources`() = runTest {
        val local = FakeLocalWorkspaceDataSource()
        val remote = FakeRemoteTextDataSource().apply {
            remotePayload = Result.success("1.1.1.1:8080")
        }
        val work = FakeBackgroundWorkDataSource()
        val repo = DefaultOpenBulletRepository(
            localDataSource = local,
            remoteDataSource = remote,
            backgroundWorkDataSource = work,
            dispatchers = TestDispatcherProvider(testDispatcher)
        )

        val (starterConfig, starterWordlist) = repo.seedStarterWorkspace()
        advanceUntilIdle()
        assertTrue(local.configsState.value.any { it.id == starterConfig.id })
        assertTrue(local.wordlistsState.value.any { it.id == starterWordlist.id })
        assertEquals(starterConfig.id, local.settingsState.value?.selectedConfigId)

        val remoteResult = repo.fetchRemoteText("https://example.com/proxies.txt")
        assertEquals("1.1.1.1:8080", remoteResult.getOrNull())

        repo.enqueueRunnerJob(
            configId = starterConfig.id,
            wordlistId = starterWordlist.id,
            startLine = 1,
            botsCount = 4,
            proxyMode = ProxyMode.DEF
        )
        assertEquals(1, work.enqueuedRunnerCount)
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
    fun `proxiesViewModel deduplicates imported proxies and persists filter in SavedStateHandle`() = runTest {
        val repo = FakeOpenBulletRepository()
        val savedState = SavedStateHandle()
        val vm = ProxiesViewModel(repo, ParseProxiesUseCase(), ExportFormatterUseCase(), savedState)
        advanceUntilIdle()

        vm.addProxies("1.1.1.1:8080\n1.1.1.1:8080\n2.2.2.2:1080", ProxyProtocol.HTTP)
        vm.setProtocolFilter(ProxyProtocol.SOCKS5)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.proxies.size)
        assertEquals(ProxyProtocol.SOCKS5.name, savedState.get<String>("proxies_protocol_filter"))
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
    fun `toolsViewModel hitsDbViewModel and settingsViewModel persist UI state in SavedStateHandle`() = runTest {
        val repo = FakeOpenBulletRepository()
        val sound = FakeSoundNotifier()
        val dispatchers = TestDispatcherProvider(testDispatcher)
        val toolsSavedState = SavedStateHandle()
        val settingsSavedState = SavedStateHandle()
        val hitsSavedState = SavedStateHandle()

        val toolsVm = ToolsViewModel(
            repository = repo,
            cleanWordlistUseCase = CleanWordlistUseCase(),
            formatCombosUseCase = FormatCombosUseCase(),
            cryptoAndRegexUseCase = CryptoAndRegexUseCase(),
            dispatchers = dispatchers,
            savedStateHandle = toolsSavedState
        )
        val settingsVm = SettingsViewModel(repo, sound, settingsSavedState)
        val hitsVm = HitsDbViewModel(repo, ExportFormatterUseCase(), hitsSavedState)

        toolsVm.selectSection(ToolsSection.REGEX_HASH)
        assertEquals(ToolsSection.REGEX_HASH.name, toolsSavedState.get<String>("tools_active_section"))

        toolsVm.updateCleanerInput("a:b\na:b\n")
        toolsVm.runListCleaner()
        advanceUntilIdle()
        assertEquals("a:b", toolsVm.uiState.value.cleanerOutput)

        settingsVm.selectSection(SettingsSection.THEMES)
        assertEquals(SettingsSection.THEMES.name, settingsSavedState.get<String>("settings_active_section"))

        settingsVm.selectThemeMode(AppThemeMode.LIGHT)
        advanceUntilIdle()
        assertEquals(AppThemeMode.LIGHT, repo.settingsState.value?.themeMode)

        hitsVm.setTypeFilter(ResultTab.CUSTOM)
        hitsVm.updateSearchQuery("vip")
        assertEquals(ResultTab.CUSTOM.name, hitsSavedState.get<String>("hits_type_filter"))
        assertEquals("vip", hitsSavedState.get<String>("hits_search_query"))
    }

    @Test
    fun `appShellViewModel restores saved tab and history and handles back stack`() = runTest {
        val repo = FakeOpenBulletRepository()
        val savedState = SavedStateHandle(
            mapOf(
                "active_tab" to MainTab.CONFIGS.name,
                "tab_history" to MainTab.RUNNER.name
            )
        )
        val vm = AppShellViewModel(repo, savedState)
        advanceUntilIdle()

        assertEquals(MainTab.CONFIGS, vm.uiState.value.activeTab)
        assertEquals(listOf(MainTab.RUNNER), vm.uiState.value.tabHistory)

        vm.selectTab(MainTab.TOOLS)
        assertEquals(MainTab.TOOLS, vm.uiState.value.activeTab)
        assertEquals("RUNNER,CONFIGS", savedState.get<String>("tab_history"))

        assertTrue(vm.navigateBack(hasOpenEditor = false, closeEditor = {}))
        assertEquals(MainTab.CONFIGS, vm.uiState.value.activeTab)
    }

    @Test
    fun `wordlistsViewModel uses CreateWordlistUseCase and selects wordlist for runner`() = runTest {
        val repo = FakeOpenBulletRepository()
        val vm = com.example.viewmodel.WordlistsViewModel(repo)
        advanceUntilIdle()

        vm.addWordlist(
            name = "combos_batch.txt",
            type = "Mail:Pass",
            purpose = "Prod Check",
            rawText = "user1@example.com:p1\n\nuser2@example.com:p2\n"
        )
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.wordlists.size)
        val created = vm.uiState.value.wordlists.first()
        assertEquals("combos_batch.txt", created.name)
        assertEquals(2, created.lineCount)

        vm.selectForRunner(created)
        advanceUntilIdle()
        assertEquals(created.id, repo.settingsState.value?.selectedWordlistId)
    }

    @Test
    fun `runnerViewModel pauses saves checkpoint and resumes via ManageRunnerJobUseCase`() = runTest {
        val repo = FakeOpenBulletRepository()
        val sound = FakeSoundNotifier()
        val vm = RunnerViewModel(repo, sound)
        advanceUntilIdle()

        vm.loadStarterSample()
        advanceUntilIdle()

        vm.toggleStartStop()
        assertEquals(1, repo.enqueuedRunnerCount)
        assertTrue(vm.uiState.value.isRunning)

        // Simulate worker progress to line 1 of 2
        repo.runnerWorkState.value = RunnerWorkSnapshot(
            workId = "work-pause-test",
            state = WorkExecutionState.RUNNING,
            progressCurrent = 1,
            progressTotal = 2,
            hits = 1
        )
        advanceUntilIdle()

        // Pause runner -> should cancel active work, compute checkpoint line #2, and persist startLine = 2
        vm.pauseOrResumeRunner()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isRunning)
        assertTrue(vm.uiState.value.isPaused)
        assertEquals(2, vm.uiState.value.startLine)
        assertEquals(2, repo.settingsState.value?.startLine)
        assertEquals(1, repo.cancelledRunnerCount)

        // Resume runner -> should enqueue runner job starting at line #2
        vm.pauseOrResumeRunner()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isRunning)
        assertFalse(vm.uiState.value.isPaused)
        assertEquals(2, repo.enqueuedRunnerCount)
    }

    @Test
    fun `viewModels compute derived filtered lists and summary metrics in UI state`() = runTest {
        val repo = FakeOpenBulletRepository()
        val dispatchers = TestDispatcherProvider(testDispatcher)

        val proxiesVm = ProxiesViewModel(repo, ParseProxiesUseCase(), ExportFormatterUseCase())
        val configsVm = ConfigsViewModel(repo, ExportFormatterUseCase(), dispatchers)
        val hitsVm = HitsDbViewModel(repo, ExportFormatterUseCase())
        advanceUntilIdle()

        // 1. Proxies derived state (filteredProxies, aliveCount)
        proxiesVm.addProxies("1.1.1.1:8080\n2.2.2.2:8080", ProxyProtocol.HTTP)
        proxiesVm.addProxies("3.3.3.3:1080", ProxyProtocol.SOCKS5)
        advanceUntilIdle()
        assertEquals(3, proxiesVm.uiState.value.proxies.size)
        assertEquals(3, proxiesVm.uiState.value.filteredProxies.size)

        proxiesVm.setProtocolFilter(ProxyProtocol.SOCKS5)
        assertEquals(1, proxiesVm.uiState.value.filteredProxies.size)
        assertEquals("3.3.3.3", proxiesVm.uiState.value.filteredProxies.first().host)

        // 2. Configs derived state (availableCategories, filteredConfigs)
        repo.saveConfigs(
            listOf(
                ConfigItem("c1", "Alpha Auth", "Author1", "Security", "Mail:Pass", true, "2026-11-20", "KEYCHECK"),
                ConfigItem("c2", "Beta Billing", "Author2", "Finance", "Mail:Pass", false, "2026-11-20", "KEYCHECK")
            )
        )
        advanceUntilIdle()
        assertEquals(listOf("Finance", "Security"), configsVm.uiState.value.availableCategories)
        assertEquals(2, configsVm.uiState.value.filteredConfigs.size)

        configsVm.setCategoryFilter("Security")
        assertEquals(1, configsVm.uiState.value.filteredConfigs.size)
        assertEquals("Alpha Auth", configsVm.uiState.value.filteredConfigs.first().name)

        // 3. HitsDb derived state (filteredHits)
        repo.saveHit(
            HitDbItem(
                id = "h1",
                date = "2026-11-20",
                data = "user1:pass1",
                type = ResultTab.HITS,
                configName = "Alpha Auth",
                wordlistName = "combos.txt",
                capturedData = "tier=pro"
            )
        )
        repo.saveHit(
            HitDbItem(
                id = "h2",
                date = "2026-11-20",
                data = "user2:pass2",
                type = ResultTab.CUSTOM,
                configName = "Beta Billing",
                wordlistName = "combos.txt",
                capturedData = "tier=free"
            )
        )
        advanceUntilIdle()
        assertEquals(2, hitsVm.uiState.value.filteredHits.size)

        hitsVm.setTypeFilter(ResultTab.HITS)
        assertEquals(1, hitsVm.uiState.value.filteredHits.size)
        assertEquals("user1:pass1", hitsVm.uiState.value.filteredHits.first().data)
    }

    @Test
    fun `runnerViewModel syncs cross-tab config selection preserves drafts and computes derived results`() = runTest {
        val repo = FakeOpenBulletRepository()
        val sound = FakeSoundNotifier()
        val dispatchers = TestDispatcherProvider(testDispatcher)
        val runnerVm = RunnerViewModel(repo, sound)
        val configsVm = ConfigsViewModel(repo, ExportFormatterUseCase(), dispatchers)
        advanceUntilIdle()

        val cfg1 = ConfigItem("c1", "First Config", "Author", "Auth", "Mail:Pass", false, "2026-11-20", "KEYCHECK")
        val cfg2 = ConfigItem("c2", "Second Config", "Author", "Auth", "Mail:Pass", false, "2026-11-20", "KEYCHECK")
        repo.saveConfigs(listOf(cfg1, cfg2))
        advanceUntilIdle()

        runnerVm.selectConfig(cfg1)
        advanceUntilIdle()
        assertEquals("c1", runnerVm.uiState.value.selectedConfig?.id)

        // Cross-tab selection from ConfigsViewModel when a config is already selected in RunnerViewModel
        configsVm.useInRunner(cfg2)
        advanceUntilIdle()
        assertEquals("c2", runnerVm.uiState.value.selectedConfig?.id)

        // Update bots draft without committing yet, then emit a proxy update -> draft should not be overwritten
        runnerVm.updateBotsCountDraft(42)
        repo.saveProxies(listOf())
        advanceUntilIdle()
        assertEquals(42, runnerVm.uiState.value.botsCount)

        // Emit runner work results and verify derived filteredResultRows & resultCounts
        val packedResults = com.example.viewmodel.RunnerPacketHelper.packWithByteBudget(
            entries = listOf(
                listOf("12:00:01", "a@b.com:1", "DIRECT", "Plan=Pro", ResultTab.HITS.name).joinToString("\u001F"),
                listOf("12:00:02", "c@d.com:2", "DIRECT", "2FA=True", ResultTab.CUSTOM.name).joinToString("\u001F")
            ),
            maxBytes = 4096
        )
        repo.runnerWorkState.value = RunnerWorkSnapshot(
            workId = "work-derived-results",
            state = WorkExecutionState.RUNNING,
            progressCurrent = 2,
            progressTotal = 2,
            hits = 1,
            custom = 1,
            resultsPacked = packedResults
        )
        advanceUntilIdle()

        assertEquals(2, runnerVm.uiState.value.resultRows.size)
        assertEquals(1, runnerVm.uiState.value.filteredResultRows.size)
        assertEquals("a@b.com:1", runnerVm.uiState.value.filteredResultRows.first().data)
        assertEquals(1, runnerVm.uiState.value.resultCounts[ResultTab.HITS])
        assertEquals(1, runnerVm.uiState.value.resultCounts[ResultTab.CUSTOM])

        runnerVm.selectResultTab(ResultTab.CUSTOM)
        assertEquals(1, runnerVm.uiState.value.filteredResultRows.size)
        assertEquals("c@d.com:2", runnerVm.uiState.value.filteredResultRows.first().data)
    }
}

