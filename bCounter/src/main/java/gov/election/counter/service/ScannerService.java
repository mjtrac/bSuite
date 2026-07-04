/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Counter — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package gov.election.counter.service;

import gov.election.counter.model.BboxReport;
import gov.election.counter.model.BboxReport.ScanResult;
import gov.election.counter.model.BboxReport.MarkingResult;
import gov.election.counter.model.BboxReport.ContestBox;
import gov.election.counter.model.BboxReport.IndicatorBox;
import gov.election.counter.model.BboxReport.PageLayout;
import gov.election.counter.model.ScanSession;
import gov.election.counter.service.Point2D;
import org.springframework.stereotype.Service;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates the full scanning pipeline for one ballot image.
 *
 * DPI DETECTION (in priority order):
 *   1. PNG pHYs metadata chunk (most accurate — set by the scanner).
 *   2. Heuristic from image width: width_px / assumedWidthIn.
 *      assumedWidthIn defaults to 8.5" but is configurable in the session.
 *      Standard DPI values (72, 96, 150, 200, 300, 400, 600) are snapped to
 *      if the heuristic result is within 10% of a standard value.
 *   3. Fall back to session.dpi (user-supplied default).
 *
 * COORDINATE SYSTEM:
 *   All coordinates in the XML/YAML report are PAGE-ABSOLUTE inches from (0,0).
 *   Multiply by imageDpi to get image pixels matching GIMP's display.
 */
@Service
public class ScannerService {

    private static final Logger log =
        LoggerFactory.getLogger(ScannerService.class);

    /** Standard DPI values to snap to when using the heuristic. */
    private static final int[] STANDARD_DPIS = {72, 96, 150, 200, 300, 400, 600};

    private final BallotIdentifierService barcodeReader;
    private final BallotCornerDetectorService cornerDetector;
    private final HomographyService      homographyService;

    @org.springframework.beans.factory.annotation.Value("${scanner.patch-warp:false}")
    private boolean patchWarp;
    private final MarkerAnalysisService  markerAnalysis;
    private final CoordinateDebugService coordinateDebug;
    private final BboxReportLoader       loader;

    public ScannerService(BallotIdentifierService barcodeReader,
                          BallotCornerDetectorService cornerDetector,
                          HomographyService homographyService,
                          MarkerAnalysisService markerAnalysis,
                          CoordinateDebugService coordinateDebug,
                          BboxReportLoader loader) {
        this.barcodeReader    = barcodeReader;
        this.cornerDetector   = cornerDetector;
        this.homographyService = homographyService;
        this.markerAnalysis   = markerAnalysis;
        this.coordinateDebug  = coordinateDebug;
        this.loader           = loader;
    }

