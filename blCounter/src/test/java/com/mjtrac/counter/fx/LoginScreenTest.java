/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 *
 * LoginScreenTest — TestFX, headless via Monocle. Same pattern as
 * blScanner/blBuilder's login tests.
 */
package com.mjtrac.counter.fx;

import com.mjtrac.counter.CounterFxApplication;
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
    private static CounterFxApplication app;

    @Start
    void start(Stage stage) throws Exception {
        springContext = new SpringApplicationBuilder(CounterFxApplication.class)
            .web(WebApplicationType.SERVLET)
            .properties("server.port=0")
            .profiles("sqlite")
            .run();

        app = new CounterFxApplication();
        app.bindContext(springContext);
        app.start(stage);
    }

    @BeforeEach
    void returnToLoginScreen() throws Exception {
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
        robot.clickOn("#passwordField").write("ChangeMe123!");
        robot.clickOn("Sign In");

        assertThat(robot.lookup("#usernameField").tryQuery()).isEmpty();
        verifyThat("#welcomeLabel", hasText("Welcome, admin"));
    }

    @Test
    void invalidLoginShowsError(FxRobot robot) {
        robot.clickOn("#usernameField").write("admin");
        robot.clickOn("#passwordField").write("wrong-password");
        robot.clickOn("Sign In");

        verifyThat("#messageLabel", hasText("Invalid username or password."));
    }
}
