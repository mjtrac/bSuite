/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 */
package com.mjtrac.counter.service;

import com.mjtrac.counter.entity.BallotImage;
import com.mjtrac.counter.entity.VoteOpportunity;
import com.mjtrac.counter.model.ScanSession;
import com.mjtrac.counter.repository.BallotImageRepository;
import com.mjtrac.counter.repository.VoteOpportunityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real, end-to-end verification that the counting engine works correctly
 * through this module's own wiring (a copy of blCounter's CountingService
 * and its dependencies) — not just that it compiles/starts. Runs a full
 * scan against real ballot images and a real layout YAML (copied from
 * test-harness/desktop_pipeline/images/marked_ballots/clean/ — 150 DPI,
 * matching this app's fixed counter.dpi=200 closely enough for corner
 * detection; an earlier 300 DPI copy of these same four scenarios passed
 * this test while silently flagging all four for review, because the test
 * only checked processed()==4 — a throughput counter that doesn't
 * distinguish "counted" from "flagged for review" — never reviewRequired
 * or the actual vote tallies. Ground truth below is pulled from
 * test-harness/desktop_pipeline/marked_ballots/ground_truth.json.
 *
 * Uses a test-only @SpringBootApplication scanning only com.mjtrac.counter
 * (not com.mjtrac.counterui) — reusing CounterApp itself would also
 * component-scan MainFrame, constructing a real JFrame as a side effect of
 * building the test context.
 */
@SpringBootTest(classes = CountingServiceIntegrationTest.TestConfig.class,
                 webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CountingServiceIntegrationTest {

    @SpringBootApplication(scanBasePackages = "com.mjtrac.counter")
    @EntityScan("com.mjtrac.counter.entity")
    @EnableJpaRepositories("com.mjtrac.counter.repository")
    static class TestConfig {
    }

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDataDirs(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("test.db"));
        registry.add("data.dir", () -> tempDir.resolve("pbss_data").toString());
        registry.add("scanner.scribble-outline-dir", () -> tempDir.resolve("pbss_data/scribbles").toString());
    }

    @Autowired
    private CountingService countingService;

    @Autowired
    private BallotImageRepository ballotImageRepository;

    @Autowired
    private VoteOpportunityRepository voteOpportunityRepository;

    /**
     * Verified against test-harness/desktop_pipeline/blcounter_results/
     * counter_results.db — a real blCounter run against this exact same
     * ballot/image corpus. ground_truth.json's raw per-indicator "VOTED"
     * count for these four scenarios sums to 21 (8 + 1 + 1 + 11), but that
     * doesn't distinguish a plurality contest's over-max marks from a
     * normal vote — the engine correctly reclassifies those as OVERVOTED,
     * so the real split is 12 VOTED + 9 OVERVOTED (all 9 from
     * overvote_plurality) = 21 marked total. This is the precise
     * regression check that would have caught the earlier 300 DPI fixture
     * silently flagging all four ballots for review instead of counting
     * them: processed()==4 alone doesn't distinguish "counted" from
     * "flagged," but this does.
     */
    private static final long EXPECTED_VOTED = 12;
    private static final long EXPECTED_OVERVOTED = 9;

    @Test
    void fullScanProducesResultsReport() throws Exception {
        URL resource = getClass().getClassLoader().getResource("test-images/ballot_1_1_1_1_1_1.yaml");
        assertThat(resource).as("test-images resource on classpath").isNotNull();
        File testImagesDir = new File(resource.getFile()).getParentFile();

        // dpi=150 matches these fixture images' actual resolution
        // (1275x2100px for an 8.5x11in ballot) — CornerDetectionService
        // uses this value directly to convert inch-based geometry (mark
        // sizes, gaps, tolerances) into pixel search windows, it does not
        // infer it from the image itself, so passing a value that doesn't
        // match the real image only survives by luck (within tolerance
        // margins), not correctness. See the class-level Javadoc.
        countingService.startNewSession(
            testImagesDir.getAbsolutePath(), testImagesDir.getAbsolutePath(),
            128, 8.0, 150, false, "", 8.5);

        countingService.startScan("test");

        long deadline = System.currentTimeMillis() + 60_000;
        while (countingService.getSession().scanning && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        assertThat(countingService.getSession().scanning).as("scan finished within 60s").isFalse();

        ScanSession session = countingService.getSession();
        assertThat(session.scanError).isNull();
        assertThat(session.processed()).isEqualTo(4);
        assertThat(session.reviewRequired)
            .as("none of the four should need manual review — a non-empty list here usually means "
                + "the fixture images don't match counter.dpi, not a real counting failure")
            .isEmpty();

        countingService.finish("test");

        List<BallotImage> ballots = ballotImageRepository.findAll();
        assertThat(ballots).as("all four ballots actually counted (inserted), not just attempted").hasSize(4);

        List<VoteOpportunity> votes = ballots.stream()
            .flatMap(b -> voteOpportunityRepository.findByBallotImage_Id(b.getId()).stream())
            .toList();
        assertThat(votes.stream().filter(vo -> vo.getVoteStatus() == VoteOpportunity.VoteStatus.VOTED).count())
            .as("VOTED indicators across all four ballots, vs. the verified blCounter run")
            .isEqualTo(EXPECTED_VOTED);
        assertThat(votes.stream().filter(vo -> vo.getVoteStatus() == VoteOpportunity.VoteStatus.OVERVOTED).count())
            .as("OVERVOTED indicators across all four ballots, vs. the verified blCounter run")
            .isEqualTo(EXPECTED_OVERVOTED);

        File resultsReport = new File(countingService.getReportOutputDir(), "results_report.html");
        assertThat(resultsReport).exists();
    }
}
