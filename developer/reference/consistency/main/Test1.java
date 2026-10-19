package consistency.main;

import java.util.Vector;

import ensdfparser.check.LOGFT;
import ensdfparser.check.SpinParityParser;
import ensdfparser.ensdf.Gamma;
import ensdfparser.ensdf.JPI;
import ensdfparser.ensdf.Level;
import ensdfparser.nds.latex.Translator;
import ensdfparser.nds.util.Str;

public class Test1 {

	@SuppressWarnings("unused")
	public static void main(String[] args) throws Exception {
		
		/*
		String js="3/2-";
		String ms="E2";
		Vector<JPI> tempV=SpinParityParser.findJPVFromGammaTo(js, ms,"1481",65,1E-9f);
		js=JPI.combineJPVtoStr(tempV);
		
		System.out.println(js);
		System.out.println();
		*/
		
		/*
		Vector<JPI> tempV1=SpinParityParser.findJPVFromGammaTo("5/2-", "M1+E2","366",65,1E-9);
		Vector<JPI> tempV2=SpinParityParser.findJPVFromGammaTo("1/2-", "","710",65,1E-9);
		
		System.out.println(JPI.combineJPVtoStr(tempV1));
		System.out.println(JPI.combineJPVtoStr(tempV2));
		
		tempV1=JPI.mergeJPV(tempV1,tempV2,true);
		//for(JPI jp:tempV1) System.out.println(jp.js());
		
		String js1=JPI.combineJPVtoStr(tempV1);
		
		System.out.println(js1);
		
		JPI jp1=new JPI("3/2-");
		JPI jp2=new JPI("(3/2)");
		JPI jp=JPI.makeBetterOneOfSameSpinValue(jp1,jp2,true);
		System.out.println(jp.js());
		*/
		
		//test1();
		
		double logft=8.1;
		int Z=59;
		Vector<String> LV=LOGFT.findDeltaJPSForLogft(8.1,Z,false,false,0.1,"");
		for(String LS:LV)
			System.out.println(LS);
		
		//FormatCheck check=new FormatCheck();
		//Comment c=check.makeComment("162YB cN NR,NT,BR  No normalization");
		//System.out.println(" c header="+c.head()+" body="+c.body());
		
		
		//test0();
		//Nucleus nuc=new Nucleus("7P");
		//System.out.println(" A="+nuc.A()+" Z="+nuc.Z()+" N="+nuc.N());
		
		//FormatCheck check=new FormatCheck();
		
		//Vector<EjectileComponent> out=check.parseEjectileComponents("NUCLIDE+'");
		//for(EjectileComponent e:out)
		//	System.out.println("size="+out.size()+" particle="+e.particle+" Mult="+e.mult+" hasPrime="+e.hasPrime);
		
		//String msg=check.checkMUL("M1,[E1+M2]");
		//System.out.println(msg);
		
		
		//FindValue fv=new FindValue();
		//fv.findValueInEntry("T=? (2013DD78)");
		//fv.findValueInEntry("%B-=(13 3)");
		//fv.findValueInEntry("XREF=-(AB)");
		
		//fv.findValueInEntry("12<BM1W<24");
		//fv.findValueInEntry("BM1W GT 24 LE 12");
		//fv.findValueInEntry("%B-N=8 2 (1995So03)");
		//System.out.println(" name="+fv.name()+" txt="+fv.txt()+" symbol="+fv.symbol()+" fv.bv="+fv.canGetV()+" bx="+fv.canGetX()+" bdx="+fv.canGetDX()+" s="+fv.s()+" ds="+fv.ds()
		//+"\n dsu="+fv.dsu()+" dsl="+fv.dsl()+" unit="+fv.units()+" x="+fv.x()+" dxu="+fv.dxu()+" dxl="+fv.dxl()+"  ref="+fv.ref()+" vNoSymbol="+fv.vNoSymbol()+" vs="+fv.v());
		//ContRecord cr=EnsdfUtil.makeContRecord("T=","2",false,false);
		//System.out.println(" "+cr.isEmpty()+" name="+cr.name()+" v="+cr.s()+" dv="+cr.ds()+" symbol="+cr.symbol()+" txt="+cr.txt());
		 
		//SDS2XDX sx=SDS2XDX.checkUncertainty("2.87E-4","20",5,35);
		
		
		/*
		Translator.init();
		Comment c=new Comment();
		Vector<String> v=new Vector<String>();
		v.add(" 31MG cL E(A,B),T(A,C)$test");
		c.setValues(v);
		System.out.println(" comment head= "+c.head());
		for(int i=0;i<c.nHeads();i++)
			System.out.println(" comment head at i="+i+": "+c.headAt(i)+" flag="+c.flagV().get(i)+" flags="+c.flags());
		 */
		
		/*
		String s="249BK cA HF$The nuclear radius parameter r{-0}({+249}Bk)=1.49492           " + 
				"is deduced from interpolation (or unweighted average) of radius\n" + 
				"parameters of the adjacent even-even nuclides.";
        s=Str.firstSentance(s).toUpperCase();
        s=s.replace("{-","").replace("{+","").replace("}", "").replace("{I", "").trim();
        s=s.replace("249BK", "").replace("(","").replace(")","").trim();
        FindValue fv=new FindValue();
        fv.findValueInEntry(s,"R0");
        
        //System.out.println("AlphaEnsdfWrap 286: line="+s+" text="+fv.txt());
        System.out.println("  val="+fv.s()+" "+fv.ds());
        */
		/*
		double brd=0.999986,dbrd=0;
		
        XDX2SDS xs=new XDX2SDS(brd,dbrd,35);
        
        //System.out.println("   brd="+brd+" dbrd="+dbrd+"  xs.s="+xs.s()+"  xs.ds="+xs.DS());
        
    	  
        String s="0.999986";
        String ds="";
        //if(ds.isEmpty())
        //	ds="11";
        
        SDS2XDX.setDefaultMaxNsigma(5);
        SDS2XDX sx=new SDS2XDX(s,ds);
        sx=sx.multiply("100");
        //sx=sx.add("100.1","");
        
        System.out.println("   brd="+brd+" dbrd="+dbrd+" s="+s+" ds="+ds+" sx.s="+sx.s()+"  sx.ds="+sx.DS());
        */
		
	    /*
		XDX2SDS xs=new XDX2SDS(5.5412153723311715,0.0671909447550526,0.0584177726153949,6);
		String s=xs.s();
		String ds=xs.ds();
		System.out.println("  s="+s+" ds="+ds);
		*/
	    
		/*
		SDS2XDX sx=new SDS2XDX("X+1234.5","11");
		System.out.println("1 result x="+sx.x()+" "+sx.DX()+" dxu="+sx.dxu()+"  dxl="+sx.dxl()+" s="+sx.S()+" ds="+sx.ds()+" dsu="+sx.dsu()+" dsl="+sx.dsl());
		sx.setErrorLimit(35);
		sx=sx.subtract(500.3,0.4);
		System.out.println("2 result x="+sx.x()+" "+sx.DX()+" dxu="+sx.dxu()+"  dxl="+sx.dxl()+" s="+sx.S()+" ds="+sx.ds()+" dsu="+sx.dsu()+" dsl="+sx.dsl());
	    */
	    
		//System.out.println("ABC".contains(""));
		//String s="(1),(2+3),+4";
		//String s="M1(+E2),E1";
		//String s="(M1,E2),M1+(E2),M1(+E2),(M1+)E2";
		//String s="(M1+E2";
	    //String s="JUN CHEN, FName LName, AND BALRAJ SINGH AND , FName1 LName1";
		//String s="James.  Bond Jr.";
		//String[] temp=s.split(", AND|,AND|AND,|AND ,|AND|[,]");
	    //String[] temp=s.split("[.][\\s]+|[\\s]+|[.]");
	    //String[] temp=s.split("[,]+|\\sand\\s|,and\\s|\\sAND\\s|,AND\\s");
		//for(int i=0;i<temp.length;i++)
		//	System.out.println("## i="+i+"  #"+temp[i]+"#");
		
       /*
		
		Vector<String> tempV=Str.parseValueStr(s, "+", true);
		for(int i=0;i<tempV.size();i++)
			System.out.println("@@ i="+i+"  "+tempV.get(i));

        
	    
	    String dateS="25-fet-2023";
	    String formatS="dd-MMM-yyyy";
	    DateUtil du=new DateUtil(dateS,formatS,true);
	    System.out.println(" d="+du.day()+" m="+du.month()+" y="+du.year()+" "+du.monthName()+" isValid="+du.isValid());
	    
	    
        SimpleDateFormat  format= new SimpleDateFormat("dd-MMM-yyyy");
        Date date = format.parse(dateS);
    
        Calendar cal=Calendar.getInstance();
        cal.setTime(date);
        
        Calendar cal1=cal;
        System.out.println(" "+dateS+" format="+formatS+" first day of week="+cal1.getFirstDayOfWeek()+" monday="+Calendar.MONDAY+"  "+cal1.get(Calendar.DAY_OF_WEEK));
        System.out.println(cal1.get(Calendar.WEEK_OF_MONTH)+"  "+cal1.get(Calendar.DAY_OF_WEEK_IN_MONTH));
        System.out.println(cal1.get(Calendar.YEAR)+"  "+cal1.get(Calendar.MONTH)+" "+cal1.get(Calendar.DAY_OF_MONTH)+" "+cal1.get(Calendar.DAY_OF_WEEK)+"  "+cal1.get(Calendar.DAY_OF_YEAR));
        */
	}
	
