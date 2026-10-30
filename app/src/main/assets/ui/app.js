// ============================================================================
// Nameless Proxy • 100% GPU-Accelerated Jelly UI & Dynamic Protocol Controller
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
  auditMode: false,
  showPassword: false
};

let saveDebounceTimer = null;

// ============================================================================
// Uninterruptible GPU Touch-to-Bubble Squish + Expanding Water-Droplet Ripple
// Triggered immediately on pointerdown so even a 20ms tap plays the full bounce!
// ============================================================================
function bindGpuJellyTouch() {
  const rippleStage = document.getElementById("rippleStage");

  document.addEventListener("pointerdown", (e) => {
    // 1. Spawn GPU expanding liquid droplet ring at exact touch point
    if (rippleStage && typeof e.clientX === "number") {
      const drop = document.createElement("div");
      drop.className = "touch-droplet";
      drop.style.left = `${e.clientX}px`;
      drop.style.top = `${e.clientY}px`;
      rippleStage.appendChild(drop);
      setTimeout(() => drop.remove(), 390);
    }

    // 2. Trigger full uninterruptible 3D squish + elastic rebound on .jelly element
    const el = e.target.closest(".jelly");
    if (!el) return;
    el.classList.remove("is-squishing");
    void el.offsetWidth;
    el.classList.add("is-squishing");
  }, { passive: true });
}

