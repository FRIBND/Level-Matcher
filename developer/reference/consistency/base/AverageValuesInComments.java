package consistency.base;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Vector;

import ensdfparser.calc.DataPoint;
import ensdfparser.ensdf.Comment;
import ensdfparser.ensdf.ENSDF;
import ensdfparser.ensdf.Gamma;
import ensdfparser.ensdf.Level;
import ensdfparser.ensdf.Record;
import ensdfparser.ensdf.SDS2XDX;
import ensdfparser.nds.ensdf.EnsdfUtil;
import ensdfparser.nds.latex.Translator;
import ensdfparser.nds.util.Str;

public class AverageValuesInComments {

    Vector<ENSDF> ensdfsV=new Vector<ENSDF>();
    StringBuilder rpt=new StringBuilder();
    StringBuilder out=new StringBuilder();
    
    
    ///////////////////////////////////////////////////////////////////////////////////
    //for data in comments, one dataset can be split to
    //multiple CommentENSDF datasets with one dataset 
    //for each reference
    LinkedHashMap<ENSDF,Vector<CommentENSDF>> ensdfCommentDataMap=new LinkedHashMap<ENSDF,Vector<CommentENSDF>>();
    LinkedHashMap<ENSDF,EnsdfAverageSetting> ensdfAverageSettingMap=new LinkedHashMap<ENSDF,EnsdfAverageSetting>();
    
    class CommentENSDF{
        //all level and gamma indexes must be kept the same as those in the original dataset
        //for levels and gammas not available in this reference, all values are set to be empty
        Vector<CommentLevel> CLevels=new Vector<CommentLevel>();
        Vector<CommentGamma> unpCGammas=new Vector<CommentGamma>();
        Vector<CommentGamma> allCGammas=new Vector<CommentGamma>();
        
        String DSID="",NUCID="";
        String ref="",ref2="";
        
        CommentENSDF(ENSDF ens){
            
            DSID=ens.DSId0();
            NUCID=ens.nucleus().nameENSDF();
            for(int i=0;i<ens.nUnpGammas();i++) {
                unpCGammas.add(new CommentGamma());             
            }
            
            for(int i=0;i<ens.nLevels();i++) {
                Level lev=ens.levelAt(i);
                CommentLevel CLev=new CommentLevel();
                for(int j=0;j<lev.nGammas();j++)
                    CLev.CGammas.add(new CommentGamma());
                
                CLevels.add(CLev);
                
                allCGammas.addAll(CLev.CGammas);
            }
            
            allCGammas.addAll(unpCGammas);//same order as the unplaced gammas being added in ENSDF
        }
    }
    class CommentLevel{
        int nGamWRI=0;
        String ES="",DES="";
        String TS="",DTS="",TU="";
        Vector<CommentGamma> CGammas=new Vector<CommentGamma>();
        CommentLevel(){}
    }
    class CommentGamma{
        String ES="",DES="";
        String RI="",DRI="";
        String BR="",DBR="";
        CommentGamma(){}
    }
    
    public LinkedHashMap<ENSDF,Vector<CommentENSDF>> ensdfCommentDataMap(){
        return ensdfCommentDataMap;
        
    }
    Vector<CommentENSDF> getCommentENSDFs(ENSDF ens) {
        return ensdfCommentDataMap.get(ens);
    }
    
    CommentENSDF getCommentENSDF(ENSDF ens,String ref) {
        try {
            Vector<CommentENSDF> out=ensdfCommentDataMap.get(ens);
            for(CommentENSDF cens:out) {
                if(cens.ref.equals(ref))
                    return cens;
            }
        }catch(Exception e) {
            //e.printStackTrace();
        } 
        
        return null;
    }
    
