/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Ballot System — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.ballot.fx;

import com.mjtrac.ballot.model.*;
import com.mjtrac.ballot.repository.*;
import com.mjtrac.ballot.service.BallotGenerationService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Mirrors print/form.html + the removed BallotController's PDF-generation
 * endpoint. The web version streamed the PDF to the browser for inline
 * display; here it's written straight to the same ballot.export.dir
 * BallotGenerationService already writes the YAML/XML offset report to
 * (confirmed by BallotGenerationServiceTest — that write already happens
 * as a side effect of generateBallot() regardless of caller). The
 * export/OCR-name-list endpoints from the removed controller are not
 * ported — out of scope for this pass, noted in the conversion report.
 */
@Component
public class PrintViewController {

    private final BallotCombinationRepository combinationRepo;
    private final BallotDesignTemplateRepository templateRepo;
    private final BallotLanguageRepository langRepo;
    private final BallotGenerationService ballotService;
    private final AuthContext authContext;

    @Value("${ballot.export.dir:${user.home}/pbss_data/ballot_templates}")
    private String exportDir;

    @FXML private Label messageLabel;
    @FXML private ComboBox<BallotCombination> combinationCombo;
    @FXML private ComboBox<BallotDesignTemplate> templateCombo;
    @FXML private ComboBox<String> languageCombo;
    @FXML private Spinner<Integer> copiesSpinner;

    public PrintViewController(BallotCombinationRepository combinationRepo,
                                BallotDesignTemplateRepository templateRepo,
                                BallotLanguageRepository langRepo,
                                BallotGenerationService ballotService,
                                AuthContext authContext) {
        this.combinationRepo = combinationRepo;
        this.templateRepo = templateRepo;
        this.langRepo = langRepo;
        this.ballotService = ballotService;
        this.authContext = authContext;
    }

    @FXML
    private void initialize() {
        combinationCombo.setConverter(new StringConverter<>() {
            @Override public String toString(BallotCombination c) {
                if (c == null) return "";
                String party = c.getParty() != null ? c.getParty().getName() : "Nonpartisan";
                return c.getRegion().getName() + " · " + party + " · "
                    + c.getBallotType().getName() + " · " + c.getElection().getName();
            }
            @Override public BallotCombination fromString(String s) { return null; }
        });
        combinationCombo.setItems(FXCollections.observableArrayList(combinationRepo.findAll()));
        combinationCombo.valueProperty().addListener((obs, old, combo) -> loadTemplatesAndLanguages(combo));

        templateCombo.setConverter(new StringConverter<>() {
            @Override public String toString(BallotDesignTemplate t) {
                if (t == null) return "";
                return t.getPaperSize().name() + " · " + t.getColumns() + " col · " + t.getVoteIndicatorStyle().name();
            }
            @Override public BallotDesignTemplate fromString(String s) { return null; }
        });

        copiesSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 1));

        hideMessage();
        if (!combinationCombo.getItems().isEmpty()) {
            combinationCombo.getSelectionModel().selectFirst();
        }
    }

    private void loadTemplatesAndLanguages(BallotCombination combo) {
        if (combo == null) {
            templateCombo.setItems(FXCollections.observableArrayList());
            languageCombo.setItems(FXCollections.observableArrayList());
            return;
        }
        List<BallotDesignTemplate> templates = templateRepo.findByElectionId(combo.getElection().getId());
        templateCombo.setItems(FXCollections.observableArrayList(templates));
        if (!templates.isEmpty()) {
            templateCombo.getSelectionModel().selectFirst();
        }

        Jurisdiction jurisdiction = combo.getElection().getJurisdiction();
        List<String> codes = new java.util.ArrayList<>();
        codes.add("en");
        if (jurisdiction != null) {
            langRepo.findByJurisdictionIdOrderByDisplayOrderAsc(jurisdiction.getId())
                .forEach(l -> codes.add(l.getLanguageCode()));
        }
        languageCombo.setItems(FXCollections.observableArrayList(codes));
        languageCombo.getSelectionModel().selectFirst();
    }

    @FXML
    private void handleGenerate() {
        BallotCombination combo = combinationCombo.getValue();
        if (combo == null) {
            showError("Please select a ballot combination.");
            return;
        }
        BallotDesignTemplate template = templateCombo.getValue();
        if (template == null) {
            template = templateRepo.findFirstByElectionIdOrderByIdAsc(combo.getElection().getId()).orElse(null);
        }
        if (template == null) {
            showError("No design template found for election \"" + combo.getElection().getName()
                + "\". Create a ballot design template for this election first.");
            return;
        }
        String lang = languageCombo.getValue() != null ? languageCombo.getValue() : "en";
        int copies = copiesSpinner.getValue();

        User user = authContext.getCurrentUser();
        if (user == null) {
            showError("No signed-in user found.");
            return;
        }

        try {
            byte[] pdf = ballotService.generateBallot(combo, template, user, copies, lang);
            String filename = "ballot-" + combo.getId() + "-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".pdf";
            Path outPath = Path.of(exportDir, filename);
            Files.createDirectories(outPath.getParent());
            Files.write(outPath, pdf);
            showOk("Generated " + pdf.length + " byte PDF (" + copies + " cop"
                + (copies == 1 ? "y" : "ies") + ") — saved to " + outPath);
        } catch (Exception e) {
            showError("Could not generate ballot: " + e.getMessage());
        }
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
