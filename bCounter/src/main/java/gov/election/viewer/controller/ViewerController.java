/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 */
package gov.election.viewer.controller;

import gov.election.viewer.service.BallotViewService;
import gov.election.viewer.service.BallotViewService.BallotImageSummary;
import gov.election.viewer.service.BallotViewService.BallotView;
import gov.election.viewer.service.BallotViewService.IndicatorBox;
import gov.election.viewer.service.ViewerFilterSession;
import gov.election.viewer.service.ViewerFilterSession.FilterType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.nio.file.*;
import java.util.*;

@Controller
public class ViewerController {

    static final String SESSION_FILTER_KEY = "viewerFilter";

    private final BallotViewService viewService;

    @Value("${reports.output.dir:${user.dir}}")
    private String reportsOutputDir;

    public ViewerController(BallotViewService viewService) {
        this.viewService = viewService;
    }

    // ── Home — image list ─────────────────────────────────────────────────────

    @GetMapping({"/viewer/", "/viewer"})
    public String index(HttpSession session, Model model) {
        ViewerFilterSession filter = getFilter(session);
        List<BallotImageSummary> images;

        if (filter.isActive()) {
            images = viewService.listByIds(filter.filteredIds);
        } else {
            images = viewService.listAll();
        }

        model.addAttribute("images",  images);
        model.addAttribute("count",   viewService.listAll().size());
        addFilterModel(session, model);
        return "viewer/index";
    }

    // ── Results report ────────────────────────────────────────────────────────

    /**
     * Serves results_report.html written by VoteTallyService, wrapped in the
     * viewer header so the user can navigate back to the ballot list without
     * needing to authenticate on port 8081.
     */
    @GetMapping("/viewer/report")
    @ResponseBody
    public ResponseEntity<String> report() {
        Path reportFile = Paths.get(reportsOutputDir, "results_report.html");
        if (!Files.exists(reportFile)) {
            String notFound =
                "<!DOCTYPE html><html><head><meta charset='UTF-8'/>" +
                "<title>Results</title></head><body style='font-family:sans-serif;padding:2rem'>" +
                "<a href='/viewer/' style='display:inline-block;margin-bottom:1.5rem;" +
                "padding:.4rem .9rem;background:#1a2744;color:#fff;text-decoration:none;" +
                "border-radius:4px;font-size:.9rem'>&#9664; Ballot List</a>" +
                "<p>No results report found. Run a scan first.</p></body></html>";
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(notFound);
        }
        try {
            String html = Files.readString(reportFile);
            // Inject a "Back to Ballot List" bar just after <body>
            String backBar =
                "<div style='background:#1a2744;padding:.5rem 1rem;margin-bottom:1rem'>" +
                "<a href='/viewer/' style='color:#fff;text-decoration:none;font-size:.9rem;" +
                "font-family:sans-serif'>&#9664; Ballot List</a>" +
                "<span style='color:#94a3b8;font-size:.85rem;margin-left:1rem;" +
                "font-family:sans-serif'>bSuite Ballot Viewer</span></div>";
            int bodyEnd = html.indexOf("<body");
            if (bodyEnd >= 0) {
                int insertAt = html.indexOf('>', bodyEnd) + 1;
                html = html.substring(0, insertAt) + backBar + html.substring(insertAt);
            }
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body("<p>Could not read report: " + e.getMessage() + "</p>");
        }
    }

    // ── Apply filter ──────────────────────────────────────────────────────────

    @PostMapping("/viewer/filter")
    public String applyFilter(
            @RequestParam(required = false) String filterType,
            @RequestParam(required = false) String filterValue,
            HttpSession session,
            Model model) {

        if (filterValue == null || filterValue.isBlank()) {
            session.removeAttribute(SESSION_FILTER_KEY);
            return "redirect:/viewer";
        }

        ViewerFilterSession filter;
        try {
            if ("sql".equalsIgnoreCase(filterType)) {
                List<BallotImageSummary> results =
                    viewService.listBySql(filterValue.trim());
                List<Long> ids = results.stream()
                    .map(BallotImageSummary::getId).toList();
                filter = new ViewerFilterSession(
                    FilterType.SQL,
                    filterValue.trim(),
                    "SQL: " + filterValue.trim(),
                    ids);
            } else {
                List<BallotImageSummary> results =
                    viewService.listByGlob(filterValue.trim());
                List<Long> ids = results.stream()
                    .map(BallotImageSummary::getId).toList();
                filter = new ViewerFilterSession(
                    FilterType.GLOB,
                    filterValue.trim(),
                    "Name filter: " + filterValue.trim(),
                    ids);
            }
            session.setAttribute(SESSION_FILTER_KEY, filter);
        } catch (IllegalArgumentException e) {
            List<BallotImageSummary> images = viewService.listAll();
            model.addAttribute("images",       images);
            model.addAttribute("count",        images.size());
            model.addAttribute("filterError",  e.getMessage());
            model.addAttribute("filterType",   filterType);
            model.addAttribute("filterValue",  filterValue);
            addFilterModel(session, model);
            return "viewer/index";
        }

        return "redirect:/viewer";
    }

