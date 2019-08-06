package com.klst.adempiere.einvoice;

import java.util.HashMap;
import java.util.Map;

public class CustomizedMapping extends DefaultMapping implements InterfaceMapping {

	Map<Object,String> uomMap;
	
	CustomizedMapping() {
		super();
		uomMap = new HashMap<Object,String>();
		uomMap.put("PCE", 	"EA");	// piece/each
		uomMap.put("STK.", 	"EA");	// piece/each (de)
		uomMap.put("HR", 	"HUR");	// hour
		uomMap.put("DA", 	"DAY");
		uomMap.put("KG", 	"KGM");
		uomMap.put("M", 	"MTR");
		uomMap.put("PA", 	"XPA"); // prefix X @see https://github.com/klst-de/e-invoice/issues/6
		uomMap.put("PK", 	"XPK");
		uomMap.put("RO", 	"XRO");
		uomMap.put("P100", 	"CEN");	// %
	}
	
//	@Override
//	public Quantity mapToQuantity(String unitCode, BigDecimal quantity) {
//		return super.mapToQuantity(unitCode, quantity);
//	}

	@Override
	public String mapUoM(String unitCode) {
		if(unitCode==null) return null;
		String key = unitCode.toUpperCase();
		String value = uomMap.get(key);
		return value==null ? key : value;
	}

}
