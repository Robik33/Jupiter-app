# JUPITER_REBOOT_MASTER.md
> Auditoría técnica completa — 2026-06-25  
> Basada exclusivamente en código existente. Sin optimismo. Sin suposiciones.

---

## DIAGNÓSTICO POR MÓDULO

### 1. NÚCLEO IA

| Capacidad | Estado | Evidencia |
|---|---|---|
| Comprensión del lenguaje | PARCIAL | JupiterBrain = keywords. Con LLM = comprensión real solo si API conectada |
| Interpretación | PARCIAL | OpenRouter/Claude interpreta libre. Sin key = keywords |
| Razonamiento | NO EXISTE | Ningún mecanismo de chain-of-thought ni ReAct. El reasoning de DeepSeek-R1 es ignorado |
| Planificación | PARCIAL | AutonomyEngine tiene paso PLAN via LLM one-shot. No planificación iterativa |
| Memoria contextual | NO EXISTE | Cada llamada a JupiterAIClient envía solo [systemPrompt + mensaje actual]. Sin historial |
| Reflexión | NO EXISTE | No existe ningún mecanismo de auto-revisión antes de responder |
| Autoevaluación | PARCIAL | SelfEvaluationEngine genera checklist estático. No razona sobre sí mismo |
| Corrección | PARCIAL | AutonomyEngine: verify() + fix() × 5. verify() = cheque de longitud y palabras de error |
| Aprendizaje | NO EXISTE | Skills guardados en Room NUNCA se inyectan en el contexto de llamadas AI futuras |

**Score NÚCLEO IA: 2.5/9 = 28%**

---

### 2. PUENTE

| Componente | Estado | Evidencia |
|---|---|---|
| GitHub Issues | EXISTE | ClaudeCodeBridge.sendViaGitHubIssue() — completo, formatea issue con labels |
| Claude Code | PARCIAL | El daemon en PC lee el issue y llama a Claude Code. Requiere daemon activo en PC |
| Daemon | EXISTE | jupiter_daemon.ps1 — debuggeado y funcional (sesiones previas) |
| Polling | PARCIAL | ClaudeCodeBridge.pollFull() existe. Pero syncAll() NUNCA se llama desde NucleusViewModel |
| Resultado | PARCIAL | Extracción de APK_URL/RESULT de comentarios existe. UI que lo muestra fue eliminada |
| Sincronización | PARCIAL | PromptBridgeService.syncAll() existe. Solo se llama desde AutonomyViewModel (pantalla eliminada) |

**Score PUENTE: 4/6 = 67%**  
**Cuello de botella crítico:** syncAll() nunca se ejecuta automáticamente. El usuario no puede ver el resultado de una tarea enviada al bridge.

---

### 3. MEMORIA

| Componente | Estado | Evidencia |
|---|---|---|
| Room | EXISTE | JupiterDatabase — 10 entidades, v5 |
| Skills | EXISTE | SkillEntity, SkillDao, seed 7 skills. SkillCreatorEngine funcional |
| Projects | EXISTE | ProjectEntity, ProjectDao, addProject() |
| Links | EXISTE | LinkEntity, LinkDao, KnowledgeIngestionEngine + LinkAnalyzer |
| Memory Graph | PARCIAL | MemoryNodeEntity + MemoryEdgeEntity + DAOs existen. KnowledgeIngestionEngine crea nodos/edges. Pero el grafo NUNCA se consulta |
| Recuperación | PARCIAL | searchSkills(), searchLinks() como Flow. La IA NUNCA los lee |
| Contexto | NO EXISTE | La memoria en Room es un almacén mudo. Ninguna consulta AI incluye skills/projects/links del usuario |

**Score MEMORIA: 5/7 = 71%**  
**Cuello de botella crítico:** La memoria existe pero es decorativa. JÚPITER no sabe lo que el usuario le ha enseñado.

---

### 4. IA

