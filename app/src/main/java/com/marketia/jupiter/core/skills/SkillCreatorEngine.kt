package com.marketia.jupiter.core.skills

import com.marketia.jupiter.data.entity.SkillEntity
import com.marketia.jupiter.data.repository.JupiterRepository
import javax.inject.Inject
import javax.inject.Singleton

data class SkillDraft(
    val name: String,
    val category: String,
    val description: String,
    val resumen: String = "",
    val conocimientos: String = "",
    val fuente: String = ""
)

@Singleton
class SkillCreatorEngine @Inject constructor(
    private val repository: JupiterRepository
) {
    suspend fun create(draft: SkillDraft): Long = repository.saveSkill(
        SkillEntity(
            name          = draft.name,
            category      = draft.category,
            description   = draft.description,
            tags          = draft.category,
            isBuiltIn     = false,
            resumen       = draft.resumen.ifBlank { draft.description },
            conocimientos = draft.conocimientos,
            fuente        = draft.fuente
        )
    )

    suspend fun createFromText(
        name: String,
        content: String,
        category: String = "general",
        source: String = ""
    ): Long = repository.saveSkill(
        SkillEntity(
            name          = name,
            category      = category,
            description   = content.take(200),
            tags          = category,
            isBuiltIn     = false,
            resumen       = content.take(500),
            conocimientos = "",
            fuente        = source
        )
    )
}
