package com.jrules.ruleengine.v2.auth.repository;

import com.jrules.ruleengine.v2.auth.entity.PasswordResetTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, String> {

    Optional<PasswordResetTokenEntity> findByToken(String token);

    /** Invalidate all previous unused tokens for a user before issuing a new one. */
    @Modifying
    @Query("UPDATE PasswordResetTokenEntity t SET t.used = true WHERE t.userId = :userId AND t.used = false")
    void invalidateAllForUser(String userId);
}
