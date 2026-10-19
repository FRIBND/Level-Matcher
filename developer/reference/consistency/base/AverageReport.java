package consistency.base;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Vector;

import ensdfparser.calc.Average;
import ensdfparser.calc.DataPoint;
import ensdfparser.ensdf.Gamma;
import ensdfparser.ensdf.Level;
import ensdfparser.ensdf.Record;
import ensdfparser.ensdf.SDS2XDX;
import ensdfparser.ensdf.XDX2SDS;
import ensdfparser.nds.ensdf.EnsdfUtil;
import ensdfparser.nds.latex.Translator;
import ensdfparser.nds.util.Str;


/*
 * average and generate reports for a group of data points for an ENSDF record 
 */
public class AverageReport {

	private double weightLowerLimit=0.02;
	private String report="",recordName="";
	private String ENSDFlinePrefix="";//used for making ENSDF comments
	private Average avg;
	private String avgOption="";
	
	private boolean isAllSameValues=false,isAllSameUncs;//for all good data points
	private boolean isNonAverage=false;//all values are the same or the average is the same as one of the input 
	private int nonaverageIndex=-1;//index of adopted value for nonaverage
	
	
	//for weighted average of selected values excluding outliers, (value,uncertainty) pair in ENSDF format 
	//valueStrInt: value string associated with internal error in ENSDF format
	//valueStrExt: value String associated with external error in ENSDF format
	@SuppressWarnings("unused")
	private String valueStrInt,valueStrExt,intErrorStr,extErrorStr,intErrorPStr,intErrorMStr,extErrorPStr,extErrorMStr;   
	private String valueStr,errorStr,errorPStr,errorMStr;//errorStr is for the larger error in extError and intError
	
	//adopted (value,uncertainty) pair in ENSDF format, following that
	//No uncertainty is smaller than the smallest input one
	private String adoptedValStr,adoptedUncStr;
	private double adoptedVal,adoptedUnc;
	
	//for weighted average of all values,(value,uncertainty) pair in ENSDF format    
	private String valueStrIntAll,valueStrExtAll,intErrorStrAll,extErrorStrAll,intErrorPStrAll,intErrorMStrAll,extErrorPStrAll,extErrorMStrAll;
	private String valueStrAll,errorStrAll,errorPStrAll,errorMStrAll;//errorStrAll is for the larger error in extErrorAll and intErrorAll
	private String unwValueStr,unwErrorStr;//for unweighted average, (value,uncertainty) pair in ENSDF format
	private String unwValueStrGoodDP,unwErrorStrGoodDP;//for unweighted average, (value,uncertainty) pair in ENSDF format
    
	
	//ENSDF-format comment lines, than can be directly copied and pasted into an ENSDF file
	//<type,comment>:
	//type: 
	//all: comment for weighted average including all data points
	//default: comment for weighted average including all data points except for those under weight threshold
	//unweighted:comment for unweighted average including all data points
	//nonaverage:comment for no average (one value is selected)
	//adopted: adopted comment (among all,default,unweighted and nonaverage), corresponding to adopted value and uncertainty
	private HashMap<String,String> avgCommentMap=null;
	
	
	public AverageReport(){
		weightLowerLimit=0.02f;
		report="";
		recordName="";
		ENSDFlinePrefix="";
		avgOption="";
		avgCommentMap=new HashMap<String,String>();
		
		valueStrInt="";
		valueStrExt="";
		intErrorStr="";
		extErrorStr="";
		intErrorPStr="";
		intErrorMStr="";
		valueStr="";
		errorStr="";
		errorPStr="";
		errorMStr="";
		
		valueStrIntAll="";
		valueStrExtAll="";
		intErrorStrAll="";
		extErrorStrAll="";
		intErrorPStrAll="";
		intErrorMStrAll="";
		extErrorPStrAll="";
		extErrorMStrAll="";
		
		valueStrAll="";
		errorStrAll="";
		errorPStrAll="";
		errorMStrAll="";
		
		adoptedValStr="";
		adoptedUncStr="";
		
		adoptedVal=-1;
		adoptedUnc=-1;
		
		unwValueStr="";
		unwErrorStr="";
		
		unwValueStrGoodDP="";
		unwErrorStrGoodDP="";
		
		isAllSameValues=false;
		isAllSameUncs=false;
		isNonAverage=false;
		nonaverageIndex=-1;
		
		avg=new Average();
		
	}
	
	/*
	 * avgOption="1", use a different variance for calculating weight of an asymmetric uncertainty
	 */
	public AverageReport(RecordGroup recordGroup,String recordName,String prefix,double weightLowerLimit,String avgOption){
		this();
		doWork(recordGroup, recordName, prefix, weightLowerLimit, avgOption);
	}
	public AverageReport(RecordGroup recordGroup,String recordName,String prefix,double weightLowerLimit){
		this();
		doWork(recordGroup, recordName, prefix, weightLowerLimit, "");
	}
	
	/////
	public AverageReport(RecordGroup recordGroup,String recordName,String prefix,String avgOption){
		this();
		doWork(recordGroup,recordName,prefix,weightLowerLimit,avgOption);
	}
	public AverageReport(RecordGroup recordGroup,String recordName,String prefix){
		this();
		doWork(recordGroup,recordName,prefix,weightLowerLimit,"");
	}
	
	/////
	public AverageReport(Vector<DataPoint> dpsV,String recordName,String prefix,double weightLowerLimit,String avgOption){
		this();
		doWork(dpsV, recordName, prefix, weightLowerLimit, avgOption);
	}
	public AverageReport(Vector<DataPoint> dpsV,String recordName,String prefix,double weightLowerLimit){
		this();
		doWork(dpsV, recordName, prefix, weightLowerLimit, "");
	}
	
	/////
	public AverageReport(Vector<DataPoint> dpsV,String recordName,String prefix,String avgOption){
		this();
		doWork(dpsV, recordName, prefix, weightLowerLimit,avgOption);
	}
	public AverageReport(Vector<DataPoint> dpsV,String recordName,String prefix){
		this();
		doWork(dpsV, recordName, prefix, weightLowerLimit,"");
	}
	
	
	private void doWork(RecordGroup recordGroup,String recordName,String prefix,double weightLowerLimit,String avgOption){
		this.avgOption=avgOption;
		setRecordName(recordName);
		setENSDFlinePrefix(prefix);
		setDataPoints(recordGroup,recordName,weightLowerLimit);
		makeReport();
	}
	
