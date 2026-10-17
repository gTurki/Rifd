"use strict";

const $ = id => document.getElementById(id);
const words = {
  en: {
    title: "Live camera demo", empty: "Start the camera to detect people", people: "People", live: "LIVE",
    backend: "Rifd server", cameraId: "Camera ID", zone: "Zone", selectZone: "Load zones first",
    threshold: "Alert at this count", loadZones: "Load zones", advanced: "Detection settings",
    hold: "Hold above threshold (seconds)", cooldown: "Cooldown (seconds)", confidence: "Detection confidence",
    start: "Start camera", stop: "Stop camera", retry: "Retry screenshot upload", lastAlert: "Latest camera event",
    explanation: "People are detected on this device. A sustained threshold crossing sends one screenshot and a CROWD alert to Rifd. Keep this page visible beside the dashboard.",
    setup: "Setup guide", policy: "Demo count rule · No server-side image AI", idle: "Ready to set up",
    loading: "Preparing local detector…", opening: "Waiting for camera permission…", detecting: "Detecting people locally",
    paused: "Detection paused while this page is hidden", stopped: "Camera stopped", armed: "Ready to send an alert",
    holdProgress: "Confirming threshold…", waitingReset: "Waiting for count to drop before re-arming",
    cooldownProgress: "Cooldown: {seconds}s", uploading: "Uploading screenshot…", sent: "Alert #{alert} sent · Incident #{incident}",
    failed: "Upload not confirmed: {message}. Retry uses the same event ID.", zonesLoaded: "Zones loaded. Select a zone and start the camera.",
    noZones: "No zones found. Prepare Rifd's demo data first.", missingModel: "Could not prepare the detector. Check the local assets using the setup guide.",
    cameraDenied: "Camera permission was denied. Allow camera access, then try again.", noCamera: "No camera was found.",
    cameraBusy: "The camera could not start. Close other apps using it and try again.", detectorFailed: "Detection stopped. Restart the camera to try again.",
    secureContext: "Open this page on localhost or HTTPS to use the camera.", invalidBackend: "Enter an HTTP or HTTPS server URL without a path.",
    missingZone: "Load and select an existing zone first.", requestFailed: "Request failed. Check the server address, connection and allowed origins.",
    requestTimeout: "The server response timed out; it may already have received this event.", disabled: "The camera demo endpoint is unavailable. Enable camera-demo.enabled on Rifd.",
    screenshotFailed: "Could not capture the screenshot.", stale: "Waiting for a fresh count…", updates: "{fps} updates/s · {ms} ms detection",
    snapshotAlt: "Original screenshot attached to the camera alert", modelTimeout: "Local detector initialization timed out.",
    retryPending: "Retry the pending screenshot before sending another event."
  },
  ar: {
    title: "عرض الكاميرا الحية", empty: "شغّل الكاميرا لبدء اكتشاف الأشخاص", people: "الأشخاص", live: "مباشر",
    backend: "خادم رفد", cameraId: "معرّف الكاميرا", zone: "المنطقة", selectZone: "حمّل المناطق أولًا",
    threshold: "إرسال تنبيه عند هذا العدد", loadZones: "تحميل المناطق", advanced: "إعدادات الكشف",
    hold: "استمرار تجاوز الحد (ثوانٍ)", cooldown: "الفاصل بين التنبيهات (ثوانٍ)", confidence: "ثقة كشف الشخص",
    start: "تشغيل الكاميرا", stop: "إيقاف الكاميرا", retry: "إعادة إرسال اللقطة", lastAlert: "آخر حدث للكاميرا",
    explanation: "يعمل كشف الأشخاص على هذا الجهاز. عند استمرار بلوغ الحد، يرسل العرض لقطة واحدة وتنبيه CROWD إلى رفد. أبقِ هذه الصفحة ظاهرة بجانب لوحة العمليات.",
    setup: "دليل الإعداد", policy: "قاعدة عدد تجريبية · دون تحليل صور على الخادم", idle: "جاهز للإعداد",
    loading: "تجهيز نموذج الكشف المحلي…", opening: "بانتظار إذن الكاميرا…", detecting: "كشف الأشخاص محليًا",
    paused: "توقف الكشف مؤقتًا لأن الصفحة مخفية", stopped: "توقفت الكاميرا", armed: "جاهز لإرسال تنبيه",
    holdProgress: "التحقق من استمرار بلوغ الحد…", waitingReset: "بانتظار انخفاض العدد لإعادة التفعيل",
    cooldownProgress: "الفاصل المتبقي: {seconds} ث", uploading: "إرسال اللقطة…", sent: "أُرسل البلاغ #{alert} · الحادث #{incident}",
    failed: "لم يتأكد الإرسال: {message}. تستخدم المحاولة مجددًا معرّف الحدث نفسه.", zonesLoaded: "حُمّلت المناطق. اختر منطقة ثم شغّل الكاميرا.",
    noZones: "لا توجد مناطق. جهّز بيانات عرض رفد أولًا.", missingModel: "تعذر تجهيز نموذج الكشف. راجع الملفات المحلية في دليل الإعداد.",
    cameraDenied: "لم تمنح إذن الكاميرا. اسمح بالوصول ثم حاول مجددًا.", noCamera: "لم يُعثر على كاميرا.",
    cameraBusy: "تعذر تشغيل الكاميرا. أغلق التطبيقات الأخرى التي تستخدمها ثم حاول مجددًا.", detectorFailed: "توقف الكشف. أعد تشغيل الكاميرا للمحاولة مجددًا.",
    secureContext: "افتح الصفحة على localhost أو HTTPS لاستخدام الكاميرا.", invalidBackend: "أدخل عنوان خادم HTTP أو HTTPS دون مسار إضافي.",
    missingZone: "حمّل منطقة موجودة واخترها أولًا.", requestFailed: "تعذر الطلب. تحقق من عنوان الخادم والاتصال والسماح بمصدر الصفحة.",
    requestTimeout: "انتهت مهلة الرد؛ قد يكون الخادم قد استلم الحدث بالفعل.", disabled: "مسار الكاميرا غير متاح. فعّل camera-demo.enabled في رفد.",
    screenshotFailed: "تعذر التقاط الصورة.", stale: "بانتظار عدّ حديث…", updates: "{fps} تحديث/ث · {ms} مللي ثانية للكشف",
    snapshotAlt: "اللقطة الأصلية المرفقة بتنبيه الكاميرا", modelTimeout: "انتهت مهلة تجهيز النموذج المحلي.",
    retryPending: "أعد إرسال اللقطة المعلقة قبل إرسال حدث آخر."
  }
};
let language = "en";
const t = (key, params = {}) => Object.entries(params).reduce((s, [name, value]) => s.replaceAll(`{${name}}`, value), words[language][key] || key);
let status = { key: "idle", params: {}, tone: "" };
let result = null;
function setStatus(key, tone = "", params = {}) {
  status = { key, tone, params };
  $("status").textContent = t(key, params);
  $("status").dataset.tone = tone;
}
function renderResult() {
  if (!result) return;
  $("alertResult").textContent = t(result.key, result.params);
  $("alertResult").dataset.tone = result.tone || "";
}
function translate() {
  document.documentElement.lang = language;
  document.documentElement.dir = language === "ar" ? "rtl" : "ltr";
  document.querySelectorAll("[data-i18n]").forEach(el => { el.textContent = t(el.dataset.i18n); });
  $("language").textContent = language === "en" ? "العربية" : "English";
  $("language").lang = language === "en" ? "ar" : "en";
  $("snapshot").alt = t("snapshotAlt");
  setStatus(status.key, status.tone, status.params);
  renderResult();
}
$("language").addEventListener("click", () => { language = language === "en" ? "ar" : "en"; translate(); });

