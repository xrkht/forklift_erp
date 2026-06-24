package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.entity.OutboundOrder;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class OutboundUploadReadinessPolicyTests {

    private final OutboundUploadReadinessPolicy policy = new OutboundUploadReadinessPolicy();

    @Test
    void invoiceIsReadyWhenIssuedDateExists() {
        OutboundOrder order = new OutboundOrder();
        order.setInvoiceIssuedDate(LocalDate.now());

        assertThat(policy.isInvoiceUploadReady(order)).isTrue();
    }

    @Test
    void contractRejectsNegativeContractText() {
        OutboundOrder order = new OutboundOrder();
        order.setContractType("无合同");

        assertThat(policy.isContractUploadReady(order)).isFalse();
    }

    @Test
    void contractAcceptsPositiveContractText() {
        OutboundOrder order = new OutboundOrder();
        order.setContractType("纸质合同");

        assertThat(policy.isContractUploadReady(order)).isTrue();
    }
}
