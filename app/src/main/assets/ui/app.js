(function () {
  "use strict";

  const $ = (id) => document.getElementById(id);

  const state = {
    slots: [
      {
        profileName: "Slot 1",
        host: "",
        port: 1080,
        type: "socks5",
        username: "",
        password: "",
        dnsMode: "auto",
        appMode: "global",
        selectedApps: [],
        hotspotShare: true,
        killSwitch: false,
        autoStartOnBoot: false
      }
    ],
    activeSlotIndex: 0,
    installedApps: [],
    status: {
      running: false,
      connecting: false,
      step: "Ready",
      lastError: "",
      userId: 0,
      exitIp: "",
      webrtcIp: "",
      country: "",
      countryCode: "",
      isp: ""
    },
    lastShownError: "",
    wasRunning: false,
    popupTimer: null,
    liveReloadTimer: null
  };

  function showPopup(icon, title, sub) {
    const popup = $("glassPopup");
    if (!popup) return;
    if ($("popupIcon")) $("popupIcon").textContent = icon || "✓";
    if ($("popupTitle")) $("popupTitle").textContent = title || "";
    if ($("popupSub")) $("popupSub").textContent = sub || "";
    popup.classList.add("show");
    if (state.popupTimer) clearTimeout(state.popupTimer);
    state.popupTimer = setTimeout(() => {
      popup.classList.remove("show");
    }, 3200);
  }

  function currentSlot() {
    if (!Array.isArray(state.slots) || state.slots.length === 0) {
      state.slots = [
        {
          profileName: "Slot 1",
          host: "",
          port: 1080,
          type: "socks5",
          username: "",
          password: "",
          dnsMode: "auto",
          appMode: "global",
          selectedApps: [],
          hotspotShare: true,
          killSwitch: false,
          autoStartOnBoot: false
        }
      ];
      state.activeSlotIndex = 0;
    }
    if (state.activeSlotIndex < 0 || state.activeSlotIndex >= state.slots.length) {
      state.activeSlotIndex = 0;
    }
    return state.slots[state.activeSlotIndex];
  }

  function syncFormToCurrentSlot() {
    const slot = currentSlot();
    if ($("inpSlotName")) slot.profileName = $("inpSlotName").value.trim() || ("Slot " + (state.activeSlotIndex + 1));
    if ($("inpHost")) slot.host = $("inpHost").value.trim();
    if ($("inpPort")) slot.port = parseInt($("inpPort").value, 10) || 1080;
    if ($("inpType")) slot.type = $("inpType").value || "socks5";
    if ($("inpUser")) slot.username = $("inpUser").value.trim();
    if ($("inpPass")) slot.password = $("inpPass").value.trim();
    if ($("chkHotspotShare")) slot.hotspotShare = !!$("chkHotspotShare").checked;
    if ($("chkAutoBoot")) slot.autoStartOnBoot = !!$("chkAutoBoot").checked;
    if ($("selDnsMode")) slot.dnsMode = $("selDnsMode").value || "auto";

    slot.appMode = (Array.isArray(slot.selectedApps) && slot.selectedApps.length > 0) ? "per_app" : "global";
    persistState();
    return slot;
  }

  function loadCurrentSlotIntoForm() {
    const slot = currentSlot();
    if ($("inpSlotName")) $("inpSlotName").value = slot.profileName || ("Slot " + (state.activeSlotIndex + 1));
    if ($("inpHost")) $("inpHost").value = slot.host || "";
    if ($("inpPort")) $("inpPort").value = slot.port || 1080;
    if ($("inpType")) $("inpType").value = slot.type || "socks5";
    if ($("inpUser")) $("inpUser").value = slot.username || "";
    if ($("inpPass")) $("inpPass").value = slot.password || "";
    if ($("chkHotspotShare")) $("chkHotspotShare").checked = slot.hotspotShare !== false;
    if ($("chkAutoBoot")) $("chkAutoBoot").checked = !!slot.autoStartOnBoot;
    if ($("selDnsMode")) $("selDnsMode").value = slot.dnsMode || "auto";
    renderSlotStrip();
    renderAppList();
    updateRoutingSummary();
  }

  function persistState() {
    const slot = currentSlot();
    try {
      if (window.AndroidBridge) {
        window.AndroidBridge.saveSlots(JSON.stringify(state.slots), state.activeSlotIndex);
        window.AndroidBridge.saveConfig(JSON.stringify(slot));
      } else {
        localStorage.setItem("nameless_slots", JSON.stringify(state.slots));
        localStorage.setItem("nameless_idx", String(state.activeSlotIndex));
      }
    } catch (e) {}
  }

  function renderSlotStrip() {
    const strip = $("slotStrip");
    if (!strip) return;
    strip.innerHTML = "";
    state.slots.forEach((slot, idx) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "slot-chip" + (idx === state.activeSlotIndex ? " active" : "");
      const label = slot.profileName || ("Slot " + (idx + 1));
      btn.textContent = slot.host ? (label + " • " + slot.host) : label;
      btn.addEventListener("click", () => {
        syncFormToCurrentSlot();
        state.activeSlotIndex = idx;
        loadCurrentSlotIntoForm();
        showPopup("⚡", "Switched to " + (slot.profileName || ("Slot " + (idx + 1))), slot.host ? (slot.host + ":" + slot.port) : "Configure host & port in Proxy tab");
      });
      strip.appendChild(btn);
    });
  }

  function parseQuickProxyString(raw) {
    if (!raw) return false;
    let s = raw.trim();
    let proto = null;
    if (s.startsWith("socks5://") || s.startsWith("socks://")) {
      proto = "socks5";
      s = s.replace(/^socks5?:\/\//i, "");
    } else if (s.startsWith("http://") || s.startsWith("https://")) {
      proto = "http";
      s = s.replace(/^https?:\/\//i, "");
    }

    // Format 1: user:pass@host:port
    if (s.includes("@")) {
      const [cred, endpoint] = s.split("@");
      const [u, p] = cred.split(":");
      const [h, pt] = endpoint.split(":");
      if (h && pt) {
        if ($("inpHost")) $("inpHost").value = h.trim();
        if ($("inpPort")) $("inpPort").value = parseInt(pt.trim(), 10) || 1080;
        if ($("inpUser")) $("inpUser").value = (u || "").trim();
        if ($("inpPass")) $("inpPass").value = (p || "").trim();
        if (proto && $("inpType")) $("inpType").value = proto;
        return true;
      }
    }

    // Format 2: host:port:user:pass or host:port
    const parts = s.split(":");
    if (parts.length >= 2) {
      if ($("inpHost")) $("inpHost").value = parts[0].trim();
      if ($("inpPort")) $("inpPort").value = parseInt(parts[1].trim(), 10) || 1080;
      if (parts.length >= 4) {
        if ($("inpUser")) $("inpUser").value = parts[2].trim();
        if ($("inpPass")) $("inpPass").value = parts.slice(3).join(":").trim();
      }
      if (proto && $("inpType")) $("inpType").value = proto;
      return true;
    }
    return false;
  }

  function updateRoutingSummary() {
    const slot = currentSlot();
    const sel = Array.isArray(slot.selectedApps) ? slot.selectedApps : [];
    const total = state.installedApps.length;
    const count = sel.length;

    if ($("appSelCountPill")) {
      $("appSelCountPill").textContent = count + " Selected";
    }
    if ($("appRoutingModeHint")) {
      if (count === 0) {
        $("appRoutingModeHint").textContent = "0 selected = Global Mode (All Apps Routed)";
      } else {
        $("appRoutingModeHint").textContent = "Per-App Mode: Routing " + count + " selected app(s) through proxy";
      }
    }
    if ($("btnToggleSelectAll")) {
      const allSelected = total > 0 && count >= total;
      $("btnToggleSelectAll").textContent = allSelected ? "Deselect All" : "Select All";
    }
    if ($("valRouteMode")) {
      $("valRouteMode").textContent = count > 0 ? ("Per-App (" + count + ")") : "Global Mode";
    }
    if ($("valHotspotState")) {
      $("valHotspotState").textContent = slot.hotspotShare !== false ? "Hotspot Share ON" : "Hotspot Share OFF";
    }
  }

  function scheduleLiveReloadIfRunning() {
    const slot = syncFormToCurrentSlot();
    updateRoutingSummary();
    if (!state.status.running) return;
    if (state.liveReloadTimer) clearTimeout(state.liveReloadTimer);
    state.liveReloadTimer = setTimeout(() => {
      if (window.AndroidBridge && window.AndroidBridge.reloadAppsLive) {
        window.AndroidBridge.reloadAppsLive(JSON.stringify(slot));
      }
    }, 300);
  }

  function renderAppList() {
    const container = $("appListContainer");
    if (!container) return;
    const slot = currentSlot();
    if (!Array.isArray(slot.selectedApps)) slot.selectedApps = [];
    const selectedSet = new Set(slot.selectedApps);
    const query = ($("appSearchInput") ? $("appSearchInput").value : "").trim().toLowerCase();

    const filtered = state.installedApps.filter((app) => {
      if (!query) return true;
      return (app.name && app.name.toLowerCase().includes(query)) ||
             (app.pkg && app.pkg.toLowerCase().includes(query));
    });

    // Sort selected apps to top for fast visibility
    filtered.sort((a, b) => {
      const aSel = selectedSet.has(a.pkg) ? 1 : 0;
      const bSel = selectedSet.has(b.pkg) ? 1 : 0;
      if (aSel !== bSel) return bSel - aSel;
      return (a.name || "").localeCompare(b.name || "");
    });

    container.innerHTML = "";
    if (filtered.length === 0) {
      const empty = document.createElement("div");
      empty.className = "card-sub";
      empty.style.padding = "18px 8px";
      empty.style.textAlign = "center";
      empty.textContent = "No matching apps found.";
      container.appendChild(empty);
      updateRoutingSummary();
      return;
    }

    filtered.forEach((app) => {
      const isSelected = selectedSet.has(app.pkg);
      const row = document.createElement("div");
      row.className = "app-item" + (isSelected ? " selected" : "");

      const meta = document.createElement("div");
      meta.className = "app-meta";
      const title = document.createElement("div");
      title.className = "app-name";
      title.textContent = app.name || app.pkg;
      const pkg = document.createElement("div");
      pkg.className = "app-pkg";
      pkg.textContent = app.pkg + (app.uid ? (" • UID " + app.uid) : "");
      meta.appendChild(title);
      meta.appendChild(pkg);

      const swLabel = document.createElement("label");
      swLabel.className = "m3-switch";
      const chk = document.createElement("input");
      chk.type = "checkbox";
      chk.checked = isSelected;
      const slider = document.createElement("span");
      slider.className = "m3-slider";
      swLabel.appendChild(chk);
      swLabel.appendChild(slider);

      chk.addEventListener("change", () => {
        const curSlot = currentSlot();
        if (!Array.isArray(curSlot.selectedApps)) curSlot.selectedApps = [];
        if (chk.checked) {
          if (!curSlot.selectedApps.includes(app.pkg)) {
            curSlot.selectedApps.push(app.pkg);
          }
          row.classList.add("selected");
        } else {
          curSlot.selectedApps = curSlot.selectedApps.filter((p) => p !== app.pkg);
          row.classList.remove("selected");
        }
        scheduleLiveReloadIfRunning();
      });

      row.appendChild(meta);
      row.appendChild(swLabel);
      container.appendChild(row);
    });

    updateRoutingSummary();
  }

  function toggleSelectAllApps() {
    const slot = currentSlot();
    if (!Array.isArray(slot.selectedApps)) slot.selectedApps = [];
    const total = state.installedApps.length;
    if (total === 0) return;

    if (slot.selectedApps.length >= total) {
      slot.selectedApps = [];
      showPopup("🌐", "Deselected All Apps", "Switched to Global Mode (All Apps Routed)");
    } else {
      slot.selectedApps = state.installedApps.map((a) => a.pkg);
      showPopup("✓", "Selected All " + total + " Apps", "All listed apps routed through proxy");
    }
    renderAppList();
    scheduleLiveReloadIfRunning();
  }

  function toggleEngine() {
    const slot = syncFormToCurrentSlot();

    if (state.status.running || state.status.connecting) {
      showPopup("⏹", "Disconnecting Tunnel...", "Cleaning up iptables & STUN shield");
      if (window.AndroidBridge && window.AndroidBridge.stopProxy) {
        window.AndroidBridge.stopProxy();
      } else {
        state.status.running = false;
        state.status.connecting = false;
        renderStatus();
      }
      return;
    }

    if (!slot.host || !slot.port || slot.port <= 0 || slot.port > 65535) {
      switchTab(1);
      showPopup("⚠", "Proxy Host Required", "Enter your SOCKS5 / HTTP proxy IP and port first");
      return;
    }

    state.lastShownError = "";
    state.status.connecting = true;
    state.status.step = "Starting sing-box & STUN responder...";
    renderStatus();
    showPopup("🚀", "Connecting to " + slot.host + ":" + slot.port, "Activating Zero-Leak DNS & WebRTC Shield");

    if (window.AndroidBridge && window.AndroidBridge.startProxy) {
      window.AndroidBridge.startProxy(JSON.stringify(slot));
    }
  }

  function renderStatus() {
    const st = state.status;
    const badge = $("statusBadge");
    const orb = $("powerBtn");
    const ring = $("orbRing");

    if ($("profileBadgeText")) {
      $("profileBadgeText").textContent = "Android User " + (st.userId || 0) + " • M3 Liquid Glass";
    }

    if (st.connecting) {
      if (badge) {
        badge.className = "status-pill connecting";
        badge.textContent = "CONNECTING";
      }
      if (orb) orb.className = "power-orb";
      if (ring) ring.className = "orb-ring connecting";
      if ($("statusTitle")) $("statusTitle").textContent = "Connecting Tunnel...";
      if ($("statusSub")) $("statusSub").textContent = st.step || "Launching sing-box & STUN shield...";
    } else if (st.running) {
      if (badge) {
        badge.className = "status-pill connected";
        badge.textContent = "PROTECTED";
      }
      if (orb) orb.className = "power-orb connected";
      if (ring) ring.className = "orb-ring connected";
      if ($("statusTitle")) $("statusTitle").textContent = "Connected — Tap to Stop";
      if ($("statusSub")) $("statusSub").textContent = "Zero-Leak DNS & WebRTC Spoofer Active";
      if ($("valExitIp")) $("valExitIp").textContent = st.exitIp || "Resolving...";
      if ($("valWebrtcIp")) $("valWebrtcIp").textContent = st.webrtcIp || st.exitIp || "Spoofing...";
      if ($("valCountry")) {
        $("valCountry").textContent = st.country
          ? (st.country + (st.countryCode ? (" (" + st.countryCode + ")") : ""))
          : "Verified via proxy tunnel";
      }
      if ($("valDnsShield")) $("valDnsShield").textContent = "Protected";
      if ($("valIsp")) $("valIsp").textContent = st.isp || "FakeIP + DoH Active";

      if (!state.wasRunning) {
        state.wasRunning = true;
        showPopup("🛡️", "Tunnel Connected", st.exitIp ? ("Exit & WebRTC IP: " + st.exitIp) : "Zero-Leak Shield Active");
      }
    } else {
      if (badge) {
        badge.className = "status-pill disconnected";
        badge.textContent = "DISCONNECTED";
      }
      if (orb) orb.className = "power-orb";
      if (ring) ring.className = "orb-ring";
      if ($("statusTitle")) $("statusTitle").textContent = "Tap to Connect";
      if ($("statusSub")) {
        $("statusSub").textContent = st.lastError
          ? ("Error: " + st.lastError)
          : "Ready • Root Transparent Proxy & STUN Spoofer";
      }
      if ($("valExitIp")) $("valExitIp").textContent = "—";
      if ($("valWebrtcIp")) $("valWebrtcIp").textContent = "—";
      if ($("valCountry")) $("valCountry").textContent = "Not connected";
      if ($("valDnsShield")) $("valDnsShield").textContent = "Standby";
      if ($("valIsp")) $("valIsp").textContent = "FakeIP + DoH Tunnel";

      if (st.lastError && st.lastError !== state.lastShownError) {
        state.lastShownError = st.lastError;
        showPopup("⚠", "Connection Failed", st.lastError);
      } else if (state.wasRunning) {
        state.wasRunning = false;
        showPopup("⏹", "Tunnel Disconnected", "All iptables & routing rules restored");
      }
    }
    updateRoutingSummary();
  }

  function pollNativeStatus() {
    if (!window.AndroidBridge || !window.AndroidBridge.getStatus) return;
    try {
      const raw = window.AndroidBridge.getStatus();
      if (raw) {
        const parsed = JSON.parse(raw);
        state.status = Object.assign(state.status, parsed);
        renderStatus();
      }
    } catch (e) {}
  }

  function switchTab(tabIdx) {
    document.querySelectorAll(".tab-pane").forEach((pane, i) => {
      pane.classList.toggle("active", i === tabIdx);
    });
    document.querySelectorAll(".m3-nav-item").forEach((btn) => {
      const t = parseInt(btn.getAttribute("data-tab"), 10);
      btn.classList.toggle("active", t === tabIdx);
    });
  }

  function init() {
    // Load initial state from AndroidBridge or localStorage fallback
    try {
      if (window.AndroidBridge && window.AndroidBridge.getInitialState) {
        const initRaw = window.AndroidBridge.getInitialState();
        const parsed = JSON.parse(initRaw || "{}");
        if (Array.isArray(parsed.slots) && parsed.slots.length > 0) {
          state.slots = parsed.slots;
          state.activeSlotIndex = parsed.activeSlotIndex || 0;
        } else if (parsed.config && parsed.config.host) {
          state.slots = [parsed.config];
          state.activeSlotIndex = 0;
        }
        if (parsed.status) {
          state.status = Object.assign(state.status, parsed.status);
          state.wasRunning = !!state.status.running;
        }
      }
    } catch (e) {}

    // Load installed apps
    try {
      if (window.AndroidBridge && window.AndroidBridge.getInstalledApps) {
        const appsRaw = window.AndroidBridge.getInstalledApps();
        state.installedApps = JSON.parse(appsRaw || "[]");
      } else {
        state.installedApps = [
          { name: "Google Chrome", pkg: "com.android.chrome", uid: 10145 },
          { name: "Telegram", pkg: "org.telegram.messenger", uid: 10162 },
          { name: "YouTube", pkg: "com.google.android.youtube", uid: 10158 },
          { name: "Brave Browser", pkg: "com.brave.browser", uid: 10184 }
        ];
      }
    } catch (e) {}

    loadCurrentSlotIntoForm();
    renderStatus();

    // Navigation bindings
    document.querySelectorAll(".m3-nav-item").forEach((btn) => {
      btn.addEventListener("click", () => {
        const idx = parseInt(btn.getAttribute("data-tab"), 10);
        switchTab(idx);
      });
    });

    // Power Orb
    if ($("powerBtn")) {
      $("powerBtn").addEventListener("click", toggleEngine);
    }

    // Verify Exit & WebRTC button
    if ($("btnVerifyLeak")) {
      $("btnVerifyLeak").addEventListener("click", () => {
        if (!state.status.running) {
          showPopup("ℹ", "Tunnel Not Active", "Tap the Power Orb to connect first");
          return;
        }
        showPopup("🔍", "Verifying Exit & WebRTC...", "Checking through local sing-box proxy");
        if (window.AndroidBridge && window.AndroidBridge.refreshExitMeta) {
          window.AndroidBridge.refreshExitMeta();
        }
      });
    }

    // Add Slot button
    if ($("btnAddSlot")) {
      $("btnAddSlot").addEventListener("click", () => {
        syncFormToCurrentSlot();
        const nextNum = state.slots.length + 1;
        state.slots.push({
          profileName: "Slot " + nextNum,
          host: "",
          port: 1080,
          type: "socks5",
          username: "",
          password: "",
          dnsMode: "auto",
          appMode: "global",
          selectedApps: [],
          hotspotShare: true,
          killSwitch: false,
          autoStartOnBoot: false
        });
        state.activeSlotIndex = state.slots.length - 1;
        loadCurrentSlotIntoForm();
        switchTab(1);
        showPopup("✨", "Created Slot " + nextNum, "Enter proxy host & port below");
      });
    }

    // Save Slot button
    if ($("btnSaveSlot")) {
      $("btnSaveSlot").addEventListener("click", () => {
        const slot = syncFormToCurrentSlot();
        renderSlotStrip();
        showPopup("💾", "Saved " + slot.profileName, slot.host ? (slot.host + ":" + slot.port) : "Slot updated");
      });
    }

    // Delete Slot button
    if ($("btnDeleteSlot")) {
      $("btnDeleteSlot").addEventListener("click", () => {
        if (state.slots.length <= 1) {
          state.slots[0].host = "";
          state.slots[0].username = "";
          state.slots[0].password = "";
          loadCurrentSlotIntoForm();
          persistState();
          showPopup("🗑", "Cleared Slot", "Reset proxy fields");
          return;
        }
        state.slots.splice(state.activeSlotIndex, 1);
        state.activeSlotIndex = Math.max(0, state.activeSlotIndex - 1);
        loadCurrentSlotIntoForm();
        persistState();
        showPopup("🗑", "Deleted Slot", "Switched to " + currentSlot().profileName);
      });
    }

    // Quick Paste parser
    if ($("inpQuickPaste")) {
      $("inpQuickPaste").addEventListener("input", (e) => {
        if (parseQuickProxyString(e.target.value)) {
          e.target.value = "";
          syncFormToCurrentSlot();
          renderSlotStrip();
          showPopup("⚡", "Proxy Auto-Filled", "Host, port & credentials imported");
        }
      });
    }

    // App search & single Select/Deselect All button
    if ($("appSearchInput")) {
      $("appSearchInput").addEventListener("input", renderAppList);
    }
    if ($("btnToggleSelectAll")) {
      $("btnToggleSelectAll").addEventListener("click", toggleSelectAllApps);
    }

    // Shield & Tethering toggles
    ["chkHotspotShare", "chkAutoBoot", "selDnsMode"].forEach((id) => {
      const el = $(id);
      if (el) {
        el.addEventListener("change", () => {
          scheduleLiveReloadIfRunning();
          showPopup("⚙", "Shield Setting Updated", "Saved to active profile slot");
        });
      }
    });

    window.onNativeStatusUpdate = pollNativeStatus;
    setInterval(pollNativeStatus, 2200);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
