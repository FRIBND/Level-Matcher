package consistency.base;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Vector;

import consistency.main.Setup;
import ensdfparser.calc.DataPoint;
import ensdfparser.ensdf.Comment;
import ensdfparser.ensdf.Nucleus;
import ensdfparser.ensdf.SDS2XDX;
import ensdfparser.nds.ensdf.EnsdfUtil;
import ensdfparser.nds.util.Str;


public class Util {
	
	@SuppressWarnings("unused")
	public static void countBlocks(String inputFilePath) throws Exception{
        //Run run=new Run();
        //run.initWebDisplay();
        Setup.load();
        ensdfparser.nds.latex.Translator.init();
        
        String baseOutDir="./out";
        
        String A,en;
        ArrayList<String> lines=new ArrayList<String>();       
        
        //run.loadENSDFDict("./test/local_dic2.dat");
        
        PrintWriter runLog=new PrintWriter(new FileOutputStream(new File(baseOutDir+"/run.log")),true); //gloabl log file
        
    	long startTime,endTime,tempStart,tempEnd;
    	float timeElapsed;//in second
    	startTime=System.currentTimeMillis();
    	tempStart=startTime;
    	
    	
    	boolean useInputFile=false;
    	
    	if(inputFilePath!=null && !inputFilePath.trim().isEmpty())
    		useInputFile=true;
    	
    	inputFilePath=inputFilePath.trim();
    	
    	runLog.println(" Start...");
    	
    	if(useInputFile){
    		try {
                File f=new File(inputFilePath);    
                //run.loadENSDF(f);
                lines.addAll(ensdfparser.nds.util.Str.readFile(f));
                runLog.println("Run log for processing file:"+f.getAbsolutePath()+"\n");
    		}catch(Exception e) {
    			runLog.println("Input file does not exist: "+inputFilePath+"\n");
    			runLog.close();
    			return;
    		}

    	}else{
            String filePath;
            
            ////////////////
            int start=1;
            int end=299;
            ////////////////
            
            runLog.println("Run log for processing files: mass="+start+" to "+end+"\n");      
            Date date=new Date();
            SimpleDateFormat sdf=new SimpleDateFormat("E MM/dd/yyyy 'at' hh:mm:ss a zzz");
            runLog.println("Generated at: "+sdf.format(date));
            
            for(int mass=start;mass<=end;mass++){
            	
            	filePath="./test/ensdf_170101";
            	
            	if(mass<10)
            		filePath+="/ensdf.00"+mass;
            	else if(mass<100)
            		filePath+="/ensdf.0"+mass;
            	else 
            		filePath+="/ensdf."+mass;
            	
            	File f=new File(filePath);
            	if(!f.exists()){
            		runLog.println("Warning: file for mass="+String.format("%3s", mass)+" does not exist! Skip it.");
            		continue;
            	}
            	
            	lines.addAll(ensdfparser.nds.util.Str.readFile(f));
            	lines.add("");
            }
    	}  
        
        Vector<Vector<String>> blocks=new Vector<Vector<String>>();
        blocks=Str.splitStringBlock(lines);

        //nds.ui.EnsdfWrap[] ews=run.getEnsdfWraps();
        int ngood=0,nbad=0;
        int ngoodTotal=0,nbadTotal=0;
        String prevA="";
        int nLevels=0,nGammas=0;//adopted levels, gammas
        int nReactions=0,nNuclides=0;
        for(int i=0;i<blocks.size();i++){
        	        	
        	ArrayList<String> list=new ArrayList<String>();
        	list.addAll(blocks.get(i));
        	
        	A=list.get(0).substring(0,3).trim();
        	en=list.get(0).substring(3,5).trim().toUpperCase();
        	if(en.length()>1)
        		en=en.substring(0, 1)+en.toLowerCase().charAt(1);
        	
        	
        			         
            if(!A.equals(prevA)){
            	
                if(prevA.length()>0){
                	tempEnd=System.currentTimeMillis();
                	//runLog.println("*** End processing mass="+prevA);
                	//runLog.println("*** Total "+(ngood+nbad)+" blocks, "+ngood+" success, "+nbad+" fail");
                	//runLog.println("*** Processing time: "+String.format("%.3f", (float)(tempEnd-tempStart)/1000)+" seconds");
                }
            	
            	//runLog.println("\n*** Processing mass="+A);
            	tempStart=System.currentTimeMillis();
            	ngood=0;
            	nbad=0;
            }
            	
            //counting levels and gamma
            if(list.get(0).contains("ADOPTED")){
            	String line="";
            	for(int j=0;j<list.size();j++){
            		line=list.get(j);
            		char c=line.charAt(7);
            		if(!line.substring(5,7).trim().isEmpty() || (c!='L' && c!='G'))
            			continue;
            		
            		if(c=='L'){
            			nLevels++;
            			
            			/*
            			//count and print g.s. of all nuclides in ENSDF
            			String NUCID=line.substring(0,5);
            			Nucleus nuc=new Nucleus(NUCID);
            			String zs=String.format("%5s    ",nuc.Z());
            			runLog.println(zs+NUCID+"     "+line.substring(9,55));
            			nNuclides++;
            			break;
            			*/
            		}else if(c=='G')
            			nGammas++;
            	}
            	
            	String s=printBE2Ratio(list);
            	if(s.length()>0) runLog.println(s);
            }else if(en.length()>0){
            	nReactions++;
            }

            
            if(i==blocks.size()-1){
            	tempEnd=System.currentTimeMillis();
            	//runLog.println("*** End processing mass="+A);
            	//runLog.println("*** Total "+(ngood+nbad)+" blocks, "+ngood+" success, "+nbad+" fail");
            	//runLog.println("*** Processing time: "+String.format("%.3f", (float)(tempEnd-tempStart)/1000)+" seconds");
            }
            	
            prevA=A;
        }

        
        runLog.println("\nTotal "+blocks.size()+" blocks, "+ngoodTotal+" success, "+nbadTotal+" fail");
        runLog.println("        total lines="+lines.size());
        runLog.println("        #levels="+nLevels+"  #gammas="+nGammas+" #reactions/decays="+nReactions);
        //runLog.println("        #nuclides="+nNuclides);
        
        endTime=System.currentTimeMillis();
        timeElapsed=(float)(endTime-startTime)/1000;
        runLog.println("\nTime elapsed: "+String.format("%.3f", timeElapsed)+" seconds");
        
        runLog.close();
    
        //run.loadENSDF(list);
        //long startTime=System.currentTimeMillis();      
        //run.convert();    
        //run.waitLaTeX(startTime);
        //run.cleanupFiles();
    }
    
    
    /*
     * ratio of B(E2)42/B(E2)/20
     */
	@SuppressWarnings("unused")
	public static String printBE2Ratio(ArrayList<String> lines){
    	String out="";
    	SDS2XDX be2=null,be4=null;
    	float e2=-1,e4=-1;
    	boolean findBE2=false,findBE4=false;
    	if(!lines.get(0).contains("ADOPTED LEVELS"))
    		return "";
		
    	String NUCID=lines.get(0).substring(0,5);
		ensdfparser.ensdf.Nucleus nuc;
		try {
			nuc = new ensdfparser.ensdf.Nucleus(NUCID);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			return "";
		}
		
		if(nuc.A()%2!=0 || nuc.z()%2!=0)
			return "";
		

		
		Vector<String> temp=new Vector<String>();
		temp.addAll(lines);
		ensdfparser.ensdf.ENSDF ens=new ensdfparser.ensdf.ENSDF();
		try {
			ens.setValues(temp);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return "";
		}
		
    	String js="";
    	for(int i=0;i<ens.nLevels();i++){
    		ensdfparser.ensdf.Level lev=ens.levelAt(i);
    		js=lev.JPiS().replace("(","").replace(")","").trim();
		
    		if(js.equals("2+") && !findBE2){
				for(int j=0;j<lev.nGammas();j++){
					ensdfparser.ensdf.Gamma gam=lev.gammaAt(j);
					ensdfparser.ensdf.ContRecord rc=null;
					try{
					    gam.contRecordsVMap().get("BE2WDWN").get(0);
					}catch(Exception e) {}
					
					if(Math.abs(gam.EF()-lev.EF())<5 && (rc!=null&&rc.s().length()>0)){
						findBE2=true;
						e2=lev.EF();
						be2=new SDS2XDX(rc.s(),rc.ds());
					}
				}
			}
    		
    		if(js.equals("4+")&&findBE2){
				for(int j=0;j<lev.nGammas();j++){
					ensdfparser.ensdf.Gamma gam=lev.gammaAt(j);
					ensdfparser.ensdf.ContRecord rc=null;
					try{
					    gam.contRecordsVMap().get("BE2WDWN").get(0);
					}catch(Exception e) {}
					
					if(Math.abs(lev.EF()-e2-gam.EF())<5 && (rc!=null&&rc.s().length()>0)){
						findBE4=true;
						e4=lev.EF();
						be4=new SDS2XDX(rc.s(),rc.ds());
					}
				}
			}

    		if((findBE2&&findBE4) || i>5)
    			break;
    		
    	}
    	
    	if(!findBE2 || !findBE4)
    		return "";
    	
    	SDS2XDX r=null;
    	if(be2!=null && be4!=null){
    		r=be4.divided(be2);
    		if(r.x()<1 && (ensdfparser.nds.util.Str.isNumeric(r.ds())||r.ds().isEmpty()) && ensdfparser.nds.util.Str.isNumeric(r.s()))
    		out=NUCID+"    "+String.format("Z=%3s     B(E2)20=%8s %4s       B(E2)42=%8s %4s     ratio=%6s  %3s",nuc.Z(),be2.S(),be2.DS(),be4.S(),be4.DS(),r.S(),r.DS());
    	}
    	
    	return out;
    }

