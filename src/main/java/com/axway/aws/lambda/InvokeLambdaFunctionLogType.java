package com.axway.aws.lambda;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Tipos de log do Invoke Lambda Function
 * Thread-safe e imutável
 */
public class InvokeLambdaFunctionLogType {
    
    /**
     * Map imutável com os tipos de log disponíveis
     */
    public static final Map<String, String> LOG_TYPES;
    
    static {
        Map<String, String> init = new HashMap<>();
        init.put("None", "None");
        init.put("Tail", "Tail");
        LOG_TYPES = Collections.unmodifiableMap(init);
    }
    
    /**
     * Construtor privado para evitar instanciação
     */
    private InvokeLambdaFunctionLogType() {
        // Utility class - não deve ser instanciada
    }
} 