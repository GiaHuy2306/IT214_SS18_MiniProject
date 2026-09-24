package com.rikkeibank.customer.service;

import com.rikkeibank.common.dto.CustomerDto;
import com.rikkeibank.customer.entity.Customer;
import com.rikkeibank.customer.repository.CustomerRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    @PostConstruct
    public void initDefaultCustomers() {
        if (customerRepository.count() == 0) {
            log.info("Initializing sample customer profiles");
            customerRepository.save(Customer.builder()
                    .cifCode("CIF100001")
                    .fullName("Nguyen Van A")
                    .identityNumber("001200000001")
                    .email("customer@rikkeibank.com")
                    .phoneNumber("0901234567")
                    .address("123 Tran Duy Hung, Cau Giay, Hanoi")
                    .dateOfBirth(LocalDate.of(1995, 5, 20))
                    .status("ACTIVE")
                    .build());

            customerRepository.save(Customer.builder()
                    .cifCode("CIF100002")
                    .fullName("Tran Thi B")
                    .identityNumber("001200000002")
                    .email("customer2@rikkeibank.com")
                    .phoneNumber("0912345678")
                    .address("456 Le Duan, District 1, Ho Chi Minh")
                    .dateOfBirth(LocalDate.of(1998, 10, 15))
                    .status("ACTIVE")
                    .build());
        }
    }

    public List<CustomerDto> getAllCustomers() {
        log.info("Querying all customers from Database");
        return customerRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "customers", key = "#id")
    public CustomerDto getCustomerById(Long id) {
        log.info("--- [CACHE MISS] Fetching customer ID {} from Database ---", id);
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with ID: " + id));
        return mapToDto(customer);
    }

    @Transactional
    public CustomerDto createCustomer(CustomerDto dto) {
        if (customerRepository.existsByIdentityNumber(dto.getIdentityNumber())) {
            throw new IllegalArgumentException("Identity number already exists: " + dto.getIdentityNumber());
        }
        if (customerRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + dto.getEmail());
        }

        String cifCode = "CIF" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Customer customer = Customer.builder()
                .cifCode(cifCode)
                .fullName(dto.getFullName())
                .identityNumber(dto.getIdentityNumber())
                .email(dto.getEmail())
                .phoneNumber(dto.getPhoneNumber())
                .address(dto.getAddress())
                .dateOfBirth(dto.getDateOfBirth())
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Customer saved = customerRepository.save(customer);
        log.info("Created customer CIF: {}", saved.getCifCode());
        return mapToDto(saved);
    }

    @Transactional
    @CachePut(value = "customers", key = "#id")
    public CustomerDto updateCustomer(Long id, CustomerDto dto) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with ID: " + id));

        customer.setFullName(dto.getFullName());
        customer.setPhoneNumber(dto.getPhoneNumber());
        customer.setAddress(dto.getAddress());
        if (dto.getDateOfBirth() != null) {
            customer.setDateOfBirth(dto.getDateOfBirth());
        }
        if (dto.getStatus() != null) {
            customer.setStatus(dto.getStatus());
        }
        customer.setUpdatedAt(LocalDateTime.now());

        Customer updated = customerRepository.save(customer);
        log.info("--- [CACHE PUT] Updated customer ID {} and refreshed Redis cache ---", id);
        return mapToDto(updated);
    }

    @Transactional
    @CacheEvict(value = "customers", key = "#id")
    public void deleteCustomer(Long id) {
        if (!customerRepository.existsById(id)) {
            throw new IllegalArgumentException("Customer not found with ID: " + id);
        }
        customerRepository.deleteById(id);
        log.info("--- [CACHE EVICT] Evicted customer ID {} from Redis cache ---", id);
    }

    private CustomerDto mapToDto(Customer c) {
        return CustomerDto.builder()
                .id(c.getId())
                .cifCode(c.getCifCode())
                .fullName(c.getFullName())
                .identityNumber(c.getIdentityNumber())
                .email(c.getEmail())
                .phoneNumber(c.getPhoneNumber())
                .address(c.getAddress())
                .dateOfBirth(c.getDateOfBirth())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
