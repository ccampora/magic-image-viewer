# Registers the Magic Image Viewer PC agent as a Scheduled Task that starts
# at logon (Windows has no systemd; this is the equivalent autostart hook).
#
# Usage (from an ordinary PowerShell prompt, no admin needed for a per-user task):
#   .\install-task.ps1 -ExePath "C:\path\to\magic-image-viewer-agent.exe"
#
# If -ExePath is omitted, defaults to the recommended install location
# (%LOCALAPPDATA%\magic-image-viewer\magic-image-viewer-agent.exe).

param(
    [string]$ExePath = "$env:LOCALAPPDATA\magic-image-viewer\magic-image-viewer-agent.exe"
)

if (-not (Test-Path $ExePath)) {
    Write-Error "Executable not found at $ExePath. Pass -ExePath, or place the binary there first."
    exit 1
}

$action = New-ScheduledTaskAction -Execute $ExePath
$trigger = New-ScheduledTaskTrigger -AtLogOn
$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable

Register-ScheduledTask -TaskName "MagicImageViewerAgent" `
    -Action $action -Trigger $trigger -Settings $settings `
    -Description "Magic Image Viewer PC agent" -Force

Write-Host "Registered scheduled task 'MagicImageViewerAgent' (runs at logon)."
Write-Host "Start it now with:  Start-ScheduledTask -TaskName MagicImageViewerAgent"
