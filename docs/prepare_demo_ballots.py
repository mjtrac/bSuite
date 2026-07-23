#!/usr/bin/env python3
"""
Off-camera prep step for the pbss video walkthrough (see
docs/video_walkthrough_script.md) -- not part of any recorded segment.

Rasterizes the ballot PDF that builder's DemoWalkthroughRobot just
generated under ~/pbss_demo/ballots/, then marks 10 copies with a fixed,
interesting vote pattern:

  Mayor (plurality):        Alice Johnson=6, Bob Williams=3, Carmen Diaz=1

  City Council (ranked, 6 -- 5 named + a Write-In slot): first-rank votes
  split 3/2/2/1/1/1 across Dana Kim/Elena Ruiz/Frank Osei/Grace Chen/
  Hadassah Olayinka Ali-Youngman/Write-In. Round 1 eliminates the three
  1-vote candidates together (Grace, Hadassah, Write-In -- tied for last),
  each transferring its single ballot to a second choice, producing an
  exact 4/3/3 second round (Dana Kim/Elena Ruiz/Frank Osei). Elena and
  Frank are then tied for last and
  eliminated together in round 2; two of their six combined ballots
  transfer to Dana Kim (the other four exhaust -- no further rank marked),
  so Dana Kim wins the final round outright with 6 votes. Ballot 10 is the
  one that marks the Write-In slot, so it also carries a hand-written
  write-in name for counter's write-in crop/report pipeline to pick up.

  Measure B (60% required): Yes=7, No=3 -- passes at 70%, comfortably over
                            the 60% bar (the exact pattern already proven
                            by PercentThreshold60IntegrationTest)

  Ballot 3 also carries an unrelated hand-scribbled note ("Meet Joe at
  5.") to the right of the Mayor contest's title -- a stray mark with no
  bearing on any vote, included so a real, imperfect cast ballot is part
  of the demo set.

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
from PIL import Image, ImageDraw, ImageFont

DEMO_ROOT = Path.home() / "pbss_demo"
BALLOTS_DIR = DEMO_ROOT / "ballots"
CAST_DIR = DEMO_ROOT / "cast_ballots"
DPI = 300

# Casual/handwriting-style fonts (macOS Supplemental fonts) used for the
# stray scribble note and the write-in name, so both read as pen-written
# rather than printed. Falls back to PIL's default bitmap font -- still
# renders, just not handwriting-styled -- if neither is present (e.g. CI).
HANDWRITING_FONT_CANDIDATES = [
    "/System/Library/Fonts/Supplemental/Bradley Hand Bold.ttf",
    "/System/Library/Fonts/Supplemental/Noteworthy.ttc",
]

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


def load_layout(yaml_path: Path):
    with open(yaml_path) as f:
        data = yaml.safe_load(f)
    sides = data if isinstance(data, list) else data.get("sides", [data])
    return sides[0]


def indicators_from_side(side, dpi=300):
    boxes = {}
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


def contest_box(side, title, dpi=300):
    for contest in side.get("contests", []):
        if contest.get("title") == title:
            bb = contest["boundingBox"]
            return {
                "x": int(float(bb["offsetFromLeft"]) * dpi),
                "y": int(float(bb["offsetFromTop"]) * dpi),
                "w": int(float(bb["width"]) * dpi),
                "h": int(float(bb["height"]) * dpi),
            }
    return None


def draw_fill(draw, box, color=(5, 5, 5)):
    x, y, w, h = box["x"], box["y"], box["w"], box["h"]
    inset = max(1, int(min(w, h) * 0.10))
    draw.ellipse([x + inset, y + inset, x + w - inset, y + h - inset], fill=color)


def _load_handwriting_font(size):
    for path in HANDWRITING_FONT_CANDIDATES:
        if Path(path).is_file():
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


def draw_handwritten_text(img: Image.Image, text: str, x: int, y: int,
                           size: int = 46, angle: float = -4.0,
                           color=(30, 30, 130)):
    """Pastes rotated, hand-font text onto img with (x, y) as its top-left."""
    font = _load_handwriting_font(size)
    draw = ImageDraw.Draw(img)
    bbox = draw.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    pad = 10
    layer = Image.new("RGBA", (tw + pad * 2, th + pad * 2), (0, 0, 0, 0))
    ImageDraw.Draw(layer).text((pad - bbox[0], pad - bbox[1]), text, font=font, fill=color + (255,))
    layer = layer.rotate(angle, expand=True, resample=Image.BICUBIC)
    img.paste(layer, (x, y), layer)


def mark_one(src_png: Path, boxes: dict, marks: list, out_path: Path,
             scribble=None, writein_name=None, writein_box=None):
    img = Image.open(str(src_png)).convert("RGB")
    draw = ImageDraw.Draw(img)
    for name in marks:
        box = boxes.get(name)
        if box is None:
            print(f"  WARNING: no indicator found for {name!r} -- check candidate names "
                  "match exactly what DemoWalkthroughRobot typed into builder.")
            continue
        draw_fill(draw, box)

    if scribble is not None:
        draw_handwritten_text(img, scribble["text"], scribble["x"], scribble["y"],
                               size=scribble.get("size", 44))

    if writein_name is not None and writein_box is not None:
        # On the blank line below the Rank 1 indicator/candidate-name row --
        # matches VoteTallyService.saveWriteInRegion()'s capture window
        # (indicator top down ~0.85in, full contest column width), so the
        # crop counter produces for review actually contains the name.
        wx = writein_box["x"] + writein_box["w"] + 20
        wy = writein_box["y"] + writein_box["h"] + 90
        draw_handwritten_text(img, writein_name, wx, wy, size=34, angle=-2.0)

    img.save(str(out_path), dpi=(DPI, DPI))


# One row per cast ballot: Mayor pick, City Council ranks (rank 1 required,
# further ranks optional -- a ballot that only ranks one choice simply
# exhausts if that choice is eliminated), Measure pick.
# Mayor/Measure marks reproduce RcvFiveCandidateIntegrationTest's/
# PercentThreshold60IntegrationTest's already-proven patterns unchanged;
# only the City Council ranks are new (see module docstring for the math).
BALLOTS = [
    # mayor,           cc ranks (in order),                measure
    ("Alice Johnson",  ["Dana Kim"],                        "Yes"),
    ("Alice Johnson",  ["Dana Kim"],                         "Yes"),
    ("Alice Johnson",  ["Dana Kim"],                         "Yes"),
    ("Alice Johnson",  ["Elena Ruiz", "Dana Kim"],           "Yes"),
    ("Alice Johnson",  ["Elena Ruiz"],                       "Yes"),
    ("Alice Johnson",  ["Frank Osei", "Dana Kim"],           "Yes"),
    ("Bob Williams",   ["Frank Osei"],                       "Yes"),
    ("Bob Williams",   ["Grace Chen", "Dana Kim"],           "No"),
    ("Bob Williams",   ["Hadassah Olayinka Ali-Youngman", "Elena Ruiz"], "No"),
    ("Carmen Diaz",    ["Write-In", "Frank Osei"],           "No"),
]

# 1-indexed ballot number carrying the stray handwritten note (unrelated to
# any vote) and the ballot marking the Write-In slot (carries a written name).
SCRIBBLE_BALLOT = 3
SCRIBBLE_TEXT = "Meet Joe at 5."
WRITEIN_BALLOT = 10
WRITEIN_NAME = "Jordan Ellis"


def marks_for_ballot(mayor, cc_ranks, measure):
    marks = [mayor, measure]
    for i, cand in enumerate(cc_ranks, 1):
        marks.append(f"{cand} (Rank {i})")
    return marks


def main():
    pdf_path, yaml_path = find_ballot_files()
    print(f"Ballot: {pdf_path.name} / {yaml_path.name}")

    if CAST_DIR.exists():
        shutil.rmtree(CAST_DIR)
    src_png = rasterize(pdf_path)

    side = load_layout(yaml_path)
    boxes = indicators_from_side(side, DPI)
    print(f"Loaded {len(boxes)} indicator boxes from YAML")

    mayor_box = contest_box(side, "Mayor", DPI)
    writein_rank1_box = boxes.get("Write-In (Rank 1)")

    for i, (mayor, cc_ranks, measure) in enumerate(BALLOTS, 1):
        marks = marks_for_ballot(mayor, cc_ranks, measure)

        scribble = None
        if i == SCRIBBLE_BALLOT and mayor_box is not None:
            scribble = {
                "text": SCRIBBLE_TEXT,
                "x": mayor_box["x"] + int(mayor_box["w"] * 0.55),
                "y": mayor_box["y"] + 4,
            }

        writein_name = WRITEIN_NAME if i == WRITEIN_BALLOT else None

        dest = CAST_DIR / f"cast_ballot_{i:02d}.png"
        mark_one(src_png, boxes, marks, dest,
                 scribble=scribble,
                 writein_name=writein_name, writein_box=writein_rank1_box)
        cc_desc = " > ".join(cc_ranks)
        extra = " [scribble]" if scribble else ""
        extra += " [write-in]" if writein_name else ""
        print(f"  {dest.name}: Mayor={mayor}, CC={cc_desc}, Measure={measure}{extra}")

    src_png.unlink()
    print(f"\nWrote {len(BALLOTS)} marked ballot images to {CAST_DIR}")
    print("\nDone. Next: run counter's DemoWalkthroughRobot.")


if __name__ == "__main__":
    main()
