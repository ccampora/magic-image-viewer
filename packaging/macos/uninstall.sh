#!/usr/bin/env bash
set -euo pipefail

PLIST="$HOME/Library/LaunchAgents/com.magicimageviewer.agent.plist"

launchctl unload "$PLIST" 2>/dev/null || true
rm -f "$PLIST"
sudo rm -f /usr/local/bin/magic-image-viewer-agent

echo "Removed."