	public static void test0() {
		String[] s1= {"A","B","C"};
		String[] s2= {"A","B","C"};
		
		testFunc1(s1);
		for(int i=0;i<s1.length;i++)
			System.out.println("test 1: "+s1[i]);
		
		for(int i=0;i<s2.length;i++) {
			testFunc2(s2[i]);
			System.out.println("test 2: "+s2[i]);
		}
	}
	public static void testFunc1(String[] s) {
		s[0]="a";
		s[1]+='b';
		s[2]="";
	}
	public static void testFunc2(String a) {
		a="";
	}
	
	public static void test1() throws Exception {
    	Translator.init();
    	
    	
    	ensdfparser.ensdf.ENSDF ens=new ensdfparser.ensdf.ENSDF();
    	String os=System.getProperty("os.name").toLowerCase();
    	if(os.contains("mac"))
    	    ens.setValues(Str.readFile(new java.io.File("/Users/chenj/work/evaluation/ENSDF/check/check.ens")));
    	else
    	    ens.setValues(Str.readFile(new java.io.File("H:\\work\\evaluation\\ENSDF\\check\\check.ens")));
    	
    	SpinParityParser jpiParser=new SpinParityParser(ens);
    	System.out.println(" parent "+ens.parentAt(0).level().JPiS());
    	
    	for(int i=0;i<ens.nLevels();i++) {
    		Level lev=ens.levelAt(i);
    		//if(lev.nGammas()==0)
    		//	continue;
    		
    		String es=lev.ES();
    		//if(!lev.ES().contains("1256"))
    		//	continue;
    		//if(i<7)
    		//	continue;
    		//if(i>7)
    		//	break;
    		
    		System.out.println("\n** #"+i+" Level="+es);
    		
    		Vector<String> out=jpiParser.findJSVFromGammas(lev,"both",false);
    		
    		String s="",MUL="";
    		for(int j=0;j<jpiParser.currentGammasForJPI().size();j++) {
    			Gamma g=jpiParser.currentGammasForJPI().get(j);
    			//System.out.println(" iLev="+i+" g.FLI="+g.FLI()+" g.ILI="+g.ILI());
    			if(g.MS().isEmpty())
    				MUL=" ";
    			else
    				MUL=" "+g.MS()+" ";
    			
    			if(g.ILI()==i) {//decaying gammas
    				
    				s+=g.ES()+" gamma"+MUL+"to "+g.JFS()+", ";
    			}else {//feeding gammas
    				s+=g.ES()+" gamma"+MUL+"from "+g.JIS()+", ";
    			}
    			
    		}
    		
    		s=s.trim();
    		if(s.length()>0)
    			s=s.substring(0,s.length()-1);
    		
    		System.out.println(s);
            /*
    		for(int j=0;j<lev.nGammas();j++) {
    			Gamma g=lev.gammaAt(j);
    			System.out.println(" ** Gamma "+g.ES()+" MUL="+g.MS()+" to JF="+g.JFS());
    		}
    		*/
    		
        	for(int j=0;j<out.size();j++) {
        		System.out.println("   JPI from gammas (decaying and feeding ones): "+out.get(j));
        	}
        	
    		System.out.println("       JPI from gammas combined: "+JPI.combineJSVtoStr(out));
    		
    		
    		System.out.println("###################");
    		out=jpiParser.findJSVFromDecays(ens.parentAt(0), lev);
        	for(int j=0;j<out.size();j++) {
        		System.out.println("   JPI from decays: "+out.get(j));
        	}
        	
        	if(lev.nBetas()>0)
        		System.out.println("  parentJP="+ens.parentAt(0).level().JPiS()+" logft="+lev.betaAt(0).LOGFTS()+"   JPI from decays combined: "+JPI.combineJSVtoStr(out));
    	
    		System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
    		out=jpiParser.findJSVFromGammasAndDecays(ens.parentAt(0), lev);
        	for(int j=0;j<out.size();j++) {
        		System.out.println("   JPI from gammas and decays: "+out.get(j));
        	}
    		System.out.println("       JPI from gammas and decays combined: "+JPI.combineJSVtoStr(out));
    	}
    	
    	/*
    	String js="(1/2+,3/2-,5/2+)";
    	String[] jps=EnsdfUtil.parseExactJPI(js);
    	
    	Vector<JPI> out=new Vector<JPI>();
    	for(int i=0;i<jps.length;i++) {
        	JPI jpi=new JPI(jps[i]);
        	out.add(jpi);
    	}
        */
    	
    	//Vector<JPI> out=JPI.JPICoupling("3/2+,1/2-", "(2+,1-)");
    	//Vector<JPI> out=findJPIsFromGammaTo("1/2+");
    	//Vector<String> out=findJPIsFromGammaTo1("1/2-","0,1,2+");
    	//for(int i=0;i<out.size();i++)
    	//	System.out.println(" 1 "+out.get(i));
    	
    	//Vector<JPI> out1=findJPIsFromGammaTo("2-");
    	//Vector<String> out1=findJPIsFromGammaTo1("2-");
    	//for(int i=0;i<out1.size();i++)
    	//	System.out.println(" 2 "+out1.get(i).js());
    	
    	//out=JPI.mergeJPV(out, out1,false);
    	//out=JPI.mergeJSV(out, out1,false);
    	
    	//for(int i=0;i<out.size();i++)
    	//	System.out.println(" merged "+out.get(i));

    	
    	/*
    	String[] jps1=EnsdfUtil.parseExactJPI(js);
    	String[] jps2=EnsdfUtil.parseJPI(js);
    	for(int i=0;i<jps1.length;i++)
    		System.out.println(jps1[i]);
    	System.out.println("");
    	for(int i=0;i<jps2.length;i++)
    		System.out.println(jps2[i]);
    	
    	*/
    	
    	//Vector<String> out=EnsdfUtil.findGammaPoleExp("(M1+E2)");
    	//for(int i=0;i<out.size();i++)
    	//	System.out.println(out.get(i));
	}

}
