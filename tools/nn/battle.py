"""The night battle: the app's small network (48 x 6, 2048 simulations) against the big one (64 x 10,
512 simulations), on the PC and on every phone connected with adb, then training of both on its games.

    .venv/Scripts/python -u battle.py                # or double-click battle.bat
    .venv/Scripts/python battle.py --status
    .venv/Scripts/python battle.py --stop            # or double-click battle-stop.bat

Phases (each one skipped once done: re-running resumes where it stopped), all in runs/battle/:
1. finetune: the small network (night512 gen 46, the app's) trained on the big network's 512-simulation
   self-play games (runs/big/data gen_0000..0334, gen_0335-0336 held out) -> small_ft/small_ft.{pt,bin}.
2. battle, until --until (default 05:00, fixed at the first launch): `selfplay battle` A = small_ft at
   2048 simulations, B = the big network (runs/big gen 336) at 512, each pair of games on one opening of
   the self-play schedule (Openings.kt: same 10 000-game distribution, its own seed), played twice
   (A first player, then B). PC: 14 threads, pairs 0+. Each phone: 1 thread (like the app), pairs
   from k * 1 000 000, the selfplay tool as a dex run by app_process in /data/local/tmp/tetrano_battle
   (phones plugged in later are started too). After the deadline no new pair starts; those under way
   finish. Results: pc/ and phone_<serial>/ (pairs.jsonl, same.bin, diff.bin).
3. analysis: report.md (scores, Elo, per opening group, search times per device and network).
4. train: each network trained on the positions of the pairs whose two games had the same result
   (same.bin, 5 % held out) -> after/small.{pt,bin}, after/big.{pt,bin}; nothing is shipped.
"""
import argparse
import datetime as dt
import json
import math
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

import numpy as np
import torch

import data
import train
import winproc
from model import load_checkpoint

HERE = Path(__file__).resolve().parent
SELFPLAY_LIB = HERE.parents[1] / "selfplay" / "build" / "install" / "selfplay" / "lib"
RUN = Path("runs/battle")
SMALL_CKPT = Path("runs/night512/checkpoints/gen_0046.pt")
BIG_CKPT = Path("runs/big/checkpoints/gen_0336.pt")
BIG_BIN = Path("runs/big/models/gen_0336.bin")
BIG_DATA = Path("runs/big/data")
BIG_TEST = ["gen_0335.bin", "gen_0336.bin"]
DEX = Path("runs/battle/phone/classes.dex")
PHONE_DIR = "/data/local/tmp/tetrano_battle"

SIMS_A, SIMS_B = 2048, 512
PC_THREADS = 14
SCHEDULE_SEED = 20261020
PHONE_OFFSET = 1_000_000
FT = {"steps": 6000, "batch": 1024, "warmup": 200, "lr_peak": 1e-4, "lr_final": 1e-5, "weight_decay": 1e-4, "seed": 11}
AFTER = {"epochs": 2.0, "batch": 1024, "test_share": 0.05, "lr_small": 3e-5, "lr_big": 2e-5, "seed": 12}
PULL_EVERY_S = 600
PHONE_GRACE_S = 3600
PHONE_MARGIN_MIN = 20  # no phone is started this close to the deadline


def log(msg):
    line = f"[{dt.datetime.now():%Y-%m-%d %H:%M:%S}] {msg}"
    print(line, flush=True)
    with open(RUN / "log.txt", "a", encoding="utf-8") as f:
        f.write(line + "\n")


def write_atomic(path, text):
    tmp = path.with_name(path.name + ".tmp")
    with open(tmp, "w", encoding="utf-8", newline="") as f:
        f.write(text)
    os.replace(tmp, path)


def save_atomic(obj, path):
    tmp = path.with_name(path.name + ".tmp")
    torch.save(obj, tmp)
    os.replace(tmp, path)


def load_state():
    path = RUN / "state.json"
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}


def save_state(st):
    write_atomic(RUN / "state.json", json.dumps(st, indent=1))


def parse_until(text):
    m = re.fullmatch(r"(\d{1,2})[:h](\d{2})", text.strip())
    if m:
        now = dt.datetime.now()
        t = now.replace(hour=int(m.group(1)), minute=int(m.group(2)), second=0, microsecond=0)
        return t if t > now else t + dt.timedelta(days=1)
    return dt.datetime.fromisoformat(text)


