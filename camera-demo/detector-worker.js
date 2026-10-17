/* Local, pinned vendor files are prepared separately; this worker never uses a CDN. */
"use strict";

let detector;
let busy = false;

self.onmessage = async ({ data }) => {
  if (data.type === "init") {
    try {
      importScripts("vendor/tf.min.js", "vendor/coco-ssd.min.js");
      tf.enableProdMode();
      try {
        if (!await tf.setBackend("webgl")) throw new Error("WebGL unavailable");
      } catch {
        await tf.setBackend("cpu");
      }
      await tf.ready();
      detector = await cocoSsd.load({
        base: "lite_mobilenet_v2",
        modelUrl: new URL("models/ssdlite_mobilenet_v2/model.json", self.location.href).href
      });
      self.postMessage({ type: "ready", backend: tf.getBackend() });
    } catch (error) {
      self.postMessage({ type: "error", phase: "init", message: String(error.message || error) });
    }
    return;
  }
  if (data.type !== "detect" || !detector || busy) return;
  busy = true;
  try {
    const started = performance.now();
    const image = new ImageData(new Uint8ClampedArray(data.pixels), data.width, data.height);
    const predictions = await detector.detect(image, 100, data.confidence);
    const people = predictions.filter(p => p.class === "person" && Number.isFinite(p.score)
      && p.bbox.every(Number.isFinite) && p.bbox[2] > 0 && p.bbox[3] > 0)
      .map(p => ({ bbox: [p.bbox[0] / data.width, p.bbox[1] / data.height,
        p.bbox[2] / data.width, p.bbox[3] / data.height], score: p.score }));
    self.postMessage({ type: "detections", session: data.session, people, elapsed: performance.now() - started });
  } catch (error) {
    self.postMessage({ type: "error", phase: "detect", message: String(error.message || error) });
  } finally {
    busy = false;
  }
};
