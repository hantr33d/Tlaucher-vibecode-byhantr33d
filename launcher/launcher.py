"""
ClientMod Launcher - one button: PLAY.
Launches Minecraft Forge 1.16.5 (already installed via TLauncher / official launcher)
with ClientMod loaded. Offline nickname. No Microsoft login.
"""
import glob
import json
import os
import shutil
import subprocess
import sys
import threading
import uuid
import zipfile
import tkinter as tk
from tkinter import filedialog, messagebox

FROZEN = getattr(sys, "frozen", False)
APP = os.path.dirname(sys.executable if FROZEN else os.path.abspath(__file__))
BASE = os.environ.get("APPDATA") or os.path.expanduser("~")
MC = os.path.join(BASE, ".minecraft")
HOME = os.path.join(BASE, ".clientmod")
CFG = os.path.join(HOME, "launcher.json")
OS = "windows" if sys.platform.startswith("win") else ("osx" if sys.platform == "darwin" else "linux")
SEP = ";" if OS == "windows" else ":"

BG, PANEL, ACCENT, TEXT = "#14101f", "#1e1830", "#9b3dff", "#ffffff"


# ---------------------------------------------------------------- version json
def read_json(p):
    with open(p, "r", encoding="utf-8") as f:
        return json.load(f)


def find_forge(mc):
    vdir = os.path.join(mc, "versions")
    found = []
    if os.path.isdir(vdir):
        for d in os.listdir(vdir):
            if "1.16.5" in d and "forge" in d.lower() and os.path.isfile(os.path.join(vdir, d, d + ".json")):
                found.append(d)
    found.sort(key=lambda s: ("optifine" in s.lower(), s))
    return found[0] if found else None


def load_version(mc, vid):
    v = read_json(os.path.join(mc, "versions", vid, vid + ".json"))
    parent_id = v.get("inheritsFrom")
    if not parent_id:
        v["_base"] = vid
        if "arguments" not in v and "minecraftArguments" in v:
            v["arguments"] = {"game": v["minecraftArguments"].split(), "jvm": []}
        return v
    p = load_version(mc, parent_id)
    m = dict(p)
    for k, val in v.items():
        if k not in ("libraries", "arguments", "inheritsFrom"):
            m[k] = val
    m["libraries"] = v.get("libraries", []) + p.get("libraries", [])
    pa, va = p.get("arguments", {}), v.get("arguments", {})
    if not va and "minecraftArguments" in v:
        va = {"game": v["minecraftArguments"].split(), "jvm": []}
    m["arguments"] = {"game": pa.get("game", []) + va.get("game", []),
                      "jvm": pa.get("jvm", []) + va.get("jvm", [])}
    m["_base"] = p.get("_base", parent_id)
    return m


def allowed(rules):
    if not rules:
        return True
    ok = False
    for r in rules:
        match = True
        if "os" in r and "name" in r["os"] and r["os"]["name"] != OS:
            match = False
        if "features" in r:
            match = False
        if match:
            ok = r.get("action") == "allow"
    return ok


def lib_rel_path(lib):
    art = lib.get("downloads", {}).get("artifact")
    if art and art.get("path"):
        return art["path"]
    parts = lib["name"].split(":")
    group, name, ver = parts[0], parts[1], parts[2]
    cls = parts[3] if len(parts) > 3 else None
    return "%s/%s/%s/%s-%s%s.jar" % (group.replace(".", "/"), name, ver, name, ver, "-" + cls if cls else "")


def extract_natives(jar, dest):
    try:
        with zipfile.ZipFile(jar) as z:
            for n in z.namelist():
                if n.startswith("META-INF") or n.endswith("/"):
                    continue
                if n.lower().endswith((".dll", ".so", ".dylib", ".jnilib")):
                    with z.open(n) as src, open(os.path.join(dest, os.path.basename(n)), "wb") as out:
                        shutil.copyfileobj(src, out)
    except Exception:
        pass


def flatten_args(items, subs):
    out = []
    for it in items:
        if isinstance(it, str):
            vals = [it]
        else:
            if not allowed(it.get("rules")):
                continue
            val = it.get("value", [])
            vals = [val] if isinstance(val, str) else list(val)
        for s in vals:
            for k, v in subs.items():
                s = s.replace("${" + k + "}", v)
            if "${" in s:
                s = ""  # unknown placeholder -> drop
            if s != "":
                out.append(s)
    return out


