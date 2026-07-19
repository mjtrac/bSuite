/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Ballot System — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.ballot.fx;

import com.mjtrac.ballot.model.Jurisdiction;
import com.mjtrac.ballot.model.Party;
import com.mjtrac.ballot.repository.BallotCombinationRepository;
import com.mjtrac.ballot.repository.JurisdictionRepository;
import com.mjtrac.ballot.repository.PartyRepository;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mirrors data/parties/list.html + form.html + the removed PartyController.
 * One screen (table + inline form) rather than separate list/form FXMLs —
 * same pattern as blScanner's Users screen. Pilot pattern for the other
 * simple entity CRUD screens (Jurisdiction, Election, BallotType, Language,
 * BallotCombination).
 */
@Component
public class PartyViewController {

    private final PartyRepository partyRepo;
    private final JurisdictionRepository jurisdictionRepo;
    private final BallotCombinationRepository combinationRepo;

    @FXML private Label messageLabel;
    @FXML private Label formTitleLabel;
    @FXML private TableView<Party> partyTable;
    @FXML private TextField nameField;
    @FXML private TextField abbreviationField;
    @FXML private ComboBox<Jurisdiction> jurisdictionCombo;

    private Party editing;

    public PartyViewController(PartyRepository partyRepo,
                                JurisdictionRepository jurisdictionRepo,
                                BallotCombinationRepository combinationRepo) {
        this.partyRepo = partyRepo;
        this.jurisdictionRepo = jurisdictionRepo;
        this.combinationRepo = combinationRepo;
    }

    @FXML
    private void initialize() {
        jurisdictionCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Jurisdiction j) { return j == null ? "" : j.getName(); }
            @Override public Jurisdiction fromString(String s) { return null; }
        });
        jurisdictionCombo.setItems(FXCollections.observableArrayList(jurisdictionRepo.findAll()));
        if (!jurisdictionCombo.getItems().isEmpty()) {
            jurisdictionCombo.getSelectionModel().selectFirst();
        }

        hideMessage();
        buildColumns();
        refresh();
        handleNew();
    }

    private void buildColumns() {
        TableColumn<Party, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(200);

        TableColumn<Party, String> abbrCol = new TableColumn<>("Abbreviation");
        abbrCol.setCellValueFactory(new PropertyValueFactory<>("abbreviation"));
        abbrCol.setPrefWidth(120);

        TableColumn<Party, String> jurisCol = new TableColumn<>("Jurisdiction");
        jurisCol.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(
            row.getValue().getJurisdiction() != null ? row.getValue().getJurisdiction().getName() : ""));
        jurisCol.setPrefWidth(180);

        TableColumn<Party, Void> editCol = new TableColumn<>("Edit");
        editCol.setPrefWidth(70);
        editCol.setCellFactory(col -> new TableCell<>() {
            private final Button editButton = new Button("Edit");
            { editButton.setOnAction(e -> loadForEdit(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : editButton);
            }
        });

        TableColumn<Party, Void> deleteCol = new TableColumn<>("Delete");
        deleteCol.setPrefWidth(80);
        deleteCol.setCellFactory(col -> new TableCell<>() {
            private final Button deleteButton = new Button("Delete");
            { deleteButton.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteButton);
            }
        });

        partyTable.getColumns().setAll(List.of(nameCol, abbrCol, jurisCol, editCol, deleteCol));
    }

    private void refresh() {
        partyTable.setItems(FXCollections.observableArrayList(partyRepo.findAll()));
    }

    private void loadForEdit(Party p) {
        editing = p;
        formTitleLabel.setText("Edit Party: " + p.getName());
        nameField.setText(p.getName());
        abbreviationField.setText(p.getAbbreviation() == null ? "" : p.getAbbreviation());
        if (p.getJurisdiction() != null) {
            jurisdictionCombo.getSelectionModel().select(p.getJurisdiction());
        }
        hideMessage();
    }

    @FXML
    private void handleNew() {
        editing = null;
        formTitleLabel.setText("New Party");
        nameField.clear();
        abbreviationField.clear();
        if (!jurisdictionCombo.getItems().isEmpty()) {
            jurisdictionCombo.getSelectionModel().selectFirst();
        }
        hideMessage();
    }

    @FXML
    private void handleSave() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        Jurisdiction jurisdiction = jurisdictionCombo.getValue();
        String abbreviation = abbreviationField.getText();

        if (name.isEmpty()) {
            showError("Party name is required.");
            return;
        }
        if (jurisdiction == null) {
            showError("Please select a jurisdiction.");
            return;
        }

        Party p = (editing != null) ? editing : new Party();
        p.setName(name);
        p.setJurisdiction(jurisdiction);
        p.setAbbreviation(abbreviation != null && !abbreviation.isBlank() ? abbreviation.trim() : null);
        partyRepo.save(p);

        String verb = (editing != null) ? "Updated" : "Created";
        refresh();
        handleNew();
        showOk(verb + " party \"" + p.getName() + "\".");
    }

    private void handleDelete(Party p) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete party \"" + p.getName() + "\"?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait();
        if (confirm.getResult() != ButtonType.YES) {
            return;
        }
        try {
            var combos = combinationRepo.findByPartyId(p.getId());
            if (!combos.isEmpty()) {
                combinationRepo.deleteAll(combos);
            }
            String name = p.getName();
            partyRepo.delete(p);
            refresh();
            showOk("Deleted party \"" + name + "\""
                + (combos.isEmpty() ? "." : " and " + combos.size() + " ballot combination(s) that referenced it."));
        } catch (Exception e) {
            showError("Could not delete \"" + p.getName() + "\": " + e.getMessage());
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
