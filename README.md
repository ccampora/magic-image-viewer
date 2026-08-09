# Magic Image Viewer

Swipe right on a photo in the Android app, and it instantly shows up open on
your PC — fullscreen, replacing whatever photo was open before.

- **`android/`** — native Kotlin app: full-screen photo viewer (swipe up/down
  to browse, swipe right to send), auto-discovers the PC agent on the LAN.
- **`pc-agent/`** — small local server that receives the photo, saves it, and
  launches your configured image viewer. See [`pc-agent/README.md`](pc-agent/README.md).

## How it works

1. `pc-agent` runs on your PC, advertises itself via mDNS, and listens for
   uploads on a local port (default 8787).
2. The Android app discovers it automatically (falls back to manual
   `host:port` entry in Settings if mDNS is blocked on your network).
3. Browsing photos: swipe **up/down** to move between photos, swipe **right**
   to transfer the current photo to the PC.
4. The PC agent saves the file to `/dev/shm` (RAM-backed, never touches disk
   — see [`pc-agent/README.md`](pc-agent/README.md) for details), closes the
   previously opened viewer (if any), and opens the new photo with your
   configured viewer command.

## Status

Verified end-to-end on a real phone and PC: swipe → upload → auto-discovered
PC agent → photo opens fullscreen. Both pieces have real, working fixes for
the non-obvious issues below — worth knowing if you extend either side.

### Android

- `GestureDetector.SimpleOnGestureListener.onDown()` must return `true`, or
  Android's touch dispatch never delivers `ACTION_MOVE`/`ACTION_UP` to the
  listener and `onFling` never fires. Easy to miss — the gesture silently
  does nothing.
- `android:usesCleartextTraffic="true"` is required in the manifest. The PC
  agent speaks plain HTTP on the LAN (no TLS), and Android blocks cleartext
  traffic by default for apps targeting API 28+.

### PC agent

- `local_ip()` doesn't naively trust the OS's default route. On a machine
  with both Wi-Fi and a USB link to the phone (tethering, docking), the
  default route can point at the USB interface even though the phone is only
  reachable over Wi-Fi — which makes the agent advertise an address the
  phone can't reach, and every transfer times out. The agent now prefers an
  interface under `/sys/class/net/*/wireless` (i.e. actual Wi-Fi) when one
  exists.
- The viewer is relaunched fresh for every photo, but the agent kills the
  previous viewer process first — so a fullscreen-capable viewer (e.g. `imv
  -f`) replaces the picture on screen instead of piling up windows.

## Repo layout

```
android/     Kotlin app (Gradle project — open in Android Studio)
pc-agent/    Python agent (Flask + zeroconf)
```
