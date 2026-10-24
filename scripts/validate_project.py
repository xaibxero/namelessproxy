#!/usr/bin/env python3
"""
Nameless Proxy v2.0 Multi-Language Build & Integrity Validator (Python 3)
Verifies AndroidManifest.xml, Go STUN source, POSIX Shell tunnel script, Java sources, and Web UI assets.
"""
import os
import sys
import xml.etree.ElementTree as ET

REQUIRED_FILES = [
    "build.gradle.kts",
    "settings.gradle.kts",
    "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/assets/tunnel.sh",
    "app/src/main/assets/ui/index.html",
    "app/src/main/assets/ui/style.css",
    "app/src/main/assets/ui/app.js",
    "app/src/main/java/com/nameless/proxy/MainActivity.java",
    "app/src/main/java/com/nameless/proxy/core/RootBridge.java",
    "app/src/main/java/com/nameless/proxy/receiver/BootReceiver.java",
    "app/src/main/java/com/nameless/proxy/service/NamelessTileService.java",
    "core/stun-responder/go.mod",
    "core/stun-responder/main.go",
]

def main():
    for path in REQUIRED_FILES:
        if not os.path.isfile(path):
            print(f"ERROR: Missing required file: {path}", file=sys.stderr)
            sys.exit(1)

    # 1. Verify AndroidManifest.xml has MAIN action and LAUNCHER category
    tree = ET.parse("app/src/main/AndroidManifest.xml")
    root = tree.getroot()
    actions = [el.attrib.get("{http://schemas.android.com/apk/res/android}name") for el in root.findall(".//action")]
    categories = [el.attrib.get("{http://schemas.android.com/apk/res/android}name") for el in root.findall(".//category")]

    if "android.intent.action.MAIN" not in actions:
        print("ERROR: Missing android.intent.action.MAIN in AndroidManifest.xml", file=sys.stderr)
        sys.exit(1)
    if "android.intent.category.LAUNCHER" not in categories:
        print("ERROR: Missing android.intent.category.LAUNCHER in AndroidManifest.xml", file=sys.stderr)
        sys.exit(1)

    # 2. Verify tunnel.sh never turns off Android Private DNS or disables IPv6 on physical interfaces
    with open("app/src/main/assets/tunnel.sh", "r", encoding="utf-8") as f:
        sh_text = f.read()
    if "private_dns_mode off" in sh_text:
        print("ERROR: tunnel.sh must never set private_dns_mode off!", file=sys.stderr)
        sys.exit(1)
    if "echo 1 > /proc/sys/net/ipv6/conf/all/disable_ipv6" in sh_text:
        print("ERROR: tunnel.sh must not disable IPv6 on cellular interfaces!", file=sys.stderr)
        sys.exit(1)

    print("Nameless Proxy v2.0 Multi-Language Validation: ALL CHECKS PASSED")

if __name__ == "__main__":
    main()
