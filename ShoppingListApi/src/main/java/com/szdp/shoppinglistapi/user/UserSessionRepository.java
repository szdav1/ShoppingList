package com.szdp.shoppinglistapi.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    boolean existsByToken(String token);

    void deleteByToken(String token);

    void deleteAllByUser_Id(Long userId);
}
