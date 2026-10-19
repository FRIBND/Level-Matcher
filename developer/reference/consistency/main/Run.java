/*
 * Created 2018 Jun Chen
*/
package consistency.main;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;

import javax.swing.JTextArea;

import consistency.base.CheckControl;
import consistency.base.ConsistencyCheck;
import consistency.base.EnsdfGroup;
import ensdfparser.base.BaseRun;
import ensdfparser.ensdf.ENSDF;
import ensdfparser.nds.ensdf.*;
import ensdfparser.nds.util.Str;
import formatcheck.check.FormatCheck;
import keynocheck.main.KeynumberCheck;
import keynocheck.main.KeynumberControl;
import keynocheck.main.Setup;

/**
 * Contains functions for communicating between checking procedures and user interface
 */
public class Run extends BaseRun<JTextArea>{
    private ConsistencyCheck consistencyCheck;
    
    private FormatCheck formatCheck;
    private KeynumberCheck keynoCheck;
    
	private String outfilename=""; //path+name of output file (no extension)
	
    Vector<File> filesV;
    
    //static {errorLimit=CheckControl.errorLimit;}
    
	public Run(){	       
	    this.redirectToFile=false;	       
	    this.redirectToMessenger=false;
        super.init();
	}

    
    /** check format and consistency of records in the input ENSDF file 
     * @throws Exception 
     * outfilename includes the full path and the file name, but does not include file extension
     **/    
    @SuppressWarnings("unused")
	public void checkMassChain(String outfilename,MassChain data) throws Exception{
        //find out what operating system is being used, and set the right filename for the script
        String temp=System.getProperty("os.name");
        String os,name="";

        try {
            outfilename=Str.fileNamePrefix(outfilename);//remove file extension if existing
            
            File f=new File(outfilename);
            name=f.getName();
            
        }catch(Exception e) {
            e.printStackTrace();
        }

        if(temp.toLowerCase().contains("linux")||temp.toLowerCase().contains("mac")) os="linux";
        else if(temp.toLowerCase().contains("windows")) os="windows";
        else os="other";       
        
        try {       
            formatCheck=new FormatCheck();
            if(CheckControl.writeRPT) {
                //format check
                printMessage("\nStart checking ENSDF format...");
                formatCheck.check(filesV);
                printMessage("Done checking ENSDF format.");
                
                formatCheck.writeReport(outfilename+".fmt");  
                
                printMessage("See report in <"+name+".fmt>: error and warning messages for format check");
                String s=formatCheck.getStatistics().trim();
                printMessage("*** "+s+" ***"+"\n\n");
            }
        }catch(Exception e) {
            printMessage("\n*** Format check failed ***");
            printMessage("Please check input file.\n\n");
            e.printStackTrace();
            return;
        }
        
        try{          
            consistencyCheck=new ConsistencyCheck(data);   
            consistencyCheck.setDeltaE(50, 50);//deltaEL,deltaEG
            
            printMessage("Start checking ENSDF consistency...");
            consistencyCheck.start();  
            printMessage("Done checking ENSDF consistency.");
            
            printMessage("Starting writing outputs...");
            consistencyCheck.writeOutputs(outfilename);//filename here is path+name only without extension

            printMessage("Following output files have been generated:");
            
            if(CheckControl.writeRPT) {
                printMessage("   "+name+String.format("%-6s",".err:")+" error and warning messages for consistency check");
                //printMessage("   "+name+String.format("%-6s",".fmt:")+" error and warning messages for format check");
            }
            
            if(CheckControl.writeLEV) printMessage("   "+name+String.format("%-6s",".lev:")+" tabulated level information");
            if(CheckControl.writeGAM) printMessage("   "+name+String.format("%-6s",".gam:")+" gammas grouped by gamma energy");
            if(CheckControl.writeGLE) printMessage("   "+name+String.format("%-6s",".gle:")+" gammas grouped by level energy");
            if(CheckControl.writeMRG) printMessage("   "+name+String.format("%-6s",".mrg:")+" grouped lines of all datasets");
            if(CheckControl.writeAVG) printMessage("   "+name+String.format("%-6s",".avg:")+" average results of records");
            if(CheckControl.writeFED) printMessage("   "+name+String.format("%-6s",".fed:")+" feeding gammas of all levels");
        
        }catch (Exception e){
        	printMessage("\n*** Consistency check failed due to format errors ***");
        	printMessage("Please check "+name+".fmt output for format errors in input datasets.\n\n");
        	e.printStackTrace();
        }

        //check keynumber
        try {       
            if(CheckControl.writeRPT) {
                printMessage("\nStart checking keynumbers...\n");
                
                File[] files=new File[filesV.size()];
                filesV.toArray(files);
                
                keynoCheck=new KeynumberCheck(files);
                keynocheck.main.KeynumberControl.useNSRfiles=true;  
                keynocheck.main.KeynumberControl.printDetails=true;
                
                boolean isNSRAccessible=keynoCheck.isNSRAccessible();
    			printMessage("--- Keynumbers in the input ENSDF file will be searched for");
       			printMessage("    in existing NSR files or in the online NSR database");
    			printMessage("    (it could take a while for retrieval from NSR database)");
    			if(!isNSRAccessible){
    				printMessage("*** NSR database is currently not accessible. Check for keynumber relevance is skipped. ***");		
    			}else {
    				keynoCheck.searchKeynumbersForAllNuclides();//search for keynumbers for nuclide from NSR database	
    			}
    			    	
    			//System.out.println(" searchedKeynosV="+keynoCheck.searchedKeynosV.size()+" "+keynoCheck.keynoNSROutputMap().size()+" "+isNSRAccessible);
    			
                keynoCheck.check();  
                
                printMessage("Done checking keynumbers.");
                
    	    	printMessage("\nUpdating default NSR output files..."); 	    	
    	    	keynoCheck.updateDefaultNSRfiles();	    	
    	    	printMessage("Done updating\n\n");
    	    	
                keynoCheck.writeErrorReport(outfilename+"_keynumber.rpt");  
                
                printMessage("See report in <"+name+"_keynumber.rpt>: error and warning messages for keynumber check");
                //String s=keynoCheck.getStatistics().trim();
                //printMessage("*** "+s+" ***"+"\n\n");
            }
        }catch(Exception e) {
        	printMessage("\n*** Keynumber check failed ***");
            printMessage("Please check input file.\n\n");
            e.printStackTrace();
            return;
        }
        
        Date date=new Date();
        SimpleDateFormat sdf=new SimpleDateFormat("E MM/dd/yyyy 'at' hh:mm:ss a zzz");
        printMessage("\nGenerated at: "+sdf.format(date));
    }    
    
