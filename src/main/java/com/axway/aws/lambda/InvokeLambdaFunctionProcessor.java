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
		String payload = buildConfigurablePayload(msg);
		if (payload == null || payload.trim().isEmpty()) {
			// Fallback to original body if no configuration
			payload = contentBody.substitute(msg);
			if (payload == null || payload.trim().isEmpty()) {
				payload = "{}";
			}
		}
		
		Trace.info("Invoking Lambda function with retry...");
		Trace.info("Using IAM Role: " + useIAMRoleValue);
		Trace.info("Memory Size: " + memorySizeValue + " MB");
		
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
				
				// Invoke Lambda function
				InvokeResult invokeResult = lambdaClient.invoke(invokeRequest);
				
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
	 * Builds configurable Lambda payload based on field configuration
	 * Supports hierarchical field names (e.g., "options.headers") and uses lambda.body as initial payload
	 */
	private String buildConfigurablePayload(Message msg) {
		try {
			// Start with existing lambda.body if available, otherwise empty map
			java.util.Map<String, Object> payload;
			Object existingBody = msg.get("lambda.body");
			
			if (existingBody != null && existingBody instanceof String) {
				try {
					// Parse existing lambda.body as JSON
					com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
					payload = mapper.readValue((String) existingBody, java.util.Map.class);
					Trace.debug("✅ Using existing lambda.body as payload base: " + existingBody);
				} catch (Exception e) {
					Trace.debug("❌ Could not parse lambda.body as JSON, starting with empty payload: " + e.getMessage());
					payload = new java.util.HashMap<>();
				}
			} else {
				Trace.debug("No lambda.body found, starting with empty payload");
				payload = new java.util.HashMap<>();
			}
			
			// Get field names from configuration
			String methodFieldName = payloadMethodField != null ? payloadMethodField.substitute(msg) : null;
			String headersFieldName = payloadHeadersField != null ? payloadHeadersField.substitute(msg) : null;
			String bodyFieldName = payloadBodyField != null ? payloadBodyField.substitute(msg) : null;
			String uriFieldName = payloadUriField != null ? payloadUriField.substitute(msg) : null;
			String queryStringFieldName = payloadQueryStringField != null ? payloadQueryStringField.substitute(msg) : null;
			String paramsPathFieldName = payloadParamsPathField != null ? payloadParamsPathField.substitute(msg) : null;
			
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
			if (bodyFieldName != null && !bodyFieldName.trim().isEmpty()) {
				String body = extractOriginalBody(msg);
				if (body != null && !body.trim().isEmpty()) {
					setNestedValue(payload, bodyFieldName.trim(), body);
				}
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
				String queryString = msg.get("http.request.querystring") != null ? msg.get("http.request.querystring").toString() : null;
				if (queryString != null && !queryString.trim().isEmpty()) {
					setNestedValue(payload, queryStringFieldName.trim(), queryString);
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
			com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
			String payloadJson = mapper.writeValueAsString(payload);
			
			Trace.debug("Configurable payload built with hierarchical structure: " + payloadJson);
			return payloadJson;
			
		} catch (Exception e) {
			Trace.error("Error building configurable payload: " + e.getMessage(), e);
			return "{}";
		}
	}
	
	/**
	 * Sets a nested value in the payload map, creating intermediate objects as needed
	 * Supports hierarchical field names like "options.headers", "crypto.request", etc.
	 */
	private void setNestedValue(java.util.Map<String, Object> payload, String fieldPath, Object value) {
		try {
			if (fieldPath == null || fieldPath.trim().isEmpty()) {
				return;
			}
			
			String[] pathParts = fieldPath.split("\\.");
			java.util.Map<String, Object> currentLevel = payload;
			
			// Navigate through the path, creating intermediate objects as needed
			for (int i = 0; i < pathParts.length - 1; i++) {
				String part = pathParts[i].trim();
				if (part.isEmpty()) continue;
				
				// Get or create the next level
				Object nextLevel = currentLevel.get(part);
				if (nextLevel == null || !(nextLevel instanceof java.util.Map)) {
					// Create new intermediate map
					nextLevel = new java.util.HashMap<String, Object>();
					currentLevel.put(part, nextLevel);
				}
				
				// Cast to map and continue
				@SuppressWarnings("unchecked")
				java.util.Map<String, Object> nextLevelMap = (java.util.Map<String, Object>) nextLevel;
				currentLevel = nextLevelMap;
			}
			
			// Set the final value
			String finalPart = pathParts[pathParts.length - 1].trim();
			if (!finalPart.isEmpty()) {
				currentLevel.put(finalPart, value);
				Trace.debug("✅ Set nested value: " + fieldPath + " = " + 
					(value instanceof String ? ((String) value).substring(0, Math.min(50, ((String) value).length())) : value));
			}
			
		} catch (Exception e) {
			Trace.error("Error setting nested value for " + fieldPath + ": " + e.getMessage(), e);
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
						headerMap.put(headerName.toLowerCase(), headerValue);
					}
				}
			}
		} catch (Exception e) {
			Trace.error("Error extracting headers: " + e.getMessage(), e);
		}
		return headerMap;
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
	

}

