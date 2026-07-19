/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 *
 * BallotDesignTemplateScreenTest — the highest-priority screen test in this
 * conversion: confirms plain hand-typed header HTML (no Quill, no ql-*
 * classes) persists correctly and that the ported Example-snippet buttons
 * insert the exact same markup the removed header-editor.js used to.
 */
package com.mjtrac.ballot.fx;

import com.mjtrac.ballot.BBuilderFxApplication;
import com.mjtrac.ballot.model.BallotDesignTemplate;
import com.mjtrac.ballot.repository.BallotDesignTemplateRepository;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

@ExtendWith(ApplicationExtension.class)
class BallotDesignTemplateScreenTest {

    private static ConfigurableApplicationContext springContext;

    @Start
    void start(Stage stage) throws Exception {
        springContext = new SpringApplicationBuilder(BBuilderFxApplication.class)
            .web(WebApplicationType.NONE)
            .profiles("sqlite")
            .run();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ballot-templates.fxml"));
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
    void plainHandTypedHeaderHtmlPersistsWithoutQuill(FxRobot robot) {
        String plainHtml = "<div style=\"text-align:center\"><p style=\"font-size:14pt\">TEST HEADER</p></div>";

        robot.clickOn("#headerHtmlArea").write(plainHtml);
        robot.clickOn("Save");

        verifyThat("#messageLabel", hasText("Created ballot template for \"Sample General Election\"."));

        BallotDesignTemplateRepository repo = springContext.getBean(BallotDesignTemplateRepository.class);
        List<BallotDesignTemplate> all = repo.findAll();
        // DataInitializer already seeds one default template, so this is the second.
        assertThat(all).hasSize(2);
        assertThat(all).extracting(BallotDesignTemplate::getHeaderHtml).contains(plainHtml);
    }

    @Test
    void exampleSnippetButtonsInsertPortedMarkup(FxRobot robot) {
        robot.clickOn("Example snippets — click to insert");
        WaitForAsyncUtils.waitForFxEvents();
        robot.clickOn("Minimal (election name only)");

        TextArea area = (TextArea) robot.lookup("#headerHtmlArea").query();
        assertThat(area.getText())
            .contains("OFFICIAL BALLOT")
            .contains("{electionName}")
            .doesNotContain("ql-align")
            .doesNotContain("quill");
    }

    @Test
    void electionIsRequired(FxRobot robot) {
        robot.interact(() -> {
            var electionCombo = (javafx.scene.control.ComboBox<?>) robot.lookup("#electionCombo").query();
            electionCombo.getSelectionModel().clearSelection();
        });
        robot.clickOn("Save");
        verifyThat("#messageLabel", hasText("Please select an election."));
    }
}
