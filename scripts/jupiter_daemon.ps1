#Requires -Version 5.1
<#
.SYNOPSIS
    JupiterDaemon -- Puente autonomo JUPITER <-> Claude Code PC
.DESCRIPTION
    Monitorea GitHub Issues con label "jupiter-task".
    Para cada issue: ejecuta Claude Code, compila APK, publica release,
    actualiza manifest, comenta resultado, cierra issue.
#>

param(
    [string]$ConfigFile = "$PSScriptRoot\jupiter_config.json"
)

# STARTUP TRACE — early diagnostic before config loads (hardcoded path)
"$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') DAEMON START PID=$PID ConfigFile=$ConfigFile" | Add-Content "C:\Users\robik\market-ia\logs\daemon_startup_trace.log"

Set-StrictMode -Off
$ErrorActionPreference = "Continue"

# CONFIG
if (-not (Test-Path $ConfigFile)) {
    Write-Error "Config no encontrado: $ConfigFile"
    exit 1
}

$cfg         = Get-Content $ConfigFile -Raw | ConvertFrom-Json
$PAT         = $cfg.github_pat
$REPO        = $cfg.repo
$GIST_ID     = $cfg.gist_id
$PROJECT_DIR = $cfg.project_dir
$RELEASES_DIR= $cfg.releases_dir
$LOGS_DIR    = $cfg.logs_dir
$CLAUDE      = $cfg.claude_path
$GH          = $cfg.gh_path
$POLL_SEC    = [int]$cfg.poll_interval_seconds
$API_BASE    = "https://api.github.com/repos/$REPO"

$AUTH_HEADERS = @{
    "Authorization" = "token $PAT"
    "Accept"        = "application/vnd.github.v3+json"
    "User-Agent"    = "JupiterDaemon/1.3"
}

New-Item -ItemType Directory -Force -Path $LOGS_DIR    | Out-Null
New-Item -ItemType Directory -Force -Path $RELEASES_DIR| Out-Null
$LOG_FILE = Join-Path $LOGS_DIR "jupiter_daemon.log"

# LOGGING
function Write-Log {
    param([string]$msg, [string]$level = "INFO")
    $ts   = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $line = "[$ts] [$level] $msg"
    $line | Add-Content -Path $LOG_FILE -Encoding UTF8
    switch ($level) {
        "ERROR" { Write-Host $line -ForegroundColor Red }
        "WARN"  { Write-Host $line -ForegroundColor Yellow }
        "OK"    { Write-Host $line -ForegroundColor Green }
        default { Write-Host $line }
    }
}

# GITHUB API
function Invoke-GhApi {
    param([string]$Method, [string]$Url, [hashtable]$Body = @{})
    $p = @{ Uri = $Url; Headers = $AUTH_HEADERS; Method = $Method }
    if ($Body.Count -gt 0) {
        $p.Body        = ($Body | ConvertTo-Json -Depth 10 -Compress)
        $p.ContentType = "application/json"
    }
    return Invoke-RestMethod @p
}

function Get-PendingTasks {
    try {
        $result = Invoke-GhApi "GET" "$API_BASE/issues?labels=jupiter-task&state=open&sort=created&direction=asc&per_page=5"
        return $result
    } catch {
        Write-Log "Error obteniendo issues: $($_.Exception.Message)" "ERROR"
        return @()
    }
}

function Set-IssueLabels {
    param([int]$IssueNum, [string[]]$Add = @(), [string[]]$Remove = @())
    try {
        $issue    = Invoke-GhApi "GET" "$API_BASE/issues/$IssueNum"
        $current  = @($issue.labels | ForEach-Object { $_.name })
        $combined = $current + $Add | Where-Object { $_ -notin $Remove } | Select-Object -Unique
        Invoke-GhApi "PATCH" "$API_BASE/issues/$IssueNum" @{ labels = @($combined) } | Out-Null
    } catch {
        Write-Log "Error labels #${IssueNum}: $($_.Exception.Message)" "WARN"
    }
}

function Add-IssueComment {
    param([int]$IssueNum, [string]$CommentBody)
    try {
        # Use gh CLI to avoid JSON encoding issues
        $tmpFile = Join-Path $env:TEMP "jd_comment_${IssueNum}.json"
        $nodeScript = "const fs=require('fs'); fs.writeFileSync('$($tmpFile -replace '\\','/')', JSON.stringify({body: process.argv[1]}), 'utf8');"
        & node.exe -e $nodeScript -- $CommentBody
        & $GH api "repos/$REPO/issues/$IssueNum/comments" --method POST --input $tmpFile | Out-Null
        Remove-Item $tmpFile -Force -ErrorAction SilentlyContinue
    } catch {
        Write-Log "Error comentando #${IssueNum}: $($_.Exception.Message)" "WARN"
    }
}

function Close-Issue {
    param([int]$IssueNum)
    try {
        Invoke-GhApi "PATCH" "$API_BASE/issues/$IssueNum" @{ state = "closed" } | Out-Null
    } catch {
        Write-Log "Error cerrando #${IssueNum}: $($_.Exception.Message)" "WARN"
    }
}

