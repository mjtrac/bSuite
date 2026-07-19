/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Ballot System — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.ballot.fx;

import com.mjtrac.ballot.model.BallotDesignTemplate;
import com.mjtrac.ballot.model.Election;
import com.mjtrac.ballot.repository.BallotDesignTemplateRepository;
import com.mjtrac.ballot.repository.ElectionRepository;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mirrors data/ballot-templates/list.html + form.html + the removed
 * BallotDesignTemplateController — minus Quill. The web version had a dual
 * raw-HTML/Quill-WYSIWYG editor for headerHtml; here it's just the raw-HTML
 * textarea (the actual form field the Quill pane always wrote back into) plus
 * the same "Example snippets" quick-insert buttons, ported verbatim from the
 * removed header-editor.js's HEADER_SNIPPETS.
 *
 * Scope note: BallotDesignTemplate has ~25 additional fine-grained per-text-type
 * font-size/bold/italic/alt-font fields (see the model) that this screen does
 * not expose. Because saving here mutates the existing managed entity in place
 * rather than reconstructing it from a full field list, editing a template
 * through this screen leaves those fields at whatever they already were —
 * it can't blow them away the way a naive full-object save could.
 */
@Component
public class BallotDesignTemplateViewController {

    private final BallotDesignTemplateRepository templateRepo;
    private final ElectionRepository electionRepo;

    @FXML private Label messageLabel;
    @FXML private Label formTitleLabel;
    @FXML private TableView<BallotDesignTemplate> templateTable;
    @FXML private ComboBox<Election> electionCombo;
    @FXML private ComboBox<BallotDesignTemplate.PaperSize> paperSizeCombo;
    @FXML private Spinner<Integer> columnsSpinner;
    @FXML private ComboBox<BallotDesignTemplate.VoteIndicatorStyle> indicatorStyleCombo;
    @FXML private Spinner<Double> barcodeHeightSpinner;
    @FXML private TextArea headerHtmlArea;

    private BallotDesignTemplate editing;

    private static final String SNIPPET_DEFAULT =
        "<div style=\"font-family:Helvetica,Arial,sans-serif;padding:4px 0\">\n"
        + "  <p style=\"font-size:13pt;font-weight:bold;line-height:1.6\">OFFICIAL BALLOT</p>\n"
        + "  <p style=\"font-size:9pt;line-height:1.4\">{jurisdictionName}</p>\n"
        + "  <p style=\"font-size:9pt;line-height:1.8\">{electionName}</p>\n"
        + "  <p style=\"font-size:9pt;font-weight:bold;line-height:1.4\">HOW TO VOTE:</p>\n"
        + "  <p style=\"font-size:9pt;line-height:1.4\">To vote, completely fill in the {indicatorName} next to your choice.</p>\n"
        + "</div>";

    private static final String SNIPPET_TWOCOL =
        "<table style=\"width:100%;border-collapse:collapse;font-family:Helvetica,Arial,sans-serif\">\n"
        + "  <tr>\n"
        + "    <td style=\"width:60pt;vertical-align:middle;padding-right:6pt\">\n"
        + "      <img src=\"data:image/png;base64,REPLACE_WITH_BASE64\" style=\"max-width:54pt;max-height:54pt\"/>\n"
        + "    </td>\n"
        + "    <td style=\"vertical-align:top\">\n"
        + "      <p style=\"font-size:13pt;font-weight:bold;line-height:1.6\">OFFICIAL BALLOT</p>\n"
        + "      <p style=\"font-size:9pt;line-height:1.4\">{jurisdictionName} | {electionName}</p>\n"
        + "      <p style=\"font-size:9pt;line-height:1.4\">Fill the {indicatorName} completely next to your choice.</p>\n"
        + "    </td>\n"
        + "  </tr>\n"
        + "</table>";

    private static final String SNIPPET_MINIMAL =
        "<div style=\"font-family:Helvetica,Arial,sans-serif;padding:4px 0\">\n"
        + "  <p style=\"font-size:11pt;font-weight:bold;line-height:1.6\">OFFICIAL BALLOT</p>\n"
        + "  <p style=\"font-size:9pt;line-height:1.4\">{electionName}</p>\n"
        + "</div>";

    private static final String SNIPPET_TABLE =
        "<table style=\"width:100%;border-collapse:collapse;font-family:Helvetica,Arial,sans-serif;font-size:9pt\">\n"
        + "  <tr><td colspan=\"2\" style=\"font-size:13pt;font-weight:bold;line-height:1.6\">OFFICIAL BALLOT</td></tr>\n"
        + "  <tr>\n"
        + "    <td style=\"width:50%;vertical-align:top;padding-right:6pt\">\n"
        + "      <strong>{jurisdictionName}</strong><br/>{electionName}\n"
        + "    </td>\n"
        + "    <td style=\"vertical-align:top\">\n"
        + "      <strong>HOW TO VOTE:</strong><br/>\n"
        + "      Fill the {indicatorName} next to your choice.\n"
        + "    </td>\n"
        + "  </tr>\n"
        + "</table>";

    public BallotDesignTemplateViewController(BallotDesignTemplateRepository templateRepo,
                                               ElectionRepository electionRepo) {
        this.templateRepo = templateRepo;
        this.electionRepo = electionRepo;
    }

