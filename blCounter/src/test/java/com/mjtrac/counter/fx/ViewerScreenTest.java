/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 *
 * ViewerScreenTest — confirms the embedded local Viewer web server actually
 * starts and the WebView successfully loads its (unmodified) index page.
 * Deliberately scoped to "does it load" rather than pixel-level canvas
 * correctness — see the blCounter plan's test-first section for why: the
 * homography/canvas JS itself is unchanged code already exercised by hand
 * in the original web app, not new logic this conversion introduced.
 */
package com.mjtrac.counter.fx;

import com.mjtrac.counter.CounterFxApplication;
import javafx.concurrent.Worker;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(ApplicationExtension.class)
class ViewerScreenTest {

    private static ConfigurableApplicationContext springContext;

    @Start
    void start(Stage stage) throws Exception {
        springContext = new SpringApplicationBuilder(CounterFxApplication.class)
            .web(WebApplicationType.SERVLET)
            .properties("server.port=0")
            .profiles("sqlite")
            .run();

        // The port listener fires during context refresh (above), so it's
        // already resolved by the time the Viewer screen loads.
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/viewer.fxml"));
        loader.setControllerFactory(springContext::getBean);
        Parent root = loader.load();
        stage.setScene(new Scene(root, 900, 700));
        stage.show();
    }

    @AfterAll
    static void closeContext() {
        if (springContext != null) springContext.close();
    }

    @Test
    void embeddedViewerServerStartsAndWebViewLoadsIt(FxRobot robot) throws Exception {
        assertThat(springContext.getBean(ViewerServerInfo.class).isReady()).isTrue();

        WebView webView = (WebView) robot.lookup("#webView").query();

        // WebEngine's load-worker state must be read on the FX thread —
        // poll it via interact() rather than binding to the property from
        // the JUnit thread (which throws "Not on FX application thread").
        long deadline = System.currentTimeMillis() + 10_000;
        Worker.State[] state = new Worker.State[1];
        do {
            robot.interact(() -> state[0] = webView.getEngine().getLoadWorker().getState());
            if (state[0] == Worker.State.SUCCEEDED || state[0] == Worker.State.FAILED) break;
            Thread.sleep(100);
        } while (System.currentTimeMillis() < deadline);

        assertThat(state[0]).isEqualTo(Worker.State.SUCCEEDED);
        robot.interact(() -> {
            assertThat(webView.getEngine().getDocument()).isNotNull();
            assertThat(webView.getEngine().getLocation()).contains("/viewer/");
        });
    }
}
