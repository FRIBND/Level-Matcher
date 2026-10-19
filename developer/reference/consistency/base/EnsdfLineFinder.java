package consistency.base;

import java.util.Vector;

import ensdfparser.ensdf.*;

/*
 * update: this class is not needed now since the access issue is fixed with a
 *         simple solution by adding the record line in each record object
 * 
 * store line indexes of each ENSDF record (level/gamma/decay/delay) 
 * to provide access to lines in each record in an ENSDF file
 * 
 * Note that now ENSDF lines are only stored in ENSDF objects but
 * not repeatedly in objects of the ENSDF records (level/gamma/decay/delay)
 * of each ENSDF object so as to reduce consumption of computer memory, which
 * however results in losing direct access to lines in each individual records.
 * So this class is created to provide a compromising solution for this issue
 * by storing only indexes of record lines in an ENSDF file.
 *  
 * Jun Chen
 * 1/11/2018
 */
public class EnsdfLineFinder {

	Vector<Integer> lIndexV=new Vector<Integer>();//level line indexes
	
	Vector<Vector<Integer>> gIndexVV=new Vector<Vector<Integer>>();//gamma line indexes
	Vector<Vector<Integer>> dIndexVV=new Vector<Vector<Integer>>();//decay or delay line indexes
	
	Vector<Integer> ugIndexV=new Vector<Integer>();//unplaced gamma line indexes
	Vector<Integer> udIndexV=new Vector<Integer>();//unplaced decay/delay line indexes
	
	EnsdfLineFinder(){}
	
	EnsdfLineFinder(ENSDF ens){
		parseLineIndex(ens);
	}
	
	public void parseLineIndex(ENSDF ens){
		lIndexV=ens.poslev();
		
		Vector<String> lines=ens.lines();
		int start=ens.firstURadLineNo();
		int end=0;
		

		//unplaced gamma/decay/delay
		start=ens.firstURadLineNo();
		end=ens.firstLevelLineNo();		
		for(int i=start;i<end;i++){
			String type=lines.get(i).substring(5,9);
			if(!type.substring(0,2).equals("  "))
				continue;
			
			type=type.trim();
			
			if(type.equals("G"))
				ugIndexV.add(i);
			else
				udIndexV.add(i);//decay/decay type: E,B,A,DP,DN (there can be only one in one ENSDF file)
		}
		
		Vector<Integer> poslev=ens.poslev();
		int size=poslev.size();//equal to nLevels()
		for(int nl=0;nl<size;nl++){
			start=poslev.get(nl);
			if(nl==size-1)
				end=lines.size();
			else
				end=poslev.get(nl+1);
			
			Vector<Integer> gIndexV=new Vector<Integer>();
			Vector<Integer> dIndexV=new Vector<Integer>();
			for(int i=start;i<end;i++){
				String type=lines.get(i).substring(5,9);
				if(!type.substring(0,2).equals("  "))
					continue;
				
				type=type.trim();
				
				if(type.equals("G"))
					gIndexV.add(i);
				else
					dIndexV.add(i);//decay/decay type: E,B,A,DP,DN (there can be only one in one ENSDF file)
			}
			
			gIndexVV.add(gIndexV);
			dIndexVV.add(dIndexV);
		}
		
	}
	
	public int indexOfLevelLine(int levelIndex){
		return getInt(lIndexV,levelIndex);
	}
	
	public int indexOfGammaLine(int levelIndex,int gammaIndex){
		return getInt(gIndexVV,levelIndex,gammaIndex);
	}
	
	public int indexOfDecayLine(int levelIndex,int decayIndex){
		return getInt(dIndexVV,levelIndex,decayIndex);
	}
	
	public int indexOfDelayLine(int levelIndex,int delayIndex){
		return getInt(dIndexVV,levelIndex,delayIndex);
	}
	
	public int indexOfUnpGammaLine(int unGammaIndex){
		return getInt(ugIndexV,unGammaIndex);
	}
	
	public int indexOfUnpDecayLine(int unDecayIndex){
		return getInt(udIndexV,unDecayIndex);
	}
	
	public int indexOfUnpDelayLine(int unDelayIndex){
		return getInt(udIndexV,unDelayIndex);
	}
		
	private int getInt(Vector<Integer> indexes,int n){
		if(indexes==null)
			return -1;
		
		int size=indexes.size();
		if(size==0 || n<0 || n>size-1)
			return -1;
		
		return indexes.get(n).intValue();
	}
	
	private int getInt(Vector<Vector<Integer>> indexes,int n,int m){
		if(indexes==null)
			return -1;
		
		int size=indexes.size();
		if(size==0 || n<0 || m<0 || n>size-1)
			return -1;
		
		return getInt(indexes.get(n),m);
	}
}