    boolean hasCommentENSDF(ENSDF ens,String ref) {
        return getCommentENSDF(ens,ref)!=null;
    }
    
    
    /*
     * Check if a CommentENSDF with ref exists
     * If not, insert a new one with ref
     */
    private CommentENSDF checkCommentENSDF(ENSDF ens,String ref) {
        try {
            CommentENSDF cens=getCommentENSDF(ens,ref);
            
            if(cens==null) {
                cens=new CommentENSDF(ens);
                cens.ref=ref;
                
                if(EnsdfUtil.isKeynumber(ref))
                	cens.ref2=ens.DSId0();
                
                Vector<CommentENSDF> censV=getCommentENSDFs(ens);
                if(censV==null) {
                    censV=new Vector<CommentENSDF>();               
                    ensdfCommentDataMap.put(ens, censV);
                }
                
                censV.add(cens);   
            }

            return cens;
        }catch(Exception e) {
            e.printStackTrace();
        }
        
        return null;
    }
    ///////////////////////////////////////////////////////////////////////////////////
    
    
    
    public AverageValuesInComments(ENSDF ens) {
        ensdfsV.clear();
        ensdfsV.add(ens);
        
        dowork();
    }
    
    public AverageValuesInComments(Vector<ENSDF> ensV) {
        ensdfsV.clear();
        ensdfsV.addAll(ensV);
        
        dowork();
    }
    
    public AverageValuesInComments(Vector<ENSDF> ensV,LinkedHashMap<ENSDF,EnsdfAverageSetting> averageSettingMap) {
        ensdfsV.clear();
        ensdfsV.addAll(ensV);
        
        ensdfAverageSettingMap.clear();
        ensdfAverageSettingMap.putAll(averageSettingMap);
        
        dowork();
    }
    
    public void reset() {
        ensdfsV.clear();
        rpt.setLength(0);
        out.setLength(0);
    }
    
    private void dowork() {
        parseCommentData();
        
        for(int i=0;i<ensdfsV.size();i++) {
            doAverage(ensdfsV.get(i));
        }
    }
    
