#Requires -Version 5.1
<#
.SYNOPSIS
    Registra jupiter_daemon.ps1 como tarea de Windows que arranca con el sistema.

.DESCRIPTION
    Crea una tarea en Task Scheduler que ejecuta el daemon automáticamente
    al iniciar sesión en Windows. Requiere permisos de administrador.

.EXAMPLE
    # Ejecutar como Administrador:
    .\install_jupiter_daemon_startup.ps1
    .\install_jupiter_daemon_startup.ps1 -Uninstall
#>

param(
    [switch]$Uninstall
)

$TASK_NAME        = "JupiterDaemon"
$TASK_DESCRIPTION = "JUPITER Daemon — puente autónomo Android ↔ Claude Code PC"
$DAEMON_SCRIPT    = "$PSScriptRoot\jupiter_daemon.ps1"
$LOG_DIR          = "C:\Users\robik\market-ia\logs"

# ─── CHECK ADMIN ────────────────────────────────────────────────────────────

$currentUser = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal   = New-Object Security.Principal.WindowsPrincipal($currentUser)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Error "Este script requiere permisos de Administrador. Ejecuta PowerShell como Administrador."
    exit 1
}

# ─── UNINSTALL ──────────────────────────────────────────────────────────────

if ($Uninstall) {
    $existing = Get-ScheduledTask -TaskName $TASK_NAME -ErrorAction SilentlyContinue
    if ($existing) {
        Unregister-ScheduledTask -TaskName $TASK_NAME -Confirm:$false
        Write-Host "[OK] Tarea '$TASK_NAME' eliminada de Task Scheduler." -ForegroundColor Green
    } else {
        Write-Host "[INFO] Tarea '$TASK_NAME' no existe." -ForegroundColor Yellow
    }
    exit 0
}

# ─── VALIDATE ───────────────────────────────────────────────────────────────

if (-not (Test-Path $DAEMON_SCRIPT)) {
    Write-Error "Daemon script no encontrado: $DAEMON_SCRIPT"
    exit 1
}

$configPath = "$PSScriptRoot\jupiter_config.json"
if (-not (Test-Path $configPath)) {
    Write-Warning "jupiter_config.json no encontrado. Crea el archivo antes de arrancar el daemon."
    Write-Warning "Template: $PSScriptRoot\jupiter_config.json.template"
}

New-Item -ItemType Directory -Force -Path $LOG_DIR | Out-Null

# ─── CREATE TASK ────────────────────────────────────────────────────────────

# Remove existing task if present
$existing = Get-ScheduledTask -TaskName $TASK_NAME -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "[INFO] Tarea existente encontrada. Reemplazando..." -ForegroundColor Yellow
    Unregister-ScheduledTask -TaskName $TASK_NAME -Confirm:$false
}

# Action: powershell.exe -WindowStyle Hidden -File daemon.ps1
$logFile = "$LOG_DIR\daemon_startup.log"
$action  = New-ScheduledTaskAction `
    -Execute "powershell.exe" `
    -Argument "-NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$DAEMON_SCRIPT`"" `
    -WorkingDirectory $PSScriptRoot

# Trigger: at logon (runs when current user logs in)
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME

# Settings
$settings = New-ScheduledTaskSettingsSet `
    -ExecutionTimeLimit (New-TimeSpan -Days 365) `
    -RestartCount 5 `
    -RestartInterval (New-TimeSpan -Minutes 5) `
    -MultipleInstances IgnoreNew

# Principal: run as current user
$principal = New-ScheduledTaskPrincipal `
    -UserId $env:USERNAME `
    -LogonType Interactive `
    -RunLevel Highest

# Register
Register-ScheduledTask `
    -TaskName $TASK_NAME `
    -Description $TASK_DESCRIPTION `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Principal $principal `
    -Force | Out-Null

Write-Host "" -ForegroundColor Green
Write-Host "════════════════════════════════════════════" -ForegroundColor Green
Write-Host "  JUPITER DAEMON instalado como tarea de Windows" -ForegroundColor Green
Write-Host "════════════════════════════════════════════" -ForegroundColor Green
Write-Host "  Tarea:   $TASK_NAME" -ForegroundColor Cyan
Write-Host "  Script:  $DAEMON_SCRIPT" -ForegroundColor Cyan
Write-Host "  Trigger: Al iniciar sesión ($( $env:USERNAME ))" -ForegroundColor Cyan
Write-Host "  Logs:    $LOG_DIR\jupiter_daemon.log" -ForegroundColor Cyan
Write-Host "" -ForegroundColor Green
Write-Host "Comandos útiles:" -ForegroundColor Yellow
Write-Host "  Iniciar ahora:   Start-ScheduledTask -TaskName '$TASK_NAME'" -ForegroundColor White
Write-Host "  Detener:         Stop-ScheduledTask  -TaskName '$TASK_NAME'" -ForegroundColor White
Write-Host "  Estado:          Get-ScheduledTask   -TaskName '$TASK_NAME' | Select-Object State" -ForegroundColor White
Write-Host "  Desinstalar:     .\install_jupiter_daemon_startup.ps1 -Uninstall" -ForegroundColor White
Write-Host "" -ForegroundColor Green

# Optionally start now
$answer = Read-Host "¿Iniciar el daemon ahora? (s/N)"
if ($answer -eq 's' -or $answer -eq 'S') {
    Start-ScheduledTask -TaskName $TASK_NAME
    Write-Host "[OK] Daemon iniciado." -ForegroundColor Green
}
