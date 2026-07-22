/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 */
package com.mjtrac.counter.service;

import com.mjtrac.counter.model.ScanSession;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real, end-to-end verification of a "vote for two" multi-winner contest —
 * the scenario named directly in the feature request ("For elections that
 * allow a user to vote for more than one, perhaps the candidates (plural)
 * with the most votes win... It would be good if the results report sorted
 * each individual contest by vote count so that winners were at the top").
 * Drives the actual production pipeline against a real ballot PDF+YAML
 * (built by builder/src/test/java's TopTwoBuilder: 5 candidates,
 * maxChoices=2) and 10 real, two-marks-each ballot images:
 *   Candidate 1: 7 marks, Candidate 2: 5, Candidate 3: 4, Candidate 4: 3,
 *   Candidate 5: 1 (20 marks total across 10 ballots).
 *
 * No candidate is anywhere near a majority of the 10 ballots — this proves
 * WinnerRules.rank() correctly ignores percentToWin entirely once
 * maxChoices > 1 and marks only the top 2 by raw count (Candidate 1,
 * Candidate 2) as winners, with Candidates 3-5 correctly NOT marked despite
 * some having double-digit-adjacent counts.
 */
@SpringBootTest(classes = TopTwoIntegrationTest.TestConfig.class,
                 webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TopTwoIntegrationTest {

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
    private ResultsQueryService resultsQuery;

    @Test
    void topTwoScanProducesExpectedMultiWinnerResult() throws Exception {
        URL resource = getClass().getClassLoader()
            .getResource("test-images/top_two_multiwinner/ballot_1_1_0_1_1_1.yaml");
        assertThat(resource).as("top_two_multiwinner fixtures on classpath").isNotNull();
        File imagesDir = new File(resource.getFile()).getParentFile();

        countingService.startNewSession(
            imagesDir.getAbsolutePath(), imagesDir.getAbsolutePath(),
            128, 8.0, 300, false, "", 8.5);
        countingService.startScan("toptwo-test");

        long deadline = System.currentTimeMillis() + 60_000;
        while (countingService.getSession().scanning && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        assertThat(countingService.getSession().scanning).as("scan finished within 60s").isFalse();

        ScanSession session = countingService.getSession();
        assertThat(session.scanError).isNull();
        assertThat(session.processed()).isEqualTo(10);
        assertThat(session.reviewRequired)
            .as("none of the 10 should need manual review")
            .isEmpty();

        countingService.finish("toptwo-test");

        Map<String, ResultsQueryService.ContestConfig> configs = resultsQuery.contestConfigByTitle();
        ResultsQueryService.ContestConfig cfg = configs.get("City Council — Vote For Two");
        assertThat(cfg).as("contest config carried through to DB").isNotNull();
        assertThat(cfg.maxVotes).as("maxChoices=2 survived generation -> scan -> persistence").isEqualTo(2);

        List<ResultsQueryService.VoteRow> rows = resultsQuery.votesByContest();
        Map<String, Long> votesByCandidate = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            String name = "Candidate " + i;
            long votes = rows.stream()
                .filter(r -> name.equals(r.getCandidate()))
                .mapToLong(ResultsQueryService.VoteRow::getVoted).sum();
            votesByCandidate.put(name, votes);
        }
        assertThat(votesByCandidate).containsExactlyInAnyOrderEntriesOf(Map.of(
            "Candidate 1", 7L, "Candidate 2", 5L, "Candidate 3", 4L,
            "Candidate 4", 3L, "Candidate 5", 1L));

        List<WinnerRules.Ranked> ranked = WinnerRules.rank(votesByCandidate, cfg.maxVotes, cfg.percentToWin);
        assertThat(ranked.stream().filter(WinnerRules.Ranked::winner).map(WinnerRules.Ranked::name).toList())
            .as("exactly the top 2 by raw count win, regardless of majority")
            .containsExactlyInAnyOrder("Candidate 1", "Candidate 2");
        assertThat(ranked.stream().filter(r -> !r.winner()).map(WinnerRules.Ranked::name).toList())
            .containsExactlyInAnyOrder("Candidate 3", "Candidate 4", "Candidate 5");

        File resultsReport = new File(countingService.getReportOutputDir(), "results_report.html");
        assertThat(resultsReport).as("results_report.html written").exists();
        String html = Files.readString(resultsReport.toPath());
        assertThat(html).as("contest heading present in report").contains("City Council");
        assertThat(html)
            .as("this contest's percentToWin is the default 50%% (never set otherwise) — "
                + "no non-default-threshold banner <div> should be rendered (the CSS class "
                + "definition is always present in the stylesheet, so check for actual usage)")
            .doesNotContain("<div class=\"threshold-banner\">");
        assertThat(html)
            .as("Candidate 1 and Candidate 2 marked as winners")
            .contains("Candidate 1 &#10003; WINNER")
            .contains("Candidate 2 &#10003; WINNER");
        assertThat(html)
            .as("Candidates 3-5 not marked as winners")
            .doesNotContain("Candidate 3 &#10003; WINNER")
            .doesNotContain("Candidate 4 &#10003; WINNER")
            .doesNotContain("Candidate 5 &#10003; WINNER");
    }
}
