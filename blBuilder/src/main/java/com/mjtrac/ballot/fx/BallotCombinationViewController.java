/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Ballot System — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.ballot.fx;

import com.mjtrac.ballot.model.*;
import com.mjtrac.ballot.repository.*;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mirrors data/ballot-combinations/list.html + form.html + the removed
 * BallotCombinationController. The web version reloaded the page via GET
 * when the Election dropdown changed, to refresh the Region/Party/BallotType
 * options for that election's jurisdiction — here that's just a ComboBox
 * listener, since everything lives in one process.
 */
@Component
public class BallotCombinationViewController {

    private final BallotCombinationRepository combinationRepo;
    private final ElectionRepository electionRepo;
    private final RegionRepository regionRepo;
    private final PartyRepository partyRepo;
    private final BallotTypeRepository ballotTypeRepo;

    @FXML private Label messageLabel;
    @FXML private Label formTitleLabel;
    @FXML private TableView<BallotCombination> combinationTable;
    @FXML private ComboBox<Election> electionCombo;
    @FXML private ComboBox<Region> regionCombo;
    @FXML private ComboBox<Party> partyCombo;
    @FXML private ComboBox<BallotType> ballotTypeCombo;

    private BallotCombination editing;

    public BallotCombinationViewController(BallotCombinationRepository combinationRepo,
                                            ElectionRepository electionRepo,
                                            RegionRepository regionRepo,
                                            PartyRepository partyRepo,
                                            BallotTypeRepository ballotTypeRepo) {
        this.combinationRepo = combinationRepo;
        this.electionRepo = electionRepo;
        this.regionRepo = regionRepo;
        this.partyRepo = partyRepo;
        this.ballotTypeRepo = ballotTypeRepo;
    }

    @FXML
    private void initialize() {
        electionCombo.setConverter(nameConverter(Election::getName));
        regionCombo.setConverter(nameConverter(Region::getDisplayName));
        partyCombo.setConverter(nameConverter(Party::getName));
        ballotTypeCombo.setConverter(nameConverter(BallotType::getName));

        electionCombo.setItems(FXCollections.observableArrayList(electionRepo.findAll()));
        electionCombo.valueProperty().addListener((obs, old, election) -> loadDropdownsFor(election));

        hideMessage();
        buildColumns();
        refresh();
        handleNew();
    }

    private <T> StringConverter<T> nameConverter(java.util.function.Function<T, String> nameFn) {
        return new StringConverter<>() {
            @Override public String toString(T t) { return t == null ? "" : nameFn.apply(t); }
            @Override public T fromString(String s) { return null; }
        };
    }

    private void loadDropdownsFor(Election election) {
        Long jurId = (election != null && election.getJurisdiction() != null)
            ? election.getJurisdiction().getId() : null;

        regionCombo.setItems(jurId != null
            ? FXCollections.observableArrayList(
                regionRepo.findByJurisdictionIdAndRegionTypeOrderByName(jurId, Region.RegionType.SINGLE_PRECINCT))
            : FXCollections.observableArrayList());
        partyCombo.setItems(jurId != null
            ? FXCollections.observableArrayList(partyRepo.findByJurisdictionIdOrderByName(jurId))
            : FXCollections.observableArrayList());
        ballotTypeCombo.setItems(jurId != null
            ? FXCollections.observableArrayList(ballotTypeRepo.findByJurisdictionIdOrderByName(jurId))
            : FXCollections.observableArrayList());
    }

    private void buildColumns() {
        TableColumn<BallotCombination, String> electionCol = new TableColumn<>("Election");
        electionCol.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(
            row.getValue().getElection() != null ? row.getValue().getElection().getName() : ""));
        electionCol.setPrefWidth(180);

