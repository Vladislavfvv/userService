package com.innowise.demo.exception;

/**
 * Исключение, выбрасываемое при попытке добавить карту, которая уже существует.
 */
public class CardAlreadyExistsException extends RuntimeException {
    public CardAlreadyExistsException(String message) {
        super(message);
    }
}