# ---------------------------------------------------------------- 1. fine-tuning of the small network

def lr_at(step):
    if step < FT["warmup"]:
        return FT["lr_peak"] * (step + 1) / FT["warmup"]
    x = (step - FT["warmup"]) / max(1, FT["steps"] - FT["warmup"])
    return FT["lr_final"] + 0.5 * (FT["lr_peak"] - FT["lr_final"]) * (1 + math.cos(math.pi * min(1.0, x)))


def big_files():
    files = [f for f in data.files_in(BIG_DATA) if not f.name.startswith("gen_-")]
    test = [f for f in files if f.name in BIG_TEST]
    return [f for f in files if f.name not in BIG_TEST], test


def finetune(st):
    out = RUN / "small_ft"
    out.mkdir(parents=True, exist_ok=True)
    if (out / "small_ft.pt").exists():
        return True
    torch.set_num_threads(os.cpu_count() or 4)
    train_files, test_files = big_files()
    t = time.time()
    train_set = np.concatenate([data.load(f) for f in train_files])
    test_set = np.concatenate([data.load(f) for f in test_files])
    log(f"fine-tuning: train {data.summary(train_set)} ({len(train_files)} files), test {len(test_set)} "
        f"positions (loaded in {time.time() - t:.0f} s)")
    model, ckpt, _ = load_checkpoint(SMALL_CKPT)
    optimizer = train.make_optimizer(model, FT["lr_peak"], FT["weight_decay"])
    optimizer.load_state_dict(ckpt["optimizer"])
    rng = np.random.default_rng(FT["seed"])
    step = 0
    state_path = out / "state.pt"
    if state_path.exists():
        saved = torch.load(state_path, map_location="cpu", weights_only=False)
        model.load_state_dict(saved["model"])
        optimizer.load_state_dict(saved["optimizer"])
        rng.bit_generator.state = saved["numpy_rng"]
        torch.set_rng_state(saved["torch_rng"])
        step = saved["step"]
    if "ft_before" not in st:
        st["ft_before"] = [float(x) for x in train.evaluate(model, test_set)]
        save_state(st)
        log(f"small network before: test value {st['ft_before'][0]:.4f} policy {st['ft_before'][1]:.4f} "
            f"accuracy {st['ft_before'][3]:.4f}")
    model.train()
    running, count, started = np.zeros(3), 0, time.time()
    first = step
    while step < FT["steps"]:
        for group in optimizer.param_groups:
            group["lr"] = lr_at(step)
        batch = train.batch_tensors(train_set[rng.integers(0, len(train_set), FT["batch"])])
        v, p, m = train.losses(model, batch)
        loss = v + p + train.MOVES_LEFT_WEIGHT * m
        optimizer.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 5.0)
        optimizer.step()
        running += (v.item(), p.item(), m.item())
        count += 1
        step += 1
        if step % 500 == 0 or step == FT["steps"]:
            save_atomic({"model": model.state_dict(), "optimizer": optimizer.state_dict(), "step": step,
                         "numpy_rng": rng.bit_generator.state, "torch_rng": torch.get_rng_state()}, state_path)
            r = running / count
            per = (time.time() - started) / (step - first)
            log(f"fine-tuning step {step}/{FT['steps']} lr {lr_at(step - 1):.2e} train value {r[0]:.4f} "
                f"policy {r[1]:.4f} ({per:.2f} s/step, {per * (FT['steps'] - step) / 60:.0f} min left)")
            running, count = np.zeros(3), 0
            model.train()
    model.eval()
    st["ft_after"] = [float(x) for x in train.evaluate(model, test_set)]
    model.export_weights(out / "small_ft.bin")
    save_atomic({"hidden": model.hidden, "layers": model.layers, "model": model.state_dict(),
                 "optimizer": optimizer.state_dict()}, out / "small_ft.pt")
    save_state(st)
    log(f"small network after fine-tuning: test value {st['ft_after'][0]:.4f} policy {st['ft_after'][1]:.4f} "
        f"accuracy {st['ft_after'][3]:.4f} -> {out / 'small_ft.bin'}")
    return True


# ---------------------------------------------------------------- 2. battle