    // ── Clear filter ──────────────────────────────────────────────────────────

    @PostMapping("/viewer/clear-filter")
    public String clearFilter(HttpSession session) {
        session.removeAttribute(SESSION_FILTER_KEY);
        return "redirect:/viewer";
    }

    // ── View a ballot with overlays ───────────────────────────────────────────

    @GetMapping("/viewer/view")
    public String view(
            @RequestParam(required = false) Long   id,
            @RequestParam(required = false) String path,
            HttpSession session,
            Model model) {

        Optional<BallotView> bv = Optional.empty();
        if (id   != null) bv = viewService.findById(id);
        else if (path != null) bv = viewService.findByPath(path);

        if (bv.isEmpty()) {
            model.addAttribute("error",
                "Ballot image not found in database."
                + (id   != null ? " (id="   + id   + ")" : "")
                + (path != null ? " (path=" + path + ")" : ""));
            return "viewer/view";
        }

        BallotView ballot = bv.get();
        model.addAttribute("ballot", ballot);

        Map<String, List<IndicatorBox>> byContest = new LinkedHashMap<>();
        for (IndicatorBox box : ballot.boxes)
            byContest.computeIfAbsent(box.contest, k -> new ArrayList<>()).add(box);
        model.addAttribute("byContest", byContest);

        ViewerFilterSession filter = getFilter(session);
        List<Long> navIds;

        if (filter.isActive()) {
            navIds = filter.filteredIds;
        } else {
            navIds = viewService.listAll().stream()
                .map(BallotImageSummary::getId).toList();
        }

        Long prevId = null, nextId = null;
        int pos = -1;
        for (int i = 0; i < navIds.size(); i++) {
            if (navIds.get(i).equals(ballot.imageId)) { pos = i; break; }
        }
        if (pos > 0)                prevId = navIds.get(pos - 1);
        if (pos < navIds.size() - 1) nextId = navIds.get(pos + 1);

        model.addAttribute("prevId",       prevId);
        model.addAttribute("nextId",       nextId);
        model.addAttribute("position",     pos >= 0 ? pos + 1 : 1);
        model.addAttribute("total",        navIds.size());
        model.addAttribute("filterActive", filter.isActive());
        model.addAttribute("filterDesc",   filter.filterDesc);

        return "viewer/view";
    }

    // ── Serve the image file ──────────────────────────────────────────────────

    @GetMapping("/viewer/image")
    public ResponseEntity<byte[]> image(@RequestParam Long id) throws Exception {
        Optional<BallotView> bv = viewService.findById(id);
        if (bv.isEmpty()) return ResponseEntity.notFound().build();

        String storedPath = bv.get().imagePath;
        if (storedPath == null) return ResponseEntity.notFound().build();

        Path file = Paths.get(storedPath);
        if (!Files.exists(file)) {
            Path counted1 = Paths.get(storedPath + ".counted");
            String counted2str = storedPath.replaceAll(
                "\\.(png|jpg|jpeg|tif|tiff|bmp)$", ".counted");
            Path counted2 = Paths.get(counted2str);
            if (Files.exists(counted1))      file = counted1;
            else if (Files.exists(counted2)) file = counted2;
            else return ResponseEntity.notFound().build();
        }

        byte[] bytes = Files.readAllBytes(file);
        String name  = file.getFileName().toString().toLowerCase();
        MediaType mt = name.contains(".png") || name.contains(".counted")
                                              ? MediaType.IMAGE_PNG
                     : name.contains(".jpg") || name.contains(".jpeg")
                                              ? MediaType.IMAGE_JPEG
                     : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok()
            .contentType(mt)
            .cacheControl(CacheControl.noCache())
            .body(bytes);
    }

    // ── JSON overlay data ─────────────────────────────────────────────────────

    @GetMapping("/viewer/boxes")
    @ResponseBody
    public List<IndicatorBox> boxes(@RequestParam Long id) {
        return viewService.findById(id)
            .map(bv -> bv.boxes)
            .orElse(List.of());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ViewerFilterSession getFilter(HttpSession session) {
        Object f = session.getAttribute(SESSION_FILTER_KEY);
        return (f instanceof ViewerFilterSession vfs) ? vfs : ViewerFilterSession.NONE;
    }

    private void addFilterModel(HttpSession session, Model model) {
        ViewerFilterSession filter = getFilter(session);
        model.addAttribute("filterActive", filter.isActive());
        model.addAttribute("filterDesc",   filter.filterDesc);
        model.addAttribute("filterType",   filter.filterType.name().toLowerCase());
        model.addAttribute("filterValue",  filter.filterValue);
        model.addAttribute("filteredCount",
            filter.isActive() ? filter.size() : -1);
    }
}
