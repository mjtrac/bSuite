/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 */
package com.mjtrac.counter.fx;

import com.mjtrac.counter.CounterFxApplication;
import com.mjtrac.counter.entity.CounterUser;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

@ExtendWith(ApplicationExtension.class)
class AdminScreenTest {

    private static ConfigurableApplicationContext springContext;

    @Start
    void start(Stage stage) throws Exception {
        springContext = new SpringApplicationBuilder(CounterFxApplication.class)
            .web(WebApplicationType.SERVLET)
            .properties("server.port=0")
            .profiles("sqlite")
            .run();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/admin.fxml"));
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
    void creatingUserWithRolesPersists(FxRobot robot) {
        robot.clickOn("#usernameField").write("operator1");
        robot.clickOn("#passwordField").write("ReallyLongPassword123!");
        robot.clickOn("#counterOpRoleCheck");
        robot.clickOn("Create User");

        verifyThat("#messageLabel", hasText("Created user \"operator1\"."));

        CounterUserRepository userRepo = springContext.getBean(CounterUserRepository.class);
        Optional<CounterUser> created = userRepo.findByUsername("operator1");
        assertThat(created).isPresent();
        assertThat(created.get().getRoles()).containsExactly(CounterUser.Role.COUNTER_OPERATOR);
    }

    @Test
    void shortPasswordShowsError(FxRobot robot) {
        robot.clickOn("#usernameField").write("op2");
        robot.clickOn("#passwordField").write("short");
        robot.clickOn("#viewerRoleCheck");
        robot.clickOn("Create User");
        verifyThat("#messageLabel", hasText("Password is required and must be at least 12 characters."));
    }
}
