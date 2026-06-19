package com.marketia.jupiter.core.ingestion

import com.marketia.jupiter.data.entity.LinkEntity
import com.marketia.jupiter.data.entity.SkillEntity
import com.marketia.jupiter.data.repository.JupiterRepository
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(
    val success: Boolean,
    val linkTitle: String,
    val skillName: String,
    val category: String,
    val summary: String,
    val error: String = ""
)

@Singleton
class KnowledgeImporter @Inject constructor(
    private val analyzer: LinkAnalyzer,
    private val extractor: SkillExtractor,
    private val repository: JupiterRepository
) {
    suspend fun import(url: String): ImportResult {
        return runCatching {
            // Step 1: Analyze the link
            val analysis = analyzer.analyze(url)

            // Step 2: Extract skill from analysis
            val skill = extractor.extract(analysis)

            // Step 3: Save link with enriched data
            val link = LinkEntity(
                url       = url,
                title     = analysis.title,
                category  = skill.category,
                summary   = skill.resumen,
                skills    = skill.name,
                concepts  = skill.conocimientos,
                processed = true
            )
            repository.saveLinkEntity(link)

            // Step 4: Save extracted skill
            val skillEntity = SkillEntity(
                name          = skill.name,
                category      = skill.category,
                description   = skill.resumen,
                tags          = skill.conocimientos.replace(",", ", "),
                isBuiltIn     = false,
                resumen       = skill.resumen,
                conocimientos = skill.conocimientos,
                fuente        = url
            )
            repository.saveSkill(skillEntity)

            ImportResult(
                success   = true,
                linkTitle = analysis.title,
                skillName = skill.name,
                category  = skill.category,
                summary   = skill.resumen
            )
        }.getOrElse { e ->
            ImportResult(
                success   = false,
                linkTitle = url,
                skillName = "",
                category  = "",
                summary   = "",
                error     = e.message ?: "Error desconocido"
            )
        }
    }
}
