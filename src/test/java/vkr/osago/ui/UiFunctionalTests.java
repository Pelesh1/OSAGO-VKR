package vkr.osago.ui;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class UiFunctionalTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void uiPagesShouldBeAccessible() throws Exception {
        String[] urls = {
                "/",
                "/login/index.html",
                "/register/index.html",
                "/insurance/osago/index.html",
                "/cabinet/client/index.html",
                "/cabinet/client/chat/index.html",
                "/cabinet/agent/index.html",
                "/cabinet/agent/chats/index.html"
        };
        for (String url : urls) {
            mockMvc.perform(get(url)).andExpect(status().isOk());
        }
    }

    @Test
    void uiRoleElementsShouldExist() throws Exception {
        mockMvc.perform(get("/cabinet/client/chat/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"agentPhone\"")));

        mockMvc.perform(get("/cabinet/agent/chats/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"clientPhone\"")));

        mockMvc.perform(get("/cabinet/agent/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"activePolicies\"")));
    }

    @Test
    void uiShouldContainErrorAndInfoBlocks() throws Exception {
        mockMvc.perform(get("/insurance/osago/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"errorBox\"")));

        mockMvc.perform(get("/cabinet/client/claims/detail.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"ok\"")));
    }

    @Test
    void uiShouldContainNavigationLinks() throws Exception {
        mockMvc.perform(get("/cabinet/client/chat/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/cabinet/client/index.html")));
    }

    @Test
    void uiAccessMatrixShouldValidatePagesByRole() {
        assertTrue(UiAccessMatrix.isPageAllowed(null, "/"));
        assertTrue(UiAccessMatrix.isPageAllowed(vkr.osago.user.UserStatus.CLIENT, "/cabinet/client/index.html"));
        assertTrue(UiAccessMatrix.isPageAllowed(vkr.osago.user.UserStatus.AGENT, "/cabinet/agent/index.html"));
        assertTrue(UiAccessMatrix.isPageAllowed(vkr.osago.user.UserStatus.ADMIN, "/any/path.html"));
        assertFalse(UiAccessMatrix.isPageAllowed(vkr.osago.user.UserStatus.CLIENT, "/cabinet/agent/index.html"));
        assertFalse(UiAccessMatrix.isPageAllowed(vkr.osago.user.UserStatus.AGENT, "/cabinet/client/index.html"));
        assertFalse(UiAccessMatrix.isPageAllowed(vkr.osago.user.UserStatus.CLIENT, " "));
    }

    @Test
    void uiValidationShouldCatchInvalidInputs() {
        var loginErrors = UiAccessMatrix.validateInput(UiFormType.LOGIN, Map.of("email", "bad", "password", "123"));
        assertTrue(loginErrors.size() >= 1);

        var claimErrors = UiAccessMatrix.validateInput(UiFormType.CLAIM, Map.of("description", "short", "phone", "12"));
        assertTrue(claimErrors.size() >= 1);

        var messageErrors = UiAccessMatrix.validateInput(UiFormType.UI_MESSAGE, Map.of("message", " "));
        assertTrue(messageErrors.size() >= 1);
    }

    @Test
    void uiValidationShouldAcceptValidInputs() {
        var loginErrors = UiAccessMatrix.validateInput(UiFormType.LOGIN, Map.of("email", "a@b.com", "password", "123456"));
        assertTrue(loginErrors.isEmpty());

        var claimErrors = UiAccessMatrix.validateInput(UiFormType.CLAIM, Map.of("description", "Valid description", "phone", "79001234567"));
        assertTrue(claimErrors.isEmpty());

        var messageErrors = UiAccessMatrix.validateInput(UiFormType.UI_MESSAGE, Map.of("message", "OK"));
        assertTrue(messageErrors.isEmpty());
    }

    @Test
    void uiValidationShouldHandleMissingType() {
        var errors = UiAccessMatrix.validateInput(null, Map.of("email", "a@b.com"));
        assertFalse(errors.isEmpty());
    }
}
