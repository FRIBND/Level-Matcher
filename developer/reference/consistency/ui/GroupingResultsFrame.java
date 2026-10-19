
package consistency.ui;


import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.GroupLayout.Alignment;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

import consistency.base.CheckControl;
import consistency.base.EnsdfGroup;
import consistency.base.RecordGroup;
import consistency.main.Run;
import consistency.main.Setup;
import ensdfparser.ensdf.Gamma;
import ensdfparser.ensdf.Level;
import ensdfparser.ensdf.Record;
import ensdfparser.nds.ensdf.MassChain;
import ensdfparser.nds.util.Str;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.util.Vector;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;

@SuppressWarnings("unused")
public class GroupingResultsFrame extends javax.swing.JFrame {
    
        /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
		RecordGroupWrap currRecordGroupWrap; //the record group (level/gamma) we're working with
		
		DefaultTreeModel tree;
        consistency.main.Run run;
        MassChain data;
        
        Vector<EnsdfGroup> ensdfGroupsV;
        Style messengerStyle=null;
        
        String dataType="E";
        String currResults="";//store the result of the last calculation of average
        
    	static private final String newline = "\n";  
    
    public GroupingResultsFrame(MassChain chain,Run r) {
        data=chain;
    	run=r;

    	ensdfGroupsV=run.getConsistencyCheck().ensdfGroupsV();
    	
        initComponents();
        
        createLevelAndGammaTree();

        if(Setup.outdir==null){
            Setup.outdir=System.getProperty("user.dir")+"//out";
        }
        
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 11);
        messenger.setFont(font);
        messengerStyle=StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
        
