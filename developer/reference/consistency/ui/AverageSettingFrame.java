package consistency.ui;

import java.awt.Color;
import java.awt.Component;

import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Vector;

import javax.swing.AbstractCellEditor;
import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.GroupLayout.Alignment;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import consistency.base.AverageValuesInComments;
import consistency.base.CheckControl;
import consistency.base.EnsdfAverageSetting;
import consistency.base.EnsdfGroup;
import consistency.main.Run;
import consistency.main.Setup;
import ensdfparser.ensdf.ENSDF;
import ensdfparser.nds.ensdf.MassChain;

import javax.swing.SwingConstants;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;


public class AverageSettingFrame extends javax.swing.JFrame {
    private static final long serialVersionUID = 1L;

    consistency.main.Run run;
    MassChain data;
    
    Vector<EnsdfGroup> ensdfGroupsV;
    Vector<ENSDF> ensdfsV=new Vector<ENSDF>();
    
    LinkedHashMap<ENSDF,EnsdfAverageSetting> ensdfAverageSettingMap;
    AverageValuesInComments avgCom=null;
    
    boolean createCombinedDataset0=CheckControl.createCombinedDataset;
    boolean convertRIForAdopted0=CheckControl.convertRIForAdopted;
    
    public AverageSettingFrame(MassChain chain,Run r) {
        data=chain;
        run=r;

        //Control.convertRIForAdopted=false;
        CheckControl.isUseAverageSettings=true;
        
        try {
            ensdfGroupsV=run.getConsistencyCheck().ensdfGroupsV();  
            ensdfAverageSettingMap=run.getConsistencyCheck().ensdfAverageSettingMap();
            for(int i=0;i<data.nENSDF();i++) {
                ENSDF ens=data.getENSDF(i);
                EnsdfAverageSetting setting=new EnsdfAverageSetting(ens);
                if(!ensdfAverageSettingMap.containsKey(ens)) {
                    ensdfAverageSettingMap.put(ens, setting);
              
                }
            }
            
            ensdfsV.clear();
            for(ENSDF ens:ensdfAverageSettingMap.keySet())
                ensdfsV.add(ens);
            
            
        }catch(Exception e) {
            
        }
     
        initComponents();
      
        if(Setup.outdir==null){
            Setup.outdir=System.getProperty("user.dir")+"//out";
        }
    

    }



    private void setTableRenderer(){
           
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);

        
        MyTableCellRenderer columnRenderer = new MyTableCellRenderer();
        MyTableCellEditor columnEditor = new MyTableCellEditor();

        
        int ncol=settingTable.getColumnCount();
        for(int i=0;i<ncol;i++) {
            TableColumn tableColumn=settingTable.getColumnModel().getColumn(i);
            tableColumn.setCellRenderer(columnRenderer);//if use render for Boolean column, a text instead of a checkbox will show.
            if(settingTable.getColumnClass(i)==Integer.class && i<ncol-1) {
                tableColumn.setCellEditor(columnEditor); 
                tableColumn.setPreferredWidth(50);
            }else if(i==ncol-1)
                tableColumn.setPreferredWidth(20);
            else if(i==0)
                tableColumn.setPreferredWidth(250);
        }


        //recordTable.getColumnModel().getColumn(0).setPreferredWidth(990);
        //recordTable.getColumnModel().getColumn(1).setPreferredWidth(40);
     

        ((DefaultTableCellRenderer)settingTable.getTableHeader().getDefaultRenderer()).setHorizontalAlignment(JLabel.CENTER);
        
