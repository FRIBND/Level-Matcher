package consistency.base;

import java.util.Vector;

import ensdfparser.calc.Average;
import ensdfparser.calc.DataPoint;
import ensdfparser.ensdf.*;
import ensdfparser.ensdf.Record;
import ensdfparser.nds.ensdf.EnsdfUtil;
import ensdfparser.nds.util.Str;
/*
 * group of the same ENSDF records (level/gamma) from different datasets
 * for level: by energy and JPI (energy math within error and JPI overlaps)
 * for gamma: by energy
 */
//public class RecordGroup implements Cloneable{
public class RecordGroup{
	//For DSID like A(B,C)D, short ID=(B,C)
	//this vector contains all DSIDs that could share the same short ID=(B,C) with others in current ENSDF group
	Vector<String> dsidsVWithDuplicateShortID=new Vector<String>();
	
	@SuppressWarnings("rawtypes")
	private Vector recordsV=new Vector();
	
	private Vector<String> dsidsV=new Vector<String>(); //dsid of the dataset from which each record is from; same as datasetDSIDsV in EnsdfGroup
	                                                    //could be NOT the same as the DSID in XREF list in Adopted dataset if existing
	private Vector<String> xtagsV=new Vector<String>(); //xtag of the dataset from which each record is from; same as datasetXTagsV in EnsdfGroup
	                                                    //They are the new xtags if no Adopted dataset;
	                                                    //If Adopted dataset exists, they are the original xtag in XREF list corresponding to the 
	                                                    //same DSID as that of the dataset;if the dataset DSID is not in the XREF list, a xtag="?#" 
	                                                    //where #=0,1,2,..., is assigned
	                                                    //Note that each xtag also contains all markers in "()" following the xtag letter or symbol.
	                                   
	private Vector<RecordGroup> subgroups=new Vector<RecordGroup>();//for gamma groups of a level groups
	
	private int iMinE=-1,iMaxE=-1;
	private final float MIN_E_INIT=1000000,MAX_E_INIT=-100000;

	private final float MIN_DE_INIT=100000;
	
	private float minE=MIN_E_INIT,maxE=MAX_E_INIT,minDE=MIN_DE_INIT;
	
	private Record adopted=null;//adopted record
    private Record refRecord=null;//reference record (best representative of the group) for printing. =adopted if not NULL
    
	private Record averageRec=null;//for average of all records in the group
	
	public void clear(){
		recordsV.clear();
		dsidsV.clear();
		xtagsV.clear();
		subgroups=new Vector<RecordGroup>();
		iMinE=-1; iMaxE=-1;
		minE=MIN_E_INIT; maxE=MAX_E_INIT;minDE=MIN_DE_INIT;
		adopted=null;
		refRecord=null;
		averageRec=null;
	}
	
	@SuppressWarnings("unchecked")
	public RecordGroup lightCopy(){
		RecordGroup newGroup=new RecordGroup();
		/*
		try {
			newGroup=(RecordGroup)this.clone();
		} catch (CloneNotSupportedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		*/
		
		newGroup.iMinE=iMinE;
		newGroup.iMaxE=iMaxE;
		newGroup.minE=minE;
		newGroup.maxE=maxE;
		newGroup.adopted=adopted;
		newGroup.refRecord=refRecord;
		newGroup.averageRec=averageRec;
		
		newGroup.recordsV().addAll(recordsV);
		newGroup.dsidsV().addAll(dsidsV);
		newGroup.xtagsV().addAll(xtagsV);
		
		newGroup.dsidsVWithDuplicateShortID.addAll(dsidsVWithDuplicateShortID);
		
		Vector<RecordGroup> subGroupsV=newGroup.subgroups();
		subGroupsV.clear();
		for(RecordGroup g:subgroups) {
			subGroupsV.add(g.lightCopy());
		}
		
		return newGroup;
	}
	
	
	/*
	 * Note that for removing a level from a level group, this doesn't remove the gamma 
	 * of the removed level from the gamma subgroups of the level group 
	 * Updated 8/3/2023: also remove gamma in gamma subgroups
	 */
	public void remove(int index){
		try{
			if(index<0)
				return;
			
			boolean isLevel=(recordsV.get(0) instanceof Level);
	          
			String xtag=xtagsV.get(index);
			
			int refIndex=recordsV.indexOf(refRecord);
			String refXtag="";
			if(refIndex>=0)
			    xtagsV.get(refIndex);
			
			recordsV.remove(index);
			dsidsV.remove(index);
			xtagsV.remove(index);
			

			//rem
			if(isLevel) {
				//gamma subgroups
				for(RecordGroup g:this.subgroups) {
					int gIndex=g.xtagsV.indexOf(xtag);
					if(gIndex<0)
						continue;
						
					g.remove(gIndex);
				}
			}
			
			if(index==iMinE || index==iMaxE) {
				//the minE and/or maxE record has been removed, find the new minE and maxE records
				minE=MIN_E_INIT;
				maxE=MAX_E_INIT;
				
				for(int i=0;i<recordsV.size();i++) {
					Record r=(Record) recordsV.get(i);
					
			        float ef=r.ERPF();
			        if(ef<minE){
			            minE=ef;
			            iMinE=i;
			        }
			        if(ef>maxE){
			            maxE=ef;
			            iMaxE=i;
			        }
				}
			}else {
				iMinE=recordsV.indexOf(this.getMinERecord());
				iMaxE=recordsV.indexOf(this.getMaxERecord());
			}
			
			//the refRecord has been removed, find the new refRecord
			if(index==refIndex) {
				minDE=-1;
				for(int i=0;i<recordsV.size();i++) {
					Record r=(Record) recordsV.get(i);
					float de=r.DEF();
					String tempXtag=xtagsV.get(i);
					
					boolean isGood=false;
	                if(tempXtag.contains("*")==refXtag.contains("*")) {
	                	if(de>0 && (minDE<0||de<minDE))
		                    isGood=true;
	                }else if(refXtag.contains("*")) {
	                	isGood=true;
	                }
	                
	                if(isGood) {
	                    minDE=de;
	                    setReferenceRecord(r);
	                }
				}

			}
						
			averageRec=calculateAverageEnergy();
			
		}catch(Exception e){
		    e.printStackTrace();
		}
	}
	
	
	public <T extends Record> void removeRecord(T r){
		try{
			int index=recordsV.indexOf(r);	         
			remove(index);

		}catch(Exception e){
			
		}
	}
	
	public <T extends Record> void remove(T r){
		removeRecord(r);
	}

	public <T extends Record> void addRecord(T r,String dsid,String xtag){
		insertRecord(recordsV.size(),r,dsid,xtag);
	}
	
	/*
	 * Note that for level record being insert into a level group, the gammas of the inserted
	 * level if existing is not grouped with the gamma subgroup of current level group at this
	 * state. It is done in the subsequent groupGammas() in groupLevels() in EnsdfGroup.java.
	 * So if one needs to also group the gammas of the inserted levels, groupGammas(LevelGroup) 
	 * should be called after adding or inserting.  
	 */
	@SuppressWarnings("unchecked")
	public <T extends Record> void insertRecord(int index,T r,String dsid,String xtag){
	    try {
	        int size=recordsV.size();
	        int i=(Math.abs(index)-Math.abs(size-index)+size)/2;
	        
	        recordsV.insertElementAt(r, i);
	        dsidsV.insertElementAt(dsid,i);
	        xtagsV.insertElementAt(xtag,i);
	        
	        float ef=r.ERPF();
	        float de=r.DEF();
	        if(ef<minE){
	            minE=ef;
	            iMinE=i;
	        }
	        if(ef>maxE){
	            maxE=ef;
	            iMaxE=i;
	        }
	        
	        /*
	        //debug
	        if(iMaxE>=recordsV.size()) {
	        //if(Math.abs(ef-3374.6)<0.1) {
	            System.out.println("In RecordGroup line 142:  DSID="+dsid+" size="+size+" xtag="+xtag+" insert: i="+i+" r.es="+r.ES()+" EF="+r.EF()+"  minE="+minE+" minDE="+minDE+" de="+de+" iMinE="+iMinE+" maxE="+maxE+" iMaxE="+iMaxE);
	            for(int k=0;k<size;k++) {
	                Record rec=(Record) recordsV.get(k);
	                System.out.println("   in group: E="+rec.ES()+" dsid="+dsidsV.get(k));
	            }
	        }
	        */
	        
	        if(xtag.trim().length()==0 && dsid.contains("ADOPTED")) {
	            setAdoptedRecord(r);
	            setReferenceRecord(r);
	            minDE=de;
	        }else if(size==0) {
	            minDE=de;
	            setReferenceRecord(r);

	            
                //debug
                //if(Math.abs(ef-7037)<5) 
                //    System.out.println("In RecordGroup line 158:   DSID="+dsid+" insert: i="+i+" r.es="+r.ES()+" EF="+r.EF()+"  minE="+minE+" minDE="+minDE+" de="+de+" iMinE="+iMinE+" maxE="+maxE+" iMaxE="+iMaxE);
                  
                
	        }else if(adopted==null) {//here refRecord!=null
	            boolean isGood=false;
                int refIndex=recordsV.indexOf(refRecord);
                String refXtag=xtagsV.get(refIndex);
                
                if(xtag.contains("*")==refXtag.contains("*")) {
                	if(de>0 && (minDE<0||de<minDE))
	                    isGood=true;
                }else if(refXtag.contains("*")) {
                	isGood=true;
                }
                
                if(isGood) {
                    minDE=de;
                    setReferenceRecord(r);
                }
                
                //debug
                //if(Math.abs(ef-7037)<5)
                //    System.out.println("In RecordGroup line 158:  reftag="+refXtag+" thistag="+xtag+" DSID="+dsid+" insert: i="+i+" r.es="+r.ES()+" EF="+r.EF()+"  minE="+minE+" minDE="+minDE+" de="+de+" iMinE="+iMinE+" maxE="+maxE+" iMaxE="+iMaxE);

                
                /*
	            if(!refXtag.contains("*") && !xtag.contains("*")) {
	                if(refXtag.contains("*"))
	                    isGood=true;
	                else if(de>0 && (minDE<0||de<minDE))
	                    isGood=true;
	                    
	                if(isGood) {
	                    minDE=de;
	                    setReferenceRecord(r);
	                }

	            }
	            */
	        }
	        
	           
            averageRec=calculateAverageEnergy();
            
	    }catch(Exception e) {
	        
	    }

	}
	
	/*
	 * insert and sort record according to energy (assuming recordsV is already sorted)
	 * For ordering level energy, use EF() and for matching energy, use ERPF(), for level like 1234.5+X
	 * No difference for numerical E(level)
	 */
	@SuppressWarnings("unchecked")
	public <T extends Record> int insertAndSortRecord(int fromIndex,T r,String dsid,String xtag){
        float ef=r.ERPF();
        int size=recordsV.size();
               
        float minEF=minE;
        float maxEF=maxE;
        if(fromIndex<0 || fromIndex>=size)
        	fromIndex=0;
        else
        	minEF=((T)recordsV.get(fromIndex)).ERPF();
                
        int index=fromIndex;
        int i1=fromIndex,i2=size-1;
        size=i2-i1+1;
        if(ef<=minEF)
        	index=i1;
        else if(ef>=maxEF)
        	index=i1+size;
        else{
        	index=(int) (size*(ef-minEF)/(maxEF-minEF))+i1;
        	float ei=((T)recordsV.get(index)).ERPF();
        	
        	while(true){
        		if(ef<ei)
        			i2=index;
        		else if(ef>ei)
        			i1=index;
        		else
        			break;
        		
        		index=(i1+i2)/2;
        		ei=((T)recordsV.get(index)).ERPF();
        		
        		if(index==i1){//that is i2=i1+1
        			index=i1+1;
        			break;
        		}
        		
        	}
        }
        
        insertRecord(index,r,dsid,xtag);
        
        return index;
	}
	
	public <T extends Record> int insertAndSortRecord(T r,String dsid,String xtag){
        return insertAndSortRecord(0,r,dsid,xtag);
	}
	
	public <T extends Record> void insertAndSortRecords(Vector<T> rV,String dsid,String xtag){
		int fromIndex=0;
		for(int i=0;i<rV.size();i++){
			T r=rV.get(i);
			fromIndex=insertAndSortRecord(fromIndex,r,dsid,xtag)+1;			
		}
	}
	
	@SuppressWarnings("unchecked")
	public <T extends Record> T getRecord(int i){
		try{
			return (T) recordsV.get(i);
		}catch (Exception e){
			return null;
		}
		
	}

	public String getDSID(int i){
		try{
			return dsidsV.get(i);
		}catch (Exception e){
			return "?";
		}	
	}
	
	/*
	 * ID of a dataset to be printed out, like the ID used for averaging comments
	 */
	public String getPrintDSID(int i){
		String dsid=getDSID(i);
		if(!dsidsVWithDuplicateShortID.contains(dsid))
			return EnsdfUtil.getShortDSID(dsid);
		
		return dsid;
	}
	
