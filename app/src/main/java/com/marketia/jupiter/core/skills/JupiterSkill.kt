package com.marketia.jupiter.core.skills

interface JupiterSkill {
    val id: String
    val name: String
    suspend fun execute(params: Map<String, String>): String
}
