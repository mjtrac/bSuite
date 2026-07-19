/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Ballot System — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.ballot.fx;

import com.mjtrac.ballot.model.BallotType;
import com.mjtrac.ballot.model.Jurisdiction;
import com.mjtrac.ballot.repository.BallotCombinationRepository;
import com.mjtrac.ballot.repository.BallotTypeRepository;
import com.mjtrac.ballot.repository.JurisdictionRepository;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.StringConverter;
import org.springframework.stereotype.Component;

import java.util.List;

/** Mirrors data/ballot-types/list.html + form.html + the removed BallotTypeController. */
@Component
public class BallotTypeViewController {

    private final BallotTypeRepository ballotTypeRepo;
    private final JurisdictionRepository jurisdictionRepo;
    private final BallotCombinationRepository combinationRepo;

    @FXML private Label messageLabel;
    @FXML private Label formTitleLabel;
    @FXML private TableView<BallotType> ballotTypeTable;
    @FXML private TextField nameField;
    @FXML private TextField descriptionField;
    @FXML private ComboBox<Jurisdiction> jurisdictionCombo;

    private BallotType editing;

    public BallotTypeViewController(BallotTypeRepository ballotTypeRepo,
                                     JurisdictionRepository jurisdictionRepo,
                                     BallotCombinationRepository combinationRepo) {
        this.ballotTypeRepo = ballotTypeRepo;
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

        hideMessage();
        buildColumns();
        refresh();
        handleNew();
    }

    private void buildColumns() {
        TableColumn<BallotType, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(160);

        TableColumn<BallotType, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setPrefWidth(260);

        TableColumn<BallotType, Void> editCol = new TableColumn<>("Edit");
        editCol.setPrefWidth(70);
        editCol.setCellFactory(col -> new TableCell<>() {
            private final Button editButton = new Button("Edit");
            { editButton.setOnAction(e -> loadForEdit(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : editButton);
            }
        });

        TableColumn<BallotType, Void> deleteCol = new TableColumn<>("Delete");
        deleteCol.setPrefWidth(80);
        deleteCol.setCellFactory(col -> new TableCell<>() {
            private final Button deleteButton = new Button("Delete");
            { deleteButton.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteButton);
            }
        });

        ballotTypeTable.getColumns().setAll(List.of(nameCol, descCol, editCol, deleteCol));
    }

    private void refresh() {
        ballotTypeTable.setItems(FXCollections.observableArrayList(ballotTypeRepo.findAll()));
    }

    private void loadForEdit(BallotType bt) {
        editing = bt;
        formTitleLabel.setText("Edit Ballot Type: " + bt.getName());
        nameField.setText(bt.getName());
        descriptionField.setText(bt.getDescription() == null ? "" : bt.getDescription());
        if (bt.getJurisdiction() != null) {
            jurisdictionCombo.getSelectionModel().select(bt.getJurisdiction());
        }
        hideMessage();
    }

    @FXML
    private void handleNew() {
        editing = null;
        formTitleLabel.setText("New Ballot Type");
        nameField.clear();
        descriptionField.clear();
        if (!jurisdictionCombo.getItems().isEmpty()) {
            jurisdictionCombo.getSelectionModel().selectFirst();
        }
        hideMessage();
    }

    @FXML
    private void handleSave() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        Jurisdiction jurisdiction = jurisdictionCombo.getValue();

        if (name.isEmpty()) {
            showError("Ballot type name is required (e.g. Precinct, Mail-In, Absentee).");
            return;
        }
        if (jurisdiction == null) {
            showError("Please select a jurisdiction.");
            return;
        }

        BallotType bt = (editing != null) ? editing : new BallotType();
        bt.setName(name);
        bt.setJurisdiction(jurisdiction);
        bt.setDescription(descriptionField.getText());
        ballotTypeRepo.save(bt);

        String verb = (editing != null) ? "Updated" : "Created";
        refresh();
        handleNew();
        showOk(verb + " ballot type \"" + bt.getName() + "\".");
    }

    private void handleDelete(BallotType bt) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete ballot type \"" + bt.getName() + "\"?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait();
        if (confirm.getResult() != ButtonType.YES) {
            return;
        }
        try {
            var combos = combinationRepo.findByBallotTypeId(bt.getId());
            if (!combos.isEmpty()) {
                combinationRepo.deleteAll(combos);
            }
            String name = bt.getName();
            ballotTypeRepo.delete(bt);
            refresh();
            showOk("Deleted ballot type \"" + name + "\""
                + (combos.isEmpty() ? "." : " and " + combos.size() + " ballot combination(s) that referenced it."));
        } catch (Exception e) {
            showError("Could not delete \"" + bt.getName() + "\": " + e.getMessage());
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
