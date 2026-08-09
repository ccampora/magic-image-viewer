# Magic Image Viewer — PC agent

Small local server that receives photos pushed from the Android app and opens
them with a configured viewer.

## Setup

```bash
cd pc-agent
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
cp config.example.yaml config.yaml   # edit viewer_command as needed
python agent.py
```

### Where photos are saved

By default, received photos go to `/dev/shm` — RAM-backed tmpfs on Linux, so
they never touch disk and are gone on reboot (falls back to the OS temp dir
if `/dev/shm` isn't available, e.g. non-Linux). Set `save_dir` in
`config.yaml` if you'd rather keep them permanently (e.g.
`~/Pictures/FromPhone`). Either way, files aren't deleted automatically after
being opened — clean up `save_dir` yourself if you want that.

The agent advertises itself over mDNS as `magic-image-viewer._magicimg._tcp.local.`
on your chosen port (default 8787), so the Android app can auto-discover it.
If discovery fails (some routers block mDNS), enter this machine's LAN IP
manually in the app's settings.

If this machine has both Wi-Fi and a USB link to the phone (tethering, a
dock), make sure the phone is actually on the same Wi-Fi network as this
machine — the agent prefers its Wi-Fi interface when advertising itself, but
a USB-only setup won't be reachable that way.

### Viewer command

`viewer_command` is run fresh for every incoming photo; `{file}` is replaced
with the saved file's absolute path. The agent kills the *previous* viewer
process right before launching the new one, so a fullscreen-capable viewer
effectively "replaces" what's on screen instead of stacking up windows:

```yaml
viewer_command: "imv -f {file}"     # fullscreen, Wayland/X11 (recommended)
viewer_command: "feh -F {file}"     # fullscreen, X11
viewer_command: "xdg-open {file}"   # OS default viewer (new window each time)
```

## Run as a background service (systemd, Linux)

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