    @FXML
    private void initialize() {
        electionCombo.setConverter(nameConverter(Election::getName));
        electionCombo.setItems(FXCollections.observableArrayList(electionRepo.findAll()));

        paperSizeCombo.setItems(FXCollections.observableArrayList(BallotDesignTemplate.PaperSize.values()));
        indicatorStyleCombo.setItems(FXCollections.observableArrayList(BallotDesignTemplate.VoteIndicatorStyle.values()));

        columnsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 6, 3));
        barcodeHeightSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0, 300, 72, 6));

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

    private void buildColumns() {
        TableColumn<BallotDesignTemplate, String> electionCol = new TableColumn<>("Election");
        electionCol.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(
            row.getValue().getElection() != null ? row.getValue().getElection().getName() : ""));
        electionCol.setPrefWidth(200);

        TableColumn<BallotDesignTemplate, String> paperCol = new TableColumn<>("Paper Size");
        paperCol.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(
            row.getValue().getPaperSize() == null ? "" : row.getValue().getPaperSize().name()));
        paperCol.setPrefWidth(160);

        TableColumn<BallotDesignTemplate, Number> columnsCol = new TableColumn<>("Columns");
        columnsCol.setCellValueFactory(row -> new javafx.beans.property.SimpleIntegerProperty(row.getValue().getColumns()));
        columnsCol.setPrefWidth(80);

        TableColumn<BallotDesignTemplate, String> indicatorCol = new TableColumn<>("Indicator");
        indicatorCol.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(
            row.getValue().getVoteIndicatorStyle() == null ? "" : row.getValue().getVoteIndicatorStyle().name()));
        indicatorCol.setPrefWidth(120);

        TableColumn<BallotDesignTemplate, Void> editCol = new TableColumn<>("Edit");
        editCol.setPrefWidth(70);
        editCol.setCellFactory(col -> new TableCell<>() {
            private final Button editButton = new Button("Edit");
            { editButton.setOnAction(e -> loadForEdit(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : editButton);
            }
        });

        TableColumn<BallotDesignTemplate, Void> deleteCol = new TableColumn<>("Delete");
        deleteCol.setPrefWidth(80);
        deleteCol.setCellFactory(col -> new TableCell<>() {
            private final Button deleteButton = new Button("Delete");
            { deleteButton.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteButton);
            }
        });

        templateTable.getColumns().setAll(List.of(electionCol, paperCol, columnsCol, indicatorCol, editCol, deleteCol));
    }

    private void refresh() {
        templateTable.setItems(FXCollections.observableArrayList(templateRepo.findAll()));
    }

    private void loadForEdit(BallotDesignTemplate t) {
        editing = t;
        formTitleLabel.setText("Edit Ballot Template");
        if (t.getElection() != null) {
            electionCombo.getSelectionModel().select(t.getElection());
        }
        paperSizeCombo.getSelectionModel().select(t.getPaperSize());
        columnsSpinner.getValueFactory().setValue(t.getColumns());
        indicatorStyleCombo.getSelectionModel().select(t.getVoteIndicatorStyle());
        barcodeHeightSpinner.getValueFactory().setValue((double) t.getBarcodeHeightPt());
        headerHtmlArea.setText(t.getHeaderHtml());
        hideMessage();
    }

    @FXML
    private void handleNew() {
        editing = null;
        formTitleLabel.setText("New Ballot Template");
        if (!electionCombo.getItems().isEmpty()) {
            electionCombo.getSelectionModel().selectFirst();
        }
        paperSizeCombo.getSelectionModel().select(BallotDesignTemplate.PaperSize.LETTER_8_5x11);
        columnsSpinner.getValueFactory().setValue(3);
        indicatorStyleCombo.getSelectionModel().select(BallotDesignTemplate.VoteIndicatorStyle.OVAL);
        barcodeHeightSpinner.getValueFactory().setValue(72.0);
        headerHtmlArea.clear();
        hideMessage();
    }

    @FXML
    private void handleSave() {
        Election election = electionCombo.getValue();
        BallotDesignTemplate.PaperSize paperSize = paperSizeCombo.getValue();
        BallotDesignTemplate.VoteIndicatorStyle indicatorStyle = indicatorStyleCombo.getValue();

        if (election == null) {
            showError("Please select an election.");
            return;
        }
        if (paperSize == null) {
            showError("Please select a paper size.");
            return;
        }
        if (indicatorStyle == null) {
            showError("Please select a vote indicator style.");
            return;
        }

        BallotDesignTemplate t = (editing != null) ? editing : new BallotDesignTemplate();
        t.setElection(election);
        t.setPaperSize(paperSize);
        t.setColumns(columnsSpinner.getValue());
        t.setVoteIndicatorStyle(indicatorStyle);
        t.setBarcodeHeightPt(barcodeHeightSpinner.getValue().floatValue());
        String headerHtml = headerHtmlArea.getText();
        t.setHeaderHtml(headerHtml != null && !headerHtml.isBlank() ? headerHtml : null);

        templateRepo.save(t);
        String verb = (editing != null) ? "Updated" : "Created";
        refresh();
        handleNew();
        showOk(verb + " ballot template for \"" + election.getName() + "\".");
    }

    private void handleDelete(BallotDesignTemplate t) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete this ballot template?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait();
        if (confirm.getResult() != ButtonType.YES) {
            return;
        }
        try {
            templateRepo.delete(t);
            refresh();
            showOk("Deleted ballot template.");
        } catch (Exception e) {
            showError("Could not delete: " + e.getMessage());
        }
    }

    @FXML private void insertDefaultSnippet() { headerHtmlArea.setText(SNIPPET_DEFAULT); }
    @FXML private void insertTwoColSnippet()  { headerHtmlArea.setText(SNIPPET_TWOCOL); }
    @FXML private void insertMinimalSnippet() { headerHtmlArea.setText(SNIPPET_MINIMAL); }
    @FXML private void insertTableSnippet()   { headerHtmlArea.setText(SNIPPET_TABLE); }

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
