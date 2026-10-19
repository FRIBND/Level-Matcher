
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
import consistency.base.ConsistencyCheck;
import consistency.base.EnsdfGroup;
import consistency.base.RecordGroup;
import consistency.main.Run;
import consistency.main.Setup;
import ensdfparser.ensdf.ENSDF;
import ensdfparser.ensdf.Gamma;
import ensdfparser.ensdf.Level;
import ensdfparser.ensdf.Record;
import ensdfparser.nds.ensdf.EnsdfUtil;
import ensdfparser.nds.ensdf.MassChain;
import ensdfparser.nds.util.Str;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Vector;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

@SuppressWarnings("unused")
public class DatasetViewerFrame extends javax.swing.JFrame {
    
        /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

		EnsdfWrap currEnsdfWrap; //the ensdf we're working with
		
		DefaultTreeModel tree;
        consistency.main.Run run;
        MassChain data;
        
        //NOTE that it is just a simple group of datasets by nuclide. 
        //It is not sorted and grouped by levels/gamms/decays
        Vector<EnsdfGroup> datasetGroupsV;//also including datasets like comment dataset
        
        Style messengerStyle=null;
        
        String dataType="E";
        String currResults="";//store the result of the last calculation of average
        
    	static private final String newline = "\n";  
    
        boolean skeletonMade;      
        
