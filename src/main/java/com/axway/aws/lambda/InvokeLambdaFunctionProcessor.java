package com.axway.aws.lambda;

import java.security.GeneralSecurityException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.auth.WebIdentityTokenCredentialsProvider;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.vordel.circuit.CircuitAbortException;
import com.vordel.circuit.Message;
import com.vordel.circuit.MessageProcessor;
import com.vordel.circuit.aws.AWSFactory;
import com.vordel.config.Circuit;
import com.vordel.config.ConfigContext;
import com.vordel.el.Selector;
import com.vordel.es.Entity;
import com.vordel.es.EntityStoreException;
import com.vordel.trace.Trace;
import java.nio.ByteBuffer;

/**
 * AWS Lambda Function Invoker with optimized IAM Role support
 * 
 * IAM Role Configuration:
 * - "iam" credential type: Uses WebIdentityTokenCredentialsProvider only
 *   - AWS SDK automatically handles IRSA (IAM Roles for Service Accounts) and EC2 Instance Profile
 *   - Reads environment variables (AWS_WEB_IDENTITY_TOKEN_FILE, AWS_ROLE_ARN) internally
 *   - Supports both ServiceAccount tokens and EC2 instance metadata
 * 
 * - "file" credential type: Uses ProfileCredentialsProvider with specified file
 * - "local" credential type: Uses AWSFactory for explicit credentials
 */
public class InvokeLambdaFunctionProcessor extends MessageProcessor {
	
	// Selectors for dynamic field resolution (following S3 pattern)
	protected Selector<String> functionName;
	protected Selector<String> awsRegion;
	protected Selector<String> invocationType;
	protected Selector<String> logType;
	protected Selector<String> qualifier;
	protected Selector<Integer> retryDelay;
	protected Selector<Integer> memorySize;
	protected Selector<String> credentialType;
	protected Selector<Boolean> useIAMRole;
	protected Selector<String> awsCredential;
	protected Selector<String> clientConfiguration;
	protected Selector<String> credentialsFilePath;
	
	// Invoke Lambda Function client builder (following S3 pattern)
	protected AWSLambdaClientBuilder lambdaClientBuilder;
	
	// Content body selector
	private Selector<String> contentBody = new Selector<>("${content.body}", String.class);
	
	// Configurable payload fields
	protected Selector<String> payloadMethodField;
	protected Selector<String> payloadHeadersField;
	protected Selector<String> payloadBodyField;
	protected Selector<String> payloadUriField;
	protected Selector<String> payloadQueryStringField;
	protected Selector<String> payloadParamsPathField;

	public InvokeLambdaFunctionProcessor() {
	}

	@Override
	public void filterAttached(ConfigContext ctx, Entity entity) throws EntityStoreException {
		super.filterAttached(ctx, entity);
		
		// Initialize selectors for all fields (following S3 pattern)
		this.functionName = new Selector(entity.getStringValue("functionName"), String.class);
		this.awsRegion = new Selector(entity.getStringValue("awsRegion"), String.class);
		this.invocationType = new Selector(entity.getStringValue("invocationType"), String.class);
		this.logType = new Selector(entity.getStringValue("logType"), String.class);
		this.qualifier = new Selector(entity.getStringValue("qualifier"), String.class);
		this.retryDelay = new Selector(entity.getStringValue("retryDelay"), Integer.class);
		this.memorySize = new Selector(entity.getStringValue("memorySize"), Integer.class);
		this.credentialType = new Selector(entity.getStringValue("credentialType"), String.class);
		this.useIAMRole = new Selector(entity.getStringValue("useIAMRole"), Boolean.class);
		this.awsCredential = new Selector(entity.getStringValue("awsCredential"), String.class);
		this.clientConfiguration = new Selector(entity.getStringValue("clientConfiguration"), String.class);
		this.credentialsFilePath = new Selector(entity.getStringValue("credentialsFilePath") != null ? entity.getStringValue("credentialsFilePath") : "", String.class);
		
		// Initialize configurable payload fields
		this.payloadMethodField = new Selector(entity.getStringValue("payloadMethodField"), String.class);
		this.payloadHeadersField = new Selector(entity.getStringValue("payloadHeadersField"), String.class);
		this.payloadBodyField = new Selector(entity.getStringValue("payloadBodyField"), String.class);
		this.payloadUriField = new Selector(entity.getStringValue("payloadUriField"), String.class);
		this.payloadQueryStringField = new Selector(entity.getStringValue("payloadQueryStringField"), String.class);
		this.payloadParamsPathField = new Selector(entity.getStringValue("payloadParamsPathField"), String.class);
		
		// Get client configuration (following S3 pattern exactly)
		Entity clientConfig = ctx.getEntity(entity.getReferenceValue("clientConfiguration"));
		
		// Configure Lambda client builder (following S3 pattern)
		this.lambdaClientBuilder = getLambdaClientBuilder(ctx, entity, clientConfig);
		
		Trace.info("=== Lambda Configuration (Following S3 Pattern) ===");
		Trace.info("Function: " + (functionName != null ? functionName.getLiteral() : "dynamic"));
		Trace.info("Region: " + (awsRegion != null ? awsRegion.getLiteral() : "dynamic"));
		Trace.info("Invocation Type: " + (invocationType != null ? invocationType.getLiteral() : "dynamic"));
		Trace.info("Log Type: " + (logType != null ? logType.getLiteral() : "dynamic"));
		Trace.info("Qualifier: " + (qualifier != null ? qualifier.getLiteral() : "dynamic"));
		Trace.info("Retry Delay: " + (retryDelay != null ? retryDelay.getLiteral() : "dynamic"));
		Trace.info("Memory Size: " + (memorySize != null ? memorySize.getLiteral() : "dynamic"));
		Trace.info("Credential Type: " + (credentialType != null ? credentialType.getLiteral() : "dynamic"));
		Trace.info("Use IAM Role: " + (useIAMRole != null ? useIAMRole.getLiteral() : "false"));
		Trace.info("AWS Credential: " + (awsCredential != null ? awsCredential.getLiteral() : "dynamic"));
		Trace.info("Client Configuration: " + (clientConfiguration != null ? clientConfiguration.getLiteral() : "dynamic"));
		Trace.info("Credentials File Path: " + (credentialsFilePath != null ? credentialsFilePath.getLiteral() : "dynamic"));
		Trace.info("Client Config Entity: " + (clientConfig != null ? "configured" : "default"));
	}