    public static Vector<DataPoint> parseDatapointsInComments(Comment c,String keyword){
        return parseDatapointsInComments(c,keyword,false);
    }
    /*
     * keyword is for identifying if the comment contains wanted data values.
     * It should be set as very unique words of phrases, like default keyword="AVERAGE OF".
     * It is not case-sensitive and the values must be listed after the keyword
     */
    public static Vector<DataPoint> parseDatapointsInComments(Comment c,String keyword,boolean shortenLabel){
        Vector<DataPoint> dpsV=new Vector<DataPoint>();
        String body=c.rawBody();
        //String flags=c.flags();
        //int nheads=c.nHeads();
        boolean isT=c.head().equals("T");
        
        String firstSentence=Str.firstSentance(body);
        
        keyword=keyword.toUpperCase().trim();

        //System.out.println(firstSentence.toUpperCase().contains(keyword));
        
        //if(nheads!=1 || flags.length()>0 || keyword.isEmpty() || !body.contains(keyword))
        if(keyword.isEmpty() || !firstSentence.toUpperCase().contains(keyword))            
            return dpsV; 
         
        body=firstSentence;
        
        String label="";
        double x=-1,dxl=-1,dxu=-1;
        
        int start=body.toUpperCase().indexOf(keyword)+keyword.length();
        int end=-1;
        
        
        boolean inBracket=false;
        int nBrackets=0;

        int ic=start+1;
        while(ic<body.length()) {
            char ch=body.charAt(ic);
            
            if(ch=='(' || ch=='[') {
                inBracket=true;
                nBrackets++;
            }if(ch==')' || ch==']') {
                nBrackets--;
                if(nBrackets==0)
                    inBracket=false;
            }
                  
            if(!inBracket) {         
                if(ch=='.') {
                    try {
                        if(Character.isDigit(body.charAt(ic-1)) && Character.isDigit(body.charAt(ic+1))) { 
                            ic++;
                            continue;
                        }
                        
                        end=ic;
                        break;
                    }catch(Exception e) {
                        //e.printStackTrace();
                    }                                           
                }                          
            }

            ic++;
        }  

        if(end<0)
            end=body.length();

        //System.out.println("ConsistencyCheck Util 357:   start="+start+" end="+end+"  body len="+body.length()+"  keyword="+keyword+" c="+c.body());
       
        String str=body.substring(start,end).trim();       
        
        int n1=-1,n2=-1,n=-1;
        String s1="",s2="";

        //System.out.println(str);
        
        n=str.toUpperCase().indexOf("{I");
        while(n>0) {
            n1=str.indexOf("}", n);
            if(n1>n) {
                s1=str.substring(n+2,n1).trim();
                if(s1.contains("+") && s1.contains("-"))
                    s2=s1.replace("+","").replace("-","").trim();
                else
                    s2=s1;
                
                //System.out.println("Util 381: s1="+s1+" s2="+s2+" str="+str);
                
                if(Str.isNumeric(s2))
                    str=str.substring(0,n)+" "+s1+" "+str.substring(n1+1);//space is added in case there is no space originally, like 12.3{I13}(1922AAAA)
                
                n=str.toUpperCase().indexOf("{I",n1);
            }else
                break;
        }
        //System.out.println(str);
        //(3754  10 ) (1974MuZB)
        if(str.startsWith("(")) {
        	int p=str.indexOf(")");
        	String str1=str.substring(0,p).trim();
        	String[] temp=str1.split("[\\s]+");
        	if(temp.length==2 && Str.isNumeric(temp[0])&&Str.isNumeric(temp[1])) {
        		str=str1+str.substring(p+1);
        	}
        }
        
        boolean isHeading=true;
        while(!str.isEmpty()) {
            
            n1=str.indexOf(" ");
            if(n1<0)
            	break;
            
            s1=str.substring(0,n1).trim();   
            int p1=s1.indexOf(",");
            if(p1>0 && p1<n1) {
            	s1=s1.substring(0,p1).trim();	
            	n1=p1;
            }
            
            s2=str.substring(n1).trim();
            
            if(s1.contains("=")) 
            	s1=s1.substring(s1.indexOf("=")+1).trim();
            
            //System.out.println("Util 420: @s1="+s1+" @s2="+s2+" ##str="+str);
            
            int offset=str.length()-s2.length();   
            n2=str.indexOf(" ",offset);
            if(n2<0)
                n2=str.length();
            
            if(n1+1<=n2)
            	s2=str.substring(n1+1,n2).trim();
            
            String[] temp=s2.split("[,;]+");          
            s2=temp[0];
            
            //System.out.println("Util 431: @s1="+s1+" @s2="+s2+" ##str="+str);
            
            n2=str.indexOf(s2,offset)+s2.length();
            
            if(Str.isNumeric(s1)) {
                if(Str.isNumeric(s2) || ( s2.contains("+")&&s2.contains("-")&&Str.isNumeric(s2.replace("+","").replace("-","")) ) ) 
                    break;

                if(isT) {
                    //at this point s2 is the unit
                    
                    s2=str.substring(n2).trim();
                    int n3=str.indexOf(" ",str.length()-s2.length());
                    if(n3<0)
                        n3=str.length();
                    
                    if(n2+1<=n3) {
                    	s2=str.substring(n2+1,n3).trim();                    
                    	temp=s2.split("[,;]+");                                  
                    	s2=temp[0];
                    }
                       
                    if(Str.isNumeric(s2) || ( s2.contains("+")&&s2.contains("-")&&Str.isNumeric(s2.replace("+","").replace("-","")) ) ) 
                        break;
                }

                //if reaching here, we are looking for value without uncertainty. Check very carefully to avoid mistakenly recognize 
                //any random numbers as a data value, like 15 in "E=15 MEV", 12.3 in "134AB B- DECAY (12.3 S)"
                //System.out.println("Str="+str+"  s1="+s1+"\n s2="+s2+" "+isT);

                if(isHeading)
                	break;
            }

            //System.out.println("Util 463: s1="+s1+" s2="+s2+" str="+str);
            
            if(s2.contains("=")) { 
            	int p=s2.indexOf("=");
            	s2=s2.substring(p+1).trim();
            	n1=str.indexOf("=",n1)+1;
            }
            
            if(Str.isNumeric(s2))
                str=str.substring(n1).trim();
            else
                str=str.substring(n2).trim();
            
            isHeading=false;
        }
        //debug
        //if(c.head().equals("T")) 
        //System.out.println("1 Type="+c.type()+" head="+c.head()+" body="+body+" str="+str+" keyword="+keyword);

        inBracket=false;
        nBrackets=0;
        
        String commaMarker="@#$";
        
        str=str.replace("and","AND");
        for(int i=0;i<str.length();i++) {
            char ch=str.charAt(i);
            if(ch=='(' || ch=='[') {
                inBracket=true;
                nBrackets++;
            }if(ch==')' || ch==']') {
                nBrackets--;
                if(nBrackets==0)
                    inBracket=false;
            }
            
            if(inBracket&&i<str.length()-1){
                if(ch==',') {
                    str=str.substring(0,i)+commaMarker+str.substring(i+1);
                }else if(str.substring(i).startsWith("AND")) {
                    str=str.substring(0,i)+"and"+str.substring(i+3);
                    i=i+2;
                }
            }
        }
        
        String[] dps=str.split("[,]+|[,\\s]+AND[\\s]+");
        //String[] dps=str.split("[,]+");
        //System.out.println(str.contains(","));
        
        //debug
        //if(c.head().equals("T")) 
        //System.out.println("####2 Type="+c.type()+" head="+c.head()+" body="+body+" str="+str+"  #"+dps.length+"#");
        
        for(int i=0;i<dps.length;i++) {
            str=dps[i].trim();

            if(str.isEmpty())
                continue;
            
            //System.out.println("i="+i+" ###"+str+" keyword="+keyword);
            
            if(!str.startsWith("=") && !str.toUpperCase().startsWith(keyword) && !Str.isDigit(str.charAt(0)))
            	continue;
            
            str=findStartOfValUncPair(str);
            
            //System.out.println("@@@"+str);
            
            if(str.isEmpty())
                continue;
            
            str=str.replace(commaMarker,",");
            
            String[] v=str.split("[\\s]+");
            
            
            //debug
            //System.out.println("*** dp#"+i+"  "+str+" v.length="+v.length);
            
            if(v.length<1 || !Str.isNumeric(v[0].trim()))
                continue;
            //System.out.println("*** dp#1"+i+"  "+str+" v.length="+v.length);
            
            boolean isFakeUnc=false;
            if(v.length==1) {
            	str+=" 1";
            	isFakeUnc=true;
            }else if(v.length==2) {
            	if((EnsdfUtil.isHalflifeUnit(v[1])||EnsdfUtil.isWidthUnit(v[1])) && isT) {
            		str+=" 1";
            		isFakeUnc=true;
            	}
            }else {
            	String s=v[1].toUpperCase();
            	if((EnsdfUtil.isHalflifeUnit(s)||EnsdfUtil.isWidthUnit(s)) && isT) {
            		s=v[2].toUpperCase();
            	}
        		if(s.equals("FROM") || s.equals("IN") || (s.startsWith("(")&&s.endsWith(")")) ) {
        			int p=str.toUpperCase().indexOf(s);
        			str=str.substring(0,p)+" 1 "+str.substring(p);
        			isFakeUnc=true;
        		}
            }
            
            if(isFakeUnc) {
            	v=str.split("[\\s]+");
            }
            
            //in case no space follows the uncertainty str, like 12.3 {I12}(1988AAAA), or 12.3 {I12}from 1988AAAA
            if(!Str.isNumeric(v[1].trim()) && !isT) {
                int j=0;
                String vs="";
                while(Character.isDigit(v[1].charAt(j))) {
                    vs+=v[1].charAt(j);
                    j++;
                }
                vs=vs.trim();
                
                
                if(Str.isNumeric(vs)) {
                    
                    String[] tempV=new String[v.length+1];
                    tempV[0]=v[0];
                    tempV[1]=vs;
                    tempV[2]=v[1].substring(vs.length()).trim();
                    for(int k=2;k<v.length;k++)
                        tempV[k]=v[k];
                    
                    v=tempV;   
                }
            }
            
            //System.out.println("***2 dp#"+i+"  "+str+" v.length="+v.length);
            
            try {
                String s="",ds="",unit="";
                
                s=v[0].trim();
                if(isT && v.length>=3 && (EnsdfUtil.isHalflifeUnit(v[1])||EnsdfUtil.isWidthUnit(v[1])) ) {
                    unit=v[1].trim();
                    ds=v[2].trim();
                }else
                    ds=v[1].trim();
                
                SDS2XDX s2x=new SDS2XDX(s,ds);
                
                if(s2x.dxl()<0 || s2x.dxu()<0)
                    continue;
                
                x=s2x.x();
                dxu=s2x.dxu();
                dxl=s2x.dxl();
                
                int p=-1;
                if(unit.length()>0)
                    p=str.indexOf(v[2],v[0].length()+v[1].length())+v[2].length();
                else
                    p=str.indexOf(v[1],v[0].length())+v[1].length();
                
                label=str.substring(p).trim();
                
                while(label.contains("  "))
                    label=label.replace("  "," ");
                
                
                //for long label like, (1974Ro31, average of 21 {I3} from |b counting, 20 {I5} from neutron)
                if(label.length()>30 && shortenLabel) {
                    String[] tempA=label.split("[,\\s]+");
                    for(int j=0;j<tempA.length;j++) {
                        str=tempA[j].trim();
                        if(str.length()>0) {
                            label=str;
                            break;
                        }
                    }
                }
                
                //if(!label.contains("{")) {    
                String temp=label;
                if(label.startsWith("(")) {
                    temp=label.substring(1).trim();
                    
                    int index=temp.lastIndexOf(")");
                    if(index>0) {
                        temp=temp.substring(0,index).trim();
                        //if(EnsdfUtil.isKeyNumber(temp))
                        //    label=temp;
                    }
                }else if(label.startsWith("[")) {
                    temp=label.substring(1).trim();
                    
                    int index=temp.lastIndexOf("]");
                    if(index>0) {
                        temp=temp.substring(0,index).trim();
                        //if(EnsdfUtil.isKeyNumber(temp))
                        //    label=temp;
                    }
                }else if(label.endsWith(")")) {
                    temp=label.substring(0,label.length()-1).trim();
                    
                    int index=temp.indexOf("(");
                    if(index>0) {
                        temp=temp.substring(index+1).trim();
                        //if(EnsdfUtil.isKeyNumber(temp))
                        //    label=temp;
                    }
                }else if(label.endsWith("]")) {
                    temp=label.substring(0,label.length()-1).trim();
                    
                    int index=temp.indexOf("[");
                    if(index>0) {
                        temp=temp.substring(index+1).trim();
                        //if(EnsdfUtil.isKeyNumber(temp))
                        //    label=temp;
                    }
                }  
                
                
                if(temp.length()>=8 && EnsdfUtil.isKeynumber(temp.substring(0,8)))
                    label=temp;
            //}
                 
                if(label.toUpperCase().startsWith("FROM"))
                    label=label.substring(4).trim();
                else if(label.toUpperCase().startsWith("IN"))
                    label=label.substring(2).trim();
                
                if(isFakeUnc) {
                	dxu=-1;
                	dxl=-1;
                	ds="";
                }
                
                DataPoint dp=new DataPoint(x,dxu,dxl,label);
                dp.setS(s,ds);//used to determine maxNDigitsAfterDot (uncertainty rounding in average value)
                dp.setUnit(unit);
                
                dpsV.add(dp);  
                
                //debug
                //if(c.head().equals("T")) 
                //System.out.println("3 Type="+c.type()+" head="+c.head()+" body="+c.body()+" s="+s+" ds="+ds+" unit="+unit+" label="+label);
            }catch(Exception e) {
                
            }

        }

        
        //System.out.println(dpsV.size());
        
        return dpsV;
    }

