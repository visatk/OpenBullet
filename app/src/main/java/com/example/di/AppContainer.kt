package com.example.di

import android.content.Context
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import com.example.data.DefaultOpenBulletRepository
import com.example.data.OpenBulletRepository
import com.example.data.local.OpenBulletDatabase
import com.example.data.local.WordlistFileStore
import com.example.data.source.BackgroundWorkDataSource
import com.example.data.source.LocalWorkspaceDataSource
import com.example.data.source.RemoteTextDataSource
import com.example.data.source.RetrofitRemoteTextDataSource
import com.example.data.source.RoomWorkspaceDataSource
import com.example.data.source.WorkManagerBackgroundWorkDataSource
import com.example.domain.usecase.CleanWordlistUseCase
import com.example.domain.usecase.CreateWordlistUseCase
import com.example.domain.usecase.CryptoAndRegexUseCase
import com.example.domain.usecase.DebugConfigScriptUseCase
import com.example.domain.usecase.ExportFormatterUseCase
import com.example.domain.usecase.FormatCombosUseCase
import com.example.domain.usecase.ImportConfigsUseCase
import com.example.domain.usecase.ManageRunnerJobUseCase
import com.example.domain.usecase.ParseProxiesUseCase
import com.example.domain.usecase.RunNativeDiagnosticUseCase
import com.example.network.OpenBulletNetworkClient
import com.example.util.SoundAlertPlayer
import com.example.util.SoundNotifier
import com.example.worker.OpenBulletWorkerFactory

interface AppContainer {
    val dispatchers: DispatcherProvider
    val localDataSource: LocalWorkspaceDataSource
    val remoteDataSource: RemoteTextDataSource
    val backgroundWorkDataSource: BackgroundWorkDataSource
    val repository: OpenBulletRepository
    val soundNotifier: SoundNotifier
    val workerFactory: WorkerFactory
    val cleanWordlistUseCase: CleanWordlistUseCase
    val createWordlistUseCase: CreateWordlistUseCase
    val formatCombosUseCase: FormatCombosUseCase
    val parseProxiesUseCase: ParseProxiesUseCase
    val cryptoAndRegexUseCase: CryptoAndRegexUseCase
    val exportFormatterUseCase: ExportFormatterUseCase
    val debugConfigScriptUseCase: DebugConfigScriptUseCase
    val importConfigsUseCase: ImportConfigsUseCase
    val manageRunnerJobUseCase: ManageRunnerJobUseCase
    val runNativeDiagnosticUseCase: RunNativeDiagnosticUseCase
}

class DefaultAppContainer(context: Context) : AppContainer {

    private val appContext = context.applicationContext

    override val dispatchers: DispatcherProvider = DefaultDispatcherProvider()

    private val database: OpenBulletDatabase by lazy {
        OpenBulletDatabase.getInstance(appContext)
    }

    private val wordlistFileStore: WordlistFileStore by lazy {
        WordlistFileStore(appContext)
    }

    private val workManager: WorkManager by lazy {
        WorkManager.getInstance(appContext)
    }

    override val soundNotifier: SoundNotifier by lazy {
        SoundAlertPlayer()
    }

    override val workerFactory: WorkerFactory by lazy {
        OpenBulletWorkerFactory(
            localDataSource = localDataSource
        )
    }

    override val localDataSource: LocalWorkspaceDataSource by lazy {
        RoomWorkspaceDataSource(
            db = database,
            wordlistFileStore = wordlistFileStore,
            dispatchers = dispatchers
        )
    }

    override val remoteDataSource: RemoteTextDataSource by lazy {
        RetrofitRemoteTextDataSource(
            apiService = OpenBulletNetworkClient.apiService,
            dispatchers = dispatchers
        )
    }

    override val backgroundWorkDataSource: BackgroundWorkDataSource by lazy {
        WorkManagerBackgroundWorkDataSource(workManager = workManager)
    }

    override val repository: OpenBulletRepository by lazy {
        DefaultOpenBulletRepository(
            localDataSource = localDataSource,
            remoteDataSource = remoteDataSource,
            backgroundWorkDataSource = backgroundWorkDataSource,
            dispatchers = dispatchers
        )
    }

    override val cleanWordlistUseCase: CleanWordlistUseCase by lazy { CleanWordlistUseCase() }
    override val createWordlistUseCase: CreateWordlistUseCase by lazy {
        CreateWordlistUseCase(repository = repository)
    }
    override val formatCombosUseCase: FormatCombosUseCase by lazy { FormatCombosUseCase() }
    override val parseProxiesUseCase: ParseProxiesUseCase by lazy { ParseProxiesUseCase() }
    override val cryptoAndRegexUseCase: CryptoAndRegexUseCase by lazy { CryptoAndRegexUseCase() }
    override val exportFormatterUseCase: ExportFormatterUseCase by lazy { ExportFormatterUseCase() }
    override val debugConfigScriptUseCase: DebugConfigScriptUseCase by lazy {
        DebugConfigScriptUseCase(dispatchers = dispatchers)
    }
    override val importConfigsUseCase: ImportConfigsUseCase by lazy {
        ImportConfigsUseCase(repository = repository)
    }
    override val manageRunnerJobUseCase: ManageRunnerJobUseCase by lazy {
        ManageRunnerJobUseCase(repository = repository)
    }
    override val runNativeDiagnosticUseCase: RunNativeDiagnosticUseCase by lazy {
        RunNativeDiagnosticUseCase(dispatchers = dispatchers)
    }
}
