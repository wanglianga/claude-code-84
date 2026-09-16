package com.port.inspection.exception;

import org.springframework.http.HttpStatus;

/** 业务异常：携带 HTTP 状态码 */
public class BizException extends RuntimeException {
    private final HttpStatus status;

    public BizException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public BizException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static BizException notFound(String what) {
        return new BizException(what + "不存在", HttpStatus.NOT_FOUND);
    }

    public static BizException forbidden(String what) {
        return new BizException(what, HttpStatus.FORBIDDEN);
    }
}
