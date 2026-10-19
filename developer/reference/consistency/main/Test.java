package consistency.main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Vector;

import consistency.base.CheckControl;
import consistency.base.RecordGroup;
import consistency.base.Util;
import ensdfparser.calc.Average;
import ensdfparser.calc.DataPoint;
import ensdfparser.check.TransferInfo;
import ensdfparser.ensdf.Comment;
import ensdfparser.ensdf.FindValue;
import ensdfparser.ensdf.Gamma;
import ensdfparser.ensdf.Level;
import ensdfparser.ensdf.Nucleus;
import ensdfparser.ensdf.SDS2XDX;
import ensdfparser.ensdf.XDX2SDS;
import ensdfparser.nds.ensdf.EnsdfFieldInterfaces;
import ensdfparser.nds.ensdf.EnsdfTableData;
import ensdfparser.nds.ensdf.EnsdfUtil;
import ensdfparser.nds.latex.Translator;
import ensdfparser.nds.util.Str;
import keynocheck.main.Setup;

@SuppressWarnings("unused")
public class Test {
    public static void main(String[] args)throws Exception{
    	System.out.println(EnsdfUtil.isLargeDJ("(LE 3)","(3)-", 2));
    	System.out.println(EnsdfUtil.isLargeDJ("(1-,2,3+)","(4)-", 2));
    	
    	//Vector<String> expectedMULsV=EnsdfUtil.findExpectedMULs("5", "4+");
    	//for(String s:expectedMULsV)
    	//	System.out.println(" MUL="+s);
    	
    	//Nucleus nuc=new Nucleus("13");
    	//System.out.println(" A="+nuc.A()+" Z="+nuc.Z()+" En="+nuc.En());
    	
    	//int d=2;
    	//System.out.println(3/ 2d);
    	//XDX2SDS xs=new XDX2SDS(0.0,143.47,0.0);
    	//System.out.println(xs.S()+" "+xs.dsu()+" "+xs.dsl()+" isLimit="+xs.isLimits()+" sl="+xs.sl()+" su="+xs.su());
    	
        //SDS2XDX sx1=new SDS2XDX("0","");
        //SDS2XDX sx2=new SDS2XDX("100","10");
        //sx1=sx1.subtract(sx2);
        //sx1=sx1.multiply(-1);
        //sx1=sx1.add(sx2);
        //sx1=sx1.multiply("0.83","4");
        //System.out.println(" s="+sx1.s()+" ds="+sx1.ds()+" dx="+sx1.dx());

    	//System.out.println(" "+Str.makeENSDFLinePrefix(" 32S ","DL"));
    	//String line=" R{-0}=1.5639 {I39}, FROM INTERPOLATION OF R{-0}({+164}W)=1.5642 {I59} AND R{-0}({+166}OS)=1.5636 {I19} FROM 2020SI16";
    	//String line="$The nuclear radius parameter r{-0}({+164}W)=1.4977 {I57}";
 	    //FindValue fv=new FindValue();
	    //fv.findValueInLine(line.toUpperCase(),"R{-0}");
	    //System.out.println(" line="+line+" text="+fv.txt());
	    //System.out.println("     val="+fv.s()+" "+fv.ds());
	    //SDS2XDX.smallError=0.05;
    	//SDS2XDX M=new SDS2XDX("5.20","10");//return unit amu*1E6
    	//M.setErrorLimit(99);
    	//M.setSmallError(0.05);
    	//SDS2XDX s1=new SDS2XDX("0.160","12");
    	//Control.errorLimit=35;
    	//SDS2XDX M=new SDS2XDX();
    	//M.setValues(2.079081240542608, 5522.867191576278, 2.088393521586303);
    	//M=M.divided(s1);
    	//System.out.println(" S="+M.S()+" "+M.ds()+" X="+M.X()+" "+M.dx()+" dxu="+M.dxu()+" dxl="+M.dxl()+" smallerror="+s1.getSmallError());
    	//System.out.println(" S="+M.S()+" "+M.ds()+" X="+M.X()+" "+M.dx()+" dxu="+M.dxu()+" dxl="+M.dxl());

    	//M=M.divided(1E6);
    	//SDS2XDX M=new SDS2XDX(""+(Z+N),"");
    	//SDS2XDX m=new SDS2XDX("4.00260325413","1");
    	//m=m.divided(1E6);
    	
    	/*
    	SDS2XDX Mr=null;
    	if(M.x()>0 && M.x()>0) {
    		SDS2XDX sx1=M.add(m);
    		SDS2XDX sx2=M.multiply(m);
    		Mr=sx2.divided(sx1);
    		//Mr=Mr.divided(1E6);
    		System.out.println(" sx2="+sx2.x()+" "+sx2.dx()+" sx1="+sx1.x()+" "+sx1.dx());
    	}
    	System.out.println("M.x="+M.x()+" "+M.dx()+" m.x="+m.x()+" "+m.dx()+" Mr.x="+Mr.x()+" "+Mr.dx());
        */
    	
    	/*
    	double x=0.011994604,dxu=0.011449713, dxl=0.011994602;
    	XDX2SDS xs=new XDX2SDS();
    	xs.setErrorLimit(99);
    	xs.setNsig(2);
    	xs.setValues(x,dxu,dxl);
    	
        
		double xu=xs.x()+xs.dxu();
		double xl=xs.x()-xs.dxl();
		double r=0;
		if(xl>0)
			r=xu/xl;

  
        System.out.println(" x="+x+" dxu="+dxu+" dxl="+dxl+" xl="+xl+" xu="+xu+"  xs.sl="+xs.sl()
        +"  xs.su="+xs.su()+" s="+xs.s()+" ss="+xs.ss()+" ds="+xs.ds()+" dsl="+xs.dsl()+" dsu="+xs.dsu()+" isLimit="+xs.isLimits()+"isSym="+xs.hasSymmetrized());
		System.out.println("    xs.x="+xs.x()+" xs.dxu="+xs.dxu()+" xs.dxl="+xs.dxl());
		*/
    	//Translator.init();
    	
    	//String s=Translator.process("238U(N,FG)");
    	//System.out.println(s);
    	
    	//Vector<String> JSV=EnsdfUtil.findJPIsFromL("2+", 2);
    	//for(String s:JSV)
    	//	System.out.println(s);
    	
    	//ensdf.ENSDF ens=new ensdf.ENSDF();
    	//ens.setValues(Str.readFile(new java.io.File("/Users/chenj/work/evaluation/ENSDF/check/check.ens")));
    	//ens.setValues(Str.readFile(new java.io.File("H:\\work\\evaluation\\ENSDF\\check\\check.ens")));
    	//Level lev1=ens.levelAt(1);
    	//Level lev2=ens.levelAt(2);
		//System.out.println("NUCID="+ens.nucleus().nameENSDF()+" DSID="+ens.DSId0()+" Lev1="+lev1.ES()+"  jpi="+lev1.JPiS()+" "+EnsdfUtil.isComparableEnergyEntry(lev1, lev2));
		//System.out.println("NUCID="+ens.nucleus().nameENSDF()+" DSID="+ens.DSId0()+" Lev2="+lev2.ES()+"  jpi="+lev2.JPiS()+" "+EnsdfUtil.isComparableEnergyEntry(lev1, lev2));
    	//ensdf.Alpha a=ens.levelAt(0).alphaAt(0);
    	//System.out.println(" EA="+a.ES());
    	
    	//ensdf.ContRecord cr=a.contRecordsVMap().get("E").get(0);
		//System.out.println(cr.txt()+" "+cr.ds()+" es="+a.ES()+" des="+a.DES());
		
		//cr=ens.norm().contRecordsVMap().get("BR").get(0);
		//System.out.println(cr.txt()+" cr.ds="+cr.ds()+" brs="+ens.norm().BRS()+" des="+ens.norm().DBRS());
		
    	//String line="Ice(K)=0.078 {I7}                                                     ";
    	//FindValue fv=new FindValue();
    	//fv.findValueInLine(line,"Ice(K)");
	    //System.out.println(" "+fv.name()+" "+fv.txt()+" v="+fv.v()+" s="+fv.s()+"  ds="+fv.ds()+"  x="+fv.x()+"  dx="+fv.dx()+" symbol="+fv.symbol()+" "+fv.canGetX()+" ");
	    
    	//test8();
    	//System.out.println(" length="+"123\n".length());
    	//countENSDF();
        
    	//SDS2XDX sx1=new SDS2XDX();
    	//sx1.setValues("1.6",">");
    	//System.out.println(" sx1.s="+sx1.s()+" "+sx1.ds()+" sx1.x="+sx1.x()+" "+sx1.dx());
    	
        //SDS2XDX sx1=new SDS2XDX("1.37","");             
        //sx1.setErrorLimit(30);
        //SDS2XDX sx2=new SDS2XDX("1.48","");             
        //sx2.setErrorLimit(30);
        
        //System.out.println(" canGetDX: "+sx1.canGetDX()+" "+sx2.canGetDXS());
        //sx1=sx1.divided(sx2);
        //System.out.println(" sx1.s="+sx1.s()+" "+sx1.ds()+" sx1.x="+sx1.x()+" "+sx1.dx());
        
        //s2x=SDS2XDX.checkUncertainty("942.748","347",1, 30);
        //s2x=s2x.add("-69.0","1");
        
        //System.out.println(" "+s2x.s()+"  "+s2x.ds());
       
        //XDX2SDS x2s=new XDX2SDS(0.31472,0.12080,0.09072,9999);             
        //System.out.println(" "+x2s.s()+"  "+x2s.dsu()+" "+x2s.dsl());
        
        //x2s=new XDX2SDS(0.31468257451383275,0.12076822742189727,0.09068926526155749,99999999); 
        //System.out.println(" "+x2s.s()+"  "+x2s.dsu()+" "+x2s.dsl());
    	
        //XDX2SDS xs=new XDX2SDS();
        
        //xs.setErrorLimit(25);
        //xs.setNsig(2);
        //xs.setValues(11.75136,1.066795,11.75136);
        
        
        //debug
        //System.out.println("  xs.sl="+xs.sl()+"  xs.su="+xs.su()+" s="+xs.s()+" ss="+xs.ss()+" ds="+xs.ds()+" dsl="+xs.dsl()+" dsu="+xs.dsu()+" isLimit="+xs.isLimits());
        
        //String s=Str.removeExtraZerosAfterDot("13.0",3);
        //String s=Str.roundByNsig("13.0",3);
        //System.out.println(s);
        
        /*
    	for(int i=0;i<1;i++) {
    		//float e=(i+1)*500;
    		//float de=e*0.001f;
    		
    		float e=9840;
    		float de=2.0f;
    		System.out.println(" e="+e+"  de="+de+" adjusted de="+EnsdfUtil.findUncertaintyForComparison(e, de));
    	}
       
        Level l=new Level();
        Vector<String> v=new Vector<String>();
        v.add(" 31CL  L 355.7E3+G   11                                                             ");
        l.setValues(v,0);
        System.out.println(l.NNES()+"  "+l.NES());
         */
        
        
        //String[] temp="Compiled by S. Geraedts and B. Singh (McMaster) Nov 28, 2007".split("[;,:]+|\\)[\\s]+");
        //String[] temp="|g sequence based on 1-, 2-,  test1;  TO  test2".split("[,\\s]+|TO[\\s]+");
        //String[] temp="|g sequence based on 1-,2-,  test1;  ".split("[\\s]+");
        //String[] temp="  test2".split("[\\s]+");
		//String[] temp="894.1 {I5} (2007Ma04), 895 {I1} (1993Kl02), 894.7 {I12} (1984Gu19) and 895.0 {I10} (2008Tr04)".split("[,]+|[,\\s]+and[\\s]+");
		//String[] temp="+12-13".split("[+-]+");
        /*
        String line="transition masked by strong background line resulting "
                + "from (n,n'|g) on germanium (29kk,3ddd); E|g is from Adopted Gammas"
                + "|g from I|g(256.2|g) and adopted branching from 434.4 level.";
        
        String[] temp=Str.specialSplit(line, "[.,;]");
        //String[] temp=line.split("[,;]");
        System.out.println("  len="+temp.length);
        for(String s:temp)
            System.out.println(" s="+s);
        */
        
        /*
    	Vector<String> lines=new Vector<String>();
        //lines.add("167ER cL T$transition masked by strong background line resulting");                
        //lines.add("167ER2cL from (n,n'|g) on germanium; 5/2+ fs is from Adopted Levels,");                
        //lines.add("167ER3cL I|g from I|g(256.2|g) and adopted branching from 434.4 level.");      
        
        lines.add("167LU cL E$could correspond to the 5142.3,(45/2-) level in Adopted Levels");
        
        Translator.init();
        Comment c=new Comment();
        c.setValues(lines);
        String s=extractAdoptedValueInComment(c);
        System.out.println("####"+s);
        */
    	
		/*
        ame.AMERun ameRun=new ame.AMERun();
        ameRun.loadAME2020();
        ame.AMETable ameTable=ameRun.getAME();
        ame.AMEEntry entry=ameTable.getEntry("167TM");
        System.out.println(" "+entry.sn_s()+"  "+entry.dsn_s());
       */
    	/*
		String dsid0="58NI(54FE,2P3NG),54FE(58NI,2P3NG):XUNDL-5";
		String dsid1="";
		String delims=" :)";

		if(dsid0.length()>30) {
            System.out.println("test1");
		    Vector<String> lines=Str.wrapDSID(dsid0,30);
		    System.out.println("test2");
		    for(String s:lines)
		    	System.out.println(s);
		    
		    dsid0=lines.get(0);
		    if(lines.size()>1)
		        dsid1=lines.get(1);
		}
		*/
    	
    	/*
    	Translator.init();
    	String line=" 31F 2 L %B-5N=?$%B-6N=?";
        Comment c=new Comment();
        c.setBody(line,true);
        System.out.println(" raw body: "+c.rawBody());
        System.out.println(" translated: "+c.getTranslated());
        */
    	
    	//test8();
    	
    	
		//String str="167YB2 G K:L2:L3=9 2:AP 0.08: LT 0.08 (1975VyZY) $ EKC=0.86 23";
        //String str="$|d(Q/D)=-0.26 {I+70-90} from |g(|q) (1981Kr08); anisotropy excludes J(431)=5/2 based on magnitude "
        //		+ "of |d required if |DJ=2 (1981Kr08). |a(K)exp=0.007 {I2} for doubly-placed |g.";
        
        //String str="K/M2=263 GT (1971Ab04)";
        //String str="BE2W=(9.3 8)$BM3W=(1.9E+4 +52-19) ";
		//FindValue fv=new FindValue();
		//fv.findValueInLine(str, "BE2W");
    	//System.out.println("name="+fv.name()+" txt="+fv.txt()+" symbol="+fv.symbol()+" v="+fv.v()+" s="+fv.s()+" ds="+fv.ds()+" x="+fv.x()+" dx="+fv.dx()+"  canGetV="+fv.canGetV()+" canGetX="+fv.canGetX()+" canGetDX="+fv.canGetDX());

    	//System.out.println(EnsdfUtil.toStandardOP("LT"));
    	//Vector<Integer> djs=EnsdfUtil.vectorDeltaJ("(3+)", "2+");
	    //for(Integer i:djs)
		//	System.out.println(i.intValue()+" "+djs.contains(2));

    	
    	/*
        Vector<FindValue> fvs=EnsdfUtil.findDataEntriesInLine(str);
        System.out.println(str);
		for(FindValue fv:fvs) {
			String name=fv.name();
			String tempRef=fv.ref().toUpperCase().trim();
			
			System.out.println("name="+name+" fv txt="+fv.txt()+" s="+fv.s()+" ds="+fv.ds());
		}
	    */
        //SDS2XDX sx1=new SDS2XDX("0.008","");             
        //sx1.setErrorLimit(99);
        //SDS2XDX sx2=new SDS2XDX("1.00","0");
        //sx1=sx1.multiply(sx2);

        //sx1=sx2.multiply(sx1);
        
        //System.out.println(" sx1.s="+sx1.s()+" ds="+sx1.ds()+" sx1.x="+sx1.x()+" dx="+sx1.dx()+" dxu="+sx1.dxu()+" dsl="+sx1.dxl());
        //String[] a="+4-2".split("[+-]+");
        //Str.splitString("+4-2", "+-").toArray(a);
        
        //for(int i=0;i<a.length;i++)
        //	System.out.println("i="+i+"  "+a[i]);
        
        //Vector<String> expectedMULs=EnsdfUtil.findExpectedMULs("7/2+","3/2-");
        //Vector<String> expectedMULs=EnsdfUtil.findExpectedMULs(3.5f, 1.5f, 1, 1);
        //for(String s:expectedMULs)
        //	System.out.println(s);
    	//findIndexCombinations0();
    	//System.out.println("**************");
    	//findIndexCombinations1();
    	
    	//SDS2XDX s2x=new SDS2XDX("1000","GT");
    	//s2x=s2x.multiply("-0.0331", "24");
    	//System.out.println(s2x.x()+"  "+s2x.s()+" "+s2x.dx()+"  "+s2x.dxu());
    	
    	//System.out.println(Str.roundByNsig(".0991974",1));
    	//System.out.println(EnsdfUtil.isCloseJPI("3/2-", "13/2-)", 1));
        
        //String s=Str.roundByNsig(Double.toString(7.6643256),3);
        //System.out.println(s);

		/*
		SDS2XDX sx1=new SDS2XDX("-21910","600");
    	sx1=sx1.add(42190,300);
    	//sx1=sx1.multiply("1.00", "10");
    	System.out.println(" sx1: "+sx1.s()+" "+sx1.ds());
    	System.out.println(" canGetDX="+sx1.canGetDX());
    
    	
    	String vs="6187.3642578125";
		System.out.println("1 vs="+vs);
		
		int p=vs.indexOf(".");

		String s1="",s2="";
		//s1=Str.roundToNDigitsAfterDot(vs,p+4);
		s2=Str.roundUpByNDigits(vs, 2);

  		System.out.println("vs="+s1+" "+s2);
  	   
    	
    	StringBuilder s1=new StringBuilder("s1");
    	StringBuilder s2=new StringBuilder("s2");
    	s1.append(s2);
     	System.out.println("1 s1="+s1+" s2="+s2);
     	
    	s2.setLength(0);
    	
    	System.out.println("2 s1="+s1+" s2="+s2);
    	 */
    	
    	//String line=" 44TI  E             44.0    50            3.43   5                             ";
    	//line=EnsdfUtil.replaceIEOfECBPLine(line, "0.0411", "47");
    	//System.out.println(line);

    	
    	//XDX2SDS xs=new XDX2SDS();
    	
    	/*
    	xs.setErrorLimit(35);
    	xs.setNsig(2);
    	xs.setValues(60.0,3.0129486295,2.007260253);
    	xs.removeEndZeros();
    	System.out.println(xs.s()+" "+xs.ds()+"  dsl="+xs.dsl()+"  dsu="+xs.dsu());
        */
    	
    	/*
    	xs=new XDX2SDS();
        xs.setErrorLimit(999);
        xs.setNsig(2);
    	xs.setValues(0.126,0.265,0.005);
    	xs.removeEndZeros();
        System.out.println(xs.s()+" "+xs.ds()+"  dsl="+xs.dsl()+"  dsu="+xs.dsu()+" isLimit="+xs.isLimits());
        */
        
    	//System.out.println(" x="+xs.x()+" dxu="+xs.dxu()+" dxl="+xs.dxl()+"  xs.sl="+xs.sl()
        //+"  xs.su="+xs.su()+" s="+xs.s()+" ss="+xs.ss()+" ds="+xs.ds()+" dsl="+xs.dsl()+" dsu="+xs.dsu()+" isLimit="+xs.isLimits());
        
    	//SDS2XDX sx=new SDS2XDX("14949.81090","0.00008");
    	//System.out.println(" x="+sx.x()+" dx="+sx.dx()+" s="+sx.s()+" ds="+sx.ds());
    	
    	//Translator.init();
    	//String s=" 44TI cG $B(M1)(W.u.)=0.0031 {I+11-10} if pure M1, B(E2)(W.u.)=2.7 {I+10-8} if pure E2 (M1)(W.u.)=0.0031 {I+11-10} if pure M1, B(E2)(W.u.)=2.7 {I+10-8} if pure E2 safad";
    	//s=EnsdfUtil.wrapENSDFLines(s);
    	//System.out.println(s);
        
        //test3();
    	
    	
    	//Translator.init();
    	
    	//ensdf.ENSDF ens=new ensdf.ENSDF();
    	//ens.setValues(Str.readFile(new java.io.File("/Users/chenj/work/evaluation/ENSDF/check/check.ens")));
    	//ens.setValues(Str.readFile(new java.io.File("H:\\work\\evaluation\\ENSDF\\check\\check.ens")));
    	
    	/*
		SDS2XDX sx1=new SDS2XDX("3","1");
		sx1.setErrorLimit(35);
    	sx1=sx1.multiply("0.10000", "");
    	System.out.println(" sx1: "+sx1.s()+" "+sx1.ds());
    	
    	sx1=new SDS2XDX("12","3");
    	sx1.setErrorLimit(35);
    	sx1=sx1.multiply("0.10000", "");
    	System.out.println(" sx1: "+sx1.s()+" "+sx1.ds());
        */

    	//test110();
    	
    }
	static void test110() {
		//String str="167YB cG $K:L1:L2:L3=10 {I3}:1.0:9.0:7.7 (1975VyZY)";
		
		
		//String str="167YB2 G K:L1:L2:L3=57 10:8.44:0.8:LT 0.1 (1975VyZY)";

		//String str="167YB2 G K:L1:L2:L3=5.4 10:0.77:0.21: LT 0.08 (1975VyZY)$ EKC=0.25 13";
		//String str="167YB2 G K:L2:L3=9 2:AP 0.08: LT 0.08 (1975VyZY) $ EKC=0.86 23";
		//String str="|a(K)exp=0.36 {I2}. K/L=3.9 {I2}, L/M=2.7 {I1} (1972Vy08). K/L=4.43 (1968Wi21); L1/L2=1.77 {I4}, L2/L3=2/11 {I6} (1987BaZB). L1/L2/L3=135 {I15}/70 {I10}/54 {I7} (1972Vy08,also 1971GoZS)";
		String str="|a(K)exp=0.0292. K/L=4.3 {I2}, L/M=3.4 {I5} (1972Vy08).";
		//String[] out=Str.specialSplitAllDelims(str,"[\\s]+");
		//for(int i=0;i<out.length;i++)
		//System.out.println("i ="+i+"  "+out[i]);
		
		Vector<FindValue> fvs=EnsdfUtil.findDataEntriesInLine(str);
		System.out.println(" fvs size="+fvs.size());
		for(FindValue fv:fvs)
			System.out.println("name="+fv.name()+" txt="+fv.txt()+" symbol="+fv.symbol()+" v="+fv.v()+" s="+fv.s()+" ds="+fv.ds()+" ref="+fv.ref()+" x="+fv.x()+" dx="+fv.dx()+"  canGetV="+fv.canGetV()+" canGetX="+fv.canGetX()+" canGetDX="+fv.canGetDX());
        
		//String s="L1/L2=1.2 3 (1972Vy08,also 1971GoZS)";
		//String shell="L1/L2";
		//FindValue fv1=new FindValue();
		//fv1.findValueInLine(s,shell);
		//System.out.println("    shell="+shell+" s="+s+" fv1.s="+fv1.s()+" ds="+fv1.ds()+"  text="+s+" ref="+fv1.ref());
		
		/*
		HashMap<String,Vector<FindValue>> shellCCMap=Util.findCCsInLine(str);
        for(String name:shellCCMap.keySet()) {
        	Vector<FindValue> fvs=shellCCMap.get(name);
        	for(FindValue fv:fvs)
        		System.out.println(" name="+name+" fv.name="+fv.name()+" txt="+fv.txt()+"  v="+fv.v()+"  s="+fv.s()+" ds="+fv.ds());
        }
        */
        		
	}
    static private void setTempIndexesVV(Vector<Vector<Integer>> indexesOfRecordsInGroupVV,Vector<Vector<Integer>> tempIndexesVV) {
    	tempIndexesVV.clear();
        for(int i=0;i<indexesOfRecordsInGroupVV.size();i++) {
        	Vector<Integer> tempIndexesV=new Vector<Integer>();
        	tempIndexesV.addAll(indexesOfRecordsInGroupVV.get(i));
        	tempIndexesVV.add(tempIndexesV);
        }
    }
    