	public String getXTag(int i){
		try{
			return xtagsV.get(i);
		}catch (Exception e){
			return "?";
		}	
	}
	
	public float getMeanEnergy(){
		return (minE+maxE)/2;
	}
	
	@SuppressWarnings("unchecked")
	public <T extends Record> T getMinERecord(){
		try{
			return (T) recordsV.get(iMinE);
		}catch (Exception e){
			return null;
		}
	}
	
	@SuppressWarnings("unchecked")
	public <T extends Record> T getMaxERecord(){
		try{
			return (T) recordsV.get(iMaxE);
		}catch (Exception e){
			return null;
		}
	}
	
    @SuppressWarnings("unchecked")
    public <T extends Record> T getAverageERecord(){
        return (T) averageRec;
    }
    
	@SuppressWarnings("unchecked")
	public <T extends Record> T getRecordByTag(String xtag){
		try{
			int i=xtagsV.indexOf(xtag.trim());
			return (T) recordsV.get(i);
		}catch (Exception e){
			return null;
		}
	}
	
	@SuppressWarnings("unchecked")
	public <T extends Record> T getRecordByDSID(String dsid0){
		try{
			int i=dsidsV.indexOf(dsid0.trim());
			return (T) recordsV.get(i);
		}catch (Exception e){
			return null;
		}
	}
	
	@SuppressWarnings("unchecked")
	public <T extends Record> Vector<T> recordsV(){return (Vector<T>) recordsV;}
	
	public Vector<String> dsidsV(){return dsidsV;}
	public Vector<String> xtagsV(){return xtagsV;}

	public int nRecords(){return recordsV.size();}
	
	public Vector<RecordGroup> subgroups(){return subgroups;}
	
	public boolean hasSubGroups(){
		if(subgroups!=null && subgroups.size()>0)
			return true;
		
		return false;
	}
	
	
	public void setSubGroups(Vector<RecordGroup> subgroups){
		this.subgroups=subgroups;
	}
	
    //check if the JPI of a level overlaps with any of the group
    //member level's JPIs
    //return false if not overlap with any of the group member's JPI
    public boolean hasOverlapJPI(Level level){
    	if(level==null)
    		return false;
    	
    	String jps=level.JPiS();
    	if(jps.isEmpty())
    		jps=level.altJPiS();

    	
    	/*
    	//debug
    	if(level.ES().contains("4201")) {
    		System.out.println("#### level="+level.ES()+"  L="+level.lS()+" jps="+level.JPiS()+" alt_jps="+level.altJPiS()+" hasOverlapJPI(level.JPiS())="+hasOverlapJPI(jps));
    		for(int i=0;i<recordsV.size();i++) {
    			Level l=(Level)recordsV.get(i);
    			System.out.println("     l in group: E="+l.ES()+" jpi="+l.JPiS()+" alt_jps="+l.altJPiS());
    		}
    	}
    	*/
    	
    	
        return hasOverlapJPI(jps);
    }
    
    public boolean hasOverlapJPI(Vector<String> jsV) {
    	if(jsV==null)
    		return false;
    	
    	String jps="";
    	for(String s:jsV)
    		jps+=s+",";
    	
    	if(jps.endsWith(","))
    		jps=jps.substring(0,jps.length()-1);
    	
    	return hasOverlapJPI(jps);
    }
    
    public boolean hasOverlapJPI(String jps) {
        if(jps==null || recordsV.size()==0 || !(recordsV.get(0) instanceof Level))
            return false;

         if(jps.trim().length()==0)
            return true;

         int n=0;
         int nMatch=0;
         int nNotMatch=0;
         
         boolean hasParityOnly=true;//"+", "-", "(+)", etc, considered it as overlap
         boolean hasNoOverlap=true;
         int nrecords=recordsV.size();
         
         for(int i=0;i<recordsV.size();i++){
            Level lev=(Level)recordsV.get(i);
            
            String s="";
            if(!lev.JPiS().trim().isEmpty())
            	s=lev.JPiS();
            else if(!lev.altJPiS().trim().isEmpty())
            	s=lev.altJPiS();
            else
            	continue;
            
           n++;
               
       	   /*
          	//debug
           float EF=((Level)recordsV.get(0)).EF();
          	if(jps.equals("4+") && Math.abs(EF-3071)<2) {
          		System.out.println(" i="+i+" level="+lev.ES()+"  L="+lev.lS()+" jps="+s+" alt_jps="+lev.altJPiS()+" isOverlapJPI(jps, s)="+EnsdfUtil.isOverlapJPI(jps, s));
          	}
       	    */
           
           //here s is not empty
     	   if(EnsdfUtil.isOverlapJPI(jps, s)) {
     		   nMatch++;
     		   hasNoOverlap=false;
     		   
               String s1=s.replace("(", "").replace(")","").replace("+", "").replace("-", "").trim();
     		   if(!s1.isEmpty()) {
     			   hasParityOnly=false;
     			   
     			   if(nMatch>=(nrecords/3))
     				   return true;     			   
     		   }
     	   }else {
     	       if(recordsV.get(i)==refRecord)
     	           return false;
     	       
     	    
     	       String xtagMarker=Util.getXTagMarker(xtagsV.get(i));
     	       if(!xtagMarker.contains("*") && xtagMarker.contains("?"))
     	           nNotMatch++;
     	   }
         }//end for
         
         /*
      	 //debug
         float EF=((Level)recordsV.get(0)).EF();
        	if(jps.equals("3-") && Math.abs(EF-2998)<10) {
        		System.out.println(" n="+n+"  hasParityOnly="+hasParityOnly+" hasNoOverlap="+hasNoOverlap);
        		
         }
         */	
        	
         if(n==0 || (hasParityOnly&&!hasNoOverlap) || nMatch>nNotMatch)
        	 return true;

         return false;

    }
    
    public boolean hasInconsistentParity(Level lev) {
    	try {
    		String jps=lev.JPiS();
    		if(jps.isEmpty())
    			jps=lev.altJPiS();
    		
    		return hasInconsistentParity(jps);
    	}catch(Exception e) {
    		
    	}
    	
    	return false;
    }
    public boolean hasInconsistentParity(String jps) {
        if(jps==null || recordsV.size()==0 || !(recordsV.get(0) instanceof Level))
            return false;

         jps=jps.replace("(", "").replace(")","").trim();
         if(jps.trim().length()==0 || !EnsdfUtil.isUniqueParity(jps))
            return false;
         
         for(int i=0;i<recordsV.size();i++){
            Level lev=(Level)recordsV.get(i);
            
            String s="";
            if(!lev.JPiS().trim().isEmpty())
            	s=lev.JPiS();
            else if(!lev.altJPiS().trim().isEmpty())
            	s=lev.altJPiS();
            else
            	continue;
            
            s=s.replace("(", "").replace(")","").trim();
            
            if(EnsdfUtil.isUniqueParity(s) && s.charAt(s.length()-1)!=jps.charAt(jps.length()-1))
            	return true;
         }
               
         return false;

    }
    
    //check if the JPI of a level is close(DJ<=DJLimit)  with any of the group
    //member level's JPIs
    //could overlap or not
    public boolean hasCloseJPI(Level level,int DJLimit){
        if(level==null || recordsV.size()==0 || !(recordsV.get(0) instanceof Level))
            return false;

         if(level.JPiS().trim().length()==0 && level.altJPiS().length()==0)
            return true;
 		
         String thisJPiS=level.JPiS();
         if(thisJPiS.isEmpty())
        	 thisJPiS=level.altJPiS();
         
        /*
 		//debug	
 		if(Math.abs(level.EF()-7179.8)<0.1){
 			System.out.println("In RecordGroup line 492: input jps="+thisJPiS+"  e="+level.EF()+" alt jpi="+level.altJPiS()+" DJLimit="+DJLimit+" hasCloseJPI(thisJPiS,DJLimit)="+hasCloseJPI(thisJPiS,DJLimit));
 			for(int i=0;i<recordsV.size();i++){
 	            Level lev=(Level)recordsV.get(i);
 	 			System.out.println("in group: i="+i+"   e="+lev.ES()+" jpi="+lev.JPiS());
 	 			System.out.println("         isCloseJPI="+EnsdfUtil.isCloseJPI(thisJPiS, lev.JPiS(),DJLimit));
 			}
 		}
 		*/
         
         return hasCloseJPI(thisJPiS,DJLimit);
    }
    
    public boolean hasCloseJPI(String jps,int DJLimit){
        if(recordsV.size()==0 || !(recordsV.get(0) instanceof Level))
            return false;

         
        jps=jps.trim(); 
        if(jps.length()==0)
            return true;
 		
         String thisJPiS=jps;
          
         int n=0;
         boolean hasParityOnly=false;
         boolean hasNoClose=false;
         for(int i=0;i<recordsV.size();i++){
            Level lev=(Level)recordsV.get(i);
            
            String s="";
            if(!lev.JPiS().trim().isEmpty())
            	s=lev.JPiS();
            else if(!lev.altJPiS().trim().isEmpty())
            	s=lev.altJPiS();
            else
            	continue;
            
            /*
    		//debug	
    		if(jps.equals("5/2+")) {
            //if(Math.abs(lev.EF()-9175)<1){
    			System.out.println("In RecordGroup line 530: input jps="+jps+" lev.es="+lev.ES()+"  e="+lev.EF()+" jpi="+lev.JPiS()+"  alt jpi="+lev.altJPiS());
    			System.out.println("in group: i="+i+"   e="+lev.ES()+" jpi="+lev.JPiS());
    			System.out.println("         isCloseJPI="+EnsdfUtil.isCloseJPI(thisJPiS, s,DJLimit));
    			System.out.println("#######################################################");
    		}
    		*/
            
        	n++;
        	
            //here s is not empty
      	   if(EnsdfUtil.isCloseJPI(thisJPiS, s,DJLimit)) {
      		   
      		   String s1=s.replace("(", "").replace(")","").replace("+", "").replace("-", "").trim();
      		   if(!s1.isEmpty())
      			   return true;
      		   
      		   //here s could be "+", "-", "(+)", etc, considered it as overlap
      		   hasParityOnly=true;

      	   }else
      		   hasNoClose=true;
         }
         
         
         if(n==0 || (hasParityOnly&&!hasNoClose))
        	 return true;
         
         return false;
    }
    
    public int findNumberOfEmptyJPI() {
        if(recordsV.size()==0 || !(recordsV.get(0) instanceof Level))
            return 0;

        int n=0;
        for(int i=0;i<recordsV.size();i++){
            Level lev=(Level)recordsV.get(i);

            if(lev.JPiS().trim().isEmpty() && lev.altJPiS().trim().isEmpty())
            	n++;
         }
       
         return n;  	
    }
    
    public int findNumberOfInConsistentNonEmptyJPI(Level level,int DJLimit) {
        if(level==null || recordsV.size()==0 || !(recordsV.get(0) instanceof Level))
            return 0;

         if(level.JPiS().trim().length()==0 && level.altJPiS().length()==0)
            return 0;
 		
         String thisJPiS=level.JPiS();
         if(thisJPiS.isEmpty())
        	 thisJPiS=level.altJPiS();  
         
         return findNumberOfInConsistentNonEmptyJPI(thisJPiS,DJLimit);
    }
    
    public int findNumberOfInConsistentNonEmptyJPI(String jps,int DJLimit) {
        if(jps==null || jps.trim().isEmpty() || recordsV.size()==0 || !(recordsV.get(0) instanceof Level))
            return 0;

        jps=jps.trim(); 
        jps=jps.replace("(", "").replace(")","");
 		      
        String thisJPiS=jps;
          
        int n=0;
        for(int i=0;i<recordsV.size();i++){
            Level lev=(Level)recordsV.get(i);
            
            String s="";
            if(!lev.JPiS().trim().isEmpty())
            	s=lev.JPiS();
            else if(!lev.altJPiS().trim().isEmpty())
            	s=lev.altJPiS();
            else
            	continue;
            
            /*
    		//debug	
    		if(jps.equals("5/2+")) {
            //if(Math.abs(lev.EF()-9175)<1){
    			System.out.println("In RecordGroup line 530: input jps="+jps+" lev.es="+lev.ES()+"  e="+lev.EF()+" jpi="+lev.JPiS()+"  alt jpi="+lev.altJPiS());
    			System.out.println("in group: i="+i+"   e="+lev.ES()+" jpi="+lev.JPiS());
    			System.out.println("         isCloseJPI="+EnsdfUtil.isCloseJPI(thisJPiS, s,DJLimit));
    			System.out.println("#######################################################");
    		}
    		*/
        	
            //here s is not empty
      	   if(!EnsdfUtil.isCloseJPI(thisJPiS, s,DJLimit)) 
      		   n++;

         }
       
         return n;  	
    }
    
    public int findNumberOfConsistentNonEmptyJPI(Level level,int DJLimit) {
        if(level==null || recordsV.size()==0 || !(recordsV.get(0) instanceof Level))
            return 0;
 		
         String thisJPiS=level.JPiS();
         if(thisJPiS.isEmpty())
        	 thisJPiS=level.altJPiS();  
         
         return findNumberOfConsistentNonEmptyJPI(thisJPiS,DJLimit);
    }
    