    public void checkKeynumber(String outfilename) throws Exception{
        //find out what operating system is being used, and set the right filename for the script
        String temp=System.getProperty("os.name");
        String name="";

        outfilename=Str.fileNamePrefix(outfilename);//remove file extension if existing
        
        try {
            File f=new File(outfilename);
            name=f.getName();
        }catch(Exception e) {
            e.printStackTrace();
        }

        if(temp.toLowerCase().contains("linux")||temp.toLowerCase().contains("mac")) os="linux";
        else if(temp.toLowerCase().contains("windows")) os="windows";
        else os="other";  
        
        try {       
        	           
            printMessage("\n\nStart checking keynumbers...\n");
            
            File[] files=new File[filesV.size()];
            filesV.toArray(files);
            
            keynoCheck=new KeynumberCheck(files);//NSR file is loaded if existing
            KeynumberControl.useNSRfiles=true;  
            KeynumberControl.printDetails=true;
            
            boolean isNSRAccessible=keynoCheck.isNSRAccessible();
			printMessage("--- Keynumbers in the input ENSDF file will be searched for");
   			printMessage("    in existing NSR files if this option is checked,");
			printMessage("    and/or in the online NSR database");
			printMessage("    (it could take a while for retrieval from NSR database)");
			if(!isNSRAccessible){
				printMessage("*** NSR database is currently not accessible. Check for keynumber is skipped. ***");
				return;
			}
			
			keynoCheck.searchKeynumbersForAllNuclides();//search for keynumbers for nuclide from NSR database		
			
            keynoCheck.check();  
 
            printMessage("Done checking keynumbers.");
            
	    	printMessage("\nUpdating default NSR output files..."); 	    	
	    	keynoCheck.updateDefaultNSRfiles();	    	
	    	printMessage("Done updating\n\n");
	    	
            keynoCheck.writeErrorReport(outfilename+"_keynumber.rpt");  
            
            printMessage("See report in <"+name+"_keynumber.rpt>: error and warning messages for keynumber check");
            //String s=keynoCheck.getStatistics().trim();
            //printMessage("*** "+s+" ***"+"\n\n");
            
        }catch(Exception e) {
        	printMessage("\n*** Keynumber check failed ***");
            printMessage("Please check input file.\n\n");
            e.printStackTrace();
            return;
        }
               
        Date date=new Date();
        SimpleDateFormat sdf=new SimpleDateFormat("E MM/dd/yyyy 'at' hh:mm:ss a zzz");
        printMessage("\nGenerated at: "+sdf.format(date));
    }
    
