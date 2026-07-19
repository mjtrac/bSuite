/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 */
package com.mjtrac.ballot.fx;

import com.mjtrac.ballot.BBuilderFxApplication;
import com.mjtrac.ballot.model.Candidate;
import com.mjtrac.ballot.model.Contest;
import com.mjtrac.ballot.model.Region;
import com.mjtrac.ballot.repository.ContestRepository;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
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
import org.testfx.util.WaitForAsyncUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

@ExtendWith(ApplicationExtension.class)
class ContestScreenTest {

    private static ConfigurableApplicationContext springContext;

    @Start
    void start(Stage stage) throws Exception {
        springContext = new SpringApplicationBuilder(BBuilderFxApplication.class)
            .web(WebApplicationType.NONE)
            .profiles("sqlite")
            .run();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/contests.fxml"));
        loader.setControllerFactory(springContext::getBean);
        Parent root = loader.load();
        stage.setScene(new Scene(root, 1000, 900));
        stage.show();
    }

    @AfterAll
    static void closeContext() {
        if (springContext != null) springContext.close();
    }

    @Test
    void creatingContestAddingCandidateAndAssigningRegionsPersists(FxRobot robot) {
        robot.clickOn("#titleField").write("City Council");
        robot.clickOn("Save");
        verifyThat("#messageLabel", hasText("Created contest \"City Council\"."));

        ContestRepository contestRepo = springContext.getBean(ContestRepository.class);
        Optional<Contest> saved = contestRepo.findAll().stream()
            .filter(c -> "City Council".equals(c.getTitle())).findFirst();
        assertThat(saved).isPresent();

        // Add a candidate — the candidates panel only appears once a contest
        // exists. Driven via interact()/.fire() rather than clickOn(), same
        // headless-Monocle visibility-detection flakiness workaround used for
        // the Region screen's post-visibility-toggle Save Members button.
        robot.interact(() -> {
            TextField nameField = (TextField) robot.lookup("#candidateNameField").query();
            nameField.setText("Carol");
            Button saveCandidateButton = (Button) robot.lookup("Save Candidate").query();
            saveCandidateButton.fire();
        });
        WaitForAsyncUtils.waitForFxEvents();
        verifyThat("#messageLabel", hasText("Added \"Carol\"."));

        Contest reloaded = contestRepo.findById(saved.get().getId()).orElseThrow();
        assertThat(reloaded.getCandidates()).extracting(Candidate::getName).containsExactly("Carol");

        // Assign a region.
        robot.interact(() -> {
            CheckBox p3Checkbox = (CheckBox) robot.lookup(
                (javafx.scene.Node n) -> n instanceof CheckBox && "p3".equals(((CheckBox) n).getText())
            ).query();
            p3Checkbox.fire();
            Button saveRegionsButton = (Button) robot.lookup("Save Regions").query();
            saveRegionsButton.fire();
        });
        WaitForAsyncUtils.waitForFxEvents();

        verifyThat("#messageLabel", hasText("Updated region assignment for \"City Council\" (1 region(s))."));
        Contest withRegion = contestRepo.findById(saved.get().getId()).orElseThrow();
        assertThat(withRegion.getAssignedRegions()).extracting(Region::getName).containsExactly("p3");
    }

    @Test
    void titleIsRequired(FxRobot robot) {
        robot.clickOn("Save");
        verifyThat("#messageLabel", hasText("Contest title is required."));
    }
}
