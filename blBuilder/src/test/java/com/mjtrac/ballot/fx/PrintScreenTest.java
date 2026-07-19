/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 */
package com.mjtrac.ballot.fx;

import com.mjtrac.ballot.BBuilderFxApplication;
import com.mjtrac.ballot.repository.PrintLogRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.testfx.api.FxAssert.verifyThat;

@ExtendWith(ApplicationExtension.class)
class PrintScreenTest {

    private static ConfigurableApplicationContext springContext;

    @Start
    void start(Stage stage) throws Exception {
        springContext = new SpringApplicationBuilder(BBuilderFxApplication.class)
            .web(WebApplicationType.NONE)
            .profiles("sqlite")
            .run();

        // Print requires a signed-in user (PrintLogService.record() needs one) —
        // normally set by LoginViewController, set directly here since this
        // test loads the screen in isolation.
        AuthContext authContext = springContext.getBean(AuthContext.class);
        authContext.setCurrentUser(springContext.getBean(UserRepository.class)
            .findByUsername("admin").orElseThrow());

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/print.fxml"));
        loader.setControllerFactory(springContext::getBean);
        Parent root = loader.load();
        stage.setScene(new Scene(root, 900, 500));
        stage.show();
    }

    @AfterAll
    static void closeContext() {
        if (springContext != null) springContext.close();
    }

    @Test
    void generatingBallotWritesPdfAndRecordsPrintLog(FxRobot robot) {
        // DataInitializer seeds exactly one combination/template, so both
        // combos are pre-selected by the valueProperty listener chain.
        robot.clickOn("Generate PDF");

        verifyThat("#messageLabel", (javafx.scene.control.Label l) -> l.getText().startsWith("Generated"));

        PrintLogRepository printLogRepo = springContext.getBean(PrintLogRepository.class);
        assertThat(printLogRepo.findAllWithValidCombination()).isNotEmpty();
    }
}
