package com.hsmart.backend.infrastructure.persistence;

import com.hsmart.backend.domain.entities.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Page<Product> findAllByIsDeletedFalse(Pageable pageable);
    Optional<Product> findByIdAndIsDeletedFalse(Long id);
}
