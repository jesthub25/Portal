#!/usr/bin/env python3
"""Integration test helper: downloads Paper + WorldEdit for a Minecraft version.

usage: fetch-server.py <mc_version> <out_dir>
"""
import json
import os
import sys
import urllib.parse
import urllib.request

UA = {"User-Agent": "jestron-portal-ci (github.com/jesthub25/Portal)"}


def get_json(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def download(url, path):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=300) as r, open(path, "wb") as f:
        while True:
            chunk = r.read(1 << 16)
            if not chunk:
                break
            f.write(chunk)
    print(f"downloaded {url} -> {path} ({os.path.getsize(path)} bytes)")


def paper_url(mc):
    # Fill v3 API
    try:
        builds = get_json(f"https://fill.papermc.io/v3/projects/paper/versions/{mc}/builds")
        if isinstance(builds, dict):
            builds = builds.get("builds", [])
        best = None
        for b in builds:
            dl = (b.get("downloads") or {}).get("server:default")
            if dl and dl.get("url"):
                if best is None or b.get("id", 0) > best[0]:
                    best = (b.get("id", 0), dl["url"])
        if best:
            return best[1]
    except Exception as e:
        print("fill v3 failed:", e)
    # Legacy v2 API
    data = get_json(f"https://api.papermc.io/v2/projects/paper/versions/{mc}/builds")
    b = data["builds"][-1]
    name = b["downloads"]["application"]["name"]
    return f"https://api.papermc.io/v2/projects/paper/versions/{mc}/builds/{b['build']}/downloads/{name}"


def worldedit_url(mc):
    q = urllib.parse.urlencode({
        "game_versions": json.dumps([mc]),
        "loaders": json.dumps(["bukkit", "paper", "spigot"]),
    })
    versions = get_json(f"https://api.modrinth.com/v2/project/worldedit/version?{q}")
    for v in versions:
        for f in v.get("files", []):
            if f.get("filename", "").endswith(".jar") and "bukkit" in f["filename"].lower():
                print("WorldEdit", v.get("version_number"))
                return f["url"]
    for v in versions:
        for f in v.get("files", []):
            if f.get("filename", "").endswith(".jar"):
                print("WorldEdit", v.get("version_number"))
                return f["url"]
    raise SystemExit(f"No WorldEdit build on Modrinth for {mc}")


def main():
    mc, out = sys.argv[1], sys.argv[2]
    os.makedirs(os.path.join(out, "plugins"), exist_ok=True)
    download(paper_url(mc), os.path.join(out, "paper.jar"))
    download(worldedit_url(mc), os.path.join(out, "plugins", "WorldEdit.jar"))


if __name__ == "__main__":
    main()