    private void doAverage(ENSDF ens) {
        LinkedHashMap<Comment,Record> comRecordMap=new LinkedHashMap<Comment,Record>();
        
        //for(int i=0;i<ens.comV().size();i++) {
        //    Comment com=ens.comV().get(i);
        //}
        
        for(int i=0;i<ens.unpGammas().size();i++) {
            Gamma gam=ens.unpGammaAt(i);
            for(int j=0;j<gam.commentV().size();j++) {
                Comment com=gam.commentAt(j);
                if(com.head().isEmpty())
                    continue;
                
                comRecordMap.put(com, gam);
            }
        }
        
        for(int i=0;i<ens.nLevels();i++) {
            Level lev=ens.levelAt(i);
            for(int j=0;j<lev.commentV().size();j++) {
                Comment com=lev.commentAt(j);
                if(com.head().isEmpty())
                    continue;
                
                comRecordMap.put(com, lev);
                
                //System.out.println("1  EL="+lev.ES()+" "+comRecordMap.get(com).isLevel());
            }
            
            for(int j=0;j<lev.nGammas();j++) {
                Gamma gam=lev.gammaAt(j);
                for(int k=0;k<gam.commentV().size();k++) {
                    Comment com=gam.commentAt(k);
                    if(com.head().isEmpty())
                        continue;
                    
                    comRecordMap.put(com, gam);
                    
                    //System.out.println("2 EG="+gam.ES()+" "+comRecordMap.get(com).isLevel());
                }
            }
        }
        
        LinkedHashMap<Level,Vector<AverageReport>> levelAvgReportMap=new LinkedHashMap<Level,Vector<AverageReport>>();
        LinkedHashMap<Gamma,Vector<AverageReport>> gammaAvgReportMap=new LinkedHashMap<Gamma,Vector<AverageReport>>();
        LinkedHashMap<AverageReport,Boolean> avgReportTypeMap=new LinkedHashMap<AverageReport,Boolean>();
        
        String str="",entryName="",prefix="",lineType="";
        String NUCID=ens.nucleus().nameENSDF();
        double weightLowerLimit=0;//=0.0-0.99, lower limit of weight for a data point to be considered as a good point
        
        Record prevRecord=null;
        String keyword="AVERAGE OF";
        
        StringBuilder tempRPT=new StringBuilder();
        for(Comment c:comRecordMap.keySet()) {
              
            //System.out.println(" "+comRecordMap.get(c).isLevel()+" "+comRecordMap.get(c).isGamma());
            
            Vector<DataPoint> dpsV=parseDatapointsInComments(c,keyword);
            if(dpsV.size()<=1)
                continue;
            
            //debug
            //System.out.println("E="+comRecordMap.get(c).ES()+" Type="+c.type()+" head="+c.head()+" body="+c.body());
            
            lineType="c"+c.type();
            
            prefix=Str.makeENSDFLinePrefix(NUCID, lineType);
            
            //System.out.println(" prefix="+prefix+" lineType="+lineType+"*");
            
            entryName=c.head();
            AverageReport ar=new AverageReport(dpsV,entryName,prefix,weightLowerLimit);
            str=ar.getReport();
            
            
            //average report
            if(str.length()>0){                
                
                if(c.rawBody().toUpperCase().contains("UNWEIGHTED AVERAGE OF"))
                    avgReportTypeMap.put(ar, false);
                else
                    avgReportTypeMap.put(ar, true);
                
                Record rec=comRecordMap.get(c);

                boolean isLevel=true;
                if(rec.isLevel()) {
                    Level lev=(Level)rec;
                    Vector<AverageReport> arV=levelAvgReportMap.get(lev);
                    if(arV==null) {
                        arV=new Vector<AverageReport>();
                        levelAvgReportMap.put(lev,arV);
                    }
                    
                    arV.add(ar);
                    
                }else if(rec.isGamma()) {
                    isLevel=false;
                    
                    Gamma gam=(Gamma)rec;
                    Vector<AverageReport> arV=gammaAvgReportMap.get(gam);
                    if(arV==null) {
                        arV=new Vector<AverageReport>();
                        gammaAvgReportMap.put(gam,arV);
                    }
                    
                    arV.add(ar);
                }
                
                if(rec==prevRecord)
                    tempRPT.append(str);
                else {
                    String subtitle="";
                    if(isLevel) {
                        subtitle="\n<For Level="+rec.ES()+">"; 
                    }else {
                        Gamma gam=(Gamma)rec;
                        if(gam.ILI()>0) {               
                            subtitle="\n<For Gamma="+gam.ES()+" from Level="+gam.ILS()+">";
                        }else {
                            subtitle="\n<For unplaced Gamma="+gam.ES()+">";
                        }
                    }
                        
                    subtitle=Str.repeat("-", 80)+subtitle;
                    tempRPT.append(subtitle+"\n"+str);  
                }

                
                prevRecord=rec;
            }
            
            
        }

        String title="*** average values in dataset: <"+ens.DSId()+"> ***\n\n";
        rpt.append(title);
        if(tempRPT.length()>0) {
            rpt.append(tempRPT);
        }else {
            rpt.append("  Nothing is averaged.");
        }
        
        //for output
        String s="",ds="";
        Level currLev=null;
        int nLev=0;
        int nGam=0;//gamma count in a level or unplaced
        
        for(int i=0;i<ens.lines().size();i++) {
            String line=ens.lines().get(i);
            lineType=line.substring(5,8);
            
            if(lineType.equals("  L")) {
                nGam=0;               
                currLev=ens.levelAt(nLev);
                
                Vector<AverageReport> arV=levelAvgReportMap.get(currLev);
                if(arV!=null) {
                    for(AverageReport ar:arV) {
                        String name=ar.getRecordName();
                                             
                        //boolean isWeighted=avgReportTypeMap.get(ar);
                        s=ar.adoptedValStr();
                        ds=ar.adoptedUncStr();
                           
                        if(name.equals("E")) {                                     
                            line=EnsdfUtil.replaceEnergyOfRecordLine(line, s, ds);
                        }else if(name.equals("T")) {
                            line=EnsdfUtil.replaceHalflifeOfLevelLine(line, s, ds, currLev.T12Unit());//average T
                        }
                            
                    }
                }
                
                nLev++;
            }else if(lineType.equals("  G")) {
                Gamma gam=null;
                if(currLev==null)
                    gam=ens.unpGammaAt(nGam);
                else {
                    //System.out.println("## line="+line+" level="+currLev.ES()+" ngams="+currLev.nGammas());
                    
                    gam=currLev.gammaAt(nGam);
                }
  
                Vector<AverageReport> arV=gammaAvgReportMap.get(gam);
                if(arV!=null) {                    
                    for(AverageReport ar:arV) {
                        String name=ar.getRecordName();
                        
                        //boolean isWeighted=avgReportTypeMap.get(ar);
                        s=ar.adoptedValStr();
                        ds=ar.adoptedUncStr();

                        //System.out.println("E="+gam.ES()+" type="+name+" isWeighted="+isWeighted);
                        
                        if(name.equals("E")) {     
                            line=EnsdfUtil.replaceEnergyOfRecordLine(line, s, ds); 
                        }else if(name.equals("RI")) {
                            line=EnsdfUtil.replaceRIOfGammaLine(line, s, ds);
                        } 
                    }
                }
                
                nGam++;
            }
            line=Str.fixLineLength(line, 80);
            out.append(line+"\n");
        }
        
        
        if(ens.nLines()>0)
            out.append(Str.fixLineLength(" ",80)+"\n\n");
        
        //System.out.println(out);

    }

    
    private void addUnpCommentGammaData(ENSDF ens,int ig,String recordName,DataPoint dp) {
        try {
            String ref=dp.label();
            CommentENSDF cens=checkCommentENSDF(ens,ref);
            
            CommentGamma CGam=cens.unpCGammas.get(ig);
            if(recordName.equals("E")) {
                CGam.ES=dp.s();
                CGam.DES=dp.ds();
            }else if(recordName.equals("RI")) {
                CGam.RI=dp.s();
                CGam.DRI=dp.ds();
            }
        }catch(Exception e) {
            //e.printStackTrace();
        }
    }
    
