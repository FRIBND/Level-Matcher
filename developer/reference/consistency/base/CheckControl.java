package consistency.base;

import ensdfparser.base.BaseControl;

public class CheckControl extends BaseControl{
	static {errorLimit=35;}
	
	public final static String version="01/14/2026";
	public final static String title="** Program for checking ENSDF format and data consistency (update "+version+") **";
	public final static String name="ConsistencyCheck";
	
	public static float deltaEL=50;
	public static float deltaEG=10;
	
	public static boolean writeRPT=true;
	public static boolean writeLEV=true;
	public static boolean writeGAM=true;
	public static boolean writeGLE=true;
	public static boolean writeMRG=false;
	public static boolean writeAVG=false;
	public static boolean writeFED=false;
	
	//Normalize gamma intensities in each dataset to relative
	//intensity from each level (PN=6) for Adopted Gammas
	public static boolean convertRIForAdopted=true;

	//create a dataset combining all data of merged datasets including comments
	public static boolean createCombinedDataset=false; 
    public static boolean isUseAnyComValue=false;//for averaging values in comments
    public static boolean isUseAverageSettings=false;
    
    public static boolean forceAverageAll=false;
    public static boolean averageC2S=false;
    public static boolean alwaysAverageEL=false;
   
	//used to make label for each data entry in the average comments,
	//like 123.4 {I1} from (n,|g), in which, "(n,|g)" from DSID of the dataset and "from " is the prefix 
	//and posfix=""
	// in 123.4 {I1} (2004Ab12), 2004Ab12 taken from the string in DSID field, and prefix="(", posfix=")"
	public static String groupLabelPrefix="from ",groupLabelPosfix="";
	
	//public static int errorLimit=35;
	

}