	/**
	 * Creates Lambda client builder following S3 pattern exactly
	 */
	private AWSLambdaClientBuilder getLambdaClientBuilder(ConfigContext ctx, Entity entity, Entity clientConfig) 
			throws EntityStoreException {
		
		// Get credentials provider based on configuration
		AWSCredentialsProvider credentialsProvider = getCredentialsProvider(ctx, entity);
		
		// Create client builder with credentials and client configuration (following S3 pattern)
		AWSLambdaClientBuilder builder = AWSLambdaClientBuilder.standard()
			.withCredentials(credentialsProvider);
		
		// Apply client configuration if available (following S3 pattern exactly)
		if (clientConfig != null) {
			ClientConfiguration clientConfiguration = createClientConfiguration(ctx, clientConfig);
			builder.withClientConfiguration(clientConfiguration);
			Trace.info("Applied custom client configuration");
		} else {
			Trace.debug("Using default client configuration");
		}
		
		return builder;
	}
	
	/**
	 * Gets the appropriate credentials provider based on configuration
	 */
	private AWSCredentialsProvider getCredentialsProvider(ConfigContext ctx, Entity entity) throws EntityStoreException {
		String credentialTypeValue = credentialType.getLiteral();
		Trace.info("=== Credentials Provider Debug ===");
		Trace.info("Credential Type Value: " + credentialTypeValue);
		
		if ("iam".equals(credentialTypeValue)) {
			// Use IAM Role - WebIdentityTokenCredentialsProvider only
			Trace.info("Using IAM Role credentials - WebIdentityTokenCredentialsProvider");
			Trace.info("Credential Type Value: " + credentialTypeValue);
			
			// Debug IRSA configuration
			Trace.info("=== IRSA Debug ===");
			Trace.info("AWS_WEB_IDENTITY_TOKEN_FILE: " + System.getenv("AWS_WEB_IDENTITY_TOKEN_FILE"));
			Trace.info("AWS_ROLE_ARN: " + System.getenv("AWS_ROLE_ARN"));
			Trace.info("AWS_REGION: " + System.getenv("AWS_REGION"));
			
			// Use WebIdentityTokenCredentialsProvider for IAM role
			Trace.info("✅ Using WebIdentityTokenCredentialsProvider for IAM role");
			return new WebIdentityTokenCredentialsProvider();
		} else if ("file".equals(credentialTypeValue)) {
			// Use credentials file
			Trace.info("Credentials Type is 'file', checking credentialsFilePath...");
			Trace.info("Credential Type Value: " + credentialTypeValue);
			String filePath = credentialsFilePath.getLiteral();
			Trace.info("File Path: " + filePath);
			Trace.info("File Path is null: " + (filePath == null));
			Trace.info("File Path is empty: " + (filePath != null && filePath.trim().isEmpty()));
			if (filePath != null && !filePath.trim().isEmpty()) {
				try {
					Trace.info("Using AWS credentials file: " + filePath);
					// Create ProfileCredentialsProvider with file path and default profile
					return new ProfileCredentialsProvider(filePath, "default");
				} catch (Exception e) {
					Trace.error("Error loading credentials file: " + e.getMessage());
					Trace.info("Falling back to DefaultAWSCredentialsProviderChain");
					return new DefaultAWSCredentialsProviderChain();
				}
			} else {
				Trace.info("Credentials file path not specified, using DefaultAWSCredentialsProviderChain");
				return new DefaultAWSCredentialsProviderChain();
			}
		} else {
			// Use explicit credentials via AWSFactory (following S3 pattern)
			Trace.info("Using explicit AWS credentials via AWSFactory");
			Trace.info("Credential Type Value: " + credentialTypeValue);
			try {
				AWSCredentials awsCredentials = AWSFactory.getCredentials(ctx, entity);
				Trace.info("AWSFactory.getCredentials() successful");
				return getAWSCredentialsProvider(awsCredentials);
			} catch (Exception e) {
				Trace.error("Error getting explicit credentials: " + e.getMessage());
				Trace.info("Falling back to DefaultAWSCredentialsProviderChain");
				return new DefaultAWSCredentialsProviderChain();
			}
		}
	}
	
	/**
	 * Creates ClientConfiguration from entity (following S3 pattern exactly)
	 */
	private ClientConfiguration createClientConfiguration(ConfigContext ctx, Entity entity) throws EntityStoreException {
		ClientConfiguration clientConfig = new ClientConfiguration();
		
		if (entity == null) {
			Trace.debug("using empty default ClientConfiguration");
			return clientConfig;
		}
		
		// Apply configuration settings with optimized single access
		setIntegerConfig(clientConfig, entity, "connectionTimeout", ClientConfiguration::setConnectionTimeout);
		setIntegerConfig(clientConfig, entity, "maxConnections", ClientConfiguration::setMaxConnections);
		setIntegerConfig(clientConfig, entity, "maxErrorRetry", ClientConfiguration::setMaxErrorRetry);
		setStringConfig(clientConfig, entity, "protocol", (config, value) -> {
			try {
				config.setProtocol(Protocol.valueOf(value));
			} catch (IllegalArgumentException e) {
				Trace.error("Invalid protocol value: " + value);
			}
		});
		setIntegerConfig(clientConfig, entity, "socketTimeout", ClientConfiguration::setSocketTimeout);
		setStringConfig(clientConfig, entity, "userAgent", ClientConfiguration::setUserAgent);
		setStringConfig(clientConfig, entity, "proxyHost", ClientConfiguration::setProxyHost);
		setIntegerConfig(clientConfig, entity, "proxyPort", ClientConfiguration::setProxyPort);
		setStringConfig(clientConfig, entity, "proxyUsername", ClientConfiguration::setProxyUsername);
		setEncryptedConfig(clientConfig, ctx, entity, "proxyPassword");
		setStringConfig(clientConfig, entity, "proxyDomain", ClientConfiguration::setProxyDomain);
		setStringConfig(clientConfig, entity, "proxyWorkstation", ClientConfiguration::setProxyWorkstation);
		
		// Handle socket buffer size hints (both must exist)
		try {
			Integer sendHint = entity.getIntegerValue("socketSendBufferSizeHint");
			Integer receiveHint = entity.getIntegerValue("socketReceiveBufferSizeHint");
			if (sendHint != null && receiveHint != null) {
				clientConfig.setSocketBufferSizeHints(sendHint, receiveHint);
			}
		} catch (Exception e) {
			// Both fields don't exist, skip silently
		}
		
		return clientConfig;
	}
	
