package com.naz.iris.core.tools

interface IrisToolDispatcher {
    suspend fun dispatch(toolName: String, argsJson: String): ToolExecutionResult
}

sealed class ToolExecutionResult {
    abstract val toolName: String
    abstract val rawJson: String

    data class WithLocalFinal(
        override val toolName: String,
        override val rawJson: String,
        val localFinalText: String
    ) : ToolExecutionResult()

    data class RequiresSecondTurn(
        override val toolName: String,
        override val rawJson: String
    ) : ToolExecutionResult()
}