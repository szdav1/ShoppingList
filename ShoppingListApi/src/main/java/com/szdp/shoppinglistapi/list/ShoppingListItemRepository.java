package com.szdp.shoppinglistapi.list;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, Long> {
	Optional<ShoppingListItem> findByIdAndShoppingList_Id(Long id, Long listId);
}