        packTable(settingTable);
    
    }


    private void packTable(JTable table) {
        TableColumnModel columnModel = table.getColumnModel();
        AveragingTableModel tableModel=(AveragingTableModel) table.getModel();
        int columnCount = table.getColumnCount();
        int rowCount = table.getRowCount();
        int[][] preferredHeights = new int[columnCount][rowCount];
        TableCellRenderer renderer;
        Component comp;
        int maxH=0;
        for (int col = 0; col < columnCount; col++) {
            renderer = columnModel.getColumn(col).getCellRenderer();
            if (renderer == null) {
                renderer = table.getDefaultRenderer(tableModel.getColumnClass(col));
            }
            for (int row = 0; row < rowCount; row++) {
                comp = renderer.getTableCellRendererComponent(table,
                    tableModel.getValueAt(row, col), false, false, row, col);
                preferredHeights[col][row] = (int) comp.getPreferredSize().getHeight();
                if(preferredHeights[col][row]>maxH)
                    maxH=preferredHeights[col][row];
            }
        }
        for (int row = 0; row < rowCount; row++) {
            int pref = 0;
            
            if(row>=ensdfAverageSettingMap.size()) {
                table.setRowHeight(row,maxH);
                continue;
            }
            
            for (int col = 0; col < columnCount; col++) {
                pref = Math.max(pref, preferredHeights[col][row]);
            }

            table.setRowHeight(row, pref);
        }

    }




    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */

    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents

    private void initComponents() {
        
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });
        mainPanel = new javax.swing.JPanel();
        mainPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        recordTableScrollPane = new javax.swing.JScrollPane();
        settingTable = new javax.swing.JTable();
        
        settingTable.setModel(new DefaultTableModel(
            new Object[][] {
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
                {null, null},
            },
            new String[] {
                "Record Lines from different datasets", "Select"
            }
        ));
      
        
        try {
            
            settingTable.setModel(new AveragingTableModel(ensdfAverageSettingMap)); 
            setTableRenderer();    
        }catch(Exception e) {
            
        }

        
        recordTableScrollPane.setViewportView(settingTable);
        
        settingsPanel = new JPanel();
        
        convertRICheckBox = new JCheckBox("<HTML>convert relative I&gamma; to branching (PN=6)</THML>");
        convertRICheckBox.setSelected(CheckControl.convertRIForAdopted);
        
        convertRICheckBox.addItemListener(new ItemListener() {
          	public void itemStateChanged(ItemEvent arg0) {
                if(((JCheckBox)arg0.getSource()).isSelected())
                    CheckControl.convertRIForAdopted=true;
                else
                    CheckControl.convertRIForAdopted=false;
            }
        });
        
        createAdoptedDatasetCheckBox = new JCheckBox("<HTML>create Adopted dataset</THML>");
        createAdoptedDatasetCheckBox.setSelected(!CheckControl.createCombinedDataset);
        
        createAdoptedDatasetCheckBox.addItemListener(new ItemListener() {
        	public void itemStateChanged(ItemEvent arg0) {
            	if(((JCheckBox)arg0.getSource()).isSelected()) {
            		CheckControl.createCombinedDataset=false;
            		convertRICheckBox.setSelected(true);
            		//convertRICheckBox.setEnabled(false);
            	}else {
            		CheckControl.createCombinedDataset=true;
            		//convertRICheckBox.setEnabled(true);
            	}
        	}
        });
        createAdoptedDatasetCheckBox.setToolTipText("<HTML>If not checked, data from all input datasets including comments<br>"
        		                                     +  "will be combined. If checked, all detailed comments in invidual<br>"
        		                                     +  "datasets will be removed for creating the Adopted dataset</HTML>");
        
        forceAverageCheckBox = new JCheckBox("<HTML>force to average for default non-average cases </THML>");
        forceAverageCheckBox.setToolTipText("<HTML>If not checked, the best value will be adopted and the rest are<br>"
        		                               + "are in comments if the average of all is the same as the best.<br>"
        		                               + "If checked, the average of all good data points will be adopted.</HTML>");
        forceAverageCheckBox.setSelected(CheckControl.forceAverageAll);         
        forceAverageCheckBox.addItemListener(new ItemListener() {
        	public void itemStateChanged(ItemEvent arg0) {
            	if(((JCheckBox)arg0.getSource()).isSelected()) {
            		CheckControl.forceAverageAll=true;
            	}else {
            		CheckControl.forceAverageAll=false;
            	}
        	}
        });
        
        alwaysAverageELCheckBox = new JCheckBox("<HTML>Always average EL</THML>");
        alwaysAverageELCheckBox.setToolTipText("<HTML>By default, E(level) values are not averaged if having decay gammas. <br>"
        		                              + "Check it to always average E(level) even if levels have gammas.</HTML>");
        alwaysAverageELCheckBox.setSelected(CheckControl.alwaysAverageEL);
        alwaysAverageELCheckBox.addItemListener(new ItemListener() {
        	public void itemStateChanged(ItemEvent arg0) {
            	if(((JCheckBox)arg0.getSource()).isSelected()) {
            		CheckControl.alwaysAverageEL=true;
            	}else {
            		CheckControl.alwaysAverageEL=false;
            	}
        	}
        });
        
        averageC2SCheckBox = new JCheckBox("<HTML>average relabelled C2S</THML>");
        averageC2SCheckBox.setToolTipText("<HTML>By default, C2S values should not be averaged. <br>"
        		+ "Check it to average values of other types placed in relabelled C2S field</HTML>");
        averageC2SCheckBox.setSelected(false);
        
        averageC2SCheckBox.setSelected(CheckControl.averageC2S);
        averageC2SCheckBox.addItemListener(new ItemListener() {
        	public void itemStateChanged(ItemEvent arg0) {
            	if(((JCheckBox)arg0.getSource()).isSelected()) {
            		CheckControl.averageC2S=true;
            		
            		//System.out.println(CheckControl.isUseAverageSettings+" #"+CheckControl.isUseAnyComValue);
                    for(EnsdfAverageSetting setting:ensdfAverageSettingMap.values()) {
                    	setting.setIsUseComValue("S", false);
                        setting.setIsOmitValue("S", false);
                    }
                    
            	}else {
            		CheckControl.averageC2S=false;
            	}
        	}
        });
        
        //
        
        GroupLayout gl_panel = new GroupLayout(settingsPanel);
        gl_panel.setHorizontalGroup(
        	gl_panel.createParallelGroup(Alignment.LEADING)
        		.addGroup(gl_panel.createSequentialGroup()
        			.addGap(24)
        			.addGroup(gl_panel.createParallelGroup(Alignment.LEADING)
        				.addComponent(convertRICheckBox, GroupLayout.PREFERRED_SIZE, 265, GroupLayout.PREFERRED_SIZE)
        				.addComponent(createAdoptedDatasetCheckBox, GroupLayout.PREFERRED_SIZE, 178, GroupLayout.PREFERRED_SIZE))
        			.addPreferredGap(ComponentPlacement.RELATED)
        			.addGroup(gl_panel.createParallelGroup(Alignment.LEADING)
        				.addGroup(gl_panel.createSequentialGroup()
        					.addComponent(averageC2SCheckBox, GroupLayout.PREFERRED_SIZE, 173, GroupLayout.PREFERRED_SIZE)
        					.addGap(18)
        					.addComponent(alwaysAverageELCheckBox, GroupLayout.PREFERRED_SIZE, 230, GroupLayout.PREFERRED_SIZE))
        				.addComponent(forceAverageCheckBox, GroupLayout.PREFERRED_SIZE, 453, GroupLayout.PREFERRED_SIZE))
        			.addContainerGap(32, Short.MAX_VALUE))
        );
        gl_panel.setVerticalGroup(
        	gl_panel.createParallelGroup(Alignment.LEADING)
        		.addGroup(gl_panel.createSequentialGroup()
        			.addGap(14)
        			.addGroup(gl_panel.createParallelGroup(Alignment.BASELINE)
        				.addComponent(convertRICheckBox)
        				.addComponent(forceAverageCheckBox))
        			.addPreferredGap(ComponentPlacement.RELATED)
        			.addGroup(gl_panel.createParallelGroup(Alignment.BASELINE)
        				.addComponent(createAdoptedDatasetCheckBox)
        				.addComponent(alwaysAverageELCheckBox)
        				.addComponent(averageC2SCheckBox))
        			.addContainerGap(14, Short.MAX_VALUE))
        );
        settingsPanel.setLayout(gl_panel);
        
        topLabel1 = new JLabel("<HTML>Averaging options for selecting values given in the record fields (Rec) or listed in comments (Com) following the keyword</HTML>");
        topLabel1.setForeground(new Color(0, 0, 255));
        topLabel1.setHorizontalAlignment(SwingConstants.CENTER);
            
        averageButton = new javax.swing.JButton();
        averageButton.setToolTipText("Calculate average of values of selected record from selected datasets in the table above.");
        
        
        averageButton.setText("average"); // NOI18N
        averageButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                averageButtonActionPerformed(evt);
            }
        });
        
        resetButton = new JButton("reset");
        resetButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    reset();
                } catch (IOException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
            }
        });
        resetButton.setToolTipText("clear all selections and set to use record values.");
        
        topLabel2 = new JLabel("<HTML>keyword=\"average of\" by default or from custom input under \"Com\" button below, e.g., \"E{-|a}=\"</HTML>");
        topLabel2.setHorizontalAlignment(SwingConstants.CENTER);
        topLabel2.setForeground(new Color(255, 0, 0));
    
    
        GroupLayout gl_mainPanel = new GroupLayout(mainPanel);
        gl_mainPanel.setHorizontalGroup(
        	gl_mainPanel.createParallelGroup(Alignment.LEADING)
        		.addGroup(gl_mainPanel.createSequentialGroup()
        			.addGap(350)
        			.addComponent(averageButton)
        			.addGap(45)
        			.addComponent(resetButton)
        			.addContainerGap(220, Short.MAX_VALUE))
        		.addGroup(gl_mainPanel.createSequentialGroup()
        			.addGap(4)
        			.addComponent(topLabel2, GroupLayout.PREFERRED_SIZE, 772, GroupLayout.PREFERRED_SIZE)
        			.addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        		.addGroup(Alignment.TRAILING, gl_mainPanel.createSequentialGroup()
        			.addGap(1)
        			.addGroup(gl_mainPanel.createParallelGroup(Alignment.TRAILING)
        				.addComponent(settingsPanel, Alignment.LEADING, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        				.addComponent(recordTableScrollPane, Alignment.LEADING, GroupLayout.DEFAULT_SIZE, 780, Short.MAX_VALUE)
        				.addComponent(topLabel1, Alignment.LEADING, GroupLayout.DEFAULT_SIZE, 780, Short.MAX_VALUE))
        			.addGap(1))
        );
        gl_mainPanel.setVerticalGroup(
        	gl_mainPanel.createParallelGroup(Alignment.LEADING)
        		.addGroup(gl_mainPanel.createSequentialGroup()
        			.addContainerGap()
        			.addComponent(topLabel1, GroupLayout.PREFERRED_SIZE, 18, GroupLayout.PREFERRED_SIZE)
        			.addGap(1)
        			.addComponent(topLabel2, GroupLayout.PREFERRED_SIZE, 18, GroupLayout.PREFERRED_SIZE)
        			.addPreferredGap(ComponentPlacement.RELATED)
        			.addComponent(recordTableScrollPane, GroupLayout.PREFERRED_SIZE, 332, GroupLayout.PREFERRED_SIZE)
        			.addGap(10)
        			.addComponent(settingsPanel, GroupLayout.PREFERRED_SIZE, 70, GroupLayout.PREFERRED_SIZE)
        			.addPreferredGap(ComponentPlacement.RELATED)
        			.addGroup(gl_mainPanel.createParallelGroup(Alignment.LEADING)
        				.addComponent(resetButton)
        				.addComponent(averageButton))
        			.addGap(18))
        );
        
       
        mainPanel.setLayout(gl_mainPanel);
        
        GroupLayout groupLayout = new GroupLayout(getContentPane());
        groupLayout.setHorizontalGroup(
            groupLayout.createParallelGroup(Alignment.LEADING)
                .addGroup(groupLayout.createSequentialGroup()
                    .addGap(1)
                    .addComponent(mainPanel, GroupLayout.DEFAULT_SIZE, 774, Short.MAX_VALUE))
        );
        groupLayout.setVerticalGroup(
            groupLayout.createParallelGroup(Alignment.LEADING)
                .addGroup(groupLayout.createSequentialGroup()
                    .addGap(1)
                    .addComponent(mainPanel, GroupLayout.DEFAULT_SIZE, 500, Short.MAX_VALUE)
                    .addGap(1))
        );
        getContentPane().setLayout(groupLayout);

        pack();
        
        setResizable(false);
    

    }// </editor-fold>//GEN-END:initComponents


    
    boolean isUseAnyComValue() {
        try {
            for(EnsdfAverageSetting setting:ensdfAverageSettingMap.values()) {
                if(setting.isUseAnyComValue())
                    return true;
            }
        }catch(Exception e) {
            
        }
        
        return false;
    }
    
    private void averageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Output_ButtonActionPerformed
        //call the rest of the program to actually produce the output  

        try{
             
            try {         
                settingTable.getCellEditor().stopCellEditing();
            }catch(Exception e) {
                
            }
            CheckControl.isUseAnyComValue=isUseAnyComValue();
            //Control.createCombinedDataset=true;
 
            //System.out.println(" ############ "+CheckControl.isUseAnyComValue);
            
            //parse comment values based on user's average settings
            avgCom=new AverageValuesInComments(ensdfsV,ensdfAverageSettingMap);  
            
            run.getConsistencyCheck().setEnsdfCommentDataMap(avgCom.ensdfCommentDataMap());
 
            //System.out.println(" covertRI="+Control.convertRIForAdopted+"  isCombined="+Control.createCombinedDataset);
            
            String name=run.getOutFilename();//including outpath
            String adpName=name+".adp";
            String avgName=name+".avg";
            run.printMessage("\nCreating a dataset (.adp) combining all data with custom average settings..."); 
            run.printMessage("\n (See averaging details in "+avgName+")"); 
            run.getConsistencyCheck().writeFile(adpName,"ADP");  
            run.getConsistencyCheck().writeFile(avgName,"AVG");  
            run.printMessage("Done");

            /*
            //debug
            System.out.println(" setting size="+ensdfAverageSettingMap.size());
            for(ENSDF ens:ensdfAverageSettingMap.keySet()) {
                System.out.println("DSID="+ens.DSId0());
                EnsdfAverageSetting setting=ensdfAverageSettingMap.get(ens);
                for(String colName:setting.customKeywordMap().keySet())
                    System.out.println("       col="+colName+"  keyword="+setting.getCustomKeyword(colName));
            }
            */
        }catch(Exception e){
            run.printMessage("Error when creating output of a combined dataset with custum average settings.\n");
            e.printStackTrace();  
        }

        
        //Control.createCombinedDataset=createCombinedDataset0;
        
    }//GEN-LAST:event_Output_ButtonActionPerformed


    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        //Control.createCombinedDataset=createCombinedDataset0;//restore original settings
        //Control.convertRIForAdopted=convertRIForAdopted0;
    	
        CheckControl.isUseAnyComValue=false;
        CheckControl.isUseAverageSettings=false;
        

    }//GEN-LAST:event_formWindowClosing


    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new AverageSettingFrame(null,null).setVisible(true);
            }
        });

    }





    public void reset() throws IOException{
        
        try {         
            settingTable.getCellEditor().stopCellEditing();
        }catch(Exception e) {
            
        }
        
        for(EnsdfAverageSetting setting:ensdfAverageSettingMap.values()) {
            for(String name:setting.isUseComValueMap().keySet()) {
                setting.setIsUseComValue(name, false);
            }
            
            for(String name:setting.isOmitValueMap().keySet()) {
                setting.setIsOmitValue(name, false);
            }
        }

        settingTable.updateUI();
        CheckControl.isUseAnyComValue=false;

    }


    //table render and editor 

    private class MyTableCellEditor extends AbstractCellEditor implements TableCellEditor {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;
        private OptionPanel optionPanel;

        
        MyTableCellEditor() {

            optionPanel=new OptionPanel();

        }

        @Override
        public Object getCellEditorValue() {
        	if(optionPanel.useRecValueOption.isSelected())
        		return 1;
        	if(optionPanel.omitValueOption.isSelected())
        		return 2;
        	if(optionPanel.useComValueOption.isSelected())
        		return 3;

        	
            return 1;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
            boolean isSelected, int row, int column) {
            
            if(column<table.getColumnCount()-1 && table.getColumnClass(column)==Integer.class && value!=null && row<ensdfAverageSettingMap.size()) {  
                optionPanel.setSelected(((Integer) value).intValue());

                
                //System.out.println("In CellEditor: row="+row+" col="+column+" isSelected="+isSelected+" value="+((Boolean) value).booleanValue());
                /*
                if (isSelected) {
                    optionPanel.setBackground(Color.GREEN);
                } else {
                    optionPanel.setBackground(Color.CYAN);
                }
                */
                
                optionPanel.rowIndex=row;
                optionPanel.columnIndex=column;
                
                if (isSelected) {
                    optionPanel.setBackground(table.getBackground());
                } else {
                    optionPanel.setBackground(table.getBackground());
                }
                
                try {
                    EnsdfAverageSetting setting=ensdfAverageSettingMap.get(ensdfsV.get(row));
                    String name=table.getColumnName(column);
                    optionPanel.setName(name+row+"_"+column);
                    if(setting!=null)
                        optionPanel.setKeyword(setting.getCustomKeyword(name));
                }catch(Exception e) {
                    
                }
                
                return optionPanel;
            }
            
            return null;
        }

    }


    private class MyTableCellRenderer extends DefaultTableCellRenderer{


        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            try{             

                
                if(column==table.getColumnCount()-1) {         
                    //System.out.println("###row="+row+"  col="+column+"   "+c.getClass().getName()+"  isSelected="+isSelected);
                    JCheckBox c=new JCheckBox();
                    c.setOpaque(true);
                    
                    c.setSelected(((Boolean)value).booleanValue());             
                    //if(row>=currRecordGroupWrap.recordGroup.nRecords() && column==1){
                    //    System.out.println("    2  row="+row+"  col="+column+"   "+c.getClass().getName());
                    //    return new JLabel("");
                    //}
                    
                    /*
                    if (isSelected) {
                        c.setBackground(table.getSelectionBackground());
                    } else {
                        c.setBackground(table.getBackground());
                    }
                    */
                    
                    setHorizontalAlignment(JLabel.CENTER);
                    c.setHorizontalAlignment(JLabel.CENTER);
                    
                    return c;
                }else if(table.getColumnClass(column)==Integer.class){
                    //System.out.println("###row="+row+"  col="+column+"   "+c.getClass().getName()+"  isSelected="+isSelected);
                    OptionPanel optionPanel=new OptionPanel();
                      
                    optionPanel.setSelected(((Integer)value).intValue());           
                    
                    //System.out.println("In CellRenderer: row="+row+" col="+column+" isSelected="+isSelected+" hasFocus="+hasFocus);
                    
                    /*
                    if (isSelected) {
                        optionPanel.setBackground(Color.RED);
                    } else {
                        optionPanel.setBackground(Color.BLUE);
                    }
                    */
                    
                    optionPanel.rowIndex=row;
                    optionPanel.columnIndex=column;
                    
                    if (isSelected) {
                        optionPanel.setBackground(table.getSelectionBackground());
                    } else {
                        optionPanel.setBackground(table.getBackground());
                    }
                    setHorizontalAlignment(JLabel.CENTER);
                    
                    if(row<ensdfAverageSettingMap.size()) {
                        try {
                            EnsdfAverageSetting setting=ensdfAverageSettingMap.get(ensdfsV.get(row));
                            String name=table.getColumnName(column);
                            optionPanel.setName(name+row+"_"+column);
                            if(setting!=null)
                                optionPanel.setKeyword(setting.getCustomKeyword(name));
                        }catch(Exception e) {
                            
                        }
                        
                        return optionPanel;
                    }
               
                }else {
                    Component c= super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column); 
                    c.setFont(new Font(Font.MONOSPACED,Font.PLAIN,11)); 
                    
                    /*
                    String colName=table.getColumnName(column);
                    if(colName.equals("EG"))
                        c.setForeground(Color.RED);
                    else if("RI,TI".contains(colName))
                        c.setForeground(Color.BLUE);
                    else
                        c.setForeground(Color.BLACK);
                    */
                    
                    setHorizontalAlignment(JLabel.LEFT);
                    
                    return c;
                }

            }catch(Exception e){}
            
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }    

    }


    private class OptionPanel extends JPanel{
        
        /**
         * 
         */
        private static final long serialVersionUID = 1L;
        JRadioButton useComValueOption,useRecValueOption,omitValueOption;
        JTextField keywordField;
        ButtonGroup buttonGroup;

        int rowIndex=-1,columnIndex=-1;
        
        OptionPanel(){
            setLayout(new GridLayout(0,1));
           
            setOpaque(true);
            
            keywordField=new JTextField();
            
            buttonGroup=new ButtonGroup();
            
            useRecValueOption=new JRadioButton("Rec");
            useRecValueOption.setToolTipText("use value in the record field");
            useRecValueOption.setOpaque(false);
            useRecValueOption.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    if(((JRadioButton)e.getSource()).isSelected()){
                        keywordField.setEnabled(false);
                    }
                }
            });
            
            omitValueOption=new JRadioButton("Omit");
            omitValueOption.setToolTipText("omit value in the record field");
            omitValueOption.setOpaque(false);
            omitValueOption.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    if(((JRadioButton)e.getSource()).isSelected()){
                        keywordField.setEnabled(false);
                    }
                }
            });
            
            useComValueOption=new JRadioButton("Com");
            useComValueOption.setToolTipText("use values in average comment");
            useComValueOption.setOpaque(false);
            useComValueOption.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    if(((JRadioButton)e.getSource()).isSelected()){
                        keywordField.setEnabled(true);
                    }else
                        keywordField.setEnabled(false);
                }
            });
            
            buttonGroup.add(useComValueOption);
            buttonGroup.add(omitValueOption);
            buttonGroup.add(useRecValueOption);

            keywordField.setEnabled(useComValueOption.isSelected());
            keywordField.setColumns(20);
            keywordField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyReleased(KeyEvent e) {
                    try {
                        
                        String keyword=keywordField.getText().trim();
                        String recordName=settingTable.getColumnName(columnIndex);
                        ENSDF ens=ensdfsV.get(rowIndex);
                        EnsdfAverageSetting setting=ensdfAverageSettingMap.get(ens);
                        setting.setCustomKeyword(recordName,keyword);
                        
                        //System.out.println("focus lost: textField: name="+getName()+" text="+keywordField.getText()+" row="+rowIndex+" col="+columnIndex+" "+keywordField.hasFocus());
                        //System.out.println("     setting keyword="+setting.getCustomKeyword(recordName));
                    }catch(Exception e1) {
                        
                    }  
                }
            });
            
            add(useRecValueOption);    
            add(omitValueOption);
            add(useComValueOption);
            add(keywordField);
            
            
        }

        /*
        boolean isUseComValue() {
            return useComValueOption.isSelected();
        }
        

        void setIsUseComValue(boolean b) {
            useComValueOption.setSelected(b);
            if(b) {
                useRecValueOption.setSelected(!b);
                omitValueOption.setSelected(!b);
            }

        }
        */
        
        void setSelected(int id) {

        	if(id==2)
        		omitValueOption.setSelected(true);
        	else if(id==3)
        		useComValueOption.setSelected(true);
        	else
        		useRecValueOption.setSelected(true);
        		
        }
        
        
        @SuppressWarnings("unused")
        String getKeyword() {return keywordField.getText();}
        void setKeyword(String s) {
            keywordField.setText(s);
        }
        
        @SuppressWarnings("unused")
        void setEnable(boolean b) {
            useComValueOption.setEnabled(b);
            useRecValueOption.setEnabled(b);
            omitValueOption.setEnabled(b);
        }

    }

    private javax.swing.JButton averageButton;
    private javax.swing.JTable settingTable;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JScrollPane recordTableScrollPane;
    private JPanel settingsPanel;
    private JButton resetButton;
    private JLabel topLabel1;
    private JCheckBox convertRICheckBox;
    private JLabel topLabel2;
    private JCheckBox createAdoptedDatasetCheckBox;
    private JCheckBox forceAverageCheckBox;
    private JCheckBox alwaysAverageELCheckBox;
    private JCheckBox averageC2SCheckBox;
}
