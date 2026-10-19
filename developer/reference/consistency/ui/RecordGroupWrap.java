
package consistency.ui;

import java.util.Vector;

import consistency.base.CheckControl;
import consistency.base.EnsdfGroup;
import consistency.base.RecordGroup;
import ensdfparser.ensdf.ENSDF;
import ensdfparser.ensdf.Gamma;
import ensdfparser.ensdf.Level;
import ensdfparser.ensdf.Record;
import ensdfparser.nds.util.Str;


/*
 * wrap of RecordGroup class for the use in the tree of LevelGroup and GammaGroup
 * in the customizing frame
 */
public class RecordGroupWrap {

	RecordGroup recordGroup=null;
	RecordGroup parentRecordGroup=null;
	EnsdfGroup ensdfGroup=null;
	
    private boolean[] isSelected;
    
    private boolean addOldXTag=false;
    
    RecordTableModel recordTableModel=null;
    
    public RecordGroupWrap(RecordGroup levGroup,EnsdfGroup ensGroup) {
        recordGroup = levGroup;
        ensdfGroup=ensGroup;
        
    	if(ensdfGroup.adopted()!=null)
    		addOldXTag=true;
    	
    }
    
    public RecordGroupWrap(RecordGroup gamGroup,RecordGroup levGroup,EnsdfGroup ensGroup) {
        recordGroup = gamGroup;
        ensdfGroup=ensGroup;
        if(levGroup.subgroups().size()>0 && levGroup.subgroups().contains(gamGroup))
        	parentRecordGroup=levGroup;
    	
        if(ensdfGroup.adopted()!=null)
    		addOldXTag=true;
    	
    }
    
    /// Return a nice string for the list box.
    public String toString(){
    	try{
    		String ES=recordGroup.getRecord(0).ES().trim();
        	if(recordGroup.getRecord(0) instanceof Level){
        		String JPS=((Level)recordGroup.getRecord(0)).JPiS().trim();
        		String s=String.format("%-10s%-18s",ES,JPS);
        		//s="<HTML><pre>"+s+"</pre></HTML>";
        		//StringBuffer html=new StringBuffer(s);
        		//return html.toString();
        		return s;
        	}
        	if(recordGroup.getRecord(0) instanceof Gamma){
        		Gamma gam=((Gamma)recordGroup.getRecord(0));
        		String fls=gam.FLS().trim();
        		String jfs=gam.JFS().trim();
        		String s=String.format(" %-10sFL=%-10s%-18s",ES,fls,jfs);
        		
        		//s="<HTML><pre><font color=\"red\"><i>"+s+"</i></font></pre></HTML>";        		
        		//StringBuffer html=new StringBuffer(s);       		
        		//return html.toString();
        		return s;
        	}
            return ES;
    	}catch(Exception e){
    	    e.printStackTrace();
    		return "";
    	}

    }
    
    
    public javax.swing.table.TableModel recordTableModel(){
    	
        //debug
        //System.out.println("In EnsdfWrap:line 129"+(drawBand==null)+(bandTableModel==null));
        
        if(isSelected==null || isSelected.length<=0 || recordTableModel==null){
        	recordTableModel=new RecordTableModel();
        }
     
        
        //debug
        //System.out.println("In EnsdfWrap:line 141"+(drawBand==null)+(bandTableModel==null));

        return recordTableModel;
    }
       
    
    public boolean[] getIsSelected(){return isSelected;}
    public boolean   isSelected(int i){return isSelected[i];}
    
    
	public <T extends Record> Vector<T> getSelectedRecordsV(){
		Vector<T> recordsV=new Vector<T>();
		for(int i=0;i<recordGroup.nRecords();i++){
			if(isSelected[i])
				recordsV.add(recordGroup.getRecord(i));
		}
		
		return recordsV;
		
	}
    
    
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public class RecordTableModel extends javax.swing.table.AbstractTableModel implements javax.swing.table.TableModel{
        /**
    	 * 
    	 */
    	private static final long serialVersionUID = 1L;
    	int numRows;

    	
        RecordTableModel(){
            numRows=recordGroup.nRecords();
            
            isSelected=new boolean[numRows];
            for(int x=0;x<numRows;x++){
                isSelected[x]=true;
            }
        }
        public void addTableModelListener(javax.swing.event.TableModelListener l){
            
        }
        public Class<?> getColumnClass(int columnIndex){
            if (columnIndex==0)
                return String.class;
            else if(columnIndex==1){ 
                return Boolean.class;
            }
            
            return String.class;
            //return null;
        }
        public int getColumnCount(){
            return 2;
        }
        public String getColumnName(int columnIndex){
            switch(columnIndex){
                case 0:
                	if(addOldXTag)
                		return "Record Lines from different datasets (new and old XREF tags also given in order)";
                	else
                		return "Record Lines from different datasets";
                case 1:
                    return "Select";

                default:
                    return null;
            }
        }
        public int getRowCount(){
        	if(numRows<8)
        		return 8;
        	
            return numRows;
        }
        
        public Object getValueAt(int rowIndex, int columnIndex){
        	//System.out.println(rowIndex+" "+columnIndex+"  size="+recordGroup.nRecords());
        	
        	if(rowIndex>=recordGroup.nRecords())
        		return null;
        	
            if (columnIndex==1){
            	try{
            		if(recordGroup.getDSID(rowIndex).contains("ADOPTED")){
            			isSelected[rowIndex]=false;
            			return null;
            		}
            	}catch(Exception e){}
            	
                return new Boolean(isSelected[rowIndex]);// ? "Yes" : "No");
            }
            if (columnIndex==0)
                return printRecordLine(rowIndex);

            return null;
        }
        public boolean isCellEditable(int rowIndex, int columnIndex){
        	if(columnIndex==0)
        		return false;
        	
            return true;
        }
        public void removeTableModelListener(javax.swing.event.TableModelListener l){
            
        }
        public void setValueAt(Object aValue, int rowIndex, int columnIndex){
        	if(rowIndex>=recordGroup.nRecords())
        		return;
        	
            if(columnIndex==1) isSelected[rowIndex]=(Boolean)aValue;

            fireTableCellUpdated(rowIndex,columnIndex);
        }        
    }



