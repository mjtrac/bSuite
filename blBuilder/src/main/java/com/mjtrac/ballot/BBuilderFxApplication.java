/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Ballot System — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.ballot;

import com.mjtrac.ballot.fx.Navigator;
import com.mjtrac.ballot.fx.ShellController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JavaFX entry point. Spring runs headless (no embedded servlet container) purely
 * for DI, JPA, and password hashing; JavaFX owns the UI. Deliberately kept at the
 * package root (unlike blScanner's ScannerFxApplication, which lives in a .fx
 * sub-package) — Spring Boot's @SpringBootTest config-class discovery only
 * searches upward from a test's own package, and every existing test here lives
 * in com.mjtrac.ballot, so the entry point has to be at or above that package
 * for those tests to keep finding it. Everything else FX-specific still lives
 * under .fx for organization; entity/repository scanning both still work
 * unchanged since this class's own package already covers com.mjtrac.ballot.model,
 * and DatabaseConfig already declares @EnableJpaRepositories explicitly.
 */
@SpringBootApplication
public class BBuilderFxApplication extends Application {

    private ConfigurableApplicationContext springContext;
    private Stage primaryStage;
    private ShellController shellController;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Override
    public void init() {
        // Same data-directory bootstrap ElectionBallotApplication used to do —
        // the DB directory must exist before Hibernate opens the SQLite file.
        String home = System.getProperty("user.home");
        String dataDir = home + "/pbss_data";
        for (String dir : new String[]{dataDir, dataDir + "/db", dataDir + "/ballot_templates", dataDir + "/logs"}) {
            try {
                Files.createDirectories(Path.of(dir));
            } catch (IOException e) {
                System.err.println("Warning: could not create directory " + dir + ": " + e.getMessage());
            }
        }

        bindContext(new SpringApplicationBuilder(BBuilderFxApplication.class)
            .web(WebApplicationType.NONE)
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
            .getProperty("app.login-title", "pbss Ballot Builder"));
        showLogin();
        stage.show();
    }

    /** Full-window login screen — shown before the persistent shell exists. */
    public void showLogin() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
        loader.setControllerFactory(springContext::getBean);
        Parent root = loader.load();
        primaryStage.setScene(new Scene(root, 460, 340));
    }

    /**
     * Builds the persistent shell (nav sidebar + swappable content pane) once,
     * after login, and lands on the dashboard. Subsequent navigation goes
     * through showInContent() rather than rebuilding the shell each time.
     */
    public void showShell() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/shell.fxml"));
        loader.setControllerFactory(springContext::getBean);
        Parent root = loader.load();
        shellController = loader.getController();
        primaryStage.setScene(new Scene(root, 1100, 720));
        showInContent("/fxml/dashboard.fxml");
    }

    /** Swaps the shell's center content — this is how every screen after login navigates. */
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
