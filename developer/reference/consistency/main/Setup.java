package consistency.main;

import java.io.*;
import java.beans.*;
import java.util.*;


/**
 * This object contains the application setup.  Paths to specific utilities 
 * needed by the program, etc.
 * 
 * Setup is saved/loaded from users home directory.
 */
public class Setup{
    
    public static String configFileName="CheckENSDF_conf.xml";
    
    // the defaults should be fine for most unix systems
	public static String dirSeparator=File.separator;

    /// directory used last
    public static String filedir = null;
    /// directory for the output files
    public static String outdir=System.getProperty("user.dir")+dirSeparator+"out";
    
    /// default folder to store the xml configure files for all codes
    public static String confdir=System.getProperty("user.home");
    
    //public static String lastOutdir=outdir;
    
    //a string containing extensions of all output files from last run, separated by ":" 
    public static String outexts="";

    
    /// load configuration
    public static void load(){
    	File f=new File(confdir);
    	if(!f.exists())
    		confdir=System.getProperty("user.dir");
    	
        String fn = confdir + dirSeparator+"."+configFileName;
        
        HashMap<?, ?> hm;
        try{
            XMLDecoder d = new XMLDecoder(
                new BufferedInputStream(
                    new FileInputStream(fn)));
            hm = (HashMap<?, ?>)d.readObject();
            d.close();

            filedir  = (String)hm.get("filedir");
            outdir=(String)hm.get("outdir");
            outexts=(String)hm.get("outexts");
            
            if(outdir==null || outdir.isEmpty())
            	outdir=System.getProperty("user.dir");
            
        }catch(Exception ex){} // ignore error
        
    }
    public static void load(String confdir1){
    	confdir=confdir1;
    	load();
    }
    /// save configuration
    public static void save(){
        HashMap<String, String> hm = new HashMap<String, String>();

        hm.put("filedir",filedir);
        hm.put("outdir",outdir);
        hm.put("outexts", outexts);

    	File f=new File(confdir);
    	if(!f.exists())
    		confdir=System.getProperty("user.dir");   	
        String fn = confdir + dirSeparator+"."+configFileName;
        
        try{
            XMLEncoder e = new XMLEncoder(
                new BufferedOutputStream(
                    new FileOutputStream(new File(fn))));
            e.writeObject(hm);
            e.close();
        }catch(Exception ex){} // ignore error        
    }
    
    //set conf dir to be the user dir (where the code is run)
    public static void setUserDirs(String indir1,String outdir1) {
    	try {
    		filedir=indir1;
    		outdir=outdir1;
    		
    		//lastOutdir=outdir;
    	}catch(Exception e) {
    		
    	}
    }
}

