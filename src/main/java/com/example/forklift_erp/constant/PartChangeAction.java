package com.example.forklift_erp.constant;

public enum PartChangeAction implements CodedEnum {
    STOCK_IN,
    DISCOUNT;

    @Override
    public String code() {
        return name();
    }
}
