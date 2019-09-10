package com.klst.adempiere.einvoice;

import java.math.BigDecimal;
import java.sql.Timestamp;

import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;

import com.klst.einvoice.BusinessParty;
import com.klst.einvoice.CoreInvoiceLine;
import com.klst.einvoice.CoreInvoiceVatBreakdown;
import com.klst.einvoice.CreditTransfer;
import com.klst.einvoice.DirectDebit;
import com.klst.einvoice.IContact;
import com.klst.einvoice.PaymentCard;
import com.klst.einvoice.PostalAddress;
import com.klst.einvoice.ubl.GenericInvoice;
import com.klst.einvoice.ubl.GenericLine;
import com.klst.einvoice.unece.uncefact.Amount;
import com.klst.einvoice.unece.uncefact.UnitPriceAmount;
import com.klst.untdid.codelist.DocumentNameCode;
import com.klst.untdid.codelist.PaymentMeansEnum;
import com.klst.untdid.codelist.TaxCategoryCode;

public class UblCreditNote extends UblImpl {
	
	private Object ublObject;
	GenericInvoice<?> ublInvoice;

	@Override
	public String getDocumentNo() {
		return ublInvoice.getId();
	}

	void setBuyerReference(String buyerReference) {
		ublInvoice.setBuyerReference(buyerReference);
	}

	@Override
	void setPaymentInstructions(PaymentMeansEnum code, String paymentMeansText, String remittanceInformation
			, CreditTransfer creditTransfer, PaymentCard paymentCard, DirectDebit directDebit) {
		ublInvoice.setPaymentInstructions(code, paymentMeansText, remittanceInformation, creditTransfer, paymentCard, directDebit);
	}
	
	@Override
	void setPaymentTermsAndDate(String description, Timestamp ts) {
		ublInvoice.setPaymentTermsAndDate(description, ts);
	}

	@Override
	void mapByuer(String buyerName, int location_ID, int user_ID) {
		PostalAddress address = mapLocationToAddress(location_ID, ublInvoice);
		IContact contact = mapUserToContact(user_ID, ublInvoice);
		ublInvoice.setBuyer(buyerName, address, contact);
	}

	@Override
	void mapSeller(String sellerName, int location_ID, int salesRep_ID, String companyId, String companyLegalForm, String taxRegistrationId) {
		PostalAddress address = mapLocationToAddress(location_ID, ublInvoice);
		IContact contact = mapUserToContact(salesRep_ID, ublInvoice);
		BusinessParty seller = ublInvoice.createParty(sellerName, address, contact);
		seller.setCompanyId(companyId);
		seller.setCompanyLegalForm(companyLegalForm);
		seller.setTaxRegistrationId(taxRegistrationId, "VAT"); // null no schemeID
		ublInvoice.setSeller(seller);
	}

	@Override
	void setTotals(Amount lineExtension, Amount taxExclusive, Amount taxInclusive, Amount payable, Amount taxTotal) {
		ublInvoice.setDocumentTotals(lineExtension, taxExclusive, taxInclusive, payable);
		ublInvoice.setInvoiceTax(taxTotal);
	}
	
	@Override
	void addVATBreakDown(CoreInvoiceVatBreakdown vatBreakdown) {
		ublInvoice.addVATBreakDown(vatBreakdown);
	}
 
	void mapLine(MInvoiceLine invoiceLine) {
		int lineId = invoiceLine.getLine(); //Id
		BigDecimal taxRate = invoiceLine.getC_Tax().getRate();
		CoreInvoiceLine line = GenericLine.createCreditNoteLine(Integer.toString(lineId)
				, this.mapping.mapToQuantity(invoiceLine.getC_UOM().getX12DE355(), invoiceLine.getQtyInvoiced())
				, new Amount(mInvoice.getCurrencyISO(), invoiceLine.getLineNetAmt())
				, new UnitPriceAmount(mInvoice.getCurrencyISO(), invoiceLine.getPriceActual())
				, invoiceLine.getProduct().getName()
				, TaxCategoryCode.StandardRate, taxRate
				);
		line.setDescription(invoiceLine.getDescription());
		ublInvoice.addLine(line);
	}

	Object mapToEModel(MInvoice adInvoice) {
		mInvoice = adInvoice;
		ublInvoice = GenericInvoice.createCreditNote(DEFAULT_PROFILE, null, DocumentNameCode.CreditNote);
		ublInvoice.setId(mInvoice.getDocumentNo());
		ublInvoice.setIssueDate(mInvoice.getDateInvoiced());
		ublInvoice.setDocumentCurrency(mInvoice.getC_Currency().getISO_Code());
		this.ublObject = ublInvoice.get();
		super.mapBuyerReference();
//
//		makeOptionals();

		super.mapSellerGroup(); 
		super.mapBuyerGroup();
		
		super.mapPaymentGroup();
		super.mapDocumentTotals();
		super.mapVatBreakDownGroup();
		super.mapLineGroup();
		return ublObject;
	}


}
