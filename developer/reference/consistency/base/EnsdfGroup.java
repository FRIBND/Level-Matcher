package consistency.base;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Vector;

import ensdfparser.check.SpinParityParser;
import ensdfparser.ensdf.Band;
import ensdfparser.ensdf.DatasetID;
import ensdfparser.ensdf.ENSDF;
import ensdfparser.ensdf.Gamma;
import ensdfparser.ensdf.JPI;
import ensdfparser.ensdf.Level;
import ensdfparser.ensdf.Record;
import ensdfparser.ensdf.XRef;
import ensdfparser.nds.ensdf.EnsdfUtil;
import ensdfparser.nds.util.Str;

/*class to store all ENSDF files that belong to the same nucleus*/
public class EnsdfGroup {
	private Vector<ENSDF> ensdfV=new Vector<ENSDF>();
	
	private ENSDF adopted;

	//(DSID,oldXTag) in XREF list in Adopted dataset or (DSID,newXTag) if no Adopted dataset 
	//dsidXTagMap=makeAdoptedXTagMap(): dsid and xtag from XREF list in Adopted dataset if existing
	//dsidXTagMap=makeNewXTagMap(): dsid from each dataset DSID and xtag is assigned in a new XREF list
	//                              if no Adopted dataset
	private HashMap<String,String> dsidXTagMapFromAdopted=new HashMap<String,String>();
	
	//newDSIDXTagMap=makeNewXTagMap()
	private HashMap<String,String> newDSIDXTagMap=new HashMap<String,String>();//(DSID,newXTag)

	private String NUCID="";
	
	private Vector<RecordGroup> levelGroupsV=new Vector<RecordGroup>();
	private Vector<RecordGroup> unpGammaGroupsV=new Vector<RecordGroup>();
	
	//For DSID like A(B,C)D, short ID=(B,C)
	//this vector contains all DSIDs that could share the same short ID=(B,C) with others in current ENSDF group
	private Vector<String> dsidsVWithDuplicateShortID=new Vector<String>();
	private Vector<String> tempShortIDsV=new Vector<String>();
	
	//temporarily store (shortID,DSID) of the first DSID that have shortID 
	private HashMap<String,String> tempShortIDdsidMap=new HashMap<String,String>();
	
	private Vector<Level> firstLevelInGroupV=new Vector<Level>();
	
	private boolean isLevelGrouped=false;
	
	private float deltaEL=50;
	private float deltaEG=50;
	
	//dsidsV: DSID0 from each dataset; 
	//        could be NOT the same as DSID from XREF list in Adopted dataset
	//xtagsV: assigned xtag for each dataset if no Adopted dataset;
	//        If Adopted dataset exists, it is the original xtag in the XREF list  
	//        corresponding to the same DSID as that of the dataset. If DSID of 
	//        a dataset is not in the XREF list, a xtag="?#" where # is 0,1,2,3,..., 
	//        is assigned
	private Vector<String> datasetXTagsV=new Vector<String>();
	private Vector<String> datasetDSID0sV=new Vector<String>();
	private Vector<String> datasetDSIDsV=new Vector<String>();
	
	private boolean isEvenEven=false;
	
	private HashMap<ENSDF,SpinParityParser> ensJPIParserMap=new HashMap<ENSDF,SpinParityParser>();
	
	public EnsdfGroup(){
	}
	
	/*only ENSDFs that belong to the same nucleus is added*/
	public void addENSDF(ENSDF ens){
		addENSDF(ens,ensdfV.size());
	}	
	
	public void addENSDF(ENSDF ens,int index){
		String id=ens.nucleus().nameENSDF().trim().toUpperCase();
		if(id.length()==0)
			return;
		
		if(ensdfV.isEmpty() || id.equals(NUCID)){
    		ensdfV.insertElementAt(ens,index);
    		NUCID=id;
    		if(ens.DSId().contains("ADOPTED")){
    			adopted=ens;
    			dsidXTagMapFromAdopted=makeXTagMapFromAdopted();
    		}
		}
		
		String dsid=ens.DSId0();
		String shortID=EnsdfUtil.getShortDSID(dsid);
		if(shortID.startsWith("(")) {
			if(!tempShortIDsV.contains(shortID)) {
				tempShortIDsV.add(shortID);
				tempShortIDdsidMap.put(shortID,dsid);
			}else{
				dsidsVWithDuplicateShortID.add(dsid);
				String tempDSID=tempShortIDdsidMap.get(shortID);
				if(tempDSID!=null && tempDSID.length()>0) {
					dsidsVWithDuplicateShortID.add(tempDSID);
					tempShortIDdsidMap.remove(tempDSID);
				}
			}
		}
	}
	
	public void addENSDFs(Vector<ENSDF> ensV){
		for(int i=0;i<ensV.size();i++)
			addENSDF(ensV.get(i));
	}
	
	
	public int nENSDF(){return ensdfV.size();}
	public String NUCID(){return NUCID;}
	public ENSDF adopted(){return adopted;}
	public Vector<ENSDF> ensdfV(){return ensdfV;}
	
	public Vector<String> datasetDSIDsV(){return datasetDSIDsV;}
	public Vector<String> datasetDSID0sV(){return datasetDSID0sV;}
	public Vector<String> datasetXTagsV(){return datasetXTagsV;}
	
	public Vector<String> dsidsVWithDuplicateShortID(){return dsidsVWithDuplicateShortID;}
	
	public HashMap<String,String> dsidXTagMapFromAdopted(){return dsidXTagMapFromAdopted;}
	public HashMap<String,String> newDSIDXTagMap(){return newDSIDXTagMap;}
	
	public String getXTagInAdopted(ENSDF ens){return dsidXTagMapFromAdopted.get(ens.DSId0());}
	
	public ENSDF getENSDFByXTag(String xtag){
		try{
			int i=datasetXTagsV.indexOf(xtag);
			return ensdfV.get(i);
		}catch (Exception e){
		}	
		return null;
	}
	
	public ENSDF getENSDFByDSID0(String dsid0){
		try{
			int i=datasetDSID0sV.indexOf(dsid0);
			return ensdfV.get(i);
		}catch (Exception e){
		}	
		return null;
	}
	public ENSDF getENSDFByDSID(String dsid){
		try{
			int i=datasetDSIDsV.indexOf(dsid);
			return ensdfV.get(i);
		}catch (Exception e){
		}	
		return null;
	}
	
	public SpinParityParser getJPIParserByDSID0(String dsid0){
		try{
			int i=datasetDSID0sV.indexOf(dsid0);
			return getJPIParser(ensdfV.get(i));
		}catch (Exception e){
		}	
		return null;
	}
	public SpinParityParser getJPIParserByDSID(String dsid){
		try{
			int i=datasetDSIDsV.indexOf(dsid);
			return getJPIParser(ensdfV.get(i));
		}catch (Exception e){
		}	
		return null;
	}
	public SpinParityParser getJPIParser(ENSDF ens) {
		if(ens==null)
			return null;
		
		return ensJPIParserMap.get(ens);
	}
	
	public void setLevelGroups(Vector<RecordGroup> levelGroupsV){
		this.levelGroupsV=levelGroupsV;
		isLevelGrouped=true;
		
		//System.out.println("EnsdfGroup 173: "+dsidsVWithDuplicateShortID.size());
		
		for(RecordGroup g:levelGroupsV) {
			g.setDSIDsVWithDuplicateShortID(dsidsVWithDuplicateShortID);
			
			//System.out.println("EnsdfGroup 173: "+g.dsidsVWithDuplicateShortID.size());
			
			for(RecordGroup sg:g.subgroups()) {
				sg.setDSIDsVWithDuplicateShortID(dsidsVWithDuplicateShortID);
			}
		}
	}
	
	public Vector<RecordGroup> unpGammaGroupsV(){return unpGammaGroupsV;}
	public Vector<RecordGroup> levelGroupsV(){return levelGroupsV;}
	public boolean isLevelGrouped(){return isLevelGrouped;}
	
    public void setDeltaEL(float DEL){deltaEL=DEL;}
    public void setDeltaEG(float DEG){deltaEG=DEG;}
    
    public float getDeltaEL(){return deltaEL;}
    public float getDeltaEG(){return deltaEG;}
    
    public void setIsEvenEven(boolean b){isEvenEven=b;}
    public boolean isEvenEven(){return isEvenEven;}
    
	//Note: it is old tag in XTagMap
	private HashMap<String,String> makeXTagMapFromAdopted(){
		HashMap<String,String> map=new HashMap<String,String>(); //(DSID,XTag)
		if(adopted==null)
			return map;
		
		Vector<XRef> xrefV=adopted.XRefV();
		map.put(adopted.DSId0(),"");
		
		for(int i=0;i<xrefV.size();i++){
			XRef xref=xrefV.get(i);
			map.put(xref.DSId0(),xref.oldTag());
		}
	
		HashMap<String,String> map0=new HashMap<String,String>();
		map0.putAll(map);
		
		datasetDSIDsV.clear();
		datasetDSID0sV.clear();
		datasetXTagsV.clear();
		int n=0;
		for(int i=0;i<ensdfV.size();i++){
			ENSDF ens=ensdfV.get(i);
			
			String dsid0=ens.DSId0();
			String dsid=ens.DSId();
			String xtag=map.get(dsid0);
			if(xtag==null){
				
				//in cases, the DSID has been updated (e.g., updated T1/2 in DSID of decay datasets)
				String temp=findMatchedDSIDInXREFMap(dsid0,map);				
				if(temp.isEmpty()){
					xtag="?"+n;
					n++;	
				}else {
					xtag=map.get(temp);
					map.remove(temp);
				}
				//System.out.println(ens.DSId0()+" EnsdfGroup 168:  temp="+temp+" dsid="+dsid+" xtag="+xtag);

			}else
				map.remove(dsid0);
			
			datasetDSIDsV.add(dsid);
			datasetDSID0sV.add(dsid0);
			datasetXTagsV.add(xtag);
			
			/*
			//debug
			for(String s:datasetDSIDsV) {
				int index=datasetDSIDsV.indexOf(s);
				System.out.println("EnsdfGroup 178: DSID="+s+" xtag="+datasetXTagsV.get(index));
			}
			*/
		}
		
		return map0;
	}
	
	/*
	 * find the DSID in the XREF map of <DSID,XTAG> from XREF list in Adopted Levels,
	 * that matches the given dsid exactly or mostly (for the latter, in cases, the
	 * DSID has been updated, e.g., updated T1/2 in DSID of decay datasets)
	 */
	private String findMatchedDSIDInXREFMap(String dsid,HashMap<String,String> map){
		String match="";
		try{
			match=map.get(dsid);
			if(match!=null)
				return match;
			
			DatasetID DID=new DatasetID(dsid);
			Vector<String> similarDSIDs=new Vector<String>();
			float diff=-1,min=10000;
			for(String s:map.keySet()){	
				DatasetID tempDID=new DatasetID(s);
				if(DID.isSameDecayType(tempDID) && DID.decayTimeUnit.equals(tempDID.decayTimeUnit)) {
				//if(DID.isSameDSID(tempDID) || DID.isSameDecayType(tempDID)){
					if(Str.isNumeric(DID.decayTimeS)) {
						float t1=EnsdfUtil.s2f(DID.decayTimeS);
						float t2=EnsdfUtil.s2f(tempDID.decayTimeS);
						
						diff=-1;
						if(t1>0 && t2>0)
							diff=Math.abs(t1-t2);
						
						if(diff>=0 && diff<min) {
							min=diff;
						}
						
						if(min<Math.min(t1, t2)*0.5)
							similarDSIDs.add(s);
					}else
						similarDSIDs.add(s);
				}
			}
			
			int n=similarDSIDs.size();
			if(n==1)
				return similarDSIDs.get(0);
			else
				return "";

		}catch(Exception e){}
		
		return match;
	}
	
	 private HashMap<String,String> makeNewXTagMap(){		
		HashMap<String,String> map=new HashMap<String,String>(); //(DSID,XTag)
		Vector<String> xtags=EnsdfUtil.makeXTags(ensdfV);
		
		datasetDSIDsV.clear();
		datasetDSID0sV.clear();
		datasetXTagsV.clear();
		
		for(int i=0;i<xtags.size();i++){
			String dsid0=ensdfV.get(i).DSId0();
			String dsid=ensdfV.get(i).DSId();
			String xtag=xtags.get(i);
			
			map.put(dsid0,xtag);
			datasetDSID0sV.add(dsid0);
			datasetDSIDsV.add(dsid);
			datasetXTagsV.add(xtag);
		}

		return map;
	 }
	 
	 public Vector<Vector<RecordWrap>> getGammaListByGamma_old(){
		 Vector<Vector<RecordWrap>> out=new Vector<Vector<RecordWrap>>();
		 Vector<RecordWrap> temp=new Vector<RecordWrap>();
		 
		 for(int i=0;i<levelGroupsV.size();i++){
			 RecordGroup lg=levelGroupsV.get(i);
			 Vector<RecordGroup> gammaGroupsV=lg.subgroups();
			 if(!lg.hasSubGroups())
				 continue;
			 
			 for(int j=0;j<gammaGroupsV.size();j++){
				 RecordGroup gg=gammaGroupsV.get(j);
				 
				 for(int k=0;k<gg.recordsV().size();k++){
					Gamma g=(Gamma)gg.recordsV().get(0);
					String dsid=gg.dsidsV().get(k);
					String xtag=gg.xtagsV().get(k);
					
					ENSDF ens=getENSDFByDSID0(dsid);
					Level parent=null;
					Level daughter=null;
					
					try{
						parent=ens.levelAt(g.ILI());
						daughter=ens.levelAt(g.FLI());
					}catch (Exception e){
					}  
					
					RecordWrap rw=new RecordWrap(g,dsid,xtag,parent,daughter);
					
					//insert level in ascending order of energy
					int im=0;
					int i1=0,i2=temp.size()-1;
					float e=g.EF();
					float e1,e2,em;
					while(i1<=i2){
						im=(i1+i2)/2;
						e1=temp.get(i1).record.EF();
						e2=temp.get(i2).record.EF();
						em=temp.get(im).record.EF();
								
						if((e-e1)*(e-em)<0){
							i2=im-1;
							continue;
						}else if((e-em)*(e-e2)<0){
							i1=im+1;
							continue;
				    	}else if(e<=e1){
							im=i1+(int)(Math.pow(2,(e-e1)));
							break;
						}else if(e>=e2){
							im=i2+1;
							break;
						}else{//e==em
							im=im+1;
							break;
						}
					}
					temp.insertElementAt(rw,im);
				 }				 
			 }
		 }
		 
		 
		 return out;
	 }
	 
	 /*
	  * return a vector of Gamma groups in order of gamma energy
	  * (no change in the order of gammas in each group, which is in order
	  *  of associated datasets in the ENSDF file)
	  */
	 public Vector<RecordGroup> getGammaGroupsByGamma(){
		 Vector<RecordGroup> out=new Vector<RecordGroup>();
		 
		 out.addAll(unpGammaGroupsV);
		 
		 for(int i=0;i<levelGroupsV.size();i++){
			 RecordGroup lg=levelGroupsV.get(i);
			 if(!lg.hasSubGroups())
				 continue;		 
			 out.addAll(lg.subgroups());
		 }
		 
		 sortRecordGroups(out);
		 		 
		 return out;
	 }
	 
	 /*
	  * return a vector of Gamma groups in order of parent level energy
	  * (no change in the order of gammas in each group, which is in order
	  *  of associated datasets in the ENSDF file)
	  */
	 public Vector<RecordGroup> getGammaGroupsByLevel(){
		 Vector<RecordGroup> out=new Vector<RecordGroup>();
		 
		 out.addAll(unpGammaGroupsV);
		 
		 for(int i=0;i<levelGroupsV.size();i++){
			 RecordGroup lg=levelGroupsV.get(i);
			 if(!lg.hasSubGroups())
				 continue;	
			 
			 out.addAll(lg.subgroups());
		 }
		 	 		 
		 return out;
	 }
	 
	///////////////////////////////////
	// grouping functions
	///////////////////////////////////
    
    public void doGrouping() throws Exception{

	
    	if(adopted!=null){    
    		newDSIDXTagMap=makeNewXTagMap();//dsidsV,xtagsV also made in this call, so this one has to be called first	
    		dsidXTagMapFromAdopted=makeXTagMapFromAdopted();//dsidsV,xtagsV also made in this call	
    	}else{
    		dsidXTagMapFromAdopted=makeNewXTagMap();//dsidsV,xtagsV also made in this call	
    		newDSIDXTagMap.putAll(dsidXTagMapFromAdopted);;
    	}

    	groupUnpGammas();

    	groupLevels();

    }
    
    
    public void groupLevels() throws Exception{
    	Vector<RecordGroup> levelGroupsV=new Vector<RecordGroup>();
    	
    	if(adopted==null)
    		levelGroupsV=groupLevelsNoAdopted();
    	else {
            levelGroupsV=groupLevelsWithAdopted();
            
            //debug
            //for(RecordGroup levelGroup:levelGroupsV)
        	//	System.out.println("In ENSDFGroup line 382: first level="+levelGroup.recordsV().get(0).EF()+" tag="+levelGroup.xtagsV().get(0)+" dsid="+levelGroup.dsidsV().get(0));

    	}
    	
    	//at this stage, all levels are grouped and levels in each group are sorted by dataset tags,
    	//and the level from Adopted dataset is always the first one with xtag="" if available
    	
    	//clear up level groups
    	//there could be some level groups in which all member levels are multiply assigned. If initial reference dataset is adopted dataset,
    	//such level groups are newly made group when the member level first inserted to this group can not be grouped with any reference 
    	//level group. 
    	
    	removeGroupsWithNoFirmMembers(levelGroupsV);
    	
    	for(int i=0;i<levelGroupsV.size();i++){
    		RecordGroup levelGroup=((RecordGroup) levelGroupsV.get(i));
    		
    		firstLevelInGroupV.add((Level)levelGroup.recordsV().get(0));

    		//debug
    		//System.out.println("In ENSDFGroup line 390: first level="+levelGroup.recordsV().get(0).EF()+" tag="+levelGroup.xtagsV().get(0)+" dsid="+levelGroup.dsidsV().get(0));
    		
    		//re-order levels in each group according to the order they show up in the file
    		sortRecordsByDSID(levelGroup);

    		//debug
    		//System.out.println("In ENSDFGroup line 395: first level="+levelGroup.recordsV().get(0).EF()+" tag="+levelGroup.xtagsV().get(0)+" dsid="+levelGroup.dsidsV().get(0));

    		groupGammas(levelGroup);
    		
    		//TO DO
    		//groupDecays(levelGroup);
    		//groupDelays(levelGroup);
    	}

    	setLevelGroups(levelGroupsV);
    }
    
    /*
     * NOTE that after grouping, the each element in refGroupsV will change 
     * if new member is inserted into the group
     */
    public Vector<RecordGroup> groupLevelsByReferenceGroups(Vector<RecordGroup> refGroupsV) throws Exception{
    	Vector<RecordGroup> levelGroupsV=new Vector<RecordGroup>();  
    	
    	Vector<RecordGroup> newRefGroupsV=new Vector<RecordGroup>();//store the original groups with the first level in each groups from the refGroupsV

        if(refGroupsV==null)
        	return groupLevelsNoAdopted();
        else {
        	
        	/*
        	//debug
    		for(int k=0;k<refGroupsV.size();k++) {
    			RecordGroup levelGroup=refGroupsV.get(k);
    			float ef=levelGroup.getRecord(0).ERPF();
    			if(Math.abs(ef-15000)<200) {
        			System.out.println("1  ref group#="+k+" size="+levelGroup.nRecords());
        			for(int j=0;j<levelGroup.nRecords();j++)
        				System.out.println("   ref group:  level#="+j+" ES="+levelGroup.getRecord(j).ES()+" dsid="+levelGroup.getDSID(j));
    			}

    		}
    		*/
        	
        	for(int i=0;i<refGroupsV.size();i++) {
        		RecordGroup group=refGroupsV.get(i);
        		
        		/*
        		//debug
                float ef=group.getRecord(0).ERPF();
                if(Math.abs(ef-699)<2) {
                    System.out.println("  group#="+i+" size="+group.nRecords());
                    for(int j=0;j<group.nRecords();j++)
                        System.out.println("     level#="+j+" ES="+group.getRecord(j).ES()+" dsid="+group.getDSID(j));
                }
                */
        		
        		RecordGroup newGroup=new RecordGroup();
        		if(group.nRecords()>0) {
        			newGroup.addRecord(group.getRecord(0), group.getDSID(0),group.getXTag(0));

        			levelGroupsV.add(newGroup);
        			newRefGroupsV.add(newGroup);
        		}
        	}
        }
       
        /*
		//debug
		for(int k=0;k<refGroupsV.size();k++) {
			RecordGroup levelGroup=refGroupsV.get(k);
			float ef=levelGroup.getRecord(0).ERPF();
			if(Math.abs(ef-15000)<200) {
    			System.out.println("2  ref group#="+k+" size="+levelGroup.nRecords());
    			for(int j=0;j<levelGroup.nRecords();j++)
    				System.out.println("   ref group:  level#="+j+" ES="+levelGroup.getRecord(j).ES()+" dsid="+levelGroup.getDSID(j));
			}

		}
		*/
        

        for(int i=0;i<ensdfV.size();i++){
    		ENSDF ens=ensdfV.get(i);
    		String dsid=ens.DSId0();
    		
    		if(ens==adopted || dsid.contains("ADOPTED")) {
    			if(!ensJPIParserMap.containsKey(ens)) {
        			SpinParityParser jpiParser=new SpinParityParser(ens);
        			
        			//jpiParser.parseJPIsFromGammas();//altJPis of each level in ens is set in this call
        			jpiParser.parseJPIsFromGammas(true);//true for using altJPiS of each level
        			
        			ensJPIParserMap.put(ens, jpiParser);
    			}
    			continue;
    		}
    		
    		/*
    		//for transfer dataset only
    		boolean isTransfer=EnsdfUtil.parseJPIsFromLTransfer(ens);
    		if(!isTransfer && !ensJPIParserMap.containsKey(ens)) {
    			SpinParityParser jpiParser=new SpinParityParser(ens);
    			jpiParser.parseJPIsFromGammasAndDecays();//altJPis of each level in ens is set in this call
    			ensJPIParserMap.put(ens, jpiParser);
    			
    			//EnsdfUtil.parseJPIsFromGammas(ens);
    		}
    		*/
    		
    		if(!ensJPIParserMap.containsKey(ens)) {
    			SpinParityParser jpiParser=new SpinParityParser(ens);
    			jpiParser.parseJPIsFromAllData();//altJPis of each level in ens is set in this call
    			ensJPIParserMap.put(ens, jpiParser);
    			
    			//EnsdfUtil.parseJPIsFromGammas(ens);
    		}
    		//debug
    		//System.out.println("#########################1 line 424 ############## +dsid="+dsid);
    		
    		/*
    		//debug
    		if(ens.DSId0().contains("1H(14O,13N)")) {
        		System.out.println("####1 ENSDF dsid="+ens.DSId0());
        		for(int k=0;k<levelGroupsV.size();k++) {
        			RecordGroup levelGroup=levelGroupsV.get(k);
        			float ef=levelGroup.getRecord(0).ERPF();
        			if(Math.abs(ef-15000)<200) {
            			System.out.println("  group#="+k+" size="+levelGroup.nRecords());
            			for(int j=0;j<levelGroup.nRecords();j++)
            				System.out.println("     level#="+j+" ES="+levelGroup.getRecord(j).ES()+" dsid="+levelGroup.getDSID(j));
        			}

        		}
    		}
            */

    		////////////////////////////////////////////////////
    		insertLevelsToReferenceGroups(ens, levelGroupsV);    

    		/*
    		//debug
    		if(ens.DSId0().contains("1H(14O,13N)")) {
        		System.out.println("####2 ENSDF dsid="+ens.DSId0());
        		for(int k=0;k<levelGroupsV.size();k++) {
        			RecordGroup levelGroup=levelGroupsV.get(k);
        			float ef=levelGroup.getRecord(0).ERPF();
        			if(Math.abs(ef-15000)<200) {
            			System.out.println("  group#="+k+" size="+levelGroup.nRecords());
            			for(int j=0;j<levelGroup.nRecords();j++)
            				System.out.println("     level#="+j+" ES="+levelGroup.getRecord(j).ES()+" dsid="+levelGroup.getDSID(j));
        			}

        		}
    		}
    		*/
    		

    	}
    		
    	//debug
    	//for(RecordGroup g:levelGroupsV){
    	//	if(Math.abs(g.recordsV().get(0).EF()-74)<1){
    	//		for(int i=0;i<g.recordsV().size();i++)
    	//			System.out.println("In EnsdfGroup line 353: ES="+g.recordsV().get(i).ES()+" DSID="+g.getDSID(i)+" xtag="+g.getXTag(i));
    	//	}    			
    	//}
        
    	//remove the reference level in each level group that counted twice, which happens if the original reference group
        //contains no Adopted level but a level from individual dataset (meaning this level is a new level not in Adopted yet)
        //NOTE this will also affect the same groups in levelGroupsV, which is the purpose here.
        //There could be new groups created during the grouping and inserting processes, in addition to the original groups.
        //For those groups, nothing should be removed.
        for(int i=0;i<newRefGroupsV.size();i++) {
        	RecordGroup group=newRefGroupsV.get(i);
        	if(group.nRecords()>1 && !group.getDSID(0).contains("ADOPTED"))
        		group.remove(0);
    	}
        //System.out.println("EnsdfGroup 644 ###########");
        findPossibleJPIsForAdopted(levelGroupsV);//altJPis in each adopted level will be set if applicable
        
    	return levelGroupsV;
    }
    
    @SuppressWarnings("unused")
	private void findPossibleJPIsForAdopted(Vector<RecordGroup> levelGroupsV) {
    	try {
    		if(!(levelGroupsV.get(0).getRecord(0) instanceof Level))
    			return;
    		
    		//for dataset which has any level used as the reference level where 
    		//there is no corresponding adopted level in adopted dataset
    		HashMap<ENSDF,Vector<String>> ensOriginalAltJPSMap=new HashMap<ENSDF,Vector<String>>();

    		//adopted (or reference) levels are not necessairly from the same dataset (Adopted dataset)
    		Vector<String> adoptedJPSV=new Vector<String>();
    		
    		SpinParityParser adoptedJPIParser=null;
    		ENSDF adoptedDataset0=null;

    		for(RecordGroup rg:levelGroupsV) {    		
    			String adoptedDSID="";
    			Level adoptedLev=(Level)rg.getAdoptedRecord();
    			boolean isRefLev=false;
    			if(adoptedLev==null) {
    				adoptedLev=(Level)rg.getReferenceRecord();
    				isRefLev=true;
    			}
    			
    			if(adoptedLev==null) {
    				adoptedJPSV.add("");
    				continue;
    			}
                
    			int index=rg.recordsV().indexOf(adoptedLev);
    			adoptedDSID=rg.dsidsV().get(index);
    			ENSDF adopted=getENSDFByDSID(adoptedDSID);
    			
    			if(isRefLev && ensOriginalAltJPSMap.containsKey(adopted)) {	    			
					Vector<String> altJPSV=new Vector<String>();
					for(int i=0;i<adopted.levelsV().size();i++) {
						Level lev=adopted.levelAt(i);
						altJPSV.add(lev.altJPiS());
					}
					ensOriginalAltJPSMap.put(adopted, altJPSV);
    			}
    			//Note that here adopted may be the actual adopted dataset or a reference dataset 
    			//when there is no adopted record in the current level group. So adoptedJPIParser
    			//for different levels is not necessarily the same
    			
    			if(adoptedJPIParser==null && !isRefLev) {
    				adoptedJPIParser=ensJPIParserMap.get(adopted);
    				adoptedDataset0=adopted;
    				
    				/*
    				//debug
    				for(int i=0;i<adoptedDataset0.levelsV().size();i++) {
    					Level lev=adoptedDataset0.levelAt(i);
    					if(lev.ES().equals("2386.9"))
    						System.out.println("EnsdfGroup 703: DSID="+adoptedDataset0.DSId()+" lev="+lev.ES()+" lev.altJPiS()="+lev.altJPiS()+" lev JPI="+lev.JPiS());
    				}
    				*/
    			}
    			String adoptedAltJPS="";
    			 			
    			//if(adoptedLev.ES().equals("2386.9"))
    			//	System.out.println("EnsdfGroup 709: DSID="+adopted.DSId()+" lev="+adoptedLev.ES()+" lev altJPI="+adoptedLev.altJPiS()+" lev JPI="+adoptedLev.JPiS()+" adopted altJPS="+adoptedAltJPS);
    			
    			@SuppressWarnings("unused")
				int count=0;
    			for(ENSDF ens:this.ensJPIParserMap.keySet()) {
    				//if(ens==adopted)
    				//	continue;

    				Level lev=(Level)rg.getRecordByDSID(ens.DSId0());  	
    				if(lev==null)
    					continue;
    				
    				/*
    				if(adoptedLev.ES().equals("597.1")) {
        				if(count==0)
        					System.out.println("###################");
            			System.out.println("EnsdfGroup 723: DSID="+ens.DSId()+" lev="+lev.ES()+" lev.altJPiS()="+lev.altJPiS()+" lev JPI="+lev.JPiS()+" adopted altJPS="+adoptedAltJPS);
            			System.out.println("   ensJPIParserMap size="+ensJPIParserMap.size()+"     "+count);
    				}
    				*/
    				
        			//debug
    				//if(adoptedLev.ES().equals("197.272"))
    				//	System.out.println("EnsdfGroup 735: adoptedAltJPS="+adoptedAltJPS+ "  adopted Lev altJPIS="+adoptedLev.altJPiS()+" lev="+lev.ES()+" dsid="+ens.DSId0()+" lev altJPIS="+lev.altJPiS());

        			
    				adoptedAltJPS=JPI.mergeJPS(adoptedAltJPS, lev.altJPiS(), false);//false for "AND" operation
    				
        			//debug
    				//if(adoptedLev.ES().equals("197.272"))
    				//	System.out.println("EnsdfGroup 745: adoptedAltJPS="+adoptedAltJPS+ "adopted Lev="+adoptedLev.altJPiS()+" lev altJPIS="+lev.altJPiS());
    				/*
    				if(adoptedLev.ES().equals("597.1")) {
        				if(count==0)
        					System.out.println("###################");
            			System.out.println("EnsdfGroup 739: DSID="+ens.DSId()+" lev="+lev.ES()+" lev.altJPiS()="+lev.altJPiS()+" lev JPI="+lev.JPiS()+" adopted altJPS="+adoptedAltJPS);
            			System.out.println("   ensJPIParserMap size="+ensJPIParserMap.size()+"     "+count);
    				}
    				*/
        			
        			count++;
    			}

    			/*
    			//debug
    			for(int i=0;i<adopted.levelsV().size();i++) {
    				Level lev=adopted.levelAt(i);
    				if(lev.ES().equals("197.272"))
    					System.out.println("EnsdfGroup 758: rg group adopted Lev="+adoptedLev.ES()+" lev="+lev.ES()+" lev.altJPiS()="+lev.altJPiS()+" lev JPI="+lev.JPiS());
    			}
    			*/
    			
    			if(adoptedAltJPS==null)
    				adoptedAltJPS="NA";
    			
    			adoptedJPSV.add(adoptedAltJPS);
    			
       			//if(adoptedLev.ES().equals("597.1"))
    			//	System.out.println("EnsdfGroup 738: DSID="+adopted.DSId()+" lev="+adoptedLev.ES()+" lev altJPI="+adoptedLev.altJPiS()+" lev JPI="+adoptedLev.JPiS()+" adopted altJPS="+adoptedAltJPS);
       			
    			adoptedLev.setAltJPiS(adoptedAltJPS);   
    			
    			/*
    			//debug
    			for(int i=0;i<adopted.levelsV().size();i++) {
    				Level lev=adopted.levelAt(i);
    				if(lev.ES().equals("197.272"))
    					System.out.println("EnsdfGroup 774: rg group adopted Lev="+adoptedLev.ES()+" lev="+lev.ES()+" lev.altJPiS()="+lev.altJPiS()+" lev JPI="+lev.JPiS());
    			}
    			*/
    			//if(adoptedLev.ES().equals("597.1"))
    			//	System.out.println("EnsdfGroup 743: DSID="+adopted.DSId()+" lev="+adoptedLev.ES()+" lev JPI="+adoptedLev.JPiS()+" adopted altJPS="+adoptedAltJPS);
    		}
			
    		/*
			//debug
			for(int i=0;i<adoptedDataset0.levelsV().size();i++) {
				Level lev=adoptedDataset0.levelAt(i);
				if(lev.ES().equals("197.272"))
					System.out.println("EnsdfGroup 744: DSID="+adoptedDataset0.DSId()+" lev="+lev.ES()+" lev.altJPiS()="+lev.altJPiS()+" lev JPI="+lev.JPiS());
			}
			*/
			
    		
    		//set AltJPiS of reference dataset to be adoptedAltJPS
    		for(ENSDF ens:ensOriginalAltJPSMap.keySet()) {
				for(int i=0;i<ens.levelsV().size();i++) {
					Level lev=ens.levelAt(i);
					lev.setAltJPiS(adoptedJPSV.get(i));
				}
				
				SpinParityParser jpiParser=this.ensJPIParserMap.get(ens);
				jpiParser.parseJPIsFromGammas(true);
				
    		}
    		
    		//parse JPI from gammas in Adopted dataset
    		if(adoptedJPIParser!=null) {
			
    			/*
				//debug
				for(int i=0;i<adoptedDataset0.levelsV().size();i++) {
					Level lev=adoptedDataset0.levelAt(i);
					if(lev.ES().equals("597.1"))
						System.out.println("EnsdfGroup 736: DSID="+adoptedDataset0.DSId()+" lev="+lev.ES()+" lev.altJPiS()="+lev.altJPiS()+" lev JPI="+lev.JPiS());
				}
				*/
    			
    			//System.out.println("EnsdfGroup 795: ######");
    			adoptedJPIParser.parseJPIsFromGammas(true);
    			//System.out.println("EnsdfGroup 797: ######");
    			
    			/*
				//debug
				for(int i=0;i<adoptedDataset0.levelsV().size();i++) {
					Level lev=adoptedDataset0.levelAt(i);
					if(lev.ES().equals("597.1"))
						System.out.println("EnsdfGroup 740: DSID="+adoptedDataset0.DSId()+" lev="+lev.ES()+" lev.altJPiS()="+lev.altJPiS()+" lev JPI="+lev.JPiS());
				}
				*/
    		}
    		
    		//restore original AltJPiS of reference dataset
    		for(ENSDF ens:ensOriginalAltJPSMap.keySet()) {
    			Vector<String> jpsV=ensOriginalAltJPSMap.get(ens);
				for(int i=0;i<ens.levelsV().size();i++) {
					Level lev=ens.levelAt(i);
					lev.setAltJPiS(jpsV.get(i));
					
					//if(lev.ES().equals("597.1"))
					//	System.out.println("EnsdfGroup 817: DSID="+adoptedDataset0.DSId()+" lev="+lev.ES()+" lev.altJPiS()="+lev.altJPiS()+" lev JPI="+lev.JPiS());
				}
    		}
    		
    		
    	}catch(Exception e) {
    		e.printStackTrace();
    	}
    }
    private Vector<RecordGroup> groupLevelsWithDataset(ENSDF dataset) throws Exception{
    	Vector<RecordGroup> levelGroupsV=new Vector<RecordGroup>();    	
    	if(dataset==null)
    		return levelGroupsV;
    	
		//System.out.println("#########################2 line 455 ############## dataset dsid="+dataset.DSId0()+" ensdfV.size()="+ensdfV.size());

    	insertLevelsToReferenceGroups(dataset, levelGroupsV);
        
    	/*
        //debug
        for(int i=0;i<levelGroupsV.size();i++){
            RecordGroup g=levelGroupsV.get(i);
            //if(Math.abs(g.recordsV().get(0).EF()-15000)<200){
                //for(int j=0;j<g.recordsV().size();j++)
            int j=0;
                    System.out.println("#1 In EnsdfGroup line 563: group="+i+" ES="+g.recordsV().get(j).ES()+" DSID="+g.getDSID(j)+" xtag="+g.getXTag(j));
            //}               
        }
        */
    	
    	for(int i=0;i<ensdfV.size();i++){
    		ENSDF ens=ensdfV.get(i);
    		String dsid=ens.DSId0();
    		
    		//System.out.println(" #1 i="+i+" dsid="+dsid+" ensdfV.size()="+ensdfV.size());
    		/*
    		boolean isTransfer=EnsdfUtil.parseJPIsFromLTransfer(ens); 		
    		if(dataset==ens || dataset.DSId0().equals(dsid)) {
        		if(!isTransfer && !ensJPIParserMap.containsKey(ens)) {
        			SpinParityParser jpiParser=new SpinParityParser(ens);
        			jpiParser.parseJPIsFromGammasAndDecays();//altJPis of each level in ens is set in this call
        			ensJPIParserMap.put(ens, jpiParser);
        		}
    			continue;
    		}
    		*/
    		
    		if(dataset==ens || dataset.DSId0().equals(dsid)) {
        		if(!ensJPIParserMap.containsKey(ens)) {
        			SpinParityParser jpiParser=new SpinParityParser(ens);
        			jpiParser.parseJPIsFromAllData();//altJPis of each level in ens is set in this call
        			ensJPIParserMap.put(ens, jpiParser);
        		}
    			continue;
    		}
    		
            //System.out.println(" #2 dsid="+dsid);
            
    		/*
    		//for non-transfer dataset only
    		if(!isTransfer && !ensJPIParserMap.containsKey(ens)) {
    			SpinParityParser jpiParser=new SpinParityParser(ens);
    			jpiParser.parseJPIsFromGammasAndDecays();//altJPis of each level in ens is set in this call
    			ensJPIParserMap.put(ens, jpiParser);
    			
    			//EnsdfUtil.parseJPIsFromGammas(ens);
    		}
    		*/
    		
    		if(!ensJPIParserMap.containsKey(ens)) {
    			SpinParityParser jpiParser=new SpinParityParser(ens);
    			jpiParser.parseJPIsFromAllData();//altJPis of each level in ens is set in this call
    			ensJPIParserMap.put(ens, jpiParser);
    		}
    		
    		//System.out.println("#########################3 EnsdfGroup line 470 ############## i="+i+" dsid="+dsid);

    		insertLevelsToReferenceGroups(ens, levelGroupsV);   
    		
    		//testing
    		/*
    		if(ens.DSId().contains("B+") ||ens.DSId().contains("B-")||ens.DSId().contains("EC")) {
    			Level pLev=ens.parentAt(0).level();
    			float jf=EnsdfUtil.spinValue(pLev.JPiS());
    			if(jf>5)
    				continue;
    			
    			for(Level l:ens.levelsV()) {
    				if(!l.JPiS().isEmpty() || l.EF()==0)
    					continue;
    				
    				for(RecordGroup g:levelGroupsV) {
    					if(Math.abs(l.EF()-g.getMeanEnergy())<20) {
    						System.out.println("Decay in "+ens.nucleus().nameENSDF()+": "+ens.DSId()+" EL="+l.ES());
    						for(Record r:g.recordsV()) {
    							int index=g.recordsV().indexOf(r);
    							String dsid1=g.dsidsV().get(index);
    							if(dsid1.equals(ens.DSId()))
    								continue;
    							System.out.println("   "+dsid1+"  EL="+r.ES()+" JPI="+((Level)r).JPiS());
    						}
    					}
    				}
    			}
    		}
    		*/
    		
    		/*
    		//testing
   			for(Level l:ens.levelsV()) {
				if(l.JPiS().isEmpty() || l.EF()==0)
					continue;
    	
    			float jf=EnsdfUtil.spinValue(l.JPiS());
    			if(jf<5)
    				continue;
    			
				for(RecordGroup g:levelGroupsV) {
		
					boolean hasMatch=false;
					for(String dsid1:g.dsidsV()) {
			    		if(dsid1.contains("B+") || dsid1.contains("B-")|| dsid1.contains("EC")) {
			    			int index=g.dsidsV().indexOf(dsid1);
			    			Level lev=(Level)g.recordsV().get(index);
			    			
							if(Math.abs(l.EF()-lev.EF())<5 && lev.JPiS().isEmpty()) {
								hasMatch=true;
								break;
							}
			    		}
					}

					if(hasMatch) {
						System.out.println("**** "+ens.DSId()+"  EL="+l.ES()+"  JPI="+l.JPiS());
						for(Record r:g.recordsV()) {
							int index=g.recordsV().indexOf(r);
							String dsid2=g.dsidsV().get(index);
							System.out.println("   "+dsid2+"  EL="+r.ES()+" JPI="+((Level)r).JPiS());
						}
					}
				}
			}
            */
    		
    		/*
    		//debug
    		System.out.println("In ENSDFGroup line 593 group ENSDF: dsid="+ens.DSId0());
            for(RecordGroup levelGroup:levelGroupsV) {
            	double EF=levelGroup.recordsV().get(0).EF();
            	if(EF>3900 && EF<4000)
        		   System.out.println("  In level group: first level="+EF+" tag="+levelGroup.xtagsV().get(0)+" dsid="+levelGroup.dsidsV().get(0));
            }
            */
    		
            //System.out.println(" #3 dsid="+dsid);
    		
    		/*
    	    //debug
            for(int k=0;k<levelGroupsV.size();k++){
                RecordGroup g=levelGroupsV.get(k);
                //if(Math.abs(g.recordsV().get(0).EF()-15000)<200){
                    for(int j=0;j<g.recordsV().size();j++)
                        System.out.println("@@@ i="+i+" In EnsdfGroup line 593: group="+k+" ES="+g.recordsV().get(j).ES()+" DSID="+g.getDSID(j)+" xtag="+g.getXTag(j));
                //}               
            }
            */
    	}
    	
    	/*
    	//debug
    	for(int i=0;i<levelGroupsV.size();i++){
    	    RecordGroup g=levelGroupsV.get(i);
    		//if(Math.abs(g.recordsV().get(0).EF()-699)<1)
    		//	for(int j=0;j<g.recordsV().size();j++)
    	    int j=0;
    				System.out.println("#2 In EnsdfGroup line 353: group="+i+" ES="+g.recordsV().get(j).ES()+" DSID="+g.getDSID(j)+" xtag="+g.getXTag(j));
    		   			
    	}
    	*/
    	
    	/*
		//debug
    	if(dataset.DSId0().contains("ADOPTED")) {
    		for(int k=0;k<levelGroupsV.size();k++) {
    			RecordGroup levelGroup=levelGroupsV.get(k);
    			float ef=levelGroup.getRecord(0).ERPF();
    			if(Math.abs(ef-12107)<1) {
        			System.out.println("  group#="+k+" size="+levelGroup.nRecords());
        			for(int j=0;j<levelGroup.nRecords();j++)
        				System.out.println("     level#="+j+" ES="+levelGroup.getRecord(j).ES()+" dsid="+levelGroup.getDSID(j));
    			}

    		}
    	}
    	*/

    	//System.out.println("EnsdfGroup 1013 ###########");   	
    	//findPossibleJPIsForAdopted(levelGroupsV);//altJPis in each adopted level will be set if applicable
    	
    	return levelGroupsV;
    }
    
