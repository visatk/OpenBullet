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
                    soundNotifier = container.soundNotifier
                ) as T
            }
            modelClass.isAssignableFrom(ProxiesViewModel::class.java) -> {
                ProxiesViewModel(
                    repository = container.repository,
                    parseProxiesUseCase = container.parseProxiesUseCase,
                    exportFormatterUseCase = container.exportFormatterUseCase
                ) as T
            }
            modelClass.isAssignableFrom(WordlistsViewModel::class.java) -> {
                WordlistsViewModel(repository = container.repository) as T
            }
            modelClass.isAssignableFrom(ConfigsViewModel::class.java) -> {
                ConfigsViewModel(
                    repository = container.repository,
                    exportFormatterUseCase = container.exportFormatterUseCase,
                    dispatchers = container.dispatchers
                ) as T
            }
            modelClass.isAssignableFrom(HitsDbViewModel::class.java) -> {
                HitsDbViewModel(
                    repository = container.repository,
                    exportFormatterUseCase = container.exportFormatterUseCase
                ) as T
            }
            modelClass.isAssignableFrom(ToolsViewModel::class.java) -> {
                ToolsViewModel(
                    repository = container.repository,
                    cleanWordlistUseCase = container.cleanWordlistUseCase,
                    formatCombosUseCase = container.formatCombosUseCase,
                    cryptoAndRegexUseCase = container.cryptoAndRegexUseCase,
                    dispatchers = container.dispatchers
                ) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(
                    repository = container.repository,
                    soundNotifier = container.soundNotifier
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
