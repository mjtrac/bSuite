/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3.
 */
package com.mjtrac.builderui;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;

/**
 * pbss's visual identity for the Swing desktop apps: paper/ink/civic-teal,
 * not generic OS chrome or a default Swing gray. Grounded in the actual
 * subject (paper ballots, official process) rather than a template color
 * scheme — deliberately avoids red/blue as primary brand colors given
 * what this software is for.
 *
 * install() must run before the Spring context is built, not after: the
 * context eagerly constructs MainFrame (and every child screen) as part of
 * bean initialization, so a look-and-feel set afterward has nothing left to
 * affect — UIManager.setLookAndFeel() doesn't retroactively re-skin
 * already-built components without an explicit
 * SwingUtilities.updateComponentTreeUI() pass.
 */
final class PbssTheme {

    private PbssTheme() {}

    static final Color PAPER      = new Color(0xFA, 0xF7, 0xF0);
    static final Color PAPER_DARK = new Color(0xF1, 0xEC, 0xDF);
    static final Color INK        = new Color(0x1C, 0x23, 0x21);
    static final Color TEAL       = new Color(0x0B, 0x5D, 0x5D);
    static final Color TEAL_DARK  = new Color(0x08, 0x47, 0x47);
    static final Color GOLD       = new Color(0xB0, 0x8D, 0x4F);
    static final Color RULE       = new Color(0xD8, 0xD2, 0xC4);

    /** Default per-card palette for HomePanel's step dashboard — overridable via dashboard.card.colors. */
    static final String DEFAULT_DASHBOARD_COLORS =
        "#FDF6E3,#FBEFDD,#F8E7D6,#F3E1DC,#EEE0E8,#E4E6EE,#DCEAE6,#CFE3DC";

    static void install() {
        FlatLightLaf.setup();

        // Base surfaces — paper, not stark white or default Swing gray.
        UIManager.put("Panel.background", PAPER);
        UIManager.put("OptionPane.background", PAPER);
        UIManager.put("control", PAPER);
        UIManager.put("@background", PAPER);
        UIManager.put("ScrollPane.background", PAPER);
        UIManager.put("Viewport.background", PAPER);
        UIManager.put("TextField.background", Color.WHITE);
        UIManager.put("TextArea.background", Color.WHITE);
        UIManager.put("Table.background", Color.WHITE);
        UIManager.put("Table.alternateRowColor", PAPER_DARK);

        // Text.
        UIManager.put("@foreground", INK);
        UIManager.put("Label.foreground", INK);
        UIManager.put("text", INK);

        // Accent — civic teal throughout, everywhere FlatLaf derives an accent from.
        UIManager.put("@accentColor", TEAL);
        UIManager.put("Component.accentColor", TEAL);
        UIManager.put("Component.focusColor", TEAL);
        UIManager.put("Button.default.background", TEAL);
        UIManager.put("Button.default.foreground", Color.WHITE);
        UIManager.put("Button.default.focusedBackground", TEAL_DARK);
        UIManager.put("Button.default.hoverBackground", TEAL_DARK);
        UIManager.put("MenuBar.background", TEAL);
        UIManager.put("MenuBar.foreground", Color.WHITE);
        UIManager.put("MenuBar.hoverBackground", TEAL_DARK);
        UIManager.put("MenuBar.selectionBackground", TEAL_DARK);
        UIManager.put("MenuBar.selectionForeground", Color.WHITE);
        UIManager.put("MenuItem.selectionBackground", TEAL);
        UIManager.put("Menu.selectionBackground", TEAL);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("Component.borderColor", RULE);
        UIManager.put("Component.disabledBorderColor", RULE);
        UIManager.put("TableHeader.background", PAPER_DARK);
        UIManager.put("TableHeader.hoverBackground", RULE);

        // Type — a clean, highly legible sans across the board (the display
        // serif is applied per-screen-title, not globally: most of this UI
        // is dense forms/tables, where the utility sans reads best small).
        Font ui = pickAvailable(13f, "Inter", "IBM Plex Sans", "Segoe UI", "Helvetica Neue");
        UIManager.put("defaultFont", ui);
    }

    /** Falls back through a preference list to whatever's actually installed, ending at the platform default. */
    private static Font pickAvailable(float size, String... families) {
        String[] installed = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        java.util.Set<String> available = java.util.Set.of(installed);
        for (String family : families) {
            if (available.contains(family)) return new Font(family, Font.PLAIN, 1).deriveFont(size);
        }
        return UIManager.getFont("defaultFont") != null
            ? UIManager.getFont("defaultFont").deriveFont(size)
            : new Font(Font.SANS_SERIF, Font.PLAIN, (int) size);
    }

    /** Parses a comma-separated list of #rrggbb values (e.g. from application.properties) into Colors, falling back to the default palette on any bad entry. */
    static Color[] parsePalette(String csv) {
        try {
            String[] parts = csv.split(",");
            Color[] colors = new Color[parts.length];
            for (int i = 0; i < parts.length; i++) {
                colors[i] = Color.decode(parts[i].trim());
            }
            return colors;
        } catch (Exception e) {
            String[] defaults = DEFAULT_DASHBOARD_COLORS.split(",");
            Color[] colors = new Color[defaults.length];
            for (int i = 0; i < defaults.length; i++) colors[i] = Color.decode(defaults[i].trim());
            return colors;
        }
    }
}
