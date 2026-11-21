package com.example.domain.usecase

import com.example.data.OpenBulletRepository
import com.example.model.ConfigItem
import com.example.network.ConfigFileCodec

/**
 * Domain UseCase that parses OpenBullet .loli, JSON bundle, or raw LoliScript payloads
 * and persists valid configurations into the repository.
 */
class ImportConfigsUseCase(
    private val repository: OpenBulletRepository
) {
    suspend operator fun invoke(
        rawPayload: String,
        fallbackName: String = "Imported Config"
    ): List<ConfigItem> {
        val parsedList = ConfigFileCodec.parseImportPayload(rawPayload, fallbackName)
        if (parsedList.isNotEmpty()) {
            repository.saveConfigs(parsedList)
        }
        return parsedList
    }
}
