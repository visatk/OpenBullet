package com.example.domain.usecase

class FormatCombosUseCase {

    data class FormatResult(
        val output: String,
        val formattedCount: Int,
        val skippedCount: Int
    )

    operator fun invoke(
        rawInput: String,
        targetSeparator: String,
        swapOrder: Boolean
    ): FormatResult {
        if (rawInput.isBlank()) {
            return FormatResult(output = "", formattedCount = 0, skippedCount = 0)
        }
        val sep = targetSeparator.ifEmpty { ":" }
        var formattedCount = 0
        var skippedCount = 0
        val outLines = rawInput.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val parts = line.split(":", ";", "|", "\t", limit = 2)
                if (parts.size == 2) {
                    val left = parts[0].trim()
                    val right = parts[1].trim()
                    if (left.isNotEmpty() && right.isNotEmpty()) {
                        formattedCount++
                        if (swapOrder) "$right$sep$left" else "$left$sep$right"
                    } else {
                        skippedCount++
                        line
                    }
                } else {
                    skippedCount++
                    line
                }
            }
            .toList()

        return FormatResult(
            output = outLines.joinToString("\n"),
            formattedCount = formattedCount,
            skippedCount = skippedCount
        )
    }
}
