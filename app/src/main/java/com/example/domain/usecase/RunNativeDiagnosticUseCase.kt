package com.example.domain.usecase

import com.example.di.DispatcherProvider
import com.example.nativeengine.NativeScannerBridge
import kotlinx.coroutines.withContext

/**
 * Domain UseCase that benchmarks the NDK C++ response key scanner and FNV-1a combo analyzer.
 */
class RunNativeDiagnosticUseCase(
    private val dispatchers: DispatcherProvider
) {
    suspend operator fun invoke(
        sampleBody: String,
        successKey: String,
        failureKey: String
    ): String = withContext(dispatchers.default) {
        val startNs = System.nanoTime()
        val (outcome, matched) = NativeScannerBridge.scanResponseKeys(
            body = sampleBody,
            successKeys = listOf(successKey).filter { it.isNotBlank() },
            customKeys = emptyList(),
            failureKeys = listOf(failureKey).filter { it.isNotBlank() }
        )
        val analysis = NativeScannerBridge.analyzeComboLine(
            sampleBody.lineSequence().firstOrNull().orEmpty()
        )
        val elapsedUs = (System.nanoTime() - startNs) / 1000L
        "Outcome=${outcome.label} | Key=${matched ?: "None"} | FNV1a=${analysis.fnv1aHashHex.take(8)} | ${elapsedUs}us"
    }
}
