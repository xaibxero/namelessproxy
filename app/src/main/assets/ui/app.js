// ============================================================================
// Nameless Proxy • Material 3 Expressive × Deep Liquid Glass Controller (v34)
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
  realityShortId: "",
  flow: "xtls-rprx-vision",
  vmessSecurity: "auto",
  alterId: 0,
  transport: "tcp",
  wsPath: ""
});

const PROTO_SCHEMAS = {
  SOCKS5: {
    fields: ["user", "pass"],
    labels: { user: "USERNAME", pass: "PASSWORD" },
    placeholders: { user: "Optional", pass: "Optional" }
  },
  SOCKS4: {
    fields: ["user"],
    labels: { user: "USER ID (OPTIONAL)" },
    placeholders: { user: "Optional" }
  },
  HTTP: {
    fields: ["user", "pass", "sni"],
    labels: { user: "USERNAME", pass: "PASSWORD", sni: "TLS SNI (OPTIONAL)" },
    placeholders: { user: "Optional", pass: "Optional" }
  },
  SHADOWSOCKS: {
    fields: ["cipher", "pass"],
    labels: { pass: "PASSWORD / PSK" },
    placeholders: { pass: "Required" }
  },
  VMESS: {
    fields: ["user", "vmessOpts", "transport", "sni", "wsPath"],
    labels: { user: "VMESS UUID", sni: "TLS SNI / HOST" },
    placeholders: { user: "UUID (e.g. b831381d-...)" }
  },
  VLESS: {
    fields: ["user", "vlessFlow", "transport", "sni", "wsPath", "reality"],
    labels: { user: "VLESS UUID", sni: "TLS / REALITY SNI" },
    placeholders: { user: "UUID (e.g. b831381d-...)" }
  },
  TROJAN: {
    fields: ["pass", "transport", "sni", "wsPath"],
    labels: { pass: "TROJAN PASSWORD", sni: "TLS SNI" },
    placeholders: { pass: "Required" }
  }
};

let state = {
  myUserId: 0,
  activeTab: 0,
  activeSlot: 0,
  runningSlot: null,
  isRunning: false,
  isTransitioning: false,
  transitionTarget: null,
  routeHotspot: true,
  routeAllApps: true,
  appFilterMode: "WHITELIST",
  bootOnStart: false,
  selectedUids: [],
  installedApps: [],
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
let liveAppReloadTimer = null;
let transitionResetTimer = null;
let popupHideTimer = null;

function triggerBubblePop(el) {
  if (!el) return;
  el.classList.remove("bubble-pop");
  void el.offsetWidth;
  el.classList.add("bubble-pop");
}

function showGlassPopup(msg) {
  const popup = document.getElementById("glassPopup");
  const txt = document.getElementById("glassPopupText");
  if (!popup || !txt) return;
  txt.textContent = msg;
  popup.classList.add("show");
  if (popupHideTimer) clearTimeout(popupHideTimer);
  popupHideTimer = setTimeout(() => {
    popup.classList.remove("show");
  }, 1900);
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
  if (window.NamelessBridge) {
    try {
      const raw = JSON.parse(window.NamelessBridge.getInitialState() || "{}");
      if (typeof raw.userId === "number") {
        state.myUserId = raw.userId;
        const badge = document.getElementById("userProfileBadge");
        if (badge) {
          badge.textContent = raw.userId === 0 ? "U0 Owner" : `User ${raw.userId}`;
        }
      }
      if (raw.network) {
        document.getElementById("netSummaryText").textContent = raw.network;
      }
      if (raw.slotsJson) {
        const parsed = JSON.parse(raw.slotsJson);
        if (Array.isArray(parsed.slots) && parsed.slots.length === 5) {
          state.slots = parsed.slots.map(s => Object.assign(defaultSlot(), s || {}));
        }
        if (typeof parsed.activeSlot === "number") state.activeSlot = parsed.activeSlot;
        if (typeof parsed.routeHotspot === "boolean") state.routeHotspot = parsed.routeHotspot;
        if (typeof parsed.routeAllApps === "boolean") state.routeAllApps = parsed.routeAllApps;
        if (typeof parsed.bootOnStart === "boolean") state.bootOnStart = parsed.bootOnStart;
        if (Array.isArray(parsed.selectedUids)) state.selectedUids = parsed.selectedUids;
        if (state.selectedUids.length === 0) state.routeAllApps = true;
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
          renderQuickPills();
          renderAppsList();
        }
      } catch (e) {}
    }, 80);
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
    appFilterMode: "WHITELIST",
    bootOnStart: state.bootOnStart,
    selectedUids: state.selectedUids,
    webrtcMode: "SPOOF",
    slots: state.slots
  });
}