def adb(serial, *args, timeout=120):
    cmd = ["adb"] + (["-s", serial] if serial else []) + list(args)
    try:
        return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, errors="replace")
    except subprocess.TimeoutExpired:
        return subprocess.CompletedProcess(cmd, -1, "", "timeout")


def adb_shell(serial, command, timeout=120):
    return adb(serial, "shell", command, timeout=timeout)


def connected_phones():
    res = adb(None, "devices", "-l", timeout=30)
    phones = {}
    for line in res.stdout.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            model = next((p.split(":", 1)[1] for p in parts if p.startswith("model:")), parts[0])
            phones[parts[0]] = model
    return phones


def battle_args(a, b, out, label, threads, offset, until_ms):
    return ["battle", f"a={a}", f"b={b}", f"sims_a={SIMS_A}", f"sims_b={SIMS_B}", f"out={out}", f"label={label}",
            f"threads={threads}", f"offset={offset}", f"schedule_seed={SCHEDULE_SEED}", f"until={until_ms}"]


def phone_run_dir(st):
    return f"{PHONE_DIR}/{st['run_id']}"


def phone_alive(serial, st):
    d = phone_run_dir(st)
    res = adb_shell(serial, f"cd {d} 2>/dev/null && if [ -f out/done.txt ]; then echo done; "
                            f"elif kill -0 $(cat pid 2>/dev/null) 2>/dev/null; then echo alive; else echo dead; fi", 30)
    word = res.stdout.strip().split()[-1:] if res.returncode == 0 else []
    return word[0] if word else "unreachable"


def start_phone(serial, model, st):
    phones = st.setdefault("phones", {})
    info = phones.get(serial)
    if info is None:
        info = {"model": model, "offset": PHONE_OFFSET * (len(phones) + 1)}
        phones[serial] = info
    d = phone_run_dir(st)
    for args in (["shell", f"mkdir -p {d}/out"], ["push", str(DEX), f"{PHONE_DIR}/battle.dex"],
                 ["push", str(RUN / "small_ft" / "small_ft.bin"), f"{d}/small.bin"], ["push", str(BIG_BIN), f"{d}/big.bin"]):
        res = adb(serial, *args, timeout=300)
        if res.returncode != 0:
            log(f"phone {serial}: adb {args[0]} failed: {res.stderr.strip()}")
            return False
    java = " ".join(battle_args(f"{d}/small.bin", f"{d}/big.bin", f"{d}/out", f"{model}", 1, info["offset"], st["until_ms"]))
    command = (f"cd {d} && rm -f out/stop && setsid nohup sh -c 'echo $$ > pid; CLASSPATH={PHONE_DIR}/battle.dex "
               f"exec app_process / fr.thibautperrin.tetrano.selfplay.MainKt {java}' >> log.txt 2>&1 < /dev/null &")
    adb_shell(serial, command, 30)
    time.sleep(5)
    state = phone_alive(serial, st)
    info["starts"] = info.get("starts", 0) + 1
    save_state(st)
    log(f"phone {serial} ({model}): battle started, pairs from {info['offset']} ({state})")
    return state == "alive"


def pull_phone(serial, st):
    local = RUN / f"phone_{serial}"
    local.mkdir(parents=True, exist_ok=True)
    d = phone_run_dir(st)
    ok = True
    for name in ("pairs.jsonl", "same.bin", "diff.bin", "done.txt"):
        tmp = local / (name + ".part")
        res = adb(serial, "pull", f"{d}/out/{name}", str(tmp), timeout=600)
        if res.returncode == 0 and tmp.exists():
            os.replace(tmp, local / name)
        elif name != "done.txt":
            ok = False
    adb(serial, "pull", f"{d}/log.txt", str(local / "log.txt"), timeout=120)
    return ok


def count_pairs(path):
    if not path.exists():
        return 0
    with open(path, "rb") as f:
        return f.read().count(b"\n")


def last_summary(log_path):
    if not log_path.exists():
        return ""
    lines = [l for l in log_path.read_text(encoding="utf-8", errors="replace").splitlines() if l.startswith(("PAIR", "RESULT"))]
    return lines[-1].split("|", 1)[-1].strip() if lines else ""


