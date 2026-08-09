#!/usr/bin/env bash
# Build a standalone, single-file executable of the PC agent for the current
# OS using PyInstaller. Run on each target OS (Linux/macOS/Windows via Git
# Bash) — PyInstaller doesn't cross-compile.
#
# Usage:
#   bash packaging/pyinstaller/build.sh
#
# Output: pc-agent/dist/magic-image-viewer-agent[.exe]

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT/pc-agent"

# `python` (not python3): guaranteed present and pointing at Python 3 by
# actions/setup-python on all three OSes; python3 isn't guaranteed on Windows.
python -m pip install -r requirements-build.txt

# `python -m PyInstaller` (not the bare `pyinstaller` command): works even if
# pip's script-install directory isn't on PATH.
python -m PyInstaller --onefile --name magic-image-viewer-agent \
  --distpath dist --workpath build --specpath . \
  --noconfirm \
  agent.py

echo "[build] output: $REPO_ROOT/pc-agent/dist/"
