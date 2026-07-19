/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 *
 * BallotCombinationScreenTest — covers the cascading-dropdown behavior that
 * replaced the web version's GET-reload-on-election-change flow: selecting
 * an Election should populate the Region/Party/BallotType combos with only
 * that election's jurisdiction's options.
 */
package com.mjtrac.ballot.fx;

import com.mjtrac.ballot.BBuilderFxApplication;
import com.mjtrac.ballot.model.BallotType;
import com.mjtrac.ballot.model.Party;
import com.mjtrac.ballot.model.Region;
import com.mjtrac.ballot.repository.BallotCombinationRepository;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

@ExtendWith(ApplicationExtension.class)
class BallotCombinationScreenTest {

    private static ConfigurableApplicationContext springContext;

    @Start
    void start(Stage stage) throws Exception {
        springContext = new SpringApplicationBuilder(BBuilderFxApplication.class)
            .web(WebApplicationType.NONE)
            .profiles("sqlite")
            .run();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ballot-combinations.fxml"));
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
    void selectingElectionPopulatesDependentDropdowns(FxRobot robot) {
        // DataInitializer seeds exactly one election, so the combo is
        // pre-selected on load — the listener already ran by the time the
        // scene is shown, so the dependent combos should already be populated.
        assertThat(((ComboBox<?>) robot.lookup("#regionCombo").query()).getItems()).isNotEmpty();

        // p1 is already used by the seeded combination (p1 + Everyone + Precinct),
        // so pick p2 instead to avoid the unique-constraint collision.
        // ComboBox popup clicks are unreliable under headless Monocle, so
        // selections are made directly on the FX thread — this still
        // exercises the real handleSave() validation/persistence logic.
        robot.interact(() -> {
            ComboBox<Region> regionCombo = (ComboBox<Region>) robot.lookup("#regionCombo").query();
            regionCombo.getItems().stream().filter(r -> "p2".equals(r.getName())).findFirst()
                .ifPresent(r -> regionCombo.getSelectionModel().select(r));

            ComboBox<Party> partyCombo = (ComboBox<Party>) robot.lookup("#partyCombo").query();
            partyCombo.getItems().stream().filter(p -> "Everyone".equals(p.getName())).findFirst()
                .ifPresent(p -> partyCombo.getSelectionModel().select(p));

            ComboBox<BallotType> ballotTypeCombo = (ComboBox<BallotType>) robot.lookup("#ballotTypeCombo").query();
            ballotTypeCombo.getItems().stream().filter(b -> "Mail-In".equals(b.getName())).findFirst()
                .ifPresent(b -> ballotTypeCombo.getSelectionModel().select(b));
        });
        WaitForAsyncUtils.waitForFxEvents();
        robot.clickOn("Save");

        verifyThat("#messageLabel", hasText("Created ballot combination."));
        BallotCombinationRepository repo = springContext.getBean(BallotCombinationRepository.class);
        assertThat(repo.findAll()).hasSize(2); // seeded one + this new one
    }

    @Test
    void missingRegionShowsError(FxRobot robot) {
        // handleNew() auto-selects the (only) seeded election but leaves
        // region/party/ballotType unselected, so Save alone should fail here.
        robot.clickOn("Save");
        verifyThat("#messageLabel", hasText("Please select a SinglePrecinct."));
    }
}