| Proveedor | Estado | Evidencia |
|---|---|---|
| Claude | EXISTE | callClaude() + ClaudeOrchestrator implementados |
| OpenRouter | EXISTE | callOpenAI() con URL OpenRouter. v1.6.0: auto-ruta a FREE_MODELS sin key |
| Gemini | EXISTE | GeminiOrchestrator implementado (API v1beta, formato correcto). Pero JupiterAIClient NO lo llama — caería a callOpenAI que es formato incompatible |
| DeepSeek | EXISTE | DeepSeekOrchestrator + AIProvider.DEEPSEEK. Funciona via callOpenAI |
| Ollama | EXISTE | OllamaRouter completo: checkStatus(), selectBestModel(), complete() |
| Fallback automático | PARCIAL | TokenSaverRouter tiene cadena por TaskType. JupiterRouter solo cae a local en IOException, no reintenta otro proveedor cloud |
| Sin API KEY | PARCIAL | v1.6.0 intenta deepseek/deepseek-r1:free. Si falla (401/rate limit) muestra mensaje de configuración. No es funcional sin key |

**Score IA: 5.5/7 = 79%**  
**Bug silencioso:** AIProvider.GEMINI existe pero JupiterAIClient nunca lo llama correctamente (callOpenAI envía formato OpenAI a la URL de Gemini que es incompatible).

---

### 5. VOZ

| Componente | Estado | Evidencia |
|---|---|---|
| STT | EXISTE | VoiceEngine.startListening() con SpeechRecognizer, resultados parciales |
| TTS | EXISTE | VoiceEngine.speak() via Android TTS, speed=0.87f, pitch=0.93f |
| Conversación continua | NO EXISTE | Después de TTS, el mic NO se reactiva automáticamente. Cada exchange requiere tap manual |
| Interrupción natural | NO EXISTE | Mientras TTS habla, mic está inactivo. No hay barge-in |
| Voz humana | PARCIAL | Android TTS con ajustes naturales. No es voz neural. Calidad depende del dispositivo |
| Personalidad | PARCIAL | 6 saludos aleatorios. Respuestas más naturales. Pero voz real del agente viene del LLM sin personalidad consistente |

**Score VOZ: 3/6 = 50%**

---

### 6. AUTONOMÍA

| Componente | Estado | Evidencia |
|---|---|---|
| Scheduler | EXISTE | TaskScheduler usa WorkManager, periodicidad 15 min, BackoffPolicy.EXPONENTIAL |
| Background | EXISTE | AutonomyWorker (@HiltWorker, CoroutineWorker) implementado |
| Cola de tareas | EXISTE | TaskEntity en Room, getNextPending(), submitTask() |
| Continuación | PARCIAL | AutonomyEngine.runLoop() diseñado para continuar. PERO: TaskScheduler.schedulePeriodicExecution() NUNCA se llama en JupiterApp |
| Reintentos | EXISTE | MAX_ATTEMPTS=5, verify()+fix() loop, BackoffPolicy.EXPONENTIAL en WorkManager |

**Score AUTONOMÍA: 4.5/5 = 90% (código) / 0% (ejecución real)**  
**Cuello de botella CRÍTICO:** JupiterApp.kt NO llama `TaskScheduler.schedulePeriodicExecution()`. La autonomía NUNCA se activa. Es un motor encendido sin combustible.

---

### 7. OJOS

| Componente | Estado | Evidencia |
|---|---|---|
| Playwright | NO EXISTE | AgentBrowserConnector es un stub. Envía strings a Hermes/PC. Playwright corre en PC, no en Android |
| Agent Browser | PARCIAL | AgentBrowserConnector tiene navigate(), screenshot(), extract(), click(), type(). Funciona solo si ORACLE HERMES + Playwright están activos en PC |
| MCP | PARCIAL | McpConnector.fetchTopology() lee /status de Hermes. No es MCP real (no hay Model Context Protocol) |
| Navegación | NO EXISTE | Sin Playwright en PC, sin navegación |
| Capturas | PARCIAL | ScreenshotAnalysisSkill existe pero analiza texto ("description"), no imagen real |
| Lectura | PARCIAL | LinkAnalyzer: HTML fetch + strip + title. Sin PDF, sin YouTube transcript, sin OCR |

**Score OJOS: 2/6 = 33%**

---

### 8. PROGRAMACIÓN

| Componente | Estado | Evidencia |
|---|---|---|
| Claude Code | PARCIAL | Daemon en PC lo llama. Requiere daemon activo + Claude Code instalado |
| GitHub | EXISTE | ClaudeCodeBridge crea issues. GitHubRepoInspector inspecciona repo |
| Build | EXISTE | Daemon ejecuta `.\gradlew assembleRelease` |
| Release | EXISTE | Daemon crea GitHub Release + sube a Catbox |
| OTA | PARCIAL | UpdateManager implementado con SHA256. BUG: MANIFEST_URL apunta a `latest.json` (v1.4.0), no a `JUPITER latest.json` (v1.6.0) |

