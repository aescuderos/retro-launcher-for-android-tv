#!/usr/bin/env python3
"""Build library.json for Retro Launcher by scanning a ROM folder.

Usage: make_library.py <roms_dir_on_device> <roms_dir_on_pc> <thumbs_dir_on_pc> <out.json>

Layout expected (same on PC and device):  <roms>/<folder>/<rom files>
Thumbnails:  <thumbs>/<libretro playlist name>/Named_Boxarts/<rom name without extension>.png
Edit SYSTEMS to match your setup.
"""
import json, os, re, sys

CORES = "/data/user/0/com.retroarch/cores/%s_libretro_android.so"
# folder, display name, core, thumbnail folder (libretro-thumbnails naming), extensions
SYSTEMS = [
    ("nes", "NES", "fceumm", "Nintendo - Nintendo Entertainment System", (".nes", ".zip")),
    ("snes", "Super Nintendo", "snes9x", "Nintendo - Super Nintendo Entertainment System", (".sfc", ".smc", ".zip")),
    ("gb", "Game Boy", "gambatte", "Nintendo - Game Boy", (".gb", ".zip")),
    ("gbc", "Game Boy Color", "gambatte", "Nintendo - Game Boy Color", (".gbc", ".zip")),
    ("gba", "Game Boy Advance", "mgba", "Nintendo - Game Boy Advance", (".gba", ".zip")),
    ("mastersystem", "Master System", "genesis_plus_gx", "Sega - Master System - Mark III", (".sms", ".zip")),
    ("megadrive", "Mega Drive", "genesis_plus_gx", "Sega - Mega Drive - Genesis", (".md", ".bin", ".gen", ".zip")),
    ("gamegear", "Game Gear", "genesis_plus_gx", "Sega - Game Gear", (".gg", ".zip")),
    ("neogeo", "Neo Geo", "fbneo", "SNK - Neo Geo", (".zip",)),
    ("mame", "Arcade (MAME)", "fbneo", "MAME", (".zip",)),
]

def title(stem):
    t = re.sub(r"\s*[\(\[].*?[\)\]]", "", stem).strip()
    return t or stem

def main(dev_roms, pc_roms, pc_thumbs, out):
    systems = []
    for folder, name, core, thumb_dir, exts in SYSTEMS:
        d = os.path.join(pc_roms, folder)
        if not os.path.isdir(d):
            continue
        games = []
        for f in sorted(os.listdir(d), key=str.lower):
            stem, ext = os.path.splitext(f)
            if ext.lower() not in exts:
                continue
            rel = "%s/Named_Boxarts/%s.png" % (thumb_dir, stem)
            if not os.path.exists(os.path.join(pc_thumbs, rel)):
                rel = ""
            games.append([title(stem), "%s/%s/%s" % (dev_roms.rstrip("/"), folder, f), rel])
        if games:
            systems.append({"name": name, "core": CORES % core, "games": games})
    with open(out, "w", encoding="utf-8") as fh:
        json.dump({"systems": systems}, fh, ensure_ascii=False)
    print("%d systems, %d games" % (len(systems), sum(len(s["games"]) for s in systems)))

if __name__ == "__main__":
    if len(sys.argv) != 5:
        sys.exit(__doc__)
    main(*sys.argv[1:])