	private Object printRecordLine(int rowIndex) {
		try{
			String line=((Record) recordGroup.getRecord(rowIndex)).recordLine();
			String lineType=line.substring(7,8);
			if(lineType.equals("L"))
				return printLevelRecordLine(rowIndex);
			if(lineType.equals("G"))
				return printGammaRecordLine(rowIndex);
			
			return null;

		}catch(Exception e){
			
		}
		
		return null;
	}

	
	private Object printLevelRecordLine(int rowIndex) {
		try{
			Level lev=(Level)recordGroup.getRecord(rowIndex);
			String dsid=recordGroup.getDSID(rowIndex);
			String xtag=recordGroup.getXTag(rowIndex);
			String newXTag=ensdfGroup.newDSIDXTagMap().get(dsid);
			if(newXTag==null)
				newXTag="?";
			
			String prefix="",line="";			
			//note that (*), (?) marks are added only for old tags if both old and new tag exist, 
			//but not for new tags for display purpose 
			//(old and new tag displayed side by side in output, new tag first and the old tag)
			if(addOldXTag){
				prefix=String.format("%30s--->%-3s%-5s",dsid,newXTag,xtag);	
			}else{//no adopted dataset in grouping and so no old tag
				prefix=String.format("%30s--->%-5s",dsid,xtag);
			}
			//System.out.println(" dsid="+dsid+" xtag="+xtag);
			
			
			line=prefix+lev.recordLine();
			
			//System.out.println(line);
			return line;
		}catch(Exception e){
			
		}
		
		return null;
	}
	
	private Object printGammaRecordLine(int rowIndex){
		try{
			Gamma gam=(Gamma)recordGroup.getRecord(rowIndex);
			String dsid=recordGroup.getDSID(rowIndex);
			String xtag=recordGroup.getXTag(rowIndex);
			String newXTag=ensdfGroup.newDSIDXTagMap().get(dsid);
			if(newXTag==null)
				newXTag="?";
					
			ENSDF ens=ensdfGroup.getENSDFByDSID0(dsid);

		    String fls="",line="",prefix="";
			try{
				//Level parent=ens.levelAt(gam.ILI());
				Level daughter=ens.levelAt(gam.FLI());
				fls=String.format("FL=%-10s%s",daughter.ES(),daughter.JPiS());
			}catch(Exception e){  
				fls="";
			}
			

			if(addOldXTag)
				prefix=String.format("%30s--->%-3s%-5s",dsid,newXTag,xtag);
			else
				prefix=String.format("%30s--->%-5s",dsid,xtag);
			
			if(CheckControl.convertRIForAdopted) {
				line=recordGroup.replaceIntensityOfGammaLineWithBR(gam,CheckControl.errorLimit,"ALL");
			}else
				line=gam.recordLine();
			
			line=prefix+Str.fixLineLength(line,80)+" * "+fls;
			return line;
		}catch(Exception e){
			
		}
		
		return null;
	}
	
}
