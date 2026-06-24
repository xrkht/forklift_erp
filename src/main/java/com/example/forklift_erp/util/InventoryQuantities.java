package com.example.forklift_erp.util;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.exception.BusinessException;

import java.util.function.IntFunction;

public final class InventoryQuantities {
    private InventoryQuantities() {
    }

    public static int quantity(Integer value) {
        return value == null ? 0 : value;
    }

    public static int nonNegative(Integer value) {
        return Math.max(0, quantity(value));
    }

    public static int requireNonNegative(Integer value, String message) {
        int quantity = quantity(value);
        if (quantity < 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, message);
        }
        return quantity;
    }

    public static int requirePositive(Integer value, String message) {
        int quantity = quantity(value);
        if (quantity <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, message);
        }
        return quantity;
    }

    public static QuantityChange inbound(Integer currentQuantity, Integer quantity, String invalidQuantityMessage) {
        int before = nonNegative(currentQuantity);
        int delta = requirePositive(quantity, invalidQuantityMessage);
        return new QuantityChange(before, delta, before + delta);
    }

    public static QuantityChange outbound(
            Integer currentQuantity,
            Integer quantity,
            String invalidQuantityMessage,
            String insufficientStockMessage
    ) {
        return outbound(currentQuantity, quantity, invalidQuantityMessage, before -> insufficientStockMessage + before);
    }

    public static QuantityChange outbound(
            Integer currentQuantity,
            Integer quantity,
            String invalidQuantityMessage,
            IntFunction<String> insufficientStockMessage
    ) {
        int before = nonNegative(currentQuantity);
        int delta = requirePositive(quantity, invalidQuantityMessage);
        if (before < delta) {
            throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, insufficientStockMessage.apply(before));
        }
        return new QuantityChange(before, delta, before - delta);
    }

    public static QuantityChange adjustTo(Integer currentQuantity, Integer targetQuantity, String invalidQuantityMessage) {
        int before = nonNegative(currentQuantity);
        int after = requireNonNegative(targetQuantity, invalidQuantityMessage);
        return new QuantityChange(before, Math.abs(after - before), after);
    }

    public record QuantityChange(int beforeQuantity, int quantity, int afterQuantity) {
    }
}
