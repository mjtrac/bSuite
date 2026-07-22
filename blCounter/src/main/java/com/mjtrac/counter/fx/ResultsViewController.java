/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Counter — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 *
 * Mirrors the removed ResultsController's DB-driven /results query (not the
 * session-tally-based /report — this is the durable view that still works
 * after a restart, since it queries persisted vote records). The
 * precinct/party breakdowns and the separate embedded rcv/scribble HTML
 * report panes are not exposed here — scope note for a follow-up pass.
 */
package com.mjtrac.counter.fx;

import com.mjtrac.counter.service.ResultsQueryService;
import com.mjtrac.counter.service.ResultsQueryService.ContestConfig;
import com.mjtrac.counter.service.ResultsQueryService.VoteRow;
import com.mjtrac.counter.service.WinnerRules;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ResultsViewController {

    private final ResultsQueryService queryService;

    @FXML private VBox printableContent;
    @FXML private Label messageLabel;
    @FXML private Label thresholdLabel;
    @FXML private Label totalBallotsLabel;
    @FXML private Label totalVotesLabel;
    @FXML private Label overvotedLabel;
    @FXML private Label scribbledLabel;
    @FXML private TableView<VoteRow> votesTable;

    public ResultsViewController(ResultsQueryService queryService) {
        this.queryService = queryService;
    }

    @FXML
    private void initialize() {
        hideMessage();
        buildColumns();
        refresh();
    }

    private void buildColumns() {
        TableColumn<VoteRow, String> contestCol = new TableColumn<>("Contest");
        contestCol.setCellValueFactory(new PropertyValueFactory<>("contest"));
        contestCol.setPrefWidth(220);

        TableColumn<VoteRow, String> candidateCol = new TableColumn<>("Candidate");
        candidateCol.setCellValueFactory(new PropertyValueFactory<>("candidate"));
        candidateCol.setPrefWidth(200);

        TableColumn<VoteRow, Number> votedCol = new TableColumn<>("Voted");
        votedCol.setCellValueFactory(row -> new javafx.beans.property.SimpleLongProperty(row.getValue().getVoted()));
        votedCol.setPrefWidth(90);

        TableColumn<VoteRow, Number> overvotedCol = new TableColumn<>("Overvoted");
        overvotedCol.setCellValueFactory(row -> new javafx.beans.property.SimpleLongProperty(row.getValue().getOvervoted()));
        overvotedCol.setPrefWidth(90);

        TableColumn<VoteRow, Number> unmarkedCol = new TableColumn<>("Unmarked");
        unmarkedCol.setCellValueFactory(row -> new javafx.beans.property.SimpleLongProperty(row.getValue().getUnmarked()));
        unmarkedCol.setPrefWidth(90);

        // Same WinnerRules counter-core's HTML reports use — winnerKeys is
        // recomputed fresh in refresh() before setItems(), so this cell
        // factory always reflects the currently-displayed rows.
        TableColumn<VoteRow, String> resultCol = new TableColumn<>("Result");
        resultCol.setCellValueFactory(row -> {
            String key = row.getValue().getContest() + "|" + row.getValue().getCandidate();
            return new javafx.beans.property.SimpleStringProperty(
                winnerKeys.contains(key) ? "✓ WINNER" : "");
        });
        resultCol.setPrefWidth(100);

        votesTable.getColumns().setAll(
            List.of(contestCol, candidateCol, votedCol, overvotedCol, unmarkedCol, resultCol));
    }

    private Set<String> winnerKeys = new HashSet<>();

    @FXML
    private void handleRefresh() {
        refresh();
    }

    /**
     * Prints the summary + votes table as currently shown — javafx.print
     * (not java.awt.print, used by the Swing/scanner-family batch sheets)
     * since printing an actual Node is the natural fit here and this app
     * has no AWT/Swing dependency to reuse.
     */
    @FXML
    private void handlePrintResults() {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            new Alert(Alert.AlertType.ERROR, "No printer available.").showAndWait();
            return;
        }
        Node node = printableContent;
        if (job.showPrintDialog(node.getScene().getWindow())) {
            boolean ok = job.printPage(node);
            if (ok) {
                job.endJob();
            } else {
                new Alert(Alert.AlertType.ERROR, "Printing failed.").showAndWait();
            }
        }
    }

    private void refresh() {
        try {
            List<VoteRow> rows = queryService.votesByContest();
            Map<String, ContestConfig> configByTitle = queryService.contestConfigByTitle();
            winnerKeys = computeWinnerKeys(rows, configByTitle);
            updateThresholdBanner(rows, configByTitle);
            votesTable.setItems(FXCollections.observableArrayList(rows));
            votesTable.refresh();
            totalBallotsLabel.setText(String.valueOf(queryService.totalBallotImages()));
            totalVotesLabel.setText(String.valueOf(queryService.totalVotesCast()));
            overvotedLabel.setText(String.valueOf(queryService.totalOvervoted()));
            scribbledLabel.setText(String.valueOf(queryService.totalScribbled()));
            hideMessage();
        } catch (Exception e) {
            winnerKeys = Set.of();
            votesTable.setItems(FXCollections.observableArrayList());
            totalBallotsLabel.setText("0");
            totalVotesLabel.setText("0");
            overvotedLabel.setText("0");
            scribbledLabel.setText("0");
            thresholdLabel.setVisible(false);
            thresholdLabel.setManaged(false);
            showMessage("No scan results available yet. Run a count first.");
        }
    }

    /**
     * Same WinnerRules counter-core's HTML reports use: top-N-by-count for
     * "vote for N" contests, a strict percentToWin threshold (default 50%)
     * for single-winner PLURALITY/MEASURE contests, not applied to
     * RANKED_CHOICE (which has its own elimination-round winner in
     * rcv_report.html).
     */
    private Set<String> computeWinnerKeys(List<VoteRow> rows, Map<String, ContestConfig> configByTitle) {
        Map<String, Map<String, Long>> votesByContestThenCandidate = new LinkedHashMap<>();
        for (VoteRow r : rows) {
            votesByContestThenCandidate.computeIfAbsent(r.getContest(), k -> new LinkedHashMap<>())
                .merge(r.getCandidate(), r.getVoted(), Long::sum);
        }
        Set<String> winners = new HashSet<>();
        for (Map.Entry<String, Map<String, Long>> ce : votesByContestThenCandidate.entrySet()) {
            ContestConfig cfg = configByTitle.get(ce.getKey());
            boolean fptp = cfg != null
                && ("PLURALITY".equals(cfg.contestType) || "MEASURE".equals(cfg.contestType));
            if (!fptp) continue;
            for (WinnerRules.Ranked rk : WinnerRules.rank(ce.getValue(), cfg.maxVotes, cfg.percentToWin)) {
                if (rk.winner()) winners.add(ce.getKey() + "|" + rk.name());
            }
        }
        return winners;
    }

    /** Warns above the table when any contest's win threshold isn't the plain 50% majority default. */
    private void updateThresholdBanner(List<VoteRow> rows, Map<String, ContestConfig> configByTitle) {
        List<String> seen = new ArrayList<>();
        List<String> nonDefault = new ArrayList<>();
        for (VoteRow r : rows) {
            if (seen.contains(r.getContest())) continue;
            seen.add(r.getContest());
            ContestConfig cfg = configByTitle.get(r.getContest());
            double pct = cfg != null ? cfg.percentToWin : 50.0;
            if (pct != 50.0) nonDefault.add(r.getContest() + " requires more than " + pct + "% to win");
        }
        if (nonDefault.isEmpty()) {
            thresholdLabel.setVisible(false);
            thresholdLabel.setManaged(false);
        } else {
            thresholdLabel.setText("Non-default win threshold: " + String.join("; ", nonDefault));
            thresholdLabel.setVisible(true);
            thresholdLabel.setManaged(true);
        }
    }

    private void showMessage(String text) {
        messageLabel.setText(text);
        messageLabel.setVisible(true);
        messageLabel.setManaged(true);
    }

    private void hideMessage() {
        messageLabel.setVisible(false);
        messageLabel.setManaged(false);
    }
}