    public DatasetViewerFrame(MassChain chain,Run r) {
        data=chain;
    	run=r;

    	ConsistencyCheck check=new ConsistencyCheck();//just used for grouping ENSDF datasets, nothing more (no check)
    	
        initComponents();
        
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        messenger.setFont(font);
        messengerStyle=StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
        
        datasetViewerTextPane.setFont(font);
        
    	try {
			datasetGroupsV=check.groupDatasets(data);
		} catch (Exception e) {
			printMessage("Can't load datasets. Please check if there is any problem with datasets");
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
    	
        createDatasetTree();

        if(Setup.outdir==null){
            Setup.outdir=System.getProperty("user.dir")+"//out";
        }
        

        //System.out.println(((ensdf.Gamma)tree.getChild(tree.getChild(tree.getRoot(),1),0)).ES());
        
    }
    
    private void createDatasetTree(){
        
        DefaultMutableTreeNode top = new DefaultMutableTreeNode();
        
        int prevA=0,currA=0;
        String NUCID="";
        DefaultMutableTreeNode massNode=null,nuclideNode=null,datasetNode=null;
        
        for(int i=0;i<datasetGroupsV.size();i++){
        	EnsdfGroup ensdfGroup=datasetGroupsV.get(i);
        	currA=ensdfGroup.ensdfV().get(0).nucleus().A();
        	NUCID=ensdfGroup.NUCID();
  
        	if(currA!=prevA) {            	
        		massNode=new DefaultMutableTreeNode("A="+currA);
        		prevA=currA;
        	}

            
        	nuclideNode=new DefaultMutableTreeNode(NUCID);
        	    	
        	for(int j=0;j<ensdfGroup.ensdfV().size();j++){
            	ENSDF ens=ensdfGroup.ensdfV().get(j);            	
            	datasetNode=new DefaultMutableTreeNode(new EnsdfWrap(ens,ens.DSId()));           	
            	nuclideNode.add(datasetNode);
            }
            
        	massNode.add(nuclideNode);
            top.add(massNode);
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
    
    public EnsdfWrap getCurrentEnsdfWrap(){
        return currEnsdfWrap;
    }
    
    

    
    /** runs every time a new record group is selected, sets up the interface */
    public void setCurrentEnsdfWrap(EnsdfWrap ew){
        currEnsdfWrap=ew;
        
        String s="",line="",postfix="",type="";
        ENSDF ens=ew.ens;
        SimpleAttributeSet style=new SimpleAttributeSet();
        
        datasetViewerPanelTopLabel.setText("DSID="+ew.toString());
        updateDatasetButton.setEnabled(true);
        
        try {
			clear(datasetViewerTextPane);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        for(int i=0;i<ens.lines().size();i++){
        	line=ens.lineAt(i);
        	type=line.substring(5,8).trim();
        	
        	String tempLine=line.substring(0).trim();
        	String newLine="";
        	while(tempLine.length()>80){
        		newLine+=tempLine.substring(0, 80);
        		tempLine=tempLine.substring(80).trim();
        		if(tempLine.length()>0)
        			newLine+="\n";
        	}
        	newLine+=Str.fixLineLength(tempLine,80);
        				
        	postfix=String.format("%-5s%-5d","|", i+1);
        	newLine+=postfix;
        	
    	    style=new SimpleAttributeSet();
    	    StyleConstants.setFontFamily(style,Font.MONOSPACED);
    	    StyleConstants.setFontSize(style, 12);
        	if(type.length()>0){
            	if("LQPN".contains(type)){
            	    style=new SimpleAttributeSet();
            	    StyleConstants.setForeground(style, Color.BLUE);
            	    StyleConstants.setFontFamily(style,Font.MONOSPACED);
            	    StyleConstants.setFontSize(style, 12);
            	}else if(type.equals("G")){
            	    style=new SimpleAttributeSet();
            	    StyleConstants.setForeground(style, Color.RED);
            	    StyleConstants.setFontFamily(style,Font.MONOSPACED);
            	    StyleConstants.setFontSize(style, 12);
            	}
        	}
   	
        	printMessage(datasetViewerTextPane, newLine, style);
        }
        
    	datasetViewerTextPane.setCaretPosition(0);
    	datasetViewerTextPane.update(datasetViewerTextPane.getGraphics());
    	
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
        			.addGap(2)
        			.addComponent(tabbedPane, GroupLayout.PREFERRED_SIZE, 704, Short.MAX_VALUE)
        			.addGap(0))
        );
        gl_rightPanel.setVerticalGroup(
        	gl_rightPanel.createParallelGroup(Alignment.TRAILING)
        		.addGroup(gl_rightPanel.createSequentialGroup()
        			.addGap(3)
        			.addComponent(tabbedPane, GroupLayout.DEFAULT_SIZE, 860, Short.MAX_VALUE))
        );
        rightPanel.setLayout(gl_rightPanel);
        
        splitPane.setDividerLocation(280);

        ensTree.addTreeSelectionListener(new javax.swing.event.TreeSelectionListener() {
            public void valueChanged(javax.swing.event.TreeSelectionEvent evt) {
                ensTreeValueChanged(evt);
            }
        });
        treeScrollPane.setViewportView(ensTree);
        
        lblListOfLevel = new JLabel("List of Datasets:");

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

        
        recordTableScrollPane.setViewportView(recordTable);
        
        settingsPanel = new JPanel();
        
        JLabel selectRecordLabel = new JLabel("Select Record for averaging:");
        selectRecordLabel.setToolTipText("select record for averaging");
        
        EoptionRadioButton = new JRadioButton();
        EoptionRadioButton.setToolTipText("energy");
        EoptionRadioButton.setText("E");
        EoptionRadioButton.setSelected(true);
        EoptionRadioButton.addActionListener(new java.awt.event.ActionListener(){
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataType="E";
            }
        });
        
        RIoptionRadioButton = new JRadioButton();
        RIoptionRadioButton.setToolTipText("gamma intensity record");
        RIoptionRadioButton.setText("RI");
        RIoptionRadioButton.addActionListener(new java.awt.event.ActionListener(){
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataType="RI";
            }
        });
        
        ToptionRadioButton = new JRadioButton();
        ToptionRadioButton.setToolTipText("half life");
        ToptionRadioButton.setText("T1/2");
        ToptionRadioButton.addActionListener(new java.awt.event.ActionListener(){
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataType="T";
            }
        });
        
        optionButtonGroup.add(EoptionRadioButton);
        optionButtonGroup.add(RIoptionRadioButton);
        optionButtonGroup.add(ToptionRadioButton);
        
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
        
                datasetViewerPanel = new javax.swing.JPanel();
                datasetViewerPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
                
                
                    	
                        tabbedPane.addTab("Dataset Lines", datasetViewerPanel);
                        
                        datasetViewerScrollPane = new JScrollPane();
                        
                        datasetViewerPanelTopLabel = new JLabel("infor");
                        
                        updateJPICheckBox = new JCheckBox("J");
                        
                        updateTCheckBox = new JCheckBox("T");
                        
                        updateMULCheckBox = new JCheckBox("MUL");
                        
                        updateMRCheckBox = new JCheckBox("MR");
                        
