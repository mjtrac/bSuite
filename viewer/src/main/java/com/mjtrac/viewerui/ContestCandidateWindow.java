/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 */
package com.mjtrac.viewerui;

import com.mjtrac.viewer.service.BallotViewService.IndicatorBox;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Companion window listing every contest on the currently-viewed ballot,
 * collapsed to contest names — clicking one expands it to that contest's
 * candidates, each with a color swatch matching its overlay-box status
 * (green VOTED, amber OVERVOTED, blue UNMARKED). The Swing equivalent of
 * bCounter's embedded web Viewer's sidebar (see view.html's `.sidebar`),
 * but a separate toggleable window instead of a fixed side panel — see
 * MainFrame's View menu.
 *
 * Clicking a candidate here activates the matching box on
 * {@link OverlayImagePanel} (via {@link #setOnCandidateSelected}); clicking
 * a box on the image selects the matching candidate here (via
 * {@link #selectBox}) — same bidirectional highlighting the web sidebar
 * does, just without a shared DOM to coordinate through.
 */
@Component
class ContestCandidateWindow extends JFrame {

    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("Ballot");
    private final DefaultTreeModel model = new DefaultTreeModel(root);
    private final JTree tree = new JTree(model);
    private final JLabel emptyLabel = new JLabel("No ballot selected.", SwingConstants.CENTER);

    /**
     * Contest names the user has expanded — kept by name, not TreePath,
     * since update() discards and rebuilds every node each time a new
     * ballot loads. Re-applied after each rebuild so navigating between
     * ballots that share the same contests (the normal case — one
     * election's ballots) doesn't collapse everything back down.
     */
    private final Set<String> expandedContests = new HashSet<>();

    private Consumer<IndicatorBox> onCandidateSelected = b -> {};

    ContestCandidateWindow() {
        super("Contests & Candidates");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(400, 620);

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new CandidateCellRenderer());
        tree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override public void treeExpanded(TreeExpansionEvent e) {
                contestNameOf(e.getPath()).ifPresent(expandedContests::add);
            }
            @Override public void treeCollapsed(TreeExpansionEvent e) {
                contestNameOf(e.getPath()).ifPresent(expandedContests::remove);
            }
        });
        tree.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                Object userObject = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
                if (userObject instanceof IndicatorBox box) {
                    onCandidateSelected.accept(box);
                }
            }
        });

        JPanel content = new JPanel(new BorderLayout());
        content.add(new JScrollPane(tree), BorderLayout.CENTER);
        emptyLabel.setForeground(Color.GRAY);
        setContentPane(content);
        showEmpty();
    }

    void setOnCandidateSelected(Consumer<IndicatorBox> onCandidateSelected) {
        this.onCandidateSelected = onCandidateSelected;
    }

    /** Rebuilds the contest/candidate tree for a newly-loaded ballot, all contests collapsed. */
    void update(List<IndicatorBox> boxes) {
        root.removeAllChildren();
        if (boxes == null || boxes.isEmpty()) {
            model.reload();
            showEmpty();
            return;
        }
        Map<String, List<IndicatorBox>> byContest = new LinkedHashMap<>();
        for (IndicatorBox box : boxes) {
            byContest.computeIfAbsent(box.contest, k -> new java.util.ArrayList<>()).add(box);
        }
        for (Map.Entry<String, List<IndicatorBox>> entry : byContest.entrySet()) {
            DefaultMutableTreeNode contestNode = new DefaultMutableTreeNode(entry.getKey());
            for (IndicatorBox box : entry.getValue()) {
                contestNode.add(new DefaultMutableTreeNode(box));
            }
            root.add(contestNode);
        }
        model.reload();
        setContentPane(new JScrollPane(tree));

        // Re-expand whichever contests were open before this rebuild —
        // model.reload() collapses everything without notifying
        // TreeExpansionListener, so expandedContests still reflects the
        // pre-reload state here.
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode contestNode = (DefaultMutableTreeNode) root.getChildAt(i);
            if (expandedContests.contains(contestNode.getUserObject())) {
                tree.expandPath(new TreePath(contestNode.getPath()));
            }
        }

        revalidate();
        repaint();
    }

    /** The contest name for a contest-level tree path, empty for the root or a candidate leaf. */
    private static java.util.Optional<String> contestNameOf(TreePath path) {
        Object userObject = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        return userObject instanceof String name ? java.util.Optional.of(name) : java.util.Optional.empty();
    }

    /** Selects (and reveals) the tree row for the given box, e.g. after it was activated by clicking the image. */
    void selectBox(IndicatorBox box) {
        if (box == null) {
            tree.clearSelection();
            return;
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode contestNode = (DefaultMutableTreeNode) root.getChildAt(i);
            for (int j = 0; j < contestNode.getChildCount(); j++) {
                DefaultMutableTreeNode candidateNode = (DefaultMutableTreeNode) contestNode.getChildAt(j);
                if (candidateNode.getUserObject() instanceof IndicatorBox b && b.id == box.id) {
                    TreePath path = new TreePath(candidateNode.getPath());
                    tree.expandPath(path.getParentPath());
                    tree.setSelectionPath(path);
                    tree.scrollPathToVisible(path);
                    return;
                }
            }
        }
    }

    void toggleVisible() {
        setVisible(!isVisible());
    }

    private void showEmpty() {
        setContentPane(emptyLabel);
        revalidate();
        repaint();
    }

    private static class CandidateCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
            if (userObject instanceof IndicatorBox box) {
                setIcon(new SwatchIcon(Color.decode(box.color)));
                setText(box.label + "  —  " + box.statusLabel.toLowerCase());
            } else {
                setIcon(null);
                setFont(getFont().deriveFont(Font.BOLD));
            }
            return this;
        }
    }

    /** A small filled square, the Swing equivalent of the web sidebar's `.cand-swatch` div. */
    private static class SwatchIcon implements Icon {
        private static final int SIZE = 11;
        private final Color color;

        SwatchIcon(Color color) { this.color = color; }

        @Override public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRect(x, y + 3, SIZE, SIZE);
        }

        @Override public int getIconWidth()  { return SIZE + 4; }
        @Override public int getIconHeight() { return SIZE + 6; }
    }
}
