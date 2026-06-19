package com.marketia.jupiter.core.skills

import com.marketia.jupiter.data.repository.JupiterRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemorySkill @Inject constructor(
    private val repository: JupiterRepository
) : JupiterSkill {
    override val id = "memory"
    override val name = "Memoria"

    override suspend fun execute(params: Map<String, String>): String {
        val name = params["name"] ?: params["content"] ?: return "Sin datos para guardar."
        val type = params["type"] ?: "nota"
        val desc = params["description"] ?: ""
        repository.addProject(name, type, desc)
        return "Guardado en memoria: \"$name\" (tipo: $type)."
    }
}
