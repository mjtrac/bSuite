/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Ballot System — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.ballot.fx;

import com.mjtrac.ballot.model.BallotLanguage;
import com.mjtrac.ballot.model.Jurisdiction;
import com.mjtrac.ballot.repository.BallotLanguageRepository;
import com.mjtrac.ballot.repository.JurisdictionRepository;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Mirrors data/languages.html's language-list section of the removed
 * LanguageController (the contest/candidate translation forms are part of
 * the Contest screen instead — they're reached from a contest/candidate row,
 * not from this list).
 */
@Component
public class LanguageViewController {

    private final BallotLanguageRepository langRepo;
    private final JurisdictionRepository jurisdictionRepo;

    @FXML private Label messageLabel;
    @FXML private TableView<BallotLanguage> languageTable;
    @FXML private TextField codeField;
    @FXML private TextField nameField;
    @FXML private Spinner<Integer> displayOrderSpinner;

    private Jurisdiction jurisdiction;

    public LanguageViewController(BallotLanguageRepository langRepo, JurisdictionRepository jurisdictionRepo) {
        this.langRepo = langRepo;
        this.jurisdictionRepo = jurisdictionRepo;
    }

    @FXML
    private void initialize() {
        displayOrderSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 999, 0));

        List<Jurisdiction> all = jurisdictionRepo.findAll();
        jurisdiction = all.isEmpty() ? null : all.get(0);

        hideMessage();
        buildColumns();
        refresh();
    }

    private void buildColumns() {
        TableColumn<BallotLanguage, String> codeCol = new TableColumn<>("Code");
        codeCol.setCellValueFactory(new PropertyValueFactory<>("languageCode"));
        codeCol.setPrefWidth(100);

        TableColumn<BallotLanguage, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("languageName"));
        nameCol.setPrefWidth(200);

        TableColumn<BallotLanguage, Number> orderCol = new TableColumn<>("Order");
        orderCol.setCellValueFactory(new PropertyValueFactory<>("displayOrder"));
        orderCol.setPrefWidth(80);

        TableColumn<BallotLanguage, Void> deleteCol = new TableColumn<>("Remove");
        deleteCol.setPrefWidth(90);
        deleteCol.setCellFactory(col -> new TableCell<>() {
            private final Button deleteButton = new Button("Remove");
            { deleteButton.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteButton);
            }
        });

        languageTable.getColumns().setAll(List.of(codeCol, nameCol, orderCol, deleteCol));
    }

    private void refresh() {
        if (jurisdiction == null) {
            languageTable.setItems(FXCollections.observableArrayList());
            return;
        }
        languageTable.setItems(FXCollections.observableArrayList(
            langRepo.findByJurisdictionIdOrderByDisplayOrderAsc(jurisdiction.getId())));
    }

    @FXML
    private void handleAdd() {
        if (jurisdiction == null) {
            showError("No jurisdiction found.");
            return;
        }
        String code = codeField.getText() == null ? "" : codeField.getText().strip().toLowerCase();
        String name = nameField.getText() == null ? "" : nameField.getText().strip();

        if (code.isEmpty() || name.isEmpty()) {
            showError("Language code and name are both required.");
            return;
        }

        Optional<BallotLanguage> existing = langRepo.findByJurisdictionIdAndLanguageCode(jurisdiction.getId(), code);
        if (existing.isPresent()) {
            showError("Language code '" + code + "' already exists for this jurisdiction.");
            return;
        }

        BallotLanguage lang = new BallotLanguage();
        lang.setJurisdiction(jurisdiction);
        lang.setLanguageCode(code);
        lang.setLanguageName(name);
        lang.setDisplayOrder(displayOrderSpinner.getValue());
        langRepo.save(lang);

        codeField.clear();
        nameField.clear();
        displayOrderSpinner.getValueFactory().setValue(0);
        refresh();
        showOk("Language '" + name + "' added.");
    }

    private void handleDelete(BallotLanguage lang) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Remove language \"" + lang.getLanguageName() + "\"?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait();
        if (confirm.getResult() != ButtonType.YES) {
            return;
        }
        langRepo.deleteById(lang.getId());
        refresh();
        showOk("Language removed.");
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