    public int findNumberOfConsistentNonEmptyJPI(String jps,int DJLimit) {
        if(jps==null || recordsV.size()==0 || !(recordsV.get(0) instanceof Level))
            return 0;

        jps=jps.trim(); 
        jps=jps.replace("(", "").replace(")","");
 		
        int n=0;
        if(jps.trim().isEmpty()) {
        	n=findNumberOfEmptyJPI();
        	return recordsV.size()-n;
        }
        
        String thisJPiS=jps;
          
      
        for(int i=0;i<recordsV.size();i++){
            Level lev=(Level)recordsV.get(i);
            
            String s="";
            if(!lev.JPiS().trim().isEmpty())
            	s=lev.JPiS();
            else if(!lev.altJPiS().trim().isEmpty())
            	s=lev.altJPiS();
            else
            	continue;
            
            /*
    		//debug	
    		if(jps.equals("5/2+")) {
            //if(Math.abs(lev.EF()-9175)<1){
    			System.out.println("In RecordGroup line 530: input jps="+jps+" lev.es="+lev.ES()+"  e="+lev.EF()+" jpi="+lev.JPiS()+"  alt jpi="+lev.altJPiS());
    			System.out.println("in group: i="+i+"   e="+lev.ES()+" jpi="+lev.JPiS());
    			System.out.println("         isCloseJPI="+EnsdfUtil.isCloseJPI(thisJPiS, s,DJLimit));
    			System.out.println("#######################################################");
    		}
    		*/
        	
            //here s is not empty
      	   if(EnsdfUtil.isCloseJPI(thisJPiS, s,DJLimit)) 
      		   n++;

         }

         
         return n;    	
    }
    
	public <T extends Record>boolean hasComparableEnergyEntry(T r,float deltaE){
        return hasComparableEnergyEntry(r,deltaE,false);
    }
	
	public <T extends Record>boolean hasComparableEnergyEntry(T r){
        return hasComparableEnergyEntry(r,-1);
    }
	
    //check if the energy of a level is comparable with any level in
    //the group
    //an energy entry is a pair of energy value and uncertainty
	@SuppressWarnings("unchecked")
	public <T extends Record>boolean hasComparableEnergyEntry(T r,float deltaE,boolean forceDeltaE){
        if(r==null || recordsV.size()==0)
           return false;

        /*
        //condition 1:
        int n=0;
        for(int i=0;i<recordsV.size();i++){
           T rec=(T)recordsV.get(i);
           
 
	       //debug
           //if(Math.abs(r.EF()-2068.7)<0.1 && (r instanceof Level)){
	       //	System.out.println("In RecordGroup line 349: es="+r.ES()+"  e="+r.EF()+"  matched="+EnsdfUtil.isComparableEnergyEntry(rec,r,deltaE,forceDeltaE));
		   //   System.out.println("            group member: es="+rec.ES()+"  e="+rec.EF());	
		   //   System.out.println("  "+rec.ES().contains(".")+"  "+r.ES().contains(".")+"  "+(Math.abs(rec.EF()-r.EF())<0.1)+"  "+Math.abs(rec.EF()-r.EF()));
	       //}
           
           if(EnsdfUtil.isComparableEnergyEntry(rec,r,deltaE,forceDeltaE)){
              n++;
              
              if(rec.ES().contains(".") && r.ES().contains(".") && Math.abs(rec.EF()-r.EF())<0.1)
            	  return true;
           }
        }            
        if(n<(recordsV.size()+1)/2)
        	return false;
        */
        //condition 2:
        if(Str.isNumeric(r.ES())){
            if(averageRec==null)
            	averageRec=(T) calculateAverageEnergy();       
            
            
            /*
  	        //debug
            if((Math.abs(r.EF()-7482)<1||Math.abs(r.EF()-7475)<1) && (r instanceof Level)){
            //if(r.ES().toUpperCase().contains("3620")){
  	           System.out.println("In RecordGroup line 833: es="+r.ES()+" de="+r.DES()+" ef="+r.EF()+"  matched="+EnsdfUtil.isComparableEnergyEntry(averageRec,r,deltaE,forceDeltaE));
  		       System.out.println("           averageRec="+averageRec.EF()+" "+averageRec.DEF()+" deltaE="+deltaE+" forceDeltaE="+forceDeltaE+" averageRec.ERPF="+averageRec.ERPF());	
  		       for(int i=0;i<recordsV.size();i++)
  		    	   System.out.println("        record E="+((Record)recordsV.get(i)).ES()+" de="+((Record)recordsV.get(i)).DES()+" tag="+xtagsV.get(i));
            }
            */
            
            if(!EnsdfUtil.isComparableEnergyEntry(averageRec,r,deltaE,forceDeltaE))
            	return false;
            
        }else if(r instanceof Level && ((Level)r).NNES().length()>0){
        	Level lev=(Level)r;
        	
        	//if(lev.ES().indexOf("0+")==0)
        	//	System.out.println("****"+lev.ES()+" lev.NNES="+lev.NNES()+"  lev.NES="+lev.NES()+" xerf="+lev.XREFS());

        	try{
            	for(int i=0;i<recordsV.size();i++){
            		Level l=(Level)recordsV.get(i);
            		
            		/*
          	        //debug
                    if(r.ES().toUpperCase().contains("1233")){
          	           System.out.println("In RecordGroup line 391: es="+r.ES()+"  e="+r.ERPF()+"  matched="+EnsdfUtil.isComparableEnergyEntry(l,r,deltaE,forceDeltaE));
          		       System.out.println("           i="+i+" group member: es="+l.ES()+"  e="+l.ERPF());	
          		       System.out.println("  "+l.ES().contains(".")+"  "+r.ES().contains(".")+"  "+(Math.abs(l.EF()-r.EF())<0.1)+"  "+Math.abs(l.EF()-r.EF()));
          	        }
                    */
 
            		if(lev.ES().equals(l.ES()))
            			return true;
            		
               		boolean isComparableE=EnsdfUtil.isComparableEnergyEntry(lev, l, deltaE, forceDeltaE);
            		if(isComparableE) {
            			if(lev.isTrueERPF()&&l.isTrueERPF())
            				return true;
            			else if(!lev.isTrueERPF()&&!l.isTrueERPF()) {
            				if(lev.NNES().equals(l.NNES()) && Math.abs(lev.ERF()-l.ERF())<=0.5 )//see also in EnsdfGroup.java: findIndexesOfPossibleGroups()
								return true;
            			}
            		}
            		
             	} 
            }catch(Exception e){}
            
            return false;

        }

     
        return true;
    }
    
	@SuppressWarnings("unchecked")
	public <T extends Record>boolean hasComparableEnergyEntry1(T r,float deltaE,boolean forceDeltaE){
        if(r==null || recordsV.size()==0)
           return false;
        
        if(averageRec==null)
        	averageRec=(T) calculateAverageEnergy();

        //debug
        //System.out.println("*** avg="+averageRec.EF()+" "+averageRec.DEF()+"   rec="+r.EF()+"  "+r.DEF());
        
        if(!EnsdfUtil.isComparableEnergyEntry(averageRec,r,deltaE,forceDeltaE))
        	return false;
             
        return true;
    }
	
	//by default, include adopted record
	public <T extends Record> Record calculateAverageEnergy(){
	    return calculateAverageEnergy(true);
	}
	@SuppressWarnings("unchecked")
	public <T extends Record> Record calculateAverageEnergy(boolean includedAdopted){
		try{
			String s="",ds="";
			String line="";		
		
			T rec=null;
			if(refRecord!=null)
				rec=(T)refRecord;
			else
				rec=(T)recordsV.get(0);	
			
			line=rec.recordLine();
			
			if(recordsV.size()==1)
				return (T) recordsV.get(0);
				
			Vector<DataPoint> dpsV=new Vector<DataPoint>();
			Vector<DataPoint> dpsAll=new Vector<DataPoint>();
			double x=-1,dx=-1,minDX=100000;
			
			//debug
			//System.out.println("recordsV.size()="+recordsV.size());
			
			Vector<T> tempV=new Vector<T>();
			
			for(int i=0;i<recordsV.size();i++){
				try{
					rec=(T)recordsV.get(i);	
					if(dsidsV.get(i).contains("ADOPTED") && !includedAdopted) 				        
					    continue;
					
					
					tempV.add(rec);
					
					s=rec.ES();
					ds=rec.DES();

					if(Str.isNumeric(s))
					    x=Float.parseFloat(s);
					else
					    x=rec.ERPF();
					
					if(!Str.isNumeric(ds)){
						dx=EnsdfUtil.findDefaultEnergyUncertainty(rec);
					}else{
						//convert ENSDF-style uncertainty string to real value 
						dx=(double) EnsdfUtil.s2x(s,ds).dxu();
					}
					
					//System.out.println(" i="+i+" s="+s+"  ds="+ds+" x="+x+" dx="+dx);
					
					if(x==0)
						dx=0.01;
					
					if(dx<=0)
						continue;
		    		
					
					String xtagMarker=Util.getXTagMarker(xtagsV.get(i));
					
					if(xtagMarker.contains("*") || xtagMarker.contains("?") || rec.q().contains("?")) {
					    dx=5*dx;
					    
					    double dxREF=EnsdfUtil.findDefaultEnergyUncertainty(refRecord);
					    if(dx<dxREF)
					        dx=dxREF;
					}
					
                    DataPoint dp=new DataPoint(x,dx);
                    dp.setS(s,ds);					
					if(xtagMarker.contains("?")) {
					    dpsAll.add(dp);
					    continue;
					}
					
					if(dx<minDX)
						minDX=dx;

					dpsV.add(dp);
					dpsAll.add(dp);
					
				}catch(NumberFormatException e){
					continue;
				}
			}
			
			if(tempV.size()==1) 
			    return tempV.get(0);
			
            if(recordsV.size()==2 && adopted==null) {
                T r1=(T) recordsV.get(0);
                T r2=(T) recordsV.get(1);
                if(r1==r2)
                    return r1;
            }
            
            
            Average avg=null;
            if(dpsV.size()==0) {
                if(adopted!=null)
                    return adopted;
                
                avg=new Average(dpsAll);
            }else
                avg=new Average(dpsV);
            
			x=avg.value();
			dx=Math.max(avg.intError(),avg.extError());
			dx=Math.max(dx,minDX);
			
			/*
            //debug
            if(refRecord.ES().contains("7010")) {
                System.out.println("RecordGroup 740: temp.size="+tempV.size()+" dps.size="+dpsV.size()+"  dpsAll size="+dpsAll.size()+" avg="+x+" dx="+dx+"   dp(0)"+dpsV.get(0).x());
                for(int i=0;i<recordsV.size();i++) {
                    T r=(T)recordsV.get(i);
                    System.out.println("  i="+i+"  E="+r.ES()+"   dsid="+dsidsV.get(i)+"  xtag="+xtagsV.get(i));
                }
            }
            */
			
			return EnsdfUtil.makeRecordWithNewEnergy(line, x, dx);
		}catch(Exception e){
			e.printStackTrace();
		}
		
		return null;
	}

    
    public boolean isGammasConsistent(Level lev,float deltaEG,boolean forceDelta){
        int ng1=lev.nGammas();	
        if(lev==null || ng1==0 || recordsV.size()==0 || !(recordsV.get(0) instanceof Level))
            return false;
        
        int nRecMatch=0,nTotal=0;
        int ng2=0,ngMatch=0,ngMax=0,ngMatchMax=0;
        for(int i=0;i<recordsV.size();i++){
        	Level l=(Level)recordsV.get(i);
        	ng2=l.nGammas();
            if(ng2==0)
            	continue;
            
            if(ng2>ngMax)
            	ngMax=ng2;
            
            
            nTotal++;
            
            
            Gamma[][] matchGV=EnsdfUtil.findConsistentGammas(lev, l, deltaEG, forceDelta);
            ngMatch=matchGV[0].length;

            /*
    		//debug		
    		if(lev.ES().equals("3276.0")) {
            //if(Math.abs(lev.EF()-1096.1)<0.01){
    			System.out.println("In RecordGroup line974: lev.es="+lev.ES()+" jpi="+lev.JPiS()+" deltaEG="+deltaEG+" forceDelta="+forceDelta); 
    			
    			System.out.println("     level first gamma lev1 eg="+lev.gammaAt(0).ES()+"  lev2 eg="+l.gammaAt(0).ES());
    			System.out.println("     record level i="+i+"   e="+l.ES()+" jpi="+l.JPiS()+" dsid="+dsidsV.get(i));
    			//System.out.println("                isGammasConsistent="+EnsdfUtil.isGammasConsistent(lev,l,deltaEG,forceDelta));
    			System.out.println("          ngMatch="+ngMatch+" ng1="+ng1+" ng2="+ng2+" ngMax="+ngMax+" ngMatchMax="+ngMatchMax+" nTotal="+nTotal);
    		}
    		*/
            
        	boolean isGood=true;
        	if(ngMatch==1 && (ng1>1||ng2>1)) {
        		Gamma g1=matchGV[0][0];
        		Gamma g2=matchGV[1][0];
        		
        		/*
        		//debug		
                if(lev.ES().equals("3276.0")) {
        		//if(Math.abs(lev.EF()-1096.1)<0.01){
        			System.out.println("In RecordGroup line 991: g1="+g1.ES()+" g1 RI="+g1.RID()+" g2="+g2.ES()+" g2 RI="+g2.RID());
        			System.out.println("   nRecMatch="+nRecMatch+"  nTotal="+nTotal+" ngMatch="+ngMatch+" n1="+ng1+" n2="+ng2+" isGood="+isGood);
        		}
                */
        		
        		if(g1.RID()>0) {
            		for(Gamma g:lev.GammasV()) {
            			if(g!=g1 && g.RID()>1.5*g1.RID() && g.q().isEmpty()) {
            				isGood=false;
            				break;
            			}
            		}
        		}
         
        		/*
        		//debug		
                if(lev.ES().equals("3276.0")) {
        		//if(Math.abs(lev.EF()-1096.1)<0.01){
        			System.out.println("In RecordGroup line 1009: g1="+g1.ES()+" g1 RI="+g1.RID()+" g2="+g2.ES()+" g2 RI="+g2.RID());
        			System.out.println("   nRecMatch="+nRecMatch+"  nTotal="+nTotal+" ngMatch="+ngMatch+" n1="+ng1+" n2="+ng2+" isGood="+isGood);
        		}
                */
        		
        		if(g2.RID()>0) {
            		for(Gamma g:l.GammasV()) {
            			if(g!=g2 && g.RID()>1.5*g2.RID() && g.q().isEmpty()) {
            				isGood=false;
            				break;
            			}
            		}
        		}
        	}
            
        	/*
    		//debug		
            if(lev.ES().equals("3276.0")) {
    		//if(Math.abs(lev.EF()-1096.1)<0.01){
    			System.out.println("In RecordGroup line 1028: lev.es="+lev.ES()+"  e="+lev.EF()+" jpi="+lev.JPiS()+" deltaEG="+deltaEG+" forceDelta="+forceDelta);
    			System.out.println("   nRecMatch="+nRecMatch+"  nTotal="+nTotal+" ngMatch="+ngMatch+" n1="+ng1+" n2="+ng2+" isGood="+isGood);
    		}
            */
        	
        	if(!isGood)
        		continue;
            
            if(ngMatch>=Math.ceil(ng1*2.0/3)) {//return true if any level in the group has # of matching gammas greater equal to 2/3 of gammas in lev  
            	return true;
            }

            
            //a loosened condition for finding match
            if(ngMatch>=(Math.min(ng1,ng2)+1)/2){//same as EnsdfUtil.isGammaConsistent()
            	nRecMatch++;
            	if(ngMatch>ngMatchMax)
            		ngMatchMax=ngMatch;
            }
        }
        
        /*
		//debug		
        if(lev.ES().equals("3276.0")) {
		//if(Math.abs(lev.EF()-1096.1)<0.01){
			System.out.println("In RecordGroup line 1045: lev.es="+lev.ES()+"  e="+lev.EF()+" jpi="+lev.JPiS()+" deltaEG="+deltaEG+" forceDelta="+forceDelta);
			System.out.println("   nRecMatch="+nRecMatch+"  nTotal="+nTotal);
		}
		*/
        
        if(nRecMatch==0 || nRecMatch<(nTotal+1)/2)
        	return false;
        	
        return true;
    }
    
