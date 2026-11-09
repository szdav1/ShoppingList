package com.szdp.shoppinglistapi.list;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShoppingListRepository extends JpaRepository<ShoppingList, Long> {
    @Query("""
            select distinct shoppingList
            from ShoppingList shoppingList
            left join ListShare share
                on share.shoppingList = shoppingList
                and share.user.id = :userId
            where shoppingList.deleted = false
                and (shoppingList.owner.id = :userId or share.id is not null)
            order by shoppingList.updatedAt desc
            """)
    List<ShoppingList> findVisibleToUser(@Param("userId") Long userId);

    Optional<ShoppingList> findByIdAndDeletedFalse(Long id);

    List<ShoppingList> findAllByOwner_IdAndDeletedTrueOrderByDeletedAtDesc(Long ownerId);

    @Modifying
    @Query("delete from ShoppingList shoppingList where shoppingList.deleted = true and shoppingList.deletedAt <= :cutoff")
    int deleteArchivedBefore(@Param("cutoff") Instant cutoff);
}
