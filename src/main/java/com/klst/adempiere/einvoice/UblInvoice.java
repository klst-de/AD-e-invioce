package com.klst.adempiere.einvoice;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.compiere.model.MBPartner;
import org.compiere.model.MInOut;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.util.DB;
import org.compiere.util.Env;

import com.klst.einvoice.CoreInvoiceVatBreakdown;
import com.klst.einvoice.CreditTransfer;
import com.klst.einvoice.DirectDebit;
import com.klst.einvoice.PaymentCard;
import com.klst.einvoice.ubl.AdditionalSupportingDocument;
import com.klst.einvoice.ubl.Address;
import com.klst.einvoice.ubl.CommercialInvoice;
import com.klst.einvoice.ubl.Contact;
import com.klst.einvoice.ubl.Delivery;
import com.klst.einvoice.ubl.Invoice;
import com.klst.einvoice.ubl.InvoiceLine;
import com.klst.einvoice.ubl.Party;
import com.klst.einvoice.unece.uncefact.Amount;
import com.klst.einvoice.unece.uncefact.UnitPriceAmount;
import com.klst.untdid.codelist.PaymentMeansEnum;
import com.klst.untdid.codelist.TaxCategoryCode;

public class UblInvoice extends UblImpl {
	
	private static final Logger LOG = Logger.getLogger(UblInvoice.class.getName());

	private Object ublObject;

	@Override
	public String getDocumentNo() {
		return ((Invoice)ublObject).getId();
	}

	@Override
	void setBuyerReference(String buyerReference) {
		((Invoice)ublObject).setBuyerReference(buyerReference);
	}
	
	@Override
	void setPaymentInstructions(PaymentMeansEnum code, String paymentMeansText, String remittanceInformation
			, CreditTransfer creditTransfer, PaymentCard paymentCard, DirectDebit directDebit) {
		((Invoice)ublObject).setPaymentInstructions(code, paymentMeansText, remittanceInformation, creditTransfer, paymentCard, directDebit);
	}
	
	@Override
	void setPaymentTermsAndDate(String description, Timestamp ts) {
		((Invoice)ublObject).setPaymentTermsAndDate(description, ts);
	}

	@Override
	void mapByuer(String buyerName, int location_ID, int user_ID) {
		Address address = mapLocationToAddress(location_ID);
		Contact contact = mapUserToContact(user_ID);
		((Invoice)ublObject).setBuyer(buyerName, address, contact);
	}

	@Override
	void mapSeller(String sellerName, int location_ID, int salesRep_ID, String companyID, String companyLegalForm, String taxCompanyId) {
		Address address = mapLocationToAddress(location_ID);
		Contact contact = mapUserToContact(salesRep_ID);
		((Invoice)ublObject).setSeller(sellerName, address, contact, companyID, companyLegalForm);
		((Invoice)ublObject).setSellerTaxCompanyId(taxCompanyId);
	}

	@Override
	void setTotals(Amount lineExtension, Amount taxExclusive, Amount taxInclusive, Amount payable, Amount taxTotal ) {
		((Invoice)ublObject).setDocumentTotals(lineExtension, taxExclusive, taxInclusive, payable);
		((Invoice)ublObject).setInvoiceTax(taxTotal);
	}

	@Override
	void addVATBreakDown(CoreInvoiceVatBreakdown vatBreakdown) {
		((Invoice)ublObject).addVATBreakDown(vatBreakdown);
	}

	void mapLine(MInvoiceLine invoiceLine) {
		int lineId = invoiceLine.getLine(); //Id
		BigDecimal taxRate = invoiceLine.getC_Tax().getRate();
		InvoiceLine line = new InvoiceLine(Integer.toString(lineId)
				, this.mapping.mapToQuantity(invoiceLine.getC_UOM().getX12DE355(), invoiceLine.getQtyInvoiced())
				, new Amount(mInvoice.getCurrencyISO(), invoiceLine.getLineNetAmt())
				, new UnitPriceAmount(mInvoice.getCurrencyISO(), invoiceLine.getPriceActual())
				, invoiceLine.getProduct().getName()
				, TaxCategoryCode.StandardRate, taxRate
				);
		line.setDescription(invoiceLine.getDescription());
		((Invoice)ublObject).addLine(line);		
	}

	Object mapToEModel(MInvoice adInvoice) {
		mInvoice = adInvoice;
		Invoice obj = new CommercialInvoice(XRECHNUNG_12);
		obj.setId(mInvoice.getDocumentNo());
		obj.setIssueDate(mInvoice.getDateInvoiced());
		obj.setDocumentCurrency(mInvoice.getC_Currency().getISO_Code());
		this.ublObject = obj;
		super.mapBuyerReference();

		makeOptionals();

		super.mapSellerGroup(); 
		super.mapBuyerGroup();
		
		super.mapPaymentGroup();
		super.mapDocumentTotals();
		super.mapVatBreakDownGroup();
		super.mapLineGroup();
		return ublObject;
	}

