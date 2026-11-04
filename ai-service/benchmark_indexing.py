"""Compare indexing HTTP endpoints using identical prepared photos, without DB writes."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import csv
import hashlib
import io
import json
import math
from pathlib import Path
import statistics
import subprocess
import threading
import time
from urllib.parse import urlsplit
import uuid

from PIL import Image
import requests


def prepare(row, directory):
    path = directory / f"{row['id']}.jpg"
    if not path.exists():
        url = row["image_url"] + "?w=1024&h=1024&fit=max&fm=jpg"
        with requests.get(url, timeout=60, stream=True) as response:
            response.raise_for_status()
            data = bytearray()
            for chunk in response.iter_content(65536):
                data.extend(chunk)
                if len(data) > 20 * 1024 * 1024:
                    raise ValueError("Image exceeds 20 MiB")
        with Image.open(io.BytesIO(data)) as image:
            if image.width * image.height > 40_000_000:
                raise ValueError("Image exceeds pixel limit")
            image.thumbnail((1024, 1024), Image.Resampling.BICUBIC)
            image.convert("RGB").save(path, "JPEG", quality=90)
    return path


def request_batch(endpoint, paths):
    started = time.perf_counter()
    ids = [str(uuid.uuid5(uuid.NAMESPACE_URL, path.name)) for path in paths]
    files = [("images", (path.name, path.read_bytes(), "image/jpeg")) for path in paths]
    try:
        response = requests.post(endpoint.rstrip("/") + "/api/v1/indexing/batch",
                                 data=[("imageIds", value) for value in ids], files=files, timeout=180)
        response.raise_for_status()
        results = response.json()["results"]
        if sorted(item["imageId"] for item in results) != sorted(ids):
            raise ValueError("Response IDs differ from request")
        errors = []
        for item in results:
            vector = item.get("embedding") or []
            if item["status"] != "SUCCESS" or len(vector) != 768 or not all(math.isfinite(v) for v in vector):
                errors.append({"imageId": item["imageId"], "error": item.get("error") or "Invalid vector"})
        return {"seconds": time.perf_counter() - started, "images": len(paths),
                "success": len(paths) - len(errors), "errors": errors,
                "ocr_regions": sum(len(item.get("ocr", [])) for item in results)}
    except (requests.RequestException, ValueError, KeyError) as error:
        return {"seconds": time.perf_counter() - started, "images": len(paths), "success": 0,
                "errors": [{"error": str(error), "files": [p.name for p in paths]}], "ocr_regions": 0}


def sample_gpu(stop, samples):
    while not stop.is_set():
        try:
            result = subprocess.run(["nvidia-smi", "--query-gpu=memory.used,utilization.gpu",
                                     "--format=csv,noheader,nounits"], capture_output=True, text=True, timeout=5, check=True)
            memory, utilization = [int(value.strip()) for value in result.stdout.splitlines()[0].split(",")]
            samples.append({"memory_mib": memory, "utilization_percent": utilization})
        except (OSError, ValueError, subprocess.SubprocessError):
            pass
        stop.wait(0.5)


def benchmark(label, endpoints, paths, batch_size, gpu_metrics):
    for endpoint in endpoints:
        warmup = request_batch(endpoint, paths[:batch_size])
        if warmup["success"] != len(paths[:batch_size]):
            raise RuntimeError(f"Warmup failed for {endpoint}: {warmup}")
    groups = [paths[start:start + batch_size] for start in range(0, len(paths), batch_size)]
    lanes = [groups[index::len(endpoints)] for index in range(len(endpoints))]

    def lane(endpoint, work):
        results = []
        for group in work:
            result = request_batch(endpoint, group)
            results.append(result)
            print(f"{label} {endpoint} images={len(group)} success={result['success']} seconds={result['seconds']:.2f}", flush=True)
        return results

    started = time.perf_counter()
    samples = []
    stop = threading.Event()
    sampler = threading.Thread(target=sample_gpu, args=(stop, samples), daemon=True)
    if gpu_metrics:
        sampler.start()
    try:
        with ThreadPoolExecutor(max_workers=len(endpoints)) as executor:
            futures = [executor.submit(lane, endpoint, work) for endpoint, work in zip(endpoints, lanes)]
            results = [result for future in futures for result in future.result()]
    finally:
        stop.set()
        if gpu_metrics:
            sampler.join(timeout=6)
    elapsed = time.perf_counter() - started
    summary = {"label": label, "endpoints": endpoints, "images": len(paths), "seconds": elapsed,
               "success": sum(r["success"] for r in results),
               "images_per_minute": sum(r["success"] for r in results) * 60 / elapsed,
               "median_batch_seconds": statistics.median(r["seconds"] for r in results),
               "ocr_regions": sum(r["ocr_regions"] for r in results),
               "valid": all(not r["errors"] for r in results),
               "errors": [error for result in results for error in result["errors"]]}
    if samples:
        summary["gpu_peak_sampled_memory_mib"] = max(sample["memory_mib"] for sample in samples)
        summary["gpu_mean_utilization_percent"] = statistics.mean(sample["utilization_percent"] for sample in samples)
    print(json.dumps(summary), flush=True)
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--csv", type=Path, default=Path(__file__).resolve().parents[1] / "infra/photos.csv")
    parser.add_argument("--directory", type=Path, default=Path(__file__).resolve().parent / "target/gpu-benchmark")
    parser.add_argument("--count", type=int, default=100)
    parser.add_argument("--batch-size", type=int, default=3)
    parser.add_argument("--case", action="append", default=[], help="LABEL=URL[,URL]")
    parser.add_argument("--gpu-metrics", action="store_true", help="Sample host nvidia-smi during processing")
    args = parser.parse_args()
    if args.count < 1 or not 1 <= args.batch_size <= 20:
        parser.error("count must be positive and batch-size must be between 1 and 20")
    images = args.directory / "images"
    images.mkdir(parents=True, exist_ok=True)
    with args.csv.open(newline="", encoding="utf-8") as stream:
        rows = [row for row in csv.DictReader(stream) if urlsplit(row["image_url"]).hostname == "images.unsplash.com"][:args.count]
    with ThreadPoolExecutor(max_workers=4) as executor:
        paths = list(executor.map(lambda row: prepare(row, images), rows))
    manifest = [{"id": row["id"], "url": row["image_url"], "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
                for row, path in zip(rows, paths)]
    (args.directory / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(f"Prepared {len(paths)} identical JPEG inputs", flush=True)
    failed = False
    for case in args.case:
        label, urls = case.split("=", 1)
        summary = benchmark(label, urls.split(","), paths, args.batch_size, args.gpu_metrics)
        (args.directory / f"{label}.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
        failed = failed or not summary["valid"]
    if failed:
        raise SystemExit("Benchmark includes failed requests. Do not use it as a complete throughput comparison.")


if __name__ == "__main__":
    main()
