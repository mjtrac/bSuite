# pbss Video Walkthrough Script

**For:** a YouTube demo video recorded with iMovie (screen capture + voiceover)
**Apps covered:** `builder` → `counter` → `viewer` (the native Swing desktop apps)
**Robot:** each segment is driven by a robot program (`DemoWalkthroughRobot`, one per
app) using real `java.awt.Robot` clicks/typing via AssertJ-Swing. The robot performs
one beat, then pauses and prints `>> Beat N done: ... -- press Enter to continue...`
in the terminal. **Read the narration below at your own pace, then press Enter** when
you're ready for the robot to perform the next beat. There's no clock to race —
record in one continuous take per segment, narrating over what's already on screen
and then watching the next action happen live.

**Demo data:** one election — *Humboldt County, 2026 General Election* — with
three contests chosen to show off three different counting modes:
- **Mayor** — plurality, vote for one, three candidates (one with a party,
  incumbent marker, and note beyond just a name)
- **City Council** — ranked-choice, five named candidates plus a write-in slot
- **Measure B (Library Bond)** — a ballot measure requiring 60% (not the usual
  50%) to pass, with a real preamble/postamble (the statutory blurb and fiscal
  note a bond measure actually carries)

Everything lives under `~/pbss_demo/`, never your real election data. Each robot
wipes and rebuilds its own app's demo database at the start of every run, so you can
re-record a segment as many times as you need.

---

## Setup (before you start recording)

1. Build everything once: `builder-core`, `counter-core`, `builder`, `counter`,
   `viewer` — `mvn -q -o install -DskipTests` in each `-core` module, then a plain
   build in each app module.
2. Open three terminal windows/tabs, one per app, sized so you can screen-record the
   app window in each while keeping the terminal itself off to the side (or on a
   second monitor) — you'll be reading narration from the terminal's prompts and
   pressing Enter there, not clicking anything yourself.
3. Have this script open on a second screen or printed out.

---

## Segment 1 — Building the ballot (`builder`)

**Terminal:**
```
cd builder
mvn -q -o exec:java -Dexec.mainClass=com.mjtrac.builderui.DemoWalkthroughRobot -Dexec.classpathScope=test
```

> Start screen recording now, showing the builder window once it appears.

### Beat 1 — Open builder

*[Robot opens the builder window to its Home dashboard.]*

> "This is builder — pbss's ballot design tool. It's a real, native desktop app, not
> a web page — everything you're about to see runs locally, no server, no internet
> connection required. The dashboard walks through the seven steps of designing a
> ballot, in order, though you're free to jump around using the menus at any time."

**Press Enter.**

### Beat 2 — Create the jurisdiction

*[Robot opens Setup → Jurisdictions, clicks New, types "Humboldt County", saves.]*

> "Every ballot starts with a jurisdiction — the county or district running the
> election. I'll call ours Humboldt County."

**Press Enter.**

### Beat 3 — Create the region

*[Robot opens Setup → Regions, creates "Precinct 1" as a single precinct.]*

> "Next, a region — the geographic area a particular ballot style covers. For a
> simple election like this one, a single precinct is all we need. Larger
> jurisdictions can group precincts into districts, and contests can be assigned to
> just the regions where they apply."

**Press Enter.**

### Beat 4 — Create the ballot type

*[Robot opens Setup → Ballot Types, creates "Precinct".]*

> "Ballot type distinguishes how a ballot was cast — precinct, mail-in, provisional,
> and so on. Ours is a straightforward precinct ballot."

**Press Enter.**

### Beat 5 — Create the election

*[Robot opens Setup → Elections, creates "2026 General Election" dated 2026-11-03.]*

> "Now the election itself, tied to the jurisdiction we just created."

**Press Enter.**

### Beat 6 — Create the Mayor contest

*[Robot opens Setup → Contests, creates "Mayor" as a plurality contest, vote for
one. Saving cascades straight into the Candidates dialog, where the robot types in
three real candidate names — Alice Johnson gets an explanatory note, so the table
visibly carries more than just a name column. Saving that cascades into the
Regions dialog, where the robot assigns Precinct 1.]*

