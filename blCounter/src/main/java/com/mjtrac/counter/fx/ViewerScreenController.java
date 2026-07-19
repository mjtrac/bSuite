/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Counter — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.counter.fx;

import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Hosts the Viewer — unlike every other screen, this one is a real embedded
 * browser (WebView) pointed at this same process's own local Viewer web
 * server (see CounterFxApplication/ViewerServerInfo). The homography/canvas
 * rendering, hover/click hit-testing, and zoom/pan in viewer-view.js are
 * completely unmodified — that's the entire point of this approach (see the
 * blCounter plan's "Viewer approach" decision).
 */
@Component
public class ViewerScreenController {

    private static final Logger log = LoggerFactory.getLogger(ViewerScreenController.class);

    private final ViewerServerInfo viewerServerInfo;

    @FXML private WebView webView;
    @FXML private Label statusLabel;

    public ViewerScreenController(ViewerServerInfo viewerServerInfo) {
        this.viewerServerInfo = viewerServerInfo;
    }

    @FXML
    private void initialize() {
        // A newly-rendered ballot image sometimes doesn't display until a
        // later navigation — see README "Known Issues". Three workarounds
        // (repaint nudge, forced reload, software rendering pipeline) were
        // tried and none resolved it; a stale-but-fully-rendered frame from
        // the *previous* page was confirmed still lingering on screen, which
        // rules out a data/loading failure and points to a WebView/Prism
        // frame-compositing lag with no known fix at this layer. Left as
        // plain logging (no forced reload) since doubling every page load
        // didn't help and just added latency.
        WebEngine engine = webView.getEngine();
        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED || state == Worker.State.FAILED) {
                log.info("Viewer WebView load state {} -> {} | location={} | exception={}",
                    old, state, engine.getLocation(), engine.getLoadWorker().getException());
            }
        });
        load();
    }

    @FXML
    private void handleReload() {
        load();
    }

    private void load() {
        if (!viewerServerInfo.isReady()) {
            statusLabel.setText("Viewer server is still starting…");
            return;
        }
        String url = "http://localhost:" + viewerServerInfo.getPort() + "/viewer/";
        statusLabel.setText(url);
        log.info("Viewer initial load() -> {}", url);
        webView.getEngine().load(url);
    }
}
