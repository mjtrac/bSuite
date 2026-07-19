/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Ballot System — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.ballot.fx;

import com.mjtrac.ballot.model.Election;
import com.mjtrac.ballot.model.Jurisdiction;
import com.mjtrac.ballot.repository.*;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.StringConverter;
import org.springframework.stereotype.Component;

import java.util.List;

/** Mirrors data/elections/list.html + form.html + the removed ElectionController. */
@Component
public class ElectionViewController {

    private final ElectionRepository electionRepo;
    private final JurisdictionRepository jurisdictionRepo;
    private final BallotCombinationRepository combinationRepo;
    private final ContestRepository contestRepo;
    private final BallotDesignTemplateRepository templateRepo;
    private final PrintLogRepository printLogRepo;

    @FXML private Label messageLabel;
    @FXML private Label formTitleLabel;
    @FXML private TableView<Election> electionTable;
    @FXML private TextField nameField;
    @FXML private ComboBox<Jurisdiction> jurisdictionCombo;
    @FXML private DatePicker electionDatePicker;
    @FXML private ComboBox<Election.ElectionType> electionTypeCombo;
    @FXML private CheckBox uniformBallotCheck;

    private Election editing;

    public ElectionViewController(ElectionRepository electionRepo,
                                   JurisdictionRepository jurisdictionRepo,
                                   BallotCombinationRepository combinationRepo,
                                   ContestRepository contestRepo,
                                   BallotDesignTemplateRepository templateRepo,
                                   PrintLogRepository printLogRepo) {
        this.electionRepo = electionRepo;
        this.jurisdictionRepo = jurisdictionRepo;
        this.combinationRepo = combinationRepo;
        this.contestRepo = contestRepo;
        this.templateRepo = templateRepo;
        this.printLogRepo = printLogRepo;
    }

    @FXML
    private void initialize() {
        jurisdictionCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Jurisdiction j) { return j == null ? "" : j.getName(); }
            @Override public Jurisdiction fromString(String s) { return null; }
        });
        jurisdictionCombo.setItems(FXCollections.observableArrayList(jurisdictionRepo.findAll()));

        electionTypeCombo.setItems(FXCollections.observableArrayList(Election.ElectionType.values()));

        hideMessage();
        buildColumns();
        refresh();
        handleNew();
    }

    private void buildColumns() {
        TableColumn<Election, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(220);

        TableColumn<Election, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(
            row.getValue().getElectionDate() == null ? "" : row.getValue().getElectionDate().toString()));
        dateCol.setPrefWidth(110);

        TableColumn<Election, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(
            row.getValue().getElectionType() == null ? "" : row.getValue().getElectionType().name()));
        typeCol.setPrefWidth(110);

        TableColumn<Election, Void> editCol = new TableColumn<>("Edit");
        editCol.setPrefWidth(70);
        editCol.setCellFactory(col -> new TableCell<>() {
            private final Button editButton = new Button("Edit");
            { editButton.setOnAction(e -> loadForEdit(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : editButton);
            }
        });

        TableColumn<Election, Void> deleteCol = new TableColumn<>("Delete");
        deleteCol.setPrefWidth(80);
        deleteCol.setCellFactory(col -> new TableCell<>() {
            private final Button deleteButton = new Button("Delete");
            { deleteButton.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteButton);
            }
        });

        electionTable.getColumns().setAll(List.of(nameCol, dateCol, typeCol, editCol, deleteCol));
    }

    private void refresh() {
        electionTable.setItems(FXCollections.observableArrayList(electionRepo.findAll()));
    }

    private void loadForEdit(Election e) {
        editing = e;
        formTitleLabel.setText("Edit Election: " + e.getName());
        nameField.setText(e.getName());
        jurisdictionCombo.getSelectionModel().select(e.getJurisdiction());
        electionDatePicker.setValue(e.getElectionDate());
        electionTypeCombo.getSelectionModel().select(e.getElectionType());
        uniformBallotCheck.setSelected(e.isUniformBallot());
        hideMessage();
    }

    @FXML
    private void handleNew() {
        editing = null;
        formTitleLabel.setText("New Election");
        nameField.clear();
        if (!jurisdictionCombo.getItems().isEmpty()) {
            jurisdictionCombo.getSelectionModel().selectFirst();
        }
        electionDatePicker.setValue(null);
        electionTypeCombo.getSelectionModel().clearSelection();
        uniformBallotCheck.setSelected(false);
        hideMessage();
    }

    @FXML
    private void handleSave() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        Jurisdiction jurisdiction = jurisdictionCombo.getValue();
        Election.ElectionType type = electionTypeCombo.getValue();

        if (name.isEmpty()) {
            showError("Election name is required.");
            return;
        }
        if (jurisdiction == null) {
            showError("Please select a jurisdiction.");
            return;
        }
        if (type == null) {
            showError("Please select an election type.");
            return;
        }

        Election e = (editing != null) ? editing : new Election();
        e.setName(name);
        e.setJurisdiction(jurisdiction);
        e.setElectionType(type);
        e.setUniformBallot(uniformBallotCheck.isSelected());
        e.setElectionDate(electionDatePicker.getValue());
        electionRepo.save(e);

        String verb = (editing != null) ? "Updated" : "Created";
        refresh();
        handleNew();
        showOk(verb + " election \"" + e.getName() + "\".");
    }

    /**
     * SQLite does not enforce foreign keys by default, so dependents are
     * deleted explicitly in order — same sequence the removed
     * ElectionController.delete() used. Not wrapped in @Transactional: called
     * directly from a lambda on this same bean, which bypasses Spring's AOP
     * proxy (self-invocation — see DataInitializer's "self" field for the
     * same pitfall) so the annotation would silently do nothing anyway. Each
     * individual repository call is still transactional on its own via
     * SimpleJpaRepository; only cross-call atomicity is lost, same as the
     * pre-existing SQLite FK-less "best effort" cleanup this mirrors.
     */
    protected void handleDelete(Election e) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete election \"" + e.getName() + "\" and all associated data?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait();
        if (confirm.getResult() != ButtonType.YES) {
            return;
        }
        try {
            printLogRepo.deleteByElection(e);
            combinationRepo.deleteAll(combinationRepo.findByElectionId(e.getId()));
            contestRepo.deleteAll(contestRepo.findByElectionId(e.getId()));
            templateRepo.deleteAll(templateRepo.findByElectionId(e.getId()));
            electionRepo.delete(e);
            refresh();
            showOk("Deleted election \"" + e.getName() + "\" and all associated data.");
        } catch (Exception ex) {
            showError("Could not delete \"" + e.getName() + "\": " + ex.getMessage());
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
