package com.example.domain.usecase

import com.example.di.DispatcherProvider
import com.example.model.ConfigDebugResult
import com.example.model.ProxyItem
import com.example.network.HttpExecutionEngine
import com.example.viewmodel.RunnerPacketHelper
import kotlinx.coroutines.withContext

/**
 * Domain UseCase that executes a single LoliScript debugging pipeline with optional proxy selection
 * and returns a structured [ConfigDebugResult] with trace logs, variables, and captures.
 */
class DebugConfigScriptUseCase(
    private val dispatchers: DispatcherProvider
) {
    suspend operator fun invoke(
        rawTestInput: String,
        scriptText: String,
        timeoutSec: Int,
        useProxy: Boolean,
        availableProxies: List<ProxyItem>
    ): ConfigDebugResult = withContext(dispatchers.io) {
        val chosenProxy = if (useProxy) {
            RunnerPacketHelper.chooseDebugProxy(availableProxies)
        } else {
            null
        }
        HttpExecutionEngine.executeDebug(
            rawLine = rawTestInput.ifBlank { DEFAULT_TEST_COMBO },
            scriptText = scriptText,
            timeoutSec = timeoutSec,
            proxyItem = chosenProxy
        )
    }

    companion object {
        const val DEFAULT_TEST_COMBO = "test@example.com:password123"
    }
}
