<!--
  Copyright (C) 2026 Mitch Trachtenberg
  Election Ballot System — licensed under the GNU General Public License v3.
  See <https://www.gnu.org/licenses/> for the full license text.
-->

# bSuite — Election Ballot Management System

bSuite is an open-source suite for anyone in or out of government to design, print, scan, count, and audit paper election ballots. It consists of four Spring Boot applications that can be run independently or together:

| App | Port | Purpose |
|---|---|---|
| **bBuilder** | 8080 | Design elections, enter candidates, generate ballot PDFs and YAML layout files |
| **bCounter** | 8081 | Load ballot images, count votes, report results |
| **bViewer** | 8082 | Review scanned ballot images with vote-indicator overlays (runs inside bCounter) |
| **bScanner** | 8083 | Drive a physical document scanner to produce ballot images |

---

## Download & Run (No Development Tools Required)

Pre-built JAR files are available on the [Releases page](https://github.com/mjtrac/bSuite/releases).
You need only **Java 21 or later** — no Maven, no git, no IDE.

### 1. Install Java 21+

- **macOS:** `brew install openjdk@21` or download from [adoptium.net](https://adoptium.net)
- **Windows:** Download from [adoptium.net](https://adoptium.net) and run the installer
- **Linux:** `sudo apt install openjdk-21-jre` or equivalent

Verify: `java -version` should show 21 or higher.

### 2. Download the JARs

From the [latest release](https://github.com/mjtrac/bSuite/releases/latest), download:
- `bBuilder-1.0.0.jar`
- `bCounter-1.0.0.jar`
- `bScanner-1.0.0.jar` (optional — only needed if driving a physical scanner)

### 3. Run

Open two terminal windows:

```bash
# Terminal 1 — Ballot Designer
java -jar bBuilder-1.0.0.jar
# Opens at http://localhost:8080

# Terminal 2 — Scanner, Counter & Viewer
java -jar bCounter-1.0.0.jar
# Opens at http://localhost:8081
# Ballot image viewer at http://localhost:8082/viewer/

# Terminal 3 (optional) — Physical scanner driver
java -jar bScanner-1.0.0.jar
# Opens at http://localhost:8083
```

All three apps create their data directories automatically on first run — no
manual setup required.

### 4. Configure (optional)

Override any setting on the command line:

```bash
java -jar bCounter-1.0.0.jar \
  --scanner.default.image.dir=/path/to/scans \
  --viewer.password=YourPassword \
  --viewer.server.port=8082

java -jar bBuilder-1.0.0.jar \
  --ballot.export.dir=/path/to/ballot/output \
  --app.login-title="My County Election System"
```

Or create an `application.properties` file in the same directory as the JAR.

### macOS launcher (optional)

Download `bSuite_app.zip` from the release, unzip it, place `bSuite.app` in the
same folder as the JARs, then:

```bash
# One-time: remove macOS quarantine
xattr -cr /path/to/bSuite.app

# Launch
open /path/to/bSuite.app
```

The launcher starts bBuilder and bCounter with a double-click and provides
Start/Stop controls.

---

## Data Directories

All bSuite data is consolidated under a single root directory: `~/bSuite_data/`.
This makes backup, migration, and management simple — one folder contains everything.
All subdirectories are created automatically on first run.

```
~/bSuite_data/
  db/
    election_ballot.db      ← bBuilder (elections, templates, combinations)
    counter_results.db      ← bCounter (scan results, votes)
    scanner.db              ← bScanner (users, configuration)
  ballot_templates/         ← bBuilder writes ballot PDFs and YAMLs here;
                               bCounter reads YAMLs from the same location
  cast_ballot_scans/        ← bScanner deposits images here;
                               bCounter reads images from the same location
  reports/                  ← bCounter results (HTML, CSV, overvote report)
  writeins/                 ← bCounter write-in image crops
  scribbles/                ← bCounter scribble-detection outline images
  logs/
    bBuilder.log
    bCounter.log
    bScanner.log
```

All paths are configurable in each app's `application.properties`. The table
below shows the key configurable properties and their defaults:

| Property | Default | App |
|---|---|---|
| `ballot.export.dir` | `~/bSuite_data/ballot_templates` | bBuilder |
| `scanner.default.image.dir` | `~/bSuite_data/cast_ballot_scans` | bCounter |
| `scanner.default.report.dir` | `~/bSuite_data/ballot_templates` | bCounter |
| `reports.output.dir` | `~/bSuite_data/reports` | bCounter |
| `data.writeins.dir` | `~/bSuite_data/writeins` | bCounter |
| `data.scribbles.dir` | `~/bSuite_data/scribbles` | bCounter |
| `scanner.output.dir` | `~/bSuite_data/cast_ballot_scans` | bScanner |

---

## Quick Start (Development / Source Build)

> **Just want to run bSuite?** See [Download & Run](#download--run-no-development-tools-required) above.

### Requirements
- Java 21+
- Maven 3.9+ (or use the included `mvnw` wrapper)
- Python 3.9+ (for the test harness only)

### Run bBuilder
```bash
cd bBuilder
./mvnw spring-boot:run
# Open browser: http://localhost:8080
# Default login: admin / ChangeMe123!
```

### Run bCounter (includes bViewer on port 8082)
```bash
cd bCounter
./mvnw spring-boot:run
# bCounter: http://localhost:8081
# bViewer:  http://localhost:8082  (login: admin / ChangeMe123!)
```

### Run bScanner (optional)
```bash
cd bScanner
./mvnw spring-boot:run
# bScanner: http://localhost:8083
# Default login: admin / ChangeMe123!
```

---

## Repository Layout

```
bSuite/
  bBuilder/           Ballot design + PDF generation          (port 8080)
  bCounter/           Ballot scanning + vote counting         (port 8081)
                      └── bViewer embedded                    (port 8082)
  bScanner/           Physical scanner driver                 (port 8083)
  test-harness/       Python automation scripts
  docs/               Documentation (SCHEMA.md, RACE_CONDITION.md)
```

---

## bBuilder

Provides a web UI for election administrators to:

- Define jurisdictions, elections, regions (precincts and precinct groups),
  parties, ballot types, and contests
- Assign contests to regions and candidates to contests
- Create ballot design templates (paper size, column count, font sizes, margins,
  vote indicator style)
- Generate ballot PDFs with machine-readable QR codes, orientation marks, and
  indicator offset YAML reports
- Manage users (ADMIN, DATA_ENTRY, PRINTER roles)

**Output files** (written to `~/bSuite_data/ballot_templates/`):
- `ballot_*.pdf` — printable ballot
- `ballot_*.yaml` — indicator positions for bCounter

**Ballot features:**
- Vote indicator styles:
  - **Oval** — filled ellipse (recommended for optical scanning)
  - **Rectangle** — filled rectangle
  - **Connect dots** — voter draws a horizontal line between two pointed markers
  - **Number field** — voter writes a rank numeral (ranked-choice ballots)
- Ranked-choice voting: one filled box per rank per candidate; rank-1 box is
  widest and closest to the candidate name; rank numbers printed above boxes
- Write-in slots with centered fill lines
- Multi-page ballots with per-page QR codes
- QR code (1" square, top-right of each page) encodes:
  `JurisdictionId|RegionId|PartyId|BallotTypeId|ElectionId|Page`
- Six registration marks per page: two 18×9 pt page-level marks (PTL/PTR)
  inside the top margin, plus four content-box corner marks (TL rectangle,
  TR/BL/BR squares) just outside the content border
- Flexible HTML header zone: full HTML+CSS with inline images via data URIs,
  rendered by iText html2pdf; supports logos, seals, and custom voter instructions

**Admin password reset:**
```bash
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Dreset.admin.password=true"
```

---

## bCounter

Loads ballot image files (PNG, JPG, TIFF) from `~/bSuite_data/cast_ballot_scans/`
by default, detects corners, warps to canonical coordinates, reads QR codes, and
samples each vote indicator for dark-pixel coverage.

**Key features:**
- Parallel scanning (configurable thread count)
- Upside-down ballot detection and auto-correction: corner detection always runs
  first; if the ballot is upside-down, the image is flipped before QR decoding
- QR-only identification — no linear barcode drawn or read
- Strategy interfaces for ballot identification (`BallotIdentifierService`) and
  corner detection (`BallotCornerDetectorService`) allow alternative implementations
  to be injected for non-bSuite ballot formats
- Six-mark page geometry with geometric corner prediction
- Ranked-choice vote counting with IRV tabulation
- Connect-dots indicator detection (centered vertical stripe)
- Overvote detection per contest
- Write-in image extraction (column-accurate crop)
- Scribble detection via inter-ballot comparison (send one clean ballot first
  to seed the normative baseline)
- Periodic `results_report.html` written to `~/bSuite_data/reports/`

**Scan start form defaults** (pre-populated, operator can override):
- Image folder: `~/bSuite_data/cast_ballot_scans/`
- YAML layout folder: `~/bSuite_data/ballot_templates/`
- Darkness threshold: 8% (dark pixel percentage to declare a mark)

**Configuration (`bCounter/src/main/resources/application.properties`):**
```properties
server.port=8081
viewer.server.port=8082
viewer.username=admin
viewer.password=ChangeMe123!
scanner.parallel-threads=0        # 0 = auto (half CPU cores)
scanner.max-review-before-stop=20 # stop after N corner-detection failures; 0 = no limit
scanner.scribble-detection=true
```

**Report files:**
| File | Location | When written |
|---|---|---|
| `results_report.html` | `~/bSuite_data/reports/` | Every N images + scan end |
| `rcv_report.html` | `~/bSuite_data/reports/` | Scan end (RCV contests) |
| `overvote_report.txt` | `~/bSuite_data/reports/` | Scan end |
| `review_required.txt` | `~/bSuite_data/reports/` | Scan end (if failures) |
| `ballot_manifest.csv` | `~/bSuite_data/reports/` | Scan end (Arlo RLA) |
| `cvr_export.csv` | `~/bSuite_data/reports/` | Scan end (Arlo RLA) |
| `vote_summary.yaml` | image folder | Scan end |
| `*.png.review` | image folder | Per-image (corner detection failure) |
| write-in crops | `~/bSuite_data/writeins/` | Scan end |
| scribble outlines | `~/bSuite_data/scribbles/` | Scan end |

### bViewer (embedded in bCounter)

Opens ballot images with colour-coded vote indicator overlays:
- 🟢 Green — VOTED / MARKED
- 🟡 Amber — OVERVOTED
- 🔵 Blue — UNMARKED

Accessible at `http://localhost:8082` while bCounter is running.

---

## bScanner

Any scanning software can be used with bSuite — simply configure it to deposit
images into `~/bSuite_data/cast_ballot_scans/` and bCounter will find them
automatically. bScanner is included as a convenience: a lightweight web UI that
drives a physical document scanner (NAPS2, scanimage, or a custom command)
without requiring any additional software configuration.

**Supported backends:**
- **NAPS2** (macOS/Windows) — recommended; supports profiles, device selection, duplex
- **scanimage** (Linux) — SANE-based; ADF and duplex supported
- **Custom command** — any shell command; use `{output}` as the output path placeholder

**Roles:** ADMINISTRATOR (full config access) and OPERATOR (scan only).

**Configuration** (`bScanner/src/main/resources/application.properties`):
```properties
server.port=8083
scanner.backend=naps2
scanner.output.dir=${user.home}/bSuite_data/cast_ballot_scans
scanner.dpi=300
scanner.duplex=true
scanner.source=duplex
```

---

## Vote Indicator Styles

| Style | Description | bCounter detection |
|---|---|---|
| **Oval** | Filled ellipse | Dark pixel % in inset sampling region |
| **Rectangle** | Filled rectangle | Dark pixel % in inset sampling region |
| **Connect dots** | Draw line between markers | Any dark pixel in centered 10%-wide vertical stripe |
| **Number field** | Write rank numeral | Dark pixel % (RCV only, not selectable as template style) |

Ranked-choice contests always use rank boxes regardless of the template's
indicator style. Connect-dots is not available for ranked-choice contests.

---

## Databases

All apps use SQLite by default. bBuilder also supports PostgreSQL.

| App | File | Default location |
|---|---|---|
| bBuilder | `election_ballot.db` | `~/bSuite_data/db/` |
| bCounter | `counter_results.db` | `~/bSuite_data/db/` |
| bScanner | `scanner.db` | `~/bSuite_data/db/` |

For full schema documentation see `docs/SCHEMA.md`.

---

## Test Harness

The `test-harness/` directory contains a Python automation suite that exercises
the full pipeline end-to-end. See `test-harness/README.md` for full details.

### Quick run
```bash
# With bBuilder (8080) and bCounter (8081) already running:
cd test-harness
./run_all.sh
```

### Scripts

| Script | Purpose |
|---|---|
| `build_election.py` | Creates a complete test election in bBuilder via `/api/test/*` REST API; generates ballot PDFs and YAMLs to `~/bSuite_data/ballot_templates/` |
| `mark_ballots.py` | Rasterizes ballot PDFs and applies voting scenarios. Supports `--ballot-pdf`/`--ballot-yaml` to use an existing bBuilder ballot directly |
| `distort_ballots.py` | Applies 15 geometric distortions (rotations, translations, skew, perspective, upside-down) × N copies per marked ballot |
| `run_counter.py` | Drives bCounter via HTTP: logs in, sets image and YAML folders, starts scanning, polls progress, waits for completion |
| `verify_results.py` | **Essential** — queries `counter_results.db` and diffs against `ground_truth_all.json`; prints summary report and writes `verify_report.json` |
| `rcv_tabulate.py` | Standalone IRV tabulator — reads `counter_results.db` and runs ranked-choice elimination rounds independently of bCounter's built-in tabulation |
| `test_template_fields.py` | Unit-tests bBuilder template fields: creates elections, prints ballots, asserts PDF output matches expectations |
| `test_rcv_two_contests.py` | Integration test for two-contest RCV: verifies IRV requires exactly two elimination rounds to produce a winner |
| `check_setup.py` | Verifies all Python dependencies are installed before running the harness |
| `run_all.sh` | Full pipeline: build → mark → distort → scan → verify |
| `reset_scan.sh` | Resets bCounter database and restores image filenames (`.counted` → `.png`) |

### What `run_all.sh` does
1. Runs `build_election.py` to create election in bBuilder
2. Runs `mark_ballots.py` to produce marked ballot PNGs with ground truth
3. Runs `distort_ballots.py` to produce distorted variants
4. Runs `run_counter.py` to drive bCounter through the full image set
5. Runs `verify_results.py` to diff DB results against ground truth

**Voting scenarios tested:** valid filled ovals, X marks, checkmarks, partial
marks, marks outside the box, messy marks, write-ins, ranked-choice, overvotes.

---

## User Roles

### bBuilder
| Role | Permissions |
|---|---|
| `ADMIN` | All: users, data entry, print ballots, audit log |
| `DATA_ENTRY` | Enter/edit election data; no printing |
| `PRINTER` | Generate ballot PDFs; read-only data access |

### bCounter / bViewer
bCounter uses its own user database (separate from bBuilder).
bViewer uses a simple in-memory user configured via `viewer.username` /
`viewer.password` in `application.properties`.

### bScanner
| Role | Permissions |
|---|---|
| `ADMINISTRATOR` | Full access: config, users, scanning |
| `OPERATOR` | Scan only |

---

## macOS Launcher (bSuite.app)

`bSuite.app` is a double-click launcher that starts bBuilder and bCounter
from a simple menu — no Terminal required for normal use.

### Installation

Place `bSuite.app` inside the `bSuite/` folder, next to `bBuilder/` and `bCounter/`:

```
bSuite/
  bBuilder/
  bCounter/
  bScanner/
  test-harness/
  bSuite.app       ← put it here
```

### First-run: remove macOS quarantine

```bash
xattr -cr /Users/yourname/bSuite/bSuite.app
```

### Menu options

| Option | What it does |
|---|---|
| **Start All** | Starts bBuilder and bCounter; opens both in browser when ready |
| Start bBuilder only | Starts bBuilder alone |
| Start bCounter only | Starts bCounter + bViewer |
| Open bBuilder/bCounter/bViewer in Browser | Opens the URL without starting |
| **Run Test Harness…** | Prompts for Full run or Rescan only |
| Stop All | Stops both services |
| Quit | Optionally stops services before exiting |

### Logs

All logs go to `~/bSuite_data/logs/`:
- `bBuilder.log`
- `bCounter.log`
- `bScanner.log`

---

## Building Distributable JARs

```bash
# bBuilder
cd bBuilder
./mvnw clean package -DskipTests
# → target/bBuilder-1.0.0.jar

# bCounter (includes bViewer on port 8082)
cd bCounter
./mvnw clean package -DskipTests
# → target/bCounter-1.0.0.jar

# bScanner
cd bScanner
./mvnw clean package -DskipTests
# → target/bScanner-1.0.0.jar
```

Run without Maven:
```bash
java -jar bBuilder/target/bBuilder-1.0.0.jar
java -jar bCounter/target/bCounter-1.0.0.jar
java -jar bScanner/target/bScanner-1.0.0.jar
```

### Publishing a GitHub Release

```bash
git tag -a v1.0.0 -m "bSuite v1.0.0"
git push origin v1.0.0
# Releases → Draft a new release → attach JARs and bSuite_app.zip
```

---

## Security Notes

| Library | License | Notes |
|---|---|---|
| Spring Boot 3.x | Apache 2.0 | Monitor https://spring.io/security |
| iText 8 + html2pdf | **AGPL 3.0** | AGPL requires open source or commercial license. Government internal use generally satisfies AGPL — confirm with legal. |
| ZXing 3.5.x | Apache 2.0 | QR code decoding |
| SnakeYAML 2.x | Apache 2.0 | **Use 2.x only** — 1.x had critical RCE (CVE-2022-1471) |
| SQLite JDBC 3.45.x | Apache 2.0 | Restrict OS permissions on `.db` files to app user |
| PostgreSQL JDBC 42.x | BSD 2-Clause | Keep updated |
| BCrypt (Spring Security) | Apache 2.0 | Cost factor 12 |

**Application-level protections:** CSRF tokens, Content-Security-Policy,
X-Frame-Options: DENY, session fixation protection, BCrypt password hashing,
parameterised JPA queries, Thymeleaf HTML auto-escaping.

---

## License

GNU General Public License v3 or later.
See `LICENSE` or https://www.gnu.org/licenses/ for the full text.

Copyright (C) 2026 Mitch Trachtenberg
