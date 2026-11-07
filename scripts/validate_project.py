#!/usr/bin/env python3
"""Static and compiler validator for NamelessProxy v36 repository."""
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

REQUIRED_FILES = [
    "settings.gradle",
    "build.gradle",
    "gradle.properties",
    ".gitignore",
    "README.md",
    ".github/workflows/build.yml",
    "core/stun-responder/go.mod",
    "core/stun-responder/main.go",
    "app/build.gradle",
    "app/proguard-rules.pro",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/assets/tunnel.sh",
    "app/src/main/assets/ui/index.html",
    "app/src/main/assets/ui/style.css",
    "app/src/main/assets/ui/app.js",
    "app/src/main/java/com/nameless/proxy/MainActivity.java",
    "app/src/main/java/com/nameless/proxy/core/ProxyConfig.java",
    "app/src/main/java/com/nameless/proxy/core/RootBridge.java",
    "app/src/main/java/com/nameless/proxy/service/ProxyVpnService.java",
    "app/src/main/java/com/nameless/proxy/service/BootReceiver.java",
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values/themes.xml",
    "app/src/main/res/drawable/ic_launcher.xml",
]


def main():
    errors = []
    for rel in REQUIRED_FILES:
        p = os.path.join(ROOT, rel)
        if not os.path.isfile(p) or os.path.getsize(p) == 0:
            errors.append(f"Missing or empty file: {rel}")

    # 1. Validate XML files parse cleanly
    for xml_rel in [
        "app/src/main/AndroidManifest.xml",
        "app/src/main/res/values/strings.xml",
        "app/src/main/res/values/themes.xml",
        "app/src/main/res/drawable/ic_launcher.xml",
    ]:
        try:
            ET.parse(os.path.join(ROOT, xml_rel))
        except Exception as exc:
            errors.append(f"Invalid XML in {xml_rel}: {exc}")

    # 2. Validate app/build.gradle starts with plugins {}
    with open(os.path.join(ROOT, "app/build.gradle"), "r", encoding="utf-8") as f:
        bg_txt = f.read().lstrip()
    if not bg_txt.startswith("plugins {"):
        errors.append("app/build.gradle must start with plugins {} block")

    # 3. Validate shell syntax of tunnel.sh
    tunnel_sh = os.path.join(ROOT, "app/src/main/assets/tunnel.sh")
    res = subprocess.run(["sh", "-n", tunnel_sh], capture_output=True, text=True)
    if res.returncode != 0:
        errors.append(f"tunnel.sh syntax error: {res.stderr}")

    with open(tunnel_sh, "r", encoding="utf-8") as f:
        sh_txt = f.read()
    if "</dev/null" not in sh_txt:
        errors.append("tunnel.sh background daemons must detach stdin (</dev/null)")
    if "DIRECT_REDIRECT_PORT=10808" not in sh_txt:
        errors.append("tunnel.sh missing DIRECT_REDIRECT_PORT=10808")

    # 4. Validate RootBridge.java has no Process import collision and valid sing-box 1.11.4 config
    rb_path = os.path.join(ROOT, "app/src/main/java/com/nameless/proxy/core/RootBridge.java")
    with open(rb_path, "r", encoding="utf-8") as f:
        rb_txt = f.read()
    if "import android.os.Process;" in rb_txt:
        errors.append("RootBridge.java must not import android.os.Process (collides with java.lang.Process)")
    if '"override_destination"' in rb_txt:
        errors.append("RootBridge.java must NOT use unknown field 'override_destination' in route.rules for sing-box 1.11.4")
    if '"sniff_override_destination"' not in rb_txt:
        errors.append("RootBridge.java must set 'sniff_override_destination' on inbounds")
    if '"direct-redirect-in"' not in rb_txt or '"mixed-in"' not in rb_txt:
        errors.append("RootBridge.java missing direct-redirect-in or mixed-in inbound")

    # 5. Validate DOM IDs between index.html and app.js
    html_path = os.path.join(ROOT, "app/src/main/assets/ui/index.html")
    js_path = os.path.join(ROOT, "app/src/main/assets/ui/app.js")
    with open(html_path, "r", encoding="utf-8") as f:
        html_txt = f.read()
    with open(js_path, "r", encoding="utf-8") as f:
        js_txt = f.read()

    html_ids = set(re.findall(r'id="([^"]+)"', html_txt))
    js_ids = set(re.findall(r'\$\("([^"]+)"\)', js_txt))
    missing_ids = js_ids - html_ids
    if missing_ids:
        errors.append(f"app.js references DOM IDs missing in index.html: {sorted(missing_ids)}")

    for forbidden in ["filterSelected", "filterUnselected", "btnSelectNone", "btnApplyAppsLive"]:
        if forbidden in html_txt:
            errors.append(f"Cluttered button ID still present in index.html: {forbidden}")

    if errors:
        for e in errors:
            print("ERROR:", e, file=sys.stderr)
        sys.exit(1)

    print(f"OK: Validated {len(REQUIRED_FILES)} files, XML, Gradle, shell syntax, DOM IDs, and sing-box 1.11.4 config schema.")


if __name__ == "__main__":
    main()
