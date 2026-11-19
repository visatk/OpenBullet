package com.example.viewmodel

sealed interface UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent
    data class ShareFile(
        val fileName: String,
        val content: String,
        val mimeType: String = "text/plain",
        val alsoCopyToClipboard: Boolean = false
    ) : UiEvent
    data class CopyText(
        val label: String,
        val text: String,
        val feedbackMessage: String
    ) : UiEvent
}