    /** check format of records in the input ENSDF file 
     * @throws Exception 
     * outfilename includes the full path and the file name, but does not include file extension
     **/ 
    public void checkFormat(String outfilename) throws Exception{
        //find out what operating system is being used, and set the right filename for the script
        String temp=System.getProperty("os.name");
        String name="";

        outfilename=Str.fileNamePrefix(outfilename);//remove file extension if existing
        
        try {
            File f=new File(outfilename);
            name=f.getName();
        }catch(Exception e) {
            e.printStackTrace();
        }

        if(temp.toLowerCase().contains("linux")||temp.toLowerCase().contains("mac")) os="linux";
        else if(temp.toLowerCase().contains("windows")) os="windows";
        else os="other";       
  
        
        try {                 
            formatCheck=new FormatCheck(); 
            printMessage("Start checking ENSDF format...");
            formatCheck.check(filesV);
            printMessage("Done checking ENSDF format.");
            
            formatCheck.writeReport(outfilename+".fmt");  
            
            printMessage("See report in <"+name+".fmt>: error and warning messages for format check");
            String s=formatCheck.getStatistics().trim();
            printMessage("*** "+s+" ***"+"\n\n");
            
        }catch(Exception e) {
            printMessage("\n*** Format check failed ***");
            printMessage("Please check input file.\n\n");
            e.printStackTrace();
            return;
        }
               
        Date date=new Date();
        SimpleDateFormat sdf=new SimpleDateFormat("E MM/dd/yyyy 'at' hh:mm:ss a zzz");
        printMessage("\nGenerated at: "+sdf.format(date));
    } 
    
    public String getOutFilename(){return outfilename;}
    public void setOutFilename(String s){outfilename=s;}
    
    public ConsistencyCheck getConsistencyCheck(){return consistencyCheck;}
    public FormatCheck getFormatCheck(){return formatCheck;}
    
    public void resetCheck(){
    	consistencyCheck=new ConsistencyCheck();
    	formatCheck=new FormatCheck();
    }
    
    private void mergeDatasets(Vector<String> lines){
    	
      	if(lines.isEmpty()) {
    			printMessage("Something wrong when merging datasets");
    			return;     	
      	}

      	PrintWriter out=null;
      	String filename=Setup.outdir+dirSeparator+"merged.ens";
    	try{
        	out=new PrintWriter(new File(filename));
        	Str.write(out, lines);        	
    	}catch(FileNotFoundException e){
    		e.printStackTrace();
    	}catch(Exception e){
    		e.printStackTrace();
    	}
    	
    	if(out!=null)
    		out.close();
    	
		printMessage("Datasets are sorted and written into a single file: \n   "+filename);    	
    }
    
