/*
 * Copyright (C) 2026 Mitch Trachtenberg
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.mjtrac.ballot.service;

import com.mjtrac.ballot.model.Candidate;
import com.mjtrac.ballot.model.Contest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Convenience shared by every builder flavor (builder, blBuilder, bBuilder):
 * a MEASURE contest is a yes/no question in the overwhelming majority of
 * real elections, so the first time one is saved with no candidates/options
 * of its own yet, it's auto-populated with "Yes"/"No" rather than starting
 * every measure from an empty Candidates screen.
 *
 * Deliberately narrow: {@link #needsMeasureDefaults} only ever returns true
 * when the contest currently has zero candidates, so this never overwrites
 * options a user has already added, renamed, or removed.
 */
@Service
public class ContestDefaultsService {

    /** True only for a MEASURE contest that has no candidates/options yet. */
    public boolean needsMeasureDefaults(Contest contest) {
        return contest.getVotingMethod() == Contest.VotingMethod.MEASURE
            && (contest.getCandidates() == null || contest.getCandidates().isEmpty());
    }

    /**
     * Builds standard Yes/No options, each already linked to {@code contest}
     * via {@link Candidate#setContest}. Callers typically pass these to
     * {@code contest.setCandidates(...)} before saving, so JPA's cascade
     * persists the contest and both candidates together in one call.
     */
    public List<Candidate> yesNoCandidates(Contest contest) {
        Candidate yes = new Candidate();
        yes.setName("Yes");
        yes.setDisplayOrder(1);
        yes.setContest(contest);

        Candidate no = new Candidate();
        no.setName("No");
        no.setDisplayOrder(2);
        no.setContest(contest);

        return List.of(yes, no);
    }
}
