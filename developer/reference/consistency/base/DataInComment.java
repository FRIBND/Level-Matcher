package consistency.base;

import java.util.Vector;

import ensdfparser.calc.DataPoint;

public class DataInComment {

    Vector<DataPoint> dpsV=new Vector<DataPoint>();
    String NUCID="";
    String recordType="";//column 8 and 9, e.g., for level ="L ", for delayed p="DP", prompt p=" P", parent record ="P "
    String entryName="";//record name, like "E", "T", "MR"
    
    DataInComment(){       
    }
    
    public boolean isEmpty() {
        return dpsV.size()<=0;
    }
    
    public Vector<DataPoint> dpsV(){return dpsV;}
    public String NUCID() {return NUCID;}
    public String recordType() {return recordType;}
    public String entryType() {return entryName;}
    
    //public void setDataPoints(Vector<DataPoint> datapointsV) {
    //    dpsV.clear();
    //    dpsV.addAll(datapointsV);
    //}
    
    //public void setNUCID(String s) {NUCID=s;}
    //public void setType(String s) {type=s;}
}