def battle(st):
    if not DEX.exists():
        sys.exit(f"missing {DEX}: d8 the selfplay tool (see README)")
    until = dt.datetime.fromtimestamp(st["until_ms"] / 1000)
    pc_out = RUN / "pc"
    pc_out.mkdir(parents=True, exist_ok=True)
    pc_log = open(pc_out / "log.txt", "a", encoding="utf-8")
    proc = None
    if not (pc_out / "done.txt").exists() or time.time() * 1000 < st["until_ms"]:
        cmd = ["java", "-Xmx6g", "-cp", str(SELFPLAY_LIB / "*"), "fr.thibautperrin.tetrano.selfplay.MainKt"] + \
            battle_args(RUN / "small_ft" / "small_ft.bin", BIG_BIN, pc_out, "pc", PC_THREADS, 0, st["until_ms"])
        proc = subprocess.Popen(cmd, stdout=pc_log, stderr=subprocess.STDOUT)
        winproc.kill_with_parent(proc)
        log(f"PC: battle started ({PC_THREADS} threads), until {until:%Y-%m-%d %H:%M}")
    last_pull, stop_sent = 0.0, False
    finish_wait_start = None
    while True:
        now = time.time()
        before_deadline = now * 1000 < st["until_ms"]
        phones = connected_phones()
        for serial, model in phones.items():
            info = st.get("phones", {}).get(serial)
            if info and info.get("finished"):
                continue
            state = phone_alive(serial, st) if info else "new"
            if state in ("new", "dead") and before_deadline and now * 1000 < st["until_ms"] - PHONE_MARGIN_MIN * 60_000:
                if state == "dead":
                    log(f"phone {serial}: battle not running, (re)starting it")
                start_phone(serial, model, st)
            elif state == "done" or (state == "dead" and not before_deadline):
                pull_phone(serial, st)
                info["finished"] = True
                save_state(st)
                log(f"phone {serial} ({info['model']}): finished, {count_pairs(RUN / f'phone_{serial}' / 'pairs.jsonl')} pairs")
        if not before_deadline and not stop_sent:
            (pc_out / "stop").touch()
            for serial in st.get("phones", {}):
                if serial in phones:
                    adb_shell(serial, f"touch {phone_run_dir(st)}/out/stop", 30)
            stop_sent = True
            finish_wait_start = now
            log("deadline reached: no new pair; waiting for the pairs under way")
        if now - last_pull > PULL_EVERY_S:
            last_pull = now
            parts = [f"PC {count_pairs(pc_out / 'pairs.jsonl')} pairs ({last_summary(pc_out / 'log.txt')})"]
            for serial, info in st.get("phones", {}).items():
                if serial in phones and not info.get("finished"):
                    pull_phone(serial, st)
                local = RUN / f"phone_{serial}"
                parts.append(f"{info['model']} {count_pairs(local / 'pairs.jsonl')} pairs ({last_summary(local / 'log.txt')})")
            log("progress: " + " | ".join(parts))
        pc_done = proc is None or proc.poll() is not None
        phones_done = all(i.get("finished") for i in st.get("phones", {}).values())
        if not before_deadline and pc_done:
            grace_over = finish_wait_start is not None and now - finish_wait_start > PHONE_GRACE_S
            if phones_done or grace_over:
                for serial, info in st.get("phones", {}).items():
                    if not info.get("finished"):
                        if serial in phones:
                            pull_phone(serial, st)
                        log(f"phone {serial} ({info['model']}): not finished after the grace period, last pull kept")
                        info["finished"] = True
                break
        elif pc_done and proc is not None and before_deadline:
            log(f"PC battle exited early (code {proc.returncode}); restarting it in 60 s")
            time.sleep(60)
            return battle(st)
        time.sleep(30)
    pc_log.close()
    if proc is not None and proc.returncode not in (0, None):
        log(f"PC battle exited with code {proc.returncode}")
    save_state(st)
    return True


# ---------------------------------------------------------------- 3. analysis

GROUPS = ["EMPTY", "BAD_FIRST", "TWO", "THREE"] + [f"R{n}" for n in range(4, 11)]
GROUP_NAMES = {"EMPTY": "plateau vierge", "BAD_FIRST": "1 mauvais coup", "TWO": "2 coups (35 classes)",
               "THREE": "3 coups (757 classes)", **{f"R{n}": f"{n} coups aléatoires" for n in range(4, 11)}}


