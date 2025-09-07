package com.rpay.wallet.exception;

import static org.springframework.http.HttpStatus.CONFLICT;

public class ConflictException extends CustomException {

    public ConflictException(String message) {
        super(CONFLICT, message);
    }
}