    /*
     * find the average distance of the given record to the record group
     * weight=1/(DE*DE), if DE is empty, assume DE=0.2*E
     */
	@SuppressWarnings("unchecked")
    public <T extends Record> float findAverageDistanceToGroup(T rec){
        if(rec==null || recordsV.size()==0)
            return -1;
        
        
        float dist=0;
        float wt=0;//total weight
        for(int i=0;i<recordsV.size();i++){
        	T r=(T)recordsV.get(i);
            if(r==null || r.EF()==0)
            	continue;
            
            float x=r.ERPF();
            float dx=r.DERPF();
            
            if(r.DES().isEmpty() || dx<=0)
            	dx=(float)Math.abs(x*0.2);

            float w=1.0f/(dx*dx);
            float diff=Math.abs(rec.ERPF()-x);
            
            dist+=w*diff;
            wt+=w;
            
            //if(Math.abs(rec.EF()-3464)<0.1 && Math.abs(minE-3500)<10)
            //	System.out.println(" i="+i+"  dist="+dist+" wt="+wt+" diff="+diff+"  Er="+x+" w="+w);
        }
        
        //if(Math.abs(rec.EF()-3464)<0.1 && Math.abs(minE-3500)<10)
        //	System.out.println(" dist="+dist+" wt="+wt+" dist/wt="+dist/wt);
        
        if(wt>0)
        	return dist/wt;
        else
        	return -1;
        
    }
	
    
	@SuppressWarnings("unchecked")
    public <T extends Record> int findNumberOfConsistentEnergies(T rec,float deltaE,boolean forceDelta){
        if(rec==null || recordsV.size()==0)
            return 0;
        
        int nMatch=0;
        for(int i=0;i<recordsV.size();i++){
        	T r=(T)recordsV.get(i);
            if(r==null)
            	continue;
            
            /*
    		//debug		
    		if(Math.abs(lev.EF()-1632.54)<0.2){
    			System.out.println("In RecordGroup line 346: lev.es="+lev.ES()+"  e="+lev.EF()+" jpi="+lev.JPiS()+" deltaEG="+deltaEG+" forceDelta="+forceDelta);
    			System.out.println("   record i="+i+"   e="+((Level)recordsV.get(i)).ES());
    			System.out.println("                isEnergyMatched="+EnsdfUtil.isGammasConsistent(lev,l,deltaEG,forceDelta));
    		}
    		*/
            
           if(EnsdfUtil.isComparableEnergyEntry(rec, r, deltaE, forceDelta))
        	   nMatch++;

        }
        
        return nMatch;
    }
    
    public int findMaxNumberOfConsistentGammas(Level lev,float deltaEG,boolean forceDelta){
        if(lev==null || lev.nGammas()==0 || recordsV.size()==0 || !(recordsV.get(0) instanceof Level))
            return 0;
        
        int nMatch=0,nMatchMax=0;
        for(int i=0;i<recordsV.size();i++){
        	Level l=(Level)recordsV.get(i);
            if(l.nGammas()==0)
            	continue;
            
            /*
    		//debug		
    		if(Math.abs(lev.EF()-1632.54)<0.2){
    			System.out.println("In RecordGroup line 346: lev.es="+lev.ES()+"  e="+lev.EF()+" jpi="+lev.JPiS()+" deltaEG="+deltaEG+" forceDelta="+forceDelta);
    			System.out.println("   record i="+i+"   e="+((Level)recordsV.get(i)).ES());
    			System.out.println("                isEnergyMatched="+EnsdfUtil.isGammasConsistent(lev,l,deltaEG,forceDelta));
    		}
    		*/
            
            nMatch=EnsdfUtil.findNumberOfConsistentGammas(lev,l,deltaEG,forceDelta);
            if(nMatch>nMatchMax)
            	nMatchMax=nMatch;
        }
        
        return nMatchMax;
    }
    
    public int findNumberOfMatchedJPI(Level lev){
        if(lev==null || lev.JPiS().isEmpty() || recordsV.size()==0 || !(recordsV.get(0) instanceof Level))
            return 0;
        
        String js1=lev.JPiS().trim();
        js1=js1.replace("(", "").replace(")","");
        
        int nMatch=0;
        for(int i=0;i<recordsV.size();i++){
        	Level l=(Level)recordsV.get(i);
            String js2=l.JPiS().trim();
            if(js2.isEmpty())
            	continue;
            
            /*
    		//debug		
    		if(Math.abs(lev.EF()-1632.54)<0.2){
    			System.out.println("In RecordGroup line 346: lev.es="+lev.ES()+"  e="+lev.EF()+" jpi="+lev.JPiS()+" deltaEG="+deltaEG+" forceDelta="+forceDelta);
    			System.out.println("   record i="+i+"   e="+((Level)recordsV.get(i)).ES());
    			System.out.println("                isEnergyMatched="+EnsdfUtil.isGammasConsistent(lev,l,deltaEG,forceDelta));
    		}
    		*/
            
            if(js1.equals(js2))
            	nMatch++;

        }
        
        return nMatch;
    }
    
    public Level findLevelWithMinNonZeroNGammas(){
    	try {
            if(recordsV.size()==0 || !(recordsV.get(0) instanceof Level))
                return null;
            
            int minNG=1000;
            Level foundLevel=null;
            for(int i=0;i<recordsV.size();i++){
            	Level l=(Level)recordsV.get(i);
                int ng=l.nGammas();
            	if(ng==0)
                	continue;
                if(ng<minNG) {
                	minNG=ng;
                	foundLevel=l;
                }
            }
            
            return foundLevel;
    	}catch(Exception e) {
    		
    	}
    	return null;
    }
    
    public Level findLevelWithMaxNonZeroNGammas(){
    	try {
            if(recordsV.size()==0 || !(recordsV.get(0) instanceof Level))
                return null;
            
            int maxNG=0;
            Level foundLevel=null;
            for(int i=0;i<recordsV.size();i++){
            	Level l=(Level)recordsV.get(i);
                int ng=l.nGammas();
            	if(ng==0)
                	continue;
                if(ng>maxNG) {
                	maxNG=ng;
                	foundLevel=l;
                }
            }
            
            return foundLevel;
    	}catch(Exception e) {
    		
    	}
    	return null;
    }
    
    public boolean hasAnyConsistentGamma(Level lev,float deltaEG,boolean forceDelta){
        if(lev==null || lev.nGammas()==0 || recordsV.size()==0 || !(recordsV.get(0) instanceof Level))
            return false;
        
        int nMatch=0;
        for(int i=0;i<recordsV.size();i++){
        	Level l=(Level)recordsV.get(i);
            if(l.nGammas()==0)
            	continue;
            
            nMatch=EnsdfUtil.findNumberOfConsistentGammas(lev,l,deltaEG,forceDelta);
            if(nMatch>0)
            	return true;
        }
        
        return false;
    }
    
    public boolean hasLevelGamma(){
        if(recordsV.size()==0 || !(recordsV.get(0) instanceof Level))
            return false;

        for(int i=0;i<recordsV.size();i++){
        	Level l=(Level)recordsV.get(i);
            if(l.nGammas()>0)
            	return true;
        }
        
        
        return false;
    }
    
    public  <T extends Record> MatchingStrength findMatchingStengthOfRecord(T rec,float deltaE){
		if(rec instanceof Level)
			return findMatchingStrengthOfLevel((Level) rec,deltaE);
		else
			return findMatchingStrengthOfGamma((Gamma) rec,deltaE);
		
    }
    
    public  <T extends Record> boolean hasMatchedRecord(T rec,float deltaE){
		if(rec instanceof Level)
			return haveMatchedLevel((Level) rec,deltaE);
		else
			return haveMatchedGamma((Gamma) rec,deltaE);
		
    }
    
    @SuppressWarnings("unchecked")
	public  <T extends Record> boolean hasRecordField(String recFieldName){
        if(recordsV.size()==0)
            return false;
        
        String s="";
		if((recordsV.get(0) instanceof Level)){
			if(recFieldName.toUpperCase().equals("E"))
				for(Level r:(Vector<Level>)recordsV) s+=r.ES().trim();
			else if(recFieldName.toUpperCase().equals("J"))
				for(Level r:(Vector<Level>)recordsV) s+=r.JPiS().trim();
			else if(recFieldName.toUpperCase().equals("T"))
				for(Level r:(Vector<Level>)recordsV) s+=r.halflife().trim();
			else if(recFieldName.toUpperCase().equals("L"))
				for(Level r:(Vector<Level>)recordsV) s+=r.lS().trim();
			else if(recFieldName.toUpperCase().equals("S"))
				for(Level r:(Vector<Level>)recordsV) s+=r.sS().trim();
			
		}else if((recordsV.get(0) instanceof Gamma)){
			if(recFieldName.toUpperCase().equals("E"))
				for(Gamma r:(Vector<Gamma>)recordsV) s+=r.ES().trim();
			else if(recFieldName.toUpperCase().equals("RI"))
				for(Gamma r:(Vector<Gamma>)recordsV) s+=r.RIS().trim();
			else if(recFieldName.toUpperCase().equals("M"))
				for(Gamma r:(Vector<Gamma>)recordsV) s+=r.MS().trim();
			else if(recFieldName.toUpperCase().equals("MR"))
				for(Gamma r:(Vector<Gamma>)recordsV) s+=r.MRS().trim();
			else if(recFieldName.toUpperCase().equals("CC"))
				for(Gamma r:(Vector<Gamma>)recordsV) s+=r.CCS().trim();
			else if(recFieldName.toUpperCase().equals("TI"))
				for(Gamma r:(Vector<Gamma>)recordsV) s+=r.TIS().trim();
		}
		
		return !s.isEmpty();
    }
    