function persistAllSlots() {
  if (saveDebounceTimer) clearTimeout(saveDebounceTimer);
  saveDebounceTimer = setTimeout(() => {
    if (window.NamelessBridge) {
      window.NamelessBridge.saveSlots(buildSlotsPayload());
    }
  }, 180);
}

function scheduleLiveReloadIfRunning(msg) {
  persistAllSlots();
  if (msg) showGlassPopup(msg);
  if (!state.isRunning) return;
  if (liveAppReloadTimer) clearTimeout(liveAppReloadTimer);
  liveAppReloadTimer = setTimeout(() => {
    if (!state.isRunning || !window.NamelessBridge) return;
    const activeIdx = state.runningSlot !== null ? state.runningSlot : state.activeSlot;
    const targetUidsStr = (state.routeAllApps || state.selectedUids.length === 0)
      ? "ALL"
      : state.selectedUids.join(",");
    const cfg = Object.assign({}, state.slots[activeIdx], {
      slot: activeIdx,
      routeHotspot: state.routeHotspot,
      targetUids: targetUidsStr,
      appFilterMode: "WHITELIST",
      webrtcMode: "SPOOF"
    });
    window.NamelessBridge.startTunnelAsync(JSON.stringify(cfg));
  }, 450);
}

function selectSlot(idx) {
  saveFormIntoSlot(state.activeSlot);
  state.activeSlot = idx;
  loadSlotIntoForm(idx);
  renderSlotCapsules();
  const btn = document.querySelector(`.slot-pill[data-slot="${idx}"]`);
  triggerBubblePop(btn);
  showGlassPopup(`Slot ${idx + 1} Selected`);
  persistAllSlots();
}

function renderSlotCapsules() {
  document.querySelectorAll(".slot-pill").forEach((btn, idx) => {
    const slotCfg = state.slots[idx] || defaultSlot();
    const hasHost = !!(slotCfg.host && slotCfg.host.trim() !== "");
    btn.classList.toggle("active", idx === state.activeSlot);
    btn.classList.toggle("running", state.isRunning && idx === state.runningSlot);
    btn.classList.toggle("configured", hasHost);
  });

  const cur = state.slots[state.activeSlot] || defaultSlot();
  document.getElementById("editingSlotTitle").textContent = `SLOT ${state.activeSlot + 1} • ${cur.type || "SOCKS5"}`;

  const coreBtn = document.getElementById("coreBtn");
  const coreIcon = document.getElementById("coreIcon");
  coreBtn.classList.toggle("online", state.isRunning);
  if (!state.isTransitioning) {
    if (state.isRunning) {
      coreIcon.textContent = "STOP";
    } else {
      coreIcon.textContent = "CONNECT";
    }
  }
}

