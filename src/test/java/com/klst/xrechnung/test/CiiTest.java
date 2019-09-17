package com.klst.xrechnung.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.compiere.Adempiere;
import org.compiere.model.MInvoice;
import org.compiere.util.Env;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.klst.adempiere.einvoice.CiiImpl;
import com.klst.adempiere.einvoice.InterfaceEinvoice;
import com.klst.adempiere.einvoice.UblImpl;

import de.kosit.validationtool.api.Check;
import de.kosit.validationtool.api.CheckConfiguration;
import de.kosit.validationtool.api.Input;
import de.kosit.validationtool.api.InputFactory;
import de.kosit.validationtool.impl.DefaultCheck;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CiiTest {

	private static final Logger LOG = Logger.getLogger(CiiTest.class.getName());

/* Kandidaten für SEPA Bankeinzug
select * from c_bpartner where c_bpartner_id IN( select c_bpartner_id from c_bp_bankaccount where ad_client_id=1000000 and isactive='Y' and iban is not null)
and isactive='Y' and isCustomer='Y'

 */
	private static int testindex;
	private static final int[] INVOICE_ID = {
			1052923 , // Gutschrift!
			1051631 , // Beleg "27012" mit Bankeinzug BP IBAN / SEPA DirectDebit
//			1051399 , // Beleg "26898" mit Bankeinzug BP IBAN / SEPA DirectDebit : "Katalog 2019" liefert val-sch.1.1 	BR-S-05 	error
//			1000009 , // wg. pa !
			1000045 , // wg. DA 
			1012810 , // wg. RO ==> https://github.com/klst-de/e-invoice/issues/6
			1019117 , // wg. kg 
			1031534 , // wg. p100
			1032760 , // wg. m
			1039639,  // wg. PCE Stk.
//			1045662 , // wg. PAU!
			1051335 ,
			1053178 , // wg. HR
			1051341 ,
			1053563 , // Beleg 27916 wg. UTF-8 charset "Baseler Straße 2-4" https://github.com/adempiere/adempiere/issues/2701
			1053453}; // Beleg 27861 wg. Delivery und Beschreibung
	
	private static Check check;
	
	private static Properties adempiereCtx;
	
    @BeforeClass
    public static void staticSetup() {
        LOG.info("startup - creating kosit validator");
        String userDir = System.getProperty("user.dir").replace('\\', '/');
		String so = "file:"+userDir.substring(2, userDir.length())+"/../e-invoice/src/test/kositresources/1.2.1_2019-06-24/scenarios.xml";
		URI scenarios =  URI.create(so); // so == ablsolte path
		CheckConfiguration config = new CheckConfiguration(scenarios);
		LOG.info("config.ScenarioDefinition:"+config.getScenarioDefinition() +
				"\n config.ScenarioRepository:"+config.getScenarioRepository()
				);
		check = new DefaultCheck(config);
		
		LOG.info("startup - Adempiere");
		Adempiere.startupEnvironment(false); // boolean isClient
		testindex = 0;
    }
    
	@Before 
    public void setup() {
		adempiereCtx = Env.getCtx();
    }

	boolean check(byte[] xml) {
		Input xmlToCheck = InputFactory.read(xml, "test result");
		Document repDoc = check.check(xmlToCheck);

/* Auswertung ## Prüfbericht
Der Aufbau des Prüfberichts ist im entsprechenden Schema [report.xsd](configurations/xrechnung/resources/report.xsd) erläutert.
Die für die maschinelle Auswertung des Prüfberichts wesentlichsten Angaben sind

* der *Konformitätsstatus* (*valid* oder *invalid*, Attribut rep:report/@valid)
* die Empfehlung zur Annahme (*accept* - Element rep:report/rep:assessment/rep:accept) oder 
  Ablehnung (*reject* - Element rep:report/rep:assessment/rep:reject) des geprüften Dokuments.  
 */

		NodeList repReport = repDoc.getElementsByTagName("rep:report");
		NamedNodeMap repReportAttribute = repReport.item(0).getAttributes();
		String validValue = repReportAttribute.getNamedItem("valid").getNodeValue();
		LOG.info("rep:report/@valid" + "="+validValue);
		boolean ret = Boolean.TRUE.toString().equals(validValue);
		if(ret) {
			return ret; // isValid
		}
		// warum not valid?
		NodeList repReject = repDoc.getElementsByTagName("rep:reject");
		if(repReject.getLength()>0) {
			NodeList repExplanation = logNodeListAndFirstChild(repReject.item(0).getChildNodes(), "rep:explanation");
			NodeList html = logNodeListAndFirstChild(repExplanation, "html");
			NodeList body = logNodeListAndFirstChild(html, "body");
			NodeList table = logNodeListAndFindChild(body, "table", "class", "tbl-errors");
			NodeList tbody = logNodeListAndFirstChild(table, "tbody");
			NodeList tr = logNodeListAndFirstChild(tbody); //, "tr");
//			NodeList xxx = logNodeListAndFirstChild(tr, "td");
		}
		return ret;
	}
	private NodeList logNodeListAndFindChild(NodeList nodeList, String nodeName, String aName, String aValue) {
		if(nodeList==null) return null;
		NodeList childNodeList = null;
		for(int i=0; i<nodeList.getLength(); i++) {
			Node node = nodeList.item(i);
			NamedNodeMap nnm = node.getAttributes();
			LOG.info(i + ":"+ node.getNodeType() + " " + node.getNodeName() + "="+node.getNodeValue() + " Attributes:"+getAttributesAsString(nnm));
			Map<String,List<String>> map = getAttributesAsMap(nnm);
			if(nodeName.equals(node.getNodeName())) {
				List<String> aValues = map.get(aName);
				int j = aValues.indexOf(aValue);
				if(j>=0) {
					aValues.get(j);
//					LOG.info("!!!!!!!!!!!!! gefunden j="+j +" >>>"+node.getNodeName()); // davon gibt es 2! - der letzte wird genommen
					childNodeList = node.getChildNodes();
				}
			}
		}
		return childNodeList;
	}
	private NodeList logNodeListAndFirstChild(NodeList nodeList, String nodeName) {
		if(nodeList==null) return null;
		NodeList childNodeList = null;
		for(int i=0; i<nodeList.getLength(); i++) {
			Node node = nodeList.item(i);
			NamedNodeMap nnm = node.getAttributes();
			LOG.info(i + ":"+ node.getNodeType() + " " + node.getNodeName() + "="+node.getNodeValue() + "- Attributes:"+getAttributesAsString(nnm));
			if(nodeName.equals(node.getNodeName()) && childNodeList==null) {
				childNodeList = node.getChildNodes();
			}
		}
		return childNodeList;
	}
	private NodeList logNodeListAndFirstChild(NodeList nodeList) {
		if(nodeList==null) return null;
		NodeList childNodeList = null;
		for(int i=0; i<nodeList.getLength(); i++) {
			Node node = nodeList.item(i);
			NamedNodeMap nnm = node.getAttributes();
			LOG.info(i + ":"+ node.getNodeType() + " " + node.getNodeName() + "="+node.getTextContent() + "- Attributes:"+getAttributesAsString(nnm));
//			if(nodeName.equals(node.getNodeName()) && childNodeList==null) {
//				childNodeList = node.getChildNodes();
//			}
		}
		return childNodeList;
	}
	
	private Map<String,List<String>> getAttributesAsMap(NamedNodeMap attributeList) {
		Map<String,List<String>> maps = new HashMap<String,List<String>>();
		StringBuilder sb = new StringBuilder();
	    for (int j = 0; j < attributeList.getLength(); j++) {
	    	if(j>0) sb.append(" ");
	    	String attributeName = attributeList.item(j).getNodeName();
	    	String attributeValue = attributeList.item(j).getNodeValue();
	    	sb.append(attributeName).append("=").append(attributeValue);
	    	if(maps.get(attributeName)==null) {
	    		List<String> attribs = new ArrayList<String>();
	    		attribs.add(attributeValue);
	    		maps.put(attributeName, attribs);
	    	} else {
	    		maps.get(attributeName).add(attributeValue);
	    	}
	    }
//	    return sb.toString();
	    return maps;
	}
	/* @see https://stackoverflow.com/questions/4171380/generic-foreach-iteration-of-namednodemap
	 * Iterates through the node attribute map, else we need to specify specific 
	 * attribute values to pull and they could be of an unknown type
	 */
	private String getAttributesAsString(NamedNodeMap attributeList) {
		StringBuilder sb = new StringBuilder();
	    for (int j = 0; j < attributeList.getLength(); j++) {
	    	if("xmlns:xml".equals(attributeList.item(j).getNodeName())) {
	    		// nix
	    	} else {
		    	if(j>0) sb.append(" ");
//		    	sb.append(attributesList.item(j).getNodeType()).append("/");
		    	sb.append(attributeList.item(j).getNodeName()).append("=").append(attributeList.item(j).getNodeValue());
	    	}
	    }
	    return sb.toString();
	}

	private Document syntaxTest(String xmlSchema, MInvoice mInvoice) {
		Document document = null;
		try
		{
			InterfaceEinvoice eInvoice = null;
			if(xmlSchema.equals(InterfaceEinvoice.UBL_SCHEMA_NAME)) {
// xsi:schemaLocation="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2 http://docs.oasis-open.org/ubl/os-UBL-2.1/xsd/maindoc/UBL-Invoice-2.1.xsd">
// <cbc:CustomizationID>urn:cen.eu:en16931:2017#compliant#urn:xoev-de:kosit:standard:xrechnung_1.2</cbc:CustomizationID>
				eInvoice = new UblImpl();
			} else if(xmlSchema.equals(InterfaceEinvoice.CII_SCHEMA_NAME)) {
// xsi:schemaLocation="urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100 ../../../schemas/UN_CEFACT/CrossIndustryInvoice_100pD16B.xsd">
//              <ram:ID>urn:cen.eu:en16931:2017#compliant#urn:xoev-de:kosit:standard:xrechnung_1.2</ram:ID>
				eInvoice = new CiiImpl();
			}
			byte[] xmlData = eInvoice.tranformToXML(mInvoice);
			document = eInvoice.tranformToDomDocument(xmlData);
		}
		catch (Exception e)
		{
//			log.log(Level.SEVERE, "", e);
			LOG.severe(e.getMessage());
		}
		return document;
	}
	
	@Test
	public void test0() {
		CiiImpl invoice = new CiiImpl();
		MInvoice mInvoice = new MInvoice(adempiereCtx, INVOICE_ID[0], invoice.get_TrxName());
		LOG.info("docBaseType='"+mInvoice.getC_DocTypeTarget().getDocBaseType() + "' for "+mInvoice);
		
		byte[] xmlBytes = invoice.tranformToXML(mInvoice);
//		assertNull(xmlBytes);
		LOG.info("xml=\n"+new String(xmlBytes));
		assertEquals(invoice.getDocumentNo(), mInvoice.getDocumentNo());
		assertTrue(check(xmlBytes));
		
		try {
			org.w3c.dom.Document doc = invoice.tranformToDomDocument(xmlBytes);
			LOG.info("DocumentURI:"+doc.getDocumentURI());
			LOG.info("BaseURI:"+doc.getBaseURI());
			LOG.info("NamespaceURI:"+doc.getNamespaceURI());
			LOG.info("XmlVersion:"+doc.getXmlVersion());
			LOG.info("NodeType:"+doc.getNodeType()); // DOCUMENT_NODE             = 9;
			LOG.info("ChildNode#:"+doc.getChildNodes().getLength());
			Node node = doc.getFirstChild();
			if(node!=null) {
//				LOG.info("FirstChild:"+node);
				NodeList nodeList = node.getChildNodes();
				for(int i=0; i<nodeList.getLength(); i++) {
//					LOG.info("Child "+i + ":"+nodeList.item(i));
				}
				LOG.info("FirstChild:"+node+" has "+nodeList.getLength()+" childs.");
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

//	@Test
//	public void test0() {
//		UblImpl ublInvoice = new UblImpl();
//		MInvoice mInvoice = new MInvoice(adempiereCtx, INVOICE_ID[0], ublInvoice.get_TrxName());
//		LOG.info("docBaseType='"+mInvoice.getC_DocTypeTarget().getDocBaseType() + "' for "+mInvoice);
//		
//		byte[] xmlBytes = ublInvoice.tranformToXML(mInvoice);
////		assertNull(xmlBytes);
//		LOG.info("xml=\n"+new String(xmlBytes));
//		assertEquals(ublInvoice.getDocumentNo(), mInvoice.getDocumentNo());
//		assertTrue(check(xmlBytes));
//		
//		try {
//			org.w3c.dom.Document doc = ublInvoice.tranformToDomDocument(xmlBytes);
//			LOG.info("DocumentURI:"+doc.getDocumentURI());
//			LOG.info("BaseURI:"+doc.getBaseURI());
//			LOG.info("NamespaceURI:"+doc.getNamespaceURI());
//			LOG.info("XmlVersion:"+doc.getXmlVersion());
//			LOG.info("NodeType:"+doc.getNodeType()); // DOCUMENT_NODE             = 9;
//			LOG.info("ChildNode#:"+doc.getChildNodes().getLength());
//			Node node = doc.getFirstChild();
//			if(node!=null) {
////				LOG.info("FirstChild:"+node);
//				NodeList nodeList = node.getChildNodes();
//				for(int i=0; i<nodeList.getLength(); i++) {
////					LOG.info("Child "+i + ":"+nodeList.item(i));
//				}
//				LOG.info("FirstChild:"+node+" has "+nodeList.getLength()+" childs.");
//			}
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//	}
//	
//	@Test
//	public void test1() {
//		UblImpl ublInvoice = new UblImpl();
//		MInvoice mInvoice = new MInvoice(adempiereCtx, 1053453, ublInvoice.get_TrxName());
//		LOG.info("docBaseType='"+mInvoice.getC_DocTypeTarget().getDocBaseType() + "' for "+mInvoice);
//
//		byte[] xmlBytes = ublInvoice.tranformToXML(mInvoice);
//		LOG.info("xml=\n"+new String(xmlBytes));
//		assertEquals(ublInvoice.getDocumentNo(), mInvoice.getDocumentNo());
//		assertTrue(check(xmlBytes));
//	}
//	   
//	@Test
//	public void testSepaDirectDebit() {
//		UblImpl ublInvoice = new UblImpl();
//		MInvoice mInvoice = new MInvoice(adempiereCtx, INVOICE_ID[1], ublInvoice.get_TrxName());
//		LOG.info("docBaseType='"+mInvoice.getC_DocTypeTarget().getDocBaseType() + "' for "+mInvoice);
//
//		byte[] xmlBytes = ublInvoice.tranformToXML(mInvoice);
//		LOG.info("xml=\n"+new String(xmlBytes));
//		assertEquals(ublInvoice.getDocumentNo(), mInvoice.getDocumentNo());
//		assertTrue(check(xmlBytes));
//	}
//	   
//	@Test
//	public void testRO_Rolle() {
//		UblImpl ublInvoice = new UblImpl();
//		MInvoice mInvoice = new MInvoice(adempiereCtx, 1012810, ublInvoice.get_TrxName());
//		LOG.info("docBaseType='"+mInvoice.getC_DocTypeTarget().getDocBaseType() + "' for "+mInvoice);
//
//		byte[] xmlBytes = ublInvoice.tranformToXML(mInvoice);
//		LOG.info("xml=\n"+new String(xmlBytes));
//		assertEquals(ublInvoice.getDocumentNo(), mInvoice.getDocumentNo());
//		assertTrue(check(xmlBytes));
//	}
//	   
//	@Test
//	public void testUTF8() {
//		UblImpl ublInvoice = new UblImpl();
//		MInvoice mInvoice = new MInvoice(adempiereCtx, 1053563, ublInvoice.get_TrxName());
//		LOG.info("docBaseType='"+mInvoice.getC_DocTypeTarget().getDocBaseType() + "' for "+mInvoice);
//
//		byte[] xmlBytes = ublInvoice.tranformToXML(mInvoice);
//		LOG.info("xml=\n"+new String(xmlBytes));
//		assertEquals(ublInvoice.getDocumentNo(), mInvoice.getDocumentNo());
//		assertTrue(check(xmlBytes));
//	}
//	   
//	@Test
//	public void ubl() {
//		for (int i = 1; i < INVOICE_ID.length; i++) {
//			UblImpl ublInvoice = new UblImpl();
//			MInvoice mInvoice = new MInvoice(adempiereCtx, INVOICE_ID[i], ublInvoice.get_TrxName());
//			LOG.info("---------------------- "+mInvoice.toString());
//			byte[] xmlBytes = ublInvoice.tranformToXML(mInvoice);
////		LOG.info("xml=\n"+new String(xmlBytes));
//			assertEquals(ublInvoice.getDocumentNo(), mInvoice.getDocumentNo());
//			assertTrue(check(xmlBytes));
//		}
//	}

}
