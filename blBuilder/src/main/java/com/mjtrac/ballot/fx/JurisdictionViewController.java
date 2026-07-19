/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Ballot System — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.ballot.fx;

import com.mjtrac.ballot.model.Jurisdiction;
import com.mjtrac.ballot.repository.JurisdictionRepository;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/** Mirrors admin/jurisdictions/list.html + form.html + the removed JurisdictionController. Admin-only. */
@Component
public class JurisdictionViewController {

    private final JurisdictionRepository jurisdictionRepo;

    @FXML private Label messageLabel;
    @FXML private Label formTitleLabel;
    @FXML private TableView<Jurisdiction> jurisdictionTable;
    @FXML private TextField nameField;
    @FXML private TextField addressField;
    @FXML private TextField contactEmailField;
    @FXML private TextArea instructionsArea;

    private Jurisdiction editing;

    public JurisdictionViewController(JurisdictionRepository jurisdictionRepo) {
        this.jurisdictionRepo = jurisdictionRepo;
    }

    @FXML
    private void initialize() {
        hideMessage();
        buildColumns();
        refresh();
        handleNew();
    }

    private void buildColumns() {
        TableColumn<Jurisdiction, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(220);

        TableColumn<Jurisdiction, String> emailCol = new TableColumn<>("Contact Email");
        emailCol.setCellValueFactory(new PropertyValueFactory<>("contactEmail"));
        emailCol.setPrefWidth(220);

        TableColumn<Jurisdiction, Void> editCol = new TableColumn<>("Edit");
        editCol.setPrefWidth(70);
        editCol.setCellFactory(col -> new TableCell<>() {
            private final Button editButton = new Button("Edit");
            { editButton.setOnAction(e -> loadForEdit(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : editButton);
            }
        });

        TableColumn<Jurisdiction, Void> deleteCol = new TableColumn<>("Delete");
        deleteCol.setPrefWidth(80);
        deleteCol.setCellFactory(col -> new TableCell<>() {
            private final Button deleteButton = new Button("Delete");
            { deleteButton.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteButton);
            }
        });

        jurisdictionTable.getColumns().setAll(List.of(nameCol, emailCol, editCol, deleteCol));
    }

    private void refresh() {
        jurisdictionTable.setItems(FXCollections.observableArrayList(jurisdictionRepo.findAll()));
    }

    private void loadForEdit(Jurisdiction j) {
        editing = j;
        formTitleLabel.setText("Edit Jurisdiction: " + j.getName());
        nameField.setText(j.getName());
        addressField.setText(j.getAddress() == null ? "" : j.getAddress());
        contactEmailField.setText(j.getContactEmail() == null ? "" : j.getContactEmail());
        instructionsArea.setText(j.getGeneralVotingInstructions() == null ? "" : j.getGeneralVotingInstructions());
        hideMessage();
    }

    @FXML
    private void handleNew() {
        editing = null;
        formTitleLabel.setText("New Jurisdiction");
        nameField.clear();
        addressField.clear();
        contactEmailField.clear();
        instructionsArea.clear();
        hideMessage();
    }

    @FXML
    private void handleSave() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isEmpty()) {
            showError("Jurisdiction name is required.");
            return;
        }

        Jurisdiction j = (editing != null) ? editing : new Jurisdiction();
        j.setName(name);
        j.setAddress(addressField.getText());
        j.setContactEmail(contactEmailField.getText());
        j.setGeneralVotingInstructions(instructionsArea.getText());
        jurisdictionRepo.save(j);

        String verb = (editing != null) ? "Updated" : "Created";
        refresh();
        handleNew();
        showOk(verb + " jurisdiction \"" + j.getName() + "\".");
    }

    private void handleDelete(Jurisdiction j) {
        if (jurisdictionRepo.count() <= 1) {
            showError("Cannot delete the only jurisdiction. Edit it to update its details instead.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete jurisdiction \"" + j.getName() + "\"?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait();
        if (confirm.getResult() != ButtonType.YES) {
            return;
        }
        try {
            jurisdictionRepo.delete(j);
            refresh();
            showOk("Deleted jurisdiction \"" + j.getName() + "\".");
        } catch (Exception e) {
            showError("Cannot delete \"" + j.getName() + "\": it still has elections, "
                + "regions, or other records linked to it. Remove those first.");
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
