/*
 * Copyright (C) 2026 Mitch Trachtenberg
 * Election Scanner Driver — licensed under the GNU General Public License v3.
 * See <https://www.gnu.org/licenses/> for the full license text.
 */
package com.mjtrac.scanner.fx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;

/**
 * JavaFX entry point. Spring runs headless (no embedded servlet container) purely
 * for DI, JPA, and password hashing; JavaFX owns the UI. This class lives in the
 * .fx sub-package, a sibling of the existing entity/repository/service/config
 * packages under com.mjtrac.scanner — scanBasePackages covers @Component scanning,
 * but @EnableJpaRepositories/@EntityScan base packages must be set separately since
 * they're driven by this class's own package, not by scanBasePackages.
 */
@SpringBootApplication(scanBasePackages = "com.mjtrac.scanner")
@EnableJpaRepositories(basePackages = "com.mjtrac.scanner")
@EntityScan(basePackages = "com.mjtrac.scanner")
public class ScannerFxApplication extends Application {

    private ConfigurableApplicationContext springContext;
    private Stage primaryStage;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Override
    public void init() {
        springContext = new SpringApplicationBuilder(ScannerFxApplication.class)
            .web(WebApplicationType.NONE)
            .run(getParameters().getRaw().toArray(new String[0]));
        // The Spring-managed ScannerFxApplication bean is a separate instance from
        // this JavaFX-launched one; bind Navigator to *this* real instance so
        // Spring-injected controllers can drive scene changes on the actual stage.
        springContext.getBean(Navigator.class).bind(this);
    }

    @Override
    public void start(Stage stage) throws IOException {
        this.primaryStage = stage;
        stage.setTitle(springContext.getEnvironment()
            .getProperty("app.login-title", "bScanner"));
        showLogin();
        stage.show();
    }

    void showLogin() throws IOException {
        showScene("/fxml/login.fxml", 420, 300);
    }

    void showMain() throws IOException {
        showScene("/fxml/main.fxml", 640, 620);
    }

    void showConfig() throws IOException {
        showScene("/fxml/config.fxml", 560, 640);
    }

    void showUsers() throws IOException {
        showScene("/fxml/users.fxml", 560, 520);
    }

    private void showScene(String fxmlPath, double width, double height) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        loader.setControllerFactory(springContext::getBean);
        Parent root = loader.load();
        primaryStage.setScene(new Scene(root, width, height));
    }

    @Override
    public void stop() {
        springContext.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
