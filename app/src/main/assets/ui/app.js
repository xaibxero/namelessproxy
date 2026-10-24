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
  if (window.NamelessBridge) {
    try {
      const raw = JSON.parse(window.NamelessBridge.getInitialState() || "{}");
      const rootBadge = document.getElementById("rootBadge");
      if (raw.root) {
        rootBadge.textContent = `ROOT • U#${raw.userId || 0}`;
      } else {
        rootBadge.textContent = "NO ROOT";
        rootBadge.style.color = "#FB7185";
      }
      if (raw.network) {
        document.getElementById("netSummaryText").textContent = raw.network;
      }
      if (raw.slotsJson) {
        const parsed = JSON.parse(raw.slotsJson);
        if (Array.isArray(parsed.slots) && parsed.slots.length === 5) {
          state.slots = parsed.slots;
        }
        if (typeof parsed.activeSlot === "number") {
          state.activeSlot = parsed.activeSlot;
        }
        if (typeof parsed.routeHotspot === "boolean") {
          state.routeHotspot = parsed.routeHotspot;
        }
      }
      if (raw.status) {
        applyPolledStatus(raw.status, true);
      }
    } catch (e) {}
  }

  loadSlotIntoForm(state.activeSlot);
  renderSlotCapsules();
  renderHotspotToggle();
  startCanvasLoops();

  setInterval(pollBackendStatus, 1000);
}

