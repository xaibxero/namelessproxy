// ============================================================================
// Nameless Proxy • 100% GPU-Accelerated Jelly UI & Verified Bridge Controller
// ============================================================================

const defaultSlot = () => ({
  type: "SOCKS5",
  host: "",
  port: 1080,
  username: "",
  password: "",
  sni: "",
  ssMethod: "chacha20-ietf-poly1305",
  realityPublicKey: "",
  realityShortId: ""
});

let state = {
  activeTab: 0,
  activeSlot: 0,
  runningSlot: null,
  isRunning: false,
  isTransitioning: false,
  routeHotspot: true,
  routeAllApps: true,
  selectedUids: [],
  installedApps: [],
  webrtcMode: "SPOOF",
  checkerUrl: "https://ipfighter.com",
  sessionStartMs: 0,
  slots: [defaultSlot(), defaultSlot(), defaultSlot(), defaultSlot(), defaultSlot()],
  prevRx: 0,
  prevTx: 0,
  startRx: 0,
  startTx: 0,
  prevTime: Date.now(),
  auditMode: false
};

// ============================================================================
// 100% GPU-Composited Touch-to-Bubble Squish & Multi-Bounce Rebound
// (Zero JS requestAnimationFrame loop -> Zero Main-Thread CPU/UI Lag!)
// ============================================================================
function bindGpuJellyTouch() {
  document.addEventListener("pointerdown", (e) => {
    const el = e.target.closest(".jelly");
    if (!el) return;
    el.classList.remove("is-bouncing");
    el.classList.add("is-pressed");
  }, { passive: true });

  const releaseAll = () => {
    const pressed = document.querySelectorAll(".jelly.is-pressed");
    pressed.forEach((el) => {
      el.classList.remove("is-pressed");
      void el.offsetWidth;
      el.classList.add("is-bouncing");
    });
  };

  document.addEventListener("pointerup", releaseAll, { passive: true });
  document.addEventListener("pointercancel", releaseAll, { passive: true });
}

function pulseVisibleTabBubbles(tabIdx) {
  const pane = document.getElementById(`tab-${tabIdx}`);
  if (!pane) return;
  const items = pane.querySelectorAll(".jelly");
  items.forEach((el, i) => {
    if (i > 10) return;
    setTimeout(() => {
      el.classList.remove("is-bouncing");
      void el.offsetWidth;
      el.classList.add("is-bouncing");
    }, i * 18);
  });
}

function flagEmojiFromCode(cc) {
  if (!cc || cc.length !== 2) return "🛡️";
  const codePoints = cc.toUpperCase().split("").map(c => 127397 + c.charCodeAt(0));
  return String.fromCodePoint(...codePoints);
}