def devices():
    out = [("PC (14 parties en parallèle)", RUN / "pc")]
    st = load_state()
    for serial, info in st.get("phones", {}).items():
        out.append((f"{info['model']} (1 partie à la fois)", RUN / f"phone_{serial}"))
    return out


def read_pairs(path):
    pairs = []
    if (path / "pairs.jsonl").exists():
        for line in (path / "pairs.jsonl").read_text(encoding="utf-8").splitlines():
            if line.strip():
                pairs.append(json.loads(line))
    return pairs


def group_of(p):
    return f"R{p['plies']}" if p["kind"] == "RANDOM" else p["kind"]


def a_points(leg):
    if leg["result"] == -1:
        return 0.5
    return 1.0 if (leg["result"] == 0) == leg["aFirst"] else 0.0


def elo(score):
    score = min(max(score, 1e-4), 1 - 1e-4)
    return 400 * math.log10(score / (1 - score))


def score_line(pairs):
    """(games, A score, Elo, 95 % half-width in Elo, A wins, draws, B wins, same-result pairs)."""
    if not pairs:
        return None
    per_pair = np.array([sum(a_points(l) for l in p["legs"]) / 2 for p in pairs])
    s = per_pair.mean()
    se = per_pair.std(ddof=1) / math.sqrt(len(per_pair)) if len(per_pair) > 1 else 0.5
    lo, hi = elo(max(1e-4, s - 1.96 * se)), elo(min(1 - 1e-4, s + 1.96 * se))
    legs = [l for p in pairs for l in p["legs"]]
    aw = sum(1 for l in legs if a_points(l) == 1.0)
    dr = sum(1 for l in legs if l["result"] == -1)
    same = sum(1 for p in pairs if p["legs"][0]["result"] == p["legs"][1]["result"])
    return len(legs), s, elo(s), (hi - lo) / 2 if se > 0 else None, aw, dr, len(legs) - aw - dr, same


def fmt_elo(e, ci):
    return f"{e:+.0f} ± {ci:.0f}" if ci is not None else f"{e:+.0f} (toutes les paires 1-1)"


def timings(pairs):
    """Per network ('A', 'B'): ms of every search, ms of the real searches (simulations > 0), simulations."""
    out = {k: {"all": [], "searched": [], "sims": [], "per_game": []} for k in "AB"}
    for p in pairs:
        start = 0 if p["kind"] == "EMPTY" else p["plies"]
        for leg in p["legs"]:
            game = {"A": 0.0, "B": 0.0}
            for i, (ms, sims) in enumerate(zip(leg["ms"], leg["sims"])):
                first_moves = (start + i) % 2 == 0
                who = "A" if first_moves == leg["aFirst"] else "B"
                out[who]["all"].append(ms)
                game[who] += ms
                if sims > 0:
                    out[who]["searched"].append(ms)
                    out[who]["sims"].append(sims)
            for who in "AB":
                out[who]["per_game"].append(game[who])
    return out