	public void doWork(Vector<DataPoint> dpsV,String recordName,String prefix,double weightLowerLimit,String avgOption){
		this.avgOption=avgOption;
		setRecordName(recordName);
		setENSDFlinePrefix(prefix);
		setDataPoints(dpsV,weightLowerLimit);
		makeReport();
	}
	
	public void setAvgOption(String s){avgOption=s;}
	
	public void setDataPoints(Vector<DataPoint> dpsV){
		
		avg=new Average(dpsV,weightLowerLimit,avgOption);
	}
	
	public void setDataPoints(Vector<DataPoint> dpsV,double weightLowerLimit){

		setweightLowerLimit(weightLowerLimit);
		
		avg=new Average(dpsV,weightLowerLimit,avgOption);
	}
	
	public void setDataPoints(RecordGroup recordGroup,String recordName,double weightLowerLimit){
		Vector<DataPoint> dpsV=new Vector<DataPoint>();
		String name=recordName.toUpperCase();
		double x=-1,dxu=-1,dxl=-1;
		String s="",ds="",unit="";
		
		for(int i=0;i<recordGroup.recordsV().size();i++){
			Record rec=recordGroup.getRecord(i);
			//String label=EnsdfUtil.makeLabelFromDSID(recordGroup.getDSID(i));
			String label=recordGroup.getPrintDSID(i);
    		
			//System.out.println("@@@@@AverageReport 203: label="+label+"  "+recordGroup.dsidsVWithDuplicateShortID.size());
			//for(String tempID:recordGroup.dsidsVWithDuplicateShortID)
			//	System.out.println("    "+tempID);
			
			//if(rec.ES().contains("1893.")) System.out.println(" 1  name="+recordName+"  es="+rec.ES());
			
			try{
				if(name.equals("E")){//energy (level/gamma/decay/delay)
					s=rec.ES();
					ds=rec.DES();
				}else if(name.equals("RI")){//only for gamma
					Gamma g=(Gamma)rec;
					//s=g.IS();
					//ds=rec.DIS();
					//if(Control.convertRIForAdopted && !Control.createCombinedDataset){
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
						
					//if(rec.ES().contains("3092")) {
					//	System.out.println("#### "+s+"  ##"+ds+"  "+unit);
					//}
						
					
				}else if(name.equals("S")){//relabelled C2S valeus (not that C2S value can't be averaged, but other values placed in C2S filed can)
					Level l=(Level)rec;
					s=l.sS();
					ds=l.dsS();
						
					//if(rec.ES().contains("3092")) {
					//	System.out.println("#### "+s+"  ##"+ds+"  "+unit);
					//}
						
					
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
				
				
				//if(name.equals("T"))
				//		System.out.println("name="+name+" s="+s+" ds="+ds+" x=="+x+"  dxu="+dxu+" dxl="+dxl);
				
				if(dxu<=0 || dxl<=0)
					continue;

	    		
				DataPoint dp=new DataPoint(x,dxu,dxl,label);
				dp.setS(s, ds);
				if(unit.length()>0)
					dp.setUnit(unit);
				
				dpsV.add(dp);
				
			}catch(NumberFormatException e){
				continue;
			}
		}
		
		/*
		if(Math.abs(recordGroup.getMeanEnergy()-3092)<5) {
			for(DataPoint dp:dpsV)
				System.out.println("#### "+recordName+" dp="+dp.s()+"  "+dp.ds()+" "+dp.label());
		}
		*/
		
		setDataPoints(dpsV,weightLowerLimit);
	}

	
	public void setweightLowerLimit(double weightLowerLimit){this.weightLowerLimit=weightLowerLimit;}
	public void setRecordName(String name){recordName=name;}
	public void setENSDFlinePrefix(String s){ENSDFlinePrefix=s;}
	public String getRecordName(){return recordName;}
	
	public Average getAverage(){return avg;}
	
	public HashMap<String,String> getAvgCommentMap(){return avgCommentMap;}
	
	public String getComment(String type){
		String s=avgCommentMap.get(type.toLowerCase());
		if(s==null)
			return "";
		
		return s;
	}
	
	public String getDefaultComment(){
		return getComment("default");
	}
	
	public String getAdoptedComment(){
		return getComment("adopted");
	}
	
	public String print(){return report;}
	public String getReport(){return report;}
	
	public boolean isAllSameValues(){return isAllSameValues;}
	public boolean isAllSameUncs() {return isAllSameUncs;}
	public boolean isNonAverage() {return isNonAverage;}
	
	private String shortenLabel(String label) {
        //for long label like, (1974Ro31, average of 21 {I3} from |b counting, 20 {I5} from neutron)
        if(label.length()>30) {              
            String[] temp=label.split("[,\\s]+");
            String str="";
            for(int j=0;j<temp.length;j++) {
                str=temp[j].trim();
                if(str.length()>0) {
                    label=str;
                    break;
                }
            }
        }	

        return label;
	}
	
    private void makeReport(){
        String defaultComment="";
        String commentForAll="";
        String unweightedComment="";
        String nonaverageComment="";
        String adoptedComment="";
        	

        
    	if(avg==null)
    		return;
    	
    	/*
    	if(avg.goodPointIndexesV().size()<=1)
    		return;
    	if(avg.intError()==0 && avg.extError()==0)
    		return;
    	*/

    	//System.out.println(avg.dataPointsV().size()+"  "+ avg.nonLimitPointIndexesV().size());
    	
    	if(avg.nonLimitPointIndexesV().size()<=1)
    		return;
    	    	
    	String dataStr="";
        StringBuilder s=new StringBuilder();
        StringBuilder commentStr=new StringBuilder();
        
    	s.append("\n------ average "+recordName+"------\n");
    	s.append("Data points of "+recordName+" record\n"); 
    			
        //In average function, if only one value or none has weight>weight_limit and the  
        //rest are out of limit, all values are used in average for displaying information
    	int nAboveLimit=avg.aboveLimitIndexesV().size();
    	if(nAboveLimit<=1 && avg.goodPointIndexesV().size()>1)
    		s.append(String.format("### (%d value has weight>%.2f%%; all values with unc  considered in weighted average)\n",nAboveLimit,weightLowerLimit*100));
    	
    	Vector<Integer> tempV=new Vector<Integer>();
    	tempV.addAll(avg.usedPointIndexesV());
    	for(int i=0;i<avg.nonLimitPointIndexesV().size();i++) {
    		int index=avg.nonLimitPointIndexesV().get(i);
    		if(!tempV.contains(index))
    			tempV.add(index);
    	}
    	for(int i=0;i<tempV.size();i++){
    		int index=tempV.get(i);
    		double weight=avg.weightsV().get(index);
    		String marker=" ";
    		String ds=avg.getDS(index).trim();
    		if(ds.length()>0)
    			ds="("+ds+")";
    		
    		dataStr=avg.getS(index).trim()+ds;
    		if(weight>=weightLowerLimit)
    			marker="*";

    		if(weight>0)
    			s.append(marker+String.format("%30s   %-16s    weight=%.2f%%\n",shortenLabel(avg.getLabel(index)),dataStr,weight*100));		
    		else
    			s.append(marker+String.format("%30s   %-16s    not used in weighted\n",shortenLabel(avg.getLabel(index)),dataStr));	
    	}
    	
    	if(avg.unusedPointIndexesV().size()>0){
    		s.append(String.format("### (weight<%.2f%% not considered in weighted average)\n", weightLowerLimit*100));
        	for(int i=0;i<avg.unusedPointIndexesV().size();i++){
        		int index=avg.unusedPointIndexesV().get(i);
        		double weight=avg.weightsV().get(index);
        		String marker=" ";
        		String ds=avg.getDS(index).trim();
        		if(ds.length()>0)
        			ds="("+ds+")";
        		
        		dataStr=avg.getS(index).trim()+ds;
        		
        		s.append(marker+String.format("%30s   %-16s    weight=%.2f%%\n",shortenLabel(avg.getLabel(index)),dataStr,weight*100));		
        	}
    	}
        
    	String dataStr1="",dataStr2="",dataStr3="";
    	XDX2SDS x2s=new XDX2SDS();
    	
		boolean hasAsymExtError=false;
		boolean isGoodWA=true;
		if(avg.intError()<0 && avg.extError()<0){
			isGoodWA=false;
		}else if(avg.extErrorP()>=0 && avg.extErrorM()>=0 && avg.extErrorP()!=avg.extErrorM())
    		hasAsymExtError=true;
    			
    	x2s.setErrorLimit(CheckControl.errorLimit);
    	x2s.setValues(avg.value(),avg.intErrorP(),avg.intErrorM());
    	if(!x2s.dsl().equals(x2s.dsu())){
    		dataStr1=x2s.S()+"(+"+x2s.dsu()+"-"+x2s.dsl()+")";
    		
			valueStrInt=x2s.S();
			intErrorStr="+"+x2s.dsu()+"-"+x2s.dsl();
			intErrorPStr=x2s.dsu();
			intErrorMStr=x2s.dsl();
			
    	}else{
    		String ds=x2s.ds();
    		if(ds.length()>0)
    			ds="("+ds+")";
    		
    		dataStr1=x2s.S()+ds;
    		    		
			valueStrInt=x2s.S();
			intErrorStr=x2s.DS();
			intErrorPStr=x2s.DS();
			intErrorMStr=x2s.DS();
    	}
    	
        //System.out.println("AverageReport 388: "+avg.value()+" "+avg.intErrorP()+"  "+avg.intErrorM()+" dsu="+x2s.dsu()+" dsl="+x2s.dsl());
        
    	double r=0;
    	if(avg.value()!=0) r=Math.abs(avg.extError()/avg.value());
    	
		if(avg.extError()==0 || (r>0&&r<1E-6)){//all data points have the same value
			dataStr2=x2s.s()+"(0)";
    		
			valueStrExt=x2s.s();
			extErrorStr="0";
			
			hasAsymExtError=false;
		}else{
	    	x2s.setValues(avg.value(),avg.extError());
	    	
    		String ds=x2s.ds();
    		if(ds.length()>0)
    			ds="("+ds+")";
	    	dataStr2=x2s.S()+ds;
	    		
			valueStrExt=x2s.S();
			extErrorStr=x2s.DS();
			
			if(hasAsymExtError) {
		    	x2s.setValues(avg.value(),avg.extErrorP(),avg.extErrorM());	    	
	    		dataStr3=x2s.S()+"(+"+x2s.dsu()+"-"+x2s.dsl()+")";
	    		
				extErrorPStr=x2s.dsu();
				extErrorMStr=x2s.dsl();
			}
		}

    	
		//debug
		//if(Math.abs(avg.value()-750)<2)
		//System.out.println("In AverageReport line 174: "+avg.value()+" "+avg.intError()+" "+avg.extError());
		

		int ndf=avg.aboveLimitIndexesV().size();
		double criticalChi2=EnsdfUtil.criticalReducedChi2(ndf);

		//if(recordName.equals("E")&&avg.value()<1345.79&&avg.value()>1345.78) System.out.println(" 1 ndf="+ndf);

		s.append(String.format("\nAveraging results:\n"));
		
		if(isGoodWA) {
			s.append(String.format("           weighted average:      %-20s (internal)\n",dataStr1));
			if(hasAsymExtError && dataStr3.length()>0) {
				s.append(String.format("                                  %-20s (external, combined weight)\n",dataStr2));
				s.append(String.format("                                  %-20s (external, separate weights)\n",dataStr3));
			}else {
				s.append(String.format("                                  %-20s (external)\n",dataStr2));
			}
			
			s.append(String.format("                                  chi**2/(n-1)=%.3f     [critical=%.3f]\n",avg.chi2(),criticalChi2));
		}

        
        Average avg_all=avg;
		boolean hasAsymExtErrorAll=false;
		
    	if(avg.unusedPointIndexesV().size()>0){
    		avg_all=new Average(avg.dataPointsV(),0,avgOption);
    		
    		isGoodWA=true;
    		if(avg_all.intError()<0 && avg_all.extError()<0){
    			isGoodWA=false;
    		}
        	if(avg_all.extErrorP()>=0 && avg_all.extErrorM()>=0 && avg_all.extErrorP()!=avg_all.extErrorM())
        		hasAsymExtErrorAll=true;
        	
        	x2s.setValues(avg_all.value(),avg_all.intErrorP(),avg_all.intErrorM());
        	
        	if(!x2s.dsl().equals(x2s.dsu())){
        		dataStr1=x2s.S()+"(+"+x2s.dsu()+"-"+x2s.dsl()+")";
        		
    			valueStrIntAll=x2s.S();
    			intErrorStrAll="+"+x2s.dsu()+"-"+x2s.dsl();
    			intErrorPStrAll=x2s.dsu();
    			intErrorMStrAll=x2s.dsl();
    			
        	}else{
        		String ds=x2s.ds();
        		if(ds.length()>0)
        			ds="("+ds+")";
        		dataStr1=x2s.S()+ds;
        		
    			valueStrIntAll=x2s.S();
    			intErrorStrAll=x2s.DS();
    			intErrorPStrAll=x2s.DS();
    			intErrorMStrAll=x2s.DS();
    			
        	}
        	
        	r=0;
        	if(avg_all.value()!=0) r=Math.abs(avg_all.extError()/avg_all.value());
        	
    		if(avg_all.extError()==0 || (r>0&&r<1E-6)){//all data points have the same value
    			dataStr2=x2s.s()+"(0)";
    		
    			valueStrExtAll=x2s.s();
    			extErrorStrAll="0";
    			
    			hasAsymExtErrorAll=false;
    		}else{
    	    	x2s.setValues(avg_all.value(),avg_all.extError());
        		String ds=x2s.ds();
        		if(ds.length()>0)
        			ds="("+ds+")";
        		
    	    	dataStr2=x2s.S()+ds;
    	    	
    			valueStrExtAll=x2s.S();
    			extErrorStrAll=x2s.DS();
    			
    			if(hasAsymExtError) {
    		    	x2s.setValues(avg_all.value(),avg_all.extErrorP(),avg_all.extErrorM());	    	
    	    		dataStr3=x2s.S()+"(+"+x2s.dsu()+"-"+x2s.dsl()+")";
    	    		
    				extErrorPStrAll=x2s.dsu();
    				extErrorMStrAll=x2s.dsl();
    			}
    		}
        	
    		ndf=avg_all.aboveLimitIndexesV().size();
    		criticalChi2=EnsdfUtil.criticalReducedChi2(ndf);

    		//if(recordName.equals("E")&&avg.value()<1345.79&&avg.value()>1345.78) System.out.println(" 2 ndf="+ndf);
    		if(isGoodWA) {
        		s.append(String.format("           weighted average:      %-20s (internal)\n",dataStr1));
        		if(hasAsymExtErrorAll && dataStr3.length()>0) {
            		s.append(String.format("            (of all values)       %-20s (external, combined weight)\n",dataStr2));
            		s.append(String.format("            (of all values)       %-20s (external, separate weights)\n",dataStr3));
        		}else {
        	   		s.append(String.format("            (of all values)       %-20s (external)\n",dataStr2));
        		}

        		s.append(String.format("                                  chi**2/(n-1)=%.3f     [critical=%.3f]\n",avg_all.chi2(),criticalChi2));
    		}

    	}else {
            valueStrIntAll=valueStrInt;
            intErrorStrAll=intErrorStr;
            intErrorPStrAll=intErrorPStr;
            intErrorMStrAll=intErrorMStr;
            
            valueStrExtAll=valueStrExt;
            extErrorStrAll=extErrorStr;
    	}
    	
    	r=0;
    	if(avg.unweightedValue()!=0)
    		r=Math.abs(avg.unweightedError()/avg.unweightedValue());
    	
    	if(avg.unweightedError()==0 || (r>0&&r<1E-6)){//all data points have the same value
    		dataStr=dataStr2;
    		
    	    int n=dataStr.indexOf("(");
    	    if(n>0){
    	    	unwValueStr=dataStr.substring(0,n);
    	    	unwErrorStr=dataStr.substring(n+1).replace(")","").trim();
    	    }else{
    	    	unwValueStr=dataStr;
    	    }
    	    	
    	}else{
        	x2s.setValues(avg.unweightedValue(),avg.unweightedError());
    		String ds=x2s.ds();
    		if(ds.length()>0)
    			ds="("+ds+")";
        	dataStr=x2s.S()+ds;	
        	
        	unwValueStr=x2s.S();
        	unwErrorStr=x2s.DS();
    	}

		ndf=avg.nonLimitPointIndexesV().size(); 
		criticalChi2=EnsdfUtil.criticalReducedChi2(ndf);
		
		//if(recordName.equals("E")&&avg.value()<1345.79&&avg.value()>1345.78) System.out.println(" 3 ndf="+ndf);
		
    	s.append(String.format("         unweighted average:      %-20s\n",dataStr));
    	s.append(String.format("           (of all values)        chi**2/(n-1)=%.3f     [critical=%.3f]\n",avg.unweightedChi2(),criticalChi2));
    	s.append("\n");

    	unwValueStrGoodDP=unwValueStr;
    	unwErrorStrGoodDP=unwErrorStr;
    	
        //unweighted of good data points
    	if(avg.unweightedValue()!=avg.unweightedValueGoodDP() && avg.goodPointIndexesV().size()>0 
    			&& avg.goodPointIndexesV().size()<avg.nDataPoints()) {
        	r=0;
        	if(avg.unweightedValueGoodDP()!=0)
        		r=Math.abs(avg.unweightedErrorGoodDP()/avg.unweightedValueGoodDP());
        	
        	if(avg.unweightedErrorGoodDP()==0 || (r>0&&r<1E-6)){//all data points have the same value
        		dataStr=dataStr2;
        		
        	    int n=dataStr.indexOf("(");
        	    if(n>0){
        	    	unwValueStrGoodDP=dataStr.substring(0,n);
        	    	unwErrorStrGoodDP=dataStr.substring(n+1).replace(")","").trim();
        	    }else{
        	    	unwValueStrGoodDP=dataStr;
        	    }
        	    	
        	}else{
            	x2s.setValues(avg.unweightedValueGoodDP(),avg.unweightedErrorGoodDP());
        		String ds=x2s.ds();
        		if(ds.length()>0)
        			ds="("+ds+")";
            	dataStr=x2s.S()+ds;	
            	
            	unwValueStrGoodDP=x2s.S();
            	unwErrorStrGoodDP=x2s.DS();
        	}

    		ndf=avg.goodPointIndexesV().size(); 
    		criticalChi2=EnsdfUtil.criticalReducedChi2(ndf);
    		
    		//if(recordName.equals("E")&&avg.value()<1345.79&&avg.value()>1345.78) System.out.println(" 3 ndf="+ndf);
    		
        	s.append(String.format("         unweighted average:      %-20s\n",dataStr));
        	s.append(String.format("        (of values with unc)      chi**2/(n-1)=%.3f     [critical=%.3f]\n",avg.unweightedChi2(),criticalChi2));
        	s.append("\n");
    	}
        //make average comments to be copied and used in an ENSDF file
        String used=printGroupRecords(avg.usedPointIndexesV());
        String unused=printGroupRecords(avg.unusedPointIndexesV());
        String allgood=printGroupRecords(avg.goodPointIndexesV()); //good=used+unused (<all points=good+bad (negative unc))
        String allNonLimit=printGroupRecords(avg.nonLimitPointIndexesV()); //nonLimit=all values except limits
        
        String txt=used;
        if(txt.length()>0 && unused.length()>0){
        	if(avg.unusedPointIndexesV().size()>1)
        		txt+=". Others: "+unused;
        	else
        		txt+=". Other: "+unused;
        }
        else if(unused.length()>0)
        	txt=unused;

        
        String temp="";
        
        if(used.length()>0){
        	commentStr.append("### weighted average comment:\n");  
        	temp=recordName+"$weighted average of "+txt;
        	try {
        		defaultComment=Str.wrapString(temp,ENSDFlinePrefix,80,5);
        		commentStr.append(defaultComment+"\n\n");
				
				commentForAll=defaultComment;
				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}   	
        }
        
        if(unused.length()>0){
        	commentStr.append("### weighted average comment (all values):\n");  
        	temp=recordName+"$weighted average of "+allgood;
        	try {
        		commentForAll=Str.wrapString(temp,ENSDFlinePrefix,80,5);
        		commentStr.append(commentForAll+"\n\n");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}   	
        }
         
        if(allgood.length()>0){
        	commentStr.append("### unweighted average comment (all non-limit values with/without unc):\n");          
        	temp=recordName+"$unweighted average of "+allNonLimit;

    		
        	try {
        		unweightedComment=Str.wrapString(temp,ENSDFlinePrefix,80,5);
        		commentStr.append(unweightedComment+"\n\n");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}   	
        }
        
        Vector<Integer> othersIndexesV=new Vector<Integer>();
        
        isNonAverage=false;
        nonaverageIndex=-1;
        
        //make non-average comments
        if(nAboveLimit==1){

        	nonaverageIndex=avg.aboveLimitIndexesV().get(0);
        	othersIndexesV.addAll(avg.belowLimitIndexesV());

        	if(avg.belowLimitIndexesV().size()>0){
        		if(avg.value()!=0 && avg_all.isAvgEqualToAnInput(CheckControl.errorLimit)){//if average of all is the same as the input value with smallest uncertainty (largest weight)
        			isNonAverage=true;       		
            	}

        	}
  		
            
        	//debug
        	//if(Math.abs(avg.value()-48.76)<1) {
        	//	System.out.println("AverageReport 346: "+nAboveLimit+" index="+nonaverageIndex+" "+avg.dataPointsV().get(nonaverageIndex).label());
        	//}
        	
        }else if(allgood.length()>0){
        	int maxWeightIndex=avg.maxWeightIndex();
        	double preValue=-1,preUnc=-1;
        	isAllSameValues=true;
        	isAllSameUncs=true;
        	
        	for(int i=0;i<avg.goodPointIndexesV().size();i++){
         		int index=avg.goodPointIndexesV().get(i);
        		double value=avg.dataPointsV().get(index).x();
        		double unc=avg.dataPointsV().get(index).dx();
   
        		if(value!=preValue){
        			isAllSameValues=false;
        		}
        		if(preUnc>0 && unc!=preUnc){
        			isAllSameUncs=false;
        		}
       		
        		preValue=value;
        		preUnc=unc;
        	}
        	
        	if(maxWeightIndex>=0) {
        		
        		isNonAverage=false;
        		
        		nonaverageIndex=maxWeightIndex;      
        		
        		othersIndexesV.addAll(avg.goodPointIndexesV()); 
        		
        		//System.out.println(avg_all.isAvgEqualToAnInput(Control.errorLimit));
        		
        		if(isAllSameValues){
         		
            		
            		//for(Integer in:othersIndexesV)
            		//	System.out.println("1 size="+othersIndexesV.size()+"  i="+in.intValue()+" maxWeightIndex="+maxWeightIndex);
            		
            		if(!isAllSameUncs)
            			othersIndexesV.remove(new Integer(maxWeightIndex));

            		//for(Integer in:othersIndexesV)
            		//	System.out.println("2 size="+othersIndexesV.size()+"  i="+in.intValue()+" maxWeightIndex="+maxWeightIndex);
            		
            		isNonAverage=true;
            		
            	}else if(avg_all.value()!=0 && avg_all.isAvgEqualToAnInput(CheckControl.errorLimit)){//if average is the same as the input value with smallest uncertainty (largest weight)
        			othersIndexesV.remove(new Integer(maxWeightIndex));       			
        			isNonAverage=true;         
            	}
        	}
        }
        
        if(isNonAverage) {
          	String others=printGroupRecords(othersIndexesV);
        	String label=avg.getLabel(nonaverageIndex);//label of a data point=DSID
        	
        	//System.out.println(" "+others.length()+" label="+label+"  "+others+" "+nonaverageIndex);
        	temp="";
        	
        	if(others.length()>0 && label.length()>0){
        		if(isAllSameUncs && isAllSameValues) {
        			temp="";
        		}else {
                	if(othersIndexesV.size()>1)
                		temp=". Others: "+others;
                	else
                		temp=". Other: "+others;
        		}
        		           	
            	if(!CheckControl.createCombinedDataset) {
            		DataPoint dp=avg.dataPointsV().get(nonaverageIndex);
            		if(dp.label2().length()>0)
            			label=label+" in "+dp.label2();
            	}
            	
            	label=EnsdfUtil.makeLabelFromDSID(label,true);
            	
            	temp="$from "+label+temp;
        	}

        	if(temp.length()>0){
        		commentStr.append("### Non-average comment:\n");
            	temp=recordName+temp;
            	try {
            		nonaverageComment=Str.wrapString(temp,ENSDFlinePrefix,80,5);
            		commentStr.append(nonaverageComment+"\n\n");
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	}
        }
        
        //System.out.println("    avg="+avg.value()+"  chi2="+avg.chi2()+"  avg_all="+avg_all.value()+"  "+avg.isEqualWeighted(avg_all,Control.errorLimit));
    	
        ///////////////////////////////////////////////////
        //find adopted value and uncertainty of average
        //////////////////////////////////////////////////
        
        int maxNDigitsAfterDot=avg.findMaxNDigitsAfterDot();
        
	    Average avg_adopted=avg;
	    boolean done=true;
	    String label="Weighted-Average";
	    double chi2=avg.chi2();
	    double all_chi2=avg_all.chi2();
	    
	    //System.out.println("AverageReport 696: chi2="+chi2+" isAllSame="+isAllSameValues+" isNonAverage="+isNonAverage+" all_chi2="+all_chi2);
	    
	    if(chi2>=0 && (isAllSameValues || (isNonAverage&&!CheckControl.forceAverageAll)) ) {
	    	adoptedComment=nonaverageComment;
            label="Non-Average";
	    	
	    	DataPoint dp=avg.dataPointsV().get(nonaverageIndex);
	    	adoptedValStr=dp.s();
	    	adoptedUncStr=dp.ds();
            
	    }else if(Math.min(chi2, all_chi2)>3.5) {
	    	adoptedComment=unweightedComment;
            label="Unweighted-Average";
	    	
	    	SDS2XDX s2x=SDS2XDX.checkUncertainty(unwValueStr, unwErrorStr, maxNDigitsAfterDot,CheckControl.errorLimit);

	    	adoptedValStr=s2x.s();
	    	adoptedUncStr=s2x.ds();
	    	
	    }else if(avg.isEqualWeighted(avg_all,CheckControl.errorLimit)) {
	    	label="Weighted-Of-All";
	    	adoptedComment=defaultComment;
        	done=false;
	    }else if(all_chi2>=0 && (all_chi2<chi2||all_chi2<3.0) ) {
	    	label="Weighted-Of-All";
	    	adoptedComment=commentForAll;
	    	avg_adopted=avg_all;
	    	done=false;
	    }else if(all_chi2>=0){//this is the most general case, all_chi2>chi2, uncertainty could be the same, but mean value different	    	
	    	adoptedComment=defaultComment;
        	done=false;
	    }
	    
	    //System.out.println("AverageReport 739: adoptedValStr="+adoptedValStr+"  adoptedUncStr="+adoptedUncStr);
	    
	    if(!done) {
	    	double value=avg_adopted.value();
	    	double errorP=avg_adopted.adoptedErrorP();
	    	double errorM=avg_adopted.adoptedErrorM();
	    	
	    	x2s=new XDX2SDS();
	    	
	    	x2s.setErrorLimit(CheckControl.errorLimit);
	    	
	       	x2s.setValues(value,errorP,errorM);
	       	    
	       	//System.out.println("AverageReport 752: value="+value+" errorP="+errorP+" errorM="+errorM+" errorLimit="+CheckControl.errorLimit+" s="+x2s.s()+" ds="+x2s.ds());
	       	
	       	maxNDigitsAfterDot=avg_adopted.findMaxNDigitsAfterDot();
	       	
        	if(!x2s.dsl().equals(x2s.dsu())){
    	    	SDS2XDX sx1=SDS2XDX.checkUncertainty(x2s.s(), x2s.dsu(), maxNDigitsAfterDot,CheckControl.errorLimit);
    	    	SDS2XDX sx2=SDS2XDX.checkUncertainty(x2s.s(), x2s.dsl(), maxNDigitsAfterDot,CheckControl.errorLimit);
    	    	if(!sx1.s().equals(sx2.s())){
	        		adoptedValStr=x2s.s();
	        		adoptedUncStr="+"+x2s.dsu()+"-"+x2s.dsl();	
    	    	}else{
	    	    	adoptedValStr=sx1.s();
	    	    	if(sx1.ds().equals(sx2.ds()))
	    	    		adoptedUncStr=sx1.ds();
	    	    	else
	    	    		adoptedUncStr="+"+sx1.ds()+"-"+sx2.ds();
    	    	}  	    	
	
        	}else{
        		
    	    	SDS2XDX s2x=SDS2XDX.checkUncertainty(x2s.s(), x2s.ds(), maxNDigitsAfterDot,CheckControl.errorLimit);
    	    	adoptedValStr=s2x.s();
    	    	adoptedUncStr=s2x.ds();
    	    	
    	    	//System.out.println("AverageReport 739: adoptedValStr="+adoptedValStr+"  adoptedUncStr="+adoptedUncStr+" s="+x2s.s()+" ds="+x2s.ds()+" "+maxNDigitsAfterDot);
        	}

        	//debug
        	//System.out.println("AverageReport 762:  x2s.s()="+x2s.s()+"  +"+x2s.dsu()+"-"+x2s.dsl()+"   Control.errorLimit="+Control.errorLimit+" "+maxNDigitsAfterDot);
	    	//System.out.println(" x="+value+" dxu="+errorP+" dxl="+errorM+" s="+adoptedValStr+" ds="+adoptedUncStr+" x2s.dsu="+x2s.dsu()+" x2s.dsl="+x2s.dsl());
	    	//System.out.println("  adoptedValStr="+adoptedValStr+"  adoptedUncStr="+adoptedUncStr);

        	
	    }

	    try {	    
	        SDS2XDX s2x=new SDS2XDX(adoptedValStr,adoptedUncStr);
	    	adoptedVal=s2x.x();
	    	adoptedUnc=s2x.dx();
	    }catch(Exception e) {	    	
	    }

	    String noteS="";
	    if(hasAsymExtError || hasAsymExtErrorAll) {
	    	noteS+="Note:  Combined weight means both unc+ and unc- of each input value are used to calculate\n"
	    		 + "          the final weight and external uncertainty, as used for the average value and chi2.\n";
	    	noteS+="       Separate weights means unc+ and unc- are used separately to calculate corresponding\n"
	    		 + "          weights and external uncertainties, respectively.\n";
	    }
	    
	    if(!adoptedUncStr.isEmpty()) {
	   		s.append(String.format("   suggested adopted result:      %-20s\n",adoptedValStr+"("+adoptedUncStr+")"));
	   		s.append(                       String.format("%24s\n\n","("+label+")"));
	   		
	   		if(noteS.length()>0) {
		    	noteS+="       Suggested adopted result is just the preference of this average code which adopts\n"
		    		 + "          an uncertainty no smaller than any input uncertainty and uses combined weight for\n"
		    		 + "          getting the external uncertainty. It is user's decision to make the final choice.\n\n";
	   		}else {
		    	noteS+="NOTE:  Suggested adopted result is just the preference of this average code which adopts\n"
		    		 + "          an uncertainty no smaller than any input uncertainty and uses combined weight for\n"
		    		 + "          getting the external uncertainty. It is user's decision to make the final choice.\n\n";
	   		}
	    }
	    
	    if(noteS.length()>0)
	    	s.append(noteS);
	    
	    s.append(commentStr);
	    //final check if it is non-average, for those that could escape from the non-average condition above: diff<(unc/100) && avgError<=unc
	    /*
	    try {
		    if(!isNonAverage && adoptedValStr.equals(avg.maxWeightDataPoint().s()) && adoptedUncStr.equals(avg.maxWeightDataPoint().ds())) {
		    	isNonAverage=true;
		    	adoptedComment=nonaverageComment;
		    }
	    }catch(Exception e) {}
        */
	 
	    	    
        avgCommentMap.put("default", defaultComment);
        avgCommentMap.put("all",commentForAll);
        avgCommentMap.put("unweighted",unweightedComment);
        avgCommentMap.put("nonaverage", nonaverageComment);
        avgCommentMap.put("adopted", adoptedComment);
       
        
        report+=s.toString();
        
	    //debug
	    //System.out.println("****\n"+getDefaultComment()+"***\n"+this.getAdoptedComment()+"&&");
        
        SDS2XDX s1=new SDS2XDX(valueStrIntAll,intErrorStrAll);
        SDS2XDX s2=new SDS2XDX(valueStrExtAll,extErrorStrAll);
        double x=avg_all.value();
        double dxu=Math.max(s1.dxu(), s2.dxu());
        double dxl=Math.max(s1.dxl(), s2.dxl());
        
        x2s=new XDX2SDS(x,dxu,dxl,CheckControl.errorLimit);
        maxNDigitsAfterDot=avg_all.findMaxNDigitsAfterDot();
        if(!x2s.dsl().equals(x2s.dsu())){
            SDS2XDX sx1=SDS2XDX.checkUncertainty(x2s.s(), x2s.dsu(), maxNDigitsAfterDot,CheckControl.errorLimit);
            SDS2XDX sx2=SDS2XDX.checkUncertainty(x2s.s(), x2s.dsl(), maxNDigitsAfterDot,CheckControl.errorLimit);
            if(!sx1.s().equals(sx2.s())){
                valueStrAll=x2s.s();
                errorStrAll="+"+x2s.dsu()+"-"+x2s.dsl(); 
                errorPStrAll=x2s.dsu();
                errorMStrAll=x2s.dsl();
            }else{
                valueStrAll=sx1.s();
                if(sx1.ds().equals(sx2.ds())) {
                    errorStrAll=sx1.ds();
                    errorPStrAll=errorStrAll;
                    errorMStrAll=errorStrAll;
                }else {
                    errorStrAll="+"+sx1.ds()+"-"+sx2.ds();
                    errorPStrAll=sx1.ds();
                    errorMStrAll=sx2.ds();
                }
            }           

        }else{
            SDS2XDX s2x=SDS2XDX.checkUncertainty(x2s.s(), x2s.ds(), maxNDigitsAfterDot,CheckControl.errorLimit);
            valueStrAll=s2x.s();
            errorStrAll=s2x.ds();
            errorPStrAll=errorStrAll;
            errorMStrAll=errorStrAll;
        }

        
        s1=new SDS2XDX(valueStrInt,intErrorStr);
        s2=new SDS2XDX(valueStrExt,extErrorStr);
        x=avg.value();
        dxu=Math.max(s1.dxu(), s2.dxu());
        dxl=Math.max(s1.dxl(), s2.dxl());
        
        x2s=new XDX2SDS(x,dxu,dxl,CheckControl.errorLimit);
        maxNDigitsAfterDot=avg.findMaxNDigitsAfterDot();
        if(!x2s.dsl().equals(x2s.dsu())){
            SDS2XDX sx1=SDS2XDX.checkUncertainty(x2s.s(), x2s.dsu(), maxNDigitsAfterDot,CheckControl.errorLimit);
            SDS2XDX sx2=SDS2XDX.checkUncertainty(x2s.s(), x2s.dsl(), maxNDigitsAfterDot,CheckControl.errorLimit);
            if(!sx1.s().equals(sx2.s())){
                valueStr=x2s.s();
                errorStr="+"+x2s.dsu()+"-"+x2s.dsl(); 
                errorPStr=x2s.dsu();
                errorMStr=x2s.dsl();
            }else{
                valueStr=sx1.s();
                if(sx1.ds().equals(sx2.ds())) {
                    errorStr=sx1.ds();
                    errorPStr=errorStr;
                    errorMStr=errorStr;
                }else {
                    errorStr="+"+sx1.ds()+"-"+sx2.ds();
                    errorPStr=sx1.ds();
                    errorMStr=sx2.ds();
                }
            }           

        }else{
            SDS2XDX s2x=SDS2XDX.checkUncertainty(x2s.s(), x2s.ds(), maxNDigitsAfterDot,CheckControl.errorLimit);
            valueStr=s2x.s();
            errorStr=s2x.ds();
            errorPStr=errorStr;
            errorMStr=errorStr;
        }        
    }
    
    private String printGroupRecords(Vector<Integer> indexesV){
    	return printGroupRecords(indexesV,false);
    }
    
    private String printGroupRecords(Vector<Integer> indexesV,boolean useOriginalLabel){
    	String out="";
    	int size=indexesV.size();
        String unit="";
        if(size>0){
        	unit=avg.dataPointsV().get(0).unit();
        	if(unit.length()>0)
        		unit=" "+unit;
        }
        
        
        
        LinkedHashMap<String,Vector<DataPoint>> sortedDPSVMap=new LinkedHashMap<String,Vector<DataPoint>>();
        String s="",ds="",label="",label2="";

    	for(int i=0;i<size;i++){
    		
    		int index=indexesV.get(i);
    		DataPoint dp=avg.dataPointsV().get(index);
    		label=dp.label().trim();
    		label2=dp.label2().trim();
    		
    		String key=label2;
    		if(key.isEmpty())
    			key="EMPTY";

    		Vector<DataPoint> dpsV=sortedDPSVMap.get(key);
    		if(dpsV==null) {
    			dpsV=new Vector<DataPoint>();
    			sortedDPSVMap.put(key, dpsV);
    		}
    		
    		dpsV.add(dp);

    	}
    	
    	LinkedHashMap<DataPoint,String> extraLabelMap=new LinkedHashMap<DataPoint,String>();
        for(String key:sortedDPSVMap.keySet()) {
        	Vector<DataPoint> dpsV=sortedDPSVMap.get(key);
        	for(DataPoint dp:dpsV) 
        		extraLabelMap.put(dp, "");
        	
        	if(dpsV.size()>0 && !CheckControl.createCombinedDataset) {
        		DataPoint dp=dpsV.lastElement();
        		if(dp.label2().length()>0) {
        			extraLabelMap.put(dp,dp.label2());
        			//extraLabelMap.put(dp,CheckControl.groupLabelPrefix+dp.label2());
        		}
        	}
        }


        int i=0;
    	for(DataPoint dp:extraLabelMap.keySet()){

    		label=dp.label().trim();
    		s=dp.s();
    		ds=dp.ds();

    		String extraLabel0=extraLabelMap.get(dp);
    		String extraLabel=extraLabel0;
            if(label.length()>0) {
        		if(extraLabel0.length()>0)
        			extraLabel="from "+extraLabel0+" in ";
        		
                //example, label=Cichocki et al., Compt Rend 207, 423 (1938)
                //      or label=Osterr. Akad. Wiss., Math.-Naturw.Kl., Anz
                boolean done=false;
                if(EnsdfUtil.isKeynumber(label) || (label.contains(".")&&label.contains(",")&&!label.startsWith("(")) || (label.length()>=8 &&EnsdfUtil.isKeynumber(label.substring(0,8))) ) {
                    label=" "+extraLabel+"("+EnsdfUtil.makeLabelFromDSID(label)+")";
                    done=true;
                }else if(label.startsWith("(")) {
                    int n=label.indexOf(")");
                    if(n>0) {
                        String temp=label.substring(1,n).trim();
                        if(EnsdfUtil.isKeynumber(temp)) {
                            label=" "+extraLabel+EnsdfUtil.makeLabelFromDSID(label);
                            done=true;
                        }
                    }
                }else {
                    String temp=label.toUpperCase();
                    if(temp.startsWith("BY") || temp.startsWith("USING") || temp.startsWith("WITH") || temp.startsWith("FROM") || temp.startsWith("IN")) {
                    	if(!useOriginalLabel)
                    		label=" "+extraLabel+EnsdfUtil.makeLabelFromDSID(label);
                    	
                        done=true;
                    }else if(useOriginalLabel) {
                    	extraLabel="";
                    	if(extraLabel0.length()>0)
                    		extraLabel=extraLabel0+" in ";
                    	label=" from "+extraLabel+label;
                    	done=true;
                    }
                }
                
                if(!done) {
                	extraLabel="";
                	if(extraLabel0.length()>0)
                		extraLabel=extraLabel0+" in ";
                    label=" "+CheckControl.groupLabelPrefix+extraLabel+EnsdfUtil.makeLabelFromDSID(label,true)
                             +CheckControl.groupLabelPosfix;
                }
            }
    		
            if(ds.length()>0)
            	ds=" {I"+ds+"}";
            
    		out+=s+unit+ds+label;

    		if(i<size-1 && size>2)
    			out+=", ";
    		
    		if(i==size-2){
    			if(size>2)
    				out+="and ";
    			else
    				out+=" and ";
    		}
    		i++;
    		
    	}  	
    	return out;
    }
       

    
	public String valueStrInt(){return valueStrInt;}
	public String valueStrExt(){return valueStrExt;}
	public String intErrorStr(){return intErrorStr;}
	public String intErrorPStr(){return intErrorPStr;}//unc+
	public String intErrorMStr(){return intErrorMStr;}//unc-
	public String extErrorStr(){return extErrorStr;}
	
    public String valueStr(){return valueStr;}
    public String errorStr(){return errorStr;}
    public String errorPStr(){return errorPStr;}//unc+
    public String errorMStr(){return errorMStr;}//unc-
    
	public String valueStrIntAll(){return valueStrIntAll;}
	public String valueStrExtAll(){return valueStrExtAll;}
	public String intErrorStrAll(){return intErrorStrAll;}
	public String intErrorPStrAll(){return intErrorPStrAll;}//unc+
	public String intErrorMStrAll(){return intErrorMStrAll;}//unc-
	public String extErrorStrAll(){return extErrorStrAll;}
	public String extErrorPStrAll(){return extErrorPStrAll;}//unc+
	public String extErrorMStrAll(){return extErrorMStrAll;}//unc-
	
    public String valueStrAll(){return valueStrAll;}
    public String errorStrAll(){return errorStrAll;}
    public String errorPStrAll(){return errorPStrAll;}//unc+
    public String errorMStrAll(){return errorMStrAll;}//unc-

	
	public String unwValueStr(){return  unwValueStr;}
	public String unwErrorStr(){return unwErrorStr;}
	
	public String unwValueStrGoodDP(){return  unwValueStrGoodDP;}
	public String unwErrorStrGoodDP(){return unwErrorStrGoodDP;}
	
	public String adoptedValStr(){return adoptedValStr;}
	public String adoptedUncStr(){return adoptedUncStr;}
	
	public double adoptedVal(){return adoptedVal;}
	public double adoptedUnc(){return adoptedUnc;}
	
	public int nonaverageIndex(){return nonaverageIndex;}
	
    ///////////////////////////////////////////////////
    // wraps of Average member variables and functions
    //////////////////////////////////////////////////
    
	//for weighted average
	public double value(){return avg.value();}
	public double intError(){return avg.intError();}
	public double intErrorP(){return avg.intErrorP();}//unc+
	public double intErrorM(){return avg.intErrorM();}//unc-
	public double extError(){return avg.extError();}
	public double chi2(){return avg.chi2();}
	public double getWeight(int i){return avg.weightsV().get(i).doubleValue();}
	
	//for unweighted average
	public double unwValue(){return avg.unweightedValue();}
	public double unwError(){return avg.unweightedError();}
	public double unwChi2(){return avg.unweightedChi2();}
	
	public double unwValueGoodDP(){return avg.unweightedValueGoodDP();}
	public double unwErrorGoodDP(){return avg.unweightedErrorGoodDP();}
	public double unwChi2GoodDP(){return avg.unweightedChi2GoodDP();}
	
	public boolean isGoodWeighted(){return avg.isGoodWeighted();}
	public boolean isGoodUnweighted(){return avg.isGoodUnweighted();}
	public boolean isGoodUnweightedGoodDP(){return avg.isGoodUnweightedGoodDP();}
}
