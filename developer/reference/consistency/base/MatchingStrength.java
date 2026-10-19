package consistency.base;

import ensdfparser.ensdf.Record;

public class MatchingStrength{
	public int order=-1;//1 the highest order (best match), multiple records can have the same order
	public float strength=-1;//a number showing the relative strength for a record to match a record group (only this group)
	                       //the higher the strength, the better match, the lower the matchingOrder
	
	
	public <T extends Record> MatchingStrength(int order) {
		this.order=order;
		this.strength=order*10;
	}
	
	public <T extends Record> MatchingStrength(int order,float strength) {
		this.order=order;
		this.strength=strength;
	}
	
	public <T extends Record> MatchingStrength(T r,RecordGroup recordGroup) {
		findMatchingStrengthAndOrder(r,recordGroup);
	}
	

	
	public <T extends Record> void findMatchingStrengthAndOrder(T r,RecordGroup recordGroup) {

	}

}