const frame = document.createElement("canvas");
const frameContext = frame.getContext("2d", { willReadFrequently: true });
const overlay = $("overlay");
const overlayContext = overlay.getContext("2d");
let worker = null;
let modelPromise = null;
let rejectModel = null;
let stream = null;
let running = false;
let session = 0;
let animation = null;
let watchdog = null;
let inFlight = false;
let settings = null;
let people = [];
let count = 0;
let lastResultAt = 0;
let lastCaptureAt = 0;
let aboveSince = null;
let belowSince = null;
let armed = true;
let nextAllowedAt = 0;
let pending = null;
let uploading = false;
let snapshotUrl = null;
let loadingZones = false;

function backendUrl() {
  let url;
  try { url = new URL($("backend").value.trim()); } catch { throw new Error(t("invalidBackend")); }
  if (!["http:", "https:"].includes(url.protocol) || url.username || url.password
      || !["", "/"].includes(url.pathname) || url.search || url.hash) throw new Error(t("invalidBackend"));
  return url.origin;
}

async function request(url, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 20000);
  try {
    const response = await fetch(url, { ...options, signal: controller.signal, credentials: "omit" });
    const text = await response.text();
    let body;
    try { body = JSON.parse(text); } catch { body = null; }
    if (!response.ok) {
      if (response.status === 404 || body?.message?.startsWith("Endpoint not found:")) throw new Error(t("disabled"));
      throw new Error(body?.message || `HTTP ${response.status}`);
    }
    return body;
  } catch (error) {
    if (error.name === "AbortError") throw new Error(t("requestTimeout"));
    if (error instanceof TypeError) throw new Error(t("requestFailed"));
    throw error;
  } finally { clearTimeout(timeout); }
}

