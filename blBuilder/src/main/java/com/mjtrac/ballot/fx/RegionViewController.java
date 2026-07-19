/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Ballot System — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.ballot.fx;

import com.mjtrac.ballot.model.Jurisdiction;
import com.mjtrac.ballot.model.Region;
import com.mjtrac.ballot.repository.BallotCombinationRepository;
import com.mjtrac.ballot.repository.JurisdictionRepository;
import com.mjtrac.ballot.repository.RegionRepository;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Mirrors data/regions/list.html + form.html + the removed RegionController. */
@Component
public class RegionViewController {

    private final RegionRepository regionRepo;
    private final JurisdictionRepository jurisdictionRepo;
    private final BallotCombinationRepository combinationRepo;

    @FXML private Label messageLabel;
    @FXML private Label formTitleLabel;
    @FXML private TableView<Region> regionTable;
    @FXML private TextField nameField;
    @FXML private ComboBox<Jurisdiction> jurisdictionCombo;
    @FXML private ComboBox<Region.RegionType> regionTypeCombo;
    @FXML private HBox groupTypeRow;
    @FXML private TextField groupTypeField;
    @FXML private TextField descriptionField;
    @FXML private VBox membersBox;
    @FXML private ListView<Region> membersListView;

    private Region editing;
    private final Set<Long> selectedMemberIds = new HashSet<>();

    public RegionViewController(RegionRepository regionRepo,
                                 JurisdictionRepository jurisdictionRepo,
                                 BallotCombinationRepository combinationRepo) {
        this.regionRepo = regionRepo;
        this.jurisdictionRepo = jurisdictionRepo;
        this.combinationRepo = combinationRepo;
    }

    @FXML
    private void initialize() {
        jurisdictionCombo.setConverter(nameConverter());
        jurisdictionCombo.setItems(FXCollections.observableArrayList(jurisdictionRepo.findAll()));

        regionTypeCombo.setItems(FXCollections.observableArrayList(Region.RegionType.values()));
        regionTypeCombo.valueProperty().addListener((obs, old, val) -> updateGroupFieldsVisibility());

        membersListView.setCellFactory(checkBoxCellFactory());

        hideMessage();
        buildColumns();
        refresh();
        handleNew();
    }

    private StringConverter<Jurisdiction> nameConverter() {
        return new StringConverter<>() {
            @Override public String toString(Jurisdiction j) { return j == null ? "" : j.getName(); }
            @Override public Jurisdiction fromString(String s) { return null; }
        };
    }

    private Callback<ListView<Region>, ListCell<Region>> checkBoxCellFactory() {
        return lv -> new ListCell<>() {
            private final CheckBox checkBox = new CheckBox();
            @Override protected void updateItem(Region region, boolean empty) {
                super.updateItem(region, empty);
                if (empty || region == null) {
                    setGraphic(null);
                    return;
                }
                checkBox.setText(region.getDisplayName());
                checkBox.setSelected(selectedMemberIds.contains(region.getId()));
                checkBox.setOnAction(e -> {
                    if (checkBox.isSelected()) {
                        selectedMemberIds.add(region.getId());
                    } else {
                        selectedMemberIds.remove(region.getId());
                    }
                });
                setGraphic(checkBox);
            }
        };
    }

    private void buildColumns() {
        TableColumn<Region, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(180);

        TableColumn<Region, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(
            row.getValue().getRegionType() == null ? "" : row.getValue().getRegionType().name()));
        typeCol.setPrefWidth(140);

        TableColumn<Region, String> groupTypeCol = new TableColumn<>("Group Type");
        groupTypeCol.setCellValueFactory(new PropertyValueFactory<>("groupType"));
        groupTypeCol.setPrefWidth(120);

        TableColumn<Region, Void> editCol = new TableColumn<>("Edit");
        editCol.setPrefWidth(70);
        editCol.setCellFactory(col -> new TableCell<>() {
            private final Button editButton = new Button("Edit");
            { editButton.setOnAction(e -> loadForEdit(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : editButton);
            }
        });

        TableColumn<Region, Void> deleteCol = new TableColumn<>("Delete");
        deleteCol.setPrefWidth(80);
        deleteCol.setCellFactory(col -> new TableCell<>() {
            private final Button deleteButton = new Button("Delete");
            { deleteButton.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteButton);
            }
        });