> "Our first contest: Mayor, a standard vote-for-one race. Saving a new contest
> takes you straight into adding candidates — Alice Johnson, Bob Williams, Carmen
> Diaz. Notice the Candidates table has room for more than a name: an optional
> party affiliation, a prefix or suffix printed right on the ballot line, and an
> explanatory note underneath — here, 'Incumbent (Ind)' prints in italics under
> Alice Johnson's name rather than crowding the name line itself. None of that is
> required, but it's there when a real race needs it. Saving candidates moves into
> assigning which regions this contest appears in — that assignment is what lets
> pbss build different ballot styles for different parts of a jurisdiction from
> the same election."

**Press Enter.**

### Beat 7 — Create the City Council contest (ranked choice)

*[Robot creates "City Council" as a ranked-choice contest, five ranks, five named
candidates plus a sixth "Write-In" slot, assigned to Precinct 1.]*

> "Here's where it gets more interesting: City Council is ranked-choice voting.
> Voters rank up to five candidates in order of preference, and if nobody gets a
> majority of first-choice votes, the counter runs an instant-runoff — eliminating
> the lowest candidate(s) and transferring their voters' next choice — until
> someone does. I'm also adding a sixth slot here, marked Write-In, so a voter can
> back someone who isn't printed on the ballot at all. We'll see both the runoff
> and a real write-in play out in a few minutes."

**Press Enter.**

### Beat 8 — Create Measure B (60% threshold, preamble/postamble)

*[Robot creates "Measure B — Library Bond" as a MEASURE contest, sets "Percent
Required to Win" to 60, types a preamble and postamble and checks their "Print"
boxes, adds Yes/No candidates, assigns Precinct 1.]*

> "And a ballot measure — Measure B, a library bond. Bond measures often need more
> than a simple majority to pass; this one requires 60%. pbss supports any threshold
> per contest, not just the default fifty-percent-plus-one, and the results report
> will flag any contest using a non-default threshold so nobody misreads a 55%
> result as a win when the real bar was 60. I'm also filling in a preamble — the
> statutory description that has to appear before the Yes/No choices — and a
> postamble underneath with the estimated cost to homeowners. Both print right on
> the ballot exactly where a real bond measure's language is legally required to
> go."

**Press Enter.**

### Beat 9 — Ballot design template

*[Robot opens Ballots → Ballot Design Templates, creates one: letter paper, oval
indicators, one column.]*

> "Before printing, a design template sets the physical layout — paper size, how
> voters mark their choice (ovals, here), and column count. This is also where
> fonts, headers, and language options live, though we'll keep it simple for this
> walkthrough."

**Press Enter.**

### Beat 10 — Ballot combination

*[Robot opens Ballots → Ballot Combinations, creates one: Precinct 1, nonpartisan,
Precinct ballot type, this election.]*

> "A ballot combination ties a region, party, and ballot type together into one
> exact ballot style. This is a nonpartisan election, so there's no party to select
> — just the precinct, the ballot type, and the election."

**Press Enter.**

### Beat 11 — Create an authorized print user

*[Robot opens Admin → Users, creates "clerk1" as an admin account.]*

> "One last thing before printing: pbss records who actually generated each
> ballot batch, so it needs a real user account to attribute that to — the same
> kind of accountability you'd want from any election system's audit trail."

**Press Enter.**

### Beat 12 — Generate the PDF

*[Robot opens Ballots → Print, selects the combination/template/user, clicks
Generate. A real PDF and YAML layout file are written to disk. It then clicks
"Open Output Folder" — a real Finder window opens on the export folder,
showing both files.]*

> "And that's enough to print a real ballot. Generate produces an actual PDF, plus a
> YAML file describing the exact pixel position of every vote target on the page —
> that YAML is what the counter uses to know where to look. Here's the folder they
> both landed in."

**Press Enter.**

> *[With the Finder window now open, click the PDF to open and scroll through
> it — point out the corner marks in each corner (used for orientation and to
> correct for a tilted scan), and the ovals next to each candidate. Then open
> the YAML alongside it and point out that its coordinates are exactly what
> counter reads to find those same marks on a scanned image.]*