    @SuppressWarnings("unused")
	private  <T extends Record> boolean isAllJPIsEmpty(){
    	try{
        	if(recordsV.get(0) instanceof Level){
            	for(int i=0;i<recordsV.size();i++){
            		Level l=(Level)recordsV.get(i);
            		if(!isEmptyJPI(l))
            			return false;
            	}	
        	}

    	}catch(Exception e){}
    	
    	return true;
    }
    
    private boolean isEmptyJPI(Level lev) {
    	try {
    		if(lev.JPiS().length()>0 || lev.altJPiS().length()>0)
    			return false;
    	}catch(Exception e) {}
    	
    	return true;
    }
    ///////////////////////////////////////////////////////////
    //This is the method to determine the grouping of levels
    //////////////////////////////////////////////////////////
    private  <T extends Record> boolean haveMatchedLevel(Level lev,float deltaEL){
    	MatchingStrength matchingStrength=findMatchingStrengthOfLevel(lev,deltaEL);
    	/*
    	if(lev.ES().equals("461") ) {
    	//if((lev.ES().equals("7475")||lev.ES().equals("7482")) && deltaEL==-1) {
    	    System.out.println("RecordGroup 1451: ES="+lev.ES()+" matchingStrength="+matchingStrength.strength+" deltaEL="+deltaEL);
    	    for(int i=0;i<recordsV().size();i++) {
    	        Record r=(Record) recordsV.get(i);
    	    	System.out.println("   record in group:"+r.ES()+"  "+r.DES()+" dsid="+this.dsidsV.get(i));
    	    }
    	}
    	*/
    	
    	if(matchingStrength.strength>0)
    		return true;
    	
    	return false;
    }
    private  <T extends Record> MatchingStrength findMatchingStrengthOfLevel(Level lev,float deltaEL){
		boolean isEnergyMatched=hasComparableEnergyEntry(lev,deltaEL);
		boolean isJPIMatched=hasOverlapJPI(lev);
		boolean hasInconsistentParity=hasInconsistentParity(lev);
		
		MatchingStrength matchingStrength=new MatchingStrength(-1);

		//boolean bothNoEmptyJPI=(!isAllJPIsEmpty() && !isEmptyJPI(lev));
		
		
		/*
		//debug
		//boolean debug=false;
		//if(lev.ES().equals("5730") && lev.JPiS().equals("3/2")) {
		//if((lev.ES().equals("9447.1")||lev.ES().equals("7475")) && deltaEL==-1) {
		//if(lev.ES().equals("7482") && lev.DES().equals("6")) {	
	    if(Math.abs(lev.EF()-5714)<0.5){    
			//debug=true;
			System.out.println("*****RecordGroup 1535:  E="+lev.ES()+" JPI="+lev.JPiS()+" altJS="+lev.altJPiS()+" deltaEL="+deltaEL+"  deltaEG="+CheckControl.deltaEG);
			System.out.println("  energy match="+isEnergyMatched+"  JPI match="+isJPIMatched+" gamma consitent="+isGammasConsistent(lev,CheckControl.deltaEG,true));
			System.out.println("  has comparable Energy entry="+hasComparableEnergyEntry(lev,0.5f,true)+"  any consistent gamma="+hasAnyConsistentGamma(lev, 0.5f, true)
			 +" hasCloseJPI(lev,1)="+hasCloseJPI(lev,1));
			if(lev.nGammas()>0) System.out.println("   lev first gamma="+lev.gammaAt(0).ES());
			for(int i=0;i<recordsV.size();i++) {
			    Level l=(Level)recordsV.get(i);
				System.out.println("  in group: i="+i+"   e="+l.ES()+" J="+l.JPiS()+" altJS="+l.altJPiS()+" dsid="+this.dsidsV.get(i)+"  isReferenceRecord="+(recordsV.get(i)==refRecord));
			}
		}
		*/
		
		if(isEnergyMatched && isJPIMatched){
			//both energy and JPI match
			if(lev.nGammas()>0 && hasLevelGamma()){			    
			 
			    float deltaEG=CheckControl.deltaEG;

			    boolean isAllDEGEmpty=true;
			    boolean hasSingleRoughEG=false;
			    for(Gamma g:lev.GammasV()) {
			        if(g.DEF()>0 || g.ES().contains("."))
			            isAllDEGEmpty=false;
			        
			        if(g.DEF()>deltaEG)
			            deltaEG=g.DEF();
			    }
			    
			    if(isAllDEGEmpty) {
			        if(lev.DEF()>deltaEG)
			            deltaEG=lev.DEF();
			        
			        float del=deltaEG;
			        if(lev.DES().isEmpty() && !lev.ES().contains(".") && lev.ES().endsWith("0")) {
			            del=EnsdfUtil.findDefaultEnergyUncertainty(lev);
	                     
                        if(lev.nGammas()==1 && lev.gammaAt(0).ES().endsWith("00"))  
                            hasSingleRoughEG=true;
			        }
			        
			        if(del>deltaEG) {
			            deltaEG=del;
		                
			            if(hasSingleRoughEG) {
			                deltaEG=2*deltaEG;
			            }
			        }
			        

			    }
			    
			     
				if(!isGammasConsistent(lev,deltaEG,true)){
					//isGammasConsistent=false if more than half of the gammas of the level don't have matches in any of 
					// level in the current record group
					
					//level energy and JPI match, but no overlapping gammas
					//if the two level energies exactly match, still consider them as the same level, otherwise not

					if(hasComparableEnergyEntry(lev,0.5f,true) && hasAnyConsistentGamma(lev, 0.5f, true)) {
						matchingStrength=new MatchingStrength(2);
					}else if(hasCloseJPI(lev, 0)) {
						int n=findMaxNumberOfConsistentGammas(lev, deltaEG, true);
						if(n==lev.nGammas()) 
							matchingStrength=new MatchingStrength(2);
						else {
							Level l=findLevelWithMinNonZeroNGammas();
							if(l!=null && n==l.nGammas())
								matchingStrength=new MatchingStrength(2);
						}
					}
				}else {//further check, even if there are matching gammas, since Control.deltaEG could be a too large range
					//TO DO: further check
					int n=findMaxNumberOfConsistentGammas(lev, deltaEG, true);
					float dist=findAverageDistanceToGroup(lev);

					matchingStrength=new MatchingStrength(1);
					if(n==1 && !hasSingleRoughEG) {
					    
	                    float tempDelta=deltaEL;
	                    String es=lev.ES();
	                    if(!es.contains("E")){
	                        if(es.contains("."))
	                            tempDelta=2.0f;
	                        else
	                            tempDelta=5.0f;
	                    }
	                    
	                    float deltaE=Math.max(tempDelta, 2.0f*dist);
	                    boolean hasComparableEL=hasComparableEnergyEntry(lev,deltaE,true);
	                    
					    if(lev.nGammas()==1 && hasComparableEL && ((Level)refRecord).nGammas()==1) {
					        //good match, do nothing
					    }else if(!isGammasConsistent(lev,deltaE,true)) {
					    	if(hasComparableEL) {
						    	if(lev.nGammas()==1) {
						    		matchingStrength=new MatchingStrength(3);
							    }else if(isGammasConsistent(lev,5,true)) {
							    	matchingStrength=new MatchingStrength(4);
							    }else if(isGammasConsistent(lev,10,true)) {
							    	matchingStrength=new MatchingStrength(5);
							    }else if(isGammasConsistent(lev,15,true) || isGammasConsistent(lev,deltaE,false)) {
							    	matchingStrength=new MatchingStrength(6);
							    }else {
							    	matchingStrength=new MatchingStrength(-1);
							    }
					    	}else {
						    	if(lev.nGammas()==1) {
						    		matchingStrength=new MatchingStrength(5);
							    }else if(isGammasConsistent(lev,5,true)) {
							    	matchingStrength=new MatchingStrength(5);
							    }else if(isGammasConsistent(lev,10,true)) {
							    	matchingStrength=new MatchingStrength(6);
							    }else if(isGammasConsistent(lev,15,true) || isGammasConsistent(lev,deltaE,false)) {
							    	matchingStrength=new MatchingStrength(7);
							    }else {
							    	matchingStrength=new MatchingStrength(-1);
							    }
					    	}

					    }
					    
					    /*
	                    //debug
					    if(lev.ES().equals("4213") && lev.DES().equals("")) {	
	                    //if(lev.ES().contains("12386") && lev.nGammas()>0) {
	                        System.out.println("*****RecordGroup 1482: E="+lev.ES()+" n="+n+" dist="+dist+" deltEG="+deltaE+" isGammasConsistent(lev,deltaE,true)="+isGammasConsistent(lev,deltaE,true));
	                        System.out.println("             subgroups.size()="+subgroups.size()+" hasComparableEnergyEntry(lev,deltaE,true)="+hasComparableEnergyEntry(lev,deltaE,true)+
	                        		" hasSubGroup="+this.hasSubGroups()+" isMatch="+(matchingStrength.strength>0));
	                        if(lev.nGammas()>0) System.out.println("   lev first gamma="+lev.gammaAt(0).ES());
	                        for(int i=0;i<recordsV.size();i++) {
	                            Level l=(Level)recordsV.get(i);
	                            System.out.println("  in group: i="+i+"   e="+l.ES()+" J="+l.JPiS()+" dsid="+this.dsidsV.get(i));
	                        }                       
	                    }
	                    */
	                    
					    
					}
	
				}
				
                /*
                //debug
				if(lev.ES().equals("1567") && lev.DES().equals("2")) {
                //if(Math.abs(lev.EF()-4430)<0.2 && lev.nGammas()>0){    
                    //debug=true;
                    System.out.println("*****RecordGroup 1411:  E="+lev.ES()+"  deltaEL="+deltaEL+"  deltaEG="+deltaEG+" "+isEnergyMatched+"  "+isJPIMatched+" "+isGammasConsistent(lev,deltaEG,true));
                    System.out.println("   matched="+matched+"   "+hasComparableEnergyEntry(lev,0.5f,true)+"   "+hasAnyConsistentGamma(lev, 0.5f, true));
                    if(lev.nGammas()>0) System.out.println("   lev first gamma="+lev.gammaAt(0).ES());
                    for(int i=0;i<recordsV.size();i++) {
                        Level l=(Level)recordsV.get(i);
                        System.out.println("  in group: i="+i+"   e="+l.ES()+" J="+l.JPiS()+" dsid="+this.dsidsV.get(i));
                    }
                }
                */		
				
			}else
				matchingStrength=new MatchingStrength(1);
		}else if(isJPIMatched || (hasCloseJPI(lev,1)&&!hasInconsistentParity)){
			//JPI matches but energy not, that could be due to large discrepancy,
			//then check if it has same gammas
			
			boolean skip=false;
			if(!isJPIMatched) {
				int nInconsistentJPI=findNumberOfInConsistentNonEmptyJPI(lev, 1);//non empty JPI in JPI filed and also in altJPI (implied from L-transfer)
				int nConsistentJPI=findNumberOfConsistentNonEmptyJPI(lev, 1);
				
				/*
				//debug
				if(lev.ES().equals("3.5E3")) {
					System.out.println("RecordGroup 1411: es="+lev.ES()+" js="+lev.JPiS()+" nInconsistentJPI="+nInconsistentJPI+" nConsistentJPI="+nConsistentJPI);
  				}
  				*/
				
				int n1=Math.min((int)Math.ceil(nInconsistentJPI/2.0),3);
				int n2=Math.min((int)Math.ceil(nRecords()/3.0),5);
				n2=Math.max(n2, nConsistentJPI);
				
				if(nConsistentJPI<=n1 || nInconsistentJPI>=n2)
					skip=true;
				
				/*
				//debug
				if(lev.ES().equals("11880")) {
					System.out.println("RecordGroup 1416: es="+lev.ES()+" js="+lev.JPiS()+" n1="+n1+" n2="+n2+" nInconsistentJPI="+nInconsistentJPI+" nConsistent="+nConsistentJPI+" skip="+skip);
                    for(int i=0;i<recordsV.size();i++) {
                        Level l=(Level)recordsV.get(i);
                        String s=l.JPiS();
                        if(s.isEmpty())
                        	s=l.altJPiS();
                        System.out.println("  in group: i="+i+"   e="+l.ES()+" J="+s+" dsid="+this.dsidsV.get(i));
                    }
				}
				*/
				
				
			}

			
			if(!skip) {
				if(deltaEL<=0)
					deltaEL=CheckControl.deltaEL;
				
				float tempDelta=deltaEL;
				String es=lev.ES();
				if(!es.contains("E")){
					if(es.contains("."))
						tempDelta=2.0f;
					else
						tempDelta=5.0f;
				}
				
				tempDelta=Math.min(tempDelta, deltaEL);
				tempDelta=Math.min(tempDelta, lev.ERPF()*0.1f);

				/*
                //debug
				if((lev.ES().equals("7482")||lev.ES().equals("7475"))) {
				//if(lev.ES().equals("4761") && lev.DES().equals("15")) {   
                    System.out.println("*****RecordGroup 1574:  E="+lev.ES()+"  deltaEL="+deltaEL+"  tempDelta="+tempDelta+" isEnergyMatched="+isEnergyMatched+" isJPIMatched="+isJPIMatched);
                    System.out.println("   matched="+(matchingStrength.strength>0)+"   hasComparableEnergyEntry(lev,delta,true)="+hasComparableEnergyEntry(lev,tempDelta,true));
                    System.out.println("   isGammaConsistent(lev,5.0f,true)="+isGammasConsistent(lev,5.0f,true)+" hasAnyConsistentGamma(lev, 0.5f, true)="+hasAnyConsistentGamma(lev, 0.5f, true));
                    System.out.println("   hasComparableEnergyEntry(lev,10.0f,true)="+hasComparableEnergyEntry(lev,10.0f,true));
                    System.out.println("   hasComparableEnergyEntry(lev,deltaEL,true)="+hasComparableEnergyEntry(lev,deltaEL,true));
        			for(int i=0;i<recordsV.size();i++) {
        			    Level l=(Level)recordsV.get(i);
        				System.out.println("  in group: i="+i+"   e="+l.ES()+" J="+l.JPiS()+" dsid="+this.dsidsV.get(i)+"  isReferenceRecord="+(recordsV.get(i)==refRecord));
        			}
                }
                */
				
				if(!Str.isNumeric(lev.ES()) && !lev.isTrueERPF()) {
				    if(!lev.NNES().isEmpty() && !lev.NES().isEmpty()) {
		                Level closestLev=(Level)EnsdfUtil.findClosestByEnergyEntry(lev, recordsV);
		                if(closestLev!=null && (lev.ES().equals(closestLev.ES())||lev.NES().equals(closestLev.NES())) && EnsdfUtil.isGammasConsistent(lev,closestLev, 0.01f,true))
		                	matchingStrength=new MatchingStrength(2);
		                else
		                	matchingStrength=new MatchingStrength(-1);
				    }
			            
				}else if(hasComparableEnergyEntry(lev,tempDelta,true)){
					if(isGammasConsistent(lev,5.0f,true) || lev.nGammas()==0 || !hasLevelGamma())
						matchingStrength=new MatchingStrength(3);
					else
						matchingStrength=new MatchingStrength(-1);
				}else if(hasComparableEnergyEntry(lev,10.0f,true)){
					if(isGammasConsistent(lev,1.0f,true))
						matchingStrength=new MatchingStrength(3);
					else if(isGammasConsistent(lev,2.0f,true) && isJPIMatched)
						matchingStrength=new MatchingStrength(4);
					else
					    matchingStrength=findMatchingStrengthOfLooseMatchLevel(lev,tempDelta,deltaEL);
				}else if(hasComparableEnergyEntry(lev,deltaEL,true)){
					matchingStrength=findMatchingStrengthOfLooseMatchLevel(lev,tempDelta,deltaEL);
				}else if(lev.nGammas()>0 && !lev.ES().contains(".") && lev.ES().endsWith("0") && hasComparableEnergyEntry(lev,1.5f*deltaEL,true) && isJPIMatched) {
				    float deltaE1=lev.DEF();
				    if(deltaE1<=0)
				        deltaE1=EnsdfUtil.findDefaultEnergyUncertainty(lev);
				    
				    if(lev.nGammas()>0) {
				        for(Gamma g:lev.GammasV()) {
				            if(g.DEF()>0) {
				                deltaE1=g.DEF();
				                break;
				            }
				        }
				        
				        if(isGammasConsistent(lev,deltaE1,true))
				        	matchingStrength=new MatchingStrength(6);
				    }
				    
				    
				    /*
			        //debug   
				    if(lev.ES().equals("1567") && lev.DES().equals("2")) {
		            //if(Math.abs(lev.EF()-4370)<40){
		                System.out.println("In RecordGroup line 1596: lev.es="+lev.ES()+"  e="+lev.EF()+" jpi="+lev.JPiS()+"  matched="+matched+"  deltaEL="+deltaEL+" tempDelta="+tempDelta+" "+hasComparableEnergyEntry(lev,tempDelta,true));
		                System.out.println("            isGammasConsistent(lev,1.0f,true)="+isGammasConsistent(lev,1.0f,true)+" hasComparableEnergyEntry(lev,deltaEL,true)="+hasComparableEnergyEntry(lev,deltaEL,true));
		                System.out.println("                  hasComparableEnergyEntry(10)="+hasComparableEnergyEntry(lev,10.0f,true)
		                                  +" hasComparableEnergyEntry(1.5*deltaE)="+hasComparableEnergyEntry(lev,1.5f*deltaEL,true));
		                System.out.println(" deltaE1="+deltaE1+" isGammasConsistent(lev,deltaE1,true)="+isGammasConsistent(lev,deltaE1,true));
		                for(int i=0;i<recordsV.size();i++)
		                    System.out.println("in group: i="+i+"   e="+((Level)recordsV.get(i)).ES());
		                System.out.println("                isEnergyMatched="+isEnergyMatched+"   isJPIMatched="+isJPIMatched);
		            }
				    */
				}
				
				/*
				//debug		
				if(Math.abs(lev.EF()-4370)<40){
					System.out.println("In RecordGroup line 845: lev.es="+lev.ES()+"  e="+lev.EF()+" jpi="+lev.JPiS()+"  matched="+matched+"  deltaEL="+deltaEL+" tempDelta="+tempDelta+" "+hasComparableEnergyEntry(lev,tempDelta,true));
					System.out.println("            isGammasConsistent(lev,1.0f,true)="+isGammasConsistent(lev,1.0f,true)+" hasComparableEnergyEntry(lev,deltaEL,true)="+hasComparableEnergyEntry(lev,deltaEL,true));
					System.out.println("                  hasComparableEnergyEntry(10)="+hasComparableEnergyEntry(lev,10.0f,true)
					                  +" hasComparableEnergyEntry(1.5*deltaE)="+hasComparableEnergyEntry(lev,1.5f*deltaEL,true));
					for(int i=0;i<recordsV.size();i++)
						System.out.println("in group: i="+i+"   e="+((Level)recordsV.get(i)).ES());
					System.out.println("                isEnergyMatched="+isEnergyMatched+"   isJPIMatched="+isJPIMatched);
				}
				*/				
			}

			
		}else if(isEnergyMatched && isGammasConsistent(lev,2.0f,true)) {//must have gammas strictly matched in energy
			if(hasCloseJPI(lev,3)){
				matchingStrength=new MatchingStrength(3);
			}else if(hasComparableEnergyEntry(lev,2.0f,true) && lev.nGammas()==findMaxNumberOfConsistentGammas(lev,2.0f,true) ) {
				matchingStrength=new MatchingStrength(3);
			}else if(hasComparableEnergyEntry(lev,1.0f,true) && isGammasConsistent(lev,1.0f,true)) {
				matchingStrength=new MatchingStrength(3);
			}
		}
		
		/*
		//debug	
		boolean debug=true;
		if(debug && Math.abs(lev.EF()-10144)<1){
			System.out.println("In RecordGroup line 1320: lev.es="+lev.ES()+"  e="+lev.EF()+" jpi="+lev.JPiS()+"  matched="+matched+"  deltaEL="+deltaEL);
			for(int i=0;i<recordsV.size();i++)
				System.out.println("in group: i="+i+"   e="+((Level)recordsV.get(i)).ES());
			System.out.println("                isEnergyMatched="+isEnergyMatched+"   isJPIMatched="+isJPIMatched);
		}
		*/
	    
		/*
		//debug
		//if(lev.ES().equals("5730") && lev.JPiS().equals("3/2")) {
		//if(lev.ES().equals("4761") && lev.DES().equals("15")) {
		if(Math.abs(lev.EF()-5642)<0.5){ 
		//if(lev.ES().equals("653")) {
		//if(debug && lev.ES().contains("5014")){
		//if(Math.abs(lev.EF()-10144)<1){
			System.out.println("In RecordGroup line 1877: e.ES="+lev.ES()+" EF="+lev.EF()+" jpi="+lev.JPiS()+" isEnergyMatched="+isEnergyMatched+" isJPIMatched="+isJPIMatched+" matched="+(matchingStrength.strength>0));
		    System.out.println("      matchingStrength.strength="+matchingStrength.strength);
			System.out.println("      group.isGammasConsistent(lev,10)="+isGammasConsistent(lev,10,true)+"  isGammasConsistent(lev,2)="+isGammasConsistent(lev,2,true)+" nGammas="+lev.nGammas()+" delta="+deltaEL);
		    System.out.println("    group.isGammasConsistent(lev,1)="+isGammasConsistent(lev,1,true)+" hasCloseJPI(lev,3)="+hasCloseJPI(lev,3)+" hasCloseJPI(lev,1)="+hasCloseJPI(lev,1));
		    //System.out.println("      EnsdfUtil.isComparableES(lev.ES(), record0InGroup.ES(), 2.0f)="+EnsdfUtil.isComparableES(lev.ES(), record0InGroup.ES(), 2.0f));
			//System.out.println("      lev.nGammas="+lev.nGammas()+" group.hasRecordGamma()="+group.hasRecordGamma());
		    for(int m=0;m<recordsV.size();m++){
			    Level l=(Level)recordsV.get(m);
				System.out.println("  record size="+recordsV.size()+" m="+m+"   l.ES="+l.ES()+" EF="+l.EF()+"  "+l.JPiS()+" dsid="+dsidsV.get(m));
			}						
		}
		*/
		
		return matchingStrength;
    }
 
