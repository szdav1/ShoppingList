package com.szdp.shoppinglistapi.list;

import com.szdp.shoppinglistapi.user.User;
import com.szdp.shoppinglistapi.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class ShoppingListService {
	private final ShoppingListRepository lists;
	private final ListShareRepository shares;
	private final ShoppingListItemRepository items;
	private final UserRepository users;

	public ShoppingListService(
		ShoppingListRepository lists,
		ListShareRepository shares,
		ShoppingListItemRepository items,
		UserRepository users
	) {
		this.lists = lists;
		this.shares = shares;
		this.items = items;
		this.users = users;
	}

	@Transactional
	public ShoppingList create(String ownerUsername, String title) {
		return lists.save(new ShoppingList(requireUser(ownerUsername), title.trim()));
	}

	@Transactional(readOnly = true)
	public List<ListResponse> findVisible(String username) {
		User user = requireUser(username);
		return lists.findVisibleToUser(user.getId()).stream()
			.map(shoppingList -> toResponse(shoppingList, permissionFor(shoppingList, user)))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<ListResponse> findArchived(String username) {
		User owner = requireUser(username);
		return lists.findAllByOwner_IdAndDeletedTrueOrderByDeletedAtDesc(owner.getId()).stream()
			.map(shoppingList -> toResponse(shoppingList, "OWNER"))
			.toList();
	}

	@Transactional(readOnly = true)
	public ListResponse findById(Long listId, String username) {
		User user = requireUser(username);
		ShoppingList shoppingList = findActiveList(listId);
		String permission = permissionFor(shoppingList, user);
		if (permission == null) {
			throw new AccessDeniedException("You do not have access to this list");
		}
		return toResponse(shoppingList, permission);
	}

	@Transactional
	public ItemResponse addItem(Long listId, String username, String name, BigDecimal quantity, String unit) {
		User user = requireUser(username);
		ShoppingList shoppingList = requireEditableList(listId, user, "add items");

		ShoppingListItem item = items.save(new ShoppingListItem(
			shoppingList,
			name.trim(),
			quantity,
			unit.trim(),
			user
		));
		shoppingList.touch();
		return toItemResponse(item);
	}

	@Transactional
	public void removeItem(Long listId, Long itemId, String username) {
		User user = requireUser(username);
		ShoppingList shoppingList = requireEditableList(listId, user, "remove items");
		ShoppingListItem item = items.findByIdAndShoppingList_Id(itemId, listId)
			.orElseThrow(() -> new EntityNotFoundException("List item not found"));
		items.delete(item);
		shoppingList.touch();
	}

	@Transactional
	public ListResponse rename(Long listId, String username, String title) {
		ShoppingList shoppingList = requireOwnedActiveList(listId, username);
		shoppingList.rename(title.trim());
		return toResponse(shoppingList, "OWNER");
	}

	@Transactional
	public void archive(Long listId, String username) {
		ShoppingList shoppingList = requireOwnedActiveList(listId, username);
		shoppingList.archive();
	}

	@Transactional
	public ListResponse restore(Long listId, String username) {
		User owner = requireUser(username);
		ShoppingList shoppingList = lists.findById(listId)
			.filter(candidate -> candidate.isDeleted() && candidate.getOwner().getId().equals(owner.getId()))
			.orElseThrow(() -> new EntityNotFoundException("Archived list not found"));
		if (shoppingList.getDeletedAt().isBefore(Instant.now().minus(30, ChronoUnit.DAYS))) {
			throw new EntityNotFoundException("Archived list has expired");
		}
		shoppingList.restore();
		return toResponse(shoppingList, "OWNER");
	}

	@Transactional
	public List<ShareResponse> findShares(Long listId, String username) {
		ShoppingList shoppingList = requireOwnedActiveList(listId, username);
		return shares.findAllByShoppingList_IdOrderByCreatedAtAsc(shoppingList.getId()).stream()
			.map(share -> new ShareResponse(
				share.getUser().getUsername(),
				share.getPermission().name().toLowerCase(Locale.ROOT),
				share.getCreatedAt()
			))
			.toList();
	}

	@Transactional
	public ShareResponse share(Long listId, String ownerUsername, String targetUsername, String permission) {
		ShoppingList shoppingList = requireOwnedActiveList(listId, ownerUsername);
		if (ownerUsername.equals(targetUsername)) {
			throw new IllegalArgumentException("A list cannot be shared with its owner");
		}
		User target = users.findByUsername(targetUsername)
			.orElseThrow(() -> new EntityNotFoundException("Target username does not exist"));
		SharePermission requestedPermission = SharePermission.valueOf(permission.toUpperCase(Locale.ROOT));
		ListShare share = shares.findByShoppingList_IdAndUser_Id(listId, target.getId())
			.map(existing -> {
				existing.updatePermission(requestedPermission);
				return existing;
			})
			.orElseGet(() -> shares.save(new ListShare(shoppingList, target, requestedPermission)));
		return new ShareResponse(target.getUsername(), permission.toLowerCase(Locale.ROOT), share.getCreatedAt());
	}

	@Transactional
	public void revokeShare(Long listId, String ownerUsername, String targetUsername) {
		ShoppingList shoppingList = requireOwnedActiveList(listId, ownerUsername);
		if (shares.findByShoppingList_IdAndUser_Username(shoppingList.getId(), targetUsername).isEmpty()) {
			throw new EntityNotFoundException("List share not found");
		}
		shares.deleteByShoppingList_IdAndUser_Username(shoppingList.getId(), targetUsername);
	}

	private ShoppingList requireOwnedActiveList(Long listId, String username) {
		ShoppingList shoppingList = findActiveList(listId);
		if (!shoppingList.getOwner().getUsername().equals(username)) {
			throw new AccessDeniedException("Only the list owner can manage this list");
		}
		return shoppingList;
	}

	private ShoppingList requireEditableList(Long listId, User user, String action) {
		ShoppingList shoppingList = findActiveList(listId);
		String permission = permissionFor(shoppingList, user);
		if (permission == null) {
			throw new AccessDeniedException("You do not have access to this list");
		}
		if ("VIEWER".equals(permission)) {
			throw new AccessDeniedException("Viewers cannot " + action + " on a list");
		}
		return shoppingList;
	}

	private ShoppingList findActiveList(Long listId) {
		return lists.findByIdAndDeletedFalse(listId)
			.orElseThrow(() -> new EntityNotFoundException("List not found"));
	}

	private User requireUser(String username) {
		return users.findByUsername(username)
			.orElseThrow(() -> new EntityNotFoundException("User not found"));
	}

	private String permissionFor(ShoppingList shoppingList, User user) {
		if (shoppingList.getOwner().getId().equals(user.getId())) {
			return "OWNER";
		}
		return shares.findByShoppingList_IdAndUser_Id(shoppingList.getId(), user.getId())
			.map(share -> share.getPermission().name())
			.orElse(null);
	}

	private static ListResponse toResponse(ShoppingList shoppingList, String permission) {
		return new ListResponse(
			shoppingList.getId(),
			shoppingList.getTitle(),
			shoppingList.getOwner().getUsername(),
			permission,
			shoppingList.isDeleted(),
			shoppingList.getCreatedAt(),
			shoppingList.getUpdatedAt(),
			shoppingList.getDeletedAt()
		);
	}

	private static ItemResponse toItemResponse(ShoppingListItem item) {
		return new ItemResponse(
			item.getId(),
			item.getShoppingList().getId(),
			item.getName(),
			item.getQuantity(),
			item.getUnit(),
			item.isChecked(),
			item.getCreatedBy().getUsername(),
			item.getCreatedAt(),
			item.getUpdatedAt()
		);
	}

	public record ListResponse(
		Long id,
		String title,
		String ownerUsername,
		String permission,
		boolean deleted,
		Instant createdAt,
		Instant updatedAt,
		Instant deletedAt
	) {
	}

	public record ShareResponse(String username, String permission, Instant createdAt) {
	}

	public record ItemResponse(
		Long id,
		Long listId,
		String name,
		BigDecimal quantity,
		String unit,
		boolean checked,
		String createdBy,
		Instant createdAt,
		Instant updatedAt
	) {
	}
}
