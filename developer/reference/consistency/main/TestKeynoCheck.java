package consistency.main;

import java.io.File;
import java.util.Vector;

public class TestKeynoCheck {

    public static void main(String[] args) {
        String filePath="",fileDir="";
        String os=System.getProperty("os.name").toLowerCase();
        if(os.contains("mac"))
            fileDir="/Users/chenj/work/evaluation/ENSDF/check/";
        else
            fileDir="H:\\work\\evaluation\\ENSDF\\check\\";
        
        filePath=fileDir+"check.ens";
        
        File f=new File(filePath);
        
        System.out.println("start...");
        long startTime,endTime;
        float timeElapsed;//in second
        startTime=System.currentTimeMillis();
        
        Run run=new Run();
        run.filesV=new Vector<File>();
        run.filesV.add(f);
        run.redirectOutputToMessenger(false);
        
        try {
			run.checkKeynumber("test");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    

        System.out.println("Done...");
        
        endTime=System.currentTimeMillis();
        timeElapsed=(float)(endTime-startTime)/1000;
        System.out.println("Time elapsed: "+String.format("%.3f", timeElapsed)+" seconds");

    }

}
