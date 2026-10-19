/*
 * Revised for checking ENSDF format
 *         check the consistency with the values in adopted
 *         of the values of M,MR,JP in decay dataset, 
 *         reaction datasets (mainly gamma-ray datasets).
 *         Mar22 2012 by Jun
 *         
 * Last update: 12/13/2018, Jun Chen
 */

package consistency.main;

import java.io.File;
import java.util.Vector;

import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

import consistency.base.CheckControl;
import consistency.ui.GroupingResultsFrame;
import consistency.ui.MasterFrame;
import ensdfparser.nds.ensdf.MassChain;
import ensdfparser.nds.latex.Translator;

import javax.swing.WindowConstants;

/**
 * Program entry point.
 */
public class Main {
    
    /**main file, essentially just initializes things and then opens up the GUI */
    public static void main(String[] args)throws Exception{
        Setup.load();
        
        Translator.init();
        
        if(args.length==0)
            startUI();
        else
            runByCommand(args);
        
        
		//SDS2XDX s2x=EnsdfUtil.scaleSDS(new SDS2XDX("3.1", "+37-11"),60);
				
		//System.out.println("   ############### "+s2x.s()+"   "+s2x.ds());
		
        //String s1="H:\\work\\evaluation\\ENSDF\\A123\\finished\\Xe123\\Xe123_adopted.ens";
        //String s2="H:\\work\\evaluation\\ENSDF\\A123\\finished\\Xe123\\Xe123_adopted.test";
        //EnsdfUtil.cleanENSDFFile(s1,s2,true);

        //testUI(frame);    	
    	//test1();
    	//test();
        
        /*
        SDS2XDX s2x=new SDS2XDX();
        s2x.setValues("1.323", "0");
        s2x=s2x.add(new SDS2XDX("1496.20","10"));
        System.out.println(" add  x="+s2x.x()+"  dx="+s2x.dx()+"  s="+s2x.s()+"  ds="+s2x.ds());
        
        s2x=new SDS2XDX();
        s2x.setValues("0.001125", "AP");
        System.out.println(" multiply1 x="+s2x.x()+"  dx="+s2x.dx()+"  s="+s2x.s()+"  ds="+s2x.ds());

        s2x=s2x.multiply(new SDS2XDX("1.0",""));
        System.out.println(" multiply2 x="+s2x.x()+"  dx="+s2x.dx()+"  s="+s2x.s()+"  ds="+s2x.ds());
        */
    	
    	//XDX2SDS xs=new XDX2SDS();
    	//double x=0.007459869, dxu=5.2444584E-4, dxl=4.6832912E-4;
    	//xs.setErrorLimit(50);
    	//xs.setNsig(2);
    	//xs.setValues(x,dxu,dxl);
        //debug
        //System.out.println("#####  x="+x+" dxu="+dxu+" dxl="+dxl+"  xs.sl="+xs.sl()+"  xs.su="+xs.su()+" s="+xs.s()+" ss="+xs.ss()+" ds="+xs.ds()+" dsl="+xs.dsl()+" dsu="+xs.dsu());
       
    }
    
    
    public static void startUI()throws Exception{
    	if(!Translator.hasInit())
    		Translator.init();
    	
        //Translator.test();

        //launch master control panel
        try{
       	
        	UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
        	//UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        	//UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        }catch(Exception e1){
            try{
                for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                	
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                    else{
                    	UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
                    }
                }
                
            }catch(Exception e2){
            	
            }        	
        }
        Run run=new Run();
        
        MasterFrame frame=new MasterFrame();
        frame.setNameAndVersion(CheckControl.name,CheckControl.version);
        
