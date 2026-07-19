/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 */
package com.mjtrac.counter.fx;

import com.mjtrac.counter.CounterFxApplication;
import com.mjtrac.counter.repository.CounterUserRepository;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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

import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

@ExtendWith(ApplicationExtension.class)
class AccountScreenTest {

    private static ConfigurableApplicationContext springContext;

    @Start
    void start(Stage stage) throws Exception {
        springContext = new SpringApplicationBuilder(CounterFxApplication.class)
            .web(WebApplicationType.SERVLET)
            .properties("server.port=0")
            .profiles("sqlite")
            .run();

        AuthContext authContext = springContext.getBean(AuthContext.class);
        authContext.setCurrentUser(springContext.getBean(CounterUserRepository.class)
            .findByUsername("admin").orElseThrow());

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/account.fxml"));
        loader.setControllerFactory(springContext::getBean);
        Parent root = loader.load();
        stage.setScene(new Scene(root, 500, 400));
        stage.show();
    }

    @AfterAll
    static void closeContext() {
        if (springContext != null) springContext.close();
    }

    @Test
    void wrongCurrentPasswordShowsError(FxRobot robot) {
        robot.clickOn("#currentPasswordField").write("wrong");
        robot.clickOn("#newPasswordField").write("NewPassword123!");
        robot.clickOn("#confirmPasswordField").write("NewPassword123!");
        robot.clickOn("#changePasswordButton");
        verifyThat("#messageLabel", hasText("Current password is incorrect."));
    }

    @Test
    void mismatchedNewPasswordsShowsError(FxRobot robot) {
        robot.clickOn("#currentPasswordField").write("ChangeMe123!");
        robot.clickOn("#newPasswordField").write("NewPassword123!");
        robot.clickOn("#confirmPasswordField").write("Different123!");
        robot.clickOn("#changePasswordButton");
        verifyThat("#messageLabel", hasText("New passwords do not match."));
    }
}
