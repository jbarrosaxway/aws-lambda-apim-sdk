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
 * - "iam" credential type: Uses WebIdentityTokenCredentialsProvider for IRSA
 *   - Detects IRSA (IAM Roles for Service Accounts) via environment variables
 *   - Falls back to DefaultAWSCredentialsProviderChain (EC2 Instance Profile) if IRSA not available
 *   - Priority: IRSA ServiceAccount > EC2 Instance Profile
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
			// Use IAM Role (IRSA - ServiceAccount or EC2 Instance Profile)
			Trace.info("Using IAM Role credentials (IRSA ServiceAccount or Instance Profile)");
			
			// Debug IRSA configuration
			Trace.info("=== IRSA Debug ===");
			Trace.info("AWS_WEB_IDENTITY_TOKEN_FILE: " + System.getenv("AWS_WEB_IDENTITY_TOKEN_FILE"));
			Trace.info("AWS_ROLE_ARN: " + System.getenv("AWS_ROLE_ARN"));
			Trace.info("AWS_REGION: " + System.getenv("AWS_REGION"));
			
			// Check if IRSA is available
			String webIdentityTokenFile = System.getenv("AWS_WEB_IDENTITY_TOKEN_FILE");
			String roleArn = System.getenv("AWS_ROLE_ARN");
			
			if (webIdentityTokenFile != null && roleArn != null) {
				// IRSA is available - use WebIdentityTokenCredentialsProvider
				Trace.info("✅ IRSA detected - using WebIdentityTokenCredentialsProvider");
				try {
					AWSCredentials credentials = new WebIdentityTokenCredentialsProvider().getCredentials();
					Trace.info("Access Key: " + credentials.getAWSAccessKeyId());
					Trace.info("Secret Key: " + (credentials.getAWSSecretKey() != null ? "***" : "null"));
					return new WebIdentityTokenCredentialsProvider();
				} catch (Exception e) {
					Trace.error("Error getting IRSA credentials: " + e.getMessage());
					Trace.info("Falling back to DefaultAWSCredentialsProviderChain");
					return new DefaultAWSCredentialsProviderChain();
				}
			} else {
				// IRSA not available - fallback to EC2 Instance Profile
				Trace.info("⚠️ IRSA not available - using DefaultAWSCredentialsProviderChain (EC2 Instance Profile)");
				try {
					AWSCredentials credentials = new DefaultAWSCredentialsProviderChain().getCredentials();
					Trace.info("Access Key: " + credentials.getAWSAccessKeyId());
					Trace.info("Secret Key: " + (credentials.getAWSSecretKey() != null ? "***" : "null"));
				} catch (Exception e) {
					Trace.error("Error getting credentials: " + e.getMessage());
				}
				return new DefaultAWSCredentialsProviderChain();
			}
		} else if ("file".equals(credentialTypeValue)) {
			// Use credentials file
			Trace.info("Credentials Type is 'file', checking credentialsFilePath...");
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
		
		String body = contentBody.substitute(msg);
		if (body == null || body.trim().isEmpty()) {
			body = "{}";
		}
		
		Trace.info("Invoking Lambda function with retry...");
		Trace.info("Using IAM Role: " + useIAMRoleValue);
		Trace.info("Memory Size: " + memorySizeValue + " MB");
		
		// Debug IRSA during actual invocation
		Trace.info("=== IRSA Debug During Invoke ===");
		Trace.info("AWS_WEB_IDENTITY_TOKEN_FILE: " + System.getenv("AWS_WEB_IDENTITY_TOKEN_FILE"));
		Trace.info("AWS_ROLE_ARN: " + System.getenv("AWS_ROLE_ARN"));
		Trace.info("AWS_REGION: " + System.getenv("AWS_REGION"));
		
		// Test current credentials
		try {
			AWSCredentials currentCredentials = lambdaClientBuilder.build().getCredentials();
			Trace.info("Current Access Key: " + currentCredentials.getAWSAccessKeyId());
			Trace.info("Current Secret Key: " + (currentCredentials.getAWSSecretKey() != null ? "***" : "null"));
		} catch (Exception e) {
			Trace.error("Error getting current credentials: " + e.getMessage());
		}
		
		Exception lastException = null;
		
		// Get maxRetries from clientConfiguration (default 3)
		int maxRetriesValue = 3; // Default value
		
		for (int attempt = 1; attempt <= maxRetriesValue; attempt++) {
			try {
				Trace.info("Attempt " + attempt + " of " + maxRetriesValue);
				
				// Create Lambda client with region (following S3 pattern)
				Trace.info("Creating Lambda client for region: " + regionValue);
				AWSLambda lambdaClient = lambdaClientBuilder.withRegion(regionValue).build();
				
				// Debug the actual client credentials
				try {
					AWSCredentials clientCredentials = lambdaClient.getCredentials();
					Trace.info("Lambda Client Access Key: " + clientCredentials.getAWSAccessKeyId());
					Trace.info("Lambda Client Secret Key: " + (clientCredentials.getAWSSecretKey() != null ? "***" : "null"));
				} catch (Exception e) {
					Trace.error("Error getting Lambda client credentials: " + e.getMessage());
				}
				
				// Create request
				InvokeRequest invokeRequest = new InvokeRequest()
					.withFunctionName(functionNameValue)
					.withPayload(ByteBuffer.wrap(body.getBytes()))
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
}