    public ScanResult scanOne(Path imagePath, ScanSession session) {
        ScanResult result = new ScanResult();
        result.imagePath = imagePath.toString();
        result.imageName = imagePath.getFileName().toString();

        // ── Load image + detect DPI ─────────────────────────────────────────
        BufferedImage image;
        double imageDpi;
        try {
            Object[] loaded = loadImageWithDpi(imagePath, session);
            image    = (BufferedImage) loaded[0];
            imageDpi = (double) loaded[1];
        } catch (Exception e) {
            result.errorMessage = "Cannot read image: " + e.getMessage();
            return result;
        }
        if (image == null) {
            result.errorMessage = "Unreadable image format: " + result.imageName;
            return result;
        }
        result.imageDpi = imageDpi;
        log.debug("[{}] imageDpi={}  size={}x{}",
        result.imageName, imageDpi, image.getWidth(), image.getHeight());

        // ── Identify ballot ─────────────────────────────────────────────────
        // Use the injected BallotIdentifierService (default: ZXing barcode reader;
        // may be replaced with OCR, template matching, or composite strategy).
        BallotIdentifierService.BallotIdentification ballotId =
            barcodeReader.identify(image);
        String barcodeData    = ballotId.decoded() ? ballotId.barcodeData() : null;
        result.barcodeData    = ballotId.barcodeData();
        result.barcodeDecoded = ballotId.decoded();
        result.pageNumber     = ballotId.pageNumber();
        double detectedBcX    = ballotId.positionX();
        double detectedBcY    = ballotId.positionY();
        log.debug("[{}] Ballot ID: {} via {} (decoded={})",
            result.imageName, result.barcodeData,
            ballotId.method(), result.barcodeDecoded);

        // ── Select page layout ──────────────────────────────────────────────
        // Load the layout specific to this ballot's barcode combination so that
        // ballots from different precincts/jurisdictions each get their own YAML.
        // Falls back to session-wide layouts if barcode-specific file not found.
        List<PageLayout> resolvedLayouts;
        if (barcodeData != null && !barcodeData.isBlank()
                && session.reportFolder != null && !session.reportFolder.isBlank()) {
            try {
                resolvedLayouts = loader.loadForBarcode(
                    java.nio.file.Paths.get(session.reportFolder), barcodeData);
            } catch (Exception e) {
                log.warn("loadForBarcode failed for" + barcodeData
                    + ": " + e.getMessage() + " -- using session layouts");
                resolvedLayouts = session.layouts;
            }
        } else {
            resolvedLayouts = session.layouts;
        }
        final List<PageLayout> layouts = resolvedLayouts;

        PageLayout layout = layouts.stream()
            .filter(p -> p.pageNumber == result.pageNumber)
            .findFirst()
            .orElse(null);
        if (layout == null) {
            // No layout matched the scanned page number.
            // Do NOT fall back to layouts.get(0) — that would silently apply
            // a different ballot's layout (e.g. Precinct 3 indicators on a
            // Precinct 1 image), creating spurious vote_opportunity rows.
            result.errorMessage = "No layout data for barcode " + barcodeData
                + " page " + result.pageNumber;
            return result;
        }
        // Log barcode and YAML source now that we know which file was used
        String yamlSrc = layout.sourceFile != null ? layout.sourceFile
                       : (session.yamlReportPath != null ? session.yamlReportPath : "(none)");
        session.yamlReportPath = yamlSrc;
        log.debug("[{}] Barcode: {}  —  YAML source: {}",
        result.imageName,
            result.barcodeDecoded ? result.barcodeData : "(not decoded)",
            yamlSrc);

        // ── Detect content box corners ──────────────────────────────────────
        // Pass image in a single-element array so CornerDetectionService can
        // replace it with a 180°-rotated copy if the ballot is upside-down.
        BufferedImage originalImage = image;
        BufferedImage[] imageHolder = new BufferedImage[]{ image };
        Point2D[] corners;
        String cornerFailReason = null;
        try {
            // Compute barcode offset: how far the detected barcode centroid
            // differs from its expected position in the YAML.
            // This shift applies equally to the corner marks.
            double bcOffsetX = 0, bcOffsetY = 0;
            if (detectedBcX >= 0 && detectedBcY >= 0
                    && layout.barcodeCentreX > 0 && layout.barcodeCentreY > 0) {
                double expectedBcX = layout.barcodeCentreX * imageDpi;
                double expectedBcY = layout.barcodeCentreY * imageDpi;
                bcOffsetX = detectedBcX - expectedBcX;
                bcOffsetY = detectedBcY - expectedBcY;
                log.debug(
                    "Barcode offset: dx={}px dy={}px ({}in, {}in)",
                    bcOffsetX, bcOffsetY,
                    bcOffsetX / imageDpi, bcOffsetY / imageDpi);
            }
            corners = cornerDetector.findContentBoxCorners(
                imageHolder, (int) Math.round(imageDpi),
                layout.contentAreaWidth, layout.contentAreaHeight, layout,
                bcOffsetX, bcOffsetY);
            result.cornersFound = corners != null;
        } catch (Exception e) {
            cornerFailReason = e.getMessage() != null ? e.getMessage()
                              : "corner detection error";
            log.warn("[CORNER]" + cornerFailReason);
            corners = null;
            result.cornersFound = false;
        }
        // Use the (possibly rotated) image for all subsequent processing
        image = imageHolder[0];
        // Track whether image was rotated so we can persist it
        result.wasRotated = (image != originalImage);

        // ── Re-read barcode if image was flipped ────────────────────────────
        // The barcode was read from the original (possibly upside-down) image.
        // After a 180° flip, re-read the barcode from the corrected image and
        // reload the YAML layout so all subsequent processing uses the right data.
        if (result.wasRotated) {
            BallotIdentifierService.BallotIdentification flippedId =
                barcodeReader.identifyRotated(image);
            if (flippedId.decoded()
                    && !flippedId.barcodeData().equals(barcodeData)) {
                log.debug("[{}] Re-identified after flip: {} -> {} via {}",
                    result.imageName, barcodeData,
                    flippedId.barcodeData(), flippedId.method());
                barcodeData               = flippedId.barcodeData();
                result.barcodeData        = flippedId.barcodeData();
                result.barcodeDecoded     = true;
                result.pageNumber         = flippedId.pageNumber();
                // Reload layout for the corrected barcode
                if (session.reportFolder != null && !session.reportFolder.isBlank()) {
                    try {
                        List<PageLayout> flippedLayouts = loader.loadForBarcode(
                            java.nio.file.Paths.get(session.reportFolder), flippedId.barcodeData());
                        if (!flippedLayouts.isEmpty()) {
                            final List<PageLayout> fl = flippedLayouts;
                            layout = fl.stream()
                                .filter(p -> p.pageNumber == result.pageNumber)
                                .findFirst()
                                .orElse(null);
                            if (layout == null) {
                                log.warn("[{}] No layout matched page {} after flip — skipping",
                                    result.imageName, result.pageNumber);
                                result.errorMessage = "No layout for page "
                                    + result.pageNumber + " after upside-down correction";
                                return result;
                            }
                            log.debug("[{}] Reloaded layout after flip: {}",
                                result.imageName,
                                layout.sourceFile != null ? layout.sourceFile : "(unknown)");
                        }
                    } catch (Exception e) {
                        log.warn("loadForBarcode after flip failed:" + e.getMessage());
                    }
                }
            } else if (flippedId.decoded()) {
                log.debug("[{}] Barcode unchanged after flip: {}",
        result.imageName, flippedId.barcodeData());
            } else {
                log.warn("[{}] Could not re-read barcode after flip",
        result.imageName);
            }
        }

        // ── Store detected corners ──────────────────────────────────────────
        if (corners != null) {
            result.bboxTLx = (int) Math.round(corners[0].x());
            result.bboxTLy = (int) Math.round(corners[0].y());
            result.bboxTRx = (int) Math.round(corners[1].x());
            result.bboxTRy = (int) Math.round(corners[1].y());
            result.bboxBRx = (int) Math.round(corners[2].x());
            result.bboxBRy = (int) Math.round(corners[2].y());
            result.bboxBLx = (int) Math.round(corners[3].x());
            result.bboxBLy = (int) Math.round(corners[3].y());

            double[] scale = homographyService.computeScaleFactors(
                corners, layout.contentAreaWidth, layout.contentAreaHeight);
            result.detectedDpiX = scale[0];
            result.detectedDpiY = scale[1];

            double ratio = result.detectedDpiX / imageDpi;
            if (ratio > 1.5 || ratio < 0.5) {
                log.warn(
                    "Detected DPI {} vs image DPI {} (ratio={}) — "
                    + "corner detection may have failed.",
                    result.detectedDpiX, imageDpi, ratio);
            } else {
                log.debug(
                    "Detected DPI: X={} Y={}  image DPI={}",
                    result.detectedDpiX, result.detectedDpiY, imageDpi);
            }
        } else {
            result.detectedDpiX = imageDpi;
            result.detectedDpiY = imageDpi;
        }

        // ── Perspective warp (using warpDpi = session.dpi for canonical size) ─
        // The warped image is always session.dpi resolution; imageDpi is only
        // used for pixel↔inch conversion on the original image.
        int warpDpi = session.dpi;
        result.warpDpi           = warpDpi;
        result.contentAreaWidth  = layout.contentAreaWidth;
        result.contentAreaHeight = layout.contentAreaHeight;
        BufferedImage warped;
        if (corners == null) {
            // Corner detection failed — flag ballot for human review and skip counting.
            // Sampling with incorrect corners produces silently wrong vote counts.
            result.homographyValid = false;
            result.errorMessage    = "Corner detection failed"
                + (cornerFailReason != null ? ": " + cornerFailReason : "")
                + " — ballot requires manual review";
            result.cornersFound    = false;
            log.error("[" + result.imageName + "] " + result.errorMessage);
            return result;
        }

        try {
            if (patchWarp) {
                // Patch-warp mode: skip full-image warp; each indicator warps its own patch.
                // Create a 1x1 placeholder — MarkerAnalysisService will use Hinv directly.
                warped = new java.awt.image.BufferedImage(1, 1,
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
                log.debug("[{}] patch-warp mode: skipping full image warp", result.imageName);
            } else {
                warped = homographyService.warpToContentArea(image, corners,
                    layout.contentAreaWidth, layout.contentAreaHeight, warpDpi);
            }
            result.homographyValid = true;
        } catch (Exception e) {
            result.homographyValid = false;
            result.errorMessage    = "Perspective warp failed — ballot requires manual review: "
                + e.getMessage();
            log.error("[" + result.imageName + "] " + result.errorMessage);
            return result;
        }

        // ── Adjusted YAML (debug mode) ──────────────────────────────────────
        coordinateDebug.writeAdjustedYaml(imagePath, session.yamlReportPath,
            layout, corners, session);

        // ── H⁻¹ for debug service (not used for sampling coordinates) ───────
        double[] Hinv = null;
        if (corners != null) {
            Hinv = homographyService.computeCanonicalToImageTransform(
                corners, layout.contentAreaWidth, layout.contentAreaHeight, warpDpi);
        }
        Point2D detectedTL = (corners != null) ? corners[0] : null;

        // ── Analyse each indicator (parallel within ballot) ─────────────────
        // warped is read-only here; markerAnalysis is stateless — safe to parallelise.
        // Results are collected into a thread-safe list then added to result.markings
        // in original order so tally/overvote logic sees a stable ordering.
        final double finalImageDpi = imageDpi;
        final double[] finalHinv   = Hinv;
        final Point2D  finalTL     = detectedTL;
        final int      finalWarpDpi = warpDpi;
        final BufferedImage finalWarped = warped;
        final BufferedImage finalOriginal = image;
        // Final copy of layout for lambda capture (layout may have been reassigned after flip)
        final PageLayout finalLayout = layout;

        List<MarkingResult> allMarkings = finalLayout.contests.stream()
            .flatMap(contest -> contest.indicators.stream()
                .map(indicator -> {
                    MarkingResult marking = markerAnalysis.analyse(
                        finalWarped, finalLayout, contest, indicator,
                        finalWarpDpi, finalImageDpi,
                        session.threshold, session.darkPctMin,
                        finalHinv, finalTL,
                        patchWarp ? finalOriginal : null,
                        patchWarp ? homographyService : null);
                    marking.contestId    = contest.id;
                    marking.contestTitle = contest.title;
                    marking.contestType  = contest.contestType;
                    marking.maxVotes     = contest.maxVotes;
                    marking.writeIn      = indicator.writeIn;
                    return marking;
                }))
            .collect(java.util.stream.Collectors.toList());

        // Switch to parallel analysis if there are enough indicators (> 8).
        // We build a flat indexed list of (contest, indicator) pairs first,
        // then process them in parallel by index, preserving order in the result.
        if (allMarkings.size() > 8) {
            // Build flat list of (contest, indicator) pairs in original order
            java.util.List<Object[]> pairs = new java.util.ArrayList<>();
            for (ContestBox contest : finalLayout.contests)
                for (IndicatorBox indicator : contest.indicators)
                    pairs.add(new Object[]{contest, indicator});

            // Allocate result array indexed by position — guarantees order
            @SuppressWarnings("unchecked")
            MarkingResult[] ordered = new MarkingResult[pairs.size()];

            java.util.stream.IntStream.range(0, pairs.size())
                .parallel()
                .forEach(i -> {
                    ContestBox   contest   = (ContestBox)   pairs.get(i)[0];
                    IndicatorBox indicator = (IndicatorBox) pairs.get(i)[1];
                    try {
                        MarkingResult marking = markerAnalysis.analyse(
                            finalWarped, finalLayout, contest, indicator,
                            finalWarpDpi, finalImageDpi,
                            session.threshold, session.darkPctMin,
                            finalHinv, finalTL);
                        marking.contestId    = contest.id;
                        marking.contestTitle = contest.title;
                        marking.contestType  = contest.contestType;
                        marking.maxVotes     = contest.maxVotes;
                        marking.writeIn      = indicator.writeIn;
                        ordered[i] = marking;
                    } catch (Exception e) {
                        MarkingResult blank  = new MarkingResult();
                        blank.contestId      = contest.id;
                        blank.contestTitle   = contest.title;
                        blank.contestType    = contest.contestType;
                        blank.maxVotes       = contest.maxVotes;
                        blank.writeIn        = indicator.writeIn;
                        blank.candidateName  = indicator.candidateName;
                        blank.candidateId    = indicator.candidateId;
                        blank.marked         = false;
                        blank.darkPct        = 0.0;
                        ordered[i]           = blank;
                        log.warn("Parallel mark analysis failed at index" + i
                            + " (" + contest.title + "/" + indicator.candidateName
                            + "): " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                });

            // Defensive: replace any remaining null slots
            for (int i = 0; i < ordered.length; i++) {
                if (ordered[i] == null) {
                    ordered[i] = new MarkingResult();
                    log.warn("Null slot at index" + i + " after parallel analysis");
                }
            }
            allMarkings = java.util.Arrays.asList(ordered);
        }

        for (MarkingResult marking : allMarkings) {
            result.markings.add(marking);
            session.recordMarking(marking);
        }

        log.info("Scanned {} — barcode={} page={} dpi={} corners={} marks={}/{}",
        result.imageName, result.barcodeData, result.pageNumber, imageDpi,
            result.cornersFound ? "found" : "approx",
            result.markings.stream().filter(m -> m.marked).count(),
            result.markings.size());

        return result;
    }

    // ── DPI detection ───────────────────────────────────────────────────────

    /**
     * Load the image and determine its DPI.
     * Returns Object[]{BufferedImage, Double} where Double is imageDpi.
     */
    private Object[] loadImageWithDpi(Path imagePath, ScanSession session)
            throws Exception {
        double imageDpi = -1;
        BufferedImage image = null;

        String name = imagePath.getFileName().toString().toLowerCase();
        if (name.endsWith(".png")) {
            imageDpi = readPngDpi(imagePath);
        }

        // Load the image regardless
        image = ImageIO.read(imagePath.toFile());
        if (image == null) return new Object[]{null, (double) session.dpi};

        // Convert to TYPE_INT_RGB to ensure thread-safe pixel access.
        // BufferedImage instances loaded from PNG may share ColorModel/SampleModel
        // instances internally; converting to a known concrete type prevents
        // data corruption when multiple threads call getRGB() concurrently.
        if (image.getType() != java.awt.image.BufferedImage.TYPE_INT_RGB) {
            java.awt.image.BufferedImage converted = new java.awt.image.BufferedImage(
                image.getWidth(), image.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2 = converted.createGraphics();
            g2.drawImage(image, 0, 0, null);
            g2.dispose();
            image = converted;
        }

        // If metadata gave us a DPI, use it
        if (imageDpi > 0) {
            log.debug("[{}] DPI from PNG metadata: {}",
        imagePath.getFileName(), imageDpi);
            return new Object[]{image, imageDpi};
        }

        // Heuristic from image width
        double assumedWidth = session.assumedPaperWidthIn;
        if (assumedWidth <= 0) assumedWidth = 8.5;
        double heuristicDpi = image.getWidth() / assumedWidth;
        double snapped = snapToStandardDpi(heuristicDpi);
        log.debug("[{}] DPI from heuristic: {} → snapped to {} (image width={}, assumed={} in)",
        imagePath.getFileName(), heuristicDpi, snapped, image.getWidth(), assumedWidth);

        return new Object[]{image, snapped};
    }

    /**
     * Read the DPI from a PNG file's pHYs metadata chunk via javax.imageio.
     * Returns -1 if not available or not in metres.
     */
    private double readPngDpi(Path path) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(path.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return -1;
            ImageReader reader = readers.next();
            reader.setInput(iis);
            IIOMetadata meta = reader.getImageMetadata(0);
            if (meta == null) return -1;

            // Try the native PNG metadata format
            String[] formats = meta.getMetadataFormatNames();
            for (String fmt : formats) {
                if (!fmt.contains("png") && !fmt.contains("PNG")) continue;
                org.w3c.dom.Element root =
                    (org.w3c.dom.Element) meta.getAsTree(fmt);
                NodeList phys = root.getElementsByTagName("pHYs");
                if (phys.getLength() == 0) continue;
                Node physNode = phys.item(0);
                NamedNodeMap attrs = physNode.getAttributes();
                if (attrs == null) continue;
                Node unitNode = attrs.getNamedItem("unitSpecifier");
                if (unitNode == null) continue;
                String unit = unitNode.getNodeValue();
                if (!"metre".equalsIgnoreCase(unit) && !"1".equals(unit)) continue;
                Node pxX = attrs.getNamedItem("pixelsPerUnitXAxis");
                if (pxX == null) continue;
                double ppm = Double.parseDouble(pxX.getNodeValue());
                // pixels per metre → pixels per inch: divide by 39.3701
                return ppm / 39.3701;
            }

            // Try the standard metadata format as fallback
            try {
                org.w3c.dom.Element root =
                    (org.w3c.dom.Element) meta.getAsTree(
                        "javax_imageio_1.0");
                NodeList dims = root.getElementsByTagName("HorizontalPixelSize");
                if (dims.getLength() > 0) {
                    // HorizontalPixelSize value is in mm per pixel
                    double mmPerPx = Double.parseDouble(
                        dims.item(0).getAttributes()
                            .getNamedItem("value").getNodeValue());
                    if (mmPerPx > 0) return 25.4 / mmPerPx;
                }
            } catch (Exception ignored) {}

        } catch (Exception e) {
            log.debug("PNG metadata read failed:" + e.getMessage());
        }
        return -1;
    }

    /** Snap a computed DPI to the nearest standard value if within 10%. */
    private double snapToStandardDpi(double computed) {
        for (int std : STANDARD_DPIS) {
            if (Math.abs(computed - std) / std < 0.10) return std;
        }
        return Math.round(computed); // no close standard — use rounded value
    }

    private Point2D[] imageFallbackCorners(BufferedImage img) {
        int w = img.getWidth() - 1, h = img.getHeight() - 1;
        return new Point2D[]{
            new Point2D(0, 0), new Point2D(w, 0),
            new Point2D(w, h), new Point2D(0, h)
        };
    }
}