    /*
     * group levels of individual datasets with respect to levels in Adopted dataset 
     * if it is present in the ENSDF file
     */
	private Vector<RecordGroup> groupLevelsWithAdopted() throws Exception{
 
		
		//NOTE: here are two grouping procedures
		//Step1: group individual datasets one by one with adopted levels
		//       In this procedure, new level groups could be created from the later datasets,
		//       for which there could be also levels from earlier datasets that could be 
		//       included in the new groups, but were not yet, because those earlier datasets have
		//       already been processed. So the second grouping is needed
		//Steps: use the new levels groups generated from the first grouping, which could have
		//       newly created groups that are not in Adopted dataset yet. Then repeat
		//       step 1 with the new reference level groups
		
	   	//return groupLevelsWithDataset(adopted);
		
	    Vector<RecordGroup> out=groupLevelsWithDataset(adopted);

		
        //for(RecordGroup levelGroup:out)
    	//	System.out.println("In ENSDFGroup line 658: first level="+levelGroup.recordsV().get(0).EF()+" tag="+levelGroup.xtagsV().get(0)+" dsid="+levelGroup.dsidsV().get(0));

        
	    /*
	    //debug
		for(int k=0;k<out.size();k++) {
			RecordGroup levelGroup=out.get(k);
			float ef=levelGroup.getRecord(0).ERPF();
			//if(Math.abs(ef-34507)<200) {
    			System.out.println("EnsdfGroup 639  ref group#="+k+" size="+levelGroup.nRecords()+" ef="+ef);
    			for(int j=0;j<levelGroup.nRecords();j++)
    				System.out.println("   ref group:  level#="+j+" ES="+levelGroup.getRecord(j).ES()+" dsid="+levelGroup.getDSID(j));
			//}

		}
		*/
	    
	    System.out.println("##### In EnsdfGroup 712: done grouping with Adopted dataset of "+this.NUCID);
	    
	    /*
        int count=0;
        for(int i=0;i<out.size();i++){
            RecordGroup group=out.get(i);
            if( Math.abs(group.getMeanEnergy()-7500)<40) {
            //if(!isGamma && dsid.contains("16O(3HE,6HE)")) {
                if(count==0) System.out.println("########### EnsdfGroup 718:");  
                count++;
                System.out.println("  *** original Records in group:"+i);
                for(int k=0;k<group.nRecords();k++) 
                    System.out.println("      k="+k+"  "+group.recordsV().get(k).ES()+"  "+group.dsidsV().get(k));                  
            }
        }
        */
	    
	    out=groupLevelsByReferenceGroups(out);
	    
	    System.out.println("##### In EnsdfGroup 716: done grouping with reference record groups created from Adopted dataset");
	      
	    /*
        count=0;
        for(int i=0;i<out.size();i++){
            RecordGroup group=out.get(i);
            if( Math.abs(group.getMeanEnergy()-7500)<40) {
            //if(!isGamma && dsid.contains("16O(3HE,6HE)")) {
                if(count==0) System.out.println("########### EnsdfGroup 742:");  
                count++;
                System.out.println("  *** original Records in group:"+i);
                for(int k=0;k<group.nRecords();k++) 
                    System.out.println("      k="+k+"  "+group.recordsV().get(k).ES()+"  "+group.dsidsV().get(k));                  
            }
        }
        */
	    
    	return out;
    }
    
    /*
     * group levels of individual datasets according to matching criteria for levels and gammas
     * without Adopted levels for reference when an Adopted dataset is not present  
     */
    @SuppressWarnings("rawtypes")
	private Vector<RecordGroup> groupLevelsNoAdopted() throws Exception{
    	Vector<RecordGroup> levelGroupsV=new Vector<RecordGroup>();
    	Vector<Vector> levelsVV=new Vector<Vector>();
    		
    	
    	for(int i=0;i<ensdfV.size();i++){
    		ENSDF ens=ensdfV.get(i);
    		
    		/*
    		//for transfer dataset only
    		boolean isTransfer=EnsdfUtil.parseJPIsFromLTransfer(ens);
    		if(!isTransfer && !ensJPIParserMap.containsKey(ens)) {
    			SpinParityParser jpiParser=new SpinParityParser(ens);
    			jpiParser.parseJPIsFromGammasAndDecays();//altJPis of each level in ens is set in this call
    			ensJPIParserMap.put(ens, jpiParser);
    			
    			//EnsdfUtil.parseJPIsFromGammas(ens);
    		}
    		*/
    		if(!ensJPIParserMap.containsKey(ens)) {
    			SpinParityParser jpiParser=new SpinParityParser(ens);
    			jpiParser.parseJPIsFromAllData();//altJPis of each level in ens is set in this call
    			ensJPIParserMap.put(ens, jpiParser);
    		}
    		
    		levelsVV.add(ens.levelsV());
    	}
    			
    	//roughly group levels to different groups based on an given energy interval
    	//Note that: multiple levels from a same dataset could fall into a same group
    	//           find grouping will be done in the next step.
    	levelGroupsV=doQuickGrouping(levelsVV,datasetDSID0sV,datasetXTagsV,deltaEL);
		
       	    	
    	//check and fine group levels in each level group from above based on other criteria, like:
    	//   if JPI overlaps
    	//   if multiple levels from same dataset 
    	Vector<RecordGroup> tempGroupsV=new Vector<RecordGroup>();
    	for(int i=0;i<levelGroupsV.size();i++){
    		RecordGroup levelGroup=((RecordGroup) levelGroupsV.get(i));
            tempGroupsV.addAll(doFineGrouping(levelGroup));
    	}   	
    	
    	levelGroupsV.clear();
    	levelGroupsV.addAll(tempGroupsV);
    	
    	findPossibleJPIsForAdopted(levelGroupsV);//altJPis in each adopted level will be set if applicable
    	
    	return levelGroupsV;
    }
    
    /*
     * group gammas from all levels in a level group that are already grouped 
     * from different datasets as corresponding to a same level. So it is 
     * important that the level grouping must be assured to be correct before
     * this call.
     */
    @SuppressWarnings("rawtypes")
	public Vector<RecordGroup> groupGammas(RecordGroup levelGroup) throws Exception{
    	Vector<RecordGroup> gammaGroupsV=new Vector<RecordGroup>();
    	
    	Vector<Vector> gammasVV=new Vector<Vector>();
    	Vector<String> dsidsV=levelGroup.dsidsV();	
    	Vector<String> xtagsV=levelGroup.xtagsV();
    	
    	
    	try{
        	for(int i=0;i<levelGroup.nRecords();i++){
        		Level lev=(Level)levelGroup.recordsV().get(i);
        		gammasVV.add(lev.GammasV());
        		
        		/*
        		//debug
        		if(Math.abs(lev.EF()-4430)<10){
        			System.out.println("i="+i+" level="+lev.ES());
        			for(Gamma g:lev.GammasV())
        				System.out.println("In EnsdfGroup line 658: i="+i+" gamma="+g.ES());
        		}
        		*/
        			
        	}
    	}catch(Exception e){
    		return gammaGroupsV;
    	}

    	
    	gammaGroupsV=(Vector<RecordGroup>)doQuickGrouping(gammasVV,dsidsV,xtagsV,deltaEG);
            
        //debug
        //if(Math.abs(levelGroup.recordsV().get(0).EF()-3250)<40){
        //  System.out.println("In EnsdfGroup line 681: gamma group  size="+gammaGroupsV.size());
        //}
    	
    	//check and process gammasVV to get final gammaGroupsV 
    	Vector<RecordGroup> tempGroupsV=new Vector<RecordGroup>();
    	for(int i=0;i<gammaGroupsV.size();i++){

    		RecordGroup gammaGroup=((RecordGroup) gammaGroupsV.get(i));
            tempGroupsV.addAll(doFineGrouping(gammaGroup));

        	//debug
        	//if(levelGroup.getAdoptedRecord().ES().contains("251.54")){
            //    System.out.println("##### try #"+i);
        	//	for(int m=0;m<tempGroupsV.size();m++){
        	//		System.out.println("  In EnsdfGroup line 448: gamma group"+m);
        	//		for(int n=0;n<tempGroupsV.get(m).nRecords();n++)
        	//			System.out.println("      gamma#"+n+"="+tempGroupsV.get(m).getRecord(n).ES());
        	//	}
        	//}
        	
        	/*
    		//debug
            int m=0;
    		if(Math.abs(gammaGroup.recordsV().get(0).EF()-4400)<50){
    			for(Record r:gammaGroup.recordsV()){
    				System.out.println(" ***EnsdfGroup 693: levelGroup.recordsV().get(0)="+levelGroup.recordsV().get(0).ES()+"  i="+i+" m="+m+" ES="+r.ES()+" ID="+gammaGroup.getDSID(m)+" tag="+gammaGroup.getXTag(m));
    				m++;
    			}
    		}
    		*/
    		
    	}   	
    	
        
		//debug
		//if(Math.abs(levelGroup.recordsV().get(0).EF()-74)<1){
    	//	System.out.println("In EnsdfGroup line 433: temp size="+tempGroupsV.size()+"  size="+gammaGroupsV.size());
		//}
		
    	gammaGroupsV.clear();
    	gammaGroupsV.addAll(tempGroupsV);
    	
    	removeGroupsWithNoFirmMembers(gammaGroupsV);
    	
    	levelGroup.subgroups().clear();
    	for(int i=0;i<gammaGroupsV.size();i++){   	
    	    RecordGroup gammaGroup=gammaGroupsV.get(i);
    		sortRecordsByDSID(gammaGroup);   		
    		levelGroup.subgroups().addElement(gammaGroup);
    		
    		/*
            //debug
            int m=0;
            if(Math.abs(gammaGroup.recordsV().get(0).EF()-1940)<20){
                for(Record r:gammaGroup.recordsV()){
                    System.out.println(" *** EnsdfGroup 720: levelGroup.recordsV().get(0)="+levelGroup.recordsV().get(0).ES()+"  i="+i+" m="+m+" ES="+r.ES()+" ID="+gammaGroup.getDSID(m)+" tag="+gammaGroup.getXTag(m));
                    m++;
                }
            }
            */
            
    	}
    	
    	//levelGroup.setSubGroups(gammaGroupsV);

    	return gammaGroupsV;
    }
   
	    
    /*
     * group unplaced gammas from datasets
     */
    @SuppressWarnings("rawtypes")
	public Vector<RecordGroup> groupUnpGammas() throws Exception{
    	
    	Vector<Vector> gammasVV=new Vector<Vector>();  	
    
    	for(int i=0;i<ensdfV.size();i++){
    		ENSDF ens=ensdfV.get(i);
    		gammasVV.add(ens.unpGammas());
    	}
    	
    	unpGammaGroupsV=(Vector<RecordGroup>)doQuickGrouping(gammasVV,datasetDSID0sV,datasetXTagsV,deltaEG);
            
    	//check and process gammasVV to get final gammaGroupsV 
    	Vector<RecordGroup> tempGroupsV=new Vector<RecordGroup>();
    	for(int i=0;i<unpGammaGroupsV.size();i++){
    		RecordGroup gammaGroup=((RecordGroup) unpGammaGroupsV.get(i));
            tempGroupsV.addAll(doFineGrouping(gammaGroup));
    	}   	
    	
    	unpGammaGroupsV.clear();
    	unpGammaGroupsV.addAll(tempGroupsV);
    	
    	for(int i=0;i<unpGammaGroupsV.size();i++){
    		sortRecordsByDSID(unpGammaGroupsV.get(i));   		
    	}

    	return unpGammaGroupsV;
    }
    
    //TO DO
	@SuppressWarnings("rawtypes")
    public Vector<RecordGroup> groupDecays(RecordGroup levelGroup) throws Exception{
    	Vector<RecordGroup> gammaGroupsV=new Vector<RecordGroup>();
    	
    	Vector<Vector> gammasVV=new Vector<Vector>();
    	Vector<String> dsidsV=levelGroup.dsidsV();	
    	Vector<String> xtagsV=levelGroup.xtagsV();
    	
    	
    	try{
        	for(int i=0;i<levelGroup.nRecords();i++){
        		Level lev=(Level)levelGroup.recordsV().get(i);
        		gammasVV.add(lev.GammasV());
        		
        		/*
        		//debug
        		if(Math.abs(lev.EF()-4430)<10){
        			System.out.println("i="+i+" level="+lev.ES());
        			for(Gamma g:lev.GammasV())
        				System.out.println("In EnsdfGroup line 658: i="+i+" gamma="+g.ES());
        		}
        		*/
        			
        	}
    	}catch(Exception e){
    		return gammaGroupsV;
    	}

    	
    	gammaGroupsV=(Vector<RecordGroup>)doQuickGrouping(gammasVV,dsidsV,xtagsV,deltaEG);
            
        //debug
        //if(Math.abs(levelGroup.recordsV().get(0).EF()-3250)<40){
        //  System.out.println("In EnsdfGroup line 681: gamma group  size="+gammaGroupsV.size());
        //}
    	
    	//check and process gammasVV to get final gammaGroupsV 
    	Vector<RecordGroup> tempGroupsV=new Vector<RecordGroup>();
    	for(int i=0;i<gammaGroupsV.size();i++){

    		RecordGroup gammaGroup=((RecordGroup) gammaGroupsV.get(i));
            tempGroupsV.addAll(doFineGrouping(gammaGroup));

        	//debug
        	//if(levelGroup.getAdoptedRecord().ES().contains("251.54")){
            //    System.out.println("##### try #"+i);
        	//	for(int m=0;m<tempGroupsV.size();m++){
        	//		System.out.println("  In EnsdfGroup line 448: gamma group"+m);
        	//		for(int n=0;n<tempGroupsV.get(m).nRecords();n++)
        	//			System.out.println("      gamma#"+n+"="+tempGroupsV.get(m).getRecord(n).ES());
        	//	}
        	//}
        	
        	/*
    		//debug
            int m=0;
    		if(Math.abs(gammaGroup.recordsV().get(0).EF()-4400)<50){
    			for(Record r:gammaGroup.recordsV()){
    				System.out.println(" ***EnsdfGroup 693: levelGroup.recordsV().get(0)="+levelGroup.recordsV().get(0).ES()+"  i="+i+" m="+m+" ES="+r.ES()+" ID="+gammaGroup.getDSID(m)+" tag="+gammaGroup.getXTag(m));
    				m++;
    			}
    		}
    		*/
    		
    	}   	
    	
        
		//debug
		//if(Math.abs(levelGroup.recordsV().get(0).EF()-74)<1){
    	//	System.out.println("In EnsdfGroup line 433: temp size="+tempGroupsV.size()+"  size="+gammaGroupsV.size());
		//}
		
    	gammaGroupsV.clear();
    	gammaGroupsV.addAll(tempGroupsV);
    	
	
    	levelGroup.subgroups().clear();
    	for(int i=0;i<gammaGroupsV.size();i++){   	
    	    RecordGroup gammaGroup=gammaGroupsV.get(i);
    		sortRecordsByDSID(gammaGroup);   		
    		levelGroup.subgroups().addElement(gammaGroup);
    		
    		/*
            //debug
            int m=0;
            if(Math.abs(gammaGroup.recordsV().get(0).EF()-1940)<20){
                for(Record r:gammaGroup.recordsV()){
                    System.out.println(" *** EnsdfGroup 720: levelGroup.recordsV().get(0)="+levelGroup.recordsV().get(0).ES()+"  i="+i+" m="+m+" ES="+r.ES()+" ID="+gammaGroup.getDSID(m)+" tag="+gammaGroup.getXTag(m));
                    m++;
                }
            }
            */
            
    	}
    	
    	//levelGroup.setSubGroups(gammaGroupsV);

    	return gammaGroupsV;
    }
	
	
	//TO DO
	@SuppressWarnings("rawtypes")
    public Vector<RecordGroup> groupDelays(RecordGroup levelGroup) throws Exception{
    	Vector<RecordGroup> delayGroupsV=new Vector<RecordGroup>();
    	
    	Vector<Vector> delaysVV=new Vector<Vector>();
    	Vector<String> dsidsV=levelGroup.dsidsV();	
    	Vector<String> xtagsV=levelGroup.xtagsV();
    	
    	
    	try{
        	for(int i=0;i<levelGroup.nRecords();i++){
        		Level lev=(Level)levelGroup.recordsV().get(i);
        		delaysVV.add(lev.DParticlesV());
        		
        		/*
        		//debug
        		if(Math.abs(lev.EF()-4430)<10){
        			System.out.println("i="+i+" level="+lev.ES());
        			for(Gamma g:lev.GammasV())
        				System.out.println("In EnsdfGroup line 658: i="+i+" delay="+g.ES());
        		}
        		*/
        			
        	}
    	}catch(Exception e){
    		return delayGroupsV;
    	}

    	
    	delayGroupsV=(Vector<RecordGroup>)doQuickGrouping(delaysVV,dsidsV,xtagsV,deltaEL);
            
        //debug
        //if(Math.abs(levelGroup.recordsV().get(0).EF()-3250)<40){
        //  System.out.println("In EnsdfGroup line 681: delay group  size="+delayGroupsV.size());
        //}
    	
    	//check and process delaysVV to get final delayGroupsV 
    	Vector<RecordGroup> tempGroupsV=new Vector<RecordGroup>();
    	for(int i=0;i<delayGroupsV.size();i++){

    		RecordGroup delayGroup=((RecordGroup) delayGroupsV.get(i));
            tempGroupsV.addAll(doFineGrouping(delayGroup));

        	//debug
        	//if(levelGroup.getAdoptedRecord().ES().contains("251.54")){
            //    System.out.println("##### try #"+i);
        	//	for(int m=0;m<tempGroupsV.size();m++){
        	//		System.out.println("  In EnsdfGroup line 448: delay group"+m);
        	//		for(int n=0;n<tempGroupsV.get(m).nRecords();n++)
        	//			System.out.println("      delay#"+n+"="+tempGroupsV.get(m).getRecord(n).ES());
        	//	}
        	//}
        	
        	/*
    		//debug
            int m=0;
    		if(Math.abs(delayGroup.recordsV().get(0).EF()-4400)<50){
    			for(Record r:delayGroup.recordsV()){
    				System.out.println(" ***EnsdfGroup 693: levelGroup.recordsV().get(0)="+levelGroup.recordsV().get(0).ES()+"  i="+i+" m="+m+" ES="+r.ES()+" ID="+delayGroup.getDSID(m)+" tag="+delayGroup.getXTag(m));
    				m++;
    			}
    		}
    		*/
    		
    	}   	
    	
        
		//debug
		//if(Math.abs(levelGroup.recordsV().get(0).EF()-74)<1){
    	//	System.out.println("In EnsdfGroup line 433: temp size="+tempGroupsV.size()+"  size="+delayGroupsV.size());
		//}
		
    	delayGroupsV.clear();
    	delayGroupsV.addAll(tempGroupsV);
    	
	
    	levelGroup.subgroups().clear();
    	for(int i=0;i<delayGroupsV.size();i++){   	
    	    RecordGroup delayGroup=delayGroupsV.get(i);
    		sortRecordsByDSID(delayGroup);   		
    		levelGroup.subgroups().addElement(delayGroup);
    		
    		/*
            //debug
            int m=0;
            if(Math.abs(delayGroup.recordsV().get(0).EF()-1940)<20){
                for(Record r:delayGroup.recordsV()){
                    System.out.println(" *** EnsdfGroup 720: levelGroup.recordsV().get(0)="+levelGroup.recordsV().get(0).ES()+"  i="+i+" m="+m+" ES="+r.ES()+" ID="+delayGroup.getDSID(m)+" tag="+delayGroup.getXTag(m));
                    m++;
                }
            }
            */
            
    	}
    	
    	//levelGroup.setSubGroups(delayGroupsV);

    	return delayGroupsV;
    }
	public void insertLevelsToReferenceGroups(ENSDF ens,Vector<RecordGroup> refLevelGroupsV) throws Exception{
		String dsid="",xtag="";	
		
		try{
			dsid=ens.DSId0();
			xtag=dsidXTagMapFromAdopted.get(dsid);	
			
			/*
			if(dsid.contains("XUNDL-2")) {
				System.out.println("EnsdfGroup 1092: "+dsid+" xtag="+xtag);
			for(int j=0;j<datasetDSIDsV.size();j++)
				System.out.println("this dsid="+dsid+" stored dsid="+datasetDSIDsV.get(j)+" xtag="+datasetXTagsV.get(j));
			}
			*/
			//this happens when dsidXTagMap is from XREF list in Adopted dataset.
			//For this case, xtag="?0","?1",... have been assigned to such datasets and 
			//stored in xtagsV when making dsidXTagMap from adopted XREF list;
			if(xtag==null){
				int i=datasetDSID0sV.indexOf(dsid);
				
				/*
				if(dsid.contains("XUNDL-2"))
					System.out.println("EnsdfGroup 1092: "+dsid+" xtag="+xtag);
				for(int j=0;j<datasetDSIDsV.size();j++)
					System.out.println("this dsid="+dsid+" stored dsid="+datasetDSIDsV.get(j)+" xtag="+datasetXTagsV.get(j));
				*/
				
				if(i>=0)
					xtag=datasetXTagsV.get(i);
				else{
					int n=0;
					xtag="?"+n;			
					while(datasetXTagsV.contains(xtag)){
						n++;
						xtag="?"+n;
						
					}
				}
			}
		}catch(Exception e){
			e.printStackTrace();
			return;
		}
		
		/*
		//debug
		System.out.println("**** EnsdfGroup 1147: insert levels in dsid="+dsid);
        for(int k=0;k<refLevelGroupsV.size();k++) {
            RecordGroup levelGroup=refLevelGroupsV.get(k);
            float ef=levelGroup.getRecord(0).ERPF();
            if(Math.abs(ef-7500)<20) {
                System.out.println("  group#="+k+" size="+levelGroup.nRecords());
                for(int j=0;j<levelGroup.nRecords();j++)
                    System.out.println("     level#="+j+" ES="+levelGroup.getRecord(j).ES()+" dsid="+levelGroup.getDSID(j));
            }

        }
        */
		
		insertRecordsToReferenceGroups(ens.levelsV(),dsid,xtag,refLevelGroupsV);

		
		/*
		//debug
    	if(dsid.contains("36AR(P,A)")) {
    		System.out.println("#2  ENsdfGroup 1113: ens. dsid="+dsid);
    		for(int k=0;k<refLevelGroupsV.size();k++) {
    			RecordGroup levelGroup=refLevelGroupsV.get(k);
    			float ef=levelGroup.getRecord(0).ERPF();
    			if(Math.abs(ef-3970)<10) {
        			System.out.println("  group#="+k+" size="+levelGroup.nRecords());
        			for(int j=0;j<levelGroup.nRecords();j++)
        				System.out.println("     level#="+j+" ES="+levelGroup.getRecord(j).ES()+" dsid="+levelGroup.getDSID(j));
    			}

    		}
    	}
    	*/
		
		
    	return;
    }
    