def analyse(st):
    rows = []
    rows.append("# Battle : petit réseau (2048 simulations) contre gros réseau (512 simulations)\n")
    rows.append(f"- A = petit réseau 48 × 6 (gen 46 de l'app, pré-entraîné sur les parties du gros réseau), {SIMS_A} simulations")
    rows.append(f"- B = gros réseau 64 × 10 (runs/big gen 336), {SIMS_B} simulations")
    rows.append("- Recherche comme le niveau 101 de l'app (bruit de Gumbel tant qu'il y a moins de 4 anneaux), "
                "chaque ouverture jouée deux fois (A premier joueur, puis B).")
    if "ft_before" in st and "ft_after" in st:
        b, a = st["ft_before"], st["ft_after"]
        rows.append(f"- Pré-entraînement du petit réseau ({FT['steps']} pas) sur les parties du gros réseau ; sur ses 2 dernières "
                    f"générations (non vues) : valeur {b[0]:.4f} → {a[0]:.4f}, politique {b[1]:.4f} → {a[1]:.4f}, "
                    f"précision {100 * b[3]:.1f} → {100 * a[3]:.1f} %")
    rows.append("")
    all_pairs = []
    per_device = []
    for name, path in devices():
        pairs = read_pairs(path)
        per_device.append((name, pairs))
        all_pairs += pairs
    rows.append("## Score de A (petit réseau)\n")
    rows.append("| Appareil | Parties | Score A | Elo A − B (IC 95 %) | A gagne | Nulles | B gagne | Paires de même issue |")
    rows.append("|---|---|---|---|---|---|---|---|")
    for name, pairs in per_device + [("**Total**", all_pairs)]:
        r = score_line(pairs)
        if r:
            g, s, e, ci, aw, dr, bw, same = r
            rows.append(f"| {name} | {g} | {100 * s:.1f} % | {fmt_elo(e, ci)} | {aw} | {dr} | {bw} | {same}/{g // 2} ({100 * same / (g // 2):.0f} %) |")
    rows.append("\n## Par type d'ouverture (tous appareils)\n")
    rows.append("| Ouverture | Parties | Score A | Elo A − B | Nulles | Même issue | Le premier joueur gagne |")
    rows.append("|---|---|---|---|---|---|---|")
    for gname in GROUPS:
        for disputed in ((None,) if gname in ("EMPTY", "BAD_FIRST", "TWO", "THREE") else (True, False)):
            sel = [p for p in all_pairs if group_of(p) == gname and (disputed is None or p["disputed"] == disputed)]
            r = score_line(sel)
            if not r:
                continue
            g, s, e, ci, aw, dr, bw, same = r
            first = sum(1 for p in sel for l in p["legs"] if l["result"] == 0)
            label = GROUP_NAMES[gname] + ("" if disputed is None else (" disputées" if disputed else " libres"))
            rows.append(f"| {label} | {g} | {100 * s:.1f} % | {fmt_elo(e, ci)} | {dr} | {100 * same / (g // 2):.0f} % | {100 * first / g:.0f} % |")
    rows.append("\n## Temps de réflexion par coup\n")
    rows.append("Moyenne sur tous les coups ; « recherches » : coups où la recherche a vraiment tourné (hors positions "
                "déjà résolues par la tactique, rendues en quelques ms).\n")
    rows.append("| Appareil | Réseau | Coups | Moyenne (tous) | Moyenne (recherches) | Médiane (recherches) | 90e centile | Max | ms / simulation | Par partie |")
    rows.append("|---|---|---|---|---|---|---|---|---|---|")
    summary = {"devices": {}}
    for name, pairs in per_device:
        if not pairs:
            continue
        t = timings(pairs)
        summary["devices"][name] = {}
        for who, label in (("A", f"petit, {SIMS_A} sim."), ("B", f"gros, {SIMS_B} sim.")):
            x = t[who]
            allv, srch = np.array(x["all"]), np.array(x["searched"])
            if len(srch) == 0:
                continue
            per_sim = srch.sum() / max(1, sum(x["sims"]))
            rows.append(f"| {name} | {label} | {len(allv)} | {allv.mean():.0f} ms | {srch.mean():.0f} ms | {np.median(srch):.0f} ms | "
                        f"{np.percentile(srch, 90):.0f} ms | {srch.max() / 1000:.1f} s | {per_sim:.2f} | {np.mean(x['per_game']) / 1000:.1f} s |")
            summary["devices"][name][who] = {"moves": len(allv), "mean_all_ms": float(allv.mean()),
                                             "mean_searched_ms": float(srch.mean()), "median_searched_ms": float(np.median(srch))}
    total = score_line(all_pairs)
    if total:
        summary["total"] = {"games": total[0], "score_a": total[1], "elo_a_minus_b": total[2], "ci": total[3], "same_pairs": total[7]}
    write_atomic(RUN / "report.md", "\n".join(rows) + "\n")
    write_atomic(RUN / "summary.json", json.dumps(summary, indent=1))
    log(f"analysis -> {RUN / 'report.md'}" + (f": A {100 * total[1]:.1f} % ({fmt_elo(total[2], total[3])} Elo) over "
                                              f"{total[0]} games, {total[7]} same-result pairs" if total else ""))
    return True


# ---------------------------------------------------------------- 4. training on the battle games

