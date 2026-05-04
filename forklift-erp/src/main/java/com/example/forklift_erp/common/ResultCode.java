// src/main/java/com/example/forklift_erp/common/ResultCode.java
package com.example.forklift_erp.common;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    ERROR(500, "操作失败"),
    VALIDATE_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    CONFLICT(409, "资源冲突"),
    PARAM_ERROR(4001, "参数错误"),
    DATA_NOT_FOUND(4004, "数据不存在"),
    DATA_DUPLICATE(4005, "数据重复"),
    BUSINESS_ERROR(5001, "业务异常"),
    CONFIG_IN_USE(5002, "配置项正在使用中，无法删除"),
    VEHICLE_NOT_FOUND(5003, "车辆信息不存在"),
    PART_NOT_FOUND(5004, "配件信息不存在"),
    INSUFFICIENT_STOCK(5005, "库存不足"),
    SYSTEM_ERROR(9999, "系统异常");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}