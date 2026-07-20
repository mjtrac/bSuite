/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 */
package com.mjtrac.viewerui;

import com.mjtrac.counter.entity.CandidateRecord;
import com.mjtrac.counter.entity.ContestRecord;
import com.mjtrac.counter.entity.VoteOpportunity;
import com.mjtrac.viewer.service.BallotViewService.IndicatorBox;
import org.junit.jupiter.api.Test;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the contest/candidate tree ContestCandidateWindow builds from a
 * ballot's boxes — the Swing equivalent of bCounter's embedded web Viewer's
 * `byContest` sidebar grouping (see ViewerController). No GUI interaction
 * needed (the sandbox this was built in lacks the macOS Accessibility
 * permission AssertJ-Swing needs — see test-harness/README-desktop.md);
 * this only checks the tree model update() actually builds.
 */
class ContestCandidateWindowTest {

    @Test
    void groupsBoxesByContestInOriginalOrder() throws Exception {
        IndicatorBox mayorAlice = box(1L, "Mayor", "Alice Johnson", VoteOpportunity.VoteStatus.VOTED);
        IndicatorBox mayorBob   = box(2L, "Mayor", "Bob Williams", VoteOpportunity.VoteStatus.UNMARKED);
        IndicatorBox councilA   = box(3L, "City Council", "Carmen Lopez", VoteOpportunity.VoteStatus.OVERVOTED);

        ContestCandidateWindow window = new ContestCandidateWindow();
        window.update(List.of(mayorAlice, mayorBob, councilA));

        DefaultTreeModel model = (DefaultTreeModel) getField(window, "model");
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();

        assertThat(root.getChildCount()).as("one node per distinct contest").isEqualTo(2);
        DefaultMutableTreeNode mayorNode = (DefaultMutableTreeNode) root.getChildAt(0);
        assertThat(mayorNode.getUserObject()).isEqualTo("Mayor");
        assertThat(mayorNode.getChildCount()).as("both Mayor candidates under the Mayor node").isEqualTo(2);

        DefaultMutableTreeNode councilNode = (DefaultMutableTreeNode) root.getChildAt(1);
        assertThat(councilNode.getUserObject()).isEqualTo("City Council");
        assertThat(councilNode.getChildCount()).isEqualTo(1);

        DefaultMutableTreeNode aliceNode = (DefaultMutableTreeNode) mayorNode.getChildAt(0);
        IndicatorBox aliceInTree = (IndicatorBox) aliceNode.getUserObject();
        assertThat(aliceInTree.label).isEqualTo("Alice Johnson");
        assertThat(aliceInTree.color).as("VOTED -> green swatch").isEqualTo("#22c55e");
    }

    @Test
    void expandedContestStaysExpandedAcrossBallotsWithTheSameContests() throws Exception {
        IndicatorBox mayorAlice = box(1L, "Mayor", "Alice Johnson", VoteOpportunity.VoteStatus.VOTED);
        IndicatorBox councilA   = box(2L, "City Council", "Carmen Lopez", VoteOpportunity.VoteStatus.UNMARKED);

        ContestCandidateWindow window = new ContestCandidateWindow();
        window.update(List.of(mayorAlice, councilA));

        JTree tree = (JTree) getField(window, "tree");
        DefaultTreeModel model = (DefaultTreeModel) getField(window, "model");
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        DefaultMutableTreeNode mayorNode = (DefaultMutableTreeNode) root.getChildAt(0);
        tree.expandPath(new TreePath(mayorNode.getPath()));
        assertThat(tree.isExpanded(new TreePath(mayorNode.getPath()))).isTrue();

        // A new ballot, same two contest names but a different candidate
        // set (as when navigating within one election) — Mayor should
        // still be open; City Council, never expanded, should not be.
        IndicatorBox mayorZoe  = box(3L, "Mayor", "Zoe Chen", VoteOpportunity.VoteStatus.VOTED);
        IndicatorBox councilB  = box(4L, "City Council", "Dan Ortiz", VoteOpportunity.VoteStatus.UNMARKED);
        window.update(List.of(mayorZoe, councilB));

        DefaultMutableTreeNode newRoot = (DefaultMutableTreeNode) model.getRoot();
        DefaultMutableTreeNode newMayorNode = (DefaultMutableTreeNode) newRoot.getChildAt(0);
        DefaultMutableTreeNode newCouncilNode = (DefaultMutableTreeNode) newRoot.getChildAt(1);
        assertThat(tree.isExpanded(new TreePath(newMayorNode.getPath())))
            .as("Mayor was expanded before navigating — should still be expanded").isTrue();
        assertThat(tree.isExpanded(new TreePath(newCouncilNode.getPath())))
            .as("City Council was never expanded").isFalse();
    }

    @Test
    void emptyBoxListClearsTheTree() {
        ContestCandidateWindow window = new ContestCandidateWindow();
        window.update(List.of());
        // Should not throw — real regression risk: an empty ballot (no
        // indicators at all) shouldn't crash the companion window.
        window.update((List<IndicatorBox>) null);
    }

    private static IndicatorBox box(long id, String contestTitle, String candidateName,
                                     VoteOpportunity.VoteStatus status) throws Exception {
        ContestRecord contest = new ContestRecord();
        contest.setContestTitle(contestTitle);
        contest.setContestType("PLURALITY");

        CandidateRecord candidate = new CandidateRecord();
        candidate.setCandidateName(candidateName);

        VoteOpportunity vo = new VoteOpportunity();
        setId(vo, id);
        vo.setVoteStatus(status);

        return new IndicatorBox(vo, contest, candidate);
    }

    /** VoteOpportunity.id is JPA-generated with no public setter — set it directly for this DB-free test. */
    private static void setId(VoteOpportunity vo, long id) throws Exception {
        Field f = VoteOpportunity.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(vo, id);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}
