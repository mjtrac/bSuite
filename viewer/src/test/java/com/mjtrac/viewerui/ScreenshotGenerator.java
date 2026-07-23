/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 *
 * Manual dev utility, not a JUnit test — generates real screenshots of this
 * app's screens for the user's guide by painting its actual Swing
 * components offscreen into PNGs. Reads (never writes — ddl-auto=none,
 * same as the real app) the rich ballot corpus left behind by an earlier
 * test-harness desktop GUI pipeline run, entirely under test-harness/
 * (gitignored, disposable test output — not real election data).
 */
package com.mjtrac.viewerui;

import com.mjtrac.viewer.service.BallotViewService;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class ScreenshotGenerator {

    static final Path OUT_DIR = Paths.get(System.getProperty("shots.dir",
        "/private/tmp/claude-501/-Users-mjtrac-pbss/2c1ac0f4-5791-487a-b6fa-f42d90ccdd41/scratchpad/shots"));

    // Read-only seed. Never written to here (viewer's own ddl-auto=none
    // matches). Was the disposable output of an earlier blCounter
    // test-harness GUI-pipeline run under test-harness/ (gitignored) — that
    // corpus is emptied/regenerated independently of this tool and had 0
    // ballot_image rows the last time this was run, silently producing only
    // 1 of 3 screenshots (see the exception-swallowing note below). counter's
    // own DemoWalkthroughRobot's demo election (docs/video_walkthrough_script.md)
    // is a real, curated, always-available alternative: 10 ballots across a
    // plurality/ranked-choice/measure mix.
    static final String SEED_DB = System.getProperty("user.home")
        + "/pbss_demo/db/counter_demo.db";

    public static void main(String[] args) {
        try {
            run();
            System.exit(0);
        } catch (Throwable t) {
            // A bare finally{System.exit(0)} here used to swallow whatever
            // this threw without ever printing it -- System.exit()
            // terminates the JVM before an uncaught exception gets a chance
            // to propagate up and print its stack trace, so a real failure
            // (e.g. the seed DB having 0 ballots) looked identical to a
            // clean, silently-partial run.
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        OUT_DIR.toFile().mkdirs();

        if (!new File(SEED_DB).isFile()) {
            throw new IllegalStateException("Seed DB not found: " + SEED_DB
                + " — run counter's DemoWalkthroughRobot first (see docs/video_walkthrough_script.md), "
                + "then docs/prepare_demo_ballots.py before that.");
        }

        String[] overrides = {
            "--spring.datasource.url=jdbc:sqlite:" + SEED_DB,
        };
        // ViewerApp.main() isn't used here (this bypasses it to build the
        // Spring context with test-only datasource overrides), so the
        // look-and-feel install() call it would normally do has to happen
        // here instead — same ordering requirement: before MainFrame gets
        // eagerly constructed as a Spring bean.
        PbssTheme.install();

        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(ViewerApp.class)
            .web(WebApplicationType.NONE)
            .headless(false)
            .run(overrides);

        String effectiveUrl = ctx.getEnvironment().getProperty("spring.datasource.url");
        System.out.println("Effective spring.datasource.url = " + effectiveUrl);
        if (effectiveUrl == null || !effectiveUrl.contains(SEED_DB)) {
            throw new IllegalStateException(
                "REFUSING TO CONTINUE: datasource did not resolve to the read-only seed db. "
                + "Effective URL was: " + effectiveUrl);
        }

        BallotListPanel listPanel = ctx.getBean(BallotListPanel.class);
        BallotViewPanel viewPanel = ctx.getBean(BallotViewPanel.class);
        BallotViewService viewService = ctx.getBean(BallotViewService.class);

        listPanel.setSize(1100, 750);
        listPanel.doLayout();
        listPanel.addNotify();
        listPanel.refresh();
        listPanel.validate();
        shoot(listPanel, "viewer_1_list.png");

        List<Long> ids = viewService.listAll().stream().map(b -> b.id).toList();
        if (ids.isEmpty()) {
            throw new IllegalStateException("Seed DB has 0 ballot images: " + SEED_DB
                + " -- run docs/prepare_demo_ballots.py and counter's DemoWalkthroughRobot first.");
        }
        // First demo ballot has a real mix of marks across all three
        // contests (Mayor, ranked-choice City Council, Measure B) -- more
        // illustrative on one screen than an arbitrary/empty one.
        Long targetId = viewService.listAll().stream()
            .filter(b -> b.imageName.contains("cast_ballot_01"))
            .map(b -> b.id).findFirst().orElse(ids.get(0));

        viewPanel.setSize(1100, 750);
        viewPanel.doLayout();
        viewPanel.addNotify();
        viewPanel.validate();

        SwingUtilities.invokeAndWait(() -> viewPanel.load(targetId, ids));
        // load() schedules fitToViewport() via invokeLater — flush the
        // EDT queue once more so it actually runs before we paint.
        SwingUtilities.invokeAndWait(() -> {});
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(() -> {});

        // fitToViewport() shrinks the whole tall page to fit height-wise,
        // leaving most of the 1100x750 frame as empty side margin around a
        // thumbnail-sized ballot. Zoom to a width-fit scale instead (bigger,
        // since width — not height — becomes the binding dimension), then
        // scroll past the header/barcode band so the frame opens directly
        // on Mayor/City Council/Measure B rather than the page top.
        OverlayImagePanel canvas = (OverlayImagePanel) getField(viewPanel, "canvas");
        JScrollPane scroll = (JScrollPane) getField(viewPanel, "scroll");
        java.lang.reflect.Method setScaleMethod = BallotViewPanel.class.getDeclaredMethod("setScale", double.class);
        setScaleMethod.setAccessible(true);

        Dimension viewportSize = scroll.getViewport().getExtentSize();
        double widthFit = canvas.fitScale(viewportSize.width, Integer.MAX_VALUE);
        SwingUtilities.invokeAndWait(() -> {
            try {
                // Goes through BallotViewPanel's own setScale (not
                // canvas.setScale directly) so the toolbar's zoom-percent
                // label stays in sync with what's actually rendered.
                setScaleMethod.invoke(viewPanel, widthFit);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
            // Force the viewport's notion of the view's size immediately,
            // rather than relying on revalidate()'s asynchronous layout
            // pass to resize `canvas` before setViewPosition() below reads
            // it — setViewPosition() clamps against the view's *current*
            // bounds, which without this were still the pre-zoom (fit-to-
            // page) size at this point in the EDT queue.
            scroll.getViewport().setViewSize(canvas.getPreferredSize());
        });
        SwingUtilities.invokeAndWait(() -> {});
        int scaledHeight = canvas.getPreferredSize().height;
        // ballotContentArea starts ~20% down a Letter-size page (corner
        // marks, barcode, and the jurisdiction/election header live above
        // it, per the ballot YAML's own offsetFromTop values) — skip that
        // band.
        int scrollY = (int) Math.round(scaledHeight * 0.20);
        SwingUtilities.invokeAndWait(() -> scroll.getViewport().setViewPosition(new Point(0, scrollY)));
        Thread.sleep(200);
        SwingUtilities.invokeAndWait(() -> {});

        shoot(viewPanel, "viewer_2_view.png");

        ContestCandidateWindow contestWindow = ctx.getBean(ContestCandidateWindow.class);
        contestWindow.setSize(400, 620);
        contestWindow.addNotify();
        JTree tree = (JTree) getField(contestWindow, "tree");
        for (int row = 0; row < tree.getRowCount(); row++) tree.expandRow(row);
        contestWindow.validate();
        shoot((JComponent) contestWindow.getContentPane(), "viewer_3_contests.png");

        System.out.println("Done: " + OUT_DIR);
    }

    static Object getField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    static void shoot(JComponent panel, String filename) throws Exception {
        BufferedImage img = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, img.getWidth(), img.getHeight());
        panel.paint(g2);
        g2.dispose();
        ImageIO.write(img, "png", OUT_DIR.resolve(filename).toFile());
        System.out.println("Wrote " + filename + " (" + img.getWidth() + "x" + img.getHeight() + ")");
    }
}
