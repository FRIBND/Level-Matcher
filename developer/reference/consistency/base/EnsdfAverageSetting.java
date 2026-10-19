package consistency.base;

import java.util.HashMap;

import ensdfparser.ensdf.ENSDF;

public class EnsdfAverageSetting {

    private String DSID,NUCID;
    private HashMap<String,Boolean> isUseComValueMap=null;
    private HashMap<String,Boolean> isOmitValueMap=null;
    
    //from user's input, override default input
    //for user's keyword, no comment head is required
    private HashMap<String,String> customKeywordMap=null;
    
    private boolean isSelected=true;
  
    
    public EnsdfAverageSetting() {
        DSID="";
        NUCID="";
        
        isUseComValueMap=new HashMap<String,Boolean>();
        isOmitValueMap=new HashMap<String,Boolean>();
        customKeywordMap=new HashMap<String,String>();
    }
    
    public EnsdfAverageSetting(ENSDF ens) {
        this();
        DSID=ens.DSId0();
        NUCID=ens.nucleus().nameENSDF().trim();
        
        isUseComValueMap=new HashMap<String,Boolean>();
        isOmitValueMap=new HashMap<String,Boolean>();
    }
    
    public void setIsUseComValue(String recordName,boolean isUseComValue) {
        if(recordName.equals("T1/2"))
            recordName="T";
        
        isUseComValueMap.put(recordName, isUseComValue);
        if(isUseComValue)
        	isOmitValueMap.remove(recordName);
    }
    
    public void setIsOmitValue(String recordName,boolean isOmitValue) {
        if(recordName.equals("T1/2"))
            recordName="T";
        
        isOmitValueMap.put(recordName, isOmitValue);
        if(isOmitValue)
        	isUseComValueMap.remove(recordName);
    }
    
    public boolean isUseComValue(String recordName) {
        try {
            if(recordName.equals("T1/2"))
                recordName="T";
            
            return isUseComValueMap.get(recordName).booleanValue();
        }catch(Exception e) {
            
        }
        
        return false;
    }
    
    public boolean isOmitValue(String recordName) {
        try {
            if(recordName.equals("T1/2"))
                recordName="T";
            
            return isOmitValueMap.get(recordName).booleanValue();
        }catch(Exception e) {
            
        }
        
        return false;
    }
    
    public void setCustomKeyword(String recordName,String keyword) {
        if(recordName.equals("T1/2"))
            recordName="T";
        
        customKeywordMap.put(recordName, keyword);
    }
    public String getCustomKeyword(String recordName) {
        if(recordName.equals("T1/2"))
            recordName="T";
        
        String keyword=customKeywordMap.get(recordName);
        if(keyword==null)
            return "";
        
        return keyword;
    }
    
    public void setDSID(String s) {DSID=s;}
    public void setNUCID(String s) {NUCID=s;}
    public String DSID() {return DSID;}
    public String NUCID() {return NUCID;}
    
    public boolean isSelected() {return isSelected;}
    public void setSelected(boolean b) {isSelected=b;}
    
    public boolean isEmpty() {
        if(isUseComValueMap.size()==0 && isOmitValueMap.size()==0)
            return true;
        
        return false;
    }
    
    public boolean isUseAnyComValue() {
        for(Boolean b:isUseComValueMap.values()) {
            if(b.booleanValue())
                return true;
        }
            
        return false;
    }
    
    public HashMap<String,Boolean> isUseComValueMap(){return isUseComValueMap;}
    public HashMap<String,Boolean> isOmitValueMap(){return isOmitValueMap;}
    public HashMap<String,String> customKeywordMap(){return customKeywordMap;}
    
    
}
