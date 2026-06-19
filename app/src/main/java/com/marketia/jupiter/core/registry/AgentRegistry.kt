package com.marketia.jupiter.core.registry

import com.marketia.jupiter.data.entity.AgentEntity
import com.marketia.jupiter.data.repository.JupiterRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentRegistry @Inject constructor(
    private val repository: JupiterRepository
) {
    val agents: Flow<List<AgentEntity>> = repository.agents

    suspend fun register(name: String, model: String, capability: String) {
        repository.addAgent(name, model, capability)
    }
}
