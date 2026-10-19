package consistency.main;

import java.io.File;
import java.io.PrintWriter;

import formatcheck.check.FormatCheck;

public class TestFMTCHK {

    public static void main(String[] args) {
        String filePath="",fileDir="";
        String os=System.getProperty("os.name").toLowerCase();
        if(os.contains("mac"))
            fileDir="/Users/chenj/work/evaluation/ENSDF/check/";
        else
            fileDir="H:\\work\\evaluation\\ENSDF\\check\\";
        
        filePath=fileDir+"check.ens";
        
        File f=new File(filePath);
        
        FormatCheck formatCheck=new FormatCheck();
        
        long startTime,endTime;
        float timeElapsed;//in second
        startTime=System.currentTimeMillis();
        
        System.out.println("start...");
        
        formatCheck.check(f);
        
        System.out.println("Done...");
        
        endTime=System.currentTimeMillis();
        timeElapsed=(float)(endTime-startTime)/1000;
        System.out.println("Time elapsed: "+String.format("%.3f", timeElapsed)+" seconds");
        
        String msg=formatCheck.getMessage();
        
        //if(msg.length()>0) {
        	//System.out.println(msg);
            PrintWriter out=null;
			try {
				out = new PrintWriter(new File(fileDir+"Format.err"));
	            out.write(msg);
	            out.close();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				if(out!=null)
					out.close();
			}
        //}
    }

}
