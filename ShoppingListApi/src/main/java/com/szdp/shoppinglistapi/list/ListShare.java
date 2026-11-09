package com.szdp.shoppinglistapi.list;

import com.szdp.shoppinglistapi.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "list_shares",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_list_shares_list_user",
                columnNames = {"list_id", "user_id"}
        )
)
public class ListShare {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "list_id", nullable = false)
    private ShoppingList shoppingList;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Convert(converter = SharePermissionConverter.class)
    @Column(nullable = false, length = 20)
    private SharePermission permission;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ListShare() {
    }

    public ListShare(ShoppingList shoppingList, User user, SharePermission permission) {
        this.shoppingList = shoppingList;
        this.user = user;
        this.permission = permission;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public ShoppingList getShoppingList() {
        return shoppingList;
    }

    public User getUser() {
        return user;
    }

    public SharePermission getPermission() {
        return permission;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void updatePermission(SharePermission permission) {
        this.permission = permission;
    }
}