	/**
	 * Optimized method to set integer configuration with single access
	 */
	private void setIntegerConfig(ClientConfiguration config, Entity entity, String fieldName, 
			java.util.function.BiConsumer<ClientConfiguration, Integer> setter) {
		try {
			Integer value = entity.getIntegerValue(fieldName);
			if (value != null) {
				setter.accept(config, value);
			}
		} catch (Exception e) {
			// Field doesn't exist, skip silently
		}
	}
	
	/**
	 * Optimized method to set string configuration with single access
	 */
	private void setStringConfig(ClientConfiguration config, Entity entity, String fieldName, 
			java.util.function.BiConsumer<ClientConfiguration, String> setter) {
		try {
			String value = entity.getStringValue(fieldName);
			if (value != null && !value.trim().isEmpty()) {
				setter.accept(config, value);
			}
		} catch (Exception e) {
			// Field doesn't exist, skip silently
		}
	}
	
	/**
	 * Optimized method to set encrypted configuration
	 */
	private void setEncryptedConfig(ClientConfiguration config, ConfigContext ctx, Entity entity, String fieldName) {
		try {
			byte[] encryptedBytes = ctx.getCipher().decrypt(entity.getEncryptedValue(fieldName));
			config.setProxyPassword(new String(encryptedBytes));
		} catch (GeneralSecurityException e) {
			Trace.error("Error decrypting " + fieldName + ": " + e.getMessage());
		} catch (Exception e) {
			// Field doesn't exist, skip silently
		}
	}
	
	/**
	 * Creates AWSCredentialsProvider (following S3 pattern)
	 */
	private AWSCredentialsProvider getAWSCredentialsProvider(final AWSCredentials awsCredentials) {
		return new AWSCredentialsProvider() {
			public AWSCredentials getCredentials() {
				return awsCredentials;
			}
			public void refresh() {}
		};
	}

	@Override
	public boolean invoke(Circuit arg0, Message msg) throws CircuitAbortException {
		
		if (lambdaClientBuilder == null) {
			Trace.error("Invoke Lambda Function client builder was not configured");
msg.put("aws.lambda.error", "Invoke Lambda Function client builder was not configured");
			return false;
		}
		
		// Get dynamic values using selectors (following S3 pattern)
		String functionNameValue = functionName.substitute(msg);
		String regionValue = awsRegion.substitute(msg);
		String invocationTypeValue = invocationType.substitute(msg);
		String logTypeValue = logType.substitute(msg);
		String qualifierValue = qualifier.substitute(msg);
		Integer retryDelayValue = retryDelay.substitute(msg);
		Integer memorySizeValue = memorySize.substitute(msg);
		String credentialTypeValue = credentialType.substitute(msg);
		Boolean useIAMRoleValue = useIAMRole.substitute(msg);
		String credentialsFilePathValue = credentialsFilePath.substitute(msg);

		Trace.info("=== Invocation Debug ===");
		Trace.info("Function Name: " + functionNameValue);
		Trace.info("Region: " + regionValue);
		Trace.info("Invocation Type: " + invocationTypeValue);
		Trace.info("Log Type: " + logTypeValue);
		Trace.info("Qualifier: " + qualifierValue);
		Trace.info("Retry Delay: " + retryDelayValue);
		Trace.info("Memory Size: " + memorySizeValue);
		Trace.info("Credential Type: " + credentialTypeValue);
		Trace.info("Use IAM Role: " + useIAMRoleValue);
		Trace.info("Credentials File Path: " + credentialsFilePathValue);
		
		// Set default values
		if (invocationTypeValue == null || invocationTypeValue.trim().isEmpty()) {
			invocationTypeValue = "RequestResponse";
		}
		if (logTypeValue == null || logTypeValue.trim().isEmpty()) {
			logTypeValue = "None";
		}
		if (retryDelayValue == null) {
			retryDelayValue = 1000;
		}
		if (credentialTypeValue == null || credentialTypeValue.trim().isEmpty()) {
			credentialTypeValue = "local";
		}
		// Determine IAM Role usage based on credential type
		useIAMRoleValue = "iam".equals(credentialTypeValue);
		if (memorySizeValue == null) {
			memorySizeValue = 128; // Default 128 MB
		}
		
		// Build payload based on configuration
		Trace.info("=== Building Lambda Payload ===");
		String payload = buildConfigurablePayload(msg);
		if (payload == null || payload.trim().isEmpty()) {
			// Fallback to original body if no configuration
			Trace.info("Payload from buildConfigurablePayload is null or empty, using fallback");
			payload = contentBody.substitute(msg);
			if (payload == null || payload.trim().isEmpty()) {
				payload = "{}";
				Trace.info("Using empty JSON payload: {}");
			} else {
				Trace.info("Using content.body as payload (length: " + payload.length() + ")");
			}
		}
		
		Trace.info("=== Lambda Invocation Details ===");
		Trace.info("Function Name: " + functionNameValue);
		Trace.info("Region: " + regionValue);
		Trace.info("Invocation Type: " + invocationTypeValue);
		Trace.info("Log Type: " + logTypeValue);
		Trace.info("Using IAM Role: " + useIAMRoleValue);
		Trace.info("Memory Size: " + memorySizeValue + " MB");
		Trace.info("Payload length: " + payload.length() + " characters");
		Trace.info("=== Payload that will be sent to Lambda ===");
		Trace.info(payload);
		Trace.info("=== End of Payload ===");
		
		Trace.info("Invoking Lambda function with retry...");
		
		// Debug IRSA during actual invocation
		Trace.info("=== IRSA Debug During Invoke ===");
		Trace.info("AWS_WEB_IDENTITY_TOKEN_FILE: " + System.getenv("AWS_WEB_IDENTITY_TOKEN_FILE"));
		Trace.info("AWS_ROLE_ARN: " + System.getenv("AWS_ROLE_ARN"));
		Trace.info("AWS_REGION: " + System.getenv("AWS_REGION"));
		
		Exception lastException = null;
		
		// Get maxRetries from clientConfiguration (default 3)
		int maxRetriesValue = 3; // Default value
		
		for (int attempt = 1; attempt <= maxRetriesValue; attempt++) {
			try {
				Trace.info("Attempt " + attempt + " of " + maxRetriesValue);
				
				// Create Lambda client with region (following S3 pattern)
				AWSLambda lambdaClient = lambdaClientBuilder.withRegion(regionValue).build();
				
				// Create request
				Trace.info("=== Creating Lambda Invoke Request ===");
				InvokeRequest invokeRequest = new InvokeRequest()
					.withFunctionName(functionNameValue)
					.withPayload(ByteBuffer.wrap(payload.getBytes()))
					.withInvocationType(invocationTypeValue)
					.withLogType(logTypeValue);
				
				// Add qualifier if specified
				if (qualifierValue != null && !qualifierValue.trim().isEmpty()) {
					invokeRequest.setQualifier(qualifierValue);
					Trace.info("Using qualifier: " + qualifierValue);
				}
				
				Trace.info("InvokeRequest created successfully");
				Trace.info("Payload bytes: " + payload.getBytes().length + " bytes");
				
				// Invoke Lambda function
				Trace.info("=== Invoking Lambda Function ===");
				InvokeResult invokeResult = lambdaClient.invoke(invokeRequest);
				Trace.info("Lambda function invoked successfully");
				
				// Process response
				return processInvokeResult(invokeResult, msg, memorySizeValue);
				
			} catch (Exception e) {
				lastException = e;
				Trace.error("Attempt " + attempt + " failed: " + e.getMessage());
				
				// Debug the specific error for IRSA issues
				if (e.getMessage().contains("AccessDeniedException")) {
					Trace.error("=== Access Denied Debug ===");
					Trace.error("Error message: " + e.getMessage());
					
					// Check if it's still using node group role
					if (e.getMessage().contains("axway-first-ng-role")) {
						Trace.error("❌ Still using node group role instead of ServiceAccount");
						Trace.error("This indicates IRSA is not properly configured");
					} else if (e.getMessage().contains("axway-lambda-role")) {
						Trace.error("✅ Using ServiceAccount role but permission denied");
						Trace.error("This indicates IRSA is working but role lacks permissions");
					}
				}
				
				// If not the last attempt, wait before retrying
				if (attempt < maxRetriesValue) {
					Trace.info("Waiting " + retryDelayValue + "ms before next attempt...");
					try {
						Thread.sleep(retryDelayValue);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						Trace.error("Thread interrupted during retry");
						return false;
					}
				}
			}
		}
		
		// If reached here, all attempts failed
		Trace.error("All " + maxRetriesValue + " attempts failed");
		msg.put("aws.lambda.error", "Failure after " + maxRetriesValue + " attempts: " + 
			(lastException != null ? lastException.getMessage() : "Unknown error"));
		return false;
	}
	
