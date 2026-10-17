#!/usr/bin/env python3
"""Explicit, one-time asset download. No packages are installed and no server is started."""
import hashlib
import json
from pathlib import Path, PurePosixPath
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parent
MODEL_ROOT = "https://storage.googleapis.com/tfjs-models/savedmodel/ssdlite_mobilenet_v2/"
FILES = {
    "vendor/tf.min.js": "https://cdn.jsdelivr.net/npm/@tensorflow/tfjs@4.22.0/dist/tf.min.js",
    "vendor/coco-ssd.min.js": "https://cdn.jsdelivr.net/npm/@tensorflow-models/coco-ssd@2.2.3/dist/coco-ssd.min.js",
    "vendor/TFJS-LICENSE": "https://raw.githubusercontent.com/tensorflow/tfjs/tfjs-v4.22.0/LICENSE",
    "vendor/COCO-SSD-LICENSE": "https://raw.githubusercontent.com/tensorflow/tfjs-models/master/LICENSE",
}
ALLOWED_HOSTS = {"cdn.jsdelivr.net", "storage.googleapis.com", "raw.githubusercontent.com"}


def download(url, relative_path, manifest):
    if urllib.parse.urlparse(url).hostname not in ALLOWED_HOSTS:
        raise ValueError("Unexpected download host")
    target = ROOT / relative_path
    print(f"Downloading {relative_path}", flush=True)
    with urllib.request.urlopen(url, timeout=60) as response:
        if urllib.parse.urlparse(response.geturl()).hostname not in ALLOWED_HOSTS:
            raise ValueError("Unexpected redirect host")
        content = response.read(64 * 1024 * 1024 + 1)
    if len(content) > 64 * 1024 * 1024:
        raise ValueError("Asset exceeds the 64 MiB limit")
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_name(target.name + ".part")
    temporary.write_bytes(content)
    temporary.replace(target)
    manifest[relative_path] = {"url": url, "bytes": len(content),
                               "sha256": hashlib.sha256(content).hexdigest()}
    return content


def main():
    manifest = {}
    for path, url in FILES.items():
        download(url, path, manifest)
    model_path = "models/ssdlite_mobilenet_v2/model.json"
    model = json.loads(download(MODEL_ROOT + "model.json", model_path, manifest))
    shards = set()
    for group in model["weightsManifest"]:
        for path in group["paths"]:
            parsed = urllib.parse.urlparse(path)
            posix = PurePosixPath(path)
            if parsed.scheme or parsed.netloc or parsed.query or parsed.fragment or posix.is_absolute() \
                    or ".." in posix.parts or "\\" in path:
                raise ValueError("Unexpected model weight path")
            shards.add(path)
    if not shards:
        raise ValueError("The model has no weight files")
    for shard in sorted(shards):
        download(urllib.parse.urljoin(MODEL_ROOT, shard),
                 "models/ssdlite_mobilenet_v2/" + shard, manifest)
    (ROOT / "assets-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print("Assets prepared. Inference uses these local files; no CDN is used by the demo page.")


if __name__ == "__main__":
    main()
