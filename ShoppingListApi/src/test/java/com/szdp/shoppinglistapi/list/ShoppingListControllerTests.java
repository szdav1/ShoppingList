package com.szdp.shoppinglistapi.list;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
	"app.jwt.secret=test-signing-secret-that-is-at-least-32-bytes",
	"spring.datasource.url=jdbc:h2:mem:shoppinglist-tests;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class ShoppingListControllerTests {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ArchivedListCleanup archivedListCleanup;

	@Test
	void ownerCanCreateRenameArchiveAndRestoreAList() throws Exception {
		String ownerToken = registerAndGetToken();
		MvcResult created = mockMvc.perform(post("/api/lists")
				.header("Authorization", bearer(ownerToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title":"Groceries"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.title", is("Groceries")))
			.andExpect(jsonPath("$.permission", is("OWNER")))
			.andReturn();
		long listId = jsonLong(created.getResponse().getContentAsString(), "id");

		mockMvc.perform(post("/api/lists/{listId}/items", listId)
				.header("Authorization", bearer(ownerToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":" Milk ","quantity":2.5,"unit":" liter "}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.listId", is((int) listId)))
			.andExpect(jsonPath("$.name", is("Milk")))
			.andExpect(jsonPath("$.quantity", is(2.5)))
			.andExpect(jsonPath("$.unit", is("liter")))
			.andExpect(jsonPath("$.checked", is(false)));

		mockMvc.perform(post("/api/lists/{listId}/items", listId)
				.header("Authorization", bearer(ownerToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"   ","quantity":1,"unit":"each"}
					"""))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/lists/{listId}/items", listId)
				.header("Authorization", bearer(ownerToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Invalid quantity","quantity":0,"unit":"each"}
					"""))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/lists/{listId}/items", listId)
				.header("Authorization", bearer(ownerToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Invalid precision","quantity":1.234,"unit":"each"}
					"""))
			.andExpect(status().isBadRequest());

		mockMvc.perform(patch("/api/lists/{listId}", listId)
				.header("Authorization", bearer(ownerToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title":"Weekly groceries"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title", is("Weekly groceries")));

		mockMvc.perform(delete("/api/lists/{listId}", listId)
				.header("Authorization", bearer(ownerToken)))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/lists/archive")
				.header("Authorization", bearer(ownerToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id", is((int) listId)))
			.andExpect(jsonPath("$[0].deleted", is(true)));

		mockMvc.perform(post("/api/lists/{listId}/restore", listId)
				.header("Authorization", bearer(ownerToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.deleted", is(false)));
	}

	@Test
	void expiredArchivedListsArePermanentlyPurged() throws Exception {
		String ownerToken = registerAndGetToken();
		MvcResult created = mockMvc.perform(post("/api/lists")
				.header("Authorization", bearer(ownerToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title":"Expired list"}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		long listId = jsonLong(created.getResponse().getContentAsString(), "id");
		mockMvc.perform(delete("/api/lists/{listId}", listId)
				.header("Authorization", bearer(ownerToken)))
			.andExpect(status().isNoContent());
		jdbcTemplate.update(
			"UPDATE lists SET deleted_at = DATEADD('DAY', -31, CURRENT_TIMESTAMP) WHERE id = ?",
			listId
		);

		org.junit.jupiter.api.Assertions.assertEquals(1, archivedListCleanup.purgeExpiredLists());
		org.junit.jupiter.api.Assertions.assertEquals(
			0,
			jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lists WHERE id = ?", Integer.class, listId)
		);
	}

	@Test
	void sharedViewerCanReadButCannotManageListAndRevocationRemovesAccess() throws Exception {
		String ownerToken = registerAndGetToken();
		String viewerToken = registerAndGetToken();
		String viewerUsername = jwtSubject(viewerToken);

		MvcResult created = mockMvc.perform(post("/api/lists")
				.header("Authorization", bearer(ownerToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title":"Shared list"}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		long listId = jsonLong(created.getResponse().getContentAsString(), "id");

		mockMvc.perform(put("/api/lists/{listId}/shares/{username}", listId, viewerUsername)
				.header("Authorization", bearer(ownerToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"permission":"viewer"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.permission", is("viewer")));

		mockMvc.perform(get("/api/lists")
				.header("Authorization", bearer(viewerToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id", is((int) listId)))
			.andExpect(jsonPath("$[0].permission", is("VIEWER")));
		mockMvc.perform(get("/api/lists/{listId}", listId)
				.header("Authorization", bearer(viewerToken)))
			.andExpect(status().isOk());

		mockMvc.perform(put("/api/lists/{listId}/shares/{username}", listId, viewerUsername)
				.header("Authorization", bearer(ownerToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"permission":"editor"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.permission", is("editor")));
		mockMvc.perform(get("/api/lists")
				.header("Authorization", bearer(viewerToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].permission", is("EDITOR")));
		mockMvc.perform(post("/api/lists/{listId}/items", listId)
				.header("Authorization", bearer(viewerToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Bread","quantity":1,"unit":"piece"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.createdBy", is(viewerUsername)));

		mockMvc.perform(put("/api/lists/{listId}/shares/{username}", listId, viewerUsername)
				.header("Authorization", bearer(ownerToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"permission":"viewer"}
					"""))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/lists/{listId}/items", listId)
				.header("Authorization", bearer(viewerToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Forbidden","quantity":1,"unit":"piece"}
					"""))
			.andExpect(status().isForbidden());

		mockMvc.perform(patch("/api/lists/{listId}", listId)
				.header("Authorization", bearer(viewerToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title":"Unauthorized rename"}
					"""))
			.andExpect(status().isForbidden());

		mockMvc.perform(delete("/api/lists/{listId}/shares/{username}", listId, viewerUsername)
				.header("Authorization", bearer(ownerToken)))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/lists/{listId}", listId)
				.header("Authorization", bearer(viewerToken)))
			.andExpect(status().isForbidden());
	}

	@Test
	void onlyListOwnersAndEditorsCanRemoveItemsFromThatList() throws Exception {
		String ownerToken = registerAndGetToken();
		String editorToken = registerAndGetToken();
		String viewerToken = registerAndGetToken();

		long listId = createList(ownerToken, "Shared list");
		long otherListId = createList(ownerToken, "Other list");
		shareList(ownerToken, listId, jwtSubject(editorToken), "editor");
		shareList(ownerToken, listId, jwtSubject(viewerToken), "viewer");
		long itemId = createItem(ownerToken, listId);
		long otherItemId = createItem(ownerToken, otherListId);

		mockMvc.perform(delete("/api/lists/{listId}/items/{itemId}", listId, otherItemId)
				.header("Authorization", bearer(ownerToken)))
			.andExpect(status().isNotFound());
		org.junit.jupiter.api.Assertions.assertEquals(1, itemCount(otherItemId));

		mockMvc.perform(delete("/api/lists/{listId}/items/{itemId}", listId, itemId)
				.header("Authorization", bearer(viewerToken)))
			.andExpect(status().isForbidden());
		org.junit.jupiter.api.Assertions.assertEquals(1, itemCount(itemId));

		mockMvc.perform(delete("/api/lists/{listId}/items/{itemId}", listId, itemId)
				.header("Authorization", bearer(editorToken)))
			.andExpect(status().isNoContent());
		org.junit.jupiter.api.Assertions.assertEquals(0, itemCount(itemId));
		mockMvc.perform(delete("/api/lists/{listId}/items/{itemId}", listId, itemId)
				.header("Authorization", bearer(editorToken)))
			.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/lists/{listId}/items/{itemId}", otherListId, otherItemId)
				.header("Authorization", bearer(ownerToken)))
			.andExpect(status().isNoContent());
	}

	private long createList(String token, String title) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/lists")
				.header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title":"%s"}
					""".formatted(title)))
			.andExpect(status().isCreated())
			.andReturn();
		return jsonLong(result.getResponse().getContentAsString(), "id");
	}

	private void shareList(String ownerToken, long listId, String username, String permission) throws Exception {
		mockMvc.perform(put("/api/lists/{listId}/shares/{username}", listId, username)
				.header("Authorization", bearer(ownerToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"permission":"%s"}
					""".formatted(permission)))
			.andExpect(status().isOk());
	}

	private long createItem(String token, long listId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/lists/{listId}/items", listId)
				.header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Item","quantity":1,"unit":"piece"}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		return jsonLong(result.getResponse().getContentAsString(), "id");
	}

	private int itemCount(long itemId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM items WHERE id = ?", Integer.class, itemId);
	}

	private String registerAndGetToken() throws Exception {
		String username = "list_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
		MvcResult result = mockMvc.perform(post("/api/users/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"username":"%s","password":"secure-pass"}
					""".formatted(username)))
			.andExpect(status().isCreated())
			.andReturn();
		return jsonValue(result.getResponse().getContentAsString(), "accessToken");
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private static String jwtSubject(String token) {
		String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]));
		return jsonValue(payload, "sub");
	}

	private static String jsonValue(String json, String name) {
		Matcher matcher = Pattern.compile("\"" + Pattern.quote(name) + "\":\"([^\"]+)\"").matcher(json);
		if (!matcher.find()) {
			throw new AssertionError("Expected JSON string field " + name);
		}
		return matcher.group(1);
	}

	private static long jsonLong(String json, String name) {
		Matcher matcher = Pattern.compile("\"" + Pattern.quote(name) + "\":(\\d+)").matcher(json);
		if (!matcher.find()) {
			throw new AssertionError("Expected JSON number field " + name);
		}
		return Long.parseLong(matcher.group(1));
	}
}
