package com.ekim.bankingapi.customer;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CustomerResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String nationalId;
    private Integer age;
    private String address;
    private Long branchId;
    private String branchName;
    private Integer naturePoints;
    private Integer treesPlanted;
    private Integer dailyNaturePoints;
    private LocalDateTime createdAt;

    public static CustomerResponse fromEntity(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getPhoneNumber(),
                customer.getNationalId(),
                customer.getAge(),
                customer.getAddress(),
                customer.getBranch() != null ? customer.getBranch().getId() : null,
                customer.getBranch() != null ? customer.getBranch().getName() : null,
                customer.getNaturePoints(),
                customer.getTreesPlanted(),
                effectiveDailyNaturePoints(customer),
                customer.getCreatedAt()
        );
    }

    // dailyNaturePoints on the entity is only reset lazily on the customer's next transaction,
    // so a stale value from a previous day must be treated as 0 here.
    private static Integer effectiveDailyNaturePoints(Customer customer) {
        if (customer.getLastPointsDate() == null || !customer.getLastPointsDate().isEqual(LocalDate.now())) {
            return 0;
        }
        return customer.getDailyNaturePoints();
    }
}