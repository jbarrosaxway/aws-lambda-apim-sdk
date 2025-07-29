package com.axway.aws.lambda;

import java.util.Vector;

import com.vordel.client.manager.filter.log.LogPage;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;

import com.vordel.client.manager.Images;
import com.vordel.client.manager.filter.DefaultGUIFilter;
import com.vordel.client.manager.wizard.VordelPage;

/**
 * Interface gráfica para o filtro de invocação de funções AWS Lambda
 * Fornece páginas de configuração e log
 */
public class InvokeLambdaFunctionFilterUI extends DefaultGUIFilter {
	
	/**
	 * Chave da imagem para o ícone do filtro
	 */
	private static final String IMAGE_KEY = "amazon";
	
	public Vector<VordelPage> getPropertyPages() {
		Vector<VordelPage> pages = new Vector<>();
		pages.add(new InvokeLambdaFunctionFilterPage());
		pages.add(createLogPage());
		return pages;
	}

	public LogPage createLogPage() {
		return new LogPage();
	}

	public String[] getCategories() {
		return new String[] { resolve("FILTER_GROUP_AWS_LAMBDA") };
	}

	public String getSmallIconId() {
		return IMAGE_KEY;
	}

	public Image getSmallImage() {
		return Images.getImageRegistry().get(getSmallIconId());
	}

	public ImageDescriptor getSmallIcon() {
		return Images.getImageDescriptor(getSmallIconId());
	}

	public String getTypeName() {
		return resolve("AWS_LAMBDA_FILTER");
	}
}