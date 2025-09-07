package com.rpay.wallet.exception;

import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

public class UnprocessableEntityException extends CustomException {

    public UnprocessableEntityException(String message) {
        super(UNPROCESSABLE_ENTITY, message);
    }
}
