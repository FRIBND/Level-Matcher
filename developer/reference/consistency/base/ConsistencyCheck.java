package consistency.base;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Vector;

import ame.base.AMEEntry;
import ame.base.AMERun;
import ame.base.AMETable;
import consistency.base.AverageValuesInComments.CommentENSDF;
import consistency.base.AverageValuesInComments.CommentGamma;
import consistency.base.AverageValuesInComments.CommentLevel;
import ensdfparser.calc.DataPoint;
import ensdfparser.check.RUL;
import ensdfparser.check.SpinParityParser;
import ensdfparser.ensdf.*;
import ensdfparser.ensdf.Record;
import ensdfparser.nds.ensdf.DSIDMatcher;
import ensdfparser.nds.ensdf.EnsdfReferences;
import ensdfparser.nds.ensdf.EnsdfUtil;
import ensdfparser.nds.ensdf.MassChain;
import ensdfparser.nds.latex.Translator;
import ensdfparser.nds.util.Str;

/* main class for checking the consistency with adopted values of
 * values in individual dataset
 * Mar22, 2012, Jun (simple structure;not working yet)
 * Jan05, 2018, Jun (revisited with extensive coding)
 */
public class ConsistencyCheck {
	
	private String message="";//store most recent messages; clear at the end of writeMessage()
	private String XREFWarningMsg="";
	
	private MassChain chain;
	
    private HashMap<String,String> scannedLevelXRefMap;//store actual XRef tags of datasets that have levels matching each Adopted level,
                                                       //obtained by scanning all individual datasets to have their dataset xtag be compared
                                                       //with the given XRef tags in the XREF record of the Adopted level. Only xtags of the
                                                       //datasets having matching levels are kept.
                                                       //key=level.ES(), value=XREF string (consisting of old-tag given in the XREF record)
    
    private HashMap<String,String> dsidXTagMapFromXREF;//store XTag for each individual dataset, given in XREF table in Adopted dataset 
	                                                   //key=dataset DSID, value=XREF tag letter or character (one-character old tag)
	
    private Vector<EnsdfGroup> ensdfGroupsV;
    
	private ENSDF adopted;//adopted dataset in current group being checked
    private EnsdfGroup currentEnsdfGroup;//current group being checked
    private ENSDF currentENSDF;//ENSDF dataset to be checked with adopted
	private String currentENSDFXTag="";//for current ENSDF dataset to be checked
	                                   //Note: it is the original old tag in XREF list in Adopt dataset if existing: upper-case letter or special character
	
	private EnsdfLineFinder currentLineFinder;//store line index of level/gamma/decay/delay in an ENSDF file
	
	//temporary variables
	private Level adoptedLevel,possibleAdoptedLev;
	private Gamma adoptedGamma,possibleAdoptedGam;
	private Vector<Level> matchedAdoptedLevels=new Vector<Level>();//matching adopted levels that are currently found 
		
	//store record names of CURRENT ENSDF under check whose values are taken from Adopted Levels
	//find the names by scanning the general comments in an ENSDF file
	//(recordName,flag): flag is as given in flagged-footnote comment
	//if flag=" ",all values of recordName are from Adopted, unless otherwise noted in comments of that record
	//if flag not " ", only values of recordName with that flag are from Adopted
	private HashMap<String,String> currentFromAdoptedRecordNameMap=new HashMap<String,String>();//(name,flag)

	//store names of all records that have footnote comments and their flags for CURRENT ENSDF
	//note that " " is assigned as the flag in the map for record with no flag in the comments (general comments)
	private HashMap<String,String> currentFootnotedRecordNameMap=new HashMap<String,String>();//(name,flag)
		
	//setting for averaging: use values from record fields or values given in the comments following "AVERAGE OF"
	private LinkedHashMap<ENSDF,EnsdfAverageSetting> ensdfAverageSettingMap=new LinkedHashMap<ENSDF,EnsdfAverageSetting>();
	private LinkedHashMap<ENSDF,Vector<CommentENSDF>> ensdfCommentDataMap=new LinkedHashMap<ENSDF,Vector<CommentENSDF>>();	
	private HashMap<String,ENSDF> labelENSDFMap=new HashMap<String,ENSDF>();
	
	//used to search for matching level/gamma
	private float deltaEL=50;
	private float deltaEG=20;
	
	//DSID of source dataset specified in a general comment for EG and RI
	private DSIDMatcher adoptedEGSourceDSIDs=new DSIDMatcher();
	private DSIDMatcher adoptedRISourceDSIDs=new DSIDMatcher();
	private DSIDMatcher adoptedTISourceDSIDs=new DSIDMatcher();
	private DSIDMatcher adoptedMRSourceDSIDs=new DSIDMatcher();
	
	private String separatorLine=Str.repeat("#", 80);
	
	private HashMap<ENSDF,HashMap<String,String>> mapOfensFromAdoptedRecordNameMap=new HashMap<ENSDF,HashMap<String,String>>();
	private HashMap<ENSDF,HashMap<String,String>> mapOfensFootnoteRecordNameMap=new HashMap<ENSDF,HashMap<String,String>>();
	
    public ConsistencyCheck(){
		scannedLevelXRefMap=new HashMap<String,String>();
		dsidXTagMapFromXREF=new HashMap<String,String>();
		ensdfGroupsV=new Vector<EnsdfGroup>();
	}
	
	public ConsistencyCheck(MassChain data){
		this();
		this.chain=data;	
		
		Vector<ENSDF> ensdfsV=new Vector<ENSDF>();
		
		//assuming each dataset of the same nuclide has a different DSID 
		for(int i=0;i<data.nENSDF();i++) {

		    ENSDF ens=data.getENSDF(i);
		    String dsid=ens.DSId0();
		    String NUCID=ens.nucleus().nameENSDF();
		    String label=NUCID.trim()+"_"+dsid;
		    
            int id=0;
            while(labelENSDFMap.containsKey(label+"_"+id))
                id++;
            
            label=label+"_"+id;
            ens.setID(id);
		        
		    labelENSDFMap.put(label, ens);
		    
		    ensdfsV.add(ens);
		}
		
		parseTargetJPI(ensdfsV);
	}
	
	public void parseTargetJPI(Vector<ENSDF> ensdfsV) {
	    AMERun ameRun=new AMERun();
	    ameRun.loadAME2020();
	    
	    AMETable ame=ameRun.getAME();
	    
	    for(ENSDF ens:ensdfsV) {
	        String targetNUCID=ens.target().nameENSDF();
	        if(!targetNUCID.isEmpty()) {
	            AMEEntry entry=ame.getEntry(targetNUCID);

	            try {
		            String jps=entry.get("JPI").replace("#","").replace("*","").trim();
		            ens.target().setJPS(jps);
	            }catch(Exception e) {
	            	//System.out.println(targetNUCID);
	            }

	        }

	    }
	}
	
	
	public void clearScannedXRefs(){
		scannedLevelXRefMap.clear();
	}
	
	public LinkedHashMap<ENSDF,EnsdfAverageSetting> ensdfAverageSettingMap(){
	    return ensdfAverageSettingMap;
	}
	
    public LinkedHashMap<ENSDF,Vector<CommentENSDF>> ensdfCommentDataMap(){
        return ensdfCommentDataMap;
    }	
    
    public void setEnsdfCommentDataMap(LinkedHashMap<ENSDF,Vector<CommentENSDF>> map) {
        ensdfCommentDataMap=map;
    }
	//check if all values of recordName is from Adopted
	//if false, it means none or not all are from Adopted 
	@SuppressWarnings("unused")
	private boolean hasFromAdoptedGeneralFootnoteOnly(Record rec,String recordName){
		try{
			String name=recordName;			
		    if(name.equals("E"))
		    	name=name+rec.recordLine().charAt(7);
		    
			if(currentFromAdoptedRecordNameMap.get(name).trim().isEmpty())
				return true;
		}catch(Exception e){}
		
		return false;
	}
	
	//check if value of recordName with recordFlags is from Adopted
	//if there is a general footnote or any flagged footnote comment 
	//stating "From Adopted Levels" for recordName, it returns true
	//But note that there are often cases that a general footnote 
	//saying "From Adopted Levels" for JPI and there a separate flagged
	//footnote for individual levels saying something else that is not
	//from Adopted Levels
	private boolean hasFromAdoptedFootnote(Record rec,String recordName){
		try{
			String name=recordName;			
		    if(name.equals("E"))
		    	name=name+rec.recordLine().charAt(7);
		    
			String fromAdoptedFlags=currentFromAdoptedRecordNameMap.get(name).trim();			
			String recordFlags=rec.flag();
			
			//if(recordName.equals("J") && recordFlags.equals("B")){
			//	for(String key:fromAdopted.keySet()) System.out.println(key+"    "+fromAdopted.get(key));
			//	System.out.println(" fromAdoptedFlags="+fromAdoptedFlags+" "+fromAdoptedFlags.isEmpty());
			//}
			
			//if fromAdoptedFlags contains " ", it means recordName is in fromAdoptedRecordNameMap and there is
			//a general comment (no flag) for this kind of record that they are from Adopted
			//if fromAdoptedFlags=null if it is not in fromAdoptedRecordNameMap
			if(fromAdoptedFlags.isEmpty())
				return true;
			
			
			for(int i=0;i<recordFlags.length();i++)
				if(fromAdoptedFlags.contains(recordFlags.substring(i,i+1)))
					return true;
		}catch(Exception e){}
		
		return false;
	}
	
	
	public boolean isRecordFromAdopted(Record rec,String recordName,HashMap<String,String> fromAdoptedRecordNameMap){
		try{
			
			if(rec==null || recordName.isEmpty())
				return false;
			
            //if(Math.abs(rec.EF()-3759)<2) System.out.println("In ConsistencyCheck line 143: E="+rec.ES()+"  recordName="+recordName);

            for(int i=0;i<rec.commentV().size();i++){
				Comment c=rec.commentAt(i);
				
				/*
				//debug
				if(rec.ES().contains("13.26E3")) System.out.println("In ConsistencyCheck line 226: E="+rec.ES()+" c.body="+c.body()
				    +" recordName="+recordName+" "+isCommentIndicateQuotedFromAdopted(c)+" c.body="+c.body());
				*/
				
				if(c.hasHead(recordName)) {
					if(isCommentIndicateQuotedFromAdopted(c))
						return true;
				}			
			}
				    
		    
			String recordFlags=rec.flag();

			//if reaching here, there is no in-line comment stating that record is "from Adopted"
			//then check footnote comments
			
			String name=recordName;			
		    if(name.equals("E"))
		    	name=name+rec.recordLine().charAt(7);
		    
			String fromAdoptedFlags=fromAdoptedRecordNameMap.get(name);//NOTE: DO NOT TRIM, since flag=" " corresponds to a general footnote (no flag) 
			      
            /*
            //debug         
            if(rec.ES().contains("5107.3")){
                for(String key: fromAdoptedRecordNameMap.keySet())
                    System.out.println(" key="+key+fromAdoptedRecordNameMap.get(key));
                
                //System.out.println("E="+rec.EF()+"  recFieldName="+recordName+"  recordFlags="+rec.flag()+" fromAdoptedFlags="+fromAdoptedFlags
                //        +"* hasCommentFor(rec,recordName)="+hasCommentFor(rec,recordName));
            }
            */
			
			if(fromAdoptedFlags==null || fromAdoptedFlags.isEmpty())//no footnote comments containing "from Adopted" 
				return false;
			
			//if(Math.abs(rec.EF()-983)<5 && recordName.equals("T"))
			//	System.out.println("   E="+rec.ES()+" fromAdoptedFlags="+fromAdoptedFlags);
			
			//NOTE that fromAdoptedFlags is not empty (=" " means there is only such a general footnote)
		    boolean hasGeneralFootnote=fromAdoptedFlags.contains(" ");
		    
			//if reaching here, there is flagged footnote or general footnote stating "from Adopted"	
	    	//first check if this record has such flagged footnotes	    
		    if(!fromAdoptedFlags.equals(" ")){
				for(int i=0;i<recordFlags.length();i++){
					if(fromAdoptedFlags.contains(recordFlags.substring(i,i+1)))
						return true;
				}
		    }
		    		
			//reaching here, no such flagged footnotes
	    	//here fromAdoptedFlags contains " ", meaning there is such a general footnote 
	    	//if the record has other flagged comments, it is considered that it is the source/argument for the record,
	    	//in another word, the record is not from adopted, otherwise (has other in-line comments or no comment), 
		    //the record is still considered from adopted as stated in the general footnote
			if(hasGeneralFootnote && !hasFlaggedCommentFor(rec, recordName)){
			    
	            if(hasCommentFor(rec,recordName)) {
	                //there is a general footnote stating "from Adopted" but also in-line comment 
	                //has other in-line comments starting with words "FROM","DEDUCED","PROPOSED".
	                
	                for(int i=0;i<rec.commentV().size();i++) {
	                    Comment c=rec.commentAt(i);
	                    if(!c.hasHead(recordName))
	                        continue;
	                    
	                    String s=c.body().trim().toUpperCase();
	                    while(true) {
	                        int n=s.indexOf(".");
	                        if(n<=0 || n==s.length()-1)
	                            break;
	                        
	                        if(Character.isDigit(s.charAt(n-1)) && Character.isDigit(s.charAt(n+1))) {
	                            s=s.substring(0,n);
	                            continue;
	                        }
	                        
	                        break;
	                    }
	                    String[] lines=s.split("[\\s+;,]+");
	                    s=lines[0].toUpperCase().trim();
	                    if(!s.contains("ADOPTED") && (s.startsWith("FROM") || s.startsWith("DEDUCED") || s.startsWith("PROPOSED") || s.startsWith("VALUE FROM")) )
	                        return false;
	                }

	            }
	            
	            
				return true;
			}
			
			//if reaching here, there is not any in-line comment or any flagged footnote
			//for recordName, then check general footnotes
			
			//if fromAdoptedFlags contains " ", it means recordName is in fromAdoptedRecordNameMap and there is
			//a general comment (no flag) for this kind of record that they are from Adopted
			//if fromAdoptedFlags=null if it is not in fromAdoptedRecordNameMap
			//It means there is no flagged footnote stating "from Adopted" but it doesn't mean there is
			//no other flagged footnotes for such record
			//if(fromAdoptedFlags.isEmpty())
			//	return true;
					
		}catch(Exception e){
			e.printStackTrace();
		}
		
		return false;
	}
	
	public boolean isRecordFromAdopted(Record rec,String recordName,ENSDF ens){
		return isRecordFromAdopted(rec,recordName,findFromAdopted(ens));
	}
	
	private boolean isRecordFromAdopted(Record rec,String recordName){
		return isRecordFromAdopted(rec,recordName,currentFromAdoptedRecordNameMap);
	}
	
	public void setDeltaE(float del,float deg){
		deltaEL=del;
		deltaEG=deg;
		CheckControl.deltaEG=deg;
		CheckControl.deltaEL=del;
		
	}
	public void setCurrentGroup(EnsdfGroup ensdfGroup){
		currentEnsdfGroup=ensdfGroup;
		setAdopted(currentEnsdfGroup.adopted());
	}
	
	private void setAdopted(ENSDF ens){
		adopted=ens;
		clearScannedXRefs();
		
		if(adopted==null)
			return;
		
	    for(int i=0;i<adopted.nLevels();i++){
	    	scannedLevelXRefMap.put(adopted.levelAt(i).ES(), "");
	    }
		
	}
	
	public void setCheckDataset(ENSDF ensdf){
		currentENSDF=ensdf;
		currentENSDFXTag=dsidXTagMapFromXREF.get(currentENSDF.DSId0());
		
		if(currentENSDFXTag==null){
			try{
				int i=currentEnsdfGroup.datasetDSID0sV().indexOf(ensdf.DSId0());
				currentENSDFXTag=currentEnsdfGroup.datasetXTagsV().get(i);
			}catch(Exception e){
				currentENSDFXTag="?";
			}				
		}
		
		findFromAdopted(currentENSDF);
	}
	
    public void parseLineIndex(ENSDF ens){
    	currentLineFinder=new EnsdfLineFinder(ens);
    }
	
	public ENSDF getAdopted(){return adopted;}
	public Vector<Level> getAdoptedLevels(){
		if(adopted==null)
			return null;
		
		return adopted.levelsV();
		
	}

	public Vector<EnsdfGroup> ensdfGroupsV(){return ensdfGroupsV;}
	public MassChain getChain(){return chain;}
	
	///////////////////////////////////////
	// All checking and related functions
	///////////////////////////////////////
	
	public String checkEnsdfGroup(EnsdfGroup group){
		String previousMsg=message;//all messages from previous groups
		clearMessage();
		
		setCurrentGroup(group);
		
	    dsidXTagMapFromXREF=group.dsidXTagMapFromAdopted();

		
		Vector<ENSDF> ensdfV=new Vector<ENSDF>();
		ensdfV.addAll(group.ensdfV());
		ensdfV.remove(group.adopted());
		
        
        if(group.adopted()==null)
        	printToMessage("Warning: No adopted dataset for nuclide="+group.NUCID().trim()+"!\n");
        
		for(int i=0;i<ensdfV.size();i++){
			ENSDF ens=ensdfV.get(i);

			String dsid=ens.DSId0();
			if(dsid.contains("(N,N):RESONANCE") || dsid.contains("N,G):RESONANCE")) {
				//System.out.println("*** skip NUCID="+ens.nucleus().nameENSDF()+" DSID="+dsid);	
				//System.out.println("    All data should be from 2018MuZY and not included in Adopted Levels");	
				
				//check if this resonance dataset is used in Adopted Levels
				boolean toCheck=true;
				if(group.adopted()!=null && group.adopted().DSId0().startsWith("ADOPTED")) {
					boolean isIncluded=false;
					ENSDF adopted=group.adopted();
					for(int j=0;j<adopted.XRefV().size();j++) {
						XRef xref=adopted.xRefAt(j);
						if(xref.DSId0().equals(dsid)) {
							isIncluded=true;
							toCheck=true;
							break;
						}
					}
					
					if(!isIncluded) {
						//check if any comment says "consult (n,|g) dataset for additional..."
						for(Comment c:adopted.comV()){
							if(!c.type().trim().isEmpty() || !c.head().trim().isEmpty())
								continue;
							
							String s=c.body().toUpperCase();
							if(s.contains("SEE") && (s.contains("(N,|G):RESONANCE")||s.contains("(N,N):RESONANCE")) ) {
								toCheck=false;
								break;
							}
							
						}
					}
				}
				if(toCheck)
					checkDataset(ens);
			}else {
				System.out.println("*** checking NUCID="+ens.nucleus().nameENSDF()+" DSID="+dsid);			
				checkDataset(ens);
			}

		}

		
        String datasetMessage=message;//message from checking all individual datasets		
		String adoptedMessage=checkAdopted();

		message=previousMsg+adoptedMessage+datasetMessage;
		
		return adoptedMessage+datasetMessage;
	}
	

	

	//NOTE: 1, called after all individual datasets have been checked
	//      2, the message is handled differently compared to other check functions
	//         since the message from this check will be put at the top of the existing 
	//         messages from checking individual datasets instead of the end. 
	private String checkAdopted(){
		String adoptedMsg="";
		if(adopted==null)
			return "";
	
        //this.parseLineIndex(adopted);
		
		this.setCheckDataset(adopted);

		String previousMsg=message;
		clearMessage();
		
    	int nLevels=adopted.nLevels();
    	int nGammas=-1;
    	Level level;
    	Gamma gamma;
        String tempLevelMsg="",tempPreviousMsg="";
    	
    	checkAdoptedQValue();
    	
    	checkXREFList();
    	
    	checkBands(adopted);
    	
    	for(int j=0;j<nLevels;j++){

    		level=adopted.levelAt(j);		
    		tempLevelMsg=checkAdoptedLevel(level);
    		
    		tempPreviousMsg=message;
    		clearMessage();
    		
    		nGammas=level.nGammas();
    		for(int k=0;k<nGammas;k++){
    			gamma=level.gammaAt(k);
    			checkAdoptedGamma(level,gamma);
    		}	
    		
    		//print level line if no error/warning message for checking level (which means level is not printed) 
    		if(tempLevelMsg.isEmpty() && !message.isEmpty()){
    			String line=level.recordLine();
    			message=line+"\n"+message;
    		}
    		message=tempPreviousMsg+message;
    	}
    	
    	//here, message is from checkAdopted() only
    	if(!message.isEmpty()){
    		String line=separatorLine+"\nIn Adopted dataset of "+adopted.nucleus().nameENSDF().trim();
    		message=line+"\n\n"+message;
    	}

    	adoptedMsg=message;
    	message=previousMsg;
    	
    	return adoptedMsg;
	}
	
	//check adopted Q values with the cited mass evaluation  
	private String checkAdoptedQValue(){
		String currentMsg="";
		String previousMsg=message;	
		clearMessage();

		if(adopted==null)
			return "";
		
		QValue qv=adopted.qv();
		String keyno=qv.QRef().toUpperCase();

		if(!keyno.isEmpty() && !keyno.contains("2021WA16")) {
			String s=qv.DQBMS()+qv.DSNS()+qv.DSPS()+qv.DQAS();
			s=s.trim();
			if(!s.contains("CA")) {
				String line=qv.recordLine();
				String msg="Q values and reference not updated\n";
				printToMessage(55,msg,"E");
				message=line+"\n"+message;
			}

		}
			   
		currentMsg=message;
		message=previousMsg+message;
		return currentMsg;
	}
	
	private String checkAdoptedLevel(Level lev){
		String previousMsg=message;
		String currentMsg="";
		String xrefMsg="";
		
		clearMessage();
		
		checkXREF(lev);
		xrefMsg=message;
		
        checkHalflife(lev);

        checkMoment(lev);

		//check if RI is normalized
		checkGammaRI(lev);
		
		if(!message.isEmpty()){
			//String line=findLevelLine(lev);
			String line=lev.recordLine();
			
			//add XREF line
			if(xrefMsg.length()>0){
				String xrefLine=line.substring(0,5)+"X L XREF="+lev.XREFS();
                line+="\n"+xrefLine;				
			}
			
			message=line+"\n"+message;
			currentMsg=message;
		}
		
		message=previousMsg+message;
		

		return currentMsg;
	}
	
	
	
	//for Adopted levels only
	//check if gamma intensities are normalized (PN=6, strongest has RI=100 and
	//not flagged by ?,*,&)
	private String checkGammaRI(Level lev){
		int nGammas=lev.nGammas();
		if(nGammas==0)
			return "";
		
		String currentMsg="";
		String previousMsg=message;	
		clearMessage();
		
		String msgText="";
		
		boolean hasRI=false;
		boolean normalized=false;
		boolean undivided=false;
		boolean questionable=false;
		boolean hasGoodRI=false;//no question or "&" mark 
		
		int nRI=0;
		for(int i=0;i<nGammas;i++){
			Gamma g=lev.gammaAt(i);
			float RI=(float) g.RID();
			
			if(RI>0){
				hasRI=true;
				nRI++;
			}
			
			char c=g.recordLine().charAt(76);
			boolean hasQMark=(g.q().trim().length()>0);
			if(!hasQMark && c!='&')
				hasGoodRI=true;
			
			if(RI==100){
				normalized=true;
				if(nGammas>1){					
					if(c=='&')
						undivided=true;
	
					questionable=hasQMark;
				}
				
				break;
			}
			
		}
		
		if(nRI>1){
			if(hasRI && !normalized){
				msgText+="Gamma intensity not normalized with strongest=100!\n";			
			}else if(undivided){
				msgText+="Gamma intensity should not be normalized to undivided.\n";
			}else if(questionable && hasGoodRI){
				msgText+="Gamma intensity should not be normalized to questionable.\n";
			}
		}

		
		if(msgText.length()>0){
			printToMessage(21,msgText,"W");
			
			String lines="";
			for(int j=0;j<nGammas;j++)
				lines+=lev.gammaAt(j).recordLine()+"\n";
			
			message=lines+message;
		
		}
		
        currentMsg=message;
		message=previousMsg+message;
		
		return currentMsg;
	}
	
	//check if there are wrong XREF tags for levels in Adopted.
	//it is called after all checks for levels are done.
	@SuppressWarnings("unused")
	private String checkXREF(){	
		Level lev;

		if(adopted==null || scannedLevelXRefMap.size()==0)
			return "";
		
		String currentMsg="";
	
		checkXREFList();

		for(int i=0;i<adopted.nLevels();i++){
			lev=adopted.levelAt(i);
			currentMsg=checkXREF(lev);
		}
		
		return currentMsg;
	}
	
	private String checkXREFList() {
		if(adopted==null || currentEnsdfGroup.nENSDF()<=1)
			return "";
		
		String currentMsg="";
		String previousMsg=message;	
		clearMessage();
		
		String msgText="";
		String msgType="E";
		
		Vector<String> dsid0sV=currentEnsdfGroup.datasetDSID0sV();
		Vector<String> dsidsV=currentEnsdfGroup.datasetDSIDsV();
		String xrefPrefix=adopted.nucleus().nameENSDF()+"  X";
		
		for(XRef xref:adopted.XRefV()) {
			//note that DSId0 of xref might not be the same as DSid0 of an ENSDF dataset
			//but datasetDSID0sV() consists of DSId0 of all ENSDF datasets in a group
			String xrefDSID0=xref.DSId0();
			String xrefDSID=xref.DSId();
			if(dsid0sV.contains(xrefDSID0) || dsidsV.contains(xrefDSID))
				continue;
			//System.out.println(" hello2 "+xrefDSID);
			
			String xtag=xref.oldTag();
			String matchedDSID=findMatchedDSIDinDatasetByXtag(xref);
			
			//System.out.println(" hello3 "+xrefDSID+"  "+matchedDSID);
			
			if(!matchedDSID.isEmpty()) {
		    	previousMsg=message;
		    	clearMessage();
		    	
				msgText+="DSID in XREF list does not match DSID in the dataset:\n";
				msgText+="    "+matchedDSID+"\n";
				msgType="E";	
				
				printToMessage(9,msgText,msgType);
				
				if(!message.isEmpty()){
					String line=xrefPrefix+xtag+xrefDSID;
					message=line+"\n"+message;
					currentMsg+=message;
				}
				
				message=previousMsg+message;
			}
		}
		
		return currentMsg;

	}
	
	@SuppressWarnings("unused")
    private String findMatchedDSID(String dsid,Vector<String> dsidsV) {
		
		try {
			Vector<String> matchedV=new Vector<String>();
			
			//DSID length at least =6, like U(P,X) 
			String pattern=dsid.substring(0,6);
			for(String s:dsidsV) {
			    if(s.equals(dsid))
			        return s;
			    
				if(s.startsWith(pattern))
				    matchedV.add(s);
			}
			
			if(matchedV.size()==0)
			    return "";
			
			DatasetID id0=new DatasetID(dsid);
			for(int i=matchedV.size()-1;i>=0;i--) {
			    if(!id0.isSameDSID(matchedV.get(i)))
			        matchedV.remove(i);
			}
			
		}catch(Exception e) {
			
		}
		
		return "";
	}
	
    @SuppressWarnings("unused")
	private String findMatchedDSID0inDatasetByXtag(XRef xref) {
        
        try {
            ENSDF ens=currentEnsdfGroup.getENSDFByXTag(xref.oldTag());
            
            /*
            System.out.println("ConsistencyCheck 763: oldtag="+xref.oldTag());
            for(ENSDF ens1:currentEnsdfGroup.ensdfV())
            	System.out.println("  DSID="+ens1.DSId0()+" xtag="+currentEnsdfGroup.getXTagInAdopted(ens1));
            */
            
            return ens.DSId0();
                
        }catch(Exception e) {
            
        }
        
        return "";
    }
    
    private String findMatchedDSIDinDatasetByXtag(XRef xref) {
        
        try {
            ENSDF ens=currentEnsdfGroup.getENSDFByXTag(xref.oldTag());
            
            /*
            System.out.println("ConsistencyCheck 763: oldtag="+xref.oldTag());
            for(ENSDF ens1:currentEnsdfGroup.ensdfV())
            	System.out.println("  DSID="+ens1.DSId0()+" xtag="+currentEnsdfGroup.getXTagInAdopted(ens1));
            */
            
            return ens.DSId();
                
        }catch(Exception e) {
            
        }
        
        return "";
    }
    
	//check if there are wrong XREF tags for levels in Adopted.
	//it is called after all checks for levels are done.
	private String checkXREF(Level lev){
		String currentMsg="";
		String previousMsg=message;	
		clearMessage();
		
		String inputXTag;
		
		//XREF given after "XREF=" in Adopted 
		//Note: newXREFS() return all newTag letters(not necessarily letters)
		//      newXREFSFormatted() return all newTag letters including "." for unreferenced dataset 
		//      **in both returned results, letters become lower-case if followed by "(*" and tag after index=25 is two-letter tag, like "AA","AB"
		//      XREFS() return original XREF string after "XREF=" consisting of letters or special characters 
		//          (one-letter or one-character tag), as well as "(",")","*","?", and energy value inside "()" 
		//      XRefsV() return original tags
		//use old Tag here!

		String msgText="";
		String msgType="";
		for(int j=0;j<lev.XRefsV().size();j++){
			inputXTag=lev.XRefsV().get(j);
			ENSDF ens=currentEnsdfGroup.getENSDFByXTag(inputXTag);
			
			String xrefs=lev.XREFS();//XREF string of lev (original XREF string) 
			int index=xrefs.indexOf(inputXTag);
			String noteInBracket=xrefs.substring(index+1).trim();
			int p=noteInBracket.indexOf("(");
			if(p==0){
				p=noteInBracket.indexOf(")");
				if(p<0) {
					msgText+="Unmatched () at tag="+inputXTag+"\n";
					msgType="E";
					noteInBracket="";
				}else if(p==0) {
					msgText+="Empty inside () at tag="+inputXTag+"\n";
					if(!msgType.equals("E"))
						msgType="W";
					noteInBracket="";
				}else{
					noteInBracket=noteInBracket.substring(1, p).trim();
					String s=noteInBracket.replace("*", "").replace("?", "").trim();
					if(!s.isEmpty()) {
						if(!Str.isNumeric(s)) {
							msgText+="Possible wrong input in ("+noteInBracket+") at tag="+inputXTag+"\n";
							if(!msgType.equals("E"))
								msgType="W";
							noteInBracket="";
						}else if(ens!=null){
							Level l=(Level)EnsdfUtil.findClosestByEnergyValue(Float.valueOf(s),ens.levelsV(),20);
		
							if(l==null || !noteInBracket.contains(l.ES())) {
								String temp="";
								temp+="No match for E(level) in ("+noteInBracket+") at tag="+inputXTag+": "+ens.DSId0()+"\n";
								if(l!=null) {
									boolean isGood=false;
									if(l.ES().toUpperCase().contains("E")) {//e.g., ES=1.23E3, but XREF=D(1230)
										String ES=String.valueOf((int)l.EF());
										if(noteInBracket.contains(ES)) {
											isGood=true;
											temp="";
										}
									}
									
									if(!isGood)
										temp+="  closest level is "+l.ES().trim()+"  "+l.DES().trim()+"\n";
									
								}
								
								msgType="E";
								noteInBracket="";
								
								msgText+=temp;
							}
						}
					}
				}
			}else
				noteInBracket="";
						
			//NOTE that scannedLevelXRefMap is obtained by scanning dataset tags of each level in each level groups 
			//which are made by the grouping algorithm based on energies, JPI, gamma transitions, and so on.
			//So the XREF of each level group could be different from that of the corresponding adopted level.
			//That is, the placement of a level given by the presence of its dataset tag in XREF in the Adopted 
			//dataset could be different from what is obtained from the grouping algorithm.
			//So, when non-match occur (e.g., a tag is in Adopted XREF but not in the scannedXREF), it must be
			//checked carefully if it is a wrong placement of the problematic tag or it is just a different placement
			
			String trueXref=scannedLevelXRefMap.get(lev.ES());//actual XREF consisting of original old tags obtained by scanning all individual datasets
			
			/*
			//debug
			if(lev.ES().equals("11157.59")) {
			//if(Math.abs(lev.EF()-13650)<3){
			System.out.println("In ConsistencyCheck line 869: lev="+lev.ES()+" XREFS="+lev.XREFS()+" newXREFS="+lev.newXREFS()+" newXREF2S="+lev.newXREFSFormatted()+" inputXTag="+inputXTag);
			System.out.println("                          trueXref="+trueXref+"     "+lev.XRefsV().size());
			for(String key:scannedLevelXRefMap.keySet())
			    System.out.println("  level="+key+"$  xref="+scannedLevelXRefMap.get(key));
			}
			*/
			
			//dataset represented by the given XTag has this level
			if(trueXref.contains(inputXTag)) { 		
				int n=currentEnsdfGroup.findLevelGroupIndexOfAdoptedLevel(lev);
				
				/*
				if(lev.ES().equals("11157.59")) {
		            System.out.println("In ConsistencyCheck line 879: lev="+lev.ES()+" XREFS="+lev.XREFS()+" newXREFS="+lev.newXREFS()+" newXREF2S="+lev.newXREFSFormatted()+" inputXTag="+inputXTag);
		            System.out.println("       n="+n+"           trueXref="+trueXref+"     "+lev.XRefsV().size());
		         }
				*/
				
				if(n>=0) {
					RecordGroup levelGroup=currentEnsdfGroup.levelGroupsV().get(n);
					Level l=(Level)levelGroup.getRecordByTag(inputXTag);
					if(l!=null) {
						
						//debug
						//System.out.println(" adopted level="+adoptedLevel.ES()+"    tag level="+l.ES());
						
						if(l.q().equals("?") && !noteInBracket.contains("?") && !lev.q().equals("?")) {
							msgText+="? mark at level="+l.ES()+" in "+inputXTag+"="+ens.DSId0()+" but no ? here at its tag="+inputXTag+"\n";
							if(!msgType.equals("E"))
								msgType="W";
						}else if(l.q().equals("S")) {
							if(!lev.q().equals("S")) {
								msgText+="S mark (not observed) at level="+l.ES()+" in "+inputXTag+"="+ens.DSId0()+"\n";
								msgText+="  but its tag="+inputXTag+" is included here as observed. Check the S mark\n";
								msgType="E";
							}

						}else if(!l.q().equals("?") && noteInBracket.contains("?")) {
							msgText+="? mark here at tag="+inputXTag+" but no ? at level="+l.ES()+" in "+inputXTag+"="+ens.DSId0()+"\n";
							if(!msgType.equals("E"))
								msgType="W";
						}else if(l.isPseudo()) {
							msgText+="Pseduo level="+l.ES()+" in "+inputXTag+"="+ens.DSId0()+". Its xtag="+inputXTag+" should not be included in XREF\n";
							msgType="E";
						}else if(!l.isGotoAdopted()) {
							msgText+="level="+l.ES()+" in "+inputXTag+"="+ens.DSId0()+" is commented not to be adopted but its xtag is present here.\n";
							if(!msgType.equals("E"))
								msgType="W";
						}
						
					}
				}
								
				continue;
			}

			
			//if reach here, dataset represented by the given XTag has no such level and this tag could be wrong, or
			//the matching level in that dataset is largely discrepant in energy
			String DSID0=EnsdfUtil.findDSID0ByOldXTag(adopted, inputXTag);
			
			/*
			//debug
			if(Math.abs(lev.EF()-13650)<3){
			System.out.println("In ConsistencyCheck line 902: lev="+lev.ES()+" XREFS="+lev.XREFS()+" newXREFS="+lev.newXREFS()+" newXREF2S="+lev.newXREFSFormatted()+" inputXTag="+inputXTag);
			System.out.println("                          trueXref="+trueXref+"     "+lev.XRefsV().size()+"  DSID0="+DSID0);
			}
			*/
			
			//if the tag is a wrong placement or just a different placement
			
            
			//no matched found with matching energy within default error, 
			//(see isComparableE() for how the error range is determined),
			//the find the one with closest energy
			if(DSID0.isEmpty()){
				msgText+="Wrong XREF tag="+inputXTag+": not exist!\n";
			}else{
				if(ens==null){
					msgText+="No input dataset with XREF tag="+inputXTag+" for DSID="+DSID0+"\n";
					if(!msgType.equals("E"))
						msgType="W";
				}else{
					
					//print a warning message for an Adopted level with non-numeric energy
					if(!Str.isNumeric(lev.ES())){
						//msgText+="Non-numerical level energy is adopted. Check datasets in XREF.\n";
						//msgType="W";
					}else{
						Level l=(Level)EnsdfUtil.findClosestByEnergyEntry(lev,ens.levelsV(),200);
						boolean isCase1=false, isCase2=false;
						
						/*
						//debug
						if(l!=null && lev.ES().equals("4061.47")) {
						//if(l!=null && Math.abs(lev.EF()-4061)<3) {
							System.out.println("ConsistencyCheck 952 #1: this level="+lev.ES()+" matched:  l.ES="+l.ES()+" l.EF="+l.EF()+" l.ERPF="+l.ERPF());
						}
						*/
						
						if(l!=null){
							//a level with close energy exists in the dataset of inputXTag, but may be different in 
							//other way (e.g., JPI) and thus could be a different level 
							isCase1=true;
						}else{
							//no level with close energy (within uncertainty) exists in the dataset of inputXTag, 
							//then continue to look for the closest level in energy for further comparison
							isCase2=true;
							l=(Level)EnsdfUtil.findClosestByEnergyValue(lev.ERPF(),ens.levelsV());
							
							//System.out.println("this level="+lev.ES()+" matched:  l.ES="+l.ES()+" l.EF="+l.EF()+" l.ERPF="+l.ERPF());
						}
						
						/*
						//debug
						if(l!=null && lev.ES().equals("4061.47")) {
						//if(l!=null && Math.abs(lev.EF()-13650)<3) {
							System.out.println("ConsistencyCheck 970 #2: this level="+lev.ES()+" matched:  l.ES="+l.ES()+" l.EF="+l.EF()+" l.ERPF="+l.ERPF()+" case1="+isCase1+" case2="+isCase2);
						}
						*/
						
						//check if there is an energy in the parenthesis following the inputXTag and if 
						//that energy (usually discrepant) is the energy of the closest level
						if(l!=null && noteInBracket.length()>0){
							if(noteInBracket.contains(l.ES())){
								isCase1=false;
								isCase2=false;
							}else if(noteInBracket.contains("*")){
								isCase1=true;
							}
						}
						
						String JPImsg="";
						if(l!=null) {
	                        if(l.JPiS().length()>0) {
	                            JPImsg=String.format(" %-20s",l.JPiS().trim());
	                        }else if(l.altJPiS().length()>0) {
	                            String LS=l.lS();
	                            String targetJPS=ens.target().JPS();
	                            if(LS.length()>0 && targetJPS.length()>0)
	                                JPImsg="   possible JPI="+l.altJPiS()+"   from L="+LS+" and target JPI="+targetJPS;
	                            else if(LS.length()>0)
	                                JPImsg="   possible JPI="+l.altJPiS()+"   from L="+LS+" and target JPI=unknown";
	                            else 
	                                JPImsg="   possible JPI="+l.altJPiS();
	                        }						    
						}

						
						if(isCase1){
							msgText+="XREF tag="+inputXTag+": level possibly not or inconsistent (E/JPI) in "+inputXTag+"="+DSID0+"\n";
							msgText+="  closest level in "+inputXTag+"="+DSID0+"\n";
							msgText+=String.format("  %-10s %2s",l.ES().trim(),l.DES().trim())+JPImsg+"\n";
							
							if(!msgType.equals("E"))
								msgType="W";
						}else if(isCase2){
							if(l==null) {
								msgText+="Wrong XREF tag="+inputXTag+": level not in "+inputXTag+"="+DSID0+"\n";
								msgType="E";
							}else {
								float e1=lev.ERPF();
								float e2=l.ERPF();
								float diff=Math.abs(e1-e2);								
								float limit=EnsdfUtil.findALargeEnergyUncertainty(e1);
								
								if(diff>limit) {
									msgText+="Wrong XREF tag="+inputXTag+": level not in "+inputXTag+"="+DSID0+"\n";
									msgType="E";
								}else {
									msgText+="XREF tag="+inputXTag+": level possibly not or energy discrepant in "+inputXTag+"="+DSID0+"\n";
									if(!msgType.equals("E"))
										msgType="W";
								}
			

								msgText+="  closest level in "+inputXTag+"="+DSID0+"\n";
								msgText+=String.format("  %-10s %2s",l.ES().trim(),l.DES().trim())+JPImsg+"\n";
								
							}
	
						}
						
						/*
						//debug
						if(l!=null && Math.abs(lev.EF()-13650)<3) {
							System.out.println("ConsistencyCheck 1002: this level="+lev.ES()+" matched:  l.ES="+l.ES()+" l.EF="+l.EF()+" l.ERPF="+l.ERPF());
							System.out.println("   msg="+msgText);
						}
						*/
					}					
					//if(lev.XREFS().equals("+"))
					//System.out.println("*****In ConsistencyCheck line 178: lev="+lev.ES()+" XREFS="+lev.XREFS()+" newXREFS="+lev.newXREFS()+" scannedXRef="+trueXref+" inputXTag="+inputXTag+" DSID="+DSID0);				
				}				
			}
		}
		
		if(msgText.length()>0){
			printToMessage(9,msgText,msgType);
            currentMsg=message;			
		}
		
		
		message=previousMsg+message;
		return currentMsg;
	}
	
	private String checkMoment(Level lev) {
		String currentMsg="";
		String previousMsg=message;
		
		clearMessage();		
        		
		Vector<ContRecord> momentRecsV=new Vector<ContRecord>();

		Vector<ContRecord> tempV=null;
		tempV=lev.contRecordsVMap().get("MOMM1");
		if(tempV!=null)
		    momentRecsV.add(tempV.get(0));
		
		tempV=lev.contRecordsVMap().get("MOME2");
		if(tempV!=null)
		    momentRecsV.add(tempV.get(0));
	    
	    int size=momentRecsV.size();
	    if(size==0) {
	    	message=previousMsg;
	    	return "";
	    }
	    
	    String msgText="";
		for(int i=0;i<size;i++) {
			ContRecord rec=momentRecsV.get(i);
			if(rec==null)
				continue;
			
			String name=rec.name();
			String ref=rec.ref().toUpperCase();
			
			boolean toCheck=false;
			String updatedRef="";
			if( name.equals("MOMM1")&&!ref.contains("2019STZV")&&!ref.contains("2020STZV") ) {
				updatedRef="2019STZV or 2020STZV";
				toCheck=true;
			}else if((name.equals("MOME2")&&!ref.contains("2021STZZ"))) {
				updatedRef="2021STZZ";
				toCheck=true;
			}
			
			//System.out.println(" name="+name+" ref="+ref+" txt="+rec.txt()+"  s="+rec.s());
			
			for(int j=0;j<lev.nComments();j++) {
				Comment c=lev.commentAt(j);
				String s=c.body().toUpperCase();
				if(s.contains(updatedRef)) {
					toCheck=false;
					break;
				}
			}
			
			if(toCheck)
				msgText+="Check if "+name+" and reference are updated with "+updatedRef+"\n";
			

		}
		
		if(msgText.length()>0) {
			String line="";
			int n=9,n1=0,n2=0;
			for(int i=0;i<lev.contRecsLineV().size();i++) {
				String temp=lev.contRecsLineV().get(i);
				if((n1=temp.indexOf("MOMM1"))>0 || (n2=temp.indexOf("MOME2"))>0) {
					line+=temp+"\n";
					n=(n1>=0)?n1:n2;
				}
			}
			
			printToMessage(n,msgText,"W");
			message=line+message;
		}
		
		currentMsg=message;
		message=previousMsg+message;

		return currentMsg;
	}
	
	
	private String checkAdoptedGamma(Level lev,Gamma gam){
		String currentMsg="";
		String previousMsg=message;
		clearMessage();
		
		
		checkMULandMR(lev,gam);
		checkBXL(gam);
		
		if(!message.isEmpty()){
			//String line=findGammaLine(lev,gam);
			String line=gam.recordLine();
			message=line+"\n"+message;
			currentMsg=message;
		}
		
		message=previousMsg+message;
		
		return currentMsg;
	}
	
	/*
	 * check NB field of N record for beta or EC decay
	 */
	private String checkNorm(ENSDF ens) {
        if(!ens.DSId().contains("DECAY"))
            return "";
        int n=ens.nBetWI()+ens.nECBPWIB()+ens.nECBPWIE()+ens.nECBPWTI();

        
        if(n==0)
            return "";
        
        Normal norm=ens.norm();
        
        
        String currentMsg="";
        String previousMsg=message;
        clearMessage();
        
        String msgText="";
        

        /*
        //have been moved to checkParent()
        //check if BR is consistent with BR of parent level in Adopted Levels of parent nucleus
		//find Adopted dataset of parent nucleus
		ENSDF parentENSDF=findParent(ens);
	    if(parentENSDF!=null){
        	
        }
        */
        
        if(!norm.BRS().isEmpty() && norm.BRD()<1.0) {
            //note that NB is assumed to be 1.0 if empty
            if(norm.isAssumedNB() && norm.NBBRS().isEmpty()) {
                msgText+="NB field is empty while BR<1.0\n";
                printToMessage(42,msgText,"W");
            }
        }
         
        if(!message.isEmpty()){
            //String line=findLevelLine(lev);
            String line=norm.recordLine();
            
            message=line+"\n"+message;
            currentMsg=message;
        }
        
        message=previousMsg+message;
        
        return currentMsg;	    
	}
	
	private String checkDataset(ENSDF ens){
		
        //this.parseLineIndex(ens);
		
		this.setCheckDataset(ens);

		String currentMsg="";
		String previousMsg=message;
		String prevXREFWarningMsg=XREFWarningMsg;
		String currXREFWarningMsg="";
		clearMessage();
		clearXREFWarningMsg();
		
		if(ens.DSId().contains("DECAY")) {
			checkDecayDSID(ens);
			
    		checkParent(ens);
    		
    		checkNorm(ens);
		}
		
    	int nLevels=ens.nLevels();
    	int nGammas=-1;
    	int nDecay=-1;
    	int nDelay=-1;
    	Level level;
    	Gamma gamma;
    	String tempLevelMsg="",tempPreviousMsg="";

		//System.out.println(" In CheckDataset: line 812: DSID="+ens.DSId0()+" ens.nLevWL="+ens.nLevWL()+"  isEvenEven="+ens.target().isEvenEven()+"  "+EnsdfUtil.parseTargetJPS(ens));

		
    	checkBands(ens);
    	
    	//debug
    	//System.out.println("Checking unplaced gamma");
    	
    	nGammas=ens.nUnpGammas();
		for(int k=0;k<nGammas;k++){
			gamma=ens.unpGammaAt(k);
			checkUnpGamma(gamma);

		}
		

    	//debug
    	//System.out.println("Checking unplaced decay");

    	nDecay=ens.unpDecaysV().size();
		for(int k=0;k<nDecay;k++){
			Decay decay=ens.unpDecaysV().get(k);
			checkUnpDecay(decay);

		}
		
    	nDelay=ens.unpDParticles().size();
		for(int k=0;k<nDelay;k++){
			DParticle delay=ens.unpDParticles().get(k);
			checkUnpDelay(delay);

		}
		

		if(ens.nLevWL()>0 && !ens.target().isEvenEven()) {
			EnsdfUtil.parseTargetJPS(ens);//parse and set target JPS (only for transfer reactions)
		}
		
		/*
		//for neutron capture dataset, skip checking for capture state
		String dsid=ens.DSId0();
		if(dsid.contains("(N,G)") && dsid.contains("E=TH")){
			Level lastLevel=ens.lastLevel();
			if(lastLevel.q().equals("S"))
				nLevels=nLevels-1;
		}
		*/
		
    	//debug
    	//System.out.println("Checking level and gamma");

    	for(int j=0;j<nLevels;j++){

    		level=ens.levelAt(j);
    		
    		/*
    		String nnes=level.NNES();
    		//if(nnes.contains("S(n)")) 
    		//	System.out.println("## j="+j+" es="+nnes+"  Msg="+message);
    		
    		if(nnes.contains("SN") || nnes.contains("SP") || nnes.contains("QA"))
    			continue;
    		*/

    
    		tempLevelMsg=checkLevel(level);

    
    		//System.out.println("&& j="+j+" Msg="+message);
    		
    		if(!XREFWarningMsg.trim().isEmpty()){
    	   		currXREFWarningMsg+=level.recordLine()+"\n"+XREFWarningMsg;    	   		
    	   		
    	   		//System.out.println("****"+level.recordLine()+" \n#"+XREFWarningMsg+"#");
    	   		
    	   		clearXREFWarningMsg();
    		}
    	
    				
    		tempPreviousMsg=message;
    		clearMessage();

    		nDecay=level.DecaysV().size();   		
    		for(int k=0;k<nDecay;k++){
    			Decay decay=level.DecaysV().get(k);
    			checkDecay(level,decay);
    		}
    
    		nDelay=level.nDParticles();
    		for(int k=0;k<nDelay;k++){
    			DParticle delay=level.DParticlesV().get(k);
    			checkDelay(level,delay);
    		}
  
    		nGammas=level.nGammas();
    		for(int k=0;k<nGammas;k++){
    			gamma=level.gammaAt(k);
    			checkGamma(level,gamma);

    		}
    		//print level line if no error/warning message for checking level (which means level is not printed) 
    		if(tempLevelMsg.isEmpty() && !message.isEmpty()){
    			String line=level.recordLine();
    			message=line+"\n"+message;
    		}

    		//System.out.println("&& j="+j+" Msg="+message);
    		
    		message=tempPreviousMsg+message;
			
    	}
   		
    	//System.out.println("##"+ens.DSId());
    	//+" "+message);
    	
		if(!message.isEmpty() || !currXREFWarningMsg.isEmpty()){
			String label=currentENSDFXTag.trim();
			if(label.length()>0) label="X"+label+" ";
			String line="\n"+separatorLine+"\nIn dataset of "+ens.nucleus().nameENSDF().trim()+": "+label+ens.DSId0()+"     XREF Tag="+currentENSDFXTag.trim();

    		
			if(!message.isEmpty())
				message=line+"\n\n"+message;
			if(!currXREFWarningMsg.isEmpty())
				currXREFWarningMsg=line+"\n\n"+currXREFWarningMsg;


		}
		
		currentMsg=message;
		message=previousMsg+message;
		
		XREFWarningMsg=prevXREFWarningMsg+currXREFWarningMsg;
		
		return currentMsg;
	}
	
	@SuppressWarnings("unused")
	private String checkDecayDSID(ENSDF ens) {
	    
	    String currentMsg="";
		String previousMsg="";
		if(!ens.DSId0().contains("DECAY") || ens.nParents()==0)
			return "";
		
		String msgText="";
    	Parent p=ens.parentAt(0);
    	Level parentLevel=p.level();

    	
    	previousMsg=message;
    	clearMessage();
    	    	
    	String dsid=ens.DSId0();
    	if(dsid.contains(" B+ DECAY")) {
    		msgText="EC instead of B+ should be used in DSID\n";
    		printToMessage(15,msgText,"E");	 
    	}
    	
    	//check if T12 in DSID match T12 in parent record
    	/*
    	//the following is checked in checkParent()
    	String t12InDSID="";

    	int n0=dsid.indexOf("DECAY");
    	int n=dsid.indexOf(":", n0);
    	if(n<0)
    		n=dsid.indexOf("(",n0);
    	if(n>0) {
    		t12InDSID=dsid.substring(n+1).replace(")","").trim();
    		String[] temp=t12InDSID.split("[\\s]+");
    		if(temp.length>=2 && Str.isNumeric(temp[0]) && EnsdfUtil.isHalflifeUnit(temp[1])) {
    			t12InDSID=temp[0].trim();
    			if(!t12InDSID.equals(parentLevel.T12S())){
    	    		msgText="Parent T1/2 doesn't match T1/2 in DSID\n";
    	    		
    	    		msgText+="  Parent T1/2="+parentLevel.T12S().trim()+"  T1/2 in DSID="+t12InDSID;

    	    		printToMessage(39,msgText,"E");	    				
    			}
    		}
    		
    	}
    	*/

    	
		if(!message.isEmpty()){
			String line=p.recordLine();
			message=line+"\n"+message;
			currentMsg+=message;
		}
		
		message=previousMsg+message;
			
	    return currentMsg;		
	}
	
	private String checkParent(ENSDF ens){
		//find Adopted dataset of parent nucleus
		ENSDF parentENSDF=findParent(ens);
	    if(parentENSDF==null)
	    	return "";
	    
	    
	    String decayType=ens.decayTypeInDSID();
	    String adoptedQS="",adoptedDQS="";
	    QValue qv=parentENSDF.qv();
	    
	    if(decayType.equals("B-")){
	    	adoptedQS=qv.QBMS().trim();
	    	adoptedDQS=qv.DQBMS().trim();
	    }else if(decayType.equals("EC") || decayType.equals("B+") || decayType.equals("EC+B+")){
	    	
	    	//System.out.println(" "+ens.DSId0()+" parent="+parentENSDF.DSId0());
	    	
	    	if(adopted!=null) {
		    	QValue qv1=adopted.qv();
		    	adoptedQS=qv1.QBMS().trim();
		    	adoptedDQS=qv1.DQBMS().trim();
		    	if(adoptedQS.indexOf("-")==0)
		    		adoptedQS=adoptedQS.substring(1);
		    	else 
		    		adoptedQS="-"+adoptedQS;
	    	}

	    }else if(decayType.equals("A")){
	    	adoptedQS=qv.QAS().trim();
	    	adoptedDQS=qv.DQAS().trim();
	    }else if(decayType.equals("IT")){
	    	//parentENSDF=ens;
	    }
	    
	    String currentMsg="";
		String previousMsg="";
		
		String msgText="",msgType="";
	    for(int i=0;i<ens.nParents();i++){
	    	Parent p=ens.parentAt(i);
	    	Level parentLevel=p.level();
	    	Level adoptedParentLevel=(Level)EnsdfUtil.findClosestMatchLevel(parentLevel, parentENSDF.levelsV(), 2.0f);
	    	
	    	/*
	    	//debug
	    	if(parentLevel.ES().contains("0+x")) {
	    		System.out.println("ConsistencyCheck 1506: dsid="+ens.DSId0()+" parentLevel="+parentLevel.ES()+" EF="+parentLevel.EF()+" ERPF="+parentLevel.ERPF()
	    		  +" parent level in dataset="+parentENSDF.DSId()+"  "+parentENSDF.levelsV().size()+"  "+(adoptedParentLevel==null));
	    		for(Level lev:parentENSDF.levelsV())
	    			System.out.println("      "+lev.ES()+" EF="+lev.EF()+" ERPF="+lev.ERPF());
	    	}
	    	*/
	    	
	    	previousMsg=message;
	    	clearMessage();
	    	
	    	//System.out.println(ens.nucleus().nameENSDF()+" p="+parentLevel.ES()+" "+parentLevel.DES()+"  "+parentENSDF.nucleus().nameENSDF()+(adoptedParentLevel==null));
	    	//System.out.println("                      "+adoptedParentLevel.ES()+"  "+adoptedParentLevel.DES());
    		    	
	    	if(adoptedParentLevel==null) {
	    	    String parentNUCID=parentENSDF.nucleus().nameENSDF().trim();
                msgText="Parent level is not found in Adopted Levels of "+parentNUCID+"\n";
                msgType="E";
                
                Level clev=(Level)EnsdfUtil.findClosestByEnergyValue(parentLevel,parentENSDF.levelsV());
                if(clev!=null){
                    msgText+="\n  closest level in Adopted:\n";
                    msgText+=String.format("  %-10s%2s %-20s%s",clev.ES().trim(),clev.DES().trim(),clev.JPiS().trim(),"XREF="+clev.XREFS());
                }                 

                printToMessage(9,msgText,msgType);
	    	}else { 
	    	    if(!EnsdfUtil.isEqualLevelES(parentLevel, adoptedParentLevel)){
	                //System.out.println("ConsistencyCheck 1416: "+ens.nucleus().nameENSDF()+" p="+parentLevel.ES()+" "+parentLevel.DES()+"  "+parentENSDF.nucleus().nameENSDF()+(adoptedParentLevel==null));
	                //System.out.println("                      "+adoptedParentLevel.ES()+"  "+adoptedParentLevel.DES());

	    	    	msgText="";
	                if(decayType.equals("IT")) {
	                    Level lev=ens.lastLevel();
	                    if(!EnsdfUtil.isEqualLevelES(parentLevel, lev)) {
	                        msgText="Parent level energy doesn't match last level in IT decay\n";
	                        msgType="E";
	                        
		                    msgText+="  Last level="+lev.ES()+"  "+lev.DES();
		                    msgText+="  this level="+parentLevel.ES().trim()+"  "+parentLevel.DES().trim()+"\n";
	                    }
	                }else {
	                    msgText="Parent level energy doesn't match adopted value in "+p.nucleus().nameENSDF().trim()+"\n";
	                    msgType="E";
	                    
		                if(adoptedParentLevel!=null){
		                    msgText+="  Adopted level="+adoptedParentLevel.ES()+"  "+adoptedParentLevel.DES();
		                    msgText+="  this level="+parentLevel.ES().trim()+"  "+parentLevel.DES().trim()+"\n";
		                }
	                }
	                
	                if(msgText.length()>0)
	                	printToMessage(9,msgText,msgType);
	    	    }else if(decayType.equals("IT")) {
                    Level lev=ens.lastLevel();
                    if(!EnsdfUtil.isEqualLevelES(parentLevel, lev)) {
                        msgText="Parent level energy doesn't match last level in IT decay\n";
                        msgType="E";
                        
	                    msgText+="  Last level="+lev.ES()+"  "+lev.DES();
	                    msgText+="  this level="+parentLevel.ES().trim()+"  "+parentLevel.DES().trim()+"\n";
	                    
	                    printToMessage(9,msgText,msgType);
                    }
	    	    }

	            if(!EnsdfUtil.isEqualJPS(adoptedParentLevel.JPiS(), parentLevel.JPiS())){
	                if(decayType.equals("IT"))
	                    msgText="Parent level JPI doesn't match in IT decay\n";
	                else
	                    msgText="Parent JPI doesn't match adopted value in "+p.nucleus().nameENSDF().trim()+"\n";
	                
	                msgText+="  Adopted JPI="+adoptedParentLevel.JPiS().trim()+"  this JPI="+parentLevel.JPiS().trim()+"\n";
	    
	                printToMessage(21,msgText,"E");
	            }
	            
	            if(!EnsdfUtil.isEqualHalflife(parentLevel, adoptedParentLevel)){
	                if(decayType.equals("IT"))
	                    msgText="Parent T1/2 doesn't match in IT decay\n";
	                else
	                    msgText="Parent T1/2 doesn't match adopted value in "+p.nucleus().nameENSDF().trim()+"\n";
	                
	                msgText+="  Adopted T1/2="+adoptedParentLevel.halflife().trim()+"  this T1/2="+parentLevel.halflife().trim();

	                printToMessage(39,msgText,"E");
	            }
	    	}
	    	
	    	
	    	//check if T12 in DSID match T12 in parent record
	    	String t12InDSID="";
	    	String dsid=ens.DSId0();
	    	int n0=dsid.indexOf("DECAY");
	    	int n=dsid.indexOf(":", n0);
	    	if(n<0)
	    		n=dsid.indexOf("(",n0);
	    	if(n>0) {
	    		t12InDSID=dsid.substring(n+1).replace(")","").trim();
	    		String[] temp=t12InDSID.split("[\\s]+");
	    		if(temp.length>=2 && Str.isNumeric(temp[0]) && EnsdfUtil.isHalflifeUnit(temp[1])) {
	    			t12InDSID=temp[0].trim();
	    			if(!t12InDSID.equals(parentLevel.T12S())){
	    	    		msgText="Parent T1/2 doesn't match T1/2 in DSID\n";
	    	    		
	    	    		msgText+="  Parent T1/2="+parentLevel.T12S().trim()+"  T1/2 in DSID="+t12InDSID;

	    	    		printToMessage(39,msgText,"E");	    				
	    			}
	    		}
	    		
	    	}
	    	
	    	//check decay Q-value
	    	String QS=p.QS().trim();
	    	String DQS=p.DQS().trim();
	    	
	    	if(adoptedQS.length()>0 && !EnsdfUtil.isEqualSDS(QS, DQS, adoptedQS, adoptedDQS)){
	    		if(decayType.equals("EC") || decayType.equals("B+"))
	    			msgText="Parent Q-value doesn't match adopted value in "+adopted.nucleus().nameENSDF().trim()+"\n";
	    		else
	    			msgText="Parent Q-value doesn't match adopted value in "+p.nucleus().nameENSDF().trim()+"\n";
	    		
	    		msgText+="  Adopted Q-value="+adoptedQS+"  "+adoptedDQS+"  this Q-value="+QS+"  "+DQS+"\n";
	    		printToMessage(65,msgText,"E");
	    	}
	    	   
	        //check if BR is consistent with BR of parent level in Adopted Levels of parent nucleus
			//find Adopted dataset of parent nucleus		
		    if(adoptedParentLevel!=null){
		    	msgText="";
		    	Normal norm=null;
		    	if(ens.nNorms()>i) 
		    	    norm=ens.normAt(i);
		    	else
		    		norm=ens.norm();
		    	
		    	
		    	if(norm!=null && !norm.BRS().isEmpty()) {
		    		String brs=norm.BRS();
		    		String dbrs=norm.DBRS();
		    		DecayMode dm=adoptedParentLevel.decayMode(decayType);
		    		if(dm==null) {
		    			String adoptedName="";
			    		if(decayType.equals("B+") || decayType.equals("EC")) {
			    			dm=adoptedParentLevel.decayMode("EC+%B+");
			    			adoptedName="EC+%B+";
			    			if(dm==null) {
			    				dm=adoptedParentLevel.decayMode("EC+B+");
			    				adoptedName="EC+B+";
			    			}
			    		}else if(decayType.equals("EC+B+")) {
			    			dm=adoptedParentLevel.decayMode("EC+%B+");
			    			adoptedName="EC+%B+";
			    			if(dm==null) {
			    				dm=adoptedParentLevel.decayMode("EC");
			    				adoptedName="EC";
			    				if(dm==null) {
				    				dm=adoptedParentLevel.decayMode("B+");
				    				adoptedName="B+";
			    				}
			    			}
			    		}
			    		
			    		if(dm!=null && (!"EC,B+,EC+B+".contains(decayType)||!adoptedName.equals("EC+%B+")) ) {
			    			msgText+="Decay Mode name in DSID inconsistent with decay mode in Adopted Levels\n";
			    			msgText+="    Decay mode in DSID:   "+decayType+"\n";
			    			msgText+="    Decay mode in Adopted:"+adoptedName+"\n";
			    			printToMessage(1,msgText,"W");
			    		}
		    		}
		    		
		    		msgText="";
		    		if(dm!=null) {
		    			String adoptedBRS=dm.value();
		    			String adoptedDBRS=dm.unc();
		    			
	    				//note that BR in adopted is in percentage while it is a fraction of 1.0 in decay dataset
	    				
	    				//default nsigma=3, for which value without unc will be rounded to have 3 nsig digits.
	    				//set no limit to keep all digits after multiplying 100
	    				SDS2XDX.setNoMaxNsigmaLimit();
	    				
	    				SDS2XDX sx1=new SDS2XDX(brs,dbrs);
	    				sx1=sx1.multiply(100);
	    				SDS2XDX sx2=new SDS2XDX(adoptedBRS,adoptedDBRS);
	    				sx2=sx2.multiply(1);
	    				
	    				SDS2XDX.resetDefaultMaxNsigma();
	    				
	    				
		    			if(!sx1.isSameNumberAs(sx2)){
			    			msgText+="Decay branching inconsistent with value in Adopted Levels\n";
			    			msgText+="    %BR from N record: "+sx1.s()+" "+sx1.ds()+"\n";
			    			msgText+="    %BR from Adopted:  "+sx2.s()+" "+sx2.ds()+"\n";
			    			printToMessage(1,msgText,"E");	
		    			}		    			
		    		}else {
		    			msgText+="Decay Mode <%"+decayType+"> is missing in Adopted Levels\n";
		    			printToMessage(1,msgText,"E");
		    		}
		    	}
	        }
		    
			if(!message.isEmpty()){
				String line=p.recordLine();
				message=line+"\n"+message;
				currentMsg+=message;
			}
			
			message=previousMsg+message;
	    }
			
	    return currentMsg;
	}
	
	private String checkLevel(Level lev){
		String currentMsg="";
		String previousMsg=message;
		
		//if(!Str.isNumeric(lev.ES()))
		//	return "";

		clearMessage();	

		adoptedLevel=findAdoptedLevel(lev);

		//System.out.println("line 1182 1 lev="+lev.ES()+" adopted="+adoptedLevel.ES()+" message="+message);
		if(lev.isGotoAdopted()) {
			checkEL(lev);
	   		checkJPI(lev);       
			checkHalflife(lev);
			checkLtransfer(lev);
						
		}

		
		if(!message.isEmpty()){
			//String line=findLevelLine(lev);
			String line=lev.recordLine();
			
			message=line+"\n"+message;
			currentMsg=message;
		}

		message=previousMsg+message;
		
	    //System.out.println(" 2 lev="+lev.ES()+" adopted="+adoptedLevel.ES()+" message="+message+" curr message="+currentMsg);
	      
		return currentMsg;
	}
	
	private String checkEL(Level lev) {
	    return checkEnergy(lev);
	}
	
	//check if E(level) is consistent with adopted E(level) if quoted as taken from Adopted
	@SuppressWarnings("unused")
    private String checkEL_old(Level lev){
		String currentMsg="";
		String previousMsg=message;
		
		String msgText="";
		String thisES=lev.ES().trim();
		String thisDES=lev.DES().trim();
		
		if(lev.EF()==0)
			return "";
			      
		clearMessage(); 
	      
		if(!thisES.isEmpty() && adopted!=null && currentENSDF!=adopted){
			boolean isEfromAdopted=isRecordFromAdopted(lev,"E");
			if(adoptedLevel!=null){
					
				String adoptedES=adoptedLevel.ES().trim();
				String adoptedDES=adoptedLevel.DES().trim();
				
				//if(Math.abs(lev.EF()-1131.3)<0.1)
				//	System.out.println(" thisES="+thisES+" DES="+thisDES+" Adopted ES="+adoptedES+" DES="+adoptedDES+" isFromAdopted="+isEfromAdopted);
				
				if(isEfromAdopted) {
					boolean isConsistent=true;
					if(!thisES.equals(adoptedES)) {
						isConsistent=false;
                        //if(Str.isInteger(thisES) && thisDES.isEmpty() && !Str.isInteger(adoptedES)) {//rounded energy value
						if(thisDES.isEmpty() && thisES.length()<adoptedES.length() && !Str.isInteger(adoptedES)) {//rounded energy value
						    int idot=thisES.indexOf(".");
						    int n=0;//number of digits after dot
						    if(idot>0)
						        n=thisES.substring(idot+1).trim().length();
						    
                            String tempES=Str.roundToNDigitsAfterDot(adoptedES,n);
                            if(thisES.equals(tempES))
                                isConsistent=true;  

						}

					}else if(!thisDES.isEmpty() && !thisDES.equals(adoptedDES) )
						isConsistent=false;
					
					if(!isConsistent) {
						msgText="E(level) commented from Adopted but inconsistent\n";
						msgText+="  Adopted E="+(adoptedES+" "+adoptedDES).trim()+" this E="+(thisES+" "+thisDES).trim()+"\n";
						printToMessage(9,msgText,"E");
					}
				}

				
				//check E(level) value in comment as quoted from Adopted Levels
				for(int i=0;i<lev.nComments();i++) {
					Comment c=lev.commentAt(i);
					if(c.head().equals("E") && c.body().toUpperCase().contains("ADOPTED")) {
						String adoptedValueInComment=extractAdoptedValueInComment(c);
						
						//debug
						//System.out.println("ConsistencyCheck line 1084: comment="+c.body()+" adoptedValueInComment="+adoptedValueInComment);
						
						if(!adoptedValueInComment.isEmpty()) {
							String[] out=adoptedValueInComment.split("[\\s]+");
							boolean matched=false, valid=false;
							String s="",ds="";
							if(out.length==2) {
								s=out[0].trim();
								ds=out[1].trim();
							}else if(out.length==1) {
								s=out[0].trim();
							}
								
							if(Str.isNumeric(s)) {
								valid=true;								
								if(!s.equals(adoptedES) || (!ds.isEmpty()&&!ds.equals(adoptedDES)) )
									matched=false;
								else
									matched=true;
								
							}
							

							if(!matched) {
								if(valid) {
									msgText="E(level) quoted in comment as from Adopted is inconsistent\n";
									msgText+="  Adopted E="+(adoptedES+" "+adoptedDES).trim()+" E in comment="+adoptedValueInComment+"\n";
									printToMessage(9,msgText,"E");
								}else {
									msgText="Please check E in comment quoted as from Adopted Levels\n";
									msgText+="  Adopted E="+(adoptedES+" "+adoptedDES).trim()+" comment="+adoptedValueInComment+"\n";
									printToMessage(9,msgText,"E");
								}

							}
						}
					}
				}

			}else if(isEfromAdopted){			
				if(possibleAdoptedLev==null || !possibleAdoptedLev.ES().equals(thisES)) {
					msgText="E(level) commented from Adopted but no such Adopted level\n";
					printToMessage(9,msgText,"E");
				}

			}
		}
		
		
	    currentMsg=message;    
		message=previousMsg+message;
		
        return currentMsg;
	}
	
	
	/*
	 * format JPIs to an uniform format (ordered, converting "TO" or ":"
	 */
	//private String formatJPIS() {
	//	
	//}
	
	//check if JPI is consistent with adopted JPI if available
	private String checkJPI(Level lev){
		
		String currentMsg="";
		String previousMsg=message;
		clearMessage();	
		
		String msgText="";
		
		String thisJPI=lev.JPiS().trim();
		
		boolean isDecay=(currentENSDF.decayTypeInDSID().length()>0);
		
		/*
		//debug
		if(lev.ES().equals("1272")) {
		//if(lev.JPiS().contains("2+") && Math.abs(lev.EF()-80)<1){
		//if(Math.abs(lev.EF()-2393)<1){
			System.out.println("*****"+lev.ES()+"   JPIS="+lev.JPiS()+"  adopted null="+(adopted==null)
					+" adoptedLevel null="+(adoptedLevel==null)+" matchedAdoptedLevels="+matchedAdoptedLevels.size());
			if(adoptedLevel!=null) 
				System.out.println(" adopted JPIS="+adoptedLevel.JPiS()+" isRecordFromAdopted(lev,J)="+isRecordFromAdopted(lev,"J"));
			for(String key:currentFromAdoptedRecordNameMap.keySet()) 
				System.out.println("****"+key+"    "+currentFromAdoptedRecordNameMap.get(key));
		}
		*/
		
		//if JPI is not taken from Adopted or there is no adopted dataset, then nothing to check
		if(adopted!=null){	
			
			boolean isJPIFromAdopted=isRecordFromAdopted(lev,"J");			
			
			if(adoptedLevel!=null){

				Vector<Level> tempLevelsV=new Vector<Level>();
				
				if(matchedAdoptedLevels.size()==1) {
					tempLevelsV.addAll(matchedAdoptedLevels);
				}else if(matchedAdoptedLevels.size()>1) {

					for(int i=0;i<matchedAdoptedLevels.size();i++){
						//debug
						//System.out.println("In ConsistencyCheck 451: dsid="+currentENSDF.DSId0()+" E="+lev.EF()+" JPI="+lev.JPiS()+" matched Adopted:"+matched.get(i).ES()+"  "+matched.get(i).JPiS());
						Level adoptedLev=matchedAdoptedLevels.get(i);
						
						//levelXRef=EnsdfUtil.parseLevelXREF(l);
						//String levelXRef=adoptedLev.XREFS();
						
						//System.out.println("  #### xref="+levelXRef+"  lev="+lev.ES()+" currentENSDFXTag="+currentENSDFXTag+"  l.xref="+l.XREFS());
						
						if(isLevelInXREFOfMatchedAdoptedLevel(lev,adoptedLev)) 
							tempLevelsV.add(adoptedLev);

					}	
					
					
				}else {//should not happen
					//System.out.println("hello################");
				}
				
				if(tempLevelsV.size()==1) {
	
					String adoptedJPI=tempLevelsV.get(0).JPiS();
					
					//debug
					//if(lev.JPiS().contains("5/2") && Math.abs(lev.EF()-180)<1){
					//	System.out.println("*****"+lev.ES()+"   TS="+lev.T12S()+"  lev.JPI="+lev.JPiS()+"  adopted JPI="+adoptedLevel.JPiS()+" isFromAdopted="+hasFromAdoptedFootnote(lev,"J"));
					//	System.out.println("    levelFlags="+lev.flag()+" has adopted="+(adopted==null)+" has adoptedlevel="+(adoptedLevel==null));
					//    System.out.println("    hasCommentFor="+hasCommentFor(lev,"J")+" equal JPI="+EnsdfUtil.isEqualJPS(thisJPI,adoptedJPI));
					//}
					
					if(!EnsdfUtil.isEqualJPS(thisJPI,adoptedJPI)){
						if(isJPIFromAdopted){//if reaching here, there is a general comment (flagged or non-flagged) saying JPI is from Adopted Levels
							if(thisJPI.isEmpty()){
								msgText="JPI commented from Adopted but empty\n";
								msgText+="  Adopted JPI="+adoptedJPI+" this JPI="+thisJPI+"\n";
								printToMessage(21,msgText,"W");
							}else{
								msgText="JPI commented from Adopted but inconsistent\n";
								msgText+="  Adopted JPI="+adoptedJPI+" this JPI="+thisJPI+"\n";
								printToMessage(21,msgText,"E");
							}
						}else if(hasFromAdoptedFootnote(lev,"J") || isDecay){//for cases which has "From Adopted" footnote but also other notes for JPI
							msgText="JPI here inconsistent with adopted\n";
							msgText+="  Adopted JPI="+adoptedJPI+" this JPI="+thisJPI+"\n";
							printToMessage(21,msgText,"W");
							
						}else if(!EnsdfUtil.isOverlapJPI(thisJPI, adoptedJPI)) {
							msgText="JPI here contradicts to adopted. Please check\n";
							msgText+="  Adopted JPI="+adoptedJPI+" this JPI="+thisJPI+"\n";
							printToMessage(21,msgText,"W");
						}
					}
		
					//check JPI in comment as quoted from Adopted Levels
					for(int i=0;i<lev.nComments();i++) {
						Comment c=lev.commentAt(i);
						if(c.head().equals("J") && c.body().toUpperCase().contains("ADOPTED")) {
							String adoptedValueInComment=extractAdoptedValueInComment(c);
							
							//debug
							//System.out.println("ConsistencyCheck line 1084: comment="+c.body()+" adoptedValueInComment="+adoptedValueInComment);
							
							if(!adoptedValueInComment.isEmpty()) {
								String[] out=adoptedValueInComment.split("[\\s]+");
								boolean matched=false, valid=false;
								String s="";
								
								for(int j=0;j<out.length;j++) {
		                            s=out[j].trim();
		                            
		                            String tempS="";
		                            if(!s.trim().isEmpty())
		                                tempS=s.replace("+", "").replace("-", "").replace("(", "").replace(")","").replace("/", "").replace(",", "").trim();
		                            
		                            valid=false;
		                            if(Str.isNumeric(tempS)) {
		                                valid=true;                             
		                                if(!s.equals(adoptedJPI))
		                                    matched=false;
		                                else {
		                                    matched=true;
		                                    break;
		                                }
		                                
		                            }							    
								}
								
								if(!matched) {
									if(valid) {
										msgText="JPI quoted in comment as from Adopted is inconsistent\n";
										msgText+="  Adopted JPI="+adoptedJPI+" JPI in comment="+adoptedValueInComment+"\n";
										printToMessage(21,msgText,"E");
									}else {
										msgText="Please check JPI in comment quoted as from Adopted Levels\n";
										msgText+="  Adopted JPI="+adoptedJPI+" comment="+adoptedValueInComment+"\n";
										printToMessage(21,msgText,"E");
									}

								}
							}
						}
					}					
				}else {//multiple matching levels in Adopted Levels
					if(isJPIFromAdopted || hasFromAdoptedFootnote(lev,"J")) {
						msgText="JPI commented from Adopted but multiple matches found in Adopted:";

						//for(Level l:tempLevelsV)
						//	msgText+="\n"+String.format(" %-10s%2s %-20s%s",l.ES().trim(),l.DES().trim(),l.JPiS().trim(),"XREF="+l.XREFS());
						
						printToMessage(21,msgText,"W");						
					}				
				}

			}else if(isJPIFromAdopted && !lev.q().equals("S")){//if no corresponding adopted level, check if JPI is from somewhere else
				if(!thisJPI.isEmpty()){
					if(possibleAdoptedLev==null || !possibleAdoptedLev.JPiS().equals(thisJPI)) {
						msgText="JPI commented from Adopted but no such Adopted level\n";
						printToMessage(21,msgText,"E");
					}
				}
			}
		}
		
		
	    currentMsg=message;
	    
		message=previousMsg+message;
		
		return currentMsg;
	}
	
	@SuppressWarnings("unused")
	private String checkHalflife(Level lev){
		String currentMsg="";
		String previousMsg=message;
		clearMessage();	
		
		String name="T1/2";
		String msgText="";
		String t12s=lev.halflife().trim();
		if(lev.EF()==0){
			if(t12s.isEmpty() && currentENSDF.DSId0().contains("ADOPTED LEVELS")){
				msgText="No T1/2 for ground state\n";
				printToMessage(39,msgText,"W");
			}
		}else if(lev.T12D()>=1E-7 && (lev.DT12S().length()==0 || lev.DT12S().charAt(0)!='L') && EnsdfUtil.isHalflifeUnit(lev.T12Unit())){
			//metastable state has "1M","2M",..., flag at col78-79 
			
        	if(lev.msS().trim().length()==0){
				msgText="Meta-stable flag missing\n";
				printToMessage(77,msgText,"W");
        	}
        }
        
		if(adopted!=null && currentENSDF!=adopted){

			//boolean isDecay=!currentENSDF.decayTypeInDSID().isEmpty();
			boolean isDecay=!currentENSDF.decayRecordType().isEmpty();
			boolean isPDecay=false;
			boolean isEmptyT=t12s.isEmpty();
			if(isDecay) {
				String s=currentENSDF.decayRecordType();
				if(!"BEA".contains(s.charAt(0)+"") && (s.length()==2&&s.charAt(1)!=' '))
					isPDecay=true;
			}
			
			if(!isEmptyT || (isDecay&&!isPDecay)) {
				
				boolean isTFromAdopted=isRecordFromAdopted(lev,"T");
				
				if(adoptedLevel!=null){
					String thisT=lev.halflife().trim();
					String adoptedT=adoptedLevel.halflife().trim();
					
					if(isEmptyT) {//isDecay=true
						if(!adoptedT.isEmpty() && !adoptedT.toUpperCase().equals("STABLE")) {
							if(isTFromAdopted){
								msgText="T1/2 commented from Adopted but empty\n";
								msgText+="  Adopted T1/2="+adoptedT+" this T1/2="+thisT+"\n";
								printToMessage(39,msgText,"W");
							}else {
								msgText="T1/2 is available from Adopted but empty here\n";
								msgText+="  Adopted T1/2="+adoptedT+" this T1/2="+thisT+"\n";
								printToMessage(39,msgText,"W");
							}							
						}

					}else if(!lev.T12S().equals(adoptedLevel.T12S()) || !lev.T12Unit().equals(adoptedLevel.T12Unit()) || !lev.DT12S().equals(adoptedLevel.DT12S())){
						boolean isSame=false;
						if(lev.DT12S().equals(adoptedLevel.DT12S()) && !lev.T12Unit().equals(adoptedLevel.T12Unit())) {
							if(lev.T12D()==adoptedLevel.T12D())
								isSame=true;
						}
						if(!isSame) {
							if(isTFromAdopted){
								msgText="T1/2 commented from Adopted but inconsistent\n";
								msgText+="  Adopted T1/2="+adoptedT+" this T1/2="+thisT+"\n";
								printToMessage(39,msgText,"E");
							}else if(hasFromAdoptedFootnote(lev,"T") || (isDecay&&!isPDecay) ){
								msgText="T1/2 here inconsistent with adopted\n";
								msgText+="  Adopted T1/2="+adoptedT+" this T1/2="+thisT+"\n";
								printToMessage(39,msgText,"E");
							}
						}


					}
					
					//check T1/2 value in comment as quoted from Adopted Levels
					for(int i=0;i<lev.nComments();i++) {
						Comment c=lev.commentAt(i);
						if(c.head().equals("T") && c.body().toUpperCase().contains("ADOPTED")) {
							String adoptedValueInComment=extractAdoptedValueInComment(c);
							
							//debug
							//System.out.println("ConsistencyCheck line 1084: comment="+c.body()+" adoptedValueInComment="+adoptedValueInComment);
							
							if(!adoptedValueInComment.isEmpty()) {
								String[] out=adoptedValueInComment.split("[\\s]+");
								boolean matched=false, valid=false;
								String s="",ds="",us="";
								if(out.length==3) {
									s=out[0].trim();
									us=out[1].trim();
									ds=out[2].trim();
								}else if(out.length==2) {
									s=out[0].trim();
									us=out[1].trim();
								}
									
								if(Str.isNumeric(s) && EnsdfUtil.isHalflifeUnit(us)) {
									valid=true;		
									boolean tsMatched=s.equals(adoptedLevel.T12S());
									boolean dtsMatched=ds.equals(adoptedLevel.DT12S());
									boolean usMatched=us.equals(adoptedLevel.T12Unit());
									if(tsMatched && dtsMatched && usMatched) {
										matched=true;
									}else if(dtsMatched) {
										matched=false;
										if(!tsMatched && !usMatched) {
											double m1=EnsdfUtil.T12UnitMultiplier(us);
											double m2=EnsdfUtil.T12UnitMultiplier(adoptedLevel.T12Unit());
											double r=m1/m2;
											
											SDS2XDX s2x=new SDS2XDX(s,ds);
											int errorLimit=25;
											int n=0;
											try {
												if(Str.isNumeric(ds))
													n=Integer.parseInt(ds);
												else {
													int p1=ds.indexOf("+");
													int p2=ds.indexOf("-");
													if(p1>=0 && p2>=0) {
														String[] V=ds.split("-");
														p1=Integer.parseInt(V[0]);
														p2=Integer.parseInt(V[1]);
														n=Math.max(p1, p2);
													}
												}
											}catch(NumberFormatException e) {						
											}
									
											if(n>errorLimit)
												errorLimit=n+1;
											
											s2x.setErrorLimit(errorLimit);
											s2x=s2x.multiply((float)r);
											
											if(s2x.s().equals(adoptedLevel.T12S()) && s2x.ds().equals(adoptedLevel.DT12S()))
												matched=true;																															
										}
									}						
								}
								

								if(!matched) {
									if(valid) {
										msgText="T1/2 quoted in comment as from Adopted is inconsistent\n";
										msgText+="  Adopted T1/2="+adoptedT+"   T1/2 in comment="+adoptedValueInComment+"\n";
										printToMessage(39,msgText,"E");
									}else {
										msgText="Please check T1/2 in comment quoted as from Adopted Levels\n";
										msgText+="  Adopted T1/2="+adoptedT+"   comment="+adoptedValueInComment+"\n";
										printToMessage(39,msgText,"E");
									}

								}
							}
						}
					}

				}else if(isTFromAdopted && !isEmptyT){
					if(possibleAdoptedLev==null || !possibleAdoptedLev.halflife().trim().equals(t12s)) {
						msgText="T1/2 commented from Adopted but no such Adopted level\n";
						printToMessage(39,msgText,"E");
					}
				}				
			}

		}
		
		
	    currentMsg=message;    
		message=previousMsg+message;
		
        return currentMsg;
	}
	
	/*
	 * check if the dataset tag of the lev to be checked is in the XREF of a possible 
	 * matched adopted level found previoulsy elsewhere
	 * 
	 * lev: level to be checked
	 * adoptedLev: an possible adopted level that matches lev (found elsewhere)
	 */
	private boolean isLevelInXREFOfMatchedAdoptedLevel(Level lev, Level adoptedLev) {
		String XRef=adoptedLev.XREFS();
		
		if(XRef.contains(currentENSDFXTag)){
			if(XRef.contains(currentENSDFXTag+"(")) {
				int n1=XRef.indexOf(currentENSDFXTag)+2;
				int n2=XRef.indexOf(")",n1);

				if(n2>n1) {
					String s=XRef.substring(n1,n2).replace("*", "").replace("?", "").trim();
					String levES=lev.ES();
					
					if(s.contains("E") && Str.isNumeric(s))//e.g., 3.2E3
						s=""+(int)Float.parseFloat(s);
					
					if(levES.contains("E") && Str.isNumeric(levES))//e.g., 3.2E3
						levES=""+(int)Float.parseFloat(levES);
					
					if(!s.isEmpty() && !s.equals(levES))
						return false;
				}
			}
			
			return true;
		}
		
		return false;
	}
	
	//for even-even target nucleus, single-particle transfer
	private String checkLtransfer(Level lev){
		String currentMsg="";
		for(int i=0;i<matchedAdoptedLevels.size();i++) {
			Level adoptedLev=matchedAdoptedLevels.get(i);
			
			if(isLevelInXREFOfMatchedAdoptedLevel(lev,adoptedLev))
				currentMsg+=checkLtransfer(lev,adoptedLev);		
		}

		return currentMsg;
	}
	
	
	//for even-even target nucleus, single-particle transfer
	private String checkLtransfer(Level lev,Level adoptedLev){
		String LS=lev.lS().replace("(", "").replace(")","");
		
		if(LS.isEmpty())
			return "";
		if(!Str.isNumeric(LS) || adopted==null)
			return "";
		if(adoptedLev==null)
			return "";

		String adoptedLevelXRef=EnsdfUtil.parseLevelXREF(adoptedLev);//level.XREF() return original string following "XREF=", it could be "+" or something like "-(AB)"	

		//System.out.println("hello4 "+lev.ES()+" "+currentENSDF.DSId0()+" XREF="+adoptedLevelXRef+"  currentTag="+currentENSDFXTag);
		
		if(!adoptedLevelXRef.contains(currentENSDFXTag)){				
			return "";
		}
		
		//System.out.println("hello5 "+lev.ES());
		//skip reactions that are not single-particle transfer
		
		int AT=currentENSDF.target().A();
		int A=currentENSDF.nucleus().A();

		//System.out.println("   "+lev.ES()+" L="+lev.lS()+"  adoptedJ="+adoptedLevel.JPiS()+"  At="+AT+"  A="+A+"  target="+currentENSDF.target().nameENSDF()+"  "+currentENSDF.target().isEvenEven());

		int DA=Math.abs(AT-A);		
		int L=-1;
		try{
			L=Integer.parseInt(LS);
		}catch(Exception e){
			return "";
		}

		String DSID=currentENSDF.DSId0();
		DatasetID id=new DatasetID(DSID);
		if(id.beam.isEmpty() || id.ejectile.isEmpty())
			return "";
		
		Nucleus beamNuc=null;
		try {
			beamNuc=new Nucleus(id.beam);
		}catch(Exception e) {			
		}

		if(beamNuc==null || beamNuc.A()<0)
			return "";
		
		if(beamNuc.Z()>3)//not check beam > Li
			return "";
		
		String reaction="("+id.beam+","+id.ejectile+")";
		
		String currentMsg="";
		String previousMsg=message;
		clearMessage();
		
		String msgText="";
		
		String adoptedJS=adoptedLev.JPiS();
		int adoptedParity=EnsdfUtil.parityNumber(adoptedLev);
		
		boolean isEvenEven=currentENSDF.target().isEvenEven();
		
		int thisParity=-1;//+1 for positive parity, -1 for negative, 0 for unknown
		int targetParity=0;

		String[] jsV=EnsdfUtil.parseJPI(adoptedJS);
		if(jsV.length>0) {
			adoptedJS=jsV[0];
			for(int i=1;i<jsV.length;i++)
				adoptedJS+=","+jsV[i];
		}

		
		adoptedJS=adoptedJS.replace("(","").replace(")","").replace("+", "").replace("-","").trim();
		
		//spin of transfered particles
		//final spin J=||L-JT|-jp| to L+JT+jp, where JT is the target spin
		//final PI=PI(target)*(-1)^L*PI(transfered particles)=PI(target)*(-1)^L
		//  (from statistics of all transfer reactions in ENSDF, PI(transfered particles)=+)
		String jpS="";
		String targetJS="";
		if(DA==1)
			jpS="1/2";
		else if(DA==2) {
			//j-transfer=0 for (P,T), (T,P), (3HE,N), (N,3HE)
			//j-transfer=1 for (D,A), (A,D)
			//j-transfer=0 or 1 for (3HE,P),(P,3HE)
			if("(P,T)(T,P)(3HE,N)(N,3HE)".contains(reaction)) {
				jpS="0";
			}else if("(D,A)(A,D)".contains(reaction)) {
				jpS="1";
			}else if("(3HE,P)(P,3HE)".contains(reaction)) {
				jpS="0,1";
			}
		}else if(DA==3) {
			//j-transfer=1/2 for (P,A), (A,P), others are much less, like (6LI,T), (3HE,6HE)
			if("(P,A)(A,P)".contains(reaction)) {
				jpS="1/2";
			}
		}
		
		if(isEvenEven) {//target even-even
			
			//target even-even, 
			//DA=1: j-transfer=1/2, final J=L+/-1/2, PI=(-1)^L
			//DA=2: j-transfer=0 or 1, final J=range  PI=(-1)^L,  e.g., (p,t), (t,p), (p,3He), (3He,n)
			//DA=3: j-transfer=1/2 or others, final J=range, PI=(-1)^L,  e.g., (p,a), (a,p),(3He,6He)
			//DA=others: do nothing

			targetParity=1;
			targetJS="0";
							
		}else {
			//target not even-even, DA=any 
			//DA=1:     j-transfer=1/2, final J=JT vector+ L vector+ 1/2, PI=PI(target)*(-1)^L
			//DA=2:     j-transfer=0, final J=|L-JT| to L+JT; or j-transfer=0,1, final J=JT vector+ L vector+ 1. PI=PI(target)*(-1)^L, JT is target JPI
			//DA=other: j-transfer=unknown, pi-transfer-unknown, final J=unknown, PI=unknown
			//          do nothing
			
			//find target spin and parity from comment (targetJPS set at the beginning of checkDataset if transfer reaction)
			String targetJPS=currentENSDF.target().JPS().replace("(", "").replace(")","").trim();
		
			//System.out.println("hello6 "+lev.ES()+" targetJPS="+targetJPS);

			if(!targetJPS.isEmpty()) {
				if(targetJPS.endsWith("+")) {
					targetParity=+1;
					targetJS=targetJPS.replace("+", "").trim();
				}else if(targetJPS.endsWith("-")) {
					targetParity=-1;
					targetJS=targetJPS.replace("-", "").trim();
				}
				
				boolean goodJS=false;
				if(targetJS.endsWith("/2")) {
					String tempJS=targetJS.replace("/2", "").trim();
					if(Str.isInteger(tempJS)) {
						int tempJ=Integer.parseInt(tempJS);
						if(tempJ%2==1)
							goodJS=true;
					}
				}else if(Str.isNumeric(targetJS)) {
					goodJS=true;
				}
					
				if(!targetJS.isEmpty() && !goodJS) {
					printToMessage(55,"Check target JPI="+currentENSDF.target().JPS()+" given in top comments","W");
					targetJS="";
				}
				
			}		
		}
		
		//System.out.println("hello7 "+lev.ES()+" targetJS="+targetJS);
		
		if(!targetJS.isEmpty() && !jpS.isEmpty()) {
			
			Vector<String> allowedJSV=new Vector<String>();
			int JT=-1,jp=-1;

			//final J=JT L vector+ L vector+ jp=|max(JT,L,jp)-sum(other two)| to JT+L+jp
			
			if(targetJS.equals("0") && L==0 && jpS.equals("0,1")) {//jpS=0,1
				allowedJSV.add("0");
				allowedJSV.add("1");
			}else {
				if(jpS.equals("0,1"))
					jpS="1";
				
				try {
					if(targetJS.contains("/2"))
						JT=Integer.parseInt(targetJS.replace("/2", ""));
					else
						JT=Integer.parseInt(targetJS)*2;
					
					if(jpS.contains("/2"))
						jp=Integer.parseInt(jpS.replace("/2", ""));
					else
						jp=Integer.parseInt(jpS)*2;
					
					int max=Math.max(JT, 2*L);
					max=Math.max(max, jp);
					
					int maxJF=JT+jp+2*L;
					int minJF=Math.abs(max-(maxJF-max));
					if(maxJF%2==0 && minJF%2==0) {
						minJF=minJF/2;
						maxJF=maxJF/2;
						for(int i=minJF;i<=maxJF;i++)
							allowedJSV.add(i+"");
					}else if(maxJF%2==1 && minJF%2==1){
						for(int i=minJF;i<=maxJF;i++) {
							if(i%2==1)
								allowedJSV.add(i+"/2");
						}
					}
				}catch(Exception e) {
					
				}
			}
			
			
			//parity
			thisParity=targetParity*(1-2*(L%2));		

			boolean processSpin=false;
			String firstLine="";
			if(!adoptedJS.isEmpty()) {
				if(allowedJSV.size()>0) 
					processSpin=true;

					
				firstLine="L="+lev.lS()+" inconsistent with JPI="+adoptedLev.JPiS()+" from Adopted Levels for E="+adoptedLev.ES()+"\n";
				
			}else {
				if(allowedJSV.size()>0 && allowedJSV.size()<=3) 
					processSpin=true;
				
				if(adoptedLev.JPiS().contains("+") || adoptedLev.JPiS().contains("-")){
					firstLine="L="+lev.lS()+" inconsistent with JPI="+adoptedLev.JPiS()+" from Adopted Levels for E="+adoptedLev.ES()+"\n";
				}else {//adopted JPS is empty
					firstLine="L="+lev.lS()+" available but empty JPI given in Adopted Levels for E="+adoptedLev.ES()+"\n";
				}
			}

			String tempMsg="";
			if(processSpin) {
				String tempJS=adoptedJS;
				
				/*
				//debug
				if(lev.ES().contains("9808.8")) { 
					System.out.println("In ConsistencyCheck line 2475 lev="+lev.ES()+" adopted JS=*"+tempJS+"* "+currentENSDF.DSId0()
					  +" XREF="+adoptedLevelXRef+"  currentTag="+currentENSDFXTag);
					System.out.println("   line="+adoptedLev.recordLine());
				}
				*/
				
				for(int i=0;i<allowedJSV.size();i++) {				
					tempJS=tempJS.replace(allowedJSV.get(i),"").trim();
				}
				
				//if(lev.ES().contains("9808.8")) System.out.println("ConsistencyCheck line 2481"+tempJS+" "+currentENSDF.DSId0()+" XREF="+adoptedLevelXRef+"  currentTag="+currentENSDFXTag);
				
				if(tempJS.replace(",", "").length()>0) 
					tempMsg="spin";
			}

			if(thisParity!=0 && adoptedParity!=0 && adoptedParity!=thisParity) {
				if(tempMsg.length()>0)
					tempMsg+=" and parity";
				else
					tempMsg="parity";
			}
			
			if(tempMsg.length()>0){
				String thisParityS="";
				String targetParityS="";
				if(thisParity==-1)
					thisParityS="-";
				else if(thisParity==1)
					thisParityS="+";
				
				if(targetParity==-1)
					targetParityS="-";
				else if(targetParity==1)
					targetParityS="+";
				
				msgText=firstLine;
				
				String targetJPS=currentENSDF.target().JPS();
				
				if(tempMsg.contains("spin")) {
					msgText+="   possible JPI from L="+lev.lS()+" and target JPI="+targetJPS+"\n";
					String temp="";
					
					for(int i=0;i<allowedJSV.size();i++)
						temp+=allowedJSV.get(i)+thisParityS+",";
					
					temp=temp.substring(0,temp.length()-1);
					if(lev.lS().contains(",") || Str.isEmbracedByBrackets(lev.lS()) || Str.isEmbracedByBrackets(targetJPS) || targetJPS.contains(","))
						temp="("+temp+")";
					else if(targetJPS.contains(")"+thisParityS)) {	
						temp=temp.replace(thisParityS, "");
						temp="("+temp+")"+thisParityS;
					}else if(targetJPS.indexOf("("+thisParityS)>0 && !thisParityS.isEmpty()) {
						temp=temp.replace(thisParityS, "("+thisParityS+")");
					}
							
					
					msgText+="       "+temp;
				}else if(tempMsg.contains("parity")) {
					if(Str.isEmbracedByBrackets(lev.lS()) || Str.isEmbracedByBrackets(targetJPS) || targetJPS.contains(targetParityS+")"))
						thisParityS="("+thisParityS+")";
					
					msgText+="   parity="+thisParityS+" from L="+lev.lS()+" and target JPI="+targetJPS+"\n";
				}
				
				printToMessage(55,msgText,"W");
			}
		}
			
		
	    currentMsg=message;    
		message=previousMsg+message;	
        return currentMsg;
	}
	
	
	private String checkGamma(Level lev,Gamma gam){
		String currentMsg="";
		String previousMsg=message;
		if(Str.isLetters(gam.ES()))
			return "";
		
		clearMessage();
		
		//check final level
		Level fl=fLevel(gam,currentENSDF);
		if(fl==null)
			printToMessage(9,"No final level found\n","W");
		
		int index=lev.GammasV().indexOf(gam);
		if(index>=0) {
			boolean isGoodOrder=true;
			for(int i=0;i<index;i++) {
				if(lev.GammasV().get(i).EF()>gam.EF()) {
					isGoodOrder=false;
					break;
				}
			}
			if(isGoodOrder) {
				for(int i=index;i<lev.GammasV().size();i++) {
					if(lev.GammasV().get(i).EF()<gam.EF()) {
						isGoodOrder=false;
						break;
					}
				}
			}
			if(!isGoodOrder) {
				printToMessage(9,"Gamma not in order","E");
			}
		}
		adoptedGamma=findAdoptedGamma(lev,gam);
		if(gam.isGotoAdopted()) {
			checkEG(gam);

			if(fl!=null){
				checkMULandMR(lev,gam);
				//checkBXL(gam);
			}
			//debug
			//if(lev.ES().contains("2191.2") && gam.ES().equals("2191.2")) 
			//	System.out.println("In ConsistencyCheck 2539: adopted=null: "+(adopted==null)+" adoptedGamma==null: "+(adoptedGamma==null)+" curr!=adopted:"+(currentENSDF!=adopted));
			
			//checked if different placement in Adopted dataset
			String msgText="";
			if(adopted!=null && gam!=null && adoptedGamma==null && currentENSDF!=adopted) {

				Vector<Gamma> similarGammasV=findEnergyMatchedGammasInAdopted(gam);
	            if(similarGammasV.size()==0) {
	            	similarGammasV=findEnergyMatchedGammasInAdopted(gam,5);
	            }
	            
				if(similarGammasV.size()>0) {
					msgText+="Similar gammas placed differently in Adopted:\n";
					for(int i=0;i<similarGammasV.size();i++) {
						Gamma g=similarGammasV.get(i);
						String egs=g.ES();
						if(!g.DES().isEmpty())
							egs+="("+g.DES()+")";
						
						String els="",jps="";
						try {
							int iLev=g.ILI();	
							Level l=adopted.levelAt(iLev);
							els=l.ES();
							jps=l.JPiS();
						}catch(Exception e) {}
							
						if(!els.isEmpty())
							msgText+="  "+String.format("%-14s from level=%-10s  JPI=%-20s\n", egs,els,jps);
					}			
				    
				}else if(gam.q().isEmpty()){
					msgText+="No matching gamma within 5 keV found in Adopted\n";
				}
				
				if(msgText.length()>0)
					printToMessage(9,msgText,"W");
			}			
		}

		if(!message.isEmpty()){
			//String line=findGammaLine(lev,gam);
			String line=gam.recordLine();
			message=line+"\n"+message;
		}
		
		currentMsg=message;
		message=previousMsg+message;
		return currentMsg;
	}

	private String checkEG(Gamma gam) {
	    return checkEnergy(gam);
	}
	   //check if E(gamma) is consistent with adopted E(gamma) if quoted as taken from Adopted
    @SuppressWarnings("unchecked")
    private <T extends Record> String checkEnergy(T rec){
        String currentMsg="";
        String previousMsg=message;
        
        String msgText="";
        String thisES=rec.ES().trim();
        String thisDES=rec.DES().trim();
        
        if(rec.EF()==0)
            return "";
           
        T adoptedRec=null;
        String name="";
        boolean isEqualES=false;
        if(rec instanceof Level) {
            adoptedRec=(T)adoptedLevel;
            name="level";
            
            Level l1=null,l2=null;
            try {
                l1=(Level)rec;
                l2=(Level)adoptedLevel;
                if(!l1.NNES().equals(l2.NNES()))
                	return "";
                
                if(l1.NNES().length()>0 && l1.NES().isEmpty()!=l2.NES().isEmpty())
                	return "";
            }catch(Exception e) {
            	//e.printStackTrace();
            }

            isEqualES=EnsdfUtil.isEqualLevelES(l1,l2);
            
        }else if(rec instanceof Gamma) {
            adoptedRec=(T)adoptedGamma;
            name="gamma";
            
            isEqualES=EnsdfUtil.isEqualES(rec, adoptedRec);
        }else
            return "";
        
        
        clearMessage(); 
        if(!thisES.isEmpty() && adopted!=null && currentENSDF!=adopted){
            boolean isEfromAdopted=isRecordFromAdopted(rec,"E");
            if(adoptedRec!=null){
                    
                String adoptedES=adoptedRec.ES().trim();
                String adoptedDES=adoptedRec.DES().trim();
                
                /*
                if(rec.ES().equals("0.0+x"))
                //if(rec.ES().contains("13.26E3"))
                  System.out.println("ConsistencyCheck 2631: thisES="+thisES+" DES="+thisDES
                		  +" Adopted ES="+adoptedES+" DES="+adoptedDES+" isFromAdopted="+isEfromAdopted+"  line="+adoptedRec.recordLine());
                */
                
                if(isEfromAdopted) {
                    boolean isConsistent=true;
                    if(!thisES.equals(adoptedES)) {
                        isConsistent=false;
                        
                        if(!thisDES.isEmpty() && !thisDES.equals(adoptedDES) )
                            isConsistent=false;
                        else {
                            //if(Str.isInteger(thisES) && thisDES.isEmpty() && !Str.isInteger(adoptedES)) {//rounded energy value
                            if(thisDES.isEmpty() && thisES.length()<adoptedES.length() && !Str.isInteger(adoptedES)) {//rounded energy value
                                int idot=thisES.indexOf(".");
                                int n=0;//number of digits after dot
                                if(idot>0)
                                    n=thisES.substring(idot+1).trim().length();
                                
                                String tempES=Str.roundToNDigitsAfterDot(adoptedES,n);
                                if(thisES.equals(tempES))
                                    isConsistent=true;  

                            }else if(thisDES.equals(adoptedDES) && isEqualES) {
                            	isConsistent=true;
                            }
                        }
                    }
                    
                    if(!isConsistent) {
                        msgText="E("+name+") commented from Adopted but inconsistent\n";
                        msgText+="  Adopted E="+(adoptedES+" "+adoptedDES).trim()+" this E="+(thisES+" "+thisDES).trim()+"\n";
                        printToMessage(9,msgText,"E");
                    }
                }

                
                //check E(level) value in comment as quoted from Adopted Levels
                for(int i=0;i<rec.nComments();i++) {
                    Comment c=rec.commentAt(i);
                    if(c.head().equals("E") && c.body().toUpperCase().contains("ADOPTED")) {
                        String adoptedValueInComment=extractAdoptedValueInComment(c);
                        
                        //debug
                        //System.out.println("ConsistencyCheck line 1084: comment="+c.body()+" adoptedValueInComment="+adoptedValueInComment);
                        
                        if(!adoptedValueInComment.isEmpty()) {
                            String[] out=adoptedValueInComment.split("[;,\\s]+");
                            boolean matched=false, valid=false;
                            String s="",ds="";
                            if(out.length==2) {
                                s=out[0].trim();
                                ds=out[1].trim();
                            }else if(out.length==1) {
                                s=out[0].trim();
                            }else if(out.length==3) {
                            	s=out[0].trim();
                            	ds=out[2].trim();
                            }
                              
                            ds=ds.replace("{I","").replace("}", "").trim();
                            if(!ds.isEmpty() && !Str.isNumeric(ds.replace("+", "").replace("-", ""))) {
                            	if(ds.length()!=2 || !"LGA".contains(ds.charAt(0)+""))
                            		ds="";
                            }
                            
                            if(Str.isNumeric(s)) {
                                valid=true;                             
                                if(!s.equals(adoptedES)) {
                                    matched=false;
                                	if(ds.isEmpty()) {
                                        int idot=s.indexOf(".");
                                        int n=0;//number of digits after dot
                                        if(idot>0 && idot<thisES.length()-1)
                                            n=thisES.substring(idot+1).trim().length();
                                        
                                        String tempES=Str.roundToNDigitsAfterDot(adoptedES,n);	
                                        if(s.equals(tempES))
                                        	matched=true;
                                	}
           
                                }else if(!ds.isEmpty()&&!ds.equals(adoptedDES))
                                	matched=false;
                                else
                                    matched=true;
                                
                            }
                            

                            if(!matched) {
                                if(valid) {
                                    msgText="E("+name+") in comment quoted as from Adopted is inconsistent\n";
                                    msgText+="  Adopted E="+(adoptedES+" "+adoptedDES).trim()+"  value in comment="+adoptedValueInComment+"\n";
                                    printToMessage(9,msgText,"E");
                                }else {
                                    msgText="Please check E in comment quoted as from Adopted "+name.toUpperCase().charAt(0)+name.substring(1)+"\n";
                                    msgText+="  Adopted E="+(adoptedES+" "+adoptedDES).trim()+"  value in comment="+adoptedValueInComment+"\n";
                                    printToMessage(9,msgText,"W");
                                }

                            }
                        }
                    }
                }

            }else if(isEfromAdopted){
            	boolean isGood=true;
            	if(name.equals("level")) {
    				if(possibleAdoptedLev==null || !possibleAdoptedLev.ES().equals(thisES) || !possibleAdoptedLev.DES().equals(thisDES)) {
    					isGood=false;
    				}
            	}else {
    				if(possibleAdoptedGam==null  || !possibleAdoptedGam.ES().equals(thisES) || !possibleAdoptedGam.DES().equals(thisDES)) {
    					isGood=false;
    				}
            	}

				if(!isGood) {
	                msgText="E("+name+") commented from Adopted but no such Adopted "+name+"\n";
	                printToMessage(9,msgText,"E");
				}

            }
        }
        
        
        currentMsg=message;    
        message=previousMsg+message;
        
        return currentMsg;
    }
    
	private String checkMULandMR(Level lev,Gamma gam){
		if(!lev.GammasV().contains(gam))
			return "";
		
				
		String mul=gam.MS().trim();
		
		Level il=lev;
		Level fl=fLevel(gam,currentENSDF);
       
		if(fl==null)
			return "";
		

		float JI=EnsdfUtil.j(il);//return -1 if non-numeric JPIS

		float JF=EnsdfUtil.j(fl);

		int PI=EnsdfUtil.parityNumber(il);//1 for parity='+',-1 for '-', 0 otherwise

		int PF=EnsdfUtil.parityNumber(fl);

		
		String JIS=il.JPiS().trim();
		String JFS=fl.JPiS().trim();  
				
		
		int DP=PI*PF;//parity change, +1 for no, -1 for yes, 0 for unknown
		int DJ=-1;
		if(JI>=0 && JF>=0)
			DJ=(int)(Math.abs(JI-JF)+0.01);
				;
	    //int[] DJs=EnsdfUtil.findMinAndMaxDJ(JFS, JIS);
	    //int minDJ=DJs[0];
	    //int maxDJ=DJs[1];
	    

		Vector<String> expectedMULsV=EnsdfUtil.findExpectedMULs(JFS, JIS);
	    String[] thisMULs=EnsdfUtil.parseMUL(mul);
	    
	    String msgText="";
		String currentMsg="";
		String previousMsg=message;
		clearMessage();
		
		/*
		//debug
		if(gam.ES().equals("5288.8")) {
		//if(Math.abs(gam.EF()-68.7)<1 && thisMULs.length>0){
		   System.out.println(lev.ES()+ " g="+gam.ES()+"  "+JIS+"  "+JFS+"  DJ="+DJ+"  MUL="+mul+"  "+thisMULs.length+"  "+expectedMULsV.contains(thisMULs[0]));
		   for(String s:expectedMULsV)
			   System.out.println("   *****"+s);
	    }
	    */
		
		String msgType="E";
		
		if(mul.contains("E0")){
			//msgText="check calculated B(E0) in continuation record\n";
			msgText="B(E0) should be from experimental data not BrIcc code.";
			msgType="W";
			for(int i=0;i<gam.nContRecordLines();i++){
				
				//System.out.println(gam.contRecordLineAt(i));
				String line=gam.contRecordLineAt(i);
				int index=gam.contRecordLineAt(i).indexOf("BE0");
				char type=line.charAt(5);
				if(index>0 && (type=='S'||type=='B')){
					message=gam.contRecordLineAt(i)+"\n"+message;				
					printToMessage(index,msgText,msgType);
					break;
				}
			}
		}

		//if MUL is not taken from Adopted or there is no adopted dataset, skip this check
		if(adopted!=null && currentENSDF!=adopted){
						
			boolean isMULFromAdopted=isRecordFromAdopted(gam,"M");
			if(adoptedGamma!=null){
				String thisMUL=gam.MS().trim().replace("E2+M1","M1+E2").replace("M2+E1","E1+M2").replace("M3+E2","E2+M3").replaceAll("E3+M2", "M2+E3");
				String adoptedMUL=adoptedGamma.MS().trim().replace("E2+M1","M1+E2").replace("M2+E1","E1+M2").replace("M3+E2","E2+M3").replaceAll("E3+M2", "M2+E3");
				thisMUL=thisMUL.replace("E2,M1", "M1,E2");
				adoptedMUL=adoptedMUL.replace("E2,M1", "M1,E2");
				
				if(!adoptedMUL.contains("[") && !thisMUL.contains("[") && !thisMUL.equals(adoptedMUL)){
					if(isMULFromAdopted){//if reaching here, there is a general comment (flagged or non-flagged) saying JPI is from Adopted Levels
						if(!thisMUL.isEmpty()){
							msgText="Mult commented from Adopted but inconsistent\n";
							msgType="E";
						}else{
							msgText="Mult commented from Adopted but not given\n";
							msgType="W";
						}
						
						msgText+="  Adopted Mult="+adoptedMUL+" this Mult="+thisMUL+"\n";
						printToMessage(31,msgText,msgType);
					}else if(hasFromAdoptedFootnote(gam,"M")){
						msgText="Mult here inconsistent with adopted\n";
						msgType="W";
						msgText+="  Adopted Mult="+adoptedMUL+" this Mult="+thisMUL+"\n";
						printToMessage(31,msgText,msgType);
					}
				}
				
				//check MUL in comment as quoted from Adopted Levels
				for(int i=0;i<gam.nComments();i++) {
					Comment c=gam.commentAt(i);
					if(c.head().equals("M") && c.body().toUpperCase().contains("ADOPTED")) {
						String adoptedValueInComment=extractAdoptedValueInComment(c);
						
						//debug
						//System.out.println("ConsistencyCheck line 1084: comment="+c.body()+" adoptedValueInComment="+adoptedValueInComment);
						
						if(!adoptedValueInComment.isEmpty()) {
							String[] out=adoptedValueInComment.split("[\\s]+");
							boolean matched=false, valid=false;
							String s="";
							if(out.length==1) 
								s=out[0].trim();
							
							String tempS="";
							if(!s.trim().isEmpty()) {
								tempS=s.replace("M", "").replace("E", "").replace("(", "").replace(")","").replace("+", "");
								tempS=tempS.replace("D", "").replace("Q", "").replace("O", "").trim();
								
								if(Str.isNumeric(tempS) || tempS.isEmpty()) {
									valid=true;								
									if(!s.equals(adoptedMUL))
										matched=false;
									else
										matched=true;
									
								}
							}
							

							if(!matched) {
								if(valid) {
									msgText="MULT quoted in comment as from Adopted is inconsistent\n";
									msgText+="  Adopted MULT="+adoptedMUL+" MULT in comment="+adoptedValueInComment+"\n";
									printToMessage(31,msgText,"E");
								}else {
									msgText="Please check MULT in comment quoted as from Adopted\n";
									msgText+="  Adopted MULT="+adoptedMUL+" comment="+adoptedValueInComment+"\n";
									printToMessage(31,msgText,"E");
								}

							}
						}
					}
				}
			}else{//if no corresponding adopted gamma, check if MUL is from somewhere else
				String MUL=gam.MS();
				if(!MUL.isEmpty() && !gam.q().equals("S") && !MUL.startsWith("[") ){
					if(isMULFromAdopted || hasFromAdoptedFootnote(gam,"M")) {
	    				if(possibleAdoptedGam==null  || !possibleAdoptedGam.MS().equals(mul)) {
							msgText="Mult commented from Adopted but no such Adopted gamma\n";
							printToMessage(31,msgText,"E");
	    				}

					}
				}
			}

		}

		/*
		if(gam.ES().equals("5288.8")) {
		//if(Math.abs(gam.EF()-68.7)<1 && thisMULs.length>0){
		   System.out.println("ConsistencyCheck 2796: lev="+lev.ES()+ " g="+gam.ES()+"  "+JIS+"  "+JFS+" JI="+JI+" JF="+JF+"  DJ="+DJ);
		   System.out.println("      MUL="+mul+" thisMULs.length="+thisMULs.length+" isLargeDJ="+EnsdfUtil.isLargeDJ(JFS,JIS,2)+" ");
	    }
		*/
		
	    msgType="E";
		msgText="";
		if(JI==0 && JF==0){
			if(DP==-1)
				printToMessage(31,"0 to 0 transition with party change=-1 is not allowed. Check spin-parities\n","E");
			else if(DP==1)
				printToMessage(31,"Check E0 transition.\n","W");
		}else if((JI<0||JF<0) && EnsdfUtil.isLargeDJ(JFS,JIS,2)){
	    	msgText+="check Jf&Ji for deltaJ>2 for gamma="+gam.ES()+"\n";
	    	msgType="W";
	    	msgText+="  JI="+JIS+" JF("+fl.ES()+")="+JFS+"\n";
	    	printToMessage(21,msgText,msgType);
		}
		
	    msgType="E";
		msgText="";
		if(thisMULs.length>0 && expectedMULsV.size()>0){//has both MUL and JI and JF (JI,JF single value)
		    boolean isGood=true,hasE=false,hasM=false;
	    	for(int i=0;i<thisMULs.length;i++){
	    		String thisMUL=thisMULs[i];
	    		if(thisMUL.contains("E"))
	    		    hasE=true;
	    		else if(thisMUL.contains("M"))
	    		    hasM=true;
	    		else {//for D,Q or D+Q
	    		    hasE=false;
	    		    hasM=false;
	    		}
	    		
	    		if(!expectedMULsV.contains(thisMUL)) {
	    			msgText+="Mult="+thisMUL+" is inconsistent with spin-parity change\n";
	    			isGood=false;
	    			break;
	    	    	//System.out.println(" final level: "+fl.ES());
	    		}
	    	}
	    	if(isGood) {//expected M1,E1,M2,E2 from JI=3+, JF=2; but MULT=[E1]
	    	    //boolean mixed=hasE&&hasM;
	    	    if(DP==0 && (hasE||hasM) && !mul.equals("[D,E2]") && !mul.equals("[M1,E2]")) {
	    	    //if(DP==0 && (PI+PF)!=0 && !mixed && (hasE||hasM)) {
	    	        msgText+="Mult="+mul+" but parity-change unknown from deltaJPI\n";
	    	        msgType="W";
	    	    }
	    	}else
	    	    msgType="E";
	    }else if(thisMULs.length>0 && (JIS.isEmpty()!=JFS.isEmpty())){//has MUL but only one of JI and JF
	    	if(!mul.equals("[D,E2]")) {
		    	msgText+="Mult is inconsistent with JPI (one of JPIs is empty)\n";
		    	msgType="W";
	    	}
	    }else if(thisMULs.length==0 && DJ>2){//has large deltaJ but no MUL
	    	//if(currentENSDF==adopted || currentENSDF.DSId0().contains("DECAY")) {
		    	msgText+="large deltaJ="+DJ+" (["+expectedMULsV.get(0)+"]), but no Mult is given\n";
		    	msgType="W";
	    	//}

	    }

	    if(msgText.length()>0){
	    	msgText+="  JI="+JIS+" JF("+fl.ES()+")="+JFS+"\n";
	    	printToMessage(31,msgText,msgType);
	    }
	    
	    //check mixing ratio
		//if MR is not taken from Adopted or there is no adopted dataset, skip this check
		if(adopted!=null && currentENSDF!=adopted){
			//if reaching here, there is a general comment (flagged or non-flagged) saying MR is from Adopted Levels
			boolean isMRFromAdopted=isRecordFromAdopted(gam,"MR");			
			
			if(adoptedGamma!=null){
				String thisMR=gam.MRS().trim();
				String thisDMR=gam.DMRS().trim();
				String thisMRS=thisMR+thisDMR;
				
				String adoptedMR=adoptedGamma.MRS().trim();
				String adoptedDMR=adoptedGamma.DMRS().trim();
				String adoptedMRS=adoptedMR+adoptedDMR;
				
				if(isMRFromAdopted){
					if(!adoptedMRS.isEmpty() && thisMRS.isEmpty()){
						msgType="E";
						if(gam.MS().trim().isEmpty())
							msgType="W";
						msgText="MR commented from Adopted but not given\n";
						msgText+="  Adopted MR="+adoptedMR+" "+adoptedDMR+" this MR="+thisMR+" "+thisDMR+"\n";
						printToMessage(41,msgText,msgType);
					}else if(!thisMR.equals(adoptedMR) || !thisDMR.equals(adoptedDMR)){
						msgText="MR commented from Adopted but inconsistent\n";
						msgText+="  Adopted MR="+adoptedMR+" "+adoptedDMR+" this MR="+thisMR+" "+thisDMR+"\n";
						printToMessage(41,msgText,"E");
					}
				}else if(hasFromAdoptedFootnote(gam,"MR")){
					if(!thisMR.equals(adoptedMR) || !thisDMR.equals(adoptedDMR)){
						msgText="MR here inconsistent with adopted\n";
						msgText+="  Adopted MR="+adoptedMR+" "+adoptedDMR+" this MR="+thisMR+" "+thisDMR+"\n";
						printToMessage(41,msgText,"W");
					}
				}
	
				//check MR value in comment as quoted from Adopted Gammas
				for(int i=0;i<gam.nComments();i++) {
					Comment c=gam.commentAt(i);
					if(c.head().equals("MR") && c.body().toUpperCase().contains("ADOPTED")) {
						String adoptedValueInComment=extractAdoptedValueInComment(c);
						
						//debug
						//System.out.println("ConsistencyCheck line 1084: comment="+c.body()+" adoptedValueInComment="+adoptedValueInComment);
						
						if(!adoptedValueInComment.isEmpty()) {
							String[] out=adoptedValueInComment.split("[\\s]+");
							boolean matched=false, valid=false;
							String s="",ds="";
							if(out.length==2) {
								s=out[0].trim();
								ds=out[1].trim();
							}else if(out.length==1) {
								s=out[0].trim();
							}
								
							if(Str.isNumeric(s)) {
								valid=true;								
								if(!s.equals(adoptedMR) || (!ds.isEmpty()&&!ds.equals(adoptedDMR)))
									matched=false;
								else
									matched=true;								
							}
							
							if(!matched) {
								if(valid) {
									msgText="MR quoted in comment as from Adopted is inconsistent\n";
									msgText+="  Adopted MR="+adoptedMR+" "+adoptedDMR+" MR in comment="+adoptedValueInComment+"\n";
									printToMessage(41,msgText,"E");
								}else {
									msgText="Please check MR in comment quoted as from Adopted Levels\n";
									msgText+="  Adopted MR="+adoptedMR+" "+adoptedDMR+" comment="+adoptedValueInComment+"\n";
									printToMessage(41,msgText,"E");
								}

							}
						}
					}
				}
			}else{//if no corresponding adopted gamma, check if MR is from somewhere else
				if(!gam.MRS().isEmpty() && isMRFromAdopted){
    				if(possibleAdoptedGam==null  || !possibleAdoptedGam.MRS().equals(gam.MRS())) {
    					msgText="MR commented from Adopted but no such Adopted gamma\n";
    					printToMessage(41,msgText,"E");
    				}
				}
			}

		}
		
	      
        //check for low energy transition: if assumed MULT and CC should be given but are missing in decay datasets,
        //for calculating absolute total g+ce intensities

        if(currentENSDF.norm().OS().equals("3") && gam.CCS().isEmpty() && currentENSDF.nGamWI()>0 && currentENSDF.decayRecordType().length()>0 && gam.RID()>0) {
        	String NRS=currentENSDF.norm().NRS();
        	String DNRS=currentENSDF.norm().DNRS();
        	double NRD=currentENSDF.norm().NRD();
        	if(!NRS.isEmpty() && NRD>0 && (!DNRS.isEmpty()||NRD!=1.0) ) {
                float eg=gam.EF();
                float egLimit=0;
                int Z=currentENSDF.nucleus().Z();
                if(DP==1) {
                    if(DJ>=2) {
                        egLimit=egForE2ICCLimit2(Z);
                    }else {
                        egLimit=egForM1ICCLimit2(Z);
                    }
                }else if(DP==-1) {
                    if(DJ>=2) {
                        egLimit=egForM2ICCLimit2(Z);
                    }else {
                        egLimit=egForE1ICCLimit2(Z);
                    }
                }
    
                boolean toCheck=false;
                if(!gam.q().isEmpty()) {
                	if(eg>0 && eg<egLimit/2.0)
                		toCheck=true;
                }else if(eg>0 && eg<egLimit) {
                	toCheck=true;
                }
                if(toCheck) {
                    msgText="Please check if ICC here can be neglected\n";
                    if(gam.MS().isEmpty()) {
                    	msgText+="   JI="+gam.JIS()+"  JF="+gam.JFS()+"\n";
                    }
                    printToMessage(56,msgText,"W");
                    
                    //System.out.println("##EG="+eg);
                    //System.out.println("EG limit="+egForE2ICCLimit2(Z)+" for E2");
                    //System.out.println("EG limit="+egForM1ICCLimit2(Z)+" for M1");
                    //System.out.println("EG limit="+egForM2ICCLimit2(Z)+" for M2");
                    //System.out.println("EG limit="+egForE1ICCLimit2(Z)+" for E1");
                }
        	}
        }

	    currentMsg=message;
	    message=previousMsg+message;
		return currentMsg;
	}
	
	/*
	 * roughly estimated using BrIcc for ICC(E2)=0.005
	 */
	public float egForE2ICCLimit1(int Z) {
	    return (float) (6E-05*Math.pow(Z,4) - 0.0087*Math.pow(Z,3) + 0.533*Z*Z - 4.934*Z + 227.78);

	}
	
	   /*
     * roughly estimated using BrIcc for ICC(M1)=0.005
     */
    public float egForM1ICCLimit1(int Z) {
        return (float) (0.0001*Math.pow(Z,4) - 0.0199*Math.pow(Z,3) + 1.4884*Z*Z - 33.671*Z + 371.11);
    }
    
	/*
	 * roughly estimated using BrIcc for ICC(M2)=0.005
	 */
	public float egForM2ICCLimit1(int Z) {
	    return (float) (2.0E-04*Math.pow(Z,4) - 0.0365*Math.pow(Z,3) + 2.6047*Z*Z - 59.947*Z + 700.11);

	}
	
	   /*
     * roughly estimated using BrIcc for ICC(E1)=0.005
     */
    public float egForE1ICCLimit1(int Z) {
        return (float) (6.0E-6*Math.pow(Z,4) - 0.0004*Math.pow(Z,3) + 0.0157*Z*Z + 6.2482*Z + 33.83);
    }
    
	/*
	 * roughly estimated using BrIcc for ICC(E2)=0.01
	 */
	public float egForE2ICCLimit2(int Z) {
	    return (float) (4E-05*Math.pow(Z,4) - 0.0073*Math.pow(Z,3) + 0.441*Z*Z - 4.2227*Z + 196.33);

	}
	
	   /*
     * roughly estimated using BrIcc for ICC(M1)=0.01
     */
    public float egForM1ICCLimit2(int Z) {
        return (float) (0.0001*Math.pow(Z,4) - 0.0199*Math.pow(Z,3) + 1.4884*Z*Z - 33.671*Z + 371.11);
    }
    
	/*
	 * roughly estimated using BrIcc for ICC(M2)=0.01
	 */
	public float egForM2ICCLimit2(int Z) {
	    return (float) (6E-05*Math.pow(Z,4) - 0.0098*Math.pow(Z,3) + 0.719*Z*Z - 9.2396*Z + 186.78);

	}
	
	   /*
     * roughly estimated using BrIcc for ICC(E1)=0.01
     */
    public float egForE1ICCLimit2(int Z) {
        return (float) (0.00001*Math.pow(Z,4) - 0.0017*Math.pow(Z,3) + 0.1078*Z*Z +2.0458*Z + 57.72);
    }
    
	@SuppressWarnings("unused")
	private String checkMR(Level lev,Gamma gam){
		String currentMsg="";
		String previousMsg=message;
		clearMessage();
		
		String msgText="";
		//if MR is not taken from Adopted or there is no adopted dataset, skip this check
		if(adopted!=null && currentENSDF!=adopted){
			
			boolean isMRFromAdopted=isRecordFromAdopted(gam,"MR");
			
			if(adoptedGamma!=null){
				String thisMR=gam.MRS().trim();
				String thisDMR=gam.DMRS().trim();
				
				String adoptedMR=adoptedGamma.MRS().trim();
				String adoptedDMR=adoptedGamma.DMRS().trim();
				
				if(!thisMR.equals(adoptedMR) || !thisDMR.equals(adoptedDMR)){
					if(isMRFromAdopted){//if reaching here, there is a general comment (flagged or non-flagged) saying MR is from Adopted Levels
						msgText="MR commented from Adopted but inconsistent\n";
						msgText+="  Adopted MR="+adoptedMR+" "+adoptedDMR+" this MR="+thisMR+" "+thisDMR+"\n";
						printToMessage(41,msgText,"E");
					}else if(hasFromAdoptedFootnote(gam,"MR")){
						msgText="MR here inconsistent with adopted\n";
						msgText+="  Adopted MR="+adoptedMR+" "+adoptedDMR+" this MR="+thisMR+" "+thisDMR+"\n";
						printToMessage(41,msgText,"W");
					}

				}
				
				//check MR value in comment as quoted from Adopted Gammas
				for(int i=0;i<gam.nComments();i++) {
					Comment c=gam.commentAt(i);
					if(c.head().equals("MR") && c.body().toUpperCase().contains("ADOPTED")) {
						String adoptedValueInComment=extractAdoptedValueInComment(c);
						
						//debug
						//System.out.println("ConsistencyCheck line 1084: comment="+c.body()+" adoptedValueInComment="+adoptedValueInComment);
						
						if(!adoptedValueInComment.isEmpty()) {
							String[] out=adoptedValueInComment.split("[\\s]+");
							boolean matched=false, valid=false;
							String s="",ds="";
							if(out.length==2) {
								s=out[0].trim();
								ds=out[1].trim();
							}else if(out.length==1) {
								s=out[0].trim();
							}
								
							if(Str.isNumeric(s)) {
								valid=true;								
								if(!s.equals(adoptedMR) || (!ds.isEmpty()&&!ds.equals(adoptedDMR)))
									matched=false;
								else
									matched=true;								
							}
							
							if(!matched) {
								if(valid) {
									msgText="MR quoted in comment as from Adopted is inconsistent\n";
									msgText+="  Adopted MR="+adoptedMR+" "+adoptedDMR+" MR in comment="+adoptedValueInComment+"\n";
									printToMessage(41,msgText,"E");
								}else {
									msgText="Please check MR in comment quoted as from Adopted Levels\n";
									msgText+="  Adopted MR="+adoptedMR+" "+adoptedDMR+" comment="+adoptedValueInComment+"\n";
									printToMessage(41,msgText,"E");
								}

							}
						}
					}
				}
			}else{//if no corresponding adopted gamma, check if MR is from somewhere else
				if(!gam.MRS().isEmpty()){
    				if(possibleAdoptedGam==null  || !possibleAdoptedGam.MRS().equals(gam.MRS())) {
    					msgText="MR commented from Adopted but no such Adopted gamma\n";
    					printToMessage(41,msgText,"E");
    				}
				}
			}
		}
		
		currentMsg=message;
		message=previousMsg+message;
		return currentMsg;
	}
	
	private HashMap<String,String> makeBXLMap(Gamma gam){
		HashMap<String,String> BXLMap=new HashMap<String,String>();
		ArrayList<String> mults=new ArrayList<String>(Arrays.asList("E1","E2","E3","E4","M1","M2","M3","M4"));
		for(int i=0;i<mults.size();i++) {
			String name="B"+mults.get(i)+"WDWN";
			ContRecord rc=null;
			try{
			    rc=gam.contRecordsVMap().get(name).get(0);
			}catch(Exception e) {}
			
			String s="";
			if(rc!=null)
				s=rc.s();
			
			BXLMap.put(mults.get(i),s);
		}

	
		String[] keys=new String[BXLMap.size()];
		BXLMap.keySet().toArray(keys);
		for(int i=0;i<keys.length;i++){
			String key=keys[i];
			if(BXLMap.get(key).trim().isEmpty())
				BXLMap.remove(key);
		}
		
		return BXLMap;
	}
	
	private String checkBands(ENSDF ens) {

		if(ens.nBands()==0)
			return "";
		
		String currentMsg="";
		String previousMsg=message;
		
		
		String msgText="";
		boolean isSEQ=false;
		for(int i=0;i<ens.nBands();i++){
			Band b=ens.bandAt(i);
			
			clearMessage();
			msgText="";
			
			//check levels in each band
			int prevDJ=-100;
			boolean isValidBand=true;
			
			//check if JPI of band head quoted in band comment is consistent with JPI of the level as band head
			String line=b.comment().cbody();
			String[] temp=Str.trimArray(line.split("\\.[\\s]+"));
			if(temp.length>0)
				line=temp[0].toUpperCase().trim();
			
			line=Str.removeEndPeriod(line).trim();
			
			
			int n=line.indexOf("BASED ON");
			String JPS="";
			
			
			String name="band";
			if(b.comment().head().startsWith("SEQ")) {
				isSEQ=true;
				name="seq.";
			}else {
				isSEQ=false;
			}
			
			//System.out.println("#1 n="+n+"  line="+line);
			
			if(n>=0) {//e.g., comment=20NA cL BAND(A)$Band based on 5/2+, ....
				line=line.substring(n+8).trim();
				temp=Str.trimArray(line.split(",[\\s]+|[;]"));//not split "3/2+,5/2+" but "3/2+, 5/2+"
				if(temp.length>0) {
					if(temp.length==2 && EnsdfUtil.isJPIStr(temp[1]) && !EnsdfUtil.isJPIStr(temp[0]))
						JPS=temp[1];
					else 
						JPS=temp[0];
					
					temp=Str.trimArray(JPS.split("[\\s]+"));
					if(temp.length>0)
						JPS=temp[0];
				}
			}else {
				n=line.indexOf("BAND");
				if(n>0) {//e.g., comment=20NA cL BAND(A)$5/2+ band
					line=line.substring(0,n).trim();
					temp=Str.trimArray(line.split("[\\s]+"));
					if(temp.length>0)
						JPS=temp[temp.length-1].trim();
				}else if(!line.contains(" ")) {//e.g., comment=20NA cL BAND(A)$5/2+
					JPS=line;
				}
			}
				
			//System.out.println("#2 n="+n+"  line="+line);
			
			if(!JPS.isEmpty() && EnsdfUtil.isJPIStr(JPS)) {
				String levelJPS=b.firstLevel().JPiS();
				if(!JPS.equals(levelJPS)) {
					String msg="JPI of "+name+" head quoted in "+name+" comment is inconsistent:\n";
					msg+="   JPI="+JPS+" in "+name+" comment    JPI="+levelJPS+" in level\n";
					printToMessage(10,msg,"E");
				}
			}
			
			//System.out.println("#3 n="+n+"  line="+line);
			
			for(int j=b.nLevels()-1;j>0;j--) {
				Level li=b.levelAt(j);
				Level lf=b.levelAt(j-1);
				Vector<Integer> DJV=EnsdfUtil.deltaJ(li.JPiS(),lf.JPiS());
				if(DJV.size()>1 && li.JPiS().contains(",") && !isSEQ) 
					msgText+="Check JPI="+li.JPiS()+" of member level="+li.ES()+"\n";
				
				int DJ=-100;
				if(DJV.size()==1)
					DJ=DJV.get(0);
				
				boolean hasFeedingGamma=false;
				for(int k=0;k<li.nGammas();k++) {
					Gamma g=li.gammaAt(k);
					
					//System.out.println("  l="+li.ES()+"  gam="+g.ES()+"  g.FLI="+g.FLI()+" index="+ens.levelsV().indexOf(li)+" nlevels="+ens.nLevels()+" lf="+lf.ES());
					
					if(g.FLI()>=0 && ens.levelAt(g.FLI())==lf) {
						hasFeedingGamma=true;
						break;
					}
				}
				
				if(!hasFeedingGamma && !isSEQ) 
					msgText+="No in-band transition from member level="+li.ES()+" to "+lf.ES()+"\n";
				
				if(DJ>0 && prevDJ>0 && DJ!=prevDJ)
					isValidBand=false;
				
				prevDJ=DJ;
			}
			
			if(!isValidBand && b.comment().head().contains("BAND")) {
				msgText+="Not a band due to different deltaJ for in-band transitions.\n";
				msgText+="     Please use \"SEQ\" instead of \"BAND\" in band comments\n";
			}
			
			if(msgText.length()>0) {
				printToMessage(10,msgText,"W");
			}
			
			//System.out.println("#4 n="+n+"  line="+line);
			
			if(message.length()>0) {
				line=b.comment().lineAt(0);
				currentMsg+=line+"\n"+message;
			}
		}//end band loop

		message=previousMsg+currentMsg;
		return currentMsg;
	}
	
	//for Adopted Gammas only
	private String checkBXL(Gamma gam){
		
		HashMap<String,String> BXLMap=makeBXLMap(gam);
		int size=BXLMap.size();
		if(size==0)
			return "";
		
		String currentMsg="";
		String previousMsg=message;
		clearMessage();
		
		
		String msgText="";
		int A=currentENSDF.nucleus().A();

		for(String key:BXLMap.keySet()){
			try{
				float limit=RUL.getLimit(A,key);
				float thisBXL=Float.parseFloat(BXLMap.get(key));
				if(thisBXL>limit){
					msgText+="B"+key+"W="+thisBXL+" exceeds RUL="+limit+"! Please check.\n";
				}
			}catch(Exception e){
				
			}
		}
		
		if(msgText.length()>0){
			printToMessage(10,msgText,"W");
			for(int i=0;i<gam.contRecsLineV().size();i++){
				String line=gam.contRecordLineAt(i);
				if(line.contains(" BE") || line.contains(" BM")){
					message=line+"\n"+message;
					break;
				}
			}
		}else if(BXLMap.size()==0 && gam.MS().length()>0 && gam.ILS().length()>0 && gam.syRID()>0){
			Level il=iLevel(gam, currentENSDF);
			if(il.halflife().length()>0){
				printToMessage(31,"BXLW not given! Please check.\n","W");
			}
	
		}
		
		currentMsg=message;
		message=previousMsg+message;
		return currentMsg;
	}
		
	private String checkDecay(Level lev, Decay decay){
		String currentMsg="";
		String previousMsg=message;
		clearMessage();
		
		//System.out.println(currentENSDF.DSId0()+""+(decay instanceof Beta)+""+(decay instanceof ECBP)+(decay instanceof Alpha));
		
		if((decay instanceof Beta) || (decay instanceof ECBP))
			checkLOGFT(lev,decay);
		else if(decay instanceof Alpha)
			checkHF(lev,(Alpha)decay);
		
		if(!message.isEmpty()){
			//String line=findDecayLine(lev,decay);
			String line=decay.recordLine();
			message=line+"\n"+message;
			currentMsg=message;
		}
		
		message=previousMsg+message;	
		return currentMsg;
	}
	
	//check hindrance factor of alpha decay:
	//if mass is odd and HF<4, then Jf=Ji and no parity change. 
	//if mass is even and Jf=0 or Ji=0, then parity	change=-1^MOD(Jf-Ji)
	private String checkHF(Level lev,Alpha decay){
		String currentMsg="";
		String previousMsg=message;
		clearMessage();
		
		String msgText="";
		
		Parent parent=currentENSDF.parentAt(0);
		float HF=100000,DHF=10000;
		String HFS=decay.HFS().trim();
		String DHFS=decay.DHFS().trim();
		try{
			HF=Float.parseFloat(HFS);
			DHF=(float)EnsdfUtil.s2x(HFS,DHFS).DX();		
		}catch(Exception e){}
		
		if(!HFS.isEmpty() && DHFS.isEmpty())
			DHF=0.2f*HF;

		int A=currentENSDF.nucleus().A();
		String parentJPI=parent.level().JPiS().trim();
		String levelJPI=lev.JPiS().trim();
		if(A%2==1){//odd mass
			if((HF+DHF)<4 || (HF<4 && DHFS.indexOf('L')==0)){
				if(!levelJPI.equals(parentJPI)){
					msgText="HF inconsistent with JPI change\n";
					msgText+="  same JPI expected by HF\n";
					msgText+="  parent JPI="+parent.level().JPiS().trim()+" this JPI="+lev.JPiS().trim()+"\n";
					printToMessage(31,msgText,"W");
				}
			}
		}else{//even mass
			parentJPI=parentJPI.replace("(", "").replace(")","").trim();
			levelJPI=levelJPI.replace("(", "").replace(")","").trim();
			int parentParity=0,levelParity=0;
            int parentJ=-1,levelJ=-1;
            
            try{
            	parentJ=Integer.parseInt(parentJPI.replace("+", "").replace("-", "").trim());
            	levelJ=Integer.parseInt(levelJPI.replace("+", "").replace("-", "").trim());
            }catch(Exception e){ 
            	    message=previousMsg;
            		return "";
            }
            
            if(parentJ!=0 && levelJ!=0){
            	message=previousMsg;
            	return "";
            }
            
            int expectedParityChange=(int)Math.pow(-1.0001,Math.abs(parentJ-levelJ));
            
            //here, at least one of parentJ and levelJ is 0 
           
			if(parentJPI.endsWith("+"))
				parentParity=1;
			else if(parentJPI.endsWith("-"))
				parentParity=-1;
			
			if(levelJPI.endsWith("+"))
				levelParity=1;
			else if(parentJPI.endsWith("-"))
				levelParity=-1;
			
			int parityChange=parentParity*levelParity;
		
			if(parityChange>0 && parityChange!=expectedParityChange){
				msgText="Parity change="+parityChangeStr(parityChange)+" inconsistent with spin change with one spin=0\n";
				msgText+="  parity change=(-1)^(Jf-Ji) ("+parityChangeStr(expectedParityChange)+") expected\n";
				msgText+="  parent JPI="+parent.level().JPiS().trim()+" this JPI="+lev.JPiS().trim()+"\n";
				printToMessage(21,msgText,"W");
			}
		}
		
		//check if relative uncertainty of HF value is consistent with decay branching for g.s. 
		if((lev.EF()==0||decay.ID()==100) && Str.isNumeric(DHFS) && decay.DAID()>0) {
			float relDHF=DHF/HF;
			float relDAI=(float)(decay.DAID()/decay.AID());//absolute alpha branching=IA*BR
			float r=relDHF/relDAI;
			float diff=(float)Math.abs(r-1.0);
			
			if(diff>0.2 || (diff>0.1 && relDHF>0.1)) {
				int n=-(int)Math.log10(relDHF)+1;
				msgText="Inconsistent relative uncertainties in HF and IA*BR (for g.s.)\n";
				msgText+="            DHF/HF="+String.format("%."+n+"f%%\n", relDHF*100);
				msgText+="  D(IA*BR)/(IA*BR)="+String.format("%."+n+"f%%\n", relDAI*100);
				printToMessage(31,msgText,"W");
			}
		}
		
		
		currentMsg=message;
		message=previousMsg+message;
		
		return currentMsg;
		
	}
	
	//For 3.6<logft<5.9, then Ji-1<=Jf<=Ji+1 with no parity change.
	//For 1U in cols. 78-79 and log ft=8.5, then Jf=Ji+/-2 with parity change 
	private String checkLOGFT(Level lev,Decay decay){
		String currentMsg="";
		String previousMsg=message;

		
		String msgText="";
		
		Parent parent=currentENSDF.parentAt(0);
		float LOGFT=100000,DLOGFT=10000;
		String LOGFTS=decay.LOGFTS().trim();
		String DLOGFTS=decay.DLOGFTS().trim();
		
		String parentJPI=parent.level().JPiS().trim();
		String levelJPI=lev.JPiS().trim();
		
		if(LOGFTS.isEmpty() || parentJPI.isEmpty() || levelJPI.isEmpty())
			return "";
		
		clearMessage();
		try{
			LOGFT=Float.parseFloat(LOGFTS);
			DLOGFT=(float)EnsdfUtil.s2x(LOGFTS, DLOGFTS).DX();	
			if(DLOGFT<0)
				DLOGFT=10000;
		}catch(Exception e){}
		
		if(!LOGFTS.isEmpty() && DLOGFTS.isEmpty())
			DLOGFT=0.2f*LOGFT;
         
		
		parentJPI=parentJPI.replace("(", "").replace(")","").trim();
		levelJPI=levelJPI.replace("(", "").replace(")","").trim();
		
		int parentParity=0,levelParity=0;
        float parentJ=-1,levelJ=-1;
        
        try{
        	parentJ=EnsdfUtil.s2f(parentJPI.replace("+", "").replace("-", "").trim());
        	levelJ=EnsdfUtil.s2f(levelJPI.replace("+", "").replace("-", "").trim());
        }catch(Exception e){ 
        }
        
        int expectedParityChange=0;//=1 for no change, -1 for change
        int expectedSpinChange=-1;
        
		if(parentJPI.endsWith("+"))
			parentParity=1;
		else if(parentJPI.endsWith("-"))
			parentParity=-1;
		
		if(levelJPI.endsWith("+"))
			levelParity=1;
		else if(levelJPI.endsWith("-"))
			levelParity=-1;
		
		int parityChange=parentParity*levelParity;
		int spinChange=-1000;
		if(parentJ>=0 && levelJ>=0)
			spinChange=(int)(Math.abs(parentJ-levelJ)+0.1);
		
		
		//print a warning message for decays with unlikely spin change and parity change
		if(spinChange>2 ||(spinChange==2 && parityChange==1)){
			msgText="Decay branch is less likely. Please check.\n";
			msgText+="  parent JPI="+parent.level().JPiS().trim()+" this JPI="+lev.JPiS().trim()+"\n";
			msgText+="  spin change="+spinChange+" parity change="+parityChangeStr(parityChange)+"\n";
			printToMessage(9,msgText,"W");
		
			msgText="";
		}
		
		boolean toCheck=false;
		boolean has1ULOGFT=false;
		if((LOGFT-DLOGFT)>=8.5 || (LOGFT>=8.5 && DLOGFTS.indexOf('G')==0))
			has1ULOGFT=true;
		
		if((LOGFT>3.6 && (LOGFT+DLOGFT)<5.9) || (LOGFT<5.9 && DLOGFTS.indexOf('L')==0)){
			expectedParityChange=1;
			expectedSpinChange=1;
			toCheck=true;

		}else if(decay.unique().toUpperCase().equals("1U")){
			if(has1ULOGFT){
				expectedParityChange=-1;
				expectedSpinChange=2;
				toCheck=true;
			}
		}

		if(toCheck){
			msgText="";
			if(parityChange!=expectedParityChange)
				msgText+="parity";
            if(spinChange>expectedSpinChange){
            	if(msgText.length()>0)
            		msgText+=" and ";
            	msgText+="spin";
            }
		}
		//System.out.println(currentENSDF.DSId0()+" "+LOGFT+" "+DLOGFT+"  "+msgText);
		
		if(msgText.length()>0){
			msgText="LOGFT inconsistent with "+msgText+" change\n";
			String temp="";
			if(msgText.contains("spin")){
				if(expectedSpinChange==1)
					temp+="spin change<=1";
				else
					temp+="spin change="+expectedSpinChange;
			}
			if(msgText.contains("parity")){
				if(temp.length()>0)
					temp+=", ";

				temp+="parity change="+parityChangeStr(expectedParityChange);
			}
			if(temp.length()>0)
				msgText+="  expected "+temp+" from LOGFT\n";
			msgText+="  parent JPI="+parent.level().JPiS().trim()+" this JPI="+lev.JPiS().trim()+"\n";
			printToMessage(41,msgText,"W");
		}
		
		//check 0 to 0 transition
		if(LOGFT>3.6 && (LOGFT+DLOGFT)<6.4 && parentJ==0 && levelJ==0 && parentParity==1 && levelParity==1) {
			msgText="LOGFT inconsistent with 0+ to 0+:\n";
			msgText+="  expected 3.6<LOGFT<6.4\n";
			printToMessage(41,msgText,"W");
		}
		
		//check 1U marker
		if(has1ULOGFT && spinChange==2 && parityChange==-1 && !decay.unique().toUpperCase().equals("1U")){
			msgText="flag=1U is missing for:\n";
			msgText+="  LOGFT>8.5  DJ=2  DPI=yes\n";
			printToMessage(77,msgText,"W");
		}
		

		
		currentMsg=message;
		message=previousMsg+message;
		
		return currentMsg;
	}
	
	private String checkDelay(Level lev, DParticle delay){
		String currentMsg="";
		String previousMsg=message;
		clearMessage();
		
		if(!message.isEmpty()){
			//String line=findDecayLine(lev,delay);
			String line=delay.recordLine();
			message=line+"\n"+message;
		}
		
		currentMsg=message;
		message=previousMsg+message;
		
		return currentMsg;
	}
	
	private String checkUnpGamma(Gamma gam){	
		String currentMsg="";
		String previousMsg=message;
		clearMessage();
		
		checkBXL(gam);
		
		if(!message.isEmpty()){
			//String line=findGammaLine(lev,gam);
			String line=gam.recordLine();
			message=line+"\n"+message;
		}
		
		currentMsg=message;
		message=previousMsg+message;
		
		return currentMsg;
	}
	
	private String checkUnpDecay(Decay decay){	
		String currentMsg="";
		String previousMsg=message;
		clearMessage();
		
		if(!message.isEmpty()){
			//String line=findDecayLine(lev,decay);
			String line=decay.recordLine();
			message=line+"\n"+message;
		}
		
		currentMsg=message;
		message=previousMsg+message;
		
		return currentMsg;
	}
	
	private String checkUnpDelay(DParticle delay){	
		String currentMsg="";
		String previousMsg=message;
		clearMessage();
		
		if(!message.isEmpty()){
			//String line=findDecayLine(lev,delay);
			String line=delay.recordLine();
			message=line+"\n"+message;
		}
		
		currentMsg=message;
		message=previousMsg+message;
		
		return currentMsg;
	}
		
	//check if a level in an individual dataset is
	//referenced in the adopted (if it is in the XREF)
	@SuppressWarnings("unused")
	private boolean isXReferenced(Level lev){
		Vector<Level> matched;
		
		matched=findMatchedLevelsInAdopted(lev);
		for(int i=0;i<matched.size();i++){
			String XREFs=EnsdfUtil.parseLevelXREF(matched.elementAt(i));
			if(XREFs.contains(currentENSDFXTag))
				return true;
		}
		
		return false;
	}
	
	//check if there is any comment of recordName in the given record
	@SuppressWarnings("unused")
	private boolean hasCommentFor(Record rec,String recordName){
		return (hasInLineCommentFor(rec,recordName)||hasFlaggedCommentFor(rec,recordName));
	}
	
	//check if there is any comment of recordName in the given record
	private boolean hasInLineCommentFor(Record rec,String recordName){
		try{
			for(int i=0;i<rec.commentV().size();i++)
				if(rec.commentAt(i).hasHead(recordName))
					return true;
		}catch(Exception e){}
		
		return false;
	}
	
	private boolean hasFlaggedCommentFor(Record rec,String recordName){
        
		try{		
			String recordFlags=rec.flag().trim();
			if(recordFlags.isEmpty())
				return false;
			
			String name=recordName;
			char type=rec.recordLine().charAt(7);
		    if(name.equals("E"))
		    	name=name+type;
		    	    
			String flagsInMap=currentFootnotedRecordNameMap.get(name);//Note: DON'T TRIM, since " " is added for footnote without flag
			
			//if(recordName.equals("J") && rec.ES().trim().equals("3483.97")){
			//	for(String key:currentFootnotedRecordNameMap.keySet()) System.out.println(key+"    "+currentFootnotedRecordNameMap.get(key));
			//	System.out.println(" flagsInMap="+flagsInMap+" "+flagsInMap.isEmpty());
			//}
			
			
			for(int i=0;i<recordFlags.length();i++)
				if(flagsInMap.contains(recordFlags.substring(i,i+1)))
					return true;
	        
		}catch(Exception e){}
			
		return false;
	}
	
	///////////////////////////////////////
	//All search functions
	///////////////////////////////////////
	
	public Level findAdoptedLevel(Level lev){
		//if(lev.ES().contains("11157.59")) 
		//	System.out.println("ConsistencyCheck 3803: lev="+lev.ES()+" adopted==null: "+(adopted==null)+" isPseudo: "+lev.isPseudo()+"  lev.isGotoAdopted()="+lev.isGotoAdopted());
		
		if(adopted==null || lev.isPseudo())
			return null;
		
		adoptedLevel=null;
		possibleAdoptedLev=null;
		
		matchedAdoptedLevels.clear();
		
		if(!lev.isGotoAdopted())
			return adoptedLevel;
		
		Vector<Level> matched=null,EMatched=null;

		String levelXRef;//actual level XREF read from input file
		String scannedLevelXRef;//string of tags for levels that could group together
		String msgText="";
		
		//find energy-matched levels in Adopted dataset within error and with consistent JPIs
		//and consistent gammas if any,
        //or adopted levels with energy not matched but having the energy of lev in its XREF  
		matched=findMatchedLevelsInAdopted(lev);

		/*
		//debug
		if(lev.ES().equals("2650") || lev.ES().equals("2898"))
		//if(lev.ES().contains("2191.2") && lev.DES().equals("13"))
		//if(Math.abs(lev.EF()-18100)<=10)// && lev.JPiS().contains("4+"))
		{    System.out.println("In ConsistencyCheck line 4082: ES="+lev.ES()+" J="+lev.JPiS()+" nmatched adopted="+matched.size()+" ensXTag="+currentENSDFXTag+" dsid="+currentENSDF.DSId0()+lev.q().equals("?"));
             for(Level l:matched) System.out.println("  level:"+l.ES()+" JPI="+l.JPiS());
		}
		*/
		
		if(matched.size()==0 && !lev.q().equals("S")){
		    
	        if(lev.NNES().equals("SN") || lev.NNES().equals("SP"))
	            return null;
	        
			EMatched=findEnergyMatchedLevelsInAdopted(lev);
			
			matched.clear();
			matched.addAll(EMatched);
			
			/*
		    //debug
			if(lev.ES().contains("642") && lev.DES().equals("2"))
	        //if(lev.ES().contains("5231.2"))
	        //if(Math.abs(lev.EF()-8820)<=10)// && lev.JPiS().contains("4+"))
	        {    System.out.println("In ConsistencyCheck line 3666: ES="+lev.ES()+" J="+lev.JPiS()+" nmatched adopted="+matched.size()+" ensXTag="+currentENSDFXTag+" dsid="+currentENSDF.DSId0()+lev.q().equals("?"));
	             for(Level l:matched) System.out.println("  level:"+l.ES()+" JPI="+l.JPiS());
	        }
			*/
			
			//further check gammas
			//Note that the same check has been done in findMatchedLevelsInAdopted(lev);
			if(matched.size()>=1 && lev.nGammas()>0){
				Vector<Level> tempLevels=new Vector<Level>();
				tempLevels.addAll(matched);
				for(int i=0;i<matched.size();i++){
					Level l=matched.get(i);
					
					/*
					if(lev.ES().contains("5231.2")) {
					    System.out.println("In ConsistencyCheck line 3860: ES="+lev.ES()+" J="+lev.JPiS()+" nmatched adopted="+matched.size());
					    System.out.println("  l="+l.ES()+" isGammasConsistent="+EnsdfUtil.isGammasConsistent(lev, l, deltaEG,false));
					}
					*/
					
					if(l.nGammas()>0 && !EnsdfUtil.isGammasConsistent(lev, l, deltaEG,false) && !l.XREFS().contains(currentENSDFXTag)) {
					        tempLevels.remove(l);
					    
					}
						
				}
				matched.clear();
				matched.addAll(tempLevels);
			}
			
			/*
	         //debug
            if(lev.ES().contains("5231.2"))
            //if(Math.abs(lev.EF()-16600)<=400)// && lev.JPiS().contains("4+"))
            {    System.out.println("In ConsistencyCheck line 3870: ES="+lev.ES()+" J="+lev.JPiS()+" nmatched adopted="+matched.size()+" ensXTag="+currentENSDFXTag+" dsid="+currentENSDF.DSId0()+lev.q().equals("?"));
                 for(Level l:matched) System.out.println("  level:"+l.ES()+" JPI="+l.JPiS());
            }
            */
			
			//no matched found with both matching energy and consistent JPI,
			//then search for those with matching energy only
			if(matched.size()==0 && Str.isNumeric(lev.ES())){

				msgText="level not placed in Adopted";		
				if(lev.ES().contains(".")){
					msgText+=" or discrepant";
				}
				msgText+=".";
				
				if(lev.q().equals("?") ) {
					msgText="questionable "+msgText;
				}
				
				//still no matched found with matching energy within default error, 
				//(see isComparableE() for how the error range is determined),
				//then find the one with closest energy
				Level clev=(Level)EnsdfUtil.findClosestByEnergyValue(lev,adopted.levelsV());
				
				boolean found=false;
				if(clev!=null){
					
					int index=adopted.levelsV().indexOf(clev);
					int start=Math.max(0, index-5);
					int end=Math.min(index+5,adopted.levelsV().size()-1);
					
					for(int i=start;i<=end;i++) {
						Level l=adopted.levelsV().get(i);
						String xrefS=l.XREFS();
						String tag=currentENSDFXTag;
						int n1=xrefS.indexOf(tag+"(");
						
						//System.out.println(" start="+start+" end="+end+" lev.es="+lev.ES()+" i="+i+" l.es="+l.ES()+" xref="+xrefS+" tag="+tag+" n1="+n1);
						
						if(n1>=0) {
							int n2=xrefS.indexOf(")",n1);
							String s=xrefS.substring(n1+2,n2);
							s=s.replace("*", "").replace("?", "").trim();
							if(s.equals(lev.ES())) {
								found=true;
								matched.add(l);
							}
						}
					}
					
					if(!found) {
						msgText+="\n  closest level in Adopted:\n";
						msgText+=String.format("  %-10s%2s %-20s%s",clev.ES().trim(),clev.DES().trim(),clev.JPiS().trim(),"XREF="+clev.XREFS());
						
						if(EnsdfUtil.isComparableEnergyEntry(lev, clev,deltaEL, true) && EnsdfUtil.isOverlapJPI(lev.JPiS(), clev.JPiS())) {
							possibleAdoptedLev=clev;
						}
					}

				}else if(!Str.isNumeric(lev.ES())){//SN+1234.1, SP+1234.1, X+1234.1
					return null;
				}

				if(!found) {
					printToMessage(9,msgText,"W");
					return null;
				}

			}
		}

		if(matched.size()==1){
			levelXRef=EnsdfUtil.parseLevelXREF(matched.elementAt(0));//level.XREF() return original string following "XREF=", it could be "+" or something like "-(AB)"

			Level tempAdoptedLevel=matched.get(0);
			
			/*
			//debug
			if(lev.ES().contains("592")) {
				System.out.println(" In ConsistencyCheck 965: matched adopted level="+matched.get(0).ES()+" levelXRef="+levelXRef+" currentENSDFXTag="+currentENSDFXTag);
				System.out.println("   isComparableEnergyEntry(lev, tempAdoptedLevel)="+EnsdfUtil.isComparableEnergyEntry(lev, tempAdoptedLevel)+" adopted="+tempAdoptedLevel.ES());
			}
			*/
			
			if(!levelXRef.contains(currentENSDFXTag)){				
				msgText="Tag="+currentENSDFXTag+" of this dataset missing in XREF of matched Adopted Level="+matched.elementAt(0).ES();
				
				if(lev.q().equals("") && Str.isNumeric(lev.ES()))
					printToMessage(9,msgText,"E");
				else if(lev.q().equals("?"))
					printToMessage(9,msgText,"W");
				
				//System.out.println("3077: message="+message);
				
				//return null;
			}else if(!EnsdfUtil.isComparableEnergyEntry(lev, tempAdoptedLevel) && Math.abs(lev.ERF()-tempAdoptedLevel.ERF())>1 ) {
				float e1=lev.ERPF();
				float e2=tempAdoptedLevel.ERPF();				
				float largeUnc=EnsdfUtil.findALargeEnergyUncertainty(e1);
				float smallUnc=EnsdfUtil.findDefaultEnergyUncertainty(lev.ES(),"");
				
				largeUnc=Math.min(largeUnc,5*deltaEL);
				largeUnc=Math.max(largeUnc,lev.DEF());
				
				smallUnc=Math.max(smallUnc,3*deltaEL);
				smallUnc=Math.max(smallUnc,lev.DEF());

				if(smallUnc<lev.DEF())
					smallUnc=lev.DEF();
				
				
				//debug
				//if(lev.ES().contains("13141")) {
				//	System.out.println(" In ConsistencyCheck 4282: matched adopted level="+matched.get(0).ES()+" levelXRef="+levelXRef+" currentENSDFXTag="+currentENSDFXTag);
				//	System.out.println("   e1="+e1+" e2="+e2+"   largeUnc="+largeUnc+" smallUnc="+smallUnc+" adopted="+tempAdoptedLevel.ES());
				//}
				
				
				float diff=Math.abs(e1-e2);
				String s1=tempAdoptedLevel.ES()+" "+tempAdoptedLevel.DES();
				String s2=lev.ES()+" "+lev.DES();
				s1=s1.trim();
				s2=s2.trim();
				
				if(diff>largeUnc) {
					msgText ="Matching Adopted energy is largely discrepant: "+s1+"   XREF="+tempAdoptedLevel.XREFS()+"\n";
					msgText+="                            this level energy: "+s2+"   XREF tag="+currentENSDFXTag+"\n";
					msgText+="Check if level matching (XREF) is correct";
					printToMessage(9,msgText,"W");
				}else if(diff>smallUnc) {
					msgText ="Matching Adopted energy is discrepant: "+s1+"   XREF="+tempAdoptedLevel.XREFS()+"\n";
					msgText+="                    this level energy: "+s2+"   XREF tag="+currentENSDFXTag+"\n";
					msgText+="Check if level matching (XREF) is correct";
					printToMessage(9,msgText,"W");
				}
			}
		    
			String ES=tempAdoptedLevel.ES();
			if(scannedLevelXRefMap.containsKey(ES)){
				scannedLevelXRef=scannedLevelXRefMap.get(ES);
				scannedLevelXRef=scannedLevelXRef+currentENSDFXTag;
			    scannedLevelXRefMap.put(ES, scannedLevelXRef);
			}
		    
			adoptedLevel=tempAdoptedLevel;
			matchedAdoptedLevels.add(adoptedLevel);	

		}
		else if(matched.size()>1){
			msgText=matched.size()+" possible matches found in Adopted! Check XREF";
			boolean printWarningMsg=false;
			boolean isXTagMissing=true;
			int nAssigned=0;
			int matchedIndex=-1;
			int nBadAssigned=0;
			int nMissedAssigned=0;
			
			for(int i=0;i<matched.size();i++){
				//debug
				//System.out.println("In ConsistencyCheck 451: dsid="+currentENSDF.DSId0()+" E="+lev.EF()+" JPI="+lev.JPiS()+" matched Adopted:"+matched.get(i).ES()+"  "+matched.get(i).JPiS());
				
				Level adoptedLev=matched.get(i);
				String at="  ",badMark=" ";
				//levelXRef=EnsdfUtil.parseLevelXREF(l);
				levelXRef=adoptedLev.XREFS();
				
				//System.out.println("  #### xref="+levelXRef+"  lev="+lev.ES()+" currentENSDFXTag="+currentENSDFXTag+"  l.xref="+l.XREFS());
				
				if(isLevelInXREFOfMatchedAdoptedLevel(lev,adoptedLev)) {
					isXTagMissing=false;
					at="@ ";
					nAssigned++;
					matchedIndex=i;
					
					if(!EnsdfUtil.isComparableEnergyEntry(lev, adoptedLev,50)) {
						nBadAssigned++;
						badMark="#";
						//System.out.println("ConsistencyCheck 4314: this lev="+lev.ES()+" adopted="+adoptedLev.ES()+" deltaEL="+deltaEL);
					}
					//System.out.println("this lev="+lev.ES()+" adopted="+adoptedLev.ES()+" "+EnsdfUtil.isComparableEnergyEntry(lev, adoptedLev));
					
				}else if(EnsdfUtil.isComparableEnergyEntry(lev, adoptedLev)) {
					nMissedAssigned++;
					badMark="#";
				}
				msgText+="\n"+String.format("%s%-10s%2s %-20s%s",badMark+at,adoptedLev.ES().trim(),adoptedLev.DES().trim(),adoptedLev.JPiS().trim(),"XREF="+adoptedLev.XREFS());

			}
	        
			
			String msgType="W",extraMsg="";
			if(isXTagMissing){
				//no level in matched contains this level in the XREF
				//search other energy-matching levels around to see if they have this level in their XREF
				if(EMatched==null) 
					EMatched=findEnergyMatchedLevelsInAdopted(lev);
				
				String at="@ ";
				int tempN=0;
				extraMsg="";
				for(Level adoptedLev:EMatched){
					if(matched.contains(adoptedLev) || !isLevelInXREFOfMatchedAdoptedLevel(lev,adoptedLev))
						continue;
					
					tempN++;
					extraMsg+="\n"+String.format("%s%-10s%2s %-20s%s",at,adoptedLev.ES().trim(),adoptedLev.DES().trim(),adoptedLev.JPiS().trim(),"XREF="+adoptedLev.XREFS());
				}
				msgText+="\nBut tag="+currentENSDFXTag+" of this dataset is not in XREF of any of them!";
				if(lev.q().equals(""))
					msgType="E";
				else if(lev.q().equals("?"))
					msgType="W";
				
				if(extraMsg.length()>0) {
					String tempS="";
					if(tempN>1)
						tempS="s";
					
					msgType="W";
					
					if(lev.nGammas()>0)
						msgText+="\nHowever it is in XREF of the following level"+tempS+" with inconsistent JPI or Gamma:\n";
					else
						msgText+="\nHowever it is in XREF of the following level"+tempS+" with inconsistent JPI:\n";
					
					msgText+=extraMsg;
				}
	
				printWarningMsg=true;
			}else if((nBadAssigned+nMissedAssigned)>0) {
				printWarningMsg=true;
				
				String keyword="";
				if(nBadAssigned>0 && nMissedAssigned>0)
					keyword="missing and incorrect";
				else if(nBadAssigned>0)
					keyword="incorrect";
				else
					keyword="missing";
				
				msgText+="\n*** Check for possible "+keyword+" XREF assignment (# marked) of this tag="+currentENSDFXTag;
			}
			
			//System.out.println(" "+msgType+"  "+msgText);
			
			if(msgType.equals("E"))
				printToMessage(9,msgText,msgType);
			else
				XREFWarningMsg=makeMessageLines(9,msgText,msgType)+"\n";
			
			for(int i=0;i<matched.size();i++){
				String ES=matched.elementAt(i).ES();
				if(scannedLevelXRefMap.containsKey(ES)){
					scannedLevelXRef=scannedLevelXRefMap.get(ES);
					scannedLevelXRef=scannedLevelXRef+currentENSDFXTag;
				    scannedLevelXRefMap.put(ES, scannedLevelXRef);
				}
			}

	        /*
            //debug
            if(lev.ES().equals("3119"))
            //if(Math.abs(lev.EF()-16600)<=400)// && lev.JPiS().contains("4+"))
            {    System.out.println("In ConsistencyCheck line 4396: ES="+lev.ES()+" J="+lev.JPiS()+" nmatched adopted="+matched.size()+" ensXTag="+currentENSDFXTag+" dsid="+currentENSDF.DSId0()+lev.q().equals("?"));
                 System.out.println(" ixXTagMissing="+isXTagMissing+" nBadAssigned="+nBadAssigned+" nMissedAssigned="+nMissedAssigned+" printWarningMsg="+printWarningMsg);
                 //System.out.println("msg="+msgText+"  msgType="+msgType+" xref warningMsg="+XREFWarningMsg);
                 System.out.println(" nAssigned="+nAssigned+" matchedIndex="+matchedIndex);
                for(Level l:matched) System.out.println("  level:"+l.ES()+" JPI="+l.JPiS());
            }
            */
			
			matchedAdoptedLevels.addAll(matched);
			
			if(nAssigned==1)
				adoptedLevel=matched.get(matchedIndex);
			else if(nAssigned>1 || extraMsg.length()>0){
				if(msgType.equals("W") && printWarningMsg)//misType="E" message has been printed above, print warning message in .err
					printToMessage(9,msgText,msgType);
				
				adoptedLevel=matched.get(0);
			}
		}
		
		return adoptedLevel;
	}
	
	//note: findAdoptedLevel(lev) must be called before this call
	public Gamma findAdoptedGamma(Level lev,Gamma gam){
		adoptedGamma=null;
		possibleAdoptedGam=null;
		
		String msgText="";
		
		//Vector<Gamma> matched=new Vector<Gamma>();
		   
		
        //debug
        //if(gam.ES().equals("2191.2"))
        //	System.out.println("ConsistencyCheck 4178: lev="+lev.ES()+" "+lev.NNES()+" gam="+gam.ES()+"   "+gam.isGotoAdopted()+" adopt Lev==null:"+(adoptedLevel==null));
		
		
		if(!gam.isGotoAdopted())
			return adoptedGamma;
		
		if(adoptedLevel!=null && lev!=null){
			
			/*
			float diff=200;
			if(adoptedLevel.NNES().equals(lev.NNES()))
				diff=Math.abs(adoptedLevel.ERF()-lev.ERF());
			else if(adoptedLevel.NNES().isEmpty())
				diff=Math.abs(adoptedLevel.ERPF()-lev.ERPF());
			*/
			
	        //debug
			//if(lev.ES().contains("18.1E3"))
	        //if(lev.ES().contains("+") || lev.ES().contains("x"))
	        //	System.out.println("0 lev="+lev.ES()+" "+lev.NNES()+" adopted lev="+adoptedLevel.ES()+" "+adoptedLevel.NNES()+" gam="+gam.ES()+"  "+(adoptedLevel==null)+" diff="+diff);
	        
			//if(diff>100)
			//	return null;
			
			
			//find all gammas in adopted dataset that match the given gamma
            Vector<Record> matchedGammas=new Vector<Record>();
            matchedGammas.addAll(findMatchedGammasInAdopted(gam,adoptedLevel));   
            //matchedGammas=EnsdfUtil.findMatchesByEnergyEntry(gam, adoptedLevel.GammasV(), deltaEG,false);
            
            /*
            //debug
            if(lev.ES().contains("25988") && gam.ES().equals("3374.6")) {
            //if(lev.ES().contains("+") || lev.ES().contains("z")){
            	System.out.println("ConsistencyCheck 4174:  lev="+lev.ES()+"  gam="+gam.ES()+"  nmatched="+matchedGammas.size());
            	for(Record g:matchedGammas) System.out.println("    matched gamma: "+g.ES());
            }
            */
            
            int nMatched=matchedGammas.size();
			if(nMatched==0){
				
			    //note that the parent level of this gam here have an corresponding adoptedLevel in the Adopted dataset
			    //which could be from the existing XREF of that adopted level which has xref of the dataset where this gam is from.
			    //But since the code will re-group all datasets and not necessarily take what exists (like XREF) in the Adopted dataset,
			    //the parent level of this gam might not be grouped as it is in the existing XREF, instead it could be considered as a different level
			    //because of very different JPI. This will result in the following situation:
			    // 1. The parent level of this gamma has a correponding adopted level (which is the input adoptedLevel here)
			    // 2. The re-grouping by the code doesn't include the parent level of this gamma in the levelGroupsV of the adoptedLevel
			    // Therefore, no matched adopted gammas will be found for this gam
				
				boolean found=false;
				
				if(Str.isNumeric(gam.ES()) && gam.q().length()==0 && Str.isNumeric(lev.ES())){
					if(matchedAdoptedLevels.size()>1) {
						Vector<Level> tempLevelsV=new Vector<Level>();
						tempLevelsV.addAll(matchedAdoptedLevels);
						tempLevelsV.remove(adoptedLevel);
						
						
						for(Level l:tempLevelsV) {
							matchedGammas=EnsdfUtil.findMatchesByEnergyEntry(gam, l.GammasV(), deltaEG,false);
							nMatched=matchedGammas.size();
							if(nMatched>0) {
								adoptedLevel=l;
								found=true;
								break;
							}
						}
					}
				}
				if(!found) {
					
					//check adoptedLevel again here
					matchedGammas=EnsdfUtil.findMatchesByEnergyEntry(gam, adoptedLevel.GammasV(), deltaEG,false);
					nMatched=matchedGammas.size();
					if(nMatched==0) {
						adoptedGamma=null;
						if(gam.q().isEmpty()) {
							msgText+="This gamma is missing in Adopted Gammas at level="+adoptedLevel.ES()+"\n";
							printToMessage(9,msgText,"W");
						}
		
						return null;
					}
				}
			}
			
			
			if(nMatched>0){		
				adoptedGamma=(Gamma)matchedGammas.get(0);
				Gamma closestLevGamma=(Gamma)EnsdfUtil.findClosestByEnergyValue(adoptedGamma,lev.GammasV());	
	
				double diffEG=Math.abs(adoptedGamma.EF()-gam.EF());
				double maxDiff=1.5*Math.max(gam.DEF(), adoptedGamma.DEF());
				maxDiff=Math.max(maxDiff, 2);
				
				/*
	            //debug
	            if(lev.ES().contains("3049") && gam.ES().equals("764")) {
	            	System.out.println("ConsistencyCheck 4224:  lev="+lev.ES()+"  gam="+gam.ES()+"  nmatched="+matchedGammas.size()+" closestLevGamma="+closestLevGamma);
	            	for(Record g:lev.GammasV()) System.out.println("    level gamma: "+g.ES());
	            }
				*/
				
				if(nMatched==1){//check if the found adoptedGamma is associated with other gamma from the same level				
				    msgText="";
					if(closestLevGamma!=gam) {	
						if(gam.q().isEmpty()) {
							msgText="This gamma is probably missing in Adopted Gammas at level="+adoptedLevel.ES()+"\n";
							msgText+="    this gamma="+gam.ES()+"      closest gamma in Adopted="+adoptedGamma.ES()+"\n";
						}

						adoptedGamma=null;		
					}else if(!EnsdfUtil.isComparableEnergyEntry(gam,adoptedGamma) && diffEG>=maxDiff ){				
						msgText ="Check discrepancy in gamma energies:\n";
						msgText+="    this energy="+gam.ES()+"      Adopted="+adoptedGamma.ES()+"\n";
					}
					
					if(msgText.length()>0)
						printToMessage(9,msgText,"W");
				}else{				
					Vector<Record> tempLevGammas=EnsdfUtil.findMatchesByEnergyEntry(adoptedGamma, lev.GammasV(), deltaEG,false);	
					int index=tempLevGammas.indexOf(gam);
					if(tempLevGammas.size()==nMatched && index>=0) {//cases like, two gammas are very close
						adoptedGamma=(Gamma)matchedGammas.get(index);
					}else{//could need more work for this case
						adoptedGamma=(Gamma)EnsdfUtil.findClosestByEnergyEntry(gam,adoptedLevel.GammasV(), deltaEG,false);	
						
						diffEG=Math.abs(adoptedGamma.EF()-gam.EF());
						
						msgText="";
						if(!EnsdfUtil.isComparableEnergyEntry(gam,adoptedGamma) && diffEG>=maxDiff){	
						//if(gam.ES().contains(".") && Math.abs(adoptedGamma.EF()-gam.EF())>=2.0){
							msgText ="Check discrepancy in gamma energies:\n";
							msgText+="    this energy="+gam.ES()+"      closest Adopted="+adoptedGamma.ES()+"\n";
						}
						
						if(msgText.length()>0)
							printToMessage(9,msgText,"W");
					}
				}
			}
			
			/*old
			adoptedGamma=(Gamma)EnsdfUtil.findClosestMatchByEnergy(gam.ES(),adoptedLevel.GammasV(), deltaEG);	
			if(adoptedGamma==null){
				if(Str.isNumeric(gam.ES()) && gam.q().length()==0){
					msgText+="This gamma is missing in Adopted Gammas\n";
					printToMessage(9,msgText,"W");
				}
			}else{//check if the found adoptedGamma is associated with other gamma from the same level
				Gamma tempGamma=(Gamma)EnsdfUtil.findClosestMatchByEnergy(adoptedGamma.ES(),lev.GammasV(),deltaEG);
				if(tempGamma!=gam)
					adoptedGamma=null;
			}
			*/
		}else if(possibleAdoptedLev!=null && lev!=null) {
			Gamma cgam=(Gamma)EnsdfUtil.findClosestByEnergyEntry(gam,possibleAdoptedLev.GammasV(), deltaEG,false);	
			if(cgam!=null) {
				if(EnsdfUtil.isComparableEnergyEntry(gam, cgam,deltaEG, true)) {
					possibleAdoptedGam=cgam;
				}
			}
		}
		//if(lev.ES().contains("3049") && gam.ES().equals("764"))
		//       System.out.println("ConsistencyCheck 4273: adopted gamma==null "+(adoptedGamma==null));
			
		return adoptedGamma;
	}
	
	//find corresponding adopted level to the input level with matching energy
	private Vector<Level> findEnergyMatchedLevelsInAdopted(Level lev){
		if(currentEnsdfGroup!=null)
			return currentEnsdfGroup.findEnergyMatchedAdoptedLevels(lev);	
		return null;
	}
	
	@SuppressWarnings("unused")
	private Vector<Level> findEnergyMatchedLevelsInAdopted(Level lev,float delta){
		if(currentEnsdfGroup!=null)
			return currentEnsdfGroup.findEnergyMatchedAdoptedLevels(lev,delta);	
		return null;
	}
	
	//find corresponding adopted level to the input level with matching energy and consistent JPI
	private Vector<Level> findMatchedLevelsInAdopted(Level lev){
		if(currentEnsdfGroup!=null)
			return currentEnsdfGroup.findMatchedAdoptedLevels(lev,this.currentENSDFXTag);	
		return null;
	}

	//find corresponding adopted gamma to the input gamma with matching energy
	private Vector<Gamma> findEnergyMatchedGammasInAdopted(Gamma gam){
		if(currentEnsdfGroup!=null)
			return currentEnsdfGroup.findEnergyMatchedAdoptedGammas(gam);	
		return null;
	}
	
	private Vector<Gamma> findEnergyMatchedGammasInAdopted(Gamma gam,float delta){
		if(currentEnsdfGroup!=null)
			return currentEnsdfGroup.findEnergyMatchedAdoptedGammas(gam,delta);	
		return null;
	}
	
	//find corresponding adopted gamma to the input gamma from ENSDF grouping
	private Vector<Gamma> findMatchedGammasInAdopted(Gamma gam, Level adoptedLev){
		if(currentEnsdfGroup!=null)
			return currentEnsdfGroup.findMatchedAdoptedGammas(gam,adoptedLev);	
		return null;
	}
	
	//find what records are from Adopted data according to general
	//comments and store their names in fromAdopted
	private HashMap<String,String> findFromAdopted(ENSDF ens){
		HashMap<String,String> tempMap1=mapOfensFromAdoptedRecordNameMap.get(ens);
		HashMap<String,String> tempMap2=mapOfensFootnoteRecordNameMap.get(ens);
		
		currentFromAdoptedRecordNameMap.clear();
		currentFootnotedRecordNameMap.clear();
		
		if(tempMap1!=null) {
			currentFromAdoptedRecordNameMap.putAll(tempMap1);
			currentFootnotedRecordNameMap.putAll(tempMap2);
			
			return currentFromAdoptedRecordNameMap;
		}

		//System.out.println("####################"+ens.DSId0()+"  "+mapOfensFromAdoptedRecordNameMap.containsKey(ens));
		
		HashMap<String,String> tempMap=new HashMap<String,String>();
		try{
			for(int i=0;i<ens.comV().size();i++){
				Comment c=ens.commentAt(i);
				
				if(c.type().trim().equals("P"))
					continue;
				
				tempMap.clear();
				if(c.nHeads()>0){			
					for(int j=0;j<c.nHeads();j++){
						String flags=c.flagV().get(j).replace(")","").replace("(","").replace(",","");
						String headName=c.headAt(j);
						String type=c.type().trim();
						if(headName.equals("E")){
							headName+=type;
						}
						//else if(type.equals("P")){
						//	if(headName.equals("J") || headName.equals("T"))
						//		headName+=type;
						//}
						
						if(tempMap.containsKey(headName)){
							if(flags.isEmpty()){
								if(tempMap.get(headName).indexOf(" ")<0)
									flags=" "+tempMap.get(headName);
							}else
								flags=tempMap.get(headName)+flags;
						}
						
						if(flags.isEmpty())
							flags=" ";
						
						tempMap.put(headName,flags);
						
					    //debug
					    //if(ens.DSId0().contains("28SI") && headName.equals("T")) System.out.println(" DSID="+ens.DSId0()+" headName="+headName+" flags="+flags+" s="+s);
					}
					
					//if(ens.DSId().contains("76GE(11B,A3NG)")) System.out.println("*****"+c.head()+"  "+c.body());
				}
				
				if(tempMap.size()>0){
				    for(String key:tempMap.keySet()) {
				        String flag=currentFootnotedRecordNameMap.get(key);
				        if(flag==null)
				            flag="";
				        
				        currentFootnotedRecordNameMap.put(key, flag+tempMap.get(key));			            
				    }

					if(isCommentIndicateQuotedFromAdopted(c)) {
												
	                    for(String key:tempMap.keySet()) {
	                        String flag=currentFromAdoptedRecordNameMap.get(key);
	                        if(flag==null)
	                            flag="";
	                        
	                        //if(ens.DSId().contains("76GE(11B,A3NG)")) System.out.println("*****"+c.head()+"  "+c.body()+"  key="+key+" flag="+flag+"$ "+
	                        //		tempMap.get(key));
	                        
	                        currentFromAdoptedRecordNameMap.put(key, flag+tempMap.get(key));                      
	                    }					    
					}
				}

			}
		}catch(Exception e){
			e.printStackTrace();
		}

		tempMap1=new HashMap<String,String>();
		tempMap2=new HashMap<String,String>();
		
		tempMap1.putAll(currentFromAdoptedRecordNameMap);
		tempMap2.putAll(currentFootnotedRecordNameMap);
		mapOfensFromAdoptedRecordNameMap.put(ens, tempMap1);		
		mapOfensFootnoteRecordNameMap.put(ens,tempMap2);
		
		return currentFromAdoptedRecordNameMap;
	}
	
	/*
	 * e.g.
	 * 1."From Adopted Level", "From Adopted Gammas", "From the Adopted Levels", "From Adopted dataset", "From Adopted"
	 *   contains "From Adopted" or "From the Adopted" at the beginning
	 * 2."From XXXXX, same as values in Adopted Gammas"; "From XXXX. Quoted values are adopted in Adopted Gammas";
	 *   "From XXXX. Values from the Adopted Gammas are the same".
	 */
	public boolean isCommentIndicateQuotedFromAdopted(Comment c) {
		String s=c.body().trim().toUpperCase();		
		if(!s.contains("ADOPTED"))
			return false;
		
		if(s.indexOf("FROM ADOPTED")==0 || s.indexOf("FROM THE ADOPTED")==0)
			return true;
		
		s=Str.firstSentance(s);
		
		//if(s.startsWith("PROPOSED") || s.startsWith("FROM") || s.startsWith("DEDUCED") || s.startsWith("BASED") 
		//		|| s.startsWith("IMPLIED") || s.startsWith("SPIN FROM") || s.startsWith("ASSIGNMENT"))
		if(s.startsWith("PROPOSED") || s.startsWith("DEDUCED") || s.startsWith("BASED") 
				|| s.startsWith("IMPLIED") || s.startsWith("SPIN FROM") || s.startsWith("PARITY FROM"))
			return false;
		
		if(s.startsWith("FROM") && s.contains("ADOPTED")) {
			int n=s.indexOf("ADOPTED");
			String s1=s.substring(4,n).trim();
			if(!s1.contains(" ")) {
				s1=s1.replace("{","").replace("}","").replace("+","");//From {+80}Sb Adopted Levels
				if(Nucleus.isNucleus(s1))
					return true;
			}
		}
		/*
		while(true) {
		    int n=s.indexOf(".");
		    if(n<=0 || n==s.length()-1)
		        break;
		    
		    if(Character.isDigit(s.charAt(n-1)) && Character.isDigit(s.charAt(n+1)))
		        s=s.substring(0,n);
		}
		*/
		
		String[] lines=s.split("[.;]+");
		for(int i=0;i<lines.length;i++) {
			s=lines[i].trim();
			
			//System.out.println("1 line="+s);
			
			if(!s.contains("ADOPTED"))
				continue;	
			else if(s.contains("FROM ADOPTED") || s.contains("FROM THE ADOPTED")) {
				//exception: "12.3 {I3} from Adopted Gammas" or "5/2+ from Adopted Gammas"
				//           "values from Adopted Gammas are: xxxxxx"
				//           "values under comments are from Adopted Gammas"
				
				int n=s.indexOf("FROM ADOPTED");
				int l=8;
				if(n<0) {
					n=s.indexOf("FROM THE ADOPTED");
					l=12;
				}
				
	            //debug         
	            //if(c.body().toUpperCase().contains("LEVEL SCHEME")){	                
	            //    System.out.println(" s="+s+"  c="+c.body());
	            //}
	            
				String s1=s.substring(0,n);
				String s2=s.substring(n+l);
				if(!Str.containAny(s1+s2, "0123456789")) {
					if(s.contains("COMMENT") || s.contains("DIFFERENT") || s.contains("CONTRADICT") 
					        || s1.contains("LEVEL SCHEME") || s1.contains("PLACE") || s1.contains("POPULAT") || s1.contains("CORRESPOND") )// || s1.contains("OTHERS") || s1.contains("REST"))
						return false;					
					
					if(c.head().equals("M")){
						s=(s1+" "+s2).replace("MULT","").replace("=", "").replace("(", "").replace(")","").trim();
						List<String> ms=Arrays.asList(s.split("[,\\s]+"));
						if(ms.contains("D") || ms.contains("Q") || ms.contains("D+Q") || ms.contains("Q+D"))
							return false;
					}
					
					//System.out.println("**2 line="+s);
					
					return true;
				}
				
			}else if(s.contains("NOT ADOPTED IN ADOPTED") || s.contains("NOT ADOPTED IN THE ADOPTED"))
				return false;
			else if(s.contains("ADOPTED IN ADOPTED") || s.contains("ADOPTED IN THE ADOPTED")) {
				//exception: "12.3 {I3} Adopted in Adopted Gammas" or "5/2+ Adopted in Adopted Gammas"
				//           "values adopted in Adopted Gammas are: xxxxxx"
				//           "values under comments adopted in Adopted Gammas"
				
				int n=s.indexOf("ADOPTED IN");
				String s1=s.substring(0,n);
				String s2=s.substring(n+10);
				if(!Str.containAny(s1+s2, "0123456789")) {
					if((s1+s2).contains("COMMENT"))
						return false;
					
					if(c.head().equals("M")){
						s=(s1+" "+s2).replace("MULT","").replace("=", "").replace("(", "").replace(")","").trim();
						List<String> ms=Arrays.asList(s.split("[,\\s]+"));
						if(ms.contains("D") || ms.contains("Q") || ms.contains("D+Q") || ms.contains("Q+D"))
							return false;
					}
					
					return true;
				}
			}else if(s.contains("SAME") && !s.contains("NOT ") && !s.contains("EXCEPT"))
				return true;
		}

					
		return false;
	}
	
	/*
	 * values like,
	 * 123.4
	 * 123.4 US 12
	 * 123.4 12
	 */
	private String extractNumericalValue(String s) {
		String s0=s;
		if(!Str.containAny(s, "0123456789"))
			return "";
		
		for(int i=0;i<s0.length();i++) {
			char c=s0.charAt(i);
			if(Character.isDigit(c)) {
				if(i>0 && "+-(".indexOf(s0.charAt(i-1))>=0) 
					s=s0.substring(i-1);
				else
					s=s0.substring(i);
				
				int p1=s0.indexOf("{",i-3);
				int p2=s0.indexOf("}",i-3);
				if(p1>=0 && p1<i && p2>i) {//skip numbers inside {}
				    i=p2+1;
				    continue;
				}
				
				break;
			}
		}
		
		for(int i=s.length()-1;i>=0;i--) {
						
			char c=s.charAt(i);
			if(Character.isDigit(c)) {
				if(i<s.length()-2 && s.charAt(i+1)==')' && "+-".indexOf(s.charAt(i+2))>=0)
					s=s.substring(0,i+3);
				else if(i<s.length()-2 && "+-".indexOf(s.charAt(i+1))>=0 && s.charAt(i+2)==')')
                    s=s.substring(0,i+3);
				else if(i<s.length()-1 && "+-)".indexOf(s.charAt(i+1))>=0)
				    s=s.substring(0,i+2);
				else
					s=s.substring(0,i+1);
				
				
				s=s.replace("{I", "").replace("}", "").trim();
				break;
			}
		}

        String[] a=s.split("[\\s]+");
        if(a.length<=3 && a.length>0) {
            if(Str.isNumeric(a[0]) || a.length==1)
                return s;
       
        }
        
		return "";
	}
	
	
	private String extractMULTValue(String s) {
		ArrayList<String> list=new ArrayList<String>(Arrays.asList(
				"M1","M2","M3","E1","E2","E3","M1+E2","E2+M1","E1+M2","M2+E1","M2+E3","E3+M2",
				"D","Q","O","D+Q","Q+D","Q+O"
				));
		
		s=s.replace("MULT", "").replace("=", "").replace(":", "");
		
		String[] ms=s.split("[\\s]+");//not split "," because there is MULT like M1,E2
		for(int i=0;i<ms.length;i++) {
			String tempS=ms[i].replace("(", "").replace(")","").trim();
			if(list.contains(tempS))
				return ms[i].trim();
		}
				
		return "";
	}
	
	private String extractJPIValue(String s) {
		if(!Str.containAny(s, "0123456789"))
			return "";
		
		return JPI.extractJPS(s);
	}
	
	public String extractAdoptedValueInComment(Comment c) {
		String s=c.body().trim().toUpperCase();

		if(!s.contains("ADOPTED"))
			return "";
		
		//String[] lines=s.split("\\.[\\s]+");
		String[] lines=Str.specialSplit(s, "[.;]+");
		
		//System.out.println("  **"+lines.length+"  "+lines[0]);
		
		String s1="",s2="";
		for(int i=0;i<lines.length;i++) {
			s=lines[i].trim();
			
			//System.out.println("1 line="+s);
			int n=-1;
			if(!s.contains("ADOPTED")) 
				continue;	
			else if(s.contains("FROM ADOPTED") || s.contains("FROM THE ADOPTED")) {
				//exception: "12.3 {I3} from Adopted Gammas" or "5/2+ from Adopted Gammas"
				//           "values from Adopted Gammas are: xxxxxx"
				//           "values under comments are from Adopted Gammas"
				
				n=s.indexOf("FROM ADOPTED");
				if(n<0) {
					n=s.indexOf("FROM THE ADOPTED");
					s1=s.substring(0,n);
					s2=s.substring(n+16);
				}else {
					s1=s.substring(0,n);
					s2=s.substring(n+12);
				}
			
				if(!s2.trim().startsWith("LEV") && !s2.trim().startsWith("GAM") && !s2.trim().startsWith("DATA"))
					s1="";
				
				s2="";
			}else if(s.contains("IN ADOPTED") || s.contains("IN THE ADOPTED")) {
				//exception: "12.3 {I3} Adopted in Adopted Gammas" or "5/2+ Adopted in Adopted Gammas"
				//           "values adopted in Adopted Gammas are: xxxxxx"
				//           "values under comments adopted in Adopted Gammas"
				
				n=s.indexOf("IN ADOPTED");
				if(n<0) {
					n=s.indexOf("IN THE ADOPTED");
					s1=s.substring(0,n);
					s2=s.substring(n+14);
				}else {
					s1=s.substring(0,n);
					s2=s.substring(n+10);
				}
							
				if(s1.trim().endsWith("ADOPTED")) {
					n=s1.trim().length();
					s1=s1.trim().substring(0,n-7);
				}
				s2="";
			}else if((n=s.indexOf("ADOPTED VALUE IS"))>=0 || (n=s.indexOf("ADOPTED VALUE:"))>=0 || (s.indexOf("ADOPTED VALUE="))>=0
					|| (n=s.indexOf("ADOPTED ASSIGNMENT IS"))>=0 || (n=s.indexOf("ADOPTED ASSIGNMENT:"))>=0 || (s.indexOf("ADOPTED ASSIGNMENT="))>=0) {
				
				s1="";
				s2=s.substring(n+7).replace("VALUE", "").replace("ASSIGNMENT", "").replace("=", "").replace(":", "").replace("IS","").trim();
				s2=s2.replace("PRELIMINARY", "").replace("PRELIMINARILY", "").replace("TENTATIVE", "").replace("TENTATIVELY", "").trim();
				
				String[] keyWords= {"DEDUCED","FROM","WEIGHTED","UNWEIGHTED","SUM","CALCULATED"};
				for(int j=0;j<keyWords.length;j++) {
					if(s2.startsWith(keyWords[j])) {
						s2="";
						break;
					}
						
				}
				
			}
			
			//System.out.println("2  s1="+s1+" s2="+s2);
			
			String head=c.head();
			if("J,T".contains(head)&&s.contains("ADOPTED GAMMAS"))
				return "";
			
			//extracting values
			if(head.equals("M")) {
				s=extractMULTValue(s1);
				if(s.isEmpty())
					s=extractMULTValue(s2);
				
				return s;
			}else if(head.equals("J")){
				s=extractJPIValue(s1);
				if(s.isEmpty() && !s2.isEmpty())
					s=extractJPIValue(s2);
			}else if(Str.containAny(s1,"0123456789")!=Str.containAny(s2, "0123456789")){
				s=extractNumericalValue(s1+" "+s2);
				
				//System.out.println("s1="+s1+" s2="+s2+" s="+s);
				
				return s;
			}
		}

					
		return "";
	}
	
	//find Adopted dataset of parent nuclei from decay dataset ens
	//in chain
	private ENSDF findParent(ENSDF ensdf){
		if(!ensdf.DSId0().contains("DECAY") && ensdf.parentsV().size()==0)
			return null;
		
		String parentNUCID=ensdf.parentAt(0).nucleus().nameENSDF().trim();
		
		for(int i=0;i<chain.nENSDF();i++){
			ENSDF ens=chain.getENSDF(i);
			String NUCID=ens.nucleus().nameENSDF().trim();
			if(NUCID.equals(parentNUCID) && ens.DSId0().contains("ADOPTED LEVELS"))
				return ens;
		}
		
		return null;
	}
	
	private Level fLevel(Gamma g, ENSDF ens){
		try{
			return ens.levelAt(g.FLI());
		}catch(Exception e){}
		
		return null;
	}
	

	private Level iLevel(Gamma g, ENSDF ens){
		try{
			return ens.levelAt(g.ILI());
		}catch(Exception e){}
		
		return null;
	}
	
	/////////////////////////////////////////////////////////////////////////
	//not needed now
	@SuppressWarnings("unused")
	private String findLevelLine(Level lev){
		int iLevel=currentENSDF.levelsV().indexOf(lev);
		int iLevelLine=currentLineFinder.indexOfLevelLine(iLevel);
		return currentENSDF.lineAt(iLevelLine);
	}
	@SuppressWarnings("unused")
	private String findGammaLine(Level lev,Gamma gam){
		int iLevel=currentENSDF.levelsV().indexOf(lev);
		int iGamma=lev.GammasV().indexOf(gam);
		int iGammaLine=currentLineFinder.indexOfGammaLine(iLevel,iGamma);
		return currentENSDF.lineAt(iGammaLine);
	}
	@SuppressWarnings("unused")
	private String findDecayLine(Level lev,Decay decay){
		int iLevel=currentENSDF.levelsV().indexOf(lev);
		int iDecay=lev.DecaysV().indexOf(decay);
		int iDecayLine=currentLineFinder.indexOfDelayLine(iLevel,iDecay);
		return currentENSDF.lineAt(iDecayLine);
	}
	@SuppressWarnings("unused")
	private String findDelayLine(Level lev,DParticle delay){
		int iLevel=currentENSDF.levelsV().indexOf(lev);
		int iDelay=lev.DParticlesV().indexOf(delay);
		int iDelayLine=currentLineFinder.indexOfDelayLine(iLevel,iDelay);
		return currentENSDF.lineAt(iDelayLine);
	}
	@SuppressWarnings("unused")
	private String findUnpGammaLine(Gamma gam){
		int iGamma=currentENSDF.unpGammas().indexOf(gam);
		int iGammaLine=currentLineFinder.indexOfUnpGammaLine(iGamma);
		return currentENSDF.lineAt(iGammaLine);
	}
	@SuppressWarnings("unused")
	private String findUnpDecayLine(Decay decay){
		int iDecay=currentENSDF.unpDecaysV().indexOf(decay);
		int iDecayLine=currentLineFinder.indexOfUnpDecayLine(iDecay);
		return currentENSDF.lineAt(iDecayLine);
	}
	@SuppressWarnings("unused")
	private String findUnpDelayLine(Level lev,DParticle delay){
		int iDelay=currentENSDF.unpDParticles().indexOf(delay);
		int iDelayLine=currentLineFinder.indexOfUnpDecayLine(iDelay);
		return currentENSDF.lineAt(iDelayLine);
	}    
	/////////////////////////////////////////////////////////////////////////
	
	
	
	///////////////////////////////////////
	// all grouping and related functions
	///////////////////////////////////////
	
	/*
	 * simply group all dataset also including comment datasets by NUCID
	 * NO grouping for levels/gammas/decays/delays
	 */
    public Vector<EnsdfGroup> groupDatasets(MassChain data) throws Exception{
    	Vector<EnsdfGroup> ensdfGroupsV=groupENSDFs(data);
    	
    	String id="";
    	
    	for(int i=0;i<data.nBlocks();i++){
    		if(data.getETDByBlockAt(i)!=null)//block of ENSDF dataset
    			continue;
    		
    		try{
    			id=data.getBlockAt(i).get(0).substring(0,5).trim();
    			ENSDF ens=new ENSDF();
    			ens.setLittleValues(data.getBlockAt(i));
    			
    			boolean found=false;
        		//process non-ENSDF dataset, eg, comment dataset, cover page
        		for(int j=0;j<ensdfGroupsV.size();j++){
        			EnsdfGroup g=ensdfGroupsV.get(i);
                	if(g.NUCID().equals(id)){
                		found=true;
                		g.addENSDF(ens,0);
                		break;
                	}
        		}
        		
        		//if not found, there are two possibilities:
        		//1, the dataset is an abstract or reference dataset with NUCID=mass number
        		//2, the dataset is a comment or reference dataset for a nuclide with no reaction/decay dataset 
        		//   NUCID=nuclide ID (therefore no EnsdfGroup has been created for this nuclide)
        		if(!found){
        			EnsdfGroup newGroup=new EnsdfGroup();
        			newGroup.addENSDF(ens);
        			ensdfGroupsV.add(newGroup);
        		}
    		}catch(Exception e){   			
    		}

    	}
    	
    	ensdfGroupsV=sortEnsdfGroups(ensdfGroupsV);
    	
    	return ensdfGroupsV;
    }
	
    /*
     * group ENSDF dataset
     */
    public Vector<EnsdfGroup> groupENSDFs(MassChain data) throws Exception{
    	return groupENSDFs(data,false);
    }
    
    public Vector<EnsdfGroup> groupENSDFs(MassChain data,boolean deepGrouping) throws Exception{
    	Vector<EnsdfGroup> groups=new Vector<EnsdfGroup>();
    	
    	int nENSDFs=data.nENSDF();
        ENSDF ens;

        boolean added=false;
        String id="";
        
        for(int i=0;i<nENSDFs;i++){
            ens=data.getENSDF(i);
            
            if(ens.DSId0().contains("THEOR"))//skip theory dataset
                continue;
            

        	id=ens.nucleus().nameENSDF().trim().toUpperCase();

        	//System.out.println(" ConsitencyCheck 4525: dsid="+ens.DSId0());
        	
        	added=false;
        	for(int j=0;j<groups.size();j++){
        		EnsdfGroup g=groups.get(j);
        		if(id.equals(g.NUCID())){
        			g.addENSDF(ens);
        			added=true;
        			break;
        		}
        	}

        	if(!added){
        		EnsdfGroup newGroup=new EnsdfGroup();
        		newGroup.addENSDF(ens);       		
        		groups.add(newGroup);
        	}

        }

        for(int i=0;i<groups.size();i++){
        	EnsdfGroup g=groups.get(i);
        	
        	g.sortENSDFs();
        	
        	Nucleus nucleus=g.ensdfV().get(0).nucleus();
        	int A=nucleus.a();
        	int Z=nucleus.z();
        	if(Z>0 && Z%2==0 && A%2==0)
        		g.setIsEvenEven(true);
        	else
        		g.setIsEvenEven(false);     	
        }

        groups=sortEnsdfGroups(groups);
        
        //if deepGrouping=true, go further to group levels and their gammas based on energies (and JPI for levels)
        if(deepGrouping){
        	for(int i=0;i<groups.size();i++){
        		EnsdfGroup g=groups.get(i);
        		g.setDeltaEG(deltaEG);
        		g.setDeltaEL(deltaEL);
        		
        		//System.out.println(" ConsitencyCheck 4568: #1 igroup="+i+" size="+groups.size());
        		
        		g.doGrouping();      		
    
        		//System.out.println(" ConsitencyCheck 4568: #2 igroup="+i+" size="+groups.size());
        		//test
        		//printTest0(g);  
        		//printGammasByGamma(g);
        		//printGammasByLevel(g);
        		//printLevelsOnly(g);
        		//printGroupedLines(g);
        		//printAverageReport(g);
        	}
        }
              
        return groups;
    }
    
    public  Vector<EnsdfGroup> sortEnsdfGroups(Vector<EnsdfGroup> ensdfGroupsV){

    	try{
        	Vector<EnsdfGroup> newGroupsV=new Vector<EnsdfGroup>();

    		for(int i=0;i<ensdfGroupsV.size();i++){
    			
    			EnsdfGroup g=ensdfGroupsV.get(i);
    			Nucleus nuc=g.ensdfV().get(0).nucleus();
    			
        		int j=0;
        		for(j=0;j<newGroupsV.size();j++){
        			EnsdfGroup g1=newGroupsV.get(j);
        			Nucleus nuc1=g1.ensdfV().get(0).nucleus();

                	if(EnsdfUtil.compareNucleus(nuc,nuc1,"A")<0)
                		break;
        		}
        		
        		newGroupsV.insertElementAt(g, j);
    		}
    	
    		return newGroupsV;
    		
    	}catch(Exception e){
    		return ensdfGroupsV;
    	}
    	
    }
    
    public String printAverageReport(EnsdfGroup ensdfGroup){
    	String out="";
     	String prefixL=Str.makeENSDFLinePrefix(ensdfGroup.NUCID(), "cL");
     	String prefixG=Str.makeENSDFLinePrefix(ensdfGroup.NUCID(), "cG");
     	String el="",eg="";
     	String title="";
     	String id="";//identifier for each block of averaging results
     	             //format=1#-2#-3# with 1#=index of the ENSDF group (start from 1)
     	             //                     2#=index of the level group (start from 1; =0 from unplaced gamma groups)
     	             //                     3#=index of the gamma sub-group of a level group (start from 1)
     	             //If 1#-2#-3# is present, it is for a gamma group
     	             //If 1#-2#    is present, it is for a level group
     	
     	int ensIndex=ensdfGroupsV.indexOf(ensdfGroup);
     	String ensdfID="";
     	if(ensIndex>=0)
     		ensdfID+=(ensIndex+1);
     	else
     		ensdfID="1";
     	
     	String groupLabelPrefix0=CheckControl.groupLabelPrefix;
     	String groupLabelPostfix0=CheckControl.groupLabelPosfix;
     	
     	boolean placeLabelInBracket=true;
     	for(int i=0;i<ensdfGroup.nENSDF();i++) {
     		String dsid=ensdfGroup.ensdfV().get(i).DSId0().trim();
     		if(!EnsdfReferences.checkIfKeyno(dsid) && !dsid.contains("ADOPTED")) {
     			placeLabelInBracket=false;
     			break;
     		}
     	}
     	if(placeLabelInBracket) {
     		CheckControl.groupLabelPrefix="(";
     		CheckControl.groupLabelPosfix=")";
     	}
     	
     	String id0="ID#="+ensdfID+"-";
     	if(!CheckControl.convertRIForAdopted){
     		for(int i=0;i<ensdfGroup.unpGammaGroupsV().size();i++){
     			RecordGroup gammaGroup=ensdfGroup.unpGammaGroupsV().get(i);
     			int ng=gammaGroup.nRecords();
     			if(ng<1 || (ng==1&&!CheckControl.isUseAverageSettings))
     				continue;
     			
         		eg=gammaGroup.getRecord(0).ES();    	
         		id=id0+"0-"+(i+1);
         		title="**************** unplaced Gamma="+eg+" in "+ensdfGroup.NUCID()+"     *******************"+id;
         		
         		out+=printAverageReportOfGammaGroup(gammaGroup,title,prefixG);
     		}
     	}
     	
     	for(int i=0;i<ensdfGroup.levelGroupsV().size();i++){
     		RecordGroup levelGroup=ensdfGroup.levelGroupsV().get(i);    
     		int nl=levelGroup.nRecords();
            if(nl<1 || (nl==1&&!CheckControl.isUseAverageSettings))
                continue;
 			
     		el=levelGroup.getRecord(0).ES();   		
     		id=id0+(i+1);
     		title="**************** Level="+el+" in "+ensdfGroup.NUCID()+"     *******************"+id;
     		
     		out+=printAverageReportOfLevelGroup(levelGroup,title,prefixL);
     		
     		int ns=levelGroup.subgroups().size();
     		if(ns==0)
     			continue;
     				
     		for(int j=0;j<ns;j++){
     			RecordGroup gammaGroup=levelGroup.subgroups().get(j);
     			int ng=gammaGroup.nRecords();
                if(ng<1 || (ng==1&&!CheckControl.isUseAverageSettings))
                    continue;
     			
         		eg=gammaGroup.getRecord(0).ES();    
         		id=id0+(i+1)+"-"+(j+1);
         		title="**************** Level="+el+"     Gamma="+eg+" in "+ensdfGroup.NUCID()+"     *******************"+id;
         		
         		out+=printAverageReportOfGammaGroup(gammaGroup,title,prefixG);
     		}
     	}
     	
     	CheckControl.groupLabelPrefix=groupLabelPrefix0;
     	CheckControl.groupLabelPosfix=groupLabelPostfix0;
     	
     	return out;
    	
    }
    
    private String printAverageReportOfLevelGroup(RecordGroup levelGroup,String title,String prefixL){
    	String out="";
 		
	    String report="",s="";
     	AverageReport ar;
     	
     	String[] recordToBePrinted= {"E","T","S"};
     	ArrayList<String> typeList=new ArrayList<String>(Arrays.asList(recordToBePrinted));
     	
 		//debug
 		//if(i<3){
 		//	for(int m=0;m<levelGroup.recordsV().size();m++)
 		//		System.out.println("In ConsistencyCheck line 1882: m="+m+" dsid="+levelGroup.getDSID(m));
 		//}
 			
 	    String label="";
 	    if(levelGroup.nRecords()>=2) {
 	    	Level lev=levelGroup.getRecord(0);
 	    	label="\n<For Level="+lev.ES()+">"; 	    		
 	    }

 	   
    	levelGroup=levelGroup.lightCopy();
 	    if(levelGroup.getDSID(0).contains("ADOPTED")){
 	    	levelGroup.remove(0);
 	    }
 	    
 	    for(int i=0;i<typeList.size();i++) {
 	    	String recordType=typeList.get(i);
	
 	 	    //System.out.println(" 1 mean energy="+levelGroup.getMeanEnergy()+" size="+levelGroup.nRecords());
    		//if(recordType.equals("T") && Math.abs(levelGroup.getRecord(0).EF()-983)<20) {
            //    for(int j=0;j<levelGroup.nRecords();j++) {
            //    	System.out.println("In printAverageReport in recordgroup j="+j+" E="+levelGroup.getRecord(j).ES());
            //    }
    		//}
 	        
 	    	//RecordGroup tempGroup=levelGroup.lightCopy();
 	 	    //cleanRecordGroupForAverage(tempGroup,recordType);
 	 	    //ar=new AverageReport(tempGroup,recordType,prefixL);
 	   	    	
 	 		ar=getAverageReport(levelGroup, recordType, prefixL);
 	 		
 	 	    //System.out.println(" 2 mean energy="+levelGroup.getMeanEnergy()+" size="+levelGroup.nRecords());
 	 	    
	    	//if(Math.abs(levelGroup.getMeanEnergy()-50)<5) {
	    	//	System.out.println("  report="+ar.getReport());
	    	//	System.out.println(" adopted comment="+ar.getAdoptedComment());
	    	//}
 	 		s="";
	    	if(ar!=null) {
	            s=ar.getReport();
	            if(s.length()>0){
	                if(report.length()>0) 
	                    report+=label+s;
	                else
	                    report+=title+"\n"+s;
	            }	    	    
	    	}

 	    }
 		
 		if(report.length()>0){
 			out+=report+"\n";
 		}
 		
 		return out;
    }

    private String printAverageReportOfGammaGroup(RecordGroup gammaGroup,String title,String prefixG){
    	String out="";
 		
	    String report="",s="";
     	AverageReport ar;
      		 		
 		//debug
 		//if(j<3){
 		//	for(int m=0;m<levelGroup.recordsV().size();m++)
 		//		System.out.println("In ConsistencyCheck line 1882: m="+m+" dsid="+levelGroup.getDSID(m));
 		//}
 		
     	String[] recordToBePrinted= {"E","RI","TI"};
     	ArrayList<String> typeList=new ArrayList<String>(Arrays.asList(recordToBePrinted));
	    		
 	    String label="";
 	    if(gammaGroup.nRecords()>=2) {
 	    	Gamma gam=gammaGroup.getRecord(0);
 	    	if(gam.ILI()>0) {	    		
 	    		label="\n<For Gamma="+gam.ES()+" from Level="+gam.ILS()+">";
 	    	}else {
 	    		label="\n<For unplaced Gamma="+gam.ES()+">";
 	    	}
 	    }

		
     	gammaGroup=gammaGroup.lightCopy();
 	    if(gammaGroup.getDSID(0).contains("ADOPTED")){
 	    	gammaGroup.remove(0);
 	    }

 	    for(int i=0;i<typeList.size();i++) {
 	    	String recordType=typeList.get(i);
 			
 			//RecordGroup tempGroup=gammaGroup.lightCopy();
 			//cleanRecordGroupForAverage(tempGroup,recordType); 			
 	 		//ar=new AverageReport(tempGroup,recordType,prefixG);
 	 		
 	    	ar=getAverageReport(gammaGroup, recordType, prefixG); //return is not null in any case
 	    	
 	    	s="";
 	    	if(ar!=null) {
 	            s=ar.getReport();
 	            if(s.length()>0) {
 	                if(report.length()>0) 
 	                    report+=label+s;
 	                else
 	                    report+=title+"\n"+s;
 	            } 	    	    
 	    	}

 	    }
 		
 	    
 		if(report.length()>0){
 			out+=report+"\n";
 		}
 		
 		
 	    //////////////////////////////////////////////////
 	    //for temporary customized printout
 		/*
 	    AverageReport ar1=null,ar2=null;
 	    
 	    for(int i=0;i<typeList.size();i++) {
 	    	String recordType=typeList.get(i);
 	    	 	
 			//RecordGroup tempGroup=gammaGroup.lightCopy();
 			//cleanRecordGroupForAverage(tempGroup,recordType);
 	 		//ar=new AverageReport(tempGroup,recordType,prefixG);
 	    	ar=getAverageReport(gammaGroup, recordType, prefixG);
 	    	        
 	 		if(recordType.equals("RI"))
 	 			ar1=ar;
 	 		else if(recordType.equals("TI"))
 	 			ar2=ar;
 	    }
 	    

 	    if(ar1!=null && ar2!=null && ar1.value()>0 && ar2.value()>0) {
 	    	String Ig=ar1.adoptedValStr();
 	    	String dIg=ar1.adoptedUncStr();
 	    	
 	    	String Ie=ar2.adoptedValStr();
 	    	String dIe=ar2.adoptedUncStr();
 	    	
 	    	SDS2XDX sx1=new SDS2XDX(Ig,dIg);
 	    	SDS2XDX sx2=new SDS2XDX(Ie,dIe);
 	    	sx2=sx2.divided(sx1).multiply(0.0488f);
 	    	
 	    	String cs=ar2.getAdoptedComment();
 	    	String[] lines=cs.split("\n");
 	    	Comment c=new Comment();
 	    	try {
				c.setValues(new Vector<String>(Arrays.asList(lines)));
				String body=c.body();
				body="$"+EnsdfUtil.printValueAsComment("Ice(K)",Ie,dIe)+", "+body;
				String prefix=lines[0].substring(0,9);
				cs=Str.wrapString(body, prefix, 80, 5);
				
				body="$"+EnsdfUtil.printValueAsComment("|a(K)exp",sx2.s(),sx2.ds());
				body+=", from "+EnsdfUtil.printValueAsComment("Ice(K)",Ie,dIe);
				body+=" and "+EnsdfUtil.printValueAsComment("I|g",Ig,dIg);
				body+=" here";
				cs+="\n"+Str.wrapString(body, prefix.replace("cG", "dG"),80,5);
				
				out+=cs+"\n\n\n";
				
				//System.out.println(cs);
				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				//for(int i=0;i<lines.length;i++)
				//	System.out.println(" line"+i+"="+lines[i]);
				
				e.printStackTrace();
			}
 	    }
 	    */
 	    //////////////////////////////////////////////////

 		
    	return out;
    }
    
    
    public String printAverageReportOfRecordGroup(RecordGroup recordGroup,String entryType,float weightLimit){
    	String out="";
 		
	    String s="";
     	AverageReport ar;
      		 
     	try{
     		String recordLine=recordGroup.getRecord(0).recordLine();
     	    		
     		String NUCID=recordLine.substring(0,5);
     		String lineType="c"+recordLine.charAt(7);
     		
         	String prefix=Str.makeENSDFLinePrefix(NUCID, lineType);
      		
	    	recordGroup=recordGroup.lightCopy();
     	    if(recordGroup.getDSID(0).contains("ADOPTED")){
     	    	recordGroup.remove(0);
     	    }
     		
     	    //debug
     	    //System.out.println("  size1="+recordGroup.nRecords()+"   "+recordGroup.recordsV().size());
     	    //for(int i=0;i<recordGroup.recordsV().size();i++)
     	    //	System.out.println(recordGroup.getRecord(i).ES()+"  ded="+recordGroup.getRecord(i).DES());
     	    		
     	    //cleanRecordGroupForAverage(recordGroup,entryType);
     	  
     	    //System.out.println("  size2="+recordGroup.nRecords()+"   "+recordGroup.recordsV().size());
     	    
     	    cleanDecayDSID(recordGroup);
     	    
     		ar=new AverageReport(recordGroup,entryType,prefix,weightLimit);
     		s=ar.getReport();
     		if(s.length()>0){
     			out+=s+"\n";
     		}
     		
     	}catch(Exception e){
     		e.printStackTrace();
     	}

    	return out;
    }
    
    /*
     * parse the comments generated in a averaging result, which must be from printAverageReportOfRecordGroup()
     */
    public Vector<String> parseComments(String averageResult){
    	Vector<String> commentsV=new Vector<String>();
    	try{
    	  	String[] lines=averageResult.split("\n");
    	  	String comment="",line="";
    	  	boolean inComment=false;
    	  	for(int i=0;i<lines.length;i++){
                line=lines[i];    	  		
    	  		if(inComment){
    	  			if(line.trim().length()==0){
    	  				inComment=false;
    	  				if(comment.length()>0){
    	  					commentsV.add(comment);
    	  					comment="";
    	  				}
    	  			}else
    	  				comment+=line+"\n";
    	  		}else{
    	  			if(line.toUpperCase().contains("AVERAGE COMMENT"))
    	  				inComment=true;
    	  				
    	  			continue;
    	  		}
    	  	}
    	  	
    	  	if(comment.length()>0)
    	  		commentsV.add(comment);
    	  		
    	}catch(Exception e){}
  
    	return commentsV;
    }
    
    public String printFeedingGammas(RecordGroup recordGroup,EnsdfGroup ensdfGroup){
    	return printFeedingGammas(recordGroup,ensdfGroup,true,false);
    }
    
    /*
     * recordGroup must be level RecordGroup
     */
    public String printFeedingGammas(RecordGroup recordGroup,EnsdfGroup ensdfGroup,boolean printRecordName,boolean printHeader){
    	String out="";

     	try{
     		Level lev=null;
     		Gamma gam=null;
     		ENSDF ens=null;
     		String dsid="",xtagWithMarker="";//note that marker like "(*)", "(?)" is added only in old xtag
            
     		Vector<RecordGroup> feedingGammaGroupsV=findFeedingGammaGroups(recordGroup,ensdfGroup);
     		int size=feedingGammaGroupsV.size();
         	RecordGroup gamGroup=null,prevGamGroup=null;
     		String s="";
     		float currEG=-1,prevEG=-1;
     		
         	for(int i=0;i<size;i++){
         		gamGroup=feedingGammaGroupsV.get(i);
         		s="";

         		currEG=gamGroup.getRecord(0).EF();
         		
         		for(int j=0;j<gamGroup.nRecords();j++){
         			dsid=gamGroup.getDSID(j);
         			
         			xtagWithMarker=gamGroup.getXTag(j);//returns old tag with marker. If Adopted exist, old tag character=new tag character
         			                                   //marker is always only added into old tag. No marker in new tag.
         			                                   //See EnsdfGroup.java: sortRecrods() line#894, insertRecordsToReferenceGroups() line#701
         			
         			ens=ensdfGroup.getENSDFByDSID0(dsid);
         			gam=(Gamma)gamGroup.getRecord(j);
         			if(prevGamGroup!=null && prevGamGroup.recordsV().contains(gam))
         				continue;
         	 		
         			lev=ens.levelAt(gam.ILI());
         			s+=printFeedingGammaLine(gam,lev,ens,xtagWithMarker,printRecordName)+"\n";
         			
         			//System.out.println(gam.ES()+"    "+lev.ES());
         		}
         		
         		if(s.length()>0){
         			if(prevEG>0 && Math.abs(currEG-prevEG)<2.0)//considered as same gammas and grouped together
         				out+=s;
         			else
         				out+="\n    GAMMA="+gamGroup.getRecord(0).ES()+"\n"+s;
         		}
         		
         		prevGamGroup=gamGroup;
         		prevEG=currEG;
         	}

         	if(out.length()>0){
         		lev=(Level)recordGroup.getRecord(0);
         		String header="\n\n--- Gamma feedings to level: EL="+String.format("%-10s JPI=%s",lev.ES(),((Level)lev).JPiS());
         		if(out.contains("*"))
         			header+="\n(* indicates possibly uncertain placements made by the program)";
         		out=header+"\n"+out;
         	}

     	}catch(Exception e){
     		e.printStackTrace();
     	}

    	return out;
    }
        
    private String printFeedingGammaLine(Gamma gam, Level lev,ENSDF ens,String xtagWithMarker,boolean printRecordName){
    	String out="";
    	String EG="",MUL="",EL="",JI="",T="",FLS="",JF="",BANDI="",BANDF="",RIS="";
    	
    	String dsid=ens.DSId0();
    	
    	String prefix=String.format("%30s-->",dsid);
    	String postfix=String.format("<---%-30s",dsid);
    	String marker=" ";
    	if(xtagWithMarker.contains("*"))
    		marker="*";
    	else if(xtagWithMarker.contains("?"))
    		marker="?";
   

    	
    	Level fLev=null;
    	try{
        	if(lev.belongs2Band())
        		BANDI="BAND="+lev.flag().charAt(0);
        	
    		fLev=ens.levelAt(gam.FLI());
    		if(fLev.belongs2Band())
    			BANDF="BAND="+fLev.flag().charAt(0);
    	}catch(Exception e){
    		e.printStackTrace();
    	}

    	RIS = gam.RIS().trim();
    	if (RIS.length()>0 && gam.DRIS().length()>0)
    		RIS = String.valueOf(RIS) + "(" + gam.DRIS() + ")";
    	
    	EG =String.format("%-10s",gam.ES());
    	RIS = String.format("%-12S",RIS);
    	
    	MUL=String.format("%-10s",gam.MS());
    	EL =String.format("%-10s",lev.ES());
    	JI =String.format("%-18s",lev.JPiS());
   		BANDI=String.format("%-8s",BANDI);
    	//T  =String.format("%-12s",lev.T12S());
    	
    	FLS=String.format("%-10s",gam.FLS());
    	
    	String JFS=gam.JFS().trim();
    	if(JFS.length()>10) {
    		int n=JFS.indexOf(",");
    		if(n>0) {
    			if(n<=7) {
    				JFS=JFS.substring(0,n).trim()+"...";
    			}else if(n<=8){
       				JFS=JFS.substring(0,n).trim()+"..";
    			}else {
    				JFS=JFS.substring(0,10);
    			}
    		}else if((n=JFS.indexOf("/2"))>0){
    			if(n<=5) {
    				JFS=JFS.substring(0,n).trim()+"...";
    			}else if(n<=6){
       				JFS=JFS.substring(0,n).trim()+"..";
    			}else {
    				JFS=JFS.substring(0,10);
    			}
    		}else {
    			JFS=JFS.substring(0,10);
    		}
    	}
    	JF =String.format("%-10s",JFS);
    	BANDF=String.format("%-8s",BANDF);
    	
    	postfix="";
		out=marker+prefix+" EL="+EL+BANDI+JI+T+"EG="+EG+"RI="+RIS+MUL+"FL="+FLS+BANDF+JF+postfix;
		
    	return out;
    }
    
    public Vector<RecordGroup> findFeedingGammaGroups(RecordGroup levelGroup,EnsdfGroup ensdfGroup){
    	Vector<RecordGroup> out=new Vector<RecordGroup>();
    	try{
    		int nLevelGroups=ensdfGroup.levelGroupsV().size();
    		int index=ensdfGroup.levelGroupsV().indexOf(levelGroup);
     		
           
    		if(index<0 || index==nLevelGroups-1)
    			return out;
    		
    		RecordGroup lgroup=null,ggroup=null;
    		for(int i=index+1;i<nLevelGroups;i++){
    			lgroup=ensdfGroup.levelGroupsV().get(i);
    			
        		for(int j=0;j<lgroup.subgroups().size();j++){
        			ggroup=lgroup.subgroups().get(j);
        			boolean found=false;
        			       			
        			//System.out.println("#### lgroup="+lgroup.getRecord(0).ES()+" size="+lgroup.subgroups().size()+"  subgroup.size="+ggroup.nRecords());
        			
        			for(int k=0;k<ggroup.nRecords();k++){
        				
        				String dsid=ggroup.getDSID(k);
        				Gamma g=(Gamma)ggroup.getRecord(k);

        				try{

            				if(g.FLI()>=0) {
               					Level fLevel=ensdfGroup.getENSDFByDSID0(dsid).levelAt(g.FLI());
                				
                				//System.out.println("    k="+k+" dsid="+dsid+"  g="+g.ES()+"  fl="+fLevel.ES());
                				
            					if(levelGroup.recordsV().contains(fLevel)){
            						out.add(ggroup);
            						found=true;
            						break;
            					}
            				}
     
        				}catch(Exception e){
        					e.printStackTrace();
        				}
        			}
        			
        			if(found)//one level group should have only one sub group of gamma that feeds the daughter-level group
        				break;    
        		}
    		}
    		
    	}catch(Exception e){
    		e.printStackTrace();
    	}
    	
    	return out;
    }
    
    
    //for output file containing original lines from all datasets grouped together, level-by-level,
    //gamma-by-gamma, in order of dataset in the file
    //Note that if adopted is present, the default xtags used in output is from old XREF in adopted dataset
    //if compareXTags=true, new xtags will also be printed after the old xtags
    public String printGroupedLines(EnsdfGroup ensdfGroup,boolean compareXTags){
    	StringBuilder out=new StringBuilder();
    	
    	if(ensdfGroup.nENSDF()==1 && ensdfGroup.adopted()!=null)
    		return "";
    	
    	boolean addXTag=false;
		String prefixFirstLevel="",prefixFirstGamma="";
		
    	if(ensdfGroup.adopted()!=null && compareXTags)
    		addXTag=true;
    	
		//prefix=String.format("%30s--->%-5s","LEVEL***********************","");
		if(addXTag){
			prefixFirstLevel=String.format("%34s%-8s","LEVEL****************************","********");
    		prefixFirstGamma=String.format("%34s%-8s","GAMMA----------------------------","--------");
		}else{
			prefixFirstLevel=String.format("%34s%-5s","LEVEL****************************","*****");
    		prefixFirstGamma=String.format("%34s%-5s","GAMMA----------------------------","-----");
		}

     	if(!CheckControl.convertRIForAdopted){
     		String tempPrefixGamma=prefixFirstGamma.replace("GAMMA", "UNPLACED GAMMA");
     		tempPrefixGamma=tempPrefixGamma.substring(0,prefixFirstGamma.length());
     		
     		System.out.println(tempPrefixGamma);
     		
     		for(int i=0;i<ensdfGroup.unpGammaGroupsV().size();i++){
     			RecordGroup gammaGroup=ensdfGroup.unpGammaGroupsV().get(i);
     			out.append(printGroupedGammaLines(gammaGroup,ensdfGroup,tempPrefixGamma,addXTag));
     		}
     	}
     	
		for(int i=0;i<ensdfGroup.levelGroupsV().size();i++){
    		RecordGroup levelGroup=ensdfGroup.levelGroupsV().get(i);
    		out.append(printGroupedLevelLines(levelGroup,ensdfGroup,prefixFirstLevel,addXTag));

    		/*
    		//debug
    		float ef=levelGroup.getReferenceRecord().EF();
    		if(Math.abs(ef-12107)<1) {
    			for(int j=0;j<levelGroup.nRecords();j++){
    				Level l=(Level)levelGroup.getRecord(j);
    				System.out.println("  group EF="+ef+"     j="+j+"  ES="+l.ES());
    			}
    		}
    		*/
    		
    		if(!levelGroup.hasSubGroups())
    			continue;
    		
    		
    		Vector<RecordGroup> gammaGroupsV=levelGroup.subgroups();
    		for(int j=0;j<gammaGroupsV.size();j++) {
    			RecordGroup gammaGroup=gammaGroupsV.get(j);
        		out.append(printGroupedGammaLines(gammaGroup,ensdfGroup,prefixFirstGamma,addXTag));
    		}   
		}
		
		//if(out.length()>0)
		//	out="\n\n"+ensdfGroup.NUCID()+"\n"+out;
		if(out.length()>0){
    		String temp="\n\n"+Str.repeat("#", 120)+"\n";
    		temp+=printXREFList(ensdfGroup,true);
    		out.insert(0,temp);
    		
    		out.append("\n\n"+printLevelGammaStatistics(ensdfGroup));
		}
		return out.toString();
    }
    
    private String printGroupedLevelLines(RecordGroup levelGroup,EnsdfGroup ensdfGroup,String prefixFirstLevel,boolean addXTag) {
    	StringBuilder out=new StringBuilder();
    	
    	String prefix="",prefixXREF="",line="";
    	
		String NUCID=ensdfGroup.ensdfV().get(0).nucleus().nameENSDF();//size=5 with leading space if existing
        //ensdfGroup.NUCID() return trimmed NUCID
    	
		String lineGroup="",newXTags="",oldXTags="",oldXREF="";        		
		out.append(Str.repeat("-", 120)+"\n");
		   	
    	HashMap<String,String> newDSIDXTagMap=ensdfGroup.newDSIDXTagMap();
    	
		Level adoptedLevel=(Level)levelGroup.getAdoptedRecord();
		float adoptedEL=0;
		
		//the first line
		if(adoptedLevel!=null) {
			line=prefixFirstLevel+Str.fixLineLength(adoptedLevel.recordLine(),80);
			if(adoptedLevel.altJPiS().length()>0) {
				if(adoptedLevel.altJPiS().equalsIgnoreCase("NA"))
					line+=" --- possible JPI=NA: check contradicting data";
				else
					line+=" --- possible JPI="+adoptedLevel.altJPiS();
				
				//if(adoptedLevel.ES().equals("597.1"))
				//	System.out.println("ConsistencyCheck 6007: adopted altJPIS="+adoptedLevel.altJPiS());
				
				//add information of feeding gammas
				int index=levelGroup.recordsV().indexOf(adoptedLevel);
				String dsid=levelGroup.dsidsV().get(index);
				ENSDF ens=ensdfGroup.getENSDFByDSID(dsid);
				
				SpinParityParser jpiParser=ensdfGroup.getJPIParserByDSID0(dsid);
				String tempS="";
				
				//System.out.println("ConsistencyCheck 6004: DSID="+dsid+" level="+adoptedLevel.ES()+" "+(jpiParser==null));
				
				if(jpiParser!=null) {
					
					Vector<Gamma> gammasForJPI=jpiParser.getGammasForJPI(adoptedLevel);
					
					/*
					if(adoptedLevel.ES().equals("3860.4")) {
						System.out.println(gammasForJPI.size());
						for(Gamma g: gammasForJPI)
							System.out.println("g="+g.ES()+" ILS="+g.ILS()+" JPI="+g.JIS()+" FLS="+g.FLS()+" JPI="+g.JFS());
					}
					*/
					
					int limit=3;
					boolean noDecayGamma=true;
					for(Gamma g:adoptedLevel.GammasV()) {
						if(gammasForJPI.contains(g)) {
							noDecayGamma=false;
							break;
						}
					}
					if(noDecayGamma)
						limit=3;
					
					if(gammasForJPI!=null) {
						int count=0;
						for(Gamma g:gammasForJPI) {
							if(adoptedLevel.GammasV().contains(g))
								continue;
							
							String label="";
							if(g.isPrimary())
								label="prim. ";
							
							if(count<limit) {
								String ms=g.MS();
								Level lev=ens.levelAt(g.ILI());
								if(ms.length()>0 && !ms.contains("["))
									tempS+=label+g.ES()+"g "+ms+" from "+lev.ES()+", "+lev.altJPiS()+"; ";
								else
									tempS+=label+g.ES()+"g from "+lev.ES()+", "+lev.altJPiS()+"; ";
							}

							
							count++;
                            if(count>limit)
                            	break;
						}
						
						if(count>limit && tempS.trim().length()>0)
							tempS=tempS.trim()+"...";
					}
				}
				
				if(tempS.trim().length()>0) {
					tempS=tempS.trim();
					if(tempS.endsWith(";"))
						tempS=tempS.substring(0,tempS.length()-1);
					
					line+=": "+tempS;
				}
				line+="\n";
			}else {
				line+=" ---\n";
			}
			adoptedEL=adoptedLevel.EF();
		}else {
			Level refLevel=(Level) levelGroup.getReferenceRecord();
			if(refLevel==null)
				refLevel=(Level)levelGroup.getRecord(0);
				
			adoptedEL=refLevel.EF();
			
			line=prefixFirstLevel+Str.fixLineLength(refLevel.recordLine(),80)+"\n";
		}
		
		out.append(line);
		
		prefix="";
	    lineGroup="";
	    oldXTags="";
	    newXTags="";
	    oldXREF="";//old XREF record in Adopted dataset
		
		Vector<String> commentLines=new Vector<String>();	
		Vector<String> oldXTagsV=new Vector<String>();
		HashMap<String,String> oldXTagMarkerMap=new HashMap<String,String>();

		for(int j=0;j<levelGroup.nRecords();j++){

			Level lev=(Level)levelGroup.getRecord(j);
            String es=lev.ES();
            float levEL=lev.EF();
			     
			//from dsidsV in RecordGroup.java, which are from datasetDSIDsV in EnsdfGroup.java
			String dsid=levelGroup.getDSID(j);
			
			//from xtagsV in RecordGroup.java, which are from datasetXTagsV in EnsdfGroup.java
			//if Adopted dataset exists, it is from makeAdoptedXTagMap() in EnsdfGroup.java, xtag in XREF list in Adopted dataset
			//Note that each xtag here also contains markers in "()" following the xtag letter or symbol
			String xtag=levelGroup.getXTag(j);
			
			//Note that the next xtag contains only the xtag letter or symbol but no "()" and xtag marker in it.
			String newXTag=newDSIDXTagMap.get(dsid);
			if(newXTag==null)
				newXTag="?";
			    	
    		//debug
    		//if(Math.abs(adoptedLevel.EF()-0)<1){
        	//	System.out.println("In ConsistencyCheck line 2084: j="+j+" dsid="+dsid+" xtag="+xtag);
    		//}
			
			if(dsid.contains("ADOPTED LEVELS")){
				oldXREF=lev.XREFS();
				continue;
			}
			
			ENSDF ens=ensdfGroup.getENSDFByDSID(dsid);
			
			//old note
			//note that (*), (?) marks are added only for old tags if both old and new tag exist, 
			//but not for new tags for display purpose 
			//(old and new tag displayed side by side in output, new tag first and the old tag)

			String xtagWithMarker=xtag;
			int n=xtag.indexOf("*");
			if(n>0 && !xtag.contains(es) && !es.contains(".")) 
				xtagWithMarker=xtag.substring(0, n)+es+xtag.substring(n);
			else if(!xtag.contains(es) && !es.contains(".") && Math.abs(adoptedEL-levEL)>Math.max(10,adoptedEL/500)) {
				n=xtag.indexOf("(");
				if(n>=0){
					if(xtag.indexOf(")",n)==n+1) 
						xtagWithMarker=xtag.substring(0, n)+"("+es+xtag.substring(n+1);
				}else{
					xtagWithMarker=xtag+"("+es+")";
				}
			}
			
			oldXTagMarkerMap.put(xtag,xtagWithMarker);
			
			if(addXTag){
				prefix=String.format("%30s--->%-3s%-5s",dsid,newXTag,xtag);
	    		prefixXREF=Str.repeat(" ", 42);
	    		
				oldXTags+=xtagWithMarker;
				newXTags+=newXTag;
				int index=xtagWithMarker.indexOf("(");
				//if(xtag.contains("(") && Character.isLetter(xtag.charAt(0)))
				if(index>0)//some new datasets has xtag=?0, ?1, ...	
					newXTags+=xtagWithMarker.substring(index);
				
				oldXTagsV.add(xtag);
				
			}else{//no adopted dataset in grouping and so no old tag
				prefix=String.format("%30s--->%-5s",dsid,xtag);
		   		prefixXREF=Str.repeat(" ", 39);
		   		
				newXTags+=xtagWithMarker; //here xtag is the newly assigned tag
			}
			//System.out.println(" dsid="+dsid+" xtag="+xtag);
			
			
			line=prefix+lev.recordLine();
			if(lev.altJPiS().length()>0) {
				if(lev.altJPiS().equalsIgnoreCase("NA"))
					line+=" *** possible JPI=NA: check contradicting data";
				else
					line+=" *** possible JPI="+lev.altJPiS();
				
				
				
				//add information of feeding gammas
				SpinParityParser jpiParser=ensdfGroup.getJPIParserByDSID0(dsid);
				String tempS="";
				if(jpiParser!=null) {
					Decay d=jpiParser.getDecayForJPI(lev);
					if(d!=null) {
						Parent parent=ens.parentAt(0);
						String parentJPS=parent.level().JPiS();
						
						if(!d.LOGFTS().isEmpty()) {
							String s=d.LOGFTS();
							if(!d.DLOGFTS().isEmpty())
								s+="("+d.DLOGFTS()+")";
							
							tempS+="logft="+s+" from "+parentJPS+"; ";
						}else if(!d.HFS().isEmpty()) {
							String s=d.HFS();
							if(!d.DHFS().isEmpty())
								s+="("+d.DHFS()+")";
							
							tempS+="HF="+s+" from "+parentJPS+"; ";
						}
					}
					
					Vector<Gamma> gammasForJPI=jpiParser.getGammasForJPI(lev);
					
					/*
					if(lev.ES().equals("4381.9")) {
						System.out.println(gammasForJPI.size());
						for(Gamma g: gammasForJPI)
							System.out.println("g="+g.ES()+" ILS="+g.ILS()+" JPI="+g.JIS()+" FLS="+g.FLS()+" JPI="+g.JFS());
					}
					*/
					
					int limit=3;
					boolean noDecayGamma=true;
					for(Gamma g:lev.GammasV()) {
						if(gammasForJPI.contains(g)) {
							noDecayGamma=false;
							break;
						}
					}
					if(noDecayGamma)
						limit=3;
					
					if(gammasForJPI!=null) {
						int count=0;
						for(Gamma g:gammasForJPI) {
							if(lev.GammasV().contains(g))
								continue;
							
							String label="";
							if(g.isPrimary())
								label="prim. ";
							
							if(count<limit) {
								String ms=g.MS();
								if(ms.length()>0 && !ms.contains("["))
									tempS+=label+g.ES()+"g "+ms+" from "+g.ILS()+","+g.JIS()+"; ";
								else
									tempS+=label+g.ES()+"g from "+g.ILS()+","+g.JIS()+"; ";
							}

							
							count++;
                            if(count>limit)
                            	break;
						}
						
						if(count>limit && tempS.trim().length()>0)
							tempS=tempS.trim()+"...";
					}
				}
				
				if(tempS.trim().length()>0) {
					tempS=tempS.trim();
					if(tempS.endsWith(";"))
						tempS=tempS.substring(0,tempS.length()-1);
					
					line+=": "+tempS;
				}
			}
			
			
			lineGroup+=line+"\n";
			

			for(String s:lev.contRecsLineV()){
				if(s.charAt(5)=='S')
					continue;
				
				commentLines.add(prefix+s);
			}
			
			for(int k=0;k<lev.nComments();k++) {
				Comment c=lev.commentAt(k);
				for(String s:c.lines())
					commentLines.add(prefix+c.checkEndPeriod(s));
			}
			
   			for(Decay d:lev.DecaysV()){
   				commentLines.add(prefix+d.recordLine());
   				for(Comment c:d.commentV())
   					for(String s:c.lines())
   						commentLines.add(prefix+c.checkEndPeriod(s));
   			}
		}
		
		//**********
		//add XREF
		//note that here new xtags are already in order but old tags are not
		if(newXTags.length()>0){
			out.append(prefixXREF.substring(0,prefixXREF.length()-21)+"*new XREF new tags   "+NUCID+"X L XREF="+newXTags+"\n");
		}
		

		int n=oldXTagsV.size();
		if(n>0){
			
			Vector<String> temp=new Vector<String>();
			int index=0;
			
			temp.add(oldXTagsV.get(0));
			oldXTagsV.remove(0);
			
			while(oldXTagsV.size()>0) {
				String t=oldXTagsV.get(0);

				index=temp.size();
				if(compareXTag(t,temp.firstElement())<=0)
					index=0;
				else if(compareXTag(t,temp.lastElement())>=0)
					index=temp.size();
				else {
					for(int k=1;k<temp.size();k++) {
						if(compareXTag(t,temp.get(k))<=0) {
							index=k;
							break;
						}
					}
				}
				
				temp.insertElementAt(t,index);
				oldXTagsV.remove(0);
			}
			
			oldXTags="";
			for(int k=0;k<temp.size();k++) {
				oldXTags+=oldXTagMarkerMap.get(temp.get(k));
			}
			
			out.append(prefixXREF.substring(0,prefixXREF.length()-21)+"*new XREF old tags   "+NUCID+"X L XREF="+oldXTags+"\n");
			if(oldXREF.length()>0)
				out.append(prefixXREF.substring(0,prefixXREF.length()-21)+"#old XREF in Adopted "+NUCID+"X L XREF="+oldXREF+"\n");
		}

		
		//add group of record lines from datasets
		out.append(lineGroup);
		
		//add comment lines
		for(String s:commentLines)
			out.append(s+"\n");
		
		return out.toString();
		    		
    }
    
    private String printGroupedGammaLines(RecordGroup gammaGroup,EnsdfGroup ensdfGroup,String prefixFirstGamma,boolean addXTag) {
    	StringBuilder out=new StringBuilder();
    	
    	String firstLine="",prefix="",line="";
		Vector<String> commentLines=new Vector<String>();
		
		HashMap<String,String> newDSIDXTagMap=ensdfGroup.newDSIDXTagMap(); 		
	    
		Gamma refGam=(Gamma)gammaGroup.getReferenceRecord();
		if(refGam==null)
	 		refGam=(Gamma)gammaGroup.getRecord(0);
		
		if(CheckControl.convertRIForAdopted && refGam!=adoptedGamma)
			firstLine=prefixFirstGamma+gammaGroup.replaceIntensityOfGammaLineWithBR(refGam,CheckControl.errorLimit,"ALL")+"\n";
		else
			firstLine=prefixFirstGamma+refGam.recordLine()+"\n"; 
		
		//debug
		//if(Math.abs(adoptedLevel.EF()-74)<1){
    	//	System.out.println("In ConsistencyCheck line 2049: i="+i+" m="+m+" firstLine="+firstLine);
		//}
		
		StringBuilder tempOut=new StringBuilder();
		
		commentLines.clear();
		for(int j=0;j<gammaGroup.nRecords();j++){
			Gamma gam=(Gamma)gammaGroup.getRecord(j);
			String dsid=gammaGroup.getDSID(j);
			String xtag=gammaGroup.getXTag(j);
			String newXTag=newDSIDXTagMap.get(dsid);
			if(newXTag==null)
				newXTag="?";
			
			ENSDF ens=ensdfGroup.getENSDFByDSID0(dsid);

		    String fls="";
			try{
				//Level parent=ens.levelAt(gam.ILI());
				Level daughter=ens.levelAt(gam.FLI());
				fls=String.format("EG=%-10s FL=%-10s JF=%s",gam.ES(),daughter.ES(),daughter.JPiS());
			}catch(Exception e){  
				fls="";
			}
			
    		//System.out.println("firstline="+firstLine+" fls="+fls+" dsid="+dsid+" gam="+gam.FLI()+" es="+gam.ES()+" "+(ens==null));
    		
			if(dsid.contains("ADOPTED LEVELS")){
				firstLine=prefixFirstGamma+Str.fixLineLength(gam.recordLine(),80)+" --- "+fls+"\n";				
				continue;
			}
			
			prefix=String.format("%30s--->%-5s",dsid,xtag);
			if(addXTag)
				prefix=String.format("%30s--->%-3s%-5s",dsid,newXTag,xtag);
			else
				prefix=String.format("%30s--->%-5s",dsid,xtag);
			
			//line=prefix+gam.recordLine()+fls;
			if(CheckControl.convertRIForAdopted)
				line=gammaGroup.replaceIntensityOfGammaLineWithBR(gam,CheckControl.errorLimit,"ALL");
			else
				line=gam.recordLine();
			
			line=prefix+Str.fixLineLength(line,80)+" *** "+fls;
			tempOut.append(line+"\n");
			
			
			for(String s:gam.contRecsLineV()){
				if(s.charAt(5)=='S')
					continue;
				commentLines.add(prefix+s);
			}
			
			for(int k=0;k<gam.nComments();k++) {
				Comment c=gam.commentAt(k);
				for(String s:c.lines())
					commentLines.add(prefix+c.checkEndPeriod(s));
			}
		}    
		
		for(String s:commentLines)
			tempOut.append(s+"\n");
		

		out.append(firstLine+tempOut);	
		
		return out.toString();
    }
    
    
    private int compareXTag(String tag1,String tag2) {
    	try {
    		char c1=tag1.charAt(0);
    		char c2=tag2.charAt(0);
			int p1=(int)c1;
			int p2=(int)c2;
			if(Character.isLetter(c1))
				p1=p1-92;
			if(Character.isLetter(c2))
				p2=p2-92;
			
			return p1-p2;
			
    	}catch(Exception e) {
    	}
    	
    	return 0;
    }
    
    public String printXREFList(EnsdfGroup ensdfGroup,boolean compareOldXREFs){
    	if(adopted==null || !compareOldXREFs)
    		return printXREFList(ensdfGroup);

    	String out="";
    	int size=ensdfGroup.ensdfV().size();
    	//String prefix=String.format("%5s  X",ensdfGroup.NUCID());
    	String prefix=Str.makeENSDFLinePrefix(ensdfGroup.NUCID(), " X");//return a string with length=9, len(NUCID)<=5, len(type)<=4, a space is added at the end
    	
    	prefix=prefix.substring(0,8);
    	
    	HashMap<String,String> newDSIDXTagMap=ensdfGroup.newDSIDXTagMap();
    	
    	//when adopted dataset is present, the dsidXTagMap is made from old XREF list in the adopted dataset
    	HashMap<String,String> oldDSIDXTagMap=ensdfGroup.dsidXTagMapFromAdopted();
    	
    	out+=String.format("%8s%-33s%s"," ","new XREFs","old XREFs")+"\n";
    	for(int i=0;i<size;i++){
    		ENSDF ens=ensdfGroup.ensdfV().get(i);   
    		String dsid0=ens.DSId0();
    		if(ens==adopted || dsid0.contains("ADOPTED LEVELS"))
    			continue;
    		
    		
    		String xtag=newDSIDXTagMap.get(dsid0);
    		if(xtag==null)
    			xtag="?";
    		
            String oldXREF=xtag+"-";
    		int index=ensdfGroup.datasetDSID0sV().indexOf(dsid0);//see datasetDSIDsV in makeAdoptedXTagMap() in EnsdfGroup.java
    		
    		if(index>=0){
    			String oldXTag=ensdfGroup.datasetXTagsV().get(index);
                boolean foundMatch=false;
        		for(String oldDSID:oldDSIDXTagMap.keySet()){
        			
        			//System.out.println("ConsistencyCheck 5840: dsid="+dsid0+"  index="+index+" oldDSID="+oldDSID+" "+oldDSIDXTagMap.get(oldDSID)+" oldXTag="+oldXTag);
        			
        			if(oldDSIDXTagMap.get(oldDSID).equals(oldXTag)){
        				oldXREF+=oldXTag+" "+oldDSID;
        				foundMatch=true;
        				break;
        			}
        		}
        		
        		if(!foundMatch)
        			oldXREF+="?";
    		}else 
    			oldXREF+="?";
    		
    		/*
            boolean foundMatch=false;
    		for(String oldDSID:oldDSIDXTagMap.keySet()){
    			String oldXTag=oldDSIDXTagMap.get(oldDSID);
    			if(oldDSID.contains(dsid0)){
    				oldXREF+=oldXTag+" "+oldDSID;
    				foundMatch=true;
    				break;
    			}
    		}
    		
    		if(!foundMatch)
    			oldXREF+="?";
    		*/
    		
    		out+=prefix+xtag+String.format("%-30s", dsid0)+oldXREF+"\n";
    	}
    	out+="################################## new tag show first below ############################################################\n";
    	return out;
    }
        
    public String printXREFList(EnsdfGroup ensdfGroup){
    	String out="";
    	
    	int size=ensdfGroup.ensdfV().size();
    	//String prefix=String.format("%5s  X",ensdfGroup.NUCID());
    	String prefix=Str.makeENSDFLinePrefix(ensdfGroup.NUCID(), " X");
    	prefix=prefix.substring(0,8);
    	
    	HashMap<String,String> newDSIDXTagMap=ensdfGroup.newDSIDXTagMap();
    	
    	for(int i=0;i<size;i++){
    		ENSDF ens=ensdfGroup.ensdfV().get(i);
    		
    		//System.out.println(" dsid="+ens.DSId0());
    		
    		if(ens==adopted)
    			continue;
    		
    		String dsid0=ens.DSId0();
    		String xtag=newDSIDXTagMap.get(dsid0);
    		if(xtag==null)
    			xtag="?";
    		
			String temp=prefix+xtag+String.format("%-30s", dsid0);
			temp+=Str.repeat(" ", 80-temp.length());
			
    		out+=temp+"\n";
    	}
    	return out;
    }
    
    /*
     * a simple dataset of Adopted levels with new XREF information only
     */
    public String printAdoptedWithNewXREFOnly(EnsdfGroup ensdfGroup){
    	StringBuilder out=new StringBuilder();
    	
    	String NUCID=ensdfGroup.ensdfV().get(0).nucleus().nameENSDF();//size=5 with leading space if existing
    	                                                              //ensdfGroup.NUCID() return trimmed NUCID
    	
    	if(ensdfGroup.nENSDF()==1 && ensdfGroup.adopted()!=null)
    		return "";
    	
    	@SuppressWarnings("unused")
		boolean hasAdopted=false;
    	if(ensdfGroup.adopted()!=null)
    		hasAdopted=true;
	
    	HashMap<String,String> newDSIDXTagMap=ensdfGroup.newDSIDXTagMap();


		for(int i=0;i<ensdfGroup.levelGroupsV().size();i++){
    		RecordGroup levelGroup=ensdfGroup.levelGroupsV().get(i);

    		String line="",newXTags="";
    		Vector<String> newXTagLinesV=new Vector<String>();        		
    		   		
    		Level adoptedLevel=(Level)levelGroup.getAdoptedRecord();
    		hasAdopted=false;
    		if(adoptedLevel!=null){
    			line=adoptedLevel.recordLine()+"\n";
    		    hasAdopted=true;
    		}else{
    			Level refLev=(Level)levelGroup.getReferenceRecord();
    			if(refLev==null)
    				line=levelGroup.getRecord(0).recordLine()+"\n";
    			else 
    				line=refLev.recordLine()+"\n";

    		    hasAdopted=false;
    		}
            
			line=line.substring(0,55)+Str.repeat(" ", 22)+line.substring(77);//remove L, S and flag record is 
    		line=EnsdfUtil.trimENSDFLine(line);
    		
			out.append(line);
    		

   		    newXTags="";
    		for(int j=0;j<levelGroup.nRecords();j++){
    			String dsid=levelGroup.getDSID(j);
    			String xtag=levelGroup.getXTag(j);
    			String newXTag=newDSIDXTagMap.get(dsid);
    			if(newXTag==null)
    				newXTag="?";
    			    	
        		//debug
        		//if(Math.abs(adoptedLevel.EF()-74)<1){
            	//	System.out.println("In ConsistencyCheck line 2084: j="+j+" dsid="+dsid+" xtag="+xtag);
        		//}
    			
    			if(dsid.contains("ADOPTED LEVELS"))
    				continue;
    			
    			String tempXTags=newXTags;
    			
    			//note that (*), (?) marks are added only for old tags if both old and new tag exist, 
    			//but not for new tags for display purpose 
    			//(old and new tag displayed side by side in output, new tag first and the old tag)
				tempXTags+=newXTag;
				
				int index=xtag.indexOf("(");
				//if(xtag.contains("(") && Character.isLetter(xtag.charAt(0)))
				if(index>0)//some new datasets has xtag=?0, ?1, ...	
					tempXTags+=xtag.substring(index);	
				
    			//System.out.println(" EL="+levelGroup.getRecord(0).EF()+" dsid="+dsid+" xtag="+xtag+" newXTag="+newXTag);
				
                if(tempXTags.length()>=65) {//max length of xtags after "XREF+="
                	newXTagLinesV.add(newXTags);
                	newXTags=tempXTags.substring(newXTags.length());
                }else
                	newXTags=tempXTags;
    		}
    		
    		if(newXTags.length()>0 && !newXTagLinesV.contains(newXTags))
    			newXTagLinesV.add(newXTags);
    		
    		//add new XREF
    		//note that here new xtags are already in order but old tags are not
    		if(newXTagLinesV.size()>0){
    			String temp=NUCID+"X L XREF="+newXTagLinesV.get(0);
    			temp+=Str.repeat(" ", 80-temp.length());
    			out.append(temp+"\n");
    			
    			for(int x=1;x<newXTagLinesV.size();x++) {
    				temp=NUCID+"X L XREF+="+newXTagLinesV.get(x);
        			temp+=Str.repeat(" ", 80-temp.length());
        			out.append(temp+"\n");
    			}
    		}   		    		
		}
		
		//if(out.length()>0)
		//	out="\n\n"+ensdfGroup.NUCID()+"\n"+out;
		if(out.length()>0){
    		out.insert(0,printXREFList(ensdfGroup));
		}
		
		return out.toString();
    }
    
    /*
     * a new dataset of Adopted Levels, Gammas with all data, merged into
     * the existing Adopted dataset if there is one
     */
    public String printAdoptedWithAllData(EnsdfGroup ensdfGroup){
    	StringBuilder out=new StringBuilder();

    	if(ensdfGroup.nENSDF()==1 && ensdfGroup.adopted()!=null)
    		return "";
    	
    	HashMap<String,String> newDSIDXTagMap=ensdfGroup.newDSIDXTagMap();

        this.adoptedEGSourceDSIDs.clear();
        this.adoptedRISourceDSIDs.clear();
        this.adoptedTISourceDSIDs.clear();
        this.adoptedMRSourceDSIDs.clear();
        
        String s="";
 
    	//write unplaced gammas
    	if(CheckControl.createCombinedDataset) {
    		for(int k=0;k<ensdfGroup.unpGammaGroupsV().size();k++){
    			RecordGroup gammaGroup=ensdfGroup.unpGammaGroupsV().get(k);
    			
        		//skip gamma group that contains only gamma from previous adopted
        		//dataset but not any gamma from individual datasets
        		if(gammaGroup.nRecords()==1 && gammaGroup.hasAdoptedRecord())
        			continue;
        		
    			s=printAdoptedGammaRecord(gammaGroup,null);
    			if(s.length()>0)
        			out.append(s);
    		}
    	}else if(ensdfGroup.adopted()!=null) {
    		ENSDF adp=ensdfGroup.adopted();
    		boolean doneEG=false,doneRI=false,doneTI=false,doneMR=false;

        	HashMap<String,Vector<String>> EGFlagSourceDSIDsMap=new HashMap<String,Vector<String>>();
        	HashMap<String,Vector<String>> RIFlagSourceDSIDsMap=new HashMap<String,Vector<String>>();
        	HashMap<String,Vector<String>> TIFlagSourceDSIDsMap=new HashMap<String,Vector<String>>();
           	HashMap<String,Vector<String>> MRFlagSourceDSIDsMap=new HashMap<String,Vector<String>>();
           	
    		for(int i=0;i<adp.comV().size();i++) {
    			Comment c=adp.commentAt(i);
    			String hs=c.head();
    			
    			if(!c.type().trim().equals("G") || (!c.headV().contains("E") && !c.headV().contains("RI")) )//look for gamma comments, type=line.substring(7,9)
    				continue;
    			
    			if(doneEG && doneRI && doneTI && doneMR)
    				break;

   				if(!doneEG && c.headV().contains("E") && !hs.contains("E(")) {
  
   					EGFlagSourceDSIDsMap=EnsdfUtil.findRecordFlagSourceDSIDsMapFromComment(c,"E");

   					Vector<String> tempV=EGFlagSourceDSIDsMap.get(" ");//flag=" " for general comment
   					adoptedEGSourceDSIDs.addAll(tempV);
   					
   					if(adoptedEGSourceDSIDs.size()>0) {
   						doneEG=true;
   					}
				}
   	
				if(!doneRI && c.headV().contains("RI") && !hs.contains("RI(")) {
					RIFlagSourceDSIDsMap=EnsdfUtil.findRecordFlagSourceDSIDsMapFromComment(c,"RI");
			
   					Vector<String> tempV=RIFlagSourceDSIDsMap.get(" ");//flag=" " for general comment
   					adoptedRISourceDSIDs.addAll(tempV);
   					
   					if(adoptedRISourceDSIDs.size()>0) {
   						doneRI=true;
   					}
				}
				
				if(!doneTI && c.headV().contains("TI") && !hs.contains("TI(")) {
					TIFlagSourceDSIDsMap=EnsdfUtil.findRecordFlagSourceDSIDsMapFromComment(c,"TI");
   					
   					Vector<String> tempV=TIFlagSourceDSIDsMap.get(" ");//flag=" " for general comment
   					adoptedTISourceDSIDs.addAll(tempV);
   					
   					if(adoptedTISourceDSIDs.size()>0) {
   						doneTI=true;
   					}
				}
				
				if(!doneMR && c.headV().contains("MR") && !hs.contains("MR(")) {
					MRFlagSourceDSIDsMap=EnsdfUtil.findRecordFlagSourceDSIDsMapFromComment(c,"MR");
			
   					Vector<String> tempV=MRFlagSourceDSIDsMap.get(" ");//flag=" " for general comment
   					adoptedMRSourceDSIDs.addAll(tempV);
   					
   					if(adoptedMRSourceDSIDs.size()>0) {
   						doneMR=true;
   					}
				}
    		}

    	}

		
    	Vector<Band> adoptedBandsV=new Vector<Band>();
    	Vector<Band> otherBandsV=new Vector<Band>();
    	HashMap<RecordGroup,String> groupFlagMap=new HashMap<RecordGroup,String>();
        LinkedHashMap<Band,String> bandFlagMap=new LinkedHashMap<Band,String>();
        
    	int nMaxBandLevels=0, nMaxBands=0,iGroupOfMaxBands=-1;
    	for(int i=0;i<ensdfGroup.nENSDF();i++) {
    	    ENSDF ens=ensdfGroup.ensdfV().get(i);
    	    
    	    int nBandLevels=0;
    	    for(Band b:ens.bandsV()) 
    	        nBandLevels+=b.nLevels();
    	    
    	    otherBandsV.addAll(ens.bandsV());
    	    
    	    if(ens.nBands()>nMaxBands && nBandLevels>nMaxBandLevels) {
    	        iGroupOfMaxBands=i;
    	        nMaxBands=ens.nBands();
    	        nMaxBandLevels=nBandLevels;
    	    }
    	}

    	if(iGroupOfMaxBands>=0) {
    	    String flags="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    	        	    
    	    ENSDF ens=ensdfGroup.ensdfV().get(iGroupOfMaxBands);
    	    adoptedBandsV.addAll(ens.bandsV());
    	    for(Band b:adoptedBandsV) {
    	        String flag=b.tag();
    	        bandFlagMap.put(b,flag);
    	        flags=flags.replace(flag,"");

    	    }
    	    
            otherBandsV.removeAll(adoptedBandsV);
            
            int count=0;
            for(Band b:otherBandsV) {
                if(!ensdfGroup.isBandOverlapAny(b,adoptedBandsV)) {
                    adoptedBandsV.add(b);
                    
                    String flag=b.tag();
                    if(flags.contains(flag))
                        flag=flags.charAt(count)+"";

                    
                    bandFlagMap.put(b,flag);
                    count++;                     
                }
            }
            
            for(Band b:bandFlagMap.keySet()) {
                Vector<RecordGroup> levGroupsV=ensdfGroup.findLevelGroupsForBand(b);
                for(RecordGroup levGroup:levGroupsV)
                    groupFlagMap.put(levGroup,bandFlagMap.get(b));
            }

    	}

    	//make band flags

		for(int i=0;i<ensdfGroup.levelGroupsV().size();i++){
    		RecordGroup levelGroup=ensdfGroup.levelGroupsV().get(i);
	
    		//skip level group that contains only level from previous adopted
    		//dataset but not any level from individual datasets
    		if(levelGroup.nRecords()==1 && levelGroup.hasAdoptedRecord())
    			continue;
    		
            String bandFlag=groupFlagMap.get(levelGroup);
            
    		s=printAdoptedLevelRecord(levelGroup,newDSIDXTagMap,bandFlag);//there is a new-line character \n at the end   		
		
            if(s.length()>0){
    			out.append(s);
    			
    			String t12s="",unit="";
    			try {
    				t12s=s.substring(39,49).trim();
    				String[] temp=t12s.split("[\\s]+");
    				if(temp.length==2)
    					unit=temp[1];
    			}catch(Exception e) {}

        		//write gammas
        		if(levelGroup.hasSubGroups()){
        			for(int k=0;k<levelGroup.subgroups().size();k++){
        				RecordGroup gammaGroup=levelGroup.subgroups().get(k);
        		        
        	    		//skip gamma group that contains only gamma from previous adopted
        	    		//dataset but not any gamma from individual datasets
        				
        				/*
        				//debug
        				System.out.println("  recordLine="+gammaGroup.getRecord(0).recordLine()+" "+gammaGroup.nRecords()+" "+gammaGroup.hasAdoptedRecord());
        				for(int m=0;m<gammaGroup.recordsV().size();m++) {
        					Gamma g=(Gamma)gammaGroup.recordsV().get(m);
        					System.out.println("   "+g.ES()+" "+gammaGroup.dsidsV().get(m));
        				}
        				*/
        				
        	    		if(gammaGroup.nRecords()==1 && gammaGroup.hasAdoptedRecord())
        	    			continue;
        	    		
        				s=printAdoptedGammaRecord(gammaGroup,levelGroup);			    	

        				if(s.length()>0) {
        					String ms="";
        					String brs="";
        					try {
        						//s=Str.fixLineLength(s, 80);
        						
          						brs=s.substring(21,29).trim();
        						ms=s.substring(31,40).trim();
        					}catch(Exception e) {}
  
        					if(!t12s.isEmpty() && EnsdfUtil.isHalflifeUnit(unit) && ms.isEmpty() && !brs.isEmpty()) {
        					    Gamma gam=(Gamma)gammaGroup.getAdoptedRecord();
        					    String JIS="",JFS="";
        					    if(gam==null) {
        					    	gam=(Gamma)gammaGroup.getReferenceRecord();
        					    	Level lev=(Level)levelGroup.getAdoptedRecord();
        					    	if(lev!=null && !lev.JPiS().isEmpty())
        					    		JIS=lev.JPiS();
        					    	else
        					    		JIS=gam.JIS();
        					    	
        					    	try {
            					    	int dsIndex=gammaGroup.recordsV().indexOf(gam);
            					    	String xtag=gammaGroup.xtagsV().get(dsIndex);
            					    	
            					    	ENSDF ens=ensdfGroup.getENSDFByXTag(xtag);
            					    	
            					    	//final level of the reference gamma
            					    	lev=ens.levelsV().get(gam.FLI());
            					    	
            					    	//find if there is an adopted level in the corresponding group that contain this final level
            					    	Vector<Level> tempV=ensdfGroup.findMatchedAdoptedLevels(lev, xtag);
                                        if(tempV.size()==1) 
                                            lev=tempV.get(0);                                        

            					    	JFS=lev.JPiS();
        					    	}catch(Exception e) {
        					    		
        					    	}

        					    }else {
        					    	JIS=gam.JIS();
        					    	JFS=gam.JFS();
        					    }
        					    /*
        					    //debug
        				    	if(gam.ES().equals("635.05")) {      
        					    	int pi=EnsdfUtil.parityNumber(JIS);
        					    	int pf=EnsdfUtil.parityNumber(JFS);
        				    		System.out.println("ConsistencyCheck 6325: ji="+JIS+" jf="+JFS+" pi="+pi+" pf="+pf+" gammaGroup.hasAdoptedRecord()="+gammaGroup.hasAdoptedRecord());
        				    		String[] jV=EnsdfUtil.parseJPI(gam.JIS());
        				    		System.out.println(" ms="+ms+" brs="+brs+" unit="+unit+"  JIS: "+EnsdfUtil.isHalflifeUnit(unit));
        				    		for(int m=0;m<jV.length;m++)
        				    			System.out.println("   "+jV[m]);
        				    				
        				    		System.out.println("   expected MUL: ");
        				    		for(String ms1:EnsdfUtil.findExpectedMULs(gam.JFS(), gam.JIS()))
        				    			System.out.println("  "+ms1);
        				    	}
    					    	*/
        					    
        					    if(gam!=null && !JIS.isEmpty() && !JFS.isEmpty()) {
        					    	
        					    	int pi=EnsdfUtil.parityNumber(JIS);
        					    	int pf=EnsdfUtil.parityNumber(JFS);
        					    	
        					    	/*
      						    	//debug
    						    	if(gam.ES().equals("635.05")) {
    						    		System.out.println("ConsistencyCheck 6346: ji="+gam.JIS()+" jf="+gam.JFS()+" pi="+pi+" pf="+pf);
    						    		String[] jV=EnsdfUtil.parseJPI(gam.JIS());
    						    		System.out.println("    JIS: ");
    						    		for(int m=0;m<jV.length;m++)
    						    			System.out.println("   "+jV[m]);
    						    				
    						    		System.out.println("   expected MUL: ");
    						    		for(String ms1:EnsdfUtil.findExpectedMULs(gam.JFS(), gam.JIS()))
    						    			System.out.println("  "+ms1);
    						    	}
    						    	*/
        					    	
        					    	if(pi*pf!=0) {
        						    	Vector<String> expectedMULs=EnsdfUtil.findExpectedMULs(JFS,JIS);
        						    	String MUL0=expectedMULs.get(0);
        						    
           						    	if(!t12s.isEmpty()) {
            						    	if(MUL0.equals("E1"))
            						    		ms="[E1]";
            						    	else if(MUL0.equals("M1") && expectedMULs.contains("E2"))
            						    		ms="[M1,E2]";
            						    	else if(expectedMULs.size()>1) {
            						    		if(MUL0.equals("M0") && expectedMULs.contains("E1"))
            						    			ms="[E1]";
            						    		else if(MUL0.equals("E0") && expectedMULs.contains("M1"))
            						    			ms="[M1,E2]";
            						    		else
            						    		    ms="["+MUL0+"]";
            						    	}else if(MUL0.startsWith("E")||MUL0.startsWith("M"))
            						    		ms="["+MUL0+"]";
        						    	}else {//dead code
        							    	if(MUL0.equals("E1"))
            						    		ms="[E1]";
            						    	else if(MUL0.equals("M1") && expectedMULs.contains("E2"))
            						    		ms="";
            						    	else if(expectedMULs.size()>1) {
            						    		if(MUL0.equals("M0"))
            						    			ms="[E1]";
            						    		else if(MUL0.equals("E0"))
            						    			ms="";
            						    		else
            						    		    ms="["+MUL0+"]";
            						    	}else if(MUL0.startsWith("E")||MUL0.startsWith("M"))
            						    		ms="["+MUL0+"]";
        						    	}  
           						    	
           						    	/*
        						    	//debug
        						    	if(gam.ES().equals("635.05")) {
        						    		System.out.println("ConsistencyCheck 6394: ji="+JIS+" jf="+JFS+" pi="+pi+" pf="+pf+" ms="+ms);
        						    		System.out.println("   expected MUL: ");
        						    		for(String ms1:expectedMULs)
        						    			System.out.println("  "+ms1);
        						    	}
        						    	*/
           						    	
        					    	}

        					    }
        					    
        					    if(!ms.isEmpty())
        					    	s=EnsdfUtil.replaceMULTOfGammaLine(s,ms);
        					}

        	    			out.append(s);
        				}

        			}
        		}

    		} 

		}
		
		//if(out.length()>0)
		//	out="\n\n"+ensdfGroup.NUCID()+"\n"+out;
		if(out.length()>0 && !CheckControl.createCombinedDataset){
			
			//print general comments and XREF list
			String heading="";
			String PNLine="";
			
			//print band comments
			if(ensdfGroup.adopted()!=null) {
				ENSDF ens=ensdfGroup.adopted();
				String NUCID=ens.nucleus().nameENSDF().trim();
				Vector<String> lines=ens.lines();
				int n=ens.firstLevelLineNo();
				boolean isBandAdded=false;
                boolean hasReachedLevelCom=false;
                
				for(int i=0;i<n;i++) {
					String line=lines.get(i);
					String lineType=line.substring(5,8).toUpperCase();
										
					if(!isBandAdded && hasReachedLevelCom && (lineType.charAt(2)=='N'||i==n-1) ){
					    for(Band b:bandFlagMap.keySet()) {
					        Comment c=b.comment();
					        String flag=bandFlagMap.get(b);
					        String cline="BAND("+flag+")$"+c.rawBody();
		                    
                            try {
                                Vector<String> linesV;
                                linesV = Str.makeENSDFLines(NUCID,"cL",cline);
                                for(String line0:linesV)
                                    heading+=Str.fixLineLength(line0, 80)+"\n";
           
                            } catch (Exception e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
					    }
					    
					    isBandAdded=true;
					}
					
					if(lineType.equals("  X")) //xref list is printed elsewhere
						continue;
					
					if(line.length()>10 && line.substring(9).trim().startsWith("BAND(") && lineType.substring(1).equals("CL")) {
						
						//skip band comments since they are printed elsewhere below
						while(i+1<n) {
							line=lines.get(i+1).toUpperCase();
							if(line.length()>10 && line.charAt(5)!=' ' && line.substring(6,8).equals("CL"))
								i++;
							else
								break;
						}
						
						continue;
					}else if(lineType.equals(" CL"))
						hasReachedLevelCom=true;
							
					if(lineType.charAt(2)=='N')
						PNLine+=Str.fixLineLength(line, 80)+"\n";
					else
						heading+=Str.fixLineLength(line, 80)+"\n";
				}
			}
			
    		out.insert(0, heading+printXREFList(ensdfGroup)+PNLine);
		}
		
		return out.toString();
    }
    
    /*
     * s: ENSDF lines concatenated by "\n"
     */
    private Comment makeComment(String s) {
    	Vector<String> linesV=Str.splitString(s,"\n");
    	linesV=Str.trimV(linesV);
    	if(linesV==null || linesV.size()==0)
    		return null;
    	
        Comment c=new Comment();
        try {
			c.setValues(linesV);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			c=null;
		}
        
        return c;
    }
    
    
    /*
     * check if comment c has updated comment in newCommentsV (generated average comments)
     */
    private boolean checkIfCommentUpdated(Comment com,Vector<Comment> newCommentsV) {
    	if(com==null || newCommentsV==null || newCommentsV.size()==0)
    		return false;
    	
    	String head=com.head();//old comment
    	String body=Str.removeEndPeriod(com.rawBody()).toUpperCase();
    	
    	for(int i=0;i<newCommentsV.size();i++) {
    		Comment c=newCommentsV.get(i);
    		String b=Str.removeEndPeriod(c.rawBody()).toUpperCase();
            String h=c.head();
    		if(head.contains(h) || h.contains(head)) {
        		
    			if(b.contains(body) || body.contains(b))
    				return true;
    			if(body.startsWith("WEIGHTED AVERAGE") || body.startsWith("UNWEIGHTED AVERAGE") 
    					|| body.startsWith("FROM WEIGHTED AVERAGE") || body.startsWith("FROM UNWEIGHTED"))
    				return true;  
    			if(body.startsWith("FROM") && b.startsWith("FROM")) {
    				String s0=body.substring(4).trim();
    				String s1=b.substring(4).trim();
    				if(s0.substring(0,5).equals(s1.substring(0,5))) //from (p,t)   						
    					return true;
    			}else if(body.startsWith("OTHER") && b.startsWith("OTHER")) {
    				return true;
    			}
    			if(head.equals(h) && body.startsWith("FROM")) {
    				String s0=body.substring(4).trim();
    				int n=s0.indexOf(".");
    				if(n>0)
    					s0=s0.substring(0,n).trim();
    				
        			if(b.contains(s0)) {
        				return true;
        			}

    			}
    		}
    	}
    	
    	return false;
    }
    
    /*
     * check if a new comment com matches similar comment in existingCommentsV (generated average comments)
     */
    private boolean checkIfCommentExist(Comment com,Vector<Comment> existingCommentsV) {
    	if(com==null || existingCommentsV==null || existingCommentsV.size()==0)
    		return false;
    	
    	String head=com.head();
    	String body=Str.removeEndPeriod(com.rawBody()).toUpperCase();
    	
    	for(int i=0;i<existingCommentsV.size();i++) {
    		Comment c=existingCommentsV.get(i);
    		String b=Str.removeEndPeriod(c.rawBody()).toUpperCase();
            String h=c.head();
            
            b=Str.firstSentance(b);
            
    		if(h.contains(head)) {
    			
    			if(b.contains(body))
    				return true;
    			if(body.contains("WEIGHTED AVERAGE"))//new comment body
    				return false;  
    			if(body.startsWith("FROM")) {
    				String s0=body.substring(4).trim();
    				if(b.contains(s0)) 
        				return true;

    			}
    		}
    	}
    	
    	return false;
    }
    
    /*
     * print adopted level based on all data from the levelGroup
     */
    public String printAdoptedLevelRecord(RecordGroup levelGroup,HashMap<String,String> newDSIDXTagMap,String bandFlag){
    	StringBuilder out=new StringBuilder();
    	
		String recordLine="",newXTags="";        		
		Vector<String> newXTagLinesV=new Vector<String>(); 
		
    	boolean hasAdopted=false;
		float adoptedEL=0;
		boolean isGS=false;
		
		Level adoptedLevel=(Level)levelGroup.getAdoptedRecord();
		Level refLev=adoptedLevel;
		
		if(adoptedLevel!=null){
			recordLine=adoptedLevel.recordLine();
		    hasAdopted=true;
		    
		    adoptedEL=adoptedLevel.EF();
		}else{
			refLev=(Level)levelGroup.getReferenceRecord();
			if(refLev==null) 
				refLev=levelGroup.getRecord(0);
			
			recordLine=refLev.recordLine();
			
			hasAdopted=false;
			
			adoptedEL=refLev.EF();
			
			if(levelGroup.nRecords()==1 && (refLev.msS().equals("R")||refLev.isPseudo()) )
				return "";
		}
        
		if(adoptedEL==0)
			isGS=true;
		
		recordLine=Str.fixLineLength(recordLine,80);

		String NUCID=recordLine.substring(0,5);
	    
		//debug
		//if(recordLine.contains("1798."))
	    //	System.out.println(" 0  recordLine="+recordLine);
	    
		if(!CheckControl.createCombinedDataset) {
			if(adoptedLevel!=null)
				recordLine=recordLine.substring(0,55)+Str.repeat(" ", 21)+recordLine.substring(76);//remove L, S and keep adopted flag record 
			else {
				recordLine=recordLine.substring(0,55)+Str.repeat(" ", 22)+recordLine.substring(77);//remove L, S 
				
				//remove T if it has been relabeled as other quantity
				Level l=(Level)refLev;
				if(l.T12Unit().isEmpty())
					recordLine=recordLine.substring(0,39)+Str.repeat(" ",16)+recordLine.substring(55);
			}
		    //if(recordLine.contains("0.0  "))
		    //	System.out.println(" 0.1  recordLine="+recordLine);
		    
			recordLine=EnsdfUtil.trimENSDFLine(recordLine);
			
		    //if(recordLine.contains("0.0  "))
		    //	System.out.println(" 0.2  recordLine="+recordLine);
		}

					
		newXTags="";
		boolean hasGammaLevel=false;

		/*
		//debug
		for(int k=0;k<levelGroup.subgroups().size();k++){
			RecordGroup gammaGroup=levelGroup.subgroups().get(k);
			System.out.println("@@@ ConsistencyCheck 6650 "+gammaGroup.nRecords());
			for(int m=0;m<gammaGroup.recordsV().size();m++) {
				Gamma g=(Gamma)gammaGroup.recordsV().get(m);
				System.out.println("   "+g.ES()+" "+gammaGroup.dsidsV().get(m));
			}
		}
		*/
		for(int j=0;j<levelGroup.nRecords();j++){
			
			Level lev=(Level)levelGroup.getRecord(j);
			float levEL=lev.EF();
			
			String dsid=levelGroup.getDSID(j);
			String xtag=levelGroup.getXTag(j);
			String newXTag=newDSIDXTagMap.get(dsid);
			if(newXTag==null)
				newXTag="?";
			    	
			/*
    		//debug
    		if(Math.abs(lev.EF()-6697)<2){
        		System.out.println("In ConsistencyCheck line 6534: j="+j+" dsid="+dsid+" xtag="+xtag+" new xtag="+newXTag+" group size="+levelGroup.nRecords());
    		}
			*/
			
			if(dsid.contains("ADOPTED LEVELS"))
				continue;
			
			String tempXTags=newXTags;
			
			
			///////////
			String xtagWithMarker=xtag;
			String es=lev.ES();
			int n=xtag.indexOf("*");
			if(n<0)
				n=xtag.indexOf("?)");
			
			if(n>0 && !xtag.contains(es) && !es.contains(".")) 
				xtagWithMarker=xtag.substring(0, n)+es+xtag.substring(n);
			else if(!xtag.contains(es) && !es.contains(".") && Math.abs(adoptedEL-levEL)>Math.max(10,adoptedEL/500)) {
				n=xtag.indexOf("(");
				if(n>=0){
					if(xtag.indexOf(")",n)==n+1) 
						xtagWithMarker=xtag.substring(0, n)+"("+es+xtag.substring(n+1);
				}else{
					xtagWithMarker=xtag+"("+es+")";
				}
			}
			
		
			///////////
			

			
			//note that (*), (?) marks are added only for old tags if both old and new tag exist, 
			//but not for new tags for display purpose 
			//(old and new tag displayed side by side in output, new tag first and the old tag)
			tempXTags+=newXTag;
			
			int index=xtagWithMarker.indexOf("(");
			//if(xtag.contains("(") && Character.isLetter(xtag.charAt(0)))
			if(index>0)//some new datasets has xtag=?0, ?1, ...	
				tempXTags+=xtagWithMarker.substring(index);	
			
			/*
			if(Str.isInteger(es) && Math.abs(adoptedEL-levEL)>Math.max(10,adoptedEL/500)) {
				if(tempXTags.endsWith(")"))
					tempXTags=tempXTags.substring(0,tempXTags.length()-1)+lev.ES()+")";
				else
					tempXTags+="("+lev.ES()+")";
			}
			*/
			
			
			//System.out.println(" EL="+levelGroup.getRecord(0).EF()+" dsid="+dsid+" xtag="+xtag+" newXTag="+newXTag);
			
            if(tempXTags.length()>=65) {//max length of xtags after "XREF+="
            	newXTagLinesV.add(newXTags);
            	newXTags=tempXTags.substring(newXTags.length());
            }else
            	newXTags=tempXTags;
            
	
			if(((Level)levelGroup.getRecord(j)).nGammas()>0)
				hasGammaLevel=true;

		}
		
		if(newXTags.length()>0 && !newXTagLinesV.contains(newXTags))
			newXTagLinesV.add(newXTags);
			
		//get averaging comments if available, removing adopted dataset only for averaging
		RecordGroup tempGroup=levelGroup.lightCopy();
 	    if(levelGroup.getDSID(0).contains("ADOPTED"))
 	    	tempGroup.remove(0);
 	    
 	    for(int i=tempGroup.nRecords()-1;i>=0;i--) {
 	        String xtag=tempGroup.xtagsV().get(i);
 	        String xtagMarker=Util.getXTagMarker(xtag);
 	        
 	        /*
    		//debug
    		if(Math.abs(tempGroup.recordsV().get(i).EF()-6697)<2){
        		System.out.println("In ConsistencyCheck line 6616: i="+i+" dsid="+tempGroup.dsidsV().get(i)+" xtag="+xtag+" xtagMarker="+xtagMarker+" group size="+tempGroup.nRecords());
    		}
    		*/
 	        
 	        if(xtagMarker.contains("?") || xtagMarker.contains("*"))
 	            tempGroup.remove(i);
 	    }
 	    
 	    if(tempGroup.nRecords()==0) {
 	    	Record refRecord=levelGroup.getReferenceRecord();
 	    	if(refRecord==null && levelGroup.nRecords()>0)
 	    		refRecord=levelGroup.getRecord(0);
 	    	
 	    	if(refRecord==null)
 	    		return "";
            int index=levelGroup.recordsV().indexOf(refRecord);
            
 	    	if(index<0)	
 	    	    return "";
 	    	
 	    	String dsid=levelGroup.dsidsV().get(index);
 	    	String xtag=levelGroup.xtagsV().get(index);
 	    	
 	    	tempGroup.addRecord(refRecord, dsid, xtag);
 	    }
 	    
 	    Vector<Comment> newCommentsV=new Vector<Comment>();
	    String commentS="",tempS="",s="",ds="",unit="";
	    
	    String dsidOfBestEL="", dsidOfBestT="";
	    Level levBestEL=null;
	    
	    AverageReport ar=null;
	    if(!isGS)
	    	ar=getAverageReport(tempGroup,"T");
	    
	    //System.out.println("ConsistencyCheck 6643: "+(ar==null)+" EL="+adoptedEL);
	    
	    if(ar!=null){
	    	tempS=ar.getAdoptedComment();
		    
	    	/*
	    	if(Math.abs(levelGroup.getMeanEnergy()-3092)<5) {
	    		System.out.println(" EL="+adoptedEL+" report="+ar.getReport()+" adoptedValStr()="+ar.adoptedValStr()+" tempS="+tempS+" unit="+unit);
	    		System.out.println(" adopted comment="+ar.getAdoptedComment());
	    		for(Record r:levelGroup.recordsV()) {
	    			Level lev=(Level)r;
	    			System.out.println("**** E="+lev.ES()+" JPI="+lev.JPiS()+" T="+lev.T12S());
	    		}
	    	}
	    	*/
	    	
	    	if(ar.getAverage().nDataPoints()>0){
	        	unit=ar.getAverage().dataPointsV().get(0).unit();

	        }        
	    }

	    
	    if(!tempS.isEmpty()){
	    	commentS+=tempS+"\n";
	    	
        	if(unit.toUpperCase().equals("|MS"))
        		unit="US";
        	
	    	recordLine=EnsdfUtil.replaceHalflifeOfLevelLine(recordLine, ar.adoptedValStr(), ar.adoptedUncStr(), unit);//average T
	    	
	    	Comment c=makeComment(tempS);
	    	if(c!=null)
	    		newCommentsV.add(c);
	    	
	    }else if(!isGS){    	
	    	Level lev=findBest(tempGroup,"T");
	    	
	    	//if(Math.abs(levelGroup.getMeanEnergy()-85)<5)
	    	//System.out.println("ConsistencyCheck 6675: best level: "+(lev==null)+" EL="+adoptedEL);
	    	
	    	if(lev!=null) {
	    		s=lev.T12S();
	    		ds=lev.DT12S();
	    		unit=lev.T12Unit();

	    		int il=tempGroup.recordsV().indexOf(lev);
	    		dsidOfBestT=tempGroup.getDSID(il);
	    		
	    		ENSDF ens=this.getENSDFByDSID(dsidOfBestT);
	    		if(isRecordFromAdopted(lev,"T",ens)) {
	    			dsidOfBestT="";
	    		}
	    		/*
	    		if(Math.abs(levelGroup.getMeanEnergy()-85)<5) {
	    			System.out.println("ConsistencyCheck 6682: best level="+lev.ES()+" T="+s+" adopted EL="+adoptedEL+" group size="+tempGroup.nRecords()+" "+tempGroup.dsidsV().get(0));
	    		    System.out.println("               dsidOfBestT="+dsidOfBestT);
	    		}
	    		*/
	    		
	    		if(!unit.isEmpty())
	    			recordLine=EnsdfUtil.replaceHalflifeOfLevelLine(recordLine, s,ds, unit);
	    	}	    	

	    }

	    //average E(level) if there is not any level that has a gamma
	    if(CheckControl.alwaysAverageEL || !hasGammaLevel) {
	    	tempS="";
	    	s="";
	    	ds="";
		    ar=getAverageReport(tempGroup,"E");
		    if(ar!=null)
		    	tempS=ar.getAdoptedComment();
		    
		    //if(recordLine.contains("606")) 
		    //System.out.println("  recordLine="+recordLine+" tempGroup.size="+tempGroup.nRecords()+" (ar==null)="+(ar==null)+"  s="+tempS+"*");

		    
		    if(!tempS.isEmpty()){
		    	commentS+=tempS+"\n";
		    	s=ar.adoptedValStr();
		    	ds=ar.adoptedUncStr();
		    	
		    	
		    	Comment c=makeComment(tempS);
		    	if(c!=null)
		    		newCommentsV.add(c);

		    }else {
		    	levBestEL=findBest(tempGroup,"E");
		    	if(levBestEL!=null) {
		    		s=levBestEL.ES();
		    		ds=levBestEL.DES();
		    		
		    		int il=tempGroup.recordsV().indexOf(levBestEL);
		    		dsidOfBestEL=tempGroup.getDSID(il);
		    	}
		    	
		       	//System.out.println("0 recordLine="+recordLine+" s="+s+"  ds="+ds+"  "+(gam==null)+" "+tempGroup.nRecords());
		    }
		    
		    /*
		    //debug	
		    if(tempGroup.nRecords()>0) {
		    //if(recordLine.contains("7980")) {
		    	float ef=tempGroup.recordsV().get(0).EF();
		    	if(Math.abs(ef-7980)<100) {
			    	for(Record rec:tempGroup.recordsV())
			    		System.out.println(" ES=  "+rec.ES()+"  "+rec.DES());
			    		
			    	if(ar!=null)
			    		System.out.println(" average="+ar.adoptedValStr()+"  "+ar.adoptedUncStr()+"  s="+s+" ds="+ds+" recordLine="+recordLine);
		    	}

		    }
		    */

	    	if(!s.isEmpty()) {
	    		s=s.toUpperCase();

	    		recordLine=EnsdfUtil.replaceEnergyOfRecordLine(recordLine, s, ds);//average EL
    	    	if(!dsidOfBestEL.isEmpty() && recordLine.charAt(79)!=' ' && levBestEL.recordLine().charAt(79)==' ') {
    	    		recordLine=recordLine.substring(0,79)+' ';
    	    	}
	    	}
	    }

	    //average E(level) if there is not any level that has a gamma
	    if(CheckControl.averageC2S) {
	    	
	    	boolean canBeAveraged=false;
	    	
	    	for(Record r:tempGroup.recordsV()) {
	    		Level lev=(Level)r;
	    		if(!lev.sS().isEmpty()) {
	    			if(!Str.isNumeric(lev.sS())){    		
		    			canBeAveraged=false;
		    			break;    		
	    			}else {
	    	    		canBeAveraged=true;
	    			}
	    		}	    		
	    	}
	    	if(canBeAveraged) {
		    	tempS="";
		    	s="";
		    	ds="";
			    ar=getAverageReport(tempGroup,"S");
			    if(ar!=null)
			    	tempS=ar.getAdoptedComment();
			    
			    //if(recordLine.contains("606")) 
			    /*
			    System.out.println("ConsistencyCheck 7594:  recordLine="+recordLine+" tempGroup.size="+tempGroup.nRecords()+" (ar==null)="+(ar==null)+"  s="+tempS+"*");
                for(Record r:tempGroup.recordsV()) {
                	Level l=(Level)r;
                	System.out.println("### "+l.sS()+"#"+l.dsS());
                }
                */
			    
			    if(!tempS.isEmpty()){
			    	commentS+=tempS+"\n";
			    	s=ar.adoptedValStr();
			    	ds=ar.adoptedUncStr();
			    	
			    	
			    	Comment c=makeComment(tempS);
			    	if(c!=null)
			    		newCommentsV.add(c);

			    }

		    	if(!s.isEmpty()) {
		    		s=s.toUpperCase();
		    		recordLine=EnsdfUtil.replaceC2SOfLevelLine(recordLine, s, ds);//average relabelled C2S
		    	}
	    	}
	    	

	    }
	    
	    if(bandFlag!=null && bandFlag.length()>=1) { 
	        bandFlag=bandFlag.substring(0,1);
	        recordLine=recordLine.substring(0,76)+bandFlag+recordLine.substring(77);
	    }

		out.append(recordLine+"\n");
		
		//add new XREF
		//note that here new xtags are already in order but old tags are not
		if(newXTagLinesV.size()>0 && !CheckControl.createCombinedDataset){
			String temp=NUCID+"X L XREF="+newXTagLinesV.get(0);
			temp+=Str.repeat(" ", 80-temp.length());
			out.append(temp+"\n");

			
			for(int x=1;x<newXTagLinesV.size();x++) {
				temp=NUCID+"X L XREF+="+newXTagLinesV.get(x);
    			temp+=Str.repeat(" ", 80-temp.length());
    			out.append(temp+"\n");

			}
		} 
		
    	String tempS1="",tempS2="";
    	if(tempGroup.nRecords()>1) {

        	if(dsidOfBestEL.length()>0 && dsidOfBestT.length()>0) {
        		if(dsidOfBestEL.equals(dsidOfBestT)) {
        			if(isGS)
        				tempS1=recordLine.substring(0,5)+" cL T$from "+EnsdfUtil.makeLabelFromDSID(dsidOfBestEL,
        						currentEnsdfGroup.dsidsVWithDuplicateShortID().contains(dsidOfBestEL));
        			else
        				tempS1=recordLine.substring(0,5)+" cL E,T$from "+EnsdfUtil.makeLabelFromDSID(dsidOfBestEL,
        						currentEnsdfGroup.dsidsVWithDuplicateShortID().contains(dsidOfBestEL));
        		}else {
        			if(!isGS)
        				tempS1=recordLine.substring(0,5)+" cL E$from "+EnsdfUtil.makeLabelFromDSID(dsidOfBestEL,
        						currentEnsdfGroup.dsidsVWithDuplicateShortID().contains(dsidOfBestEL));
        			
        			tempS2=recordLine.substring(0,5)+" cL T$from "+EnsdfUtil.makeLabelFromDSID(dsidOfBestT,
        					currentEnsdfGroup.dsidsVWithDuplicateShortID().contains(dsidOfBestT));
        		}
        	}else if(dsidOfBestEL.length()>0){
        		if(!isGS)
        			tempS1=recordLine.substring(0,5)+" cL E$from "+EnsdfUtil.makeLabelFromDSID(dsidOfBestEL,
        					currentEnsdfGroup.dsidsVWithDuplicateShortID().contains(dsidOfBestEL));
        	}else if(dsidOfBestT.length()>0) {
        		tempS1=recordLine.substring(0,5)+" cL T$from "+EnsdfUtil.makeLabelFromDSID(dsidOfBestT,
        				currentEnsdfGroup.dsidsVWithDuplicateShortID().contains(dsidOfBestT));
        	}
    	}

    	
    	if(tempS1.length()>0) {
	    	Comment c=makeComment(tempS1);
	    	if(c!=null)
	    		newCommentsV.add(c);
	    	
	    	commentS+=tempS1+"\n";
    	}
    	if(tempS2.length()>0) {
	    	Comment c=makeComment(tempS2);
	    	if(c!=null)
	    		newCommentsV.add(c);
	    	
	    	commentS+=tempS2+"\n";
    	}
    	
	    String contCommentS="",otherCommentS="";
		if(CheckControl.createCombinedDataset) {
			
			//For combining input datasets:
			//write existing level comments from all datasets including Adopted		
			
			for(Record l:levelGroup.recordsV()) {
				for(String line:l.contRecsLineV())
					contCommentS+=line+"\n";
				
				for(Comment c:l.commentV()){
					if(!checkIfCommentUpdated(c,newCommentsV)) {
						for(String line:c.lines()) {
							otherCommentS+=c.checkEndPeriod(line)+"\n";
						}
					}
					
					/*
					for(String line:c.lines()) {
						tempS=Str.removeEndPeriod(line);
						if(commentS.contains(tempS))
							continue;
						
						otherCommentS+=line+"\n";
					}
					*/
				}
			}
		}else if(hasAdopted) {
			
			//for creating an new Adopted dataset from input datasets:
			//write existing level comments only from the Adopted dataset
			
			for(String line:adoptedLevel.contRecsLineV()){
				if(line.length()>0 && !line.contains("X L XREF"))
					contCommentS+=line+"\n";
			}
			
			for(Comment c:adoptedLevel.commentV()){
				
				//remove comment generated when parsing a XREF tag in the XREF record of an adopted level where an energy is given, like XREF=ACK(2090)L
				//NOTE that in such a comment, xtag in use is assigned in ascending alphabetical order according to the original order of the dataset appearing in the XREF list.
				//But in the grouping of datasets, the datasets are re-ordered based on reaction DSID and the assignment of the new XREF tag is different from above.
				if(c.head().equals("XREF"))
					continue;
				
				if(!checkIfCommentUpdated(c,newCommentsV)) {
					for(String line:c.lines()) {
						otherCommentS+=c.checkEndPeriod(line)+"\n";
					}
				}
			}
			
			//document records
			for(Comment c:adoptedLevel.documentV()){
				for(String line:c.lines()) {
					otherCommentS+=c.checkEndPeriod(line)+"\n";
				}
			}
		}
		
		if(!contCommentS.isEmpty())
			out.append(contCommentS);
	    if(!commentS.isEmpty())
	    	out.append(commentS);
	    if(!otherCommentS.isEmpty())
	    	out.append(otherCommentS);
	    
	    
	    //write decay/delay if there is any, only when combining all datasets
		if(CheckControl.createCombinedDataset) {
			
			//For combining input datasets:
			//write existing level comments from all datasets including Adopted		
			
			for(Record r:levelGroup.recordsV()) {
				Level l=(Level)r;
				
				for(Record d:l.DecaysV()) {
					
					out.append(d.recordLine()+"\n");
					
					for(String line:d.contRecsLineV()){
						if(line.length()>0)
							out.append(line+"\n");
					}
					
					for(Comment c:d.commentV()){
						for(String line:c.lines()) {
							if(line.length()>0)
								out.append(c.checkEndPeriod(line)+"\n");
						}
					}
				}	
			}
		}
	    
		return out.toString();
    }
    

    /*
     * print adopted gamma based on all data from the gammaGroup
     */

    public String printAdoptedGammaRecord(RecordGroup gammaGroup,RecordGroup levelGroup){
    	StringBuilder out=new StringBuilder();
    	
		String recordLine="";        		
   		
    	boolean hasAdopted=true;
		Gamma adoptedGamma=(Gamma)gammaGroup.getAdoptedRecord();
		if(adoptedGamma==null){
			adoptedGamma=(Gamma)gammaGroup.getReferenceRecord();
			if(adoptedGamma==null)
				adoptedGamma=gammaGroup.getRecord(0);
			
			hasAdopted=false;
		}
        
		recordLine=Str.fixLineLength(recordLine,80);

		//if(Control.convertRIForAdopted && !Control.createCombinedDataset) {
		recordLine=adoptedGamma.recordLine();
		if(CheckControl.convertRIForAdopted) 
			recordLine=gammaGroup.replaceIntensityOfGammaLineWithBR(adoptedGamma,CheckControl.errorLimit,"ADOPTED"); 

		boolean isUndividedRI=false;
		if(!CheckControl.createCombinedDataset) {
			
			//If there is only one record and it is not in adopted, then keep its &,@,* flag,
			//and convert its RI to limit if available
			char c=recordLine.charAt(76);
			if(!hasAdopted) {
				if("&@*".indexOf(c)>=0 && gammaGroup.nRecords()==1) {
					if(c=='&')
						isUndividedRI=true;
				}else 
					c=' ';	
			}
		
			recordLine=recordLine.substring(0,55)+Str.repeat(" ", 21)+c+" "+recordLine.substring(78);//remove flag, COIN label ("C" at col=78) record if not &,*,@ 
			
			recordLine=EnsdfUtil.trimENSDFLine(recordLine);
		}

						
		//get averaging comments if available
		RecordGroup tempGroup=gammaGroup;
 	    if(gammaGroup.getDSID(0).contains("ADOPTED")){
 	    	tempGroup=gammaGroup.lightCopy();
 	    	tempGroup.remove(0);
 	    }

	    String commentS="",tempS="",s="",ds="";	    
	    Vector<Comment> newCommentsV=new Vector<Comment>();
	    
	    AverageReport ar=getAverageReport(tempGroup,"E");
	    if(ar!=null)
	    	tempS=ar.getAdoptedComment();
	    
	    //if(recordLine.contains("695.7.0")) 
	    //System.out.println("  recordLine="+recordLine+" tempGroup.size="+tempGroup.nRecords()+" (ar==null)="+(ar==null)+"  s="+tempS+"*");

	    String dsidOfBestEG="", dsidOfBestRI="",dsidOfBestTI="";
	    Gamma gamBestEG=null,gamBestRI=null,gamBestTI=null;
	    
	    if(!tempS.isEmpty()){
	    	if(ar.isNonAverage() && tempS.contains(". Other") && adoptedEGSourceDSIDs.size()>0) {
	    		int n1=tempS.indexOf("E$");
	    		int n2=tempS.indexOf(". Other");
	    		if(n1>0 && n2>n1) {
	    			int index=ar.nonaverageIndex();
	    			
	    			/*
	    			//debug
	    			if(Math.abs(ar.value()-48.76)<1) {
	    				System.out.println("ConsistencyCheck 7289: index="+index+" temp G size="+tempGroup.nRecords()+" ar size="+ar.getAverage().nDataPoints());
	    			}
	    			*/
	    			
	    			//NOTE that records used in average could be not exactly the same group as tempGroup
	    			//since some records could be filtered out for average (e.g., from Adopted dataset)
	    			//String dsid=tempGroup.getDSID(index);//upper-case ENSDF format
	    			String dsid=ar.getAverage().getLabel(index);
	    			
	    			try {
		    			dsid=Translator.procGenCom(dsid,0,false);//upper "C" to lower "c" translation
	    			}catch(Exception e) {
	    				
	    			}

	    			boolean toChange=false;
	    			
	    			String dsid1=DSIDMatcher.shorten(dsid);
	    			if(adoptedEGSourceDSIDs.contains(dsid) || adoptedEGSourceDSIDs.contains(dsid1))
	    				toChange=true;
	    			
	    			/*
    				if(Math.abs(levelGroup.getMeanEnergy()-85)<5) {
    					System.out.println("ConsistencyCheck 7312: dataset dsid="+dsid+" comment dsid1="+dsid1+" tempS="+tempS+" "+toChange);
    				    adoptedEGSourceDSIDs.print();
    				}
    				*/
	    			//old
	    			/*
	    			for(String dsid1:adoptedEGSourceDSIDs) {
	    				//if(Math.abs(levelGroup.getMeanEnergy()-85)<5) 
	    				//System.out.println("ConsistencyCheck 7187: dataset dsid="+dsid+" comment dsid="+dsid1+" tempS="+tempS);
	    				
	    				int n=dsid1.indexOf("(");

	    				int ntries=0;
	    				while(ntries<=1) {
		    				if(dsid1.equals(dsid) || dsid.contains(dsid1)) {
		    					//note that dsid from getDSID() is full DSID, while dsid1 from general comment
		    					//could be a part of a DSID, e.g., dsid=A(B,C), dsid1=(B,C)
		    					toChange=true;
		    					break;
		    				}else if(n>0) {
		        				dsid1=dsid.substring(n).trim();
			    				ntries++;
		        				continue;
		    				}
		    				ntries++;
	    				}

	    			}
	    			*/
	    			
	    			if(toChange) {
		    			tempS=tempS.substring(0,n1+2)+"o"+tempS.substring(n2+3);
		    			tempS=EnsdfUtil.wrapENSDFLines(tempS);
	    			}

	    		}

	    	}
	    	
	    	commentS+=tempS+"\n";
	    	s=ar.adoptedValStr();
	    	ds=ar.adoptedUncStr();
	    	
	    	//System.out.println(" s="+s+" ds="+ds+" x="+ar.adoptedVal()+" dx="+ar.adoptedUnc());
	    	
	    	Comment c=makeComment(tempS);
	    	if(c!=null)
	    		newCommentsV.add(c);

	    }else {
	    	gamBestEG=findBest(tempGroup,"E");
	    	if(gamBestEG!=null) {
	    		s=gamBestEG.ES();
	    		ds=gamBestEG.DES();
	    		
	    		int ig=tempGroup.recordsV().indexOf(gamBestEG);
	    		dsidOfBestEG=tempGroup.getDSID(ig);
	    		
	    		ENSDF ens=this.getENSDFByDSID(dsidOfBestEG);
	    		if(isRecordFromAdopted(gamBestEG,"E",ens)) {
	    			dsidOfBestEG="";
	    		}
	
	    		/*
		    	if(gamBestEG.EF()<=50 && gamBestEG.EF()>=48) {
		    		System.out.println("ConsistencyCheck 7374: recordLine="+recordLine+" s="+s+"  ds="+ds+" dsidOfBestEG="+dsidOfBestEG+"  "+tempGroup.nRecords());
		    		for(int i=0;i<tempGroup.nRecords();i++)
		    			System.out.println("              "+tempGroup.getRecord(i).ES()+" dsid="+tempGroup.getDSID(i));
		    	}
		    	*/
	    	}
		    
		    //if(tempGroup.nRecords()==1 && tempGroup.recordsV().get(0).ES().equals("313.3"))

	    }

	    //debug
	    /*
	    if(recordLine.contains("G 128.")) {
	    	for(Record rec:tempGroup.recordsV())
	    		System.out.println(" ES=  "+rec.ES()+"  "+rec.DES()+"  RI= "+rec.IS()+"  "+rec.DIS());
	    		
	    	if(ar!=null)
	    		System.out.println(" average="+ar.adoptedValStr()+"  "+ar.adoptedUncStr()+"  s="+s+" ds="+ds);
	    }
	    */
    	
	    if(!s.isEmpty()) {
	    	recordLine=EnsdfUtil.replaceEnergyOfRecordLine(recordLine, s, ds);//average EG
	    	if(!dsidOfBestEG.isEmpty() && recordLine.charAt(79)!=' ' && gamBestEG.recordLine().charAt(79)==' ') {
	    		recordLine=recordLine.substring(0,79)+' ';
	    	}
	    }
	    
	    tempS="";
	    s="";
	    ds="";
	    ar=getAverageReport(tempGroup,"RI");
	    if(ar!=null)
	    	tempS=ar.getAdoptedComment();
	    
	    //for(Record r:tempGroup.recordsV())
	    //	System.out.println("**"+r.recordLine());
	    
	    //System.out.println("     tempS="+tempS+" "+ar.adoptedValStr()+" "+ar.adoptedUncStr()+" val="+ar.valueStr()+" "+ar.errorStr());

	    
	    if(!tempS.isEmpty()){
	    	if(ar.isNonAverage() && tempS.contains(". Other") && adoptedRISourceDSIDs.size()>0) {
	    		int n1=tempS.indexOf("RI$");
	    		int n2=tempS.indexOf(". Other");
	    		if(n1>0 && n2>n1) {
	    			int index=ar.nonaverageIndex();
	    			//String dsid=tempGroup.getDSID(index);//upper-case ENSDF format
	    			String dsid=ar.getAverage().getLabel(index);
	    			
	    			try {
		    			dsid=Translator.procGenCom(dsid,0,false);//upper "C" to lower "c" translation
	    			}catch(Exception e) {
	    				
	    			}

	    			boolean toChange=false;
	    			
	    			String dsid1=DSIDMatcher.shorten(dsid);
	    			if(adoptedRISourceDSIDs.contains(dsid) || adoptedRISourceDSIDs.contains(dsid1))
	    				toChange=true;
	    			/*
	    			//old
	    			for(String dsid1:adoptedRISourceDSIDs) {
	    				//System.out.println("ConsistencyCheck 7187: dataset dsid="+dsid+" comment dsid="+dsid1);
	    				
	    				int n=dsid1.indexOf("(");

	    				int ntries=0;
	    				while(ntries<=1) {
		    				if(dsid1.equals(dsid) || dsid.contains(dsid1)) {
		    					//note that dsid from getDSID() is full DSID, while dsid1 from general comment
		    					//could be a part of a DSID, e.g., dsid=A(B,C), dsid1=(B,C)
		    					toChange=true;
		    					break;
		    				}else if(n>0) {
		        				dsid1=dsid.substring(n).trim();
			    				ntries++;
		        				continue;
		    				}
		    				ntries++;
	    				}
	
	    			}
	    			*/
	    			
	    			if(toChange) {
		    			tempS=tempS.substring(0,n1+3)+"o"+tempS.substring(n2+3);
		    			tempS=EnsdfUtil.wrapENSDFLines(tempS);
	    			}

	    		}

	    	}
	    	
	    	commentS+=tempS+"\n";
	    	s=ar.adoptedValStr();
	    	ds=ar.adoptedUncStr();
	    	
	    	Comment c=makeComment(tempS);
	    	if(c!=null)
	    		newCommentsV.add(c);
	    }else if(CheckControl.isUseAverageSettings && ar!=null && ar.getAverage().goodPointIndexesV().size()==1){//for using a single comment value
	        int index=ar.getAverage().goodPointIndexesV().get(0);
	        DataPoint dp=ar.getAverage().dataPointsV().get(index);
	        s=dp.s();
	        ds=dp.ds();

    		dsidOfBestRI=dp.label();//for comment value, label is set using DSID
	    }else {
	    	gamBestRI=findBest(tempGroup,"RI");
	    	RecordGroup tempGroup1=tempGroup.lightCopy();
	    	Gamma tempGam=gamBestRI;
	    	while(tempGam!=null) {
	    	    
	    	    /*
	    	    //debug
	    		System.out.println("ConsistencyCheck 7116 tempGam="+tempGam.ES()+" RI="+tempGam.RIS()+"  "+tempGam.DRIS()+"  BR="+tempGam.RelBRS()+" "
	    	                         +tempGam.DRelBRS()+" RelBR="+tempGam.DRIS()+" "+tempGroup1.nRecords());
	    		System.out.println("     "+tempGroup1.recordsV().contains(tempGam));
	    		*/
	    	    
	    		boolean isSingleBranch=false;
	    		if(Str.isNumeric(tempGam.DRIS()) && tempGam.DRelBRS().isEmpty())
	    		    isSingleBranch=true;
	    		else {
	    		    try {
	                    int gamIndex=tempGroup1.recordsV().indexOf(tempGam);
                        String dsid=tempGroup1.dsidsV().get(gamIndex);
                        int levIndex=levelGroup.dsidsV().indexOf(dsid);
                        Level lev=(Level)levelGroup.recordsV().get(levIndex);
                        if(lev.nGammas()==1)
                            isSingleBranch=true;
	    		    }catch(Exception e) {
	    		        
	    		    }
	    		}

	                     
		    	if(isSingleBranch) {//remove single-branch transition
	                 
	    			tempGroup1.remove(tempGam);
	    			tempGam=findBest(tempGroup1,"RI");
	                
	    			continue;
		    	}
		    	
		    	gamBestRI=tempGam;
		    	
		    	break;
	    	}
	    	
	    	if(gamBestRI!=null) {
	    		
	    		int ig=tempGroup.recordsV().indexOf(gamBestRI);
	    		dsidOfBestRI=tempGroup.getDSID(ig);
	    		
	    		ENSDF ens=this.getENSDFByDSID(dsidOfBestRI);
	    		if(isRecordFromAdopted(gamBestRI,"E",ens)) {//"E" for gamma being commented from Adopted Gammas; DO NOT USE "RI"
	    			dsidOfBestRI="";
	    		}
	    		
	    		String line="";
	    		if(CheckControl.convertRIForAdopted)
	    		//if(Control.convertRIForAdopted && !Control.createCombinedDataset)
	    			line=gammaGroup.replaceIntensityOfGammaLineWithBR(gamBestRI,CheckControl.errorLimit,"ADOPTED");
	    		else
	    			line=gamBestRI.recordLine();
	    		
	    		try {
		    		s=line.substring(21,29).trim();
		    		ds=line.substring(29,31).trim();
		    		
		            if(!CheckControl.createCombinedDataset && line.charAt(76)=='&') 
		                isUndividedRI=true;
		    		
		            //if(gamBestRI.ES().equals("92.0")) System.out.println(" ConsistencyCheck 7135: s="+s+" ds="+ds+" isUndividedRI="+isUndividedRI);
		            
	    		}catch(Exception e) {}
	    	}
	    }
	    
	    //if(gamBestEG!=null && gamBestEG.ES().equals("605.5"))
	    //	System.out.println(" ConsistencyCheck 7448: E="+gamBestEG.ES()+" RI="+s+" ds="+ds+" "+levelGroup.subgroups().size()+"  "+(EnsdfUtil.s2f(s)==100));
	    
	    if(levelGroup!=null && levelGroup.subgroups().size()==1 && EnsdfUtil.s2f(s)==100 && ds.isEmpty()) {
	    	dsidOfBestRI="";
	    }
	    
		//convert undivided numerical RI to limit
		if(isUndividedRI && Str.isNumeric(s) && Str.isNumeric(ds)) {
			SDS2XDX s2x=new SDS2XDX(s,ds);
			double x=s2x.x()+s2x.dx();
			double dx=s2x.dx();
			XDX2SDS x2s=new XDX2SDS(x,dx);
			s=x2s.s();
			ds="LT";
			if(s.contains("E")) {
			    x2s=new XDX2SDS(x,dx,99);
			    s=x2s.s();
			}
			
			char c=recordLine.charAt(76);
			if(c!='&')
				recordLine=recordLine.substring(0,76)+"&"+recordLine.substring(77);
		}
		
    	recordLine=EnsdfUtil.replaceRIOfGammaLine(recordLine, s, ds);
    	
    	///////////////////////////////////
    	//For TI (for some cases, TI field can be used to hold other types of values for avaraging purpose)
    	///////////////////////////////////
	    tempS="";
	    s="";
	    ds="";
	    ar=getAverageReport(tempGroup,"TI");
	    if(ar!=null)
	    	tempS=ar.getAdoptedComment();

	    if(!tempS.isEmpty()){
	    	if(ar.isNonAverage() && tempS.contains(". Other") && adoptedTISourceDSIDs.size()>0) {
	    		int n1=tempS.indexOf("TI$");
	    		int n2=tempS.indexOf(". Other");
	    		if(n1>0 && n2>n1) {
	    			int index=ar.nonaverageIndex();
	    			//String dsid=tempGroup.getDSID(index);//upper-case ENSDF format
	    			String dsid=ar.getAverage().getLabel(index);
	    			
	    			try {
		    			dsid=Translator.procGenCom(dsid,0,false);//upper "C" to lower "c" translation
	    			}catch(Exception e) {
	    				
	    			}

	    			boolean toChange=false;
	    			
	    			String dsid1=DSIDMatcher.shorten(dsid);
	    			if(adoptedTISourceDSIDs.contains(dsid) || adoptedTISourceDSIDs.contains(dsid1))
	    				toChange=true;
	   	    			
	    			if(toChange) {
		    			tempS=tempS.substring(0,n1+3)+"o"+tempS.substring(n2+3);
		    			tempS=EnsdfUtil.wrapENSDFLines(tempS);
	    			}

	    		}

	    	}
	    	
	    	commentS+=tempS+"\n";
	    	s=ar.adoptedValStr();
	    	ds=ar.adoptedUncStr();
	    	
	    	Comment c=makeComment(tempS);
	    	if(c!=null)
	    		newCommentsV.add(c);
	    }else if(CheckControl.isUseAverageSettings && ar!=null && ar.getAverage().goodPointIndexesV().size()==1){//for using a single comment value
	        int index=ar.getAverage().goodPointIndexesV().get(0);
	        DataPoint dp=ar.getAverage().dataPointsV().get(index);
	        s=dp.s();
	        ds=dp.ds();

    		dsidOfBestTI=dp.label();//for comment value, label is set using DSID
	    }else {
	    	gamBestTI=findBest(tempGroup,"TI");
	    	RecordGroup tempGroup1=tempGroup.lightCopy();
	    	Gamma tempGam=gamBestTI;
	    	while(tempGam!=null) {
	    	    
	    	    /*
	    	    //debug
	    		System.out.println("ConsistencyCheck 7116 tempGam="+tempGam.ES()+" RI="+tempGam.RIS()+"  "+tempGam.DRIS()+"  BR="+tempGam.RelBRS()+" "
	    	                         +tempGam.DRelBRS()+" RelBR="+tempGam.DRIS()+" "+tempGroup1.nRecords());
	    		System.out.println("     "+tempGroup1.recordsV().contains(tempGam));
	    		*/
	    	    
	    		boolean isSingleBranch=false;
	    		if(Str.isNumeric(tempGam.DRIS()) && tempGam.DRelBRS().isEmpty())
	    		    isSingleBranch=true;
	    		else {
	    		    try {
	                    int gamIndex=tempGroup1.recordsV().indexOf(tempGam);
                        String dsid=tempGroup1.dsidsV().get(gamIndex);
                        int levIndex=levelGroup.dsidsV().indexOf(dsid);
                        Level lev=(Level)levelGroup.recordsV().get(levIndex);
                        if(lev.nGammas()==1)
                            isSingleBranch=true;
	    		    }catch(Exception e) {
	    		        
	    		    }
	    		}

	                     
		    	if(isSingleBranch) {//remove single-branch transition
	                 
	    			tempGroup1.remove(tempGam);
	    			tempGam=findBest(tempGroup1,"TI");
	                
	    			continue;
		    	}
		    	
		    	gamBestTI=tempGam;
		    	
		    	break;
	    	}
	    	
	    	if(gamBestTI!=null) {
	    		
	    		int ig=tempGroup.recordsV().indexOf(gamBestTI);
	    		dsidOfBestTI=tempGroup.getDSID(ig);
	    		
	    		ENSDF ens=this.getENSDFByDSID(dsidOfBestTI);
	    		if(isRecordFromAdopted(gamBestTI,"E",ens)) {//"E" for gamma being commented from Adopted Gammas; DO NOT USE "TI"
	    			dsidOfBestTI="";
	    		}
	    		
	    		String line="";
    			line=gamBestTI.recordLine();
	    		
	    		try {
		    		s=line.substring(64,74).trim();
		    		ds=line.substring(74,76).trim();
	    		}catch(Exception e) {}
	    	}
	    }
	    
	    //if(gamBestEG!=null && gamBestEG.ES().equals("605.5"))
	    //	System.out.println(" ConsistencyCheck 7448: E="+gamBestEG.ES()+" RI="+s+" ds="+ds+" "+levelGroup.subgroups().size()+"  "+(EnsdfUtil.s2f(s)==100));
	    
	    if(levelGroup!=null && levelGroup.subgroups().size()==1 && EnsdfUtil.s2f(s)==100 && ds.isEmpty()) {
	    	dsidOfBestTI="";
	    }
	    
		//not print TI in Adopted dataset
		if(CheckControl.createCombinedDataset) {
			recordLine=EnsdfUtil.replaceTIOfGammaLine(recordLine, s, ds);
		}else {
			dsidOfBestTI="";
		}
    	
    	//if(recordLine.contains(" 605.7"))
    	//	System.out.println("###### "+dsidOfBestEG+"  "+dsidOfBestRI);
    	
    	String tempS1="",tempS2="";
    	String header1="",header2="",dsid1="",dsid2="";
    	
		String tempEGDSID=EnsdfUtil.makeLabelFromDSID(dsidOfBestEG,
				currentEnsdfGroup.dsidsVWithDuplicateShortID().contains(dsidOfBestEG));
		String tempRIDSID=EnsdfUtil.makeLabelFromDSID(dsidOfBestRI,
				currentEnsdfGroup.dsidsVWithDuplicateShortID().contains(dsidOfBestRI));
		String tempTIDSID=EnsdfUtil.makeLabelFromDSID(dsidOfBestTI,
				currentEnsdfGroup.dsidsVWithDuplicateShortID().contains(dsidOfBestTI));
		
    	if(tempGroup.nRecords()>1) {
    		
        	if(dsidOfBestEG.length()>0 && dsidOfBestRI.length()>0) {
        		if(dsidOfBestEG.equals(dsidOfBestRI)) {

        			if(adoptedEGSourceDSIDs==null || !adoptedEGSourceDSIDs.contains(tempEGDSID)) {
            			header1="E,RI";
            			dsid1=dsidOfBestEG;
            			tempS1=recordLine.substring(0,5)+" cG E,RI$from "+tempEGDSID;
        			}

        		}else {
        			
        			if(adoptedEGSourceDSIDs==null || !adoptedEGSourceDSIDs.contains(tempEGDSID)) { 
            			header1="E";
            			dsid1=dsidOfBestEG;
        				tempS1=recordLine.substring(0,5)+" cG E$from "+tempEGDSID;
        			}
        			
        			if(adoptedRISourceDSIDs==null || !adoptedRISourceDSIDs.contains(tempRIDSID)) {
            			header2="RI";
            			dsid2=dsidOfBestRI;
        				tempS2=recordLine.substring(0,5)+" cG RI$from "+tempRIDSID;
        			}
        		}
        	}else if(dsidOfBestEG.length()>0){
        		if(adoptedEGSourceDSIDs==null || !adoptedEGSourceDSIDs.contains(tempEGDSID)) {
            		header1="E";
            		dsid1=dsidOfBestEG;
        			tempS1=recordLine.substring(0,5)+" cG E$from "+tempEGDSID;
        		}
        	}else if(dsidOfBestRI.length()>0) {
        		if(adoptedRISourceDSIDs==null || !adoptedRISourceDSIDs.contains(tempRIDSID)) {
            		header1="RI";
            		dsid1=dsidOfBestRI;
        			tempS1=recordLine.substring(0,5)+" cG RI$from "+tempRIDSID;
        			
        			/*
    				if(Math.abs(tempGroup.getMeanEnergy()-315)<2) {
    					System.out.println("ConsistencyCheck 7621: header2="+header2+" tempDSID="+tempRIDSID+" dsidOfBestRI="+dsidOfBestRI);
    					System.out.println("    "+DSIDMatcher.shorten(tempRIDSID)+"       "+adoptedRISourceDSIDs.contains(DSIDMatcher.shorten(tempRIDSID)));
    					adoptedRISourceDSIDs.print();
    					
    				}
    				*/
        		}
        	}
        	
        	if(dsidOfBestTI.length()>0) {
        		if(adoptedTISourceDSIDs==null || !adoptedTISourceDSIDs.contains(tempTIDSID)) {
            		header1="TI";
            		dsid1=dsidOfBestTI;
        			tempS1=recordLine.substring(0,5)+" cG TI$from "+tempTIDSID;
        			
        			/*
    				if(Math.abs(tempGroup.getMeanEnergy()-315)<2) {
    					System.out.println("ConsistencyCheck 7621: header2="+header2+" tempDSID="+tempRIDSID+" dsidOfBestRI="+dsidOfBestRI);
    					System.out.println("    "+DSIDMatcher.shorten(tempRIDSID)+"       "+adoptedRISourceDSIDs.contains(DSIDMatcher.shorten(tempRIDSID)));
    					adoptedRISourceDSIDs.print();
    					
    				}
    				*/
        		}
        	}
    	}

    	
	    //for MULT and MR
	    ArrayList<Gamma> gV1=new ArrayList<Gamma>();//firm assignment, like M1, E2, M1+E2, M1(+E2)
	    ArrayList<Gamma> gV2=new ArrayList<Gamma>();//assignment, like D, Q, D(+Q)
	    ArrayList<Gamma> gV3=new ArrayList<Gamma>();//uncertain assignment like (M1),(E2)

	    Gamma gam=null;
	    for(int i=tempGroup.recordsV().size()-1;i>=0;i--) {
	    	gam=(Gamma)tempGroup.recordsV().get(i);
	    	
	    	String dsid=tempGroup.getDSID(i);
	    	
    		ENSDF ens=this.getENSDFByDSID(dsid);
    		
    		/*
    		if(gam.EF()>244 && gam.EF()<246) {
    	    	System.out.println(" ConsistencyCheck 7663: DSID="+dsid+" gam="+gam.ES()+" MUL="+gam.MS()+" "+isRecordFromAdopted(gam,"M",ens));
    		}
    		*/
    		
    		boolean hasMULArgument=false;
    		for(Comment c:gam.commentV()) {
    			String s1=c.body().trim();
    			if(c.headV().contains("M") || s1.contains("|g(|q)") || (s1.contains("|a")&&s.contains("exp")) ){
    				hasMULArgument=true;
    			}
    		}
    		
    		if(isRecordFromAdopted(gam,"M",ens) && !hasMULArgument) 
    			continue;
    		
    		
	    	String ms=gam.MS().trim();
	    	if(!ms.isEmpty() && !ms.startsWith("[") && !ms.endsWith("]")) {
	    		if(ms.startsWith("(") && ms.endsWith(")")) {
	    				gV3.add(gam);
	    		}else {
	    			ms=Str.removeTextInParentheses(ms).trim();
	    			if(Str.isOverlap(ms, "EM"))
	    				gV1.add(gam);
	    			else 
	    				gV2.add(gam);
	    		}
	    	}
	    	/*
    		if(gam.EF()>244 && gam.EF()<246) {
    	    	System.out.println(" ConsistencyCheck 7663: DSID="+dsid+" gam="+gam.ES()+" MUL="+gam.MS()+" "+isRecordFromAdopted(gam,"M",ens));
    	    	System.out.println("                       size1="+gV1.size()+" size2="+gV2.size()+" size3="+gV3.size());
    		}
	    	*/
	    }
	    
	    gam=null;
	    if(gV1.size()>0) 
	    	gam=gV1.get(0);    	
	    else if(gV2.size()>0)
	    	gam=gV2.get(0);
	    else if(gV3.size()>0)
	    	gam=gV3.get(0);

	    String ms="",mrs="",dmrs="";
	    String tempS3="",header3="";
	    if(gam!=null) {
	    	ms=gam.MS();
	    	mrs=gam.MRS();
	    	dmrs=gam.DMRS();
	    	
	    	//System.out.println(" ConsistencyCheck 8330: recordLine="+recordLine+" MUL="+ms);
	    	
	    	String dmrs0=dmrs.replace("+","").replace("-","").trim();
	    	if(ms.contains("+")){
	    	//if(ms.contains("+") && (mrs.isEmpty()||!Str.isNumeric(dmrs0))){
	    	    Vector<Gamma> gV4=new Vector<Gamma>();
	    	    Vector<Gamma> gV5=new Vector<Gamma>();
	    	    gV1.addAll(gV2);
	    	    gV1.addAll(gV3);
	    	    for(Gamma g:gV1) {
	    	        if(!g.MRS().isEmpty()) {
	    	            dmrs0=g.DMRS().replace("+","").replace("-","").trim();
	    	            if(Str.isNumeric(dmrs0))
	    	                gV4.add(g);
	    	            else
	    	                gV5.add(g);
	    	        }
	    	    }
	    	    
	    	    boolean done=false;
	    	    Gamma minGam=null;
	    	    if(gV4.size()>0) {
	    	        double min=10000;
	    	        for(Gamma g:gV4) {
	    	            SDS2XDX s2x=new SDS2XDX(g.MRS(),g.DMRS());
	    	            double rl=Math.abs(s2x.dxl()/s2x.x());
	    	            double ru=Math.abs(s2x.dxu()/s2x.x());
	    	            double r=Math.max(rl, ru);
	    	            
	    	            //System.out.println("ConsistencyCheck 8357: level="+g.ILS()+" gam="+g.ES()+" mrs="+g.MRS()+" dmrs="+g.DMRS()+" dxu="+s2x.dxu()+" dxl="+s2x.dxl());
	    	            
	    	            if(r<min) {
	    	                minGam=g;
	    	                min=r;
	    	            }
	    	            
	    	        }
	    	        
	    	        if(minGam!=null) {
	    	            done=true;
	    	            mrs=minGam.MRS();
	    	            dmrs=minGam.DMRS();
	    	            gam=minGam;
	    	        }
	    	        
	    	        if(gV4.size()>1) {
	    	        	RecordGroup tempGamGroup=new RecordGroup();
	    	        	boolean hasOppositeSign=false;
	    	        	String prevSign="";
	    	        	for(Gamma g:gV4) {
	    		    		int igam=tempGroup.recordsV().indexOf(g);
	    		    		String dsid=tempGroup.getDSID(igam);
	    		    		String xtag=tempGroup.getXTag(igam);
	    		    		tempGamGroup.addRecord(g, dsid, xtag);
	    		    		String tempMRS=g.MRS();
	    		    		if(tempMRS.startsWith("+") || tempMRS.startsWith("-")) {
	    		    			if(prevSign.length()>0 && !tempMRS.startsWith(prevSign)) {
	    		    				hasOppositeSign=true;
	    		    				break;
	    		    			}
	    		    			prevSign=tempMRS.charAt(0)+"";
	    		    		}
	    	        	}
	    	        	if(!hasOppositeSign) {
		    	    	    AverageReport ar1=getAverageReport(tempGamGroup,"MR");
		    	    	    if(ar1!=null) {
		    	    	    	String tempComS=ar1.getAdoptedComment();
		    	    	    	if(!tempComS.isEmpty()) {
		    	    		    	if(ar1.isNonAverage() && tempComS.contains(". Other") && adoptedMRSourceDSIDs.size()>0) {
		    	    		    		int n1=tempComS.indexOf("RI$");
		    	    		    		int n2=tempComS.indexOf(". Other");
		    	    		    		if(n1>0 && n2>n1) {
		    	    		    			int index=ar.nonaverageIndex();
		    	    		    			//String dsid=tempGroup.getDSID(index);//upper-case ENSDF format
		    	    		    			String dsid=ar1.getAverage().getLabel(index);
		    	    		    			
		    	    		    			try {
		    	    			    			dsid=Translator.procGenCom(dsid,0,false);//upper "C" to lower "c" translation
		    	    		    			}catch(Exception e) {
		    	    		    				
		    	    		    			}

		    	    		    			boolean toChange=false;
		    	    		    			
		    	    		    			String shortDSID=DSIDMatcher.shorten(dsid);
		    	    		    			if(adoptedMRSourceDSIDs.contains(dsid) || adoptedMRSourceDSIDs.contains(shortDSID))
		    	    		    				toChange=true;
		    	    		    			
		    	    		    			if(toChange) {
		    	    			    			tempComS=tempComS.substring(0,n1+3)+"o"+tempComS.substring(n2+3);
		    	    			    			tempComS=EnsdfUtil.wrapENSDFLines(tempComS);
		    	    		    			}

		    	    		    		}

		    	    		    	}
			    	    	    	mrs=ar1.adoptedValStr();
			    	    	    	dmrs=ar1.adoptedUncStr();
			    	    	    	tempS3=tempComS;
			    	    	    	done=true;
		    	    	    	}
		    	    	    }
	    	        	}

	    	        }
	    	    }
	    	    
	    	    if(!done && gV5.size()>0) {
	    	        gam=gV5.get(0);
	    	        mrs=gam.MRS();
	    	        dmrs=gam.DMRS();
	    	    }
	    	}
	    	
    		int ig=tempGroup.recordsV().indexOf(gam);
    		String tempDSID=tempGroup.getDSID(ig);
    		
    		tempS1="";
    		if(tempGroup.nRecords()>1 && tempS3.isEmpty()) {

    			header3="";
        		if(!mrs.isEmpty()) 
        			header3="MR";
        		else if(!ms.isEmpty())
        			header3="M";

        		if(header3.length()>0) {
        			boolean toMakeNewComment=true;
        			
        			/*
    				if(Math.abs(tempGroup.getMeanEnergy()-245)<2) {
    					System.out.println("ConsistencyCheck 7706: header1="+header1+" header2="+header2+" header3="+header3+" tempDSID="+tempDSID);
    					System.out.println("      dsid1="+dsid1+"  dsid2="+dsid2);
    				}
    				*/
        			if(tempDSID.equals(dsid1)) {
        				if(header1.length()>0) {
        					header1+=","+header3;
        					tempS1=recordLine.substring(0,5)+" cG "+header1+"$from "+EnsdfUtil.makeLabelFromDSID(tempDSID,
        							currentEnsdfGroup.dsidsVWithDuplicateShortID().contains(tempDSID));
        					toMakeNewComment=false;
        					header3="";
        				}
        			}else if(tempDSID.equals(dsid2)) {
        				//if(Math.abs(tempGroup.getMeanEnergy()-315)<2)
        				//System.out.println("ConsistencyCheck 7712: header2="+header2+" tempDSID="+tempDSID);
        				
        				if(header2.length()>0) {
        					header2+=","+header3;
        					tempS2=recordLine.substring(0,5)+" cG "+header2+"$from "+EnsdfUtil.makeLabelFromDSID(tempDSID,
        							currentEnsdfGroup.dsidsVWithDuplicateShortID().contains(tempDSID));
        					toMakeNewComment=false;
        					header3="";
        				}
        			}
        			
            		if(toMakeNewComment)
            			tempS3=recordLine.substring(0,5)+" cG "+header3+"$from "+EnsdfUtil.makeLabelFromDSID(tempDSID,
            					currentEnsdfGroup.dsidsVWithDuplicateShortID().contains(tempDSID));
        		}

    		}	    		    	
	    }

	    
		//if(Math.abs(tempGroup.getMeanEnergy()-245)<2) {
		//	System.out.println("ConsistencyCheck 7804: tempS1="+tempS1+" tempS2="+tempS2+"  tempS3="+tempS3+" line="+recordLine);
		//	System.out.println("           commentS="+commentS);
		//	System.out.println("      dsid1="+dsid1+"  dsid2="+dsid2);
		//}
    	
	    
    	if(tempS1.length()>0) {
	    	Comment c=makeComment(tempS1);
	    	if(c!=null && !checkIfCommentExist(c,adoptedGamma.commentV())) {
	    		newCommentsV.add(c);
		    	commentS+=tempS1+"\n";
	    	}

    	}
    	if(tempS2.length()>0) {
	    	Comment c=makeComment(tempS2);
	    	if(c!=null && !checkIfCommentExist(c,adoptedGamma.commentV())) {
	    		newCommentsV.add(c);
		    	commentS+=tempS2+"\n";
	    	}
    	}
    	if(tempS3.length()>0) {
	    	Comment c=makeComment(tempS3);

	    	if(c!=null && !checkIfCommentExist(c,adoptedGamma.commentV())) {
	    		newCommentsV.add(c);
	    		commentS+=tempS3+"\n";
	    	}
	    	
    	}

	    //if(recordLine.contains("92.0"))  System.out.println(" ConsistencyCheck 6996: recordLine="+recordLine+" MUL="+ms);
	    
    	recordLine=EnsdfUtil.replaceMULTOfGammaLine(recordLine,ms);
    	recordLine=EnsdfUtil.replaceMROfGammaLine(recordLine, mrs,dmrs);	

	    out.append(recordLine+"\n");
	    
    	//if(recordLine.contains("2186.8")) System.out.println(" ConsistencyCheck 7001: recordLine="+recordLine+" MUL="+ms+" out="+out.toString());
    	
	    //debug
	    //if(out.toString().contains("2186.8"))
	    //if(gam!=null && Math.abs(gam.EF()-1083)<2) 
	    //	System.out.println(" ConsistencyCheck 7050: "+gam.ES()+" MUL="+gam.MS()+" size1="+gV1.size()+" size2="+gV2.size()+" size3="+gV3.size()+" out="+out);
	    
		//write existing gamma comments from the existing Adopted dataset
	    String contCommentS="",otherCommentS="";	
		if(CheckControl.createCombinedDataset) {
			for(Record g:gammaGroup.recordsV()) {
				for(String line:g.contRecsLineV())
					contCommentS+=line+"\n";
				
				for(Comment c:g.commentV()){
					if(!checkIfCommentUpdated(c,newCommentsV)) {
						for(String line:c.lines()) {
							otherCommentS+=c.checkEndPeriod(line)+"\n";
						}
					}
				}
			}

		}else if(hasAdopted) {
			for(String line:adoptedGamma.contRecsLineV())
				contCommentS+=line+"\n";
			
			for(Comment c:adoptedGamma.commentV()){
				//if(adoptedGamma.ES().equals("861.9"))
				//	System.out.println("ConsistencyCheck 7929: "+checkIfCommentUpdated(c,newCommentsV)+"  c="+c.body());
				
				if(!checkIfCommentUpdated(c,newCommentsV)) {
					for(String line:c.lines()) {
						otherCommentS+=c.checkEndPeriod(line)+"\n";
					}
				}
			}
			
			for(Comment c:adoptedGamma.documentV()){
				for(String line:c.lines()) {
					otherCommentS+=c.checkEndPeriod(line)+"\n";
				}
			}
		}
		
		if(!contCommentS.isEmpty())
			out.append(contCommentS);
	    if(!commentS.isEmpty())
	    	out.append(commentS);
	    
	    //if(recordLine.contains("605.7"))
	    //	System.out.println(commentS+"1####"+out);
	    
	    if(!otherCommentS.isEmpty())
	    	out.append(otherCommentS);
	    
	    //if(recordLine.contains("605.7"))
	    //	System.out.println(commentS+"2####"+out);
	    //debug
	    //if(out.toString().contains("2186.8"))
	    //if(gam!=null && Math.abs(gam.EF()-1083)<2) 
	    //	System.out.println(" ConsistencyCheck 7050: size1="+gV1.size()+" size2="+gV2.size()+" size3="+gV3.size()+" out="+out.toString());

		return out.toString();
    }
    
    /*
     * find the record that has the best field of (s,ds) of recordName in tempGroup
     * NOTE: tempGroup should not include Adopted record
     */
    private <T extends Record> T findBest(RecordGroup tempGroup,String fieldName) {
    	
    	Vector<T> recordsV0=EnsdfUtil.findRecordsWithNonEmptyField(tempGroup.recordsV(), fieldName);  
    	
    	//System.out.println("0 recordsV0="+recordsV0.size()+" fieldName="+fieldName);

    	T rec0=null;
    	if(recordsV0.size()>0)
    		rec0=recordsV0.get(0);
    	else
    		return null;
    	
    	//System.out.println("1 "+tempGroup.recordsV().get(0).ES()+" tempGroup.recordsV()="+tempGroup.recordsV().size()+" recordsV="+recordsV0.size()+" fieldName="+fieldName);
    	
    	Vector<T> recordsV=EnsdfUtil.orderRecordsByFieldPrecision(recordsV0, fieldName);
    	
    	//System.out.println("2 recordsV="+recordsV.size());
    	if(fieldName.equals("T") && rec0 instanceof Level) {
        	for(int i=recordsV.size()-1;i>=0;i--) {
        		Level l=(Level)recordsV.get(i);
        		if(l.T12Unit().isEmpty()) {
        			recordsV.remove(i);
        			continue;
        		}
        	}
    	}
    	
    	//System.out.println("2 "+tempGroup.recordsV().get(0).ES()+" tempGroup.recordsV()="+tempGroup.recordsV().size()+" recordsV="+recordsV.size()+" fieldName="+fieldName);
    	
    	Vector<T> gV=EnsdfUtil.findRecordsByFieldWithUncertainty(recordsV, fieldName);

    	/*
    	if(tempGroup.getMeanEnergy()<=606 && tempGroup.getMeanEnergy()>=605) {
    		System.out.println("3 recordsV="+recordsV.size()+" gv="+gV.size());
    		for(int i=0;i<gV.size();i++)
    			System.out.println(" "+gV.get(i).ES());
    	}
    	*/
    	
    	int index=0;
    	String xtag="",flag="",qs="";
    	T rec=null;
    	int i=0;
    	
    	while(i<gV.size()) {
    		rec=gV.get(i);
    		qs=rec.q();
    		flag=rec.flag();
    		if(qs.isEmpty())
    			qs=" ";
    		if(flag.isEmpty())
    			flag=" ";
    		
    		if(!"*@&".contains(flag) && !"?S".contains(qs))
    			return rec;
    		
    		i++;
    	}
    	
    	i=0;
    	while(i<gV.size()) {
    		rec=gV.get(i);   		
    		qs=rec.q();
    		if(qs.isEmpty())
    			qs=" ";

    		if(!"?S".contains(qs))
    			return rec;
    		
    		i++;
    	}
    	
    	i=0;
    	while(i<gV.size()) {
    		rec=gV.get(i);
    		index=tempGroup.recordsV().indexOf(rec);
    		xtag=tempGroup.getXTag(index);//new XREF with old tag label except for those new in XREF, which is in form like "?1", "?2"
    		
    		//System.out.println(" xtag="+xtag);
    		
    		if(!xtag.contains("*") && !xtag.contains("?"))
    			return rec;
    		if(xtag.startsWith("?") && xtag.length()==2 && Str.isInteger(xtag.substring(1)))
    			return rec;
    		
    		i++;
    	}

    	
    	//System.out.println("3.5 recordsV="+recordsV.size()+" gv="+gV.size());
    	
    	i=0;
    	while(i<recordsV.size()) {
    		rec=recordsV.get(i);
    		index=tempGroup.recordsV().indexOf(rec);
    		xtag=tempGroup.getXTag(index);
    		if(!xtag.contains("*") && !xtag.contains("?"))
    			return rec;
    		if(xtag.startsWith("?") && xtag.length()==2 && Str.isInteger(xtag.substring(1)))
    			return rec;
    		
    		i++;
    	}
    
    	/*
    	//debug
    	if(rec!=null) {
    		System.out.println(fieldName+"   "+tempGroup.nRecords()+" rec="+rec.recordLine());
        	for(Record r:recordsV)
        		System.out.println("  "+r.recordLine());
    	}
        */
    	
    	//System.out.println("4 recordsV="+recordsV.size()+" gv="+gV.size()+" recordsV0="+recordsV0.size());
    	
    	if(!gV.isEmpty())
    		return gV.get(0);
    	
    	if(!recordsV.isEmpty())
    		return recordsV.get(0);
    	
    	if(!recordsV0.isEmpty())
    		return recordsV0.get(0);
    	
    	return null;
    }
    
    
    public String printAverageCommentLines(RecordGroup recordGroup, String recFieldName){ 
	    return printAverageCommentLines(getAverageReport(recordGroup,recFieldName));
    }
    
    /*
     * determine and print average comment in ENSDF-format based on
     * reduced chi-square
     */
    public String printAverageCommentLines(AverageReport ar){
	   	String out="";
	   	if(ar==null)
	   		return out;
	   	
	    return ar.getAdoptedComment();
    }
    
    /*
     * wrap of AverageReport(recordGroup,recFieldName,prefix) constructor
     * with some cleaning of the recordGroup before making the average
     */
    private AverageReport getAverageReport(RecordGroup recordGroup,String recFieldName){
    	if(recordGroup.nRecords()==0)
    		return null;
    	
    	Record r0=recordGroup.getRecord(0);
 		String recordLine=r0.recordLine();
 		String NUCID=recordLine.substring(0,5);
    	String prefix="";
    	
    	if(r0 instanceof Level)
    		prefix=Str.makeENSDFLinePrefix(NUCID, "cL");
    	else if(r0 instanceof Gamma)
    		prefix=Str.makeENSDFLinePrefix(NUCID, "cG");
    
 	    return getAverageReport(recordGroup,recFieldName,prefix);
    }
    
    
    private AverageReport getAverageReport(RecordGroup recordGroup,String recFieldName,String prefix){

        if(recordGroup==null || recordGroup.nRecords()==0)
            return null;
        
        recordGroup=recordGroup.lightCopy();    
        
        //System.out.println("1 size="+recordGroup.nRecords()+" name="+recFieldName+" E="+recordGroup.getRecord(0).ES());

        /*
        for(int i=0;i<recordGroup.nRecords();i++) {
        	if(recordGroup.getDSID(i).contains("IT DECAY"))
            System.out.println("ConsistencyCheck 8039: "+recordGroup.getDSID(i));
        }
        */
        
    	
        cleanRecordGroupForAverage(recordGroup,recFieldName);

        /*
        for(int i=0;i<recordGroup.nRecords();i++) {
        	if(recordGroup.getDSID(i).contains("IT DECAY"))
            System.out.println("ConsistencyCheck 8045: "+recordGroup.getDSID(i));
        }
        */
        
        //System.out.println("2 size="+recordGroup.nRecords()+" name="+recFieldName+" Control.isUseAverageSettings="+CheckControl.isUseAverageSettings);
    	/*
        if(recFieldName.equals("S") && recordGroup.nRecords()>0) {
            //if(recFieldName.equals("T") && recordGroup.nRecords()>0 && Math.abs(recordGroup.getRecord(0).EF()-2092)<10) {
                for(int i=0;i<recordGroup.nRecords();i++) {
                	Level r=(Level)recordGroup.recordsV().get(i);
                	System.out.println("Consistency 8940:  level="+r.ES()+" S="+r.sS()+" "+r.dsS()+CheckControl.isUseAverageSettings);
                	//System.out.println("Consistency7660:  level="+r.ES()+" T="+r.T12S()+" "+r.DT12S());
                }         
    	}
    	*/
    	
        AverageReport ar=null;
        if(CheckControl.isUseAverageSettings) {          
            //System.out.println((recordGroup==null)+" name="+recFieldName+" size="+recordGroup.nRecords());
            //if(recordGroup.nRecords()>0) System.out.println("1 E="+recordGroup.getRecord(0).ES()+" name="+recFieldName);
         
            //code to get all data points for recFieldName
            //If there is no comment value for a record, use the value in the record field
            Vector<DataPoint> dpsV=findCommentValues(recordGroup,recFieldName);
            
            /*
            if(recFieldName.equals("T") && recordGroup.nRecords()>0 && Math.abs(recordGroup.getRecord(0).EF()-942)<1) {
                for(DataPoint dp:dpsV)
                	System.out.println("1 dp: "+dp.s()+"  "+dp.ds());
            }
            */

            //if(recordGroup.nRecords()>0 && Math.abs(recordGroup.getRecord(0).EF()-942)<1) 
            //    System.out.println("2 E="+recordGroup.getRecord(0).ES()+" name="+recFieldName+" size="+dpsV.size()+"  record size="+recordGroup.nRecords());
            
            ar=new AverageReport(dpsV,recFieldName,prefix); 
            
        }else {   
        	/*
        	if(recFieldName.equals("S") && recordGroup.nRecords()>0) {
            //if(recFieldName.equals("T") && recordGroup.nRecords()>0 && Math.abs(recordGroup.getRecord(0).EF()-2092)<10) {
                for(int i=0;i<recordGroup.nRecords();i++) {
                	Level r=(Level)recordGroup.recordsV().get(i);
                	System.out.println("Consistency 8940:  level="+r.ES()+" S="+r.sS()+" "+r.dsS());
                	//System.out.println("Consistency7660:  level="+r.ES()+" T="+r.T12S()+" "+r.DT12S());
                }
            }
            */
            
            ar=new AverageReport(recordGroup,recFieldName,prefix); 
        }
                  
        return ar;
    }
    
    //assuming each ENSDF dataset has a different DSID.
    //If no comment value, use record value
    private Vector<DataPoint> findCommentValues(RecordGroup recordGroup, String recFieldName){
        Vector<DataPoint> dpsV=new Vector<DataPoint>();

        boolean isLevel=false,isGamma=false;
        String recName=recFieldName;
        
        try {
            Record rec=recordGroup.getRecord(0);
            if(rec instanceof Level) {
                isLevel=true;
                isGamma=false;
                if(recName.equals("E"))
                	recName="EL";
            }else if(rec instanceof Gamma) {
                isLevel=false;
                isGamma=true;
                if(recName.equals("E"))
                	recName="EG";
 
            }
        }catch(Exception e) {
            return dpsV;
        }

        recordGroup=recordGroup.lightCopy();
        
        cleanDecayDSID(recordGroup);
        
        for(int i=0;i<recordGroup.nRecords();i++) {
            Record rec=recordGroup.getRecord(i);
            String dsid=recordGroup.getDSID(i);
            
            ENSDF ens=findENSDFForRecord(rec,dsid);
            
            boolean isUseComValue=false,isOmitValue=false;
            boolean isDatasetSelected=true;
            boolean isReadComValue=false;
            
            Vector<CommentENSDF> censV=null;
            EnsdfAverageSetting setting=null;

            if(ens!=null) {
                censV=ensdfCommentDataMap.get(ens);
                setting=ensdfAverageSettingMap.get(ens);
 
                if(setting!=null) {
                    if(!setting.isSelected()) {
                        isDatasetSelected=false;
                    }else {
                    	if(setting.isOmitValue(recName)) {
                        	isOmitValue=true;
                        }else if(censV!=null && setting.isUseComValue(recName)) {
                            isReadComValue=true;
                        } 
                    }
                }
                
                /*
                if(dsid.contains("D,2NG") && recFieldName.equals("E")) {
                     System.out.println("ConsistencyCheck 8147: DSID="+dsid+"  isReadComValue="+isReadComValue+" isOmitValue="+isOmitValue);
                     for(String s:setting.isOmitValueMap().keySet())
                    	 System.out.println("    key="+s+" v="+setting.isOmitValue(s));
                }
                */
                //for(ENSDF ensdf:ensdfCommentDataMap.keySet())
                //    System.out.println("ens.dsid="+ens.DSId0()+" ensdf.dsid="+ensdf.DSId0()+" CommentENSDF size="+ensdfCommentDataMap.get(ensdf).size()+" contain="+ensdfCommentDataMap.containsKey(ens));

                
            }
            
            if(isReadComValue) {
                
                //for(ENSDF ensdf:ensdfCommentDataMap.keySet())
                //    System.out.println("ens.dsid="+ens.DSId0()+" ensdf.dsid="+ensdf.DSId0()+" CommentENSDF size="+ensdfCommentDataMap.get(ensdf).size()+" contain="+ensdfCommentDataMap.containsKey(ens));
                
                if(isLevel) {
                    rec=(Level)rec;
                    int il=ens.levelsV().indexOf(rec);
                    for(CommentENSDF cens:censV) {
                        CommentLevel CLev=cens.CLevels.get(il);
                        if(recFieldName.equals("E") && setting.isUseComValue("EL")) {
                            if(!CLev.ES.isEmpty()) {
                                isUseComValue=true;
                                SDS2XDX s2x=new SDS2XDX(CLev.ES,CLev.DES);
                                s2x.setErrorLimit(CheckControl.errorLimit);
                                DataPoint dp=new DataPoint(s2x.x(),s2x.dxu(),s2x.dxl(),cens.ref,cens.ref2);
                                
                                dp.setS(s2x.s(), s2x.ds());
                                dpsV.add(dp);
                            }

                        }else if(recFieldName.equals("T") && setting.isUseComValue("T")) {
                            if(!CLev.TS.isEmpty()) {
                                isUseComValue=true;
                                SDS2XDX s2x=new SDS2XDX(CLev.TS,CLev.DTS);
                                s2x.setErrorLimit(CheckControl.errorLimit);
                                DataPoint dp=new DataPoint(s2x.x(),s2x.dxu(),s2x.dxl(),cens.ref,cens.ref2);
                                dp.setUnit(CLev.TU);

                                dp.setS(s2x.s(), s2x.ds());
                                dpsV.add(dp);
                            }

                        } 
                    } 
                }else if(isGamma) {
                    rec=(Gamma)rec;
                    int ig=ens.gammasV().indexOf(rec);
                    for(CommentENSDF cens:censV) {
                        CommentGamma CGam=cens.allCGammas.get(ig);
                        if(recFieldName.equals("E") && setting.isUseComValue("EG")) {
                            if(!CGam.ES.isEmpty()) {
                                isUseComValue=true;
                                SDS2XDX s2x=new SDS2XDX(CGam.ES,CGam.DES);
                                s2x.setErrorLimit(CheckControl.errorLimit);
                                DataPoint dp=new DataPoint(s2x.x(),s2x.dxu(),s2x.dxl(),cens.ref,cens.ref2);
                                
                                dp.setS(s2x.s(), s2x.ds());
                                dpsV.add(dp);
                            }

                        }else if(recFieldName.equals("RI") && setting.isUseComValue("RI")) {
                            if(!CGam.RI.isEmpty()) {
                                isUseComValue=true;
                                SDS2XDX s2x=null;
                                if(CheckControl.convertRIForAdopted)
                                    s2x=new SDS2XDX(CGam.BR,CGam.DBR);
                                else
                                    s2x=new SDS2XDX(CGam.RI,CGam.DRI);
           
                                s2x.setErrorLimit(CheckControl.errorLimit);
                                DataPoint dp=new DataPoint(s2x.x(),s2x.dxu(),s2x.dxl(),cens.ref,cens.ref2);
                                
                                //System.out.println(" RI="+CGam.RI+" DRI="+CGam.DRI+" BR="+CGam.BR+" DBR="+CGam.DBR);
                                
                                dp.setS(s2x.s(), s2x.ds());
                                dpsV.add(dp);
                            }

                        } 
                    }
                }

            }
            
            if(isDatasetSelected && !isUseComValue && !isOmitValue) { 
                //System.out.println(" selected, use record: E="+rec.ES()+" recFieldName="+recFieldName+" dsid="+dsid);
                
                DataPoint dp=findRecordDataPoint(rec,recFieldName);
                if(dp!=null) {
                    dp.setLabel(dsid);
                    dpsV.add(dp); 
                }

            }
            
        }
        

        return dpsV;
    }
    
    private ENSDF findENSDFForRecord(Record rec,String dsid){
        try {
            Vector<ENSDF> ensV=new Vector<ENSDF>();
            String NUCID=rec.recordLine().substring(0,5);   
            String label=NUCID.trim()+"_"+dsid;    
            int i=0;
            while(true) {
                ENSDF ens=labelENSDFMap.get(label+"_"+i);
                if(ens==null)
                    break;
                
                ensV.add(ens);
                i++;
            }
            
            if(ensV.size()==0)
                return null;
            if(ensV.size()==1)
                return ensV.get(0);
            
            boolean isLevel=true,isGamma=false;
            if(rec instanceof Level) {
                isLevel=true;
                isGamma=false;
                rec=(Level)rec;
            }else if(rec instanceof Gamma) {
                isLevel=false;
                isGamma=true;
                rec=(Gamma)rec;
            }
                
            for(ENSDF ens:ensV) {
                if(isLevel && ens.levelsV().contains(rec))
                    return ens;
                if(isGamma && ens.gammasV().contains(rec))
                    return ens;
 
            }
        }catch(Exception e) {
            
        }

        return null;
    }
    
    private DataPoint findRecordDataPoint(Record rec,String recFieldName) {
        DataPoint dp=null;
        
        try{
            String name=recFieldName.toUpperCase();
            double x=-1,dxu=-1,dxl=-1;
            String s="",ds="",unit="";
            
            if(name.equals("E")){//energy (level/gamma/decay/delay)
                s=rec.ES();
                ds=rec.DES();
            }else if(name.equals("RI")){//only for gamma
                Gamma g=(Gamma)rec;
                //s=g.IS();
                //ds=rec.DIS();
                if(CheckControl.convertRIForAdopted){
                    XDX2SDS xs=new XDX2SDS(g.RelBRD(),g.DRelBRD(),CheckControl.errorLimit);
                    s=xs.s();
                    ds=xs.ds();
                }else{
                    s=g.RIS();
                    ds=g.DRIS();                    
                }
    
            }else if(name.equals("T")){//halflife
                Level l=(Level)rec;
                s=l.T12S();
                ds=l.DT12S();
                unit=Translator.halfLifeUnitsLowerCase(l.T12Unit());
            }else if(name.equals("S")){//relabelled C2S value
                Level l=(Level)rec;
                s=l.sS();
                ds=l.dsS();
                unit=Translator.halfLifeUnitsLowerCase(l.T12Unit());
            }else if(name.equals("TI")){//only for gamma
                Gamma g=(Gamma)rec;
                s=g.TIS();
                ds=g.DTIS();
    
                
                //if(rec.ES().contains("1893.")) System.out.println(" 2 name="+recordName+"  es="+rec.ES()+"  s="+s+"  ds="+ds);
            }

            //ds could be like "+12-10" for asymmetric uncertainty
            //exception if x or dx not numerical
            
            x=Float.parseFloat(s);
            //dx=Float.parseFloat(ds);
            
            //convert ENSDF-style uncertainty string to real value  
            dxu=(double) EnsdfUtil.s2x(s,ds).dxu();
            dxl=(double) EnsdfUtil.s2x(s,ds).dxl();
            
            
            //System.out.println("name="+name+" s="+s+" ds="+ds+" x=="+x+"  dxu="+dxu+" dxl="+dxl);
            
            if(dxu<=0 || dxl<=0)
                return null;

            
            dp=new DataPoint(x,dxu,dxl);
            dp.setS(s, ds);
            if(unit.length()>0)
                dp.setUnit(unit);
             
        }catch(Exception e){
        }    
        
        return dp;
    }
    
    
    /*
     * return an array of size=2 holding:
     * [0]: a new record line with the value of recFieldName replaced by the average value
     * [1]: the corresponding average comment for the average value
     */
    /*
    private String[] makeAverageLineAndComment(String recordLine,RecordGroup recordGroup,String recFieldName){
	   	String[] out=new String[2];
	   	out[0]=recordLine;
	   	out[1]="";
	   	
	   	AverageReport ar=getAverageReport(recordGroup,recFieldName);
	   	if(ar==null)
	   		return out;
	   	
	   	String commentS="";
	   	String valS="",uncS="",unitS="";
	    
	    if(commentS.isEmpty()){
	    	commentS=ar.getComment("nonaverage");
	    }
	    

	    out[0]=recordLine;
	    out[1]=commentS;
	    
    	return out;
    }
    */
    
     //remove (T12) in decay dataset dsid if it is the only dataset of its type
    private void cleanDecayDSID(RecordGroup recordGroup) {
    	for(int i=0;i<recordGroup.recordsV().size();i++) {
    		ENSDF ens=getENSDFByDSID(recordGroup.getDSID(i));
    		
    		//System.out.println("ConsistencyCheck 8372: dsid="+recordGroup.getDSID(i)+" ens==null "+(ens==null));
    		if(ens==null)
    			continue;
    		
        	String dsid=ens.DSId0();
        	if(!dsid.contains(" DECAY") || (!dsid.contains("(")&&!dsid.contains(":")) )
        		continue;
        		
    		String s="DECAY";
    		int n=dsid.indexOf(s);               		
    		if(n>0) {
    			s=dsid.substring(0,n+s.length());
    			
    			String NUCID=ens.nucleus().nameENSDF().trim();
    			
    			String s1=NUCID+"_"+s;//key in labelENSDFMap=NUCID+"_"+dsid+"_"+id, id for datasets with same dsid starting from 0
    			
    			
    			n=0;
        		for(String key:labelENSDFMap.keySet()) {                   			
        			if(key.contains(s1))
        				n++;
        		}  
        		
        		if(n==1) {        			
        			recordGroup.dsidsV().remove(i);
        			recordGroup.dsidsV().add(i,s);
        		}
    		}
        	
    	}
   	
    }    
    
    /*
     * clean a record group for average by removing records that noted from
     * Adopted dataset not the local datasets 
     */
    public String cleanRecordGroupForAverage(RecordGroup recGroup,String recFieldName){
    	String msg="";
    	
    	try{
    
    	    /*
    		if(recFieldName.equals("T") && Math.abs(recGroup.getRecord(0).EF()-50)<2) {
  
                for(int j=0;j<recGroup.nRecords();j++) {
                	System.out.println("In CleanRecord  in recordgroup j="+j+" E="+recGroup.getRecord(j).ES());
                }
    		}
            */
    	    //System.out.println("$$$$$recFieldName="+recFieldName+" null group "+(recGroup==null)+" "+recGroup.nRecords());

    	    String recType=recGroup.getRecord(0).recordLine().substring(7,8).trim();
    	    
    		int i=recGroup.nRecords()-1;
    		while(i>=0){
    			ENSDF ens=getENSDFByDSID(recGroup.getDSID(i));
    			
    			/*
    		    //debug
    		    if(Math.abs(recGroup.getRecord(i).EF()-85)<2 && recFieldName.equals("T")){
    		    	System.out.println("1 size="+recGroup.nRecords()+" i="+i+" recFieldName="+recFieldName+" E="+recGroup.getRecord(i).ES()+"  isRecordFromAdopted="+
    		    isRecordFromAdopted(recGroup.getRecord(i),recFieldName,ens)+" DSID="+recGroup.getDSID(i));
    		    }
    		    */
    		    
    			boolean isUseComValue=false;
    			if(CheckControl.isUseAverageSettings) {
    			    try {  	      
    			        //debug
    			        //System.out.println("  setting size="+ensdfAverageSettingMap.size()+" recFieldName="+recFieldName);
    			        
    			        String name=recFieldName;
    			        if(name.equals("E"))
    			            name=name+recType;
    			        
    			        isUseComValue=ensdfAverageSettingMap.get(ens).isUseComValue(name); 

    			    }catch(Exception e) {    			        
    			    }
    			}
    			
    			if(!isUseComValue && isRecordFromAdopted(recGroup.getRecord(i),recFieldName,ens)) {
    				msg+=recFieldName+" from "+ens.DSId()+" is taken from Adopted dataset\n";
    				recGroup.remove(i);
    			}
    			
    			i--;
    			
    		}

    		cleanDecayDSID(recGroup);		

    	}catch(Exception e){
    		e.printStackTrace();
    	}
    	
    	return msg;
    }
    
    
    public ENSDF getENSDFByDSID(String dsid){
    	for(int i=0;i<this.ensdfGroupsV.size();i++){
    		EnsdfGroup group=ensdfGroupsV.get(i);
    		ENSDF ens=group.getENSDFByDSID0(dsid);
    		if(ens!=null)
    			return ens;
    	}
    	
    	return null;
    }
    
    public EnsdfGroup getEnsdfGroupByNUCID(String NUCID) {
    	for(int i=0;i<ensdfGroupsV.size();i++) {
    		EnsdfGroup group=ensdfGroupsV.get(i);
    		String thisNUCID=group.NUCID().trim().toUpperCase();
    		//System.out.println(NUCID+"$$$$"+thisNUCID+" @@@ "+thisNUCID.equalsIgnoreCase(NUCID));
    		
    		if(thisNUCID.equalsIgnoreCase(NUCID.trim()))
    			return group;
    	}
    	
    	return null;
    }
    
    //for output file containing tabulated level information group-by-group in order of level energy
    //inside group, it is in order of dataset in the file
    public String printLevelsOnly(EnsdfGroup ensdfGroup){
    	String out="";
    	Vector<RecordGroup> levelGroupsV=ensdfGroup.levelGroupsV();
    	
        //            E    DE      JPI     T+TU+DT L      DSID    XTag
    	String  FMT=" %-10s %-2s   %-18s   %-16s   %-9s   %-40s   %s";
    	String FMT0=" %-13s   %-18s   %-16s   %-9s   %-40s   %s";
    	String title=String.format(FMT,"E(Level)","","JPI","T1/2","L-value","DSID","XREF");
    	String underline=String.format(FMT0, Str.repeat("-", 13),Str.repeat("-",18),Str.repeat("-",16),Str.repeat("-",9),
    			Str.repeat("-",40),Str.repeat("-",3));
    	title=title+"\n"+underline;
    	
    	
    	for(int i=0;i<levelGroupsV.size();i++){
    		RecordGroup levelGroup=levelGroupsV.get(i);
    		for(int j=0;j<levelGroup.recordsV().size();j++){
    			Level l=levelGroup.getRecord(j);
    			String dsid=levelGroup.getDSID(j);
    			String xtag=levelGroup.getXTag(j);

    			out+=String.format(FMT,l.ES().trim(),l.DES(),l.JPiS(),l.halflife(),l.lS(),dsid,xtag)+"\n";   				    						
    		}
    		out+="\n";
    	}
    	
    	if(out.length()>0){
    		out=title+"\n"+out;
   			out="\n\n"+ensdfGroup.NUCID()+"\n"+out;
   			
   			out+="\n\n"+printLevelGammaStatistics(ensdfGroup);

    	}
    	return out;
    }
    
    //for output file containing tabulated gamma information group-by-group in order of gamma energy (group mean)
    //inside group (same gamma belonging to same level), it is in order of dataset in the file
    public String printGammasByGamma(EnsdfGroup ensdfGroup){
    	String out="";
    	Vector<RecordGroup> gammaGroupsV=ensdfGroup.getGammaGroupsByGamma();
    	
    	
        //            E    DE      RI   DRI     MULT    MR   DMR    CC   DCC    Ei   Ji      Ef   Jf       DSID
    	String  FMT=" %-10s %-2s   %-10s %-2s   %-10s   %-8s %-6s   %-7s %-2s   %-10s %-10s  %-10s %-10s   %s";
    	String FMT0=" %-13s   %-13s   %-10s   %-15s   %-10s   %-10s %-10s  %-10s %-10s   %s";
    	String title=String.format(FMT,"EG","","RI","","MULT","MR","","CC","","EI","JI","EF","JF","DSID");
    	String underline=String.format(FMT0, Str.repeat("-", 13),Str.repeat("-",13),Str.repeat("-",10),Str.repeat("-",15),
    			Str.repeat("-",10),Str.repeat("-",10),Str.repeat("-",10),Str.repeat("-",10),Str.repeat("-",10),Str.repeat("-",30));
    	title=title+"\n"+underline;
    			
    	for(int i=0;i<gammaGroupsV.size();i++){
    		RecordGroup gammaGroup=gammaGroupsV.get(i);
    		for(int j=0;j<gammaGroup.recordsV().size();j++){
    			Gamma g=gammaGroup.getRecord(j);
    			String dsid=gammaGroup.getDSID(j);
    			ENSDF ens=ensdfGroup.getENSDFByDSID0(dsid);
    			Level parent=null,daughter=null;
    			try{
    				parent=ens.levelAt(g.ILI());
    				daughter=ens.levelAt(g.FLI());
    				
    				String JI=parent.JPiS().trim();
    				String JF=daughter.JPiS().trim();
    				if(JI.length()<=10 && JF.length()<=10)
    					out+=String.format(FMT,g.ES().trim(),g.DES(),g.RIS(),g.DRIS(),g.MS(),g.MRS(),g.DMRS(),g.CCS(),g.DCCS(),
    						parent.ES().trim(),JI,daughter.ES().trim(),JF,dsid)+"\n";   
    				else{
    					out+=String.format(FMT,g.ES().trim(),g.DES(),g.RIS(),g.DRIS(),g.MS(),g.MRS(),g.DMRS(),g.CCS(),g.DCCS(),
        					parent.ES().trim(),"",daughter.ES().trim(),"",dsid)+"\n";
    					out+=String.format(FMT,"","","","","","","","","",
    						"",JI,"",JF,"")+"\n";
    				}
    			}catch(Exception e){  
    				out+=String.format(FMT,g.ES().trim(),g.DES(),g.RIS(),g.DRIS(),g.MS(),g.MRS(),g.DMRS(),g.CCS(),g.DCCS(),
    						"UNPLACED","","","",dsid)+"\n";
    			}
    						
    		}
    		out+="\n";
    	}

    	if(out.length()>0){
    		out=title+"\n"+out;
   			out="\n\n"+ensdfGroup.NUCID()+"\n"+out;
   			
   			out+="\n\n"+printLevelGammaStatistics(ensdfGroup);
    	}
    	
    	return out;
    }
    
    //for output file containing tabulated gamma information group-by-group in order of parent level energy
    //inside group (same gamma belonging to same level), it is in order of dataset in the file
    public String printGammasByLevel(EnsdfGroup ensdfGroup){
    	String out="";
    	Vector<RecordGroup> gammaGroupsV=ensdfGroup.getGammaGroupsByLevel();
    	
        //            Ei    Ji      Eg    DEg    Ef    Jf      RI    DRI    MULT    MR   DMR    CC   DCC    DSID
    	String  FMT=" %-10s %-10s   %-10s %-2s   %-10s %-10s   %-10s %-2s   %-10s   %-8s %-6s   %-7s %-2s   %s";
    	String FMT0=" %-10s %-10s   %-13s   %-10s %-10s   %-13s   %-10s   %-15s   %-10s   %s";
    	String title=String.format(FMT,"EI","JI","EG","","EF","JF","RI","","MULT","MR","","CC","","DSID");
    	String underline=String.format(FMT0, Str.repeat("-", 10),Str.repeat("-",10),Str.repeat("-",13),Str.repeat("-",10),
    			Str.repeat("-",10),Str.repeat("-",13),Str.repeat("-",10),Str.repeat("-",15),Str.repeat("-",10),Str.repeat("-",30));
    	title=title+"\n"+underline;
    	
    	for(int i=0;i<gammaGroupsV.size();i++){
    		RecordGroup gammaGroup=gammaGroupsV.get(i);
    		for(int j=0;j<gammaGroup.recordsV().size();j++){
    			Gamma g=gammaGroup.getRecord(j);
    			String dsid=gammaGroup.getDSID(j);
    			ENSDF ens=ensdfGroup.getENSDFByDSID0(dsid);
    			Level parent=null,daughter=null;
    			try{
    				parent=ens.levelAt(g.ILI());
    				daughter=ens.levelAt(g.FLI());
    				
    				String JI=parent.JPiS().trim();
    				String JF=daughter.JPiS().trim();
    				
    				//if(g.EF()<11){
    					//System.out.println(" EG="+g.ES()+" g.ILI="+g.ILI()+"  "+g.ILS()+" g.FLI="+g.FLI()+"  "+g.FLS()+" "+dsid+"  "+ens.DSId0());
    					//for(int k=0;k<ens.nLevels();k++)
    					//	System.out.println("    "+ens.levelAt(k).ES());
    				//}
    				
    				String IS="",DIS="";
    				if(CheckControl.convertRIForAdopted){
    					if(g.DRelBRD()>0) {
    						XDX2SDS xs=new XDX2SDS(g.RelBRD(),g.DRelBRD(),CheckControl.errorLimit);
    						IS=xs.s();
    						DIS=xs.ds();
    					}else {
        					IS=g.RelBRS();
        					DIS=g.DRelBRS();
    					}
    				}else{
    					IS=g.RIS();
    					DIS=g.DRIS();
    				}
    					
    				if(JI.length()<=10 && JF.length()<=10)
    					out+=String.format(FMT,parent.ES().trim(),JI,g.ES().trim(),g.DES(),daughter.ES().trim(),JF,
    						IS,DIS,g.MS(),g.MRS(),g.DMRS(),g.CCS(),g.DCCS(),dsid)+"\n";   
    				else{
    					out+=String.format(FMT,parent.ES().trim(),"",g.ES().trim(),g.DES(),daughter.ES().trim(),"",
        						IS,DIS,g.MS(),g.MRS(),g.DMRS(),g.CCS(),g.DCCS(),dsid)+"\n"; 
    					out+=String.format(FMT,"",JI,"","","",JF,
    						"","","","","","","","")+"\n";
    				}				
    			}catch(Exception e){  
					out+=String.format(FMT,"UNPLACED","",g.ES().trim(),g.DES(),"","",
    						g.RIS(),g.DRIS(),g.MS(),g.MRS(),g.DMRS(),g.CCS(),g.DCCS(),dsid)+"\n"; 
    			}
    						
    		}
    		out+="\n";
    	}
    	
    	if(out.length()>0){
    		out=title+"\n"+out;
   			out="\n\n"+ensdfGroup.NUCID()+"\n"+out;
   			
   			out+="\n\n"+printLevelGammaStatistics(ensdfGroup);
    	}
    	
    	return out;
    }
    
    public String printLevelGammaStatistics(EnsdfGroup ensdfGroup){
    	String out="";
    	String formatStr="   %30s     %-15s  %-15s  %s\n";
    	
    	ENSDF ens;
    	String dsid="";
    	String line="";
    	int ngamma=0;
    	int nlevel=0;
    	int ngammaTotal=0;
    	int nlevelTotal=0;
    	String lowestELS="",highestELS="";
    	float lowestEL=100000,highestEL=0;
    	
    	out+="Statistics of levels and gammas from all datasets of "+ensdfGroup.NUCID()+":\n";
    	out+=String.format(formatStr,"DSID","#of levels","#of gammas","level range");
    	
    	for(int i=0;i<ensdfGroup.nENSDF();i++){
    		ens=ensdfGroup.ensdfV().get(i);
    		dsid=ens.DSId0();
    		ngamma=ens.nTotGam();
    		nlevel=ens.nLevels();
    		
    		ngammaTotal+=ngamma;
    		nlevelTotal+=nlevel;
    		
    		line="";
    		
    		if(nlevel>0){
        		String lowELS=ens.levelsV().get(0).ES();
        		String highELS=ens.levelsV().lastElement().ES();
        		if(ens.nLevels()==1)
        			line=lowELS;
        		else
        			line=String.format("%-10s to %10s",lowELS,highELS);
        		
        		if(Str.isNumeric(lowELS)) {
        			float lowEL=Float.parseFloat(lowELS);
        			if(lowEL<lowestEL) {
        				lowestEL=lowEL;
        				lowestELS=lowELS;
        			}
        		}
        		
        		if(Str.isNumeric(highELS)) {
        			float highEL=Float.parseFloat(highELS);
        			if(highEL>highestEL) {
        				highestEL=highEL;
        				highestELS=highELS;
        			}
        		}
    		}
    		
    		line=String.format(formatStr,dsid,"   "+nlevel,"   "+ngamma,line);
    		out+=line;
    	}
    	
    	if(nlevelTotal>0) {
    		out+="\n";
    		if(!highestELS.isEmpty())
    			line=String.format("%-10s to %10s",lowestELS,highestELS);
    		else
    			line=lowestELS;
    		
    		out+=String.format(formatStr,"IN TOTAL","   "+nlevelTotal,"   "+ngammaTotal,line);
    	}

    	return out;
    }
    
    
    public String printAllStatistics(){
    	String out="";
    	String formatStr="";
    	
    	
    	ENSDF ens;
    	String dsid="";

    	int nLevelsAdopted=0,nGammasAdopted=0,nLevelsAll=0,nGammasAll=0,nLines=0,nDatasets=0;//numbers for each nuclide
    	int nLevelsAdoptedTotal=0,nGammasAdoptedTotal=0,nLinesTotal=0,nDatasetsTotal=0;//total numbers
    	int nLevelsAllTotal=0,nGammasAllTotal=0;
        int nNuclides=ensdfGroupsV.size();
    	
        out+="%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n";
        
    	out+="Statistics of all datasets from input ENSDF files:\n";
    	
    	formatStr="   %20s     %-11s  %-11s  %-11s  %-11s    %-15s  %s\n";
    	out+=String.format(formatStr,"Nuclide","#of Adopted","#of Adopted","  #of All  ","  #of All  ","#of Lines","#of Datasets");
    	out+=String.format(formatStr,"       ","   Levels  ","   Gammas  "," L Records "," G Records ","         ","(non-Adopted)");
    	
    	for(int i=0;i<ensdfGroupsV.size();i++){
    		EnsdfGroup ensdfGroup=ensdfGroupsV.get(i);
    		String NUCID=ensdfGroup.NUCID();
    		
    		nLevelsAdopted=0;
    		nGammasAdopted=0;
    		nLines=0;
    		nDatasets=0;
    		nLevelsAll=0;
    		nGammasAll=0;
    				
    		
    		for(int j=0;j<ensdfGroup.nENSDF();j++){
        		ens=ensdfGroup.ensdfV().get(j);
        		dsid=ens.DSId0();
        		
        		nLines+=ens.nLines();
        		
        		if(dsid.indexOf("COMMENTS")>=0 || dsid.indexOf("REFERENCES")>=0){
        			continue;
        		}else if(dsid.indexOf("ADOPTED")>=0){
        			nLevelsAdopted+=ens.nLevels();
        			nGammasAdopted+=ens.nTotGam();
        		}else{
        			nDatasets++;
        		}
        		
        		nLevelsAll+=ens.nLevels();
        		nGammasAll+=ens.nTotGam();
    		}
    		
    		nLevelsAdoptedTotal+=nLevelsAdopted;
    		nGammasAdoptedTotal+=nGammasAdopted;
    		
    		nLevelsAllTotal+=nLevelsAll;
    		nGammasAllTotal+=nGammasAll;

    		nLinesTotal +=nLines;
    		nDatasetsTotal+=nDatasets;
    		
    		out+=String.format(formatStr, NUCID,"   "+nLevelsAdopted,"   "+nGammasAdopted,"   "+nLevelsAll,"   "+nGammasAll,"   "+nLines,"   "+nDatasets);

    	}
    	
    	out+="\n";
		out+=String.format(formatStr, "IN TOTAL","   "+nLevelsAdoptedTotal,"   "+nGammasAdoptedTotal,"   "+nLevelsAllTotal,"   "+nGammasAllTotal,"   "+nLinesTotal,"   "+nDatasetsTotal);
		out+="\n";
		out+="Number of nuclides     : "+nNuclides+"\n";
        out+="Number of blocks       : "+chain.nBlocks()+"\n";
        out+="Number of references   : "+chain.ref().getKeyNumbers().length+"\n";
        out+="Number of lines in files: "+chain.nLines()+"\n";
        out+="(from all blocks including abstract, comment, reference)\n";
        
    	return out;
    }
    
    
    public void printTest0(EnsdfGroup g){
    	Vector<Level> levelsV=g.levelGroupsV().get(0).recordsV();
    	Vector<String> xtagsV=g.levelGroupsV().get(0).xtagsV();
    	Vector<String> dsidsV=g.levelGroupsV().get(0).dsidsV();
    	
    	int nLevels=levelsV.size();
    	Object[][] A=new Object[nLevels][3];
    	for(int i=0;i<nLevels;i++){
    		A[i][0]=levelsV.get(i);
    		A[i][1]=dsidsV.get(i);
    		A[i][2]=xtagsV.get(i);
    	}
    	
    	for(int i=0;i<nLevels;i++){
    		System.out.println("    "+A[i][2]+" "+((Level)A[i][0]).EF()+"  "+A[i][1]);
    	}
    }
    
    public void printTest(EnsdfGroup g){
		System.out.println("\n Group of levels and gammas for nuclide="+g.NUCID());
		for(int j=0;j<g.levelGroupsV().size();j++){
    		RecordGroup levelGroup=g.levelGroupsV().get(j);
    		System.out.println("\n\n ***level group "+j);
    		for(int k=0;k<levelGroup.nRecords();k++){
    			Level lev=(Level)levelGroup.recordsV().get(k);
    			System.out.println("  level:"+String.format("%-10s%-2s %-30s%-5s%s", lev.ES(),lev.DES(),lev.JPiS(),levelGroup.xtagsV().get(k),levelGroup.dsidsV().get(k)));
    		}
    		
    		if(!levelGroup.hasSubGroups())
    			continue;
    		
    		Vector<RecordGroup> gammaGroupsV=levelGroup.subgroups();
    		for(int k=0;k<gammaGroupsV.size();k++){
        		RecordGroup gammaGroup=gammaGroupsV.get(k);
        		System.out.println("\n ***gamma group "+k);
    			for(int m=0;m<gammaGroup.nRecords();m++){
        			Gamma gam=(Gamma)gammaGroup.recordsV().get(m);
        			System.out.println("  gamma:"+String.format("%-10s%-2s %-5s%s", gam.ES(),gam.DES(),gammaGroup.xtagsV().get(m),gammaGroup.dsidsV().get(m)));
    			}            				
    		}
		}
    }
    
	///////////////////////////////////////
	//Other functions
	///////////////////////////////////////

    
    private String printToMessage(String text){
    	return printToMessage(-1,text,"");
    }
    
    /*
     * generate a formatted message to be written in the report file
     * markPos   : # of column to place the mark indicating what the message for
     *             if markPos<0, just print the text at column 0
     * text      : message body
     * msgType   : message type, eg, "E" for "error", "W" for "warning","I" for information
     */
    private String printToMessage(int markPos,String text,String msgType){
    	String out=makeMessageLines(markPos,text,msgType);
        message+=out+"\n";
    	return out;
    }
    
    private String makeMessageLines(int markPos,String text,String msgType){
    	String out="";
    	int pos=markPos;
        if(pos>=80)
        	pos=79;
        
        if(text.trim().length()==0)
        	return out;
        
        String[] lines=text.split("\\r?\\n");
        
        String type=msgType.trim().toUpperCase();
        char c=' ';
        if(type.length()>0)
        	c=type.charAt(0);
        
        if(c=='E')
        	type="<E>";
        else if(c=='W')
        	type="<W>";
        else
        	type="<I>";
        
        //markPos<0, used to print the text at the #column=0, eg, ENSDF line
        if(markPos<0)
        	out=text;
        else{
        	int count=0;
        	for(int i=0;i<lines.length;i++){
        		String line=lines[i];
        		if(line.length()==0)
        			continue;
        		
        		if(count==0)
                	out+=Str.repeat(" ", pos)+"*"+Str.repeat(" ", 81-pos)+type+" "+line;
        		else
        			out+="\n"+Str.repeat(" ", 82+type.length())+" "+line;
        		
        		count++;
        			
        	}

        }
        
        Str.removeEOL(out);        
    	return out;
    }
    
    private void writeErrorMessage(PrintWriter out){  	
    	if(message.trim().length()>0) {
    		out.write(message);
    		out.write("\n\n");
    		out.write("End of report\n");
    	}else
    		out.write("No problems are found!");
    	
    	message="";
    }
    
    private void writeXREFWarningMessage(PrintWriter out){  	
    	if(XREFWarningMsg.trim().length()>0)
    		out.write(XREFWarningMsg);
    	else
    		out.write("See error report file.");
    	
    	XREFWarningMsg="";
    }

    //write report file for error and warning messages   
    private void writeErrorReport(PrintWriter out){
        writeHeader(out,"Error and Warnings");

        //at the end, write all messages from checking 
        writeErrorMessage(out);
    }
    
    private void writeXREFWarningReport(PrintWriter out){
        writeHeader(out,"XREF Warnings only");

        writeXREFWarningMessage(out);
    }
    
    //write summary file for levels from different datasets grouped by level energies
    private void writeLevelSummary(PrintWriter out){
    	writeHeader(out,"levels");
    	for(int i=0;i<ensdfGroupsV.size();i++)
    		out.write(printLevelsOnly(ensdfGroupsV.get(i)));
    	
		if(ensdfGroupsV.size()>0)
			out.write("\n\n\n"+printAllStatistics());
    }
    
    //write summary file for gammas from different datasets grouped by gamma energies
    //in each group of gammas from different datasets corresponding to the same gamma,
    //gammas are ordered by the order of datasets in ENSDF file 
    private void writeGammaSummaryByGamma(PrintWriter out){
    	writeHeader(out,"gammas ordered by gamma energies");
    	for(int i=0;i<ensdfGroupsV.size();i++)
    		out.write(printGammasByGamma(ensdfGroupsV.get(i)));
    	
		if(ensdfGroupsV.size()>0)
			out.write("\n\n\n"+printAllStatistics());
    }
    
    //write summary file for gammas from different datasets grouped by level energies
    //in each group of gammas from different datasets corresponding to the same gamma,
    //gammas are ordered by the order of datasets in ENSDF file 
    private void writeGammaSummaryByLevel(PrintWriter out){
    	writeHeader(out,"gammas ordered by level energies");
    	for(int i=0;i<ensdfGroupsV.size();i++)
    		out.write(printGammasByLevel(ensdfGroupsV.get(i)));
    	
		if(ensdfGroupsV.size()>0)
			out.write("\n\n\n"+printAllStatistics());
    }
    
    private void writeFeedingGammas(PrintWriter out){
    	writeHeader(out,"feeding gammas of levels");
    	for(int i=0;i<ensdfGroupsV.size();i++){
    		EnsdfGroup ensdfGroup=ensdfGroupsV.get(i);
    		for(int j=0;j<ensdfGroup.levelGroupsV().size();j++){
    			RecordGroup recordGroup=ensdfGroup.levelGroupsV().get(j);
    			out.write(printFeedingGammas(recordGroup,ensdfGroup));
    		}
    	}

    }
    
    //write output file of all original lines from different dataset grouped together based on
    //level and gamma eneriges
    private void writeGroupedLines(PrintWriter out){
    	writeHeader(out,"original lines grouped by level and gamma energies");
    	
    	String note="Note: 1. possible JPI presented at the end of each record line of each individual dataset is deduced only based on\n"
    			  + "         available data in the corresponding dataset, including decay data (logft or HF), L-transfer, and decaying\n"
    			  + "         and feeding gammas of each level, and so on.\n"
    			  + "      2. possible JPI presented at the end of each adopted record line is deduced by combining data from all\n"
    			  + "         individual datasets (data from the record lines only and might be incomplete since arguments only presented\n"
    			  + "         in comments haven't been considered, like shell-model calculations, analyzing power, atomic method for g.s.\n"
    			  + "         spin, and other qualitative arguments).\n\n"
    			  + "*** All possible JPI values are for reference purpose only and should not be taken as the final adopted assignments ***\n";
    	out.write("\n");
    	out.write(note);
    	
    	for(int i=0;i<ensdfGroupsV.size();i++)
    		out.write(printGroupedLines(ensdfGroupsV.get(i),true));
    	
		if(ensdfGroupsV.size()>0)
			out.write("\n\n\n"+printAllStatistics());
    }
    
    //write output file for averaging results of records if values of record from different
    //datasets are available and their average can be performed.
    private void writeAverageReport(PrintWriter out){
    	writeHeader(out,"averaging results for gamma energies and intensities");
    	for(int i=0;i<ensdfGroupsV.size();i++)
    		out.write(printAverageReport(ensdfGroupsV.get(i)));
    }
    
    private void writeAdoptedWithNewXREFOnly(PrintWriter out){
    	writeHeader(out,"tentative Adopted levels with tentative new XREF flags");
    	for(int i=0;i<ensdfGroupsV.size();i++)
    		out.write(printAdoptedWithNewXREFOnly(ensdfGroupsV.get(i))+"\n\n");
    }
    
    private void writeAdoptedWithAllData(PrintWriter out){
    	writeHeader(out,"new dataset of Adopted Levels, Gammas with all data");
    	for(int i=0;i<ensdfGroupsV.size();i++) {
    		out.write(printAdoptedWithAllData(ensdfGroupsV.get(i))+"\n\n");
    	}
    }
    
    public void writeFile(String filename,String fileType){
    	PrintWriter out=null;
    	try{
    		boolean good=true;
    		out=new PrintWriter(new File(filename));
    		
        	if(fileType.equals("ERR"))
        		writeErrorReport(out);
        	else if(fileType.equals("WRN"))
        		writeXREFWarningReport(out);
        	else if(fileType.equals("LEV"))
        		writeLevelSummary(out);
        	else if(fileType.equals("GAM"))
        		writeGammaSummaryByGamma(out);
        	else if(fileType.equals("GLE"))
        		writeGammaSummaryByLevel(out);
        	else if(fileType.equals("MRG"))
        		writeGroupedLines(out);
        	else if(fileType.equals("AVG"))
        		writeAverageReport(out);
        	else if(fileType.equals("XRF"))
        		writeAdoptedWithNewXREFOnly(out);
           	else if(fileType.equals("ADP")){
        		writeAdoptedWithAllData(out);
           	}else if(fileType.equals("FED"))
           		writeFeedingGammas(out);
           	else
        		good=false;
        	
        	if(good)
        		System.out.println("...Done writing "+filename);
        	
    	}catch(FileNotFoundException e){
    		e.printStackTrace();
    	}catch(Exception e){
    		e.printStackTrace();
    	}
    	
    	if(out!=null)
    		out.close();
    	
    	//convert upper-case "C" comments to lower-case "c" comments
    	if(fileType.equals("ADP")) {
    		EnsdfUtil.cleanENSDFFile(filename,true);
    		
    		//update Q-Value
    		if(!CheckControl.createCombinedDataset) {
    			AMERun ameRun=new AMERun();
    			try {
					ameRun.updateQValues(new File(filename));
					
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    		}
    	}
    }
    
    private void writeHeader(PrintWriter out,String title){
        Date date=new Date();
        SimpleDateFormat sdf=new SimpleDateFormat("E MM/dd/yyyy 'at' hh:mm:ss a zzz");
        String dateS="Generated at: "+sdf.format(date);
        
    	out.write("Program for consistency check: output for "+title+" (version "+CheckControl.version+")"
    			+"\n"+dateS+"\n\n");
    }
    
    @SuppressWarnings("unused")
	private void writeTail(String fileType){
    }
    
    
    public void writeOutputs(String filename){
    	if(CheckControl.writeLEV) writeFile(filename+".lev","LEV");
    	if(CheckControl.writeGAM) writeFile(filename+".gam","GAM");
    	if(CheckControl.writeGLE) writeFile(filename+".gle","GLE");
    	if(CheckControl.writeMRG) writeFile(filename+".mrg","MRG");
    	if(CheckControl.writeAVG) writeFile(filename+".avg","AVG");
    	if(CheckControl.writeFED) writeFile(filename+".fed","FED");
    	if(CheckControl.writeRPT){
    		writeFile(filename+".err","ERR");
    		writeFile(filename+".wrn","WRN");
    		writeFile(filename+".xrf","XRF");
    	}
    }
    
    /*
     * newResults must be from printAverageReportOfRecordGroup(), which is the output of AvergeReport.getReport()
     * dataType="E" for level and gamma energies, "RI" for gamma intensities, "T" for half-life
     */
    public void updateAverageOutput(String avgOutfileName,RecordGroup recordGroup,String newResults,String dataType){
    	Vector<String> lines=null;
    	try{
    		if(!avgOutfileName.contains(".avg"))
    			avgOutfileName+=".avg";
    		
    		String id=findRecordGroupID(recordGroup);
    		if(id.isEmpty())
    			return;
    		
    		String[] resultLines=newResults.trim().split("\n");
    		if(resultLines.length==0)
    			return;
    		
    		lines=Str.readFile(new File(avgOutfileName));
    		if(lines==null || lines.size()==0)
    			return;
    		
    		String line="";
    		Vector<Vector<String>> blocksV=new Vector<Vector<String>>();
    		Vector<String> block=new Vector<String>();
    		

    		int n=0;
    		for(int i=0;i<lines.size();i++){
    			line=lines.get(i);
    			if(line.contains("**************") && line.contains("=")){//title line in .avg file for a block of a level or gamma results 
    				n=i;
    				break;
    			}
    		}
    		
    		
    		Vector<String> matchedBlock=null;
    		
    		for(int i=n;i<lines.size();i++){
    			line=lines.get(i);
    			if(line.contains("**************") && line.contains("=")){//title line in .avg file for a block of a level or gamma results 
    				if(block.size()>0)
    					blocksV.add(block);
    				
    				block=new Vector<String>();
    				
    				String tempID="";
    				try{
    					tempID=line.substring(line.indexOf("ID#=")).substring(4).trim();
    				}catch(Exception e){}
    				
    				if(id.equals(tempID))
    					matchedBlock=block;
    			}
    			
    			block.add(line);
    		}
    		
			if(block.size()>0){
				blocksV.add(block);
			}

			//the first line of a block is the block title, e.g., "**************** Level=26.1 in 190IR     *******************"
			//the first line of newResults should be the sub-title line, e.g., "------ average E------"
			if(matchedBlock==null)
				return;

			Vector<String> newBlock=new Vector<String>();
			
			String subtitle=resultLines[0];
			for(int i=0;i<resultLines.length;i++){
				subtitle=resultLines[i].trim();
				if(subtitle.length()>0)
					break;
			}
			
			if(subtitle.length()==0)
				return;
			
			
			int size=matchedBlock.size();
			int istart=size,iend=size;
			
			for(int i=0;i<size;i++){
				line=matchedBlock.get(i);
				if(line.contains(subtitle)){
					istart=i;
					break;
				}
			}
			for(int i=istart+1;i<size;i++){
				line=matchedBlock.get(i);
				if(line.contains("------ average") || line.contains("****************")){
					iend=i;
					break;
				}
			}
			
			if(istart<iend){
				
				//System.out.println("  start="+istart+"   end="+iend+" matchedBlock.size="+size+" subtitle="+subtitle);
				//System.out.println(newResults);
				//for(String s:matchedBlock)
				//	System.out.println("****old"+s);
				
				newBlock.addAll(matchedBlock.subList(0, istart));
				
				//for(String s:newBlock)
				//	System.out.println("****new1"+s);
				
				for(int j=0;j<resultLines.length;j++)
					newBlock.add(resultLines[j]);
				
				//for(String s:newBlock)
				//	System.out.println("****new2"+s);
				
				newBlock.addAll(matchedBlock.subList(iend,size));
				
				//for(String s:newBlock)
				//	System.out.println("****new3"+s);
				
				matchedBlock.clear();
				matchedBlock.addAll(newBlock);
				matchedBlock.add("\n");
				
				//for(String s:matchedBlock)
				//	System.out.println("****final"+s);
				
				PrintWriter out=new PrintWriter(new File(avgOutfileName));
				writeHeader(out, "averaging results for gamma energies and intensities (update)");
				for(int i=0;i<blocksV.size();i++){
					for(int j=0;j<blocksV.get(i).size();j++)
						out.write(blocksV.get(i).get(j)+"\n");
				}
				
	
				out.flush();
				out.close();
			}			
    		
    	}catch(Exception e){
    		
    	}
    	
    }
    
    
    /*
     * update specified records in the input dataset with adopted value if available and
     * write updated dataset to a new file
     */
    public String updateENSDFWithAdopted(ENSDF ens,ArrayList<String> recordList, EnsdfGroup ensdfGroup,String outfilePath) {

    	
    	String msg="";
    	if(ens==null || ensdfGroup.adopted()==null || recordList==null || recordList.isEmpty())
    		return "Nothing to update";
    	if(!ensdfGroup.ensdfV().contains(ens) || ens.DSId0().contains("ADOPTED"))
    		return "Nothing to update";
    	
    	
    	//parse record list
    	ArrayList<String> levelRecords=new ArrayList<String>(Arrays.asList("E","J","T"));
    	ArrayList<String> gammaRecords=new ArrayList<String>(Arrays.asList("E","M","MR"));
    	ArrayList<String> LRecordsToReplace=new ArrayList<String>();
    	ArrayList<String> GRecordsToReplace=new ArrayList<String>();
    	for(String r:recordList) {
    		String s=r.toUpperCase();
    		if(levelRecords.contains(s))
    			LRecordsToReplace.add(s);
    		if(gammaRecords.contains(s))
    			GRecordsToReplace.add(s);
    	}
    	
    	if(LRecordsToReplace.isEmpty() && GRecordsToReplace.isEmpty())
    		return "Nothing to update";
    	
    	setCurrentGroup(ensdfGroup);
    	findFromAdopted(ens);
    	
    	Vector<String> newLines=new Vector<String>();
    	boolean isUpdated=false;
    	
    	
    	try {

        	Level lev=null,adoptedLev=null;
        	Gamma gam=null,adoptedGam=null;
        	int iLevel=-1;
        	int iGamma=-1;

        	for(int i=0;i<ens.nLines();i++) {
        		String line=ens.lineAt(i);
        		String type=line.substring(5,8);
        		if(type.equals("  L")) {
        			iLevel++;
        			lev=ens.levelAt(iLevel);
        			
        			iGamma=-1;
        			
        			adoptedLev=findAdoptedLevel(lev);
        			if(adoptedLev!=null) {
        				for(String s:LRecordsToReplace) {
        					if(isRecordFromAdopted(lev,s)) {
            					if(s.equals("J") && !lev.JPiS().equals(adoptedLev.JPiS())) {
            						line=EnsdfUtil.replaceJPIOfLevelLine(line, " "+adoptedLev.JPiS());
            						isUpdated=true;
            					}else if(s.equals("T") && (!lev.T12S().equals(adoptedLev.T12S()) || !lev.DT12S().equals(adoptedLev.DT12S()))){
            						line=EnsdfUtil.replaceHalflifeOfLevelLine(line, adoptedLev.T12S(), adoptedLev.DT12S(), adoptedLev.T12Unit());
            						isUpdated=true;
            					}
        					}
        				}

        			}
         			
        		}else if(type.equals("  G") && iLevel>=0) {
        			iGamma++;
        			gam=lev.gammaAt(iGamma);
        			
        			adoptedGam=findAdoptedGamma(lev,gam);
        			
        			if(adoptedGam!=null) {
        				for(String s:GRecordsToReplace) {
        					if(this.isRecordFromAdopted(gam, s)) {
            					if(s.equals("M") && !gam.MS().equals(adoptedGam.MS())) {
               						line=EnsdfUtil.replaceMULTOfGammaLine(line, adoptedGam.MS());
               						isUpdated=true;
            					}else if(s.equals("MR") && (!gam.MRS().equals(adoptedGam.MRS()) || !gam.DMRS().equals(adoptedGam.DMRS()))) {
            						line=EnsdfUtil.replaceMROfGammaLine(line, adoptedGam.MRS(), adoptedGam.DMRS());
            						isUpdated=true;
            					}
        					}
        				}

        			}        			
        		}
        		
    			newLines.add(line);
        			
        	}
        	
        	File f=new File(outfilePath);
    		PrintWriter out=new PrintWriter(f);
	    	if(isUpdated) {
	    		Str.write(out, newLines);
	    		msg+="The following records in "+ens.DSId0()+" have been updated with Adopted values:\n";
	    		msg+="    updated records:  ";
	    		for(String s:recordList)
	    			msg+=s+",";
	    		msg=msg.substring(0,msg.length()-1)+"\n";
	    		msg+="\nUpdated dataset is written into file:\n  "+outfilePath+"\n";
	    		
	    	}else {
	    		msg+="Nothing needs to update. Values quoted as from adopted are consistent with adopted values\n";
	    	}
	    	
	    	out.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
      	
    	return msg;
    }
    
    
    /*
     * find ID of a recordGroup inside a vector of EnsdfGroup objects, used in average report to identify each block of average results
     */
    public String findRecordGroupID(RecordGroup recordGroup){
    	String id="";
    	int index=-1;
    	try{
        	Record first=recordGroup.getRecord(0);
        	if(first instanceof Level){
            	for(int i=0;i<ensdfGroupsV.size();i++){
            		String ensdfID=""+(i+1);
            		EnsdfGroup ensdfGroup=ensdfGroupsV.get(i);
            		
            		index=ensdfGroup.levelGroupsV().indexOf(recordGroup);
            		if(index>=0)
            			return ensdfID+"-"+(index+1);
            	}
        	}else if(first instanceof Gamma){
            	for(int i=0;i<ensdfGroupsV.size();i++){
            		String ensdfID=""+(i+1);
            		EnsdfGroup ensdfGroup=ensdfGroupsV.get(i);
            		
            		index=ensdfGroup.unpGammaGroupsV().indexOf(recordGroup);
            		if(index>=0)
            			return ensdfID+"-0-"+(index+1);
            		
            		
            		for(int j=0;j<ensdfGroup.levelGroupsV().size();j++){
            			String levelID=""+(j+1);
            			RecordGroup levelGroup=ensdfGroup.levelGroupsV().get(j);
            			index=levelGroup.subgroups().indexOf(recordGroup);
                		if(index>=0)
                			return ensdfID+"-"+levelID+"-"+(index+1);
            		}  
            	}	
        	}
    	}catch(Exception e){
    		
    	}

    	return id;
    }
    
    public void clearMessage(){
    	message="";
    }
    
    public void clearXREFWarningMsg(){
    	XREFWarningMsg="";
    }
    
    public String parityChangeStr(int pc){
    	if(pc<0)
    		return "yes";
    	else if(pc>0)
    		return "no";
    	else
    		return "";
    }
    
    public void start() throws Exception{
    	
    	if(chain==null || chain.nENSDF()==0)
    		return;
    	
    	//System.out.println("1  size="+ensdfGroupsV.size());
    	
        ensdfGroupsV=groupENSDFs(chain,true);

        //System.out.println("2  size="+ensdfGroupsV.size());
        
        //all messages will be stored in the global variable=message
        for(int i=0;i<ensdfGroupsV.size();i++){
        	checkEnsdfGroup(ensdfGroupsV.get(i));
        }

    }
    
}

