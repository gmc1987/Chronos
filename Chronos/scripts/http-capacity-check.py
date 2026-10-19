#!/usr/bin/env python3
"""Concurrent authenticated HTTP capacity gate using only the Python stdlib."""

import argparse
import concurrent.futures
import json
import math
import statistics
import time
import urllib.error
import urllib.request


def request(url, token=None, body=None, timeout=30):
    headers = {"Accept": "application/json", "Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    payload = None if body is None else json.dumps(body).encode()
    method = "GET" if payload is None else "POST"
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(
            urllib.request.Request(url, data=payload, headers=headers, method=method), timeout=timeout
        ) as response:
            data = json.loads(response.read())
            ok = 200 <= response.status < 300 and data.get("code") in ("200", "201")
            return ok, (time.perf_counter() - started) * 1000, response.status
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
        return False, (time.perf_counter() - started) * 1000, type(error).__name__


def login(url, username, password):
    request_value = urllib.request.Request(
        url,
        data=json.dumps({"username": username, "password": password}).encode(),
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request_value, timeout=30) as response:
            body = json.loads(response.read())
            if not 200 <= response.status < 300 or body.get("code") not in ("200", "201"):
                raise SystemExit(f"FAIL: login failed: HTTP {response.status}, code={body.get('code')}")
            return body["data"]["accessToken"]
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, KeyError) as error:
        raise SystemExit(f"FAIL: login failed: {error}") from error


def percentile(values, percent):
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, max(0, math.ceil(len(ordered) * percent) - 1))]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--username", required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument("--requests", type=int, default=200)
    parser.add_argument("--concurrency", type=int, default=20)
    parser.add_argument("--p95-ms", type=float, default=1500)
    parser.add_argument("--max-error-rate", type=float, default=0.01)
    arguments = parser.parse_args()
    base = arguments.base_url.rstrip("/")
    token = login(base + "/auth/login", arguments.username, arguments.password)
    with concurrent.futures.ThreadPoolExecutor(max_workers=arguments.concurrency) as pool:
        results = list(pool.map(lambda _: request(base + "/portal/bootstrap", token), range(arguments.requests)))
    timings = [result[1] for result in results]
    errors = sum(not result[0] for result in results)
    error_rate = errors / len(results)
    p95 = percentile(timings, 0.95)
    print(json.dumps({
        "requests": len(results), "concurrency": arguments.concurrency,
        "errors": errors, "errorRate": round(error_rate, 4),
        "p50Ms": round(statistics.median(timings), 2), "p95Ms": round(p95, 2),
        "p99Ms": round(percentile(timings, 0.99), 2),
    }, ensure_ascii=False))
    if error_rate > arguments.max_error_rate or p95 > arguments.p95_ms:
        raise SystemExit("FAIL: capacity threshold exceeded")
    print("PASS: HTTP capacity threshold satisfied")


if __name__ == "__main__":
    main()