    /*
     * find the beginning of a value&uncertainty entry and return a string
     * with anything before it being removed. 
     * return "" if no val&unc pair in the input str
     */
    static String findStartOfValUncPair(String str) {
    	int n1=-1,n2=-1;
    	String s1="",s2="";
    	boolean isT=false;
    	boolean isHeading=true;
    	//Vector<String>  saveSV=new Vector<String>();
    	
        while(!str.isEmpty()) {
            
            n1=str.indexOf(" ");
  
            
            if(n1<0) {
            	int n0=str.indexOf("(");
            	         	
            	
            	boolean isGood=false;
            	if(n0>0 && str.endsWith(")")) {//e.g., 23(10)
            		String temp1=str.substring(0,n0);
            		String temp2=str.substring(n0+1,str.length()-1);
            		
            		if(Str.isNumeric(temp1)) {
            			if(Str.isNumeric(temp2)) {
            				isGood=true;
            			}else if(temp2.startsWith("+")&& temp2.indexOf("-")>1) {
            				temp2=temp2.replace("+","").replace("-","").trim();
            				if(Str.isInteger(temp2))
            					isGood=true;
            				
            			}else if(temp2.startsWith("-")&& temp2.indexOf("+")>1) {
            				temp2=temp2.replace("+","").replace("-","").trim();
            				if(Str.isInteger(temp2))
            					isGood=true;
            			}
            		}
            	}else {//e.g., "61 from (p,2n|g)" or simply "61", no uncertainty
            		s1=str;
            		if(Str.isNumeric(s1)) {
            			isGood=true;
            			n0=s1.length()+1;
            		}
            	}
            	if(isGood) {
            		n1=n0-1;
            		if(n0<=str.length()) {
                		s1=str.substring(0,n0).trim();
                		s2=str.substring(n0).trim();
            		}else {
            			s1=str;
            			s2="";
            		}

            		str=s1+" "+s2;
            		str=str.trim();
            	}else
            		return "";
            }else {
                s1=str.substring(0,n1).trim();         
                s2=str.substring(n1).trim();
            }

            
            if(s1.contains("=")) 
            	s1=s1.substring(s1.indexOf("=")+1).trim();
            
            
            //System.out.println(s1+"$"+s2);
            
            int offset=str.length()-s2.length();
            n2=str.indexOf(" ",offset);
            if(n2<0)
                n2=str.length();

            
            if(n1<n2)
            	s2=str.substring(n1+1,n2).trim();
            else
            	s2="";
            
            String[] temp=s2.split("[,;]+");
            
            //System.out.println("3 str="+str+" s1="+s1+"  s2="+s2+" temp.len="+temp.length+"\n");
            
            s2=temp[0];
            n2=str.indexOf(s2,offset)+s2.length();
            
            //System.out.println("4 str="+str+" s1="+s1+"  s2="+s2+" temp.len="+temp.length+"\n");
            

            if(Str.isNumeric(s1)) {

            	String s3=s2;
            	if(s3.startsWith("(") && s3.endsWith(")")){
            		s3=s3.substring(1).trim();
            		s3=s3.substring(0,s3.length()-1).trim();
            	}
                if(Str.isNumeric(s3) || ( s3.contains("+")&&s3.contains("-")&&Str.isNumeric(s3.replace("+","").replace("-","")) ) ) { 
                	n1=str.indexOf(s1);
                	str=str.substring(n1).trim();
                    break;
                }

                if(!isT) {
                    if(EnsdfUtil.isHalflifeUnit(s2) || EnsdfUtil.isWidthUnit(s2) )
                        isT=true;
                }


                //System.out.println(" str="+str+" s1="+s1+"\n s2="+s2+" "+isT);
                
                if(isT) {
                    //at this point s2 is the unit
                    
                    s2=str.substring(n2).trim();
                    int n3=str.indexOf(" ",str.length()-s2.length());
                    if(n3<0)
                        n3=str.length();

                    if(n2+1<=n3) {
                    	s2=str.substring(n2+1,n3).trim();                    
                    	temp=s2.split("[,;]+");                                  
                    	s2=temp[0];
                    }

                    
                    //System.out.println("  s1="+s1+"\n s2="+s2+" "+isT);
                    
                    if(Str.isNumeric(s2) || ( s2.contains("+")&&s2.contains("-")&&Str.isNumeric(s2.replace("+","").replace("-","")) ) ) {
                    	n1=str.indexOf(s1);
                    	str=str.substring(n1).trim();
                    	break;
                    }
                        
                }
                
                //if reaching here, we are looking for value without uncertainty. Check very carefully to avoid mistakenly recognize 
                //any random numbers as a data value, like 15 in "E=15 MEV", 12.3 in "134AB B- DECAY (12.3 S)"
                //System.out.println("Str="+str+"  s1="+s1+"\n s2="+s2+" "+isT);

                if(isHeading)
                	break;
     
            }

            if(s2.contains("=")) { 
            	int p=s2.indexOf("=");
            	s2=s2.substring(p+1).trim();
            	n1=str.indexOf("=",n1)+1;
            }
            
            if(Str.isNumeric(s2))
                str=str.substring(n1).trim();
            else
                str=str.substring(n2).trim();
            
            isHeading=false;
        }   
        //System.out.println(str);
        
        return str;
    }
    
