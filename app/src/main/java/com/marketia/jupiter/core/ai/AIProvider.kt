package com.marketia.jupiter.core.ai

enum class AIProvider(
    val label: String,
    val defaultModel: String,
    val usesOpenAIFormat: Boolean
) {
    LOCAL(      "Local (Sin API)",          "",                                       false),
    OPENROUTER( "OpenRouter",               "meta-llama/llama-3.2-3b-instruct:free", true),
    CLAUDE(     "Claude (Anthropic)",       "claude-haiku-4-5-20251001",              false),
    OLLAMA(     "Ollama (Local)",           "llama3.2",                               true),
    HERMES(     "ORACLE HERMES (PC:7799)",   "oracle-hermes",                          true),
    GEMINI(     "Gemini (Google)",          "gemini-1.5-flash",                       false),
    DEEPSEEK(   "DeepSeek",                 "deepseek-chat",                          true)
}