function formatBytes(b) {
  if (!b || b <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  let i = 0;
  let val = b;
  while (val >= 1024 && i < units.length - 1) {
    val /= 1024;
    i++;
  }
  return (i === 0 ? val : val.toFixed(1)) + " " + units[i];
}

function formatDuration(ms) {
  if (ms <= 0) return "00:00:00";
  const s = Math.floor(ms / 1000);
  const hh = String(Math.floor(s / 3600)).padStart(2, "0");
  const mm = String(Math.floor((s % 3600) / 60)).padStart(2, "0");
  const ss = String(s % 60).padStart(2, "0");
  return `${hh}:${mm}:${ss}`;
}

function initApp() {
  bindGpuJellyTouch();

  if (window.NamelessBridge) {
    try {
      const raw = JSON.parse(window.NamelessBridge.getInitialState() || "{}");
      if (raw.network) {
        document.getElementById("netSummaryText").textContent = raw.network;
      }
      if (raw.slotsJson) {
        const parsed = JSON.parse(raw.slotsJson);
        if (Array.isArray(parsed.slots) && parsed.slots.length === 5) {
          state.slots = parsed.slots;
        }
        if (typeof parsed.activeSlot === "number") state.activeSlot = parsed.activeSlot;
        if (typeof parsed.routeHotspot === "boolean") state.routeHotspot = parsed.routeHotspot;
        if (typeof parsed.routeAllApps === "boolean") state.routeAllApps = parsed.routeAllApps;
        if (Array.isArray(parsed.selectedUids)) state.selectedUids = parsed.selectedUids;
        if (parsed.webrtcMode) state.webrtcMode = parsed.webrtcMode;
      }
      if (raw.status) {
        applyPolledStatus(raw.status, true);
      }
    } catch (e) {}

    setTimeout(() => {
      try {
        const appsRaw = JSON.parse(window.NamelessBridge.getInstalledApps() || "[]");
        if (Array.isArray(appsRaw)) {
          state.installedApps = appsRaw;
          renderAppsList();
        }
      } catch (e) {}
    }, 120);
  }

  loadSlotIntoForm(state.activeSlot);
  renderSlotCapsules();
  renderQuickPills();

  setInterval(pollBackendStatus, 1200);
}

function persistAllSlots() {
  const payload = JSON.stringify({
    activeSlot: state.activeSlot,
    routeHotspot: state.routeHotspot,
    routeAllApps: state.routeAllApps,
    selectedUids: state.selectedUids,
    webrtcMode: state.webrtcMode,
    slots: state.slots
  });
  if (window.NamelessBridge) {
    window.NamelessBridge.saveSlots(payload);
  }
}

function selectSlot(idx) {
  saveFormIntoSlot(state.activeSlot);
  state.activeSlot = idx;
  loadSlotIntoForm(idx);
  renderSlotCapsules();
  persistAllSlots();
}

function renderSlotCapsules() {
  document.querySelectorAll(".slot-orb").forEach((btn, idx) => {
    btn.classList.toggle("active", idx === state.activeSlot);
    btn.classList.toggle("running", state.isRunning && idx === state.runningSlot);
  });

  const cur = state.slots[state.activeSlot] || defaultSlot();
  const nodeLabel = document.getElementById("hubNodeLabel");
  if (cur.host && cur.host.trim() !== "") {
    nodeLabel.textContent = `${cur.host}:${cur.port}`;
  } else {
    nodeLabel.textContent = `P${state.activeSlot + 1} (${cur.type})`;
  }
  document.getElementById("editingSlotTitle").textContent = `P${state.activeSlot + 1} • ${cur.type}`;
}

function renderQuickPills() {
  const hsPill = document.getElementById("pillHotspot");
  hsPill.classList.toggle("on", state.routeHotspot);
  document.getElementById("lblHotspotState").textContent = state.routeHotspot ? "ON" : "OFF";

  const appsPill = document.getElementById("pillApps");
  appsPill.classList.toggle("on", true);
  document.getElementById("lblAppsState").textContent = state.routeAllApps
    ? "ALL"
    : `${state.selectedUids.length} APPS`;

  const rtcPill = document.getElementById("pillWebrtc");
  const isSpoof = state.webrtcMode !== "DISABLE";
  rtcPill.classList.toggle("on", isSpoof);
  document.getElementById("lblWebrtcState").textContent = isSpoof ? "IP" : "OFF";

  document.getElementById("btnAllApps").classList.toggle("active", state.routeAllApps);
  document.getElementById("btnSelApps").classList.toggle("active", !state.routeAllApps);
}

function toggleHotspotSetting() {
  state.routeHotspot = !state.routeHotspot;
  renderQuickPills();
  persistAllSlots();
}

function toggleWebrtcMode() {
  state.webrtcMode = state.webrtcMode === "SPOOF" ? "DISABLE" : "SPOOF";
  renderQuickPills();
  persistAllSlots();
}

function setAppRouteMode(all) {
  state.routeAllApps = !!all;
  renderQuickPills();
  renderAppsList();
  persistAllSlots();
}

function selectAllApps() {
  state.routeAllApps = false;
  state.selectedUids = state.installedApps.map(a => a.uid);
  renderQuickPills();
  renderAppsList();
  persistAllSlots();
}

function clearSelectedApps() {
  state.routeAllApps = false;
  state.selectedUids = [];
  renderQuickPills();
  renderAppsList();
  persistAllSlots();
}

function toggleAppUid(uid) {
  state.routeAllApps = false;
  const idx = state.selectedUids.indexOf(uid);
  if (idx >= 0) state.selectedUids.splice(idx, 1);
  else state.selectedUids.push(uid);
  renderQuickPills();
  renderAppsList();
  persistAllSlots();
}

function renderAppsList() {
  const container = document.getElementById("appsListContainer");
  if (!container) return;
  const q = (document.getElementById("appSearchInput").value || "").trim().toLowerCase();
  const filtered = state.installedApps.filter(a =>
    !q || (a.name && a.name.toLowerCase().includes(q)) || (a.pkg && a.pkg.toLowerCase().includes(q))
  );
  container.innerHTML = "";
  const frag = document.createDocumentFragment();
  filtered.forEach(app => {
    const selected = state.routeAllApps || state.selectedUids.includes(app.uid);
    const row = document.createElement("div");
    row.className = "app-row jelly" + (selected ? " selected" : "");
    row.setAttribute("data-squish", "soft");
    row.onclick = () => toggleAppUid(app.uid);
    row.innerHTML = `
      <div>
        <div class="app-name">${app.name}</div>
        <div class="app-pkg">${app.pkg}</div>
      </div>
      <div class="app-check">✓</div>
    `;
    frag.appendChild(row);
  });
  container.appendChild(frag);
}

function loadSlotIntoForm(idx) {
  const s = state.slots[idx] || defaultSlot();
  document.getElementById("inpHost").value = s.host || "";
  document.getElementById("inpPort").value = s.port || 1080;
  document.getElementById("inpUser").value = s.username || "";
  document.getElementById("inpPass").value = s.password || "";
  document.getElementById("inpSni").value = s.sni || "";
  document.getElementById("inpExtra").value = s.realityPublicKey || s.ssMethod || "";
  document.querySelectorAll(".proto-orb").forEach(btn => {
    btn.classList.toggle("active", btn.getAttribute("data-proto") === (s.type || "SOCKS5"));
  });
}

function saveFormIntoSlot(idx) {
  const s = state.slots[idx] || defaultSlot();
  s.host = (document.getElementById("inpHost").value || "").trim();
  s.port = parseInt(document.getElementById("inpPort").value || "1080", 10) || 1080;
  s.username = (document.getElementById("inpUser").value || "").trim();
  s.password = (document.getElementById("inpPass").value || "").trim();
  s.sni = (document.getElementById("inpSni").value || "").trim();
  const extra = (document.getElementById("inpExtra").value || "").trim();
  if (s.type === "SHADOWSOCKS") {
    s.ssMethod = extra || "chacha20-ietf-poly1305";
  } else {
    s.realityPublicKey = extra;
  }
  state.slots[idx] = s;
}

// Auto-split host:port:user:pass when pasted or typed into Host box
function onHostInput() {
  const hostInput = document.getElementById("inpHost");
  const raw = (hostInput.value || "").trim();
  if (raw.includes(":") && !raw.includes("://")) {
    const parts = raw.split(":");
    if (parts.length >= 2 && /^\d+$/.test(parts[1].trim())) {
      hostInput.value = parts[0].trim();
      document.getElementById("inpPort").value = parseInt(parts[1].trim(), 10) || 1080;
      if (parts.length >= 3) document.getElementById("inpUser").value = parts[2].trim();
      if (parts.length >= 4) document.getElementById("inpPass").value = parts.slice(3).join(":").trim();
    }
  }
  onFormEdited();
}

function onFormEdited() {
  saveFormIntoSlot(state.activeSlot);
  renderSlotCapsules();
  persistAllSlots();
}

function setProtocol(proto) {
  state.slots[state.activeSlot].type = proto;
  document.querySelectorAll(".proto-orb").forEach(btn => {
    btn.classList.toggle("active", btn.getAttribute("data-proto") === proto);
  });
  renderSlotCapsules();
  persistAllSlots();
}

function clearCurrentSlot() {
  state.slots[state.activeSlot] = defaultSlot();
  loadSlotIntoForm(state.activeSlot);
  renderSlotCapsules();
  persistAllSlots();
}

function pasteUriFromClipboard() {
  if (!window.NamelessBridge) return;
  try {
    const parsed = JSON.parse(window.NamelessBridge.importClipboardUri() || "{}");
    if (parsed.host) {
      const s = state.slots[state.activeSlot];
      s.type = parsed.type || "SOCKS5";
      s.host = parsed.host || "";
      s.port = parsed.port || 1080;
      s.username = parsed.username || "";
      s.password = parsed.password || "";
      s.sni = parsed.sni || "";
      s.realityPublicKey = parsed.realityPublicKey || "";
      s.realityShortId = parsed.realityShortId || "";
      loadSlotIntoForm(state.activeSlot);
      renderSlotCapsules();
      persistAllSlots();
      window.NamelessBridge.showToast("Imported into P" + (state.activeSlot + 1));
    } else {
      window.NamelessBridge.showToast("Clipboard has no valid proxy URI");
    }
  } catch (e) {}
}

function toggleTunnel() {
  if (state.isTransitioning) return;
  saveFormIntoSlot(state.activeSlot);
  persistAllSlots();

  if (state.isRunning && state.runningSlot === state.activeSlot) {
    state.isTransitioning = true;
    document.getElementById("coreStateText").textContent = "WAIT";
    if (window.NamelessBridge) {
      window.NamelessBridge.stopTunnelAsync();
    }
  } else {
    const targetUidsStr = (state.routeAllApps || state.selectedUids.length === 0)
      ? "ALL"
      : state.selectedUids.join(",");
    const cfg = Object.assign({}, state.slots[state.activeSlot], {
      slot: state.activeSlot,
      routeHotspot: state.routeHotspot,
      targetUids: targetUidsStr,
      webrtcMode: state.webrtcMode
    });
    if (!cfg.host || cfg.host.trim() === "") {
      switchTab(2);
      if (window.NamelessBridge) window.NamelessBridge.showToast("Enter Proxy Host & Port");
      return;
    }
    state.isTransitioning = true;
    document.getElementById("coreStateText").textContent = "BOOT";
    if (window.NamelessBridge) {
      window.NamelessBridge.startTunnelAsync(JSON.stringify(cfg));
    }
  }
}

window.onTunnelActionComplete = function(jsonStr) {
  state.isTransitioning = false;
  try {
    const res = JSON.parse(jsonStr);
    if (!res.ok && res.error && window.NamelessBridge) {
      window.NamelessBridge.showToast(res.error);
    }
  } catch (e) {}
  pollBackendStatus();
};

function pingCurrentNode() {
  saveFormIntoSlot(state.activeSlot);
  const s = state.slots[state.activeSlot];
  if (!s.host) {
    switchTab(2);
    return;
  }
  document.getElementById("hubPingLabel").textContent = "...";
  if (window.NamelessBridge) {
    window.NamelessBridge.pingServerAsync(s.host, s.port || 1080);
  }
}

window.onPingResult = function(ms) {
  const el = document.getElementById("hubPingLabel");
  el.textContent = ms > 0 ? `${ms} ms` : "Timeout";
};

function applyPolledStatus(st, firstInit) {
  const wasRunning = state.isRunning;
  state.isRunning = !!st.running;
  state.runningSlot = state.isRunning ? (st.slot || 0) : null;

  document.body.dataset.state = state.isRunning ? "connected" : "disconnected";

  if (st.network) {
    document.getElementById("netSummaryText").textContent = st.network;
  }

  const orb = document.getElementById("headerOrb");
  const coreBtn = document.getElementById("coreBtn");
  const coreStateText = document.getElementById("coreStateText");
  const coreTimerText = document.getElementById("coreTimerText");

  orb.classList.toggle("online", state.isRunning);
  coreBtn.classList.toggle("online", state.isRunning);

  if (state.isRunning) {
    if (!wasRunning || state.sessionStartMs === 0) {
      state.sessionStartMs = Date.now();
      state.startRx = st.rxBytes || 0;
      state.startTx = st.txBytes || 0;
      state.prevRx = st.rxBytes || 0;
      state.prevTx = st.txBytes || 0;
      state.prevTime = Date.now();
    }
    if (!state.isTransitioning) {
      coreStateText.textContent = `P${(state.runningSlot || 0) + 1} ON`;
    }
    coreTimerText.textContent = formatDuration(Date.now() - state.sessionStartMs);

    const now = Date.now();
    const dt = Math.max(0.5, (now - state.prevTime) / 1000);
    const curRx = Math.max(state.prevRx, st.rxBytes || 0);
    const curTx = Math.max(state.prevTx, st.txBytes || 0);
    const rxRate = firstInit ? 0 : Math.max(0, Math.round((curRx - state.prevRx) / dt));
    const txRate = firstInit ? 0 : Math.max(0, Math.round((curTx - state.prevTx) / dt));
    state.prevRx = curRx;
    state.prevTx = curTx;
    state.prevTime = now;

    document.getElementById("rxSpeedText").textContent = formatBytes(rxRate) + "/s";
    document.getElementById("txSpeedText").textContent = formatBytes(txRate) + "/s";
    document.getElementById("rxTotalText").textContent = formatBytes(Math.max(0, curRx - state.startRx));
    document.getElementById("txTotalText").textContent = formatBytes(Math.max(0, curTx - state.startTx));

    const g = st.geo;
    const ip = (g && g.query) || st.exitIp || state.slots[state.runningSlot || 0].host || "Active";
    document.getElementById("exitIpText").textContent = ip;
    if (g && g.country) {
      document.getElementById("exitFlag").textContent = flagEmojiFromCode(g.countryCode);
      document.getElementById("exitLocText").textContent = g.countryCode ? `${g.country} (${g.countryCode})` : g.country;
    } else {
      document.getElementById("exitFlag").textContent = "🛡️";
      document.getElementById("exitLocText").textContent = "Protected";
    }
  } else {
    state.sessionStartMs = 0;
    if (!state.isTransitioning) {
      coreStateText.textContent = "OFF";
    }
    coreTimerText.textContent = "00:00:00";
    document.getElementById("rxSpeedText").textContent = "0 B/s";
    document.getElementById("txSpeedText").textContent = "0 B/s";
    document.getElementById("exitFlag").textContent = "🌐";
    document.getElementById("exitIpText").textContent = "Offline";
    document.getElementById("exitLocText").textContent = "Exit IP";
  }

  renderSlotCapsules();
}

function pollBackendStatus() {
  if (!window.NamelessBridge) return;
  try {
    const raw = JSON.parse(window.NamelessBridge.pollStatus() || "{}");
    applyPolledStatus(raw, false);
    if (state.activeTab === 4) {
      refreshLogsNow();
    }
  } catch (e) {}
}

function mountEmbeddedChecker(url, forceReload) {
  if (url) state.checkerUrl = url;
  if (!window.NamelessBridge || state.activeTab !== 1) return;

  requestAnimationFrame(() => {
    const slot = document.getElementById("checkerPlaceholder");
    const vh = window.innerHeight || 800;
    const rect = slot ? slot.getBoundingClientRect() : { top: 124, bottom: vh - 74 };
    const topRatio = Math.max(0.13, Math.min(0.28, rect.top / vh));
    const bottomRatio = Math.max(0.08, Math.min(0.20, (vh - rect.bottom) / vh));
    if (forceReload) {
      window.NamelessBridge.navigateChecker(state.checkerUrl);
    } else {
      window.NamelessBridge.showCheckerRatio(state.checkerUrl, topRatio, bottomRatio);
    }
  });
}

function switchTab(idx) {
  state.activeTab = idx;
  document.querySelectorAll(".tab-view").forEach((pane, i) => {
    pane.classList.toggle("active", i === idx);
  });
  document.querySelectorAll(".dock-orb").forEach((btn, i) => {
    btn.classList.toggle("active", i === idx);
  });

  pulseVisibleTabBubbles(idx);

  if (window.NamelessBridge) {
    if (idx === 1) {
      mountEmbeddedChecker(state.checkerUrl, false);
    } else {
      window.NamelessBridge.hideCheckerView();
    }
  }

  if (idx === 4) {
    refreshLogsNow();
  }
}

function loadCheckerPreset(url, btnId) {
  document.querySelectorAll(".preset-orb").forEach(b => b.classList.remove("active"));
  const btn = document.getElementById(btnId);
  if (btn) btn.classList.add("active");
  state.checkerUrl = url;
  mountEmbeddedChecker(url, true);
}

function reloadCheckerView(wipe) {
  if (window.NamelessBridge) {
    window.NamelessBridge.reloadChecker(!!wipe);
  }
}

window.onCheckerState = function(url, loading) {
  if (url) {
    state.checkerUrl = url;
  }
};

function setLogMode(audit) {
  state.auditMode = !!audit;
  document.getElementById("btnModeLogs").classList.toggle("active", !state.auditMode);
  document.getElementById("btnModeAudit").classList.toggle("active", state.auditMode);
  refreshLogsNow();
}

function refreshLogsNow() {
  if (!window.NamelessBridge) return;
  const text = window.NamelessBridge.fetchLogs(state.auditMode);
  document.getElementById("logConsole").textContent = text || "";
}

function clearLogsNow() {
  if (!window.NamelessBridge) return;
  window.NamelessBridge.clearKernelLogs();
  setTimeout(refreshLogsNow, 150);
}

function exportAllSlots() {
  saveFormIntoSlot(state.activeSlot);
  persistAllSlots();
  if (window.NamelessBridge) {
    const ok = window.NamelessBridge.exportAllSlots();
    window.NamelessBridge.showToast(ok ? "Exported to Download/nameless_proxy_backup.json" : "Export failed");
  }
}

function importAllSlots() {
  if (!window.NamelessBridge) return;
  const raw = window.NamelessBridge.importAllSlots();
  if (raw) {
    try {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed.slots) && parsed.slots.length === 5) {
        state.slots = parsed.slots;
        loadSlotIntoForm(state.activeSlot);
        renderSlotCapsules();
        window.NamelessBridge.showToast("Imported 5 slots");
        return;
      }
    } catch (e) {}
  }
  window.NamelessBridge.showToast("No backup found");
}

window.addEventListener("DOMContentLoaded", initApp);