def battle_positions():
    trains, tests = [], []
    for _, path in devices():
        f = path / "same.bin"
        if f.exists() and f.stat().st_size > 0:
            arr = data.load(f)
            cut = int(len(arr) * (1 - AFTER["test_share"]))
            trains.append(arr[:cut])
            tests.append(arr[cut:])
    if not trains:
        return None, None
    return np.concatenate(trains), np.concatenate(tests)


def train_one(name, ckpt_path, lr, train_set, test_sets, out):
    model, ckpt, _ = load_checkpoint(ckpt_path)
    optimizer = train.make_optimizer(model, lr, AFTER.get("weight_decay", 1e-4))
    if "optimizer" in ckpt:
        optimizer.load_state_dict(ckpt["optimizer"])
    for group in optimizer.param_groups:
        group["lr"] = lr
    before = {k: [float(x) for x in train.evaluate(model, v)] for k, v in test_sets.items()}
    steps = max(1, int(math.ceil(AFTER["epochs"] * len(train_set) / AFTER["batch"])))
    t = time.time()
    res = train.train(model, optimizer, train_set, steps, AFTER["batch"], np.random.default_rng(AFTER["seed"]), log_every=200)
    after = {k: [float(x) for x in train.evaluate(model, v)] for k, v in test_sets.items()}
    model.export_weights(out / f"{name}.bin")
    save_atomic({"hidden": model.hidden, "layers": model.layers, "model": model.state_dict(),
                 "optimizer": optimizer.state_dict()}, out / f"{name}.pt")
    log(f"{name}: {steps} steps at lr {lr:.1e} in {time.time() - t:.0f} s, train value {res[0]:.4f} policy {res[1]:.4f}; "
        + "; ".join(f"{k}: value {before[k][0]:.4f} -> {after[k][0]:.4f}, policy {before[k][1]:.4f} -> {after[k][1]:.4f}"
                    for k in test_sets))
    return {"steps": steps, "lr": lr, "before": before, "after": after}


def train_after(st):
    out = RUN / "after"
    out.mkdir(parents=True, exist_ok=True)
    torch.set_num_threads(os.cpu_count() or 4)
    train_set, test_set = battle_positions()
    if train_set is None:
        log("no same-result positions: nothing to train on")
        return True
    log(f"training on the battle: {data.summary(train_set)}, {len(test_set)} held out")
    _, big_test = big_files()
    tests = {"battle (held out)": test_set, "big self-play gen 335-336": np.concatenate([data.load(f) for f in big_test])}
    results = st.setdefault("after", {})
    for name, ckpt, lr in (("small", RUN / "small_ft" / "small_ft.pt", AFTER["lr_small"]), ("big", BIG_CKPT, AFTER["lr_big"])):
        if name not in results:
            results[name] = train_one(name, ckpt, lr, train_set, tests, out)
            save_state(st)
    rows = ["\n## Entraînement sur les parties de la battle\n",
            f"Positions des paires dont les deux parties ont eu la même issue : {len(train_set)} pour l'entraînement, "
            f"{len(test_set)} gardées pour le test ; {AFTER['epochs']:g} passages. Résultat : `runs/battle/after/` "
            "(rien n'est mis dans l'app ni dans l'entraînement du gros réseau).\n",
            "| Réseau | Pas | lr | Test | Valeur avant → après | Politique avant → après | Précision avant → après |",
            "|---|---|---|---|---|---|---|"]
    for name, r in results.items():
        for k in r["before"]:
            b, a = r["before"][k], r["after"][k]
            rows.append(f"| {name} | {r['steps']} | {r['lr']:.0e} | {k} | {b[0]:.4f} → {a[0]:.4f} | {b[1]:.4f} → {a[1]:.4f} | "
                        f"{100 * b[3]:.1f} → {100 * a[3]:.1f} % |")
    with open(RUN / "report.md", "a", encoding="utf-8") as f:
        f.write("\n".join(rows) + "\n")
    return True


# ---------------------------------------------------------------- driver

def read_pid():
    path = RUN / "battle.pid"
    if not path.exists():
        return None, {}
    info = json.loads(path.read_text())
    alive = winproc.creation_time(info["pid"])
    return (info["pid"] if alive is not None and alive == info.get("created") else None), info


