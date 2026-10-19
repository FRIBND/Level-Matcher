
package consistency.ui;

import ensdfparser.ensdf.*;

/**
 * Wrap indivisual ensdf objects and store their various GUI set options
 * in a serializable form.
 *
 * Also contains templates for all the different tables drawn by the GUI
 * Created by Roy Zywina 2007
 * Last Updated Scott Geraedts May 4 2010
 */
public class EnsdfWrap {
    ENSDF ens;
    
    String label;
    
    //the names of the bands
    public Band [] bandName;
    /// which bands to draw
    public boolean[] drawBand;
    //width of the bands
    public float [] bandWidth;
    
    //this is the format for the table where band selection is done
    //check out the java documentations for TableModels if you want to know how it fits into the GUI
    public class BandTableModel extends javax.swing.table.AbstractTableModel implements javax.swing.table.TableModel{
        /**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		int numRows;
        BandTableModel(float [] f){
            numRows=ens.nBands();
            bandName=new Band[numRows];
            drawBand=new boolean[numRows];
            bandWidth=new float[numRows];
            for(int x=0;x<ens.nBands();x++){
                bandName[x]=ens.bandAt(x);
                drawBand[x]=true;
                bandWidth[x]=f[x];
            }
        }
        public void addTableModelListener(javax.swing.event.TableModelListener l){
            
        }
        public Class<?> getColumnClass(int columnIndex){
            if (columnIndex==1)
                return Boolean.class;
            else if(columnIndex==2) 
                return Float.class;
            return Band.class;
            //return null;
        }
        public int getColumnCount(){
            return 3;
        }
        public String getColumnName(int columnIndex){
            switch(columnIndex){
                case 0:
                    return "Band Label";
                case 1:
                    return "Draw Band?";
                case 2:
                    return "Band Width";
                default:
                    return null;
            }
        }
        public int getRowCount(){
            return numRows;
        }
        public Object getValueAt(int rowIndex, int columnIndex){
            if (columnIndex==1)
                return new Boolean(drawBand[rowIndex]);// ? "Yes" : "No");
            if (columnIndex==0)
                return bandName[rowIndex].comment().body();
            if (columnIndex==2)
                return new Float(bandWidth[rowIndex]);
            return null;
        }
        public boolean isCellEditable(int rowIndex, int columnIndex){
            return true;
        }
        public void removeTableModelListener(javax.swing.event.TableModelListener l){
            
        }
        public void setValueAt(Object aValue, int rowIndex, int columnIndex){
            if(columnIndex==1) drawBand[rowIndex]=(Boolean)aValue;
            if(columnIndex==0){
                try {
					bandName[rowIndex].comment().setBody((String)aValue);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            }
            if(columnIndex==2){
                bandWidth[rowIndex]=Float.parseFloat((String)aValue);
            }
            fireTableCellUpdated(rowIndex,columnIndex);
        }        
    }
    /** Creates a new instance of EnsdfWrap */
    public EnsdfWrap(ENSDF e) {
        ens = e;
        label=ens.nucleus().nameENSDF()+": "+ens.DSId();
    }
    
    public EnsdfWrap(ENSDF e,String label) {
        ens = e;
        this.label=label;
    }
    
    /// Return a nice string for the list box.
    public String toString(){
        return label;
    }
    
    public javax.swing.table.TableModel bandModel(float [] f){
        
        return new BandTableModel(f);
    }
    /*tell the config file which bands have been selected to be drawn */
    public void setBands(){
        int nBands=0;
        for(int x=0;x<drawBand.length;x++)
            if(drawBand[x])nBands++;
       
        Band []b=new Band[nBands];
        int pos=0;
        boolean bandFound;
        for(int x=0;x<nBands;x++){
            bandFound=false;
            while(!bandFound){
                if(drawBand[pos]){
                    b[x]=ens.bandAt(pos);
                    bandFound=true;
                    pos++;
                }
                else
                    pos++;
           }
        }

    }

}