    private  <T extends Record>MatchingStrength findMatchingStrengthOfLooseMatchLevel(Level lev,float minDeltaE,float maxDeltaE) {
        MatchingStrength matchingStrength=new MatchingStrength(-1);
        try {
            if(recordsV.size()>1) {
                String js1="",js2="";
                js1=lev.JPiS().replace("(","").replace(")","");
                
                int nEnergyLooseMatch=0;
                int nNonEmptyJPIMatch=0;
                int nEnergyComparable=0;
                int ng1=lev.nGammas();
                int ng2=0;
                
                boolean allEenegyLooseMatch=true;
                boolean isGammaConsistent=false;
                boolean hasLevelWithNoGamma=false;
                
                for(int i=0;i<recordsV.size();i++) {
                    Level l=(Level)recordsV.get(i);                 
                    js2=lev.JPiS().replace("(","").replace(")","");

                    ng2=l.nGammas();
                    if(ng1==0 || ng2==0)
                        hasLevelWithNoGamma=true;
                    else
                        hasLevelWithNoGamma=false;
                        
                    isGammaConsistent=(EnsdfUtil.isGammasConsistent(l,lev,5.0f,true) || (ng1==0&&ng2==0));
                    isGammaConsistent=(isGammaConsistent||hasLevelWithNoGamma);
                    
                    /*
                    //debug
                    if(lev.ES().equals("4761") && lev.DES().equals("15")) { 
                    //if(Math.abs(lev.EF()-3464)<0.2){    
                        System.out.println("*****RecordGroup 1666:  E="+lev.ES()+"  deltaEL="+deltaEL+"  tempDelta="+tempDelta+" "+isEnergyMatched+"  "+isJPIMatched);
                        System.out.println("   matched="+(matchingStrength.strength>0)+"   hasComparableEnergyEntry(lev,deltaEL,true)="+hasComparableEnergyEntry(lev,deltaEL,true));
                        System.out.println("   isGammaConsistent="+isGammaConsistent+" hasAnyConsistentGamma(lev, 0.5f, true)="+hasAnyConsistentGamma(lev, 0.5f, true));
                        System.out.println("   js1="+js1+" lev.nGammas()="+lev.nGammas()+" l.es="+l.ES()+"   js2="+js2+" l.nGammas()="+l.nGammas());
                        System.out.println("   isComparableEnergyEntry(l,lev,deltaEL,true)="+EnsdfUtil.isComparableEnergyEntry(l,lev,deltaEL,true));
                    }
                    */
                    
                    
                    if(!js1.isEmpty() && js1.equals(js2) && isGammaConsistent){
                        nNonEmptyJPIMatch++;                        
                        if(EnsdfUtil.isComparableEnergyEntry(l,lev, minDeltaE,true)) {
                            matchingStrength=new MatchingStrength(5);
                            break;
                        }
                    }
                    
                    if(!EnsdfUtil.isComparableEnergyEntry(l,lev,maxDeltaE,true) || !isGammaConsistent)
                        allEenegyLooseMatch=false;
                    else { 
                        nEnergyLooseMatch++;
                        if(EnsdfUtil.isComparableEnergyEntry(l,lev,maxDeltaE,false))
                            nEnergyComparable++;
                    }           
                }
                
                if(nEnergyComparable>0) {
                    if(allEenegyLooseMatch && (js1.isEmpty()||nNonEmptyJPIMatch>0) && nEnergyLooseMatch>1)
                        matchingStrength=new MatchingStrength(6);
                    else if(nEnergyLooseMatch>recordsV.size()/2 && nNonEmptyJPIMatch>0)
                        matchingStrength=new MatchingStrength(6);
                }

                
                /*
                //debug
                if(lev.ES().equals("4761") && lev.DES().equals("15")) { 
                //if(Math.abs(lev.EF()-3464)<0.2){   
                System.out.println("*****RecordGroup 1699:  E="+lev.ES()+"  deltaEL="+deltaEL+"  tempDelta="+tempDelta+" "+isEnergyMatched+"  "+isJPIMatched+" r.mean E="+this.getMeanEnergy());
                System.out.println("   matched="+(matchingStrength.strength>0)+"   allEenegyLooseMatch="+allEenegyLooseMatch+" nNonEmptyJPIMatch="+nNonEmptyJPIMatch
                        +" nEnergyLooseMatch="+nEnergyLooseMatch+" order="+(matchingStrength.order));
                
                }
                */
                
            }   
        }catch(Exception e) {
            
        }
        
        return matchingStrength;
    }
    