	/**
	 * Processes the result of the Lambda invocation
	 */
	private boolean processInvokeResult(InvokeResult invokeResult, Message msg, Integer memorySizeValue) {
		try {
			String response = new String(invokeResult.getPayload().array(), "UTF-8");
			int statusCode = invokeResult.getStatusCode();
			
			// === Lambda Response ===
			Trace.info("=== Lambda Response ===");
			Trace.info("Status Code: " + statusCode);
			Trace.info("Response: " + response);
			Trace.info("Executed Version: " + invokeResult.getExecutedVersion());
			
			if (invokeResult.getLogResult() != null) {
				Trace.info("Log Result: " + invokeResult.getLogResult());
			}
			
			// Store results
			msg.put("aws.lambda.response", response);
			msg.put("aws.lambda.http.status.code", statusCode);
			msg.put("aws.lambda.executed.version", invokeResult.getExecutedVersion());
			msg.put("aws.lambda.log.result", invokeResult.getLogResult());
			msg.put("aws.lambda.memory.size", memorySizeValue);
			
			// Check Lambda function error
			if (invokeResult.getFunctionError() != null) {
				Trace.error("Lambda function error: " + invokeResult.getFunctionError());
				msg.put("aws.lambda.error", invokeResult.getFunctionError());
				msg.put("aws.lambda.function.error", invokeResult.getFunctionError());
				return false;
			}
			
			// Check HTTP status code
			if (statusCode >= 400) {
				Trace.error("HTTP error in Lambda invocation: " + statusCode);
				msg.put("aws.lambda.error", "HTTP Error: " + statusCode);
				return false;
			}
			
			Trace.info("Lambda invocation successful");
			return true;
			
		} catch (Exception e) {
			Trace.error("Error processing Lambda response: " + e.getMessage(), e);
			msg.put("aws.lambda.error", "Error processing response: " + e.getMessage());
			return false;
		}
	}
	
	/**
	 * Gets Content-Type from message headers
	 */
	private String getContentType(Message msg) {
		try {
			java.util.Map<String, String> headerMap = extractHeaders(msg);
			Trace.debug("Extracted " + (headerMap != null ? headerMap.size() : 0) + " headers");
			if (headerMap != null) {
				// Try case-insensitive lookup
				for (java.util.Map.Entry<String, String> entry : headerMap.entrySet()) {
					if ("content-type".equalsIgnoreCase(entry.getKey())) {
						Trace.debug("Found Content-Type header: " + entry.getValue());
						return entry.getValue();
					}
				}
				Trace.debug("Content-Type header not found in headers");
				Trace.debug("Available headers: " + headerMap.keySet());
			} else {
				Trace.debug("Header map is null");
			}
		} catch (Exception e) {
			Trace.error("Error getting Content-Type: " + e.getMessage());
			Trace.debug("Exception details: ", e);
		}
		return null;
	}
	
