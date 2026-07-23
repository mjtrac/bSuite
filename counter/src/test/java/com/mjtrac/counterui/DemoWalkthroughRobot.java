/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3.
 *
 * Robot-driven walkthrough of counter, for recording the "counting cast
 * ballots" segment of the pbss video demo. See docs/video_walkthrough_script.md
 * for exactly what to say at each beat, and docs/prepare_demo_ballots.py,
 * which must be run first to produce ~/pbss_demo/cast_ballots/.
 *
 * Not a JUnit test -- a standalone main(). Run with:
 *   cd counter && mvn -q -o exec:java \
 *     -Dexec.mainClass=com.mjtrac.counterui.DemoWalkthroughRobot \
 *     -Dexec.classpathScope=test
 *
 * Uses a dedicated demo database under ~/pbss_demo/db/counter_demo.db --
 * never the real ~/pbss_data/db/counter_results.db -- and wipes it clean
 * at the start of every run, so this is safe to re-run for as many takes
 * as recording needs. CounterDataInitializer auto-seeds the "admin" /
 * "ChangeMe123!" account into this same demo database on first startup,
 * which viewer's own DemoWalkthroughRobot then signs in with.
 */
package com.mjtrac.counterui;

import com.mjtrac.counter.service.CountingService;
import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.timing.Condition;
import org.assertj.swing.timing.Pause;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.swing.timing.Timeout.timeout;

public class DemoWalkthroughRobot {

    private static final Path DEMO_ROOT = Paths.get(System.getProperty("user.home"), "pbss_demo");
    private static final Path DB_PATH = DEMO_ROOT.resolve("db").resolve("counter_demo.db");
    private static final Path IMAGES_DIR = DEMO_ROOT.resolve("cast_ballots");
    private static final Path LAYOUT_DIR = DEMO_ROOT.resolve("ballots");

    private static final BufferedReader STDIN = new BufferedReader(new InputStreamReader(System.in));
    private static int beatNumber = 0;

    public static void main(String[] args) {
        try {
            run();
            System.exit(0);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    static void run() throws Exception {
        if (!Files.isDirectory(IMAGES_DIR)) {
            System.err.println("No cast ballots found at " + IMAGES_DIR
                + " -- run docs/prepare_demo_ballots.py first (after builder's "
                + "DemoWalkthroughRobot).");
            System.exit(1);
        }
        if (!Files.isDirectory(LAYOUT_DIR)) {
            System.err.println("No ballot layout found at " + LAYOUT_DIR
                + " -- run builder's DemoWalkthroughRobot first.");
            System.exit(1);
        }

        resetDemoDb();

        // data.dir defaults to the real ~/pbss_data — every report/scan/
        // writein/scribble subfolder is derived from it, so it must be
        // overridden too, not just the datasource, or this demo would write
        // its results report into the user's real pbss_data/reports/.
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(CounterApp.class)
            .web(WebApplicationType.NONE)
            .headless(false)
            .run(
                "--spring.datasource.url=jdbc:sqlite:" + DB_PATH,
                "--data.dir=" + DEMO_ROOT.resolve("pbss_data"));

        MainFrame mainFrame = GuiActionRunner.execute(() -> ctx.getBean(MainFrame.class));
        FrameFixture window = new FrameFixture(BasicRobot.robotWithCurrentAwtHierarchy(), mainFrame);
        window.show();
        window.focus();

        try {
            beat("Open counter", () -> {});

            beat("Point counter at the scanned images and the ballot layout", () -> {
                window.textBox("imageFolderField").deleteText().enterText(IMAGES_DIR.toString());
                window.textBox("reportFolderField").deleteText().enterText(LAYOUT_DIR.toString());
            });

            action("Click Start Counting", () -> window.button("startButton").click());

            System.out.println();
            System.out.println(">> Scanning... waiting for it to finish (this runs live, no fixed pause).");
            Pause.pause(new Condition("scan to finish and results to be written") {
                @Override public boolean test() {
                    return window.button("openResultsButton").target().isEnabled();
                }
            }, timeout(120_000));

            CountingService countingService = ctx.getBean(CountingService.class);
            if (countingService.getSession().scanError != null) {
                throw new IllegalStateException("Scan finished with an error: "
                    + countingService.getSession().scanError);
            }
            System.out.println(">> Scan complete: " + countingService.getSession().processed() + " ballots counted.");

            waitForEnter("Beat " + (++beatNumber) + " done: counting finished");

            beat("Open the results report", () -> window.button("openResultsButton").click());

            waitForEnter("Press Enter to close counter and exit");
        } finally {
            window.cleanUp();
            ctx.close();
        }
    }

    private static void beat(String description, Runnable action) {
        action.run();
        waitForEnter("Beat " + (++beatNumber) + " done: " + description);
    }

    /** Runs an action without a following pause -- used right before a long-running wait (the scan itself). */
    private static void action(String description, Runnable action) {
        action.run();
        System.out.println(description + "...");
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

    private static void resetDemoDb() throws Exception {
        Files.createDirectories(DB_PATH.getParent());
        File dbFile = DB_PATH.toFile();
        if (dbFile.exists()) dbFile.delete();
    }
}
