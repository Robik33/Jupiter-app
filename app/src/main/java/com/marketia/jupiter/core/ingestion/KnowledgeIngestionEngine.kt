package com.marketia.jupiter.core.ingestion

import com.marketia.jupiter.data.entity.LinkEntity
import com.marketia.jupiter.data.entity.MemoryEdgeEntity
import com.marketia.jupiter.data.entity.MemoryNodeEntity
import com.marketia.jupiter.data.entity.SkillEntity
import com.marketia.jupiter.data.repository.JupiterRepository
import javax.inject.Inject
import javax.inject.Singleton

enum class IngestionStatus { SUCCESS, PENDING_INGESTION, PARTIAL, FAILED }

data class IngestionResult(
    val status: IngestionStatus,
    val title: String,
    val skillName: String = "",
    val category: String = "",
    val error: String = ""
)

@Singleton
class KnowledgeIngestionEngine @Inject constructor(
    private val analyzer: LinkAnalyzer,
    private val extractor: SkillExtractor,
    private val repository: JupiterRepository
) {
    suspend fun ingest(url: String): IngestionResult {
        // Step 1: Analyze URL (requires network)
        val analysis = runCatching { analyzer.analyze(url) }.getOrNull()

        if (analysis == null) {
            val linkId = repository.saveLinkEntity(
                LinkEntity(url = url, title = "PENDIENTE: ${url.take(50)}",
                    category = "pendiente", processed = false)
            )
            repository.saveMemoryNode(MemoryNodeEntity(
                type = "LINK", refId = linkId,
                label = "PENDIENTE: ${url.take(50)}",
                summary = "Link guardado sin procesar. Sin conexión o error de red.",
                tags = "pendiente,link"
            ))
            return IngestionResult(IngestionStatus.PENDING_INGESTION, "PENDIENTE: ${url.take(40)}",
                error = "Sin conexión. Guardado como PENDIENTE_INGESTIÓN.")
        }

        val title = analysis.title.ifBlank { url.take(60) }

        // Step 2: Try to extract skill via AI
        val skillDraft = runCatching { extractor.extract(analysis) }.getOrNull()

        // Step 3: Save link with all available info
        val linkId = repository.saveLinkEntity(LinkEntity(
            url      = url,
            title    = title,
            category = skillDraft?.category ?: "general",
            summary  = analysis.metaDescription,
            skills   = skillDraft?.name ?: "",
            processed = skillDraft != null
        ))

        // Step 4: Create LINK memory node
        val linkNodeId = repository.saveMemoryNode(MemoryNodeEntity(
            type    = "LINK",
            refId   = linkId,
            label   = title,
            summary = analysis.metaDescription.take(200),
            tags    = "link,${analysis.type.name.lowercase()}"
        ))

        if (skillDraft == null) {
            return IngestionResult(IngestionStatus.PARTIAL, title,
                error = "Link guardado. Sin IA disponible para extraer skill.")
        }

        // Step 5: Save skill
        val skillId = repository.saveSkill(SkillEntity(
            name          = skillDraft.name,
            category      = skillDraft.category,
            description   = skillDraft.resumen,
            tags          = title,
            isBuiltIn     = false,
            resumen       = skillDraft.resumen,
            conocimientos = skillDraft.conocimientos,
            fuente        = url
        ))

        // Step 6: Create SKILL memory node
        val skillNodeId = repository.saveMemoryNode(MemoryNodeEntity(
            type    = "SKILL",
            refId   = skillId,
            label   = skillDraft.name,
            summary = skillDraft.resumen,
            tags    = "skill,${skillDraft.category}"
        ))

        // Step 7: Semantic edge SKILL -[CREATED_FROM]-> LINK
        repository.saveMemoryEdge(MemoryEdgeEntity(
            fromType = "SKILL", fromId = skillNodeId,
            toType   = "LINK",  toId   = linkNodeId,
            relation = "CREATED_FROM",
            label    = "Extraído de"
        ))

        return IngestionResult(IngestionStatus.SUCCESS, title,
            skillName = skillDraft.name, category = skillDraft.category)
    }
}
