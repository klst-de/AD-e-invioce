package com.klst.einvoice;

import org.compiere.model.MInvoice;

public interface InterfaceEinvoice {

	/**
	 * setup an appropriate transformer. aka marshaller
	 * <p>
	 * UBL uses different transformer for CreditNote and Invoice
	 * 
	 * @param isCreditNote
	 */
	void setupTransformer(boolean isCreditNote);
	
	/**
	 * create the xml e-invoice
	 * <p>
	 * in UBL it can be CreditNote or (UBL)Invoice
	 * 
	 * @param mInvoice
	 * @return the xml e-representation of the adempiere invoice
	 */
	byte[] tranformToXML(MInvoice mInvoice);
	
}
