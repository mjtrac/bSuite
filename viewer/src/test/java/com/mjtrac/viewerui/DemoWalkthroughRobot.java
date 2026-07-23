/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3.
 *
 * Robot-driven walkthrough of viewer, for recording the "reviewing
 * individual ballots" segment of the pbss video demo. See
 * docs/video_walkthrough_script.md for exactly what to say at each beat.
 * Must run after counter's own DemoWalkthroughRobot has counted the demo
 * ballots — this robot reads the exact same database counter just wrote
 * to, and signs in with the "admin"/"ChangeMe123!" account
 * CounterDataInitializer auto-created there.
 *
 * Not a JUnit test -- a standalone main(). Run with:
 *   cd viewer && mvn -q -o exec:java \
 *     -Dexec.mainClass=com.mjtrac.viewerui.DemoWalkthroughRobot \
 *     -Dexec.classpathScope=test
 */
package com.mjtrac.viewerui;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.finder.WindowFinder;
import org.assertj.swing.fixture.DialogFixture;
import org.assertj.swing.fixture.FrameFixture;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DemoWalkthroughRobot {

    private static final Path DEMO_ROOT = Paths.get(System.getProperty("user.home"), "pbss_demo");
    private static final Path DB_PATH = DEMO_ROOT.resolve("db").resolve("counter_demo.db");

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
        if (!Files.exists(DB_PATH)) {
            System.err.println("No demo database found at " + DB_PATH
                + " -- run counter's DemoWalkthroughRobot first.");
            System.exit(1);
        }

        PbssTheme.install();
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(ViewerApp.class)
            .web(WebApplicationType.NONE)
            .headless(false)
            .run("--spring.datasource.url=jdbc:sqlite:" + DB_PATH);

        MainFrame mainFrame = GuiActionRunner.execute(() -> ctx.getBean(MainFrame.class));

        // robotWithCurrentAwtHierarchy(), not ...WithNewAwtHierarchy(): the
        // latter tracks windows via an incrementally-updated AWTEventListener
        // and its isShowing() checks never resolved for a dialog shown via
        // invokeLater() from a background thread rather than a robot-
        // triggered event (confirmed by hand-walking java.awt.Window.
        // getWindows(), which found the same dialog, genuinely showing, at
        // every poll). "Current" hierarchy queries the live AWT window tree
        // directly instead — the same choice builder's/counter's own robots
        // make, for the same underlying reason (MainFrame is already built
        // before this robot exists).
        Robot robot = BasicRobot.robotWithCurrentAwtHierarchy();

        // MainFrame.start() blocks (modal LoginDialog) until sign-in
        // succeeds, so it can't be called directly from this thread -- but
        // it also must run ON the EDT, not an arbitrary background thread,
        // since it creates/shows real Swing components. invokeLater() posts
        // it to the EDT's own queue without this (the calling) thread
        // waiting for it to return.
        SwingUtilities.invokeLater(mainFrame::start);

        DialogFixture login = WindowFinder.findDialog("loginDialog").using(robot);

        try {
            beat("Sign in as admin", () -> {
                login.textBox("usernameField").setText("admin");
                login.textBox("passwordField").setText("ChangeMe123!");
                login.button("signInButton").click();
            });

            FrameFixture window = new FrameFixture(robot, mainFrame);
            window.show();
            window.focus();

            beat("Browse the ballot list", () -> {
                window.table("ballotTable").requireRowCount(10);
            });

            beat("Open the first ballot", () -> {
                window.table("ballotTable").selectRows(0);
                window.button("viewButton").click();
            });

            beat("Step to the next ballot", () -> {
                window.button("nextButton").click();
            });

            waitForEnter("Press Enter to close viewer and exit");
            window.cleanUp();
        } finally {
            ctx.close();
        }
    }

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
}