	//For level and gamma:
	@SuppressWarnings("unchecked")
	private <T extends Record>void insertRecordsToReferenceGroups(Vector<T> recordsV,String dsid,String xtag,Vector<RecordGroup> refRecordGroupsV) throws Exception{
        //long startTime,endTime;
        //float timeElapsed;//in second
        //startTime=System.currentTimeMillis();		
	    
		int nRecords=recordsV.size();
		int nGroups=refRecordGroupsV.size();
		
		if(nRecords==0)
			return;

		//System.out.println(" refRecordGroupsV.size()="+refRecordGroupsV.size());
		
		if(refRecordGroupsV.size()==0 || refRecordGroupsV.get(0).nRecords()==0){
			for(int i=0;i<recordsV.size();i++){
				RecordGroup g=new RecordGroup();
				g.addRecord(recordsV.get(i), dsid, xtag);
				refRecordGroupsV.add(g);
				
				//System.out.println("        record E="+((Record)recordsV.get(i)).ES());
			}
			
			return;
		}
		
		float deltaE;
		boolean isGamma=false;
		if(refRecordGroupsV.get(0).recordsV().get(0) instanceof Level){
			deltaE=deltaEL;
			isGamma=false;
		}else{
			deltaE=deltaEG;
			isGamma=true;
		}		
		
		//indexesOfRecordsInGroupVV: (for records in recordsV)
		//store indexes of records assigned to each group for current records with the same tag (from same dataset)
		//Note that:
		//one record could be assigned to multiple groups. After all records have been assigned (for those that 
		//cannot be assigned to any group, new groups will be made for them), check the groups that have 
		//more than one record to keep the best-matched record and remove others, since one group can only contain
		//one record from one dataset (while one record can be assigned to multiple groups). 
		Vector<Vector<Integer>> indexesOfRecordsInGroupVV=new Vector<Vector<Integer>>();		
		for(int i=0;i<nGroups;i++){
			Vector<Integer> temp=new Vector<Integer>();
			indexesOfRecordsInGroupVV.add(temp);
		}
		
		//store the matching strength (and order) of matching records in recordsV of each ref group: map of record index 
		//and matching strength for each ref group 
		Vector<HashMap<Integer,MatchingStrength>> matchingStrengthMapOfRecordsInGroupV=new Vector<HashMap<Integer,MatchingStrength>>();
		for(int i=0;i<nGroups;i++){
			HashMap<Integer,MatchingStrength> matchingMap=new HashMap<Integer,MatchingStrength>();
			matchingStrengthMapOfRecordsInGroupV.add(matchingMap);
		}
		
		//if(dsid.contains("(N,G),(N,N):RE")) System.out.println("hello1  "+isGamma+" refRecordGroupsV.size="+refRecordGroupsV.size());
		
		//allocate records of the same xtag (same dataset) to groups and get the indexes of records of each group
		int[] multiplicity=new int[nRecords];
		boolean[] isGoodES=new boolean[nRecords];
		Vector<Vector<Integer>> indexesOfGroupsForRecordVV=new Vector<Vector<Integer>>();//store number of groups that each record is assigned to
		Vector<Integer> indexesOfPossibleGroups=new Vector<Integer>();
		
		int totalCount=0,nMatches=0;
		
		for(int j=0;j<nRecords;j++){
			
			T rec=recordsV.get(j);
			multiplicity[j]=0;
			indexesOfGroupsForRecordVV.add(new Vector<Integer>());
			
			String ES=rec.ES().toUpperCase().replace("(", "").replace(")","");
			if(!Str.isNumeric(ES) && (ES.contains("SP")||ES.contains("SN"))){ 
				if(!rec.isTrueERPF()) {
					isGoodES[j]=false;
					continue;
				}

			}
			
			/*
			if(Math.abs(rec.EF()-1940)<20 && (rec instanceof Gamma)){
	            //if(r.ES().toUpperCase().contains("3620")){
	               System.out.println("In EnsdfGroup 888: es="+rec.ES()+" de="+rec.DES()+" ef="+rec.EF());;   
	               for(int i=0;i<recordsV.size();i++)
	                   System.out.println("        record E="+((Record)recordsV.get(i)).ES());
	            
			}	
			*/
			
			/*
			//debug
			if(dsid.contains("(P,A)"))
			for(RecordGroup levelGroup:refRecordGroupsV) {
				double EF=levelGroup.getRecord(0).EF();
				if(EF>3970 && EF<4000)
					System.out.println("EnsdfGroup 1226: process dataset "+dsid+"  in group: first level="+EF);
			}
			*/
			
			isGoodES[j]=true;
			
			//find record groups that roughly match in Energy, large energy range
			indexesOfPossibleGroups=findIndexesOfPossibleGroups(rec,dsid,xtag,refRecordGroupsV,true);          		
	        
			//if(dsid.contains("RESONANCE") || dsid.contains("D,P")) System.out.println("EnsdfGroup 1247 hello1: DSID="+dsid+" j="+j+" E="+rec.ES());
			
			/*
			//make a new record by setting an uncertainty based on energy of previous and next record
			//if the distance is smaller than the default=20%
			if(rec.DES().equals("AP") || rec.DES().equals("CA")) {
				
			}
			*/
			
			/*
			//debug
			if(rec.ES().equals("9447.1")&& (rec instanceof Level)){
			//if(rec.ES().equals("1096.3")&& dsid.contains("(3HE,T")) {
	    	//if(dsid.contains("30SI(P,G") && (rec instanceof Gamma) && rec.ES().toUpperCase().contains("X")) {
	    		System.out.println("****ensdfGroup line 1339 DSID="+dsid+" xtag="+xtag+" j="+j+" ES="+rec.ES()+" ngroup="+indexesOfPossibleGroups.size());
	    		//for(int k=0;k<refRecordGroupsV.size();k++) {
                //    RecordGroup group=refRecordGroupsV.get(k);
                //    if(Math.abs(group.getMeanEnergy()-7500)>60)
                //        continue;
                    
	    		for(int k=0;k<indexesOfPossibleGroups.size();k++) {
	    			RecordGroup group=refRecordGroupsV.get(indexesOfPossibleGroups.get(k));
	    			//float ef=levelGroup.getRecord(0).ERPF();
	    			//if(group.getRecord(0).ES().contains("1233")) {
	        			System.out.println("  group#="+k+" size="+group.nRecords());
	        			for(int m=0;m<group.nRecords();m++)
	        				System.out.println("     level#="+m+" ES="+group.getRecord(m).ES()+" dsid="+group.getDSID(m));
	    			//}

	    		}
	    		
	    	}
            */
			
	        //endTime=System.currentTimeMillis();
	        //timeElapsed=(float)(endTime-startTime)/1000;
	        //if(dsid.contains("(N,G),(N,N)")) System.out.println("  "+j+"-1 DSID="+dsid+" Time elapsed: "+String.format("%.3f", timeElapsed)+" seconds");
	        
			nMatches=0;
			
			int ntries=0;
			Vector<Boolean> matchedV=new Vector<Boolean>();
			int NT=1;
			if(rec instanceof Level)
			    NT=2;

			
			while(ntries<NT) {
			    
	            for(int k=0;k<indexesOfPossibleGroups.size();k++){
	                int igroup=indexesOfPossibleGroups.get(k).intValue(); 
	                RecordGroup group=refRecordGroupsV.get(igroup);  
	                
	                boolean matched=false;
	                MatchingStrength matchingStrength=new MatchingStrength(-1);
	                
	                if(ntries==0) {
	                    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	                    //determine which records can be grouped together (need more work):
	                    //match within in error=(sum of two energy uncertainties)*scale_factor
	                    //see isComprableE()
	                    //more precise matching in both energy and JPI, also considering uncertainties.
	                    //so, deltaE works here as an upper limit, depending on uncertainties.
	                    //this is the core function that determines the final grouping.                     
	                    //matched=group.hasMatchedRecord(rec,deltaE);
	                	matchingStrength=group.findMatchingStengthOfRecord(rec, deltaE);
	                	matched=(matchingStrength.strength>0);
	                	
	                    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////	 
	                    
	                    matchedV.add(matched);
	                    
	                    /*
                    	//debug
                    	if(Math.abs(rec.EF()-5714)<0.5){ 
                        	System.out.println("In ENSFDGroup line 1529: es="+rec.ES()+" dsid="+dsid+"  matched="+matched);
                        	for(int m=0;m<group.recordsV().size();m++){
                        		Level l=(Level)group.recordsV().get(m);
                        		System.out.println("   in group:    l.ES="+l.ES()+" EF="+l.EF()+"  "+l.JPiS()+" dsid="+group.dsidsV().get(m));      
                        	} 
                    	}
                    	*/
	                }else if(!matchedV.get(k)){
	                    if((rec instanceof Level) && group.hasComparableEnergyEntry(rec,5.0f,true) && group.hasLevelGamma()) {
	                        Level lev=(Level)rec;
	                        String jps=lev.JPiS().replace("+", "").replace("-", "");
	                        
	                        Vector<Integer> indexesV=indexesOfRecordsInGroupVV.get(igroup);

	                        if(lev.nGammas()>0 && group.hasCloseJPI(jps,2) && group.isGammasConsistent(lev,2.0f,false)){
	                        	boolean isGood=false;
	                        	if(jps.contains("(") && group.hasOverlapJPI(jps)) 
	                        		isGood=true;
	                        	else if(group.isGammasConsistent(lev,1.0f,true) && group.findMaxNumberOfConsistentGammas(lev,1.0f,true)>1)
	                        		isGood=true;
	                        	
	    	                    
	                        	if(isGood) {
		                        	
		                            float dist0=group.findAverageDistanceToGroup(rec);                          
		                            float distPrev=1000,distNext=1000;
		            
		                            
		                            try {
		                                RecordGroup prevGroup=refRecordGroupsV.get(igroup-1);
		                                distPrev=prevGroup.findAverageDistanceToGroup(rec);
		                            }catch(Exception e) {}
		                            try {
		                                RecordGroup nextGroup=refRecordGroupsV.get(igroup+1);
		                                distNext=nextGroup.findAverageDistanceToGroup(rec);
		                            }catch(Exception e) {}
		                            
		                            
		                            if(dist0<distPrev && dist0<distNext) {
		                                
		                                //System.out.println("In ENSFDGroup line 1001: es="+rec.ES()+"  e="+rec.EF()+"  nmatches="+nMatches+" group#="+igroup+"  group size="+refRecordGroupsV.size());
		                                
		                                matched=true;
		                                matchingStrength=new MatchingStrength(5);
		                                
		                                float dist=-1;
		                                for(int m=0;m<indexesV.size();m++) {
		                                    Record r=recordsV.get(indexesV.get(m).intValue());
		                                    dist=group.findAverageDistanceToGroup(r);
		                                    if(dist<dist0) {
		                                        matched=false;
		                                        matchingStrength=new MatchingStrength(-1);
		                                        break;
		                                    }
		                                }
		                            }	                        		
	                        	}//end if(isGood)
	                        	
	                        	/*
	                        	//debug
	                        	if(Math.abs(lev.EF()-5642)<0.5){ 
		                        	System.out.println("In ENSFDGroup line 1589: es="+rec.ES()+" dsid="+dsid+"  nmatches="+nMatches+" group#="+igroup
          			                      +"  group size="+refRecordGroupsV.size()+" isGood="+isGood+" matched="+matched);
		                        	System.out.println("            group.isGammasConsistent(lev,1.0f,true)="+group.isGammasConsistent(lev,1.0f,true)+"   group.hasCloseJPI(jps,2)="+group.hasCloseJPI(jps,2)
          	                              +"    group.findMaxNumberOfConsistentGammas(lev,1.0f,true)="+group.findMaxNumberOfConsistentGammas(lev,1.0f,true));
              
		                        	for(int m=0;m<group.recordsV().size();m++){
		                        		Level l=(Level)group.recordsV().get(m);
		                        		System.out.println("   in group:    l.ES="+l.ES()+" EF="+l.EF()+"  "+l.JPiS()+" dsid="+group.dsidsV().get(m));      
		                        	} 
	                        	}
   	                        	*/
	                        }//end if(jps)
	                    }	                    
	                }else
	                	continue;

	                /*	                
	                //debug
	                if(rec.ES().toUpperCase().contains("X") && (rec instanceof Gamma)){
	                    System.out.println("In ENSFDGroup line 1022: es="+rec.ES()+"  e="+rec.EF()+"  matched="+matched+" group#="+igroup+"  group size="+refRecordGroupsV.size());
	                    for(int m=0;m<group.recordsV().size();m++){
	                        Gamma g=(Gamma)group.recordsV().get(m);
	                        System.out.println("     g.ES="+g.ES()+" EF="+g.EF()+"  dsid="+group.dsidsV().get(m));
	                    }   
	                }
	                */
	               
	                /*
	                //debug         
	                //if(rec.ES().contains("0+y") && (rec instanceof Level)){
	                if(rec.ES().contains("4201") && (rec instanceof Level)){
	                //if(Math.abs(rec.EF()-3464)<1 && (rec instanceof Level) && dsid.contains("10B(3HE,H)")){
	                    Level lev=(Level)rec;
	                    System.out.println("In ENSFDGroup line 1550: es="+rec.ES()+"  e="+rec.EF()+" jpi="+lev.JPiS()+" deltaE="+deltaE);
	                    System.out.println(" ***** matched="+matched+" igroup="+igroup+" irecord="+j+" DSID="+dsid+" ntries="+ntries);
	                    System.out.println("   matching strength="+matchingStrength.strength+" order="+matchingStrength.order);
	                    if(lev.nGammas()>0)
	                        System.out.println(" first gamma="+lev.gammaAt(0).ES());                    
	                    System.out.println("      group.isGammasConsistent(lev)="+group.isGammasConsistent(lev,deltaEG,false)+" indexesOfPossibleGroups.size()="+indexesOfPossibleGroups.size()+
	                            "  has close JPI="+group.hasOverlapJPI(lev.JPiS().replace("+", "").replace("-", ""))+" group.hasComparableEnergyEntry="+group.hasComparableEnergyEntry(rec) );
	        
	                    //System.out.println("      EnsdfUtil.isComparableES(lev.ES(), record0InGroup.ES(), 2.0f)="+EnsdfUtil.isComparableES(lev.ES(), record0InGroup.ES(), 2.0f));
	                    //System.out.println("      lev.nGammas="+lev.nGammas()+" group.hasRecordGamma()="+group.hasRecordGamma());
	                    for(int m=0;m<group.recordsV().size();m++){
	                        Level l=(Level)group.recordsV().get(m);
	                        System.out.println("     l.ES="+l.ES()+" EF="+l.EF()+"  "+l.JPiS()+" dsid="+group.dsidsV().get(m));
	                    }                       
	                }
	                */
	                
	                if(matched){
	                    indexesOfRecordsInGroupVV.get(igroup).add(j);
	                    matchingStrengthMapOfRecordsInGroupV.get(igroup).put(j, matchingStrength);
	                    
	                    nMatches++;
	                    totalCount++;
	                }
	            }	
	            
	            ntries++;
			}

            //endTime=System.currentTimeMillis();
            //timeElapsed=(float)(endTime-startTime)/1000;
            //if(dsid.contains("(N,G),(N,N)")) System.out.println("  "+j+"-2 DSID="+dsid+" Time elapsed: "+String.format("%.3f", timeElapsed)+" seconds");	
            
			/*
			//debug			
			//if(rec.ES().equals("5730") && (rec instanceof Level) && ((Level)rec).JPiS().equals("3/2")) {
			//if(rec.ES().contains("9447") && (rec instanceof Level)&& dsid.contains("(D,P")){
			if(rec.ES().contains("653") && (rec instanceof Level)){
			//if(Math.abs(rec.EF()-3464)<0.1 && (rec instanceof Level)){
				Level lev=(Level)rec;
				System.out.println("In ENSFDGroup line 1519: es="+rec.ES()+"  e="+rec.EF()+" jpi="+lev.JPiS()+"  nmatched="+nMatches);

				if(lev.nGammas()>0)
				    System.out.println(" first gamma="+lev.gammaAt(0).ES());
	    		System.out.println("****   DSID="+dsid+" xtag="+xtag+" j="+j+" ES="+rec.ES()+" ngroup="+indexesOfPossibleGroups.size());
	    		
	    		for(int k=0;k<indexesOfPossibleGroups.size();k++) {
	    			RecordGroup group=refRecordGroupsV.get(indexesOfPossibleGroups.get(k));
	    			//float ef=levelGroup.getRecord(0).ERPF();
	    			//if(group.getRecord(0).ES().contains("1233")) {
	        			System.out.println("  group#="+k+" size="+group.nRecords());
	        			for(int m=0;m<group.nRecords();m++)
	        				System.out.println("     level#="+m+" ES="+group.getRecord(m).ES()+" dsid="+group.getDSID(m));
	    			//}

	    		}
	    		
			    //System.out.println("     indexesOfPossibleGroups.size()="+indexesOfPossibleGroups.size());		
			}
			*/
			
			/*
	        if(Math.abs(rec.EF()-5642)<0.5 && (rec instanceof Level)){
	            //Gamma gam=(Gamma)rec;

	            System.out.println("In ENSFDGroup line 1678: es="+rec.ES()+"  e="+rec.EF()+"  nmatched="+nMatches+" ntries="+ntries);
	            System.out.println("     indexesOfPossibleGroups.size()="+indexesOfPossibleGroups.size());        
       
	        }
		    */
			
			//further check if unassigned records can still be assigned to any group under looser conditions
			if(nMatches==0) { 
				
				//for gamma records
				//since in some cases, levels are found matched but have discrepant gammas and those gammas with similar energies should be 
				//grouped into the same gamma groups 
				if(isGamma){
				    //System.out.println(" rec="+rec.ES()+"  "+indexesOfPossibleGroups.size());
				    
					for(int k=0;k<indexesOfPossibleGroups.size();k++){
						int igroup=indexesOfPossibleGroups.get(0).intValue(); 
						RecordGroup group=refRecordGroupsV.get(igroup);  
						boolean matched=group.hasComparableEnergyEntry(rec,5.0f,true);
					    if(!matched && group.hasComparableEnergyEntry(rec,deltaEG,true)) {
					        //further checking gammas with large discrepancy in energy but having the matching final levels
					        //note that at this point the parent levels of those gammas have been found matched
					        ENSDF ens=getENSDFByDSID0(dsid);
					        try {
					            Gamma gam=(Gamma)rec;
		                        Level fLev=ens.levelAt(gam.FLI());
		                        //Level iLev=ens.levelAt(gam.ILI());
                                for(int m=0;m<group.nRecords();m++) {
                                    Gamma g=(Gamma)group.recordsV().get(m);
                                    ens=getENSDFByDSID0(group.dsidsV().get(m));
                                    Level fL=ens.levelAt(g.FLI());
                                    //Level iL=ens.levelAt(g.ILI());
                                    
                                    //System.out.println("EnsdfGroup 1130: EG="+gam.ES()+"  Lev="+iLev.ES()+" "+fLev.ES()+" g="+g.ES()+" iL="+iL.ES()+" fL="+fL.ES());
                                    //System.out.println("    isComparableEnergyEntry(fLev,fL)="+EnsdfUtil.isComparableEnergyEntry(fLev,fL));
                                    
                                    if(EnsdfUtil.isComparableEnergyEntry(fLev,fL) && EnsdfUtil.isOverlapJPI(fLev.JPiS(), fL.JPiS())) {
                                        matched=true;
                                        break;
                                    }
                                               
                                }
					        }catch(Exception e) {
					            
					        }
     
					    }
					    
						if(matched){
							MatchingStrength matchingStrength=new MatchingStrength(6);
							indexesOfRecordsInGroupVV.get(igroup).add(j);
							matchingStrengthMapOfRecordsInGroupV.get(igroup).put(j,matchingStrength);
							
							totalCount++;
						}
					}
				}else if(indexesOfPossibleGroups.size()==1){//for level records
					int igroup=indexesOfPossibleGroups.get(0).intValue(); 
					RecordGroup group=refRecordGroupsV.get(igroup);  
					boolean matched=false;
					boolean hasComparableEnergyEntry=group.hasComparableEnergyEntry(rec,10.0f,true);
					boolean hasOverlapJPI=group.hasOverlapJPI((Level)rec);
					
					MatchingStrength matchingStrength=new MatchingStrength(-1);
					
					if(hasOverlapJPI && group.isGammasConsistent((Level)rec, 50, true)) {
					    if(hasComparableEnergyEntry) {
					        matched=true;
					        matchingStrength=new MatchingStrength(6);
					    }else if(group.findPossibleRecordsForGroup(recordsV,50).size()==1) {//if the rec is the only record that
					        matched=true;
					        matchingStrength=new MatchingStrength(6);
					    }
					}
				
					/*
					//debug
					if(Math.abs(rec.EF()-461)<0.5 && (rec instanceof Level)){
						Level lev=(Level)rec;
						System.out.println("In ENSFDGroup line1665: es="+rec.ES()+"  e="+rec.EF()+" jpi="+lev.JPiS()+"  matched="+matched+" igroup="+igroup+" irecord="+j);
						System.out.println(" hasComparableEnergyEntry="+hasComparableEnergyEntry);
					    System.out.println("      group.isGammasConsistent(lev)="+group.isGammasConsistent(lev,10,true));
					    //System.out.println("      EnsdfUtil.isComparableES(lev.ES(), record0InGroup.ES(), 2.0f)="+EnsdfUtil.isComparableES(lev.ES(), record0InGroup.ES(), 2.0f));
						//ystem.out.println("      lev.nGammas="+lev.nGammas()+" group.hasRecordGamma()="+group.hasRecordGamma());
					    for(int m=0;m<group.recordsV().size();m++){
						    Level l=(Level)group.recordsV().get(m);
							System.out.println("     l.ES="+l.ES()+" EF="+l.EF()+"  "+l.JPiS()+" dsid="+group.dsidsV().get(m));
						}						
					}
					*/
					
					if(matched){
						//check if rec is the only record of all that matches the group
						for(int m=j-1;m>=j-3&&m>=0;m--) {//search backward by 3 records
							T tempRec=recordsV.get(m);							
							boolean tempMatched=group.hasComparableEnergyEntry(tempRec,10.0f,true) && group.hasOverlapJPI((Level)tempRec);
							
							//System.out.println("1* m="+m+" rec.E="+tempRec.ES()+" match="+matched+" tempMatched="+tempMatched);
							
							if(tempMatched) {
								//there is other record also matching the group under the same conditions,
								//then an unique match cannot be found
								matchingStrength=new MatchingStrength(-1);
								break;
							}
						}
						
						for(int m=j+1;m<=j+3&&m<nRecords;m++) {//search forward by 3 records
							T tempRec=recordsV.get(m);							
							boolean tempMatched=group.hasComparableEnergyEntry(tempRec,10.0f,true) && group.hasOverlapJPI((Level)tempRec);
							
							//System.out.println("2* m="+m+" rec.E="+tempRec.ES()+" match="+matched+" tempMatched="+tempMatched);
							
							if(tempMatched) {
								//there is other record also matching the group under the same conditions,
								//then an unique match cannot be found
								matchingStrength=new MatchingStrength(-1);
								break;
							}
						}
						
						if(matched) {
							indexesOfRecordsInGroupVV.get(igroup).add(j);
							matchingStrengthMapOfRecordsInGroupV.get(igroup).put(j,matchingStrength);
							
							totalCount++;
						}
					}
					
					//System.out.println("In ENSFDGroup line 907: es="+rec.ES()+"  e="+rec.EF()+"  matched="+matched+" igroup="+igroup+" irecord="+j);
					
				}
					
			}

		}//end for-loop for records

        //endTime=System.currentTimeMillis();
        //timeElapsed=(float)(endTime-startTime)/1000;
        //if(dsid.contains("(N,G),(N,N)")) System.out.println("DSID="+dsid+"## Time elapsed: "+String.format("%.3f", timeElapsed)+" seconds");
        
		//if(dsid.contains("(N,G),(N,N):RE")) System.out.println("hello2");
		
		
		/*
        //debug
		int count=0;
		for(int i=0;i<nGroups;i++){
			RecordGroup group=refRecordGroupsV.get(i);
			Vector<Integer> indexesOfRecordsInGroupV=indexesOfRecordsInGroupVV.get(i);
			if(!isGamma && dsid.contains("(D,P)") && Math.abs(group.getMeanEnergy()-461)<20) {
	        	if(count==0) System.out.println("########### EnsdfGroup 1736:");  
	        	count++;
	            System.out.println("  *** 1697 original Records in group:"+i);
	            for(int k=0;k<group.nRecords();k++) 
	            	System.out.println("      k="+k+"  "+group.recordsV().get(k).ES()+"  "+group.dsidsV().get(k));
	                          
	            System.out.println("       records from "+dsid+" to be assigned to the group: ");
	            for(int k=0;k<indexesOfRecordsInGroupV.size();k++) 
	            	System.out.println("           k="+k+"  "+recordsV.get(indexesOfRecordsInGroupV.get(k)).ES());
	            
	        }
		}
        */
		
		/*
		//debug
		for(RecordGroup levelGroup:refRecordGroupsV) {
			double EF=levelGroup.getRecord(0).EF();
			if(EF>3970 && EF<4000)
				System.out.println("1 EnsdfGroup 1590: process dataset "+dsid+"  in group: first level="+EF);
		}
		*/
		
		/*
        //debug
		int count1=0;
		for(int i=0;i<nGroups;i++){
			RecordGroup group=refRecordGroupsV.get(i);
			Vector<Integer> indexesOfRecordsInGroupV=indexesOfRecordsInGroupVV.get(i);
			if(!isGamma && Math.abs(group.getMeanEnergy()-5714)<10) {
	        //if(!isGamma && dsid.contains("(D,P)") && Math.abs(group.getMeanEnergy()-7615)<20) {
	        //if(!isGamma && dsid.contains("(3HE,P)") && Math.abs(group.getMeanEnergy()-7500)<100) {
	        	if(count1==0) System.out.println("########### EnsdfGroup 1872:");  
	        	count1++;
	            System.out.println("  *** Records in group before filter:"+i);
	            for(int k=0;k<group.nRecords();k++) 
	            	System.out.println("      k="+k+"  "+group.recordsV().get(k).ES()+"  "+group.dsidsV().get(k));
	                          
	            System.out.println("       records from "+dsid+" to be assigned to the group: ");
	            for(int k=0;k<indexesOfRecordsInGroupV.size();k++) 
	            	System.out.println("           k="+k+"  "+recordsV.get(indexesOfRecordsInGroupV.get(k)).ES());
	            
	        }
		}
		*/
		
		//now filter groups to which each level is multiply assigned and remove the less likely assignment of groups
		if(!isGamma)
			preFilterGroupAssignmentsForRecords(recordsV,refRecordGroupsV,indexesOfRecordsInGroupVV,matchingStrengthMapOfRecordsInGroupV,deltaE);

		
		/*
        //debug
		int count=0;
		for(int i=0;i<nGroups;i++){
			RecordGroup group=refRecordGroupsV.get(i);
			Vector<Integer> indexesOfRecordsInGroupV=indexesOfRecordsInGroupVV.get(i);
			if(!isGamma && Math.abs(group.getMeanEnergy()-5650)<10) {
	        //if(!isGamma && dsid.contains("(D,P)") && Math.abs(group.getMeanEnergy()-7615)<20) {
	        //if(!isGamma && dsid.contains("(3HE,P)") && Math.abs(group.getMeanEnergy()-7500)<100) {
	        	if(count==0) System.out.println("########### EnsdfGroup 1899:");  
	        	count++;
	            System.out.println("  *** Records in group after filter:"+i);
	            for(int k=0;k<group.nRecords();k++) 
	            	System.out.println("      k="+k+"  "+group.recordsV().get(k).ES()+"  "+group.dsidsV().get(k));
	                          
	            System.out.println("       records from "+dsid+" to be assigned to the group: ");
	            for(int k=0;k<indexesOfRecordsInGroupV.size();k++) 
	            	System.out.println("           k="+k+"  "+recordsV.get(indexesOfRecordsInGroupV.get(k)).ES());
	            
	        }
		}
		*/
		
		if(totalCount>0){		
			
			//check the groups that have more than one records from the same dataset to keep the best-matched record and remove others, 
			//since one group can only contain one record from one dataset (while one record can be assigned to multiple groups). 
			
		    //System.out.println("begin findBestRecordsForGroups: records of "+dsid);
            
            findBestRecordsForGroups_old(recordsV, refRecordGroupsV, indexesOfRecordsInGroupVV,isGamma,deltaE);
            //findBestRecordsForGroups_BAD(recordsV, refRecordGroupsV, indexesOfRecordsInGroupVV,isGamma,deltaE);

            /*
            //debug
    		if(!isGamma) {
    			System.out.println("***");
    			preFilterGroupAssignmentsForRecords(recordsV,refRecordGroupsV,indexesOfRecordsInGroupVV,deltaE);
    		}
    		*/
            
            /*
            //debug
    		int count=0;
    		for(int i=0;i<nGroups;i++){
    			RecordGroup group=refRecordGroupsV.get(i);
    			Vector<Integer> indexesOfRecordsInGroupV=indexesOfRecordsInGroupVV.get(i);
    			if(!isGamma && dsid.contains("12C(58NI") && Math.abs(group.getMeanEnergy()-5714.0)<10) {
    	        //if(!isGamma && dsid.contains("(D,P)") && Math.abs(group.getMeanEnergy()-7615)<20) {
    			//if(!isGamma && dsid.contains("(3HE,P)") && Math.abs(group.getMeanEnergy()-7500)<100) {
    	        //if(!isGamma && dsid.contains("(3HE,P)")) {
    	        	if(count==0) System.out.println("########### EnsdfGroup 1942 after findBest:");  
    	        	count++;
    	            System.out.println("  *** original Records in group:"+i);
    	            for(int k=0;k<group.nRecords();k++) 
    	            	System.out.println("      k="+k+"  "+group.recordsV().get(k).ES()+"  "+group.dsidsV().get(k));
    	                          
    	            System.out.println("       records from "+dsid+" to be assigned to the group: ");
    	            for(int k=0;k<indexesOfRecordsInGroupV.size();k++) 
    	            	System.out.println("           k="+k+"  "+recordsV.get(indexesOfRecordsInGroupV.get(k)).ES());
    	            
    	        }
    		}
    		*/
            
			for(int i=0;i<nGroups;i++){
				
				//indexes of the records (from the same dataset) to be inserted to the group at igroup=i
				Vector<Integer> indexesOfRecordsInGroupV=indexesOfRecordsInGroupVV.get(i);
				
				int nIndexes=indexesOfRecordsInGroupV.size();	

				/*
				//debug
				for(int k=0;k<nRecords;k++){
					Record rec=recordsV.get(k);
				    if(Math.abs(rec.EF()-689.24)<0.01 && (rec instanceof Level) && nIndexes>0) {
				    	if(indexesOfRecordsInGroupV.contains(k)) {
						      System.out.println("\n   igroup="+i+" nRecordIndexes="+nIndexes+" rec="+rec.ES()+" dsid="+dsid);			      
						      for(Integer ind:indexesOfRecordsInGroupV) System.out.println("       iRecord="+ind.intValue()+" record E="+recordsV.get(ind.intValue()).ES());
						      
						      for(Record r:refRecordGroupsV.get(i).recordsV())
						          System.out.println("   record in group: E="+r.ES()+"  JPI="+((Level)r).JPiS());
				    	}

				    }
				}
				*/
				
				/*
				RecordGroup group=refRecordGroupsV.get(i);
		        if(!isGamma && Math.abs(group.getMeanEnergy()-8439)<50) {
		            
		            System.out.print("EnsdfGroup 1262 (after findBestRecord):           Record in group:");
		            for(int k=0;k<group.nRecords();k++) System.out.print("      "+group.recordsV().get(k).ES());
		            System.out.println("");
		                          
		            System.out.print("                                                  new record assigned: ");
		            for(int k=0;k<indexesOfRecordsInGroupV.size();k++) System.out.print("      "+recordsV.get(indexesOfRecordsInGroupV.get(k)).ES());
		            System.out.println("");
		            
		        }
		        */
				
				//nIndexes=0 or 1. 
				//nIndexes>1 means there are 2 or more records (from the dataset) are assigned to the same group, 
				//which is not allowed, and the best match need to find and keep and others should be removed 
				//from the group.
				if(nIndexes==0)
					continue;                				
			
				//here indexesOfRecordsInGroupV.size=1 after being processed
				int irecord=indexesOfRecordsInGroupV.get(0).intValue();				
				multiplicity[irecord]++;
	        
				indexesOfGroupsForRecordVV.get(irecord).add(i);
				
				/*
				//debug
				if(!isGamma && Math.abs(thisGroup.getMeanEnergy()-7010)<30 && dsid.contains("(P,T)")) { 
                    System.out.println("EnsdfGroup 1834: @@@@@ nIndexes0="+nIndexes0+" nIndexes="+nIndexes+" inPrevGroup="+inPrevIndexes+" inNextGroup="+inNextIndexes
                    		+" ibest="+irecord+" multiplicity[ibest]="+multiplicity[ibest]+" best="+recordsV.get(irecord).ES()+" minDiff="+minDiff+"  nextgroup null="+(nextGroup==null));
                    if(nextGroup!=null)
                    for(int k=0;k<indexesOfRecordsInNextGroupV.size();k++)
                        System.out.println("next nIndexes now: record #"+k+" index="+indexesOfRecordsInNextGroupV.get(k)+" E="+recordsV.get(indexesOfRecordsInNextGroupV.get(k)).ES());
                }
                */
				
			}//end for-loop for groups

			/*
			//debug
			for(int i=0;i<nRecords;i++){
				Record rec=recordsV.get(i);
				if(rec.ES().contains("7068") && (rec instanceof Level)) {
					System.out.println("EnsdfGroup 1678: i="+i+" ES="+rec.ES()+" dsid="+dsid+"  multiplicity="+multiplicity[i]);
				}
			}
			*/
			
			/*
			 * NO LONGER NEEDED, altJPS from L-transfer is set for each level of an ENSDF objsect before grouping levels and
			 * is compared and checked in the matching process above
			 * 
			//add records to different groups
			//check L from transfer reaction with JPs in recordGroup
			
			TransferInfo tr=null;		
			if(!isGamma) {
				ENSDF ens=this.getENSDFByDSID(dsid);
				if(ens!=null && ens.nLevWL()>0) {
					tr=new TransferInfo(ens);
				}
			}
            */
            
			/*
	         //debug
            for(int i=0;i<nRecords;i++){
                Record rec=recordsV.get(i);
                if(rec.ES().contains("7441.4")) {
                    System.out.println("EnsdfGroup 1315: i="+i+" ES="+rec.ES()+" dsid="+dsid+"  multiplicity="+multiplicity[i]);
                }
            }
            */
			
			//int[] multOffset=new int[multiplicity.length];//for re-adjusting multiplicity after following process
			//for(int i=0;i<multiplicity.length;i++)
			//	multplOffset[i]=0;

			//check level records that have been assigned to multiple groups
			if(!isGamma) {
				
				LinkedHashMap<Integer,RecordGroup> mapOfLevelGroupsWithGamma=new LinkedHashMap<Integer,RecordGroup>();	
				
				for(int irecord=0;irecord<nRecords;irecord++) {
					
					Level lev=(Level)recordsV.get(irecord);
					
					/*					 
		            if(Math.abs(lev.EF()-7498)<1) {
		            	System.out.println("EnsdfGroup 1850: NUCID"+this.NUCID+" E="+lev.EF()+"  mult="+multiplicity[irecord]+"   "+indexesOfGroupsForRecordVV.get(irecord).size());
		            	for(int j=0;j<indexesOfGroupsForRecordVV.get(irecord).size();j++) {
		            		int igroup=indexesOfGroupsForRecordVV.get(irecord).get(j);
		            		for(int k=0;k<refRecordGroupsV.get(igroup).nRecords();k++)
		            			System.out.println("group"+k+"  E="+refRecordGroupsV.get(igroup).getRecord(k).ES());
			            }
		            }
		            */
					
					int multpl=multiplicity[irecord];
					if(multpl<=1)
						continue;
					
					
					mapOfLevelGroupsWithGamma.clear();
					Vector<Integer> indexesOfAssignedGroups=indexesOfGroupsForRecordVV.get(irecord);
					for(int j=0;j<indexesOfAssignedGroups.size();j++) {
						int igroup=indexesOfAssignedGroups.get(j);
						RecordGroup levGroup=refRecordGroupsV.get(igroup);
						if(levGroup.hasLevelGamma())
							mapOfLevelGroupsWithGamma.put(igroup,levGroup);												
					}
				
					boolean found=false;
					Vector<Integer> igroupFoundV=new Vector<Integer>();
					float minAvgDist=1E6f;
					int iBestGroup=-1;
					float avgDistBest=-1;

	                
					//level has gammas and group has gammas
					if(mapOfLevelGroupsWithGamma.size()>0 && lev.nGammas()>0) {
						//keep level group containing any level with gamma
						int nMatchedGam=0;
						
						LinkedHashMap<Integer,Float>  groupAvgDistMap=new LinkedHashMap<Integer,Float>(); 
						
						for(int igroup:mapOfLevelGroupsWithGamma.keySet()) {
							RecordGroup levGroup=mapOfLevelGroupsWithGamma.get(igroup);
							
							int ng=0;
							float avgDist=-1;
							boolean isGood=false;
							
							ng=levGroup.findMaxNumberOfConsistentGammas(lev,deltaE,true);
						    if(ng>=nMatchedGam || ng==lev.nGammas()) {
						    	avgDist=levGroup.findAverageDistanceToGroup(lev);
						    							    	
						    	if(ng>nMatchedGam) {
						    		isGood=true;
						    	}else if(ng==nMatchedGam && ng>0) {
						    	    isGood=true;
						    	}else if(ng==lev.nGammas()) {
						    		if(levGroup.isGammasConsistent(lev, deltaE,false) && levGroup.hasComparableEnergyEntry(lev) && levGroup.hasOverlapJPI(lev)) {
						    			isGood=true;
						    		}
						    	}
						    	
						    }else if(ng>0 && ng<lev.nGammas()) {
						    	//levGroup.subGroups().size=number of different gammas in this level group and set in groupGammas, but
						    	//at this point groupGammas hasn't been called yet, so levGroup.subgroups.size=0 here
						    	
						    	if(levGroup.isGammasConsistent(lev, deltaE,false) && levGroup.hasComparableEnergyEntry(lev) && levGroup.hasOverlapJPI(lev)) {
							    	int ngOfLevGroup=0;
						    		for(int i=0;i<levGroup.nRecords();i++) {
							    		Level l=(Level)levGroup.getRecord(i);
							    		if(l.nGammas()>ngOfLevGroup)
							    			ngOfLevGroup=l.nGammas();
							    	}
						    		if(ng==ngOfLevGroup)
						    			isGood=true;
						    	}
						    	
			
						    }
						    
						    //if(lev.ES().contains("1335.6")) System.out.println("EnsdfGroup 1069: igroup="+igroup+" isGood="+isGood+" nMatchedGam="+nMatchedGam+" ng of lev="+ng);
										
						    if(isGood) {
						    	groupAvgDistMap.put(igroup,avgDist);
						    	
						    	found=true;
						    	nMatchedGam=ng;	
					    		if(avgDist<minAvgDist) {
					    			minAvgDist=avgDist;
					    			
							    	iBestGroup=igroup;	
							    	avgDistBest=avgDist;
					    		}
					    	
						    }
						    
						    /*
						    //debug
							if(lev.ES().contains("7419.23")) {
								System.out.println("EnsdfGroup 1395: igroup="+igroup+" isGood="+isGood+" nMatchedGam="+nMatchedGam+" ng of lev="+ng+"  ES="+
							lev.ES()+" dsid="+dsid+"  multiplicity="+multiplicity[irecord]);
								System.out.println("   avgDist="+avgDist+"    minAvgDist="+minAvgDist+" iBestGroup="+iBestGroup+" groupAvgDistMap.size="+groupAvgDistMap.size());
								System.out.println("    gamma subgroups size="+levGroup.subgroups().size()+" levGroup.isGammasConsistent(lev, deltaE,false)="+
										levGroup.isGammasConsistent(lev, deltaE,false)+" levGroup.hasComparableEnergyEntry="+levGroup.hasComparableEnergyEntry(lev)+
										" levGroup.hasOverlapJPI(lev)="+levGroup.hasOverlapJPI(lev));
								for(Record rec:levGroup.recordsV())
									System.out.println("     igroup "+igroup+": rec.ES="+rec.ES());
							}
						    */
						}
						
						if(iBestGroup>=0) {
							igroupFoundV.add(iBestGroup);
							groupAvgDistMap.remove(iBestGroup);
							for(int ig:groupAvgDistMap.keySet()) {
								float avgDist=groupAvgDistMap.get(ig);
								if(EnsdfUtil.isComparableEnergyValue(avgDist,avgDistBest,2)) {
									igroupFoundV.add(ig);
								}
							}
						}
						
						
					}else if(lev.nGammas()>0){
						//the current level has gammas but no level group containing any level with gammas  
						//find the closest group in energy
						Vector<Integer> tempIndexesV=new Vector<Integer>();
						tempIndexesV.addAll(indexesOfAssignedGroups);
						
						while(!found && tempIndexesV.size()>0) {//first find the closest group in energy and then check JPI

							
							iBestGroup=-1;
							for(int j=0;j<tempIndexesV.size();j++) {
								int igroup=tempIndexesV.get(j);
								RecordGroup levGroup=refRecordGroupsV.get(igroup);
								
						    	float avgDist=levGroup.findAverageDistanceToGroup(lev);
						    	if(avgDist<minAvgDist) {
							    	iBestGroup=igroup;
							    	minAvgDist=avgDist;
						    	}						
							}
							
							//System.out.println("EnsdfGroup 1504: IbestGroup ="+iBestGroup+" tempIndexesV.size()="+tempIndexesV.size()+" minAvgDist="+minAvgDist);
							
							if(iBestGroup<0)
								break;
							
							igroupFoundV.add(iBestGroup);//temporarily store matching groups by order of closest energies 	
							RecordGroup levGroup=refRecordGroupsV.get(iBestGroup);
							
							/*
							//debug
			                //if(lev.ES().contains("1485")) {
			                    System.out.println("EnsdfGroup 1980:  ES="+lev.ES()+" JPI="+lev.JPiS()+" altJPI="+lev.altJPiS()+" dsid="+dsid+"  multiplicity="+multiplicity[irecord]
			                    		+" levGroup.hasOverlapJPI(lev)="+levGroup.hasOverlapJPI(lev));
								for(int j=0;j<indexesOfAssignedGroups.size();j++) {
									int igroup=indexesOfAssignedGroups.get(j);
									RecordGroup g=refRecordGroupsV.get(igroup);
									for(int k=0;k<g.nRecords();k++) {
										Level l=(Level)g.recordsV().get(k);
										System.out.println("     in group "+igroup+"  lev="+l.ES()+"  JPI="+l.JPiS()+" altJPI="+l.altJPiS()+" DSID="+g.dsidsV().get(k));
									}
								}
			                //}
			                */
							
							if(levGroup.hasOverlapJPI(lev)) {
								found=true;
								break;
							}
							
							tempIndexesV.removeElement(iBestGroup);
						}

						//if no group found with overlapping JPI, take the group with the closest energy
						if(!found && igroupFoundV.size()>1) {
							found=true;
							iBestGroup=igroupFoundV.get(0);
							igroupFoundV.clear();
							igroupFoundV.add(iBestGroup);							
						}

						/*
					    //debug
			            if(Math.abs(lev.EF()-11668)<1) {
			            	System.out.println("EnsdfGroup 1559 NUCID"+this.NUCID+" E="+lev.EF()+"  mult="+multiplicity[irecord]+"   "+indexesOfGroupsForRecordVV.get(irecord).size());
			            	for(int j=0;j<indexesOfGroupsForRecordVV.get(irecord).size();j++) {
			            		int igroup=indexesOfGroupsForRecordVV.get(irecord).get(j);
			            		for(int k=0;k<refRecordGroupsV.get(igroup).nRecords();k++)
			            			System.out.println("group"+k+"  E="+refRecordGroupsV.get(igroup).getRecord(k).ES());
				            }
	    
			            }
			            */
					}else {	                
						//no level group containing any level with gammas and the current level has no gammas
						//find the closest group in energy
					
					}
					
                    /*
                    //debug
                    if(lev.ES().contains("7498")) {
                        System.out.println("EnsdfGroup 2051:  ES="+lev.ES()+" JPI="+lev.JPiS()+" altJPI="+lev.altJPiS()+" found="+found+" dsid="+dsid+"  multiplicity="+multiplicity[irecord]);
                        for(int j=0;j<indexesOfAssignedGroups.size();j++) {
                            int igroup=indexesOfAssignedGroups.get(j);
                            RecordGroup g=refRecordGroupsV.get(igroup);
                            for(int k=0;k<g.nRecords();k++) {
                                Level l=(Level)g.recordsV().get(k);
                                System.out.println("     in group "+igroup+"  lev="+l.ES()+"  JPI="+l.JPiS()+" altJPI="+l.altJPiS()+" DSID="+g.dsidsV().get(k));
                            }
                        }
                    }
                    */
					
					if(found) {
						
						//remove existing group assignments
						for(int j=0;j<indexesOfAssignedGroups.size();j++) {
							int igroup=indexesOfAssignedGroups.get(j);
							if(!igroupFoundV.contains(igroup)) {
								indexesOfRecordsInGroupVV.get(igroup).removeElement(irecord);
								matchingStrengthMapOfRecordsInGroupV.get(igroup).remove(irecord);
							}
						}
						
						//updated assignment
						//indexesOfRecordsInGroupVV.get(igroupFound).add(irecord);
						multiplicity[irecord]=igroupFoundV.size();
					}
				}
			}

			/*
			//debug
			for(int i=0;i<nRecords;i++){
				Record rec=recordsV.get(i);
				if(rec.ES().contains("1485")) {
					System.out.println("EnsdfGroup 1848: i="+i+" ES="+rec.ES()+" dsid="+dsid+"  multiplicity="+multiplicity[i]);
				}
			}
	        */

			/*
	        //debug
			int count=0;
			for(int i=0;i<nGroups;i++){
				RecordGroup group=refRecordGroupsV.get(i);
				Vector<Integer> indexesOfRecordsInGroupV=indexesOfRecordsInGroupVV.get(i);
				if(!isGamma && dsid.contains("(D,P)") && Math.abs(group.getMeanEnergy()-7615)<20) {
		        //if(!isGamma && dsid.contains("16O(3HE,6HE)")) {
		        	if(count==0) System.out.println("########### EnsdfGroup 2097:");  
		        	count++;
		            System.out.println("  *** original Records in group:"+i);
		            for(int k=0;k<group.nRecords();k++) 
		            	System.out.println("      k="+k+"  "+group.recordsV().get(k).ES()+"  "+group.dsidsV().get(k));
		                          
		            System.out.println("       records from "+dsid+" to be assigned to the group: ");
		            for(int k=0;k<indexesOfRecordsInGroupV.size();k++) 
		            	System.out.println("           k="+k+"  "+recordsV.get(indexesOfRecordsInGroupV.get(k)).ES());
		            
		        }
			}
			*/
			
			
			//Now insert records from the current dataset to corresponding reference record groups based on assignments above
			for(int i=0;i<nGroups;i++){
				Vector<Integer> indexesOfRecordsInGroupV=indexesOfRecordsInGroupVV.get(i);
				
				RecordGroup recordGroup=refRecordGroupsV.get(i);
				
				//at this stage, all nIndexes=0 or 1
				int nIndexes=indexesOfRecordsInGroupV.size();
				
				//debug
				//if(dsid.contains("RESONANCE")) System.out.println("    "+nIndexes);
				
				if(nIndexes==0)
					continue;
				
				int irecord=indexesOfRecordsInGroupV.get(0).intValue();  

				String xtagWithMarker=xtag;
				
				//boolean addRecord=true;
				if(!xtag.trim().isEmpty()) {
					int n=xtag.indexOf("(");
					Record rec=recordsV.get(irecord);
					if(multiplicity[irecord]>1){
						if(n>=0){
							if(!xtag.contains("*")) { 
								int n1=xtag.indexOf(")",n);
								if(n1>0)
									xtagWithMarker=xtag.substring(0, n1)+"*)"+xtag.substring(n1+1);		
							}
						}else
							xtagWithMarker=xtag+"(*)";						
						
					}else if(multiplicity[irecord]==1) {
						if(rec.q().equals("?")) {
							n=xtag.indexOf("(");					
							if(n>=0){
								if(!xtag.contains("?")) { 
									int n1=xtag.indexOf(")",n);
									if(n1>0)
										xtagWithMarker=xtag.substring(0, n1)+"?)"+xtag.substring(n1+1);
								}
							}else{
								xtagWithMarker=xtag+"(?)";
							}
						}

					}					
				}

				
				
				//if(recordsV.get(irecord).ES().contains("4761")) {
				//	System.out.println("EnsdfGroup 1895:  i record="+irecord+" ES="+recordsV.get(irecord).ES()
				//	        +"  multiplicity="+multiplicity[irecord]+" index size="+indexesOfRecordsInGroupV.size()+" dsid="+dsid+" xtag="+xtag+" xtagWithMarker="+xtagWithMarker);
				//}
				
				//if(addRecord)
					recordGroup.addRecord(recordsV.get(irecord), dsid, xtagWithMarker);	
				//else
				//	multplOffset[irecord]++;

			}

			//for(int i=0;i<multiplicity.length;i++)
			//	multiplicity[i]-=multplOffset[i];
			
		}

        /*
        //debug
        int count=0;
        for(int i=0;i<nGroups;i++){
            RecordGroup group=refRecordGroupsV.get(i);
            Vector<Integer> indexesOfRecordsInGroupV=indexesOfRecordsInGroupVV.get(i);
            if(!isGamma && dsid.contains("12C(58NI") && Math.abs(group.getMeanEnergy()-5714)<5) {
            //if(!isGamma && dsid.contains("16O(3HE,6HE)")) {
                if(count==0) System.out.println("########### EnsdfGroup 2414:");  
                count++;
                System.out.println("  *** original Records in group:"+i);
                for(int k=0;k<group.nRecords();k++) 
                    System.out.println("      k="+k+"  "+group.recordsV().get(k).ES()+"  "+group.dsidsV().get(k));
                              
                System.out.println("       records from "+dsid+" to be assigned to the group: ");
                for(int k=0;k<indexesOfRecordsInGroupV.size();k++) 
                    System.out.println("           k="+k+"  "+recordsV.get(indexesOfRecordsInGroupV.get(k)).ES());
                
            }
        }
        */
		
	    //////////////////////////////////////////////////////////////////
		//make new groups for those records not assigned to any group, that is, multiplicity==0		
		int[] groupIndexRangeL=new int[nRecords]; //minimum (left edge) of indexes of groups that a record is assigned to
		int[] groupIndexRangeR=new int[nRecords]; //maximum (right edge) of indexes of groups that a record is assigned to
		
		//before making new groups ,double check if those unassigned records actually belong to neighboring groups
		//even though their JPIs don't match (or contradict), because of very close level energies
		if(!isGamma) {
	        for(int i=0;i<nRecords;i++){
	            
	            if(multiplicity[i]>0)
	                continue;
	            

	            Level lev=(Level)recordsV.get(i);
                String js0=lev.JPiS();
                if(js0.isEmpty())
                	js0=lev.altJPiS();
                
                boolean isTentativeJ0=isTentativeSpin(js0);
                boolean isTentativeP0=isTentativeParity(js0);
                boolean isUniqueJ0=isUniqueSpin(js0);
                boolean isUniqueP0=isUniqueParity(js0);
                
                int ntries=0;
                Vector<RecordGroup> tempGroupsV=new Vector<RecordGroup>();
                tempGroupsV.addAll(refRecordGroupsV);
                for(int j=tempGroupsV.size()-1;j>=0;j--) {
                    RecordGroup g=tempGroupsV.get(j);
                    int index=g.dsidsV().indexOf(dsid);
                    if(index>=0) {
                    	String tag=g.xtagsV().get(index);
                    	if(!tag.contains("*"))
                    		 tempGroupsV.remove(g);
                    }                       
                }
                
                /*
                //debug
                if(dsid.contains("EC+B+")) {
                	System.out.println("EnsdfGroup 2292: unassigned level= "+lev.ES()+" jpis="+js0+" dsid="+dsid+
                			" tempGroupsV.size()="+tempGroupsV.size());
                }
                */
                
                Level prevLev=null,nextLev=null;
  
            	if(i>0) { 
            		prevLev=(Level)recordsV.get(i-1);
            	}
            	
            	if(i<recordsV.size()-1) 
            		nextLev=(Level)recordsV.get(i+1);           	
                
                while(ntries<=2 && tempGroupsV.size()>0) {
                	ntries++;
                	
    	            RecordGroup closestGroup=findClosestGroupForRecord(lev, tempGroupsV,true);
    	            boolean isGoodClosestGroup=true;

    	            
    	            int indexOfClosestGroup=refRecordGroupsV.indexOf(closestGroup);
    	            
    	            int dsidIndex=closestGroup.dsidsV().indexOf(dsid);

    	            /*
    	            //debug
    	            if(lev.ES().equals("5642")) {
    	            //if(lev.ES().contains("7601")&& dsid.contains("(D,P")) {
    	                System.out.println("@@@ EnsdfGroup 2496: dsidIndex="+dsidIndex);
    	                for(Record r:closestGroup.recordsV())
    	                    System.out.println("EnsdfGroup 2498: lev.ES="+lev.ES()+" in cloest ref group r.ES="+r.ES());
    	                
    	                RecordGroup prevGroup=null,nextGroup=null;
    	                if(indexOfClosestGroup>0) {
    	                    prevGroup=refRecordGroupsV.get(indexOfClosestGroup-1);
                            for(Record r:prevGroup.recordsV())
                                System.out.println("  in prev group r.ES="+r.ES());
    	                }
    	                 
    	                if(indexOfClosestGroup<refRecordGroupsV.size()-1) {
    	                    nextGroup=refRecordGroupsV.get(indexOfClosestGroup+1);
                            for(Record r:nextGroup.recordsV())
                                System.out.println("  in next group r.ES="+r.ES());
    	                }
    	            }
                    */
    	            
    	            String xtag1="";
    	            if(dsidIndex>=0) {
    	            	//the closestGroup already has a record from recordsV of current dataset
    	            	//this must be a multiply assigned record according to making of tempGroupsV above
    	                //and that record must be the prev level or next level
    	                
    	            	T rec1=(T) closestGroup.recordsV().get(dsidIndex);
    	            	int recIndex=recordsV.indexOf(rec1);
    	            	if(recIndex!=(i-1) && recIndex!=(i+1) && recIndex>=0)//not good closestGroup
    	            	    continue;
    	            	
    	            	xtag1=closestGroup.xtagsV().get(dsidIndex);//this could be the index of prev level or next level
    	            	                                           //xtag1 here must contain *
    	            	
    	            	Vector<Integer> indexesOfAssignedGroups=new Vector<Integer>();
    	            	if(recIndex>=0)
    	            		indexesOfAssignedGroups.addAll(indexesOfGroupsForRecordVV.get(recIndex));//DO NOT assign indexesOfGroupsForRecordVV.get(recIndex) to the temp variable.
    	            	                                                                             //See codes below
    	            	
    	                  //debug
                        //if(lev.ES().contains("5931")&& dsid.contains("(3HE,P")) {
                        //    System.out.println("EnsdfGroup 2237: lev.ES="+lev.ES()+" current rec Index="+i+" rec index for the ref of the same dataset in cloestGroup="+recIndex);
                        //}
                            
    	            	int orderInAssignedGroups=0;//for closestGroup
    	            	for(int k=0;k<indexesOfAssignedGroups.size();k++) {
    	            		int tempIgroup=indexesOfAssignedGroups.get(k).intValue();
    	            		if(indexOfClosestGroup>tempIgroup)
    	            			orderInAssignedGroups++;
    	            		
    	            		/*
                            //debug
                            if(dsid.contains("3HE,P")) {
                            	System.out.println("EnsdfGroup 2224: unassigned level= "+lev.ES()+" jpis="+js0+" dsid="+dsid+
                            			" tempGroupsV.size()="+tempGroupsV.size()+" dsidIndex="+dsidIndex+" recIndex="+recIndex+
                            			" rec="+rec1.ES()+" i="+i+" tempIgroup="+tempIgroup);
                            	System.out.println("   orderInAssignedGroups="+orderInAssignedGroups+
                            			" multiplicity[recIndex]="+multiplicity[recIndex]+" indexOfClosestGroup="+indexOfClosestGroup);
                            	
                            }
                            */
    	            	}
    	            	
    	            	//NOTE that indexesOfAssignedGroups.size()>=2
    	            	
    	            	boolean done=false;
    	            	int dsidIndex1=0;
    	            	xtag1="";
    	            	if(recIndex>=0) {
    	            		
            	            /*
                            //debug
                            if(dsid.contains("3HE,P")) {
                            	System.out.println("EnsdfGroup 2241: unassigned level= "+lev.ES()+" jpis="+js0+" dsid="+dsid+
                            			" tempGroupsV.size()="+tempGroupsV.size()+" dsidIndex="+dsidIndex+" recIndex="+recIndex+
                            			" rec="+rec1.ES()+" i="+i);
                            	System.out.println("   orderInAssignedGroups="+orderInAssignedGroups+
                            			" multiplicity[recIndex]="+multiplicity[recIndex]+" indexOfClosestGroup="+indexOfClosestGroup);
                            	
                            }
                            */
    	            		
    	            		if(recIndex==i-1) {
    	            			//rec1 is previous level
    	            			if(orderInAssignedGroups==indexesOfAssignedGroups.size()-1) {
    	            			    //the closestGroup is the last one of all groups that the rec1 (previous level) is assigned to 
    	            			    //then assign the current level to the closestGroup, remove previous level (from the same dataset as the current level)
    	            			    //from this group
    	            			    
                                    float thisLevDiff2ClosestGroup=closestGroup.findAverageDistanceToGroup(lev);
                                    float prevLevDiff2ClosestGroup=closestGroup.findAverageDistanceToGroup(rec1);
                                    float prevLevDiff2PrevGroup=refRecordGroupsV.get(indexesOfAssignedGroups.get(orderInAssignedGroups-1)).findAverageDistanceToGroup(rec1);
                                    
                                    if(thisLevDiff2ClosestGroup>2*prevLevDiff2PrevGroup && thisLevDiff2ClosestGroup>5*prevLevDiff2ClosestGroup) {
                                        //still not good to be assigned to any group
                                        done=false;
                                    }else {
                                        multiplicity[recIndex]-=1;
                                        indexesOfGroupsForRecordVV.get(recIndex).removeElement(indexOfClosestGroup);                                
                                        indexesOfRecordsInGroupVV.get(indexOfClosestGroup).removeElement(recIndex);                                 
                                        
                                        closestGroup.removeRecord(rec1);
                                        
                                        done=true;
                                    }

    	            			}else if(indexesOfAssignedGroups.size()>2 && orderInAssignedGroups>0) {
    	            			    //the closestGroup is in the middle of all groups that the rec1 (previous level) is assigned to ,
    	            			    //then remove rec1 (previous level) from all assigned groups after the closeGroup and assign the 
    	            			    //current level to the closestGroup
    	            			    
    	        	            	for(int k=0;k<indexesOfAssignedGroups.size();k++) {
    	        	            		int tempIgroup=indexesOfAssignedGroups.get(k);
    	        	            		if(tempIgroup>=indexOfClosestGroup) {
    	            	                    multiplicity[recIndex]-=1;
    	            	                    indexesOfGroupsForRecordVV.get(recIndex).removeElement(tempIgroup);           	                    
    	            	                    indexesOfRecordsInGroupVV.get(tempIgroup).removeElement(recIndex);
    	            	                    
    	            	                    refRecordGroupsV.get(tempIgroup).removeRecord(rec1);
    	        	            		}
    	        	            	}
    	        	            	
    	        	            	done=true;
    	            			}
    	            		}else if(recIndex==i+1){
                                      
    	            			//rec1 is next level
    	            			if(orderInAssignedGroups==0) {
                                    //the closestGroup is the first one of all groups that the rec1 (next level) is assigned to 
                                    //then assign the current level to the closestGroup, remove next level (from the same dataset as the current level)
                                    //from this group
    	            			    
    	            			    float thisLevDiff2ClosestGroup=closestGroup.findAverageDistanceToGroup(lev);
    	            			    float nextLevDiff2ClosestGroup=closestGroup.findAverageDistanceToGroup(rec1);
    	            			    float nextLevDiff2NextGroup=refRecordGroupsV.get(indexesOfAssignedGroups.get(1)).findAverageDistanceToGroup(rec1);
    	            			    if(thisLevDiff2ClosestGroup>2*nextLevDiff2NextGroup && thisLevDiff2ClosestGroup>5*nextLevDiff2ClosestGroup) {
    	            			        //still not good to be assigned to any group
    	            			        done=false;
    	            			    }else {
                                        multiplicity[recIndex]-=1;
                                        indexesOfGroupsForRecordVV.get(recIndex).removeElement(indexOfClosestGroup);                                
                                        indexesOfRecordsInGroupVV.get(indexOfClosestGroup).removeElement(recIndex);
                                        
                                        closestGroup.removeRecord(rec1);
                                        
                                        done=true;
    	            			    }

    	            			}else if(indexesOfAssignedGroups.size()>2 && orderInAssignedGroups<indexesOfAssignedGroups.size()-1) {
                                    //the closestGroup is in the middle of all groups that the rec1 (next level) is assigned to ,
                                    //then remove rec1 (next level) from all assigned groups before the closeGroup and assign the 
                                    //current level to the closestGroup
    	            			    
    	        	            	for(int k=0;k<indexesOfAssignedGroups.size();k++) {
    	        	            		int tempIgroup=indexesOfAssignedGroups.get(k);
    	        	            		if(tempIgroup<=indexOfClosestGroup) {
    	            	                    multiplicity[recIndex]-=1;
    	            	                    indexesOfGroupsForRecordVV.get(recIndex).removeElement(tempIgroup);           	                    
    	            	                    indexesOfRecordsInGroupVV.get(tempIgroup).removeElement(recIndex);
    	            	                    
    	            	                    refRecordGroupsV.get(tempIgroup).removeRecord(rec1);
    	        	            		}
    	        	            	}
    	        	            	
    	            				done=true;
    	            			}
    	            		}
    	            		
    	                    
    	            		if(done) {
    	            			
        	                    //remove * in xtag if original multiplicity==2
        	                    if(multiplicity[recIndex]==1) {
        	                    	int igroup1=indexesOfGroupsForRecordVV.get(recIndex).get(0);
        	                    	RecordGroup tempGroup=refRecordGroupsV.get(igroup1);
        	                    	dsidIndex1=tempGroup.dsidsV().indexOf(dsid);
        	                    	if(dsidIndex1>=0) {
        	                    		xtag1=tempGroup.xtagsV().get(dsidIndex1);
        	                    		xtag1=xtag1.replace("(*)","").replace("*", "");
        	                    		tempGroup.xtagsV().remove(dsidIndex1);
        	                    		tempGroup.xtagsV().add(dsidIndex1, xtag1);
        	                    	}

        	                    }
        	                            	    	            
        	                    
            	            	//matched=true;
        	                    multiplicity[i]+=1;
        	                    indexesOfGroupsForRecordVV.get(i).add(indexOfClosestGroup);
        	                    
        	                    indexesOfRecordsInGroupVV.get(indexOfClosestGroup).add(i);
        	                    
        	                    matchingStrengthMapOfRecordsInGroupV.get(indexOfClosestGroup).put(i,new MatchingStrength(5));
        	                    
        	                    closestGroup.addRecord(lev, dsid, xtag);   
        	                    
        	                    /*
        	    	            //debug
        	    	            //if(lev.ES().equals("4761")) {
        	    	            if(lev.ES().contains("5931")&& dsid.contains("(3HE,P")) {
        	    	                System.out.println("EnsdfGroup 2314:  lev="+lev.ES()+" jpi="+lev.JPiS()+" match E(10 keV)="+closestGroup.hasComparableEnergyEntry(lev, 10,true)+" "+closestGroup.hasOverlapJPI(lev));
        	                        System.out.println("   delta EL="+deltaEL+" matchE(deltaEL)="+closestGroup.hasComparableEnergyEntry(lev,deltaEL,false));
        	    	        	    	                
        	                        for(int j=0;j<closestGroup.recordsV().size();j++) {
        	                            Level l=(Level)closestGroup.getRecord(j);
        	                            String tag=closestGroup.xtagsV().get(j);                    
        	                            System.out.println("    in group: lev="+lev.ES()+" l.E="+l.ES()+" jpi="+l.JPiS()+" dsid="+closestGroup.dsidsV().get(j)+" tag="+tag);
        	          
        	                        }	                
        	    	            }
        	    	            */
        	    	            
        	                    break;//break out of while loop
    	            		}else {
    	            		    isGoodClosestGroup=false;
    	            		}
    	            	}
    	            }else {
    	                //closestGroup does not contain a record from the current recordsV
    	                //check if there is any group between current level and the cloestGroup, that contains a level from recordsV
    	                //if any, then the cloestGroup is not good and the current level can't be placed into any existing group
    	                
                        Vector<Integer> indexesOfAssignedGroupsPrevLev=new Vector<Integer>();
                        Vector<Integer> indexesOfAssignedGroupsNextLev=new Vector<Integer>();
                        Vector<Integer> tempV1=new Vector<Integer>();
                        Vector<Integer> tempV2=new Vector<Integer>();
                                               
                        if(prevLev!=null) {
                            indexesOfAssignedGroupsPrevLev.addAll(indexesOfGroupsForRecordVV.get(i-1));
                            tempV1.addAll(indexesOfAssignedGroupsPrevLev);
                            
                            for(int k=0;k<indexesOfAssignedGroupsPrevLev.size();k++) {
                                int tempIgroup=indexesOfAssignedGroupsPrevLev.get(k);
                                if(tempIgroup>=indexOfClosestGroup) 
                                    tempV1.removeElement(tempIgroup);                                 
                            }
                            if(tempV1.isEmpty())
                                isGoodClosestGroup=false;
                        }
                        
                        if(nextLev!=null && isGoodClosestGroup) {
                            indexesOfAssignedGroupsNextLev.addAll(indexesOfGroupsForRecordVV.get(i+1));
                            tempV2.addAll(indexesOfAssignedGroupsNextLev);
                            
                            for(int k=0;k<indexesOfAssignedGroupsNextLev.size();k++) {
                                int tempIgroup=indexesOfAssignedGroupsNextLev.get(k);
                                if(tempIgroup<=indexOfClosestGroup) 
                                    tempV2.removeElement(tempIgroup);                                 
                            }
                            
                            if(tempV2.isEmpty())
                                isGoodClosestGroup=false;
                        }
                        
                        /*
	    	            //debug
	    	            if(lev.ES().equals("5714.0")) {
	    	            //if(lev.ES().contains("5642")&& dsid.contains("EC+B+")) {
	    	                System.out.println("EnsdfGroup 2754:  lev="+lev.ES()+" jpi="+lev.JPiS()+" match E(10 keV)="+closestGroup.hasComparableEnergyEntry(lev, 10,true)+" "+closestGroup.hasOverlapJPI(lev));
	                        System.out.println("   delta EL="+deltaEL+" matchE(deltaEL)="+closestGroup.hasComparableEnergyEntry(lev,deltaEL,false));
	                        System.out.println("   isGoodClosestGroup="+isGoodClosestGroup);
	    	        	    	                
	                        for(int j=0;j<closestGroup.recordsV().size();j++) {
	                            Level l=(Level)closestGroup.getRecord(j);
	                            String tag=closestGroup.xtagsV().get(j);                    
	                            System.out.println("    in group: lev="+lev.ES()+" l.E="+l.ES()+" jpi="+l.JPiS()+" dsid="+closestGroup.dsidsV().get(j)+" tag="+tag);
	          
	                        }	                
	    	            }
	    	            */
                        
                        if(isGoodClosestGroup) {
                            for(int k=0;k<indexesOfAssignedGroupsPrevLev.size();k++) {
                                int tempIgroup=indexesOfAssignedGroupsPrevLev.get(k);
                                if(tempIgroup>=indexOfClosestGroup) {
                                    multiplicity[i-1]-=1;
                                    indexesOfGroupsForRecordVV.get(i-1).removeElement(tempIgroup);                                 
                                    indexesOfRecordsInGroupVV.get(tempIgroup).removeElement(i-1);
                                    
                                    refRecordGroupsV.get(tempIgroup).removeRecord(prevLev);
                                }
                            }
                            indexesOfAssignedGroupsPrevLev.clear();
                            indexesOfAssignedGroupsPrevLev.addAll(tempV1);
                            
                            for(int k=0;k<indexesOfAssignedGroupsNextLev.size();k++) {
                                int tempIgroup=indexesOfAssignedGroupsNextLev.get(k);
                                if(tempIgroup<=indexOfClosestGroup) {
                                    multiplicity[i+1]-=1;
                                    indexesOfGroupsForRecordVV.get(i+1).removeElement(tempIgroup);                                 
                                    indexesOfRecordsInGroupVV.get(tempIgroup).removeElement(i+1);
                                    
                                    refRecordGroupsV.get(tempIgroup).removeRecord(nextLev);
                                }
                            }
                            indexesOfAssignedGroupsNextLev.clear();
                            indexesOfAssignedGroupsNextLev.addAll(tempV2);
                        }
    	            }
    	            
    	            /*
    	            //debug
    	            if(lev.ES().equals("5642")) {
    	            //if(lev.ES().contains("7601")&& dsid.contains("(D,P")) {
    	                System.out.println("EnsdfGroup 2800:  lev="+lev.ES()+" jpi="+lev.JPiS()+" match E(10 keV)="+closestGroup.hasComparableEnergyEntry(lev, 10,true)+" "+closestGroup.hasOverlapJPI(lev));
                        System.out.println("   delta EL="+deltaEL+" matchE(deltaEL)="+closestGroup.hasComparableEnergyEntry(lev,deltaEL,false)+" isGood="+isGoodClosestGroup);
    	                for(int j=0;j<closestGroup.recordsV().size();j++) {
                            Level l=(Level)closestGroup.getRecord(j);
                            String tag=closestGroup.xtagsV().get(j);                    
                            System.out.println("    in group: lev="+lev.ES()+" l.E="+l.ES()+" jpi="+l.JPiS()+" dsid="+closestGroup.dsidsV().get(j)+" tag="+tag);
          
                        }	                
    	            }
    	            */
            		
    	            if(!isGoodClosestGroup)
    	                continue;
    	            
    	            if(closestGroup.hasComparableEnergyEntry(lev, 10,true) && !closestGroup.hasOverlapJPI(lev) && closestGroup.hasCloseJPI(lev,3)) {
    	                boolean matched=false;
    	                MatchingStrength matchingStrength=new MatchingStrength(-1);
    	                if(lev.nGammas()==0 && closestGroup.hasComparableEnergyEntry(lev, 5) ) {
    	                    matched=true;
    	                    matchingStrength=new MatchingStrength(4);
    	                    
                            for(int j=0;j<closestGroup.recordsV().size();j++) {
                                Level l=(Level)closestGroup.getRecord(j);
                                String tag=closestGroup.xtagsV().get(j);
                                
                                String js=l.JPiS();
                                if(js.isEmpty())
                                	js=l.altJPiS();
                                
                                /*
                                if(lev.ES().contains("653")) {
                                	System.out.println("EnsdfGroup 2700:  lev="+lev.ES()+"  l.E="+l.ES()+" l.jpi="+js+" dsid="+closestGroup.dsidsV().get(j)+" tag="+tag);
                                    System.out.println("  isTentativeParity(js)="+isTentativeParity(js) +" isTentativeP0="+isTentativeP0); 
                                    System.out.println("  isTentativeSpin(js)="+isTentativeSpin(js)+" isTentativeJ0="+!isTentativeJ0);
                                    System.out.println("  isUniqueSpin(js)="+isUniqueSpin(js)+"  isUniqueJ0="+isUniqueJ0);
                                    System.out.println("  isUniqueParity(js)="+isUniqueParity(js)+"  isUniqueP0="+isUniqueP0);
                                }
                                */
                                
                                String xtagMarker=Util.getXTagMarker(tag);
                                if(xtagMarker.contains("*") || xtagMarker.contains("?"))
                                    continue;
                                
                                if( (!isTentativeParity(js) && !isTentativeP0) || (!isTentativeSpin(js) && !isTentativeJ0) 
                                		|| (isUniqueSpin(js)&&isUniqueJ0) || (isUniqueParity(js)&&isUniqueP0)){
                                    matched=false;
                                    matchingStrength=new MatchingStrength(-1);
                                    break;
                                }
                            }	                    
    	                    
    	                }else if(closestGroup.isGammasConsistent(lev,5,true)) {
    	                	matchingStrength=new MatchingStrength(4);
                            for(int j=0;j<closestGroup.recordsV().size();j++) {
                                Level l=(Level)closestGroup.getRecord(j);
                                String tag=closestGroup.xtagsV().get(j);
                                if(tag.contains("*") || tag.contains("?"))
                                    continue;
                                
                                String js=l.JPiS();
                                if(js.isEmpty())
                                	js=l.altJPiS();
                                
                                if( (!isTentativeParity(js) && !isTentativeP0) || (!isTentativeSpin(js) && !isTentativeJ0) 
                                		|| (isUniqueSpin(js)&&isUniqueJ0) || (isUniqueParity(js)&&isUniqueP0)){
    	                            matched=false;
    	                            matchingStrength=new MatchingStrength(-1);
    	                            break;
    	                        }
    	                    }
                            
        	                //further check
        	                if(!matched) {
            	            	RecordGroup prevTempGroup=null,nextTempGroup=null;
            	            	int i1=tempGroupsV.indexOf(closestGroup);
            	            	if(i1>0)
            	            		prevTempGroup=tempGroupsV.get(i1-1);
            	            	if(i1<tempGroupsV.size()-1)
            	            		nextTempGroup=tempGroupsV.get(i1+1);
            	            	
            	            	if(prevTempGroup!=null && prevTempGroup.hasComparableEnergyEntry(lev, 10,true) && prevTempGroup.hasOverlapJPI(lev) ) {
            	            		matched=false;
            	            	}else if(nextTempGroup!=null && nextTempGroup.hasComparableEnergyEntry(lev, 10,true) && nextTempGroup.hasOverlapJPI(lev) ) {
            	            		matched=false;
            	            	}else if(closestGroup.hasComparableEnergyEntry(lev, 2,true) && closestGroup.isGammasConsistent(lev,2,true) && closestGroup.hasCloseJPI(lev,1)) {
            	                    matched=true;
            	                    matchingStrength=new MatchingStrength(3);
        	                	}

        	                }
    	                }
    	                
    	                
    	                if(matched) {
    	                    multiplicity[i]+=1;
    	                    indexesOfGroupsForRecordVV.get(i).add(indexOfClosestGroup);
    	                    
    	                    indexesOfRecordsInGroupVV.get(indexOfClosestGroup).add(i);                   
    	                    matchingStrengthMapOfRecordsInGroupV.get(indexOfClosestGroup).put(i,matchingStrength);
    	                    
    	                    closestGroup.addRecord(lev, dsid, xtag);   
    	                    
    	                    break;//break out of while loop
    	                }
    	                
    	                tempGroupsV.remove(closestGroup);
    	            }else {
    	  
    	            	float deltaE0=(float)Math.min(2.0*deltaEL, lev.ERPF()*0.2f);
    	            	
    	            	/*
        	            //debug
        	            if(lev.ES().equals("461")) {
        	            //if(lev.ES().contains("7036")&& dsid.contains("(3HE,T")) {
        	                System.out.println("EnsdfGroup 2763:  lev="+lev.ES()+" jpi="+lev.JPiS()+" overlapJPI="+closestGroup.hasOverlapJPI(lev));
                            System.out.println("   delta EL="+deltaE0+" matchE(deltaEL)="+closestGroup.hasComparableEnergyEntry(lev,deltaE0,true));
        	                for(int j=0;j<closestGroup.recordsV().size();j++) {
                                Level l=(Level)closestGroup.getRecord(j);
                                String tag=closestGroup.xtagsV().get(j);                    
                                System.out.println("    in group: lev="+lev.ES()+" l.E="+l.ES()+" jpi="+l.JPiS()+" dsid="+closestGroup.dsidsV().get(j)+" tag="+tag);
              
                            }	                
        	            }
        	            */
    	            	
    	            	if(closestGroup.hasComparableEnergyEntry(lev,deltaE0,true) && closestGroup.hasOverlapJPI(lev) 
    	            		//&& !closestGroup.hasLevelGamma() && lev.nGammas()==0) {
    	            		&& lev.nGammas()==0) {
    	            			
    	            		
    	            		if(prevLev!=null && closestGroup.hasComparableEnergyEntry(prevLev,10,true) && closestGroup.hasOverlapJPI(prevLev)) {
    	            			
    	            			/*
                	            //debug
                	            if(lev.ES().equals("653")) {
                	            //if(lev.ES().contains("7036")&& dsid.contains("(3HE,T")) {
                	                System.out.println("EnsdfGroup 2784:  prevlev="+prevLev.ES()+" jpi="+prevLev.JPiS()+" overlapJPI="+closestGroup.hasOverlapJPI(prevLev));              
                	            }
                	            */
    	            			
    	            			tempGroupsV.remove(closestGroup);
    	            	
    	            			continue;
    	            		}
    	            		
    	            		/*
            	            //debug
            	            if(lev.ES().equals("653")) {
            	            //if(lev.ES().contains("7036")&& dsid.contains("(3HE,T")) {
            	                System.out.println("EnsdfGroup 2798:  lev="+lev.ES()+" jpi="+lev.JPiS()+" overlapJPI="+closestGroup.hasOverlapJPI(lev));              
            	            }
            	            */
    	            		
    	            		if(nextLev!=null && closestGroup.hasComparableEnergyEntry(nextLev,10,true) && closestGroup.hasOverlapJPI(nextLev)) {
    	            			tempGroupsV.remove(closestGroup);
    	            			continue;
    	            		}
        	            	
    	            		/*
            	            //debug
            	            if(lev.ES().equals("653")) {
            	            //if(lev.ES().contains("7036")&& dsid.contains("(3HE,T")) {
            	                System.out.println("EnsdfGroup 2810:  lev="+lev.ES()+" jpi="+lev.JPiS()+" overlapJPI="+closestGroup.hasOverlapJPI(lev));              
            	            }
            	            */
    	            		
        	            	RecordGroup prevGroup=null,nextGroup=null;
        	            	
        	            	if(indexOfClosestGroup>0) {       	            		 
           	            		 prevGroup=refRecordGroupsV.get(indexOfClosestGroup-1);     	            		
           	            		 if(prevGroup.hasComparableEnergyEntry(lev,10,true) && prevGroup.hasOverlapJPI(lev)) {
           	            			tempGroupsV.remove(closestGroup);
        	            			continue;
           	            		 }
        	            	}
        	            	
        	            	/*
            	            //debug
            	            if(lev.ES().equals("653")) {
            	            //if(lev.ES().contains("7036")&& dsid.contains("(3HE,T")) {
            	                System.out.println("EnsdfGroup 2829:  lev="+lev.ES()+" jpi="+lev.JPiS()+" overlapJPI="+closestGroup.hasOverlapJPI(lev));              
            	            }
            	            */
        	            	
        	            	if(indexOfClosestGroup<refRecordGroupsV.size()-1) {       	            		 
          	            		 nextGroup=refRecordGroupsV.get(indexOfClosestGroup+1);
           	            		 if(nextGroup.hasComparableEnergyEntry(lev,10,true) && nextGroup.hasOverlapJPI(lev)) {
           	            			tempGroupsV.remove(closestGroup);
        	            			continue;
           	            		 }
        	            	}
        	            	
        	            	/*
            	            //debug
            	            if(lev.ES().equals("653")) {
            	            //if(lev.ES().contains("7036")&& dsid.contains("(3HE,T")) {
            	                System.out.println("EnsdfGroup 2844:  lev="+lev.ES()+" jpi="+lev.JPiS()+" overlapJPI="+closestGroup.hasOverlapJPI(lev));              
            	            }
            	            */
        	            	
        	            	//matched=true;
    	                    multiplicity[i]+=1;
    	                    indexesOfGroupsForRecordVV.get(i).add(indexOfClosestGroup);
    	                    
    	                    indexesOfRecordsInGroupVV.get(indexOfClosestGroup).add(i);
    	                    
    	                    matchingStrengthMapOfRecordsInGroupV.get(indexOfClosestGroup).put(i,new MatchingStrength(5));
    	                    
    	                    closestGroup.addRecord(lev, dsid, xtag);   
    	                    
    	                    break;//break out of while loop

    	            		
    	            
    	            	}
    	            	
    	            	
    	            }//end if
                }//end while ntries

	        }//end for record		    
		}

        /*
       //debug
       int count=0;
       for(int i=0;i<nGroups;i++){
           RecordGroup group=refRecordGroupsV.get(i);
           Vector<Integer> indexesOfRecordsInGroupV=indexesOfRecordsInGroupVV.get(i);
           if(!isGamma && Math.abs(group.getMeanEnergy()-9447)<20) {
           //if(!isGamma && dsid.contains("(D,P)") && Math.abs(group.getMeanEnergy()-7615)<20) {
           //if(!isGamma && dsid.contains("16O(3HE,6HE)")) {
               if(count==0) System.out.println("########### EnsdfGroup 2776:");  
               count++;
               System.out.println("  *** original Records in group:"+i);
               for(int k=0;k<group.nRecords();k++) 
                   System.out.println("      k="+k+"  "+group.recordsV().get(k).ES()+"  "+group.dsidsV().get(k));
                             
               System.out.println("       records from "+dsid+" to be assigned to the group: ");
               for(int k=0;k<indexesOfRecordsInGroupV.size();k++) 
                   System.out.println("           k="+k+"  "+recordsV.get(indexesOfRecordsInGroupV.get(k)).ES());
               
           }
       }
       */
		
		int prevGroupIndexRangeL=-1;
		for(int i=0;i<nRecords;i++){

			if(multiplicity[i]>0){//record has been assigned 
				groupIndexRangeL[i]=indexesOfGroupsForRecordVV.get(i).firstElement().intValue();
				prevGroupIndexRangeL=groupIndexRangeL[i];
			}else{ //record has not been (can not) assigned to any group
				//the minimum index for the new group of this record can not be less than prevGroupIndexRangeL
				groupIndexRangeL[i]=prevGroupIndexRangeL+1;
				
				//if no records at the beginning have been assigned, those records have the same groupIndexRangeL
				prevGroupIndexRangeL=groupIndexRangeL[i]-1;

			}
		}
		
		int nextGroupIndexRangeR=nGroups+1;
		for(int i=nRecords-1;i>=0;i--){
			if(multiplicity[i]>0){//record has been assigned 
				groupIndexRangeR[i]=indexesOfGroupsForRecordVV.get(i).lastElement().intValue();
				nextGroupIndexRangeR=groupIndexRangeR[i];
			}else{//record has not been (can not) assigned to any group
				//the maximum index for the new group of this record can not be great than nextGroupIndexRangeR
				groupIndexRangeR[i]=nextGroupIndexRangeR-1;
				
				//if no records at the end have been assigned, those records have the same groupIndexRangeR
				nextGroupIndexRangeR=groupIndexRangeR[i]+1;
				
				//System.out.println("EnsdfGroup 2198: unassigned level= "+recordsV.get(i).ES()+"  dsid="+dsid);
			}
		}
        
		/*
		//debug
		for(int i=0;i<nRecords;i++){
			Record rec=recordsV.get(i);
			if(rec.ES().contains("9447")) {
				System.out.println(" EnsdfGroup 2859: i="+i+" ES="+rec.ES()+" dsid="+dsid+"  multiplicity="+multiplicity[i]
						+" indexL="+groupIndexRangeL[i]+" indexR="+groupIndexRangeR[i]);
			}
		}
        */
		
		//insert from right, so that the original indexes below insertion point will
		//not get affected by the increased size of the new group
		//NOTE: since refRecordGroupsV changes here, the original indexesOfRecordsInGroupVV is no longer valid for the changed one
		int lastInsertPos=groupIndexRangeR[nRecords-1];		
		for(int i=nRecords-1;i>=0;i--){
		    
			if(multiplicity[i]>0){
				if(lastInsertPos>groupIndexRangeR[i])
					lastInsertPos=groupIndexRangeR[i];
				
				continue;
			}

			T rec=recordsV.get(i);
			float ef=rec.ERPF();
			
	    	
			RecordGroup newGroup=new RecordGroup();
			newGroup.addRecord(rec, dsid, xtag);
			
            //if(!isGamma)
			//System.out.println("EnsdfGroup 2247:  l="+rec.ES()+" jpi="+((Level)rec).JPiS()+"  multiplicty="+multiplicity[i]);

	        
			int rangeL=groupIndexRangeL[i];
			int rangeR=lastInsertPos;
			int igroup=-1;
			
			//System.out.println("EnsdfGroup 3411: "+rangeL+"  "+rangeR+"  size="+refRecordGroupsV.size());
			
			int size=refRecordGroupsV.size();
			//if(rangeR>0 && ef>refRecordGroupsV.get(rangeR-1).getMinERecord().EF())
			if(rangeR>0 && rangeR<=size && ef>refRecordGroupsV.get(rangeR-1).getReferenceRecord().ERPF())	
				igroup=rangeR;
			//else if(rangeL>=0 && ef<refRecordGroupsV.get(rangeL).getMaxERecord().EF())
			else if(rangeL>=0 && rangeL<size && ef<refRecordGroupsV.get(rangeL).getReferenceRecord().ERPF())
				igroup=rangeL;
			else{
				for(int j=rangeL+1;j<rangeR;j++){
					//float record0E=refRecordGroupsV.get(j).recordsV().get(0).EF();
					float record0E=refRecordGroupsV.get(j).getReferenceRecord().ERPF();
					
					if(ef<=record0E){
						igroup=j;
						break;
					}
				}
				
				if(igroup<0){
					igroup=rangeR;
				}
			}


			/*
	    	//debug
	    	if(rec.ES().contains("9447") && (rec instanceof Level)){
	    		System.out.println("In ENSFDGroup line 2918: es="+rec.ES()+"  e="+rec.EF()+"  igroup="+igroup+"  dsid="+dsid+"  group size="+refRecordGroupsV.size());
	    		System.out.println("           rangeL="+rangeL+" maxE="+refRecordGroupsV.get(rangeL).getMaxERecord().EF()+"  rangeR="+rangeR+" minE="+refRecordGroupsV.get(rangeR-1).getMinERecord().EF());
	    		RecordGroup group=refRecordGroupsV.get(igroup);
			    for(int m=0;m<group.recordsV().size();m++){
				    Level l=(Level)group.recordsV().get(m);
					System.out.println("     l.ES="+l.ES()+" EF="+l.EF()+"  dsid="+group.dsidsV().get(m));
				}	
	    	}
			*/
			
			
			if(isGoodES[i] && igroup>=0){ 
				refRecordGroupsV.insertElementAt(newGroup, igroup);
				lastInsertPos=igroup;
				
				/*
				//debug
				System.out.println("EnsdfGroup 2280: ES="+rec.ES()+"  isGoodES="+isGoodES[i]+"  igroup="+igroup+" dsid="+dsid);
				for(Record r:newGroup.recordsV())
				    System.out.println("  inserted new group rec="+rec.ES()+" dsid="+newGroup.dsidsV().get(newGroup.recordsV().indexOf(r)));
				
				if(Math.abs(rec.EF()-7036)<30) {
	                for(RecordGroup g:refRecordGroupsV) {
	                    int ig=refRecordGroupsV.indexOf(g);
	                    if(Math.abs(g.getReferenceRecord().EF()-7036)<30) {
	                        System.out.println("  in group: "+ig);
	                        for(Record r:g.recordsV())
	                            System.out.println("    rec="+r.ES()+"    dsid="+g.dsidsV().get(g.recordsV().indexOf(r)));                      
	                    }

	                }				    
				}
                */
			}
			//else{
			//	System.out.println(" ES="+rec.ES()+"  isGoodES="+isGoodES[i]+"  igroup="+igroup);
			//}
						
		}
		
		/*
        int count=0;
        for(int i=0;i<refRecordGroupsV.size();i++){
            RecordGroup group=refRecordGroupsV.get(i);
            if(!isGamma && dsid.contains("(3HE,P)") && Math.abs(group.getMeanEnergy()-7500)<20) {
            //if(!isGamma && dsid.contains("16O(3HE,6HE)")) {
                if(count==0) System.out.println("########### EnsdfGroup 2876:");  
                count++;
                System.out.println("  *** original Records in group:"+i);
                for(int k=0;k<group.nRecords();k++) 
                    System.out.println("      k="+k+"  "+group.recordsV().get(k).ES()+"  "+group.dsidsV().get(k));                  
            }
        }
		*/
		
		/*
		//debug
		if(!isGamma) {
	        for(RecordGroup g:refRecordGroupsV) {
	            Level refRecord=(Level)g.getReferenceRecord();
	            int n=g.recordsV().indexOf(refRecord);
	            if(refRecord.ES().contains("7036")) {
	                System.out.println("  group: refRecord="+refRecord.ES()+" "+refRecord.JPiS()+" dsid="+g.dsidsV().get(n));
	                for(int i=0;i<g.recordsV().size();i++) {
	                    Level l=(Level)g.recordsV().get(i);
	                    System.out.println("     rec in group="+l.ES()+"   "+l.JPiS()+" dsid="+g.dsidsV().get(i));
	                }
	            }
	            
	        }		    
		}
        */		
		
    	return;
    }
	
