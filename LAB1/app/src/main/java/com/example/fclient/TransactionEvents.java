package com.example.fclient;

public interface TransactionEvents {
    String enterPin(int ptc, String amount);
    void transactionResult(boolean success);
}