    static private void resetTempIndexesVV(Vector<Vector<Integer>> indexesOfRecordsInGroupVV,Vector<Vector<Integer>> tempIndexesVV, int iGroup) {
    	tempIndexesVV.get(iGroup).clear();
    	tempIndexesVV.get(iGroup).addAll(indexesOfRecordsInGroupVV.get(iGroup));
    }
    
    static void findIndexCombinations1() {
    	Vector<Vector<Integer>> indexesOfRecordsInGroupVV=new Vector<Vector<Integer>>();
    	/*
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(90,91,92,93,94)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(91,92,93,94,95)));
        indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(92,93,94,95,96,97)));
        indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(93,94,95,96,97,98)));
        indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(94,95,96,97,98)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(94,95,96,97,98,99)));
        indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(95,96,97,98,99)));
        indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(97,98,99)));
        */
    	
    	/*
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(43)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(43)));
        indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(43,44)));
        indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(43,44)));
        indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(44)));
        */
    	/*
       	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(140,141,142,144)));
       	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(140,141,142,144)));
       	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(140,141,142,144)));
       	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(140,141,142,144)));
       	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(140,141,142,144)));
       	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(140,141,142,144)));
       	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(140,141,142,144)));
       	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(140,141,142,144)));
       	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(140,141,142,144)));
       	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(140,141,142,144)));
       	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(140,141,142,144)));
       	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(140,141,142,144)));
       	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(140,141,142,144)));
       	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(140,141,142,144)));
       	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(140,141,142,144)));
       	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(140,141,142,144)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(140,141,142,144)));
        indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(140,141,142,144)));
        indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(140,141,142,144)));
        indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(143,144)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(144,145,146)));
        indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(144,145,146)));
        */
    	
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(13706,13711,13716,13727)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(13706,13711,13716,13727,13732)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(13706,13711,13716,13727,13732)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(13706,13711,13716,13727,13732,13742)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(13711,13716,13727,13732,13742,13753)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(13727,13732,13742,13753)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(13732,13742,13753,13769)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(13753,13769,13779)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(13769,13779,13800)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(13779,13800,13805,13816)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(13800,13805,13816)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(13800,13805,13816,13836)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(13816,13836,13842,13852,13857)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(13836,13842,13852,13857,13863)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(13836,13842,13852,13857,13863,13873)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(13836,13842,13852,13857,13863,13873,13878)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(13842,13852,13857,13863,13873,13878)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(13852,13857,13863,13873,13878,13889)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(13857,13863,13873,13878,13889,13899)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(13863,13873,13878,13889)));
    	
    	Vector<String> processedCombinations=new Vector<String>();
    	Vector<Integer> indexesOfRecordsInGroupV=new Vector<Integer>();
    	
    	int nRecords=21;

    	int N=indexesOfRecordsInGroupVV.size();
    	int nGroups=N;
		float diff=-1,minSumDiff=10000;
		float sumDiff=0;
		int[] a=new int[N];//store selectedIndexes
		
		int offset=0;
		int[] indexOffset=new int[a.length];
		Arrays.fill(indexOffset, 0);

		boolean done=false;
		int maxNGood=0;
		int count=0;
        int goodCount=0,nTries=0;;
        
		HashMap<String,Integer> processedCombinationMap=new HashMap<String,Integer>();
        Vector<Vector<Integer>> tempIndexesVV=new Vector<Vector<Integer>>();
        
        for(int i=0;i<indexesOfRecordsInGroupVV.size();i++) {
        	Vector<Integer> tempIndexesV=new Vector<Integer>();
        	tempIndexesV.addAll(indexesOfRecordsInGroupVV.get(i));
        	tempIndexesVV.add(tempIndexesV);
        }
        
		while(true) {
			//System.out.println("hello1"+" subGroup size="+subGroupsV.size());
			
			//get a good combination of index of a selected record out of each sub group
			int nGood=0;
			int prevIndex=-1;//previous positive index
			
			for(int i=0;i<N;i++) {
				indexesOfRecordsInGroupV=tempIndexesVV.get(i);
				int nIndexes=indexesOfRecordsInGroupV.size();
				
				//if(Math.abs(allRecordsV.get(0).EF()-12980)<10) {
				//if(Math.abs(allRecordsV.get(0).EF()-10145)<100) {
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
                		    iNextGroup=k;
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
                                	int iGroup=j;
                                	resetTempIndexesVV(indexesOfRecordsInGroupVV,tempIndexesVV,iGroup);
                                	
                                    indexOffset[j]=0;
                                    a[j]=-1;                                   
                                    
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
                    			iNextGroup=k;
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
				//if(Math.abs(allRecordsV.get(0).EF()-10145)<100) {
				//    System.out.println("2 size="+subGroupsV.size()+" i="+i+" a[i]="+a[i]+" prevIndex="+prevIndex+" nIndexes="+nIndexes+" indexOffset="+indexOffset[i]+" nGood="+nGood);
				//}
				
			}
			

			
			if(done)
				break;
			
			indexOffset[0]++;
			
			if(nGood==0)
				break;
			       
			count++;
			
            String s="";
            for(int i=0;i<a.length;i++) {
                s+=a[i]+"-";
            } 
            
            /*
            //debug
            //if(a[0]==92 && a[7]==99 && a[3]==95) {
                for(int i=0;i<a.length;i++) {
                    System.out.print(" "+a[i]);
                }  
                System.out.println("   %%11111");
            //}
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
                    //  break;
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
            //if(a[0]==92 && a[7]==99 && a[3]==95) {
                for(int i=0;i<a.length;i++) {
                    System.out.print(" "+a[i]);
                }  
                System.out.println("   #### count="+count);	
            //}
            */
                   
            if(nGood>=maxNGood) {
                if(nGood>maxNGood)
                    minSumDiff=10000;
                
                maxNGood=nGood;
            }
            
            
            if(nGroups<=nRecords) {
                if(nGood<nGroups/3)
                    break;
                else 
                    if(nGood<nGroups-1) 
                    continue;
            }else {
                if(nGood<nRecords/3)
                    break;
                else 
                if(nGood<nRecords-1) 
                    continue;   
            }
        
            goodCount++;
			
            
            /*
			//if(a[0]==92 && a[7]==99 && a[3]==95) {
				for(int i=0;i<a.length;i++) {
					System.out.print(" "+a[i]);
				}  
				System.out.println("  n="+n);
			//}
            */

			
		}
		
		System.out.println(" Total count="+count+" good count="+goodCount);
		System.out.println("  nGroups="+nGroups+" nRecords="+nRecords);
    }
    
    static void findIndexCombinations0() {
    	Vector<Vector<Integer>> indexesOfRecordsInGroupVV=new Vector<Vector<Integer>>();
    	/*
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(45)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(45,46)));
        indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(45,46)));
        indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(45,46,47)));
        indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(46,47)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(47)));
        indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(47)));
        //indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(44)));
        */
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(43)));
    	indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(43)));
        indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(43,44)));
        indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(43,44)));
        indexesOfRecordsInGroupVV.add(new Vector<Integer>(Arrays.asList(44)));
        
    	Vector<String> processed=new Vector<String>();
    	
    	int nRecords=2;

    	int N=indexesOfRecordsInGroupVV.size();
    	int nGroups=N;
		float diff=-1,minSumDiff=10000;
		float sumDiff=0;
		int[] a=new int[N];//store selectedIndexes
		
		int offset=0;
		int[] indexOffset=new int[a.length];
		Arrays.fill(indexOffset, 0);

		boolean done=false;
		int maxNGood=0;
		int count=0, goodCount=0;
		while(true) {
			//System.out.println("hello1"+" subGroup size="+subGroupsV.size());
			
			//get a good combination of index of a selected record for each sub group
			int nGood=0;
			int prevIndex=-1;//previous positive index
			for(int i=0;i<N;i++) {
				Vector<Integer> indexesOfRecordsInGroupV=indexesOfRecordsInGroupVV.get(i);
				int nIndexes=indexesOfRecordsInGroupV.size();
				
				//System.out.println("1 size="+subGroupsV.size()+" i="+i+" a[i]="+a[i]+" prevIndex="+prevIndex+" nIndexes="+nIndexes+" indexOffset="+indexOffset[i]);
				
				if(indexOffset[i]<nIndexes) {
					a[i]=indexesOfRecordsInGroupV.get(indexOffset[i]).intValue();
					//if(a[i]<prevIndex)//allow a[i]=prevIndex, one record assigned to mutliple groups
					if(a[i]<=prevIndex)//do not allow a[i]=prevIndex, one record assigned to muttiple groups
						a[i]=-1;

				}else if (indexOffset[i]==nIndexes)
					a[i]=-1;
				else{
                	if(i<N-1) {
                		indexOffset[i+1]++;
                		
                		//reset a and offset for all preceding groups
                		for(int j=0;j<=i;j++) {
                			indexOffset[j]=0;
                		}
                		
                		i=-1;
                		prevIndex=-1;
                		nGood=0;
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
				//System.out.println("2 size="+subGroupsV.size()+" i="+i+" a[i]="+a[i]+" prevIndex="+prevIndex+" nIndexes="+nIndexes+" indexOffset="+indexOffset[i]);
			}
			if(done)
				break;
			
			if(nGood==0)
				break;
			
			String s="";
			for(int i=0;i<a.length;i++) {
				s+=a[i]+",";
			}           
			
			indexOffset[0]++;
			
			count++;
			if(!s.isEmpty() && !processed.contains(s))
				processed.add(s);
			else 
				continue;
			
			
			if(nGood>=maxNGood) {
				if(nGood>maxNGood)
					minSumDiff=10000;
				
				maxNGood=nGood;
			}else
				continue;
			
			
			if(nGroups<=nRecords) {
				if(nGood<nGroups-1) 
					continue;
			}else {
				if(nGood<nRecords-1) 
					continue;		
			}
			
			for(int i=0;i<a.length;i++) {
				System.out.print(" "+a[i]);
			}  
			System.out.println("");
			goodCount++;
		}
		
		System.out.println(" Total count="+count+" good count="+goodCount);
    }
    
    
    static String extractAdoptedValueInComment(Comment c) {
        String s=c.body().trim().toUpperCase();

        if(!s.contains("ADOPTED"))
            return "";
        
        //System.out.println(s);
        
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
            }else if(Str.containAny(s1,"0123456789")!=Str.containAny(s2, "0123456789")){
                s=extractNumericalValue(s1+" "+s2);
                
                System.out.println("s1="+s1+" s2="+s2+" s="+s);
                
                return s;
            }
            
        }

                    
        return "";
    }    
    static void test9()throws Exception {
        /*
        String s="1983AAAA,,2032";
        String [] a=s.split("[,]+");
        for(int i=0;i<a.length;i++)
            System.out.println(" i="+i+"  part="+a[i]);
        */
        XDX2SDS x2s=new XDX2SDS(Float.parseFloat("42482.1010"),Float.parseFloat("525.5484"),999);
        System.out.println(" s="+x2s.s()+" ds="+x2s.ds());
    }
    
    static void test8() throws Exception {
    	Translator.init();
    	
    	ensdfparser.ensdf.ENSDF ens=new ensdfparser.ensdf.ENSDF();
    	//ens.setValues(Str.readFile(new java.io.File("/Users/chenj/work/evaluation/ENSDF/check/check.ens")));
    	ens.setValues(Str.readFile(new java.io.File("H:\\work\\evaluation\\ENSDF\\check\\check.ens")));
    	
    	/*
    	//check.AverageValuesInComments avg=new check.AverageValuesInComments(ens);
    	TransferInfo tr=new TransferInfo(ens);
    	
    	Vector<String> jpV=tr.findLevelJPSFromLtransfer(ens.levelAt(0));
    	for(String s:jpV)
    	    System.out.println(s);
    	*/
    	
    	Vector<Gamma> gammasV=ens.unpGammas();
    	//for(Level l:ens.levelsV()) {
    	//	gammasV.addAll(l.GammasV());
    	//}
    	
    	gammasV=ens.gammasV();
    	EnsdfUtil.sortRecordsByEnergy(gammasV);
    	
    	float norm1=(float) (0.143/(0.85/18.0));//E=210 gamma
    	float norm2=(float) (1.01/(1.0/15.8));//E=140 gamma
		for(Gamma g:gammasV) {
			String ELS="";
			if(g.ILI()>0) {
				ELS=ens.levelAt(g.ILI()).ES();
			}
			FindValue fv=EnsdfUtil.findValueInCommentByName(g, "Ice(K)");
			String ri=g.RIS();
			String dri=g.DRIS();
			
			if(!fv.s().isEmpty() && !ri.isEmpty()) {

				
				SDS2XDX sx1=new SDS2XDX(fv.s(),fv.ds());
				SDS2XDX sx2=new SDS2XDX(ri,dri);
				SDS2XDX ick=null;
				if(g.flag().contains("j"))
					ick=sx1.divided(sx2).multiply(norm2);
				else
					ick=sx1.divided(sx2).multiply(norm1);
				
				String ck0=g.EKCS();
				String dck0=g.DEKCS();
				
				String rs="";
				try {
					float r=(float) (ick.X()/Float.parseFloat(ck0));
					rs=String.format("%.2f", r);
				}catch(Exception e) {}
				
				System.out.println(String.format(" level=%10s gamma=%10s      Ice(K)=%7s dIce(K)=%3s     RI=%7s  dRI=%3s     ECK=%6s  dECK=%3s    ECK0=%6s dECK0=%3s   ECK/ECK0=%4s",
						ELS,g.ES(),fv.s(),fv.ds(),ri,dri,ick.s(),ick.ds(),ck0,dck0,rs));
			}
		}
    }
    
    
    static void test7() throws Exception {
    	String s="(1+,2+,3+)+";
    	String[] a=EnsdfUtil.parseJPI(s);
    	//for(int i=0;i<a.length;i++)
    	//	System.out.println(a[i]);
    	System.out.println(EnsdfUtil.isEqualJPS("4+,3+","3+,4"));
    }
    static void test6() throws Exception {
    	//XDX2SDS s2x=new XDX2SDS(-0.015092789322137867,0.029422583157366126,0.029422583157366126,25);
    	//String out=String.format("%10s %2s",s2x.s(),s2x.ds());  	
    	//System.out.println(out);
    	//System.out.println(Math.round(-1.51));
    	
        System.out.println(Str.roundToNDigitsAfterDot("1234.5451",2,0));
        
        System.out.println(Math.round(123456.5));
        
		/*	 
    	SDS2XDX s2x=new SDS2XDX("190","+16-14");			 
		s2x.setErrorLimit(50);
			 
		//s2x=s2x.add((float)100,(float)0.00*100);
		//s2x=s2x.add(-5.035789489746094f,0.7510401010513306f);
		 
		s2x=s2x.multiply(0.0001001f);
		System.out.println(" s2x="+s2x.s()+" "+s2x.ds()+"  +"+s2x.dxu()+"-"+s2x.dxl());
		*/
    	
    	//String[] v="test  ; +16-14".split("[\\s,;]+");
    	//for(int i=0;i<v.length;i++)	System.out.println(" i="+i+"  "+v[i]);
    	
    	//EnsdfUtil.checkStarMarkerInXREF("/home/junchen/work/evaluation/ENSDF/check/check.ens","/home/junchen/work/evaluation/ENSDF/check/check_start.ens");
    	
    	/*
    	String[] jps=EnsdfUtil.parseJPI("(LT4)+");
    	for(int i=0;i<jps.length;i++)
    		System.out.println(" J="+jps[i]);
    
    	System.out.println(Str.isNumeric("3.2E3"));
    	System.out.println((int)Float.parseFloat("3.2E3"));
    	*/
    	
    	/*
    	double x=19;
    	double dxu=98;
    	double dxl=19;
    	XDX2SDS xs=new XDX2SDS();
    	xs.setErrorLimit(25);
    	xs.setNsig(2);
    	xs.setValues(x,dxu,dxl);
      	
    	
        //debug
        System.out.println(" x="+x+" dxu="+dxu+" dxl="+dxl+"  xs.sl="+xs.sl()
        +"  xs.su="+xs.su()+" s="+xs.s()+" ss="+xs.ss()+" ds="+xs.ds()+" dsl="+xs.dsl()+" dsu="+xs.dsu()+" isLimit="+xs.isLimits());
       
    	
		String val="160";
		String tempVal=val;
		String unc="+130-90";
		int errorLimit=25;
    	SDS2XDX s2x=new SDS2XDX(tempVal,unc);
    	SDS2XDX constant=new SDS2XDX("0.69315","1");
    	
    	System.out.println("#1 s2x.s="+s2x.s()+" s2x.ds="+s2x.ds()+" s2x.dxu="+s2x.dxu()+"  s2x.dxl="+s2x.dxl());
    	
    	s2x.setErrorLimit(99);
    	//s2x=s2x.multiply(-1);
    	s2x=s2x.divided(-1);
    	
    	System.out.println("#2 s2x.s="+s2x.s()+" s2x.ds="+s2x.ds()+" s2x.dxu="+s2x.dxu()+"  s2x.dxl="+s2x.dxl());
    	
    	XDX2SDS x2s=new XDX2SDS(s2x.x(),s2x.xUVf()-s2x.x(),s2x.x()-s2x.xLVf(),errorLimit);

    	int index=val.indexOf(tempVal);
    	int len=tempVal.length();
    	String s1=val.substring(0,index).trim();
    	String s2=val.substring(index+len).trim();
    	
    	System.out.println(" val="+val+" unc="+unc+"  tempVal="+tempVal+" x2s.s="+x2s.s()+" x2s.dsu="+x2s.dsu()+" x2s.dsl="+x2s.dsl());
    	System.out.println("s2x.s="+s2x.s()+" s2x.ds="+s2x.ds()+" s2x.dxu="+s2x.dxu()+"  s2x.dxl="+s2x.dxl()+" s2x.xUVf()-s2x.x()="+(s2x.xUVf()-s2x.x())+" s2x.x()-s2x.xLVf()="+(s2x.x()-s2x.xLVf()));
      */
    }

    static void test5() throws Exception {
        // creating vector type object 
        Vector<Integer> v 
            = new Vector<Integer>(); 
  
        // inserting elements into the vector 
        v.add(1); 
        v.add(2); 
        v.add(3); 
        v.add(4); 
        v.add(5); 
        v.add(6); 
  
        // printing vector before deleting element 
        System.out.println("Before deleting"); 
        System.out.println("Vector: " + v); 
        System.out.println("Size: " + v.size()); 
  
        System.out.println("\nAfter deleting"); 
  
        // trying to deleting object 3 
        v.removeElement(3); 
        //v.remove(Integer.valueOf(3)); 
  
        System.out.println("Vector: " + v); 
        System.out.println("Size: " + v.size()); 
    }
    
    static void test4() throws Exception {
    	SDS2XDX tiToGS=new SDS2XDX("100.0","");
    	
		SDS2XDX NR=tiToGS.divided(106.3523f,0.20f);
		
		System.out.println("ti="+tiToGS.s()+" dti="+tiToGS.ds()+" NR="+NR.s()+" DNR="+NR.ds());
    }
    
	static void test3() throws Exception{
    	Translator.init();
    	
    	ensdfparser.ensdf.ENSDF ens=new ensdfparser.ensdf.ENSDF();
    	ens.setValues(Str.readFile(new java.io.File("./test/test.ens")));
    	
    	ensdfparser.check.TransferInfo tr=new ensdfparser.check.TransferInfo(ens);
    	tr.printTest();
    	
    	for(Level lev:ens.levelsV()) {
        	Vector<String> jV=tr.findLevelJPSFromLtransfer(lev);
        	System.out.print("\nL="+lev.lS()+" j=");
        	
        	for(String s:jV)
        		System.out.print(s+",");
    	}
 
    	System.out.println("\n isTransfer="+tr.isTransfer()+" T="+ens.target().nameENSDF()+" B="+tr.id().beam+" Ejectile="+tr.id().ejectile);
        //Nucleus nuc1=new Nucleus(tr.id().beam);
        //Nucleus nuc2=new Nucleus(tr.id().ejectile);
        
        Nucleus nuc1=new Nucleus("3H");
        Nucleus nuc2=new Nucleus("4n");
        System.out.println(" A1="+nuc1.A()+" Z1="+nuc1.Z()+" A2="+nuc2.A()+" Z2="+nuc2.Z());
	}
	
	static void test2() {
		float e=0.123f;
		float de=0.02f;
		java.util.ArrayList<Float> list=new java.util.ArrayList<Float>();
		for(int i=0;i<100;i++) {
			float r=(float) Math.random();
			list.add(r);
		}
		
		Collections.sort(list);
		
		for(int i=0;i<list.size();i++) {
            float r=list.get(i);
			double unc=EnsdfUtil.findUncertaintyForComparison(e, de*r);
			
			String s=String.format("e=%-10f de=%10f de/e=%5.3f found de=%10f  ratio=%f", e,de*r,(de*r/e),unc,unc/(de*r));
			System.out.println(s);
		}
		
		System.out.println(EnsdfUtil.findUncertaintyForComparison(2001, 2));
	}
	
	static void test0() {
		ensdfparser.ensdf.SDS2XDX sx1=new ensdfparser.ensdf.SDS2XDX("9","LT");
		ensdfparser.ensdf.SDS2XDX sx2=new ensdfparser.ensdf.SDS2XDX("0.06211","1");
		
		sx1=sx1.multiply(sx2);
		System.out.println(" s="+sx1.s()+" ds="+sx1.ds());
	}
	
    static void test() throws Exception{
    	Translator.init();
    	
    	ensdfparser.ensdf.ENSDF ens=new ensdfparser.ensdf.ENSDF();
    	ens.setValues(Str.readFile(new java.io.File("./test/test.ens")));
    	ensdfparser.ensdf.Level lev=ens.levelAt(2);
    	
    	String ES=EnsdfFieldInterfaces.getS(lev,"ES");
    	double EF=EnsdfFieldInterfaces.getD(lev, "EF");
    	String JS=EnsdfFieldInterfaces.getS(lev,"J");
    	
    	System.out.println("   ES="+ES+"   EF="+EF+"  JS="+JS+"  test="+EnsdfFieldInterfaces.getD(lev,"T"));
    }
    
    static void test1() throws Exception{
		String msgText="";
		int n=2;
    	msgText="Relative uncertainties in HF and IA are inconsistent (for g.s.)\n";
		msgText+="  DHF/HF="+String.format("%."+n+"f%%\n", 0.0123*100);
		msgText+="  DIA/IA="+String.format("%."+n+"f%%\n", 0.0113*100);
    	//System.out.println(msgText);
    	
    	
        String[] temp="Compiled by S. Geraedts and B. Singh (McMaster): Nov 28, 2007".split("[.,:]+[\\s]+");
        //String[] temp="|g sequence based on 1-, 2-,  test1;  TO  test2".split("[,\\s]+|TO[\\s]+");
        //String[] temp="|g sequence based on 1-,2-,  test1;  ".split("[\\s]+");
        //String[] temp="  test2".split("[\\s]+");
        System.out.println("  len="+temp.length);
        for(String s:temp)
        	System.out.println(" s="+s);
        
        //System.out.println(temp[0]+"  $"+temp[1]+" size="+temp.length);
    }
    
    static void countENSDF() {
    	String filePath="./test/test.ens";
    	try {
			Util.countBlocks(filePath);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    static void test10()throws Exception{
        Setup.load();
        Translator.init();
        
        Average avg=new Average();
        avg.addDataPoint(new DataPoint(12.3,0.4,"p1"));
        avg.addDataPoint(new DataPoint(10.3,0.5,1.2,"p2"));
        avg.addDataPoint(new DataPoint(11.4,0.8,"p3"));
        
        //avg.calculate();
        //avg.debug();
        
        consistency.base.AverageReport report1=new consistency.base.AverageReport(avg.dataPointsV(),"name","test",0.02,"1");
        //System.out.println(report1.getReport());
        
        avg=report1.getAverage();
        System.out.println("  1: "+avg.value()+"   "+avg.intErrorM()+"  "+avg.intErrorP());
        
        consistency.base.AverageReport report2=new consistency.base.AverageReport(avg.dataPointsV(),"name","test",0.02,"2");
        //System.out.println(report2.getReport());
        
        avg=report2.getAverage();
        System.out.println("  2: "+avg.value()+"   "+avg.intErrorM()+"  "+avg.intErrorP());
        
        consistency.base.AverageReport report3=new consistency.base.AverageReport(avg.dataPointsV(),"name","test",0.02);
        //System.out.println(report2.getReport());
        
        avg=report3.getAverage();
        System.out.println("  3: "+avg.value()+"   "+avg.intErrorM()+"  "+avg.intErrorP());
    }
    
    static void test11(){
        SDS2XDX s1=new SDS2XDX("276.0","5");
        SDS2XDX s2=new SDS2XDX("0.01","");
  
        s1.setErrorLimit(99);
        s1=s1.multiply(s2);
        System.out.println(s1.s()+"  "+s1.ds());
        
        s1=new SDS2XDX("10","1");
        s2=new SDS2XDX("10","0");
  
        s1.setErrorLimit(99);
        s1=s1.divided(s2);
        System.out.println(s1.s()+"  "+s1.ds());
        
        /*
        float de,gap=1;
        float de1,r;
        for(float e=1;e<20000;e=e+gap){
            if(e<500){
                gap=1;
                de=0.05f;
            }else if(e<=1000){
                gap=2;
                de=0.1f;
            }else if(e<=2000){
                gap=5;
                de=0.2f;
            }else if(e<=5000){
                gap=10;
                de=0.5f;
            }else if(e<=10000){
                gap=50;
                de=1;
            }else{
                gap=100;
                de=100;
            }
            
            de1=EnsdfUtil.findUncertaintyForComparison(e, de);
            r=de1/de;
            System.out.println(String.format("   e=%10.2f  de=%5.2f   revised de=%6.2f   ratio=%5.2f", e,de,de1,r));
                
        }
        */
    }
    
    /*
     * values like,
     * 123.4
     * 123.4 US 12
     * 123.4 12
     */
    private static String extractNumericalValue(String s) {
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
        
        //System.out.println("s0="+s0+"     **** s="+s);
        
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
        System.out.println("s0="+s0+"     **** s="+s);
        
        String[] a=s.split("[,]+");
        if(a.length<=3 && a.length>0) {
            if(Str.isNumeric(a[0]) || a.length==1)
                return s;
       
        }
        
        return "";
    }
    
    
    private static String extractMULTValue(String s) {
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
        
}
