package com.claimswift.claimservice.exception;

public class ClaimNotFoundException extends RuntimeException {
    
    public ClaimNotFoundException(String message) {
        super(message);
    }
    
    public ClaimNotFoundException(Long id) {
        super("Claim not found with id: " + id);
    }
    
    public ClaimNotFoundException(String claimNumber, boolean byNumber) {
        super("Claim not found with number: " + claimNumber);
    }
}