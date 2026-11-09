package com.szdp.shoppinglistapi.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.jwt.secret=test-signing-secret-that-is-at-least-32-bytes",
        "spring.datasource.url=jdbc:h2:mem:shoppinglist-tests;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class UserControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void initializesAllTablesFromDocumentedSchema() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = 'PUBLIC'
                  AND TABLE_NAME IN ('USERS', 'LISTS', 'LIST_SHARES', 'ITEMS', 'SESSIONS')
                """, Integer.class);

        org.junit.jupiter.api.Assertions.assertEquals(5, tableCount,
                jdbcTemplate.queryForList("""
                        SELECT TABLE_SCHEMA, TABLE_NAME
                        FROM INFORMATION_SCHEMA.TABLES
                        WHERE TABLE_SCHEMA = 'PUBLIC'
                        """).toString());
    }

    @Test
    void openApiRequiresBearerAuthForMeButNotLogin() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/users/me'].get.security[0]['Bearer Authentication']").isArray())
                .andExpect(jsonPath("$.paths['/api/users/login'].post.security").doesNotExist());
    }

    @Test
    void registrationCreatesAccountAndReturnsUsableJwt() throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"spec_user_1","password":"secure-pass"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username", is("spec_user_1")))
                .andExpect(jsonPath("$.recoveryKey").isString())
                .andReturn();

        String response = registration.getResponse().getContentAsString();
        String token = jsonValue(response, "accessToken");
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("spec_user_1")));

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"spec_user_1","password":"secure-pass"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryKey").doesNotExist());
    }

    @Test
    void registrationRejectsInvalidInputAndDuplicateUsernames() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ab","password":"short"}
                                """))
                .andExpect(status().isBadRequest());

        registerAndGetToken("duplicate_user");
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"duplicate_user","password":"another-pass"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void logoutRevokesTheJwt() throws Exception {
        String token = registerAndGetToken("logout_user");

        mockMvc.perform(post("/api/users/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recoveryKeyResetsPasswordAndRevokesExistingSessions() throws Exception {
        String registration = mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"reset_user","password":"initial-pass"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String oldToken = jsonValue(registration, "accessToken");
        String recoveryKey = jsonValue(registration, "recoveryKey");

        mockMvc.perform(post("/api/users/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"reset_user","recoveryKey":"%s","newPassword":"changed-pass"}
                                """.formatted(recoveryKey)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"reset_user","password":"changed-pass"}
                                """))
                .andExpect(status().isOk());
    }

    private String registerAndGetToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"secure-pass"}
                                """.formatted(username)))
                .andExpect(status().isCreated())
                .andReturn();
        return jsonValue(result.getResponse().getContentAsString(), "accessToken");
    }

    private static String jsonValue(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(name) + "\":\"([^\"]+)\"").matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Expected JSON string field " + name);
        }
        return matcher.group(1);
    }
}
