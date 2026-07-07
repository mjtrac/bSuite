/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 */
package gov.election.ballot.controller;

import gov.election.ballot.model.*;
import gov.election.ballot.repository.*;
import gov.election.ballot.service.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/print")
public class BallotController {

    private final BallotCombinationRepository    combinationRepo;
    private final BallotDesignTemplateRepository templateRepo;
    private final BallotLanguageRepository        langRepo;
    private final UserRepository                 userRepo;
    private final ElectionRepository             electionRepo;
    private final BallotGenerationService        ballotService;
    private final ExportService                  exportService;
    private final PrinterService                 printerService;

    public BallotController(BallotCombinationRepository combinationRepo,
                             BallotDesignTemplateRepository templateRepo,
                             BallotLanguageRepository langRepo,
                             UserRepository userRepo,
                             ElectionRepository electionRepo,
                             BallotGenerationService ballotService,
                             ExportService exportService,
                             PrinterService printerService) {
        this.combinationRepo = combinationRepo;
        this.templateRepo    = templateRepo;
        this.langRepo        = langRepo;
        this.userRepo        = userRepo;
        this.electionRepo    = electionRepo;
        this.ballotService   = ballotService;
        this.exportService   = exportService;
        this.printerService  = printerService;
    }

    // ── Single-ballot form ─────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PRINTER')")
    public String printForm(Model model) {
        // Filter out any combinations whose election was deleted (orphaned rows)
        var allCombos = combinationRepo.findAll().stream()
            .filter(c -> c.getElection() != null)
            .toList();
        model.addAttribute("combinations", allCombos);

        if (allCombos.isEmpty()) {
            model.addAttribute("info",
                "No ballot combinations are available. " +
                "Create an election and ballot combination first.");
            return "print/form";
        }

        allCombos.stream()
            .map(c -> c.getElection().getJurisdiction())
            .filter(j -> j != null)
            .findFirst()
            .ifPresent(j -> model.addAttribute("languages",
                langRepo.findByJurisdictionIdOrderByDisplayOrderAsc(j.getId())));
        return "print/form";
    }

    // ── Single-ballot generation (extended with optional print) ───────────