        TableColumn<BallotCombination, String> regionCol = new TableColumn<>("Precinct");
        regionCol.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(
            row.getValue().getRegion() != null ? row.getValue().getRegion().getName() : ""));
        regionCol.setPrefWidth(120);

        TableColumn<BallotCombination, String> partyCol = new TableColumn<>("Party");
        partyCol.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(
            row.getValue().getParty() != null ? row.getValue().getParty().getName() : ""));
        partyCol.setPrefWidth(120);

        TableColumn<BallotCombination, String> typeCol = new TableColumn<>("Ballot Type");
        typeCol.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(
            row.getValue().getBallotType() != null ? row.getValue().getBallotType().getName() : ""));
        typeCol.setPrefWidth(120);

        TableColumn<BallotCombination, Void> editCol = new TableColumn<>("Edit");
        editCol.setPrefWidth(70);
        editCol.setCellFactory(col -> new TableCell<>() {
            private final Button editButton = new Button("Edit");
            { editButton.setOnAction(e -> loadForEdit(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : editButton);
            }
        });

        TableColumn<BallotCombination, Void> deleteCol = new TableColumn<>("Delete");
        deleteCol.setPrefWidth(80);
        deleteCol.setCellFactory(col -> new TableCell<>() {
            private final Button deleteButton = new Button("Delete");
            { deleteButton.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteButton);
            }
        });

        combinationTable.getColumns().setAll(List.of(electionCol, regionCol, partyCol, typeCol, editCol, deleteCol));
    }

    private void refresh() {
        combinationTable.setItems(FXCollections.observableArrayList(combinationRepo.findAll()));
    }

    private void loadForEdit(BallotCombination c) {
        editing = c;
        formTitleLabel.setText("Edit Ballot Combination");
        electionCombo.getSelectionModel().select(c.getElection());
        loadDropdownsFor(c.getElection());
        regionCombo.getSelectionModel().select(c.getRegion());
        partyCombo.getSelectionModel().select(c.getParty());
        ballotTypeCombo.getSelectionModel().select(c.getBallotType());
        hideMessage();
    }

    @FXML
    private void handleNew() {
        editing = null;
        formTitleLabel.setText("New Ballot Combination");
        if (!electionCombo.getItems().isEmpty()) {
            electionCombo.getSelectionModel().selectFirst();
        } else {
            regionCombo.setItems(FXCollections.observableArrayList());
            partyCombo.setItems(FXCollections.observableArrayList());
            ballotTypeCombo.setItems(FXCollections.observableArrayList());
        }
        hideMessage();
    }

    @FXML
    private void handleSave() {
        Election election = electionCombo.getValue();
        Region region = regionCombo.getValue();
        Party party = partyCombo.getValue();
        BallotType ballotType = ballotTypeCombo.getValue();

        if (election == null) {
            showError("Please select an election.");
            return;
        }
        if (region == null) {
            showError("Please select a SinglePrecinct.");
            return;
        }
        if (!region.isSinglePrecinct()) {
            showError("\"" + region.getName() + "\" is a PrecinctGroup, not a SinglePrecinct. "
                + "Ballot combinations must use a SinglePrecinct.");
            return;
        }
        if (ballotType == null) {
            showError("Please select a ballot type.");
            return;
        }
        if (party == null) {
            showError("Please select a party. For general or nonpartisan elections, select \"Everyone\".");
            return;
        }

        BallotCombination c = (editing != null) ? editing : new BallotCombination();
        c.setElection(election);
        c.setRegion(region);
        c.setParty(party);
        c.setBallotType(ballotType);
        combinationRepo.save(c);

        String verb = (editing != null) ? "Updated" : "Created";
        refresh();
        handleNew();
        showOk(verb + " ballot combination.");
    }

    private void handleDelete(BallotCombination c) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete this ballot combination?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait();
        if (confirm.getResult() != ButtonType.YES) {
            return;
        }
        try {
            combinationRepo.delete(c);
            refresh();
            showOk("Deleted ballot combination.");
        } catch (Exception e) {
            showError("Cannot delete: this combination has print log records attached.");
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
