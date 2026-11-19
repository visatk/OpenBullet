package com.example.domain.usecase

class CleanWordlistUseCase {

    data class CleanResult(
        val output: String,
        val inputCount: Int,
        val outputCount: Int,
        val removedCount: Int
    )

    operator fun invoke(
        rawInput: String,
        trimWhitespace: Boolean,
        removeEmpty: Boolean,
        removeDuplicates: Boolean
    ): CleanResult {
        if (rawInput.isEmpty()) {
            return CleanResult(output = "", inputCount = 0, outputCount = 0, removedCount = 0)
        }
        val rawLines = rawInput.lineSequence().toList()
        val inputCount = rawLines.size
        var lines = rawLines
        if (trimWhitespace) lines = lines.map { it.trim() }
        if (removeEmpty) lines = lines.filter { it.isNotEmpty() }
        if (removeDuplicates) lines = lines.distinct()
        val outputCount = lines.size
        return CleanResult(
            output = lines.joinToString("\n"),
            inputCount = inputCount,
            outputCount = outputCount,
            removedCount = (inputCount - outputCount).coerceAtLeast(0)
        )
    }
}