# ---------------------------------------------------------------- java
def find_java(mc):
    cands = []
    for pat in (os.path.join(mc, "runtime", "**", "bin", "javaw.exe"),
                os.path.join(mc, "runtime", "**", "bin", "java")):
        cands += glob.glob(pat, recursive=True)
    for c in cands:
        low = c.lower()
        if "legacy" in low or "jre8" in low or "1.8" in low:
            return c
    for base in (r"C:\Program Files\Java", r"C:\Program Files (x86)\Java",
                 r"C:\Program Files\Eclipse Adoptium", r"C:\Program Files\Zulu"):
        for c in glob.glob(os.path.join(base, "*", "bin", "javaw.exe")):
            if "8" in os.path.basename(os.path.dirname(os.path.dirname(c))):
                return c
    if cands:
        return cands[0]
    return shutil.which("javaw") or shutil.which("java") or ""


# ---------------------------------------------------------------- launch
def build_command(cfg, log):
    mc = cfg["mc_dir"]
    vid = find_forge(mc)
    if not vid:
        raise RuntimeError("Forge 1.16.5 not found in %s\\versions.\nInstall it in TLauncher first "
                           "(choose 'Forge 1.16.5' and start the game once)." % mc)
    log("Forge version: " + vid)
    v = load_version(mc, vid)
    base = v["_base"]

    libs_dir = os.path.join(mc, "libraries")
    natives = os.path.join(HOME, "natives")
    shutil.rmtree(natives, ignore_errors=True)
    os.makedirs(natives, exist_ok=True)

    cp, seen, missing = [], set(), 0
    for lib in v["libraries"]:
        if not allowed(lib.get("rules")):
            continue
        parts = lib["name"].split(":")
        key = ":".join([parts[0], parts[1]] + parts[3:4])
        if key in seen:
            continue
        seen.add(key)
        rel = lib_rel_path(lib)
        full = os.path.join(libs_dir, rel.replace("/", os.sep))
        if os.path.isfile(full):
            cp.append(full)
            if "natives-" + OS in os.path.basename(full):
                extract_natives(full, natives)
        else:
            missing += 1
        nat = lib.get("natives")
        if nat and OS in nat:
            cls = nat[OS].replace("${arch}", "64")
            cinfo = lib.get("downloads", {}).get("classifiers", {}).get(cls)
            if cinfo and cinfo.get("path"):
                nf = os.path.join(libs_dir, cinfo["path"].replace("/", os.sep))
                if os.path.isfile(nf):
                    extract_natives(nf, natives)
    if missing:
        log("Warning: %d libraries missing (start this Forge once in TLauncher so it downloads them)." % missing)

    client = os.path.join(mc, "versions", base, base + ".jar")
    if os.path.isfile(client):
        cp.append(client)
    else:
        log("Warning: client jar not found: " + client)

    game_dir = mc if cfg.get("use_mc") else os.path.join(HOME, "game")
    os.makedirs(os.path.join(game_dir, "mods"), exist_ok=True)

    # copy the mod next to the launcher into mods/
    jars = glob.glob(os.path.join(APP, "clientmod*.jar"))
    if jars:
        for old in glob.glob(os.path.join(game_dir, "mods", "clientmod*.jar")):
            try:
                os.remove(old)
            except OSError:
                pass
        shutil.copy(jars[0], os.path.join(game_dir, "mods"))
        log("Mod installed: " + os.path.basename(jars[0]))
    else:
        log("No clientmod*.jar next to the launcher - starting without the mod.")

    name = cfg["name"] or "Player"
    player_uuid = str(uuid.uuid3(uuid.NAMESPACE_OID, "OfflinePlayer:" + name)).replace("-", "")
    asset_id = v.get("assetIndex", {}).get("id") or v.get("assets", "legacy")
    subs = {
        "auth_player_name": name, "version_name": vid, "game_directory": game_dir,
        "assets_root": os.path.join(mc, "assets"), "assets_index_name": asset_id,
        "auth_uuid": player_uuid, "auth_access_token": "0", "user_type": "legacy",
        "version_type": "release", "natives_directory": natives, "launcher_name": "ClientMod",
        "launcher_version": "1.0", "classpath": SEP.join(cp), "library_directory": libs_dir,
        "classpath_separator": SEP, "user_properties": "{}", "clientid": "", "auth_xuid": "",
        "game_assets": os.path.join(mc, "assets", "virtual", "legacy"),
    }
    jvm = flatten_args(v["arguments"].get("jvm", []), subs)
    if not any(a == "-cp" or a == "-classpath" for a in jvm):
        jvm += ["-Djava.library.path=" + natives, "-cp", SEP.join(cp)]
    game = flatten_args(v["arguments"].get("game", []), subs)

    java = cfg.get("java") or find_java(mc)
    if not java:
        raise RuntimeError("Java not found. Click 'Browse' and choose javaw.exe (Java 8).")
    log("Java: " + java)
    cmd = [java, "-Xms1G", "-Xmx%dG" % int(cfg["ram"])] + jvm + [v["mainClass"]] + game
    return cmd, game_dir