	private  <T extends Record> void preFilterGroupAssignmentsForRecords(Vector<T> recordsV,Vector<RecordGroup> refRecordGroupsV,Vector<Vector<Integer>> indexesOfRecordsInGroupVV,
			Vector<HashMap<Integer,MatchingStrength>> matchingStrengthMapOfRecordsInGroupV,float deltaE) {

		if(recordsV.size()==0 || !(recordsV.get(0) instanceof Level))
			return;
		
		int nRecords=recordsV.size();
		int nGroups=refRecordGroupsV.size();
	
		Vector<Vector<Integer>> indexesOfGroupsForRecordVV=new Vector<Vector<Integer>>();//store number of groups that each record is assigned to		
		for(int i=0;i<nRecords;i++)
			indexesOfGroupsForRecordVV.add(new Vector<Integer>());
		
		for(int i=0;i<nGroups;i++){
			
			//indexes of the records (from the same dataset) to be inserted to the group at igroup=i
			Vector<Integer> indexesOfRecordsInGroupV=indexesOfRecordsInGroupVV.get(i);		
			int nIndexes=indexesOfRecordsInGroupV.size();	
			if(nIndexes==0)
				continue;                				
		
			for(int j=0;j<nIndexes;j++) {
				int irecord=indexesOfRecordsInGroupV.get(j).intValue();				
				indexesOfGroupsForRecordVV.get(irecord).add(i);
			}			
		}
		

		LinkedHashMap<Integer,RecordGroup> mapOfLevelGroupsWithGamma=new LinkedHashMap<Integer,RecordGroup>();	
				
		for(int irecord=0;irecord<nRecords;irecord++) {		
			Level lev=(Level)recordsV.get(irecord);		
			
			mapOfLevelGroupsWithGamma.clear();
			
			/*
			//debug
			if(lev.ES().equals("5730") && lev.JPiS().equals("3/2")) {
            //if(Math.abs(lev.EF()-11668)<1) {
            	System.out.println("EnsdfGroup 2470 NUCID"+this.NUCID+" E="+lev.EF()+"  mult="+indexesOfGroupsForRecordVV.get(irecord).size());
            	for(int j=0;j<indexesOfGroupsForRecordVV.get(irecord).size();j++) {
            		int igroup=indexesOfGroupsForRecordVV.get(irecord).get(j);
            		for(int k=0;k<refRecordGroupsV.get(igroup).nRecords();k++)
            			System.out.println("group"+igroup+"  E="+refRecordGroupsV.get(igroup).getRecord(k).ES());
	            }
            }
            */
			
			Vector<Integer> indexesOfAssignedGroups=indexesOfGroupsForRecordVV.get(irecord);
			if(indexesOfAssignedGroups.size()==0)
				continue;
			
			//map of group number and the matching strength of the current record with the corresponding reference record group
			HashMap<Integer,MatchingStrength> igroupMatchingStrengthMap=new HashMap<Integer,MatchingStrength>();
			int minMatchingOrder=100;
			
			for(int j=0;j<indexesOfAssignedGroups.size();j++) {
				int igroup=indexesOfAssignedGroups.get(j);
				RecordGroup levGroup=refRecordGroupsV.get(igroup);
				if(levGroup.hasLevelGamma())
					mapOfLevelGroupsWithGamma.put(igroup,levGroup);		
				
				try {
					MatchingStrength ms=matchingStrengthMapOfRecordsInGroupV.get(igroup).get(irecord);
					igroupMatchingStrengthMap.put(igroup, ms);
					if(ms.order<minMatchingOrder)
						minMatchingOrder=ms.order;
				}catch(Exception e) {
					
				}
				
				
				/*
				//debug
				if(lev.ES().equals("5730") && lev.JPiS().equals("3/2")) {
					System.out.println("EnsdfGroup 2491 NUCID"+this.NUCID+" E="+lev.EF()+"  mapOfLevelGroupsWithGamma size="+mapOfLevelGroupsWithGamma.size());
            		for(int k=0;k<levGroup.nRecords();k++)
            			System.out.println("     ### 2492: group"+igroup+"  E="+levGroup.getRecord(k).ES()+"  dsid="+levGroup.dsidsV().get(k));
				}
				*/
			}
		
			boolean found=false;
			Vector<Integer> igroupFoundV=new Vector<Integer>();
			float minAvgDist=1E6f;
			int iBestGroup=-1,iAvgDistBest=-1;
			float avgDistBest=-1;
           
			
			//level has gammas and group has gammas, keep only the groups with consistent gammas
			if(mapOfLevelGroupsWithGamma.size()>0 && lev.nGammas()>0) {
		
				//keep level group containing any level with gamma
				int nMatchedGamMax=0;
				
				LinkedHashMap<Integer,Float>  groupAvgDistMap=new LinkedHashMap<Integer,Float>(); 
				
				for(int igroup:mapOfLevelGroupsWithGamma.keySet()) {
					RecordGroup levGroup=mapOfLevelGroupsWithGamma.get(igroup);
					
					int ng=0;
					float avgDist=-1;
					boolean isGood=false;
					
					ng=levGroup.findMaxNumberOfConsistentGammas(lev,deltaE,true);
				    if(ng>0) {
				    	isGood=true;
				    	avgDist=levGroup.findAverageDistanceToGroup(lev);
				    							    	
				    	if(ng>nMatchedGamMax) { 
				    		nMatchedGamMax=ng;
				    		iBestGroup=igroup;	
				    	}else if(ng<=nMatchedGamMax/2)
				    		isGood=false;
				    }
				    
				    //if(lev.ES().contains("1335.6")) System.out.println("EnsdfGroup 1069: igroup="+igroup+" isGood="+isGood+" nMatchedGam="+nMatchedGam+" ng of lev="+ng);
								
				    if(isGood) {
				    	groupAvgDistMap.put(igroup,avgDist);			    	
				    	found=true;
			    		if(avgDist<minAvgDist) {
			    			minAvgDist=avgDist;
					    	avgDistBest=avgDist;
					    	iAvgDistBest=igroup;
			    		}
			    	
				    }

				}
				
				if(iBestGroup>=0) {
					igroupFoundV.add(iBestGroup);
					groupAvgDistMap.remove(iBestGroup);
					if(iAvgDistBest>=0 && iAvgDistBest!=iBestGroup) {
						igroupFoundV.add(iAvgDistBest);
						groupAvgDistMap.remove(iAvgDistBest);
					}
					for(int ig:groupAvgDistMap.keySet()) {
						float avgDist=groupAvgDistMap.get(ig);
						if(EnsdfUtil.isComparableEnergyValue(avgDist,avgDistBest,2)) {
							igroupFoundV.add(ig);
						}
					}
				}
			}
			
			//filter for level with gammas and ref group with gammas
			if(found) {				
				//remove existing group assignments
				for(int j=0;j<indexesOfAssignedGroups.size();j++) {
					int igroup=indexesOfAssignedGroups.get(j);
					
					float avgDist=refRecordGroupsV.get(igroup).findAverageDistanceToGroup(lev);
					
					if(!igroupFoundV.contains(igroup) && avgDist>avgDistBest) {
						indexesOfRecordsInGroupVV.get(igroup).removeElement(irecord);
						indexesOfAssignedGroups.removeElement(igroup);
						
						matchingStrengthMapOfRecordsInGroupV.get(igroup).remove(irecord);
					}
				}
			}
			
			/*
			//debug
			if(lev.ES().equals("5730") && lev.JPiS().equals("3/2")) {
            //if(Math.abs(lev.EF()-11668)<1) {
            	System.out.println("EnsdfGroup 2578 NUCID"+this.NUCID+" E="+lev.EF()+"  mult="+indexesOfGroupsForRecordVV.get(irecord).size());
            	for(int j=0;j<indexesOfGroupsForRecordVV.get(irecord).size();j++) {
            		int igroup=indexesOfGroupsForRecordVV.get(irecord).get(j);
            		for(int k=0;k<refRecordGroupsV.get(igroup).nRecords();k++)
            			System.out.println("group"+igroup+"  E="+refRecordGroupsV.get(igroup).getRecord(k).ES());
	            }
            }
            */
			
			//continue to filter out record that is less likely for the ref group which it is assigned to
			if(minMatchingOrder<5) {
				//remove existing group assignments
				for(int j=0;j<indexesOfAssignedGroups.size();j++) {
					int igroup=indexesOfAssignedGroups.get(j);
					
					MatchingStrength ms=matchingStrengthMapOfRecordsInGroupV.get(igroup).get(irecord);
					if(ms!=null && ms.order>5) {
						indexesOfRecordsInGroupVV.get(igroup).removeElement(irecord);
						indexesOfAssignedGroups.removeElement(igroup);
						
						matchingStrengthMapOfRecordsInGroupV.get(igroup).remove(irecord);
					}

				}
			}
            
		}		
	}
	
