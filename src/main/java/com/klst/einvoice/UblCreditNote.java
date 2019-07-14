package com.klst.einvoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;

import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;

import com.klst.ubl.Address;
import com.klst.ubl.Contact;
import com.klst.ubl.CreditNote;
import com.klst.ubl.CreditNoteLine;
import com.klst.ubl.VatCategory;
import com.klst.un.unece.uncefact.Amount;
import com.klst.un.unece.uncefact.IBANId;
import com.klst.un.unece.uncefact.UnitPriceAmount;
import com.klst.untdid.codelist.DocumentNameCode;
import com.klst.untdid.codelist.PaymentMeansCode;
import com.klst.untdid.codelist.TaxCategoryCode;

public class UblCreditNote extends UblImpl {
	
	private Object ublObject;

	@Override
	public String getDocumentNo() {
		return ((CreditNote)ublObject).getId();
	}

	void setBuyerReference(String buyerReference) {
		((CreditNote)ublObject).setBuyerReference(buyerReference);
	}

	@Override
	void setPaymentInstructions(PaymentMeansCode paymentMeansCode, IBANId iban, String remittanceInformation, String accountName) {
		((CreditNote)ublObject).setPaymentInstructions(paymentMeansCode, iban, remittanceInformation, accountName);
	}
	
	@Override
	void setPaymentTermsAndDate(String description, Timestamp ts) {
		((CreditNote)ublObject).setPaymentTermsAndDate(description, ts);
	}

	@Override
	void mapByuer(String buyerName, int location_ID, int user_ID) {
		Address address = mapLocationToAddress(location_ID);
		Contact contact = mapUserToContact(user_ID);
		((CreditNote)ublObject).setBuyer(buyerName, address, contact);
	}

	@Override
	void mapSeller(String sellerName, int location_ID, int salesRep_ID, String companyID, String companyLegalForm, String taxCompanyId) {
		Address address = mapLocationToAddress(location_ID);
		Contact contact = mapUserToContact(salesRep_ID);
		((CreditNote)ublObject).setSeller(sellerName, address, contact, companyID, companyLegalForm);
		((CreditNote)ublObject).setSellerTaxCompanyId(taxCompanyId);
	}

	@Override
	void setTotals(Amount lineExtension, Amount taxExclusive, Amount taxInclusive, Amount payable, Amount taxTotal) {
		((CreditNote)ublObject).setDocumentTotals(lineExtension, taxExclusive, taxInclusive, payable);
		((CreditNote)ublObject).setInvoiceTax(taxTotal);
	}
	
	@Override
	void setVATBreakDown(Amount taxableAmount, Amount tax, VatCategory vatCategory) {
		((CreditNote)ublObject).addVATBreakDown(taxableAmount, tax, vatCategory);
//				, vatCategory   // TODO testen mehr als eine mappen
	}
	
	void mapLine(MInvoiceLine invoiceLine) {
		int lineId = invoiceLine.getLine(); //Id
		BigDecimal taxRate = invoiceLine.getC_Tax().getRate().setScale(SCALE, RoundingMode.HALF_UP);
		VatCategory vatCategory = new VatCategory(TaxCategoryCode.StandardRate, taxRate);
		CreditNoteLine line = new CreditNoteLine(Integer.toString(lineId)
				, mapToQuantity(invoiceLine.getC_UOM().getX12DE355(), invoiceLine.getQtyInvoiced())
				, new Amount(mInvoice.getCurrencyISO(), invoiceLine.getLineNetAmt())
				, new UnitPriceAmount(mInvoice.getCurrencyISO(), invoiceLine.getPriceActual())
				, invoiceLine.getProduct().getName()
				, vatCategory
				);
		line.addItemDescription(invoiceLine.getDescription());
		((CreditNote)ublObject).addLine(line);		
	}

	Object mapToEModel(MInvoice adInvoice) {
		mInvoice = adInvoice;
		CreditNote obj = new CreditNote(XRECHNUNG_12, null, DocumentNameCode.CreditNote);
		obj.setId(mInvoice.getDocumentNo());
		obj.setIssueDate(mInvoice.getDateInvoiced());
		obj.setDocumentCurrency(mInvoice.getC_Currency().getISO_Code());
		this.ublObject = obj;
		super.mapBuyerReference();
//
//		makeOptionals();

		super.mapSellerGroup(); 
		super.mapBuyerGroup();
		
		makePaymentGroup();
		super.mapDocumentTotals();
		super.mapVatBreakDownGroup();
		super.mapLineGroup();
		return ublObject;
	}


}