        feedingTextPane.setFont(font);
        //System.out.println(((ensdf.Gamma)tree.getChild(tree.getChild(tree.getRoot(),1),0)).ES());
        
    }
    
    private void createLevelAndGammaTree(){
        
        DefaultMutableTreeNode top = new DefaultMutableTreeNode("Adopted Levels");
        
        for(int i=0;i<ensdfGroupsV.size();i++){
        	EnsdfGroup ensdfGroup=ensdfGroupsV.get(i);
        	DefaultMutableTreeNode ensdfGroupNode=new DefaultMutableTreeNode("Adopted Levels: "+ensdfGroup.NUCID());
            for(int j=0;j<ensdfGroup.levelGroupsV().size();j++){
            	RecordGroup levelGroup=ensdfGroup.levelGroupsV().get(j);
            	
            	DefaultMutableTreeNode levelGroupNode=new DefaultMutableTreeNode(new RecordGroupWrap(levelGroup,ensdfGroup));
            	for(int k=0;k<levelGroup.subgroups().size();k++){
            		RecordGroup gammaGroup=levelGroup.subgroups().get(k);
            		DefaultMutableTreeNode gammaGroupNode=new DefaultMutableTreeNode(new RecordGroupWrap(gammaGroup,levelGroup,ensdfGroup));
            		
            		levelGroupNode.add(gammaGroupNode);
            	}
            	
            	ensdfGroupNode.add(levelGroupNode);
            }
            
            top.add(ensdfGroupNode);
        }
        

        
        
        tree=new DefaultTreeModel(top);
        //tree=new LevelTree();
        ensTree.setModel(tree);
        //tree.reset(levels);
        for (int i = 0; i < ensTree.getRowCount(); i++) {
            ensTree.expandRow(i);
        }
        
        ensTree.setRootVisible(false);
        ensTree.setShowsRootHandles(true);
        
        ensTree.updateUI();

        
        
        ensTree.setCellRenderer(new DefaultTreeCellRenderer() {
            /**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			@Override
            public Component getTreeCellRendererComponent(JTree tree,
                    Object value, boolean sel, boolean expanded, boolean leaf,
                    int row, boolean hasFocus) {

		        Component c= super.getTreeCellRendererComponent(
		                tree, value, sel, expanded, leaf, row, hasFocus);
            	
		        if(value.toString().contains("FL=")){ 
		        	c.setFont(new Font(Font.MONOSPACED,Font.ITALIC,12));
		        	c.setForeground(Color.RED);
		        }else{ 
		        	c.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));
		        	c.setForeground(Color.BLACK);
		        }
		        
		        return c;
            }
        });
        
        // Remove default JTree icons
        DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) ensTree.getCellRenderer();
        renderer.setLeafIcon(null);
        renderer.setClosedIcon(null);
        renderer.setOpenIcon(null);

    }
    
    public RecordGroupWrap getCurrentRecordGroupWrap(){
        return currRecordGroupWrap;
    }
    
    
    private void setTableRenderer(JTable recordTable){
    	
    	
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        
        DefaultTableCellRenderer lineTextRenderer = new DefaultTableCellRenderer(){
            /**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			@Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
				Component c=null;
                try{
    		        c= super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);   
                    c.setFont(new Font(Font.MONOSPACED,Font.PLAIN,11));    		        
                    if(currRecordGroupWrap.recordGroup.getRecord(0) instanceof Gamma)
                    	c.setForeground(Color.RED);
                }catch(Exception e){}
                
                setHorizontalAlignment(JLabel.LEFT);
                
                return c;
            }
			
			
        };

        
        DefaultTableCellRenderer selectColumnRenderer = new DefaultTableCellRenderer(){
            /**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			@Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                try{                    
    		        if(value.getClass()==Boolean.class){
    		        	//System.out.println("###row="+row+"  col="+column+"   "+c.getClass().getName()+"  isSelected="+isSelected);
    		        	JCheckBox c=new JCheckBox();
    		        	c.setSelected(((Boolean)value).booleanValue());	        	
    	   		        if(row>=currRecordGroupWrap.recordGroup.nRecords() && column==1){
        		        	System.out.println("    2  row="+row+"  col="+column+"   "+c.getClass().getName());
        		        	return new JLabel("");
        		        }
    	   		        setHorizontalAlignment(JLabel.CENTER);
    	   		        c.setHorizontalAlignment(JLabel.CENTER);
    	   		        
    	   		        return c;
    		        }

                }catch(Exception e){}
                
                return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            }
        };

        
        recordTable.getColumnModel().getColumn(0).setCellRenderer(lineTextRenderer);
        recordTable.getColumnModel().getColumn(1).setCellRenderer(selectColumnRenderer);//if use render for Boolean column, a text instead of a checkbox will show.


        recordTable.getColumnModel().getColumn(0).setPreferredWidth(1000);
        recordTable.getColumnModel().getColumn(1).setPreferredWidth(40);
 

    	((DefaultTableCellRenderer)recordTable.getTableHeader().getDefaultRenderer()).setHorizontalAlignment(JLabel.CENTER);
    }
    
    /** runs every time a new record group is selected, sets up the interface */
    public void setCurrentRecordGroupWrap(RecordGroupWrap rgw){
        currRecordGroupWrap=rgw;
        
        recordTable.setModel(rgw.recordTableModel());
        
        //debug
        //System.out.println("In MainSetting: line 101:"+(ew.bandTableModel==null)+(ew.drawBand==null));
         
        
        //JTextField jtext=new JTextField();
        
        //jtext.setFont(new Font(Font.MONOSPACED,Font.PLAIN,11));
        //jtext.setHorizontalAlignment(JLabel.LEFT);
        //jtext.setForeground(Color.RED);
        //jtext.setBackground(Color.BLUE);
        
        //javax.swing.table.TableCellEditor editor=new DefaultCellEditor(jtext);

        //recordTable.getColumnModel().getColumn(0).setCellEditor(editor);
        //recordTable.getColumnModel().getColumn(1).setCellEditor(editor);
        //recordTable.getColumnModel().getColumn(0).setPreferredWidth(900);
        //recordTable.getColumnModel().getColumn(1).setPreferredWidth(20);
        
        recordTable.setRowHeight(18);
        recordTable.setRowMargin(-1);
        
        setTableRenderer(recordTable);  
               
        Record r=rgw.recordGroup.getRecord(0);
        String s="";
        
        if(r instanceof Level){
        	tabbedPane.setTitleAt(0, "Level Lines");
        	ToptionRadioButton.setEnabled(rgw.recordGroup.nRecordsWithT12()>0);
        	
        	RIoptionRadioButton.setEnabled(false);       	
        	if(RIoptionRadioButton.isSelected()){
                RIoptionRadioButton.setSelected(false);
        		EoptionRadioButton.setSelected(true);             
        	}
        	
        	TIoptionRadioButton.setEnabled(false);
        	if(TIoptionRadioButton.isSelected()){
                TIoptionRadioButton.setSelected(false);
        		EoptionRadioButton.setSelected(true);             
        	}
        	
        	try{
        		s="Nuclide="+r.recordLine().substring(0,5)+"      Level="+r.ES();
        		topLabel.setText(s);
        	}catch(Exception e){
        		e.printStackTrace();
        	}
        	      	
        	
            if(tabbedPane.indexOfComponent(feedingGammasPanel)<0){
            	tabbedPane.addTab("Feeding Gammas", feedingGammasPanel);
            }
            
            //tabbedPane.updateUI();
            
        	s=run.getConsistencyCheck().printFeedingGammas(rgw.recordGroup,rgw.ensdfGroup);
        	
        	feedingPanelTopLabel.setText("All gamma transitions to level="+String.format("%-10s%s",r.ES(),((Level)r).JPiS()));

        	
        	//print feeding gammas
        	//1. simple format
        	feedingTextPane.setText(s);
            
        	/*
        	//2. rich format
            try {
    			clear(feedingTextPane);
    			
    			String[] lines=s.split("\n");
            	SimpleAttributeSet style=new SimpleAttributeSet();
        	    StyleConstants.setFontFamily(style,Font.MONOSPACED);
        	    StyleConstants.setFontSize(style, 12);
        	    
        	    for(int i=0;i<lines.length;i++) {
            	    String line=lines[i];
            	    if(line.trim().isEmpty())
            	    	continue;
            	    
        	    	style=new SimpleAttributeSet();
            	    StyleConstants.setFontFamily(style,Font.MONOSPACED);
            	    StyleConstants.setFontSize(style, 12);
            	    
                	if(line.contains("GAMMA=")){
                   	    StyleConstants.setForeground(style, Color.BLUE);
                	}
           	
                	printMessage(feedingTextPane, line, style);
        	    }
               
        	    //feedingTextPane.setCaretPosition(0);
        	    //feedingTextPane.update(feedingTextPane.getGraphics());
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    		*/
            
        }else if(r instanceof Gamma){
        	tabbedPane.setTitleAt(0, "Gamma Lines");
        	RIoptionRadioButton.setEnabled(rgw.recordGroup.nRecordsWithRI()>0);
        	TIoptionRadioButton.setEnabled(rgw.recordGroup.nRecordsWithTI()>0);
        	
        	ToptionRadioButton.setEnabled(false);
        	if(ToptionRadioButton.isSelected()){
                ToptionRadioButton.setSelected(false);
        		EoptionRadioButton.setSelected(true);             
        	}
        	
        	try{
        		String parentES=((Gamma)r).ILS();
        		String parentJPS=((Gamma)r).JIS();
        		s="Nuclide="+r.recordLine().substring(0,5)+"      Parent Level="+String.format("%-10s%s",parentES,parentJPS);
        		topLabel.setText(s);
        	}catch(Exception e){}

        	if(tabbedPane.indexOfComponent(feedingGammasPanel)>=0)
        		tabbedPane.remove(feedingGammasPanel);
        }
        
        String selectedType="E";
        if(RIoptionRadioButton.isSelected())
        	selectedType="RI";
        else if(TIoptionRadioButton.isSelected())
        	selectedType="TI";
        else if(ToptionRadioButton.isSelected())
        	selectedType="T";
        
        setDatasetCheckboxes(selectedType);
    }


    
    /** set sections of the dataset checkboxes in recold-line table */
    private void setDatasetCheckboxes(String dataType){
        for(int i=0;i<currRecordGroupWrap.recordGroup.nRecords();i++){        	
        	String ds="";
        	try{
            	Record rec=currRecordGroupWrap.recordGroup.getRecord(i);
            	if(rec instanceof Level){
            		rec=(Level)rec;
            		if(dataType.equals("E"))
            			ds=rec.DES();
            		else if(dataType.equals("T"))
            			ds=((Level) rec).DT12S();
            	}else if(rec instanceof Gamma){
            		rec=(Gamma)rec;
            		if(dataType.equals("E"))
            			ds=rec.DES();
            		else if(dataType.equals("RI"))
            			ds=rec.DRIS();
            		else if(dataType.equals("TI"))
            			ds=((Gamma) rec).DTIS();
            	}
            	
            	currRecordGroupWrap.recordTableModel.setValueAt(Str.isNumeric(ds), i, 1);
            	
            	recordTable.updateUI();
        
        	}catch(Exception e){}
        }
    }
    
    
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        optionButtonGroup = new javax.swing.ButtonGroup();
        new javax.swing.ButtonGroup();
        splitPane = new javax.swing.JSplitPane();
        leftPanel = new javax.swing.JPanel();
        treeScrollPane = new javax.swing.JScrollPane();
        ensTree = new javax.swing.JTree();
        rightPanel = new javax.swing.JPanel();
        tabbedPane = new javax.swing.JTabbedPane();
        tabbedPane.setBorder(null);
        groupedLinesDisplayPanel = new javax.swing.JPanel();
        groupedLinesDisplayPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        recordTableScrollPane = new javax.swing.JScrollPane();
        recordTable = new javax.swing.JTable();

        feedingGammasPanel = new javax.swing.JPanel();
        feedingGammasPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        GroupLayout gl_rightPanel = new GroupLayout(rightPanel);
        gl_rightPanel.setHorizontalGroup(
        	gl_rightPanel.createParallelGroup(Alignment.LEADING)
        		.addGroup(gl_rightPanel.createSequentialGroup()
        			.addGap(3)
        			.addComponent(tabbedPane, GroupLayout.DEFAULT_SIZE, 1095, Short.MAX_VALUE))
        );
        gl_rightPanel.setVerticalGroup(
        	gl_rightPanel.createParallelGroup(Alignment.TRAILING)
        		.addGroup(gl_rightPanel.createSequentialGroup()
        			.addGap(3)
        			.addComponent(tabbedPane, GroupLayout.DEFAULT_SIZE, 850, Short.MAX_VALUE))
        );
        rightPanel.setLayout(gl_rightPanel);
        
        splitPane.setDividerLocation(280);

        ensTree.addTreeSelectionListener(new javax.swing.event.TreeSelectionListener() {
            public void valueChanged(javax.swing.event.TreeSelectionEvent evt) {
                ensTreeValueChanged(evt);
            }
        });
        treeScrollPane.setViewportView(ensTree);
        
        lblListOfLevel = new JLabel("List of Level and Gamma groups:");

        javax.swing.GroupLayout gl_leftPanel = new javax.swing.GroupLayout(leftPanel);
        gl_leftPanel.setHorizontalGroup(
        	gl_leftPanel.createParallelGroup(Alignment.LEADING)
        		.addGroup(gl_leftPanel.createSequentialGroup()
        			.addGroup(gl_leftPanel.createParallelGroup(Alignment.LEADING)
        				.addComponent(treeScrollPane, GroupLayout.DEFAULT_SIZE, 280, Short.MAX_VALUE)
        				.addGroup(gl_leftPanel.createSequentialGroup()
        					.addGap(6)
        					.addComponent(lblListOfLevel, GroupLayout.PREFERRED_SIZE, 204, GroupLayout.PREFERRED_SIZE)))
        			.addGap(0))
        );
        gl_leftPanel.setVerticalGroup(
        	gl_leftPanel.createParallelGroup(Alignment.LEADING)
        		.addGroup(gl_leftPanel.createSequentialGroup()
        			.addGap(7)
        			.addComponent(lblListOfLevel)
        			.addPreferredGap(ComponentPlacement.RELATED)
        			.addComponent(treeScrollPane, GroupLayout.DEFAULT_SIZE, 825, Short.MAX_VALUE)
        			.addGap(1))
        );
        leftPanel.setLayout(gl_leftPanel);
        
        splitPane.setLeftComponent(leftPanel);
        
        recordTable.setModel(new DefaultTableModel(
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
        
        setTableRenderer(recordTable);    
        
        recordTableScrollPane.setViewportView(recordTable);
        
        settingsPanel = new JPanel();
        
        JLabel selectRecordLabel = new JLabel("Select Record for averaging:");
        selectRecordLabel.setToolTipText("select record for averaging");
        
        EoptionRadioButton = new JRadioButton();
        EoptionRadioButton.setToolTipText("energy");
        EoptionRadioButton.setText("E");
        EoptionRadioButton.setSelected(true);
        
        EoptionRadioButton.addChangeListener(new ChangeListener() {
        	public void stateChanged(ChangeEvent arg0) {
                if(((JRadioButton)arg0.getSource()).isSelected()){
                	dataType="E";
                    setDatasetCheckboxes(dataType);
                }
        	}
        });
        
        RIoptionRadioButton = new JRadioButton();
        RIoptionRadioButton.setToolTipText("gamma intensity record");
        RIoptionRadioButton.setText("RI");
        RIoptionRadioButton.addChangeListener(new ChangeListener() {
        	public void stateChanged(ChangeEvent arg0) {
                if(((JRadioButton)arg0.getSource()).isSelected()){
                	dataType="RI";
                    setDatasetCheckboxes(dataType);
                }
        	}
        });
        
        ToptionRadioButton = new JRadioButton();
        ToptionRadioButton.setToolTipText("half life");
        ToptionRadioButton.setText("T1/2");
        ToptionRadioButton.addChangeListener(new ChangeListener() {
        	public void stateChanged(ChangeEvent arg0) {
                if(((JRadioButton)arg0.getSource()).isSelected()){
                	dataType="T";
                    setDatasetCheckboxes(dataType);
                }
        	}
        });
        
        TIoptionRadioButton = new JRadioButton();
        TIoptionRadioButton.setToolTipText("gamma+ce intensity record");
        TIoptionRadioButton.setText("TI");
        TIoptionRadioButton.addChangeListener(new ChangeListener() {
        	public void stateChanged(ChangeEvent arg0) {
                if(((JRadioButton)arg0.getSource()).isSelected()){
                	dataType="TI";
                    setDatasetCheckboxes(dataType);
                }
        	}
        });
        
        optionButtonGroup.add(EoptionRadioButton);
        optionButtonGroup.add(RIoptionRadioButton);
        optionButtonGroup.add(ToptionRadioButton);
        optionButtonGroup.add(TIoptionRadioButton);
        
        averageButton = new javax.swing.JButton();
        averageButton.setToolTipText("Calculate average of values of selected record from selected datasets in the table above.");
        
        
        averageButton.setText("average"); // NOI18N
        averageButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                averageButtonActionPerformed(evt);
            }
        });
        copyCommentButton = new javax.swing.JButton();
        copyCommentButton.setToolTipText("<HTML>Copy user-selected (use mouse) comment in the output area below into the system clipboard<br>"
        		                              +"If no text in the output is selected, the comment for weighted average is copied by default.");
        copyCommentButton.setText("copy comment");
        copyCommentButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyCommentButtonActionPerformed(evt);
            }
        });        

        
        updateOutputCheckBox = new JCheckBox("update output file (.avg)");
        updateOutputCheckBox.setToolTipText("If selected, the output file of all averaging results will be updated with this new result.");
        
        clearButton = new JButton("clear");
        clearButton.addActionListener(new ActionListener() {
        	public void actionPerformed(ActionEvent e) {
        		try {
					clear();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
        	}
        });
        clearButton.setToolTipText("clear results in the output area");
        

        //
       
        GroupLayout gl_panel = new GroupLayout(settingsPanel);
        gl_panel.setHorizontalGroup(
        	gl_panel.createParallelGroup(Alignment.LEADING)
        		.addGroup(gl_panel.createSequentialGroup()
        			.addContainerGap()
        			.addComponent(selectRecordLabel, GroupLayout.PREFERRED_SIZE, 190, GroupLayout.PREFERRED_SIZE)
        			.addGap(2)
        			.addComponent(EoptionRadioButton, GroupLayout.PREFERRED_SIZE, 48, GroupLayout.PREFERRED_SIZE)
        			.addPreferredGap(ComponentPlacement.RELATED)
        			.addComponent(RIoptionRadioButton, GroupLayout.PREFERRED_SIZE, 53, GroupLayout.PREFERRED_SIZE)
        			.addPreferredGap(ComponentPlacement.RELATED)
        			.addComponent(ToptionRadioButton, GroupLayout.PREFERRED_SIZE, 65, GroupLayout.PREFERRED_SIZE)
        			.addPreferredGap(ComponentPlacement.RELATED)
        			.addComponent(TIoptionRadioButton, GroupLayout.PREFERRED_SIZE, 53, GroupLayout.PREFERRED_SIZE)
        			.addGap(67)
        			.addComponent(averageButton)
        			.addGap(18)
        			.addComponent(updateOutputCheckBox)
        			.addPreferredGap(ComponentPlacement.RELATED, 89, Short.MAX_VALUE)
        			.addComponent(clearButton)
        			.addGap(79)
        			.addComponent(copyCommentButton, GroupLayout.PREFERRED_SIZE, 119, GroupLayout.PREFERRED_SIZE)
        			.addContainerGap())
        );
        gl_panel.setVerticalGroup(
        	gl_panel.createParallelGroup(Alignment.LEADING)
        		.addGroup(gl_panel.createSequentialGroup()
        			.addContainerGap()
        			.addGroup(gl_panel.createParallelGroup(Alignment.BASELINE)
        				.addComponent(selectRecordLabel)
        				.addComponent(EoptionRadioButton)
        				.addComponent(RIoptionRadioButton)
        				.addComponent(ToptionRadioButton)
        				.addComponent(averageButton)
        				.addComponent(updateOutputCheckBox)
        				.addComponent(clearButton)
        				.addComponent(copyCommentButton)
        				.addComponent(TIoptionRadioButton))
        			.addContainerGap(12, Short.MAX_VALUE))
        );
        settingsPanel.setLayout(gl_panel);
        
        topLabel = new JLabel("");
        
        messenger = new JTextPane();
		messenger.setEditable(false);
		messenger.setMargin(new Insets(5, 5, 5, 5));
		//textPane.setBorder(BorderFactory.createEmptyBorder());
        //textPane.setSize(new Dimension(300, 200));

		//DefaultCaret caret = (DefaultCaret)textArea.getCaret();
		//caret.setUpdatePolicy(DefaultCaret.OUT_TOP);
		//caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
		//caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
		
		//PrintStream printStream = new PrintStream(new CustomOutputStream(textArea));
		//System.setOut(printStream);
        messengerScrollPane = new JScrollPane();
        //scrollPane.setViewportBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null), "message", TitledBorder.LEADING, TitledBorder.TOP, null, new Color(0, 0, 0)));
        //scrollPane.setBorder(BorderFactory.createEmptyBorder());
        messengerScrollPane.setBorder(BorderFactory.createEtchedBorder(javax.swing.border.EtchedBorder.LOWERED, null, null));
        messengerScrollPane.setPreferredSize(new Dimension(340,330));
		messengerScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		messengerScrollPane.add(messenger);
        messengerScrollPane.setViewportView(messenger);


        GroupLayout gl_groupedLineDisplayPanel = new GroupLayout(groupedLinesDisplayPanel);
        gl_groupedLineDisplayPanel.setHorizontalGroup(
        	gl_groupedLineDisplayPanel.createParallelGroup(Alignment.LEADING)
        		.addGroup(gl_groupedLineDisplayPanel.createSequentialGroup()
        			.addGroup(gl_groupedLineDisplayPanel.createParallelGroup(Alignment.LEADING)
        				.addGroup(gl_groupedLineDisplayPanel.createSequentialGroup()
        					.addGap(3)
        					.addComponent(recordTableScrollPane, GroupLayout.DEFAULT_SIZE, 1088, Short.MAX_VALUE)
        					.addGap(1))
        				.addGroup(gl_groupedLineDisplayPanel.createSequentialGroup()
        					.addGap(3)
        					.addComponent(settingsPanel, GroupLayout.DEFAULT_SIZE, 1080, Short.MAX_VALUE)
        					.addGap(1))
        				.addGroup(gl_groupedLineDisplayPanel.createSequentialGroup()
        					.addGap(3)
        					.addComponent(messengerScrollPane, GroupLayout.DEFAULT_SIZE, 1089, Short.MAX_VALUE))
        				.addGroup(gl_groupedLineDisplayPanel.createSequentialGroup()
        					.addGap(18)
        					.addComponent(topLabel, GroupLayout.PREFERRED_SIZE, 1064, GroupLayout.PREFERRED_SIZE)))
        			.addGap(3))
        );
        gl_groupedLineDisplayPanel.setVerticalGroup(
        	gl_groupedLineDisplayPanel.createParallelGroup(Alignment.LEADING)
        		.addGroup(gl_groupedLineDisplayPanel.createSequentialGroup()
        			.addContainerGap()
        			.addComponent(topLabel, GroupLayout.PREFERRED_SIZE, 26, GroupLayout.PREFERRED_SIZE)
        			.addPreferredGap(ComponentPlacement.RELATED)
        			.addComponent(recordTableScrollPane, GroupLayout.PREFERRED_SIZE, 177, GroupLayout.PREFERRED_SIZE)
        			.addGap(10)
        			.addComponent(settingsPanel, GroupLayout.PREFERRED_SIZE, 46, GroupLayout.PREFERRED_SIZE)
        			.addPreferredGap(ComponentPlacement.RELATED)
        			.addComponent(messengerScrollPane, GroupLayout.DEFAULT_SIZE, 544, Short.MAX_VALUE)
        			.addGap(1))
        );
        
        groupedLinesDisplayPanel.setLayout(gl_groupedLineDisplayPanel);
        
        //tableControlPanel=new TableControlPanel(data,curEnsdf,run);      
        //tabbedPane.addTab("Table settings", tableControlPanel);
        
              
        tabbedPane.addTab("Grouped Lines", groupedLinesDisplayPanel);


    	
        tabbedPane.addTab("feeding gammas", feedingGammasPanel);
        
        feedingPanelScrollPane = new JScrollPane();
        
        feedingPanelTopLabel = new JLabel("infor");
        GroupLayout gl_feedingGammasPanel = new GroupLayout(feedingGammasPanel);
        gl_feedingGammasPanel.setHorizontalGroup(
        	gl_feedingGammasPanel.createParallelGroup(Alignment.LEADING)
        		.addGroup(gl_feedingGammasPanel.createSequentialGroup()
        			.addGap(3)
        			.addGroup(gl_feedingGammasPanel.createParallelGroup(Alignment.LEADING)
        				.addComponent(feedingPanelScrollPane, GroupLayout.DEFAULT_SIZE, 1175, Short.MAX_VALUE)
        				.addComponent(feedingPanelTopLabel, GroupLayout.PREFERRED_SIZE, 1064, GroupLayout.PREFERRED_SIZE))
        			.addGap(3))
        );
        gl_feedingGammasPanel.setVerticalGroup(
        	gl_feedingGammasPanel.createParallelGroup(Alignment.LEADING)
        		.addGroup(gl_feedingGammasPanel.createSequentialGroup()
        			.addContainerGap()
        			.addComponent(feedingPanelTopLabel, GroupLayout.PREFERRED_SIZE, 26, GroupLayout.PREFERRED_SIZE)
        			.addPreferredGap(ComponentPlacement.UNRELATED)
        			.addComponent(feedingPanelScrollPane, GroupLayout.DEFAULT_SIZE, 777, Short.MAX_VALUE)
        			.addGap(1))
        );
        
        feedingTextPane = new JTextPane();
        feedingTextPane.setEditable(false);
		feedingTextPane.setMargin(new Insets(5, 5, 5, 5));
		
        feedingPanelScrollPane.setViewportView(feedingTextPane);
        feedingGammasPanel.setLayout(gl_feedingGammasPanel);
        
        splitPane.setRightComponent(rightPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        layout.setHorizontalGroup(
        	layout.createParallelGroup(Alignment.TRAILING)
        		.addGroup(layout.createSequentialGroup()
        			.addGap(3)
        			.addComponent(splitPane, GroupLayout.PREFERRED_SIZE, 1384, Short.MAX_VALUE)
        			.addGap(3))
        );
        layout.setVerticalGroup(
        	layout.createParallelGroup(Alignment.LEADING)
        		.addGroup(layout.createSequentialGroup()
        			.addComponent(splitPane, GroupLayout.DEFAULT_SIZE, 865, Short.MAX_VALUE)
        			.addGap(3))
        );
        getContentPane().setLayout(layout);

        pack();
        
        setResizable(false);
        
    }// </editor-fold>//GEN-END:initComponents

    
    private void averageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Output_ButtonActionPerformed
        //call the rest of the program to actually produce the output  

        currResults="";
        try{
            RecordGroupWrap rgw=getCurrentRecordGroupWrap();
            if(rgw==null || rgw.recordGroup==null){
            	JOptionPane.showMessageDialog(this,"You haven't selected a level/gamma from the list.");
            	return;
            }
            
            int size=rgw.getSelectedRecordsV().size();
            if(size==0){
            	JOptionPane.showMessageDialog(this,"You haven't selected any datasets in the table.");
            	return;
            }
            
            if(size==1){
            	printMessage("No average for "+dataType+": only one dataset!");
            	return;
            }
            	
            RecordGroup tempGroup=rgw.recordGroup.lightCopy();
            for(int i=0;i<rgw.recordGroup.nRecords();i++){
            	if(!rgw.isSelected(i))
            		tempGroup.removeRecord(rgw.recordGroup.getRecord(i));
            }
            
            
            String msg="";
            //msg=run.getCheck().cleanRecordGroupForAverage(tempGroup, dataType);
            
            float weightLimit=0;//=0, no restriction on weightlimit and all selected points will be used in averaging
            currResults=run.getConsistencyCheck().printAverageReportOfRecordGroup(tempGroup, dataType,weightLimit);
            
            if(currResults.length()==0){
            	printMessage("No average for "+dataType+": no enough data points of "+dataType+" with uncertainties");
            	if(msg.length()>0)
            		printMessage(msg);
            	
            	return;
            }

            printMessage(currResults);
            
            if(updateOutputCheckBox.isSelected() && CheckControl.writeAVG){            	
            	//id is the unique identifier of a group (level or gamma) in an ENSDF group and generated in the average report  
            	run.getConsistencyCheck().updateAverageOutput(run.getOutFilename(),rgw.recordGroup,currResults,dataType);
            }
           
            
        }catch(NullPointerException e1){
                e1.printStackTrace();
                JOptionPane.showMessageDialog(this,"Null Pointer: Make sure a level/gamma is selected from the tree on the left");
        }catch(ArrayIndexOutOfBoundsException e2){
                e2.printStackTrace();
                JOptionPane.showMessageDialog(this,"Array out of bounds when creating averaging results");
        }catch(Exception e3){
                e3.printStackTrace();
                JOptionPane.showMessageDialog(this,e3);
        }
    }//GEN-LAST:event_Output_ButtonActionPerformed

    private void ensTreeValueChanged(javax.swing.event.TreeSelectionEvent evt) {//GEN-FIRST:event_ensTreeValueChanged
    //a dataset has been selected, set it as the current ensdf
       javax.swing.tree.TreePath tp=ensTree.getSelectionPath();
       Object o=tp.getLastPathComponent();

       
       if(o==null || o.getClass()!=DefaultMutableTreeNode.class){ 
    	   currRecordGroupWrap=null;
    	   return;
       }
       
       try{
    	   RecordGroupWrap rgw=(RecordGroupWrap) ((DefaultMutableTreeNode)o).getUserObject();
    	   setCurrentRecordGroupWrap(rgw);
    	   
    	   //System.out.println("****size="+rgw.recordGroup.nRecords()+"  "+rgw.recordGroup.getRecord(0).ES());
       }catch(Exception e){
    	   //e.printStackTrace();
    	   currRecordGroupWrap=null;
    	   return;
       }
       
       return;
      
    }//GEN-LAST:event_ensTreeValueChanged

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        //if(!skeletonMade) 
         //   JOptionPane.showMessageDialog(this,"You didn't run the skeleton generator (thats gonna cause trouble later....)");
    }//GEN-LAST:event_formWindowClosing

    private void copyCommentButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_include_ButtonActionPerformed
      
        try{
        	
        	String text=messenger.getSelectedText();
        	if(text==null || text.isEmpty()){
        		Vector<String> commentsV=run.getConsistencyCheck().parseComments(currResults);
        		text=commentsV.get(0);
        		//for(int i=0;i<commentsV.size();i++)
        		//	System.out.println(commentsV.get(i));
        	}else{
        		messenger.select(0,0);//
        	}
        	
        	Toolkit.getDefaultToolkit()
            .getSystemClipboard()
            .setContents(
                    new StringSelection(text),
                    null
            );

        	//System.out.println(messenger.getSelectedText());
        	
        }catch(Exception e){
        }
    }//GEN-LAST:event_include_ButtonActionPerformed


    
    
    /*    
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new NDSFrame().setVisible(true);
            }
        });
    }
   */ 
    
    //////////////////
    public void printMessage(JTextPane textPane,String message){
    	printMessage(textPane,message,messengerStyle);	
    }
    
    public void printMessage(JTextPane textPane,String message,AttributeSet style){

    	if(message==null)
    		return;
    	
    	if(!message.contains(newline) || message.indexOf(newline)<message.length()-1)
    		message+=newline;
		
    	printMessageAsIs(textPane,message,style);	
    }
    
    public void printMessageAsIs(JTextPane textPane,String message,AttributeSet style){

    	if(message==null)
    		return;
    	  	
    	//System.out.print(message);
    	   			
    	if(textPane!=null){	
			StyledDocument doc=textPane.getStyledDocument();
			try {
				doc.insertString(doc.getLength(),message,style);
			} catch (BadLocationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
    		//messenger.setCaretPosition(messenger.getDocument().getLength());
    		//textPane.update(textPane.getGraphics());
    		
    		//try {
			//	messenger.scrollRectToVisible(messenger.modelToView(messenger.getDocument().getLength()));
			//} catch (BadLocationException e) {
			//	System.out.println("Error");
			//	e.printStackTrace();
			//}
    		

    		/*
    		//THIS DOES NOT AUTOMATICALLY SCROLL TEXT 
    		DefaultCaret caret = (DefaultCaret)messenger.getCaret();
    		//caret.setUpdatePolicy(DefaultCaret.OUT_BOTTOM);
    		//caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);    
    		caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
    		//messenger.setCaretPosition(messenger.getDocument().getLength());
    		 */
    	}
    }
    
    public void printMessageAsIs(JTextPane textPane,String message){
    	Style style=StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
    	printMessageAsIs(textPane,message,style);
    }
    
    public void clear(JTextPane textPane) throws IOException{
  
        if(textPane!=null){
        	textPane.setText("");
        	textPane.setCaretPosition(textPane.getDocument().getLength());
        	textPane.update(textPane.getGraphics());
        }
    }
    
    public void printMessage(String message){
    	printMessage(messenger,message);	
    }
    
    public void printMessage(String message,AttributeSet style){
    	printMessage(messenger,message,style);	
    }
    
    public void printMessageAsIs(String message,AttributeSet style){
    	printMessageAsIs(messenger,message,style);

    }
    
    public void printMessageAsIs(String message){
    	printMessageAsIs(messenger,message);
    }
    
    public void clear() throws IOException{
    	clear(messenger);
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTabbedPane tabbedPane;
    private javax.swing.JButton averageButton;
    private javax.swing.JTable recordTable;
    private javax.swing.ButtonGroup optionButtonGroup;
    private javax.swing.JTree ensTree;
    private javax.swing.JButton copyCommentButton;
    private javax.swing.JPanel leftPanel;
    private javax.swing.JPanel rightPanel;
    private javax.swing.JPanel groupedLinesDisplayPanel;
    private javax.swing.JPanel feedingGammasPanel;
    private javax.swing.JScrollPane treeScrollPane;
    private javax.swing.JScrollPane recordTableScrollPane;
    private javax.swing.JSplitPane splitPane;
    private JPanel settingsPanel;
    private JTextPane messenger;
    private JScrollPane messengerScrollPane;
    private JRadioButton EoptionRadioButton;
    private JRadioButton RIoptionRadioButton;
    private JRadioButton ToptionRadioButton;
    private JButton clearButton;
    private JLabel lblListOfLevel;
    private JLabel topLabel;
    private JScrollPane feedingPanelScrollPane;
    private JLabel feedingPanelTopLabel;
    private JTextPane feedingTextPane;
    private JCheckBox updateOutputCheckBox;
    private JRadioButton TIoptionRadioButton;
}
