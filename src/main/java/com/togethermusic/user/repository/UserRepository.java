package com.togethermusic.user.repository;

import com.togethermusic.user.entity.RegisteredUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<RegisteredUser, Long> {

    Optional<RegisteredUser> findByUsername(String username);

    Optional<RegisteredUser> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