    private  <T extends Record> boolean haveMatchedGamma(Gamma gam,float deltaEG){
    	MatchingStrength matchingStrength=findMatchingStrengthOfGamma(gam,deltaEG);
    	if(matchingStrength.strength>0)
    		return true;
    	
    	return false;
    }
	private  <T extends Record> MatchingStrength findMatchingStrengthOfGamma(Gamma gam,float deltaEG){
		boolean isEnergyMatched=hasComparableEnergyEntry(gam,deltaEG);
		MatchingStrength matchingStrength=new MatchingStrength(-1);
		
		if(isEnergyMatched){
			return new MatchingStrength(1);
		}else if(gam.ES().contains(".") && !gam.ES().contains("E")){
			//Eg=330.914(9) and 331.05(2), isEnergyMatched=false due to very small uncertainty for 330.914
			//but they are the same gamma. So for this case, additional comparison is needed.
			//Note that hasComparableEnergyEntry() for this case should return false and additional comparison 
			//should be done outside of this function, as done in haveMatchedLevel()
			try{
				float de=gam.DEF();
				//float e=gam.EF();
				//float de0=((Gamma)recordsV.get(0)).DEF();
				//float e0=((Gamma)recordsV.get(0)).EF();
				if(de<deltaEG){
					if(de>1){
						if(hasComparableEnergyEntry(gam,5.0f,true)){
							matchingStrength=new MatchingStrength(3);
						}
					}else if(hasComparableEnergyEntry(gam,1.0f,true)){
						matchingStrength=new MatchingStrength(2);						
					}
				}

			}catch(Exception e){}
			
		}
		
		/*
    	//debug
		if(Math.abs(gam.EF()-1940)<100) {
    	//if(gam.ES().contains("988.9")){
    		System.out.println("In RecordGroup line 458: es="+gam.ES()+" des="+gam.DES()+" e="+gam.EF()+" de="+gam.DEF()+"  matched="+matched+" isEnergyMatched="+isEnergyMatched+" deltaEG="+deltaEG);
    		System.out.println("     hasComparableEnergyEntry(gam,5.0f,true)="+hasComparableEnergyEntry(gam,5.0f,true)+"  hasComparableEnergyEntry(gam,1.0f,true)="+hasComparableEnergyEntry(gam,5.0f,true));
		    for(int m=0;m<recordsV.size();m++){
			    Gamma g=(Gamma)recordsV.get(m);
				System.out.println("     g.ES="+g.ES()+" DES="+g.DES()+" EF="+g.EF()+" DEF="+g.DEF()+" dsid="+dsidsV.get(m));
			}	
    	}
    	*/
		
		return matchingStrength;
    }
    
    public <T extends Record> void setAdoptedRecord(T r){adopted=r;}
    public <T extends Record> void setReferenceRecord(T r){refRecord=r;}
    

	
    @SuppressWarnings("unchecked")
	public <T extends Record> T getAdoptedRecord(){return (T)adopted;}
    
	@SuppressWarnings("unchecked")
	public <T extends Record> T getReferenceRecord(){return (T)refRecord;}
	
    public boolean hasAdoptedRecord(){
    	return (adopted!=null);
    }
    
    public int nRecordsWithT12(){
    	int n=0;
    	try{
        	if(recordsV.get(0) instanceof Level){
            	for(int i=0;i<recordsV.size();i++){
            		if(((Level)recordsV.get(i)).halflife().length()>0)
            			n++;
            	}	
        	}

    	}catch(Exception e){}
    	
    	return n;
    }
    
    public int nRecordsWithRI(){
    	int n=0;
    	try{
        	if(recordsV.get(0) instanceof Gamma){
            	for(int i=0;i<recordsV.size();i++){
            		if(((Gamma)recordsV.get(i)).RIS().length()>0)
            			n++;
            	}	
        	}

    	}catch(Exception e){}
    	
    	return n;
    }
    
    public int nRecordsWithTI(){
    	int n=0;
    	try{
        	if(recordsV.get(0) instanceof Gamma){
            	for(int i=0;i<recordsV.size();i++){
            		if(((Gamma)recordsV.get(i)).TIS().length()>0)
            			n++;
            	}	
        	}

    	}catch(Exception e){}
    	
    	return n;
    }
    
    /*
     * replace RI record (or TI if RI empty) with branching ratios (strongest IG=100)  
     * Note that DBRS is rounded using default ENSDF error-limit=35
     * 
     * option=ADOPTED, remove TI if not empty; replace RI with BR from RI, or with BR from TI if RI is empty
     *       =ALL,     replace TI with TBR if not empty (no change if TI is empty), replace RI with BR from RI, or with BR from TI if RI is empty
     *       =DEFAULT, replace TI with TBR if TI not empty and replace RI with BR if RI not empty (no change if TI or RI empty)
     */
    public String replaceIntensityOfGammaLineWithBR(Gamma g,int errorLimit,String option){   	
    	option=option.toUpperCase().trim();
    	if(option.equals("ADOPTED"))
    		return replaceIntensityOfGammaLineWithBRForAdopted(g,errorLimit);
    	if(option.equals("ALL"))
    		return replaceIntensityOfGammaLineWithBRForAll(g,errorLimit);

    		
    	return replaceIntensityOfGammaLineWithBRDefault(g,errorLimit);
    }
    
    public String replaceIntensityOfGammaLineWithBRForAdopted(Gamma g,int errorLimit){
    	
    	String line=g.recordLine();
    	line=EnsdfUtil.replaceTIOfGammaLine(line,"","");
    	
    	if(!g.RIS().isEmpty())
    		line=replaceRIOfGammaLineWithBR(line,g,errorLimit);
    	else
    		line=replaceRIOfGammaLineWithBRFromTBR(line,g,errorLimit);
    	
    	return line;
    }
    
    public String replaceIntensityOfGammaLineWithBRForAll(Gamma g,int errorLimit){
    	
    	String line=g.recordLine();
    	if(!g.TIS().isEmpty()) 
    		line=replaceTIOfGammaLineWithTBR(line,g,errorLimit);
    	
    	if(!g.RIS().isEmpty())
    		line=replaceRIOfGammaLineWithBR(line,g,errorLimit);
    	else
    		line=replaceRIOfGammaLineWithBRFromTBR(line,g,errorLimit);
    	
    	return line;
    }
    
    public String replaceIntensityOfGammaLineWithBRDefault(Gamma g,int errorLimit){
    	
    	String line=g.recordLine();
    	if(!g.TIS().isEmpty()) 
    		line=replaceTIOfGammaLineWithTBR(line,g,errorLimit);
    	
    	if(!g.RIS().isEmpty())
    		line=replaceRIOfGammaLineWithBR(line,g,errorLimit);
    	
    	return line;
    }
    /*
     * replace RI record with branching ratios (strongest IG=100)  
     * Note that DBRS is rounded using default ENSDF error-limit=25
     */
    public String replaceRIOfGammaLineWithBR(Gamma g,int errorLimit){
    	return replaceRIOfGammaLineWithBR(g.recordLine(),g,errorLimit);
    }
    
    public String replaceRIOfGammaLineWithBR(String recordLine,Gamma g,int errorLimit){
    	if(g.RIS().isEmpty())
    		return recordLine;
    	
    	
    	//debug
    	//if(g.ES().equals("713.25")) {
    	//	System.out.println("RecordGroup 2156: e="+g.ES()+"  "+recordLine+" g.RelBRS()="+g.RelBRS()+" g.DRelBRS()="+g.DRelBRS());
    	//}
    	
    	
    	if(!Str.isNumeric(g.DRelBRS())) {
    	    String s=g.RelBRS();
    	    if(s.contains(".")) {
    	        while(s.endsWith("0")) {
    	            s=s.substring(0,s.length()-1);
    	        }
    	    }
    	    
            if(s.endsWith("."))
                s=s.substring(0,s.length()-1);
            
            if(s.contains("."))
            	s=Str.roundByNsig(s,2);
            
            
        	//debug
        	//if(g.ES().equals("713.25")) {
        	//	System.out.println("RecordGroup 2175: e="+g.ES()+"  "+recordLine+" g.RelBRS()="+g.RelBRS()+" g.DRelBRS()="+g.DRelBRS()+" s="+s);
        	//}
        	
            
    		return EnsdfUtil.replaceRIOfGammaLine(recordLine,s,g.DRelBRS());
    	}else {
    		XDX2SDS xs=new XDX2SDS(g.RelBRD(),g.DRelBRD(),errorLimit);
    		if(xs.s().contains("E") && xs.ds().length()==1 && errorLimit<99 && g.DRelBRD()>errorLimit) {
    		    
    			//if(g.ES().contains("2711")) System.out.println("1 e="+g.ES()+"  "+recordLine+" g.RelBRS()="+g.RelBRS()+" g.DRelBRS()="+g.DRelBRS()+"  s="+xs.S()+"  ds="+xs.ds()+" g.DRelBRD()="+g.DRelBRD());
    		    
    		    //if(g.DRelBRD()<99 && g.RelBRD()<200) {
    			if(g.DRelBRD()<99) {
    				xs=new XDX2SDS(g.RelBRD(),g.DRelBRD(),99);
    			}
    			
    			//System.out.println("2 e="+g.ES()+"  "+recordLine+" g.RelBRS()="+g.RelBRS()+" g.DRelBRS()="+g.DRelBRS()+"  s="+xs.S()+"  ds="+xs.ds());
    		}else if(xs.ds().length()==1 && xs.s().length()==1 && xs.s().equals(xs.ds()) && g.RelBRD()>g.DRelBRD()) {
    		    //System.out.println("3 e="+g.ES()+"  "+recordLine+" g.RelBRS()="+g.RelBRS()+" g.DRelBRS()="+g.DRelBRS()+"  s="+xs.S()+"  ds="+xs.ds()+" g.RelBRD()="+g.RelBRD()+" g.DRelBRD()="+g.DRelBRD());
    		    
    		    xs=new XDX2SDS(g.RelBRD(),g.DRelBRD(),99);
    		    
    		}
    		//if(g.ES().contains("2711")) System.out.println("2 e="+g.ES()+"  br="+xs.S()+"  dbr="+xs.ds()+" line="+EnsdfUtil.replaceRIOfGammaLine(recordLine,xs.s(),xs.ds()));
    		
    		xs=xs.removeEndZeros();
    		
    		//if(g.ES().contains("2711")) System.out.println("3 e="+g.ES()+"  br="+xs.S()+"  dbr="+xs.ds()+" line="+EnsdfUtil.replaceRIOfGammaLine(recordLine,xs.s(),xs.ds()));
    		
    		return EnsdfUtil.replaceRIOfGammaLine(recordLine,xs.s(),xs.ds());
    	}
    }
    
    /*
     * replace TI record with branching ratios normalized to strongest IG=100)  
     * Note that DBRS is rounded using default ENSDF error-limit=25
     */
    public String replaceTIOfGammaLineWithTBR(Gamma g,int errorLimit){
    	return replaceTIOfGammaLineWithTBR(g.recordLine(),g,errorLimit);
    }
    
    public String replaceTIOfGammaLineWithTBR(String recordLine,Gamma g,int errorLimit){
    	if(g.TIS().isEmpty())
    		return recordLine;
    	
    	if(!Str.isNumeric(g.DRelTBRS())) {
            String s=Str.removeZeros(g.RelTBRS());
            if(s.endsWith("."))
                s=s.substring(0,s.length()-1);
            
            if(s.contains("."))
            	s=Str.roundByNsig(s,2);
            
    		return EnsdfUtil.replaceTIOfGammaLine(recordLine,s,g.DRelTBRS());
    	}else {
    		XDX2SDS xs=new XDX2SDS(g.RelTBRD(),g.DRelTBRD(),errorLimit);
    		if(xs.s().contains("E") && xs.ds().length()==1 && errorLimit<99 && g.DRelTBRD()>errorLimit) {
    			if(g.DRelTBRD()<99 && g.RelTBRD()<200) {
    				xs=new XDX2SDS(g.RelTBRD(),g.DRelTBRD(),99);
    			}
    		}
    		xs=xs.removeEndZeros();
    		return EnsdfUtil.replaceTIOfGammaLine(recordLine,xs.s(),xs.ds());
    	}
    }
    
