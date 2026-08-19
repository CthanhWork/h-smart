package com.hsmart.backend.infrastructure.persistence;

import com.hsmart.backend.domain.entities.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsernameOrEmail(String username, String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    Page<User> findByActive(boolean active, Pageable pageable);

    @Query("""
            SELECT u
            FROM User u
            WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
            """)
    Page<User> searchForAdmin(@Param("search") String search, Pageable pageable);

    @Query("""
            SELECT u
            FROM User u
            WHERE u.active = :isActive
              AND (
                    LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """)
    Page<User> searchForAdminByActive(@Param("search") String search, @Param("isActive") boolean isActive, Pageable pageable);
}
