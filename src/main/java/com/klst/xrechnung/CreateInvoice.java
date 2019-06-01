package com.klst.xrechnung;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import org.compiere.model.I_C_Order;
import org.compiere.model.I_C_Tax;
import org.compiere.model.MBPartner;
import org.compiere.model.MBank;
import org.compiere.model.MBankAccount;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MInvoiceTax;
import org.compiere.model.MLocation;
import org.compiere.model.MOrg;
import org.compiere.model.MOrgInfo;
import org.compiere.model.MPaymentTerm;
import org.compiere.model.MUser;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;
import org.compiere.util.Env;

import com.klst.cius.IContact;
import com.klst.cius.PostalAddress;
import com.klst.marshaller.AbstactTransformer;
import com.klst.marshaller.UblInvoiceTransformer;
import com.klst.ubl.Address;
import com.klst.ubl.CommercialInvoice;
import com.klst.ubl.Contact;
import com.klst.ubl.Invoice;
import com.klst.ubl.InvoiceLine;
import com.klst.ubl.VatCategory;
import com.klst.un.unece.uncefact.Amount;
import com.klst.un.unece.uncefact.IBANId;
import com.klst.un.unece.uncefact.Quantity;
import com.klst.un.unece.uncefact.UnitPriceAmount;
import com.klst.untdid.codelist.PaymentMeansCode;
import com.klst.untdid.codelist.TaxCategoryCode;

public class CreateInvoice extends SvrProcess {

	private static final Logger LOG = Logger.getLogger(CreateInvoice.class.getName());
	private static final String XRECHNUNG_12 = "urn:cen.eu:en16931:2017#compliant#urn:xoev-de:kosit:standard:xrechnung_1.2";

	protected AbstactTransformer transformer; // ein Singleton
	protected MInvoice mInvoice; // from AD object
	protected Invoice ublInvoice; // to UBL object

