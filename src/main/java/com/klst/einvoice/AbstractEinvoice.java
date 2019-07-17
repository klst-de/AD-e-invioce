package com.klst.einvoice;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.compiere.model.I_C_Order;
import org.compiere.model.I_C_Tax;
import org.compiere.model.MBPartner;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MInvoiceTax;
import org.compiere.model.MOrg;
import org.compiere.model.MOrgInfo;
import org.compiere.process.SvrProcess;
import org.compiere.util.Env;
import org.w3c.dom.Document;

import com.klst.einvoice.ubl.VatCategory;
import com.klst.einvoice.unece.uncefact.Amount;
import com.klst.einvoice.unece.uncefact.Quantity;
import com.klst.marshaller.AbstactTransformer;
import com.klst.untdid.codelist.TaxCategoryCode;

public abstract class AbstractEinvoice extends SvrProcess implements InterfaceEinvoice {

	private static final Logger LOG = Logger.getLogger(AbstractEinvoice.class.getName());
	
	static final String XRECHNUNG_12 = "urn:cen.eu:en16931:2017#compliant#urn:xoev-de:kosit:standard:xrechnung_1.2";
	
	protected AbstactTransformer transformer; // Singleton
	protected MInvoice mInvoice; // the source AD object
	
	/**
	 * the equivalent to adempiere invoice Document No
	 * 
	 * @return Invoice number
	 */
	abstract public String getDocumentNo();
	
	/**
	 * generic mapping adempiere invoice to UBL or CII
	 * 
	 * @param mInvoice
	 * @return object instance of (UBL)Invoice or CreditNote or CrossIndustryInvoice
	 */
	abstract Object mapToEModel(MInvoice mInvoice);
	
	abstract void setBuyerReference(String buyerReference);
	abstract void setTotals(Amount lineExtension, Amount taxExclusive, Amount taxInclusive, Amount payable, Amount taxTotal);
	abstract void mapByuer(String buyerName, int location_ID, int user_ID);
	abstract void mapSeller(String sellerName, int location_ID, int salesRep_ID, String companyID, String companyLegalForm, String taxCompanyId);
	abstract void setVATBreakDown(Amount taxableAmount, Amount tax, VatCategory vatCategory);
	abstract void mapLine(MInvoiceLine line);

	protected Quantity mapToQuantity(String unitCode, BigDecimal quantity) {
		if("PCE".equals(unitCode)) return new Quantity("EA", quantity);
		if("Stk.".equals(unitCode)) return new Quantity("EA", quantity);
		if("HR".equals(unitCode)) return new Quantity("HUR", quantity); // @see https://github.com/klst-de/e-invoice/issues/4
		if("DA".equals(unitCode)) return new Quantity("DAY", quantity);
		if("kg".equals(unitCode)) return new Quantity("KGM", quantity);
		if("m".equals(unitCode)) return new Quantity("MTR", quantity);
		if("pa".equals(unitCode)) return new Quantity("PA", quantity); // weder PA noch PK sind valid?
		if("p100".equals(unitCode)) return new Quantity("CEN", quantity);
		return new Quantity(unitCode, quantity);
	}
	
	// mapping POReference -> BuyerReference 
	void mapBuyerReference() {
		String mPOReference = mInvoice.getPOReference(); // kann null sein
		if(mPOReference==null) {
			I_C_Order order = mInvoice.getC_Order();
			if(order.getPOReference()==null) { // auch das kann null sein, dann 
				mPOReference = "Order " +order.getDocumentNo() + ", DateOrdered " +order.getDateOrdered().toString().substring(0, 10);
			} else {
				mPOReference = order.getPOReference();
			}
		}
		setBuyerReference(mPOReference);
	}
	
