/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 */
package com.mjtrac.counter.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the "percent required to win" feature end to end at the pure-logic
 * level: default 50% simple majority, a 60% supermajority (the tax-measure
 * scenario the feature was specifically built for), the strict "plus one"
 * semantics (exactly at the threshold is NOT a win), "vote for N" multi-
 * winner contests (no percentage applied), and the always-sort-by-count
 * requirement (winners at the top of the report).
 */
class WinnerRulesTest {

    private static Map<String, Long> votes(Object... nameThenCount) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (int i = 0; i < nameThenCount.length; i += 2) {
            m.put((String) nameThenCount[i], ((Number) nameThenCount[i + 1]).longValue());
        }
        return m;
    }

    @Test
    void defaultFiftyPercentMajorityWins() {
        // 60 of 100 valid votes — comfortably over 50%.
        Map<String, Long> v = votes("Alice", 60L, "Bob", 40L);
        List<WinnerRules.Ranked> ranked = WinnerRules.rank(v, 1, 50.0);

        assertThat(ranked).hasSize(2);
        assertThat(ranked.get(0).name()).isEqualTo("Alice");
        assertThat(ranked.get(0).winner()).isTrue();
        assertThat(ranked.get(1).name()).isEqualTo("Bob");
        assertThat(ranked.get(1).winner()).isFalse();
    }

    @Test
    void exactlyFiftyPercentDoesNotWin() {
        // "50% plus one" means exactly 50% is NOT a win — a real tie, not a majority.
        Map<String, Long> v = votes("Alice", 50L, "Bob", 50L);
        List<WinnerRules.Ranked> ranked = WinnerRules.rank(v, 1, 50.0);

        assertThat(ranked).noneMatch(WinnerRules.Ranked::winner);
    }

    @Test
    void sixtyPercentSupermajority_exactlyAtThreshold_doesNotWin() {
        // The explicit tax-measure scenario: Yes needs MORE than 60%, not 60% flat.
        Map<String, Long> v = votes("Yes", 60L, "No", 40L);
        List<WinnerRules.Ranked> ranked = WinnerRules.rank(v, 1, 60.0);

        assertThat(ranked).noneMatch(WinnerRules.Ranked::winner);
    }

    @Test
    void sixtyPercentSupermajority_oneVoteOver_wins() {
        // 61 of 100 — one vote past the 60% line — should clear a 60%-plus-one threshold.
        Map<String, Long> v = votes("Yes", 61L, "No", 39L);
        List<WinnerRules.Ranked> ranked = WinnerRules.rank(v, 1, 60.0);

        assertThat(ranked.get(0).name()).isEqualTo("Yes");
        assertThat(ranked.get(0).winner()).isTrue();
    }

    @Test
    void sixtyPercentSupermajority_justUnderThreshold_doesNotWin() {
        // 59 of 100 — below 60% entirely, let alone 60%-plus-one.
        Map<String, Long> v = votes("Yes", 59L, "No", 41L);
        List<WinnerRules.Ranked> ranked = WinnerRules.rank(v, 1, 60.0);

        assertThat(ranked).noneMatch(WinnerRules.Ranked::winner);
    }

    @Test
    void voteForTwo_topTwoByCountWin_noPercentageApplied() {
        // "Vote for 2" city council race — winners are simply the top 2 vote-getters,
        // even though none of them individually clears 50% of all votes cast.
        Map<String, Long> v = votes("Alice", 40L, "Bob", 35L, "Carol", 30L, "Dave", 20L);
        List<WinnerRules.Ranked> ranked = WinnerRules.rank(v, 2, 50.0);

        assertThat(ranked.get(0).name()).isEqualTo("Alice");
        assertThat(ranked.get(0).winner()).isTrue();
        assertThat(ranked.get(1).name()).isEqualTo("Bob");
        assertThat(ranked.get(1).winner()).isTrue();
        assertThat(ranked.get(2).name()).isEqualTo("Carol");
        assertThat(ranked.get(2).winner()).isFalse();
        assertThat(ranked.get(3).name()).isEqualTo("Dave");
        assertThat(ranked.get(3).winner()).isFalse();
    }

    @Test
    void alwaysSortedDescendingByVoteCount_winnersAtTop() {
        // Insertion order is deliberately NOT sorted — Ranked's output order must be.
        Map<String, Long> v = votes("Carol", 5L, "Alice", 90L, "Bob", 5L);
        List<WinnerRules.Ranked> ranked = WinnerRules.rank(v, 1, 50.0);

        assertThat(ranked).extracting(WinnerRules.Ranked::name)
            .containsExactly("Alice", "Carol", "Bob");
    }

    @Test
    void noVotesCast_noWinner_noCrash() {
        List<WinnerRules.Ranked> ranked = WinnerRules.rank(Map.of(), 1, 50.0);
        assertThat(ranked).isEmpty();
    }

    @Test
    void singleCandidateWithVotes_wins() {
        // A single unopposed candidate/measure choice with any votes at all
        // still has to clear the threshold — 100% of 1 candidate's votes
        // trivially exceeds 50%.
        Map<String, Long> v = votes("Alice", 10L);
        List<WinnerRules.Ranked> ranked = WinnerRules.rank(v, 1, 50.0);

        assertThat(ranked.get(0).winner()).isTrue();
    }
}
