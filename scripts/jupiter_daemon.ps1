#Requires -Version 5.1
<#
.SYNOPSIS
    JupiterDaemon — Puente autónomo JÚPITER ↔ Claude Code PC

.DESCRIPTION
    Monitorea GitHub Issues con label "jupiter-task".
    Para cada issue: ejecuta Claude Code, compila APK, publica release,
    actualiza manifest, comenta resultado, cierra issue.

.PARAMETER ConfigFile
    Ruta al archivo jupiter_config.json (por defecto busca junto al script)

.EXAMPLE
    .\jupiter_daemon.ps1
    .\jupiter_daemon.ps1 -ConfigFile "C:\path\to\config.json"
#>

param(
    [string]$ConfigFile = "$PSScriptRoot\jupiter_config.json"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ─── CONFIG ─────────────────────────────────────────────────────────────────

if (-not (Test-Path $ConfigFile)) {
    Write-Error "Config no encontrado: $ConfigFile`nCrea jupiter_config.json desde jupiter_config.json.template"
    exit 1
}

$cfg = Get-Content $ConfigFile -Raw | ConvertFrom-Json

$PAT          = $cfg.github_pat
$REPO         = $cfg.repo
$GIST_ID      = $cfg.gist_id
$PROJECT_DIR  = $cfg.project_dir
$RELEASES_DIR = $cfg.releases_dir
$LOGS_DIR     = $cfg.logs_dir
$CLAUDE       = $cfg.claude_path
$GH           = $cfg.gh_path
$POLL_SEC     = $cfg.poll_interval_seconds
$API_BASE     = "https://api.github.com/repos/$REPO"

$AUTH_HEADERS = @{
    "Authorization" = "token $PAT"
    "Accept"        = "application/vnd.github.v3+json"
    "User-Agent"    = "JupiterDaemon/1.3"
}

$LOG_FILE = "$LOGS_DIR\jupiter_daemon.log"
New-Item -ItemType Directory -Force -Path $LOGS_DIR | Out-Null
New-Item -ItemType Directory -Force -Path $RELEASES_DIR | Out-Null

# ─── LOGGING ────────────────────────────────────────────────────────────────

function Write-Log {
    param([string]$msg, [string]$level = "INFO")
    $line = "[$( Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] [$level] $msg"
    $line | Add-Content -Path $LOG_FILE -Encoding UTF8
    switch ($level) {
        "ERROR" { Write-Host $line -ForegroundColor Red }
        "WARN"  { Write-Host $line -ForegroundColor Yellow }
        "OK"    { Write-Host $line -ForegroundColor Green }
        default { Write-Host $line }
    }
}

# ─── GITHUB API ─────────────────────────────────────────────────────────────

function Invoke-GhApi {
    param([string]$Method, [string]$Url, [object]$Body = $null)
    $params = @{ Uri = $Url; Headers = $AUTH_HEADERS; Method = $Method; ContentType = "application/json" }
    if ($Body) { $params.Body = ($Body | ConvertTo-Json -Depth 10 -Compress) }
    return Invoke-RestMethod @params
}

function Get-PendingTasks {
    try {
        return Invoke-GhApi GET "$API_BASE/issues?labels=jupiter-task&state=open&sort=created&direction=asc&per_page=5"
    } catch {
        Write-Log "Error obteniendo issues: $_" "ERROR"
        return @()
    }
}

function Set-IssueLabels {
    param([int]$Number, [string[]]$Add = @(), [string[]]$Remove = @())
    try {
        $issue = Invoke-GhApi GET "$API_BASE/issues/$Number"
        $current = @($issue.labels | ForEach-Object { $_.name })
        $newLabels = ($current + $Add | Where-Object { $_ -notin $Remove } | Select-Object -Unique)
        Invoke-GhApi PATCH "$API_BASE/issues/$Number" @{ labels = $newLabels } | Out-Null
    } catch { Write-Log "Error actualizando labels #$Number: $_" "WARN" }
}

function Add-Comment {
    param([int]$Number, [string]$Body)
    try {
        Invoke-GhApi POST "$API_BASE/issues/$Number/comments" @{ body = $Body } | Out-Null
    } catch { Write-Log "Error comentando issue #$Number: $_" "WARN" }
}

function Close-GhIssue {
    param([int]$Number)
    try {
        Invoke-GhApi PATCH "$API_BASE/issues/$Number" @{ state = "closed" } | Out-Null
    } catch { Write-Log "Error cerrando issue #$Number: $_" "WARN" }
}

# ─── CLAUDE CODE ────────────────────────────────────────────────────────────

function Invoke-ClaudeCode {
    param([string]$Prompt, [string]$WorkDir = $PROJECT_DIR)
    Write-Log "  Invocando Claude Code..."

    $tmpPrompt = [System.IO.Path]::GetTempFileName() + ".txt"
    Set-Content -Path $tmpPrompt -Value $Prompt -Encoding UTF8

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName               = $CLAUDE
    $psi.Arguments              = "--print --dangerously-skip-permissions"
    $psi.WorkingDirectory       = $WorkDir
    $psi.RedirectStandardInput  = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError  = $true
    $psi.UseShellExecute        = $false
    $psi.CreateNoWindow         = $true

    $proc = [System.Diagnostics.Process]::Start($psi)
    $content = Get-Content $tmpPrompt -Raw
    $proc.StandardInput.Write($content)
    $proc.StandardInput.Close()

    $stdout = $proc.StandardOutput.ReadToEnd()
    $stderr = $proc.StandardError.ReadToEnd()
    $proc.WaitForExit(300000)  # 5 min timeout

    Remove-Item $tmpPrompt -Force -ErrorAction SilentlyContinue

    if ($proc.ExitCode -ne 0) {
        Write-Log "  Claude Code exit code: $($proc.ExitCode)" "WARN"
    }
    Write-Log "  Claude Code completado."
    return $stdout
}

# ─── BUILD ──────────────────────────────────────────────────────────────────

function Build-Apk {
    Write-Log "  Compilando APK..."
    Push-Location $PROJECT_DIR
    try {
        $env:KEY_STORE_PASSWORD = $cfg.keystore_password
        $env:KEY_ALIAS          = $cfg.key_alias
        $env:KEY_PASSWORD       = $cfg.key_password

        $output = & .\gradlew assembleRelease --no-daemon 2>&1
        $success = ($LASTEXITCODE -eq 0)
        Write-Log "  Build resultado: $(if ($success) { 'SUCCESS' } else { 'FAILED' })" $(if ($success) { 'OK' } else { 'ERROR' })
        return @{ success = $success; output = ($output -join "`n") }
    } finally {
        Pop-Location
    }
}

function Get-VersionFromGradle {
    $content = Get-Content "$PROJECT_DIR\app\build.gradle.kts" -Raw
    $code = [regex]::Match($content, 'versionCode\s*=\s*(\d+)').Groups[1].Value
    $name = [regex]::Match($content, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
    return @{ code = [int]$code; name = $name }
}

# ─── PUBLISH ────────────────────────────────────────────────────────────────

function Upload-Catbox {
    param([string]$FilePath)
    Write-Log "  Subiendo APK a Catbox.moe..."
    $url = & curl.exe -s -F "reqtype=fileupload" -F "fileToUpload=@$FilePath" https://catbox.moe/user/api.php
    if ($url -notlike "https://*") { throw "Catbox upload falló: $url" }
    Write-Log "  Catbox URL: $url" "OK"
    return $url.Trim()
}

function New-GhRelease {
    param([string]$Tag, [string]$ApkPath, [string]$Notes)
    Write-Log "  Creando GitHub Release $Tag..."
    Push-Location $PROJECT_DIR
    try {
        # Create local tag
        & git tag $Tag 2>&1 | Out-Null
        # Push tag (might already exist)
        & git push origin $Tag 2>&1 | Out-Null
        # Create release with APK
        $result = & $GH release create $Tag $ApkPath --title "JÚPITER $Tag" --notes $Notes --repo $REPO 2>&1
        Write-Log "  Release creado: $($result -join '')" "OK"
        return "https://github.com/$REPO/releases/tag/$Tag"
    } finally {
        Pop-Location
    }
}

function Update-GistManifest {
    param([int]$VCode, [string]$VName, [string]$ApkUrl, [string]$ReleaseUrl, [string]$Sha256, [long]$SizeBytes, [string]$Changelog)
    Write-Log "  Actualizando Gist manifest..."

    # Build JSON using Node to avoid PS 5.1 ConvertTo-Json depth issues
    $nodeScript = @"
const m = {
  versionCode: $VCode,
  versionName: '$VName',
  apkUrl: '$ApkUrl',
  releaseUrl: '$ReleaseUrl',
  sha256: '$Sha256',
  sizeBytes: $SizeBytes,
  mandatory: false,
  changelog: ['$Changelog']
};
const payload = JSON.stringify({ files: { 'latest.json': { content: JSON.stringify(m, null, 2) } } });
process.stdout.write(payload);
"@
    $payload = & node.exe -e $nodeScript

    $patchHeaders = $AUTH_HEADERS.Clone()
    $patchHeaders["Content-Type"] = "application/json"
    Invoke-RestMethod -Uri "https://api.github.com/gists/$GIST_ID" -Method PATCH -Headers $patchHeaders -Body $payload | Out-Null
    Write-Log "  Gist actualizado." "OK"
}

# ─── COMMIT & PUSH ──────────────────────────────────────────────────────────

function Push-Changes {
    param([string]$CommitMsg)
    Push-Location $PROJECT_DIR
    try {
        $status = & git status --short 2>&1
        if ($status) {
            & git add -A 2>&1 | Out-Null
            & git commit -m $CommitMsg 2>&1 | Out-Null
            & git push origin main 2>&1 | Out-Null
            Write-Log "  Cambios pusheados: $CommitMsg" "OK"
        } else {
            Write-Log "  Sin cambios para commitear."
        }
    } finally {
        Pop-Location
    }
}

# ─── TASK PROCESSOR ─────────────────────────────────────────────────────────

function Invoke-Task {
    param($Issue)

    $num   = [int]$Issue.number
    $title = $Issue.title
    $body  = $Issue.body

    Write-Log "━━━ Procesando issue #$num: $title" "INFO"
    Set-IssueLabels -Number $num -Add @("jupiter-running") -Remove @("jupiter-task")

    try {
        # Extract ClaudeCodeTask JSON from issue body
        $jsonMatch = [regex]::Match($body, '```json\s*([\s\S]*?)```')
        $taskJson  = if ($jsonMatch.Success) { $jsonMatch.Groups[1].Value } else { "{}" }
        $taskData  = $taskJson | ConvertFrom-Json -ErrorAction SilentlyContinue

        $objective      = if ($taskData.objective)       { $taskData.objective }       else { $title }
        $requestedChange = if ($taskData.requestedChange) { $taskData.requestedChange } else { $body }
        $steps          = if ($taskData.steps)            { $taskData.steps -join "`n" } else { "Ver issue body" }
        $validation     = if ($taskData.validation)       { $taskData.validation }      else { "Build exitoso" }

        # Build Claude Code prompt
        $claudePrompt = @"
Eres Claude Code trabajando en JUPITER Android App.
Directorio del proyecto: $PROJECT_DIR

TAREA RECIBIDA DESDE JÚPITER ANDROID (issue #$num):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

OBJETIVO: $objective

CAMBIO SOLICITADO:
$requestedChange

PASOS:
$steps

VALIDACIÓN: $validation
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Por favor:
1. Lee los archivos relevantes del proyecto
2. Implementa los cambios necesarios
3. Verifica que no hay errores de sintaxis en Kotlin
4. Reporta exactamente qué archivos modificaste y qué cambió

NO ejecutes el build — el daemon lo hará por separado.
Responde en español.
"@

        # Run Claude Code
        $claudeOutput = Invoke-ClaudeCode -Prompt $claudePrompt

        # Build APK
        $build = Build-Apk
        if (-not $build.success) {
            throw "Build falló.`n`nÚltimas líneas de output:`n$( ($build.output -split "`n" | Select-Object -Last 30) -join "`n" )"
        }

        # Find built APK
        $apkFile = Get-ChildItem "$PROJECT_DIR\app\build\outputs\apk\release" -Filter "*.apk" | Select-Object -First 1
        if (-not $apkFile) { throw "APK no encontrado en build/outputs" }

        # Version info
        $ver   = Get-VersionFromGradle
        $vCode = $ver.code
        $vName = $ver.name
        $tag   = "v$vName"

        # SHA256 + copy
        $sha256   = (Get-FileHash $apkFile.FullName -Algorithm SHA256).Hash
        $destPath = "$RELEASES_DIR\jupiter-$tag.apk"
        Copy-Item $apkFile.FullName $destPath -Force
        Write-Log "  APK: $destPath ($sha256)"

        # Upload to Catbox
        $apkUrl = Upload-Catbox -FilePath $destPath

        # Create GitHub Release
        $releaseNotes = "## JÚPITER $tag`n`n**SHA256**: ``$sha256```n`nBuild automático desde issue #$num`n`n*JupiterDaemon*"
        $releaseUrl   = New-GhRelease -Tag $tag -ApkPath $destPath -Notes $releaseNotes

        # Update Gist manifest
        $changelog = "Auto-build #$num: $title"
        Update-GistManifest -VCode $vCode -VName $vName -ApkUrl $apkUrl -ReleaseUrl $releaseUrl -Sha256 $sha256 -SizeBytes $apkFile.Length -Changelog $changelog

        # Commit code changes
        Push-Changes -CommitMsg "feat: auto-build from issue #$num — $title"

        # Comment success on issue
        $successComment = @"
## ✅ JUPITER DAEMON — COMPLETADO

**RESULT**: success
**APK_URL**: $apkUrl
**RELEASE_URL**: $releaseUrl
**SHA256**: $sha256
**VERSION**: $vName (code $vCode)

### Cambios realizados por Claude Code
``````
$($claudeOutput.Substring(0, [Math]::Min($claudeOutput.Length, 2000)))
``````

---
*JupiterDaemon · $( Get-Date -Format 'yyyy-MM-dd HH:mm:ss' )*
"@
        Add-Comment -Number $num -Body $successComment
        Set-IssueLabels -Number $num -Add @("jupiter-done") -Remove @("jupiter-running")
        Close-GhIssue -Number $num
        Write-Log "━━━ Issue #$num COMPLETADO" "OK"

    } catch {
        $err = $_.Exception.Message
        Write-Log "━━━ ERROR en issue #$num: $err" "ERROR"

        $blockedComment = @"
## ❌ JUPITER DAEMON — BLOQUEADO

**BLOCKED**: $err

**Timestamp**: $( Get-Date -Format 'yyyy-MM-dd HH:mm:ss' )

Revisar el issue y reintentar o modificar la tarea.
"@
        Add-Comment -Number $num -Body $blockedComment
        Set-IssueLabels -Number $num -Add @("jupiter-blocked") -Remove @("jupiter-running", "jupiter-task")
    }
}

# ─── MAIN LOOP ───────────────────────────────────────────────────────────────

Write-Log "════════════════════════════════════════════" "OK"
Write-Log "  JUPITER DAEMON v1.3 iniciado" "OK"
Write-Log "  Repo: $REPO" "OK"
Write-Log "  Polling cada $POLL_SEC segundos..." "OK"
Write-Log "════════════════════════════════════════════" "OK"

while ($true) {
    try {
        $tasks = Get-PendingTasks
        if ($tasks.Count -gt 0) {
            Write-Log "Encontradas $($tasks.Count) tarea(s) pendiente(s)."
            foreach ($task in $tasks) {
                Invoke-Task -Issue $task
            }
        } else {
            Write-Log "Sin tareas. Esperando $POLL_SEC s..."
        }
    } catch {
        Write-Log "Error inesperado en main loop: $_" "ERROR"
    }
    Start-Sleep -Seconds $POLL_SEC
}
