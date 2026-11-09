package com.szdp.shoppinglistapi.list;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/lists")
@SecurityRequirement(name = "Bearer Authentication")
public class ShoppingListController {
	private final ShoppingListService service;

	public ShoppingListController(ShoppingListService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a shopping list")
	public ShoppingListService.ListResponse create(
		@Valid @RequestBody TitleRequest request,
		@AuthenticationPrincipal Jwt jwt
	) {
		ShoppingList list = service.create(jwt.getSubject(), request.title());
		return new ShoppingListService.ListResponse(
			list.getId(), list.getTitle(), list.getOwner().getUsername(), "OWNER",
			false, list.getCreatedAt(), list.getUpdatedAt(), null
		);
	}

	@GetMapping
	@Operation(summary = "Get active lists owned by or shared with the current user")
	public List<ShoppingListService.ListResponse> findVisible(@AuthenticationPrincipal Jwt jwt) {
		return service.findVisible(jwt.getSubject());
	}

	@GetMapping("/archive")
	@Operation(summary = "Get archived lists owned by the current user")
	public List<ShoppingListService.ListResponse> findArchived(@AuthenticationPrincipal Jwt jwt) {
		return service.findArchived(jwt.getSubject());
	}

	@GetMapping("/{listId}")
	public ShoppingListService.ListResponse findById(
		@PathVariable Long listId,
		@AuthenticationPrincipal Jwt jwt
	) {
		return service.findById(listId, jwt.getSubject());
	}

	@PostMapping("/{listId}/items")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Add an item to a list (owner or editor)")
	public ShoppingListService.ItemResponse addItem(
		@PathVariable Long listId,
		@Valid @RequestBody AddItemRequest request,
		@AuthenticationPrincipal Jwt jwt
	) {
		return service.addItem(listId, jwt.getSubject(), request.name(), request.quantity(), request.unit());
	}

	@DeleteMapping("/{listId}/items/{itemId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Remove an item from a list (owner or editor)")
	public void removeItem(
		@PathVariable Long listId,
		@PathVariable Long itemId,
		@AuthenticationPrincipal Jwt jwt
	) {
		service.removeItem(listId, itemId, jwt.getSubject());
	}

	@PatchMapping("/{listId}")
	@Operation(summary = "Rename an owned list")
	public ShoppingListService.ListResponse rename(
		@PathVariable Long listId,
		@Valid @RequestBody TitleRequest request,
		@AuthenticationPrincipal Jwt jwt
	) {
		return service.rename(listId, jwt.getSubject(), request.title());
	}

	@DeleteMapping("/{listId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Archive an owned list")
	public void archive(@PathVariable Long listId, @AuthenticationPrincipal Jwt jwt) {
		service.archive(listId, jwt.getSubject());
	}

	@PostMapping("/{listId}/restore")
	@Operation(summary = "Restore an archived list within 30 days of deletion")
	public ShoppingListService.ListResponse restore(
		@PathVariable Long listId,
		@AuthenticationPrincipal Jwt jwt
	) {
		return service.restore(listId, jwt.getSubject());
	}

	@GetMapping("/{listId}/shares")
	public List<ShoppingListService.ShareResponse> findShares(
		@PathVariable Long listId,
		@AuthenticationPrincipal Jwt jwt
	) {
		return service.findShares(listId, jwt.getSubject());
	}

	@PutMapping("/{listId}/shares/{username}")
	@Operation(summary = "Share a list or update the recipient's permission")
	public ShoppingListService.ShareResponse share(
		@PathVariable Long listId,
		@PathVariable String username,
		@Valid @RequestBody ShareRequest request,
		@AuthenticationPrincipal Jwt jwt
	) {
		return service.share(listId, jwt.getSubject(), username, request.permission());
	}

	@DeleteMapping("/{listId}/shares/{username}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Revoke a list share")
	public void revokeShare(
		@PathVariable Long listId,
		@PathVariable String username,
		@AuthenticationPrincipal Jwt jwt
	) {
		service.revokeShare(listId, jwt.getSubject(), username);
	}

	public record TitleRequest(@NotBlank @Size(max = 255) String title) {
	}

	public record ShareRequest(
		@NotBlank @Pattern(regexp = "editor|viewer") String permission
	) {
	}

	public record AddItemRequest(
		@NotBlank @Size(max = 255) String name,
		@NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal quantity,
		@NotBlank @Size(max = 50) String unit
	) {
	}
}
