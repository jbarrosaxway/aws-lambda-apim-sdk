package com.axway.aws.lambda;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to provide boolean options for ComboAttribute
 * Thread-safe e imutável
 */
public class AWSBooleanOptions {
    
    /**
     * Construtor privado para evitar instanciação
     */
    private AWSBooleanOptions() {
        // Utility class - não deve ser instanciada
    }
    
    /**
     * Provides boolean options for UI components
     * @return Map imutável com opções booleanas
     */
    public static Map<String, String> booleanOptions() {
        Map<String, String> options = new HashMap<>();
        options.put("true", "Yes");
        options.put("false", "No");
        return Collections.unmodifiableMap(options);
    }
} 