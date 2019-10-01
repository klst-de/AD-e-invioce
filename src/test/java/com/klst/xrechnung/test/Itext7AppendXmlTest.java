package com.klst.xrechnung.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.logging.Logger;

import org.adempiere.pdf.ZFappendix;
import org.compiere.util.Language;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.itextpdf.kernel.pdf.PdfAConformanceLevel;
import com.itextpdf.kernel.pdf.PdfDate;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfDocumentInfo;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfOutputIntent;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.PdfViewerPreferences;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.kernel.pdf.filespec.PdfFileSpec;
import com.itextpdf.kernel.utils.PdfMerger;
import com.itextpdf.kernel.xmp.XMPException;
import com.itextpdf.kernel.xmp.XMPMeta;
import com.itextpdf.kernel.xmp.XMPMetaFactory;
import com.itextpdf.pdfa.PdfADocument;

import io.konik.harness.AppendParameter;
import io.konik.harness.exception.InvoiceAppendError;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class Itext7AppendXmlTest {

	private static final Logger LOG = Logger.getLogger(Itext7AppendXmlTest.class.getName());

    public static final String sourceFolder = "./src/test/resources/"; // "./src/test/resources/com/itextpdf/kernel/pdf/XmpWriterTest/";
    public static final String destinationFolder = "./target/test/"; //"./target/test/com/itextpdf/kernel/pdf/XmpWriterTest/";
	
    @BeforeClass
    public static void staticSetup() {
        String userDir = System.getProperty("user.dir").replace('\\', '/');
		String so = "file:"+userDir.substring(2, userDir.length())+"/../e-invoice/src/test/kositresources/1.2.0_2018-12-19/scenarios.xml";
        LOG.info("userDir:"+userDir);
    }
    
	@Before 
    public void setup() {
        LOG.info("setup");
    }

//	@Test
	public void resources() {
        String filename = "Rechnung_27916.pdf";
        InputStream origin = getClass().getResourceAsStream("/" + filename);
        assertNotNull(origin);
		
	}
/*

emuliert/testet public void IText7Document.appendXml(AppendParameter appendable, final String description, final String fileName, PdfName mimeType, PdfName aFRelationship)
	      try {
	          appendInvoiceIntern(appendable, description, fileName, PdfName.ApplicationXml, new PdfName("Data"));
	       } catch (IOException e) {
	          throw new InvoiceAppendError("PDF IO Error", e);
	       }

 */
    @Test
    public void appendXml() throws IOException, InterruptedException {
        String filename = "Rechnung_27916.pdf";
        String attachmentName = "Rechnung_27916-cii.xml";
        String resultFile = destinationFolder + "Rechnung_27916+xml.pdf";
        LOG.info("filename:"+filename + " attachmentName"+attachmentName); // /AD-e-invoice/src/test/resources/Rechnung_27916.pdf
                                                                           //             ./src/test/resources/Rechnung_27916.pdf
        
        InputStream origin = getClass().getResourceAsStream("/" + filename);
        InputStream attachment = getClass().getResourceAsStream("/" + attachmentName);
        FileOutputStream fos1 = new FileOutputStream(resultFile);  // resultig OutputStream
        
        assertNotNull(origin);
        assertNotNull(attachment);
    	AppendParameter appendable = new ZFappendix(fos1, origin, attachment
    			, ZFappendix.ZF_VERSION_2_0
//    			, ZFappendix.ZF_CONFORMANCE_LEVEL_EN16931
    			, ZFappendix.ZF_CONFORMANCE_LEVEL_BASIC  // damit es zu xmpMeta passt
    			); // implements AppendParameter

//    	String embeddedFileName = "zugferd-invoice.xml"; // damit es zu xmpMeta passt, ABER https://services.fnfe-mpe.org/account/analysis?id=2995
// liefert: No embedded factur-x.xml file
    	String embeddedFileName = "factur-x.xml";
        appendInvoiceIntern(appendable, "description", embeddedFileName, PdfName.ApplicationXml, PdfName.Alternative);
    }
    
    
    private final static String ICC_URL = "sRGBColorSpaceProfile.icm"; // from pdfa/src/test/resources ohne blanks
    private InputStream iccStream() {
    	InputStream iccStream = getClass().getResourceAsStream("/" + ICC_URL);
    	assertNotNull(iccStream);
    	return iccStream;
    }
    
    private final static String DEFAULT_TITLE = "PDF/A-3 by klst.com";
	private void appendInvoiceIntern(AppendParameter appendable, final String description, final String fileName, PdfName mimeType, PdfName aFRelationship) throws IOException {
		LOG.info("AppendParameter:"+appendable 
				+ " description:"+description 
				+ " fileName:"+fileName 
				+ " mimeType:" + mimeType 
				+ " aFRelationship:" + aFRelationship
				);
		byte[] attachmentFile = convertToByteArray(appendable.attachmentFile());
		PdfADocument pdf = merge7(appendable.resultingPdf(), appendable.inputPdf(), DEFAULT_TITLE); 
		//Add attachment
		PdfDictionary parameters = new PdfDictionary();
		parameters.put(PdfName.ModDate, new PdfDate().getPdfObject());
		PdfFileSpec fileSpec = PdfFileSpec.createEmbeddedFileSpec(pdf, attachmentFile
				, description    // @param description         file description 
				, fileName       // @param fileDisplay         actual file name stored in the pdf
				, mimeType       // @param mimeType            mime-type of the file (optional - null)
				, parameters     // @param fileParameter       Pdfdictionary containing fil parameters
				, aFRelationship // @param AFRelationship key value (optional - null)
				);
		pdf.addAssociatedFile(description, fileSpec);
//		pdf.close();
		
//		//Add attachment
//		PdfDictionary pdfDictionary = new PdfDictionary();
//		pdfDictionary.put(PdfName.Type, PdfName.Filespec);
//		pdfDictionary.put(PdfName.ModDate, new PdfDate().getPdfObject()); // ?? braucht man es?
////		PdfString pdfString = new PdfString(fileName);
//		pdfDictionary.put(PdfName.F, new PdfString(fileName));
////		pdfDictionary.put(PdfName.UF, new PdfString(fileName));  // optional
//		pdfDictionary.put(PdfName.AFRelationship, PdfName.Data);
//		pdfDictionary.put(PdfName.Desc, new PdfString(description));
//		PdfFileSpec fileSpec = PdfFileSpec.createEmbeddedFileSpec(pdf, attachmentFile
//				, description    // @param description         file description 
//				, fileName       // @param fileDisplay         actual file name stored in the pdf
//				, mimeType       // @param mimeType            mime-type of the file (optional - null)
//				, pdfDictionary     // @param fileParameter       Pdfdictionary containing fil parameters
//				, aFRelationship // @param AFRelationship key value (optional - null)
//				);
////		fileSpec.put(key, value)
//		pdf.addAssociatedFile(description, fileSpec);
		try {
			pdf.setXmpMetadata(this.getXMPMeta()); //  throws XMPException
		} catch (XMPException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		pdf.close();
	}

	// aus IText7Document:
	private XMPMeta getXMPMeta() {
		XMPMeta xmpMeta = null;
		// im xmpMeta steht: <zf:ConformanceLevel>BASIC</zf:ConformanceLevel>
		//                   <zf:DocumentFileName>zugferd-invoice.xml</zf:DocumentFileName>
//		InputStream is = getClass().getResourceAsStream("/ZUGFeRD2_extension_schema.xmp.xml");
		InputStream is = getClass().getResourceAsStream("/Factur-X_extension_schema.xmp.xml");
		assertNotNull(is);
		try {
			xmpMeta = XMPMetaFactory.parse(is);
		} catch (XMPException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return xmpMeta;
	}
	private static byte[] convertToByteArray(InputStream is) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		byte[] buffer = new byte[65536];
		try {
			for (int length; (length = is.read(buffer)) != -1;) {
				baos.write(buffer, 0, length);
			}
			is.close();
			baos.close();
		} catch (IOException e) {
			throw new InvoiceAppendError("Was not possible to read Invoice Content stream", e);
		}
		return baos.toByteArray();
	}
	private PdfADocument merge7(OutputStream result, final InputStream origin, final String title) throws IOException {
		assertNotNull(origin);
        WriterProperties props = new WriterProperties(); 
//        if(debugMode) props.useDebugMode();
        PdfWriter writer = new PdfWriter(result, props);
        
        //Initialize PDFA document with output intent
        PdfOutputIntent intent = new PdfOutputIntent("Custom", "", "http://www.color.org", "sRGB IEC61966-2.1"
//        		, org.compiere.Adempiere.class.getResource(ICC_URL).openStream() ); // throws IOException 
        		, iccStream() ); // throws IOException 
        PdfADocument pdf = new PdfADocument(writer, PdfAConformanceLevel.PDF_A_3B, intent);
        // Creates output intent dictionary. Null values are allowed to suppress any key. 
        //   By default output intent subtype is GTS_PDFA1, use setter to change it.

        //Setting some required parameters
        pdf.setTagged();
        Locale locale = Language.getLoginLanguage().getLocale();
        pdf.getCatalog().setLang(new PdfString(locale.toString())); // #AD_Language 
        pdf.getCatalog().setViewerPreferences(new PdfViewerPreferences().setDisplayDocTitle(true));
        PdfDocumentInfo info = pdf.getDocumentInfo();
        info.setTitle(title);

        //Create PdfMerger instance
//		PdfMerger merger = new PdfMerger(pdf);
//		PdfDocument pdforigin = new PdfDocument(new PdfReader(origin)); // PdfReader throws IOException
//		int fromPage = 1;
//		merger.merge(pdforigin, fromPage, pdforigin.getNumberOfPages());
//		pdforigin.close();
//		
//        //pdf.close(); do not close, return:
//		return pdf;
        //Create PdfMerger instance
		PdfMerger merger = new PdfMerger(pdf);
		merger.setCloseSourceDocuments(true);
		PdfDocument pdforigin = new PdfDocument(new PdfReader(origin)); // PdfReader throws IOException
		int fromPage = 1;
		assertEquals(1,pdforigin.getNumberOfPages());
		merger.merge(pdforigin, fromPage, pdforigin.getNumberOfPages());
		return pdf;
	}

}
