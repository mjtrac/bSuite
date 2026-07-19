/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Counter — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.counter;

import com.mjtrac.counter.fx.Navigator;
import com.mjtrac.counter.fx.ShellController;
import com.mjtrac.counter.fx.ViewerServerInfo;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JavaFX entry point. Unlike blScanner/blBuilder, this one boots Spring in
 * WebApplicationType.SERVLET (not NONE) mode: the Viewer screen embeds its
 * unmodified web UI (viewer-view.js's homography/canvas code, kept exactly
 * as it was) inside a JavaFX WebView, pointed at this same process's own
 * embedded Tomcat on server.port=0 (an OS-assigned free port, localhost
 * only). Since CountController/AdminController/etc. were deleted, only
 * com.mjtrac.viewer's routes remain registered, so nothing else is
 * reachable through that server. Kept at the package root (not a .fx
 * subpackage) for the same @SpringBootTest config-discovery reason as
 * blBuilder's BBuilderFxApplication — every existing test here lives in
 * com.mjtrac.counter.
 */
@SpringBootApplication(scanBasePackages = {"com.mjtrac.counter", "com.mjtrac.viewer"})
@EntityScan("com.mjtrac.counter.entity")
@EnableJpaRepositories({"com.mjtrac.counter.repository"})
public class CounterFxApplication extends Application {

    private ConfigurableApplicationContext springContext;
    private Stage primaryStage;
    private ShellController shellController;

    // No passwordEncoder() @Bean here — unlike blScanner/blBuilder, bCounter
    // already had one in a standalone PasswordEncoderConfig (kept separate
    // from the security configs specifically to avoid a circular dependency
    // through CounterUserService), so it survived the controller/security
    // cleanup unchanged. A second definition here would just collide with it.

    /** Captures the OS-assigned port once the embedded Viewer server has actually bound it. */
    @Bean
    public ApplicationListener<WebServerInitializedEvent> viewerPortListener(ViewerServerInfo info) {
        return event -> info.setPort(event.getWebServer().getPort());
    }

    @Override
    public void init() {
        String home = System.getProperty("user.home");
        String dataDir = home + "/pbss_data";
        for (String dir : new String[]{dataDir, dataDir + "/db", dataDir + "/cast_ballot_scans",
                dataDir + "/reports", dataDir + "/writeins", dataDir + "/scribbles", dataDir + "/logs"}) {
            try {
                Files.createDirectories(Path.of(dir));
            } catch (IOException e) {
                System.err.println("Warning: could not create directory " + dir + ": " + e.getMessage());
            }
        }

        bindContext(new SpringApplicationBuilder(CounterFxApplication.class)
            .web(WebApplicationType.SERVLET)
            .properties("server.port=0")
            .run(getParameters().getRaw().toArray(new String[0])));
    }

    /**
     * Wires an already-built Spring context to this instance. Split out from
     * init() so TestFX tests can build a headless context themselves (real
     * init() relies on getParameters(), which only works after a full
     * Application.launch() — TestFX's @Start bypasses that lifecycle) and
     * still exercise the real showLogin()/showShell()/showInContent() flow.
     */
    public void bindContext(ConfigurableApplicationContext ctx) {
        this.springContext = ctx;
        ctx.getBean(Navigator.class).bind(this);
    }

    @Override
    public void start(Stage stage) throws IOException {
        this.primaryStage = stage;
        stage.setTitle(springContext.getEnvironment()
            .getProperty("app.login-title", "pbss Election Counter"));
        showLogin();
        stage.show();
    }

    public void showLogin() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
        loader.setControllerFactory(springContext::getBean);
        Parent root = loader.load();
        primaryStage.setScene(new Scene(root, 460, 340));
    }

    public void showShell() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/shell.fxml"));
        loader.setControllerFactory(springContext::getBean);
        Parent root = loader.load();
        shellController = loader.getController();
        primaryStage.setScene(new Scene(root, 1150, 750));
        showInContent("/fxml/dashboard.fxml");
    }

    public void showInContent(String fxmlPath) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        loader.setControllerFactory(springContext::getBean);
        Parent node = loader.load();
        shellController.setContent(node);
    }

    @Override
    public void stop() {
        springContext.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