### Beat 13 — Try a different design

*[Robot creates a second Ballot Design Template — same election, letter paper,
but "Connect the Dots" instead of ovals, and two columns — then generates and
opens the output folder again. A generated ballot's filename is keyed to its
ballot combination, not its template, so this regenerates the *same* PDF/YAML
the folder already had, now in the new design — not a second file alongside
the first. The robot then silently regenerates once more with the original
oval/one-column template, off camera, so the ballot the rest of this demo
(and `prepare_demo_ballots.py`, next) is built around ends up back on disk
exactly as it was after Beat 12 — that last regenerate isn't narrated or
shown; nothing changes on screen when it happens.]*

> "The physical design isn't locked in once you've printed — here's a second
> template for the same election: connect-the-dots instead of filled ovals, and
> two columns instead of one, even though this short a ballot doesn't actually
> need the second column. Generate again, and the same PDF is rebuilt in the
> new design — same contests, same candidates, a completely different physical
> layout."

**Press Enter** — the robot prints where the files landed and exits.

> *[Open the regenerated PDF and page through it — point out how different
> connect-the-dots and two columns look against the oval/one-column version
> from a moment ago.]*

**Stop recording this segment.**

---

## Off-camera — preparing cast ballots

*(This step is intentionally not filmed — a real video wouldn't show voters
filling out ballots by hand either. Run this between recording sessions.)*

```
python3 docs/prepare_demo_ballots.py
```

This rasterizes the oval/one-column ballot PDF — Beat 13's connect-the-dots/
two-column design is shown briefly for the demo but never scanned or counted;
its own last action silently regenerates the oval version back onto disk before
builder exits, specifically so this step always finds the right one — and
produces 10 marked, scanned-looking ballot images under `~/pbss_demo/cast_ballots/`
as if they'd already come back from the precinct. The vote pattern is designed
to be interesting on camera:

- **Mayor:** Alice Johnson wins clearly (6 of 10)
- **City Council:** first-choice votes split 3/2/2/1/1/1 across Dana Kim, Elena
  Ruiz, Frank Osei, Grace Chen, Hadassah Olayinka Ali-Youngman, and the
  Write-In slot — nobody has a majority, so the three lowest (tied at 1 each)
  are eliminated together in round 1, producing an exact 4/3/3 second round;
  the two now-tied-for-last are eliminated together in round 2, and Dana Kim
  wins the final round outright with 6 votes. One of the ten ballots is the
  one that actually marks the Write-In slot, with a hand-written name on it —
  counter's write-in crop/review pipeline picks that up automatically.
- **Measure B:** passes at 70% — comfortably over its 60% threshold

One ballot (`cast_ballot_03.png`) also carries an unrelated hand-scribbled note —
"Meet Joe at 5." — to the right of the Mayor contest title, simulating a real
voter's stray pen mark that has nothing to do with any vote. It's there so the
demo set includes at least one imperfect, real-looking ballot rather than ten
that look computer-generated.

---

## Segment 2 — Counting the ballots (`counter`)

**Terminal:**
```
cd counter
mvn -q -o exec:java -Dexec.mainClass=com.mjtrac.counterui.DemoWalkthroughRobot -Dexec.classpathScope=test
```

> Start screen recording now, showing the counter window.

### Beat 1 — Open counter

*[Robot opens counter to its main screen: an image folder field, a report folder
field, and a Start Counting button.]*

> "This is counter — where scanned ballot images actually get counted. In a real
> deployment these images come off a real scanner; for this demo, imagine our
> precinct's ten ballots have already been scanned and dropped into a folder,
> exactly like they would be after a real scanning session."

**Press Enter.**

### Beat 2 — Point counter at the images and layout