def run_game(cfg, log, done):
    try:
        cmd, cwd = build_command(cfg, log)
        flags = 0x08000000 if OS == "windows" else 0  # CREATE_NO_WINDOW
        log("Starting Minecraft...")
        proc = subprocess.Popen(cmd, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                creationflags=flags)
        for raw in proc.stdout:
            log(raw.decode("utf-8", "replace").rstrip())
        log("Game closed (exit code %s)." % proc.wait())
    except Exception as e:
        log("ERROR: %s" % e)
    finally:
        done()


# ---------------------------------------------------------------- UI
def load_cfg():
    d = {"name": "Player", "ram": 4, "java": "", "mc_dir": MC, "use_mc": False}
    try:
        d.update(read_json(CFG))
    except Exception:
        pass
    return d


def save_cfg(d):
    os.makedirs(HOME, exist_ok=True)
    with open(CFG, "w", encoding="utf-8") as f:
        json.dump(d, f, indent=2)


def main():
    cfg = load_cfg()
    root = tk.Tk()
    root.title("ClientMod Launcher")
    root.geometry("620x520")
    root.configure(bg=BG)
    root.resizable(False, False)

    tk.Label(root, text="ClientMod", font=("Segoe UI", 26, "bold"), fg=ACCENT, bg=BG).pack(pady=(14, 0))
    tk.Label(root, text="Forge 1.16.5", font=("Segoe UI", 10), fg="#aaaaaa", bg=BG).pack()

    box = tk.Frame(root, bg=PANEL)
    box.pack(fill="x", padx=18, pady=12)

    def row(r, label):
        tk.Label(box, text=label, fg=TEXT, bg=PANEL, font=("Segoe UI", 10)).grid(row=r, column=0, sticky="w", padx=10, pady=6)

    name_v, ram_v = tk.StringVar(value=cfg["name"]), tk.StringVar(value=str(cfg["ram"]))
    java_v, use_v = tk.StringVar(value=cfg["java"]), tk.BooleanVar(value=cfg["use_mc"])

    row(0, "Nickname")
    tk.Entry(box, textvariable=name_v, width=28, bg="#2a2244", fg=TEXT, insertbackground=TEXT, relief="flat").grid(row=0, column=1, sticky="w")
    row(1, "RAM (GB)")
    tk.Spinbox(box, from_=1, to=32, textvariable=ram_v, width=6, bg="#2a2244", fg=TEXT, relief="flat").grid(row=1, column=1, sticky="w")
    row(2, "Java (auto)")
    tk.Entry(box, textvariable=java_v, width=36, bg="#2a2244", fg=TEXT, insertbackground=TEXT, relief="flat").grid(row=2, column=1, sticky="w")

    def browse():
        p = filedialog.askopenfilename(title="Select javaw.exe / java")
        if p:
            java_v.set(p)

    tk.Button(box, text="Browse", command=browse, bg="#2a2244", fg=TEXT, relief="flat").grid(row=2, column=2, padx=6)
    tk.Checkbutton(box, text="Use my .minecraft folder (saves, options)", variable=use_v, fg=TEXT, bg=PANEL,
                   selectcolor="#2a2244", activebackground=PANEL, activeforeground=TEXT).grid(row=3, column=0, columnspan=3, sticky="w", padx=8, pady=6)

    logbox = tk.Text(root, height=11, bg="#0d0a16", fg="#cfc3ff", relief="flat", font=("Consolas", 9))
    logbox.pack(fill="both", padx=18, pady=(0, 10), expand=True)

    def log(s):
        def w():
            logbox.insert("end", s + "\n")
            logbox.see("end")
        root.after(0, w)

    play = tk.Button(root, text="PLAY", font=("Segoe UI", 18, "bold"), bg=ACCENT, fg=TEXT,
                     activebackground="#b469ff", relief="flat", height=1)
    play.pack(fill="x", padx=18, pady=(0, 16))

    def done():
        root.after(0, lambda: play.config(state="normal", text="PLAY"))

    def on_play():
        try:
            c = {"name": name_v.get().strip() or "Player", "ram": max(1, int(ram_v.get())),
                 "java": java_v.get().strip(), "mc_dir": cfg["mc_dir"], "use_mc": use_v.get()}
        except ValueError:
            messagebox.showerror("ClientMod", "RAM must be a number")
            return
        save_cfg(c)
        play.config(state="disabled", text="Starting...")
        threading.Thread(target=run_game, args=(c, log, done), daemon=True).start()

    play.config(command=on_play)
    log("Ready. Press PLAY.")
    if not find_forge(cfg["mc_dir"]):
        log("Forge 1.16.5 not found yet - install it in TLauncher first (see INSTALL).")
    root.mainloop()


if __name__ == "__main__":
    main()
