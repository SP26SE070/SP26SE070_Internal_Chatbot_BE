package com.gsp26se114.chatbot_rag_be.util;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Utility class for user-related operations
 */
public class UserUtil {
    
    private static final SecureRandom random = new SecureRandom();
    
    /**
     * Generate random secure password
     * Format: 12 characters with uppercase, lowercase, digits, special chars
     * Example: K9mZ#pQ2wX!r
     */
    public static String generateRandomPassword() {
        String upperCase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowerCase = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String special = "!@#$%^&*";
        String allChars = upperCase + lowerCase + digits + special;
        
        StringBuilder password = new StringBuilder(12);
        
        // Ensure at least 1 character of each type
        password.append(upperCase.charAt(random.nextInt(upperCase.length())));
        password.append(lowerCase.charAt(random.nextInt(lowerCase.length())));
        password.append(digits.charAt(random.nextInt(digits.length())));
        password.append(special.charAt(random.nextInt(special.length())));
        
        // Fill remaining 8 characters randomly
        for (int i = 4; i < 12; i++) {
            password.append(allChars.charAt(random.nextInt(allChars.length())));
        }
        
        // Shuffle to avoid predictable pattern
        return shuffleString(password.toString());
    }
    
    /**
     * Convert full name to username for login email
     * 
     * Examples:
     * - "Phạm Hồng Quân" → "quanph" (tên + họ + đệm)
     * - "Phạm Văn Quân" → "quanpv"
     * - "Nguyễn Văn Thành Long" → "longnvt"
     * - "Nguyễn Quân" → "quann"
     * - "Quân" → "quan"
     * 
     * Format: {tên}{chữ_cái_đầu_họ}{chữ_cái_đầu_các_tên_đệm}
     */
    public static String convertFullNameToUsername(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name cannot be empty");
        }
        
        // Normalize whitespace
        String[] parts = fullName.trim().replaceAll("\\s+", " ").split(" ");
        
        if (parts.length == 1) {
            // Only 1 word: "Quân" → "quan"
            return removeAccent(parts[0]).toLowerCase();
        }
        
        // Họ = First word
        String lastName = parts[0];
        char lastNameInitial = lastName.charAt(0);
        
        // Tên = Last word
        String firstName = parts[parts.length - 1];
        
        // Tên đệm = All words between first and last
        StringBuilder middleNameInitials = new StringBuilder();
        for (int i = 1; i < parts.length - 1; i++) {
            middleNameInitials.append(parts[i].charAt(0));
        }
        
        // Combine: {firstName}{lastNameInitial}{middleNameInitials}
        String username = firstName + lastNameInitial + middleNameInitials.toString();
        
        return removeAccent(username).toLowerCase();
    }
    
    /**
     * Remove Vietnamese accents and convert to ASCII
     * 
     * Examples:
     * - "Quân" → "quan"
     * - "Hồng" → "hong"
     * - "Đức" → "duc"
     */
    public static String removeAccent(String text) {
        if (text == null) {
            return "";
        }
        
        // Normalize Unicode (decompose accents)
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        
        // Remove combining diacritical marks
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String result = pattern.matcher(normalized).replaceAll("");
        
        // Handle special Vietnamese characters
        result = result.replaceAll("đ", "d").replaceAll("Đ", "D");
        
        // Keep only letters and numbers
        result = result.replaceAll("[^a-zA-Z0-9]", "");
        
        return result;
    }
    
    /**
     * Shuffle string characters randomly
     */
    private static String shuffleString(String input) {
        char[] characters = input.toCharArray();
        for (int i = characters.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = characters[i];
            characters[i] = characters[j];
            characters[j] = temp;
        }
        return new String(characters);
    }
}
