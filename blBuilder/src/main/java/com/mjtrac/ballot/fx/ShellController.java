/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Ballot System — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.ballot.fx;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Controller for the persistent shell (shell.fxml): a nav sidebar plus a
 * center content pane whose contents Navigator.showInContent() swaps on
 * every screen change. Built as a persistent shell rather than blScanner's
 * full-scene-swap because bBuilder has 15+ screens — swapping the whole
 * Scene that many times would flicker/resize/lose window position.
 */
@Component
public class ShellController {

    private final AuthContext authContext;
    private final Navigator navigator;

    @FXML private Label titleLabel;
    @FXML private Label breadcrumbLabel;
    @FXML private Label signedInAsLabel;
    @FXML private VBox adminSection;
    @FXML private VBox dataEntrySection;
    @FXML private VBox printSection;
    @FXML private StackPane contentArea;

    public ShellController(AuthContext authContext, Navigator navigator) {
        this.authContext = authContext;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        boolean admin = authContext.isAdmin();
        adminSection.setVisible(admin);
        adminSection.setManaged(admin);

        boolean dataEntry = authContext.isDataEntry();
        dataEntrySection.setVisible(dataEntry);
        dataEntrySection.setManaged(dataEntry);

        boolean printer = authContext.isPrinter();
        printSection.setVisible(printer);
        printSection.setManaged(printer);

        if (authContext.getCurrentUser() != null) {
            signedInAsLabel.setText("Signed in as " + authContext.getCurrentUser().getUsername());
        }
    }

    public void setContent(Node node) {
        contentArea.getChildren().setAll(node);
    }

    /** Updates the top-bar breadcrumb to reflect the currently-shown screen. */
    public void setBreadcrumb(String text) {
        breadcrumbLabel.setText(text);
    }

    @FXML private void showDashboard()          { go("/fxml/dashboard.fxml", "Dashboard"); }
    @FXML private void showJurisdictions()      { go("/fxml/jurisdictions.fxml", "Jurisdictions"); }
    @FXML private void showAdmin()              { go("/fxml/admin.fxml", "Users & Audit Log"); }
    @FXML private void showElections()          { go("/fxml/elections.fxml", "Elections"); }
    @FXML private void showRegions()            { go("/fxml/regions.fxml", "Regions"); }
    @FXML private void showParties()            { go("/fxml/parties.fxml", "Parties"); }
    @FXML private void showBallotTypes()        { go("/fxml/ballot-types.fxml", "Ballot Types"); }
    @FXML private void showContests()           { go("/fxml/contests.fxml", "Contests"); }
    @FXML private void showLanguages()          { go("/fxml/languages.fxml", "Languages"); }
    @FXML private void showBallotTemplates()    { go("/fxml/ballot-templates.fxml", "Ballot Templates"); }
    @FXML private void showBallotCombinations() { go("/fxml/ballot-combinations.fxml", "Ballot Combinations"); }
    @FXML private void showPrint()              { go("/fxml/print.fxml", "Print Ballots"); }

    @FXML
    private void handleSignOut() {
        authContext.clear();
        try {
            navigator.showLogin();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Could not sign out: " + e.getMessage()).showAndWait();
        }
    }

    private void go(String fxmlPath, String breadcrumb) {
        try {
            navigator.showInContent(fxmlPath);
            setBreadcrumb(breadcrumb);
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Could not open " + breadcrumb + ": " + e.getMessage()).showAndWait();
        }
    }
}