	/* optional elements:
	 * VAT accounting currency code, BT-6
	 * Value added tax point date, BT-7
	 * Value added tax point date code, BT-8
	 * Payment due date, BT-9
	 * Project reference, BT-11
	 * Contract reference, BT-12
	 * Purchase order reference, BT-13
	 * Sales order reference, BT-14
	 * Receiving advice reference, BT-15
	 * Despatch advice reference, BT-16        --------- Eine Kennung für eine referenzierte Versandanzeige. LS
	 * Tender or lot reference, BT-17
	 * Invoiced object identifier, BT-18 / + Invoiced object identifier/Scheme identifier
	 * Buyer accounting reference, BT-19
	 * Payment terms, BT-20         --------> makePaymentGroup()
	 * INVOICE NOTE, BG-1
	 * PRECEDING INVOICE REFERENCE, BG-3
	 * PAYEE, BG-10
	 * SELLER TAX REPRESENTATIVE PARTY BG-11,
	 * DELIVERY INFORMATION, BG-13                            
	 * DOCUMENT LEVEL ALLOWANCES, BG-20
	 * DOCUMENT LEVEL CHARGES, BG-21
	 * ADDITIONAL SUPPORTING DOCUMENTS, BG-24   ---------------------- MZ
	 */
	// overwrite this to set optional elements
	protected void makeOptionals() {
		// Description ==> optional INVOICE NOTE
		String description = mInvoice.getDescription();
		if(description!=null) {
//			((Invoice)ublObject).addNote("Es gelten unsere Allgem. Geschäftsbedingungen."); // Bsp	
			((Invoice)ublObject).setNote(description);
			
			
			// TODO raus nur Test AdditionalSupportingDocument
			LOG.info(">>>>>>>>>>>>>>>>>>>>>>>>>");
			AdditionalSupportingDocument asd = new AdditionalSupportingDocument("1","AdditionalSupportingDocument description");
			asd.setExternalDocumentLocation("a URL TODO");
			((Invoice)ublObject).addAdditionalSupportingDocument(asd);

		}
		
		//  LS -> DELIVERY : 
		final String subselect = "SELECT m.M_InOut_ID FROM M_InOut m"
				+" LEFT JOIN M_InOutline ml ON ml.M_InOut_ID = m.M_InOut_ID"
				+" LEFT JOIN c_invoiceline il ON il.M_InOutline_ID = ml.M_InOutline_ID"
				+" WHERE il.C_Invoice_ID=? AND m.MovementType IN ('"+MInOut.MOVEMENTTYPE_CustomerShipment+"')"; // 2
		final String sql = "SELECT *"
				+" FROM "+MInOut.Table_Name
				+" WHERE "+MInOut.COLUMNNAME_MovementType + "='"+MInOut.MOVEMENTTYPE_CustomerShipment+"'"
				+" AND (("+MInOut.COLUMNNAME_C_Invoice_ID + "= ? AND "+MInOut.COLUMNNAME_IsSOTrx+"='Y')"  // 1
				+       " OR "+MInOut.COLUMNNAME_M_InOut_ID + " IN("+subselect+"))"
				+" AND "+MInOut.COLUMNNAME_IsActive+"='Y'"; 
//		LOG.info("\n"+sql);
		PreparedStatement pstmt = DB.prepareStatement(sql, get_TrxName());
		ResultSet rs = null;
		List<MInOut> mInOutList = new ArrayList<MInOut>();
		try {
			int invoice_ID = mInvoice.getC_Invoice_ID();
			DB.setParameter(pstmt, 1, invoice_ID);
			DB.setParameter(pstmt, 2, invoice_ID);
			rs = pstmt.executeQuery();
			while (rs.next()) {
				MInOut mInOut = new MInOut(Env.getCtx(), rs, get_TrxName());
				LOG.info("mInOut:"+mInOut);
				mInOutList.add(mInOut);
			}
			rs.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(mInOutList.size()==1) {
			int mBP_ID = mInOutList.get(0).getC_BPartner_ID();
			if(mBP_ID==mInvoice.getC_BPartner_ID()) {
				LOG.info("!!!!!!!!!!!!!!!!!!!!!! kein Delivery! size="+mInOutList.size());
			} else {
				int mC_Location_ID = mInOutList.get(0).getC_BPartner_Location().getC_Location_ID();
//				int mUser_ID = mInOutList.get(0).getAD_User_ID();
				
				MBPartner mBPartner = new MBPartner(Env.getCtx(), mBP_ID, get_TrxName());
				String shipToTradeName = mBPartner.getName();
				Address address = mapLocationToAddress(mC_Location_ID);
//				Contact contact = mapUserToContact(mUser_ID);
//				address = null; // wg. UBL-CR-394 	warning
//				contact = null; // wg. UBL-CR-398 	warning
//				Party party = new Party(name, address, contact);
				Party party = new Party(null, null, null, null, null);
				party.addName(shipToTradeName);
				Delivery delivery = new Delivery(party);
				delivery.setActualDate(mInOutList.get(0).getMovementDate());
				delivery.setLocationAddress(address);
				((Invoice)ublObject).addDelivery(delivery);
			}
		} else {
			LOG.warning("!!!!!!!!!!!!!!!!!!!!!! size="+mInOutList.size());
		}
		LOG.info("overwrite this to set optional elements.");
	}

}
