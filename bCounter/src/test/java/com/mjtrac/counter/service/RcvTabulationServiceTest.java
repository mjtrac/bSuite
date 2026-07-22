/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 */
package com.mjtrac.counter.service;

import com.mjtrac.counter.service.RcvTabulationService.RcvResult;
import com.mjtrac.counter.service.RcvTabulationService.RcvRound;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for RcvTabulationService.
 *
 * RcvTabulationService.tabulateAndWrite() reads ranked votes from the
 * counter_results.db and writes an HTML report. Its results depend on
 * what's in the database at test time.
 *
 * These tests verify:
 *   1. tabulateAndWrite() completes without throwing even with an empty DB.
 *   2. Any RcvResult objects returned have valid outcome values.
 *   3. RcvResult structure has non-null required fields.
 *   4. The IRV algorithm is separately covered by rcv_tabulate.py in the
 *      test harness, which exercises the Python implementation against a
 *      known election fixture.
 *
 * Note: full IRV round-by-round correctness is best tested via the Python
 * test harness (rcv_tabulate.py) which runs against a real scan session.
 * The Java service's IRV logic is private; these tests exercise the public
 * API surface only.
 */
@SpringBootTest
@ActiveProfiles("sqlite")
@DisplayName("RcvTabulationService — public API")
class RcvTabulationServiceTest {

    @Autowired RcvTabulationService rcvService;

    @Test
    @DisplayName("tabulateAndWrite completes without throwing on empty database")
    void testTabulateEmptyDb() {
        // With no RANKED_CHOICE contests in DB, should return empty list silently
        assertThatCode(() -> {
            var results = rcvService.tabulateAndWrite(
                System.getProperty("java.io.tmpdir"));
            assertThat(results).isNotNull();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Any returned RcvResult has a valid outcome value")
    void testRcvResultOutcomeValues() {
        var results = rcvService.tabulateAndWrite(
            System.getProperty("java.io.tmpdir"));
        for (RcvResult r : results) {
            assertThat(r.outcome)
                .as("outcome for contest: " + r.contest)
                .isIn("winner", "tie", "no_majority");
        }
    }

    @Test
    @DisplayName("Any returned RcvResult has non-null rounds list")
    void testRcvResultHasRounds() {
        var results = rcvService.tabulateAndWrite(
            System.getProperty("java.io.tmpdir"));
        for (RcvResult r : results) {
            assertThat(r.rounds)
                .as("rounds for: " + r.contest)
                .isNotNull();
        }
    }

    @Test
    @DisplayName("Winner-outcome result has non-null winner field")
    void testWinnerResultHasWinner() {
        var results = rcvService.tabulateAndWrite(
            System.getProperty("java.io.tmpdir"));
        for (RcvResult r : results) {
            if ("winner".equals(r.outcome)) {
                assertThat(r.winner)
                    .as("winner field for: " + r.contest)
                    .isNotNull()
                    .isNotBlank();
            }
        }
    }

    /**
     * Five-candidate, ten-ballot IRV scenario, calling runIrv() directly
     * (package-private, no DB needed) rather than through the DB-driven
     * tabulateAndWrite() entry point above.
     *
     * Ballots (10 total):
     *   3x Candidate 1 first, Candidate 2 second
     *   2x Candidate 2 first, Candidate 1 second
     *   2x Candidate 3 first, Candidate 1 second
     *   2x Candidate 4 first, Candidate 1 second
     *   1x Candidate 5 first, Candidate 1 second
     *
     * Round 1: 3/2/2/2/1 (majority=6) — Candidate 5 alone at the minimum
     *          (1 vote) is eliminated; its ballot's second choice (Candidate 1)
     *          transfers.
     * Round 2: Candidate 1=4, Candidates 2/3/4 tied at 2 each (majority=6) —
     *          per RcvTabulationService's documented algorithm ("Eliminate
     *          candidate(s) with fewest rank-1 votes (parallel elimination
     *          for ties at the bottom)"), ALL THREE tied candidates are
     *          eliminated together in this round, not just one — this
     *          scenario was specifically chosen to exercise that bulk-tie
     *          elimination path, not the single-lowest-candidate case
     *          Round 1 already covers.
     * Round 3: Only Candidate 1 remains active (all 10 ballots' final
     *          surviving choice) — sole-candidate winner, no majority
     *          threshold check needed.
     */
    @Test
    @DisplayName("Five-candidate IRV: single elimination then bulk tie elimination, 3 rounds to a winner")
    void testFiveCandidateBulkTieElimination() {
        List<List<String>> ballots = new ArrayList<>();
        for (int i = 0; i < 3; i++) ballots.add(List.of("Candidate 1", "Candidate 2"));
        for (int i = 0; i < 2; i++) ballots.add(List.of("Candidate 2", "Candidate 1"));
        for (int i = 0; i < 2; i++) ballots.add(List.of("Candidate 3", "Candidate 1"));
        for (int i = 0; i < 2; i++) ballots.add(List.of("Candidate 4", "Candidate 1"));
        ballots.add(List.of("Candidate 5", "Candidate 1"));

        RcvResult result = rcvService.runIrv("Five-Candidate RCV Test", ballots);

        assertThat(result.outcome).isEqualTo("winner");
        assertThat(result.winner).isEqualTo("Candidate 1");
        assertThat(result.totalBallots).isEqualTo(10);
        assertThat(result.rounds).as("expected exactly 3 rounds").hasSize(3);

        RcvRound r1 = result.rounds.get(0);
        assertThat(r1.counts).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
            "Candidate 1", 3, "Candidate 2", 2, "Candidate 3", 2,
            "Candidate 4", 2, "Candidate 5", 1));
        assertThat(r1.eliminated).containsExactly("Candidate 5");

        RcvRound r2 = result.rounds.get(1);
        assertThat(r2.counts).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
            "Candidate 1", 4, "Candidate 2", 2, "Candidate 3", 2, "Candidate 4", 2));
        assertThat(r2.eliminated)
            .as("all three tied-at-minimum candidates eliminated together, not just one")
            .containsExactlyInAnyOrder("Candidate 2", "Candidate 3", "Candidate 4");

        RcvRound r3 = result.rounds.get(2);
        assertThat(r3.counts).containsExactlyInAnyOrderEntriesOf(
            java.util.Map.of("Candidate 1", 10));
        assertThat(r3.winner).isEqualTo("Candidate 1");
    }
}
