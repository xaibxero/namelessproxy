// ============================================================================
// Nameless Proxy v2.0 • 120Hz Hooke's Law Jelly Physics & Bubble Controller
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
  sessionStartMs: 0,
  slots: [defaultSlot(), defaultSlot(), defaultSlot(), defaultSlot(), defaultSlot()],
  prevRx: 0,
  prevTx: 0,
  startRx: 0,
  startTx: 0,
  prevTime: Date.now(),
  rxHistory: new Array(28).fill(0),
  txHistory: new Array(28).fill(0),
  auditMode: false
};

// ============================================================================
// Real Hooke's Law Spring-Jelly Physics Engine for Every .jelly Element
// ============================================================================
const springs = new Map();

function attachJellyPhysics(el) {
  if (springs.has(el)) return;
  const sp = {
    sx: 1, sy: 1,
    tx: 1, ty: 1,
    vx: 0, vy: 0
  };
  springs.set(el, sp);

  el.addEventListener("pointerdown", () => {
    sp.tx = 1.12;
    sp.ty = 0.88;
  }, { passive: true });

  const release = () => {
    if (sp.tx !== 1 || sp.ty !== 1) {
      sp.vx += 0.09;
      sp.vy -= 0.09;
      sp.tx = 1;
      sp.ty = 1;
    }
  };
  el.addEventListener("pointerup", release, { passive: true });
  el.addEventListener("pointercancel", release, { passive: true });
  el.addEventListener("pointerleave", release, { passive: true });
}

function pulseAllVisibleBubbles() {
  const pane = document.getElementById(`tab-${state.activeTab}`);
  if (!pane) return;
  pane.querySelectorAll(".jelly").forEach((el, i) => {
    const sp = springs.get(el);
    if (sp) {
      setTimeout(() => {
        sp.sx = 0.86;
        sp.sy = 1.14;
        sp.vx = 0.08;
        sp.vy = -0.08;
      }, i * 28);
    }
  });
}

function stepSpringPhysics() {
  const stiffness = 0.26;
  const damping = 0.72;
  springs.forEach((sp, el) => {
    const fx = (sp.tx - sp.sx) * stiffness;
    const fy = (sp.ty - sp.sy) * stiffness;
    sp.vx = (sp.vx + fx) * damping;
    sp.vy = (sp.vy + fy) * damping;
    sp.sx += sp.vx;
    sp.sy += sp.vy;
    if (Math.abs(sp.sx - 1) > 0.001 || Math.abs(sp.sy - 1) > 0.001 || Math.abs(sp.vx) > 0.001) {
      el.style.transform = `scale(${sp.sx.toFixed(4)}, ${sp.sy.toFixed(4)})`;
    } else if (el.style.transform) {
      el.style.transform = "";
    }
  });
  requestAnimationFrame(stepSpringPhysics);
}

function flagEmojiFromCode(cc) {
  if (!cc || cc.length !== 2) return "🛡️";
  const codePoints = cc.toUpperCase().split("").map(c => 127397 + c.charCodeAt(0));
  return String.fromCodePoint(...codePoints);
}

function formatBytes(b) {
  if (b <= 0) return "0 B";
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
  document.querySelectorAll(".jelly").forEach(attachJellyPhysics);
  requestAnimationFrame(stepSpringPhysics);

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
    }, 150);
  }

  loadSlotIntoForm(state.activeSlot);
  renderSlotCapsules();
  renderQuickPills();
  startCanvasLoops();

  setInterval(pollBackendStatus, 1000);
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
  document.getElementById("lblWebrtcState").textContent = isSpoof ? "IP" : "N/A";

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
  filtered.forEach(app => {
    const selected = state.routeAllApps || state.selectedUids.includes(app.uid);
    const row = document.createElement("div");
    row.className = "app-row jelly" + (selected ? " selected" : "");
    row.onclick = () => toggleAppUid(app.uid);
    row.innerHTML = `
      <div>
        <div class="app-name">${app.name}</div>
        <div class="app-pkg">${app.pkg}</div>
      </div>
      <div class="app-check">✓</div>
    `;
    container.appendChild(row);
    attachJellyPhysics(row);
  });
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
      window.NamelessBridge.showToast("Invalid URI");
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
      if (window.NamelessBridge) window.NamelessBridge.showToast("Set Host first");
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
  el.textContent = ms > 0 ? `${ms} ms` : "Fail";
};

