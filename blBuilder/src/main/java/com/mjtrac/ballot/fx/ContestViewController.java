/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Ballot System — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.ballot.fx;

import com.mjtrac.ballot.model.*;
import com.mjtrac.ballot.repository.*;
import com.mjtrac.ballot.service.ContestDefaultsService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mirrors data/contests.html + contest-form.html + data/candidates.html +
 * the removed ContestController — the largest single screen in bBuilder,
 * built last per the plan once the CRUD/checklist patterns were proven on
 * Party and Region.
 *
 * Scope note: printableTitle/recordTitle default to the same value as title
 * (matching the removed controller's own fallback when only "title" was
 * submitted); groupingLabel/preamble/postamble/explanatoryText and their
 * individual print-flag toggles, and the coverage-preview and
 * contest/candidate translation screens are not exposed here — left at
 * whatever the entity already has, same "mutate in place, don't clobber
 * unexposed fields" approach as the BallotDesignTemplate screen.
 */
@Component
public class ContestViewController {

    private final ContestRepository contestRepo;
    private final ElectionRepository electionRepo;
    private final RegionRepository regionRepo;
    private final ContestDefaultsService contestDefaultsService;

    @FXML private Label messageLabel;
    @FXML private Label formTitleLabel;
    @FXML private TableView<Contest> contestTable;
    @FXML private TextField titleField;
    @FXML private ComboBox<Election> electionCombo;
    @FXML private ComboBox<Contest.VotingMethod> votingMethodCombo;
    @FXML private Spinner<Integer> maxChoicesSpinner;
    @FXML private Spinner<Integer> displayOrderSpinner;
    @FXML private TextField instructionsField;
    @FXML private VBox regionsBox;
    @FXML private ListView<Region> regionsListView;

    @FXML private VBox candidatesBox;
    @FXML private Label candidatesTitleLabel;
    @FXML private TableView<Candidate> candidateTable;
    @FXML private Label candidateFormTitleLabel;
    @FXML private TextField candidateNameField;
    @FXML private TextField candidatePartyField;
    @FXML private Spinner<Integer> candidateOrderSpinner;
    @FXML private CheckBox candidateWriteInCheck;

    private Contest editing;
    private final Set<Long> selectedRegionIds = new HashSet<>();
    private Candidate editingCandidate;

    public ContestViewController(ContestRepository contestRepo,
                                  ElectionRepository electionRepo,
                                  RegionRepository regionRepo,
                                  ContestDefaultsService contestDefaultsService) {
        this.contestRepo = contestRepo;
        this.electionRepo = electionRepo;
        this.regionRepo = regionRepo;
        this.contestDefaultsService = contestDefaultsService;
    }

