/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 *
 * RegionScreenTest — covers the one piece of real logic beyond the Party
 * CRUD pattern: creating a PrecinctGroup and assigning SinglePrecinct members
 * to it via the checkbox ListView.
 */
package com.mjtrac.ballot.fx;

import com.mjtrac.ballot.BBuilderFxApplication;
import com.mjtrac.ballot.model.Region;
import com.mjtrac.ballot.repository.RegionRepository;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

@ExtendWith(ApplicationExtension.class)
class RegionScreenTest {

    private static ConfigurableApplicationContext springContext;

    @Start
    void start(Stage stage) throws Exception {
        springContext = new SpringApplicationBuilder(BBuilderFxApplication.class)
            .web(WebApplicationType.NONE)
            .profiles("sqlite")
            .run();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/regions.fxml"));
        loader.setControllerFactory(springContext::getBean);
        Parent root = loader.load();
        stage.setScene(new Scene(root, 900, 700));
        stage.show();
    }

    @AfterAll
    static void closeContext() {
        if (springContext != null) springContext.close();
    }

    @SuppressWarnings("unchecked")
    @Test
    void creatingPrecinctGroupAndAssigningMembersPersists(FxRobot robot) {
        // p1/p2/p3 are seeded SinglePrecincts (see DataInitializer).
        // ComboBox popup clicks are unreliable under headless Monocle, so the
        // type is selected directly on the FX thread rather than simulating
        // the dropdown click — this still exercises the real valueProperty
        // listener and the real handleSave()/handleSaveMembers() logic.
        robot.clickOn("#nameField").write("g-test");
        robot.interact(() -> {
            ComboBox<Region.RegionType> combo =
                (ComboBox<Region.RegionType>) robot.lookup("#regionTypeCombo").query();
            combo.getSelectionModel().select(Region.RegionType.PRECINCT_GROUP);
        });
        WaitForAsyncUtils.waitForFxEvents();
        robot.clickOn("#groupTypeField").write("DISTRICT");
        robot.clickOn("Save");

        verifyThat("#messageLabel", hasText("Created region \"g-test\"."));

        RegionRepository regionRepo = springContext.getBean(RegionRepository.class);
        Optional<Region> saved = regionRepo.findAll().stream()
            .filter(r -> "g-test".equals(r.getName())).findFirst();
        assertThat(saved).isPresent();
        assertThat(saved.get().isPrecinctGroup()).isTrue();

        // Members box should now be visible with p1/p2/p3 as candidates.
        // Both the checkbox and the Save Members button are driven via
        // .fire() rather than simulated clicks, for the same headless-Monocle
        // visibility-detection flakiness reason as the ComboBox selections
        // above — .fire() still runs the exact same onAction handler a real
        // click would, it just skips TestFX's screen-bounds visibility check.
        // "p1" also appears as plain table-cell text in the region list above,
        // so the lookup is narrowed to CheckBox nodes specifically.
        robot.interact(() -> {
            CheckBox p1Checkbox = (CheckBox) robot.lookup(
                (javafx.scene.Node n) -> n instanceof CheckBox && "p1".equals(((CheckBox) n).getText())
            ).query();
            p1Checkbox.fire();
        });
        WaitForAsyncUtils.waitForFxEvents();
        robot.interact(() -> {
            Button saveMembersButton = (Button) robot.lookup("Save Members").query();
            saveMembersButton.fire();
        });

        verifyThat("#messageLabel", hasText("Updated member list for \"g-test\" (1 SinglePrecinct(s))."));
        Region reloaded = regionRepo.findById(saved.get().getId()).orElseThrow();
        assertThat(reloaded.getMembers()).extracting(Region::getName).containsExactly("p1");
    }

    @Test
    void nameIsRequired(FxRobot robot) {
        robot.clickOn("Save");
        verifyThat("#messageLabel", hasText("Name is required."));
    }
}
