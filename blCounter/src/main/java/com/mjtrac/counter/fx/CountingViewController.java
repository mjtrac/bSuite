/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Counter — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.counter.fx;

import com.mjtrac.counter.model.ScanSession;
import com.mjtrac.counter.service.CountingService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

/**
 * Mirrors templates/index.html (config form) + counting.html (progress) +
 * the removed CountController — combined into one screen, same shape as
 * blScanner's single scan screen. Drives the ported CountingService instead
 * of polling /progress over HTTP.
 */
@Component
public class CountingViewController {

    private final CountingService countingService;
    private final AuthContext authContext;
    private final Navigator navigator;

    @FXML private Label messageLabel;
    @FXML private TextField imageFolderField;
    @FXML private TextField reportFolderField;
    @FXML private Spinner<Integer> thresholdSpinner;
    @FXML private Spinner<Double> darkPctSpinner;
    @FXML private Spinner<Integer> dpiSpinner;
    @FXML private Spinner<Double> paperWidthSpinner;
    @FXML private CheckBox debugCoordinatesCheck;

    @FXML private javafx.scene.layout.VBox statusBoxCard;
    @FXML private Label statusBox;
    @FXML private Label currentImageLabel;
    @FXML private Label countsLabel;
    @FXML private Button stopButton;
    @FXML private Button finishButton;
    @FXML private Button viewReportButton;

    private Timeline pollTimeline;

    public CountingViewController(CountingService countingService, AuthContext authContext, Navigator navigator) {
        this.countingService = countingService;
        this.authContext = authContext;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        thresholdSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 255, 128));
        darkPctSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0, 100, 8, 1));
        dpiSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(72, 1200, 200, 25));
        paperWidthSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(1, 20, 8.5, 0.5));

        hideMessage();

        ScanSession existing = countingService.getSession();
        if (existing.isStarted()) {
            imageFolderField.setText(existing.imageFolder);
            reportFolderField.setText(existing.reportFolder);
            showStatusPanel();
            renderSession(existing);
            if (existing.scanning) {
                startPolling();
            }
        }
    }

    @FXML
    private void browseImageFolder() {
        browseInto(imageFolderField);
    }

    @FXML
    private void browseReportFolder() {
        browseInto(reportFolderField);
    }

    private void browseInto(TextField field) {
        DirectoryChooser chooser = new DirectoryChooser();
        if (!field.getText().isBlank()) {
            File current = new File(field.getText());
            if (current.isDirectory()) chooser.setInitialDirectory(current);
        }
        File dir = chooser.showDialog(imageFolderField.getScene().getWindow());
        if (dir != null) {
            field.setText(dir.getAbsolutePath());
        }
    }

    @FXML
    private void handleStart() {
        try {
            countingService.startNewSession(
                imageFolderField.getText(), reportFolderField.getText(),
                thresholdSpinner.getValue(), darkPctSpinner.getValue(), dpiSpinner.getValue(),
                debugCoordinatesCheck.isSelected(), "", paperWidthSpinner.getValue());
        } catch (Exception e) {
            showError(e.getMessage());
            return;
        }

        String username = authContext.getCurrentUser() != null
            ? authContext.getCurrentUser().getUsername() : "(system)";
        try {
            countingService.startScan(username);
        } catch (IllegalStateException e) {
            showError(e.getMessage());
            return;
        }

        showStatusPanel();
        startPolling();
    }

    @FXML
    private void handleStop() {
        countingService.stopScan();
    }

    @FXML
    private void handleFinish() {
        String username = authContext.getCurrentUser() != null
            ? authContext.getCurrentUser().getUsername() : "(system)";
        try {
            countingService.finish(username);
            showOk("Results saved to: " + countingService.getSession().imageFolder);
        } catch (Exception e) {
            showError("Could not write results: " + e.getMessage());
        }
    }

    @FXML
    private void handleViewReport() {
        try {
            navigator.showInContent("/fxml/results.fxml");
        } catch (IOException e) {
            showError("Could not open results: " + e.getMessage());
        }
    }

    private void showStatusPanel() {
        statusBoxCard.setVisible(true);
        statusBoxCard.setManaged(true);
    }

    private void startPolling() {
        stopPolling();
        pollTimeline = new Timeline(new KeyFrame(Duration.millis(500), e -> pollStatus()));
        pollTimeline.setCycleCount(Timeline.INDEFINITE);
        pollTimeline.play();
    }

    private void stopPolling() {
        if (pollTimeline != null) {
            pollTimeline.stop();
            pollTimeline = null;
        }
    }

    private void pollStatus() {
        ScanSession session = countingService.getSession();
        // The FX progress panel is always live, so the milestone pause the
        // browser version needed (POST /resume) is cleared immediately here.
        if (session.pauseForResults) {
            countingService.clearPause();
        }
        renderSession(session);
        if (!session.scanning && (session.isComplete() && session.tallyDone || session.scanError != null)) {
            stopPolling();
        }
    }

    private void renderSession(ScanSession session) {
        stopButton.setDisable(!session.scanning);
        finishButton.setDisable(session.scanning || !session.isStarted());
        viewReportButton.setDisable(!(session.isComplete() && session.tallyDone));

        currentImageLabel.setText(session.currentImagePath != null ? session.currentImagePath : "");
        countsLabel.setText(String.format("Pass %d — %d / %d images — %d duplicate(s), %d flagged for review",
            session.passNumber, session.processed(), session.totalImages(),
            session.duplicatePaths.size(), session.reviewRequired.size()));

        if (session.scanError != null && !session.scanError.isEmpty()) {
            setStatus("status-errored", "Error: " + session.scanError);
        } else if (session.isComplete() && session.tallyDone) {
            setStatus("status-done", "Complete — " + session.processed() + " image(s) counted");
        } else if (session.scanning) {
            setStatus("status-running", "Scanning… " + session.processed() + " / " + session.totalImages());
        } else if (session.stopRequested) {
            setStatus("status-errored", "Stopped after " + session.processed() + " image(s)");
        } else {
            setStatus("status-idle", "Ready");
        }
    }

    private void setStatus(String styleClass, String text) {
        statusBox.getStyleClass().setAll("status-box", styleClass);
        statusBox.setText(text);
    }

    private void showOk(String text) {
        messageLabel.getStyleClass().setAll("msg-ok");
        messageLabel.setText(text);
        messageLabel.setVisible(true);
        messageLabel.setManaged(true);
    }

    private void showError(String text) {
        messageLabel.getStyleClass().setAll("msg-error");
        messageLabel.setText(text);
        messageLabel.setVisible(true);
        messageLabel.setManaged(true);
    }

    private void hideMessage() {
        messageLabel.setVisible(false);
        messageLabel.setManaged(false);
    }
}
