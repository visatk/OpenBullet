package com.example.domain.usecase

import com.example.data.OpenBulletRepository
import com.example.model.WordlistItem
import java.util.UUID

/**
 * Domain UseCase reused across [com.example.viewmodel.WordlistsViewModel] and
 * [com.example.viewmodel.ToolsViewModel] to normalize, construct, and persist a [WordlistItem].
 */
class CreateWordlistUseCase(
    private val repository: OpenBulletRepository
) {
    suspend operator fun invoke(
        rawText: String,
        name: String = "",
        type: String = "Default",
        purpose: String = "Runner Input",
        ignoreEmptyLines: Boolean = true,
        requireNonEmpty: Boolean = false,
        fallbackIndex: Int = 1
    ): WordlistItem? {
        val cleanLines = rawText
            .lineSequence()
            .map { it.trim() }
            .filter { !(ignoreEmptyLines && it.isEmpty()) }
            .toList()

        if (requireNonEmpty && cleanLines.isEmpty()) {
            return null
        }

        val cleanName = name.trim().ifEmpty { "wordlist_$fallbackIndex.txt" }
        val id = UUID.randomUUID().toString()
        val item = WordlistItem(
            id = id,
            name = cleanName,
            path = "files://wordlists/$id.txt",
            type = type.trim().ifEmpty { "Default" },
            lines = cleanLines,
            purpose = purpose.trim().ifEmpty { "Runner Input" }
        )
        repository.saveWordlist(item)
        return item
    }
}
