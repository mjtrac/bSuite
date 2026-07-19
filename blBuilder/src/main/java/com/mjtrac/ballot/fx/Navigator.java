/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Ballot System — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.ballot.fx;

import com.mjtrac.ballot.BBuilderFxApplication;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Spring-managed indirection to the JavaFX Application instance, so FXML
 * controllers can request scene/content changes via ordinary constructor
 * injection instead of depending on the (separately-instantiated) Application
 * bean — same pattern as blScanner's Navigator. bind() is public (rather than
 * package-private like blScanner's) because BBuilderFxApplication lives in the
 * parent package com.mjtrac.ballot, not alongside Navigator in .fx — see the
 * class comment on BBuilderFxApplication for why it had to move there.
 */
@Component
public class Navigator {

    private BBuilderFxApplication app;

    public void bind(BBuilderFxApplication app) {
        this.app = app;
    }

    public void showLogin() throws IOException { app.showLogin(); }
    public void showShell() throws IOException { app.showShell(); }

    /** Swaps the persistent shell's center content to the given FXML screen. */
    public void showInContent(String fxmlPath) throws IOException { app.showInContent(fxmlPath); }
}