function updateProtocolFieldsUI(proto) {
  const p = (proto || "SOCKS5").toUpperCase();
  const schema = PROTO_SCHEMAS[p] || PROTO_SCHEMAS.SOCKS5;

  const credsGrid = document.getElementById("credsGrid");
  const groupUser = document.getElementById("groupUser");
  const groupCipher = document.getElementById("groupCipher");
  const groupPass = document.getElementById("groupPass");
  const groupVmessOpts = document.getElementById("groupVmessOpts");
  const groupTransport = document.getElementById("groupTransport");
  const groupVlessFlow = document.getElementById("groupVlessFlow");
  const groupSni = document.getElementById("groupSni");
  const groupWsPath = document.getElementById("groupWsPath");
  const groupReality = document.getElementById("groupReality");

  const hasUser = schema.fields.includes("user");
  const hasCipher = schema.fields.includes("cipher");
  const hasPass = schema.fields.includes("pass");
  const hasSni = schema.fields.includes("sni");
  const hasWsPath = schema.fields.includes("wsPath");

  groupUser.classList.toggle("hidden", !hasUser);
  groupCipher.classList.toggle("hidden", !hasCipher);
  groupPass.classList.toggle("hidden", !hasPass);
  groupVmessOpts.classList.toggle("hidden", !schema.fields.includes("vmessOpts"));
  groupTransport.classList.toggle("hidden", !schema.fields.includes("transport"));
  groupVlessFlow.classList.toggle("hidden", !schema.fields.includes("vlessFlow"));
  groupSni.classList.toggle("hidden", !hasSni);
  groupWsPath.classList.toggle("hidden", !hasWsPath);
  groupReality.classList.toggle("hidden", !schema.fields.includes("reality"));

  const visibleCreds = [hasUser, hasCipher, hasPass].filter(Boolean).length;
  credsGrid.classList.toggle("single-col", visibleCreds === 1);
  groupSni.classList.toggle("single-col", !hasWsPath);

  if (schema.labels.user) document.getElementById("lblUser").textContent = schema.labels.user;
  if (schema.labels.pass) document.getElementById("lblPass").textContent = schema.labels.pass;
  if (schema.labels.sni) document.getElementById("lblSni").textContent = schema.labels.sni;

  if (schema.placeholders.user) document.getElementById("inpUser").placeholder = schema.placeholders.user;
  if (schema.placeholders.pass) document.getElementById("inpPass").placeholder = schema.placeholders.pass;
}

function togglePasswordVisibility() {
  state.showPassword = !state.showPassword;
  const inpPass = document.getElementById("inpPass");
  const svg = document.getElementById("eyeIconSvg");
  if (inpPass) inpPass.type = state.showPassword ? "text" : "password";
  if (svg) {
    svg.style.color = state.showPassword ? "#3DDC84" : "";
  }
}

function renderQuickPills() {
  const hsPill = document.getElementById("pillHotspot");
  hsPill.classList.toggle("on", state.routeHotspot);
  document.getElementById("lblHotspotState").textContent = state.routeHotspot ? "ON" : "OFF";

  const appsPill = document.getElementById("pillApps");
  appsPill.classList.toggle("on", true);
  if (state.routeAllApps || state.selectedUids.length === 0) {
    document.getElementById("lblAppsState").textContent = "ALL";
  } else {
    document.getElementById("lblAppsState").textContent = `${state.selectedUids.length} APP`;
  }

  const bootPill = document.getElementById("pillBoot");
  if (bootPill) {
    bootPill.classList.toggle("on", state.bootOnStart);
    document.getElementById("lblBootState").textContent = state.bootOnStart ? "ON" : "OFF";
  }

  const toggleBtn = document.getElementById("btnToggleSelectAll");
  const bannerTxt = document.getElementById("appModeBannerText");
  const allSelected = state.routeAllApps || (state.installedApps.length > 0 && state.selectedUids.length === state.installedApps.length);

  if (toggleBtn) {
    toggleBtn.classList.toggle("active", allSelected);
    toggleBtn.textContent = allSelected ? "Deselect All" : "Select All";
  }
  if (bannerTxt) {
    if (state.routeAllApps) {
      bannerTxt.textContent = "All apps in this profile route through proxy";
    } else if (state.selectedUids.length === 0) {
      bannerTxt.textContent = "No app selected (tap apps to route)";
    } else {
      bannerTxt.textContent = `${state.selectedUids.length} selected app(s) route through proxy`;
    }
  }
}

