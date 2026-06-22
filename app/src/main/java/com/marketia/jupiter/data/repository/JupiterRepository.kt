package com.marketia.jupiter.data.repository

import com.marketia.jupiter.data.db.dao.*
import com.marketia.jupiter.data.entity.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JupiterRepository @Inject constructor(
    val skillDao: SkillDao,
    val linkDao: LinkDao,
    val projectDao: ProjectDao,
    val systemDao: SystemDao,
    val agentDao: AgentDao,
    val taskDao: TaskDao,
    val nodeDao: MemoryNodeDao,
    val edgeDao: MemoryEdgeDao
) {
    val skills:   Flow<List<SkillEntity>>      = skillDao.getAll()
    val links:    Flow<List<LinkEntity>>       = linkDao.getAll()
    val projects: Flow<List<ProjectEntity>>    = projectDao.getAll()
    val systems:  Flow<List<SystemEntity>>     = systemDao.getAll()
    val agents:   Flow<List<AgentEntity>>      = agentDao.getAll()
    val nodes:    Flow<List<MemoryNodeEntity>> = nodeDao.getAll()
    val edges:    Flow<List<MemoryEdgeEntity>> = edgeDao.getAll()

    fun searchSkills(q: String):   Flow<List<SkillEntity>>   = skillDao.search(q)
    fun searchLinks(q: String):    Flow<List<LinkEntity>>    = linkDao.search(q)
    fun searchProjects(q: String): Flow<List<ProjectEntity>> = projectDao.search(q)

    suspend fun seedIfEmpty() {
        if (skillDao.count() == 0) {
            skillDao.insertAll(listOf(
                SkillEntity(name = "SKILL SALUD",          category = "salud",
                    description = "Medicina integrativa, bioelectricidad y salud holística",
                    tags = "Dr. Duarte,Bioelectricidad,Biodescodificación,Nutrición,Medicina Integrativa"),
                SkillEntity(name = "SKILL SUPERVIVENCIA",  category = "supervivencia",
                    description = "Supervivencia urbana, en campo y situaciones críticas",
                    tags = "Urbana,Bosque,Emergencias,Nuclear,Química,Logística"),
                SkillEntity(name = "SKILL IA",             category = "ia",
                    description = "Modelos de lenguaje, agentes y arquitecturas de inteligencia artificial",
                    tags = "LLM,Claude,GPT,Gemini,DeepSeek,OpenRouter"),
                SkillEntity(name = "SKILL CIBERSEGURIDAD", category = "ciberseguridad",
                    description = "Seguridad de redes, infraestructura y auditoría técnica",
                    tags = "Redes,Infraestructura,Auditoría,Automatización"),
                SkillEntity(name = "SKILL MARKETING",      category = "marketing",
                    description = "Marketing digital, contenido viral y automatización",
                    tags = "Contenido,Reels,Meta Ads,Automatización,Bots,Ventas"),
                SkillEntity(name = "SKILL SISTEMAS",       category = "sistemas",
                    description = "Arquitecturas técnicas, automatización y documentación",
                    tags = "Arquitecturas,Automatización,Documentación,APIs,Cloud"),
                SkillEntity(name = "SKILL FINANZAS",       category = "finanzas",
                    description = "Sistemas financieros, trading y gestión de capital",
                    tags = "Trading,DeFi,Capital,Riesgo,Análisis,Inversión")
            ))
        }
    }

    suspend fun saveLink(url: String, title: String, category: String) {
        linkDao.insert(LinkEntity(url = url, title = title, category = category))
    }

    suspend fun saveLinkEntity(link: LinkEntity): Long = linkDao.insert(link)
    suspend fun updateLink(link: LinkEntity)          = linkDao.update(link)
    suspend fun deleteLink(link: LinkEntity)          = linkDao.delete(link)
    suspend fun saveSkill(skill: SkillEntity): Long   = skillDao.insert(skill)
    suspend fun deleteSkill(skill: SkillEntity)       = skillDao.delete(skill)

    suspend fun addProject(name: String, type: String, description: String) {
        projectDao.insert(ProjectEntity(name = name, type = type, description = description))
    }

    suspend fun addSystem(name: String, type: String, architecture: String) {
        systemDao.insert(SystemEntity(name = name, type = type, architecture = architecture))
    }

    suspend fun addAgent(name: String, model: String, capability: String) {
        agentDao.insert(AgentEntity(name = name, model = model, capability = capability))
    }

    // Semantic memory
    suspend fun saveMemoryNode(node: MemoryNodeEntity): Long = nodeDao.insert(node)
    suspend fun saveMemoryEdge(edge: MemoryEdgeEntity): Long = edgeDao.insert(edge)
    suspend fun deleteMemoryNode(node: MemoryNodeEntity)     = nodeDao.delete(node)

    // Task queue
    val tasks: Flow<List<TaskEntity>> = taskDao.getAll()

    suspend fun submitTask(title: String, description: String, priority: String = "MEDIUM"): Long =
        taskDao.insert(TaskEntity(title = title, description = description, priority = priority))

    suspend fun updateTask(task: TaskEntity)           = taskDao.update(task)
    suspend fun getNextPendingTask(): TaskEntity?      = taskDao.getNextPending()
    suspend fun countDoneTasks(): Int                  = taskDao.countDone()
    suspend fun countPendingTasks(): Int               = taskDao.countPending()
    suspend fun clearDoneTasks()                       = taskDao.clearDone()
    suspend fun deleteTask(task: TaskEntity)           = taskDao.delete(task)
    suspend fun getTasksWithIssueUrl(): List<TaskEntity> = taskDao.getWithIssueUrl()

    suspend fun submitTaskWithIssue(title: String, description: String, priority: String = "MEDIUM", issueUrl: String): Long {
        return taskDao.insert(TaskEntity(
            title = title, description = description,
            priority = priority, issueUrl = issueUrl,
            remoteStatus = "PENDING"
        ))
    }
}