	/**
	 * Attempts to parse a string as JSON and return as Object (Map or List)
	 * Returns null if parsing fails or content is not valid JSON
	 */
	private Object tryParseJson(String jsonString) {
		if (jsonString == null || jsonString.trim().isEmpty()) {
			Trace.debug("JSON string is null or empty");
			return null;
		}
		
		try {
			Trace.debug("Attempting JSON parse of string (length: " + jsonString.length() + ")");
			com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
			// Try to parse as generic Object (can be Map or List)
			Object jsonObj = mapper.readValue(jsonString, Object.class);
			Trace.info("✅ Successfully parsed body as JSON object");
			Trace.debug("Parsed object type: " + jsonObj.getClass().getName());
			return jsonObj;
		} catch (com.fasterxml.jackson.core.JsonParseException e) {
			Trace.error("⚠️ JSON parse error: " + e.getMessage());
			Trace.debug("JSON parse exception at line " + e.getLocation().getLineNr() + ", column " + e.getLocation().getColumnNr());
			return null;
		} catch (com.fasterxml.jackson.databind.JsonMappingException e) {
			Trace.error("⚠️ JSON mapping error: " + e.getMessage());
			Trace.debug("JSON mapping exception details: ", e);
			return null;
		} catch (Exception e) {
			Trace.error("⚠️ Could not parse body as JSON: " + e.getMessage());
			Trace.debug("Exception type: " + e.getClass().getName());
			Trace.debug("Exception details: ", e);
			return null;
		}
	}
	