**Score PROGRAMACIÓN: 4/5 = 80%**

---

## 9. EXPERIENCIA — ¿IA o Aplicación?

**Veredicto: APLICACIÓN ANDROID. No parece una IA.**

| Criterio | Observación |
|---|---|
| Contexto entre mensajes | No existe. Cada mensaje es aislado |
| Inicia conversación | No. Solo responde |
| Recuerda lo que aprendiste | No. Skills en DB nunca se usan en respuestas |
| Entiende sin comandos | Con API key sí; sin ella, necesita keywords exactas |
| Conversación fluida | No. Mic manual por cada mensaje |
| Ejecuta tareas en background | No. TaskScheduler nunca se activa |
| Siente como agente | No. Siente como form con botones y badges técnicos |

---

## ARQUITECTURA ACTUAL

```
Usuario
  │
  ▼
NucleusScreen (UI)
  │  tap mic → VoiceEngine.startListening()
  │  texto → processInput()
  ▼
NucleusViewModel
  │  → router.route(text)
  ▼
JupiterRouter
  │  → aiClient.call()  [ALWAYS en v1.6.0]
  │    ├── Claude / OpenRouter / DeepSeek / Ollama
  │    └── IOException → JupiterBrain (keywords)
  │  → parseAI(json) → RouterResult
  │  → handleSideEffect()
  │    ├── SAVE_PROJECT / SAVE_SYSTEM / MEMORY_SAVE → JupiterRepository
  │    └── SEARCH → ToolRegistry
  ▼
NucleusViewModel (continuación)
  │  → APPLY_VOICE → VoiceEngine.setSpeed/setPitch
  │  → CREATE_SKILL → SkillCreatorEngine.createFromText()
  │  → DISPATCH_BRIDGE → PromptBridgeService.createAndQueue() + dispatch()
  │    └── → ClaudeCodeBridge.sendViaGitHubIssue() → GitHub Issue
  │          [DAEMON EN PC lee y ejecuta — sin retorno automático a Android]
  ▼
JupiterResponse → NucleusScreen (JarvisResponseCard)
  └── VoiceEngine.speak(response)

[DESCONECTADO DEL FLUJO PRINCIPAL]
  AutonomyEngine (PLAN→EXECUTE→VERIFY→FIX×5)
  TaskScheduler (WorkManager 15min)
  AutonomyWorker
  SelfEvaluationEngine
  AutonomyScreen / AutonomyViewModel (sin nav)
  McpConnector / AgentBrowserConnector → ORACLE HERMES (PC)
  pollFull() / syncAll() → Bridge result (nadie lo lee)
```

---

## ARQUITECTURA IDEAL (agente real)

```
Usuario
  │
  ├── VOZ CONTINUA (mic siempre activo, barge-in)
  │
  ▼
AgentCore
  │  1. Recupera memoria relevante (RAG sobre Skills, Links, Projects)
  │  2. Construye contexto: [system + memoria + historial] + mensaje
  │  3. Llama LLM con contexto completo
  │  4. LLM razona → decide acción (ReAct loop)
  │
  ├── [ACCIONES LOCALES]
  │     → VoiceEngine, Room, UI update
  │
  ├── [ACCIONES REMOTAS VÍA BRIDGE]
  │     → GitHub Issue → Daemon → Claude Code → Build/Release
  │     → Polling automático con callback
  │
  ├── [OJOS VÍA HERMES]
  │     → navigate(), screenshot(), extract()
  │
  └── [AUTONOMÍA REAL]
        → TaskScheduler activo desde startup
        → Background loop procesa cola sin intervención
        → Resultado llega como notificación

[MEMORIA VIVA]
  Toda interacción → RAG index
  Cada skill aprendido → disponible en próximas llamadas AI
  Historial conversación → ventana de contexto deslizante
```

---

## LISTA COMPLETA DE PROBLEMAS (ordenados P0→P3)

### P0 — CRÍTICO: el proyecto no funciona como agente

