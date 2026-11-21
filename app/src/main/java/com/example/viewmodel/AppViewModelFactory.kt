package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.OpenBulletApplication

class AppViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
        val container = (application as OpenBulletApplication).container
        val savedStateHandle = extras.createSavedStateHandle()

        return when {
            modelClass.isAssignableFrom(AppShellViewModel::class.java) -> {
                AppShellViewModel(
                    repository = container.repository,
                    savedStateHandle = savedStateHandle
                ) as T
            }
            modelClass.isAssignableFrom(RunnerViewModel::class.java) -> {
                RunnerViewModel(
                    repository = container.repository,
                    soundNotifier = container.soundNotifier,
                    manageRunnerJobUseCase = container.manageRunnerJobUseCase,
                    savedStateHandle = savedStateHandle
                ) as T
            }
            modelClass.isAssignableFrom(ProxiesViewModel::class.java) -> {
                ProxiesViewModel(
                    repository = container.repository,
                    parseProxiesUseCase = container.parseProxiesUseCase,
                    exportFormatterUseCase = container.exportFormatterUseCase,
                    savedStateHandle = savedStateHandle
                ) as T
            }
            modelClass.isAssignableFrom(WordlistsViewModel::class.java) -> {
                WordlistsViewModel(
                    repository = container.repository,
                    createWordlistUseCase = container.createWordlistUseCase
                ) as T
            }
            modelClass.isAssignableFrom(ConfigsViewModel::class.java) -> {
                ConfigsViewModel(
                    repository = container.repository,
                    exportFormatterUseCase = container.exportFormatterUseCase,
                    dispatchers = container.dispatchers,
                    importConfigsUseCase = container.importConfigsUseCase,
                    debugConfigScriptUseCase = container.debugConfigScriptUseCase,
                    savedStateHandle = savedStateHandle
                ) as T
            }
            modelClass.isAssignableFrom(HitsDbViewModel::class.java) -> {
                HitsDbViewModel(
                    repository = container.repository,
                    exportFormatterUseCase = container.exportFormatterUseCase,
                    savedStateHandle = savedStateHandle
                ) as T
            }
            modelClass.isAssignableFrom(ToolsViewModel::class.java) -> {
                ToolsViewModel(
                    repository = container.repository,
                    cleanWordlistUseCase = container.cleanWordlistUseCase,
                    formatCombosUseCase = container.formatCombosUseCase,
                    cryptoAndRegexUseCase = container.cryptoAndRegexUseCase,
                    dispatchers = container.dispatchers,
                    runNativeDiagnosticUseCase = container.runNativeDiagnosticUseCase,
                    createWordlistUseCase = container.createWordlistUseCase,
                    savedStateHandle = savedStateHandle
                ) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(
                    repository = container.repository,
                    soundNotifier = container.soundNotifier,
                    savedStateHandle = savedStateHandle
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
