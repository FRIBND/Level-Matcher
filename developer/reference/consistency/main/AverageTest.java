package consistency.main;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.Vector;

import consistency.base.AverageReport;
import consistency.base.CheckControl;
import consistency.base.DataInComment;
import consistency.base.Util;
import ensdfparser.calc.Average;
import ensdfparser.calc.DataPoint;
import ensdfparser.ensdf.Comment;
import ensdfparser.ensdf.SDS2XDX;
import ensdfparser.nds.latex.Translator;
import ensdfparser.nds.util.Str;

public class AverageTest {
    public static void main(String[] args)throws Exception{
    	test3();
    }
     
    public static void test3() throws Exception{
    	Translator.init();
    	Average avg=new Average();
    	SDS2XDX s2x=avg.convertUnit("1.00", "10", "FS","EV");
    	
    	System.out.println(s2x.toString()+" ##"+s2x.s()+" $$"+s2x.ds());
    
    }
    
    @SuppressWarnings("unused")
	public static void test2() throws Exception{
    	Translator.init();
    	
    	String s=""+
    			" 76BR cG $E{-|a}=795.8 {I4}, from 785.75 {I8} (1973Pa02)";
    	
    	
    	String s1=" 65GE cL T$weighted mean of 31.2 s {I7} (1973Jo12), 30.0 s \n"
    			+ " 65GE2cL {I12} (1974Ro16), 30.8 s {I10} (1976Ha29,1981Ha44), and                \n"
    			+ " 65GE3cL 30.8 s {I7} (2000Gi11).    ";
 
    	
    	//s=s1;

    	Comment c=new Comment();
    	c.setBody(s);
    	Vector<DataPoint> dpsV=Util.parseDatapointsInComments(c,"E{-|a}=");

     
    	System.out.println(dpsV.size());
    	
    	String lineType="cG";
        String entryName="G";
        String NUCID="xxxxx";
        
        String prefix=Str.makeENSDFLinePrefix(NUCID, lineType);
        
        //System.out.println(" prefix="+prefix+" lineType="+lineType+"*");
        CheckControl.errorLimit=99;
        AverageReport ar=new AverageReport(dpsV,entryName,prefix,0);
        String str=ar.getReport();
        //System.out.println(" size="+dpsV.size()+" str="+str);
        System.out.println(" avg0="+ar.getAverage().value()+" "+ar.adoptedValStr());
        
        
        CheckControl.errorLimit=35;       
        AverageReport ar1=new AverageReport(dpsV,entryName,prefix,0);
        System.out.println(" avg1="+ar1.getAverage().value()+" "+ar1.adoptedValStr());
    }
    
    @SuppressWarnings("unused")
	public static void test0() throws Exception{
    	String s=""+
    			" 76BR cG $E|g=795.1 {I4}, I|g=0.75 {I8} (1973Pa02)\n"
    		  + " 76BR cG $E|g=796.5 {I5}, I|g=1.20 {I34} (1973Lo07)\n"
    			
    			;
    	
    	
    	String s1=" 65GE cL T$weighted mean of 31.2 s {I7} (1973Jo12), 30.0 s \n"
    			+ " 65GE2cL {I12} (1974Ro16), 30.8 s {I10} (1976Ha29,1981Ha44), and                \n"
    			+ " 65GE3cL 30.8 s {I7} (2000Gi11).    ";
 
    	
    	//s=s1;

        DataInComment data=Util.parseDatapointsInComments(s);
        Vector<DataPoint> dpsV=data.dpsV();
    	
     
    	String lineType="cX";
        String entryName="X";
        String NUCID="xxxxx";
        if(!data.recordType().isEmpty())
            lineType="c"+data.recordType();
        if(!data.NUCID().isEmpty()) 
            NUCID=data.NUCID();
        if(!data.entryType().isEmpty())
            entryName=data.entryType();
        
        String prefix=Str.makeENSDFLinePrefix(NUCID, lineType);
        
        //System.out.println(" prefix="+prefix+" lineType="+lineType+"*");
        CheckControl.errorLimit=99;
        AverageReport ar=new AverageReport(dpsV,entryName,prefix,0);
        String str=ar.getReport();
        //System.out.println(" size="+dpsV.size()+" str="+str);
        System.out.println(" avg0="+ar.getAverage().value()+" "+ar.adoptedValStr());
        
        
        CheckControl.errorLimit=35;       
        AverageReport ar1=new AverageReport(dpsV,entryName,prefix,0);
        System.out.println(" avg1="+ar1.getAverage().value()+" "+ar1.adoptedValStr());
    }
    
