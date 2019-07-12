package com.klst.einvoice;

import org.compiere.model.MInvoice;
import org.compiere.process.SvrProcess;

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
