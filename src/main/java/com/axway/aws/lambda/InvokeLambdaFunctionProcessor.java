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
	
	// Client builder (following S3 pattern exactly)
	protected AWSLambdaClientBuilder lambdaClientBuilder;
	
	// Selectors (following S3 pattern exactly)
	protected Selector<String> region;
	protected Selector<String> functionName;
	protected Selector<String> invocationType;
	protected Selector<String> logType;
	protected Selector<String> qualifier;
	protected Selector<Integer> retryDelay;
	protected Selector<Integer> memorySize;
	protected Selector<String> storageClass;

	@Override
	public void filterAttached(ConfigContext ctx, Entity entity) throws EntityStoreException {
		super.filterAttached(ctx, entity);
		
		// Initialize selectors (following S3 pattern exactly)
		this.region = new Selector<String>(entity.getStringValue("region"), String.class);
		this.functionName = new Selector<String>(entity.getStringValue("functionName"), String.class);
		this.invocationType = new Selector<String>(entity.getStringValue("invocationType"), String.class);
		this.logType = new Selector<String>(entity.getStringValue("logType"), String.class);
		this.qualifier = new Selector<String>(entity.getStringValue("qualifier"), String.class);
		this.retryDelay = new Selector<Integer>(entity.getStringValue("retryDelay"), Integer.class);
		this.memorySize = new Selector<Integer>(entity.getStringValue("memorySize"), Integer.class);
		this.storageClass = new Selector<String>(entity.getStringValue("storageClass"), String.class);
		
		// Get client builder (following S3 pattern exactly)
		this.lambdaClientBuilder = getLambdaClientBuilder(ctx, entity);
	}

	/**
	 * Creates Lambda client builder following S3 pattern exactly
	 */
	private AWSLambdaClientBuilder getLambdaClientBuilder(ConfigContext ctx, Entity entity) throws EntityStoreException {
		Entity clientConfig = ctx.getEntity(entity.getReferenceValue("clientConfiguration"));
		
		// Use AWSFactory like S3 pattern (following S3 pattern exactly)
		return (AWSLambdaClientBuilder) AWSFactory.createLambdaClientBuilder(ctx, AWSFactory.getCredentials(ctx, entity), clientConfig);
	}

	@Override
	public boolean invoke(Circuit c, Message m) throws CircuitAbortException {
		try {
			// Get dynamic values using selectors (following S3 pattern)
			String regionValue = region.substitute(m);
			String functionNameValue = functionName.substitute(m);
			String invocationTypeValue = invocationType.substitute(m);
			String logTypeValue = logType.substitute(m);
			String qualifierValue = qualifier.substitute(m);
			Integer retryDelayValue = retryDelay.substitute(m);
			Integer memorySizeValue = memorySize.substitute(m);
			String storageClassValue = storageClass.substitute(m);

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
			
			// Get body content (following S3 pattern)
			String body = m.get("content.body") != null ? m.get("content.body").toString() : "{}";
			if (body == null || body.trim().isEmpty()) {
				body = "{}";
			}
			
			// Prepare payload
			ByteBuffer payload = ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8));
			
			// Validate required fields
			if (regionValue == null || regionValue.trim().isEmpty()) {
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
			lambdaClientBuilder.withRegion(regionValue);
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