    private void setTempIndexesVV(Vector<Vector<Integer>> indexesOfRecordsInGroupVV,Vector<Vector<Integer>> tempIndexesVV) {
    	tempIndexesVV.clear();
        for(int i=0;i<indexesOfRecordsInGroupVV.size();i++) {
        	Vector<Integer> tempIndexesV=new Vector<Integer>();
        	tempIndexesV.addAll(indexesOfRecordsInGroupVV.get(i));
        	tempIndexesVV.add(tempIndexesV);
        }
    }
    
    private void resetTempIndexesVV(Vector<Vector<Integer>> indexesOfRecordsInGroupVV,Vector<Vector<Integer>> tempIndexesVV, int iGroup) {
    	tempIndexesVV.get(iGroup).clear();
    	tempIndexesVV.get(iGroup).addAll(indexesOfRecordsInGroupVV.get(iGroup));
    }

    @SuppressWarnings("unchecked")
	private  <T extends Record> void removeGroupsWithNoFirmMembers(Vector<RecordGroup> recordGroupsV) {
    	try {
    		Vector<RecordGroup> tempGroupsV=new Vector<RecordGroup>();
    		
    		
    		//store indexes of groups that each record is assigned to 
    		HashMap<T,Vector<Integer>> recordGroupIndexesVMap=new HashMap<T,Vector<Integer>>();
    		 //rec,Vector<Integer> groupIndexesV
    		 
    		//first, find multiplicity of each record
    		for(int igroup=0;igroup<recordGroupsV.size();igroup++) {
    			RecordGroup recGroup=recordGroupsV.get(igroup);
    			for(int irecord=0;irecord<recGroup.nRecords();irecord++) {
    				T rec=(T) recGroup.recordsV().get(irecord);
    				if(rec==recGroup.getAdoptedRecord())
    					continue;
    				
    				if(!recordGroupIndexesVMap.containsKey(rec))
    					recordGroupIndexesVMap.put(rec, new Vector<Integer>());
    			
    				recordGroupIndexesVMap.get(rec).add(igroup);
    			}
    		}
    		
    		tempGroupsV.addAll(recordGroupsV);
    		for(int igroup=0;igroup<recordGroupsV.size();igroup++) {
    			RecordGroup recGroup=recordGroupsV.get(igroup);
    			
    			boolean hasFirmMember=false;
    			for(String xtag:recGroup.xtagsV()) {
    				if(!xtag.contains("*")) {
    					hasFirmMember=true;
    					break;
    				}
    			}
    			
    			if(!hasFirmMember && !recGroup.hasAdoptedRecord()) {
    				tempGroupsV.remove(recGroup);
    				
    				//remove * in xtags of the record assigned in other group if multiplicity=2
        			for(int irecord=0;irecord<recGroup.nRecords();irecord++) {
        				T rec=(T) recGroup.recordsV().get(irecord);
        				Vector<Integer> assignedGroupIndexesV=recordGroupIndexesVMap.get(rec);
        				if(assignedGroupIndexesV.size()==2) {
        					assignedGroupIndexesV.removeElement(igroup);
        					int igroup1=assignedGroupIndexesV.get(0).intValue();
        					RecordGroup otherGroup=recordGroupsV.get(igroup1);
        					
        					int recIndex=otherGroup.recordsV().indexOf(rec);
        					if(recIndex>=0) {
        						String xtag=otherGroup.getXTag(recIndex);
        						otherGroup.xtagsV().remove(recIndex);
        						
        						xtag=xtag.replace("(*)","").replace("*", "");
        						otherGroup.xtagsV().add(recIndex, xtag);//the same group is also in tempGroupsV and it will change as well 
        					}
        				}
        			}        			
    			}
    		}
    		
    		
    		recordGroupsV.clear();
    		recordGroupsV.addAll(tempGroupsV);
    	}catch(Exception e) {
    		
    	}
    }
    
    
    private <T extends Record> Vector<Vector<RecordGroup>> splitGroup(Vector<RecordGroup> groupsV,
            Vector<T> recordsV, Vector<RecordGroup> refRecordGroupsV, Vector<Vector<Integer>> indexesOfRecordsInGroupVV){
        
        Vector<Vector<RecordGroup>> out=new Vector<Vector<RecordGroup>>();

        Vector<Integer> indexesOfRecordsInGroupV=new Vector<Integer>();
        
        Vector<T> allRecordsV=new Vector<T>();//all records assigned to any group in this subGroup
        //int[] nIndexes=new int[groupsV.size()];
        
        //Vector<Integer> posV=new Vector<Integer>();
        
        for(int i=0;i<groupsV.size();i++) {
            RecordGroup group=groupsV.get(i);
            int igroup=refRecordGroupsV.indexOf(group);
            indexesOfRecordsInGroupV=indexesOfRecordsInGroupVV.get(igroup);
            for(int k=0;k<indexesOfRecordsInGroupV.size();k++) {
                int irecord=indexesOfRecordsInGroupV.get(k).intValue();
                T r=recordsV.get(irecord);
                if(!allRecordsV.contains(r))
                    allRecordsV.add(r);
            }
            
            //nIndexes[i]=indexesOfRecordsInGroupV.size();
            //if(nIndexes[i]==1)
            //  posV.add(i);
        }

        int nr=allRecordsV.size();       
        int ng=groupsV.size();
        //if(ng<=10 || (ng<=30 && nr<=10)) {
        if(ng<=5 || (ng<=10 && nr<20) || (ng<=30 && nr<=10)) {
            out.add(groupsV);
            return out;
        }
  
        //now split subGroupsV
        int nm=ng/2;
        Vector<RecordGroup> tempV1=new Vector<RecordGroup>();
        Vector<RecordGroup> tempV2=new Vector<RecordGroup>();
        
        tempV2.addAll(groupsV);
        
        //System.out.println("1 subGroup. size="+ng+" record size="+nr);
        
        int k=0;
        while(k<nm) {
            tempV1.add(tempV2.get(0));
            tempV2.remove(0);
            k++;
        }
        
        
        Vector<Vector<RecordGroup>> out1=splitGroup(tempV1,recordsV,refRecordGroupsV,indexesOfRecordsInGroupVV);
        Vector<Vector<RecordGroup>> out2=splitGroup(tempV2,recordsV,refRecordGroupsV,indexesOfRecordsInGroupVV);
        
        //System.out.println("2 done");
        
        out.addAll(out1);
        out.addAll(out2);
        
        return out;
    }
	//A NEW APPROACH BELOW TO FIND ibest: 
	//
	//findBestRecForGroups(recordsV,groupsV,indexesOfRecordsInGroupsVV), return a vector of ibest for groupsV
	//            with each element being the index of the record best matching the corresponding group in groupsV
	//            ibest<0 means no best match
	//
	//STEP1: 
	//If thisGroup has >1 records initially assigned from recordsV, AND then find 
	//all following and adjacent groups that also have >1 records and have OVERLAPPING
	//record with its preceding group (until it reaches a group that has 0 or 1 record
	//or has no overlapping record with the previous group). Process found groups next 
	//STEP2:
	//For each group found in step 1, calculate average diff between the group and a
	//record to be assigned to this one, with the record not having been selected by other group and
	//having to be after previously selected records by preceding groups among the found
	//groups in step 1. Sum up all difference values of all groups above and find the minimum 
	//sum, for which the record assigned to each group is the best match.
	//
	//
	// let # of group=ng, # of records=nr for groups and records involved in step 2
	//when processing those groups and records
	//if nr>ng, each group has to be assigned a record, but there are records that are not assigned to any group
	//if nr<ng, each record has to be assigned to a group, but there are groups that don't have an assigned record
	
	//NOT COMPLETED YET
	@SuppressWarnings({ "unchecked", "unused" })
	private  <T extends Record> Vector<Integer> findBestRecordsForGroups_BAD(Vector<T> recordsV, Vector<RecordGroup> refRecordGroupsV, 
			Vector<Vector<Integer>> indexesOfRecordsInGroupVV,boolean isGamma,float deltaE){
		
		Vector<Integer> bestIndexes=new Vector<Integer>();// corresponding to refRecordGroupsV

		//step1: divide groupsV to subGroupsV, as described in STEP1 above
		Vector<Vector<RecordGroup>> subGroupsVV=new Vector<Vector<RecordGroup>>();
		Vector<RecordGroup> subGroupsV=new Vector<RecordGroup>();
		
        Vector<Integer> indexesOfRecordsInNextGroupV=new Vector<Integer>();
        Vector<Integer> indexesOfRecordsInPrevGroupV=new Vector<Integer>();
        Vector<Integer> indexesOfRecordsInGroupV=new Vector<Integer>();
        
        //first clean of indexesOfRecordsInGroupV
        for(int i=0;i<refRecordGroupsV.size();i++) {
            //indexes of the records (from the same dataset) to be inserted to the group at igroup=i
            indexesOfRecordsInGroupV=indexesOfRecordsInGroupVV.get(i);
            
            indexesOfRecordsInNextGroupV=new Vector<Integer>();
            indexesOfRecordsInPrevGroupV=new Vector<Integer>();
            
            int nIndexes=indexesOfRecordsInGroupV.size();   

            RecordGroup thisGroup=refRecordGroupsV.get(i);          
            RecordGroup prevGroup=null,nextGroup=null;
            Vector<Integer> tempIndexes=new Vector<Integer>();
            
            tempIndexes.addAll(indexesOfRecordsInGroupV);

            int minPrevIndex=-1, maxNextIndex=10000;
            int j=-1;

            j=i;
            while(j<refRecordGroupsV.size()-1 && indexesOfRecordsInNextGroupV.size()==0) {
                nextGroup=refRecordGroupsV.get(j+1);
                indexesOfRecordsInNextGroupV=indexesOfRecordsInGroupVV.get(j+1);
                for(int k=indexesOfRecordsInNextGroupV.size()-1;k>=0;k--) {
                	int index=indexesOfRecordsInNextGroupV.get(k).intValue();
                	if(!indexesOfRecordsInGroupV.contains(index)) {
                        maxNextIndex=index;
                        break;
                	}
 
                }

                j++;                        
            }
            
            j=i;
            while(j>0 && indexesOfRecordsInPrevGroupV.size()==0) {
                prevGroup=refRecordGroupsV.get(j-1);
                indexesOfRecordsInPrevGroupV=indexesOfRecordsInGroupVV.get(j-1);//size=0 or 1
                for(int k=0;k<indexesOfRecordsInPrevGroupV.size();k++) {
                	int index=indexesOfRecordsInPrevGroupV.get(k).intValue();
                	if(!indexesOfRecordsInGroupV.contains(index)) {
                        minPrevIndex=index;
                        break;
                	}
 
                }
                
                j--;
            }               

            Vector<Integer> tempIndexes0=new Vector<Integer>();
            
            /*
            tempIndexes0.addAll(tempIndexes);
            for(int k=tempIndexes0.size()-1;k>=0;k--) {
                Integer ind=tempIndexes0.get(k);
                if(ind.intValue()>maxNextIndex)
                    tempIndexes.removeElement(ind);
            }
            //NOTE indexes of next group at this point have been processed yet and they might or might not be really assigned to next group
            //     so they can't be used as reference for removing indexes assigned to this group. But at this point the indexes of previous
            //     group should have been processed
            */
            
            tempIndexes0.clear();
            tempIndexes0.addAll(tempIndexes);
            for(int k=0;k<tempIndexes0.size();k++) {
                Integer ind=tempIndexes0.get(k);
                if(ind.intValue()<minPrevIndex-1)// keep one extra before minPrevIndex in case two close levels cross match those reference levels
                    tempIndexes.removeElement(ind);
            }  

	        
            /*
            //debug
            if(Math.abs(thisGroup.getMeanEnergy()-8438)<50) {
                
                System.out.print("EnsdfGroup 1910 (before):                         Record in group:");
                for(int k=0;k<thisGroup.nRecords();k++) System.out.print("      "+thisGroup.recordsV().get(k).ES());
                System.out.println("");
                              
                System.out.print("                                                  new record assigned: ");
                for(int k=0;k<indexesOfRecordsInGroupV.size();k++) System.out.print("      "+recordsV.get(indexesOfRecordsInGroupV.get(k)).ES());
                System.out.println("");
                
            }
            */
            
            indexesOfRecordsInGroupV.clear();
            indexesOfRecordsInGroupV.addAll(tempIndexes);
            
            /*
            //debug
            if(Math.abs(thisGroup.getMeanEnergy()-8438)<50) {
                
                System.out.print("EnsdfGroup 1936 (after):                          Record in group:");
                for(int k=0;k<thisGroup.nRecords();k++) System.out.print("      "+thisGroup.recordsV().get(k).ES());
                System.out.println("");
                              
                System.out.print("                                                  new record assigned: ");
                for(int k=0;k<indexesOfRecordsInGroupV.size();k++) System.out.print("      "+recordsV.get(indexesOfRecordsInGroupV.get(k)).ES());
                System.out.println("");
                
                if(minPrevIndex>=0 && maxNextIndex<recordsV.size())
                System.out.println(" minPrevIndex="+minPrevIndex+"  rec="+recordsV.get(minPrevIndex).ES()+"  maxNextIndex="+maxNextIndex+" rec="+recordsV.get(maxNextIndex).ES());
            }
            */
        }
        
        //System.out.println("*****************************************");
        
        boolean endSub=false;
		for(int i=0;i<refRecordGroupsV.size();i++) {
			
			RecordGroup group=refRecordGroupsV.get(i);
			
			indexesOfRecordsInGroupV=indexesOfRecordsInGroupVV.get(i);
			
			/*
			//debug
            if(Math.abs(group.getMeanEnergy()-10144)<5) {
                System.out.print("EnsdfGroup 1971:                                  Record in group# "+i);
                for(int k=0;k<group.nRecords();k++) System.out.print("      "+group.recordsV().get(k).ES());
                System.out.println("");
                              
                System.out.print("                                                  new record assigned: ");
                for(int k=0;k<indexesOfRecordsInGroupV.size();k++) System.out.print("      "+recordsV.get(indexesOfRecordsInGroupV.get(k)).ES());
                System.out.println("");
                
                System.out.println(" subgroup size="+subGroupsV.size());
                for(int k=0;k<subGroupsV.size();k++)
                	System.out.println(" in subGroup: ig="+refRecordGroupsV.indexOf(subGroupsV.get(k))+" mean e=");
            }
            */
			
			//initialize bestIndexes array and find ibest for groups that have no overlapping records with any other.
			if(indexesOfRecordsInGroupV.size()<1)
				bestIndexes.add(-1);
			else if(indexesOfRecordsInGroupV.size()==1) {
				bestIndexes.add(indexesOfRecordsInGroupV.get(0));
			}else {
				Vector<T> tempRecordsV=new Vector<T>();				
				for(int k=0;k<indexesOfRecordsInGroupV.size();k++) {
					int irecord=indexesOfRecordsInGroupV.get(k).intValue();
					tempRecordsV.add(recordsV.get(irecord));
				}
				int ibest=-1;
				T r=null;
				if(isGamma) {
					r=group.findBestMatchRecordByEenrgy(tempRecordsV);						
				}else {
					r=(T) group.findBestMatchLevel((Vector<Level>) tempRecordsV, deltaE,true);
				}
				ibest=recordsV.indexOf(r);
				
				bestIndexes.add(ibest);
				
				//debug
				//String es="";
				//if(ibest>=0) es=recordsV.get(ibest).ES();
				//System.out.println(" igroup="+i+" size="+bestIndexes.size()+" ibestRec="+ibest+" es="+es+"  tempRecordsV[0]="
				//   +tempRecordsV.get(0).ES()+" tempRecordsV size="+tempRecordsV.size()+"  endSub="+endSub+" ref Group size="+refRecordGroupsV.size());
			}
			
			if(endSub || indexesOfRecordsInGroupV.size()<1 || i==refRecordGroupsV.size()-1) {
			//if(endSub || indexesOfRecordsInGroupV.size()<1 || i==refRecordGroupsV.size()-1 || subGroupsV.size()>4) {
				
				boolean hasThisGroup=false;
				if(subGroupsV.size()>0) {
					subGroupsVV.add(subGroupsV);
					if(subGroupsV.contains(group))
						hasThisGroup=true;
				}
				
				subGroupsV=new Vector<RecordGroup>();
				endSub=false;	
				
				if(hasThisGroup)
					continue;
			}
			
			RecordGroup thisGroup=refRecordGroupsV.get(i);			
	        RecordGroup nextGroup=null;
	        
			if(i<refRecordGroupsV.size()-1 && indexesOfRecordsInGroupVV.get(i+1).size()>0) {
                nextGroup=refRecordGroupsV.get(i+1);
                indexesOfRecordsInNextGroupV=indexesOfRecordsInGroupVV.get(i+1);
                

                for(int k=indexesOfRecordsInGroupV.size()-1;k>=0;k--) {
                    Integer ind=indexesOfRecordsInGroupV.get(k);

                    if(indexesOfRecordsInNextGroupV.contains(ind)) {
                    	if(!subGroupsV.contains(thisGroup))
                    		subGroupsV.add(thisGroup);
                    	
                        
                        //debug
                        /*
                            T rec=(T)recordsV.get(ind.intValue());                  
                            System.out.println(" i="+i+" this group index size="+indexesOfRecordsInGroupV.size()+" rec="+rec.ES()+" ind="+ind.intValue()+" next group index size="
                              +indexesOfRecordsInNextGroupV.size()+" subGroupsV size="+subGroupsV.size()+" ");
                            
                            for(int m=0;m<indexesOfRecordsInGroupV.size();m++) {
                                int ir=indexesOfRecordsInGroupV.get(m).intValue();
                                
                                T r=(T) recordsV.get(ir);
                                System.out.print(" #this group: index="+ir+" rec="+r.ES());
                            }
                            for(int m=0;m<indexesOfRecordsInNextGroupV.size();m++) {
                                int ir=indexesOfRecordsInNextGroupV.get(m).intValue();
                                
                                T r=(T) recordsV.get(ir);
                                System.out.print(" ##next group: index="+ir+" rec="+r.ES());
                            }
                            System.out.println("");
                            for(int m=0;m<thisGroup.nRecords();m++) {
                                T r=(T) thisGroup.recordsV().get(m);
                                System.out.println(" in this group: "+r.ES());
                            }
                            
                            for(int m=0;m<nextGroup.nRecords();m++) {
                                T r=(T) nextGroup.recordsV().get(m);
                                System.out.println(" in next group: "+r.ES());
                            }
                       */     
                        
                        
                        subGroupsV.add(nextGroup);
                        break;
                    }
                    
                    //if reaching here, it means the rightmost record from this group is not in next group meaning no overlap, then start a new subGroupsV next time
                    endSub=true;
                }                    
            }		
		}//end refRecordGroup loop
		
		//System.out.println("done");
		
		
		//split large subGroups (n>10) if possible, since step2 would take a very long time for large groups
		//It works!!! Now the processing time is reduced to about 3 seconds from 2 minutes for grouping all 31P datasets with the same results.
		Vector<Vector<RecordGroup>> tempVV=new Vector<Vector<RecordGroup>>();
		for(int igV=0;igV<subGroupsVV.size();igV++) {
			subGroupsV=subGroupsVV.get(igV);
			tempVV.addAll(splitGroup(subGroupsV,recordsV,refRecordGroupsV,indexesOfRecordsInGroupVV));
		}
		subGroupsVV.clear();
		subGroupsVV.addAll(tempVV);
		
		  
        Vector<Vector<Integer>> tempIndexesVV=new Vector<Vector<Integer>>();
        setTempIndexesVV(indexesOfRecordsInGroupVV,tempIndexesVV);
        
		//step2: process each subGroupsV to find ibest and update indexesOfRecordsInGroupV, as STEP2 above
		for(int igV=0;igV<subGroupsVV.size();igV++) {
			subGroupsV=subGroupsVV.get(igV);
			
			//Vector<Integer> selectedIndexes=new Vector<Integer>();
			int[] a=new int[subGroupsV.size()];//store selectedIndexes
			int[] a0=new int[subGroupsV.size()];//store selectedIndexes
			Vector<Integer> igroupV=new Vector<Integer>();
			Vector<T> allRecordsV=new Vector<T>();//all records assigned to any group in this subGroup

	         
            //store average record-to-group distances of all records to be assigned to a group      
            HashMap<RecordGroup,HashMap<T,Float>> groupRecordDistMap=new HashMap<RecordGroup,HashMap<T,Float>>();
            
			for(int i=0;i<subGroupsV.size();i++) {
				RecordGroup group=subGroupsV.get(i);
				int igroup=refRecordGroupsV.indexOf(group);
				igroupV.add(igroup);
				
				indexesOfRecordsInGroupV=indexesOfRecordsInGroupVV.get(igroup);
				int nr=indexesOfRecordsInGroupV.size();
				if(nr>0) {
					a0[i]=indexesOfRecordsInGroupV.get(0).intValue();
				}else
					a0[i]=-1;

				/*
    			//debug
                if(Math.abs(group.getMeanEnergy()-10144)<5) {
                    System.out.print("EnsdfGroup 2206:                                  Record in group# "+i);
                    for(int k=0;k<group.nRecords();k++) System.out.print("      "+group.recordsV().get(k).ES());
                    System.out.println("");
                                  
                    System.out.print("                                                  new record assigned: ");
                    for(int k=0;k<indexesOfRecordsInGroupV.size();k++) System.out.print("      "+recordsV.get(indexesOfRecordsInGroupV.get(k)).ES());
                    System.out.println("");
                    
                    System.out.println(" subgroup size="+subGroupsV.size());
                    for(int k=0;k<subGroupsV.size();k++)
                    	System.out.println(" in subGroup: ig="+refRecordGroupsV.indexOf(subGroupsV.get(k))+" mean e=");
                }
                */
                
				/*
				//debug
				System.out.println(" EnsdfGroup 2223: subGroup size="+subGroupsV.size()+"  igv="+igV+" i="+i+" igroup="+igroup+" a0[i]="+a0[i]+" group mean="+
				group.getMeanEnergy()+" indexesOfRecordsInGroupV.size="+indexesOfRecordsInGroupV.size());
				for(int j=0;j<recordsV.size();j++) {
					T r=(T)recordsV.get(j);
					System.out.println(" in recordsV j="+j+" r="+r.ES());
				}
				*/
				
				for(int k=0;k<indexesOfRecordsInGroupV.size();k++) {
					int irecord=indexesOfRecordsInGroupV.get(k).intValue();
					T r=recordsV.get(irecord);
					if(!allRecordsV.contains(r))
						allRecordsV.add(r);
					
					float diff=group.findAverageDistanceToGroup(r);
					if(!groupRecordDistMap.containsKey(group)) {
					    HashMap<T,Float> recordDistMap=new HashMap<T,Float>();
					    groupRecordDistMap.put(group, recordDistMap);
					}
					groupRecordDistMap.get(group).put(r,diff);
				}
			}

			
			for(int i=0;i<a0.length;i++) {
				//if(a0[i]<0)
					a[i]=a0[i];
				//else
				//	a[i]=a0[i]-1;		
			}
			
			float diff=-1,minSumDiff=10000;
			float sumDiff=0;
			int maxNGood=0;
			
			int[] indexesOfMinDiff=new int[0];
			
			
			int nGroups=subGroupsV.size();
			int nRecords=allRecordsV.size();
			
			//System.out.println("\n@@@subgroups: "+igV);
			
			int offset=0;
			int[] indexOffset=new int[a.length];
			Arrays.fill(indexOffset, 0);

			HashMap<String,Integer> processedCombinationMap=new HashMap<String,Integer>();		
	    	
			//long startTime,endTime,tempStart,tempEnd;
	    	//float timeElapsed;//in second
	    	//startTime=System.currentTimeMillis();
	    	//tempStart=startTime;
	        
			boolean done=false; 
			
			int count=0,goodCount=0,nTries=0;
			while(true) {
				//System.out.println("hello1"+" subGroup size="+subGroupsV.size()+" nRecords="+nRecords);
				
				//get a good combination of index of a selected record out of each sub group
				int nGood=0;
				int prevIndex=-1;//previous positive index
				
				for(int i=0;i<subGroupsV.size();i++) {
					int igroup=igroupV.get(i).intValue();
					indexesOfRecordsInGroupV=tempIndexesVV.get(igroup);
					int nIndexes=indexesOfRecordsInGroupV.size();
					
					//if(Math.abs(allRecordsV.get(0).EF()-12980)<10) {
					//if(Math.abs(allRecordsV.get(0).EF()-10099)<50) {
					//    System.out.println("1 size="+subGroupsV.size()+" nRecords="+nRecords+" i="+i+" a[i]="+a[i]+" prevIndex="+prevIndex+" nIndexes="+nIndexes+" indexOffset="+indexOffset[i]);	                        
					//}
	
	                
					if(indexOffset[i]<nIndexes) {
						a[i]=indexesOfRecordsInGroupV.get(indexOffset[i]).intValue();
						
						
						//if(a[i]<prevIndex)//allow a[i]=prevIndex, one record assigned to multiple groups
						if(a[i]<=prevIndex){//do not allow a[i]=prevIndex, one record assigned to multiple groups
							
							boolean found=false;
							for(int j=indexOffset[i]+1;j<nIndexes;j++) {
								int tempIndex=indexesOfRecordsInGroupV.get(j).intValue();
								if(tempIndex>prevIndex) {
									a[i]=tempIndex;
									found=true;
									break;
								}
							}
							//if(a[i]==prevIndex || a[i]<prevIndex-1) //keep one extra before minPrevIndex in case two close levels cross match those reference levels. NOT WORKING AS EXPECTED!!!
							if(!found)														
								a[i]=-1;											
						}
	                    

					}else if (indexOffset[i]==nIndexes) {
						//find the prevIndex before current one if the current one is a[i]
						//DO NOT increment indexOffset for next group, since no record in next group has been selected yet
						//and just go to proceed to and process next group
					    
						if(a[i]>=0) {
							prevIndex=-1;
							for(int j=i-1;j>=0;j--) {
								if(a[j]>=0) {
									prevIndex=a[j];
									break;
								}
							}
						}
					    
						a[i]=-1;
					}else{
		                //now at this step, the next group should have been dealt with and no combination is available for groups
						//before next group, so now it is safe to increment the offset of next group and start over for
						//all groups before next group
                    	//a[i]=-1;
                    	
						if(i<nGroups-1) {
                    		indexOffset[i+1]++;
                            
                    		/*
                    		for(int j=0;j<=i;j++) {
                                indexOffset[j]=0;
                            }
                            
                            i=-1;
                            prevIndex=-1;
                            nGood=0;
                            continue;
                            
                    		*/
                    		int iNextGroup=-1;
                    		int k=i+1;
                    		int tempOffset=0;
                    		if(i<nGroups-1) {
                    		    iNextGroup=igroupV.get(k).intValue();
                    		    tempOffset=indexOffset[k];
                    		}
                                        		
                    		while(iNextGroup>0) {
            					//debug
            					//if(Math.abs(allRecordsV.get(0).EF()-12980)<10) {
            					//    System.out.println(" ####    i="+i+" k="+k+"   indexOffset[k]="+indexOffset[k]+" tempOffset="+tempOffset+" "+indexesOfRecordsInGroupVV.get(iNextGroup).size());
            					//}
            					                 
                        		if(tempOffset<tempIndexesVV.get(iNextGroup).size()) {
                        			
                                    indexOffset[k]=tempOffset;
                                    
                                    Integer ind=tempIndexesVV.get(iNextGroup).get(tempOffset);
                                    
                                    //reset a and offset for all preceding groups
                                    for(int j=0;j<k;j++) {
                                        indexOffset[j]=0;
                                        a[j]=-1;
                                        
                                        
                                        int iGroup=igroupV.get(j).intValue();
                                        
                                    	resetTempIndexesVV(indexesOfRecordsInGroupVV,tempIndexesVV,iGroup);      
                                    	
                                        Vector<Integer> tempV=tempIndexesVV.get(iGroup);
                                        int n0=tempV.size();
                                        for(int m=n0-1;m>=0;m--) {
                                        	Integer d=tempV.get(m);
                                        	if(d.intValue()>=ind.intValue())
                                        		tempV.remove(d);
                                        	else
                                        		break;
                                        		
                                        }
                                        
                                    }
                                               
                                    //start over from beginning, except for that the index offset of subGroup k is increased by 1
                                    i=-1;
                                    prevIndex=-1;
                                    nGood=0;
                                    break;
                        		}else if(tempOffset==tempIndexesVV.get(iNextGroup).size()){
                        			//DO NOT increment indexOffset for next group, since no record in next group has been selected yet                       		    
                        			break;
                        		}else if(k<nGroups-1){
                        			a[k]=-1;
                        			
                        			k++;
                        			tempOffset=indexOffset[k]+1;
                        			iNextGroup=igroupV.get(k).intValue();
                        			continue;
                        		}else {
                        			break;
                        		}                       		                     		
                    		}                   		
                    		
                    		continue;
                    	}else {
                    		done=true;
                    		break;
                    	}                    		
                    }
					
					if(a[i]>=0) {
						prevIndex=a[i];
						nGood++;
					}
					
					
					//debug
					//if(Math.abs(allRecordsV.get(0).EF()-12980)<10) {
					//if(Math.abs(allRecordsV.get(0).EF()-10099)<50) {
					//    System.out.println("2 size="+subGroupsV.size()+" i="+i+" a[i]="+a[i]+" prevIndex="+prevIndex+" nIndexes="+nIndexes+" indexOffset="+indexOffset[i]+" nGood="+nGood);
					//}
					
				}

				/*
				//debug
				if(Math.abs(allRecordsV.get(0).EF()-10099)<50) {
                //if(Math.abs(allRecordsV.get(0).EF()-10145)<100) {
	                for(int i=0;i<a.length;i++) {
	                    System.out.print(" "+a[i]);
	                }  
	                System.out.println("");
	                
                       System.out.println("EnsdfGroup 2322 ### nGood="+nGood+" maxNGood="+maxNGood+" nGroups="+nGroups+" nRecords="+nRecords+" this sumDiff="+sumDiff+" minSumDiff="+minSumDiff+" done="+done);
                       for(int j=0;j<allRecordsV.size();j++) {
                    	   System.out.println("   all rec available to be assigned to groups in this subGroupsV:  "+allRecordsV.get(j).ES());
                       }
                       for(int j=0;j<a.length;j++) {
                            System.out.println("***  a0["+j+"]="+a0[j]+"   a["+j+"]="+a[j]+" offset="+indexOffset[j]);
                            if(a[j]>=0)
                                System.out.println("       selected rec to be assigned to group# "+igroupV.get(j)+" at rec index# "+a[j]+" "+recordsV.get(a[j]).ES());
           
                            RecordGroup g=refRecordGroupsV.get(igroupV.get(j));
                            for(int k=0;k<g.nRecords();k++) 
                            	System.out.println("           all existing rec in this group:  "+g.recordsV().get(k).ES());

                            Vector<Integer> indexesOfRecordsInThisGroupV=indexesOfRecordsInGroupVV.get(igroupV.get(j));
                            for(int k=0;k<indexesOfRecordsInThisGroupV.size();k++) {
                            	int index=indexesOfRecordsInThisGroupV.get(k).intValue();
                            	System.out.println("               all new rec assigned to this group:  "+recordsV.get(index).ES());
                            }
                        }
                }
                */
				
				//System.out.println("3 size="+subGroupsV.size()+" prevIndex="+prevIndex+" nGood="+nGood+" maxNGood="+maxNGood);
				
				if(done)
					break;
				
				indexOffset[0]++;
				
				/* old
				for(int i=0;i<subGroupsV.size();i++) {
					int igroup=igroupV.get(i).intValue();
					indexesOfRecordsInGroupV=indexesOfRecordsInGroupVV.get(igroup);
							
					//System.out.println(" hello2"+" group="+igroup+" index size="+indexesOfRecordsInGroupV.size());
					
					//only increment index for one group at one time by using an offset
					if(indexesOfRecordsInGroupV.size()>0 && a[i]>=-1) {
						a[i]++;
						while(!indexesOfRecordsInGroupV.contains(a[i]) || (i>0&&a[i]<=prevIndex) ){
							//System.out.println("  hello3 a[i]="+a[i]+" a0[i]="+a0[i]+" i="+i+" last="+indexesOfRecordsInGroupV.lastElement());							
							a[i]++;
							if(a[i]>indexesOfRecordsInGroupV.lastElement())
								break;
						}
						
						if(!indexesOfRecordsInGroupV.contains(a[i])) {
							if(a[i]>indexesOfRecordsInGroupV.lastElement()) {
								a[i]=-10;
			
								for(int k=i+1;k<a.length;k++) {								
									if(a0[k]<0)
										a[k]=a0[k];
									else
										a[k]=a0[k]-1;//reset
								}
							}
						}else {
							nGood++;
							prevIndex=a[i];
						}
					}
				}
                */
                
				/*
                //debug
                if(Math.abs(allRecordsV.get(0).EF()-9301)<100) {
                    System.out.println("EnsdfGroup 2246 for a good a[] ### nGood="+nGood+" maxNGood="+maxNGood+" nGroups="+nGroups+" nRecords="+nRecords+" prev minSumDiff="+minSumDiff);
                    for(int i=0;i<a.length;i++) {
                        System.out.print("***  a0["+i+"]="+a0[i]+"   a["+i+"]="+a[i]);
                        if(a[i]>=0)
                            System.out.print("   rec at a[i]="+recordsV.get(a[i]).ES());
                        System.out.println("");
                        int igroup=igroupV.get(i).intValue();
                        RecordGroup g=refRecordGroupsV.get(igroup);
                        
                        System.out.print("                                                  Record in group:");
                        for(int k=0;k<g.nRecords();k++) System.out.print("      "+g.recordsV().get(k).ES());
                        System.out.println("");
                        
                        
                        indexesOfRecordsInGroupV=indexesOfRecordsInGroupVV.get(igroup);
                        System.out.print("                                                  new record assigned: ");
                        for(int k=0;k<indexesOfRecordsInGroupV.size();k++) System.out.print("      "+recordsV.get(indexesOfRecordsInGroupV.get(k)).ES());
                        System.out.println("");
                        
                    }
                }
                */
				
				/*
				//debug
				if(Math.abs(allRecordsV.get(0).EF()-13450)<40) {
					System.out.println("### nGood="+nGood+" maxNGood="+maxNGood+" nGroups="+nGroups+" nRecords="+nRecords+" prev minSumDiff="+minSumDiff);
					for(int i=0;i<a.length;i++) {
						System.out.print("***  a0["+i+"]="+a0[i]+"   a["+i+"]="+a[i]);
						if(a[i]>=0)
							System.out.print("   rec at a[i]="+recordsV.get(a[i]).ES());
						System.out.println("");
					}
					
					
					//if(nGroups==18 && nRecords==2) {
						for(int i=0;i<subGroupsV.size();i++) {
							int igroup=igroupV.get(i);
							RecordGroup group=refRecordGroupsV.get(igroup);
							indexesOfRecordsInGroupV=indexesOfRecordsInGroupVV.get(igroup);
							
							System.out.println("EnsdfGroup 2069: igroup="+igroup+" nr in group="+group.nRecords()+"  group average="+group.getMeanEnergy()
							+" subGroupsV.size()="+subGroupsV.size()+" assigned nrec="+allRecordsV.size()+" max R="+recordsV.lastElement().ES()
							+"  closest R="+group.findBestMatchRecordByEenrgy(recordsV).ES()+" indexesOfRecordsInGroupV.size="+indexesOfRecordsInGroupV.size());
							
							for(int m=0;m<indexesOfRecordsInGroupV.size();m++) {
								int ir=indexesOfRecordsInGroupV.get(m);
								T r=(T) recordsV.get(ir);
								System.out.println("  assigned records  irecord="+ir+"   r="+r.ES());
							}

							
							for(int m=0;m<group.nRecords();m++) {
								T r=(T) group.recordsV().get(m);
								System.out.println("    in group   m="+m+"   r="+r.ES());
							}
						}
					//}
					 
				}               
				*/
				
				if(nGood==0)
					break;
				       
                
				count++;
				
                String s="";
                for(int i=0;i<a.length;i++) {
                    s+=a[i]+"-";
                } 
                
                /*
                //debug
                if(nGroups==5) {
                    for(int i=0;i<a.length;i++) {
                        System.out.print(" "+a[i]);
                    }  
                    System.out.println("   %%11111");
                }
                */
                

                if(!s.isEmpty()) {
    				//debug
                	//if(Math.abs(allRecordsV.get(0).EF()-10099)<50)
    	            //    System.out.println(" ***** already processed="+processedCombinationMap.containsKey(s)+" nGood="+nGood+" nGroup="+nGroups+" nRecords="+nRecords);
                    
                    if(!processedCombinationMap.containsKey(s)) {
                        processedCombinationMap.put(s, 1);                    
                    }else {
                    	int n=processedCombinationMap.get(s).intValue();
                    	n=n+1;
                    	//if(n>nGroups)
                    	//	break;
                    	//else {
                    		processedCombinationMap.put(s,n);
                    		continue;
                    	//}
                        
                    }                   
                }else
                    break;

                nTries=processedCombinationMap.get(s).intValue();
                if(nTries>nGroups)
                	break;
                /*
                //debug
                //if(nGroups==5) {
                    for(int i=0;i<a.length;i++) {
                        System.out.print(" "+a[i]);
                    }  
                    System.out.println("   ####");
                //}
                */
                
                
				if(nGood>=maxNGood) {
					if(nGood>maxNGood)
						minSumDiff=10000;
					
					maxNGood=nGood;
				}
				
				
				if(nGroups<=nRecords) {
				    if(nGood<nGroups/3)
					//if(nGood<Math.max(maxNGood-3,nGroups/3))
						break;
					else 
						if(nGood<nGroups-1) 
						continue;
				}else {
				    if(nGood<nRecords/3)
					//if(nGood<Math.max(maxNGood-3,nRecords/3))
						break;
					else 
					if(nGood<nRecords-1) 
						continue;	
				}
			
                goodCount++;
                
				/*
				//debug
				//if(Math.abs(allRecordsV.get(0).EF()-10099)<50) {
	                for(int i=0;i<a.length;i++) {
	                    System.out.print(" "+a[i]);
	                }  
	                System.out.println("");

                //}
                */
				
                //debug
				//if(Math.abs(allRecordsV.get(0).EF()-13889)<200)
                //System.out.println("EnsdfGroup 2452 ### nGood="+nGood+" maxNGood="+maxNGood+" nGroups="+nGroups+" nRecords="+nRecords+" this sumDiff="+sumDiff+" minSumDiff="+minSumDiff);

				//////////////////////////////////////////////////////////////////////////////////////////////
				//STILL TO OPTIMIZE: 1) how to compare sumDiff for different nGood
				//                   2) too slow ?
				//                   3) Note that records assigned to two adjacent groups could have inverse order, 
				//                          see 24Mg, 8437.1 (lower) and 8437.7 from 20NE(A,G), they are assigned to 8439.3 (higher) and 8438.4 levels, respectively
				//                   4) should also consider isGammaConsistent
                
                
				//now calculate the sum differences of all group-to-record
				sumDiff=0;
				boolean goodSum=false;
				for(int i=0;i<a.length;i++) {
					try {
						RecordGroup group=subGroupsV.get(i);
						if(a[i]<0)
							continue;
						
						//if(!isGamma && !group.hasOverlapJPI((Level)recordsV.get(a[i])) ){
						//    goodSum=false;
						//    break;
						//}
						
						/*NOT WORKING AS EXPECTED
						if(!isGamma && group.hasLevelGamma()){
							Level lev=(Level)recordsV.get(a[i]);
							if(lev.nGammas()>0 && !group.isGammasConsistent(lev, deltaE,false)) {
							    goodSum=false;
							    break;
							}
						}
						*/
						
						//diff=group.findAverageDistanceToGroup(recordsV.get(a[i]));
						Float f=groupRecordDistMap.get(group).get(recordsV.get(a[i]));
						if(f!=null)
						    diff=f.floatValue();
						
						//System.out.println("  a[i]="+a[i]+"  diff="+diff+"  rec="+recordsV.get(a[i]).ES());
						
						if(diff>=0)
							sumDiff+=diff;
						
						goodSum=true;
					}catch(Exception e) {
						goodSum=false;
					}
				}
				//////////////////////////////////////////////////////////////////////////////////////////////////////////
				
				sumDiff=sumDiff/nGood;
				if(goodSum && sumDiff>=0 && sumDiff<minSumDiff) {
					indexesOfMinDiff=Arrays.copyOf(a, a.length);
					minSumDiff=sumDiff;
				}
                //debug
                //System.out.println("EnsdfGroup 2503 ### nGood="+nGood+" maxNGood="+maxNGood+" nGroups="+nGroups+" nRecords="+nRecords+" this sumDiff="+sumDiff+" minSumDiff="+minSumDiff);

				/*
				//debug
				if(Math.abs(allRecordsV.get(0).EF()-11668)<100) {
                //if(Math.abs(allRecordsV.get(0).EF()-10145)<100) {
                       System.out.println("EnsdfGroup 2590 ### nGood="+nGood+" maxNGood="+maxNGood+" nGroups="+nGroups+" nRecords="+nRecords+" this sumDiff="+sumDiff+" minSumDiff="+minSumDiff+" done="+done);
                       for(int j=0;j<allRecordsV.size();j++) {
                    	   System.out.println("   all rec available to be assigned to groups in this subGroupsV:  "+allRecordsV.get(j).ES());
                       }
                       for(int j=0;j<a.length;j++) {
                            System.out.println("***  a0["+j+"]="+a0[j]+"   a["+j+"]="+a[j]+" offset="+indexOffset[j]);
                            if(a[j]>=0)
                                System.out.println("       selected rec to be assigned to group# "+igroupV.get(j)+" at rec index# "+a[j]+" "+recordsV.get(a[j]).ES());
           
                            RecordGroup g=refRecordGroupsV.get(igroupV.get(j));
                            for(int k=0;k<g.nRecords();k++) 
                            	System.out.println("           all existing rec in this group:  "+g.recordsV().get(k).ES());

                            Vector<Integer> indexesOfRecordsInThisGroupV=indexesOfRecordsInGroupVV.get(igroupV.get(j));
                            for(int k=0;k<indexesOfRecordsInThisGroupV.size();k++) {
                            	int index=indexesOfRecordsInThisGroupV.get(k).intValue();
                            	System.out.println("               all new rec assigned to this group:  "+recordsV.get(index).ES());
                            }
                        }
                }
				*/
				
				/*
                //debug
                if(!isGamma && Math.abs(allRecordsV.get(0).EF()-9301)<100) {
                    System.out.println("\n#####EnsdfGroup 2436 after calculate sumDiff: ### goodsum="+goodSum+" nGood="+nGood+" maxNGood="+maxNGood+" nGroups="+nGroups+" nRecords="+nRecords+" thisSumDiff="+sumDiff+"  minSumDiff="+minSumDiff);
                    for(int i=0;i<a.length;i++) {
                        System.out.print("***  a0["+i+"]="+a0[i]+"   a["+i+"]="+a[i]);
                        if(a[i]>=0)
                            System.out.print("   rec at a[i]="+recordsV.get(a[i]).ES());
                        System.out.println("");
                        int igroup=igroupV.get(i).intValue();
                        RecordGroup g=refRecordGroupsV.get(igroup);
                        
                        System.out.print("                                                  Record in group:");
                        for(int k=0;k<g.nRecords();k++) System.out.print("      "+g.recordsV().get(k).ES());
                        System.out.println("");
                        
                        
                        indexesOfRecordsInGroupV=indexesOfRecordsInGroupVV.get(igroup);
                        System.out.print("                                                  new record assigned: ");
                        for(int k=0;k<indexesOfRecordsInGroupV.size();k++) System.out.print("      "+recordsV.get(indexesOfRecordsInGroupV.get(k)).ES());
                        System.out.println("");
                        
                    }
                }
                */
                
			}//end while loop for finding minSumDiff
			
			/*
    		System.out.println("***Total count="+count+" good count="+goodCount);
    		System.out.println("   sub groupV###="+igV+" nGroups="+nGroups+" nRecords="+nRecords+" nTries="+nTries);
    		if(nGroups==5 && nRecords==25) {
    			for(int i=0;i<subGroupsV.size();i++) {
    				RecordGroup g=subGroupsV.get(i);
    				int igroup=igroupV.get(i).intValue();
    				Vector<Integer> indexesV=indexesOfRecordsInGroupVV.get(igroup);
                    System.out.print("*** Record in group #:"+i);
                    for(int k=0;k<g.nRecords();k++) 
                    	System.out.print("      "+g.recordsV().get(k).ES());
                    System.out.println("");
                    
                    System.out.print("    new record assigned: ");
                    for(int k=0;k<indexesV.size();k++)
                    	System.out.print("   "+recordsV.get(indexesV.get(k)).ES());
                    System.out.println("");
    			}


    		}
    		*/
			
	        //endTime=System.currentTimeMillis();
	        //timeElapsed=(float)(endTime-startTime)/1000;
			//debug
			//System.out.println("EnsdfGroup 2363 ###  processedCombinations.size()="+processedCombinations.size()+" maxNGood="+maxNGood+""
			//		+ " nGroups="+nGroups+" nRecords="+nRecords+" Time elapsed: "+String.format("%.3f", timeElapsed)+" seconds");

			/*
			//debug
			System.out.println("indexesOfMinDiff.len="+indexesOfMinDiff.length);
			for(int i=0;i<indexesOfMinDiff.length;i++) {
				System.out.print("*** indexesOfMinDiff["+i+"]="+indexesOfMinDiff[i]);
				if(indexesOfMinDiff[i]>=0)
					System.out.print("   rec="+recordsV.get(indexesOfMinDiff[i]).ES());
				System.out.println("");
			}
			*/
			
			//System.out.println("### subgroups: "+igV);
			int iPrevBest=-1;
			for(int i=0;i<subGroupsV.size();i++) {
				int igroup=igroupV.get(i).intValue();
				RecordGroup group=subGroupsV.get(i);
				
				int ibest=-1;
				int ibest0=bestIndexes.get(igroup).intValue();
				if(indexesOfMinDiff.length>0) {
					ibest=indexesOfMinDiff[i];	
					if(!isGamma) {
						if(ibest<0) {//ibest=-1
							
							T r=(T)recordsV.get(ibest0);
							if(ibest0>=iPrevBest-1 && group.hasMatchedRecord(r, deltaE)) {
								ibest=ibest0;
							}
						}else if(ibest!=ibest0 && ibest0>=0) {
							int ip=-1,in=-1;//ibest of immediate previous and next group
							if(i>0)
								ip=indexesOfMinDiff[i-1];
							if(i<indexesOfMinDiff.length-1)
								in=indexesOfMinDiff[i+1];
							
							if(ip<0 && in<0) {
								Level lev0=(Level)recordsV.get(ibest0);
								Level lev=(Level)recordsV.get(ibest);
								if(group.whichLevelMatchBetter(lev0, lev, deltaE)>0)
									ibest=ibest0;//MUST BE VERY CAREFUL WITH THIS
							}
		
						}
					}

					bestIndexes.remove(igroup);
					bestIndexes.insertElementAt(ibest,igroup);
				}else{
					//Here, use initial ibest
					
					/*
					ibest=bestIndexes.get(igroup);
					int ibestPrev=-1;
					int ig=igroup;
					while(ibestPrev<0 && ig>0) {
						ig--;
						ibestPrev=bestIndexes.get(ig);
					}
					if(ibest>0 && ibestPrev>0 && ibestPrev>ibest) {
						
					}
					*/
				}
			
				if(ibest>=0)
					iPrevBest=ibest;
				
				/*
				//debug
				RecordGroup group=refRecordGroupsV.get(igroup);
				indexesOfRecordsInGroupV=indexesOfRecordsInGroupVV.get(igroup);
				//if(Math.abs(group.getReferenceRecord().EF()-12852)<40) {
				if(Math.abs(group.getMeanEnergy()-15117)<50) {
					
					System.out.println("EnsdfGroup 2413: igroup="+igroup+" nr in group="+group.nRecords()+" indexesOfMinDiff.length="+indexesOfMinDiff.length+" ###ibest="+bestIndexes.get(igroup)+" group average="+group.getMeanEnergy()
					+" subGroupsV.size()="+subGroupsV.size()+" nrec="+recordsV.size()+" max R="+recordsV.lastElement().ES()+"  closest R="+group.findBestMatchRecordByEenrgy(recordsV).ES());
					System.out.println(" bestIndexes size="+bestIndexes.size()+"  group size="+refRecordGroupsV.size());
					
					//for(int m=0;m<recordsV.size();m++) {
					//	T r=(T) recordsV.get(m);
					//	System.out.println(" recordsV     m="+m+"   r="+r.ES());
					//}

					for(int m=0;m<indexesOfRecordsInGroupV.size();m++) {
						int ir=indexesOfRecordsInGroupV.get(m);
						T r=(T) recordsV.get(ir);
						System.out.println("  assigned records  irecord="+ir+"   r="+r.ES());
					}
					
					for(int m=0;m<group.nRecords();m++) {
						T r=(T) group.recordsV().get(m);
						System.out.println("  this group   m="+m+"   r="+r.ES());
					}
					
					
					if(ibest>=0) {
						System.out.println("    ###best rec="+recordsV.get(ibest).ES());
						for(int m=0;m<refRecordGroupsV.get(igroup).nRecords();m++) {
							T r=(T) refRecordGroupsV.get(igroup).recordsV().get(m);
							System.out.println("  ibest>0   m="+m+"   r="+r.ES());
						}
					}
				}
                */
				

			}

		}//end loop for subGroupsVV

		//Now the best matching record for each subgroup in subGroupsV has been found and next
		//proceed to update indexesOfRecordsInGroupV of each group based on ibest
		for(int i=0;i<refRecordGroupsV.size();i++) {
			RecordGroup thisGroup=refRecordGroupsV.get(i);
			int igroup=i;
			int ibest=bestIndexes.get(igroup).intValue();
			
			//indexes of the records (from the same dataset) to be inserted to the group at igroup=i
			indexesOfRecordsInGroupV=indexesOfRecordsInGroupVV.get(igroup);

			/*
            //debug
            if(!isGamma && Math.abs(thisGroup.getMeanEnergy()-9301)<20) {
                
                System.out.print("EnsdfGroup 2567 (final1):                          Record in group:");
                for(int k=0;k<thisGroup.nRecords();k++) System.out.print("      "+thisGroup.recordsV().get(k).ES());
                System.out.println("");
                              
                System.out.print("      ibest="+ibest+"                              new record assigned: ");
                for(int k=0;k<indexesOfRecordsInGroupV.size();k++) System.out.print("      "+recordsV.get(indexesOfRecordsInGroupV.get(k)).ES()+"@"+indexesOfRecordsInGroupV.get(k));
                System.out.println("");
            }
            */
			
	        Vector<Integer> tempIndexes=new Vector<Integer>();
	        
	        tempIndexes.addAll(indexesOfRecordsInGroupV);
	        
	        indexesOfRecordsInNextGroupV=new Vector<Integer>();
	        indexesOfRecordsInPrevGroupV=new Vector<Integer>();
	        
	        int j=-1;

            j=i;
			while(j<subGroupsV.size()-1 && indexesOfRecordsInNextGroupV.size()==0) {
                indexesOfRecordsInNextGroupV=indexesOfRecordsInGroupVV.get(j+1);
                j++;	                    
            }
			
			j=i;
			while(j>0 && indexesOfRecordsInPrevGroupV.size()==0) {
                indexesOfRecordsInPrevGroupV=indexesOfRecordsInGroupVV.get(j-1);//size=0 or 1	                
                j--;
            }			    
	                        					    		
	        int thisIndex=-1;	            

            if(ibest>=0) {
            	
	            for(int k=0;k<indexesOfRecordsInGroupV.size();k++) {
	                Integer ind=indexesOfRecordsInGroupV.get(k);
	                thisIndex=ind.intValue();                    
	                if(thisIndex<ibest) {
	                    //Record thisRec=recordsV.get(thisIndex);
	                    //Record refRecordOfNextGroup=nextGroup.getReferenceRecord();
	                    //if(thisRec!=refRecordOfNextGroup)
	                        indexesOfRecordsInNextGroupV.removeElement(ind);

	                    //debug
	                    //if(!isGamma && Math.abs(recordsV.get(ind.intValue()).EF()-7010)<30 && dsid.contains("(P,T)")) 
	                    //    System.out.println(" ENsdfGroup 1609: ***ibest="+ibest+"  e="+recordsV.get(ibest).ES()+" this E="+recordsV.get(thisIndex).ES()+" next E="+recordsV.get(ibestNext).ES());
	                    
	                }else if(thisIndex>ibest) {	                    
	                    indexesOfRecordsInPrevGroupV.removeElement(ind);
	                }
	            } 
	            
                indexesOfRecordsInGroupV.clear();
                indexesOfRecordsInGroupV.add(ibest); 
                
                Record bestRec=recordsV.get(ibest);
                RecordGroup closestGroup=findClosestGroupForRecord(bestRec,refRecordGroupsV,true);
    			int indexOfClosestGroup=refRecordGroupsV.indexOf(closestGroup);
    			if(closestGroup!=null && indexOfClosestGroup!=igroup) {
            
    				boolean isGood=true;
    				boolean strictMatchToThis=thisGroup.hasComparableEnergyEntry(bestRec, deltaE,false)&&thisGroup.hasComparableEnergyEntry(bestRec,10,true);
    				boolean looseMatchToThis=thisGroup.hasComparableEnergyEntry(bestRec, deltaE,true);
    				boolean strictMatchToClosest=closestGroup.hasComparableEnergyEntry(bestRec, deltaE,false)&&closestGroup.hasComparableEnergyEntry(bestRec,10,true);;
    				boolean looseMatchToClosest=closestGroup.hasComparableEnergyEntry(bestRec, deltaE,true);
    				
	                //further check
	                if(!looseMatchToThis && looseMatchToClosest) {
	                    if(isGamma || closestGroup.hasOverlapJPI((Level)bestRec)) {
	                    	isGood=false;
	                    }
	                }  
	                
	                //for levels
	                if(isGood && indexesOfRecordsInGroupVV.get(indexOfClosestGroup).contains(ibest)) {
	                	if(!strictMatchToThis && strictMatchToClosest)
	                		isGood=false;
	                	else if(strictMatchToThis && !strictMatchToClosest) {//should not happen
	                		isGood=true;
	                	}else if(strictMatchToThis && strictMatchToClosest) {
	                		if(Math.abs(igroup-indexOfClosestGroup)>1) {
	                			int i1=igroup;
	                			int i2=indexOfClosestGroup;
	                			if(i1>i2) {
	                				i1=i2;
	                				i2=igroup;
	                			}
	                			for(int k=i1+1;k<i2;k++) {
	                				if(!indexesOfRecordsInGroupVV.get(k).contains(ibest)) {
	                					isGood=false;
	                					break;
	                				}
	                			}
	                		}
	                	}else {//!strictMatchToThis && !strictMatchToClosest
	                		
	                		//if the closest group does not match, this group does not match as well
	                		//if this group matches, the closest group must match too
	                		
	                		isGood=false;
	                	}
	              
	                }
	                
	                /*
                    //debug
                	if(!isGamma && Math.abs(thisGroup.getMeanEnergy()-9300)<50 ) { 
	                //if(!isGood) {
                        System.out.println(" EnsdfGroup 2718: ibest="+ibest+" E="+bestRec.ES()+" DE="+bestRec.DES()+" isGamma="+isGamma+" closest group index="+indexOfClosestGroup+" this group index="+i+
                        		" deltaE="+deltaE+" match within datalE: thisGroup="+thisGroup.hasComparableEnergyEntry(bestRec, deltaE,true)+
                        		" closestGroup="+closestGroup.hasComparableEnergyEntry(bestRec, deltaE,true)+" isGood best="+isGood);
                        System.out.println(" strictMatchToThis="+strictMatchToThis+" looseMatchToThis="+looseMatchToThis+" strictMatchToClosest="
                        		+strictMatchToClosest+" looseMatchToClosest="+looseMatchToClosest);
                        for(String s:thisGroup.dsidsV()) {
                        	Record r=thisGroup.getRecordByDSID(s);
                        	System.out.println("   in this Group rec E="+r.ES()+" DE="+r.DES()+" dsid="+s);
                        }
                       		
                	}
                	*/
                	
	                if(!isGood) 
	                	indexesOfRecordsInGroupV.clear();

    			}
            }else
            	indexesOfRecordsInGroupV.clear();
            
            /*
            //debug
            if(!isGamma && Math.abs(thisGroup.getMeanEnergy()-9301)<20) {
                
                System.out.print("EnsdfGroup 2567 (final2):                          Record in group:");
                for(int k=0;k<thisGroup.nRecords();k++) System.out.print("      "+thisGroup.recordsV().get(k).ES());
                System.out.println("");
                              
                System.out.print("                                                  new record assigned: ");
                for(int k=0;k<indexesOfRecordsInGroupV.size();k++) System.out.print("      "+recordsV.get(indexesOfRecordsInGroupV.get(k)).ES());
                System.out.println("");
            }
            */
		}

		//if(!isGamma) System.out.println("***************************");
		
		return bestIndexes;
	}
	
