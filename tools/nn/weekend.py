"""The weekend run, in one command: training until --train-until, then the tournament until
--tournament-until. Re-running it resumes at the right step (training state and tournament
games are saved as they go).

    .venv/Scripts/python -u weekend.py
"""
import argparse
import datetime as dt
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", default="runs/weekend")
    ap.add_argument("--reference-checkpoint", default="runs/main/checkpoints/gen_0198.pt")
    ap.add_argument("--reference-model", default="runs/main/models/gen_0198.bin")
    ap.add_argument("--train-until", default="2026-10-11T18:00")
    ap.add_argument("--tournament-until", default="2026-10-11T20:00")
    args = ap.parse_args()
    for f in (args.reference_checkpoint, args.reference_model):
        if not (HERE / f).exists():
            sys.exit(f"missing {f}")
    python = sys.executable
    try:
        subprocess.run(["java", "-version"], capture_output=True, check=True)
    except (OSError, subprocess.CalledProcessError):
        sys.exit("java is not on the PATH (the self-play tool needs a JDK 17+)")
    if not (HERE.parents[1] / "selfplay" / "build" / "install" / "selfplay" / "lib").exists():
        sys.exit("build the self-play tool first: ./gradlew :selfplay:installDist")

    failures = 0
    while dt.datetime.now() < dt.datetime.fromisoformat(args.train_until):
        print(f"== training until {args.train_until}", flush=True)
        code = subprocess.run([python, "-u", "loop.py", "--run", args.run, "--stop-at", args.train_until,
                               "--init-checkpoint", args.reference_checkpoint,
                               "--elo-every", "0"], cwd=HERE).returncode
        if code == 0:
            break
        # A crash: resume from the last saved generation rather than idle until the tournament.
        failures += 1
        print(f"== training exited with code {code} (failure {failures}); retrying in 60 s", flush=True)
        if failures >= 20:
            break
        time.sleep(60)
    print(f"== tournament until {args.tournament_until}", flush=True)
    subprocess.run([python, "-u", "tournament.py", "--run", args.run, "--reference", args.reference_model,
                    "--stop-at", args.tournament_until], cwd=HERE, check=True)
    print(f"== done: see {Path(args.run) / 'tournament' / 'report.md'}", flush=True)


if __name__ == "__main__":
    main()
