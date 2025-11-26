package com.innowise.demo.exception;

public class CardInfoNotFoundException extends RuntimeException{
    public CardInfoNotFoundException(String message) {
        super(message);
    }
}
