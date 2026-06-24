package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.Customer;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CustomerDTO {
    private Long version;

    @NotBlank(message = "公司名称不能为空")
    private String companyName;

    private String address;
    private String contactName;
    private String contactPhone;
    private String taxOrIdNumber;
    private String remarks;

    public Customer toEntity() {
        Customer customer = new Customer();
        updateEntity(customer);
        return customer;
    }

    public void updateEntity(Customer customer) {
        customer.setCompanyName(trim(companyName));
        customer.setAddress(trim(address));
        customer.setContactName(trim(contactName));
        customer.setContactPhone(trim(contactPhone));
        customer.setTaxOrIdNumber(trim(taxOrIdNumber));
        customer.setRemarks(trim(remarks));
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
