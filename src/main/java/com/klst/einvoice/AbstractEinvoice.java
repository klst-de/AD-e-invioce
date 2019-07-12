package com.klst.einvoice;

import java.io.ByteArrayInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.compiere.model.MInvoice;
import org.compiere.process.SvrProcess;
import org.w3c.dom.Document;

import com.klst.marshaller.AbstactTransformer;

public abstract class Einvoice extends SvrProcess implements InterfaceEinvoice {

	protected AbstactTransformer transformer; // Singleton
	protected MInvoice mInvoice; // the source AD object
	
	/*
	 * (non-Javadoc)
	 * @see com.klst.einvoice.InterfaceEinvoice#setupTransformer(boolean)
	 */
	@Override
	abstract public void setupTransformer(boolean isCreditNote);

	/*
	 * (non-Javadoc)
	 * @see com.klst.einvoice.InterfaceEinvoice#tranformToXML(org.compiere.model.MInvoice)
	 */
	@Override
	abstract public byte[] tranformToXML(MInvoice mInvoice);

	/*
	 * (non-Javadoc)
	 * @see com.klst.einvoice.InterfaceEinvoice#tranformToDomDocument(byte[])
	 */
	@Override
	// tks to https://stackoverflow.com/questions/21165871/how-to-convert-array-byte-to-org-w3c-dom-document
	public Document tranformToDomDocument(byte[] xmlData) throws Exception {
	    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	    factory.setNamespaceAware(true);
	    DocumentBuilder builder = factory.newDocumentBuilder();
	    return builder.parse(new ByteArrayInputStream(xmlData));
	}

	@Override
	protected void prepare() {
		// TODO Auto-generated method stub		
	}

	@Override
	protected String doIt() throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

}
