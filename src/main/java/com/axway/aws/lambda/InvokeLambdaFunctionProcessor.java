package com.axway.aws.lambda;

import java.security.GeneralSecurityException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
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
import com.vordel.mime.Body;

public class InvokeLambdaFunctionProcessor extends MessageProcessor {
	
	// Client builder (following S3 pattern exactly)
	protected AWSLambdaClientBuilder lambdaClientBuilder;
	
	// Selectors (following S3 pattern exactly)
	protected Selector<String> awsRegion;
	protected Selector<String> functionName;
	protected Selector<String> invocationType;
	protected Selector<String> logType;
	protected Selector<String> qualifier;
	protected Selector<Integer> retryDelay;
	protected Selector<Integer> memorySize;

	// Content body selector (following SQS pattern exactly)
	private Selector<String> bodyToString = new Selector<>("${content.body}", String.class);

	@Override
	public void filterAttached(ConfigContext ctx, Entity entity) throws EntityStoreException {
		super.filterAttached(ctx, entity);
		
		// Initialize selectors (following S3 pattern exactly)
		this.awsRegion = new Selector<String>(entity.getStringValue("awsRegion"), String.class);
		this.functionName = new Selector<String>(entity.getStringValue("functionName"), String.class);
		this.invocationType = new Selector<String>(entity.getStringValue("invocationType"), String.class);
		this.logType = new Selector<String>(entity.getStringValue("logType"), String.class);
		this.qualifier = new Selector<String>(entity.getStringValue("qualifier"), String.class);
		this.retryDelay = new Selector<Integer>(entity.getStringValue("retryDelay"), Integer.class);
		this.memorySize = new Selector<Integer>(entity.getStringValue("memorySize"), Integer.class);
		
		// Get client builder (following S3 pattern exactly)
		this.lambdaClientBuilder = getLambdaClientBuilder(ctx, entity);
	}

	/**
	 * Creates Lambda client builder following S3 pattern exactly
	 */
	private AWSLambdaClientBuilder getLambdaClientBuilder(ConfigContext ctx, Entity entity) throws EntityStoreException {
		Entity clientConfig = ctx.getEntity(entity.getReferenceValue("clientConfiguration"));
		
		// Use AWSFactory like S3 pattern (following S3 pattern exactly)
		AWSCredentials awsCredentials = AWSFactory.getCredentials(ctx, entity);
		AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(awsCredentials);
		
		// Create client builder with credentials (following S3 pattern exactly)
		AWSLambdaClientBuilder builder = AWSLambdaClientBuilder.standard()
			.withCredentials(credentialsProvider);
		
		// Apply client configuration if available (following S3 pattern exactly)
		if (clientConfig != null) {
			ClientConfiguration clientConfiguration = createClientConfiguration(clientConfig);
			builder.withClientConfiguration(clientConfiguration);
		}
		
		return builder;
	}
	
	/**
	 * Creates ClientConfiguration from entity (following S3 pattern exactly)
	 */
	private ClientConfiguration createClientConfiguration(Entity entity) {
		ClientConfiguration clientConfig = new ClientConfiguration();
		
		if (entity == null) {
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
				byte[] proxyPasswordBytes = entity.getEncryptedValue("proxyPassword");
				clientConfig.setProxyPassword(new String(proxyPasswordBytes));
			} catch (Exception e) {
				Trace.error("Error decrypting proxy password: " + e.getMessage());
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
	 * Gets the payload following SQS pattern exactly
	 */
	private String getPayload(Message m) {
		Body b = (Body)m.get("content.body");
		String payload = this.bodyToString.substitute(m);
		
		if (payload == null || payload.trim().isEmpty()) {
			payload = "{}";
		}
		
		return payload;
	}

	@Override
	public boolean invoke(Circuit c, Message m) throws CircuitAbortException {
		try {
			// Get dynamic values using selectors (following S3 pattern)
			String awsRegionValue = awsRegion.substitute(m);
			String functionNameValue = functionName.substitute(m);
			String invocationTypeValue = invocationType.substitute(m);
			String logTypeValue = logType.substitute(m);
			String qualifierValue = qualifier.substitute(m);
			Integer retryDelayValue = retryDelay.substitute(m);
			Integer memorySizeValue = memorySize.substitute(m);
			
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
			if (memorySizeValue == null) {
				memorySizeValue = 128; // Default 128 MB
			}
			
			// Get body content using getPayload method (following SQS pattern exactly)
			String body = getPayload(m);
			
			if (body == null || body.trim().isEmpty()) {
				body = "{}";
			}
			
			// Validate JSON format
			if (!body.trim().startsWith("{") && !body.trim().startsWith("[")) {
				Trace.debug("Body is not valid JSON, using empty JSON: " + body);
				body = "{}";
			}
			
			// Prepare payload
			ByteBuffer payload = ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8));
			
			// Validate required fields
			if (awsRegionValue == null || awsRegionValue.trim().isEmpty()) {
				Trace.error("AWS Region is required but not provided");
				m.put("aws.lambda.error", "AWS Region is required but not provided");
				return false;
			}
			
			if (functionNameValue == null || functionNameValue.trim().isEmpty()) {
				Trace.error("Function name is required but not provided");
				m.put("aws.lambda.error", "Function name is required but not provided");
				return false;
			}
			
			Trace.debug("Invoking Lambda function...");
			Trace.debug("Memory Size: " + memorySizeValue + " MB");
			
			// Create Lambda client with region (following S3 pattern)
			lambdaClientBuilder.withRegion(awsRegionValue);
			AWSLambda lambdaClient = lambdaClientBuilder.build();
			
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
			return processInvokeResult(invokeResult, m, memorySizeValue);
			
		} catch (Exception e) {
			Trace.error("Error in Lambda invocation: " + e.getMessage(), e);
			m.put("aws.lambda.error", "Error in Lambda invocation: " + e.getMessage());
			return false;
		}
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
			
			// Store results (following S3 pattern)
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
