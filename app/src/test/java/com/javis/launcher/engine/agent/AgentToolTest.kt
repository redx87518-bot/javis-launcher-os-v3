package com.javis.launcher.engine.agent

import junit.framework.Assert.*
import org.junit.Test
import org.json.JSONObject

class AgentToolTest {

    @Test
    fun testToolResultSuccess() {
        val result = ToolResult.Success(
            data = mapOf("key" to "value"),
            userMessage = "Done, Sir."
        )
        assertTrue(result is ToolResult.Success)
        assertEquals("Done, Sir.", result.userMessage)
        assertEquals("value", result.data["key"])
    }

    @Test
    fun testToolResultNeedInput() {
        val result = ToolResult.NeedInput(question = "Which contact?")
        assertTrue(result is ToolResult.NeedInput)
        assertEquals("Which contact?", result.question)
    }

    @Test
    fun testToolResultConfirmationRequired() {
        val result = ToolResult.ConfirmationRequired(
            question = "Proceed?",
            options = listOf("Yes", "No")
        )
        assertTrue(result is ToolResult.ConfirmationRequired)
        assertEquals("Proceed?", result.question)
        assertEquals(2, result.options.size)
    }

    @Test
    fun testToolResultFailed() {
        val result = ToolResult.Failed("Error", retryable = true)
        assertTrue(result is ToolResult.Failed)
        assertEquals("Error", result.error)
        assertTrue(result.retryable)
    }

    @Test
    fun testToolResultFailedNonRetryable() {
        val result = ToolResult.Failed("Fatal", retryable = false)
        assertFalse(result.retryable)
    }

    @Test
    fun testToolRegistryRegisterAndGet() {
        val registry = ToolRegistry()
        val tool = object : JavisTool {
            override val name = "test.tool"
            override val description = "A test tool"
            override val parametersSchema: JSONObject? = null
            override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
                return ToolResult.Success(userMessage = "ok")
            }
        }
        registry.register(tool)
        assertEquals(tool, registry.getTool("test.tool"))
        assertTrue(registry.hasTool("test.tool"))
        assertEquals(1, registry.getAllTools().size)
    }

    @Test
    fun testToolRegistryDefinitions() {
        val registry = ToolRegistry()
        val tool = object : JavisTool {
            override val name = "my.tool"
            override val description = "Description"
            override val parametersSchema: JSONObject? = JSONObject("{""foo"":""bar""}")
            override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
                return ToolResult.Success()
            }
        }
        registry.register(tool)
        val defs = registry.getToolDefinitions()
        assertEquals(1, defs.size)
        assertEquals("my.tool", defs[0].optString("name"))
        assertEquals("Description", defs[0].optString("description"))
    }

    @Test
    fun testToolRegistryClear() {
        val registry = ToolRegistry()
        registry.register(object : JavisTool {
            override val name = "a"
            override val description = ""
            override val parametersSchema: JSONObject? = null
            override suspend fun execute(arguments: Map<String, Any?>): ToolResult = ToolResult.Success()
        })
        registry.clear()
        assertTrue(registry.getAllTools().isEmpty())
    }
}