    private void addCommentGammaData(ENSDF ens,int il,int ig,String recordName,DataPoint dp) {
        try {
            String ref=dp.label();
            CommentENSDF cens=checkCommentENSDF(ens,ref);
            
            //System.out.println("##dsid="+ens.DSId0()+" name="+recordName+" value="+dp.s()+" ref="+dp.label()+" nl="+(cens==null));
            
            CommentLevel CLev=cens.CLevels.get(il);
            CommentGamma CGam=CLev.CGammas.get(ig);
            if(recordName.equals("E")) {
                CGam.ES=dp.s();
                CGam.DES=dp.ds();
            }else if(recordName.equals("RI")) {
                CGam.RI=dp.s();
                CGam.DRI=dp.ds();
                
                CLev.nGamWRI++;
            }
            
                     
            //System.out.println("dsid="+ens.DSId0()+" lev="+ens.levelAt(il).ES()+" gam="+ens.levelAt(il).gammaAt(ig).ES()
            //        +" clev="+CLev.ES+" dp="+dp.ds()+" ref="+ref+" "+this.ensdfCommentDataMap.size());
            
        }catch(Exception e) {
            //e.printStackTrace();
        }
    }  
    
    private void addCommentLevelData(ENSDF ens,int il,String recordName,DataPoint dp) {
        try {
            String ref=dp.label();
            CommentENSDF cens=checkCommentENSDF(ens,ref);
            
            CommentLevel CLev=cens.CLevels.get(il);
            if(recordName.equals("E")) {
                CLev.ES=dp.s();
                CLev.DES=dp.ds();
            }else if(recordName.equals("T")) {
                CLev.TS=dp.s();
                CLev.DTS=dp.ds();
                CLev.TU=dp.unit();
            }

        }catch(Exception e) {
            //e.printStackTrace();
        }
    }  

    
    private void parseCommentData() {
        for(int i=0;i<ensdfsV.size();i++) {
            ENSDF ens=ensdfsV.get(i);
            EnsdfAverageSetting setting=null;
            setting=ensdfAverageSettingMap.get(ens);

            parseCommentData(ens,setting);
        }
    }
    
