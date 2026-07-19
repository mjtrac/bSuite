/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Ballot System — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.ballot.fx;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Landing screen shown in the shell after login — mirrors templates/dashboard.html's card grid. */
@Component
public class DashboardController {

    private final AuthContext authContext;
    private final Navigator navigator;

    @FXML private Label welcomeLabel;
    @FXML private VBox adminSection;
    @FXML private VBox setupSection;
    @FXML private VBox printSection;

    public DashboardController(AuthContext authContext, Navigator navigator) {
        this.authContext = authContext;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        if (authContext.getCurrentUser() != null) {
            welcomeLabel.setText("Welcome, " + authContext.getCurrentUser().getUsername());
        }
        boolean admin = authContext.isAdmin();
        adminSection.setVisible(admin);
        adminSection.setManaged(admin);

        boolean dataEntry = authContext.isDataEntry();
        setupSection.setVisible(dataEntry);
        setupSection.setManaged(dataEntry);

        boolean printer = authContext.isPrinter();
        printSection.setVisible(printer);
        printSection.setManaged(printer);
    }

    @FXML private void showJurisdictions()      { go("/fxml/jurisdictions.fxml"); }
    @FXML private void showAdmin()              { go("/fxml/admin.fxml"); }
    @FXML private void showElections()          { go("/fxml/elections.fxml"); }
    @FXML private void showRegions()            { go("/fxml/regions.fxml"); }
    @FXML private void showParties()            { go("/fxml/parties.fxml"); }
    @FXML private void showBallotTypes()        { go("/fxml/ballot-types.fxml"); }
    @FXML private void showContests()           { go("/fxml/contests.fxml"); }
    @FXML private void showLanguages()          { go("/fxml/languages.fxml"); }
    @FXML private void showBallotTemplates()    { go("/fxml/ballot-templates.fxml"); }
    @FXML private void showBallotCombinations() { go("/fxml/ballot-combinations.fxml"); }
    @FXML private void showPrint()              { go("/fxml/print.fxml"); }

    private void go(String fxmlPath) {
        try {
            navigator.showInContent(fxmlPath);
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Could not open screen: " + e.getMessage()).showAndWait();
        }
    }
}