async function loadZones() {
  if (running || loadingZones) return;
  loadingZones = true;
  $("loadZones").disabled = true;
  try {
    const backend = backendUrl();
    const zones = await request(`${backend}/api/v1/zones`);
    if (!Array.isArray(zones) || zones.length === 0) throw new Error(t("noZones"));
    const selected = $("zone").value;
    $("zone").replaceChildren(...zones.filter(z => typeof z.code === "string").map(z => {
      const option = document.createElement("option");
      option.value = z.code;
      option.textContent = `${z.code} · ${z.name || z.code}`;
      return option;
    }));
    if ([...$("zone").options].some(o => o.value === selected)) $("zone").value = selected;
    $("zone").dataset.backend = backend;
    setStatus("zonesLoaded", "success");
  } catch (error) { setStatus(error.message, "error"); }
  finally { loadingZones = false; $("loadZones").disabled = running; }
}
$("loadZones").addEventListener("click", loadZones);
$("backend").addEventListener("input", () => { $("zone").replaceChildren(new Option(t("selectZone"), "")); delete $("zone").dataset.backend; });

function lockSettings(locked) {
  $("settings").querySelectorAll("input, select, button").forEach(el => { el.disabled = locked; });
  $("start").disabled = locked;
  $("stop").disabled = !locked;
}

function ensureModel() {
  if (modelPromise) return modelPromise;
  modelPromise = new Promise((resolve, reject) => {
    rejectModel = reject;
    worker = new Worker("detector-worker.js");
    const timeout = setTimeout(() => {
      worker?.terminate(); worker = null; modelPromise = null;
      reject(new Error(t("modelTimeout")));
    }, 90000);
    worker.onmessage = ({ data }) => {
      if (data.type === "ready") { clearTimeout(timeout); rejectModel = null; resolve(); }
      else if (data.type === "detections") receivedDetections(data);
      else if (data.type === "error") {
        console.error("Local detector:", data.message);
        clearTimeout(timeout);
        if (data.phase === "init") {
          worker?.terminate(); worker = null; modelPromise = null; rejectModel = null;
          reject(new Error(t("missingModel")));
        } else failDetection();
      }
    };
    worker.onerror = event => {
      console.error("Local detector worker:", event.message);
      clearTimeout(timeout);
      if (rejectModel) { rejectModel(new Error(t("missingModel"))); rejectModel = null; }
      failDetection();
    };
    worker.postMessage({ type: "init" });
  });
  return modelPromise;
}