    private void parseCommentData(ENSDF ens,EnsdfAverageSetting setting) {
        String defaultKeyword="AVERAGE OF";
        String ELkeyword="";
        String Tkeyword="";
        String EGkeyword="";
        String RIkeyword="";
        if(setting!=null) {
            ELkeyword=setting.getCustomKeyword("EL");
            Tkeyword=setting.getCustomKeyword("T");
            EGkeyword=setting.getCustomKeyword("EG");
            RIkeyword=setting.getCustomKeyword("RI");
        }


        
        for(int i=0;i<ens.unpGammas().size();i++) {
            Gamma gam=ens.unpGammaAt(i);
            for(int j=0;j<gam.commentV().size();j++) {
                Comment com=gam.commentAt(j);
                String chead=com.head();
                String body=com.rawBody();
                              
                String recordName=chead;
                String keyword=defaultKeyword;
                
                //If custom keyword available, use it to extract data values in 
                //comments no matter if there is a comment  head or not
                //If not, then use default keyword to extract data values only in
                //comments with comment head
                boolean done=false;
                if(!done && !EGkeyword.isEmpty() && (chead.isEmpty()||chead.equals("E")) ) {
                    keyword=EGkeyword;
                    recordName="E"; 
                    if(body.contains(EGkeyword)) 
                        done=true;                       

                }
                if(!done && !RIkeyword.isEmpty() && (chead.isEmpty()||chead.equals("RI")) ) {
                    keyword=RIkeyword;
                    recordName="RI";
                    if(body.contains(RIkeyword)) 
                        done=true;                     

                }
                
                if(!done) {
                    if(chead.isEmpty() || !keyword.equals(defaultKeyword))
                        continue;
                }
                
                Vector<DataPoint> dpsV=parseDatapointsInComments(com,keyword);
                if(dpsV.size()<1)
                    continue;

                for(DataPoint dp:dpsV) {                    
                    addUnpCommentGammaData(ens,i,recordName,dp);
                }
            }
        }
        
        for(int i=0;i<ens.nLevels();i++) {
            Level lev=ens.levelAt(i);
            for(int j=0;j<lev.commentV().size();j++) {
                Comment com=lev.commentAt(j);
                String chead=com.head();
                String body=com.rawBody();
                
                String recordName=chead;
                String keyword=defaultKeyword;
                
                boolean done=false;
                if(!done && !ELkeyword.isEmpty() && (chead.isEmpty()||chead.equals("E")) ) {
                    keyword=ELkeyword;
                    recordName="E"; 
                    if(body.contains(ELkeyword)) 
                        done=true;                       

                }
                if(!done && !Tkeyword.isEmpty() && (chead.isEmpty()||chead.equals("T")) ) {
                    keyword=Tkeyword;
                    recordName="T";
                    if(body.contains(Tkeyword)) 
                        done=true;                     

                }
                
                if(!done) {
                    if(chead.isEmpty() || !keyword.equals(defaultKeyword))
                        continue;
                }

                
                Vector<DataPoint> dpsV=parseDatapointsInComments(com,keyword);

                //System.out.println("dsid="+ens.DSId0()+" lev="+lev.ES()+" ndp="+dpsV.size());
                
                if(dpsV.size()<1)
                    continue;
                
                for(DataPoint dp:dpsV) {
                    addCommentLevelData(ens,i,recordName,dp);
                }
                
                //System.out.println("1  EL="+lev.ES()+" "+comRecordMap.get(com).isLevel());
            }
            
            for(int j=0;j<lev.nGammas();j++) {
                Gamma gam=lev.gammaAt(j);
                for(int k=0;k<gam.commentV().size();k++) {
                    Comment com=gam.commentAt(k);
                    String chead=com.head();
                    String body=com.rawBody();
                    
                    String recordName=chead;
                    String keyword=defaultKeyword;
                       
                    boolean done=false;
                    if(!done && !EGkeyword.isEmpty() && (chead.isEmpty()||chead.equals("E")) ) {
                        keyword=EGkeyword;
                        recordName="E"; 
                        if(body.contains(EGkeyword)) 
                            done=true;                       

                    }
                    if(!done && !RIkeyword.isEmpty() && (chead.isEmpty()||chead.equals("RI")) ) {
                        keyword=RIkeyword;
                        recordName="RI";
                        if(body.contains(RIkeyword)) 
                            done=true;                     

                    }
                    
                    
                    //System.out.println(" body="+body+"  keyword="+keyword+"  RIkeyword="+RIkeyword +" done="+done);
                    
                    if(!done) {
                        if(chead.isEmpty() || !keyword.equals(defaultKeyword))
                            continue;
                    }

                    
                    Vector<DataPoint> dpsV=parseDatapointsInComments(com,keyword);
                    
                    //System.out.println("dsid="+ens.DSId0()+" lev="+lev.ES()+" gam="+gam.ES()+" ndp="+dpsV.size());
                    
                    if(dpsV.size()<1)
                        continue;

                    for(DataPoint dp:dpsV) {
                        int levelIndex=i;
                        int gammaIndex=j;
                        
                        //System.out.println("dsid="+ens.DSId0()+" lev="+lev.ES()+" gam="+gam.ES()+" name="+recordName+" value="+dp.s()+" ref="+dp.label());
                        
                        addCommentGammaData(ens,levelIndex,gammaIndex,recordName,dp);
                    }
                    //System.out.println("2 EG="+gam.ES()+" "+comRecordMap.get(com).isLevel());
                }
            }
        } 
        
        //convert RI to BR (PN=6) for each comment dataset
        Vector<CommentENSDF> censV=getCommentENSDFs(ens);
        if(censV!=null) {
            for(CommentENSDF cens:censV) {
                for(CommentLevel CLev:cens.CLevels) {
                    if(CLev.nGamWRI==0)
                        continue;
                    
                    //for a level with only one gamma which has a RI given as a limit, like RI<12
                    //goodMaxRI=0, iGoodMaxRI=-1, but maxRI=12, iMaxRI=0
                    //for goodMax, ri should not be a limit
                    int iGoodMaxRI=-1,iMaxRI=-1;
                    float goodMaxRI=0,maxRI=0;
                    for(int i=0;i<CLev.CGammas.size();i++) {
                        CommentGamma CGam=CLev.CGammas.get(i);
                        String ris=CGam.RI.trim();
                        String dris=CGam.DRI.trim();
                        if(ris.isEmpty())
                            continue;
                        
                        float ri=-1;
                        try {
                            ri=Float.parseFloat(ris);                          
                            if(ri>maxRI) {
                                maxRI=ri;
                                iMaxRI=i;
                            }

                            if(!dris.isEmpty())
                                Float.parseFloat(dris);

                        }catch(Exception e) {
                            continue;
                        }
                        
                        if(ri>goodMaxRI) {
                            goodMaxRI=ri;
                            iGoodMaxRI=i;
                        }
                        
                    }
                    
                    if(CLev.nGamWRI==1) {
                        CommentGamma CGam=CLev.CGammas.get(iMaxRI);
                        CGam.BR="100";
                        CGam.DBR="";
                        continue;
                    }
                    
                    //all RI are given as limit
                    if(iGoodMaxRI<0 && goodMaxRI>0) {
                        iGoodMaxRI=iMaxRI;
                        goodMaxRI=maxRI;
                    }

                    Float norm=100/goodMaxRI;
                    
                    //normalize
                    for(int i=0;i<CLev.CGammas.size();i++) {
                        CommentGamma CGam=CLev.CGammas.get(i);
                        String ris=CGam.RI.trim();
                        String dris=CGam.DRI.trim();
                        if(ris.isEmpty())
                            continue;
                        
                        SDS2XDX s2x=new SDS2XDX(ris,dris);
                        s2x.setErrorLimit(CheckControl.errorLimit);
                        
                        s2x=s2x.multiply(norm);
                        
                        CGam.BR=s2x.s();
                        CGam.DBR=s2x.ds();
                        
                        //System.out.println("  RI="+CGam.RI+"  DRI="+CGam.DRI+" BR="+CGam.BR+" DBR="+CGam.DBR);
                        
                    }                

                }
            }            
        }

    }
    
