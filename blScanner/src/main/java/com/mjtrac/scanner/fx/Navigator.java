/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Scanner Driver — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.scanner.fx;

import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Spring-managed indirection to the JavaFX Application instance, so FXML
 * controllers can request scene changes via ordinary constructor injection
 * instead of depending on the (separately-instantiated) Application bean.
 */
@Component
public class Navigator {

    private ScannerFxApplication app;

    void bind(ScannerFxApplication app) {
        this.app = app;
    }

    public void showLogin() throws IOException { app.showLogin(); }
    public void showMain() throws IOException { app.showMain(); }
    public void showConfig() throws IOException { app.showConfig(); }
    public void showUsers() throws IOException { app.showUsers(); }
}
