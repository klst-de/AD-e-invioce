package com.klst.einvoice;

import java.sql.Timestamp;
import java.util.logging.Logger;

import org.compiere.model.MBank;
import org.compiere.model.MBankAccount;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MLocation;
import org.compiere.model.MOrgInfo;
import org.compiere.model.MPaymentTerm;
import org.compiere.model.MUser;
import org.compiere.util.DB;
import org.compiere.util.Env;

import com.klst.marshaller.UblCreditNoteTransformer;
import com.klst.marshaller.UblInvoiceTransformer;
import com.klst.einvoice.ubl.Address;
import com.klst.einvoice.ubl.Contact;
import com.klst.einvoice.ubl.VatCategory;
import com.klst.einvoice.unece.uncefact.Amount;
import com.klst.einvoice.unece.uncefact.IBANId;
import com.klst.untdid.codelist.PaymentMeansCode;

public class UblImpl extends AbstractEinvoice {

	private static final Logger LOG = Logger.getLogger(UblImpl.class.getName());

	private UblImpl delegate;
	
	// ctor
	public UblImpl() {
		super();
		delegate = null;
	}

	@Override
	public String getDocumentNo() {
		return delegate.getDocumentNo();
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

	// TODO: idee mInvoice.get_xmlDocument ... für xRechnung nutzen!!!!
	// ist in PO als public org.w3c.dom.Document get_xmlDocument(boolean noComment)
	// nein ==> get_xmlDocument wird in PO.get_xmlString genutzt und das in HouseKeeping Process

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
			delegate = new UblCreditNote();
//			return transformer.fromModel(mapToUblCreditNote(mInvoice));			
		} else {
			delegate = new UblInvoice();
//			return transformer.fromModel(mapToEModel(mInvoice));			
		}
		return transformer.fromModel(delegate.mapToEModel(mInvoice));			
	}

	@Override
	Object mapToEModel(MInvoice mInvoice) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	void setBuyerReference(String buyerReference) {
		// TODO Auto-generated method stub
		
	}
	
	void setPaymentInstructions(PaymentMeansCode paymentMeansCode, IBANId iban, String remittanceInformation, String accountName) {
		// TODO
	}
	void setPaymentTermsAndDate(String description, Timestamp ts) {
		
	}
	void makePaymentGroup() { // TODO nach oben und mit cii einheitlich
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
			setPaymentInstructions(paymentMeansCode, iban, "TODO Verwendungszweck", null); // TODO
		} else {
			LOG.warning("TODO PaymentMeansCode: mInvoice.PaymentRule="+mInvoice.getPaymentRule());
		}

		MPaymentTerm mPaymentTerm = new MPaymentTerm(Env.getCtx(), mInvoice.getC_PaymentTerm_ID(), get_TrxName());
//		((Invoice)ublObject).addPaymentTerms("#SKONTO#TAGE=7#PROZENT=2.00#"); // TODO
		setPaymentTermsAndDate(mPaymentTerm.getName(), (Timestamp)null); 
		LOG.info("finished.");
	}


	@Override
	void setTotals(Amount lineExtension, Amount taxExclusive, Amount taxInclusive, Amount payable, Amount taxTotal) {
		// TODO Auto-generated method stub
		
	}

	@Override
	void mapByuer(String buyerName, int location_ID, int user_ID) {
		// TODO Auto-generated method stub
		
	}

	@Override
	void mapSeller(String sellerName, int location_ID, int salesRep_ID, String companyID, String companyLegalForm,
			String taxCompanyId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	void setVATBreakDown(Amount taxableAmount, Amount tax, VatCategory vatCategory) {
		// TODO Auto-generated method stub
		
	}

	@Override
	void mapLine(MInvoiceLine line) {
		// TODO Auto-generated method stub
		
	}

}
