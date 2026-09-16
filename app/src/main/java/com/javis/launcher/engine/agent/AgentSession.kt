package com.javis.launcher.engine.agent

import com.javis.launcher.models.ConversationMessage

enum class AgentStatus {
    IDLE,
    OBSERVING,
    UNDERSTANDING,
    PLANNING,
    EXECUTING,
    VERIFYING,
    RESPONDING,
    SPEAKING,
    ERROR
}

data class AgentSession(
    val sessionId: String = java.util.UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    var status: AgentStatus = AgentStatus.IDLE,
    var currentGoal: String? = null,
    var currentTask: String? = null,
    var pendingConfirmation: String? = null,
    var steps: MutableList<AgentStep> = mutableListOf(),
    var conversationBuffer: MutableList<ConversationMessage> = mutableListOf()
)