async function start() {
  if (!$("settings").reportValidity() || running) return;
  let backend;
  try {
    backend = backendUrl();
    if (!$("zone").value || $("zone").dataset.backend !== backend) throw new Error(t("missingZone"));
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) throw new Error(t("secureContext"));
  } catch (error) { setStatus(error.message, "error"); return; }
  const current = ++session;
  settings = { backend, cameraId: $("cameraId").value, zoneCode: $("zone").value,
    threshold: Number($("threshold").value), holdMs: Number($("hold").value) * 1000,
    cooldownMs: Number($("cooldown").value) * 1000, confidence: Number($("confidence").value) };
  lockSettings(true);
  setStatus("loading");
  try {
    await ensureModel();
    if (current !== session) return;
    setStatus("opening");
    const camera = await navigator.mediaDevices.getUserMedia({ audio: false,
      video: { facingMode: "user", width: { ideal: 1280 }, height: { ideal: 720 }, frameRate: { ideal: 30 } } });
    if (current !== session) { camera.getTracks().forEach(track => track.stop()); return; }
    stream = camera;
    $("video").srcObject = stream;
    await $("video").play();
    if (current !== session) return;
    stream.getVideoTracks()[0].addEventListener("ended", () => { if (running) { stop(); setStatus("cameraBusy", "error"); } });
    const width = $("video").videoWidth;
    const height = $("video").videoHeight;
    overlay.width = width; overlay.height = height;
    $("video").parentElement.style.aspectRatio = `${width} / ${height}`;
    frame.width = Math.min(640, width);
    frame.height = Math.max(1, Math.round(height * frame.width / width));
    running = true;
    lastResultAt = 0; aboveSince = null; belowSince = null;
    $("empty").hidden = true; $("live").hidden = false;
    setStatus(document.hidden ? "paused" : "detecting", "success");
    animation = requestAnimationFrame(tick);
  } catch (error) {
    if (current !== session) return;
    if (!worker) modelPromise = null;
    stop();
    const key = { NotAllowedError: "cameraDenied", NotFoundError: "noCamera", NotReadableError: "cameraBusy" }[error.name];
    setStatus(key || error.message, "error");
  }
}

function stop() {
  ++session; running = false;
  cancelAnimationFrame(animation);
  stream?.getTracks().forEach(track => track.stop()); stream = null;
  $("video").srcObject = null;
  overlayContext.clearRect(0, 0, overlay.width, overlay.height);
  $("count").textContent = "—"; $("live").hidden = true; $("empty").hidden = false;
  $("video").parentElement.classList.remove("crowded");
  $("performance").textContent = ""; $("trigger").textContent = "";
  aboveSince = null; belowSince = null; people = [];
  lockSettings(false); setStatus("stopped");
}

function failDetection() {
  clearTimeout(watchdog);
  inFlight = false;
  worker?.terminate(); worker = null; modelPromise = null;
  stop(); setStatus("detectorFailed", "error");
}

function tick(now) {
  if (!running) return;
  const fresh = lastResultAt && now - lastResultAt < 2000;
  overlayContext.clearRect(0, 0, overlay.width, overlay.height);
  if (fresh) {
    overlayContext.strokeStyle = count >= settings.threshold ? "#ffb669" : "#8affb2";
    overlayContext.lineWidth = Math.max(2, overlay.width / 400);
    for (const person of people) {
      const [x, y, w, h] = person.bbox;
      overlayContext.strokeRect(x * overlay.width, y * overlay.height, w * overlay.width, h * overlay.height);
    }
    $("count").textContent = count;
  } else { $("count").textContent = "—"; aboveSince = null; }
  $("video").parentElement.classList.toggle("crowded", Boolean(fresh && count >= settings.threshold));
  if (!document.hidden && !inFlight && now - lastCaptureAt >= 66 && $("video").readyState >= 2 && worker) {
    frameContext.drawImage($("video"), 0, 0, frame.width, frame.height);
    const pixels = frameContext.getImageData(0, 0, frame.width, frame.height);
    inFlight = true; lastCaptureAt = now;
    watchdog = setTimeout(failDetection, 15000);
    worker.postMessage({ type: "detect", session, pixels: pixels.data.buffer, width: frame.width,
      height: frame.height, confidence: settings.confidence }, [pixels.data.buffer]);
  }
  animation = requestAnimationFrame(tick);
}

