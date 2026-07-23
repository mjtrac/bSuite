/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3.
 *
 * Robot-driven walkthrough of builder, for recording the "building a
 * ballot" segment of the pbss video demo. Drives the real BuilderApp GUI
 * with real java.awt.Robot clicks/typing (via AssertJ-Swing), pausing for
 * an Enter keypress in the terminal between narration beats so the
 * presenter can talk at their own pace and advance when ready — see
 * docs/video_walkthrough_script.md for exactly what to say at each beat.
 *
 * Not a JUnit test — a standalone main(), matching TestElectionBuilder's/
 * RcvFiveCandidateBuilder's pattern. Run with:
 *   cd builder && mvn -q -o exec:java \
 *     -Dexec.mainClass=com.mjtrac.builderui.DemoWalkthroughRobot \
 *     -Dexec.classpathScope=test
 *
 * Uses a dedicated demo area under ~/pbss_demo — never the real
 * ~/pbss_data election database or export folder — and wipes it clean at
 * the start of every run, so this is safe to re-run for as many takes as
 * recording needs.
 */
package com.mjtrac.builderui;

import com.mjtrac.ballot.model.Jurisdiction;
import com.mjtrac.ballot.model.Party;
import com.mjtrac.ballot.model.Region;
import com.mjtrac.ballot.repository.JurisdictionRepository;
import com.mjtrac.ballot.repository.PartyRepository;
import com.mjtrac.ballot.repository.RegionRepository;
import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.data.TableCell;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.FrameFixture;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DemoWalkthroughRobot {

    private static final Path DEMO_ROOT = Paths.get(System.getProperty("user.home"), "pbss_demo");
    private static final Path DB_PATH = DEMO_ROOT.resolve("db").resolve("builder_demo.db");
    private static final Path EXPORT_DIR = DEMO_ROOT.resolve("ballots");

    private static final BufferedReader STDIN = new BufferedReader(new InputStreamReader(System.in));
    private static int beatNumber = 0;

    @SpringBootApplication(scanBasePackages = "com.mjtrac.ballot")
    @EntityScan("com.mjtrac.ballot.model")
    static class SeedConfig {
    }

    public static void main(String[] args) {
        try {
            run();
            System.exit(0);
        } catch (Throwable t) {
            // A plain finally{System.exit(0)} here would silently swallow
            // this without ever printing it -- System.exit() terminates the
            // JVM immediately, before an uncaught exception from run() gets
            // a chance to propagate up and print its stack trace.
            t.printStackTrace();
            System.exit(1);
        }
    }

    static void run() throws Exception {
        resetDemoArea();

        String dbUrl = "jdbc:sqlite:" + DB_PATH;

        // MainFrame's constructor eagerly navigate()s to Home, which
        // refreshCurrent()s every screen -- including PartyPanel/RegionPanel,
        // whose onFirstOpenEmpty() pops a real, blocking "Quick Setup"
        // JOptionPane the instant either table is still empty. Against this
        // freshly-wiped demo DB that fires immediately, before the robot
        // below exists to dismiss it. Seed one throwaway party/region first
        // (through a minimal UI-free context, same trick
        // AbstractBuilderGuiTest/TestElectionBuilder use), then delete them
        // right after MainFrame is built — beats 2/6 create the demo's own
        // real Region and Party from a clean, empty-looking screen.
        // .headless(false) matters even though this seed context never
        // touches Swing: SpringApplicationBuilder defaults to setting the
        // JVM-wide java.awt.headless=true the first time anything touches
        // java.awt, and that sticks for the rest of the process.
        try (ConfigurableApplicationContext seedContext = new SpringApplicationBuilder(SeedConfig.class)
                .web(WebApplicationType.NONE)
                .headless(false)
                .run("--spring.datasource.url=" + dbUrl, "--ballot.export.dir=" + EXPORT_DIR)) {
            JurisdictionRepository jurisdictionRepo = seedContext.getBean(JurisdictionRepository.class);
            Jurisdiction placeholder = new Jurisdiction();
            placeholder.setName("__demo_boot_placeholder__");
            placeholder = jurisdictionRepo.save(placeholder);

            Party party = new Party();
            party.setJurisdiction(placeholder);
            party.setName("__placeholder__");
            seedContext.getBean(PartyRepository.class).save(party);

            Region region = new Region();
            region.setJurisdiction(placeholder);
            region.setName("__placeholder__");
            region.setRegionType(Region.RegionType.SINGLE_PRECINCT);
            seedContext.getBean(RegionRepository.class).save(region);
        }

        PbssTheme.install();
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(BuilderApp.class)
            .web(WebApplicationType.NONE)
            .headless(false)
            .run(
                "--spring.datasource.url=" + dbUrl,
                "--ballot.export.dir=" + EXPORT_DIR);

        // Placeholders have served their purpose (MainFrame's constructor
        // has already run past the empty-table check) -- remove them so
        // beats 1/2/6 start from screens that look genuinely empty on
        // camera, and so the placeholder jurisdiction never appears in any
        // combo box the robot selects "item 0" from below.
        ctx.getBean(com.mjtrac.ballot.repository.RegionRepository.class).deleteAll();
        ctx.getBean(PartyRepository.class).deleteAll();
        ctx.getBean(JurisdictionRepository.class).deleteAll();

        MainFrame mainFrame = GuiActionRunner.execute(() -> ctx.getBean(MainFrame.class));
        FrameFixture window = new FrameFixture(BasicRobot.robotWithCurrentAwtHierarchy(), mainFrame);
        window.show();
        window.focus();

        try {
            beat("Open builder", () -> {});

            beat("Create the jurisdiction — Riverside County", () -> {
                window.menuItem("JurisdictionsMenuItem").click();
                window.button("JurisdictionsNewButton").click();
                window.textBox("nameField").setText("Riverside County");
                window.button("saveButton").click();
            });

            beat("Create the region — Precinct 1", () -> {
                window.menuItem("RegionsMenuItem").click();
                window.button("RegionsNewButton").click();
                window.comboBox("jurisdictionCombo").selectItem(0);
                window.textBox("nameField").setText("Precinct 1");
                window.comboBox("typeCombo").selectItem("SINGLE_PRECINCT");
                window.button("saveButton").click();
            });

            beat("Create the ballot type — Precinct", () -> {
                window.menuItem("BallotTypesMenuItem").click();
                window.button("BallotTypesNewButton").click();
                window.comboBox("jurisdictionCombo").selectItem(0);
                window.textBox("nameField").setText("Precinct");
                window.button("saveButton").click();
            });

            beat("Create the election — 2026 General Election", () -> {
                window.menuItem("ElectionsMenuItem").click();
                window.button("ElectionsNewButton").click();
                window.comboBox("jurisdictionCombo").selectItem(0);
                window.textBox("nameField").setText("2026 General Election");
                window.textBox("dateField").setText("2026-11-03");
                window.comboBox("typeCombo").selectItem("GENERAL");
                window.button("saveButton").click();
            });

            beat("Create the Mayor contest — plurality, three candidates", () -> {
                window.menuItem("ContestsMenuItem").click();
                window.button("ContestsNewButton").click();
                window.comboBox("electionCombo").selectItem(0);
                window.textBox("titleField").setText("Mayor");
                window.comboBox("methodCombo").selectItem("PLURALITY");
                window.spinner("maxChoicesSpinner").enterText("1");
                window.button("saveButton").click();

                addCandidate(window, "Alice Johnson");
                addCandidate(window, "Bob Williams");
                addCandidate(window, "Carmen Diaz");
                window.dialog("candidatesDialog").button("saveContinueButton").click();

                window.dialog("regionsDialog").list("regionList").selectItem(0);
                window.dialog("regionsDialog").button("saveButton").click();
            });

            beat("Create the City Council contest — ranked choice, five candidates", () -> {
                window.menuItem("ContestsMenuItem").click();
                window.button("ContestsNewButton").click();
                window.comboBox("electionCombo").selectItem(0);
                window.textBox("titleField").setText("City Council");
                window.comboBox("methodCombo").selectItem("RANKED_CHOICE");
                window.spinner("maxChoicesSpinner").enterText("1");
                window.spinner("maxRankSpinner").enterText("5");
                window.button("saveButton").click();

                addCandidate(window, "Dana Kim");
                addCandidate(window, "Elena Ruiz");
                addCandidate(window, "Frank Osei");
                addCandidate(window, "Grace Chen");
                addCandidate(window, "Henry Park");
                window.dialog("candidatesDialog").button("saveContinueButton").click();

                window.dialog("regionsDialog").list("regionList").selectItem(0);
                window.dialog("regionsDialog").button("saveButton").click();
            });

            beat("Create Measure B — a library bond requiring 60% to pass", () -> {
                window.menuItem("ContestsMenuItem").click();
                window.button("ContestsNewButton").click();
                window.comboBox("electionCombo").selectItem(0);
                window.textBox("titleField").setText("Measure B — Library Bond");
                window.comboBox("methodCombo").selectItem("MEASURE");
                window.spinner("maxChoicesSpinner").enterText("1");
                window.spinner("percentToWinSpinner").enterText("60");
                window.button("saveButton").click();
                // Changing percentToWin away from the 50% default pops a
                // confirmation dialog (see ContestPanel's showConfirmDialog) —
                // fires here since a brand-new contest's spinner still starts
                // at the 50% default before this beat changes it to 60.
                org.assertj.swing.finder.JOptionPaneFinder.findOptionPane()
                    .using(window.robot()).yesButton().click();

                addCandidate(window, "Yes");
                addCandidate(window, "No");
                window.dialog("candidatesDialog").button("saveContinueButton").click();

                window.dialog("regionsDialog").list("regionList").selectItem(0);
                window.dialog("regionsDialog").button("saveButton").click();
            });

            beat("Set up the ballot design — letter paper, oval indicators", () -> {
                window.menuItem("BallotDesignTemplatesMenuItem").click();
                window.button("BallotDesignTemplatesNewButton").click();
                window.comboBox("electionCombo").selectItem(0);
                window.comboBox("paperCombo").selectItem("LETTER_8_5x11");
                window.comboBox("indicatorCombo").selectItem("OVAL");
                window.spinner("columnsSpinner").enterText("1");
                window.button("saveButton").click();
            });

            beat("Define the ballot combination — Precinct 1, nonpartisan", () -> {
                window.menuItem("BallotCombinationsMenuItem").click();
                window.button("BallotCombinationsNewButton").click();
                window.comboBox("regionCombo").selectItem(0);
                window.comboBox("partyCombo").selectItem(0);
                window.comboBox("typeCombo").selectItem(0);
                window.comboBox("electionCombo").selectItem(0);
                window.button("saveButton").click();
            });

            beat("Create an authorized print user", () -> {
                window.menuItem("AdminUsersMenuItem").click();
                window.button("UsersNewButton").click();
                window.textBox("usernameField").setText("clerk1");
                window.textBox("passwordField").setText("s3cret-pw");
                window.checkBox("adminCheck").check();
                window.comboBox("jurisdictionCombo").selectItem(0);
                window.button("saveButton").click();
            });

            beat("Generate the ballot PDF", () -> {
                window.menuItem("PrintMenuItem").click();
                window.comboBox("combinationCombo").selectItem(0);
                window.comboBox("templateCombo").selectItem(0);
                window.comboBox("userCombo").selectItem(0);
                window.button("generateButton").click();
            });

            System.out.println();
            System.out.println("Done. Ballot PDF/YAML written under: " + EXPORT_DIR);
            System.out.println("Next: run prepare_demo_ballots.py to mark sample cast ballots,");
            System.out.println("then run counter's DemoWalkthroughRobot.");
            waitForEnter("Press Enter to close builder and exit");
        } finally {
            window.cleanUp();
            ctx.close();
        }
    }

    /** Types a candidate's real name into the next open row of the already-visible candidates dialog. */
    private static void addCandidate(FrameFixture window, String name) {
        window.dialog("candidatesDialog").button("addCandidateButton").click();
        int row = window.dialog("candidatesDialog").table("candidatesTable").target().getRowCount() - 1;
        window.dialog("candidatesDialog").table("candidatesTable")
            .enterValue(TableCell.row(row).column(0), name);
    }

    /** Prints the beat's narration cue, runs the GUI action, then blocks on Enter before the next beat. */
    private static void beat(String description, Runnable action) {
        action.run();
        waitForEnter("Beat " + (++beatNumber) + " done: " + description);
    }

    private static void waitForEnter(String prompt) {
        System.out.println();
        System.out.println(">> " + prompt + " -- press Enter to continue...");
        try {
            STDIN.readLine();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void resetDemoArea() throws Exception {
        Files.createDirectories(DEMO_ROOT);
        deleteRecursively(DB_PATH.getParent());
        deleteRecursively(EXPORT_DIR);
        Files.createDirectories(DB_PATH.getParent());
        Files.createDirectories(EXPORT_DIR);
    }

    private static void deleteRecursively(Path dir) throws Exception {
        if (!Files.exists(dir)) return;
        File[] files = dir.toFile().listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteRecursively(f.toPath());
                else f.delete();
            }
        }
        dir.toFile().delete();
    }
}
