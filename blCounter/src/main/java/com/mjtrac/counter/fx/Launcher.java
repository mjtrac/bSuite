/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Counter — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.counter.fx;

import com.mjtrac.counter.CounterFxApplication;

import java.io.InputStream;
import java.util.Properties;

/**
 * Plain (non-Application) entry point for the repackaged fat jar — see
 * blScanner/blBuilder's Launcher for why this indirection is needed.
 *
 * Also handles --version: jpackage-built native launchers forward CLI args
 * to main() unchanged, so this must run before Application.launch() — the
 * GUI never starts if --version is passed.
 */
public class Launcher {
    public static void main(String[] args) {
        if (args.length > 0 && ("--version".equals(args[0]) || "-version".equals(args[0]))) {
            System.out.println(readName() + " " + readVersion());
            return;
        }
        CounterFxApplication.main(args);
    }

    private static String readName() {
        return readProperty("name", "blCounter");
    }

    private static String readVersion() {
        return readProperty("version", "unknown");
    }

    private static String readProperty(String key, String fallback) {
        try (InputStream in = Launcher.class.getResourceAsStream("/version.properties")) {
            if (in == null) return fallback;
            Properties props = new Properties();
            props.load(in);
            return props.getProperty(key, fallback);
        } catch (Exception e) {
            return fallback;
        }
    }
}
