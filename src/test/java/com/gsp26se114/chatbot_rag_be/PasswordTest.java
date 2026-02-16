package com.gsp26se114.chatbot_rag_be;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordTest {
    
    @Test
    public void testPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        // Test if the hash in data.sql matches "123456"
        String hashInDatabase = "$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00dmwtfXz9Re6.";
        boolean matches = encoder.matches("123456", hashInDatabase);
        
        System.out.println("Does the hash match '123456'? " + matches);
        
        // Generate a new hash for reference
        String newHash = encoder.encode("123456");
        System.out.println("New BCrypt hash for '123456': " + newHash);
        
        // Test if new hash also matches
        boolean newMatches = encoder.matches("123456", newHash);
        System.out.println("Does the new hash match '123456'? " + newMatches);
    }
}