function pulseVisibleTabBubbles(tabIdx) {
  const pane = document.getElementById(`tab-${tabIdx}`);
  if (!pane) return;
  const items = pane.querySelectorAll(".jelly");
  items.forEach((el, i) => {
    if (i > 10) return;
    setTimeout(() => {
      el.classList.remove("is-squishing");
      void el.offsetWidth;
      el.classList.add("is-squishing");
    }, i * 16);
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

function buildSlotsPayload() {
  return JSON.stringify({
    activeSlot: state.activeSlot,
    routeHotspot: state.routeHotspot,
    routeAllApps: state.routeAllApps,
    selectedUids: state.selectedUids,
    webrtcMode: state.webrtcMode,
    slots: state.slots
  });
}

function persistAllSlots() {
  if (saveDebounceTimer) clearTimeout(saveDebounceTimer);
  saveDebounceTimer = setTimeout(() => {
    const payload = buildSlotsPayload();
    if (window.NamelessBridge) {
      window.NamelessBridge.saveSlots(payload);
    }
    const cur = state.slots[state.activeSlot] || defaultSlot();
    const statusEl = document.getElementById("adbSaveStatusText");
    if (statusEl && cur.host) {
      statusEl.textContent = `✓ Saved to /data/adb/nameless_proxy/config_u0_s${state.activeSlot}.json & backup_u0_s${state.activeSlot}.json`;
    }
  }, 280);
}

function saveSlotToAdbNow() {
  saveFormIntoSlot(state.activeSlot);
  renderSlotCapsules();
  const payload = buildSlotsPayload();
  let ok = false;
  if (window.NamelessBridge) {
    ok = window.NamelessBridge.saveSlotsNow(payload);
    window.NamelessBridge.showToast(
      ok
        ? `Saved P${state.activeSlot + 1} in /data/adb/nameless_proxy`
        : "Saved to local storage"
    );
  }
  const statusEl = document.getElementById("adbSaveStatusText");
  if (statusEl) {
    statusEl.textContent = `✓ Saved /data/adb/nameless_proxy/config_u0_s${state.activeSlot}.json & backup_u0_s${state.activeSlot}.json`;
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
    const slotCfg = state.slots[idx] || defaultSlot();
    const hasHost = !!(slotCfg.host && slotCfg.host.trim() !== "");
    btn.classList.toggle("active", idx === state.activeSlot);
    btn.classList.toggle("running", state.isRunning && idx === state.runningSlot);
    btn.classList.toggle("configured", hasHost);
  });

  const cur = state.slots[state.activeSlot] || defaultSlot();
  const protoBadge = document.getElementById("hubProtoBadge");
  const nodeLabel = document.getElementById("hubNodeLabel");
  const hubAdbStatus = document.getElementById("hubAdbStatus");
  const hubSlotShort = document.getElementById("hubSlotShort");

  if (protoBadge) protoBadge.textContent = cur.type || "SOCKS5";
  if (hubSlotShort) hubSlotShort.textContent = `Slot P${state.activeSlot + 1}`;

  if (cur.host && cur.host.trim() !== "") {
    const authTag = (cur.username || cur.password) ? " • Auth ✓" : "";
    nodeLabel.textContent = `${cur.host}:${cur.port || 1080}${authTag}`;
    if (hubAdbStatus) {
      hubAdbStatus.textContent = `Saved in /data/adb/nameless_proxy/config_u0_s${state.activeSlot}.json`;
    }
  } else {
    nodeLabel.textContent = `Tap to Enter P${state.activeSlot + 1} Server & Port`;
    if (hubAdbStatus) {
      hubAdbStatus.textContent = `Protocol: ${cur.type || "SOCKS5"} • Tap to configure`;
    }
  }
  document.getElementById("editingSlotTitle").textContent = `SLOT P${state.activeSlot + 1} • ${cur.type || "SOCKS5"}`;
}

// ============================================================================
// Dynamic Protocol-Specific Form Configurator
// Adapts fields, labels, and placeholders for SOCKS5, HTTP, SOCKS4, SS, VLESS, VMESS, TROJAN, HY2
// ============================================================================
function updateProtocolFieldsUI(proto) {
  const p = (proto || "SOCKS5").toUpperCase();

  const groupUser = document.getElementById("groupUser");
  const groupCipher = document.getElementById("groupCipher");
  const groupPass = document.getElementById("groupPass");
  const groupSni = document.getElementById("groupSni");
  const groupReality = document.getElementById("groupReality");

  const lblUser = document.getElementById("lblUser");
  const inpUser = document.getElementById("inpUser");
  const lblPass = document.getElementById("lblPass");
  const inpPass = document.getElementById("inpPass");
  const lblSni = document.getElementById("lblSni");

  // Reset visibility
  groupUser.classList.add("hidden");
  groupCipher.classList.add("hidden");
  groupPass.classList.add("hidden");
  groupSni.classList.add("hidden");
  groupReality.classList.add("hidden");

  if (p === "SOCKS5") {
    groupUser.classList.remove("hidden");
    groupPass.classList.remove("hidden");
    lblUser.textContent = "👤 SOCKS5 USERNAME (OPTIONAL)";
    inpUser.placeholder = "Enter SOCKS5 username";
    lblPass.textContent = "🔑 SOCKS5 PASSWORD (OPTIONAL)";
    inpPass.placeholder = "Enter SOCKS5 password";
  } else if (p === "HTTP") {
    groupUser.classList.remove("hidden");
    groupPass.classList.remove("hidden");
    groupSni.classList.remove("hidden");
    lblUser.textContent = "👤 HTTP PROXY USERNAME (OPTIONAL)";
    inpUser.placeholder = "Enter HTTP username";
    lblPass.textContent = "🔑 HTTP PROXY PASSWORD (OPTIONAL)";
    inpPass.placeholder = "Enter HTTP password";
    lblSni.textContent = "🔒 TLS SNI (OPTIONAL FOR HTTPS PROXY)";
  } else if (p === "SOCKS4") {
    groupUser.classList.remove("hidden");
    lblUser.textContent = "👤 SOCKS4 USER ID (OPTIONAL)";
    inpUser.placeholder = "Optional SOCKS4 User ID";
  } else if (p === "SHADOWSOCKS") {
    groupCipher.classList.remove("hidden");
    groupPass.classList.remove("hidden");
    lblPass.textContent = "🔑 SHADOWSOCKS PASSWORD / PSK";
    inpPass.placeholder = "Enter Shadowsocks password";
  } else if (p === "VLESS") {
    groupUser.classList.remove("hidden");
    groupSni.classList.remove("hidden");
    groupReality.classList.remove("hidden");
    lblUser.textContent = "🆔 VLESS UUID";
    inpUser.placeholder = "e.g. a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    lblSni.textContent = "🔒 TLS / REALITY SNI (SERVER NAME)";
  } else if (p === "VMESS") {
    groupUser.classList.remove("hidden");
    groupSni.classList.remove("hidden");
    lblUser.textContent = "🆔 VMESS UUID";
    inpUser.placeholder = "e.g. a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    lblSni.textContent = "🔒 TLS SNI / WS HOST";
  } else if (p === "TROJAN") {
    groupPass.classList.remove("hidden");
    groupSni.classList.remove("hidden");
    lblPass.textContent = "🔑 TROJAN PASSWORD";
    inpPass.placeholder = "Enter Trojan password";
    lblSni.textContent = "🔒 TLS SNI (SERVER NAME)";
  } else if (p === "HYSTERIA2") {
    groupPass.classList.remove("hidden");
    groupSni.classList.remove("hidden");
    lblPass.textContent = "🔑 HYSTERIA2 AUTH PASSWORD";
    inpPass.placeholder = "Enter Hysteria2 password";
    lblSni.textContent = "🔒 TLS SNI (SERVER NAME)";
  }
}

function togglePasswordVisibility() {
  state.showPassword = !state.showPassword;
  const inpPass = document.getElementById("inpPass");
  const btn = document.getElementById("btnTogglePass");
  if (inpPass) {
    inpPass.type = state.showPassword ? "text" : "password";
  }
  if (btn) {
    btn.textContent = state.showPassword ? "🙈" : "👁";
  }
}

function pickSsCipher(cipher) {
  const inp = document.getElementById("inpCipher");
  if (inp) {
    inp.value = cipher;
    onFormEdited();
  }
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
  const proto = (s.type || "SOCKS5").toUpperCase();

  document.getElementById("inpHost").value = s.host || "";
  document.getElementById("inpPort").value = s.port || 1080;
  document.getElementById("inpUser").value = s.username || "";
  document.getElementById("inpPass").value = s.password || "";
  document.getElementById("inpSni").value = s.sni || "";
  document.getElementById("inpCipher").value = s.ssMethod || "chacha20-ietf-poly1305";
  document.getElementById("inpExtra").value = s.realityPublicKey || "";
  document.getElementById("inpShortId").value = s.realityShortId || "";

  document.querySelectorAll(".proto-orb").forEach(btn => {
    btn.classList.toggle("active", btn.getAttribute("data-proto") === proto);
  });

  updateProtocolFieldsUI(proto);
}

function saveFormIntoSlot(idx) {
  const s = state.slots[idx] || defaultSlot();
  const proto = (s.type || "SOCKS5").toUpperCase();

  s.host = (document.getElementById("inpHost").value || "").trim();
  s.port = parseInt(document.getElementById("inpPort").value || "1080", 10) || 1080;
  s.username = (document.getElementById("inpUser").value || "").trim();
  s.password = (document.getElementById("inpPass").value || "").trim();
  s.sni = (document.getElementById("inpSni").value || "").trim();
  s.ssMethod = (document.getElementById("inpCipher").value || "chacha20-ietf-poly1305").trim();
  s.realityPublicKey = (document.getElementById("inpExtra").value || "").trim();
  s.realityShortId = (document.getElementById("inpShortId").value || "").trim();

  if ((proto === "VLESS" || proto === "VMESS") && s.username && !s.password) {
    s.password = s.username;
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
  saveFormIntoSlot(state.activeSlot);
  state.slots[state.activeSlot].type = proto;
  document.querySelectorAll(".proto-orb").forEach(btn => {
    btn.classList.toggle("active", btn.getAttribute("data-proto") === proto);
  });
  updateProtocolFieldsUI(proto);
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
      saveSlotToAdbNow();
    } else {
      window.NamelessBridge.showToast("Clipboard has no valid proxy URI");
    }
  } catch (e) {}
}

function toggleTunnel() {
  if (state.isTransitioning) return;
  saveFormIntoSlot(state.activeSlot);
  const payload = buildSlotsPayload();
  if (window.NamelessBridge) {
    window.NamelessBridge.saveSlots(payload);
  }

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
      if (window.NamelessBridge) window.NamelessBridge.showToast("Enter Server Host & Port first");
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
  const payload = buildSlotsPayload();
  if (window.NamelessBridge) {
    window.NamelessBridge.saveSlotsNow(payload);
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
        window.NamelessBridge.showToast("Imported 5 slots into /data/adb/nameless_proxy");
        return;
      }
    } catch (e) {}
  }
  window.NamelessBridge.showToast("No backup found");
}

window.addEventListener("DOMContentLoaded", initApp);
