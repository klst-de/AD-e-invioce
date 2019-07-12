package com.klst.einvoice;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.compiere.model.I_C_Order;
import org.compiere.model.I_C_Tax;
import org.compiere.model.MBPartner;
import org.compiere.model.MBank;
import org.compiere.model.MBankAccount;
import org.compiere.model.MInOut;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MInvoiceTax;
import org.compiere.model.MLocation;
import org.compiere.model.MOrg;
import org.compiere.model.MOrgInfo;
import org.compiere.model.MPaymentTerm;
import org.compiere.model.MUser;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.w3c.dom.Document;

import com.klst.marshaller.UblCreditNoteTransformer;
import com.klst.marshaller.UblInvoiceTransformer;
import com.klst.ubl.AdditionalSupportingDocument;
import com.klst.ubl.Address;
import com.klst.ubl.CommercialInvoice;
import com.klst.ubl.Contact;
import com.klst.ubl.CreditNote;
import com.klst.ubl.CreditNoteLine;
import com.klst.ubl.Delivery;
import com.klst.ubl.Invoice;
import com.klst.ubl.InvoiceLine;
import com.klst.ubl.Party;
import com.klst.ubl.VatCategory;
import com.klst.un.unece.uncefact.Amount;
import com.klst.un.unece.uncefact.IBANId;
import com.klst.un.unece.uncefact.Quantity;
import com.klst.un.unece.uncefact.UnitPriceAmount;
import com.klst.untdid.codelist.DocumentNameCode;
import com.klst.untdid.codelist.PaymentMeansCode;
import com.klst.untdid.codelist.TaxCategoryCode;

public class UblImpl extends AbstractEinvoice {

	private static final Logger LOG = Logger.getLogger(UblImpl.class.getName());
	private static final String XRECHNUNG_12 = "urn:cen.eu:en16931:2017#compliant#urn:xoev-de:kosit:standard:xrechnung_1.2";

	protected Invoice ublInvoice; // to UBL object
	protected CreditNote ublCreditNote; // to UBL object
	
	// ctor
	public UblImpl() {
		super();
	}

	public String getDocumentNo() {
		return ublCreditNote==null ? ublInvoice.getId() : ublCreditNote.getId();
	}
	
	protected Address mapLocationToAddress(int location_ID) {
		MLocation mLocation = new MLocation(Env.getCtx(), location_ID, get_TrxName());
		String countryCode = mLocation.getCountry().getCountryCode();
		String postalCode = mLocation.getPostal();
		String city = mLocation.getCity();
		String street = null;
		String a1 = mLocation.getAddress1();
		String a2 = mLocation.getAddress2();
		String a3 = mLocation.getAddress3();
		String a4 = mLocation.getAddress4();
		Address address = new Address(countryCode, postalCode, city, street);
		if(a1!=null) address.setAddressLine1(a1);
		if(a2!=null) address.setAddressLine2(a2);
		if(a3!=null) address.setAddressLine3(a3);
		if(a4!=null) address.setAdditionalStreet(a4);
		return address;
	}
	
