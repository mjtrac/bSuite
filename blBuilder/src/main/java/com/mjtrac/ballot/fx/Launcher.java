/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Ballot System — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.ballot.fx;

import com.mjtrac.ballot.BBuilderFxApplication;

import java.io.InputStream;
import java.util.Properties;

/**
 * Plain (non-Application) entry point for the repackaged fat jar. Running
 * `java -jar` with a javafx.application.Application subclass as Main-Class
 * fails classpath launches ("JavaFX runtime components are missing") outside
 * jlink/jpackage — this indirection sidesteps that.
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
        BBuilderFxApplication.main(args);
    }

    private static String readName() {
        return readProperty("name", "blBuilder");
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
