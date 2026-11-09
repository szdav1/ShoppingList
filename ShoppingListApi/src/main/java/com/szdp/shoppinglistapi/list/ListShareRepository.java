package com.szdp.shoppinglistapi.list;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ListShareRepository extends JpaRepository<ListShare, Long> {
    List<ListShare> findAllByShoppingList_IdOrderByCreatedAtAsc(Long listId);

    Optional<ListShare> findByShoppingList_IdAndUser_Id(Long listId, Long userId);

    Optional<ListShare> findByShoppingList_IdAndUser_Username(Long listId, String username);

    void deleteByShoppingList_IdAndUser_Username(Long listId, String username);

    void deleteAllByShoppingList_Id(Long listId);
}
