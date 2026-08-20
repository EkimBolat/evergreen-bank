package com.ekim.bankingapi.account;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AccountLookupResponse {

    private Long id;
    private String accountNumber;
    private String customerFullName;

    public static AccountLookupResponse fromEntity(Account account) {
        return new AccountLookupResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getCustomer().getFirstName() + " " + account.getCustomer().getLastName()
        );
    }
}