                        updateDatasetButton = new JButton("Update dataset with Adopted");
                        updateDatasetButton.setEnabled(false);
                        updateDatasetButton.addActionListener(new ActionListener() {
                        	public void actionPerformed(ActionEvent e) {
                        		updateDatasetButtonActionPerformed(e);
                        	}
                        });
                        updateDatasetButton.setToolTipText("<HTML>Update specified records of selected dataset with adopted values <br>"
                        		+                                "if available and write updated dataset into a separate ENSDF file<br>"
                        		+                                "(file name ends with UPDATED)</HTML>");
                        GroupLayout gl_feedingGammasPanel = new GroupLayout(datasetViewerPanel);
                        gl_feedingGammasPanel.setHorizontalGroup(
                        	gl_feedingGammasPanel.createParallelGroup(Alignment.LEADING)
                        		.addGroup(gl_feedingGammasPanel.createSequentialGroup()
                        			.addGap(3)
                        			.addGroup(gl_feedingGammasPanel.createParallelGroup(Alignment.LEADING)
                        				.addGroup(gl_feedingGammasPanel.createSequentialGroup()
                        					.addComponent(datasetViewerPanelTopLabel, GroupLayout.PREFERRED_SIZE, 287, GroupLayout.PREFERRED_SIZE)
                        					.addGap(1)
                        					.addComponent(updateDatasetButton, GroupLayout.PREFERRED_SIZE, 200, GroupLayout.PREFERRED_SIZE)
                        					.addGap(4)
                        					.addComponent(updateJPICheckBox, GroupLayout.PREFERRED_SIZE, 36, GroupLayout.PREFERRED_SIZE)
                        					.addPreferredGap(ComponentPlacement.RELATED)
                        					.addComponent(updateTCheckBox, GroupLayout.PREFERRED_SIZE, 38, GroupLayout.PREFERRED_SIZE)
                        					.addPreferredGap(ComponentPlacement.RELATED)
                        					.addComponent(updateMULCheckBox, GroupLayout.PREFERRED_SIZE, 56, GroupLayout.PREFERRED_SIZE)
                        					.addPreferredGap(ComponentPlacement.RELATED)
                        					.addComponent(updateMRCheckBox, GroupLayout.PREFERRED_SIZE, 49, GroupLayout.PREFERRED_SIZE)
                        					.addContainerGap())
                        				.addGroup(gl_feedingGammasPanel.createSequentialGroup()
                        					.addComponent(datasetViewerScrollPane, GroupLayout.DEFAULT_SIZE, 707, Short.MAX_VALUE)
                        					.addGap(2))))
                        );
                        gl_feedingGammasPanel.setVerticalGroup(
                        	gl_feedingGammasPanel.createParallelGroup(Alignment.LEADING)
                        		.addGroup(gl_feedingGammasPanel.createSequentialGroup()
                        			.addGap(6)
                        			.addGroup(gl_feedingGammasPanel.createParallelGroup(Alignment.BASELINE)
                        				.addComponent(datasetViewerPanelTopLabel, GroupLayout.PREFERRED_SIZE, 26, GroupLayout.PREFERRED_SIZE)
                        				.addComponent(updateJPICheckBox)
                        				.addComponent(updateTCheckBox)
                        				.addComponent(updateMULCheckBox)
                        				.addComponent(updateMRCheckBox)
                        				.addComponent(updateDatasetButton))
                        			.addPreferredGap(ComponentPlacement.UNRELATED)
                        			.addComponent(datasetViewerScrollPane, GroupLayout.DEFAULT_SIZE, 785, Short.MAX_VALUE)
                        			.addGap(1))
                        );
                        
                        datasetViewerTextPane = new JTextPane();
                        datasetViewerTextPane.setEditable(false);
                        datasetViewerTextPane.setMargin(new Insets(5, 5, 5, 5));
                        
