package com.example.walletService.exception;

public class WalletException extends RuntimeException {

    private final int statusCode;

    public WalletException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
