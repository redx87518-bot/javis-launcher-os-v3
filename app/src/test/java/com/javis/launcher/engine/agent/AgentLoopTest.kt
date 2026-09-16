package com.javis.launcher.engine.agent

import junit.framework.Assert.*
import org.junit.Test

class AgentEngineToolLoopTest {

    @Test
    fun testAgentResultContainsResponse() {
        val result = AgentResult(
            response = "Done, Sir.",
            needsConfirmation = false,
            session = AgentSession()
        )
        assertEquals("Done, Sir.", result.response)
        assertFalse(result.needsConfirmation)
    }

    @Test
    fun testAgentSessionSteps() {
        val session = AgentSession(
            steps = mutableListOf(
                AgentStep(observation = "step1"),
                AgentStep(observation = "step2")
            )
        )
        assertEquals(2, session.steps.size)
    }

    @Test
    fun testAgentSessionConversationBuffer() {
        val session = AgentSession(
            conversationBuffer = mutableListOf(
                ConversationMessage("user", "hello"),
                ConversationMessage("assistant", "hi")
            )
        )
        assertEquals(2, session.conversationBuffer.size)
    }

    @Test
    fun testToolCallingResultTool() {
        val result = ToolCallingResult(
            toolCall = ToolCall("whatsapp.get_recent_messages", mapOf("chatId" to "abc"))
        )
        assertNotNull(result.toolCall)
        assertEquals("whatsapp.get_recent_messages", result.toolCall!!.toolName)
        assertEquals("abc", result.toolCall!!.arguments["chatId"])
    }

    @Test
    fun testToolCallingResultConfirm() {
        val result = ToolCallingResult(
            needsConfirmation = true,
            confirmationText = "Continue?"
        )
        assertTrue(result.needsConfirmation)
        assertEquals("Continue?", result.confirmationText)
    }

    @Test
    fun testToolCallingResultAIResponse() {
        val result = ToolCallingResult(aiResponse = "Hello there.")
        assertEquals("Hello there.", result.aiResponse)
        assertNull(result.toolCall)
    }

    @Test
    fun testPlanResultContinue() {
        val result = PlanResult.Continue(
            observation = "tool executed",
            thought = "continue",
            action = "whatsapp.send_message"
        )
        assertTrue(result is PlanResult.Continue)
    }

    @Test
    fun testPlanResultRespond() {
        val result = PlanResult.Respond("Done.")
        assertTrue(result is PlanResult.Respond)
        assertEquals("Done.", (result as PlanResult.Respond).response)
    }

    @Test
    fun testPlanResultNeedConfirmation() {
        val result = PlanResult.NeedConfirmation("Proceed?")
        assertTrue(result is PlanResult.NeedConfirmation)
        assertEquals("Proceed?", result.question)
    }

    @Test
    fun testPlanResultError() {
        val result = PlanResult.Error("Failed")
        assertTrue(result is PlanResult.Error)
        assertEquals("Failed", result.message)
    }
}