# CLAUDE CODE
function Invoke-ClaudeCode {
    param([string]$Prompt, [string]$WorkDir = $PROJECT_DIR)
    Write-Log "  Invocando Claude Code..."

    $tmpIn = Join-Path $env:TEMP "jd_prompt.txt"
    Set-Content -Path $tmpIn -Value $Prompt -Encoding UTF8

    Push-Location $WorkDir
    try {
        # Pipe prompt file into claude via PowerShell — works in non-interactive Task Scheduler sessions
        $claudeExe = $CLAUDE -replace '/', '\'
        $output = Get-Content $tmpIn -Raw | & $claudeExe --print --dangerously-skip-permissions 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    Remove-Item $tmpIn -Force -ErrorAction SilentlyContinue
    Write-Log "  Claude Code completado (exit: $exitCode)."
    return ($output -join "`n")
}

# BUILD
function Build-Apk {
    Write-Log "  Compilando APK..."
    Push-Location $PROJECT_DIR
    try {
        $env:KEY_STORE_PASSWORD = $cfg.keystore_password
        $env:KEY_ALIAS          = $cfg.key_alias
        $env:KEY_PASSWORD       = $cfg.key_password
        $output = & .\gradlew assembleRelease --no-daemon 2>&1
        $ok = ($LASTEXITCODE -eq 0)
        Write-Log "  Build: $(if ($ok) { 'SUCCESS' } else { 'FAILED' })" $(if ($ok) { 'OK' } else { 'ERROR' })
        return @{ success = $ok; output = ($output -join "`n") }
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

# PUBLISH
function Upload-Catbox {
    param([string]$FilePath)
    Write-Log "  Subiendo APK a Catbox.moe..."
    $url = & curl.exe -s -F "reqtype=fileupload" -F "fileToUpload=@$FilePath" https://catbox.moe/user/api.php
    if ($url -notlike "https://*") { throw "Catbox upload fallo: $url" }
    Write-Log "  Catbox URL: $url" "OK"
    return $url.Trim()
}

function New-GhRelease {
    param([string]$Tag, [string]$ApkPath, [string]$Notes)
    Write-Log "  Creando GitHub Release $Tag..."
    Push-Location $PROJECT_DIR
    try {
        # Tag: delete local if exists to allow re-tag on same commit
        & git tag -d $Tag 2>&1 | Out-Null
        & git tag $Tag 2>&1 | Out-Null
        & git push origin $Tag 2>&1 | Out-Null

        # Release: create or upload to existing
        $existing = & $GH release view $Tag --repo $REPO 2>&1
        if ($LASTEXITCODE -eq 0) {
            # Release already exists — upload APK to it
            & $GH release upload $Tag $ApkPath --repo $REPO --clobber 2>&1 | Out-Null
            Write-Log "  Release existente actualizado con nuevo APK." "OK"
        } else {
            & $GH release create $Tag $ApkPath --title "JUPITER $Tag" --notes $Notes --repo $REPO 2>&1 | Out-Null
            Write-Log "  Release creado." "OK"
        }
        return "https://github.com/$REPO/releases/tag/$Tag"
    } finally {
        Pop-Location
    }
}

function Update-GistManifest {
    param([int]$VCode, [string]$VName, [string]$ApkUrl, [string]$ReleaseUrl,
          [string]$Sha256, [long]$SizeBytes, [string]$Changelog)
    Write-Log "  Actualizando Gist manifest..."
    $tmpJs  = Join-Path $env:TEMP "jd_gist.js"
    $tmpPay = Join-Path $env:TEMP "jd_gist_payload.json"
    $jsBody = "const fs=require('fs');" +
              "const m={versionCode:$VCode,versionName:'$VName',apkUrl:'$ApkUrl',releaseUrl:'$ReleaseUrl',sha256:'$Sha256',sizeBytes:$SizeBytes,mandatory:false,changelog:['$Changelog']};" +
              "const p=JSON.stringify({files:{'latest.json':{content:JSON.stringify(m,null,2)}}});" +
              "fs.writeFileSync('$($tmpPay -replace '\\','/')',p,'utf8');"
    Set-Content -Path $tmpJs -Value $jsBody -Encoding UTF8
    & node.exe $tmpJs
    Remove-Item $tmpJs -Force -ErrorAction SilentlyContinue
    & $GH api "gists/$GIST_ID" --method PATCH --input $tmpPay | Out-Null
    Remove-Item $tmpPay -Force -ErrorAction SilentlyContinue
    Write-Log "  Gist actualizado." "OK"
}

function Push-Changes {
    param([string]$CommitMsg)
    Push-Location $PROJECT_DIR
    try {
        # Only stage tracked files — never git add -A (would include hprof/crash dumps)
        & git add -u 2>&1 | Out-Null
        $status = & git status --short 2>&1
        if ($status) {
            & git commit -m $CommitMsg 2>&1 | Out-Null
            & git push origin main 2>&1 | Out-Null
            Write-Log "  Push: $CommitMsg" "OK"
        }
    } finally {
        Pop-Location
    }
}

# TASK PROCESSOR
function Invoke-Task {
    param($Issue)

    $num   = [int]$Issue.number
    $title = [string]$Issue.title
    $body  = [string]$Issue.body

    Write-Log "=== Procesando issue #${num}: $title"
    Set-IssueLabels -IssueNum $num -Add @("jupiter-running") -Remove @("jupiter-task")

    try {
        # Extraer JSON de tarea del body
        $jsonMatch = [regex]::Match($body, '```json\s*([\s\S]*?)```')
        $taskData  = if ($jsonMatch.Success) {
            $jsonMatch.Groups[1].Value | ConvertFrom-Json -ErrorAction SilentlyContinue
        } else { $null }

        $objective = if ($taskData -and $taskData.objective) { $taskData.objective } else { $title }
        $change    = if ($taskData -and $taskData.requestedChange) { $taskData.requestedChange } else { $body }
        $steps     = if ($taskData -and $taskData.steps) { $taskData.steps -join "`n" } else { "Ver issue body" }

        $claudePrompt = "TAREA JUPITER #${num}: $objective`n`nCAMBIO: $change`n`nPASOS:`n$steps`n`nImplementa los cambios en el proyecto. NO ejecutes el build. Reporta que archivos modificaste."

        # 1. Claude Code
        $claudeOutput = Invoke-ClaudeCode -Prompt $claudePrompt

        # 2. Build
        $build = Build-Apk
        if (-not $build.success) {
            throw "Build fallo. Output: $($build.output | Select-Object -Last 20)"
        }

        # 3. APK info
        $apkFile = Get-ChildItem "$PROJECT_DIR\app\build\outputs\apk\release" -Filter "*.apk" | Select-Object -First 1
        if (-not $apkFile) { throw "APK no encontrado" }

        $ver    = Get-VersionFromGradle
        $vCode  = $ver.code
        $vName  = $ver.name
        $tag    = "v$vName"
        $sha256 = (Get-FileHash $apkFile.FullName -Algorithm SHA256).Hash
        $dest   = Join-Path $RELEASES_DIR "jupiter-$tag.apk"
        Copy-Item $apkFile.FullName $dest -Force

        # 4. Catbox upload
        $apkUrl = Upload-Catbox -FilePath $dest

        # 5. GitHub Release
        $notes      = "JUPITER $tag - SHA256: $sha256 - Build desde issue #${num}"
        $releaseUrl = New-GhRelease -Tag $tag -ApkPath $dest -Notes $notes

        # 6. Gist manifest
        Update-GistManifest -VCode $vCode -VName $vName -ApkUrl $apkUrl -ReleaseUrl $releaseUrl -Sha256 $sha256 -SizeBytes $apkFile.Length -Changelog "Auto-build #${num}: $title"

        # 7. Push code
        Push-Changes -CommitMsg "feat: auto-build issue #${num} - $title"

        # 8. Comment + close
        $claudeSummary = if ($claudeOutput) { $claudeOutput.Substring(0, [Math]::Min(500, $claudeOutput.Length)) } else { "(sin salida)" }
        $ok = "JUPITER DAEMON - COMPLETADO`n`nRESULT: success`nAPK_URL: $apkUrl`nRELEASE_URL: $releaseUrl`nSHA256: $sha256`nVERSION: $vName (code $vCode)`n`nClaude Code: $claudeSummary`n`nDaemon ${tag} - $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
        Add-IssueComment -IssueNum $num -CommentBody $ok
        Set-IssueLabels -IssueNum $num -Add @("jupiter-done") -Remove @("jupiter-running")
        Close-Issue -IssueNum $num
        Write-Log "=== Issue #${num} COMPLETADO" "OK"

    } catch {
        $errMsg = $_.Exception.Message
        Write-Log "=== ERROR issue #${num}: $errMsg" "ERROR"
        $blocked = "JUPITER DAEMON - BLOQUEADO`n`nBLOCKED: $errMsg`nTimestamp: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
        Add-IssueComment -IssueNum $num -CommentBody $blocked
        Set-IssueLabels -IssueNum $num -Add @("jupiter-blocked") -Remove @("jupiter-running", "jupiter-task")
    }
}

# MAIN LOOP
Write-Log "============================================" "OK"
Write-Log "  JUPITER DAEMON v1.3 iniciado" "OK"
Write-Log "  Repo: $REPO" "OK"
Write-Log "  Polling cada $POLL_SEC segundos" "OK"
Write-Log "============================================" "OK"

while ($true) {
    try {
        $tasks = @(Get-PendingTasks)
        if ($tasks.Count -gt 0) {
            Write-Log "Encontradas $($tasks.Count) tarea(s) pendiente(s)."
            foreach ($t in $tasks) { Invoke-Task -Issue $t }
        } else {
            Write-Log "Sin tareas. Esperando ${POLL_SEC}s..."
        }
    } catch {
        Write-Log "Error en main loop: $($_.Exception.Message)" "ERROR"
    }
    Start-Sleep -Seconds $POLL_SEC
}