    public void mergeDatasets() {

      	if(filesV.size()==0) {		
      		printMessage("No datasets to be merged");
      		return;	
      	}
      	
    	Vector<String> lines=EnsdfUtil.mergeDatasets(filesV);

    	mergeDatasets(lines);
    	
    }
    public void mergeDatasets(MassChain data) {
      	if(data.isEmpty()) {		
      		printMessage("No datasets to be merged");
      		return;	
      	}
      	
    	Vector<String> lines=EnsdfUtil.mergeAllDatasets(data);

    	mergeDatasets(lines);
    }
    
	public void mergeDatasets_old(MassChain data) throws Exception {
		if(data.nENSDF()==0)
			return;
		
		if(consistencyCheck==null || data!=consistencyCheck.getChain()){
			consistencyCheck=new ConsistencyCheck();
		}
				
		Vector<EnsdfGroup> ensdfGroupsV=consistencyCheck.ensdfGroupsV();
		int nGroups=ensdfGroupsV.size();
		if(nGroups==0){
			ensdfGroupsV=consistencyCheck.groupENSDFs(data);//no deep grouping (just grouping datasets by NUCID, no grouping levels and gammas)
			nGroups=ensdfGroupsV.size();
		}
		if(nGroups==0){
			printMessage("No datasets to be merged");
			return;
		}
		
		
		String filename=Setup.outdir+dirSeparator+"merged.ens";
    	PrintWriter out=null;
    	try{
        	out=new PrintWriter(new File(filename));
        	for(int i=0;i<nGroups;i++){
        		EnsdfGroup g=ensdfGroupsV.get(i);
        		Vector<String> lines;
        		
        		if(g.adopted()!=null){
            		lines=g.adopted().lines();
            		Str.write(out, lines);
        			out.write("\n");
        			g.ensdfV().remove(g.adopted());
        		}
 			
        		for(int j=0;j<g.nENSDF();j++){
        			lines=g.ensdfV().get(j).lines();
        			Str.write(out, lines);
        			out.write("\n");
        		}
        	}
        	
    	}catch(FileNotFoundException e){
    		e.printStackTrace();
    	}catch(Exception e){
    		e.printStackTrace();
    	}
    	
    	if(out!=null)
    		out.close();
    	
		printMessage("Datasets are sorted and written into a single file: \n   "+filename);
		
	}  
	
    public void saveENSDF(Vector<String> lines,String filename)throws FileNotFoundException{
    	String msg=EnsdfUtil.saveENSDF(lines, filename);
		if(!msg.isEmpty()){
			printMessage(msg);
			return;
		}  	
    }
    
    //save an ENSDF dataset into a file
    public void saveENSDF(ENSDF ens,String filename) throws FileNotFoundException{
    	saveENSDF(ens.lines(),filename);
    }
    
    public void saveENSDF(ENSDF ens)throws FileNotFoundException{
    	String nucleus=ens.nucleus().En()+ens.nucleus().A();
    	String filename=nucleus+"_"+EnsdfUtil.DSID2Filename(ens.DSId0())+".ens";
    	String path=Setup.outdir;
    	String folderName="A"+ens.nucleus().A()+"_autosaved_datasets";
    	
    	String s=dirSeparator;   	
        
        filename=path+s+folderName+s+filename;
        
        saveENSDF(ens,filename);
    }
    
    public void saveENSDF(ENSDF ens,String path,String extension)throws FileNotFoundException{
    	EnsdfUtil.saveENSDF(ens, path, extension);
    }
    
    public void saveAllENSDF(MassChain data)throws FileNotFoundException{
    	saveAllENSDF(data,"");
    }
    
    public void saveAllENSDF(MassChain data,String extension)throws FileNotFoundException{
    	int A=data.getA();

    	String path=Setup.outdir;
    	String folderName="A"+A+"_autosaved_datasets";
    	
    	path+=dirSeparator+folderName;
    	
    	saveAllENSDF(data,path,extension);
    }
    
