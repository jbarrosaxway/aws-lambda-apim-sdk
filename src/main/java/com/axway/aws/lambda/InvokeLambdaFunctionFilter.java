package com.axway.aws.lambda;

import com.vordel.circuit.DefaultFilter;
import com.vordel.common.util.PropDef;
import com.vordel.config.ConfigContext;
import com.vordel.es.EntityStoreException;
import com.vordel.mime.Body;
import com.vordel.mime.HeaderSet;

/**
 * Filter for AWS Lambda function invocation
 * Configures the necessary properties for processing
 */
public class InvokeLambdaFunctionFilter extends DefaultFilter {

	@Override
	protected final void setDefaultPropertyDefs() {
		// Input properties
		this.reqProps.add(new PropDef("content.body", Body.class));
		this.reqProps.add(new PropDef("http.headers", HeaderSet.class));
		
		// Output properties
		genProps.add(new PropDef("aws.lambda.response", String.class));
		genProps.add(new PropDef("aws.lambda.http.status.code", Integer.class));
		genProps.add(new PropDef("aws.lambda.executed.version", String.class));
		genProps.add(new PropDef("aws.lambda.log.result", String.class));
		genProps.add(new PropDef("aws.lambda.memory.size", Integer.class));
		genProps.add(new PropDef("aws.lambda.error", String.class));
		genProps.add(new PropDef("aws.lambda.function.error", String.class));
	}

	@Override
	public void configure(ConfigContext ctx, com.vordel.es.Entity entity) throws EntityStoreException {
		super.configure(ctx, entity);
	}

	@Override
	public Class<InvokeLambdaFunctionProcessor> getMessageProcessorClass() {
		return InvokeLambdaFunctionProcessor.class;
	}

	public Class getConfigPanelClass() throws ClassNotFoundException {
		// Avoid any compile or runtime dependencies on SWT and other UI
		// libraries by lazily loading the class when required.
		return Class.forName("com.axway.aws.lambda.InvokeLambdaFunctionFilterUI");
	}
}