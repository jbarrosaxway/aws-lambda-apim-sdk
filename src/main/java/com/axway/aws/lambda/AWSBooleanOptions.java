package com.axway.aws.lambda;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to provide boolean options for ComboAttribute
 * Thread-safe and immutable
 */
public class AWSBooleanOptions {
    
    /**
     * Private constructor to prevent instantiation
     */
    private AWSBooleanOptions() {
        // Utility class - should not be instantiated
    }
    
    /**
     * Provides boolean options for UI components
     * @return Immutable map with boolean options
     */
    public static Map<String, String> booleanOptions() {
        Map<String, String> options = new HashMap<>();
        options.put("true", "Yes");
        options.put("false", "No");
        return Collections.unmodifiableMap(options);
    }
} 