/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Counter — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.counter.fx;

import com.mjtrac.counter.CounterFxApplication;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Spring-managed indirection to the JavaFX Application instance — same
 * pattern as blScanner/blBuilder's Navigator. bind() is public because
 * CounterFxApplication lives in the parent package com.mjtrac.counter, not
 * alongside Navigator in .fx (see CounterFxApplication's class comment).
 */
@Component
public class Navigator {

    private CounterFxApplication app;

    public void bind(CounterFxApplication app) {
        this.app = app;
    }

    public void showLogin() throws IOException { app.showLogin(); }
    public void showShell() throws IOException { app.showShell(); }
    public void showInContent(String fxmlPath) throws IOException { app.showInContent(fxmlPath); }
}
