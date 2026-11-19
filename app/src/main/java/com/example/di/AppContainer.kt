package com.example.di

import android.content.Context
import androidx.work.WorkManager
import com.example.data.DefaultOpenBulletRepository
import com.example.data.OpenBulletRepository
import com.example.data.local.OpenBulletDatabase
import com.example.data.local.WordlistFileStore
import com.example.domain.usecase.CleanWordlistUseCase
import com.example.domain.usecase.CryptoAndRegexUseCase
import com.example.domain.usecase.ExportFormatterUseCase
import com.example.domain.usecase.FormatCombosUseCase
import com.example.domain.usecase.ParseProxiesUseCase
import com.example.network.OpenBulletNetworkClient
import com.example.util.SoundAlertPlayer
import com.example.util.SoundNotifier

interface AppContainer {
    val dispatchers: DispatcherProvider
    val repository: OpenBulletRepository
    val soundNotifier: SoundNotifier
    val cleanWordlistUseCase: CleanWordlistUseCase
    val formatCombosUseCase: FormatCombosUseCase
    val parseProxiesUseCase: ParseProxiesUseCase
    val cryptoAndRegexUseCase: CryptoAndRegexUseCase
    val exportFormatterUseCase: ExportFormatterUseCase
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

    override val repository: OpenBulletRepository by lazy {
        DefaultOpenBulletRepository(
            context = appContext,
            db = database,
            wordlistFileStore = wordlistFileStore,
            workManager = workManager,
            apiService = OpenBulletNetworkClient.apiService,
            dispatchers = dispatchers
        )
    }

    override val cleanWordlistUseCase: CleanWordlistUseCase by lazy { CleanWordlistUseCase() }
    override val formatCombosUseCase: FormatCombosUseCase by lazy { FormatCombosUseCase() }
    override val parseProxiesUseCase: ParseProxiesUseCase by lazy { ParseProxiesUseCase() }
    override val cryptoAndRegexUseCase: CryptoAndRegexUseCase by lazy { CryptoAndRegexUseCase() }
    override val exportFormatterUseCase: ExportFormatterUseCase by lazy { ExportFormatterUseCase() }
}
