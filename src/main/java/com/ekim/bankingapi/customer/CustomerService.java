package com.ekim.bankingapi.customer;

import com.ekim.bankingapi.audit.AuditLogService;
import com.ekim.bankingapi.branch.Branch;
import com.ekim.bankingapi.branch.BranchService;
import com.ekim.bankingapi.exception.DuplicateResourceException;
import com.ekim.bankingapi.exception.InvalidCredentialsException;
import com.ekim.bankingapi.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final BranchService branchService;
    private final AuditLogService auditLogService;

    public CustomerResponse createCustomer(CustomerRequest request) {
        if (customerRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already in use: " + request.getEmail());
        }
        if (customerRepository.existsByNationalId(request.getNationalId())) {
            throw new DuplicateResourceException("National ID already registered: " + request.getNationalId());
        }

        Customer customer = new Customer();
        customer.setFirstName(request.getFirstName());
        customer.setLastName(request.getLastName());
        customer.setEmail(request.getEmail());
        customer.setPhoneNumber(request.getPhoneNumber());
        customer.setNationalId(request.getNationalId());
        customer.setAge(request.getAge());
        customer.setAddress(request.getAddress());

        if (request.getBranchId() != null) {
            Branch branch = branchService.findBranchEntityById(request.getBranchId());
            customer.setBranch(branch);
        }

        Customer saved = customerRepository.save(customer);

        auditLogService.log("Customer", saved.getId(), "CREATE", "Created customer: " + saved.getEmail());

        return CustomerResponse.fromEntity(saved);
    }

    public CustomerResponse getCustomerById(Long id) {
        Customer customer = findCustomerEntityById(id);
        return CustomerResponse.fromEntity(customer);
    }

    public CustomerResponse getMyProfile() {
        Customer customer = findCustomerEntityById(currentCustomerId());
        return CustomerResponse.fromEntity(customer);
    }

    private Long currentCustomerId() {
        Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        if (!(details instanceof Long customerId)) {
            throw new InvalidCredentialsException("Unable to resolve authenticated customer");
        }
        return customerId;
    }

    public Page<CustomerResponse> getAllCustomers(Pageable pageable) {
        return customerRepository.findAll(pageable)
                .map(CustomerResponse::fromEntity);
    }

    public CustomerResponse updateCustomer(Long id, CustomerRequest request) {
        Customer existing = findCustomerEntityById(id);
        existing.setFirstName(request.getFirstName());
        existing.setLastName(request.getLastName());
        existing.setPhoneNumber(request.getPhoneNumber());
        existing.setAge(request.getAge());
        existing.setAddress(request.getAddress());

        if (request.getBranchId() != null) {
            Branch branch = branchService.findBranchEntityById(request.getBranchId());
            existing.setBranch(branch);
        }

        Customer saved = customerRepository.save(existing);

        auditLogService.log("Customer", saved.getId(), "UPDATE", "Updated customer: " + saved.getEmail());

        return CustomerResponse.fromEntity(saved);
    }

    public void deleteCustomer(Long id) {
        Customer existing = findCustomerEntityById(id);
        customerRepository.delete(existing);
        auditLogService.log("Customer", id, "DELETE", "Deleted customer: " + existing.getEmail());
    }

    public Customer findCustomerEntityById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));
    }

    public Customer findCustomerEntityByNationalId(String nationalId) {
        return customerRepository.findByNationalId(nationalId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with national ID: " + nationalId));
    }

    public Customer saveCustomerEntity(Customer customer) {
        return customerRepository.save(customer);
    }
}