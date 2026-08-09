#!/usr/bin/env bash
# Installs the Magic Image Viewer PC agent binary and a launchd agent that
# starts it at login (macOS has no systemd; launchd is the equivalent).
#
# Usage:
#   bash install.sh /path/to/magic-image-viewer-agent

set -euo pipefail

BIN_SRC="${1:?Usage: install.sh /path/to/magic-image-viewer-agent}"
PLIST_SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/com.magicimageviewer.agent.plist"
PLIST_DEST="$HOME/Library/LaunchAgents/com.magicimageviewer.agent.plist"

sudo install -m 755 "$BIN_SRC" /usr/local/bin/magic-image-viewer-agent

mkdir -p "$HOME/Library/LaunchAgents"
cp "$PLIST_SRC" "$PLIST_DEST"

launchctl unload "$PLIST_DEST" 2>/dev/null || true
launchctl load "$PLIST_DEST"

echo "Installed and started. Logs: /tmp/magic-image-viewer-agent.log"
echo "Config: ~/Library/Application Support/magic-image-viewer/config.yaml"
