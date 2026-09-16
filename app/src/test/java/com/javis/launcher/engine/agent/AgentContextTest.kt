package com.javis.launcher.engine.agent

import junit.framework.Assert.*
import org.junit.Test
import org.json.JSONObject

class AgentContextTest {

    @Test
    fun testAgentContextDefaults() {
        val ctx = AgentContext(userInput = "test")
        assertEquals("test", ctx.userInput)
        assertTrue(ctx.conversationHistory.isEmpty())
        assertNotNull(ctx.contextEngine)
    }

    @Test
    fun testAgentContextWithTools() {
        val tools = listOf(
            object : JavisTool {
                override val name = "t1"
                override val description = ""
                override val parametersSchema: JSONObject? = null
                override suspend fun execute(arguments: Map<String, Any?>): ToolResult = ToolResult.Success()
            }
        )
        val ctx = AgentContext(userInput = "test", tools = tools)
        assertEquals(1, ctx.tools.size)
    }
}