    @PostMapping(value = "/generate", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','PRINTER')")
    public ResponseEntity<byte[]> generate(
            @RequestParam(required = false) Long combinationId,
            @RequestParam(required = false, defaultValue = "en") String lang,
            @RequestParam(defaultValue = "1") int copies,
            @RequestParam(required = false) String printerName,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {

        if (combinationId == null) {
            return ResponseEntity.badRequest().build();
        }

        BallotCombination combo = combinationRepo.findById(combinationId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Invalid combination ID: " + combinationId));

        BallotDesignTemplate template = templateRepo
            .findFirstByElectionIdOrderByIdAsc(combo.getElection().getId())
            .orElseThrow(() -> new IllegalStateException(
                "No design template found for election \"" +
                combo.getElection().getName() + "\"."));

        User user = userRepo.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new IllegalStateException(
                "Authenticated user not found in database."));

        byte[] pdf = ballotService.generateBallot(combo, template, user, copies, lang);

        // ── Optional: send to printer ──────────────────────────────────────
        if (printerName != null && !printerName.isBlank()) {
            printerService.printPdf(pdf, printerName, buildJobName(combo));
        }

        List<String> written = ballotService.getLastWrittenFiles();
        String filesHeader = String.join("|", written);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"ballot-" + combinationId + ".pdf\"")
            .header("X-Ballot-Files", filesHeader)
            .header("Access-Control-Expose-Headers", "X-Ballot-Files")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }

    // ── Printer discovery ──────────────────────────────────────────────────

    @GetMapping(value = "/printers", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','PRINTER')")
    @ResponseBody
    public PrinterListResponse listPrinters() {
        return new PrinterListResponse(
            printerService.listPrinters(),
            printerService.getDefaultPrinterName());
    }

    public record PrinterListResponse(List<String> printers, String defaultPrinter) {}

    // ── Bulk ballot generation ─────────────────────────────────────────────

    public record BulkBallotResult(
        long   combinationId,
        String regionName,
        String partyName,
        String ballotTypeName,
        boolean success,
        List<String> filesWritten,
        String errorMessage
    ) {}

    @GetMapping("/generate-all")
    @PreAuthorize("hasAnyRole('ADMIN','PRINTER')")
    public String generateAllForm(Model model) {
        model.addAttribute("elections", electionRepo.findAll());
        return "print/generate-all";
    }

    @PostMapping("/generate-all")
    @PreAuthorize("hasAnyRole('ADMIN','PRINTER')")
    public String generateAll(
            @RequestParam Long electionId,
            @RequestParam(defaultValue = "en") String lang,
            @RequestParam(required = false) String printerName,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {

        Election election = electionRepo.findById(electionId).orElse(null);
        if (election == null) {
            model.addAttribute("elections", electionRepo.findAll());
            model.addAttribute("error", "Election not found.");
            return "print/generate-all";
        }

        BallotDesignTemplate template = templateRepo
            .findFirstByElectionIdOrderByIdAsc(electionId)
            .orElse(null);
        if (template == null) {
            model.addAttribute("elections", electionRepo.findAll());
            model.addAttribute("error",
                "No ballot design template found for \"" + election.getName() +
                "\". Create a template for this election first.");
            return "print/generate-all";
        }

        User user = userRepo.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new IllegalStateException(
                "Authenticated user not found in database."));

        List<BallotCombination> combinations =
            combinationRepo.findByElectionId(electionId);

        if (combinations.isEmpty()) {
            model.addAttribute("elections", electionRepo.findAll());
            model.addAttribute("error",
                "No ballot combinations found for \"" + election.getName() +
                "\". Create at least one ballot combination first.");
            return "print/generate-all";
        }

        boolean printing = printerName != null && !printerName.isBlank();
        List<BulkBallotResult> results = new ArrayList<>();
        int successCount = 0, failCount = 0;

        for (BallotCombination combo : combinations) {
            String regionName     = combo.getRegion()     != null ? combo.getRegion().getName()     : "?";
            String partyName      = combo.getParty()      != null ? combo.getParty().getName()      : "—";
            String ballotTypeName = combo.getBallotType() != null ? combo.getBallotType().getName() : "?";

            try {
                byte[] pdf = ballotService.generateBallot(combo, template, user, 1, lang);
                List<String> written = new ArrayList<>(ballotService.getLastWrittenFiles());
                if (printing) {
                    printerService.printPdf(pdf, printerName, buildJobName(combo));
                }
                results.add(new BulkBallotResult(
                    combo.getId(), regionName, partyName, ballotTypeName,
                    true, written, null));
                successCount++;
            } catch (Exception e) {
                results.add(new BulkBallotResult(
                    combo.getId(), regionName, partyName, ballotTypeName,
                    false, List.of(), e.getMessage()));
                failCount++;
            }
        }

        String exportDir = results.stream()
            .filter(BulkBallotResult::success)
            .flatMap(r -> r.filesWritten().stream())
            .findFirst()
            .map(f -> java.nio.file.Paths.get(f).getParent().toString())
            .orElse("");

        model.addAttribute("elections",    electionRepo.findAll());
        model.addAttribute("election",     election);
        model.addAttribute("results",      results);
        model.addAttribute("successCount", successCount);
        model.addAttribute("failCount",    failCount);
        model.addAttribute("exportDir",    exportDir);
        model.addAttribute("printerUsed",  printing ? printerName : null);
        return "print/generate-all";
    }

    // ── Export endpoints (unchanged) ───────────────────────────────────────

    @GetMapping("/export/xml/{combinationId}")
    @PreAuthorize("hasAnyRole('ADMIN','DATA_ENTRY')")
    public ResponseEntity<String> exportXml(
            @PathVariable Long combinationId,
            @RequestParam(defaultValue = "INCHES") ExportService.MeasurementUnit unit) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_XML)
            .body(exportService.exportOffsetReportXml(combinationId, unit));
    }

    @GetMapping("/export/yaml/{combinationId}")
    @PreAuthorize("hasAnyRole('ADMIN','DATA_ENTRY')")
    public ResponseEntity<String> exportYaml(
            @PathVariable Long combinationId,
            @RequestParam(defaultValue = "INCHES") ExportService.MeasurementUnit unit) {
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .body(exportService.exportOffsetReportYaml(combinationId, unit));
    }

    @GetMapping("/ocr-names/{electionId}")
    @PreAuthorize("hasAnyRole('ADMIN','DATA_ENTRY')")
    public ResponseEntity<String> ocrNames(
            @PathVariable Long electionId,
            @RequestParam(required = false) Long regionId) {
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .body(exportService.buildOcrNameList(electionId, regionId));
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private String buildJobName(BallotCombination combo) {
        StringBuilder sb = new StringBuilder();
        if (combo.getRegion() != null)
            sb.append(combo.getRegion().getName());
        if (combo.getParty() != null)
            sb.append(" / ").append(combo.getParty().getName());
        sb.append(" [")
          .append(combo.getElection() != null ? combo.getElection().getName() : "Election")
          .append(']');
        return sb.toString();
    }
}
