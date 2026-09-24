package com.rikkeibank.customer.service;

import com.rikkeibank.common.dto.CustomerDto;
import com.rikkeibank.customer.entity.Customer;
import com.rikkeibank.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    private Customer sampleCustomer;

    @BeforeEach
    void setUp() {
        sampleCustomer = Customer.builder()
                .id(1L)
                .cifCode("CIF100001")
                .fullName("Nguyen Van A")
                .identityNumber("001200000001")
                .email("test@rikkeibank.com")
                .phoneNumber("0901234567")
                .address("Hanoi")
                .dateOfBirth(LocalDate.of(1995, 1, 1))
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("Should retrieve customer by ID successfully")
    void testGetCustomerById() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(sampleCustomer));

        CustomerDto dto = customerService.getCustomerById(1L);

        assertNotNull(dto);
        assertEquals("Nguyen Van A", dto.getFullName());
        assertEquals("CIF100001", dto.getCifCode());
        verify(customerRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should throw exception when customer ID not found")
    void testGetCustomerNotFound() {
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> customerService.getCustomerById(999L));
    }

    @Test
    @DisplayName("Should create new customer successfully")
    void testCreateCustomer() {
        CustomerDto input = CustomerDto.builder()
                .fullName("Tran Van B")
                .identityNumber("001200000002")
                .email("b@rikkeibank.com")
                .phoneNumber("0912345678")
                .address("Da Nang")
                .build();

        when(customerRepository.existsByIdentityNumber("001200000002")).thenReturn(false);
        when(customerRepository.existsByEmail("b@rikkeibank.com")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> {
            Customer c = i.getArgument(0);
            c.setId(2L);
            return c;
        });

        CustomerDto created = customerService.createCustomer(input);

        assertNotNull(created);
        assertEquals("Tran Van B", created.getFullName());
        assertNotNull(created.getCifCode());
        assertTrue(created.getCifCode().startsWith("CIF"));
    }
}
