package com.hsmart.backend.infrastructure.persistence;

import com.hsmart.backend.domain.entities.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
