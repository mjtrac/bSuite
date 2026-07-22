/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3.
 */
package com.mjtrac.builderui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * pbss's signature element: a row of small dots evoking a paper ballot's
 * perforated tear-off stub — grounded in the actual subject rather than a
 * generic rule or gradient. Used once, consistently, as the divider under
 * every screen's title (see PbssTheme.titleBlock()) — the one place this
 * visual identity spends its "boldness," rather than decorating multiple
 * things.
 *
 * Tinted with the destination screen's own dashboard card color when one
 * applies (see PbssTheme.accentColorFor()), so a screen reached from the
 * dashboard carries that card's color forward instead of resetting to a
 * neutral rule the instant you navigate — a visual thread back to how you
 * got there. Screens with no dashboard card (Home itself, Languages,
 * Jurisdictions, Users) fall back to the plain neutral color.
 */
final class PerforationDivider extends JComponent {

    private static final int DOT_DIAMETER = 4;
    private static final int GAP = 6;

    private final Color dotColor;

    /** @param accent this screen's dashboard card color, or null to use the plain neutral rule color. */
    PerforationDivider(Color accent) {
        setOpaque(false);
        this.dotColor = accent != null ? saturate(accent) : PbssTheme.RULE;
    }

    /**
     * Dashboard card colors are very pale pastels (near-white fills meant
     * to sit behind dark text) — used as-is or just .darker()'d, they read
     * as barely-distinguishable variants of the same neutral gray as every
     * other screen's dots, defeating the point of carrying the color
     * through. Keeping the same hue but pushing saturation/brightness into
     * a mid range makes each screen's dots read as a clearly distinct,
     * recognizable color while staying in the same pastel's hue family.
     */
    private static Color saturate(Color c) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        return Color.getHSBColor(hsb[0], 0.45f, 0.65f);
    }

    @Override public Dimension getPreferredSize() { return new Dimension(40, DOT_DIAMETER); }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, DOT_DIAMETER); }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(dotColor);
        int y = (getHeight() - DOT_DIAMETER) / 2;
        for (int x = 0; x + DOT_DIAMETER <= getWidth(); x += DOT_DIAMETER + GAP) {
            g2.fillOval(x, y, DOT_DIAMETER, DOT_DIAMETER);
        }
        g2.dispose();
    }
}