        frame.setTitle("ENSDF format and consistency check (version "+run.version()+")");
        frame.setVisible(true);
        frame.setResizable(false);
        
    }
    
    @SuppressWarnings("unused")
	public static void runByCommand(String[] args) throws Exception{
        //Control settings are reset in Run() constructor
        Run run=new Run();
        run.setRedirectOutputToFile(false);
        
        System.out.println("Start runConsistencyCheck(args): for usage, run with -USAGE argument");
        if(args.length==0){
            System.out.println("Error: no ENSDF file path is given!");
            return;
        }
        
        
        String s=args[0].toUpperCase();
        if(args.length==1 && (s.contains("-HELP")||s.contains("-USAGE"))){
            System.out.println(run.usage());        
            return;
        }
        
        
        boolean openGUI=false;
        String outputDir="",inputDir="";  
        String inputfilePath="";
        int count=0;
        
        MassChain data=null;
        
        if(args[0].charAt(0)=='-'){
            System.out.println("No input ENSDF file is specified! The GUI will open\n");
            openGUI=true;
        }else {
            File f=new File(args[0]);
            if(!f.exists()){
                System.out.println("Error: ENSDF file does not exist or wrong file path:");
                System.out.println(" "+f.getAbsolutePath());
                return;
            }
            
            inputfilePath=args[0];  
            count++;
            
            File parentFile=f.getAbsoluteFile().getParentFile();
            if(parentFile==null)
                inputDir=".";
            else
                inputDir=parentFile.getAbsolutePath();
            
            outputDir=inputDir;
            
            data=new MassChain();
            data.load(f);
            
            Vector<File> filesV=new Vector<File>();
            filesV.add(f);
            
            run.setFilesV(filesV);
    
            if(args.length>1 && ((String)args[1]).trim().charAt(0)!='-'){
                outputDir=args[1];//output path
                
                File fout=new File(outputDir);
                if(!fout.exists()) {
                    System.out.println("Warning: output path does not exist and will be created.:");
                    System.out.println("  "+f.getAbsolutePath());
                    System.out.flush();
                    fout.mkdir();
                }
                
                count++;
            }
        }

        
        //other options, all options begin with '-'
        //skip the option otherwise
        for(int i=count;i<args.length;i++){
            s=((String)args[i]).trim();
            if(s.charAt(0)!='-')
                continue;
            
            while(s.length()>0 && s.charAt(0)=='-')
                s=s.substring(1);
            
            s=s.trim();
            if(s.length()<=0)
                continue;
            
            String s0=s;
            s=s.toUpperCase();
            
            if(s.equals("RPT")){
                consistency.base.CheckControl.writeRPT=true;
            }else if(s.indexOf("LEV")==0){
                consistency.base.CheckControl.writeLEV=true;
            }else if(s.indexOf("GAM")==0){
                consistency.base.CheckControl.writeGAM=true;
            }else if(s.indexOf("GLE")==0){
                consistency.base.CheckControl.writeGLE=true;
            }else if(s.indexOf("MRG")==0){
                consistency.base.CheckControl.writeMRG=true;
            }else if(s.indexOf("AVG")==0){
                consistency.base.CheckControl.writeAVG=true;
            }else if(s.indexOf("FED")==0){
                consistency.base.CheckControl.writeFED=true;
            }else if(s.indexOf("ALL")==0){
                consistency.base.CheckControl.writeRPT=true;
                consistency.base.CheckControl.writeLEV=true;
                consistency.base.CheckControl.writeGAM=true;
                consistency.base.CheckControl.writeGLE=true;
                consistency.base.CheckControl.writeMRG=true;
                consistency.base.CheckControl.writeAVG=true;
                consistency.base.CheckControl.writeFED=true;
            }else if(s.startsWith("WORKDIR=") || s.startsWith("CURR")) {
            	String dir="";
            	if(s.startsWith("WORKDIR")) {
                	int n=s0.indexOf("=");
                	dir=s0.substring(n+1).trim();
            	}
            	
            	File f1=null;
            	if(dir.isEmpty()) {
            		f1=new File(System.getProperty("user.dir"));
            	}else {
            		f1=new File(dir);
            	}
            	
            	dir=f1.getAbsolutePath();
            	if(!f1.exists()) {
            		System.out.println("Error: dir="+dir+" does not exist!");
            		return;
            	}
            	
            	
            	consistency.base.CheckControl.workdir=dir;
            	outputDir=dir;
            	//System.out.println(dir);
            }else if(s.startsWith("ERRORLIMIT=")){
            	//skip do nothing
            }else if((s.contains("HELP")||s.contains("USAGE"))) {
                System.out.println(run.usage());        
                return;
            }else {
        		System.out.println("Error: invalid argument: "+s0);
        		return;
            }
        }
               
        File dir=new File(outputDir);
        if(!dir.exists())
            dir.mkdir();
           
        if(!outputDir.isEmpty()) {
        	consistency.main.Setup.filedir=inputDir;     
        	consistency.main.Setup.outdir=outputDir;
        	consistency.main.Setup.save();
        }

        if(openGUI) {
        	startUI();
        }else {
            String outfilename=Setup.outdir+File.separator+Integer.toString(data.getENSDF(0).nucleus().A());
            
            run.setOutFilename(outfilename);
            
            try{

                run.checkMassChain(outfilename,data);

                String outexts="";
                if(consistency.base.CheckControl.writeRPT) outexts+=":rpt";
                if(consistency.base.CheckControl.writeLEV) outexts+=":lev";
                if(consistency.base.CheckControl.writeGAM) outexts+=":gam";
                if(consistency.base.CheckControl.writeGLE) outexts+=":gle";
                if(consistency.base.CheckControl.writeMRG) outexts+=":mrg";
                if(consistency.base.CheckControl.writeAVG) outexts+=":avg";
                if(consistency.base.CheckControl.writeFED) outexts+=":fed";

                if(outexts.length()>0){
                    outexts=outexts.substring(1);
                    Setup.outexts=outexts;
                    Setup.save();
                }
                    
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }    

    
    static void testUI(ame.ui.MasterFrame frame){
    	
    	MassChain data=new MassChain();
    	consistency.main.Run run=new consistency.main.Run();
    	try {
			data.load(new File("H:\\work\\evaluation\\ENSDF\\check\\merged.ens"));
			//data.load(new File("/home/junchen/work/evaluation/ENSDF/check/merged.ens"));
			
			run.checkMassChain("test", data);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

    	
        GroupingResultsFrame settingFrame=new GroupingResultsFrame(data,run);
        settingFrame.setTitle("Adopted Levels and Gammas from grouping");
        
        if(frame!=null)
        	settingFrame.setLocation(frame.getX()+frame.getWidth()-120,frame.getY()+70);
        
        settingFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        settingFrame.setVisible(true);
    }
}
