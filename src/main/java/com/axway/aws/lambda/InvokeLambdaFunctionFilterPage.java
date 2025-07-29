package com.axway.aws.lambda;

import org.eclipse.swt.widgets.Composite;

import com.vordel.client.manager.wizard.VordelPage;

/**
 * Configuration page for AWS Lambda function invocation filter
 * Provides graphical interface for parameter configuration
 */
public class InvokeLambdaFunctionFilterPage extends VordelPage {

	/**
	 * Configuration page constructor
	 */
	public InvokeLambdaFunctionFilterPage() {
		super("AWSLambdaPage");

		setTitle(resolve("AWS_LAMBDA_PAGE"));
		setDescription(resolve("AWS_LAMBDA_PAGE_DESCRIPTION"));
		setPageComplete(true);
	}

	@Override
	public String getHelpID() {
		return "com.vordel.rcp.policystudio.filter.help.send_to_s3_bucket_filter_help";
	}

	@Override
	public boolean performFinish() {
		return true;
	}

	@Override
	public void createControl(Composite parent) {
		Composite panel = render(parent, getClass().getResourceAsStream("aws_lambda.xml"));
		setControl(panel);
		setPageComplete(true);
	}
}