function toggleSelectAllApps() {
  const btn = document.getElementById("btnToggleSelectAll");
  triggerBubblePop(btn);
  const allSelected = state.routeAllApps || (state.installedApps.length > 0 && state.selectedUids.length === state.installedApps.length);
  if (allSelected) {
    state.routeAllApps = false;
    state.selectedUids = [];
    renderQuickPills();
    renderAppsList();
    persistAllSlots();
    showGlassPopup("Deselected all apps • Tap specific apps to route");
  } else {
    state.routeAllApps = true;
    state.selectedUids = [];
    renderQuickPills();
    renderAppsList();
    scheduleLiveReloadIfRunning("All Profile Apps Enabled");
  }
}

function toggleHotspotSetting() {
  state.routeHotspot = !state.routeHotspot;
  triggerBubblePop(document.getElementById("pillHotspot"));
  renderQuickPills();
  scheduleLiveReloadIfRunning(state.routeHotspot ? "Hotspot Routing Enabled" : "Hotspot Routing Disabled");
}

function toggleBootSetting() {
  state.bootOnStart = !state.bootOnStart;
  triggerBubblePop(document.getElementById("pillBoot"));
  renderQuickPills();
  persistAllSlots();
  showGlassPopup(state.bootOnStart ? "Fast-Boot (service.d) Enabled" : "Fast-Boot Disabled");
}

function toggleAppUid(uid, rowEl) {
  if (rowEl) triggerBubblePop(rowEl);
  if (state.routeAllApps) {
    state.routeAllApps = false;
    state.selectedUids = [uid];
  } else {
    const idx = state.selectedUids.indexOf(uid);
    if (idx >= 0) state.selectedUids.splice(idx, 1);
    else state.selectedUids.push(uid);
    if (state.selectedUids.length === 0) {
      state.routeAllApps = true;
    }
  }
  renderQuickPills();
  renderAppsList();
  const msg = state.routeAllApps
    ? "Routing All Profile Apps"
    : `Routing ${state.selectedUids.length} Selected App(s)`;
  scheduleLiveReloadIfRunning(msg);
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
    const isChecked = state.routeAllApps || state.selectedUids.includes(app.uid);
    const row = document.createElement("div");
    row.className = "app-row" + (isChecked ? " selected" : "");
    row.onclick = () => toggleAppUid(app.uid, row);
    row.innerHTML = `
      <div>
        <div class="app-name">${app.name}</div>
        <div class="app-pkg">${app.pkg}</div>
      </div>
      <div class="app-check"></div>
    `;
    frag.appendChild(row);
  });
  container.appendChild(frag);
}

function loadSlotIntoForm(idx) {
  const s = Object.assign(defaultSlot(), state.slots[idx] || {});
  let proto = (s.type || "SOCKS5").toUpperCase();
  if (!PROTO_SCHEMAS[proto]) proto = "SOCKS5";
  s.type = proto;

  document.getElementById("inpHost").value = s.host || "";
  document.getElementById("inpPort").value = s.port || 1080;
  document.getElementById("inpUser").value = s.username || "";
  document.getElementById("inpPass").value = s.password || "";
  document.getElementById("inpSni").value = s.sni || "";
  document.getElementById("inpCipher").value = s.ssMethod || "chacha20-ietf-poly1305";
  document.getElementById("inpExtra").value = s.realityPublicKey || "";
  document.getElementById("inpShortId").value = s.realityShortId || "";
  document.getElementById("inpFlow").value = s.flow || "xtls-rprx-vision";
  document.getElementById("inpVmessSec").value = s.vmessSecurity || "auto";
  document.getElementById("inpAlterId").value = typeof s.alterId === "number" ? s.alterId : 0;
  document.getElementById("inpTransport").value = s.transport || "tcp";
  document.getElementById("inpWsPath").value = s.wsPath || "";

  document.querySelectorAll(".proto-chip").forEach(btn => {
    btn.classList.toggle("active", btn.getAttribute("data-proto") === proto);
  });

  updateProtocolFieldsUI(proto);
}

