package com.javis.launcher.engine.agent

import org.json.JSONObject

class ToolRegistry {
    private val tools = mutableMapOf<String, JavisTool>()

    fun register(tool: JavisTool) {
        tools[tool.name] = tool
    }

    fun getTool(name: String): JavisTool? = tools[name]

    fun getAllTools(): List<JavisTool> = tools.values.toList()

    fun getToolDefinitions(): List<JSONObject> = tools.values.map { tool ->
        JSONObject().apply {
            put("name", tool.name)
            put("description", tool.description)
            put("parameters", tool.parametersSchema ?: JSONObject())
        }
    }

    fun hasTool(name: String): Boolean = tools.containsKey(name)

    fun clear() {
        tools.clear()
    }
}