	/**
	 * Builds configurable Lambda payload based on field configuration
	 * Only includes fields that have non-empty field names configured
	 */
	private String buildConfigurablePayload(Message msg) {
		try {
			Trace.info("=== Starting buildConfigurablePayload ===");
			java.util.Map<String, Object> payload = new java.util.HashMap<>();
			
			// Check if lambda.body exists and use it as initial payload base
			Object lambdaBodyObj = msg.get("lambda.body");
			Trace.debug("lambda.body object: " + (lambdaBodyObj != null ? lambdaBodyObj.getClass().getName() : "null"));
			if (lambdaBodyObj != null && lambdaBodyObj instanceof String) {
				String lambdaBodyStr = (String) lambdaBodyObj;
				Trace.debug("lambda.body string length: " + (lambdaBodyStr != null ? lambdaBodyStr.length() : "null"));
				if (lambdaBodyStr != null && !lambdaBodyStr.trim().isEmpty()) {
					Trace.debug("lambda.body preview: " + lambdaBodyStr.substring(0, Math.min(200, lambdaBodyStr.length())));
					try {
						com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
						@SuppressWarnings("unchecked")
						java.util.Map<String, Object> lambdaBodyMap = mapper.readValue(lambdaBodyStr, java.util.Map.class);
						if (lambdaBodyMap != null && !lambdaBodyMap.isEmpty()) {
							payload.putAll(lambdaBodyMap);
							Trace.info("✅ Using lambda.body as initial payload base with " + lambdaBodyMap.size() + " entries");
							Trace.debug("lambda.body entries: " + lambdaBodyMap.keySet());
							for (java.util.Map.Entry<String, Object> entry : lambdaBodyMap.entrySet()) {
								Object value = entry.getValue();
								String valueType = value != null ? value.getClass().getName() : "null";
								if (value instanceof String) {
									String strValue = (String) value;
									Trace.debug("  - " + entry.getKey() + ": " + valueType + " (length: " + strValue.length() + ", preview: " + strValue.substring(0, Math.min(100, strValue.length())) + ")");
								} else {
									Trace.debug("  - " + entry.getKey() + ": " + valueType);
								}
							}
						}
					} catch (Exception e) {
						Trace.info("⚠️ Could not parse lambda.body as JSON: " + e.getMessage());
						Trace.debug("Exception details: ", e);
					}
				} else {
					Trace.debug("lambda.body is null or empty");
				}
			} else {
				Trace.debug("lambda.body is null or not a String");
			}
			
			// Get field names from configuration
			String methodFieldName = payloadMethodField != null ? payloadMethodField.substitute(msg) : null;
			String headersFieldName = payloadHeadersField != null ? payloadHeadersField.substitute(msg) : null;
			String bodyFieldName = payloadBodyField != null ? payloadBodyField.substitute(msg) : null;
			String uriFieldName = payloadUriField != null ? payloadUriField.substitute(msg) : null;
			String queryStringFieldName = payloadQueryStringField != null ? payloadQueryStringField.substitute(msg) : null;
			String paramsPathFieldName = payloadParamsPathField != null ? payloadParamsPathField.substitute(msg) : null;
			
			Trace.info("=== Field Configuration ===");
			Trace.info("payloadMethodField: " + methodFieldName);
			Trace.info("payloadHeadersField: " + headersFieldName);
			Trace.info("payloadBodyField: " + bodyFieldName);
			Trace.info("payloadUriField: " + uriFieldName);
			Trace.info("payloadQueryStringField: " + queryStringFieldName);
			Trace.info("payloadParamsPathField: " + paramsPathFieldName);
			
			// Add request_method if configured and not empty
			if (methodFieldName != null && !methodFieldName.trim().isEmpty()) {
				String method = msg.get("http.request.verb") != null ? msg.get("http.request.verb").toString() : null;
				if (method != null && !method.trim().isEmpty()) {
					setNestedValue(payload, methodFieldName.trim(), method);
				}
			}
			
			// Add request_headers if configured and not empty
			if (headersFieldName != null && !headersFieldName.trim().isEmpty()) {
				java.util.Map<String, String> headerMap = extractHeaders(msg);
				if (headerMap != null && !headerMap.isEmpty()) {
					setNestedValue(payload, headersFieldName.trim(), headerMap);
				}
			}
			
			// Add request_body if configured and not empty
			// Intelligently converts JSON to Map/List if Content-Type is application/json or body looks like JSON
			// IMPORTANT: This will ALWAYS overwrite any value from lambda.body to ensure body is sent as object, not string
			if (bodyFieldName != null && !bodyFieldName.trim().isEmpty()) {
				Trace.info("=== Processing request_body ===");
				Trace.info("Body field name: " + bodyFieldName.trim());
				
				// Check if this field already exists in payload (from lambda.body)
				Object existingValue = getNestedValue(payload, bodyFieldName.trim());
				if (existingValue != null) {
					Trace.info("⚠️ Field '" + bodyFieldName.trim() + "' already exists in payload from lambda.body");
					Trace.info("Existing value type: " + existingValue.getClass().getName());
					if (existingValue instanceof String) {
						String strValue = (String) existingValue;
						Trace.info("⚠️ Existing value is STRING - will be OVERWRITTEN with parsed object");
						Trace.info("Existing value preview: " + strValue.substring(0, Math.min(200, strValue.length())));
					} else {
						Trace.info("Existing value is " + existingValue.getClass().getName() + " - will be OVERWRITTEN");
					}
				}
				
				String body = extractOriginalBody(msg);
				if (body != null && !body.trim().isEmpty()) {
					Trace.info("Body extracted successfully");
					Trace.info("Body length: " + body.length());
					Trace.info("Body preview (first 500 chars): " + body.substring(0, Math.min(500, body.length())));
					
					// Check Content-Type to determine if body should be parsed as JSON
					// ONLY parse as JSON if Content-Type is application/json
					String contentType = getContentType(msg);
					boolean isJsonContentType = contentType != null && 
						contentType.toLowerCase().contains("application/json");
					
					Trace.info("Content-Type: " + (contentType != null ? contentType : "null"));
					Trace.info("Is JSON Content-Type: " + isJsonContentType);
					
					if (isJsonContentType) {
						// Content-Type is application/json - ALWAYS attempt to parse as JSON
						Trace.info("Content-Type is application/json - attempting to parse body as JSON...");
						// Try to parse as JSON object (Map or List)
						Object bodyObj = tryParseJson(body);
						if (bodyObj != null) {
							// Successfully parsed as JSON - add as object (Map/List)
							// This will ALWAYS overwrite any existing value at this path from lambda.body
							// This ensures request_body_args is sent as object, not string
							setNestedValue(payload, bodyFieldName.trim(), bodyObj);
							Trace.info("✅ Body parsed as JSON object and added to payload");
							Trace.info("✅ This OVERWRITES any existing value from lambda.body");
							Trace.info("Body object type: " + bodyObj.getClass().getName());
							if (bodyObj instanceof java.util.Map) {
								java.util.Map<?, ?> bodyMap = (java.util.Map<?, ?>) bodyObj;
								Trace.info("Body object is Map with " + bodyMap.size() + " entries");
								Trace.debug("Body Map keys: " + bodyMap.keySet());
							} else if (bodyObj instanceof java.util.List) {
								Trace.info("Body object is List with " + ((java.util.List<?>) bodyObj).size() + " items");
							}
							
							// Verify that the value was actually set correctly
							Object verifyValue = getNestedValue(payload, bodyFieldName.trim());
							if (verifyValue != null) {
								Trace.info("✅ Verification: Field '" + bodyFieldName.trim() + "' now has type: " + verifyValue.getClass().getName());
								if (verifyValue instanceof String) {
									Trace.error("❌ ERROR: Field is still STRING after setNestedValue! This should not happen!");
								} else {
									Trace.info("✅ Verification: Field is correctly set as object (not string)");
								}
							} else {
								Trace.info("⚠️ Verification: Field '" + bodyFieldName.trim() + "' is null after setNestedValue");
							}
						} else {
							// Content-Type says JSON but parse failed - this is unexpected
							Trace.error("❌ ERROR: Content-Type is application/json but JSON parse failed!");
							Trace.error("❌ This may indicate malformed JSON in the request body");
							// Still overwrite any existing value from lambda.body, but as string (fallback)
							setNestedValue(payload, bodyFieldName.trim(), body);
							Trace.info("⚠️ Falling back to string (parse failed)");
							Trace.info("⚠️ This still overwrites any existing value from lambda.body");
						}
					} else {
						// Not JSON Content-Type - add as string (maintains backward compatibility)
						// But still overwrite any existing value from lambda.body
						setNestedValue(payload, bodyFieldName.trim(), body);
						Trace.info("Body added as string (Content-Type: " + contentType + " is not application/json)");
						Trace.info("This overwrites any existing value from lambda.body");
					}
				} else {
					Trace.info("⚠️ Body is null or empty, skipping");
					if (existingValue != null) {
						Trace.info("⚠️ Keeping existing value from lambda.body (may be string!)");
					}
				}
			} else {
				Trace.debug("Body field name not configured, skipping request_body");
			}
			
			// Add request_uri if configured and not empty
			if (uriFieldName != null && !uriFieldName.trim().isEmpty()) {
				String uri = msg.get("http.request.uri") != null ? msg.get("http.request.uri").toString() : null;
				if (uri != null && !uri.trim().isEmpty()) {
					setNestedValue(payload, uriFieldName.trim(), uri);
				}
			}
			
			// Add request_querystring if configured and not empty
			if (queryStringFieldName != null && !queryStringFieldName.trim().isEmpty()) {
				java.util.Map<String, Object> queryMap = extractQueryString(msg);
				if (queryMap != null && !queryMap.isEmpty()) {
					setNestedValue(payload, queryStringFieldName.trim(), queryMap);
				}
			}
			
			// Add path parameters if configured and not empty
			if (paramsPathFieldName != null && !paramsPathFieldName.trim().isEmpty()) {
				Object paramsPathObj = msg.get("params.path");
				if (paramsPathObj instanceof java.util.Map) {
					@SuppressWarnings("unchecked")
					java.util.Map<String, Object> paramsPathMap = (java.util.Map<String, Object>) paramsPathObj;
					if (paramsPathMap != null && !paramsPathMap.isEmpty()) {
						setNestedValue(payload, paramsPathFieldName.trim(), paramsPathMap);
						Trace.debug("✅ Path parameters extracted: " + paramsPathMap);
					}
				} else if (paramsPathObj != null) {
					Trace.debug("⚠️ params.path is not a Map: " + paramsPathObj.getClass().getName());
				}
			}
			
			// Convert to JSON
			Trace.info("=== Final Payload Summary (Before Serialization) ===");
			Trace.info("Total payload fields: " + payload.size());
			Trace.info("Payload keys: " + payload.keySet());
			
			// Get body field name for special checking
			String bodyFieldNameForCheck = payloadBodyField != null ? payloadBodyField.substitute(msg) : null;
			
			Trace.info("=== Payload Field Details ===");
			// Log each field and its type with detailed information
			for (java.util.Map.Entry<String, Object> entry : payload.entrySet()) {
				Object value = entry.getValue();
				String valueType = value != null ? value.getClass().getName() : "null";
				boolean isBodyField = bodyFieldNameForCheck != null && entry.getKey().equals(bodyFieldNameForCheck);
				
				if (value instanceof String) {
					String strValue = (String) value;
					if (isBodyField) {
						Trace.error("  ❌ " + entry.getKey() + ": STRING (length: " + strValue.length() + ") - THIS IS WRONG! Should be object, not string!");
						if (strValue.length() > 0 && (strValue.startsWith("{") || strValue.startsWith("["))) {
							Trace.error("    ❌ Value looks like JSON string but should be parsed as object!");
							Trace.error("    ❌ Content preview: " + strValue.substring(0, Math.min(300, strValue.length())));
						} else {
							Trace.error("    ❌ Content preview: " + strValue.substring(0, Math.min(300, strValue.length())));
						}
					} else {
						Trace.info("  - " + entry.getKey() + ": STRING (length: " + strValue.length() + ")");
						if (strValue.length() > 0) {
							Trace.info("    Content preview: " + strValue.substring(0, Math.min(200, strValue.length())));
						}
					}
				} else if (value instanceof java.util.Map) {
					@SuppressWarnings("unchecked")
					java.util.Map<String, Object> mapValue = (java.util.Map<String, Object>) value;
					if (isBodyField) {
						Trace.info("  ✅ " + entry.getKey() + ": OBJECT (Map with " + mapValue.size() + " entries) - CORRECT! This is an object");
						Trace.info("    Map keys: " + mapValue.keySet());
						// Show first few entries for body field
						int count = 0;
						for (java.util.Map.Entry<String, Object> mapEntry : mapValue.entrySet()) {
							if (count++ < 5) {
								Object mapEntryValue = mapEntry.getValue();
								String mapEntryType = mapEntryValue != null ? mapEntryValue.getClass().getSimpleName() : "null";
								if (mapEntryValue instanceof String) {
									String str = (String) mapEntryValue;
									Trace.info("      - " + mapEntry.getKey() + ": " + mapEntryType + " = " + str.substring(0, Math.min(100, str.length())));
								} else {
									Trace.info("      - " + mapEntry.getKey() + ": " + mapEntryType);
								}
							}
						}
						if (mapValue.size() > 5) {
							Trace.info("      ... and " + (mapValue.size() - 5) + " more entries");
						}
					} else {
						Trace.info("  - " + entry.getKey() + ": OBJECT (Map with " + mapValue.size() + " entries)");
						Trace.info("    Map keys: " + mapValue.keySet());
					}
				} else if (value instanceof java.util.List) {
					java.util.List<?> listValue = (java.util.List<?>) value;
					if (isBodyField) {
						Trace.info("  ✅ " + entry.getKey() + ": OBJECT (List with " + listValue.size() + " items) - CORRECT! This is an object");
					} else {
						Trace.info("  - " + entry.getKey() + ": OBJECT (List with " + listValue.size() + " items)");
					}
				} else {
					Trace.info("  - " + entry.getKey() + ": " + valueType + " = " + (value != null ? value.toString() : "null"));
				}
			}
			
			// Serialize to JSON
			com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
			String payloadJson = mapper.writeValueAsString(payload);
			
			Trace.info("=== Payload Serialization ===");
			Trace.info("✅ Configurable payload built successfully");
			Trace.info("Payload JSON length: " + payloadJson.length() + " characters");
			
			// Show full JSON payload (important for debugging)
			Trace.info("=== Complete Payload JSON (What will be sent to Lambda) ===");
			Trace.info(payloadJson);
			Trace.info("=== End of Payload JSON ===");
			
			// Also show formatted version if not too large
			if (payloadJson.length() < 5000) {
				try {
					com.fasterxml.jackson.databind.ObjectMapper prettyMapper = new com.fasterxml.jackson.databind.ObjectMapper();
					com.fasterxml.jackson.databind.JsonNode jsonNode = prettyMapper.readTree(payloadJson);
					String prettyJson = prettyMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
					Trace.info("=== Formatted Payload JSON (Pretty Print) ===");
					Trace.info(prettyJson);
					Trace.info("=== End of Formatted Payload JSON ===");
				} catch (Exception e) {
					Trace.debug("Could not format JSON for pretty print: " + e.getMessage());
				}
			} else {
				Trace.info("Payload too large for pretty print, showing first 2000 characters:");
				Trace.info(payloadJson.substring(0, Math.min(2000, payloadJson.length())));
			}
			
			return payloadJson;
			
		} catch (Exception e) {
			Trace.error("Error building configurable payload: " + e.getMessage(), e);
			return "{}";
		}
	}
	