    public static DataInComment parseDatapointsInComments(String body){
        return parseDatapointsInComments(body,false);
    }
    
    public static DataInComment parseDatapointsInComments(String body,boolean shortenLabel){
        DataInComment data=new DataInComment();

        if(body==null || body.trim().isEmpty())
            return data;
        
        String body1=body;
        String body2=body;
        int offset0=0,index0=0;
        while((index0=body1.indexOf("(",offset0))>0) {
        	char c=body1.charAt(index0-1);
        	if(Character.isDigit(c)) {//to add space between a number and "("
        		body2=body2.substring(0,index0)+" "+body2.substring(index0);
        		body1=body2;
      
        		offset0=body1.indexOf(")",index0+1);
        		if(offset0<0) {
        			break;
        		}
        	}else {
        		offset0=body1.indexOf(")",index0+1);
        		if(offset0<0) 
        			break;
        	}
        	
        }
        body=body2;
        
        //System.out.println(body);
        
        Vector<DataPoint> dpsV=data.dpsV;
        
        int start=-1;
        int end=-1;
        
        boolean inBracket=false;
        int nBrackets=0;
        
        int ic=0;
        while(ic<body.length()) {
            char c=body.charAt(ic);
            
            if(c=='(' || c=='[') {
                inBracket=true;
                nBrackets++;
            }if(c==')' || c==']') {
                nBrackets--;
                if(nBrackets==0)
                    inBracket=false;
            }
            
            if(!inBracket) {         
                if(start<0) {
                    if(Character.isDigit(c)) {
                        start=ic;                      
                    }else if(c=='+' || c=='-') {
                        if(ic<body.length()-1 && Character.isDigit(body.charAt(ic+1))) {
                            start=ic;
                        }
                    }                     
                }else if(c=='.') {
                    try {
                        if(Character.isDigit(body.charAt(ic-1)) && Character.isDigit(body.charAt(ic+1))) { 
                            ic++;
                            continue;
                        }
                        
                        end=ic;
                        break;
                    }catch(Exception e) {
                        //e.printStackTrace();
                    }                                           
                }                          
            }

            ic++;
        }
            
        //System.out.println(" start="+start+" end="+end);
        
        if(end<0)
            end=body.length();
        
        if(start>=end)
            return data;
        
        String str=body.substring(start,end).trim();
        str=str.replace("\n","").replace("\r","");
          
        //System.out.println(" start="+start+" end="+end+" str="+str);
        
        
        String label="";
        double x=-1,dxl=-1,dxu=-1;
        
        int n=-1;
        int n1=-1;
        int n2=-1;
        String s1="",s2="",s0="";
        boolean isT=false;
        
        n=str.toUpperCase().indexOf("{I");
        while(n>0) {
            n1=str.indexOf("}", n);
            if(n1>n) {
                s1=str.substring(n+2,n1).trim();
                if(s1.contains("+") && s1.contains("-"))
                    s2=s1.replace("+","").replace("-","").trim();
                else
                    s2=s1;
                
                if(Str.isNumeric(s2))
                    str=str.substring(0,n)+" "+s1+" "+str.substring(n1+1);//space is added in case there is no space originally, like 12.3{I13}(1922AAAA)
                
                n=str.toUpperCase().indexOf("{I",n1);
            }else
                break;
        }
        
        //System.out.println("1 str=\n"+str+"\n\n");
        
        //looking for NUCID prefix and remove them all
        s0=str;
        
        n=s0.indexOf(" ");
        boolean found=false;
        Nucleus nuc=null;
        
        while(n>0) {
            s1=s0.substring(0,n).trim();
            s0=s0.substring(n).trim();
            n=s0.indexOf(" ");
            
            int i=0;
            s2="";
            while(i<s1.length() && Character.isDigit(s1.charAt(i))) {
                s2+=s1.charAt(i);
                i++;
            }
            
            //System.out.println(" s0="+s0+"\n s1="+s1+"\n s2="+s2);
            
            if(!s2.isEmpty() && Integer.parseInt(s2)<350 && i<s1.length()) {
                while(i<s1.length() && Character.isLetter(s1.charAt(i))) {
                    s2+=s1.charAt(i);
                    i++;
                }

                try {
                    nuc=new Nucleus(s2);
                    if(nuc.Z()>0) {
                        found=true;
                        break;
                    }
                }catch(Exception e) {
                    
                }

            }
            
        }

        //remove NUCID prefix
        if(found) {
        	s0=str;
        	
            data.NUCID=nuc.nameENSDF();
            
            //System.out.println("  *"+nuc.nameENSDF()+"1");
            
            ArrayList<String> types=new ArrayList<String>(Arrays.asList("L ","G ","B ","E ","A ","N ","P ",//N and P here are norm and parent
            		"DN","DP"," N"," P"));//N and P here are prompt neutron and proton decays
            ArrayList<String> nums=new ArrayList<String>(Arrays.asList(" ","2","3","4","5","6","7","8","9",
            		"A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z",
            		"a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p","q","r","s","t","u","v","w","x","y","z"));
            
            Vector<String> foundTypesV=new Vector<String>();
            for(String type:types) {
                for(String num:nums) {
                    String prefix=nuc.nameENSDF()+num;
                    String str1=str;
                    String prefix1=(prefix+"C"+type).trim();
                    String prefix2=(prefix+"c"+type).trim();
                    String prefix3=(prefix+"D"+type).trim();
                    String prefix4=(prefix+"d"+type).trim();
                    
                    str1=str1.replace(prefix1,"").trim();
                    str1=str1.replace(prefix2,"").trim();
                    str1=str1.replace(prefix3,"").trim();
                    str1=str1.replace(prefix4,"").trim();
                    
                    //if(data.recordType.isEmpty()&& str1.length()!=str.length())
                    //    data.recordType=type;
                    if(str1.length()!=str.length())
                    	foundTypesV.add(type);
                    
                    str=str1;
                }
                //System.out.println(" nuc="+nuc.nameENSDF()+" recordType="+data.recordType+" type="+type+"$");
            }
            if(foundTypesV.size()==1) {
            	data.recordType=foundTypesV.get(0);
            }else if(foundTypesV.size()>1) {
            	int lowestIndex=10000;
            	for(String ts:foundTypesV) {
            		int index=s0.indexOf(ts);
            		if(index>=0 && index<lowestIndex) {
            			lowestIndex=index;
            			data.recordType=ts;
            		}
            	}
            }
            //System.out.println(" nuc="+nuc.nameENSDF()+" recordType="+data.recordType);
        }

        String temp="";
        if(body.contains("$")) {
            temp="";
            n=body.indexOf("$");
            temp=body.substring(0,n);
            
            n1=temp.lastIndexOf(" ");
            if(n1<n)
                temp=body.substring(n1,n).trim();
            else
                temp=body.substring(0,n).trim();
            
            //System.out.println(" body="+body+" \n temp="+temp);
            
            temp=temp.toUpperCase();
            if(temp.equals("E"))
                data.entryName="E";
            else if(temp.equals("T"))
                data.entryName="T";
            else if(temp.equals("RI"))
                data.entryName="RI";
            else if(temp.equals("MR"))
                data.entryName="MR";
            else
            	data.entryName=temp;
                
        }
        
        n=str.toUpperCase().indexOf("AVERAGE");
        	
        
        if(n>0){
        	/*
        	int nlb=str.indexOf("(");
        	
            if(nlb>=0 && nlb<n) {
            	int nrb=str.indexOf(")");
            	if(nrb<=nlb)
            		return data;
            	
            	temp=str.substring(nlb+1,nrb).trim();
            	if(Str.isNumeric(temp))
            		return data;
            }
            */
        	int offset=0;
        	
        	str=str.substring(n+7).trim();
            n=str.toUpperCase().indexOf("OF");   
            offset=2;
            if(n<0) {
            	n=str.indexOf(":");
            	offset=1;
            	if(n<0)
            		return data;
            }

            //System.out.println("Util 939 str=\n"+str+"\n\n");
            
            if(n>0) {
                temp=str.substring(0,n).trim();
                
                //System.out.println("Util 950 str=\n"+temp+"\n\n");
                
                if(temp.startsWith("(")&&temp.endsWith(")")){
                	
                }else if(temp.startsWith("[")&&temp.endsWith("]")){
                	
                }else {
                	String temp1=str.substring(n+1).trim();
                	
                	//System.out.println("Util 959 str=\n"+temp1+"\n\n");
                	
                	int p=temp1.indexOf(" ");
                	if(p>0) {
                    	temp1=temp1.substring(0,p).trim();
                    	if(!Str.isNumeric(temp1))
                    		return data;
                	}else
                		return data;
                }
                
                /*
                temp=temp.substring(1,temp.length()-1).trim();
                
                //System.out.println("Util 953 temp=\n"+temp+"\n\n");
                
                while(temp.length()>0) {
                	if(!Character.isLetter(temp.charAt(0)))
                		return data;
                	
                	temp=temp.substring(1);
                }
                */
            }
		
            str=str.substring(n+offset).trim();        	
        }else {
            temp=data.entryName+"$";
            n=str.indexOf(temp);
            if(n>=0)
                str=str.substring(n+temp.length()).trim();
        }
        
       
        //System.out.println("2 str=\n"+str+"\n\n");
        //find the beginning of the data entries
        boolean isHeading=true;
        while(!str.isEmpty()) {
            
            n1=str.indexOf(" ");

            if(n1<0) {
            	int n0=str.indexOf("(");
            	         	           	
            	boolean isGood=false;
            	if(n0>0 && str.endsWith(")")) {//e.g., 23(10)
            		String temp1=str.substring(0,n0);
            		String temp2=str.substring(n0+1,str.length()-1);
            		
            		if(Str.isNumeric(temp1)) {
            			if(Str.isNumeric(temp2)) {
            				isGood=true;
            			}else if(temp2.startsWith("+")&& temp2.indexOf("-")>1) {
            				temp2=temp2.replace("+","").replace("-","").trim();
            				if(Str.isInteger(temp2))
            					isGood=true;
            				
            			}else if(temp2.startsWith("-")&& temp2.indexOf("+")>1) {
            				temp2=temp2.replace("+","").replace("-","").trim();
            				if(Str.isInteger(temp2))
            					isGood=true;
            			}
            		}
            	}else {//e.g., "61", no uncertainty
            		s1=str;
            		if(Str.isNumeric(s1)) {
            			isGood=true;
            			n0=s1.length()+1;
            		}else {
            			n0=s1.indexOf(",");//4,3,5
            			if(n0>0 && n0<s1.length()-1) {
            				String tempS1=s1.substring(0,n0).trim();
            				String tempS2=s1.substring(n0+1).trim();
            				if(Str.isNumeric(tempS1) && Character.isDigit(tempS2.charAt(0))) {
            					isGood=true;
            				}
            			}
            			
            		}
            	}
            	//System.out.println(str);
            	
            	if(isGood) {
            		n1=n0-1;
            		if(n0<=str.length()) {
                		s1=str.substring(0,n0).trim();
                		s2=str.substring(n0).trim();
            		}else {
            			s1=str;
            			s2="";
            		}

            		str=s1+" "+s2;
            		str=str.trim();
     
            	}else
            		return data;
            }else {
                s1=str.substring(0,n1).trim();  
                int p=s1.indexOf(",");
                if(p>0 && p<n1) {
                	s1=s1.substring(0,p).trim();	
                	n1=p;
                }
                s2=str.substring(n1).trim();
            }

            String tempStr=str;
   
            if(s1.indexOf("(")>0 && s1.endsWith(")")&&Character.isDigit(s2.charAt(0))) {
            	int n0=s1.indexOf("(");
          		String temp1=str.substring(0,n0);
        		String temp2=str.substring(n0+1,str.length()-1);
        		boolean isGood=false;
        		if(Str.isNumeric(temp1)) {
        			if(Str.isNumeric(temp2)) {
        				isGood=true;
        			}else if(temp2.startsWith("+")&& temp2.indexOf("-")>1) {
        				temp2=temp2.replace("+","").replace("-","").trim();
        				if(Str.isInteger(temp2))
        					isGood=true;
        				
        			}else if(temp2.startsWith("-")&& temp2.indexOf("+")>1) {
        				temp2=temp2.replace("+","").replace("-","").trim();
        				if(Str.isInteger(temp2))
        					isGood=true;
        			}
        		}
        		
        		if(isGood) {
            		n1=n0-1;
            		s1=str.substring(0,n0).trim();
            		s2=str.substring(n0).trim();
            		str=s1+" "+s2;
            		tempStr=str;
        		}
            }
            
            if(s1.contains("=")) {
            	s1=s1.substring(s1.indexOf("=")+1).trim();
            	tempStr=s1+" "+s2;
            }
            
            int offset=str.length()-s2.length();
            n2=str.indexOf(" ",offset);
            if(n2<0)
                n2=str.length();
            
            if(n1+1<=n2)
            	s2=str.substring(n1+1,n2).trim();
            
            String[] tempA=s2.split("[,;]+");
            
            s2=tempA[0];
            n2=str.indexOf(s2,offset)+s2.length();
            

            if(Str.isNumeric(s1)) {

            	String s3=s2;
            	if(s3.startsWith("(") && s3.endsWith(")")){
            		s3=s3.substring(1).trim();
            		s3=s3.substring(0,s3.length()-1).trim();
            	}
                if(Str.isNumeric(s3) || ( s3.contains("+")&&s3.contains("-")&&Str.isNumeric(s3.replace("+","").replace("-","")) ) ) { 
                	str=tempStr;
                    break;
                }
                
            	s2=s3;
            	
                if(!isT && (EnsdfUtil.isHalflifeUnit(s2)||EnsdfUtil.isWidthUnit(s2)) )
                    isT=true;

                //System.out.println("  s1="+s1+"\n s2="+s2+" "+isT);
                
                if(isT) {
                    //at this point s2 is the unit
                    
                    s2=str.substring(n2).trim();
                    int n3=str.indexOf(" ",str.length()-s2.length());
                    if(n3<0)
                        n3=str.length();
  
                    if(n2+1<=n3) {
                    	s2=str.substring(n2+1,n3).trim();                    
                    	tempA=s2.split("[,;]+");                                  
                    	s2=tempA[0];
                    }
                    
                    //System.out.println("  s1="+s1+"\n s2="+s2+" "+isT);
                    
                    if(Str.isNumeric(s2) || ( s2.contains("+")&&s2.contains("-")&&Str.isNumeric(s2.replace("+","").replace("-","")) ) ) { 
                    	str=tempStr;
                        break;
                    }
                }

                //if reaching here, we are looking for value without uncertainty. Check very carefully to avoid mistakenly recognize 
                //any random numbers as a data value, like 15 in "E=15 MEV", 12.3 in "134AB B- DECAY (12.3 S)"
                //System.out.println("Str="+str+"  s1="+s1+"\n s2="+s2+" "+isT);
                System.out.println("s1="+s1+"  s2="+s2+" str="+str+" "+isHeading);
                if(isHeading)
                	break;
            }

            //System.out.println("s1="+s1+"  s2="+s2+" str="+str);
            
            if(s2.contains("=")) { 
            	int p=s2.indexOf("=");
            	s2=s2.substring(p+1).trim();
            	n1=str.indexOf("=",n1)+1;
            }
            
            if(Str.isNumeric(s2))
                str=str.substring(n1).trim();
            else 
                str=str.substring(n2).trim();
            
            isHeading=false;
        }
        //debug
        //System.out.println("1 str="+str);

        
        inBracket=false;
        nBrackets=0;
        
        String commaMarker="@#$";

        str=str.replace("and","AND");
        for(int i=0;i<str.length();i++) {
            char ch=str.charAt(i);
            if(ch=='(' || ch=='[') {
                inBracket=true;
                nBrackets++;
            }if(ch==')' || ch==']') {
                nBrackets--;
                if(nBrackets==0)
                    inBracket=false;
            }
            
            if(inBracket&&i<str.length()-1){
                if(ch==','||ch==';') {
                    str=str.substring(0,i)+commaMarker+str.substring(i+1);
                }else if(str.substring(i).startsWith("AND")) {
                    str=str.substring(0,i)+"and"+str.substring(i+3);
                    i=i+2;
                }
            }
        }
        
        String[] dps=str.split("[,;]+|[,\\s]+AND[\\s]+|[;\\s]+AND[\\s]+|AND");
        
        //debug
        //System.out.println("2  body="+body+" str="+str+"  "+dps.length);
        
        for(int i=0;i<dps.length;i++) {
            str=dps[i].trim();

            if(str.isEmpty())
                continue;
            
            
            //System.out.println("i="+i+" str="+str+"  "+dps.length);
            
            str=findStartOfValUncPair(str);
            
            //System.out.println(str);
            
            if(str.isEmpty())
                continue;

            
            str=str.replace(commaMarker,",");
            
            //System.out.println("str="+str);
            
            String[] v=str.split("[\\s]+");
            if(v.length<1 || !Str.isNumeric(v[0].trim()))
                continue;
            
            boolean isFakeUnc=false;
            if(v.length==1) {
            	str+=" 1";
            	isFakeUnc=true;
            }else if(v.length==2) {
            	if((EnsdfUtil.isHalflifeUnit(v[1])||EnsdfUtil.isWidthUnit(v[1])) && isT) {
            		str+=" 1";
            		isFakeUnc=true;
            	}
            }else {
            	String s=v[1].toUpperCase();
            	if((EnsdfUtil.isHalflifeUnit(s)||EnsdfUtil.isWidthUnit(s)) && isT) {
            		s=v[2].toUpperCase();
            	}
        		if(s.equals("FROM") || s.equals("IN") || (s.startsWith("(")&&s.endsWith(")")) ) {
        			int p=str.toUpperCase().indexOf(s);
        			str=str.substring(0,p)+" 1 "+str.substring(p);
        			isFakeUnc=true;
        		}
            }
            
            if(isFakeUnc) {
            	v=str.split("[\\s]+");
            }
            
            //in case no space follows the uncertainty str, like 12.3 {I12}(1988AAAA), or 12.3 {I12}from 1988AAAA
            if(!Str.isNumeric(v[1].trim())) {
                int j=0;
                String vs="";
                while(Character.isDigit(v[1].charAt(j))) {
                    vs+=v[1].charAt(j);
                    j++;
                }
           
                vs=vs.trim();
                
                
                if(Str.isNumeric(vs)) {
                    
                    String[] tempV=new String[v.length+1];
                    tempV[0]=v[0];
                    tempV[1]=vs;
                    tempV[2]=v[1].substring(vs.length()).trim();
                    for(int k=2;k<v.length;k++)
                        tempV[k]=v[k];
                    
                    v=tempV;   
                }else if(v[1].startsWith("(")&&v[1].endsWith(")")) {
                	String temp1=v[1].substring(1,v[1].length()-1).trim();
                	if(Str.isNumeric(temp1) || (temp1.contains("+") && temp1.contains("-"))&&Str.isNumeric(temp1.replace("+","").replace("-",""))) {
                		v[1]=temp1;
                	}
                }
            }
            
            try {
                String s="",ds="",unit="";
                
                s=v[0].trim();
                if(isT && v.length>=3 && (EnsdfUtil.isHalflifeUnit(v[1])||EnsdfUtil.isWidthUnit(v[1])) ) {
                    unit=v[1].trim();
                    ds=v[2].trim();
                }else
                    ds=v[1].trim();
                
                SDS2XDX s2x=new SDS2XDX(s,ds);
                
                //System.out.println(s+" ds="+ds);
                
                if(s2x.dxl()<0 || s2x.dxu()<0)
                    continue;
                
                x=s2x.x();
                dxu=s2x.dxu();
                dxl=s2x.dxl();
                
                int p=-1;
                if(unit.length()>0)
                    p=str.indexOf(v[2],v[0].length()+v[1].length())+v[2].length();
                else
                    p=str.indexOf(v[1],v[0].length())+v[1].length();
                
                label=str.substring(p).trim();
                
                while(label.contains("  "))
                    label=label.replace("  "," ");
                   
                if(label.length()==1 && "()[]{}".contains(label))
                	label="";
                
                //System.out.println(" label="+label);
                
                //for long label like, (1974Ro31, average of 21 {I3} from |b counting, 20 {I5} from neutron)
                if(label.length()>30 && shortenLabel) {
                    
                    String[] tempA=label.split("[,\\s]+");
                    for(int j=0;j<tempA.length;j++) {
                        str=tempA[j].trim();
                        if(str.length()>0) {
                            label=str;
                            break;
                        }
                    }
                }
                
                temp=label;
                //if(!label.contains("{")) {
                if(label.startsWith("(")) {
                    temp=label.substring(1).trim();
                    
                    int index=temp.lastIndexOf(")");
                    if(index>0) {
                        temp=temp.substring(0,index).trim();
                        //if(EnsdfUtil.isKeyNumber(temp))
                        //    label=temp;
                    }
                }else if(label.startsWith("[")) {
                    temp=label.substring(1).trim();
                    
                    int index=temp.lastIndexOf("]");
                    if(index>0) {
                        temp=temp.substring(0,index).trim();
                        //if(EnsdfUtil.isKeyNumber(temp))
                        //    label=temp;
                    }
                }else if(label.endsWith(")")) {
                    temp=label.substring(0,label.length()-1).trim();
                    
                    int index=temp.indexOf("(");
                    if(index>0) {
                        temp=temp.substring(index+1).trim();
                        //if(EnsdfUtil.isKeyNumber(temp))
                        //    label=temp;
                    }
                }else if(label.endsWith("]")) {
                    temp=label.substring(0,label.length()-1).trim();
                    
                    int index=temp.indexOf("[");
                    if(index>0) {
                        temp=temp.substring(index+1).trim();
                        //if(EnsdfUtil.isKeyNumber(temp))
                        //    label=temp;
                    }
                }                	
            //}
                
                if(temp.length()>=8 && EnsdfUtil.isKeynumber(temp.substring(0,8)))
                    label=temp;
                
                if(label.toUpperCase().startsWith("FROM"))
                    label=label.substring(4).trim();
                else if(label.toUpperCase().startsWith("IN"))
                    label=label.substring(2).trim();
                               
                if(isFakeUnc) {
                	dxu=-1;
                	dxl=-1;
                	ds="";
                }
                DataPoint dp=new DataPoint(x,dxu,dxl,label);
                dp.setS(s,ds);//used to determine maxNDigitsAfterDot (uncertainty rounding in average value)
                dp.setUnit(unit);
                
                dpsV.add(dp);  
                
                //System.out.println(" s="+s+" ds="+ds+" unit="+unit);
                
                //debug
                //if(c.head().equals("T")) System.out.println("3 Type="+c.type()+" head="+c.head()+" body="+c.body()+" s="+s+" ds="+ds+" unit="+unit+" label="+label);
            }catch(Exception e) {
                
            }

        }
        
        return data;
    }   
    
    static String getXTagMarker(String xtag) {
        int n=xtag.indexOf("(");
        if(n<0)
            return "";
        
        String s=xtag.substring(n+1).replace(")","").trim();
        return s;
    }
}