function saveFormIntoSlot(idx) {
  const s = Object.assign(defaultSlot(), state.slots[idx] || {});
  const proto = (s.type || "SOCKS5").toUpperCase();

  s.host = (document.getElementById("inpHost").value || "").trim();
  s.port = parseInt(document.getElementById("inpPort").value || "1080", 10) || 1080;
  s.username = (document.getElementById("inpUser").value || "").trim();
  s.password = (document.getElementById("inpPass").value || "").trim();
  s.sni = (document.getElementById("inpSni").value || "").trim();
  s.ssMethod = (document.getElementById("inpCipher").value || "chacha20-ietf-poly1305").trim();
  s.realityPublicKey = (document.getElementById("inpExtra").value || "").trim();
  s.realityShortId = (document.getElementById("inpShortId").value || "").trim();
  s.flow = (document.getElementById("inpFlow").value || "xtls-rprx-vision").trim();
  s.vmessSecurity = (document.getElementById("inpVmessSec").value || "auto").trim();
  s.alterId = parseInt(document.getElementById("inpAlterId").value || "0", 10) || 0;
  s.transport = (document.getElementById("inpTransport").value || "tcp").trim();
  s.wsPath = (document.getElementById("inpWsPath").value || "").trim();

  if ((proto === "VLESS" || proto === "VMESS") && s.username && !s.password) {
    s.password = s.username;
  }
  state.slots[idx] = s;
}

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
  document.querySelectorAll(".proto-chip").forEach(btn => {
    const isMatch = btn.getAttribute("data-proto") === proto;
    btn.classList.toggle("active", isMatch);
    if (isMatch) triggerBubblePop(btn);
  });
  updateProtocolFieldsUI(proto);
  renderSlotCapsules();
  showGlassPopup(`Protocol: ${proto}`);
  persistAllSlots();
}

function clearCurrentSlot() {
  state.slots[state.activeSlot] = defaultSlot();
  loadSlotIntoForm(state.activeSlot);
  renderSlotCapsules();
  persistAllSlots();
  showGlassPopup(`Slot ${state.activeSlot + 1} Cleared`);
}

function pasteUriFromClipboard() {
  if (!window.NamelessBridge) return;
  try {
    const parsed = JSON.parse(window.NamelessBridge.importClipboardUri() || "{}");
    if (parsed.host) {
      const s = Object.assign(defaultSlot(), state.slots[state.activeSlot]);
      s.type = PROTO_SCHEMAS[parsed.type] ? parsed.type : "SOCKS5";
      s.host = parsed.host || "";
      s.port = parsed.port || 1080;
      s.username = parsed.username || "";
      s.password = parsed.password || "";
      s.sni = parsed.sni || "";
      s.realityPublicKey = parsed.realityPublicKey || "";
      s.realityShortId = parsed.realityShortId || "";
      if (parsed.flow) s.flow = parsed.flow;
      if (parsed.vmessSecurity) s.vmessSecurity = parsed.vmessSecurity;
      if (typeof parsed.alterId === "number") s.alterId = parsed.alterId;
      if (parsed.transport) s.transport = parsed.transport;
      if (parsed.wsPath) s.wsPath = parsed.wsPath;
      state.slots[state.activeSlot] = s;
      loadSlotIntoForm(state.activeSlot);
      renderSlotCapsules();
      persistAllSlots();
      showGlassPopup("Pasted into Slot " + (state.activeSlot + 1));
    } else {
      showGlassPopup("Clipboard does not contain a valid proxy URI");
    }
  } catch (e) {}
}

