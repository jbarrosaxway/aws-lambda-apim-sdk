package com.axway.aws.lambda;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Tipos de invocação do Invoke Lambda Function
 * Thread-safe e imutável
 */
public class InvokeLambdaFunctionInvocationType {
    
    /**
     * Map imutável com os tipos de invocação disponíveis
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
     * Construtor privado para evitar instanciação
     */
    private InvokeLambdaFunctionInvocationType() {
        // Utility class - não deve ser instanciada
    }
} 