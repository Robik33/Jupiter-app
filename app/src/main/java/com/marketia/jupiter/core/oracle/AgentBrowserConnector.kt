package com.marketia.jupiter.core.oracle

import com.marketia.jupiter.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

// Agent Browser Connector — stub ready for activation
// Routes via Hermes /prompt endpoint; Hermes dispatches to oracle_chat_gateway.py
// which holds the Playwright Agent Browser (AgentBrowserStub / PlaywrightStub)
@Singleton
class AgentBrowserConnector @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val hermesClient: OracleHermesClient
) {
    // Navigate to URL — Hermes routes to Playwright via oracle_chat_gateway.py
    suspend fun navigate(url: String): String =
        hermesClient.sendPromptSync("[AGENT_BROWSER] navigate url=$url")

    // Take screenshot and analyze
    suspend fun screenshot(description: String = ""): String =
        hermesClient.sendPromptSync("[AGENT_BROWSER] screenshot desc=$description")

    // Extract data from current page
    suspend fun extract(selector: String): String =
        hermesClient.sendPromptSync("[AGENT_BROWSER] extract selector=$selector")

    // Click element
    suspend fun click(selector: String): String =
        hermesClient.sendPromptSync("[AGENT_BROWSER] click selector=$selector")

    // Send text to input
    suspend fun type(selector: String, text: String): String =
        hermesClient.sendPromptSync("[AGENT_BROWSER] type selector=$selector text=$text")

    // Validate page state
    suspend fun validate(condition: String): String =
        hermesClient.sendPromptSync("[AGENT_BROWSER] validate condition=$condition")
}