    /*
     * keyword is for identifying if the comment contains wanted data values.
     * It should be set as very unique words of phrases, like default keyword="AVERAGE OF".
     * It is not case-sensitive and the values must be listed after the keyword
     */
    private Vector<DataPoint> parseDatapointsInComments(Comment c,String keyword){
        return Util.parseDatapointsInComments(c,keyword);
    }

    public void writeReport(String rptFilePath) {
        PrintWriter output;
        try {
            output = new PrintWriter(new File(rptFilePath));
            output.write(rpt.toString());
            output.close();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public void writeOutput(String outFilePath) {
        PrintWriter output;
        try {
            output = new PrintWriter(new File(outFilePath));
            output.write(out.toString());
            output.close();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
    }
    
    public static void main(String[] args)throws Exception{
        Translator.init();
 
        ensdfparser.nds.ensdf.MassChain data=new ensdfparser.nds.ensdf.MassChain();
        data.load(new java.io.File("/Users/chenj/work/evaluation/ENSDF/check/check.ens"));
        
        Vector<ENSDF> ensdfsV=new Vector<ENSDF>();
        for(int i=0;i<data.nENSDF();i++)
            ensdfsV.add(data.getENSDF(i));
        
        consistency.base.AverageValuesInComments avg=new consistency.base.AverageValuesInComments(ensdfsV);
        
        for(ENSDF ens:avg.ensdfCommentDataMap.keySet()) {
            Vector<CommentENSDF> censV=avg.ensdfCommentDataMap.get(ens);
            System.out.println("\n\n***** ENSDF DSID: "+ens.DSId0()+"   # of Comment datasets: "+censV.size());
            for(CommentENSDF cens:censV) {
                System.out.println("---- Comment ENSDF: "+cens.ref);
                for(int i=0;i<cens.CLevels.size();i++) {
                    CommentLevel CLev=cens.CLevels.get(i);
                    Level lev=ens.levelAt(i);
                    System.out.println(String.format("   CLevel: E=%15s T=%20s, original: E=%15s T=%20s",CLev.ES+" "+CLev.DES,CLev.TS+" "+CLev.TU+" "+CLev.DTS,
                            lev.ES()+" "+lev.DES(),lev.halflife()));
                    for(int j=0;j<CLev.CGammas.size();j++) {
                        CommentGamma CGam=CLev.CGammas.get(j);
                        Gamma gam=lev.gammaAt(j);
                        System.out.println(String.format("      CGamma: E=%15s RI=%15s, original: E=%15s RI=%15s",CGam.ES+" "+CGam.DES,CGam.RI+" "+CGam.DRI,
                                gam.ES()+" "+gam.DES(),gam.RIS()+" "+gam.DRIS())); 
                    }
                }
                
            }
        }
    }
}
