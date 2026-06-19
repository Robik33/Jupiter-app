package com.marketia.jupiter.core.bridge

object SafetyGate {
    // Actions that NEVER execute automatically — always require approval
    private val hardBlocked = setOf(
        "AUTO_INSTALL_APK",
        "DELETE_CRITICAL_FILES",
        "UPLOAD_API_KEYS",
        "EXECUTE_SHELL_COMMAND",
        "WIPE_DATABASE",
        "FORCE_PUSH",
        "DELETE_BRANCH"
    )

    // Actions that require user approval before executing
    private val requiresApproval = setOf(
        "BUILD_APK",
        "PUSH_TO_GITHUB",
        "CREATE_GITHUB_RELEASE",
        "SEND_TO_CLAUDE_CODE",
        "MODIFY_SETTINGS",
        "SAVE_API_KEY"
    )

    fun check(action: String): SafetyResult = when {
        hardBlocked.any { action.uppercase().contains(it) } ->
            SafetyResult.BLOCKED("Accion bloqueada por politica de seguridad: $action")
        requiresApproval.any { action.uppercase().contains(it) } ->
            SafetyResult.NEEDS_APPROVAL("Esta accion requiere tu aprobacion: $action")
        else ->
            SafetyResult.ALLOWED
    }

    fun validateTask(task: ClaudeCodeTask): SafetyResult {
        val text = "${task.goal} ${task.requestedChange}".uppercase()
        return when {
            hardBlocked.any { text.contains(it) } ->
                SafetyResult.BLOCKED("Tarea contiene operacion bloqueada.")
            else ->
                SafetyResult.NEEDS_APPROVAL("Tarea lista para revision. Aprueba para enviar.")
        }
    }
}

sealed class SafetyResult {
    object ALLOWED : SafetyResult()
    data class NEEDS_APPROVAL(val message: String) : SafetyResult()
    data class BLOCKED(val reason: String) : SafetyResult()
}