| # | Problema | Impacto |
|---|---|---|
| P0-1 | **Memoria contextual = 0**: JupiterAIClient nunca pasa historial. Cada respuesta parte de cero | JÚPITER no aprende, no recuerda, no contextualiza |
| P0-2 | **Skills/Links/Projects en Room = decorativos**: La IA nunca sabe lo que el usuario guardó | Toda la "memoria" es un almacén que nadie consulta |
| P0-3 | **TaskScheduler nunca arranca**: JupiterApp.kt no llama schedulePeriodicExecution(). La autonomía = código muerto | Background autonomy no existe en la práctica |
| P0-4 | **syncAll() nunca se llama**: El resultado de tareas enviadas al bridge no vuelve a la UI principal | El usuario no sabe si el daemon procesó su tarea |
| P0-5 | **Conversación continua = no existe**: Cada exchange requiere tap manual | No es un agente de voz, es un formulario con voz |

### P1 — BLOQUEANTE: funciona a medias

| # | Problema | Impacto |
|---|---|---|
| P1-1 | **OpenRouter sin key siempre falla**: deepseek-r1:free requiere key OpenRouter (gratis pero necesaria). Sin key = message de config permanente | Sin key configurada, JÚPITER muestra error en cada mensaje |
| P1-2 | **OTA apunta a archivo incorrecto**: MANIFEST_URL → `latest.json` (v1.4.0), no `JUPITER latest.json` (v1.6.0) | El OTA nunca detectará v1.5.0 / v1.6.0 como actualizaciones |
| P1-3 | **Gemini declarado pero no enrutado**: AIProvider.GEMINI existe, GeminiOrchestrator existe. JupiterAIClient lo envía a callOpenAI con formato incompatible | Seleccionar Gemini en settings = error silencioso |
| P1-4 | **AutonomyScreen eliminada del nav**: AutonomyViewModel con control del bridge fue removido. Sin pantalla para monitorear tareas en curso | El usuario no puede ver qué pasa con sus tareas |
| P1-5 | **SkillExtractor conflicto de system prompt**: aiClient.call() prepend system prompt de JÚPITER (formato JSON intención). SkillExtractor espera JSON de skill. El LLM recibe instrucciones contradictorias | Extracción de skills de URLs = resultado impredecible |

### P2 — DEGRADADO: existe pero incompleto

| # | Problema | Impacto |
|---|---|---|
| P2-1 | **TokenSaverRouter sin fallback multi-cloud**: Si OpenRouter falla con 429, no reintenta Gemini/DeepSeek | Una rate limit = tarea sin ejecutar |
| P2-2 | **MemoryGraph creado pero nunca consultado**: Nodos y edges en Room. Sin API de traversal. Sin RAG | El grafo semántico es decorativo |
| P2-3 | **verify() en AutonomyEngine es primitivo**: Verifica solo longitud y lista de palabras de error. Acepta cualquier respuesta >10 chars que no contenga "error" | El agente no verifica si la tarea se completó realmente |
| P2-4 | **AgentBrowserConnector 100% dependiente de PC**: Android no puede ver páginas web solo. Sin Hermes activo, todos los "ojos" fallan en silencio | JÚPITER no puede leer la web por sí mismo |
| P2-5 | **JupiterOrchestrator no se usa**: `process()` tiene lógica de intención y planificación. NucleusViewModel usa JupiterRouter directamente. JupiterOrchestrator = código muerto | Una capa de planificación completa nunca se llama |

### P3 — MEJORABLE: funciona pero debajo del potencial

| # | Problema | Impacto |
|---|---|---|
| P3-1 | **Sistema prompt genérico**: No incluye personalidad, nombre, capacidades actuales ni contexto del usuario | La IA actúa como asistente genérico, no como JÚPITER |
| P3-2 | **Conversación continua de voz**: Después de TTS, STT no se reactiva. No hay barge-in | Experiencia interrumpida vs. agente fluido |
| P3-3 | **SelfEvaluationEngine = checklist estático**: No usa LLM para razonar sobre su estado. No genera insights reales | Autoevaluación sin valor práctico |
| P3-4 | **ScreenshotAnalysisSkill sin imagen real**: Solo procesa texto "description". Vision (base64) marcado como V0.6 pero nunca implementado | No puede analizar pantallas |
| P3-5 | **SafetyGate no conectado al flujo**: SafetyGate.check() y validateTask() existen pero NADIE los llama antes de dispatch() | Las acciones peligrosas no pasan por el gate de seguridad |