*[Robot fills in the image folder (`~/pbss_demo/cast_ballots`) and the report
folder (`~/pbss_demo/ballots`, where builder's YAML lives).]*

> "counter needs two things: where the scanned images are, and where the layout
> files from builder are — that's how it knows what to look for on each page."

**Press Enter.**

### Beat 3 — Start Counting

*[Robot clicks Start Counting. The window shows live progress as all ten images are
processed.]*

> "For each image, counter decodes an identifying QR code, finds the four corner
> marks to work out exactly how the page is oriented — even if it's tilted or
> printed at a slightly different scale — warps the image mathematically back to a
> perfect rectangle, and then measures how much of each oval is filled in. All of
> that happens in well under a second per ballot, and every result is written
> straight to the database as it goes, not held in memory."

**Press Enter** — the robot waits here until the scan actually finishes.

### Beat 4 — Results

*[Robot opens the results report.]*

> "And here's the tally. Mayor: Alice Johnson wins with a clear majority.
> [Point out the] non-default win threshold banner at the top — that's Measure B's
> 60% requirement being called out explicitly, so nobody misreads what number
> actually mattered.
>
> City Council is the interesting one — six-way field, nobody close to a
> majority on first choices. Open the ranked-choice report and you can see it
> round by round: the three lowest candidates, all tied at one vote apiece, get
> eliminated together in round one — that produces this clean 4/3/3 second
> round. The two now tied for last get eliminated together in round two, and
> Dana Kim comes out the other side with a clear majority: six votes, a
> straight win. That entire ranked-choice tabulation, corner detection, and
> mark-reading pipeline is the same code whether you're counting ten ballots or
> ten thousand.
>
> One more thing worth opening: the write-in report. One of these ten ballots
> marked the write-in slot instead of a printed candidate, and counter didn't
> just count that mark — it cropped the actual handwritten name off the ballot
> image and put it in a report for a human to read and adjudicate. Software
> never decides what a write-in name says; it just makes sure nobody has to go
> hunting through a stack of paper to find it."

**Stop recording this segment.**

---

## Segment 3 — Reviewing individual ballots (`viewer`)

**Terminal:**
```
cd viewer
mvn -q -o exec:java -Dexec.mainClass=com.mjtrac.viewerui.DemoWalkthroughRobot -Dexec.classpathScope=test
```

> Start screen recording now.

### Beat 1 — Sign in

*[Robot signs in with the "admin" account — counter's own app auto-creates it
against the shared demo database the first time counter starts up.]*

> "viewer is pbss's audit tool — it reads the exact same database counter just
> wrote to, and lets you look at any individual ballot image with the counter's own
> markings overlaid on top. This is how a recount or a public audit actually
> verifies the software did what it says it did — not by re-running the algorithm
> and trusting the same code twice, but by putting a human eye on the actual pixels
> next to the actual decision."

**Press Enter.**

### Beat 2 — Browse the ballot list

*[Robot shows the ballot list — all ten scanned images.]*

> "Every ballot counter processed is right here, searchable by filename or by a
> direct SQL filter if you need something more specific — by precinct, by whether a
> particular contest was overvoted, whatever the question is."

**Press Enter.**

### Beat 3 — Open a ballot

*[Robot selects a ballot and clicks View.]*

> "And here's one actual scanned ballot, full size, with a colored box drawn over
> every single place counter looked for a mark. Green means counter recorded a
> vote there; you can see exactly which oval it means, at exactly the position it
> was sampled — this is not a re-rendering or an approximation, it's the real image
> with the real coordinates the counting engine actually used."

**Press Enter** — robot navigates to the next ballot.

### Beat 4 — Navigate

*[Robot clicks Next to move to another ballot.]*

> "You can step through every ballot this way — useful for a spot audit of a
> sample, or for walking a room full of observers through exactly how a specific
> contested result was reached."

**Stop recording.**

---

## Closing

> "That's the full loop: builder designs the ballot and generates the layout the
> rest of the system reads. counter turns scanned images into a verified tally, with
> a full ranked-choice tabulation and custom win thresholds built in, not bolted on.
> And viewer lets anyone — an auditor, an observer, a curious voter — check the
> software's work against the actual image, one ballot at a time. All three are open
> source, GPL-licensed, and none of it leaves your own machine."

---

*End of script.*
