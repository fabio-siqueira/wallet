package com.rpay.wallet.controller;


import com.rpay.wallet.exception.CustomException;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class CustomControllerAdvice {

    private final Logger logger = org.slf4j.LoggerFactory.getLogger(CustomControllerAdvice.class);

    @ExceptionHandler(CustomException.class)
    public ErrorResponse handleCustomException(CustomException customException) {
        return ErrorResponse.builder(customException, customException.getHttpStatus(), customException.getMessage())
                .build();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationExceptions(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ErrorResponse.builder(ex, HttpStatus.BAD_REQUEST, "Validation failed: " + String.join(", ", errors))
                .build();
    }

    @ExceptionHandler(Exception.class)
    public ErrorResponse handleGenericException(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse)
            return errorResponse;

        logger.error("An unexpected error occurred.", ex);
        return ErrorResponse.builder(ex, HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.")
                .build();
    }
}
