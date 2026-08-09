# Removes the scheduled task installed by install-task.ps1.
Unregister-ScheduledTask -TaskName "MagicImageViewerAgent" -Confirm:$false
Write-Host "Removed scheduled task 'MagicImageViewerAgent'."