function applyPolledStatus(st, firstInit) {
  const wasRunning = state.isRunning;
  state.isRunning = !!st.running;
  state.runningSlot = state.isRunning ? (st.slot || 0) : null;

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

    state.rxHistory.push(rxRate);
    if (state.rxHistory.length > 28) state.rxHistory.shift();
    state.txHistory.push(txRate);
    if (state.txHistory.length > 28) state.txHistory.shift();

    document.getElementById("rxSpeedText").textContent = formatBytes(rxRate) + "/s";
    document.getElementById("txSpeedText").textContent = formatBytes(txRate) + "/s";
    document.getElementById("rxTotalText").textContent = formatBytes(Math.max(0, curRx - state.startRx));
    document.getElementById("txTotalText").textContent = formatBytes(Math.max(0, curTx - state.startTx));

    const g = st.geo;
    const ip = (g && g.query) || st.exitIp || state.slots[state.runningSlot || 0].host || "Active";
    document.getElementById("exitIpText").textContent = ip;
    if (g && g.country) {
      document.getElementById("exitFlag").textContent = flagEmojiFromCode(g.countryCode);
      document.getElementById("exitLocText").textContent = g.countryCode || g.country;
    } else {
      document.getElementById("exitFlag").textContent = "🛡️";
      document.getElementById("exitLocText").textContent = "Tunnel";
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

function switchTab(idx) {
  state.activeTab = idx;
  document.querySelectorAll(".tab-view").forEach((pane, i) => {
    pane.classList.toggle("active", i === idx);
  });
  document.querySelectorAll(".dock-orb").forEach((btn, i) => {
    btn.classList.toggle("active", i === idx);
  });

  pulseAllVisibleBubbles();

  if (window.NamelessBridge) {
    if (idx === 1) {
      setTimeout(() => {
        const toolbar = document.getElementById("checkerToolbar");
        const dock = document.getElementById("bottomDock");
        const topDp = Math.round(toolbar.getBoundingClientRect().bottom + 8);
        const bottomDp = Math.round((window.innerHeight - dock.getBoundingClientRect().top) + 8);
        const url = document.getElementById("checkerUrlInput").value || "https://ipfighter.com";
        window.NamelessBridge.showCheckerView(url, topDp, bottomDp);
      }, 60);
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
  document.getElementById("checkerUrlInput").value = url;
  if (window.NamelessBridge) {
    window.NamelessBridge.navigateChecker(url);
  }
}

function goCheckerUrl() {
  let u = (document.getElementById("checkerUrlInput").value || "").trim();
  if (!u) return;
  if (!u.startsWith("http://") && !u.startsWith("https://")) {
    u = "https://" + u;
    document.getElementById("checkerUrlInput").value = u;
  }
  if (window.NamelessBridge) {
    window.NamelessBridge.navigateChecker(u);
  }
}

function reloadCheckerView(wipe) {
  if (window.NamelessBridge) {
    window.NamelessBridge.reloadChecker(!!wipe);
  }
}

window.onCheckerState = function(url, loading) {
  if (url) {
    document.getElementById("checkerUrlInput").value = url;
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
    window.NamelessBridge.showToast(ok ? "Exported" : "Export failed");
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

// ============================================================================
// 120fps Liquid Blob Satellites & Wave Canvas Engine
// ============================================================================
function startCanvasLoops() {
  const orbCanvas = document.getElementById("orbitalCanvas");
  const waveCanvas = document.getElementById("waveCanvas");
  const orbCtx = orbCanvas.getContext("2d");
  const waveCtx = waveCanvas.getContext("2d");
  let phase = 0;

  function renderFrame() {
    phase += state.isRunning ? 0.052 : 0.022;

    orbCtx.clearRect(0, 0, orbCanvas.width, orbCanvas.height);
    const cx = orbCanvas.width / 2;
    const cy = orbCanvas.height / 2;
    const baseColor = state.isRunning ? "52, 211, 153" : "56, 189, 248";

    // Soft wobbling jelly halo + orbiting liquid droplets
    for (let i = 0; i < 6; i++) {
      const ang = phase * (i % 2 === 0 ? 1 : -0.8) + (i * Math.PI) / 3;
      const dist = 88 + Math.sin(phase * 1.8 + i) * 7;
      const bx = cx + Math.cos(ang) * dist;
      const by = cy + Math.sin(ang) * dist;
      const rad = 7 + (i % 3) * 3 + Math.cos(phase * 2 + i) * 1.8;

      orbCtx.beginPath();
      orbCtx.arc(bx, by, rad, 0, Math.PI * 2);
      orbCtx.fillStyle = `rgba(${baseColor}, ${0.32 - i * 0.03})`;
      orbCtx.fill();
    }

    waveCtx.clearRect(0, 0, waveCanvas.width, waveCanvas.height);
    const w = waveCanvas.width;
    const h = waveCanvas.height;
    const maxVal = Math.max(1024, ...state.rxHistory, ...state.txHistory);

    function drawSeries(arr, stroke, fill) {
      waveCtx.beginPath();
      for (let i = 0; i < arr.length; i++) {
        const x = (i / (arr.length - 1)) * w;
        const norm = Math.min(1, arr[i] / maxVal);
        const y = h - 7 - norm * (h - 16) + Math.sin(phase * 2.2 + i * 0.4) * (state.isRunning ? 2.5 : 0.9);
        if (i === 0) waveCtx.moveTo(x, y);
        else waveCtx.lineTo(x, y);
      }
      waveCtx.strokeStyle = stroke;
      waveCtx.lineWidth = 2.4;
      waveCtx.stroke();
      waveCtx.lineTo(w, h);
      waveCtx.lineTo(0, h);
      waveCtx.closePath();
      waveCtx.fillStyle = fill;
      waveCtx.fill();
    }

    drawSeries(state.rxHistory, "#38BDF8", "rgba(56, 189, 248, 0.18)");
    drawSeries(state.txHistory, "#34D399", "rgba(52, 211, 153, 0.14)");

    requestAnimationFrame(renderFrame);
  }

  requestAnimationFrame(renderFrame);
}

window.addEventListener("DOMContentLoaded", initApp);