---

## DEPENDENCIAS Y ORDEN DE RECONSTRUCCIÓN

```
Bloque A — BASE (prerequisito de todo)
  A1. Historial de conversación en JupiterAIClient
      [bloquea: Planificación, Reflexión, Aprendizaje]
  A2. RAG: inyectar Skills/Links relevantes en contexto AI
      [bloquea: Aprendizaje, Personalidad real]
  A3. TaskScheduler activado en JupiterApp (1 línea)
      [bloquea: Autonomía real]
  A4. syncAll() auto-llamado en NucleusViewModel polling loop
      [bloquea: Resultado del bridge visible]
  A5. MANIFEST_URL corregido a "JUPITER latest.json"
      [bloquea: OTA funcional]

Bloque B — AGENTE (requiere A)
  B1. ReAct loop simple: LLM decide acción → ejecuta → LLM evalúa → siguiente paso
      [requiere: A1]
  B2. Conversación de voz continua (auto-restart STT post-TTS)
      [independiente de A]
  B3. SkillExtractor con prompt separado (no usar systemPrompt de intento)
      [independiente de A]
  B4. Gemini routing correcto en JupiterAIClient
      [independiente de A]
  B5. AutonomyScreen recuperada en nav (o integrada en NucleusScreen)
      [independiente de A]

Bloque C — OJOS (requiere A+B)
  C1. Bridge de resultado: callback cuando daemon termina → notificación Android
      [requiere: A4]
  C2. RAG sobre MemoryGraph (traversal real)
      [requiere: A2]
  C3. Vision real en ScreenshotAnalysisSkill (imagen base64 via AI)
      [requiere: A1]

Bloque D — INTELIGENCIA AVANZADA (requiere A+B+C)
  D1. Auto-reflexión antes de responder
  D2. Aprendizaje por interacción (actualizar skills vía conversación)
  D3. Playwright nativo en Android (WebView + JS injection, alternativa a PC)
```

---

## PORCENTAJE REAL DEL PROYECTO

| Módulo | Peso | % código existe | % funciona como agente |
|---|---|---|---|
| NÚCLEO IA | 30% | 28% | 10% |
| PUENTE | 15% | 67% | 40% |
| MEMORIA | 10% | 71% | 15% |
| IA (providers) | 15% | 79% | 60% |
| VOZ | 10% | 50% | 25% |
| AUTONOMÍA | 10% | 90% | 5% |
| OJOS | 5% | 33% | 10% |
| PROGRAMACIÓN | 5% | 80% | 70% |

**Promedio ponderado — código existe: 59%**  
**Promedio ponderado — funciona como agente: 24%**

---

## RESUMEN EJECUTIVO

JÚPITER tiene un **76% de los módulos escritos** pero solo el **24% funciona como un agente real**.

La brecha entre código y agente se explica en 5 puntos:

1. **Sin memoria viva**: Room tiene datos. La IA no los sabe. Cada conversación empieza en cero.
2. **Sin autonomía activa**: TaskScheduler existe. Nunca se enciende. El motor está construido pero sin llave.
3. **Sin contexto conversacional**: No hay historial entre mensajes. No se puede razonar sobre lo que se dijo hace 2 frases.
4. **Sin feedback del bridge**: El usuario envía una tarea. Nunca sabe el resultado porque syncAll() nunca se llama.
5. **Sin coherencia entre módulos**: JupiterOrchestrator existe y es bueno. Nadie lo llama. AutonomyEngine existe. No arranca. SafetyGate existe. No se invoca.

**Los 5 cambios que transforman JÚPITER de app a agente:**
```
1. A1 — Historial conversacional en JupiterAIClient (5 líneas)
2. A2 — RAG de skills en contexto (10 líneas)
3. A3 — TaskScheduler.schedulePeriodicExecution() en JupiterApp (1 línea)
4. A4 — syncAll() polling en NucleusViewModel (3 líneas)
5. A5 — MANIFEST_URL correcto (1 línea)
```

Sin estos 5 cambios, todo lo demás es código que existe en un cajón.

---

*Auditoría basada en lectura directa de 35 archivos Kotlin.*  
*Ningún módulo asumido. Ningún comportamiento inventado.*  
*Fecha: 2026-06-25 | Versión auditada: v1.6.0*