        datasetViewerScrollPane.setViewportView(datasetViewerTextPane);
        datasetViewerPanel.setLayout(gl_feedingGammasPanel);
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
        			.addGap(118)
        			.addComponent(averageButton)
        			.addGap(18)
        			.addComponent(updateOutputCheckBox)
        			.addPreferredGap(ComponentPlacement.RELATED, 163, Short.MAX_VALUE)
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
        				.addComponent(copyCommentButton))
        			.addContainerGap(13, Short.MAX_VALUE))
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
        
              
        //tabbedPane.addTab("Grouped Lines", groupedLinesDisplayPanel);
        
        splitPane.setRightComponent(rightPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        layout.setHorizontalGroup(
        	layout.createParallelGroup(Alignment.TRAILING)
        		.addGroup(layout.createSequentialGroup()
        			.addGap(3)
        			.addComponent(splitPane, GroupLayout.PREFERRED_SIZE, 993, Short.MAX_VALUE)
        			.addGap(1))
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

    

    protected void updateDatasetButtonActionPerformed(ActionEvent evt) {
		//NOTE that the datasetGroups here has nothing to do with ensdfGroups in run.getCheck()
		//in which all ensdfGroups have been sorted and grouped by levels/gamma/decays/delays.
		//So in order to get level/gamma grouping information, ENSDF and EnsdfGroups objects of
		//run.getCheck() should be used
		ConsistencyCheck activeCheck=run.getConsistencyCheck();
	
		if(activeCheck.ensdfGroupsV().size()==0) {
        	JOptionPane.showMessageDialog(this,"You must first click \"Start Checking\" on main window to group ENSDF");
        	return;
		}
		
		ENSDF ens=activeCheck.getENSDFByDSID(currEnsdfWrap.ens.DSId0());
		String NUCID=ens.nucleus().nameENSDF().trim();//nameENSDF return a 5-char string including heading/tailing space, like " 35S "
    
		ArrayList<String> recordList=new ArrayList<String>();
		if(updateJPICheckBox.isSelected())
			recordList.add("J");
		if(updateTCheckBox.isSelected())
			recordList.add("T");
		if(updateMULCheckBox.isSelected())
			recordList.add("M");
		if(updateMRCheckBox.isSelected())
			recordList.add("MR");
		
		if(recordList.isEmpty()) {
        	JOptionPane.showMessageDialog(this,"You haven't selected any record to update.");
        	return;
		}
		
		String filepath="";
		

		
		try {
			 for(int i=0;i<run.filesV().size();i++){
				 File f=run.filesV().get(i);
				 String firstLine=Str.readLines(f,1).get(0);
				 String DSID=EnsdfUtil.getDSIDFromLine(firstLine);
				 if(ens.DSId0().equals(DSID))
					 filepath=f.getAbsolutePath();			 
			 }
			 
				
			 //System.out.println(currEnsdfWrap.ens.DSId0()+" ###  "+ens.nucleus().A()+"$$$"+NUCID);
			 //System.out.println("$$$$"+(activeCheck.getEnsdfGroupByNUCID(NUCID)==null)+"  "+activeCheck.ensdfGroupsV().size()+"  "+activeCheck.ensdfGroupsV().get(0).adopted());
				
			 if(filepath.length()>0) {
				 String filename=Str.getNameFromPath(filepath);
				 filepath=consistency.main.Setup.outdir+Str.dirSeparator()+filename;
			 }
			 String msg=activeCheck.updateENSDFWithAdopted(ens, recordList, activeCheck.getEnsdfGroupByNUCID(NUCID), filepath+".UPDATED");
				

				
			 run.printMessage("\n\n"+msg);
		}catch(Exception e) {			
			e.printStackTrace();
		}
	

	}

	private void averageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Output_ButtonActionPerformed
        //call the rest of the program to actually produce the output  

        currResults="";
        try{
            EnsdfWrap ew=getCurrentEnsdfWrap();
            if(ew==null){
            	JOptionPane.showMessageDialog(this,"You haven't selected a level/gamma from the list.");
            	return;
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
    	   currEnsdfWrap=null;
    	   return;
       }
       
       try{
    	   EnsdfWrap ew=(EnsdfWrap) ((DefaultMutableTreeNode)o).getUserObject();
    	   setCurrentEnsdfWrap(ew);
    	   
    	   //System.out.println("****size="+rgw.recordGroup.nRecords()+"  "+rgw.recordGroup.getRecord(0).ES());
       }catch(Exception e){
    	   //e.printStackTrace();
    	   currEnsdfWrap=null;
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
    private javax.swing.JPanel datasetViewerPanel;
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
    private JScrollPane datasetViewerScrollPane;
    private JLabel datasetViewerPanelTopLabel;
    private JTextPane datasetViewerTextPane;
    private JCheckBox updateOutputCheckBox;
    private JCheckBox updateJPICheckBox;
    private JCheckBox updateTCheckBox;
    private JCheckBox updateMULCheckBox;
    private JCheckBox updateMRCheckBox;
    private JButton updateDatasetButton;
}
