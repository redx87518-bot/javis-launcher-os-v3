package com.javis.launcher.engine.agent

import org.json.JSONObject

interface JavisTool {
    val name: String
    val description: String
    val parametersSchema: JSONObject?

    suspend fun execute(arguments: Map<String, Any?>): ToolResult
}
