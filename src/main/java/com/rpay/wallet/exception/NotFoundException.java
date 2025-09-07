package com.rpay.wallet.exception;

import static org.springframework.http.HttpStatus.NOT_FOUND;

public class NotFoundException extends CustomException {

    public NotFoundException(String message) {
        super(NOT_FOUND, message);
    }
}