function toggleTunnel() {
  if (saveDebounceTimer) clearTimeout(saveDebounceTimer);
  saveFormIntoSlot(state.activeSlot);
  triggerBubblePop(document.getElementById("coreBtn"));

  if (state.isRunning || state.transitionTarget === "START") {
    state.isTransitioning = true;
    state.transitionTarget = "STOP";
    state.isRunning = false;
    state.runningSlot = null;
    document.body.dataset.state = "disconnected";
    document.getElementById("coreStateText").textContent = "STOPPING";
    document.getElementById("coreIcon").textContent = "WAIT";
    renderSlotCapsules();
    showGlassPopup("Stopping Tunnel...");
    if (transitionResetTimer) clearTimeout(transitionResetTimer);
    transitionResetTimer = setTimeout(() => {
      state.isTransitioning = false;
      state.transitionTarget = null;
      renderSlotCapsules();
    }, 3000);
    if (window.NamelessBridge) {
      window.NamelessBridge.stopTunnelAsync();
    }
    return;
  }

  if (state.isTransitioning) return;

  const targetUidsStr = (state.routeAllApps || state.selectedUids.length === 0)
    ? "ALL"
    : state.selectedUids.join(",");
  const cfg = Object.assign({}, state.slots[state.activeSlot], {
    slot: state.activeSlot,
    routeHotspot: state.routeHotspot,
    targetUids: targetUidsStr,
    appFilterMode: "WHITELIST",
    webrtcMode: "SPOOF"
  });
  if (!cfg.host || cfg.host.trim() === "") {
    document.getElementById("inpHost").focus();
    showGlassPopup("Enter Server Host & Port");
    return;
  }
  state.isTransitioning = true;
  state.transitionTarget = "START";
  document.getElementById("coreStateText").textContent = "STARTING";
  document.getElementById("coreIcon").textContent = "WAIT";
  showGlassPopup(`Connecting Slot ${state.activeSlot + 1}...`);
  if (transitionResetTimer) clearTimeout(transitionResetTimer);
  transitionResetTimer = setTimeout(() => {
    state.isTransitioning = false;
    state.transitionTarget = null;
    renderSlotCapsules();
  }, 4000);
  if (window.NamelessBridge) {
    window.NamelessBridge.saveSlots(buildSlotsPayload());
    window.NamelessBridge.startTunnelAsync(JSON.stringify(cfg));
  }
}

window.onTunnelActionComplete = function(jsonStr) {
  if (transitionResetTimer) clearTimeout(transitionResetTimer);
  state.isTransitioning = false;
  state.transitionTarget = null;
  try {
    const res = JSON.parse(jsonStr);
    if (res.stopped) {
      state.isRunning = false;
      state.runningSlot = null;
      document.body.dataset.state = "disconnected";
      document.getElementById("coreStateText").textContent = "OFFLINE";
      renderSlotCapsules();
      showGlassPopup("Tunnel Stopped");
      return;
    }
    if (res.ok) {
      showGlassPopup("Tunnel Connected • Shield Active");
    } else if (res.error) {
      showGlassPopup(res.error);
    }
  } catch (e) {}
  pollBackendStatus();
};

