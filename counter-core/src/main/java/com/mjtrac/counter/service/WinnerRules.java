/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 */
package com.mjtrac.counter.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure winner-determination rules, independent of I/O, so they can be unit
 * tested directly without booting a Spring context or writing files.
 *
 * Two modes, chosen by maxChoices (the contest's "vote for N" limit):
 *   - maxChoices == 1: the single leading candidate/choice wins, but only if
 *     their vote share STRICTLY exceeds percentToWin — "50% plus one", not
 *     "50% or more" (a candidate at exactly the threshold has not won). If
 *     nobody clears it, there is no winner; a real election would call a
 *     runoff, this system just reports that state.
 *   - maxChoices > 1 ("vote for N" contests, e.g. city council races with
 *     multiple open seats): the top N candidates by raw vote count win — a
 *     percentage threshold doesn't map onto "vote for N" races the way it
 *     does for single-winner ones (requiring every one of N winners to
 *     individually clear 50% of all votes cast isn't how plurality
 *     multi-winner races work), so percentToWin is not applied there.
 *
 * Not applied to RANKED_CHOICE contests — those have their own elimination-
 * round winner determination in RcvTabulationService.
 */
public final class WinnerRules {

    private WinnerRules() {}

    /** One candidate/choice's standing within a contest, already sorted by descending vote count. */
    public record Ranked(String name, long votes, boolean winner) {}

    /**
     * Ranks every candidate by vote count (descending — winners at the top,
     * per the report's sort requirement) and marks winners per the rules
     * above. Ties at the maxChoices boundary are broken arbitrarily (by
     * whatever order Collectors produced) — full co-winner/tie handling
     * isn't implemented.
     */
    public static List<Ranked> rank(Map<String, Long> votesByCandidate, int maxChoices, double percentToWin) {
        List<Map.Entry<String, Long>> sorted = votesByCandidate.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .toList();
        if (sorted.isEmpty()) return List.of();

        Set<String> winners;
        if (maxChoices <= 1) {
            long total = votesByCandidate.values().stream().mapToLong(Long::longValue).sum();
            Map.Entry<String, Long> top = sorted.get(0);
            boolean clears = total > 0 && top.getValue() > (percentToWin / 100.0) * total;
            winners = clears ? Set.of(top.getKey()) : Set.of();
        } else {
            winners = sorted.stream().limit(maxChoices).map(Map.Entry::getKey).collect(Collectors.toSet());
        }

        List<Ranked> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : sorted) {
            out.add(new Ranked(e.getKey(), e.getValue(), winners.contains(e.getKey())));
        }
        return out;
    }
}
