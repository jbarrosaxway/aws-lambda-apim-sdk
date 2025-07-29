package com.axway.aws.lambda;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Log types for Invoke Lambda Function
 * Thread-safe and immutable
 */
public class InvokeLambdaFunctionLogType {
    
    /**
     * Immutable map with available log types
     */
    public static final Map<String, String> LOG_TYPES;
    
    static {
        Map<String, String> init = new HashMap<>();
        init.put("None", "None");
        init.put("Tail", "Tail");
        LOG_TYPES = Collections.unmodifiableMap(init);
    }
    
    /**
     * Private constructor to prevent instantiation
     */
    private InvokeLambdaFunctionLogType() {
        // Utility class - should not be instantiated
    }
} 