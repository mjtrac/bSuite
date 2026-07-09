/*
 * Copyright (C) 2026 Mitch Trachtenberg — GPL v3
 */
package gov.election.counter;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests that a viewer-authenticated user on port 8082 can navigate
 * between the ballot image list and the results report without
 * requiring a separate bCounter login on port 8081.
 *
 * Regression guard: if /viewer/report is removed from ViewerController,
 * or the viewer security config stops covering that path, these tests fail.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sqlite")
@DisplayName("Viewer navigation — ballot list ↔ results report")
class ViewerNavigationTest {

    @Autowired MockMvc mvc;

    @org.springframework.beans.factory.annotation.Value("${viewer.username:admin}")
    private String viewerUsername;

    @org.springframework.beans.factory.annotation.Value("${viewer.password:ChangeMe123!}")
    private String viewerPassword;

    private MockHttpSession viewerSession;

    @BeforeEach
    void loginAsViewer() throws Exception {
        // Perform login — expect 302 redirect to /viewer/
        MvcResult login = mvc.perform(post("/viewer/login")
                .with(csrf())
                .param("username", viewerUsername)
                .param("password", viewerPassword))
            .andExpect(status().is3xxRedirection())
            .andReturn();

        // The authenticated session is on the request that was processed
        viewerSession = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(viewerSession).as("Viewer session must exist after login").isNotNull();

        // Follow the redirect to confirm authentication is complete
        mvc.perform(get(login.getResponse().getRedirectedUrl())
                .session(viewerSession))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /viewer/ returns 200 for authenticated viewer user")
    void testViewerIndexAccessible() throws Exception {
        mvc.perform(get("/viewer/").session(viewerSession))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Ballot Viewer")));
    }

    @Test
    @DisplayName("GET /viewer/report returns 200 for authenticated viewer user")
    void testViewerReportAccessible() throws Exception {
        mvc.perform(get("/viewer/report").session(viewerSession))
            .andExpect(status().isOk())
            .andExpect(content().string(anyOf(
                containsString("Results"),
                containsString("No results report found")
            )));
    }

    @Test
    @DisplayName("GET /viewer/report contains a link back to ballot list")
    void testReportHasBackLink() throws Exception {
        String body = mvc.perform(get("/viewer/report").session(viewerSession))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("/viewer/");
    }

    @Test
    @DisplayName("GET /viewer/ contains a link to /viewer/report, not localhost:8081")
    void testIndexHasResultsLink() throws Exception {
        String body = mvc.perform(get("/viewer/").session(viewerSession))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(body)
            .contains("/viewer/report")
            .doesNotContain("localhost:8081/results");
    }

    @Test
    @DisplayName("GET /viewer/report redirects to login for unauthenticated user")
    void testReportRequiresAuth() throws Exception {
        mvc.perform(get("/viewer/report"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("/viewer/login")));
    }

    @Test
    @DisplayName("Navigation round-trip: index → report → index all return 200")
    void testRoundTrip() throws Exception {
        mvc.perform(get("/viewer/").session(viewerSession))
            .andExpect(status().isOk());
        mvc.perform(get("/viewer/report").session(viewerSession))
            .andExpect(status().isOk());
        mvc.perform(get("/viewer/").session(viewerSession))
            .andExpect(status().isOk());
    }
}