        regionTable.getColumns().setAll(List.of(nameCol, typeCol, groupTypeCol, editCol, deleteCol));
    }

    private void refresh() {
        regionTable.setItems(FXCollections.observableArrayList(regionRepo.findAll()));
    }

    private void updateGroupFieldsVisibility() {
        boolean isGroup = regionTypeCombo.getValue() == Region.RegionType.PRECINCT_GROUP;
        groupTypeRow.setVisible(isGroup);
        groupTypeRow.setManaged(isGroup);
        boolean showMembers = isGroup && editing != null;
        membersBox.setVisible(showMembers);
        membersBox.setManaged(showMembers);
        if (showMembers) {
            loadMemberCandidates();
        }
    }

    private void loadMemberCandidates() {
        if (editing == null || editing.getJurisdiction() == null) {
            membersListView.setItems(FXCollections.observableArrayList());
            return;
        }
        List<Region> singles = regionRepo.findByJurisdictionIdAndRegionTypeOrderByName(
            editing.getJurisdiction().getId(), Region.RegionType.SINGLE_PRECINCT);
        membersListView.setItems(FXCollections.observableArrayList(singles));
    }

    private void loadForEdit(Region r) {
        editing = r;
        formTitleLabel.setText("Edit Region: " + r.getName());
        nameField.setText(r.getName());
        if (r.getJurisdiction() != null) {
            jurisdictionCombo.getSelectionModel().select(r.getJurisdiction());
        }
        regionTypeCombo.getSelectionModel().select(r.getRegionType());
        groupTypeField.setText(r.getGroupType() == null ? "" : r.getGroupType());
        descriptionField.setText(r.getDescription() == null ? "" : r.getDescription());

        selectedMemberIds.clear();
        r.getMembers().forEach(m -> selectedMemberIds.add(m.getId()));

        hideMessage();
        updateGroupFieldsVisibility();
    }

    @FXML
    private void handleNew() {
        editing = null;
        formTitleLabel.setText("New Region");
        nameField.clear();
        if (!jurisdictionCombo.getItems().isEmpty()) {
            jurisdictionCombo.getSelectionModel().selectFirst();
        }
        regionTypeCombo.getSelectionModel().select(Region.RegionType.SINGLE_PRECINCT);
        groupTypeField.clear();
        descriptionField.clear();
        selectedMemberIds.clear();
        hideMessage();
        updateGroupFieldsVisibility();
    }

    @FXML
    private void handleSave() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        Jurisdiction jurisdiction = jurisdictionCombo.getValue();
        Region.RegionType type = regionTypeCombo.getValue();

        if (name.isEmpty()) {
            showError("Name is required.");
            return;
        }
        if (jurisdiction == null) {
            showError("Please select a master jurisdiction.");
            return;
        }
        if (type == null) {
            showError("Please select a region type (SinglePrecinct or PrecinctGroup).");
            return;
        }

        Region r = (editing != null) ? editing : new Region();
        r.setName(name);
        r.setJurisdiction(jurisdiction);
        r.setRegionType(type);
        r.setDescription(descriptionField.getText());

        if (type == Region.RegionType.SINGLE_PRECINCT) {
            r.setGroupType(null);
            r.getMembers().clear();
        } else {
            String gt = groupTypeField.getText();
            r.setGroupType(gt != null && !gt.isBlank() ? gt : null);
        }

        Region saved = regionRepo.save(r);
        String verb = (editing != null) ? "Updated" : "Created";
        refresh();
        loadForEdit(saved);
        showOk(verb + " region \"" + saved.getName() + "\".");
    }

    @FXML
    private void handleSaveMembers() {
        if (editing == null || !editing.isPrecinctGroup()) {
            showError("\"" + (editing == null ? "" : editing.getName())
                + "\" is a SinglePrecinct and cannot have members.");
            return;
        }
        List<Region> members = new ArrayList<>();
        if (!selectedMemberIds.isEmpty()) {
            members = regionRepo.findAllById(selectedMemberIds).stream()
                .filter(Region::isSinglePrecinct)
                .toList();
        }
        editing.setMembers(members);
        regionRepo.save(editing);
        refresh();
        showOk("Updated member list for \"" + editing.getName() + "\" (" + members.size() + " SinglePrecinct(s)).");
    }

    private void handleDelete(Region r) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete region \"" + r.getName() + "\"?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait();
        if (confirm.getResult() != ButtonType.YES) {
            return;
        }
        try {
            var combos = combinationRepo.findByRegionIdOrderByElectionId(r.getId());
            if (!combos.isEmpty()) {
                combinationRepo.deleteAll(combos);
            }
            String name = r.getName();
            regionRepo.delete(r);
            refresh();
            showOk("Deleted region \"" + name + "\""
                + (combos.isEmpty() ? "." : " and " + combos.size() + " ballot combination(s) that referenced it."));
        } catch (Exception e) {
            showError("Could not delete \"" + r.getName() + "\": " + e.getMessage());
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
