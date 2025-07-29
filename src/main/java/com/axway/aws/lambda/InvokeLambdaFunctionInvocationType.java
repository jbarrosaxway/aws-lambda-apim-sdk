package com.axway.aws.lambda;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Invocation types for Invoke Lambda Function
 * Thread-safe and immutable
 */
public class InvokeLambdaFunctionInvocationType {
    
    /**
     * Immutable map with available invocation types
     */
    public static final Map<String, String> INVOCATION_TYPES;
    
    static {
        Map<String, String> init = new HashMap<>();
        init.put("RequestResponse", "RequestResponse");
        init.put("Event", "Event");
        init.put("DryRun", "DryRun");
        INVOCATION_TYPES = Collections.unmodifiableMap(init);
    }
    
    /**
     * Private constructor to prevent instantiation
     */
    private InvokeLambdaFunctionInvocationType() {
        // Utility class - should not be instantiated
    }
} 