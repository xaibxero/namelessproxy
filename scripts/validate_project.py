#!/usr/bin/env python3
"""
Nameless Proxy Multi-Language Build & Cross-Layer Integrity Validator (Python 3)
Verifies:
1. All required files exist.
2. AndroidManifest.xml has MAIN action and LAUNCHER category.
3. tunnel.sh never turns off Android Private DNS or disables IPv6 on physical interfaces,
   and scopes Table 2088 (lo) rules to `ipproto tcp` so WebRTC UDP getsockname() succeeds.
4. Every `window.NamelessBridge.<method>` called in `app.js` exists as a `@JavascriptInterface`
   method in `MainActivity.java`.
5. Every `document.getElementById("<id>")` called in `app.js` exists in `index.html`.
"""
import os
import re
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

    # 2. Verify tunnel.sh safety and WebRTC Table 2088 TCP-scoping
    with open("app/src/main/assets/tunnel.sh", "r", encoding="utf-8") as f:
        sh_text = f.read()
    if "private_dns_mode off" in sh_text:
        print("ERROR: tunnel.sh must never set private_dns_mode off!", file=sys.stderr)
        sys.exit(1)
    if "echo 1 > /proc/sys/net/ipv6/conf/all/disable_ipv6" in sh_text:
        print("ERROR: tunnel.sh must not disable IPv6 on cellular interfaces!", file=sys.stderr)
        sys.exit(1)
    if "ip rule add ipproto tcp to 198.18.0.0/15 lookup 2088 pref 8806" not in sh_text:
        print("ERROR: tunnel.sh must scope 198.18.0.0/15 Table 2088 rule to ipproto tcp!", file=sys.stderr)
        sys.exit(1)

    # 3. Verify all NamelessBridge calls in app.js exist in MainActivity.java
    with open("app/src/main/assets/ui/app.js", "r", encoding="utf-8") as f:
        js_text = f.read()
    with open("app/src/main/java/com/nameless/proxy/MainActivity.java", "r", encoding="utf-8") as f:
        java_text = f.read()

    js_bridge_calls = set(re.findall(r"NamelessBridge\.([a-zA-Z0-9_]+)\s*\(", js_text))
    java_bridge_methods = set(re.findall(r"@JavascriptInterface\s+public\s+[A-Za-z0-9_<>\[\]]+\s+([a-zA-Z0-9_]+)\s*\(", java_text))
    missing_methods = js_bridge_calls - java_bridge_methods
    if missing_methods:
        print(f"ERROR: app.js calls missing NamelessBridge methods: {missing_methods}", file=sys.stderr)
        sys.exit(1)

    # 4. Verify all document.getElementById calls in app.js exist in index.html
    with open("app/src/main/assets/ui/index.html", "r", encoding="utf-8") as f:
        html_text = f.read()
    html_ids = set(re.findall(r'id="([^"]+)"', html_text))
    js_ids = set(re.findall(r'getElementById\("([^"]+)"\)', js_text))
    missing_ids = js_ids - html_ids
    if missing_ids:
        print(f"ERROR: app.js references missing HTML IDs: {missing_ids}", file=sys.stderr)
        sys.exit(1)

    # 5. Verify all onclick="fn(...)" handlers in index.html exist in app.js
    onclick_fns = set(re.findall(r'onclick="([a-zA-Z0-9_]+)\(', html_text))
    js_fns = set(re.findall(r'function\s+([a-zA-Z0-9_]+)\s*\(', js_text))
    missing_fns = onclick_fns - js_fns
    if missing_fns:
        print(f"ERROR: index.html onclick references missing JS functions: {missing_fns}", file=sys.stderr)
        sys.exit(1)

    print(f"Verified {len(js_bridge_calls)} Bridge methods, {len(js_ids)} DOM IDs, and {len(onclick_fns)} UI handlers.")
    print("Nameless Proxy Multi-Language Validation: ALL CHECKS PASSED")

if __name__ == "__main__":
    main()