	@SuppressWarnings("unused")
	private  <T extends Record> Vector<Integer> findBestRecordsForGroups_old(Vector<T> recordsV, Vector<RecordGroup> refRecordGroupsV, 
			Vector<Vector<Integer>> indexesOfRecordsInGroupVV,boolean isGamma,float deltaE){
		
		Vector<Integer> bestIndexes=new Vector<Integer>();// corresponding to refRecordGroupsV
/*		
		float deltaE;
		boolean isGamma=false;
		if(refRecordGroupsV.get(0).recordsV().get(0) instanceof Level){
			deltaE=deltaEL;
			isGamma=false;
		}else{
			deltaE=deltaEG;
			isGamma=true;
		}
*/
		
		for(int i=0;i<refRecordGroupsV.size();i++) {
			
			//indexes of the records (from the same dataset) to be inserted to the group at igroup=i
			Vector<Integer> indexesOfRecordsInGroupV=indexesOfRecordsInGroupVV.get(i);
			
			int nIndexes=indexesOfRecordsInGroupV.size();	

			RecordGroup thisGroup=refRecordGroupsV.get(i);			
	        RecordGroup prevGroup=null,nextGroup=null;
	        Vector<Integer> indexesOfRecordsInNextGroupV=new Vector<Integer>();
	        Vector<Integer> indexesOfRecordsInPrevGroupV=new Vector<Integer>();
	        Vector<Integer> tempIndexes=new Vector<Integer>();
	        
	        tempIndexes.addAll(indexesOfRecordsInGroupV);
	        
	        boolean inPrevIndexes=false,inNextIndexes=false;

	        int minPrevIndex=-1, maxNextIndex=10000;
	        int j=-1;
			//if(nIndexes>1){
	            j=i;
				while(j<refRecordGroupsV.size()-1 && indexesOfRecordsInNextGroupV.size()==0) {
	                nextGroup=refRecordGroupsV.get(j+1);
	                indexesOfRecordsInNextGroupV=indexesOfRecordsInGroupVV.get(j+1);
	                if(indexesOfRecordsInNextGroupV.size()>0)
	                    maxNextIndex=indexesOfRecordsInNextGroupV.lastElement().intValue();
	                
	                for(int k=indexesOfRecordsInGroupV.size()-1;k>=0;k--) {
	                    Integer ind=indexesOfRecordsInGroupV.get(k);
	                    if(indexesOfRecordsInNextGroupV.contains(ind)) {
	                        tempIndexes.removeElement(ind);
	                        inNextIndexes=true;
	                    }else if(inNextIndexes) {
	                        
	                    }
	                }
	                j++;	                    
	            }
				
				j=i;
				while(j>0 && indexesOfRecordsInPrevGroupV.size()==0) {
	            	prevGroup=refRecordGroupsV.get(j-1);
	                indexesOfRecordsInPrevGroupV=indexesOfRecordsInGroupVV.get(j-1);//size=0 or 1
	                if(indexesOfRecordsInPrevGroupV.size()>0)
	                    minPrevIndex=indexesOfRecordsInPrevGroupV.get(0).intValue();
	                
	                for(int k=indexesOfRecordsInGroupV.size()-1;k>=0;k--) {
	                    Integer ind=indexesOfRecordsInGroupV.get(k);
	                    if(indexesOfRecordsInPrevGroupV.contains(ind)) {
	                        tempIndexes.removeElement(ind);
	                        inPrevIndexes=true;
	                    }
	                }
	                
	                j--;
	            }			    
			    
	            ///////////////////////////////////////
				//NOT NEEDED
	            //further check
	            Vector<Integer> tempIndexes0=new Vector<Integer>();
	            tempIndexes0.addAll(tempIndexes);
	            for(int k=tempIndexes0.size()-1;k>=0;k--) {
	                Integer ind=tempIndexes0.get(k);
	                if(ind.intValue()>maxNextIndex)
	                    tempIndexes.removeElement(ind);
	            }
	            
	            tempIndexes0.clear();
	            tempIndexes0.addAll(tempIndexes);
	            for(int k=0;k<tempIndexes0.size();k++) {
	                Integer ind=tempIndexes0.get(k);
	                if(ind.intValue()<minPrevIndex)
	                    tempIndexes.removeElement(ind);
	            }	

	            
	            nIndexes=tempIndexes.size();
	            
	            //////////////////////////////////////////
	            
			//}

						    		
	        int thisIndex=-1;
	        float minDiff=10000,diff=10000,diff1=10000,diff2=10000,minDiff1=10000,minDiff2=10000;
	        int ibest=-1,ibestNext=-1,ibest1=-1,ibest2=-1;
	        
	        tempIndexes.clear();

	        for(int k=0;k<indexesOfRecordsInGroupV.size();k++) {
	            Integer ind=indexesOfRecordsInGroupV.get(k);
	            thisIndex=ind.intValue();
	            if(ind.intValue()>=minPrevIndex-1) { // take one extra before minPrevIndex in case two close levels cross match those reference levels
	                tempIndexes.add(ind);
	            }
	        }
	        
	        /*
	        //debug
	        if(!isGamma && Math.abs(refRecordGroupsV.get(i).getMeanEnergy()-7482)<40) {
	            boolean skip=true;
	            for(Record r:recordsV)
	                if(r.ES().equals("7482")) {
	                    skip=false;
	                    break;
	                }
	            
	            if(!skip) {
	                RecordGroup group=refRecordGroupsV.get(i);
	                System.out.println("@@@@4609: in group# "+i);
	                for(int k=0;k<group.nRecords();k++)
	                    System.out.println("       record #i="+k+" ES="+group.recordsV().get(k).ES()+" dsid="+group.dsidsV().get(k));
	                for(int k=0;k<tempIndexes.size();k++) {
	                    int index=tempIndexes.get(k).intValue();
	                    System.out.println("    in this dataset record index="+index+" ES="+recordsV.get(index).ES());
	                }
	            }

	        }
	        */
	        
	        boolean done=false;
	        int ntries=0;
	        boolean foundBestThis=false,foundBestNext=false;
	        while(!done && ntries<10) {

	            for(int k=0;k<tempIndexes.size();k++) {
	                Integer ind=tempIndexes.get(k);
	                thisIndex=ind.intValue();
	                
	                diff1=thisGroup.findAverageDistanceToGroup(recordsV.get(thisIndex));
	                //diff1=Math.abs(thisGroup.getMeanEnergy()-recordsV.get(thisIndex).ERPF());
	                
	                if(thisGroup.getReferenceRecord()==recordsV.get(thisIndex)) {
	                    ibest1=thisIndex;
	                    ibest2=thisIndex;
	                    foundBestThis=true;
	                }else if(!foundBestThis && diff1<minDiff1) {
	                    minDiff1=diff1;
	                    ibest1=thisIndex;
	                }
	                
	                for(int m=0;m<indexesOfRecordsInNextGroupV.size();m++) {
	                    int nextIndex=indexesOfRecordsInNextGroupV.get(m).intValue();
	                    if(nextIndex>=thisIndex) {

	                        diff2=thisGroup.findAverageDistanceToGroup(recordsV.get(thisIndex))+nextGroup.findAverageDistanceToGroup(recordsV.get(nextIndex));                                 
	                        //diff2=Math.abs(thisGroup.getMeanEnergy()-recordsV.get(thisIndex).ERPF())+Math.abs(nextGroup.getMeanEnergy()-recordsV.get(nextIndex).ERPF());
	                        
	                        if(nextGroup.getReferenceRecord()==recordsV.get(nextIndex)) {
	                            ibestNext=nextIndex;
	                            foundBestNext=true;
	                        }else if(!foundBestNext && diff2<minDiff2) {
	                            minDiff2=diff2;
	                            if(!foundBestThis)
	                                ibest2=thisIndex;
	                            
	                            ibestNext=nextIndex;           
	                        }
	                    }
	                }         
	            }
	            
	            ibest=ibest1;
	            
	            /*
	            //debug
	            if(recordsV.get(0).ES().contains("509.2")) {
		        
	            	System.out.println(indexesOfRecordsInNextGroupV.size()+" "+recordsV.get(0).ES()+"  "+recordsV.get(0).ERPF()+
	            			" "+refRecordGroupsV.size()+" ntries="+ntries+" isGamma="+isGamma);
	            	for(RecordGroup g:refRecordGroupsV)
	            		System.out.println("    "+g.recordsV().get(0).ES()+"  dsid="+g.dsidsV().get(0));
	            }
	            */
	            
	            if(indexesOfRecordsInNextGroupV.size()==1) {
	            	int nextIndex=indexesOfRecordsInNextGroupV.get(0).intValue();
	            	int maxThisIndex=-1;
	            	if(tempIndexes.size()>0)
	            		maxThisIndex=tempIndexes.lastElement().intValue();
	            	
	            	/*
	            	//debug
	            	if(!isGamma && Math.abs(thisGroup.getMeanEnergy()-7036)<50 && dsid.contains("(3HE,T)")) { 
	                    System.out.println("\nEnsdfGroup 1364: nIndexes="+nIndexes+" inPrevGroup="+inPrevIndexes+" inNextGroup="+inNextIndexes
	                    		+"  ibest1="+ibest1+" minDiff1="+minDiff1+" ibest2="+ibest2+" minDiff2="+minDiff2+"  next group null="+(nextGroup==null));
	                    System.out.println("   nextIndex="+nextIndex+"  maxThisIndex="+maxThisIndex);

	                    if(prevGroup!=null)
	                    for(Record r:prevGroup.recordsV()) {
	                        Level l=(Level)r;
	                        System.out.println("--> in prev group level E: "+l.ES()+" JPI="+l.JPiS()+" dsid="+prevGroup.dsidsV().get(prevGroup.recordsV().indexOf(r)));
	                    }
	                    for(Record r:thisGroup.recordsV()) {
	                        Level l=(Level)r;
	                        System.out.println("--> in this group level E: "+l.ES()+" JPI="+l.JPiS()+" dsid="+thisGroup.dsidsV().get(thisGroup.recordsV().indexOf(r)));
	                    }
	                    if(nextGroup!=null)
	                    for(Record r:nextGroup.recordsV()) {
	                        Level l=(Level)r;
	                        System.out.println("--> in next group level E: "+l.ES()+" JPI="+l.JPiS()+" dsid="+nextGroup.dsidsV().get(nextGroup.recordsV().indexOf(r)));
	                    }

	                }
					*/
	            	
	            	if(nextIndex<maxThisIndex) {
	            		//it is likely the nextGroup is not really able to hold record from recordsV
	            		//then find another next group to which records from recordsV have been assigned
	            		//this group can not hold the record at nextIndex and index after
	                    
	            		boolean isGoodNextIndex=false;
	            		
	            		j=-1;
	                    if(nextGroup!=null) {
	                    	j=refRecordGroupsV.indexOf(nextGroup);
	                    	
	                    	float distNextRecToNextGroup=nextGroup.findAverageDistanceToGroup(recordsV.get(nextIndex));
	                    	float distNextRecToThisGroup=thisGroup.findAverageDistanceToGroup(recordsV.get(nextIndex));
	    				    
	                    	boolean isGammaConsistentNextOnly=false;
	                    	boolean isGammaConsistentThisOnly=false;
	                    	if(!isGamma) {
	                    	    Level l=(Level)recordsV.get(nextIndex);
	                    	    boolean isGammaConsistentNext=nextGroup.isGammasConsistent(l,deltaEG,false);
	                    	    boolean isGammaConsistentThis=thisGroup.isGammasConsistent(l,deltaEG, false);
	                    	    if(isGammaConsistentNext && !isGammaConsistentThis)
	                    	        isGammaConsistentNextOnly=true;
	                    	    else if(!isGammaConsistentNext && isGammaConsistentThis)
	                    	        isGammaConsistentThisOnly=true;
	                    	}
	                    	
	                    	if(isGammaConsistentNextOnly || (!isGammaConsistentThisOnly&&distNextRecToNextGroup<distNextRecToThisGroup) ) {
	                    		isGoodNextIndex=true;
	                    		
	                         	for(int k=0;k<indexesOfRecordsInGroupV.size();k++) {
	                        		int tempIndex=indexesOfRecordsInGroupV.get(k);
	                        		if(tempIndex>nextIndex) {
	                        			float distToThisGroup=thisGroup.findAverageDistanceToGroup(recordsV.get(tempIndex));
	                        			if(distToThisGroup<distNextRecToNextGroup) {
	                        				isGoodNextIndex=false;
	                        				break;
	                        			}
	                        		}
	                        		
	                        	}	
	                    	}

	                    }
	                    
	                    /*
	                	//debug
	                    if(!isGamma && Math.abs(thisGroup.getMeanEnergy()-7036)<50 && dsid.contains("(3HE,T)")) { 
	                        System.out.println("\nEnsdfGroup 1422: isGoodNextIndex="+isGoodNextIndex
	                        		+"  ibest1="+ibest1+" minDiff1="+minDiff1+" ibest2="+ibest2+" minDiff2="+minDiff2);
	                        System.out.println("   nextIndex="+nextIndex+"  maxThisIndex="+maxThisIndex+" tempIndexes.size()="+tempIndexes.size());
	    				}
	    				*/
	                    
	                    if(isGoodNextIndex) {
	                    	inNextIndexes=true;
		                    for(int k=tempIndexes.size()-1;k>=0;k--) {
		                        Integer ind=tempIndexes.get(k);
		                        if(ind.intValue()>nextIndex) {
		                            tempIndexes.removeElement(ind);
		                        }
		                    }                           	
	                    }else {
	                        int size=0;
	                        
	    					while(j>0 && j<refRecordGroupsV.size()-1 && size==0) {
	    	                    nextGroup=refRecordGroupsV.get(j+1);
	    	                    indexesOfRecordsInNextGroupV=indexesOfRecordsInGroupVV.get(j+1);
	    	                    size=indexesOfRecordsInNextGroupV.size();
	    	                    
	    	                    if(size>0)
	    	                        maxNextIndex=indexesOfRecordsInNextGroupV.lastElement().intValue();
	    	                    
	    	                    for(int k=indexesOfRecordsInGroupV.size()-1;k>=0;k--) {
	    	                        Integer ind=indexesOfRecordsInGroupV.get(k);
	    	                        if(indexesOfRecordsInNextGroupV.contains(ind)) {
	    	                            tempIndexes.removeElement(ind);
	    	                            inNextIndexes=true;
	    	                        }else if(inNextIndexes) {
	    	                            
	    	                        }
	    	                    }
	    	                    j++;	                    
	    	                }                             	
	                    }

	                    
						if(indexesOfRecordsInNextGroupV.size()>0) {
							ntries++;
							continue;
						}else
							break;
	            	}
	            }
		        //System.out.println(" #2 "+indexesOfRecordsInNextGroupV.size());
	               
	            done=true;
	        }

	        if(ibest1>ibest2 && ibest2>=0)
	            ibest=ibest2;
	        
	        minDiff=minDiff2;
	        

            
	        /*
	        //debug
			float thisAvgE=thisGroup.getMeanEnergy();
			int nIndexes0=indexesOfRecordsInGroupV.size();	
			if(!isGamma && Math.abs(thisGroup.getMeanEnergy()-6000)<60 ) { 
	            System.out.println("\n EnsdfGrup 1522 ###### nIndexes0="+nIndexes0+" nIndexes="+nIndexes+" inPrevGroup="+inPrevIndexes+" inNextGroup="+inNextIndexes
	            		+"  ibest1="+ibest1+" minDiff1="+minDiff1+" ibest2="+ibest2+" minDiff2="+minDiff2+"  next group null="+(nextGroup==null)+" ibest="+ibest+" ibestNext="+ibestNext);

	            for(int k=0;k<nIndexes;k++)
	                System.out.println(" this nIndexes after removing: record #"+k+"  index="+tempIndexes.get(k)+" E="+recordsV.get(tempIndexes.get(k)).ES());
	            

	            if(prevGroup!=null)
	            for(int k=0;k<indexesOfRecordsInPrevGroupV.size();k++)
	                System.out.println("prev nIndexes : record #"+k+" index="+indexesOfRecordsInPrevGroupV.get(k)+" E="+recordsV.get(indexesOfRecordsInPrevGroupV.get(k)).ES());
	            
	            for(int k=0;k<nIndexes0;k++)
	                System.out.println("this nIndexes0: record #"+k+"  index="+indexesOfRecordsInGroupV.get(k)+" E="+recordsV.get(indexesOfRecordsInGroupV.get(k)).ES());
	            
	            if(nextGroup!=null)
	            for(int k=0;k<indexesOfRecordsInNextGroupV.size();k++)
	                System.out.println("next nIndexes : record #"+k+" index="+indexesOfRecordsInNextGroupV.get(k)+" E="+recordsV.get(indexesOfRecordsInNextGroupV.get(k)).ES());
	            


	            if(prevGroup!=null)
	            for(Record r:prevGroup.recordsV()) {
	                Level l=(Level)r;
	                System.out.println("--> in prev group level E: "+l.ES()+" JPI="+l.JPiS()+" dsid="+prevGroup.dsidsV().get(prevGroup.recordsV().indexOf(r)));
	            }
	            for(Record r:thisGroup.recordsV()) {
	                Level l=(Level)r;
	                System.out.println("--> in this group level E: "+l.ES()+" JPI="+l.JPiS()+" dsid="+thisGroup.dsidsV().get(thisGroup.recordsV().indexOf(r)));
	            }
	            if(nextGroup!=null)
	            for(Record r:nextGroup.recordsV()) {
	                Level l=(Level)r;
	                System.out.println("--> in next group level E: "+l.ES()+" JPI="+l.JPiS()+" dsid="+nextGroup.dsidsV().get(nextGroup.recordsV().indexOf(r)));
	            }

	        }
	        */

	        /*
            //debug
	        boolean skip=true;
            if(!isGamma && Math.abs(refRecordGroupsV.get(i).getMeanEnergy()-7482)<40) {
                
                for(Record r:recordsV) {
                    if(r.ES().equals("7482")) {
                        skip=false;
                        break;
                    }
                }
                
                if(!skip) {
                    RecordGroup group=refRecordGroupsV.get(i);
                    System.out.println("@@@@4831: in group# "+i+" record ibest="+ibest+" ES="+recordsV.get(ibest).ES()+" ibestNext="+ibestNext);
                    System.out.println("         minPrevIndex="+minPrevIndex);
                    for(int k=0;k<group.nRecords();k++)
                        System.out.println("       record #i="+k+" ES="+group.recordsV().get(k).ES()+" dsid="+group.dsidsV().get(k));
                    for(int k=0;k<indexesOfRecordsInGroupV.size();k++) {
                        int index=indexesOfRecordsInGroupV.get(k).intValue();
                        System.out.println("    ###for this group in this dataset record index="+index+" ES="+recordsV.get(index).ES());
                    }
                    
                    for(int k=0;k<indexesOfRecordsInPrevGroupV.size();k++) {
                        int index=indexesOfRecordsInPrevGroupV.get(k).intValue();
                        System.out.println("    $$$for prev group in this dataset record index="+index+" ES="+recordsV.get(index).ES());
                    }
                    
                    for(int k=0;k<indexesOfRecordsInNextGroupV.size();k++) {
                        int index=indexesOfRecordsInNextGroupV.get(k).intValue();
                        System.out.println("    %%%for next group in this dataset record index="+index+" ES="+recordsV.get(index).ES());
                    }
                    
                }
                

            }
            */
	        
	        float diffToRef=1000,minDiffToRef=1000;
	        for(int k=0;k<indexesOfRecordsInGroupV.size();k++) {
	            Integer ind=indexesOfRecordsInGroupV.get(k);
	            thisIndex=ind.intValue();
	            
	            Record rec=recordsV.get(thisIndex);
	            if(thisIndex>=minPrevIndex && ibestNext>0) {
	                //if(thisIndex>ibestNext)
	                //    ibestNext=thisIndex;

	            
	                diff=thisGroup.findAverageDistanceToGroup(rec)+nextGroup.findAverageDistanceToGroup(recordsV.get(ibestNext));
	            	
	            	//diff=Math.abs(thisGroup.getMeanEnergy()-recordsV.get(thisIndex).ERPF())+Math.abs(nextGroup.getMeanEnergy()-recordsV.get(ibestNext).ERPF());
	            	//NOT OPTIMAL CONDITION
	            	if(thisIndex==ibestNext && thisIndex==indexesOfRecordsInGroupV.size()-1)
	            	    diff+=5.0;
	            	
	            	diffToRef=Math.abs(rec.ERPF()-thisGroup.getReferenceRecord().ERPF())+diff;
	            	if(Math.abs(diff-minDiff)<deltaEL) {
	            	    if(diffToRef<minDiffToRef) {
	            	        minDiffToRef=diffToRef;
	                        if(isGamma || (thisIndex!=ibest&&thisGroup.whichMatchBetter((Level)recordsV.get(thisIndex),(Level)recordsV.get(ibest))>=0) ) {
	                            ibest=thisIndex;     
	                        }
	            	    }else if(diffToRef==minDiffToRef && (isGamma||thisGroup.whichMatchBetter((Level)recordsV.get(thisIndex),(Level)recordsV.get(ibest))>0)){//rec at thisIndex and ibest found previously have the same energy
	            	        ibest=thisIndex; 
	            	    }
	            	}
	            	
	            	/*
	                //debug
	            	if(!skip) {
	            	//if(!isGamma && Math.abs(thisGroup.getMeanEnergy()-3500)<100 && Math.abs(rec.EF()-3450)<100) { 
	                    System.out.println("EnsdfGroup 4936:  *** k="+k+"  this rec="+rec.ES()+" ibest="+ibest+"  thisIndex="+thisIndex+" e="+recordsV.get(ibest).ES()+" next E="+recordsV.get(ibestNext).ES()
	                    		+"  diffToRef="+diffToRef+" minDiffToRef="+minDiffToRef+" diff="+diff+"  minDiff="+minDiff+"  test="+
	                    		thisGroup.whichMatchBetter((Level)recordsV.get(thisIndex),(Level)recordsV.get(ibest)));
	                    System.out.println(" Math.abs(diff-minDiff)="+Math.abs(diff-minDiff)+" deltaEL="+deltaEL);
	                    
	                    for(int m=0;m<thisGroup.nRecords();m++)
	                    	System.out.println("    In group: "+thisGroup.getRecord(m).ES()+" "+thisGroup.getRecord(m).DES());
	                   		
	            	}
	            	*/
	            	
	            }
	        }

            for(int k=0;k<indexesOfRecordsInGroupV.size();k++) {
                Integer ind=indexesOfRecordsInGroupV.get(k);
                thisIndex=ind.intValue();                    
                if(thisIndex<ibest) {
                    //Record thisRec=recordsV.get(thisIndex);
                    //Record refRecordOfNextGroup=nextGroup.getReferenceRecord();
                    //if(thisRec!=refRecordOfNextGroup)
                        indexesOfRecordsInNextGroupV.removeElement(ind);

                    //debug
                    //if(!isGamma && Math.abs(recordsV.get(ind.intValue()).EF()-6000)<60) 
                    //    System.out.println(" ENsdfGroup 1609: ***ibest="+ibest+"  e="+recordsV.get(ibest).ES()+" this E="+recordsV.get(thisIndex).ES()+" next E="+recordsV.get(ibestNext).ES());
                    
                }else if(thisIndex>ibest) {
                    
                    indexesOfRecordsInPrevGroupV.removeElement(ind);
                }
            }   
            
            /*
            //debug
            if(!isGamma && Math.abs(refRecordGroupsV.get(i).getMeanEnergy()-7482)<40) {
                boolean skip=true;
                for(Record r:recordsV)
                    if(r.ES().equals("7482")) {
                        skip=false;
                        break;
                    }
                
                if(!skip) {
                    RecordGroup group=refRecordGroupsV.get(i);
                    System.out.println("@@@@4972: in group# "+i+" record ibest="+ibest+" ES="+recordsV.get(ibest).ES());
                    for(int k=0;k<group.nRecords();k++)
                        System.out.println("       record #i="+k+" ES="+group.recordsV().get(k).ES()+" dsid="+group.dsidsV().get(k));
                    for(int k=0;k<indexesOfRecordsInGroupV.size();k++) {
                        int index=indexesOfRecordsInGroupV.get(k).intValue();
                        System.out.println("    ###for this group in this dataset record index="+index+" ES="+recordsV.get(index).ES());
                    }
                    
                    for(int k=0;k<indexesOfRecordsInPrevGroupV.size();k++) {
                        int index=indexesOfRecordsInPrevGroupV.get(k).intValue();
                        System.out.println("    $$$for prev group in this dataset record index="+index+" ES="+recordsV.get(index).ES());
                    }
                    
                    for(int k=0;k<indexesOfRecordsInNextGroupV.size();k++) {
                        int index=indexesOfRecordsInNextGroupV.get(k).intValue();
                        System.out.println("    %%%for next group in this dataset record index="+index+" ES="+recordsV.get(index).ES());
                    }
                    
                }

            }
            */
            
            /*
	        //debug
			float thisAvgE=thisGroup.getMeanEnergy();
			int nIndexes0=indexesOfRecordsInGroupV.size();	
			if(!isGamma && recordsV.size()>6 && recordsV.get(6).ES().equals("6.00E3") ) { 
			//if(!isGamma && Math.abs(thisGroup.getMeanEnergy()-6000)<60 && recordsV.size()>6 && recordsV.get(6).ES().equals("6.00E3") ) { 
	            System.out.println("\n EnsdfGrup 4269 @@@@@@ nIndexes0="+nIndexes0+" nIndexes="+nIndexes+" inPrevGroup="+inPrevIndexes+" inNextGroup="+inNextIndexes
	            		+"  ibest1="+ibest1+" minDiff1="+minDiff1+" ibest2="+ibest2+" minDiff2="+minDiff2+"  next group null="+(nextGroup==null)+" ibest="+ibest+" ibestNext="+ibestNext);

	            for(int k=0;k<nIndexes;k++)
	                System.out.println(" this nIndexes after removing: record #"+k+"  index="+tempIndexes.get(k)+" E="+recordsV.get(tempIndexes.get(k)).ES());
	            

	            if(prevGroup!=null)
	            for(int k=0;k<indexesOfRecordsInPrevGroupV.size();k++)
	                System.out.println("prev nIndexes : record #"+k+" index="+indexesOfRecordsInPrevGroupV.get(k)+" E="+recordsV.get(indexesOfRecordsInPrevGroupV.get(k)).ES());
	            
	            for(int k=0;k<nIndexes0;k++)
	                System.out.println("this nIndexes0: record #"+k+"  index="+indexesOfRecordsInGroupV.get(k)+" E="+recordsV.get(indexesOfRecordsInGroupV.get(k)).ES());
	            
	            if(nextGroup!=null)
	            for(int k=0;k<indexesOfRecordsInNextGroupV.size();k++)
	                System.out.println("next nIndexes : record #"+k+" index="+indexesOfRecordsInNextGroupV.get(k)+" E="+recordsV.get(indexesOfRecordsInNextGroupV.get(k)).ES());
	            


	            if(prevGroup!=null)
	            for(Record r:prevGroup.recordsV()) {
	                Level l=(Level)r;
	                System.out.println("--> in prev group level E: "+l.ES()+" JPI="+l.JPiS()+" dsid="+prevGroup.dsidsV().get(prevGroup.recordsV().indexOf(r)));
	            }
	            for(Record r:thisGroup.recordsV()) {
	                Level l=(Level)r;
	                System.out.println("--> in this group level E: "+l.ES()+" JPI="+l.JPiS()+" dsid="+thisGroup.dsidsV().get(thisGroup.recordsV().indexOf(r)));
	            }
	            if(nextGroup!=null)
	            for(Record r:nextGroup.recordsV()) {
	                Level l=(Level)r;
	                System.out.println("--> in next group level E: "+l.ES()+" JPI="+l.JPiS()+" dsid="+nextGroup.dsidsV().get(nextGroup.recordsV().indexOf(r)));
	            }

	        }
			*/
            
            
            if(ibest>0) {
                indexesOfRecordsInGroupV.clear();
                indexesOfRecordsInGroupV.add(ibest); 
                
                Record bestRec=recordsV.get(ibest);
                RecordGroup closestGroup=findClosestGroupForRecord(bestRec,refRecordGroupsV,true);
                
    			int indexOfClosestGroup=refRecordGroupsV.indexOf(closestGroup);
    			if(closestGroup!=null && indexOfClosestGroup!=i) {
            
    				boolean isGood=true;
	                //further check: still need more work here
    				boolean thisGroupComp1=thisGroup.hasComparableEnergyEntry(bestRec, deltaE,true);//force to use the given uncertainty deltaE for comparisons
    				boolean thisGroupComp2=thisGroup.hasComparableEnergyEntry(bestRec,deltaE);//using uncertainties in records in existing for comparisons
    				boolean closestGroupComp1=closestGroup.hasComparableEnergyEntry(bestRec,deltaE,true);
    				boolean closestGroupComp2=closestGroup.hasComparableEnergyEntry(bestRec,deltaE);
	                if( (!thisGroupComp1&&!thisGroupComp2) && (closestGroupComp1||closestGroupComp2)) {
	                    if(isGamma || closestGroup.hasOverlapJPI((Level)bestRec)) {
	                    	isGood=false;
	                    }
	                }  
	                
	                /*
                    //debug
	                if(!isGamma && recordsV.size()>6 && recordsV.get(6).ES().equals("6.00E3") ) { 
                	//if(!isGamma && Math.abs(thisGroup.getMeanEnergy()-7010)<30 && dsid.contains("(P,T)")) { 
	                //if(!isGood) {
                        System.out.println(" EnsdfGroup 1618: ibest="+ibest+" E="+bestRec.ES()+" DE="+bestRec.DES()+" isGamma="+isGamma+" closest group index="+indexOfClosestGroup+" this group index="+i+
                        		" deltaE="+deltaE+" match within datalE: thisGroup="+thisGroup.hasComparableEnergyEntry(bestRec, deltaE,true)+
                        		" closestGroup="+closestGroup.hasComparableEnergyEntry(bestRec, deltaE,true)+" isGood best="+isGood);
                        for(String s:thisGroup.dsidsV()) {
                        	Record r=thisGroup.getRecordByDSID(s);
                        	System.out.println("   in this Group rec E="+r.ES()+" DE="+r.DES()+" dsid="+s);
                        }
                       		
                	}
                	*/
                	
	                if(!isGood) {
	                	indexesOfRecordsInGroupV.clear();
	                	continue;
	                }
    			}

            }   
            

            /*
	        //debug
			thisAvgE=thisGroup.getMeanEnergy();
			nIndexes0=indexesOfRecordsInGroupV.size();	
			if(!isGamma && recordsV.size()>6 && recordsV.get(6).ES().equals("6.00E3") ) { 
			//if(!isGamma && Math.abs(thisGroup.getMeanEnergy()-6000)<60 && recordsV.size()>6 && recordsV.get(6).ES().equals("6.00E3") ) { 
	            System.out.println("\n EnsdfGrup 4310 ###### nIndexes0="+nIndexes0+" nIndexes="+nIndexes+" inPrevGroup="+inPrevIndexes+" inNextGroup="+inNextIndexes
	            		+"  ibest1="+ibest1+" minDiff1="+minDiff1+" ibest2="+ibest2+" minDiff2="+minDiff2+"  next group null="+(nextGroup==null)+" ibest="+ibest+" ibestNext="+ibestNext);

	            for(int k=0;k<nIndexes;k++)
	                System.out.println(" this nIndexes after removing: record #"+k+"  index="+tempIndexes.get(k)+" E="+recordsV.get(tempIndexes.get(k)).ES());
	            

	            if(prevGroup!=null)
	            for(int k=0;k<indexesOfRecordsInPrevGroupV.size();k++)
	                System.out.println("prev nIndexes : record #"+k+" index="+indexesOfRecordsInPrevGroupV.get(k)+" E="+recordsV.get(indexesOfRecordsInPrevGroupV.get(k)).ES());
	            
	            for(int k=0;k<nIndexes0;k++)
	                System.out.println("this nIndexes0: record #"+k+"  index="+indexesOfRecordsInGroupV.get(k)+" E="+recordsV.get(indexesOfRecordsInGroupV.get(k)).ES());
	            
	            if(nextGroup!=null)
	            for(int k=0;k<indexesOfRecordsInNextGroupV.size();k++)
	                System.out.println("next nIndexes : record #"+k+" index="+indexesOfRecordsInNextGroupV.get(k)+" E="+recordsV.get(indexesOfRecordsInNextGroupV.get(k)).ES());
	            


	            if(prevGroup!=null)
	            for(Record r:prevGroup.recordsV()) {
	                Level l=(Level)r;
	                System.out.println("--> in prev group level E: "+l.ES()+" JPI="+l.JPiS()+" dsid="+prevGroup.dsidsV().get(prevGroup.recordsV().indexOf(r)));
	            }
	            for(Record r:thisGroup.recordsV()) {
	                Level l=(Level)r;
	                System.out.println("--> in this group level E: "+l.ES()+" JPI="+l.JPiS()+" dsid="+thisGroup.dsidsV().get(thisGroup.recordsV().indexOf(r)));
	            }
	            if(nextGroup!=null)
	            for(Record r:nextGroup.recordsV()) {
	                Level l=(Level)r;
	                System.out.println("--> in next group level E: "+l.ES()+" JPI="+l.JPiS()+" dsid="+nextGroup.dsidsV().get(nextGroup.recordsV().indexOf(r)));
	            }

	        }
	        */
/*
			if(nIndexes==0) {//for nIndexes0>1
                tempIndexes.clear();
                
			    if(!inPrevIndexes) {//nIndexes=0 or 1 for prevGroup after being processed for nIndexes0>1
			        minDiff=10000;
			        ibest=-1;
			        ibestNext=-1;
			    	for(int k=0;k<indexesOfRecordsInGroupV.size();k++) {
                        Integer ind=indexesOfRecordsInGroupV.get(k);
                        thisIndex=ind.intValue();
                        
                        for(int m=0;m<indexesOfRecordsInNextGroupV.size();m++) {
                            int nextIndex=indexesOfRecordsInNextGroupV.get(m).intValue();
                            if(nextIndex>=thisIndex) {
     
                                //float e2=recordsV.get(nextIndex).ERPF();
                                //float diff=Math.abs(e1-thisAvgE)+Math.abs(e2-nextAvgE);
                                diff=thisGroup.findAverageDistanceToGroup(recordsV.get(thisIndex))+nextGroup.findAverageDistanceToGroup(recordsV.get(nextIndex));
                                
                                if(diff<minDiff && Math.abs(diff-minDiff)>5.0) {
                                	//if ibest not the first index, all records before ibest in current indexesOfRecords will be added into new groups 
                                    minDiff=diff;
                                    ibest=thisIndex;
                                    ibestNext=nextIndex;
                                }
     
                                //debug
                                //if(!isGamma && Math.abs(thisAvgE-9816)<4 && dsid.contains("30SI(P,")) { 
                                //    System.out.println("  ***e1="+e1+" e2="+e2+"  ibest="+ibest+"  best="+recordsV.get(ibest).ES()+" next best="+recordsV.get(ibestNext).ES());
                                //    System.out.println(" thisIndex="+thisIndex+" nextIndex="+nextIndex+" this mean E="+thisGroup.getMeanEnergy()+" next mean E"+nextGroup.getMeanEnergy());
                                //}
                            }
                        }
			    	}
			    	
                    if(ibest<ibestNext) {
                        for(int k=0;k<indexesOfRecordsInGroupV.size();k++) {
                            Integer ind=indexesOfRecordsInGroupV.get(k);
                            thisIndex=ind.intValue();
                            if(thisIndex<ibestNext ) {
                                ibest=thisIndex;
                                indexesOfRecordsInNextGroupV.removeElement(ind);
                                //debug
                                //if(!isGamma && Math.abs(thisAvgE-9816)<10 && dsid.contains("30SI(P,")) 
                                //    System.out.println("  ***ibest="+ibest+"  e="+recordsV.get(ibest).ES()+" next E="+recordsV.get(ibestNext).ES());
                                
                                break;
                            }
                        }
                    }
                    
                    if(ibest>0) {
                        tempIndexes.add(ibest);
                        indexesOfRecordsInGroupV.clear();
                        indexesOfRecordsInGroupV.addAll(tempIndexes); 

                    }

			    }else {
			        //here inPrevGroup=inNextGroup=true, since nIndexes==1 in this case 
			        //(nIndexes can only be 0 or 1 for prevGroup after being processed),
			        //and nIndexes0>1 for this group
			  
			        minDiff=10000;
			        ibest=-1;
			        ibestNext=-1;
                    for(int k=0;k<indexesOfRecordsInGroupV.size();k++) {
                        Integer ind=indexesOfRecordsInGroupV.get(k);
                        thisIndex=ind.intValue();
                        if(ind.intValue()<minPrevIndex) 
                            continue;
                            
                        //float e1=recordsV.get(thisIndex).ERPF();
                        
                        for(int m=0;m<indexesOfRecordsInNextGroupV.size();m++) {
                            int nextIndex=indexesOfRecordsInNextGroupV.get(m).intValue();
                            if(nextIndex>=thisIndex) {
     
                                //float e2=recordsV.get(nextIndex).ERPF();
                                //float diff=Math.abs(e1-thisAvgE)+Math.abs(e2-nextAvgE);
                                diff=thisGroup.findAverageDistanceToGroup(recordsV.get(thisIndex))+nextGroup.findAverageDistanceToGroup(recordsV.get(nextIndex));
                                
                                if(diff<minDiff) {
                                    minDiff=diff;
                                    ibest=thisIndex;
                                    ibestNext=nextIndex;
                                }
     
                                //debug
                                //if(!isGamma && Math.abs(thisAvgE-9816)<4 && dsid.contains("30SI(P,")) { 
                                //    System.out.println("  ***e1="+e1+" e2="+e2+"  ibest="+ibest+"  best="+recordsV.get(ibest).ES()+" next best="+recordsV.get(ibestNext).ES());
                                //    System.out.println(" thisIndex="+thisIndex+" nextIndex="+nextIndex+" this mean E="+thisGroup.getMeanEnergy()+" next mean E"+nextGroup.getMeanEnergy());
                                //}
                            }
                        }         
                    }
                    
                    boolean done=false;
                    for(int k=0;k<indexesOfRecordsInGroupV.size();k++) {
                        Integer ind=indexesOfRecordsInGroupV.get(k);
                        thisIndex=ind.intValue();
                        
                        if(!done && thisIndex>=minPrevIndex && ibestNext>0) {
                        	diff=thisGroup.findAverageDistanceToGroup(recordsV.get(thisIndex))+nextGroup.findAverageDistanceToGroup(recordsV.get(ibestNext));
                        	
                        	if(Math.abs(diff-minDiff)<5) {
                        		ibest=thisIndex;
                        		done=true;
                        	}
                        	
            				//debug
                            if(!isGamma && Math.abs(thisAvgE-11360)<40 && dsid.contains("30SI(P,G")) { 
                                System.out.println("     this index="+thisIndex+" ibest="+ibest+" e1="+recordsV.get(thisIndex).ES()+
                                		" ibestNext="+ibestNext+"  e2="+recordsV.get(ibestNext).ES()+"  mindiff="+minDiff+"  diff="+diff);

                            }
                            
                        }
                        
                        if(thisIndex<ibest) {
                            indexesOfRecordsInNextGroupV.removeElement(ind);
                            //debug
                            //if(!isGamma && Math.abs(thisAvgE-9816)<10 && dsid.contains("30SI(P,")) 
                            //    System.out.println("  ***ibest="+ibest+"  e="+recordsV.get(ibest).ES()+" next E="+recordsV.get(ibestNext).ES());
                            
                        }
                    }
                    
                    if(ibest>0) {
                        tempIndexes.add(ibest);
                        indexesOfRecordsInGroupV.clear();
                        indexesOfRecordsInGroupV.addAll(tempIndexes); 

                    }

			    }
			}else if(nIndexes==1) {
			    indexesOfRecordsInGroupV.clear();
			    indexesOfRecordsInGroupV.addAll(tempIndexes);
			}else {
			    
				irecord=tempIndexes.get(0).intValue();
				
				minDiff=thisGroup.findAverageDistanceToGroup(recordsV.get(irecord));
				diff=1000000;
		        int iremove=1;
		        
				while(nIndexes>1){
					irecord=tempIndexes.get(1).intValue();
					
					iremove=1;
					diff=thisGroup.findAverageDistanceToGroup(recordsV.get(irecord));
					if(diff<minDiff){
						minDiff=diff;
						iremove=0;
					}
					
					tempIndexes.removeElementAt(iremove);		
					nIndexes--;
				}
				
                indexesOfRecordsInGroupV.clear();
                indexesOfRecordsInGroupV.addAll(tempIndexes); 
			}
*/					        
		}
		
		
		return bestIndexes;
	}
	