    public void test1() throws Exception{
    	String s=""+
    			" 76BR cG $E|g=795.8 {I4}, I|g=0.75 {I8} (1973Pa02)\n"
    			+ " 76BR cG $E|g=796.5 {I5}, I|g=1.20 {I34} (1973Lo07)\n"
    			
    			;
    	
    	
    	String s1=" 65GE cL T$weighted mean of 31.2 s {I7} (1973Jo12), 30.0 s \n"
    			+ " 65GE2cL {I12} (1974Ro16), 30.8 s {I10} (1976Ha29,1981Ha44), and                \n"
    			+ " 65GE3cL 30.8 s {I7} (2000Gi11).    ";
 
    	
    	s=s1;
    	String egLine="",riLine="";
    	String[] temp=s.split("[\\n]+");
    	String prefix=temp[0].substring(0,9);
    	
    	egLine=prefix+"E$weighted average of ";
    	riLine=prefix+"RI$weighted average of ";
    	
    	//System.out.println(prefix+"#");
    	
    	int n1=-1,n2=-1,n3=-1;
    	String line="",ref="";
    	for(int i=0;i<temp.length;i++) {
    		line=temp[i];
    		
    		//System.out.println(line);
    		
    		n1=line.indexOf("(");
    		n2=line.indexOf(")");
    		ref=line.substring(n1,n2+1);
    		
    		line=line.substring(9,n1).trim();
    		
    		n1=line.indexOf("E|g=");
    		n2=line.indexOf(",");
    		n3=line.indexOf("I|g=");
    		if(n2<0)
    			n2=line.length();
    		
    		if(n1>=0) {
    			egLine+=line.substring(n1+4,n2).trim()+" "+ref;
    			egLine=egLine.trim()+", ";
    		}
    		
    		if(n3>=0) {
    			riLine+=line.substring(n3+4).trim()+" "+ref;
    			riLine=riLine.trim()+", ";
    		}
    		
    		//System.out.println(temp[i]);
    	}
    	
    	egLine=egLine.trim();
    	riLine=riLine.trim();
    	
    	if(egLine.endsWith(","))
    		egLine=egLine.substring(0,egLine.length()-1).trim();
    	if(riLine.endsWith(","))
    		riLine=riLine.substring(0,riLine.length()-1).trim();
    	
    	
        AverageReport ar=null;
        String report="";
    
        s="";
        DataInComment data=Util.parseDatapointsInComments(egLine);
        Vector<DataPoint> dpsV=data.dpsV();

        
        //System.out.println(" prefix="+prefix+" lineType="+lineType+"*");

        ar=new AverageReport(dpsV,"E",prefix,0);
        report=ar.getReport();       
        s+=ar.getAdoptedComment();
        System.out.println(report);
        
        data=Util.parseDatapointsInComments(riLine);
        dpsV=data.dpsV();
        ar=new AverageReport(dpsV,"RI",prefix,0);
        report=ar.getReport();
        if(s.length()>0)
        	s+="\n"+ar.getAdoptedComment();
        else
        	s=ar.getAdoptedComment();
        
        System.out.println(report);
        
        System.out.println("\n"+s);
        
    	Toolkit.getDefaultToolkit()
        .getSystemClipboard()
        .setContents(
                new StringSelection(s),
                null
        );
    }
}