    @FXML
    private void initialize() {
        electionCombo.setConverter(nameConverter(Election::getName));
        electionCombo.setItems(FXCollections.observableArrayList(electionRepo.findAll()));

        votingMethodCombo.setItems(FXCollections.observableArrayList(Contest.VotingMethod.values()));

        maxChoicesSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, 1));
        displayOrderSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 999, 0));
        candidateOrderSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 1));

        regionsListView.setCellFactory(regionCheckBoxCellFactory());

        hideMessage();
        buildContestColumns();
        buildCandidateColumns();
        refreshContests();
        handleNew();
    }

    private <T> StringConverter<T> nameConverter(java.util.function.Function<T, String> nameFn) {
        return new StringConverter<>() {
            @Override public String toString(T t) { return t == null ? "" : nameFn.apply(t); }
            @Override public T fromString(String s) { return null; }
        };
    }

    private Callback<ListView<Region>, ListCell<Region>> regionCheckBoxCellFactory() {
        return lv -> new ListCell<>() {
            private final CheckBox checkBox = new CheckBox();
            @Override protected void updateItem(Region region, boolean empty) {
                super.updateItem(region, empty);
                if (empty || region == null) {
                    setGraphic(null);
                    return;
                }
                checkBox.setText(region.getDisplayName());
                checkBox.setSelected(selectedRegionIds.contains(region.getId()));
                checkBox.setOnAction(e -> {
                    if (checkBox.isSelected()) {
                        selectedRegionIds.add(region.getId());
                    } else {
                        selectedRegionIds.remove(region.getId());
                    }
                });
                setGraphic(checkBox);
            }
        };
    }

    private void buildContestColumns() {
        TableColumn<Contest, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setPrefWidth(220);

        TableColumn<Contest, String> electionCol = new TableColumn<>("Election");
        electionCol.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(
            row.getValue().getElection() != null ? row.getValue().getElection().getName() : ""));
        electionCol.setPrefWidth(180);

        TableColumn<Contest, String> methodCol = new TableColumn<>("Voting Method");
        methodCol.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(
            row.getValue().getVotingMethod() == null ? "" : row.getValue().getVotingMethod().name()));
        methodCol.setPrefWidth(130);

        TableColumn<Contest, Void> editCol = new TableColumn<>("Edit");
        editCol.setPrefWidth(70);
        editCol.setCellFactory(col -> new TableCell<>() {
            private final Button editButton = new Button("Edit");
            { editButton.setOnAction(e -> loadForEdit(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : editButton);
            }
        });

        TableColumn<Contest, Void> deleteCol = new TableColumn<>("Delete");
        deleteCol.setPrefWidth(80);
        deleteCol.setCellFactory(col -> new TableCell<>() {
            private final Button deleteButton = new Button("Delete");
            { deleteButton.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteButton);
            }
        });

        contestTable.getColumns().setAll(List.of(titleCol, electionCol, methodCol, editCol, deleteCol));
    }

    private void buildCandidateColumns() {
        TableColumn<Candidate, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(180);

        TableColumn<Candidate, String> partyCol = new TableColumn<>("Party");
        partyCol.setCellValueFactory(new PropertyValueFactory<>("partyAffiliation"));
        partyCol.setPrefWidth(140);

        TableColumn<Candidate, Boolean> writeInCol = new TableColumn<>("Write-in");
        writeInCol.setCellValueFactory(new PropertyValueFactory<>("writeIn"));
        writeInCol.setPrefWidth(70);

        TableColumn<Candidate, Number> orderCol = new TableColumn<>("Order");
        orderCol.setCellValueFactory(row -> new javafx.beans.property.SimpleIntegerProperty(row.getValue().getDisplayOrder()));
        orderCol.setPrefWidth(70);

        TableColumn<Candidate, Void> editCol = new TableColumn<>("Edit");
        editCol.setPrefWidth(70);
        editCol.setCellFactory(col -> new TableCell<>() {
            private final Button editButton = new Button("Edit");
            { editButton.setOnAction(e -> loadCandidateForEdit(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : editButton);
            }
        });

        TableColumn<Candidate, Void> deleteCol = new TableColumn<>("Delete");
        deleteCol.setPrefWidth(80);
        deleteCol.setCellFactory(col -> new TableCell<>() {
            private final Button deleteButton = new Button("Delete");
            { deleteButton.setOnAction(e -> handleDeleteCandidate(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteButton);
            }
        });

        candidateTable.getColumns().setAll(List.of(nameCol, partyCol, writeInCol, orderCol, editCol, deleteCol));
    }

    private void refreshContests() {
        contestTable.setItems(FXCollections.observableArrayList(contestRepo.findAll()));
    }

    private void refreshCandidates() {
        candidateTable.setItems(editing != null
            ? FXCollections.observableArrayList(editing.getCandidates())
            : FXCollections.observableArrayList());
    }

    private void loadForEdit(Contest c) {
        editing = c;
        formTitleLabel.setText("Edit Contest: " + c.getTitle());
        candidatesTitleLabel.setText("Candidates — " + c.getTitle());
        titleField.setText(c.getTitle());
        if (c.getElection() != null) {
            electionCombo.getSelectionModel().select(c.getElection());
        }
        votingMethodCombo.getSelectionModel().select(c.getVotingMethod());
        maxChoicesSpinner.getValueFactory().setValue(c.getMaxChoices());
        displayOrderSpinner.getValueFactory().setValue(c.getDisplayOrder());
        instructionsField.setText(c.getInstructions() == null ? "" : c.getInstructions());

        selectedRegionIds.clear();
        c.getAssignedRegions().forEach(r -> selectedRegionIds.add(r.getId()));
        loadRegionCandidates();

        regionsBox.setVisible(true);
        regionsBox.setManaged(true);
        candidatesBox.setVisible(true);
        candidatesBox.setManaged(true);

        handleNewCandidate();
        refreshCandidates();
        hideMessage();
    }

    private void loadRegionCandidates() {
        if (editing == null || editing.getElection() == null || editing.getElection().getJurisdiction() == null) {
            regionsListView.setItems(FXCollections.observableArrayList());
            return;
        }
        List<Region> regions = regionRepo.findByJurisdictionIdOrderByName(
            editing.getElection().getJurisdiction().getId());
        regionsListView.setItems(FXCollections.observableArrayList(regions));
    }

    @FXML
    private void handleNew() {
        editing = null;
        formTitleLabel.setText("New Contest");
        titleField.clear();
        if (!electionCombo.getItems().isEmpty()) {
            electionCombo.getSelectionModel().selectFirst();
        }
        votingMethodCombo.getSelectionModel().select(Contest.VotingMethod.PLURALITY);
        maxChoicesSpinner.getValueFactory().setValue(1);
        displayOrderSpinner.getValueFactory().setValue(0);
        instructionsField.clear();
        selectedRegionIds.clear();

        regionsBox.setVisible(false);
        regionsBox.setManaged(false);
        candidatesBox.setVisible(false);
        candidatesBox.setManaged(false);
        hideMessage();
    }

    @FXML
    private void handleSave() {
        String title = titleField.getText() == null ? "" : titleField.getText().trim();
        Election election = electionCombo.getValue();
        Contest.VotingMethod method = votingMethodCombo.getValue();

        if (election == null) {
            showError("Please select an election.");
            return;
        }
        if (title.isEmpty()) {
            showError("Contest title is required.");
            return;
        }
        if (method == null) {
            showError("Please select a voting method.");
            return;
        }

        Contest c = (editing != null) ? editing : new Contest();
        c.setElection(election);
        c.setTitle(title);
        c.setPrintableTitle(title);
        if (c.getRecordTitle() == null || c.getRecordTitle().isBlank()) {
            c.setRecordTitle(title);
        }
        c.setVotingMethod(method);
        c.setMaxChoices(maxChoicesSpinner.getValue());
        c.setDisplayOrder(displayOrderSpinner.getValue());
        c.setInstructions(instructionsField.getText());

        // Convenience: a brand-new Measure contest starts with "Yes"/"No"
        // already in place instead of an empty Candidates screen. Never
        // fires once the contest has any candidates of its own.
        if (contestDefaultsService.needsMeasureDefaults(c)) {
            c.setCandidates(contestDefaultsService.yesNoCandidates(c));
        }

        Contest saved = contestRepo.save(c);
        String verb = (editing != null) ? "Updated" : "Created";
        refreshContests();
        loadForEdit(saved);
        showOk(verb + " contest \"" + saved.getTitle() + "\".");
    }

    @FXML
    private void handleSaveRegions() {
        if (editing == null) {
            return;
        }
        List<Region> assigned = selectedRegionIds.isEmpty()
            ? new ArrayList<>()
            : regionRepo.findAllById(selectedRegionIds);
        editing.setAssignedRegions(assigned);
        contestRepo.save(editing);
        refreshContests();
        showOk("Updated region assignment for \"" + editing.getTitle() + "\" (" + assigned.size() + " region(s)).");
    }

    private void handleDelete(Contest c) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete contest \"" + c.getTitle() + "\"?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait();
        if (confirm.getResult() != ButtonType.YES) {
            return;
        }
        String title = c.getTitle();
        contestRepo.delete(c);
        if (editing != null && editing.getId().equals(c.getId())) {
            handleNew();
        }
        refreshContests();
        showOk("Deleted contest \"" + title + "\".");
    }

    // ── Candidates ────────────────────────────────────────────────────────────

    @FXML
    private void handleNewCandidate() {
        editingCandidate = null;
        candidateFormTitleLabel.setText("Add Candidate");
        candidateNameField.clear();
        candidatePartyField.clear();
        candidateOrderSpinner.getValueFactory().setValue(
            editing != null ? editing.getCandidates().size() + 1 : 1);
        candidateWriteInCheck.setSelected(false);
    }

    private void loadCandidateForEdit(Candidate c) {
        editingCandidate = c;
        candidateFormTitleLabel.setText("Edit Candidate: " + c.getName());
        candidateNameField.setText(c.getName());
        candidatePartyField.setText(c.getPartyAffiliation() == null ? "" : c.getPartyAffiliation());
        candidateOrderSpinner.getValueFactory().setValue(c.getDisplayOrder());
        candidateWriteInCheck.setSelected(c.isWriteIn());
    }

    @FXML
    private void handleSaveCandidate() {
        if (editing == null) {
            showError("Save the contest before adding candidates.");
            return;
        }
        String name = candidateNameField.getText() == null ? "" : candidateNameField.getText().trim();
        if (name.isEmpty()) {
            showError("Name is required (e.g. the candidate's name, or YES / NO for a measure).");
            return;
        }

        Candidate c = (editingCandidate != null) ? editingCandidate : new Candidate();
        c.setContest(editing);
        c.setName(name);
        c.setPrintableName(name);
        if (c.getRecordName() == null || c.getRecordName().isBlank()) {
            c.setRecordName(name);
        }
        c.setWriteIn(candidateWriteInCheck.isSelected());
        String party = candidatePartyField.getText();
        c.setPartyAffiliation(party != null && !party.isBlank() ? party.trim() : null);
        c.setDisplayOrder(candidateOrderSpinner.getValue());

        if (editingCandidate == null) {
            editing.getCandidates().add(c);
        }
        contestRepo.save(editing);

        String verb = (editingCandidate != null) ? "Updated" : "Added";
        refreshCandidates();
        handleNewCandidate();
        showOk(verb + " \"" + name + "\".");
    }

    private void handleDeleteCandidate(Candidate c) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Remove \"" + c.getName() + "\"?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait();
        if (confirm.getResult() != ButtonType.YES) {
            return;
        }
        String name = c.getName();
        editing.getCandidates().remove(c);
        contestRepo.save(editing);
        refreshCandidates();
        showOk("Removed \"" + name + "\".");
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