	@SuppressWarnings("unused")
	private boolean isTentativeJP(String js) {
        boolean isTentativeJP=false;
        if(js.contains("(") || js.contains("["))
            isTentativeJP=true;
        else {
            String s=js.replace(",","").replace("/","").trim();
            if(!s.isEmpty() && !Str.isNumeric(s))
                isTentativeJP=true;
        } 
        
        return isTentativeJP;
	}
	
	private boolean isTentativeSpin(String js) {
        return EnsdfUtil.isTentativeSpin(js);
	}
	
	private boolean isTentativeParity(String js) {
        return EnsdfUtil.isTentativeParity(js);
	}
	
	private boolean isUniqueSpin(String js) {
        return EnsdfUtil.isUniqueSpin(js);
	}
	
	private boolean isUniqueParity(String js) {
        return EnsdfUtil.isUniqueParity(js);
	}
    /* For level or gamma:
     * 
     * fine re-group level records in a small level group from doQuickGrouping() that could 
     * contains level records still belonging to different datasets.
     * 
     * The idea is to first put all levels into groups roughly made based on a limit for 
     * energy interval and then further divide each group based on precise matching of energies
     * within error and consistency of JPI values, and also on other conditions, like gamma
     * transitions (in this step one level could be assigned to different sub-groups). 
     * The two-step grouping process is intended to save processing time for large sets of ENSDF files.
     * 
     * But the issue that hasn't be resolved is that if two levels from two datasets are assigned
     * into different groups (each group corresponding to a same Adopted level) in the first
     * step, they cannot be in the same group in the end of the grouping, even though they could be
     * belong to a same level, especially when the two levels are at the edge of each group. 
     */

    @SuppressWarnings({ "unchecked" })
	private <T extends Record> Vector<RecordGroup> doFineGrouping(RecordGroup recordGroup) throws Exception{
		Vector<RecordGroup> recordGroupsV=new Vector<RecordGroup>();
		Vector<String> xtagsV=recordGroup.xtagsV();
		Vector<String> dsidsV=recordGroup.dsidsV();

		//first re-group gammas by tag (dataset)
		HashMap<String,Vector<T>> sameTagRecordsVMap=new HashMap<String,Vector<T>>();
		HashMap<String,String> dsidMap=new HashMap<String,String>();
		for(int i=0;i<recordGroup.nRecords();i++){
			String xtag=xtagsV.get(i);
			String dsid=dsidsV.get(i);
			
			T rec=(T) recordGroup.recordsV().get(i);
			
			if(!sameTagRecordsVMap.containsKey(xtag)){
				sameTagRecordsVMap.put(xtag,new Vector<T>());
				dsidMap.put(xtag,dsid);
			}
			
			sameTagRecordsVMap.get(xtag).add(rec);

		}
		
		int maxN=0;
		String maxNxtag="";
		for(String xtag:sameTagRecordsVMap.keySet()){
			int size=sameTagRecordsVMap.get(xtag).size();
			if(size>maxN){
				maxN=size;
				maxNxtag=xtag;
			}					
		}
		
		//make new sub-groups from max number of records with the same tag (from the same dataset)
		Vector<T> maxNrecordsV=sameTagRecordsVMap.get(maxNxtag);
		String dsid=dsidMap.get(maxNxtag);
		for(int i=0;i<maxN;i++){
			RecordGroup newGroup=new RecordGroup();
			newGroup.addRecord(maxNrecordsV.get(i), dsid, maxNxtag);
			recordGroupsV.add(newGroup);
		}
    	
		//remove the records that have been used in making new sub-groups
		sameTagRecordsVMap.remove(maxNxtag);
		
		//group the remaining records
		for(String xtag:sameTagRecordsVMap.keySet()){		
			Vector<T> recordsV=sameTagRecordsVMap.get(xtag);
		    dsid=dsidMap.get(xtag);
		    
		    //System.out.println("#### in EnsdfGroup line 3197 doFineGrouping xtag="+xtag+" dsid="+dsid);
		    
		    insertRecordsToReferenceGroups(recordsV, dsid, xtag, recordGroupsV);
		}
    	
		/*
		//debug
		//if(Math.abs(recordGroup.recordsV().get(0).ERPF()-12107)<100){
    		System.out.println("In EnsdfGroup line 1398: size="+recordGroupsV.size());
			//for(Record r:recordGroup.recordsV())
			//	System.out.println("   ES="+r.ES());
			for(int i=0;i<recordGroupsV.size();i++)
				for(Record r:recordGroupsV.get(i).recordsV())
					System.out.println("  subgroup i="+i+" ES="+r.ES());
		//}
		*/
		
		return recordGroupsV;
	}

    /*
     * sort a vector of RecordGroup objects in order of it mean energy
     */
    private void sortRecordGroups(Vector<RecordGroup> recordGroupsV){
		 Vector<RecordGroup> temp=new Vector<RecordGroup>(); 
    	
		 for(int j=0;j<recordGroupsV.size();j++){
			 RecordGroup g=recordGroupsV.get(j);
			 				
			 //insert level in ascending order of energy			 				
			 int im=0;			
			 int i1=0,i2=temp.size()-1;				
			 float e=g.getReferenceRecord().ERPF();				
			 float e1,e2,em;	
			 while(i1<=i2){
				 im=(i1+i2)/2;
				 e1=temp.get(i1).getReferenceRecord().ERPF();
				 e2=temp.get(i2).getReferenceRecord().ERPF();
				 em=temp.get(im).getReferenceRecord().ERPF();
					
				 if((e-e1)*(e-em)<0){
					 i2=im-1;	
					 continue;
				 }else if((e-em)*(e-e2)<0){
				     i1=im+1;
					 continue;
			     }else if(e<=e1){
				     im=i1+(int)(Math.pow(2,(e-e1)));
					 break;
				 }else if(e>=e2){
					 im=i2+1;
					 break;
				 }else{//e==em
				 	 im=im+1;
					 break;
				 }
			}
			temp.insertElementAt(g,im);  
		 }	
		 
		 recordGroupsV.clear();
		 recordGroupsV.addAll(temp);
    }
    
	@SuppressWarnings({ "unchecked" })
	private <T extends Record> void sortRecordsByDSID(RecordGroup g){	
    	Vector<String> xtags=new Vector<String>();
    	Vector<String> dsids=new Vector<String>();
    	Vector<T> records=new Vector<T>();
        
        
    	//NOTE that for a dataset, xtag in newXTagsV could be different from
    	//its xtag in a level group where (*) could be added following the tag 
    	//character, that is
    	//newXTgasV: dataset tag character
    	//RecordGroup.xtags: tags of datasets where the levels in this group belong to
    	//                   with (*) added after tag character to indicate uncertain
    	//                   placement of the level in current group


    	
        int[] indexes=new int[g.recordsV().size()];
            	
        for(int i=0;i<indexes.length;i++){
        	int dsidIndex=datasetDSID0sV.indexOf(g.dsidsV().get(i));
        	int pos=i;
        	indexes[i]=dsidIndex;
        	
        	for(int j=i-1;j>=0;j--){
        		if(dsidIndex<indexes[j]){
        			indexes[j+1]=indexes[j];
        			indexes[j]=dsidIndex;
        			pos=j;
        		}else{
        			pos=j+1;
        			break;
        		}
        	}
        	
        	xtags.insertElementAt(g.xtagsV().get(i), pos);
        	dsids.insertElementAt(g.dsidsV().get(i), pos);
        	records.insertElementAt((T)g.recordsV().get(i), pos);
        }
        
        Record refRecord=g.getReferenceRecord();
        
    	g.clear();
    	for(int i=0;i<xtags.size();i++){
    		g.addRecord(records.get(i), dsids.get(i), xtags.get(i));
    	}

    	if(refRecord!=null)
    	    g.setReferenceRecord(refRecord);
    }
    
    
	/*
	 * sort ENSDF datasets in ensdfsV according to DSID (decay,reaction,...)
	 * sortOption:  ='A' or 'MASS' sort by mass first, 
	                ='Z' by proton number first
	 * default by mass number first
	 */
	
	public void sortENSDFs(String sortOption){		
		EnsdfUtil.sortENSDFs(ensdfV, sortOption);
	}
    	
