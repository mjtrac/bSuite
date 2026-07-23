#!/usr/bin/env python3
"""
Off-camera prep step for the pbss video walkthrough (see
docs/video_walkthrough_script.md) -- not part of any recorded segment.

Rasterizes the ballot PDF that builder's DemoWalkthroughRobot just
generated under ~/pbss_demo/ballots/, then marks 10 copies with a fixed,
interesting vote pattern:

  Mayor (plurality):        Alice Johnson=6, Bob Williams=3, Carmen Diaz=1
  City Council (ranked, 5): Dana=3/Elena=2/Frank=2/Grace=2/Henry=1 first
                            ranks -- no majority, so counting produces two
                            elimination rounds before Dana Kim wins (the
                            exact pattern already proven by
                            RcvFiveCandidateIntegrationTest this session)
  Measure B (60% required): Yes=7, No=3 -- passes at 70%, comfortably over
                            the 60% bar (the exact pattern already proven
                            by PercentThreshold60IntegrationTest)

No account seeding needed here: counter's own CounterDataInitializer
auto-creates an "admin"/"ChangeMe123!" account (ADMIN role) the first time
counter's DemoWalkthroughRobot starts up against the shared demo database
-- ADMIN satisfies viewer's login filter (VIEWER or ADMIN) just as well as
a dedicated VIEWER account would, so viewer's robot signs in with those
same credentials directly.

Requires: pdftoppm (poppler-utils) and Pillow (`pip install pillow`).
"""
import shutil
import subprocess
import sys
from pathlib import Path
from PIL import Image, ImageDraw

DEMO_ROOT = Path.home() / "pbss_demo"
BALLOTS_DIR = DEMO_ROOT / "ballots"
CAST_DIR = DEMO_ROOT / "cast_ballots"
DPI = 300

try:
    import yaml
except ImportError:
    sys.exit("Missing dependency: pip install pyyaml")


def find_ballot_files():
    pdfs = sorted(BALLOTS_DIR.glob("ballot_*.pdf"))
    yamls = sorted(BALLOTS_DIR.glob("ballot_*.yaml"))
    if not pdfs or not yamls:
        sys.exit(
            f"No ballot_*.pdf/.yaml found under {BALLOTS_DIR} -- run builder's "
            "DemoWalkthroughRobot first (see docs/video_walkthrough_script.md)."
        )
    return pdfs[0], yamls[0]


def rasterize(pdf_path: Path) -> Path:
    out_prefix = CAST_DIR / "page"
    CAST_DIR.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["pdftoppm", "-png", "-r", str(DPI), str(pdf_path), str(out_prefix)],
        check=True,
    )
    rendered = sorted(CAST_DIR.glob("page-*.png"))
    if not rendered:
        sys.exit("pdftoppm did not produce a page-*.png -- is poppler-utils installed?")
    return rendered[0]


def indicators_from_yaml(yaml_path: Path, dpi=300):
    with open(yaml_path) as f:
        data = yaml.safe_load(f)
    boxes = {}
    sides = data if isinstance(data, list) else data.get("sides", [data])
    for side in sides:
        for contest in side.get("contests", []):
            for cand in contest.get("candidates", []):
                ind = cand.get("indicator", {})
                if not ind:
                    continue
                ol = float(ind.get("offsetFromLeft", 0))
                ot = float(ind.get("offsetFromTop", 0))
                w = float(ind.get("width", 0.18))
                h = float(ind.get("height", 0.14))
                boxes[cand.get("name", "")] = {
                    "x": int(ol * dpi), "y": int(ot * dpi),
                    "w": int(w * dpi), "h": int(h * dpi),
                }
    return boxes


def draw_fill(draw, box, color=(5, 5, 5)):
    x, y, w, h = box["x"], box["y"], box["w"], box["h"]
    inset = max(1, int(min(w, h) * 0.10))
    draw.ellipse([x + inset, y + inset, x + w - inset, y + h - inset], fill=color)


def mark_one(src_png: Path, boxes: dict, marks: list, out_path: Path):
    img = Image.open(str(src_png))
    draw = ImageDraw.Draw(img)
    for name in marks:
        box = boxes.get(name)
        if box is None:
            print(f"  WARNING: no indicator found for {name!r} -- check candidate names "
                  "match exactly what DemoWalkthroughRobot typed into builder.")
            continue
        draw_fill(draw, box)
    img.save(str(out_path), dpi=(DPI, DPI))


# One row per cast ballot: Mayor pick, City Council rank-1, rank-2, Measure pick.
# Matches RcvFiveCandidateIntegrationTest's proven RCV pattern and
# PercentThreshold60IntegrationTest's proven 60%-threshold pattern exactly.
BALLOTS = [
    ("Alice Johnson", "Dana Kim",   "Elena Ruiz", "Yes"),
    ("Alice Johnson", "Dana Kim",   "Elena Ruiz", "Yes"),
    ("Alice Johnson", "Dana Kim",   "Elena Ruiz", "Yes"),
    ("Alice Johnson", "Elena Ruiz", "Dana Kim",   "Yes"),
    ("Alice Johnson", "Elena Ruiz", "Dana Kim",   "Yes"),
    ("Alice Johnson", "Frank Osei", "Dana Kim",   "Yes"),
    ("Bob Williams",  "Frank Osei", "Dana Kim",   "Yes"),
    ("Bob Williams",  "Grace Chen", "Dana Kim",   "No"),
    ("Bob Williams",  "Grace Chen", "Dana Kim",   "No"),
    ("Carmen Diaz",   "Henry Park", "Dana Kim",   "No"),
]


def marks_for_ballot(mayor, cc_rank1, cc_rank2, measure):
    return [
        mayor,
        f"{cc_rank1} (Rank 1)",
        f"{cc_rank2} (Rank 2)",
        measure,
    ]


def main():
    pdf_path, yaml_path = find_ballot_files()
    print(f"Ballot: {pdf_path.name} / {yaml_path.name}")

    if CAST_DIR.exists():
        shutil.rmtree(CAST_DIR)
    src_png = rasterize(pdf_path)

    boxes = indicators_from_yaml(yaml_path, DPI)
    print(f"Loaded {len(boxes)} indicator boxes from YAML")

    for i, (mayor, cc1, cc2, measure) in enumerate(BALLOTS, 1):
        marks = marks_for_ballot(mayor, cc1, cc2, measure)
        dest = CAST_DIR / f"cast_ballot_{i:02d}.png"
        mark_one(src_png, boxes, marks, dest)
        print(f"  {dest.name}: Mayor={mayor}, CC1={cc1}, CC2={cc2}, Measure={measure}")

    src_png.unlink()
    print(f"\nWrote {len(BALLOTS)} marked ballot images to {CAST_DIR}")
    print("\nDone. Next: run counter's DemoWalkthroughRobot.")


if __name__ == "__main__":
    main()
