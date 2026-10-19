package consistency.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Vector;

import consistency.base.EnsdfAverageSetting;
import ensdfparser.ensdf.ENSDF;


public class AveragingTableModel extends javax.swing.table.AbstractTableModel implements javax.swing.table.TableModel{
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    int numRows;

    
    boolean[] isSelected;
    
    private ArrayList<String> columnNames=new ArrayList<String>(Arrays.asList("DSID","EL","T1/2","EG","RI","Select"));
    
    int numColumns=columnNames.size();
    
    HashMap<ENSDF,EnsdfAverageSetting> ensdfAverageSettingMap=null;
    
    Vector<ENSDF> ensdfsV=null;
    
    public AveragingTableModel(LinkedHashMap<ENSDF,EnsdfAverageSetting> settingMap){
        setTableValues(settingMap);
    }
    
    public void setTableValues(LinkedHashMap<ENSDF,EnsdfAverageSetting> settingMap) {
      

        ensdfAverageSettingMap=settingMap;
        
        numRows=settingMap.size();
        ensdfsV=new Vector<ENSDF>();
        for(ENSDF ens:settingMap.keySet())
            ensdfsV.add(ens);

        isSelected=new boolean[numRows];
        for(int x=0;x<numRows;x++){
            ENSDF ens=ensdfsV.get(x);
            isSelected[x]=settingMap.get(ens).isSelected();
        }   
        
        //System.out.println("size="+numRows+"  ensdf="+ensdfsV.get(0).DSId());
    }
    
    public void addTableModelListener(javax.swing.event.TableModelListener l){
        
    }
    public Class<?> getColumnClass(int columnIndex){

    	if(columnIndex==columnNames.size()-1)
    		return Boolean.class;
    	else if(columnIndex!=0)
            return Integer.class;
        
        return String.class;
        //return null;
    }
    public int getColumnCount(){
        return numColumns;
    }
    public String getColumnName(int columnIndex){
        try {
            return columnNames.get(columnIndex);
        }catch(Exception e) {
            return null;
        }
    }
    public int getRowCount(){
        if(numRows<28)
            return 28;
        
        return numRows;
    }
    
    public Object getValueAt(int rowIndex, int columnIndex){
        //System.out.println(rowIndex+" "+columnIndex+"  size="+ensdfAverageSettingMap.size());
        
        if(rowIndex>=numRows || columnIndex>=numColumns || rowIndex<0 || columnIndex<0)
            return null;
        
        try {
            ENSDF ens=ensdfsV.get(rowIndex);
            EnsdfAverageSetting setting=ensdfAverageSettingMap.get(ens);
            
            //System.out.println(" rowIndex="+rowIndex+" columIndex="+columnIndex+" value="+isSelected[rowIndex]+" dsid="+ens.DSId0());
            
            if (columnIndex==columnNames.size()-1)
                return new Boolean(isSelected[rowIndex]);// ? "Yes" : "No");
            
            if (columnIndex==0) 
                return ens.nucleus().nameENSDF()+": "+ens.DSId0();
                

            String colName=columnNames.get(columnIndex);
            
            if(setting.isOmitValue(colName))
            	return new Integer(2);
            if(setting.isUseComValue(colName))
            	return new Integer(3);
            
            return new Integer(1);            
        }catch(Exception e) {
            
        }

        return null;

    }
    public boolean isCellEditable(int rowIndex, int columnIndex){
        if(columnIndex==0)
            return false;
        
        return true;
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex){
        if(rowIndex>=numRows)
            return;
        
        ENSDF ens=ensdfsV.get(rowIndex);
        EnsdfAverageSetting setting=ensdfAverageSettingMap.get(ens);
        String colName=columnNames.get(columnIndex);  
        
        if(columnIndex==columnNames.size()-1) { 
            isSelected[rowIndex]=(Boolean)aValue;
            setting.setSelected(isSelected[rowIndex]);
        }else if(columnIndex>0) {     
        	int id=((Integer)aValue).intValue();
         		
        	setting.setIsOmitValue(colName, (id==2));
            setting.setIsUseComValue(colName, (id==3));
            
            //System.out.println(" row="+rowIndex+" column="+columnIndex+" value="+(Boolean)aValue);
        }

        fireTableCellUpdated(rowIndex,columnIndex);
        
    }    
    
    public boolean isSelected(int i){return isSelected[i];}  
   
    
}