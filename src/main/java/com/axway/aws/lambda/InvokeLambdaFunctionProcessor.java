package com.axway.aws.lambda;

import java.security.GeneralSecurityException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper;
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
import java.nio.charset.StandardCharsets;

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
		this.functionName = new Selector<String>(entity.getStringValue("functionName"), String.class);
		this.awsRegion = new Selector<String>(entity.getStringValue("awsRegion"), String.class);
		this.invocationType = new Selector<String>(entity.getStringValue("invocationType"), String.class);
		this.logType = new Selector<String>(entity.getStringValue("logType"), String.class);
		this.qualifier = new Selector<String>(entity.getStringValue("qualifier"), String.class);
		this.retryDelay = new Selector<Integer>(entity.getStringValue("retryDelay"), Integer.class);
		this.memorySize = new Selector<Integer>(entity.getStringValue("memorySize"), Integer.class);
		this.credentialType = new Selector<String>(entity.getStringValue("credentialType"), String.class);
		this.awsCredential = new Selector<String>(entity.getStringValue("awsCredential"), String.class);
		this.clientConfiguration = new Selector<String>(entity.getStringValue("clientConfiguration"), String.class);
		this.credentialsFilePath = new Selector<String>(entity.getStringValue("credentialsFilePath"), String.class);
		
		// Get client configuration (following S3 pattern exactly)
		Entity clientConfig = ctx.getEntity(entity.getReferenceValue("clientConfiguration"));
		
		// Configure Lambda client builder (following S3 pattern)
		this.lambdaClientBuilder = getLambdaClientBuilder(ctx, entity, clientConfig);
		
		Trace.info("=== Lambda Configuration (Following S3 Pattern) ===");
		Trace.debug("Function: " + (functionName != null ? functionName.getLiteral() : "dynamic"));
		Trace.debug("Region: " + (awsRegion != null ? awsRegion.getLiteral() : "dynamic"));
		Trace.debug("Invocation Type: " + (invocationType != null ? invocationType.getLiteral() : "dynamic"));
		Trace.debug("Log Type: " + (logType != null ? logType.getLiteral() : "dynamic"));
		Trace.debug("Qualifier: " + (qualifier != null ? qualifier.getLiteral() : "dynamic"));
		Trace.debug("Retry Delay: " + (retryDelay != null ? retryDelay.getLiteral() : "dynamic"));
		Trace.debug("Memory Size: " + (memorySize != null ? memorySize.getLiteral() : "dynamic"));
		Trace.debug("Credential Type: " + (credentialType != null ? credentialType.getLiteral() : "dynamic"));
		Trace.debug("AWS Credential: " + (awsCredential != null ? awsCredential.getLiteral() : "dynamic"));
		Trace.debug("Client Configuration: " + (clientConfiguration != null ? clientConfiguration.getLiteral() : "dynamic"));
		Trace.debug("Credentials File Path: " + (credentialsFilePath != null ? credentialsFilePath.getLiteral() : "dynamic"));
		Trace.debug("Client Config Entity: " + (clientConfig != null ? "configured" : "default"));
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
			Trace.debug("Applied custom client configuration");
		} else {
			Trace.debug("Using default client configuration");
		}
		
		return builder;
	}
	
	/**
	 * Gets the appropriate credentials provider based on configuration
	 */
	private AWSCredentialsProvider getCredentialsProvider(ConfigContext ctx, Entity entity) throws EntityStoreException {
		String credentialTypeValue = credentialType != null ? credentialType.getLiteral() : null;
		Trace.debug("=== Credentials Provider Debug ===");
		Trace.debug("Credential Type Value: " + credentialTypeValue);
		
		if ("iam".equals(credentialTypeValue)) {
			// Use IAM Role (EC2 Instance Profile or ECS Task Role)
			Trace.info("Using IAM Role credentials (Instance Profile/Task Role)");
			return new EC2ContainerCredentialsProviderWrapper();
		} else if ("file".equals(credentialTypeValue)) {
			// Use credentials file
			Trace.debug("Credentials Type is 'file', checking credentialsFilePath...");
			String filePath = credentialsFilePath != null ? credentialsFilePath.getLiteral() : null;
			Trace.debug("File Path: " + filePath);
			Trace.debug("File Path is null: " + (filePath == null));
			Trace.debug("File Path is empty: " + (filePath != null && filePath.trim().isEmpty()));
			if (filePath != null && !filePath.trim().isEmpty()) {
				try {
					Trace.info("Using AWS credentials file: " + filePath);
					// Create ProfileCredentialsProvider with file path and default profile
					return new ProfileCredentialsProvider(filePath, "default");
				} catch (Exception e) {
					Trace.error("Error loading credentials file: " + e.getMessage());
					Trace.debug("Falling back to DefaultAWSCredentialsProviderChain");
					return new DefaultAWSCredentialsProviderChain();
				}
			} else {
				Trace.debug("Credentials file path not specified, using DefaultAWSCredentialsProviderChain");
				return new DefaultAWSCredentialsProviderChain();
			}
		} else {
			// Use explicit credentials via AWSFactory (following S3 pattern)
			Trace.info("Using explicit AWS credentials via AWSFactory");
			try {
				AWSCredentials awsCredentials = AWSFactory.getCredentials(ctx, entity);
				Trace.debug("AWSFactory.getCredentials() successful");
				return getAWSCredentialsProvider(awsCredentials);
			} catch (Exception e) {
				Trace.error("Error getting explicit credentials: " + e.getMessage());
				Trace.debug("Falling back to DefaultAWSCredentialsProviderChain");
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
		
		// Apply configuration settings (following S3 pattern exactly)
		setIntegerConfig(clientConfig, entity, "connectionTimeout", (config, value) -> config.setConnectionTimeout(value));
		setIntegerConfig(clientConfig, entity, "maxConnections", (config, value) -> config.setMaxConnections(value));
		setIntegerConfig(clientConfig, entity, "maxErrorRetry", (config, value) -> config.setMaxErrorRetry(value));
		setIntegerConfig(clientConfig, entity, "socketTimeout", (config, value) -> config.setSocketTimeout(value));
		setIntegerConfig(clientConfig, entity, "proxyPort", (config, value) -> config.setProxyPort(value));
		
		setStringConfig(clientConfig, entity, "protocol", (config, value) -> config.setProtocol(Protocol.valueOf(value)));
		setStringConfig(clientConfig, entity, "userAgent", (config, value) -> config.setUserAgent(value));
		setStringConfig(clientConfig, entity, "proxyHost", (config, value) -> config.setProxyHost(value));
		setStringConfig(clientConfig, entity, "proxyUsername", (config, value) -> config.setProxyUsername(value));
		setStringConfig(clientConfig, entity, "proxyDomain", (config, value) -> config.setProxyDomain(value));
		setStringConfig(clientConfig, entity, "proxyWorkstation", (config, value) -> config.setProxyWorkstation(value));
		
		// Handle encrypted proxy password
		if (entity.containsKey("proxyPassword")) {
			try {
				byte[] proxyPasswordBytes = ctx.getCipher().decrypt(entity.getEncryptedValue("proxyPassword"));
				clientConfig.setProxyPassword(new String(proxyPasswordBytes));
			} catch (GeneralSecurityException e) {
				Trace.error("Error decrypting proxy password: " + e.getMessage());
			}
		}
		
		// Handle socket buffer size hints (both values required)
		String socketSendBufferSizeHintStr = entity.getStringValue("socketSendBufferSizeHint");
		String socketReceiveBufferSizeHintStr = entity.getStringValue("socketReceiveBufferSizeHint");
		if (socketSendBufferSizeHintStr != null && !socketSendBufferSizeHintStr.trim().isEmpty() && 
			socketReceiveBufferSizeHintStr != null && !socketReceiveBufferSizeHintStr.trim().isEmpty()) {
			try {
				Integer socketSendBufferSizeHint = Integer.valueOf(socketSendBufferSizeHintStr.trim());
				Integer socketReceiveBufferSizeHint = Integer.valueOf(socketReceiveBufferSizeHintStr.trim());
				clientConfig.setSocketBufferSizeHints(socketSendBufferSizeHint, socketReceiveBufferSizeHint);
			} catch (NumberFormatException e) {
				Trace.error("Invalid socket buffer size hint values: " + socketSendBufferSizeHintStr + ", " + socketReceiveBufferSizeHintStr);
			}
		}
		
		return clientConfig;
	}
	
	/**
	 * Helper method to set integer configuration values
	 */
	private void setIntegerConfig(ClientConfiguration config, Entity entity, String fieldName, 
			java.util.function.BiConsumer<ClientConfiguration, Integer> setter) {
		String valueStr = entity.getStringValue(fieldName);
		if (valueStr != null && !valueStr.trim().isEmpty()) {
			try {
				Integer value = Integer.valueOf(valueStr.trim());
				setter.accept(config, value);
			} catch (NumberFormatException e) {
				Trace.error("Invalid " + fieldName + " value: " + valueStr);
			}
		}
	}
	
	/**
	 * Helper method to set string configuration values
	 */
	private void setStringConfig(ClientConfiguration config, Entity entity, String fieldName, 
			java.util.function.BiConsumer<ClientConfiguration, String> setter) {
		String value = entity.getStringValue(fieldName);
		if (value != null && !value.trim().isEmpty()) {
			setter.accept(config, value);
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
		String credentialsFilePathValue = credentialsFilePath.substitute(msg);

		Trace.debug("=== Invocation Debug ===");
		Trace.debug("Function Name: " + functionNameValue);
		Trace.debug("Region: " + regionValue);
		Trace.debug("Invocation Type: " + invocationTypeValue);
		Trace.debug("Log Type: " + logTypeValue);
		Trace.debug("Qualifier: " + qualifierValue);
		Trace.debug("Retry Delay: " + retryDelayValue);
		Trace.debug("Memory Size: " + memorySizeValue);
		Trace.debug("Credential Type: " + credentialTypeValue);
		Trace.debug("Credentials File Path: " + credentialsFilePathValue);
		
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
		if (memorySizeValue == null) {
			memorySizeValue = 128; // Default 128 MB
		}
		
		String body = contentBody.substitute(msg);
		if (body == null || body.trim().isEmpty()) {
			body = "{}";
		}
		
		// Prepare payload once
		ByteBuffer payload = ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8));
		
		// Validate required fields
		if (regionValue == null || regionValue.trim().isEmpty()) {
			Trace.error("AWS Region is required but not provided");
			msg.put("aws.lambda.error", "AWS Region is required but not provided");
			return false;
		}
		
		if (functionNameValue == null || functionNameValue.trim().isEmpty()) {
			Trace.error("Function name is required but not provided");
			msg.put("aws.lambda.error", "Function name is required but not provided");
			return false;
		}
		
		Trace.debug("Invoking Lambda function with retry...");
		Trace.debug("Memory Size: " + memorySizeValue + " MB");
		
		Exception lastException = null;
		
		// Get maxRetries from clientConfiguration (default 3)
		int maxRetriesValue = 3; // Default value
		
		// Create Lambda client once with region
		AWSLambda lambdaClient = lambdaClientBuilder.withRegion(regionValue).build();
		
		for (int attempt = 1; attempt <= maxRetriesValue; attempt++) {
			try {
				Trace.debug("Attempt " + attempt + " of " + maxRetriesValue);
				
				// Create request
				InvokeRequest invokeRequest = new InvokeRequest()
					.withFunctionName(functionNameValue)
					.withPayload(payload)
					.withInvocationType(invocationTypeValue)
					.withLogType(logTypeValue);
				
				// Add qualifier if specified
				if (qualifierValue != null && !qualifierValue.trim().isEmpty()) {
					invokeRequest.setQualifier(qualifierValue);
					Trace.debug("Using qualifier: " + qualifierValue);
				}
				
				// Invoke Lambda function
				InvokeResult invokeResult = lambdaClient.invoke(invokeRequest);
				
				// Process response
				return processInvokeResult(invokeResult, msg, memorySizeValue);
				
			} catch (Exception e) {
				lastException = e;
				Trace.error("Attempt " + attempt + " failed: " + e.getMessage());
				
				// If not the last attempt, wait before retrying
				if (attempt < maxRetriesValue) {
					Trace.debug("Waiting " + retryDelayValue + "ms before next attempt...");
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
			String response = new String(invokeResult.getPayload().array(), StandardCharsets.UTF_8);
			int statusCode = invokeResult.getStatusCode();
			
			// === Lambda Response ===
			Trace.debug("=== Lambda Response ===");
			Trace.debug("Status Code: " + statusCode);
			Trace.debug("Response: " + response);
			Trace.debug("Executed Version: " + invokeResult.getExecutedVersion());
			
			if (invokeResult.getLogResult() != null) {
				Trace.debug("Log Result: " + invokeResult.getLogResult());
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