	// ctor
	public CreateInvoice() {
		super();
		transformer = UblInvoiceTransformer.getInstance();
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

	public String getDocumentNo() {
		return ublInvoice.getId();
	}
	
	protected PostalAddress mapLocationToAddress(int location_ID) {
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
	
	protected IContact mapUserToContact(int user_ID) {
		MUser mUser = new MUser(Env.getCtx(), user_ID, get_TrxName());
		String contactName = mUser.getName();
		String contactTel = mUser.getPhone();
		String contactMail = mUser.getEMail();
		IContact contact = new Contact(contactName, contactTel, contactMail);
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
	
	Invoice makeInvoice(MInvoice adInvoice) {
		mInvoice = adInvoice;
		ublInvoice = new CommercialInvoice(XRECHNUNG_12);
		ublInvoice.setId(mInvoice.getDocumentNo());
		ublInvoice.setIssueDate(mInvoice.getDateInvoiced());
		ublInvoice.setDocumentCurrencyCode(mInvoice.getC_Currency().getISO_Code());
//		ublInvoice.setTaxCurrencyCode(mInvoice.getC_Currency().getISO_Code()); TODO
		String mPOReference = mInvoice.getPOReference(); // kann null sein
		if(mPOReference==null) {
			I_C_Order order = mInvoice.getC_Order();
			if(order.getPOReference()==null) { // auch das kann null sein, dann 
				mPOReference = "Order " +order.getDocumentNo() + ", DateOrdered " +order.getDateOrdered().toString().substring(0, 10);
			} else {
				mPOReference = order.getPOReference();
			}
		}
		ublInvoice.setBuyerReference(mPOReference);

//		makeOptionals(ublInvoice);

		makeSellerGroup();
		 
		makeBuyerGroup();
		makePaymentGroup();
		makesDocumentTotalsGroup();
		makeVatBreakDownGroup();
		makeLineGroup();
//		LOG.info("finished.");
		return ublInvoice;
	}

	void makeSellerGroup() {
		int mAD_Org_ID = mInvoice.getAD_Org_ID();
		int mSalesRep_ID = mInvoice.getSalesRep_ID();
		
		String sellerRegistrationName = null;
		String companyID = null;
		String ompanyLegalForm = null;
		
		MOrg mOrg = new MOrg(Env.getCtx(), mAD_Org_ID, get_TrxName());
		sellerRegistrationName = mOrg.getName();
		MOrgInfo mOrgInfo = MOrgInfo.get(Env.getCtx(), mAD_Org_ID, get_TrxName());		
		PostalAddress address = mapLocationToAddress(mOrgInfo.getC_Location_ID());
		IContact contact = mapUserToContact(mSalesRep_ID);
		LOG.info("sellerRegistrationName:"+sellerRegistrationName +
				" companyID:"+companyID +
				" companyLegalForm:"+ompanyLegalForm
				);

		String taxCompanyId = mOrgInfo.getTaxID(); // UStNr DE....
		ublInvoice.setSeller(sellerRegistrationName, address, contact, 
				companyID, ompanyLegalForm);
// optional:
		ublInvoice.setSellerTaxCompanyId(taxCompanyId);
//		partyNames.forEach(name -> {
//			ublInvoice.addSellerPartyName(name);
//		});
		LOG.info("finished.");
	}
	
	void makeBuyerGroup() {
		int mBP_ID = mInvoice.getC_BPartner_ID();
		int mC_Location_ID = mInvoice.getC_BPartner_Location().getC_Location_ID();
		int mUser_ID = mInvoice.getAD_User_ID();
		
		MBPartner mBPartner = new MBPartner(Env.getCtx(), mBP_ID, get_TrxName());
		String buyerName = mBPartner.getName();
		PostalAddress address = mapLocationToAddress(mC_Location_ID);
		IContact contact = mapUserToContact(mUser_ID);
		ublInvoice.setBuyer(buyerName, address, contact);
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
			ublInvoice.addPaymentInstructions(paymentMeansCode, iban, "TODO Verwendungszweck"); // TODO
		} else {
			LOG.warning("TODO PaymentMeansCode: mInvoice.PaymentRule="+mInvoice.getPaymentRule());
		}

		MPaymentTerm mPaymentTerm = new MPaymentTerm(Env.getCtx(), mInvoice.getC_PaymentTerm_ID(), get_TrxName());
//		ublInvoice.addPaymentTerms("#SKONTO#TAGE=7#PROZENT=2.00#"); // TODO
		ublInvoice.addPaymentTerms(mPaymentTerm.getName());
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
		ublInvoice.setDocumentTotals(new Amount(mInvoice.getCurrencyISO(), mInvoice.getTotalLines()) // lineExtension
				, new Amount(mInvoice.getCurrencyISO(), taxBaseAmt) // taxExclusive
				, new Amount(mInvoice.getCurrencyISO(), taxBaseAmt.add(taxAmt)) // taxInclusive
				, new Amount(mInvoice.getCurrencyISO(), mInvoice.getGrandTotal()) // payable
				);
		ublInvoice.setInvoiceTax(new Amount(mInvoice.getCurrencyISO(), taxAmt));
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
			ublInvoice.addVATBreakDown(new Amount(mInvoice.getCurrencyISO(), mInvoiceTax.getTaxBaseAmt())
					, new Amount(mInvoice.getCurrencyISO(), mInvoiceTax.getTaxAmt())
					, vatCategory   // TODO mehr als eine mappen
					);
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
				InvoiceLine line = new InvoiceLine(Integer.toString(lineId)
						, mapToQuantity(invoiceLine.getC_UOM().getX12DE355(), invoiceLine.getQtyInvoiced())
						, new Amount(mInvoice.getCurrencyISO(), invoiceLine.getLineNetAmt())
						, new UnitPriceAmount(mInvoice.getCurrencyISO(), invoiceLine.getPriceActual())
						, invoiceLine.getProduct().getName()
						, vatCategory
						);
				line.addItemDescription(invoiceLine.getDescription());
				ublInvoice.addInvoiceLine(line);		
			}
		});
		LOG.info("finished. "+invoiceLines.size() + " lines.");
	}

	public byte[] toUbl(MInvoice mInvoice) {
		makeInvoice(mInvoice);
		return transformer.fromModel(ublInvoice);
	}

}
