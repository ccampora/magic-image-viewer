"""Magic Image Viewer - PC agent.

Advertises itself on the LAN via mDNS, accepts image uploads from the
Android app over HTTP, saves them, and launches a configured viewer.
"""
import argparse
import logging
import os
import platform
import shlex
import socket
import subprocess
import sys
import tempfile
import uuid
from pathlib import Path

if platform.system() == "Linux":
    import fcntl
    import struct

import yaml
from flask import Flask, request, abort
from zeroconf import ServiceInfo, Zeroconf

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("magic-image-viewer")

ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".heic"}
MDNS_SERVICE_TYPE = "_magicimg._tcp.local."


def load_config(path: Path) -> dict:
    if not path.exists():
        log.error("Config file not found: %s (copy config.example.yaml to config.yaml)", path)
        sys.exit(1)
    with path.open() as f:
        cfg = yaml.safe_load(f)

    save_dir = cfg.get("save_dir")
    if save_dir:
        cfg["save_dir"] = Path(save_dir).expanduser()
    else:
        cfg["save_dir"] = _default_save_dir()
    cfg["save_dir"].mkdir(parents=True, exist_ok=True)
    return cfg


def _default_save_dir() -> Path:
    """RAM-backed by default: /dev/shm is conventionally always tmpfs on
    Linux (unlike /tmp, whose backing varies by distro/config), so photos
    never touch disk unless the user opts into a persistent save_dir.
    """
    shm = Path("/dev/shm")
    if shm.is_dir() and os.access(shm, os.W_OK):
        return shm / "magic-image-viewer"
    return Path(tempfile.gettempdir()) / "magic-image-viewer"


def _interface_ipv4(ifname: str) -> str | None:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        packed = fcntl.ioctl(
            s.fileno(),
            0x8915,  # SIOCGIFADDR
            struct.pack("256s", ifname[:15].encode()),
        )
        return socket.inet_ntoa(packed[20:24])
    except OSError:
        return None
    finally:
        s.close()


def _wifi_ip() -> str | None:
    """Prefer a Wi-Fi interface's address: on machines with both Wi-Fi and a
    USB/wired link to the phone (tethering, docks), the default route can
    point at the wrong one even though the phone is only reachable over
    Wi-Fi. /sys/class/net/<iface>/wireless only exists for real Wi-Fi NICs.

    Linux-only (uses fcntl + /sys/class/net); other platforms fall back to
    the default-route heuristic in local_ip().
    """
    if platform.system() != "Linux":
        return None
    net_dir = Path("/sys/class/net")
    if not net_dir.is_dir():
        return None
    for iface_dir in net_dir.iterdir():
        if (iface_dir / "wireless").exists():
            ip = _interface_ipv4(iface_dir.name)
            if ip:
                return ip
    return None


def local_ip() -> str:
    wifi_ip = _wifi_ip()
    if wifi_ip:
        return wifi_ip

    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


def make_app(cfg: dict) -> Flask:
    app = Flask(__name__)

    @app.post("/upload")
    def upload():
        if "file" not in request.files:
            abort(400, "missing 'file' field")
        f = request.files["file"]
        ext = Path(f.filename or "").suffix.lower()
        if ext not in ALLOWED_EXTENSIONS:
            abort(400, f"unsupported file type: {ext}")

        dest = cfg["save_dir"] / f"{uuid.uuid4().hex}{ext}"
        f.save(dest)
        log.info("Received %s (%d bytes) -> %s", f.filename, dest.stat().st_size, dest)

        launch_viewer(cfg["viewer_command"], dest)
        return {"status": "ok", "saved_as": dest.name}, 200

    @app.get("/ping")
    def ping():
        return {"status": "ok", "service": cfg["service_name"]}, 200

    return app


_last_viewer_proc: subprocess.Popen | None = None


def launch_viewer(command_template: str, file_path: Path) -> None:
    global _last_viewer_proc

    if _last_viewer_proc is not None and _last_viewer_proc.poll() is None:
        _last_viewer_proc.terminate()
        try:
            _last_viewer_proc.wait(timeout=2)
        except subprocess.TimeoutExpired:
            _last_viewer_proc.kill()

    # posix=False on Windows so backslashes in paths (e.g. "C:\Program Files\...")
    # aren't treated as shell escape characters.
    cmd = shlex.split(command_template.format(file=str(file_path)), posix=(os.name != "nt"))
    try:
        _last_viewer_proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except FileNotFoundError:
        log.error("Viewer command not found: %s", cmd[0])


def advertise(cfg: dict, port: int) -> tuple[Zeroconf, ServiceInfo]:
    zc = Zeroconf()
    ip = local_ip()
    info = ServiceInfo(
        MDNS_SERVICE_TYPE,
        f"{cfg['service_name']}.{MDNS_SERVICE_TYPE}",
        addresses=[socket.inet_aton(ip)],
        port=port,
        properties={},
        server=f"{socket.gethostname()}.local.",
    )
    zc.register_service(info)
    log.info("Advertising %s on %s:%d via mDNS", cfg["service_name"], ip, port)
    return zc, info


def _default_config_path() -> Path:
    # Respect a config.yaml sitting next to the script (git-clone / manual dev
    # workflow) before falling back to the per-OS user config location — the
    # latter also matters for frozen (PyInstaller) builds, where __file__'s
    # directory is a temp extraction folder, not somewhere to persist config.
    local = Path(__file__).resolve().parent / "config.yaml"
    if local.exists():
        return local

    system = platform.system()
    if system == "Windows":
        base = Path(os.environ.get("APPDATA", Path.home() / "AppData" / "Roaming"))
    elif system == "Darwin":
        base = Path.home() / "Library" / "Application Support"
    else:
        base = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config"))
    return base / "magic-image-viewer" / "config.yaml"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=Path, default=_default_config_path())
    args = parser.parse_args()

    cfg = load_config(args.config)
    port = cfg["port"]

    zc, info = advertise(cfg, port)
    app = make_app(cfg)
    try:
        app.run(host="0.0.0.0", port=port)
    finally:
        zc.unregister_service(info)
        zc.close()


if __name__ == "__main__":
    main()
