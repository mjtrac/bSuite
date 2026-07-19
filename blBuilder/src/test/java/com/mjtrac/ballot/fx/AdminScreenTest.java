/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 */
package com.mjtrac.ballot.fx;

import com.mjtrac.ballot.BBuilderFxApplication;
import com.mjtrac.ballot.model.User;
import com.mjtrac.ballot.repository.UserRepository;
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
        springContext = new SpringApplicationBuilder(BBuilderFxApplication.class)
            .web(WebApplicationType.NONE)
            .profiles("sqlite")
            .run();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/admin.fxml"));
        loader.setControllerFactory(springContext::getBean);
        Parent root = loader.load();
        stage.setScene(new Scene(root, 900, 800));
        stage.show();
    }

    @AfterAll
    static void closeContext() {
        if (springContext != null) springContext.close();
    }

    @Test
    void creatingUserWithShortPasswordShowsError(FxRobot robot) {
        robot.clickOn("#usernameField").write("newop");
        robot.clickOn("#passwordField").write("short");
        robot.clickOn("#dataEntryRoleCheck");
        robot.clickOn("Create User");

        verifyThat("#messageLabel", hasText("Password is required and must be at least 12 characters."));
    }

    @Test
    void creatingUserPersistsWithSelectedRoles(FxRobot robot) {
        robot.clickOn("#usernameField").write("newoperator");
        robot.clickOn("#passwordField").write("ReallyLongPassword123!");
        robot.clickOn("#dataEntryRoleCheck");
        robot.clickOn("Create User");

        verifyThat("#messageLabel", hasText("Created user \"newoperator\"."));

        UserRepository userRepo = springContext.getBean(UserRepository.class);
        Optional<User> created = userRepo.findByUsername("newoperator");
        assertThat(created).isPresent();
        assertThat(created.get().getRoles()).containsExactly(User.Role.DATA_ENTRY);
        assertThat(created.get().isEnabled()).isTrue();
    }
}
