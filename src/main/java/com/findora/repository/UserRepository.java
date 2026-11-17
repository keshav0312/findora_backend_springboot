package com.findora.repository;

import com.findora.model.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);

    Optional<User> findByEmailVerificationTokenAndEmailVerificationExpiresAfter(String token, Instant now);

    Optional<User> findByResetPasswordTokenAndResetPasswordExpiresAfter(String token, Instant now);

    List<User> findTop20ByRoleOrderByTrustPointsDesc(String role);

    List<User> findTop200ByOrderByCreatedAtDesc();
}
