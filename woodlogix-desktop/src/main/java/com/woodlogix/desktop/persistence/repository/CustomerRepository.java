package com.woodlogix.desktop.persistence.repository;

import com.woodlogix.desktop.persistence.entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerRepository extends JpaRepository<CustomerEntity, String> {
    List<CustomerEntity> findByFullNameContainingIgnoreCase(String fullName);

    List<CustomerEntity>
    findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrPhoneContainingIgnoreCase(
            String fullName, String email, String phone
    );
}
