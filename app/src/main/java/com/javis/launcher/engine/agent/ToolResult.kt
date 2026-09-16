package com.javis.launcher.engine.agent

import org.json.JSONObject

sealed class ToolResult {
    data class Success(
        val data: Map<String, Any?> = emptyMap(),
        val userMessage: String = "",
        val raw: Any? = null
    ) : ToolResult()

    data class NeedInput(
        val question: String,
        val context: Map<String, Any?> = emptyMap()
    ) : ToolResult()

    data class ConfirmationRequired(
        val question: String,
        val options: List<String> = emptyList(),
        val context: Map<String, Any?> = emptyMap()
    ) : ToolResult()

    data class Failed(
        val error: String,
        val retryable: Boolean = false,
        val context: Map<String, Any?> = emptyMap()
    ) : ToolResult()
}