    public String replaceRIOfGammaLineWithBRFromTBR(String recordLine,Gamma g,int errorLimit){
    	if(g.CCS().isEmpty() || g.TIS().isEmpty() || g.RelTBRS().isEmpty())
    		return recordLine;
    	
    	if(!Str.isNumeric(g.DRelTBRS()) || !Str.isNumeric(g.DCCS())) {
    		SDS2XDX sx=new SDS2XDX(g.RelTBRS(),g.DRelTBRS());
    		sx.setErrorLimit(errorLimit);
    		
    		SDS2XDX sx1=new SDS2XDX(g.CCS(),g.DCCS());
    		sx1.setErrorLimit(errorLimit);
    		sx1=sx1.add(1.0f);//1+CC
    		

    		sx=sx.divided(sx1,true);

    		sx=sx.removeEndZeros();
    		
    		String s=sx.s();
    		String ds=sx.ds();
    		
    		if(!Str.isNumeric(ds) && !ds.contains("+") && s.contains("."))
    			s=Str.roundByNsig(s, 2);
    		
    		return EnsdfUtil.replaceRIOfGammaLine(recordLine,s,ds);
    	}else {

    		XDX2SDS xs=new XDX2SDS(g.RelTBRD(),g.DRelTBRD(),errorLimit);
    		if(xs.s().contains("E") && xs.ds().length()==1 && errorLimit<99 && g.DRelTBRD()>errorLimit) {
    			if(g.DRelTBRD()<99 && g.RelTBRD()<200) {
    				xs=new XDX2SDS(g.RelTBRD(),g.DRelTBRD(),99);
    			}
    		}
    		
    		XDX2SDS xs1=new XDX2SDS(g.CCF(),g.DCCF(),errorLimit);
    		if(xs1.s().contains("E") && xs1.ds().length()==1 && errorLimit<99 && g.DCCF()>errorLimit) {
    			if(g.DCCF()<99 && g.CCF()<200) {
    				xs1=new XDX2SDS(g.CCF(),g.DCCF(),99);
    			}
    		}
    		
    		SDS2XDX sx=new SDS2XDX();
    		sx.setValues(xs);
    		
    		SDS2XDX sx1=new SDS2XDX();
    		sx1.setValues(xs1);
    		sx1=sx1.add(1.0f);//1+CC
    		
    		sx=sx.divided(sx1,true);
    		
    		sx=sx.removeEndZeros();
    		
    		return EnsdfUtil.replaceRIOfGammaLine(recordLine,sx.s(),sx.ds());
    	}
    }
    
    /*
     * find possible records that could roughly match the given group in energy 
     */
    @SuppressWarnings("unchecked")
    public <T extends Record> Vector<T> findPossibleRecordsForGroup(Vector<T> recordsV,float deltaE) {
        Vector<T> out=new Vector<T>();
        if(deltaE<=0)
            return out;
              
        if(averageRec==null)
            averageRec=(T) calculateAverageEnergy();       
        
        out=(Vector<T>) EnsdfUtil.findMatchesByEnergyEntry(averageRec, recordsV, deltaE,true);
             
        return out;
    }
    
    /*
     * Compare energy, JPI, and other properties to see which record 
     * better matches the group
     * 
     * >0, r1 match the group better
     * <0, r2 match the group better
     * =0, same
     */
    public int whichLevelMatchBetter(Level lev1, Level lev2,float deltaE,boolean forceDelta) {
        
        boolean hasOverlapJPI1=this.hasOverlapJPI(lev1);
        boolean hasOverlapJPI2=this.hasOverlapJPI(lev2);

        int ng1=lev1.nGammas();
        int ng2=lev2.nGammas();
        boolean isGammaConsistent1=(ng1>0)&&this.hasLevelGamma()&&this.isGammasConsistent(lev1, deltaE, forceDelta);
        boolean isGammaConsistent2=(ng2>0)&&this.hasLevelGamma()&&this.isGammasConsistent(lev2, deltaE, forceDelta);
        
        float deltaE1=deltaE,deltaE2=deltaE;
        if(!forceDelta || deltaE<=0) {
            if(isGammaConsistent1) {
            	deltaE1=CheckControl.deltaEL;
            }
            if(isGammaConsistent2) {
            	deltaE2=CheckControl.deltaEL;
            }
        }

        
        boolean hasMatchedLevel1=this.hasMatchedRecord(lev1, deltaE1);
        boolean hasMatchedLevel2=this.hasMatchedRecord(lev2, deltaE2);
        
        /*
        //debug
        if(Math.abs(lev1.EF()-7475)<1 && Math.abs(lev2.EF()-7482)<1) {
        //if(lev1.ES().equals("1243") || lev2.ES().equals("1243")) 
        //if(Math.abs(lev1.EF()-7068)<20 && Math.abs(lev2.EF()-7081)<20) 
            System.out.println("  RecordGroup 2272 *** e1="+lev1.ES()+" "+lev1.DES()+" e2="+lev2.ES()+" "+lev2.DES()+" hasMatchedLevel1="+hasMatchedLevel1+" hasMatchedLevel2="
        +hasMatchedLevel2+" deltaE1="+deltaE1+" detalE2="+deltaE2+" forceDelta="+forceDelta+" hasOverlapJPI1="+hasOverlapJPI1+" hasOverlapJPI2="+hasOverlapJPI2);
            for(Record r:this.recordsV())
                System.out.println("   record in group: "+r.ES()+" "+r.DES());
        }
        */
        
        if(hasMatchedLevel1 && hasMatchedLevel2) {
            if(hasOverlapJPI1 && hasOverlapJPI2) {
                float dist1=this.findAverageDistanceToGroup(lev1);
                float dist2=this.findAverageDistanceToGroup(lev2);
                
                float diffToRef1=-1;
                float diffToRef2=-1;
                if(refRecord!=null) {
                    diffToRef1=Math.abs(lev1.ERPF()-refRecord.ERPF());
                    diffToRef2=Math.abs(lev2.ERPF()-refRecord.ERPF());   
                    
                    dist1=(dist1+diffToRef1)/2.0f;
                    dist2=(dist2+diffToRef2)/2.0f;
                }
                
                /*
                //debug
                if(Math.abs(lev1.EF()-3500)<60 && Math.abs(lev2.EF()-3500)<60)
                //if(lev1.ES().equals("1243") || lev2.ES().equals("1243")) 
                //if(Math.abs(lev1.EF()-7068)<20 && Math.abs(lev2.EF()-7081)<20) 
                    System.out.println("  RecordGroup 1818 *** e1="+lev1.EF()+" e2="+lev2.EF()+" dist1="+dist1+" dist2="+dist2+" diffToRef1="+diffToRef1+" diffToRef2="
                            +diffToRef2+" isGammaCons1="+isGammaConsistent1+" isGammaCons2="+isGammaConsistent2);
                */
                
                
                if(isGammaConsistent1==isGammaConsistent2) {
                	
                	int ngMatch1=findMaxNumberOfConsistentGammas(lev1, deltaE, true);
                  	int ngMatch2=findMaxNumberOfConsistentGammas(lev2, deltaE, true);
                  	
                  	/*
                    //debug
                  	if(Math.abs(lev1.EF()-3500)<60 && Math.abs(lev2.EF()-3500)<60)
                    //if(lev1.ES().equals("1243") || lev2.ES().equals("1243")) 
                        System.out.println(" RecordGroup 1832  *** e1="+lev1.EF()+" e2="+lev2.EF()+" dist1="+dist1+" dist2="+dist2+" diffToRef1="+diffToRef1+" diffToRef2="
                                +diffToRef2+" ngMatch1="+ngMatch1+" ngMatch2="+ngMatch2);
                    */
                  	
                  	if(ngMatch1>ngMatch2) {
                  		return 1;
                  	}else if(ngMatch1<ngMatch2) {
                  		return -1;
                  	}
                  	
                    float de=5.0f;
                    if(lev1.ES().contains(".") && lev2.ES().contains("."))
                        de=2.0f;
                    
                    float distDiff=Math.abs(dist1-dist2);
                    
                    /*
                    //debug
                  	if(Math.abs(lev1.EF()-3500)<60 && Math.abs(lev2.EF()-3500)<60)
                    //if(lev1.ES().equals("1243") || lev2.ES().equals("1243")) 
                        System.out.println(" RecordGroup 1854  *** e1="+lev1.EF()+" e2="+lev2.EF()+" dist1="+dist1+" dist2="+dist2+" diffToRef1="+diffToRef1+" diffToRef2="
                                +diffToRef2+" distDiff="+distDiff+"  de="+de+" deltaE="+deltaE+"  ng1="+ng1+" ng2="+ng2+" group mean E="+this.getMeanEnergy()+" min E="+this.minE);
                  	*/
                    
                    if(distDiff<=de) {
                        //if(Math.abs(diffToRef1-diffToRef2)<=de)
                        if(Math.abs(diffToRef1-diffToRef2)<=1E-3)	
                            return 0;
                        
                        if(diffToRef1<diffToRef2)
                            return 1;
                        else if(diffToRef1>diffToRef2)
                            return -1;
                    }

                    if(deltaE<0)
                    	deltaE=CheckControl.deltaEL;
                                       
                    if(distDiff<deltaE && ng1==0 && ng2==0) {                       
                        int nMatchedNonEmptyJPI1=this.findNumberOfMatchedJPI(lev1);
                        int nMatchedNonEmptyJPI2=this.findNumberOfMatchedJPI(lev2);
                        
                        /*
                        //debug
                      	if(Math.abs(lev1.EF()-3500)<60 && Math.abs(lev2.EF()-3500)<60)
                        //if(lev1.ES().equals("1243") || lev2.ES().equals("1243")) 
                            System.out.println(" RecordGroup 1878  *** e1="+lev1.EF()+" e2="+lev2.EF()+" dist1="+dist1+" dist2="+dist2+" diffToRef1="+diffToRef1+" diffToRef2="
                                    +diffToRef2+" nMatchedNonEmptyJPI1="+nMatchedNonEmptyJPI1+"nMatchedNonEmptyJPI2="+nMatchedNonEmptyJPI1+" group mean E="+this.getMeanEnergy());
                      	*/
                        
                        if(nMatchedNonEmptyJPI1>0 && nMatchedNonEmptyJPI2==0)
                        	return 1;
                        else if(nMatchedNonEmptyJPI2>0 && nMatchedNonEmptyJPI1==0)
                        	return -1;
                    }
                    
                    if(dist1<dist2) {
                        return 1;
                    }else if(dist1>dist2)
                        return -1;

                   
                }else if(isGammaConsistent1) {
                    if(lev2.nGammas()>0)
                        return 1;
                    else if(diffToRef2>0 && diffToRef2<diffToRef1/2.0 && dist2<dist1)
                        return -1;
                    else
                        return 1;
                }else{//isGammaConsistent2
                    if(lev1.nGammas()>0)
                        return -1;
                    else if(diffToRef1>0 && diffToRef1<diffToRef2/2.0 && dist1<dist2)
                        return 1;
                    else
                        return -1;                    
                }
                
            }else if(hasOverlapJPI1) {
                return 1;
            }else if(hasOverlapJPI2) {
                return -1;
            }
        }else if(hasMatchedLevel1) {
            return 1;
        }else if(hasMatchedLevel2) {
            return -1;
        }
        
        return 0;
    }
    
    public int whichLevelMatchBetter(Level lev1, Level lev2,float deltaE) {
        return whichLevelMatchBetter(lev1,lev2,deltaE,false);
    }
    
    public int whichMatchBetter(Level lev1, Level lev2) {
        return whichLevelMatchBetter(lev1,lev2,-1,false);
    }

    public Level findBestMatchLevel(Vector<Level> levelsV,float deltaE,boolean forceDelta) {
    	Level bestMatch=null;
    	
    	try {
        	bestMatch=levelsV.get(0);
        	for(int i=1;i<levelsV.size();i++) {
        		Level lev=levelsV.get(i);
        		int n=whichLevelMatchBetter(lev,bestMatch, deltaE,forceDelta);
        		if(n>0)
        			bestMatch=lev;
        	}

    	}catch(Exception e) {
    		
    	}

    	return bestMatch;
    }
    
    public <T extends Record> T findBestMatchRecordByEenrgy(Vector<T> recordsV) {
    	T bestRec=null;
        
        float diffToRef=-1,minDiff=100000;
        
    	for(T r:recordsV) {
            float dist=this.findAverageDistanceToGroup(r);
       
            
            if(refRecord!=null) {
                diffToRef=Math.abs(r.ERPF()-refRecord.ERPF());
                dist+=diffToRef;

            }
            
            
            if(dist<minDiff) {
            	minDiff=dist;
            	bestRec=r;
            }
    	}
    	
    	return bestRec;

    }    
    
    public void setDSIDsVWithDuplicateShortID(Vector<String> dsidsV) {
    	try {
    		dsidsVWithDuplicateShortID.addAll(dsidsV);
    	}catch(Exception e) {
    		
    	}
    }
}