	/**
	 * Extracts headers from message and converts to Map
	 */
	private java.util.Map<String, String> extractHeaders(Message msg) {
		java.util.Map<String, String> headerMap = new java.util.HashMap<>();
		try {
			Object headersObj = msg.get("http.headers");
			if (headersObj instanceof com.vordel.mime.HeaderSet) {
				com.vordel.mime.HeaderSet headers = (com.vordel.mime.HeaderSet) headersObj;
				java.util.Iterator<String> nameIterator = headers.getHeaderNames();
				while (nameIterator.hasNext()) {
					String headerName = nameIterator.next();
					String headerValue = headers.getHeader(headerName);
					if (headerValue != null) {
						headerMap.put(headerName, headerValue);
					}
				}
			}
		} catch (Exception e) {
			Trace.error("Error extracting headers: " + e.getMessage(), e);
		}
		return headerMap;
	}
	
	/**
	 * Extracts query string parameters from message and converts to Map
	 */
	private java.util.Map<String, Object> extractQueryString(Message msg) {
		java.util.Map<String, Object> queryMap = new java.util.HashMap<>();
		try {
			Object queryObj = msg.get("http.querystring");
			if (queryObj instanceof com.vordel.mime.QueryStringHeaderSet) {
				com.vordel.mime.QueryStringHeaderSet queryParams = (com.vordel.mime.QueryStringHeaderSet) queryObj;
				java.util.Iterator<String> nameIterator = queryParams.getHeaderNames();
				while (nameIterator.hasNext()) {
					String paramName = nameIterator.next();
					Object paramValue = queryParams.get(paramName);
					if (paramValue != null) {
						// QueryStringHeaderSet.get() returns single value or ArrayList for multiple values
						if (paramValue instanceof java.util.ArrayList) {
							// Multiple values for the same parameter
							queryMap.put(paramName, paramValue);
						} else {
							// Single value
							queryMap.put(paramName, paramValue.toString());
						}
					}
				}
				Trace.debug("✅ Query string parameters extracted: " + queryMap.size() + " parameters");
			} else {
				Trace.debug("⚠️ http.querystring is not a QueryStringHeaderSet: " + 
					(queryObj != null ? queryObj.getClass().getName() : "null"));
			}
		} catch (Exception e) {
			Trace.error("Error extracting query string: " + e.getMessage(), e);
		}
		return queryMap;
	}
	