	protected Contact mapUserToContact(int user_ID) {
		MUser mUser = new MUser(Env.getCtx(), user_ID, get_TrxName());
		String contactName = mUser.getName();
		String contactTel = mUser.getPhone();
		String contactMail = mUser.getEMail();
		Contact contact = new Contact(contactName, contactTel, contactMail);
		return contact;
	}

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
//			ublInvoice.addNote("Es gelten unsere Allgem. Geschäftsbedingungen."); // Bsp	
			ublInvoice.setNote(description);
			
			
			// TODO raus nur Test AdditionalSupportingDocument
			LOG.info(">>>>>>>>>>>>>>>>>>>>>>>>>");
			AdditionalSupportingDocument asd = new AdditionalSupportingDocument("1","AdditionalSupportingDocument description");
			asd.setExternalDocumentLocation("a URL TODO");
			ublInvoice.addAdditionalSupportingDocument(asd);

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
				ublInvoice.addDelivery(delivery);
			}
		} else {
			LOG.warning("!!!!!!!!!!!!!!!!!!!!!! size="+mInOutList.size());
		}
		LOG.info("overwrite this to set optional elements.");
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
		if(mInvoice.isCreditMemo()) {
			ublCreditNote.setBuyerReference(mPOReference);
		} else {
			ublInvoice.setBuyerReference(mPOReference);
		}
	}
	
	Object mapToEModel(MInvoice adInvoice) {
		mInvoice = adInvoice;
		ublInvoice = new CommercialInvoice(XRECHNUNG_12);
		ublInvoice.setId(mInvoice.getDocumentNo());
		ublInvoice.setIssueDate(mInvoice.getDateInvoiced());
		ublInvoice.setDocumentCurrency(mInvoice.getC_Currency().getISO_Code());
		mapBuyerReference();

		makeOptionals();

		makeSellerGroup();
		 
		makeBuyerGroup();
		makePaymentGroup();
		makesDocumentTotalsGroup();
		makeVatBreakDownGroup();
		makeLineGroup();
		LOG.info("finished.");
		return ublInvoice;
	}

	Object mapToUblCreditNote(MInvoice adInvoice) { // TODO
		mInvoice = adInvoice;
		ublCreditNote = new CreditNote(XRECHNUNG_12, null, DocumentNameCode.CreditNote);
		ublCreditNote.setId(mInvoice.getDocumentNo());
		ublCreditNote.setIssueDate(mInvoice.getDateInvoiced());
		ublCreditNote.setDocumentCurrency(mInvoice.getC_Currency().getISO_Code());
		mapBuyerReference();
//
//		makeOptionals();

		makeSellerGroup(); 
		makeBuyerGroup();
		
		makePaymentGroup();
		makesDocumentTotalsGroup();
		makeVatBreakDownGroup();
		makeLineGroup();
		LOG.info("finished.");
		return ublCreditNote;
	}

	void makeSellerGroup() {
		int mAD_Org_ID = mInvoice.getAD_Org_ID();
		int mSalesRep_ID = mInvoice.getSalesRep_ID();
		
		String sellerRegistrationName = null;
		String companyID = null;
		String companyLegalForm = null;
		
		MOrg mOrg = new MOrg(Env.getCtx(), mAD_Org_ID, get_TrxName());
		sellerRegistrationName = mOrg.getName();
		MOrgInfo mOrgInfo = MOrgInfo.get(Env.getCtx(), mAD_Org_ID, get_TrxName());		
		Address address = mapLocationToAddress(mOrgInfo.getC_Location_ID());
		Contact contact = mapUserToContact(mSalesRep_ID);
		LOG.info("sellerRegistrationName:"+sellerRegistrationName +
				" companyID:"+companyID +
				" companyLegalForm:"+companyLegalForm
				);

		// optional:
		String taxCompanyId = mOrgInfo.getTaxID(); // UStNr DE....
		
		if(mInvoice.isCreditMemo()) {
			ublCreditNote.setSeller(sellerRegistrationName, address, contact, companyID, companyLegalForm);
			ublCreditNote.setSellerTaxCompanyId(taxCompanyId);
		} else {
			ublInvoice.setSeller(sellerRegistrationName, address, contact, companyID, companyLegalForm);
			ublInvoice.setSellerTaxCompanyId(taxCompanyId);
		}
		LOG.info("finished.");
	}
	
	void makeBuyerGroup() {
		int mBP_ID = mInvoice.getC_BPartner_ID();
		int mC_Location_ID = mInvoice.getC_BPartner_Location().getC_Location_ID();
		int mUser_ID = mInvoice.getAD_User_ID();
		
		MBPartner mBPartner = new MBPartner(Env.getCtx(), mBP_ID, get_TrxName());
		String buyerName = mBPartner.getName();
		Address address = mapLocationToAddress(mC_Location_ID);
		Contact contact = mapUserToContact(mUser_ID);
		if(mInvoice.isCreditMemo()) {
			ublCreditNote.setBuyer(buyerName, address, contact);
		} else {
			ublInvoice.setBuyer(buyerName, address, contact);
		}
		LOG.info("finished.");
	}

	void makePaymentGroup() {
		int mAD_Org_ID = mInvoice.getAD_Org_ID();
		MOrgInfo mOrgInfo = MOrgInfo.get(Env.getCtx(), mAD_Org_ID, get_TrxName());	
		MBank mBank = new MBank(Env.getCtx(), mOrgInfo.getTransferBank_ID(), get_TrxName());
		final String sql = "SELECT MIN("+MBankAccount.COLUMNNAME_C_BankAccount_ID+")"
				+" FROM "+MBankAccount.Table_Name
				+" WHERE "+MBankAccount.COLUMNNAME_C_Bank_ID+"=?"
				+" AND "+MBankAccount.COLUMNNAME_IsActive+"=?"; 
		int bankAccount_ID = DB.getSQLValueEx(get_TrxName(), sql, mOrgInfo.getTransferBank_ID(), true);
		MBankAccount mBankAccount = new MBankAccount(Env.getCtx(), bankAccount_ID, get_TrxName());
		
		if(mInvoice.getPaymentRule().equals(MInvoice.PAYMENTRULE_OnCredit) 
		|| mInvoice.getPaymentRule().equals(MInvoice.PAYMENTRULE_DirectDeposit) 
				) {
			PaymentMeansCode paymentMeansCode = PaymentMeansCode.CreditTransfer;
			IBANId iban = new IBANId(mBankAccount.getIBAN());
			if(mInvoice.isCreditMemo()) {
				ublCreditNote.setPaymentInstructions(paymentMeansCode, iban, "TODO Verwendungszweck", null); // TODO
			} else {
				ublInvoice.setPaymentInstructions(paymentMeansCode, iban, "TODO Verwendungszweck", null); // TODO
			}
		} else {
			LOG.warning("TODO PaymentMeansCode: mInvoice.PaymentRule="+mInvoice.getPaymentRule());
		}

		MPaymentTerm mPaymentTerm = new MPaymentTerm(Env.getCtx(), mInvoice.getC_PaymentTerm_ID(), get_TrxName());
//		ublInvoice.addPaymentTerms("#SKONTO#TAGE=7#PROZENT=2.00#"); // TODO
		if(mInvoice.isCreditMemo()) {
			ublCreditNote.setPaymentTermsAndDate(mPaymentTerm.getName(), (Timestamp)null);
		} else {
			ublInvoice.setPaymentTermsAndDate(mPaymentTerm.getName(), (Timestamp)null);
		}
		LOG.info("finished.");
	}

	void makesDocumentTotalsGroup() {
		BigDecimal taxBaseAmt = BigDecimal.ZERO;
		BigDecimal taxAmt = BigDecimal.ZERO;
		MInvoiceTax[] mInvoiceTaxes = mInvoice.getTaxes(true); // MInvoiceTax[] getTaxes (boolean requery)
		for(int i=0; i<mInvoiceTaxes.length; i++) {
			MInvoiceTax mInvoiceTax = mInvoiceTaxes[i];
			LOG.info(mInvoiceTax.toString());
			taxBaseAmt = taxBaseAmt.add(mInvoiceTax.getTaxBaseAmt());
			taxAmt = taxAmt.add(mInvoiceTax.getTaxAmt());
		}
		if(mInvoice.isCreditMemo()) {
			ublCreditNote.setDocumentTotals(new Amount(mInvoice.getCurrencyISO(), mInvoice.getTotalLines()) // lineExtension
					, new Amount(mInvoice.getCurrencyISO(), taxBaseAmt) // taxExclusive
					, new Amount(mInvoice.getCurrencyISO(), taxBaseAmt.add(taxAmt)) // taxInclusive
					, new Amount(mInvoice.getCurrencyISO(), mInvoice.getGrandTotal()) // payable
					);
			ublCreditNote.setInvoiceTax(new Amount(mInvoice.getCurrencyISO(), taxAmt));
		} else {
			ublInvoice.setDocumentTotals(new Amount(mInvoice.getCurrencyISO(), mInvoice.getTotalLines()) // lineExtension
					, new Amount(mInvoice.getCurrencyISO(), taxBaseAmt) // taxExclusive
					, new Amount(mInvoice.getCurrencyISO(), taxBaseAmt.add(taxAmt)) // taxInclusive
					, new Amount(mInvoice.getCurrencyISO(), mInvoice.getGrandTotal()) // payable
					);
			ublInvoice.setInvoiceTax(new Amount(mInvoice.getCurrencyISO(), taxAmt));
		}
		LOG.info("finished.");
	}

	private static final int SCALE = 2;
	void makeVatBreakDownGroup() {
		List<MInvoiceTax> taxes = Arrays.asList(mInvoice.getTaxes(true));
		taxes.forEach(mInvoiceTax -> {
			I_C_Tax tax = mInvoiceTax.getC_Tax(); // mapping
			LOG.info(mInvoiceTax.toString() + " - " + tax);
			BigDecimal taxRate = tax.getRate().setScale(SCALE, RoundingMode.HALF_UP);
			VatCategory vatCategory = new VatCategory(TaxCategoryCode.StandardRate, taxRate);
			// die optionalen "VAT exemption reason text" und "VAT exemption reason code" TODO
			LOG.info("vatCategory:" +vatCategory);
			if(mInvoice.isCreditMemo()) {
				ublCreditNote.addVATBreakDown(new Amount(mInvoice.getCurrencyISO(), mInvoiceTax.getTaxBaseAmt())
						, new Amount(mInvoice.getCurrencyISO(), mInvoiceTax.getTaxAmt())
						, vatCategory   // TODO mehr als eine mappen
						);
			} else {
				ublInvoice.addVATBreakDown(new Amount(mInvoice.getCurrencyISO(), mInvoiceTax.getTaxBaseAmt())
						, new Amount(mInvoice.getCurrencyISO(), mInvoiceTax.getTaxAmt())
						, vatCategory   // TODO mehr als eine mappen
						);
			}
		});
		LOG.info("finished. "+taxes.size() + " vatBreakDowns.");
	}
	
	void makeLineGroup() {
		// 
		List<MInvoiceLine> invoiceLines = Arrays.asList(mInvoice.getLines());
		invoiceLines.forEach(invoiceLine -> {
			LOG.info(invoiceLine.toString());
			int lineId = invoiceLine.getLine(); //Id
			if(BigDecimal.ZERO.compareTo(invoiceLine.getQtyInvoiced())==0) { 
				// empty Line
			} else {
				BigDecimal taxRate = invoiceLine.getC_Tax().getRate().setScale(SCALE, RoundingMode.HALF_UP);
				VatCategory vatCategory = new VatCategory(TaxCategoryCode.StandardRate, taxRate);
				if(mInvoice.isCreditMemo()) {
					CreditNoteLine line = new CreditNoteLine(Integer.toString(lineId)
							, mapToQuantity(invoiceLine.getC_UOM().getX12DE355(), invoiceLine.getQtyInvoiced())
							, new Amount(mInvoice.getCurrencyISO(), invoiceLine.getLineNetAmt())
							, new UnitPriceAmount(mInvoice.getCurrencyISO(), invoiceLine.getPriceActual())
							, invoiceLine.getProduct().getName()
							, vatCategory
							);
					line.addItemDescription(invoiceLine.getDescription());
					ublCreditNote.addLine(line);		
				} else {
					InvoiceLine line = new InvoiceLine(Integer.toString(lineId)
							, mapToQuantity(invoiceLine.getC_UOM().getX12DE355(), invoiceLine.getQtyInvoiced())
							, new Amount(mInvoice.getCurrencyISO(), invoiceLine.getLineNetAmt())
							, new UnitPriceAmount(mInvoice.getCurrencyISO(), invoiceLine.getPriceActual())
							, invoiceLine.getProduct().getName()
							, vatCategory
							);
					line.addItemDescription(invoiceLine.getDescription());
					ublInvoice.addLine(line);		
				}
			}
		});
		LOG.info("finished. "+invoiceLines.size() + " lines.");
	}

	// TODO: idee mInvoice.get_xmlDocument ... für xRechnung nutzen!!!!
	// ist in PO als public org.w3c.dom.Document get_xmlDocument(boolean noComment)
	// @see https://stackoverflow.com/questions/21165871/how-to-convert-array-byte-to-org-w3c-dom-document
	public Document tranformToDomDocument(byte[] xmlData) throws Exception {
	    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	    factory.setNamespaceAware(true);
	    DocumentBuilder builder = factory.newDocumentBuilder();
	    return builder.parse(new ByteArrayInputStream(xmlData));
	}
	

	@Override
	public void setupTransformer(boolean isCreditNote) {
		if(isCreditNote) {
			transformer = UblCreditNoteTransformer.getInstance();
		} else {
			transformer = UblInvoiceTransformer.getInstance();
		}
	}

	@Override
	public byte[] tranformToXML(MInvoice mInvoice) {
		boolean isCreditNote = mInvoice.isCreditMemo();
		setupTransformer(isCreditNote);
		if(isCreditNote) {
			return transformer.fromModel(mapToUblCreditNote(mInvoice));			
		} else {
			return transformer.fromModel(mapToEModel(mInvoice));			
		}
	}

}
