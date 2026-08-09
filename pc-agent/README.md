# Magic Image Viewer — PC agent

Small local server that receives photos pushed from the Android app and opens
them with a configured viewer. Runs on Linux, Windows, and macOS.

## Setup

### Arch Linux (pacman package)

If you have a local pacman repo set up, build and publish the package with:

```bash
bash packaging/arch/build-arch.sh          # uses the version already in PKGBUILD
bash packaging/arch/build-arch.sh 0.2.0    # or bump to a new git tag first
```

This pulls the source tarball from the matching `vX.Y.Z` GitHub tag, builds
with `makepkg`, and publishes to the local repo (`$PKGREPO_DIR`, default
`/srv/pkgrepo`) — so tag and push a release before building. Then install
normally:

```bash
sudo pacman -Sy magic-image-viewer
```

The post-install message walks you through config setup and enabling the
systemd service. See [`packaging/arch/PKGBUILD`](../packaging/arch/PKGBUILD).

### Windows / macOS / other Linux (standalone binary)

Pushing a `vX.Y.Z` tag triggers a GitHub Actions build
([`.github/workflows/release-agent.yml`](../.github/workflows/release-agent.yml))
that attaches standalone executables to the release — no Python install
needed. Grab `magic-image-viewer-agent-windows.exe`, `-macos`, or `-linux`
from the [Releases page](https://github.com/ccampora/magic-image-viewer/releases).

**Windows:**
```powershell
mkdir "$env:LOCALAPPDATA\magic-image-viewer"
move magic-image-viewer-agent-windows.exe "$env:LOCALAPPDATA\magic-image-viewer\magic-image-viewer-agent.exe"
mkdir "$env:APPDATA\magic-image-viewer"
copy config.example.yaml "$env:APPDATA\magic-image-viewer\config.yaml"   # edit viewer_command as needed
.\packaging\windows\install-task.ps1   # registers a Scheduled Task that starts it at logon
```

**macOS:**
```bash
chmod +x magic-image-viewer-agent-macos
mkdir -p ~/Library/Application\ Support/magic-image-viewer
cp config.example.yaml ~/Library/Application\ Support/magic-image-viewer/config.yaml   # edit viewer_command as needed
bash packaging/macos/install.sh ./magic-image-viewer-agent-macos   # installs + starts via launchd
```

Uninstall with `packaging/windows/uninstall-task.ps1` or
`packaging/macos/uninstall.sh` respectively.

### Manual (any OS, from source)

```bash
cd pc-agent
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
cp config.example.yaml config.yaml   # edit viewer_command as needed
python agent.py
```

### Building the standalone binary yourself

```bash
bash packaging/pyinstaller/build.sh
```

PyInstaller doesn't cross-compile — this builds an executable for whatever
OS you run it on, output to `pc-agent/dist/`.

### Where photos are saved

By default, received photos go to `/dev/shm` on Linux — RAM-backed tmpfs, so
they never touch disk and are gone on reboot (falls back to the OS temp
directory on Windows/macOS, where there's no equivalent guarantee). Set
`save_dir` in `config.yaml` if you'd rather keep them permanently (e.g.
`~/Pictures/FromPhone`). Either way, files aren't deleted automatically after
being opened — clean up `save_dir` yourself if you want that.

The agent advertises itself over mDNS as `magic-image-viewer._magicimg._tcp.local.`
on your chosen port (default 8787), so the Android app can auto-discover it.
If discovery fails (some routers block mDNS, and Windows Firewall may prompt
to allow it on first run — allow it), enter this machine's LAN IP manually in
the app's settings.

If this machine has both Wi-Fi and a USB link to the phone (tethering, a
dock), make sure the phone is actually on the same Wi-Fi network as this
machine. On Linux the agent prefers its Wi-Fi interface when advertising
itself; on Windows/macOS it just uses the OS's default route, so a USB-only
setup may advertise an address the phone can't reach.

### Viewer command

`viewer_command` is run fresh for every incoming photo; `{file}` is replaced
with the saved file's absolute path. The agent kills the *previous* viewer
process right before launching the new one, so a fullscreen-capable viewer
effectively "replaces" what's on screen instead of stacking up windows. See
the commented examples in `config.example.yaml` for Linux, Windows, and
macOS — pick the one for your OS.

## Run as a background service — manual install (systemd, Linux)

(If you installed via pacman, use the service that ships with the package
instead — see above. Windows/macOS: see the Scheduled Task / launchd steps
above instead of this section.)

```bash
mkdir -p ~/.config/systemd/user
cp magic-image-viewer.service ~/.config/systemd/user/
systemctl --user daemon-reload
systemctl --user enable --now magic-image-viewer.service
journalctl --user -u magic-image-viewer -f   # logs
```

## Manual test

```bash
curl -F "file=@/path/to/photo.jpg" http://localhost:8787/upload
```

This should save the file into `save_dir` and launch `viewer_command`.