	public void sortENSDFs(){
		sortENSDFs("A");
	}
	        		
	        		
    /* quickly group the input records based on energies by allocating them to energy-evenly-distributed 
     * empty groups and then removing the remaining empty groups
     * 
     * ensdfRecordsVV: records from different parents (it is dataset for grouping levels
     *                 and level for grouping gammas) to be grouped;
     *                 each element is a vector of records from the same dataset or level (level 
     *                 records for grouping levels and gamma records from a level for grouping gammas)
     *                 and it must be in order of ascending energies
     * groupedRecordsVV: records that have been grouped according to energies;
     *                 each element is a vector of records from different datasets or levels that are
     *                 grouped together based on energy 
     *                                
     */
    @SuppressWarnings({ "unchecked", "rawtypes", "unused" })
	private Vector<RecordGroup> doQuickGrouping0(Vector<Vector> ensdfRecordsVV,Vector<String> dsidsV,Vector<String> xtagsV,float interval){
    	Vector<RecordGroup> groupedRecordsVV=new Vector<RecordGroup>();
    	float minEF=10000,maxEF=-1;

    	int nInputSets=ensdfRecordsVV.size();   
    	
        if(nInputSets==0)
        	return groupedRecordsVV;
    	
    	for(int i=0;i<nInputSets;i++){
    		Vector<?extends Record> ensdfRecordsV=ensdfRecordsVV.get(i);
    		int nRecordsInSet=ensdfRecordsV.size();
    		
    		if(nRecordsInSet==0)
    			continue;
    		
    		float firstEF=ensdfRecordsV.firstElement().EF();
    		float lastEF=ensdfRecordsV.lastElement().EF();
    		if(firstEF<minEF)
    			minEF=firstEF;
    		if(lastEF>maxEF)
    			maxEF=lastEF;    			
    	}
    
        int maxN=2000;
        
        float gap=interval;
        float defaultInterval=20;
        if(interval<=0)
        	gap=defaultInterval;
        
        int N=(int)((maxEF-minEF)/gap)+1;
        if(N>maxN){
        	gap=(maxEF-minEF)/maxN;
        	N=maxN;
        }
        
        
        //make N empty groups
   	    for(int i=0;i<N;i++){
   	    	RecordGroup group=new RecordGroup();
   	    	groupedRecordsVV.add(group);
   	    }
    
   	    //allocate all records to the N groups based on energies (1st attempt: rough grouping)
    	for(int i=0;i<nInputSets;i++){
    		Vector<?extends Record> ensdfRecordsV=ensdfRecordsVV.get(i);
    		int nRecordsInSet=ensdfRecordsV.size();   		
    		if(nRecordsInSet==0)
    			continue;
    		
    		String dsid=dsidsV.get(i);
    		String xtag=xtagsV.get(i);
    		
    		for(int j=0;j<nRecordsInSet;j++){
    			Record r=ensdfRecordsV.get(j);
    			float ef=r.EF();
    			int index=(int)((ef-minEF)/gap);
    			groupedRecordsVV.get(index).addRecord(r, dsid, xtag);

    		}  
    	}
    	
    	
    	//remove remaining empty groups
    	int ig=0;
    	int size=groupedRecordsVV.size();
    	while(ig<size){
    		RecordGroup group=groupedRecordsVV.get(ig);
    		if(group.nRecords()==0){
    			groupedRecordsVV.remove(group);
    			size--;
    			continue;
    		}
    		
    		ig++;
    	}   	
    	
    	return groupedRecordsVV;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes", "unused" })
	private Vector<RecordGroup> doQuickGrouping(Vector<Vector> ensdfRecordsVV,Vector<String> dsidsV,Vector<String> xtagsV,float interval){
    	Vector<RecordGroup> groupedRecordsV=new Vector<RecordGroup>();

    	int nInputSets=ensdfRecordsVV.size();   
    	
        if(nInputSets==0)
        	return groupedRecordsV;
    	
    
        //first, put and sort all records in one group
        RecordGroup tempGroup=new RecordGroup();
    	for(int i=0;i<nInputSets;i++){
    		Vector<?extends Record> ensdfRecordsV=ensdfRecordsVV.get(i);
    		int nRecordsInSet=ensdfRecordsV.size();   		
    		if(nRecordsInSet==0)
    			continue;
    		
    		String dsid=dsidsV.get(i);
    		String xtag=xtagsV.get(i);
    		
    		tempGroup.insertAndSortRecords(ensdfRecordsV,dsid,xtag);
			
    	}
    	
    	if(tempGroup.nRecords()==0)
    		return groupedRecordsV;
    	
    	//second, divide the whole group to small groups according to energy gap
    	float width=0;
    	float firstEF=tempGroup.getRecord(0).EF();
    	float prevEF=firstEF;
    	RecordGroup group=new RecordGroup();
    	groupedRecordsV.add(group);
    	
    	int iGroup=0;
    	Record prevRec=null;
    	for(int i=0;i<tempGroup.nRecords();i++){
    	    Record rec=tempGroup.getRecord(i);
    		float ef=tempGroup.getRecord(i).EF();
    		float gap=ef-prevEF;
    		
    		width=ef-firstEF;
    		
    		if(gap>interval || (gap>interval/2 && width>interval)){
    		    //debug
                //if((rec instanceof Gamma) && Math.abs(rec.EF()-1940)<20) {
                //    System.out.println("EnsdfGroup 3785: size="+tempGroup.nRecords()+" i="+i+" igroup="+iGroup+" ef="+ef+" gap="+gap+" width="+width+" interval="+interval+" dsid="+tempGroup.dsidsV().get(i));
                //    System.out.println("  prevRec="+prevRec.ES()+" rec="+rec.ES()+" isComparableEnergyEntry(prevRec, rec)="+EnsdfUtil.isComparableEnergyEntry(prevRec, rec));
                //}
    		    
                if(prevRec!=null && !EnsdfUtil.isComparableEnergyEntry(prevRec, rec,deltaEL)) {
                    firstEF=ef;
                    group=new RecordGroup();
                    groupedRecordsV.add(group);
                    
                    iGroup++;
    		    }

    		}
    		
            
            //if((rec instanceof Gamma) && Math.abs(rec.EF()-1940)<20)
            //    System.out.println("EnsdfGroup 1835: size="+tempGroup.nRecords()+" i="+i+" igroup="+iGroup+" ef="+ef+" gap="+gap+" width="+width+" interval="+interval+" dsid="+tempGroup.dsidsV().get(i));

    		group.addRecord(tempGroup.recordsV().get(i), tempGroup.dsidsV().get(i),tempGroup.xtagsV().get(i));
    		prevEF=ef;
    		prevRec=rec;
    	}

			
    	return groupedRecordsV;
    }
    

	///////////////////////////////////
	// searching functions
	///////////////////////////////////
    
   
	//find energy-matched adopted gammas with the input gamma
	public Vector<Gamma> findEnergyMatchedAdoptedGammas(Gamma gam){
		return findEnergyMatchedAdoptedGammas(gam,-1);			
	}
	
	@SuppressWarnings("rawtypes")
	public Vector<Gamma> findEnergyMatchedAdoptedGammas(Gamma gam,float delta){
		Vector<Gamma> matchedGammas=new Vector<Gamma>();

		for(int i=0;i<adopted.nLevels();i++) {
			Level lev=adopted.levelAt(i);
			
			Vector temp=EnsdfUtil.findMatchesByEnergyEntry(gam, lev.GammasV(), delta);
			
			for(int k=0;k<temp.size();k++)
				matchedGammas.add((Gamma)temp.get(k));
		}

		return matchedGammas;			
	}
	
	//find energy-matched adopted levels with the input level
	public Vector<Level> findEnergyMatchedAdoptedLevels(Level lev){
		return findEnergyMatchedAdoptedLevels(lev,-1);			
	}
	
	@SuppressWarnings("rawtypes")
	public Vector<Level> findEnergyMatchedAdoptedLevels(Level lev,float delta){
		Vector<Level> matchedLevels=new Vector<Level>();

		Vector temp=EnsdfUtil.findMatchesByEnergyEntry(lev, adopted.levelsV(), delta);
	
		for(int i=0;i<temp.size();i++)
			matchedLevels.add((Level)temp.get(i));
		
		return matchedLevels;			
	}
	
	//energy match and JPIs overlap after groupLevels(), PLUS those adopted with XREF containing xtag(ES) of the lev
	public Vector<Level> findMatchedAdoptedLevels(Level lev,String xtag){
		Vector<Level> matchedLevels=new Vector<Level>();
		
		Vector<Integer> indexesOfMatchedGroups=findLevelGroupIndexesOfLevel(lev);

		/*
		if(lev.ES().equals("592")) {
			 System.out.println("In ENSDFGroup line 5401 lev="+lev.EF()+" JPI= "+lev.JPiS()+" # of possible groups="+indexesOfMatchedGroups.size());
			 System.out.println("                   "+lev.EF()+"  ES="+lev.ES()+" "+lev.DES()+" erf="+lev.ERF()+"   erpf="+lev.ERPF());
		}
		*/
		
		int size=indexesOfMatchedGroups.size();
		   
		for(int i=0;i<size;i++){
			int igroup=indexesOfMatchedGroups.get(i);
			RecordGroup g=levelGroupsV.get(igroup);
			if(g.hasAdoptedRecord()){
				Level matchedAdopted=(Level)g.getAdoptedRecord();
				matchedLevels.add(matchedAdopted);
			}
			
		   /*
		   if(lev.ES().equals("2191.2+x")) {	
		   //if(Math.abs(lev.EF()-2927.6)<0.1){	
		      System.out.println("In ENSDFGroup line 2712 lev="+lev.EF()+" JPI= "+lev.JPiS()+"  "+lev.DES()+" igroup="+igroup+" hasAdoptedRecord="+g.hasAdoptedRecord()+" level0="+g.recordsV().get(0).EF()+"  "+g.dsidsV().get(0));
		   	  System.out.println("  i="+i+" size="+matchedLevels.size());
		      for(int j=0;j<g.recordsV().size();j++)
		   		  System.out.println("   l="+g.recordsV().get(j).EF()+" es="+g.recordsV().get(j).ES()+" dsid="+g.dsidsV().get(j));
		   }
		   */
		}
		
		/*
		//debug
		if(lev.ES().equals("4060.8")) {
		//if(Math.abs(lev.EF()-8820)<10){
			System.out.println("A In EnsdfGroup 5040: ES="+lev.ES()+" E="+lev.EF()+" JPI="+lev.JPiS()+" matched Adopted size="+matchedLevels.size());
			for(int i=0;i<matchedLevels.size();i++)
				System.out.println("In EnsdfGroup 4757: ES="+lev.ES()+" E="+lev.EF()+" JPI="+lev.JPiS()+" matched Adopted:"+matchedLevels.get(i).ES()+"  "+matchedLevels.get(i).JPiS());
		}
		*/
		
		if(true){
			
			//check neighboring adopted levels if the XREF of any of them has the 
			//currentENSDFXTag following by the current level energy in the parenthesis
			int index0=-1;
			float diff=0;
			Level tempLevel=null;
			
			if(matchedLevels.size()>0){
				index0=adopted.levelsV().indexOf(matchedLevels.get(0));
			}else{
				tempLevel=(Level) EnsdfUtil.findClosestByEnergyValue(lev,adopted.levelsV());
				
				if(tempLevel!=null)
					index0=adopted.levelsV().indexOf(tempLevel);
			}
			
			if(index0<0)
				index0=0;
			
			int index=index0;
			int count=0;
			
			String tag1="",tag2="",tag3="",xrefS="";
			xtag=xtag.trim();
			if(!xtag.isEmpty()) {
				String s=lev.ES();
				tag1=xtag+"("+s+")";
				tag2=xtag+"(*"+s+")";
				tag3=xtag+"("+s+"*)";
			}
			
			while(index<adopted.nLevels()){
				try{
					tempLevel=adopted.levelAt(index);
					float ef=lev.ERPF();
					diff=Math.abs(ef-tempLevel.ERPF());
					if(diff>ef*0.2 || count>5)
						break;				
					
					xrefS=tempLevel.XREFS();
					if(xrefS.contains(lev.ES()) && !matchedLevels.contains(tempLevel)){
						if(xrefS.contains(tag1)||xrefS.contains(tag2)||xrefS.contains(tag3))
							matchedLevels.add(tempLevel);
					}										
					
					/*
					//debug
					if(Math.abs(lev.EF()-8820)<10){
						System.out.println("In EnsdfGroup 4795: ES="+lev.ES()+" E="+lev.EF()+" JPI="+lev.JPiS()+" matched Adopted size="+matchedLevels.size());
						System.out.println("   tag1="+tag1+"  tag2="+tag2+" tag3="+tag3);
     					System.out.println("In EnsdfGroup 4797: count="+count+" index="+index+" temp Lev="+tempLevel.ES()+" tempAdoptedLevel.XREFS()="+tempLevel.XREFS()
     					+" matchedLevels.contains(tempLevel)="+matchedLevels.contains(tempLevel));
					}
					*/
					
					index=index+1;
					count++;
				}catch(Exception e){
					e.printStackTrace();
					break;
				}
			}
			
			index=index0;
			count=0;
			while(index>=0){
				try{
					index=index-1;
					tempLevel=adopted.levelAt(index);
					diff=Math.abs(lev.ERPF()-tempLevel.ERPF());
					if(diff>1000 || count>5)
						break;
					
					xrefS=tempLevel.XREFS();
					if(xrefS.contains(lev.ES()) && !matchedLevels.contains(tempLevel)){
						if(xrefS.contains(tag1)||xrefS.contains(tag2)||xrefS.contains(tag3))
							matchedLevels.add(tempLevel);
					}
					
					/*
					//debug
					if(Math.abs(lev.EF()-8820)<10){
						System.out.println("In EnsdfGroup 4842: ES="+lev.ES()+" E="+lev.EF()+" JPI="+lev.JPiS()+" matched Adopted size="+matchedLevels.size());
						System.out.println("   tag1="+tag1+"  tag2="+tag2+" tag3="+tag3);
     					System.out.println("In EnsdfGroup 4844: count="+count+" index="+index+" temp Lev="+tempLevel.ES()+" tempAdoptedLevel.XREFS()="+tempLevel.XREFS()
     					+" matchedLevels.contains(tempLevel)="+matchedLevels.contains(tempLevel));
					}
					*/
					
					count++;
				}catch(Exception e){
					break;
				}
			}
		}
		
		/*
		//debug
		if(lev.ES().contains("11.86E3"))
		for(int i=0;i<matchedLevels.size();i++)
		System.out.println("In ConsistencyCheck 4826:  E="+lev.EF()+" JPI="+lev.JPiS()+" matched Adopted:"+matchedLevels.get(i).ES()+"  "+matchedLevels.get(i).JPiS());
		*/
		
		//further check gammas
		if(matchedLevels.size()>1 && lev.nGammas()>0){
			Vector<Level> tempLevels=new Vector<Level>();
			for(int i=0;i<matchedLevels.size();i++){
				Level l=matchedLevels.get(i);
				if(l.nGammas()>0 && EnsdfUtil.isGammasConsistent(lev, l, deltaEG,false))
					tempLevels.add(l);
			}
			
			if(tempLevels.size()>0){
				matchedLevels.clear();
				matchedLevels.addAll(tempLevels);
			}

		}
		
		/*
		//debug
		if(lev.ES().contains("25.2E3")) {
		//if(Math.abs(lev.EF()-1902.9)<0.2){
			System.out.println("B In EnsdfGroup 4849: ES="+lev.ES()+" E="+lev.EF()+" JPI="+lev.JPiS()+" matched Adopted size="+matchedLevels.size());
			for(int i=0;i<matchedLevels.size();i++)
				System.out.println("In EnsdfGroup 4851: ES="+lev.ES()+" E="+lev.EF()+" JPI="+lev.JPiS()+" matched Adopted:"+matchedLevels.get(i).ES()+"  "+matchedLevels.get(i).JPiS());
		}
		*/
		
		
		return matchedLevels;			
	}
	
	public Vector<Gamma> findMatchedAdoptedGammas(Gamma gam,Level adoptedLev){
		Vector<Gamma> matchedGammas=new Vector<Gamma>();
		
		int levGroupIndex=findLevelGroupIndexOfAdoptedLevel(adoptedLev);
		
		//if(adoptedLev.ES().contains("1273") && gam.ES().equals("962")) {
		//	System.out.println("In ENSDFGroup line 6114 lev="+adoptedLev.EF()+" JPI= "+adoptedLev.JPiS()+" gam="+gam.ES()
		//	+" levGroupIndex="+levGroupIndex);
		//}
		if(levGroupIndex<0)
			return matchedGammas;
		
		RecordGroup levGroup=levelGroupsV.get(levGroupIndex);
	    for(RecordGroup gamGroup:levGroup.subgroups()) {
	    	if(gamGroup.hasAdoptedRecord()&&gamGroup.recordsV().contains(gam))
	    		matchedGammas.add(gamGroup.getAdoptedRecord());
	    	
	    	/*
	    	//debug
			if(adoptedLev.ES().contains("1273") && gam.ES().equals("962")) {
				System.out.println("In ENSDFGroup line 6127 lev="+adoptedLev.EF()+" JPI= "+adoptedLev.JPiS()+" gam="+gam.ES()
				+" hasAdopted="+gamGroup.hasAdoptedRecord()+" contain this gam="+gamGroup.recordsV().contains(gam));
				
				for(int i=0;i<gamGroup.recordsV().size();i++) {
					Record r=gamGroup.recordsV().get(i);
				    System.out.println("  gam-"+i+"  E="+r.ES()+" dsid="+gamGroup.dsidsV().get(i));
				}
			}
			*/
	    }
		
		/*
	    //debug
		if(adoptedLev.ES().equals("25987") && gam.ES().equals("3374.6")) {
			 System.out.println("In ENSDFGroup line 5216 lev="+adoptedLev.EF()+" JPI= "+adoptedLev.JPiS()+" # of matchedGammas="+matchedGammas.size());
		}
		*/

		return matchedGammas;			
	}
	
	//find the indexes of the groups that contains the input level
	public Vector<Integer> findLevelGroupIndexesOfLevel(Level lev){
		Vector<Integer> indexes=new Vector<Integer>();

		if(!isLevelGrouped)//level not grouped
			return null;
			
		//note after groupLevels(), the adopted level is always the first one in each level group
		//and so firstLevelInGroupV contains all adopted levels and other levels that are not in 
		//adopted from individual datasets
		//First, find a smaller range of groups that could contain the input level
		//NOTE that if using findMatchesByEnergy(entry,recordsV,200), the smaller of 200 and the sum
		//of errors will be used as error range to determine if matching
		//while findMatchesByEnergy(entry.EF,recordsV,200) or findMatchesByEnergy(entry,recordsV,200,true)
		//will forces to use error range=200
		Vector<Level> tempV0=new Vector<Level>();
		tempV0.addAll(firstLevelInGroupV);
		EnsdfUtil.sortRecordsByEnergy(tempV0);
        
		//Vector<Record> tempV=EnsdfUtil.findMatchesByEnergyEntry(lev, tempV0,200,false);
		Vector<Record> tempV=EnsdfUtil.findMatchesByEnergyEntry(lev, tempV0,200,true);
		
		Record closest=null;
		if(Str.isNumeric(lev.ES()))
		    closest=EnsdfUtil.findClosestByEnergyValue(lev,tempV0);
		else
		    closest=EnsdfUtil.findClosestByEnergyEntry(lev,tempV0);
		
		if(closest!=null && !tempV.contains(closest))
			tempV.add(closest);
		
		/*
		//debug
		if(lev.ES().equals("2191.2+x")) {
		//if(lev.ES().contains("x")){
		   System.out.println("@@@@@ In ENSDFGroup line 5273 lev ES="+lev.ES()+" EF="+lev.EF()+" ERPF="+lev.ERPF()+" match size="+tempV.size()+" closest="+closest.ES());
		   if(tempV.size()>0)
			   for(int i=0;i<tempV.size();i++) System.out.println(tempV.get(i).ES());
		   for(Level l:tempV0)
		       System.out.println(" first level in Group:"+l.ES());
		}
		*/
	
		if(tempV==null || tempV.size()==0)
			return indexes;

		//if(Math.abs(lev.EF()-3022)<2){ 
		//	for(Level l:firstLevelInGroupV) System.out.println("  firstL="+l.ES());
		//	for(int i=0;i<temp.size();i++) System.out.println(temp.get(i).ES());
		//}

		
		for(int i=0;i<tempV.size();i++){
			int igroup=firstLevelInGroupV.indexOf(tempV.get(i));
			if(levelGroupsV.get(igroup).recordsV().contains(lev))
				indexes.add(igroup);
			
			
			/*
			//debug
			if(lev.ES().contains("x")) {
			//if(Math.abs(lev.EF()-2953.2)<0.5){	
				System.out.println("ENSDFGroup line 1197 lev="+lev.EF()+" igroup="+igroup+" temp="+temp.size()+"="+temp.get(i).ES()+" "+levelGroupsV.get(igroup).recordsV().contains(lev));
				for(int j=0;j<levelGroupsV.get(igroup).recordsV().size();j++){
					Level l=(Level)levelGroupsV.get(igroup).recordsV().get(j);
					System.out.println("   l="+l.EF()+"  "+l.JPiS()+"  "+levelGroupsV.get(igroup).dsidsV().get(j)+" lev="+lev.ES()+" "+lev.JPiS());
					System.out.println("   l.nbeta="+l.nBetas()+"  lev.nbeta="+lev.nBetas()+" index="+levelGroupsV.get(igroup).recordsV().indexOf(lev));
				}
			}
			*/
		}
		
		return indexes;
	}

	//find the index of the group that contains the input adopted level
	public int findLevelGroupIndexOfAdoptedLevel(Level lev){

		if(lev==null || firstLevelInGroupV==null)//level not grouped
			return -1;
		
		return firstLevelInGroupV.indexOf(lev);

	}
	
	/*
	 * default: interval=1000 (keV), step=50 (keV) (increase 50 every 1000 keV)
	 * base=100 (keV)
	 */
	@SuppressWarnings("unused")
	private float offset(float e,int interval,int step,int base){
		int n=(int)(e/interval)+1;
		float offset=n*step+base;
		
		float r=0.2f;
		if(n==0 && e*r<offset){
			offset=e*r;
		}
		
		return offset;
	}

	private float offset(float e){
		try{
			float r=(float)Math.exp(-e/20)+0.2f;
			float offset=Math.min(r*e, 100);
			return Math.max(offset,2);
		}catch(Exception exp){
			return 0;
		}
	}
	
	//by default, hasSorted=false and recordGroupsV will be sorted in each call
    @SuppressWarnings("unused")
    private <T extends Record> RecordGroup findClosestGroupForRecord(T rec,Vector<RecordGroup> recordGroupsV) {
        return findClosestGroupForRecord(rec,recordGroupsV,false);
    }
	
    /*
     * find possible groups that rec's energy roughly matches with in energy 
     * hasSorted=true if recordGroupsV has been sorted by reference record energy, for which case
     * hasSorted=true should be set to avoid the repeated and possibly time-consuming sorting of recordGroups for each rec
     * 
     */
    @SuppressWarnings("unchecked")
    private <T extends Record> RecordGroup findClosestGroupForRecord(T rec,Vector<RecordGroup> recordGroupsV,boolean hasSorted) {
        Vector<T> refRecordsV=new Vector<T>();
        for(int i=0;i<recordGroupsV.size();i++) {
            RecordGroup group=recordGroupsV.get(i);
            refRecordsV.add(group.getReferenceRecord());
        }
        
        Vector<T> originalRefRecordsV=new Vector<T>();
        originalRefRecordsV.addAll(refRecordsV);
        
        if(!hasSorted)
            EnsdfUtil.sortRecordsByEnergy(refRecordsV);
        
        T closestRec=null;
        
        if(Str.isNumeric(rec.ES()) || rec.isTrueERPF()) {   
            closestRec=(T) EnsdfUtil.findClosestByEnergyValue(rec.ERPF(), refRecordsV);
            
            
            /*
    		//debug
    		if(rec.ES().contains("9447") && rec instanceof Level){ 
    			
    			System.out.println("In EnsdfGroup 5779: ES="+rec.ES()+" ef="+rec.EF()+" erf="+rec.ERF()+" erpf="+rec.ERPF()+" JS="+((Level)rec).JPiS()+" closestRec==null:"+(closestRec==null));
    			if(closestRec!=null)
    			    System.out.println("      closestRec="+closestRec.ES()+"  ef="+closestRec.EF()+" erf="+closestRec.ERF()+" erpf="+closestRec.ERPF()+" igroup="+refRecordsV.indexOf(closestRec));
    			
    			for(int i=0;i<recordGroupsV.size();i++){
    			    RecordGroup g=recordGroupsV.get(i);
    			    if(Math.abs(g.getMeanEnergy()-rec.EF())>50)
    			        continue;
    			    
    				System.out.println("       in group #"+i+"   minE="+g.getMinERecord().EF()+" ref E="+g.getReferenceRecord().ES());
    				for(int j=0;j<g.nRecords();j++){
    					Record r=g.getRecord(j);
    					System.out.println("            record #"+j+"   es="+r.ES()+" ef="+r.EF()+" erf="+r.ERF()+"  erpf="+r.ERPF()+"  js="+((Level)r).JPiS()+" "+g.dsidsV().get(j));
    				}
    			}    			
    		}       
            */
        }else {
            closestRec=(T) EnsdfUtil.findClosestByEnergyEntry(rec, refRecordsV); 
            
            /*
    		//debug
    		if(rec.ES().contains("4829.") && rec instanceof Level){ 
    			
    			System.out.println("@@@In EnsdfGroup 6179: ES="+rec.ES()+" ef="+rec.EF()+" erf="+rec.ERF()+" erpf="+rec.ERPF()+" JS="+((Level)rec).JPiS()+" closestRec==null:"+(closestRec==null));
    			if(closestRec!=null)
    				System.out.println("    closestRec "+closestRec.ES()+" erpf="+closestRec.ERPF());
    			
    			
    			//for(int i=0;i<recordGroupsV.size();i++){
    			//	System.out.println("       in group #"+i+"   minE="+recordGroupsV.get(i).getMinERecord().EF());
    			//	for(int j=0;j<recordGroupsV.get(i).nRecords();j++){
    			//		Record r=recordGroupsV.get(i).getRecord(j);
    			//		System.out.println("            record #"+j+"   es="+r.ES()+" ef="+r.EF()+" erf="+r.ERF()+"  erpf="+r.ERPF()+"  js="+((Level)r).JPiS());
    			//	}
    			//} 
    			
    			 			
    		}  
    		*/
            
            if(closestRec==null)
                closestRec=(T) EnsdfUtil.findClosestByEnergyValue(rec.ERPF(), refRecordsV); 
        }
        
        if(closestRec!=null) {
            int index=originalRefRecordsV.indexOf(closestRec);
            
            /*
            //debug
            if(rec.ES().contains("4829.") && rec instanceof Level){ 
            	System.out.println("In EnsdfGroup 6203: ES="+rec.ES()+" ef="+rec.EF()+" erf="+rec.ERF()+" erpf="+rec.ERPF());
            	System.out.println("    closestRec "+closestRec.ES()+" erpf="+closestRec.ERPF()+" index="+index);
            	RecordGroup g=recordGroupsV.elementAt(index);
            	for(int i=0;i<g.nRecords();i++) {
            		Record r=g.recordsV().get(i);
            		System.out.println("   in group i="+i+" r="+r.ES()+" erpf="+r.ERPF());
            	}
            }
            */
            
            
            return recordGroupsV.elementAt(index);
        }
        
        
        //debug
        //System.out.println(" EnsdfGroup 2945: ES="+rec.ES()+" refs size="+refRecordsV.size());
        //for(Record r:refRecordsV)
        //    System.out.println("   in Group: es="+r.ES());
        
        
        return null;
    }
    
    @SuppressWarnings("unused")
    private <T extends Record> Vector<Integer> findIndexesOfPossibleGroups(T rec,String dsid,String xtag,Vector<RecordGroup> recordGroupsV) {
        return findIndexesOfPossibleGroups(rec,dsid,xtag,recordGroupsV,false);
    }
    
	/*
	 * find possible groups that rec's energy roughly matches with in energy 
	 */
    private <T extends Record> Vector<Integer> findIndexesOfPossibleGroups(T rec,String dsid,String xtag,Vector<RecordGroup> recordGroupsV,boolean hasSorted) {
		Vector<Integer> indexes=new Vector<Integer>();
		int size=recordGroupsV.size();
        if(rec==null || size==0)
        	return indexes;
        
        
        //EG=X
        if(Str.isLetters(rec.ES()) && rec.DES().isEmpty()) {
            for(int i=0;i<size;i++) {
                RecordGroup group=recordGroupsV.get(i);
                if(group.nRecords()==1 && group.getRecord(0).ES().equalsIgnoreCase(rec.ES()))
                    indexes.add(i);
            }
            
            return indexes;
        }else if(!Str.isNumeric(rec.ES()) && (rec instanceof Level)) {    		
        	Level lev=(Level)rec;
            for(int i=0;i<size;i++) {
                RecordGroup group=recordGroupsV.get(i);
                /*
        		//debug
        		if(rec.ES().contains("14206.8") && Math.abs(group.getMinERecord().ERF()-14206)<10){ 
        			System.out.println("In EnsdfGroup line 5448: i="+i+" ES="+rec.ES()+" ERPF="+rec.ERPF()+" nrecs="+group.nRecords()+" size="+size);
        			for(Record r:group.recordsV())
        			    System.out.println("      r="+r.ES()+" ERPF="+r.ERPF());
        		}
        		*/
                if(group.nRecords()>=1) { 		
                	Level l=(Level)group.getRecord(0);
                	if(l.ES().equalsIgnoreCase(lev.ES()) || (l.NES().equals(lev.NES())&&l.NNES().equalsIgnoreCase(lev.NNES()) ))             			
                		indexes.add(i);
                	else if(l.NNES().equalsIgnoreCase(lev.NNES())) {//4289.0+x vs 4289.1+x
                		if(Math.abs(l.ERF()-lev.ERF())<2.0)
                			indexes.add(i);
                	}
                }
            }
            
            if(indexes.size()>0 || !rec.isTrueERPF())
            	return indexes;
        }
        
		float e=rec.ERPF();
		float de=rec.DEF();
		
		float minEF=100000,maxEF=-1;

		float minOffset=100, maxOffset=100;
	    
		Vector<RecordGroup> originalRecordGroupsV=new Vector<RecordGroup>();
		originalRecordGroupsV.addAll(recordGroupsV);
		
		/*
		//debug
		if(dsid.contains("(P,A)"))
		for(RecordGroup levelGroup:recordGroupsV) {
			double EF=levelGroup.getRecord(0).EF();
			if(EF>3970 && EF<4000)
				System.out.println("EnsdfGroup 5531: process dataset "+dsid+"  in group: first level="+EF+" mean="+levelGroup.getMeanEnergy());
		}
		*/
		
		if(!hasSorted)
		    sortRecordGroups(recordGroupsV);
		
		/*
		//debug
		if(dsid.contains("(P,A)"))
		for(RecordGroup levelGroup:recordGroupsV) {
			double EF=levelGroup.getRecord(0).EF();
			if(EF>3970 && EF<4000)
				System.out.println("EnsdfGroup 5542: process dataset "+dsid+"  in group: first level="+EF+" mean="+levelGroup.getMeanEnergy());
		}
		*/
		
	    minEF=recordGroupsV.get(0).getMinERecord().ERPF();
		maxEF=recordGroupsV.get(size-1).getMaxERecord().ERPF();

		minOffset=offset(minEF);
		maxOffset=offset(maxEF);
		if(de>minOffset)
		    minOffset=de;
		if(de>maxOffset)
		    maxOffset=de;
		
		/*
		//debug
		if(rec.ES().contains("14206.8")){ 
			System.out.println("In EnsdfGroup line 2094: ES="+rec.ES()+" EF="+e+" dsid="+dsid+" groupd E range: min="+minEF+" max="+maxEF+"  minOffset="+minOffset+" maxOffset="+maxOffset);
			for(int i=0;i<size;i++){
				System.out.println("       in group #"+i+"   minE="+recordGroupsV.get(i).getMinERecord().EF());
				for(int j=0;j<recordGroupsV.get(i).nRecords();j++){
					Record r=recordGroupsV.get(i).getRecord(j);
					System.out.println("            record #"+j+"   ef="+r.ERPF()+"  es="+r.ES());
				}
			}
		}
		*/
		
		minEF=minEF-minOffset;
		maxEF=maxEF+maxOffset;
		if(e<minEF || e>maxEF) {
			recordGroupsV.clear();
			recordGroupsV.addAll(originalRecordGroupsV);
			
			return indexes;
		}
		
		Vector<RecordGroup> tempRecGroupsV=new Vector<RecordGroup>();
		for(int i=0;i<size;i++) {
		    RecordGroup group=recordGroupsV.get(i);
            if(group.dsidsV().contains(dsid)&&group.xtagsV().contains(xtag)) {
                //there is already a record from the same dataset as rec in the recordGroup and so it requires an exact match
                if(group.recordsV().contains(rec))
                    tempRecGroupsV.add(group);
            }else
                tempRecGroupsV.add(group);
		}
		
      
        
        RecordGroup closestGroup=findClosestGroupForRecord(rec, tempRecGroupsV,true);//set true for tempRecGroupsV is already sorted

        
        /*
        //debug
        if(rec.ES().contains("9447")) {
        //if(Math.abs(rec.EF()-699)<2) {
            System.out.println("  rec ES="+rec.ES()+" dsid="+dsid+" closestGroup E="+closestGroup.recordsV().get(0).ES());
            for(RecordGroup g:tempRecGroupsV)
                System.out.println("  groud E: "+g.recordsV().get(0).ES()+" size="+g.nRecords());            
        }
        */
        
        if(closestGroup==null) {
			recordGroupsV.clear();
			recordGroupsV.addAll(originalRecordGroupsV);
			
            return indexes;
        }
        
		int closestIndex=tempRecGroupsV.indexOf(closestGroup);
		
		indexes.add(originalRecordGroupsV.indexOf(closestGroup));
		  
		size=tempRecGroupsV.size();
		
		if(Str.isNumeric(rec.ES())){
			for(int i=closestIndex+1;i<size;i++){
				minOffset=100;
				maxOffset=100;
				
				//System.out.println("*"+recordGroupsV.get(i).getRecord(0).ES()+"  "+recordGroupsV.get(i).nRecords());
				
				RecordGroup group=tempRecGroupsV.get(i);
				
				//System.out.println("EnsdfGroup 5945: group size="+group.nRecords());
				
				minEF=group.getMinERecord().ERPF();
				maxEF=group.getMaxERecord().ERPF();
				
				minOffset=offset(minEF);
				maxOffset=offset(maxEF);
				
		        if(de>minOffset)
		            minOffset=de;
		        if(de>maxOffset)
		            maxOffset=de;
		        
				minEF=minEF-minOffset;
				maxEF=maxEF+maxOffset;
				
				if(e<minEF || e>maxEF)
				    break;
	        
				indexes.add(originalRecordGroupsV.indexOf(group));
  
				
				/*
				//debug
				if(rec.ES().contains("0+y")){
					System.out.println(" ******     in group #"+i+"  e="+e+" minE="+minEF+" maxE="+maxEF+" isInside="+(e>=minEF && e<=maxEF));
					for(int j=0;j<recordGroupsV.get(i).nRecords();j++)
						System.out.println(" *****           record #"+j+"   e="+recordGroupsV.get(i).getRecord(j).EF());
				}
				*/
				
				
				//debug
				//if(rec.ES().contains("564")) 
				//	System.out.println("In EnsdfGroup line 1146: rec.ES="+rec.ES()+" rec.EF="+rec.EF()+"  refES="+recordGroupsV.get(i).getMinERecord().ES()
				//			+" refEF="+recordGroupsV.get(i).getMinERecord().EF()+" index size="+indexes.size());
			}
			
            for(int i=closestIndex-1;i>=0;i--){
                minOffset=100;
                maxOffset=100;
                
                //System.out.println("*"+recordGroupsV.get(i).getRecord(0).ES()+"  "+recordGroupsV.get(i).nRecords());
                RecordGroup group=tempRecGroupsV.get(i);
                minEF=group.getMinERecord().ERPF();
                maxEF=group.getMaxERecord().ERPF();
                
                minOffset=offset(minEF);
                maxOffset=offset(maxEF);
                
                if(de>minOffset)
                    minOffset=de;
                if(de>maxOffset)
                    maxOffset=de;
                
                minEF=minEF-minOffset;
                maxEF=maxEF+maxOffset;
                
                /*
                //debug
                if(rec.ES().contains("4.56")){
                    System.out.println(" ******EnsdfGroup 2279:  in group #"+i+"  e="+e+" minE="+minEF+" maxE="+maxEF+" minOffset="+minOffset+" maxOffset="+maxOffset+" isInside="+(e>=minEF && e<=maxEF));
                    for(int j=0;j<recordGroupsV.get(i).nRecords();j++)
                        System.out.println(" *****           record #"+j+"   e="+recordGroupsV.get(i).getRecord(j).EF());
                }
                */
                
                
                if(e<minEF || e>maxEF)
                    break;
                  
                indexes.insertElementAt(originalRecordGroupsV.indexOf(group),0);                

            }
		}else if(rec instanceof Level){//rec like 1234.5+X
			Level lev=(Level)rec;
			e=lev.ERPF();//see ENSDF.java for explanation of EF(),ERF(),ERPF()
		
			//System.out.println("  ES="+rec.ES()+" e="+e+" closetIndex="+closestIndex+"  ES="+tempRecGroupsV.get(closestIndex).getRecord(0).ES());
			
			for(int i=closestIndex+1;i<size;i++){
				minOffset=100;
				maxOffset=100;
				
				RecordGroup levelGroup=tempRecGroupsV.get(i);

			    int signal=checkComparableLevelWithGroup(lev, levelGroup);
			    //signal==0, proceed to further checking
                if(signal!=0) {
                    if(signal>0)
                        indexes.insertElementAt(originalRecordGroupsV.indexOf(levelGroup),0);      
                    
                    continue;
                }
                
				minEF=((Level)levelGroup.getMinERecord()).ERPF();
				maxEF=((Level)levelGroup.getMaxERecord()).ERPF();
				
				
				minOffset=offset(minEF);
				maxOffset=offset(maxEF);
				
		        if(de>minOffset)
		            minOffset=de;
		        if(de>maxOffset)
		            maxOffset=de;
		        
				minEF=minEF-minOffset;
				maxEF=maxEF+maxOffset;
				
                if(e<minEF || e>maxEF)
                    break;

                indexes.add(originalRecordGroupsV.indexOf(levelGroup));     
				
				
				/*
				//debug
				if(rec.ES().contains("1233")){
					System.out.println(" ******     in group #"+i+"  e="+e+" minE="+minEF+" maxE="+maxEF+" isInside="+(e>=minEF && e<=maxEF)+" index size="+indexes.size());
					for(int j=0;j<recordGroupsV.get(i).nRecords();j++)
						System.out.println(" *****           record #"+j+"   e="+recordGroupsV.get(i).getRecord(j).EF());
				}
				*/
				
				
				//debug
				//if(rec.ES().contains("564")) 
				//	System.out.println("In EnsdfGroup line 1146: rec.ES="+rec.ES()+" rec.EF="+rec.EF()+"  refES="+recordGroupsV.get(i).getMinERecord().ES()
				//			+" refEF="+recordGroupsV.get(i).getMinERecord().EF()+" index size="+indexes.size());
			}
			
			for(int i=closestIndex-1;i>=0;i--){
                minOffset=100;
                maxOffset=100;
                
                RecordGroup levelGroup=tempRecGroupsV.get(i);

                int signal=checkComparableLevelWithGroup(lev, levelGroup);
                //signal==0, proceed to further checking
                if(signal!=0) {
                    if(signal>0)
                        indexes.insertElementAt(originalRecordGroupsV.indexOf(levelGroup),0);      
                    
                    continue;
                }
                
                minEF=((Level)levelGroup.getMinERecord()).ERPF();
                maxEF=((Level)levelGroup.getMaxERecord()).ERPF();
                
                
                minOffset=offset(minEF);
                maxOffset=offset(maxEF);
                
                if(de>minOffset)
                    minOffset=de;
                if(de>maxOffset)
                    maxOffset=de;
                
                minEF=minEF-minOffset;
                maxEF=maxEF+maxOffset;
                
                if(e<minEF || e>maxEF)
                    break;

                indexes.insertElementAt(originalRecordGroupsV.indexOf(levelGroup),0);	           
			}
		}

		/*
		//debug
		if(rec.ES().contains("699")) {
		//	System.out.println(" ******     e="+e+" minE="+minEF+" maxE="+maxEF+" isInside="+(e>=minEF && e<=maxEF)+" index size="+indexes.size());
            System.out.println("In EnsdfGroup line 2332: ES="+rec.ES()+" EF="+e+" dsid="+dsid+" groupd E range: min="+minEF+" max="+maxEF+"  minOffset="+minOffset+" maxOffset="+maxOffset);
            for(int i=0;i<indexes.size();i++){
                int index=indexes.get(i);
                System.out.println("       in group #"+index+"   minE="+recordGroupsV.get(index).getMinERecord().EF());
                for(int j=0;j<recordGroupsV.get(index).nRecords();j++){
                    Record r=recordGroupsV.get(index).getRecord(j);
                    System.out.println("            record #"+j+"   ef="+r.ERPF()+"  es="+r.ES());
                }
            }
		}
		*/
		recordGroupsV.clear();
		recordGroupsV.addAll(originalRecordGroupsV);
		
		return indexes;
	}
    
    //check if there is any level in level group which is comparable to lev
    //>0 yes
    //=0 no, but need further check
    //<0 no, skip this levelGroup 
    private int checkComparableLevelWithGroup(Level lev,RecordGroup levelGroup) {
        
        int signal=-1;
        
        try{
            
            //if(rec.ES().contains("1233")) System.out.println(" ensdfGroup 2107: ES="+lev.ES()+" rec.isTrueERPF()="+lev.isTrueERPF()+" erf="+lev.ERF()+" nnes="+lev.NNES());
    
            if(!lev.isTrueERPF()) {
                boolean toSkip=false;
                Level comparableLev=null;
                for(int j=0;j<levelGroup.nRecords();j++) {
                    Level l=levelGroup.getRecord(j);
                    
                    //if(rec.ES().contains("1233") && l.ES().contains("1233")) { 
                    //  System.out.println("     ensdfGroup 2114: levelGroup.record#"+j+" ES="+l.ES()+" isTrueERPF()="+l.isTrueERPF()+" erf="+l.ERF()+" nnes="+l.NNES());
                    //  System.out.println("      lev.ERF()==l.ERF(): "+(lev.ERF()==l.ERF())+"  lev.NNES().equals(l.NNES())="+(lev.NNES().equals(l.NNES())));
                    //}
                    
                    if(l.isTrueERPF()) {
                        toSkip=true;
                        break;
                    }else{
                        if( lev.ES().equals(l.ES()) )
                            comparableLev=l;
                        else if(lev.NNES().equals(l.NNES()) && Math.abs(lev.ERF()-l.ERF())<=0.5f )
                            comparableLev=l;
                                
                    }
                }
                
                //if(rec.ES().contains("1233")) 
                //  System.out.println("    toContinue="+toContinue+" comparableLev=null "+(comparableLev==null));  
                
                if(!toSkip && comparableLev!=null) //all records in levelGroup have isTrueERPF=false
                    signal=1;            
                
            }else {
                boolean toSkip=false;
                for(int j=0;j<levelGroup.nRecords();j++) {
                    //if(rec.ES().contains("5505")) 
                    //  System.out.println("     ensdfGroup 2114: levelGroup.record#"+j+" ES="+levelGroup.getRecord(j).ES()+" isTrueERPF()="+levelGroup.getRecord(j).isTrueERPF());
                    
                    if(!levelGroup.getRecord(j).isTrueERPF()) {
                        toSkip=true;
                        break;
                    }
                }
                if(!toSkip)
                    signal=0;//signal for further checking by other function
            }
        }catch(Exception e1){
            e1.printStackTrace();
        } 
        
        return signal;
    }
    
    /*
     * find possible records that could roughly match the given group in energy 
     */
    public <T extends Record> Vector<T> findPossibleRecordsForGroup(Vector<T> recordsV,RecordGroup recordGroup,float deltaE) {
        Vector<T> out=new Vector<T>();
        if(recordGroup==null || recordGroup.nRecords()==0 || recordsV.size()==0)
            return out;
        
        return recordGroup.findPossibleRecordsForGroup(recordsV,deltaE);
    }
    
    /*
     * check if the band b has any level which or whose peer level from different datasets but in the same level group is also in any of the refBandsV
     */
    public boolean isBandOverlapAny(Band band,Vector<Band> refBandsV) {
        try {
            if(band==null || refBandsV.size()==0)
                return false;
            
            if(refBandsV.contains(band))
                return true;
            
            Vector<RecordGroup> levelGroups=findLevelGroupsForBand(band);
            
            for(Band b:refBandsV) {
                Vector<RecordGroup> tempLevelGroups=findLevelGroupsForBand(b);
                for(RecordGroup group:levelGroups) {
                    if(tempLevelGroups.contains(group)) 
                        return true;
                  
                }
            }
            
        }catch(Exception e) {
            
        }
        
        return false;
    }
        
     
    public Vector<RecordGroup> findLevelGroupsForBand(Band band){
        Vector<RecordGroup> levelGroups=new Vector<RecordGroup>();
        for(Level l:band.levels()) {
            //this.findLevelGroupIndexesOfLevel(l);
            for(RecordGroup levGroup:levelGroupsV) {
                if(levGroup.recordsV().contains(l))
                    levelGroups.add(levGroup);
            }
        }
        
        return levelGroups;
    }
        
    /*
     * wrap of record (level/gamma)
     */
    public class RecordWrap{
    	public Record record=null;
    	public String dsid="";
    	public String xtag="";
    	public Level iLevel=null;
    	public Level fLevel=null;
    	
    	RecordWrap(){};
    	
    	RecordWrap(Record r,String d,String x,Level ilev,Level flev){
    		record=r;dsid=d;xtag=x;iLevel=ilev;fLevel=flev;
    	}
    	
    	RecordWrap(Record r,String d,String x){
    		record=r;dsid=d;xtag=x;
    	}
    }
}