	// mapping SellerGroup
	//         MOrg.name      -> Seller.name     
	//         Location       ->       Address
	//         Contact        ->       Contact
	void mapSellerGroup() {
		int mAD_Org_ID = mInvoice.getAD_Org_ID();
		int salesRep_ID = mInvoice.getSalesRep_ID();
		
		String sellerName = null;
		String companyID = null;
		String companyLegalForm = null;
		
		MOrg mOrg = new MOrg(Env.getCtx(), mAD_Org_ID, get_TrxName());
		sellerName = mOrg.getName();
		MOrgInfo mOrgInfo = MOrgInfo.get(Env.getCtx(), mAD_Org_ID, get_TrxName());	
		
		LOG.info("sellerName/MOrg.name:"+sellerName +
				" companyID:"+companyID +
				" companyLegalForm:"+companyLegalForm
				);

		// optional:
		String taxCompanyId = mOrgInfo.getTaxID(); // UStNr DE....
		
		mapSeller(sellerName, mOrgInfo.getC_Location_ID(), salesRep_ID, companyID, companyLegalForm, taxCompanyId);
	}

	// mapping BuyerGroup
	//         MBPartner.name -> Buyer.name     
	//         Location       ->       Address
	//         Contact        ->       Contact
	void mapBuyerGroup() { // void makeBuyerGroup() {
		int mBP_ID = mInvoice.getC_BPartner_ID();
		int location_ID = mInvoice.getC_BPartner_Location().getC_Location_ID();
		int user_ID = mInvoice.getAD_User_ID();
		
		MBPartner mBPartner = new MBPartner(Env.getCtx(), mBP_ID, get_TrxName());
		String buyerName = mBPartner.getName();
		
		mapByuer(buyerName, location_ID, user_ID);
	}
	
	void mapDocumentTotals() {
		BigDecimal taxBaseAmt = BigDecimal.ZERO;
		BigDecimal taxAmt = BigDecimal.ZERO;
		MInvoiceTax[] mInvoiceTaxes = mInvoice.getTaxes(true); // MInvoiceTax[] getTaxes (boolean requery)
		for(int i=0; i<mInvoiceTaxes.length; i++) {
			MInvoiceTax mInvoiceTax = mInvoiceTaxes[i];
			LOG.info(mInvoiceTax.toString());
			taxBaseAmt = taxBaseAmt.add(mInvoiceTax.getTaxBaseAmt());
			taxAmt = taxAmt.add(mInvoiceTax.getTaxAmt());
		}
		setTotals(new Amount(mInvoice.getCurrencyISO(), mInvoice.getTotalLines()) // lineExtension
				, new Amount(mInvoice.getCurrencyISO(), taxBaseAmt) // taxExclusive
				, new Amount(mInvoice.getCurrencyISO(), taxBaseAmt.add(taxAmt)) // taxInclusive
				, new Amount(mInvoice.getCurrencyISO(), mInvoice.getGrandTotal()) // payable
				, new Amount(mInvoice.getCurrencyISO(), taxAmt) // taxTotal
				);
	}

	static final int SCALE = 2;
	void mapVatBreakDownGroup() {
		List<MInvoiceTax> taxes = Arrays.asList(mInvoice.getTaxes(true));
		taxes.forEach(mInvoiceTax -> {
			I_C_Tax tax = mInvoiceTax.getC_Tax(); // mapping
			LOG.info(mInvoiceTax.toString() + " - " + tax);
			BigDecimal taxRate = tax.getRate().setScale(SCALE, RoundingMode.HALF_UP);
			VatCategory vatCategory = new VatCategory(TaxCategoryCode.StandardRate, new com.klst.einvoice.ubl.Percent(taxRate));
			// die optionalen "VAT exemption reason text" und "VAT exemption reason code" TODO
			LOG.info("vatCategory:" +vatCategory);
			setVATBreakDown(new Amount(mInvoice.getCurrencyISO(), mInvoiceTax.getTaxBaseAmt()) // taxableAmount
						, new Amount(mInvoice.getCurrencyISO(), mInvoiceTax.getTaxAmt()) // tax
						, vatCategory   // TODO mehr als eine mappen
						);
		});
		LOG.info("finished. "+taxes.size() + " vatBreakDowns.");
	}

	void mapLineGroup() {
		// 
		List<MInvoiceLine> invoiceLines = Arrays.asList(mInvoice.getLines());
		invoiceLines.forEach(invoiceLine -> {
			LOG.info(invoiceLine.toString());
			if(BigDecimal.ZERO.compareTo(invoiceLine.getQtyInvoiced())==0) { 
				// empty Line
			} else {
				mapLine(invoiceLine);
			}
		});
		LOG.info("finished. "+invoiceLines.size() + " lines.");
	}


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
