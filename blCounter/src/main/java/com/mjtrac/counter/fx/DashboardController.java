/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Counter — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.counter.fx;

import com.mjtrac.counter.service.VoteTallyService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Landing screen shown in the shell after login — mirrors templates/index.html's summary. */
@Component
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final AuthContext authContext;
    private final Navigator navigator;
    private final VoteTallyService voteTally;

    @FXML private Label welcomeLabel;
    @FXML private Label resultsInfoLabel;

    public DashboardController(AuthContext authContext, Navigator navigator, VoteTallyService voteTally) {
        this.authContext = authContext;
        this.navigator = navigator;
        this.voteTally = voteTally;
    }

    @FXML
    private void initialize() {
        if (authContext.getCurrentUser() != null) {
            welcomeLabel.setText("Welcome, " + authContext.getCurrentUser().getUsername());
        }

        String outputDir = voteTally.getReportOutputDir();
        if (outputDir != null) {
            Path resultsFile = Path.of(outputDir, "results_report.html");
            if (Files.exists(resultsFile)) {
                try {
                    var lastModified = Files.getLastModifiedTime(resultsFile).toInstant()
                        .atZone(ZoneId.systemDefault());
                    resultsInfoLabel.setText("Results from a previous count are available (generated "
                        + lastModified.format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")) + ").");
                } catch (IOException e) {
                    resultsInfoLabel.setText("Results from a previous count are available.");
                }
                resultsInfoLabel.setVisible(true);
                resultsInfoLabel.setManaged(true);
            }
        }
    }

    @FXML
    private void showDashboard() {
        go("/fxml/counting.fxml");
    }

    @FXML
    private void showViewer() {
        go("/fxml/viewer.fxml");
    }

    private void go(String fxmlPath) {
        try {
            navigator.showInContent(fxmlPath);
        } catch (IOException e) {
            log.error("Could not open {}", fxmlPath, e);
            Throwable cur = e;
            while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
            new Alert(Alert.AlertType.ERROR, "Could not open screen: "
                + cur.getClass().getSimpleName() + ": " + cur.getMessage()).showAndWait();
        }
    }
}