function receivedDetections(data) {
  clearTimeout(watchdog);
  inFlight = false;
  if (!running || document.hidden || data.session !== session) return;
  const now = performance.now();
  if (now - lastResultAt > 2000) { aboveSince = null; belowSince = null; }
  const interval = now - lastResultAt;
  people = data.people; count = people.length; lastResultAt = now;
  $("performance").textContent = t("updates", { fps: (1000 / Math.max(interval, 1)).toFixed(1), ms: Math.round(data.elapsed) });
  setStatus("detecting", "success");
  if (count < settings.threshold) {
    aboveSince = null;
    belowSince ??= now;
    if (now - belowSince >= 2000 && now >= nextAllowedAt && !pending) armed = true;
  } else {
    belowSince = null;
    aboveSince ??= now;
    if (armed && !pending && now >= nextAllowedAt && now - aboveSince >= settings.holdMs) {
      // Copy the actual analyzed frame before the next inference overwrites the canvas.
      const snapshot = document.createElement("canvas");
      snapshot.width = frame.width; snapshot.height = frame.height;
      snapshot.getContext("2d").drawImage(frame, 0, 0);
      armed = false; nextAllowedAt = now + settings.cooldownMs;
      pending = { ...settings, eventId: crypto.randomUUID(), peopleCount: count, blob: null };
      const event = pending;
      snapshot.toBlob(blob => {
        if (!blob) { pending = null; setStatus("screenshotFailed", "error"); return; }
        event.blob = blob;
        if (snapshotUrl) URL.revokeObjectURL(snapshotUrl);
        snapshotUrl = URL.createObjectURL(blob);
        $("snapshot").src = snapshotUrl; $("lastAlert").hidden = false;
        void submitPending();
      }, "image/jpeg", 0.88);
    }
  }
  const triggerKey = pending ? (uploading ? "uploading" : "retryPending")
    : now < nextAllowedAt ? "cooldownProgress" : !armed ? "waitingReset"
      : aboveSince !== null ? "holdProgress" : "armed";
  $("trigger").textContent = t(triggerKey, { seconds: Math.max(0, Math.ceil((nextAllowedAt - now) / 1000)) });
}

async function submitPending() {
  if (!pending?.blob || uploading) return;
  const event = pending;
  uploading = true; $("retry").hidden = true;
  result = { key: "uploading", params: {} }; renderResult();
  const body = new FormData();
  for (const field of ["cameraId", "zoneCode", "peopleCount", "threshold", "eventId"]) body.append(field, String(event[field]));
  body.append("photo", event.blob, "camera-frame.jpg");
  try {
    const alert = await request(`${event.backend}/api/v1/demo/camera-alerts`, { method: "POST", body });
    if (!alert?.id || alert.aiCategory !== "CROWD" || alert.source !== "CCTV") throw new Error(t("requestFailed"));
    result = { key: "sent", tone: "success", params: { alert: alert.id, incident: alert.incidentId || "—" } };
    pending = null;
  } catch (error) {
    result = { key: "failed", tone: "error", params: { message: error.message } };
    $("retry").hidden = false;
  } finally { uploading = false; renderResult(); }
}

$("start").addEventListener("click", start);
$("stop").addEventListener("click", stop);
$("retry").addEventListener("click", submitPending);
$("settings").addEventListener("submit", event => { event.preventDefault(); void start(); });
document.addEventListener("visibilitychange", () => {
  aboveSince = null; belowSince = null;
  if (running) setStatus(document.hidden ? "paused" : "detecting");
});
window.addEventListener("pagehide", () => { stop(); worker?.terminate(); if (snapshotUrl) URL.revokeObjectURL(snapshotUrl); });
translate();
