/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 *
 * PartyScreenTest — TestFX, headless via Monocle. Establishes the pilot
 * pattern for the other simple-entity CRUD screens: load the screen FXML
 * directly against a fresh headless Spring context (not through login/shell),
 * drive it with FxRobot, and assert both the UI message and the underlying
 * repository state.
 */
package com.mjtrac.ballot.fx;

import com.mjtrac.ballot.BBuilderFxApplication;
import com.mjtrac.ballot.model.Party;
import com.mjtrac.ballot.repository.PartyRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

@ExtendWith(ApplicationExtension.class)
class PartyScreenTest {

    private static ConfigurableApplicationContext springContext;

    @Start
    void start(Stage stage) throws Exception {
        springContext = new SpringApplicationBuilder(BBuilderFxApplication.class)
            .web(WebApplicationType.NONE)
            .profiles("sqlite")
            .run();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/parties.fxml"));
        loader.setControllerFactory(springContext::getBean);
        Parent root = loader.load();
        stage.setScene(new Scene(root, 900, 650));
        stage.show();
    }

    @AfterAll
    static void closeContext() {
        if (springContext != null) springContext.close();
    }

    @Test
    void seededEverypartyIsListedAndNewPartyCanBeAdded(FxRobot robot) {
        PartyRepository partyRepo = springContext.getBean(PartyRepository.class);
        assertThat(partyRepo.findAll()).extracting(Party::getName).contains("Everyone");

        robot.clickOn("#nameField").write("Green");
        robot.clickOn("#abbreviationField").write("GRN");
        robot.clickOn("Save");

        verifyThat("#messageLabel", hasText("Created party \"Green\"."));
        List<Party> parties = partyRepo.findAll();
        assertThat(parties).extracting(Party::getName).contains("Everyone", "Green");
        assertThat(parties).filteredOn(p -> p.getName().equals("Green"))
            .extracting(Party::getAbbreviation).containsExactly("GRN");
    }

    @Test
    void partyNameIsRequired(FxRobot robot) {
        robot.clickOn("Save");
        verifyThat("#messageLabel", hasText("Party name is required."));
    }
}