	/**
	 * Extracts original request body from message as string (following TraceProcessor pattern)
	 */
	private String extractOriginalBody(Message msg) {
		try {
			Trace.debug("=== Starting ORIGINAL body extraction (TraceProcessor pattern) ===");
			
			// Follow the exact same pattern as TraceProcessor
			Object bodyObj = msg.get("content.body");
			Trace.debug("Body object: " + (bodyObj != null ? bodyObj.getClass().getName() : "null"));
			Trace.debug("Body object toString: " + (bodyObj != null ? bodyObj.toString() : "null"));
			
			if (bodyObj != null && bodyObj.getClass().getName().contains("vordel.mime.Body")) {
				Trace.debug("Found content.body: " + bodyObj.getClass().getName());
				
				java.io.ByteArrayOutputStream os = new java.io.ByteArrayOutputStream();
				try {
					// Use reflection to call write method (like TraceProcessor does)
					java.lang.reflect.Method writeMethod = bodyObj.getClass().getMethod("write", 
						java.io.OutputStream.class, int.class);
					writeMethod.invoke(bodyObj, os, 0);
					
					String content = new String(os.toByteArray(), "UTF-8");
					Trace.debug("✅ Body extracted (TraceProcessor pattern): " + content.substring(0, Math.min(100, content.length())));
					
					// Check if this looks like our own payload (recursive)
					if (content != null && content.contains("request_body") && content.contains("request_headers")) {
						Trace.debug("❌ Content contains recursive payload, trying alternative...");
						
						// Try to get the original request body before processing
						Object originalBodyObj = msg.get("http.request.body");
						if (originalBodyObj != null && originalBodyObj.getClass().getName().contains("vordel.mime.Body")) {
							Trace.debug("Found http.request.body: " + originalBodyObj.getClass().getName());
							
							java.io.ByteArrayOutputStream originalOs = new java.io.ByteArrayOutputStream();
							try {
								java.lang.reflect.Method originalWriteMethod = originalBodyObj.getClass().getMethod("write", 
									java.io.OutputStream.class, int.class);
								originalWriteMethod.invoke(originalBodyObj, originalOs, 0);
								
								String originalContent = new String(originalOs.toByteArray(), "UTF-8");
								Trace.debug("✅ Original body from http.request.body: " + originalContent.substring(0, Math.min(100, originalContent.length())));
								return originalContent;
							} catch (Exception e) {
								Trace.debug("❌ Could not write original body: " + e.getMessage());
							} finally {
								originalOs.close();
							}
						}
						
						// If still recursive, return empty
						Trace.debug("❌ Still recursive, returning empty string");
						return "";
					}
					
					return content;
				} catch (Exception e) {
					Trace.debug("❌ Could not extract body using TraceProcessor pattern: " + e.getMessage());
				} finally {
					os.close();
				}
			} else {
				Trace.debug("❌ No content.body found or not a Body instance");
				
				// Try to find any body-related keys in the message
				Trace.debug("Searching for other body keys...");
				java.util.Set<String> keys = msg.keySet();
				for (String key : keys) {
					if (key.toLowerCase().contains("body")) {
						Object value = msg.get(key);
						Trace.debug("Found body-related key: " + key + " = " + 
							(value != null ? value.getClass().getName() : "null"));
					}
				}
				
				// Try direct cast like TraceProcessor does
				Trace.debug("Trying direct cast like TraceProcessor...");
				try {
					Object directBody = msg.get("content.body");
					if (directBody != null) {
						Trace.debug("Direct body object: " + directBody.getClass().getName());
						
						// Try to cast directly like TraceProcessor
						java.lang.reflect.Method getHeadersMethod = directBody.getClass().getMethod("getHeaders");
						Object headers = getHeadersMethod.invoke(directBody);
						Trace.debug("Headers: " + (headers != null ? headers.getClass().getName() : "null"));
						
						java.io.ByteArrayOutputStream os = new java.io.ByteArrayOutputStream();
						try {
							// Write only body (skip headers)
							java.lang.reflect.Method bodyWriteMethod = directBody.getClass().getMethod("write", 
								java.io.OutputStream.class, int.class);
							bodyWriteMethod.invoke(directBody, os, 0);
							
							String content = new String(os.toByteArray(), "UTF-8");
							Trace.debug("✅ Body extracted (headers skipped): " + content.substring(0, Math.min(100, content.length())));
							return content;
						} catch (Exception e) {
							Trace.debug("❌ Could not extract body with headers: " + e.getMessage());
						} finally {
							os.close();
						}
					}
				} catch (Exception e) {
					Trace.debug("❌ Could not try direct cast: " + e.getMessage());
				}
			}
			
			Trace.debug("❌ No original body content found, returning empty string");
			return "";
		} catch (Exception e) {
			Trace.error("❌ Error extracting original body: " + e.getMessage(), e);
			return "";
		}
	}
	
	/**
	 * Gets a nested value from a Map.
	 * This is useful for handling cases where a field name might contain dots,
	 * e.g., "request_headers.Content-Type" or "params.path.id".
	 */
	private Object getNestedValue(java.util.Map<String, Object> map, String key) {
		String[] keys = key.split("\\.");
		java.util.Map<String, Object> currentMap = map;
		
		for (int i = 0; i < keys.length - 1; i++) {
			String currentKey = keys[i];
			Object nextObj = currentMap.get(currentKey);
			if (nextObj == null || !(nextObj instanceof java.util.Map)) {
				return null;
			}
			currentMap = (java.util.Map<String, Object>) nextObj;
		}
		return currentMap.get(keys[keys.length - 1]);
	}
	
	/**
	 * Sets a nested value in a Map.
	 * This is useful for handling cases where a field name might contain dots,
	 * e.g., "request_headers.Content-Type" or "params.path.id".
	 */
	private void setNestedValue(java.util.Map<String, Object> map, String key, Object value) {
		String[] keys = key.split("\\.");
		java.util.Map<String, Object> currentMap = map;
		
		for (int i = 0; i < keys.length - 1; i++) {
			String currentKey = keys[i];
			if (!currentMap.containsKey(currentKey)) {
				currentMap.put(currentKey, new java.util.HashMap<String, Object>());
			}
			currentMap = (java.util.Map<String, Object>) currentMap.get(currentKey);
		}
		currentMap.put(keys[keys.length - 1], value);
	}

}