    public void saveAllENSDF(MassChain data,String path,String extension)throws FileNotFoundException{
    	String msg=EnsdfUtil.saveAllENSDF(data, path,extension);
    	
    	if(!msg.isEmpty())
    		printMessage(msg);
    }
    
    public void saveAllENSDFByNuclide(MassChain data)throws FileNotFoundException{
        saveAllENSDFByNuclide(data,"");
    }
    
    public void saveAllENSDFByNuclide(MassChain data,String extension)throws FileNotFoundException{
        int A=data.getA();

        String path=Setup.outdir;
        String folderName="A"+A+"_autosaved_datasets";
        
        path+=dirSeparator+folderName;
        
        saveAllENSDFByNuclide(data,path,extension);
    }
    
    public void saveAllENSDFByNuclide(MassChain data,String path,String extension)throws FileNotFoundException{
        String msg=EnsdfUtil.saveAllENSDFByNuclide(data, path,extension);
        
        if(!msg.isEmpty())
            printMessage(msg);
    }
    public void setFilesV(Vector<File> filesV) {
    	this.filesV=filesV;
    }
	
    public Vector<File> filesV(){return filesV;}
    
	///////////////////////////////////////////////////////////////////////
    public String usage(){
    	String s="";
    	s+="------------------------------------------------------------------------------------------\n";
    	s+="To use ConsistencyCheck (for executable=ConsistencyCheck.jar) in a command line:          \n";
    	s+="    java -jar ConsistencyCheck.jar PATH_TO_ENSDF_FILE OUTFILE_PATH [-OPTION1 -OPTION2 ...]\n";
    	s+=" or java -jar ConsistencyCheck.jar [-OPTION1 -OPTION2 ...] to open the program            \n";
    	s+="                                                                                          \n";
    	s+="-OPTION1, -OPTION2,..., are following:                                                    \n";
    	s+="   -RPT              : to generate .err and .wrn outputs for warning and error messages   \n";
    	s+="   -LEV              : to generate .lev output, level data only                           \n";
    	s+="   -GAM              : to generate .gam output, gamma data ordered by E(gamma)            \n";
    	s+="   -GLE              : to generate .gle output, gamma data ordered by level               \n";
    	s+="   -GAM              : to generate .mrg output, all data grouped by level and gamma       \n";
    	s+="   -AVG              : to generate .avg output, average results of E, T, RI from grouping \n";
       	s+="   -FED              : to generate .fed output, feeding gammas to each level              \n";
       	s+="   -ALL              : to generate all outputs above                                      \n";
    	s+="   -WORKDIR=path     : to set the working folder (output path) to be the given path       \n";
    	s+="   -CURRDIR          : to set the working folder (output path) to be the current folder   \n";
    	s+="   -help             : to print usage                                                     \n";
    	s+="   -usage            : same as -help                                                      \n";
    	s+="------------------------------------------------------------------------------------------\n";

    	return s;
    }
    
    public String getLogFilePath(){
    	return Setup.outdir+dirSeparator+logFilename;
    }
            
    public void debug(String text){
    	Str.debug(text);
    }
    
    public String title(){
    	return CheckControl.title;
    }
    
    public String formatCheckTitle(){
        String out="";
        out="** Program for checking ENSDF format (update "+version()+") **\n";
        return out;
    }
    
    /*
    public String version(){
        //Date date=new Date();
        //SimpleDateFormat sdf=new SimpleDateFormat("MM/dd/yyyy");
    	//return "Version 1.5: last update on "+sdf.format(date);
    	
    	return "(version 05/23/2023)";
    }
    */
    
    public String version(){
    	return CheckControl.version;
    }


	@Override
	public void load(Vector<String> lines) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public String printDeclaration(String indent) {
		// TODO Auto-generated method stub
		return "";
	}


	@Override
	public String name() {
		// TODO Auto-generated method stub
		return CheckControl.name;
	}


	@Override
	public String outdir() {
		// TODO Auto-generated method stub
		return Setup.outdir;
	}       
}

