package com.klst.einvoice;

import org.compiere.model.MInvoice;

import com.klst.marshaller.CiiTransformer;

public class CiiImpl extends Einvoice {

	Object mapToInvoice(MInvoice adInvoice) {
		return null; // TODO
	}
	
	@Override
	public void setupTransformer(boolean isCreditNote) {
		// CII uses same transformer for CreditNote and Invoice
		transformer = CiiTransformer.getInstance();
	}

	@Override
	public byte[] tranformToXML(MInvoice mInvoice) {
		boolean isCreditNote = mInvoice.isCreditMemo();
		setupTransformer(isCreditNote);
		return transformer.fromModel(mapToInvoice(mInvoice));			
	}

}
