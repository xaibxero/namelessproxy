(function () {
  "use strict";

  const Bridge = window.NamelessBridge || null;

  const state = {
    activeTab: 0,
    running: false,
    connecting: false,
    proto: "socks5",
    host: "",
    port: "",
    user: "",
    pass: "",
    hotspot: false,
    killswitch: true,
    appMode: "all",
    selectedApps: new Set(),
    installedApps: [],
    profiles: [],
    checkerUrl: "https://ipfighter.com",
    lastRx: 0,
    lastTx: 0,
    lastTs: Date.now()
  };

  const els = {
    body: document.body,
    exitIpBadge: document.getElementById("exitIpBadge"),
    powerOrb: document.getElementById("powerOrb"),
    orbStateText: document.getElementById("orbStateText"),
    rxSpeed: document.getElementById("rxSpeed"),
    txSpeed: document.getElementById("txSpeed"),
    rxTotal: document.getElementById("rxTotal"),
    txTotal: document.getElementById("txTotal"),
    protoSocks5: document.getElementById("protoSocks5"),
    protoHttp: document.getElementById("protoHttp"),
    quickPasteBtn: document.getElementById("quickPasteBtn"),
    proxyHost: document.getElementById("proxyHost"),
    proxyPort: document.getElementById("proxyPort"),
    proxyUser: document.getElementById("proxyUser"),
    proxyPass: document.getElementById("proxyPass"),
    hotspotBtn: document.getElementById("hotspotBtn"),
    killswitchBtn: document.getElementById("killswitchBtn"),
    saveProfileBtn: document.getElementById("saveProfileBtn"),
    profilesStrip: document.getElementById("profilesStrip"),
    checkerFrameSlot: document.getElementById("checkerFrameSlot"),
    chkReload: document.getElementById("chkReload"),
    appSearchInput: document.getElementById("appSearchInput"),
    clearAppsBtn: document.getElementById("clearAppsBtn"),
    appsList: document.getElementById("appsList"),
    toastBubble: document.getElementById("toastBubble")
  };

  let toastTimer = null;
  function showToast(msg) {
    if (!els.toastBubble) return;
    els.toastBubble.textContent = msg;
    els.toastBubble.classList.add("show");
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      els.toastBubble.classList.remove("show");
    }, 1550);
  }

  /* 100% GPU-Composited Touch-to-Bubble Squish & Elastic Rebound */
  function bindJellyTouch() {
    document.addEventListener("pointerdown", (e) => {
      const target = e.target.closest(".jelly-el");
      if (!target) return;
      target.classList.remove("is-bouncing");
      target.classList.add("is-pressed");
    }, { passive: true });

    const releaseJelly = () => {
      const pressed = document.querySelectorAll(".jelly-el.is-pressed");
      pressed.forEach((el) => {
        el.classList.remove("is-pressed");
        void el.offsetWidth; // trigger lightweight reflow for CSS animation restart
        el.classList.add("is-bouncing");
      });
    };

    document.addEventListener("pointerup", releaseJelly, { passive: true });
    document.addEventListener("pointercancel", releaseJelly, { passive: true });
  }

  /* Persist & Restore Local Configuration */
  function loadSavedConfig() {
    try {
      const raw = localStorage.getItem("nameless_v21_cfg");
      if (raw) {
        const cfg = JSON.parse(raw);
        state.proto = cfg.proto || "socks5";
        state.host = cfg.host || "";
        state.port = cfg.port || "";
        state.user = cfg.user || "";
        state.pass = cfg.pass || "";
        state.hotspot = !!cfg.hotspot;
        state.killswitch = cfg.killswitch !== false;
        state.appMode = cfg.appMode || "all";
        state.selectedApps = new Set(Array.isArray(cfg.selectedApps) ? cfg.selectedApps : []);
        state.profiles = Array.isArray(cfg.profiles) ? cfg.profiles : [];
      }
    } catch (_) {}

    els.proxyHost.value = state.host;
    els.proxyPort.value = state.port;
    els.proxyUser.value = state.user;
    els.proxyPass.value = state.pass;
    syncFormUI();
    renderProfiles();
  }

  function saveConfig() {
    state.host = els.proxyHost.value.trim();
    state.port = els.proxyPort.value.trim();
    state.user = els.proxyUser.value.trim();
    state.pass = els.proxyPass.value;
    try {
      localStorage.setItem("nameless_v21_cfg", JSON.stringify({
        proto: state.proto,
        host: state.host,
        port: state.port,
        user: state.user,
        pass: state.pass,
        hotspot: state.hotspot,
        killswitch: state.killswitch,
        appMode: state.appMode,
        selectedApps: Array.from(state.selectedApps),
        profiles: state.profiles
      }));
    } catch (_) {}
  }

  function syncFormUI() {
    els.protoSocks5.classList.toggle("active", state.proto === "socks5");
    els.protoHttp.classList.toggle("active", state.proto === "http");
    els.hotspotBtn.classList.toggle("active", state.hotspot);
    els.killswitchBtn.classList.toggle("active", state.killswitch);

    document.querySelectorAll(".mode-btn").forEach((btn) => {
      btn.classList.toggle("active", btn.dataset.mode === state.appMode);
    });
  }

  /* Smart Proxy String Parser (host:port:user:pass or scheme://user:pass@host:port) */
  function parseProxyString(str) {
    if (!str) return false;
    let s = str.trim();
    if (!s) return false;

    if (s.toLowerCase().startsWith("http://") || s.toLowerCase().startsWith("https://")) {
      state.proto = "http";
      s = s.replace(/^https?:\/\//i, "");
    } else if (s.toLowerCase().startsWith("socks5://") || s.toLowerCase().startsWith("socks://")) {
      state.proto = "socks5";
      s = s.replace(/^socks5?:\/\//i, "");
    }

    if (s.includes("@")) {
      const [cred, endpoint] = s.split("@");
      const [u, p] = cred.split(":");
      const [h, pt] = endpoint.split(":");
      if (h && pt) {
        els.proxyHost.value = h;
        els.proxyPort.value = pt;
        els.proxyUser.value = u || "";
        els.proxyPass.value = p || "";
        saveConfig();
        syncFormUI();
        return true;
      }
    }

    const parts = s.split(":");
    if (parts.length >= 2) {
      els.proxyHost.value = parts[0];
      els.proxyPort.value = parts[1];
      els.proxyUser.value = parts[2] || "";
      els.proxyPass.value = parts.slice(3).join(":") || "";
      saveConfig();
      syncFormUI();
      return true;
    }
    return false;
  }

  /* Compute Screen Ratios and Mount Native Hardware-Accelerated Checker WebView */
  function launchEmbeddedChecker(url) {
    if (url) state.checkerUrl = url;
    document.querySelectorAll(".checker-pill").forEach((btn) => {
      btn.classList.toggle("active", btn.dataset.url === state.checkerUrl);
    });

    if (!Bridge || state.activeTab !== 1) return;

    requestAnimationFrame(() => {
      const rect = els.checkerFrameSlot.getBoundingClientRect();
      const vh = window.innerHeight || 800;
      const topRatio = Math.max(0.12, Math.min(0.35, rect.top / vh));
      const bottomRatio = Math.max(0.08, Math.min(0.25, (vh - rect.bottom) / vh));
      try {
        if (typeof Bridge.showCheckerRatio === "function") {
          Bridge.showCheckerRatio(state.checkerUrl, topRatio, bottomRatio);
        } else if (typeof Bridge.showChecker === "function") {
          Bridge.showChecker(state.checkerUrl, Math.round(rect.top), Math.round(vh - rect.bottom));
        }
      } catch (_) {}
    });
  }

  function switchTab(tabIdx) {
    state.activeTab = tabIdx;
    document.querySelectorAll(".tab-view").forEach((sec, idx) => {
      sec.classList.toggle("active", idx === tabIdx);
    });
    document.querySelectorAll(".dock-btn").forEach((btn) => {
      btn.classList.toggle("active", Number(btn.dataset.tab) === tabIdx);
    });

    if (tabIdx === 1) {
      launchEmbeddedChecker(state.checkerUrl);
    } else if (Bridge && typeof Bridge.hideChecker === "function") {
      try { Bridge.hideChecker(); } catch (_) {}
    }

    if (tabIdx === 2 && state.installedApps.length === 0) {
      loadInstalledApps();
    }
  }

  /* Profiles Management */
  function renderProfiles() {
    if (!els.profilesStrip) return;
    els.profilesStrip.innerHTML = "";
    state.profiles.forEach((prof, idx) => {
      const pill = document.createElement("div");
      pill.className = "profile-Bubble jelly-el";
      pill.innerHTML = `<span>${prof.proto.toUpperCase()} • ${prof.host}:${prof.port}</span><span class="profile-del" data-del="${idx}">✕</span>`;
      pill.addEventListener("click", (e) => {
        if (e.target.dataset.del !== undefined) {
          state.profiles.splice(idx, 1);
          saveConfig();
          renderProfiles();
          showToast("Removed");
          return;
        }
        state.proto = prof.proto;
        els.proxyHost.value = prof.host;
        els.proxyPort.value = prof.port;
        els.proxyUser.value = prof.user || "";
        els.proxyPass.value = prof.pass || "";
        saveConfig();
        syncFormUI();
        showToast("Loaded " + prof.host);
      });
      els.profilesStrip.appendChild(pill);
    });
  }

  /* Per-App Routing Selector */
  function loadInstalledApps() {
    if (!Bridge || typeof Bridge.getInstalledAppsJson !== "function") return;
    try {
      const raw = Bridge.getInstalledAppsJson();
      const parsed = JSON.parse(raw || "[]");
      if (Array.isArray(parsed)) {
        state.installedApps = parsed;
        renderAppsList();
      }
    } catch (_) {}
  }

  function renderAppsList() {
    if (!els.appsList) return;
    const q = (els.appSearchInput.value || "").trim().toLowerCase();
    const frag = document.createDocumentFragment();

    const filtered = state.installedApps.filter((a) => {
      if (!q) return true;
      return (a.label && a.label.toLowerCase().includes(q)) ||
             (a.pkg && a.pkg.toLowerCase().includes(q));
    });

    filtered.sort((a, b) => {
      const sa = state.selectedApps.has(a.pkg) ? 0 : 1;
      const sb = state.selectedApps.has(b.pkg) ? 0 : 1;
      if (sa !== sb) return sa - sb;
      return (a.label || "").localeCompare(b.label || "");
    });

    filtered.forEach((app) => {
      const row = document.createElement("div");
      const isSel = state.selectedApps.has(app.pkg);
      row.className = "app-item jelly-el" + (isSel ? " selected" : "");
      row.innerHTML = `
        <div class="app-info">
          <span class="app-name">${app.label || app.pkg}</span>
          <span class="app-pkg">${app.pkg} (${app.uid})</span>
        </div>
        <div class="app-check">✓</div>
      `;
      row.addEventListener("click", () => {
        if (state.selectedApps.has(app.pkg)) {
          state.selectedApps.delete(app.pkg);
          row.classList.remove("selected");
        } else {
          state.selectedApps.add(app.pkg);
          row.classList.add("selected");
        }
        saveConfig();
      });
      frag.appendChild(row);
    });

    els.appsList.innerHTML = "";
    els.appsList.appendChild(frag);
  }

  function getSelectedUidCsv() {
    if (state.appMode === "all" || state.selectedApps.size === 0) return "";
    const uids = [];
    state.installedApps.forEach((app) => {
      if (state.selectedApps.has(app.pkg) && app.uid) {
        uids.push(app.uid);
      }
    });
    return uids.join(",");
  }

  /* Start / Stop Tunnel */
  function toggleTunnel() {
    if (state.connecting) return;
    saveConfig();

    if (state.running) {
      state.connecting = true;
      els.body.dataset.state = "connecting";
      els.orbStateText.textContent = "STOPPING";
      setTimeout(() => {
        try {
          if (Bridge) Bridge.stopTunnel();
        } catch (_) {}
        state.running = false;
        state.connecting = false;
        updateVisualState(false, "");
        showToast("Disconnected");
      }, 40);
      return;
    }

    if (!state.host || !state.port) {
      showToast("Enter Host & Port");
      return;
    }

    state.connecting = true;
    els.body.dataset.state = "connecting";
    els.orbStateText.textContent = "LINKING";

    if (state.appMode !== "all" && state.installedApps.length === 0) {
      loadInstalledApps();
    }

    const payload = JSON.stringify({
      type: state.proto,
      host: state.host,
      port: parseInt(state.port, 10) || 1080,
      user: state.user,
      pass: state.pass,
      dns: "1.1.1.1",
      hotspot: state.hotspot,
      killswitch: state.killswitch,
      appMode: state.appMode,
      appUids: getSelectedUidCsv()
    });

    setTimeout(() => {
      let resp = {};
      try {
        if (Bridge) {
          const raw = Bridge.startTunnel(payload);
          resp = JSON.parse(raw || "{}");
        }
      } catch (_) {}

      state.connecting = false;
      if (resp && resp.ok) {
        state.running = true;
        updateVisualState(true, resp.exitIp || state.host);
        showToast("Connected • " + (resp.exitIp || state.host));
      } else {
        state.running = false;
        updateVisualState(false, "");
        showToast((resp && resp.msg) ? resp.msg : "Connection Failed");
      }
    }, 50);
  }

  function updateVisualState(running, exitIp) {
    state.running = running;
    els.body.dataset.state = running ? "connected" : "disconnected";
    els.orbStateText.textContent = running ? "ACTIVE" : "TAP";
    els.exitIpBadge.textContent = running ? (exitIp || state.host || "ONLINE") : "OFFLINE";
  }

  function formatBytes(bytes) {
    if (!bytes || bytes < 0) return "0.0 KB";
    const mb = bytes / (1024 * 1024);
    if (mb >= 1024) return (mb / 1024).toFixed(2) + " GB";
    if (mb >= 1) return mb.toFixed(1) + " MB";
    return (bytes / 1024).toFixed(1) + " KB";
  }

  function formatRate(bytesPerSec) {
    if (!bytesPerSec || bytesPerSec <= 0) return "0.0 KB/s";
    const mbps = bytesPerSec / (1024 * 1024);
    if (mbps >= 1) return mbps.toFixed(2) + " MB/s";
    return (bytesPerSec / 1024).toFixed(1) + " KB/s";
  }

  /* Non-Blocking Cached Status Poller (<0.1ms native read) */
  function pollTunnelStatus() {
    if (!Bridge || state.connecting) return;
    try {
      const raw = Bridge.getTunnelStatusJson();
      if (!raw) return;
      const st = JSON.parse(raw);
      const now = Date.now();
      const dt = Math.max(0.25, (now - state.lastTs) / 1000);

      const rx = Number(st.rxBytes) || 0;
      const tx = Number(st.txBytes) || 0;
      const rxRate = state.lastRx > 0 && rx >= state.lastRx ? (rx - state.lastRx) / dt : 0;
      const txRate = state.lastTx > 0 && tx >= state.lastTx ? (tx - state.lastTx) / dt : 0;

      state.lastRx = rx;
      state.lastTx = tx;
      state.lastTs = now;

      els.rxSpeed.textContent = formatRate(rxRate);
      els.txSpeed.textContent = formatRate(txRate);
      els.rxTotal.textContent = formatBytes(rx);
      els.txTotal.textContent = formatBytes(tx);

      updateVisualState(!!st.running, st.exitIp || "");
    } catch (_) {}
  }

  /* Event Bindings */
  function initEvents() {
    els.powerOrb.addEventListener("click", toggleTunnel);

    els.protoSocks5.addEventListener("click", () => {
      state.proto = "socks5";
      saveConfig();
      syncFormUI();
    });

    els.protoHttp.addEventListener("click", () => {
      state.proto = "http";
      saveConfig();
      syncFormUI();
    });

    els.quickPasteBtn.addEventListener("click", async () => {
      try {
        const text = await navigator.clipboard.readText();
        if (parseProxyString(text)) {
          showToast("Proxy Pasted");
        } else {
          showToast("Format: host:port:user:pass");
        }
      } catch (_) {
        const manual = prompt("Paste proxy (host:port:user:pass):");
        if (manual && parseProxyString(manual)) showToast("Proxy Pasted");
      }
    });

    els.proxyHost.addEventListener("input", (e) => {
      const v = e.target.value;
      if (v.includes(":")) {
        parseProxyString(v);
      } else {
        saveConfig();
      }
    });
    els.proxyPort.addEventListener("input", saveConfig);
    els.proxyUser.addEventListener("input", saveConfig);
    els.proxyPass.addEventListener("input", saveConfig);

    els.hotspotBtn.addEventListener("click", () => {
      state.hotspot = !state.hotspot;
      saveConfig();
      syncFormUI();
      if (Bridge && typeof Bridge.toggleHotspot === "function" && state.running) {
        try { Bridge.toggleHotspot(state.hotspot); } catch (_) {}
      }
      showToast(state.hotspot ? "Hotspot Share ON" : "Hotspot Share OFF");
    });

    els.killswitchBtn.addEventListener("click", () => {
      state.killswitch = !state.killswitch;
      saveConfig();
      syncFormUI();
      showToast(state.killswitch ? "Killswitch ON" : "Killswitch OFF");
    });

    els.saveProfileBtn.addEventListener("click", () => {
      saveConfig();
      if (!state.host || !state.port) {
        showToast("Enter Host & Port");
        return;
      }
      state.profiles.unshift({
        proto: state.proto,
        host: state.host,
        port: state.port,
        user: state.user,
        pass: state.pass
      });
      if (state.profiles.length > 8) state.profiles.pop();
      saveConfig();
      renderProfiles();
      showToast("Saved Profile");
    });

    document.querySelectorAll(".dock-btn").forEach((btn) => {
      btn.addEventListener("click", () => {
        switchTab(Number(btn.dataset.tab));
      });
    });

    document.querySelectorAll(".checker-pill").forEach((btn) => {
      btn.addEventListener("click", () => {
        launchEmbeddedChecker(btn.dataset.url);
      });
    });

    els.chkReload.addEventListener("click", () => {
      launchEmbeddedChecker(state.checkerUrl);
      showToast("Reloading...");
    });

    document.querySelectorAll(".mode-btn").forEach((btn) => {
      btn.addEventListener("click", () => {
        state.appMode = btn.dataset.mode;
        saveConfig();
        syncFormUI();
        showToast("Mode: " + btn.textContent);
      });
    });

    els.appSearchInput.addEventListener("input", renderAppsList);
    els.clearAppsBtn.addEventListener("click", () => {
      state.selectedApps.clear();
      saveConfig();
      renderAppsList();
      showToast("Cleared Selection");
    });
  }

  /* Initialize */
  bindJellyTouch();
  loadSavedConfig();
  initEvents();
  pollTunnelStatus();
  setInterval(pollTunnelStatus, 1500);
})();
