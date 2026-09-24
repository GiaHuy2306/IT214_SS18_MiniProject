package com.rikkeibank.customer.repository;

import com.rikkeibank.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByCifCode(String cifCode);
    Optional<Customer> findByIdentityNumber(String identityNumber);
    Optional<Customer> findByEmail(String email);
    Optional<Customer> findByPhoneNumber(String phoneNumber);
    boolean existsByIdentityNumber(String identityNumber);
    boolean existsByEmail(String email);
}