def status():
    pid, info = read_pid()
    st = load_state()
    print(f"Tetrano battle ({RUN}): " + (f"RUNNING (PID {pid}, since {info.get('started')})" if pid else "not running"))
    if "until_ms" in st:
        print(f"   stop of new pairs at {dt.datetime.fromtimestamp(st['until_ms'] / 1000):%Y-%m-%d %H:%M}")
    print("   phases done: " + (", ".join(p for p in ("finetune", "battle", "analysis", "train") if st.get(p)) or "none"))
    print(f"   PC: {count_pairs(RUN / 'pc' / 'pairs.jsonl')} pairs {last_summary(RUN / 'pc' / 'log.txt')}")
    for serial, i in st.get("phones", {}).items():
        local = RUN / f"phone_{serial}"
        print(f"   {i['model']} ({serial}): {count_pairs(local / 'pairs.jsonl')} pairs pulled {last_summary(local / 'log.txt')}")
    if (RUN / "log.txt").exists():
        print("last log lines:")
        for line in (RUN / "log.txt").read_text(encoding="utf-8").splitlines()[-5:]:
            print("   " + line)


def stop():
    pid, _ = read_pid()
    st = load_state()
    for serial in st.get("phones", {}):
        adb_shell(serial, f"cd {phone_run_dir(st)} && kill $(cat pid) 2>/dev/null", 30)
    if pid is None:
        print("battle is not running (phones told to stop)")
        return 0
    subprocess.run(["taskkill", "/PID", str(pid), "/T", "/F"], capture_output=True)
    print(f"battle stopped (PID {pid}); battle.bat resumes it")
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--until", default="05:00", help="no new pair after this time (first launch only): HH:MM or ISO")
    ap.add_argument("--status", action="store_true")
    ap.add_argument("--stop", action="store_true")
    ap.add_argument("--run", help="another run directory (smoke tests)")
    ap.add_argument("--ft-steps", type=int, help="fine-tuning steps (smoke tests)")
    ap.add_argument("--phone-margin", type=int, help="minutes before the deadline after which no phone starts")
    args = ap.parse_args()
    os.chdir(HERE)
    global RUN, PHONE_MARGIN_MIN
    if args.run:
        RUN = Path(args.run)
    if args.ft_steps:
        FT["steps"], FT["warmup"] = args.ft_steps, min(FT["warmup"], args.ft_steps // 2)
    if args.phone_margin is not None:
        PHONE_MARGIN_MIN = args.phone_margin
    RUN.mkdir(parents=True, exist_ok=True)
    if args.status:
        status()
        return 0
    if args.stop:
        return stop()
    pid, info = read_pid()
    if pid is not None:
        print(f"battle is already running (PID {pid}, since {info.get('started')})")
        return 1
    for f in (SMALL_CKPT, BIG_CKPT, BIG_BIN, Path("runs/battle/phone/classes.dex")):
        if not f.exists():
            print(f"missing {f}")
            return 1
    st = load_state()
    if "until_ms" not in st:
        st["until_ms"] = int(parse_until(args.until).timestamp() * 1000)
        st["run_id"] = f"{dt.datetime.now():%Y%m%d_%H%M%S}"
        save_state(st)
    own = os.getpid()
    write_atomic(RUN / "battle.pid", json.dumps({"pid": own, "created": winproc.creation_time(own),
                                                 "started": f"{dt.datetime.now():%Y-%m-%d %H:%M:%S}"}))
    winproc.keep_system_awake()
    try:
        log(f"start (PID {own}), new pairs until {dt.datetime.fromtimestamp(st['until_ms'] / 1000):%Y-%m-%d %H:%M}")
        for phase, fn in (("finetune", finetune), ("battle", battle), ("analysis", analyse), ("train", train_after)):
            if st.get(phase):
                continue
            log(f"phase {phase}")
            if fn(st):
                st[phase] = True
                save_state(st)
        log(f"all done: {RUN / 'report.md'}")
        return 0
    except KeyboardInterrupt:
        log("interrupted; battle.bat resumes")
        return 130
    finally:
        try:
            if json.loads((RUN / "battle.pid").read_text())["pid"] == own:
                (RUN / "battle.pid").unlink()
        except OSError:
            pass


if __name__ == "__main__":
    sys.exit(main())
