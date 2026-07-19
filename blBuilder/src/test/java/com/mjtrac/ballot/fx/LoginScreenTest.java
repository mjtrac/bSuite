/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 *
 * LoginScreenTest — TestFX, headless via Monocle (see pom.xml surefire
 * systemPropertyVariables). Drives the real login.fxml/LoginViewController
 * through a real (isolated, H2 in-memory) Spring context — no display, no
 * OS permissions, unlike the manual click-through blScanner needed.
 */
package com.mjtrac.ballot.fx;

import com.mjtrac.ballot.BBuilderFxApplication;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

@ExtendWith(ApplicationExtension.class)
class LoginScreenTest {

    private static ConfigurableApplicationContext springContext;
    private static BBuilderFxApplication app;

    @Start
    void start(Stage stage) throws Exception {
        springContext = new SpringApplicationBuilder(BBuilderFxApplication.class)
            .web(WebApplicationType.NONE)
            .profiles("sqlite")
            .run();

        app = new BBuilderFxApplication();
        app.bindContext(springContext);
        app.start(stage);
    }

    @BeforeEach
    void returnToLoginScreen() throws Exception {
        // Each test starts from a clean login screen, whichever screen the
        // previous test left the (reused) Stage on. showLogin() touches the
        // Stage, so it must run on the FX Application Thread, not JUnit's.
        WaitForAsyncUtils.asyncFx(() -> {
            try {
                app.showLogin();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).get();
        WaitForAsyncUtils.waitForFxEvents();
    }

    @AfterAll
    static void closeContext() {
        if (springContext != null) springContext.close();
    }

    @Test
    void validLoginNavigatesToDashboard(FxRobot robot) {
        robot.clickOn("#usernameField").write("admin");
        robot.clickOn("#passwordField").write("TestAdmin#2026!");
        robot.clickOn("Sign In");

        // Login screen is gone, replaced by the shell/dashboard.
        assertThat(robot.lookup("#usernameField").tryQuery()).isEmpty();
        verifyThat("#welcomeLabel", hasText("Welcome, admin"));
    }

    @Test
    void invalidLoginShowsError(FxRobot robot) {
        robot.clickOn("#usernameField").write("admin");
        robot.clickOn("#passwordField").write("wrong-password");
        robot.clickOn("Sign In");

        verifyThat("#messageLabel", hasText("Invalid username or password."));
        // Still on the login screen.
        assertThat(robot.lookup("#usernameField").tryQuery()).isPresent();
    }
}