function pingCurrentNode() {
  saveFormIntoSlot(state.activeSlot);
  const s = state.slots[state.activeSlot];
  if (!s.host) {
    document.getElementById("inpHost").focus();
    showGlassPopup("Enter a host to ping");
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
  showGlassPopup(ms > 0 ? `Latency: ${ms} ms` : "Ping Timeout");
};

function applyPolledStatus(st, firstInit) {
  if (st.network) {
    document.getElementById("netSummaryText").textContent = st.network;
  }

  const userMatches = (typeof st.userId !== "number" || st.userId === -1 || st.userId === state.myUserId);
  const nowRunning = !!(st.running && userMatches);

  if (state.isTransitioning) {
    if (state.transitionTarget === "STOP" && nowRunning) {
      return;
    }
    if (state.transitionTarget === "START" && !nowRunning) {
      return;
    }
    state.isTransitioning = false;
    state.transitionTarget = null;
  }

  const wasRunning = state.isRunning;
  state.isRunning = nowRunning;
  state.runningSlot = state.isRunning ? (st.slot || 0) : null;

  document.body.dataset.state = state.isRunning ? "connected" : "disconnected";

  const orb = document.getElementById("headerOrb");
  const coreStateText = document.getElementById("coreStateText");
  const coreTimerText = document.getElementById("coreTimerText");

  orb.classList.toggle("online", state.isRunning);

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
      coreStateText.textContent = `CONNECTED • SLOT ${(state.runningSlot || 0) + 1}`;
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
      document.getElementById("exitLocText").textContent = g.countryCode || g.country;
    } else {
      document.getElementById("exitFlag").textContent = "🛡️";
      document.getElementById("exitLocText").textContent = "";
    }
  } else {
    state.sessionStartMs = 0;
    if (!state.isTransitioning) {
      coreStateText.textContent = "OFFLINE";
    }
    coreTimerText.textContent = "00:00:00";
    document.getElementById("rxSpeedText").textContent = "0 B/s";
    document.getElementById("txSpeedText").textContent = "0 B/s";
    document.getElementById("rxTotalText").textContent = "0 B";
    document.getElementById("txTotalText").textContent = "0 B";
    document.getElementById("exitFlag").textContent = "🌐";
    document.getElementById("exitIpText").textContent = "Not Connected";
    document.getElementById("exitLocText").textContent = "";
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

function mountEmbeddedChecker(url) {
  if (url) state.checkerUrl = url;
  if (!window.NamelessBridge || state.activeTab !== 1) return;

  requestAnimationFrame(() => {
    if (state.activeTab !== 1) return;
    const slot = document.getElementById("checkerPlaceholder");
    const vw = document.documentElement.clientWidth || window.innerWidth || 360;
    const vh = document.documentElement.clientHeight || window.innerHeight || 800;
    const rect = slot && slot.getBoundingClientRect().height > 10
      ? slot.getBoundingClientRect()
      : { left: 12, top: 104, right: vw - 12, bottom: vh - 62 };
    const topRatio = Math.max(0.08, Math.min(0.30, rect.top / vh));
    const bottomRatio = Math.max(0.05, Math.min(0.22, (vh - rect.bottom) / vh));
    const sideRatio = Math.max(0.01, Math.min(0.08, rect.left / vw));
    window.NamelessBridge.showCheckerBounds(state.checkerUrl, topRatio, bottomRatio, sideRatio);
  });
}

function switchTab(idx) {
  state.activeTab = idx;
  document.querySelectorAll(".tab-view").forEach((pane, i) => {
    pane.classList.toggle("active", i === idx);
  });
  document.querySelectorAll(".dock-btn").forEach((btn, i) => {
    const active = i === idx;
    btn.classList.toggle("active", active);
    if (active) triggerBubblePop(btn);
  });

  if (window.NamelessBridge) {
    if (idx === 1) {
      mountEmbeddedChecker(state.checkerUrl);
    } else {
      window.NamelessBridge.hideCheckerView();
    }
  }

  if (idx === 3) {
    refreshLogsNow();
  }
}

function loadCheckerPreset(url, btnId) {
  document.querySelectorAll(".chk-tab").forEach(b => b.classList.remove("active"));
  const btn = document.getElementById(btnId);
  if (btn) {
    btn.classList.add("active");
    triggerBubblePop(btn);
  }
  state.checkerUrl = url;
  mountEmbeddedChecker(url);
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
  showGlassPopup("Kernel Logs Cleared");
  setTimeout(refreshLogsNow, 150);
}

function exportAllSlots() {
  saveFormIntoSlot(state.activeSlot);
  const payload = buildSlotsPayload();
  if (window.NamelessBridge) {
    window.NamelessBridge.saveSlots(payload);
    const ok = window.NamelessBridge.exportAllSlots();
    showGlassPopup(ok ? "Exported Backup to Download" : "Export Failed");
  }
}

function importAllSlots() {
  if (!window.NamelessBridge) return;
  const raw = window.NamelessBridge.importAllSlots();
  if (raw) {
    try {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed.slots) && parsed.slots.length === 5) {
        state.slots = parsed.slots.map(s => Object.assign(defaultSlot(), s || {}));
        loadSlotIntoForm(state.activeSlot);
        renderSlotCapsules();
        showGlassPopup("Imported 5 Slots");
        return;
      }
    } catch (e) {}
  }
  showGlassPopup("No Backup File Found");
}

window.addEventListener("DOMContentLoaded", initApp);