function persistAllSlots() {
  const payload = JSON.stringify({
    activeSlot: state.activeSlot,
    routeHotspot: state.routeHotspot,
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
  for (let i = 0; i < 5; i++) {
    const s = state.slots[i] || defaultSlot();
    const protoEl = document.getElementById(`slotProto${i}`);
    if (protoEl) protoEl.textContent = s.type || "SOCKS5";
  }
  document.querySelectorAll(".slot-pill").forEach((btn, idx) => {
    btn.classList.toggle("active", idx === state.activeSlot);
    btn.classList.toggle("running", state.isRunning && idx === state.runningSlot);
  });

  const cur = state.slots[state.activeSlot] || defaultSlot();
  const nodeLabel = document.getElementById("hubNodeLabel");
  if (cur.host && cur.host.trim() !== "") {
    nodeLabel.textContent = `${cur.host}:${cur.port} (${cur.type})`;
  } else {
    nodeLabel.textContent = `Slot P${state.activeSlot + 1} Empty`;
  }
  document.getElementById("editingSlotTitle").textContent = `SLOT P${state.activeSlot + 1} CONFIGURATION`;
}

function loadSlotIntoForm(idx) {
  const s = state.slots[idx] || defaultSlot();
  document.getElementById("inpHost").value = s.host || "";
  document.getElementById("inpPort").value = s.port || 1080;
  document.getElementById("inpUser").value = s.username || "";
  document.getElementById("inpPass").value = s.password || "";
  document.getElementById("inpSni").value = s.sni || "";
  document.getElementById("inpExtra").value = s.realityPublicKey || s.ssMethod || "";
  document.querySelectorAll(".proto-chip").forEach(btn => {
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
  document.querySelectorAll(".proto-chip").forEach(btn => {
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
      window.NamelessBridge.showToast("Imported URI into Slot P" + (state.activeSlot + 1));
    } else {
      window.NamelessBridge.showToast("No valid proxy URI in clipboard");
    }
  } catch (e) {}
}

function toggleHotspotSetting() {
  state.routeHotspot = !state.routeHotspot;
  renderHotspotToggle();
  persistAllSlots();
}

function renderHotspotToggle() {
  const sw = document.getElementById("hotspotSwitch");
  if (sw) sw.classList.toggle("on", state.routeHotspot);
}

function toggleTunnel() {
  if (state.isTransitioning) return;
  saveFormIntoSlot(state.activeSlot);
  persistAllSlots();

  if (state.isRunning && state.runningSlot === state.activeSlot) {
    state.isTransitioning = true;
    document.getElementById("coreStateText").textContent = "STOPPING...";
    if (window.NamelessBridge) {
      window.NamelessBridge.stopTunnelAsync();
    }
  } else {
    const cfg = Object.assign({}, state.slots[state.activeSlot], {
      slot: state.activeSlot,
      routeHotspot: state.routeHotspot
    });
    if (!cfg.host || cfg.host.trim() === "") {
      switchTab(2);
      if (window.NamelessBridge) window.NamelessBridge.showToast("Enter proxy host first");
      return;
    }
    state.isTransitioning = true;
    document.getElementById("coreStateText").textContent = "CONNECTING...";
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
  document.getElementById("hubPingLabel").textContent = "Pinging...";
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
      coreStateText.textContent = `ONLINE • P${(state.runningSlot || 0) + 1}`;
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
    document.getElementById("rxTotalText").textContent = "Total: " + formatBytes(Math.max(0, curRx - state.startRx));
    document.getElementById("txTotalText").textContent = "Total: " + formatBytes(Math.max(0, curTx - state.startTx));

    const g = st.geo;
    const ip = (g && g.query) || st.exitIp || state.slots[state.runningSlot || 0].host || "Protected";
    document.getElementById("exitIpText").textContent = ip;
    if (g && g.country) {
      document.getElementById("exitFlag").textContent = flagEmojiFromCode(g.countryCode);
      document.getElementById("exitLocText").textContent = `${g.city ? g.city + ", " : ""}${g.country} • ${g.isp || g.org || "Tunnel"}`;
    } else {
      document.getElementById("exitFlag").textContent = "🛡️";
      document.getElementById("exitLocText").textContent = "Active Root Tunnel • Verifying Exit GeoIP...";
    }
  } else {
    state.sessionStartMs = 0;
    if (!state.isTransitioning) {
      coreStateText.textContent = "STANDBY";
    }
    coreTimerText.textContent = "TAP TO CONNECT";
    document.getElementById("rxSpeedText").textContent = "0 B/s";
    document.getElementById("txSpeedText").textContent = "0 B/s";
    document.getElementById("exitFlag").textContent = "🌐";
    document.getElementById("exitIpText").textContent = "Tunnel Offline";
    document.getElementById("exitLocText").textContent = "Connect a slot to mask Phone & Hotspot";
  }

  renderSlotCapsules();
}

function pollBackendStatus() {
  if (!window.NamelessBridge) return;
  try {
    const raw = JSON.parse(window.NamelessBridge.pollStatus() || "{}");
    applyPolledStatus(raw, false);
    if (state.activeTab === 3) {
      refreshLogsNow();
    }
  } catch (e) {}
}

function switchTab(idx) {
  state.activeTab = idx;
  document.querySelectorAll(".tab-pane").forEach((pane, i) => {
    pane.classList.toggle("active", i === idx);
  });
  document.querySelectorAll(".dock-item").forEach((btn, i) => {
    btn.classList.toggle("active", i === idx);
  });

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

  if (idx === 3) {
    refreshLogsNow();
  }
}

function loadCheckerPreset(url, btnId) {
  document.querySelectorAll(".preset-pill").forEach(b => b.classList.remove("active"));
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
  document.getElementById("logConsole").textContent = text || "No kernel output.";
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
    window.NamelessBridge.showToast(ok ? "Exported to /sdcard/Download/nameless_proxy_backup.json" : "Export failed");
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
        window.NamelessBridge.showToast("Imported all 5 slots");
        return;
      }
    } catch (e) {}
  }
  window.NamelessBridge.showToast("Backup file not found");
}

function startCanvasLoops() {
  const orbCanvas = document.getElementById("orbitalCanvas");
  const waveCanvas = document.getElementById("waveCanvas");
  const orbCtx = orbCanvas.getContext("2d");
  const waveCtx = waveCanvas.getContext("2d");
  let phase = 0;

  function renderFrame() {
    phase += state.isRunning ? 0.045 : 0.018;

    orbCtx.clearRect(0, 0, orbCanvas.width, orbCanvas.height);
    const cx = orbCanvas.width / 2;
    const cy = orbCanvas.height / 2;
    const baseColor = state.isRunning ? "52, 211, 153" : "56, 189, 248";

    for (let r = 0; r < 3; r++) {
      const radius = 74 + r * 11 + Math.sin(phase * 1.6 + r) * 3;
      orbCtx.beginPath();
      orbCtx.arc(cx, cy, radius, phase + r * 1.1, phase + r * 1.1 + Math.PI * 1.35);
      orbCtx.strokeStyle = `rgba(${baseColor}, ${0.42 - r * 0.11})`;
      orbCtx.lineWidth = 2.6 - r * 0.5;
      orbCtx.lineCap = "round";
      orbCtx.stroke();
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
        const y = h - 8 - norm * (h - 18) + Math.sin(phase * 2 + i * 0.35) * (state.isRunning ? 2.2 : 0.7);
        if (i === 0) waveCtx.moveTo(x, y);
        else waveCtx.lineTo(x, y);
      }
      waveCtx.strokeStyle = stroke;
      waveCtx.lineWidth = 2.2;
      waveCtx.stroke();
      waveCtx.lineTo(w, h);
      waveCtx.lineTo(0, h);
      waveCtx.closePath();
      waveCtx.fillStyle = fill;
      waveCtx.fill();
    }

    drawSeries(state.rxHistory, "#38BDF8", "rgba(56, 189, 248, 0.16)");
    drawSeries(state.txHistory, "#34D399", "rgba(52, 211, 153, 0.12)");

    requestAnimationFrame(renderFrame);
  }

  requestAnimationFrame(renderFrame);
}

window.addEventListener("DOMContentLoaded", initApp);
