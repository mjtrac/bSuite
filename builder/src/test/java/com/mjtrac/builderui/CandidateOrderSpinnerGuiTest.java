/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3.
 */
package com.mjtrac.builderui;

import com.mjtrac.ballot.model.Candidate;
import com.mjtrac.ballot.model.Contest;
import com.mjtrac.ballot.model.Election;
import com.mjtrac.ballot.model.Jurisdiction;
import com.mjtrac.ballot.repository.ContestRepository;
import com.mjtrac.ballot.repository.ElectionRepository;
import com.mjtrac.ballot.repository.JurisdictionRepository;
import org.assertj.swing.data.TableCell;
import org.junit.jupiter.api.Test;

import javax.swing.JSpinner;
import javax.swing.JTable;
import java.awt.event.KeyEvent;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for a real bug found by hand after the Order column
 * switched from free-text entry to a JSpinner: candidates created through
 * bBuilder's REST API (e.g. test-harness/build_election.py) never set
 * displayOrder at all, leaving it at Java's field default of 0 — confirmed
 * directly against a real database (every "County Executive" candidate had
 * display_order=0). ContestCandidatesDialog's OrderSpinnerCellEditor used a
 * SpinnerNumberModel with a minimum of 1, and SpinnerNumberModel.setValue()
 * throws IllegalArgumentException for anything outside the model's own
 * bounds — so opening the editor on any such candidate threw immediately,
 * leaving the cell editor in a broken, unresponsive state: clicking the
 * spin arrows did nothing, and typing into the field produced a system
 * beep (a JFormattedTextField's standard reaction when the editor it's
 * bound to is in an invalid state) instead of accepting input.
 */
class CandidateOrderSpinnerGuiTest extends AbstractBuilderGuiTest {

    @Test
    void editingAnExistingCandidateWithUnsetDisplayOrderWorks() {
        JurisdictionRepository jurisdictionRepo = bean(JurisdictionRepository.class);
        ElectionRepository electionRepo = bean(ElectionRepository.class);
        ContestRepository contestRepo = bean(ContestRepository.class);

        Jurisdiction jurisdiction = new Jurisdiction();
        jurisdiction.setName("Test County");
        jurisdiction = jurisdictionRepo.save(jurisdiction);

        Election election = new Election();
        election.setJurisdiction(jurisdiction);
        election.setName("Test Election");
        election.setElectionType(Election.ElectionType.GENERAL);
        electionRepo.save(election);

        Contest contest = new Contest();
        contest.setElection(election);
        contest.setTitle("County Executive");
        contest.setMaxChoices(1);
        contest.setVotingMethod(Contest.VotingMethod.PLURALITY);
        Candidate existing = new Candidate();
        existing.setName("Victoria Chang");
        existing.setDisplayOrder(0); // never explicitly set — the real-world condition that broke this
        existing.setContest(contest);
        contest.setCandidates(List.of(existing));
        contestRepo.save(contest);

        robotAction(() -> {
            window.menuItem("ContestsMenuItem").click();
            window.table("ContestsTable").cell(TableCell.row(0).column(2)).doubleClick();
            window.button("manageCandidatesButton").requireEnabled().click();
            window.dialog("candidatesDialog").requireVisible();

            window.dialog("candidatesDialog").table("candidatesTable")
                .cell(TableCell.row(0).column(3)).click();
        });

        JTable table = window.dialog("candidatesDialog").table("candidatesTable").target();
        assertThat(table.isEditing())
            .as("the Order cell should have entered edit mode instead of the editor construction throwing")
            .isTrue();
        Object editor = table.getEditorComponent();
        assertThat(editor).isInstanceOf(JSpinner.class);
        assertThat(((JSpinner) editor).getValue())
            .as("the spinner should show the candidate's real (0) displayOrder, not silently substitute something else")
            .isEqualTo(0);

        // Spin it via the keyboard (Up arrow), then save, then confirm it
        // actually persisted — not just that the editor opened.
        robotAction(() -> window.robot().pressAndReleaseKey(KeyEvent.VK_UP));
        assertThat(((JSpinner) editor).getValue()).isEqualTo(1);

        robotAction(() -> {
            window.robot().pressAndReleaseKey(KeyEvent.VK_ENTER);
            window.dialog("candidatesDialog").button("saveContinueButton").click();
        });

        Contest reloaded = contestRepo.findAll().get(0);
        assertThat(reloaded.getCandidates()).hasSize(1);
        assertThat(reloaded.getCandidates().get(0).getDisplayOrder()).isEqualTo(1);
    }
}
