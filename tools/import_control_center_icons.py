"""Import Google's Apache-2.0 Material icons as Android vector resources."""

from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ICONS = {
    "wifi": "notification", "bluetooth": "device", "swap_vert": "action",
    "airplanemode_active": "device", "notifications_off": "social",
    "flashlight_on": "device", "content_cut": "content", "battery_saver": "device",
    "screen_lock_rotation": "device", "cast": "hardware", "videocam": "av",
    "link": "content", "search": "action", "more_horiz": "navigation",
    "wb_sunny": "image", "volume_up": "av", "play_arrow": "av",
    "skip_previous": "av", "skip_next": "av", "gps_fixed": "device", "nfc": "device",
}

SOURCE_COMMIT = "bd8cb85bd4bad964fe6918f79665bb40c3a8efef"


def fetch(url):
    return subprocess.check_output(["curl.exe", "-sSfL", "--retry", "2", "--retry-all-errors", "--max-time", "30", url])


def main():
    root = Path(__file__).resolve().parents[1]
    base = f"https://raw.githubusercontent.com/google/material-design-icons/{SOURCE_COMMIT}"
    ns = "http://schemas.android.com/apk/res/android"
    ET.register_namespace("android", ns)
    for name, category in ICONS.items():
        source = ET.fromstring(fetch(f"{base}/src/{category}/{name}/materialicons/24px.svg"))
        vector = ET.Element("vector", {f"{{{ns}}}{key}": value for key, value in {
            "width": "24dp", "height": "24dp", "viewportWidth": "24", "viewportHeight": "24"
        }.items()})
        for node in source.iter():
            tag = node.tag.split("}")[-1]
            if node.get("fill") == "none" or tag in ("svg", "g"): continue
            if node.get("transform"): raise ValueError(f"Unsupported transform in {name}")
            if tag == "path":
                path = node.attrib["d"]
            elif tag == "rect":
                x, y = node.get("x", "0"), node.get("y", "0")
                w, h = node.attrib["width"], node.attrib["height"]
                path = f"M{x},{y}h{w}v{h}h-{w}z"
            else:
                raise ValueError(f"Unsupported {tag} in {name}")
            ET.SubElement(vector, "path", {f"{{{ns}}}fillColor": "#FFFFFFFF", f"{{{ns}}}pathData": path})
        ET.indent(vector)
        target = root / "app/src/main/res/drawable" / f"cc_icon_{name}.xml"
        ET.ElementTree(vector).write(target, encoding="utf-8", xml_declaration=True)
        print(name, flush=True)
    license_dir = root / "third_party/material-icons"
    license_dir.mkdir(parents=True, exist_ok=True)
    license_text = fetch(f"{base}/LICENSE")
    (license_dir / "LICENSE").write_bytes(license_text)
    asset_dir = root / "app/src/main/assets/licenses"
    asset_dir.mkdir(parents=True, exist_ok=True)
    (asset_dir / "material-icons.txt").write_bytes(
        b"Material Icons by Google\nhttps://github.com/google/material-design-icons\n\n" + license_text)


if __name__ == "__main__":
    main()
