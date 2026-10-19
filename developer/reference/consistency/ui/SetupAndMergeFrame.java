package consistency.ui;

import java.awt.Color;
import java.awt.SystemColor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.GroupLayout.Alignment;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import consistency.main.Run;
import consistency.main.Setup;
import ensdfparser.ensdf.Nucleus;
import ensdfparser.nds.ensdf.EnsdfUtil;
import ensdfparser.nds.ensdf.MassChain;
import ensdfparser.nds.util.Str;

import javax.swing.JMenuItem;
import javax.swing.JLabel;


public class SetupAndMergeFrame extends javax.swing.JFrame {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private JPanel mergePanel;
	private javax.swing.ButtonGroup buttonGroup1,buttonGroup2,extButtonGroup;
	private JRadioButton setupByMassChainRdBtn;
	private JRadioButton setupByNuclideListRdBtn;

	@SuppressWarnings("unused")
	private MasterFrame parentFrame;
	private JButton loadMassChainButtonForSetup;
	private JButton loadNuclideListButton;
	private JButton setButton;
	private JButton mergeButton;
	private JButton loadDatasetsButtonForMerging;
	private JRadioButton mergeInputDatasetsRdBtn;
	private JRadioButton mergeAllRdBtn;
	private JButton selectFoldersButton;
	

	//for set up evaluation folders
	private MassChain chain=null;
	private Vector<String> nucNames=new Vector<String>();
	private String basePath="";
	private String fileExt="ens";//default output file extension="ens" for individual datasets
	
	//for merging datasets
	private Vector<File> filesV=new Vector<File>();
    private String topDir="";
	
	private JTextField pathField;
	private JButton pathButton;
	private JPopupMenu popupMenu;
	
	private Run run;
	private JTextField otherExtTextField;
	private JRadioButton oldExtRadioButton;
	private JLabel fileExtLabel;
	private JRadioButton newExtRadioButton;
	private JRadioButton ensExtRadioButton;
	private JRadioButton xundlExtRadioButton;
	private JRadioButton otherExtRadioButton;

	
    public SetupAndMergeFrame(MasterFrame pFrame,Run run) {
    	this.run=run;
    	this.parentFrame=pFrame;   	
    	
    	basePath=Setup.outdir;
    	
    	if(this.run==null)
    		this.run=new Run();
    	
        initComponents();
        pathField.setText(basePath);
        
    }
		
    private void enableExtRadioButtons(boolean enable){
		fileExtLabel.setEnabled(enable);
		oldExtRadioButton.setEnabled(enable);
		newExtRadioButton.setEnabled(enable);
		ensExtRadioButton.setEnabled(enable);
		xundlExtRadioButton.setEnabled(enable);
		otherExtRadioButton.setEnabled(enable);
		otherExtTextField.setEnabled(enable);
    }
    
	private void initComponents() {
		setTitle("Setup folders and Merge datasets");
		
		
		mergePanel = new JPanel();
		mergePanel.setBorder(new TitledBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null), "", TitledBorder.LEADING, TitledBorder.TOP, null, new Color(59, 59, 59)), "Merge Datasets", TitledBorder.LEADING, TitledBorder.TOP, null, new Color(59, 59, 59)));
		
		JPanel setupPanel = new JPanel();
		setupPanel.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null), "Setup Evaluation Folders", TitledBorder.LEADING, TitledBorder.TOP, null, new Color(59, 59, 59)));
		
		setupByMassChainRdBtn = new JRadioButton("from a mass chain");
		setupByMassChainRdBtn.setToolTipText("Set up evaluation folders using the input ENSDF file of a mass chain");
		
		setupByNuclideListRdBtn = new JRadioButton("from a nuclide list");
		setupByNuclideListRdBtn.setToolTipText("Set up evaluation folders using a list of nuclides");
		
		buttonGroup1=new javax.swing.ButtonGroup();
        buttonGroup1.add(setupByMassChainRdBtn);
        setupByMassChainRdBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
            	setupByDatasetsRadioButtonActionPerformed(evt);
            }
        });

        buttonGroup1.add(setupByNuclideListRdBtn);
        setupByNuclideListRdBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
            	setupByNuclideRadioButtonActionPerformed(evt);
            }

        });
		
		loadMassChainButtonForSetup = new JButton("Load MassChain");
		loadMassChainButtonForSetup.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				setupByMassChainRdBtn.setSelected(true);
				loadENSDFButtonActionPerformed(arg0);
				setButton.setEnabled(true);
				
				enableExtRadioButtons(true);
			}
		});
		
		loadNuclideListButton = new JButton("Load Nulide List");
		loadNuclideListButton.addMouseListener(new MouseAdapter() {
        	@Override
        	public void mouseClicked(MouseEvent evt) {
				setupByNuclideListRdBtn.setSelected(true);
				loadNuclideButtonMouseClicked(evt);
				setButton.setEnabled(true);
				
				enableExtRadioButtons(false);
        	}
        });
		
		setButton = new JButton("Set");
		setButton.setEnabled(false);
		setButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setButtonActionPerformed(e);
			}
		});
		
		pathField = new JTextField();
        pathField.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                pathFieldPropertyChange(evt);
            }
        });
        pathField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                pathFieldKeyReleased(evt);
            }
        });
        
		pathButton = new JButton("path");
		pathButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				pathButtonActionPerformed(e);
			}
		});
		
		fileExtLabel = new JLabel("outfile extension:");
		fileExtLabel.setToolTipText("<HTML>Specify extension of output file to be saved for each dataset<br>Default=old</HTML>");
		
		oldExtRadioButton = new JRadioButton("old");
		oldExtRadioButton.setSelected(true);
		
		newExtRadioButton = new JRadioButton("new");
		
		ensExtRadioButton = new JRadioButton("ens");
		
		xundlExtRadioButton = new JRadioButton("xundl");
		
		otherExtRadioButton = new JRadioButton("");
		
		otherExtTextField = new JTextField();
		otherExtTextField.setColumns(10);
        
		extButtonGroup=new javax.swing.ButtonGroup();

        extButtonGroup.add(oldExtRadioButton);
        extButtonGroup.add(newExtRadioButton);
        extButtonGroup.add(ensExtRadioButton);
        extButtonGroup.add(xundlExtRadioButton);
        extButtonGroup.add(otherExtRadioButton);

        enableExtRadioButtons(false);
		
		GroupLayout gl_setupPanel = new GroupLayout(setupPanel);
		gl_setupPanel.setHorizontalGroup(
			gl_setupPanel.createParallelGroup(Alignment.LEADING)
				.addGroup(gl_setupPanel.createSequentialGroup()
					.addGroup(gl_setupPanel.createParallelGroup(Alignment.LEADING)
						.addGroup(gl_setupPanel.createSequentialGroup()
							.addContainerGap()
							.addGroup(gl_setupPanel.createParallelGroup(Alignment.TRAILING, false)
								.addComponent(setupByMassChainRdBtn, Alignment.LEADING, GroupLayout.DEFAULT_SIZE, 150, Short.MAX_VALUE)
								.addComponent(setupByNuclideListRdBtn, Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 160, GroupLayout.PREFERRED_SIZE))
							.addPreferredGap(ComponentPlacement.RELATED, 23, Short.MAX_VALUE)
							.addGroup(gl_setupPanel.createParallelGroup(Alignment.LEADING)
								.addComponent(loadMassChainButtonForSetup, GroupLayout.PREFERRED_SIZE, 130, GroupLayout.PREFERRED_SIZE)
								.addComponent(loadNuclideListButton, GroupLayout.PREFERRED_SIZE, 130, GroupLayout.PREFERRED_SIZE))
							.addGap(34)
							.addComponent(setButton, GroupLayout.PREFERRED_SIZE, 70, GroupLayout.PREFERRED_SIZE))
						.addGroup(gl_setupPanel.createSequentialGroup()
							.addContainerGap()
							.addComponent(pathButton)
							.addPreferredGap(ComponentPlacement.UNRELATED)
							.addComponent(pathField, GroupLayout.PREFERRED_SIZE, 346, GroupLayout.PREFERRED_SIZE))
						.addGroup(gl_setupPanel.createSequentialGroup()
							.addGap(12)
							.addComponent(fileExtLabel)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addComponent(oldExtRadioButton, GroupLayout.PREFERRED_SIZE, 47, GroupLayout.PREFERRED_SIZE)
							.addGap(2)
							.addComponent(newExtRadioButton, GroupLayout.PREFERRED_SIZE, 50, GroupLayout.PREFERRED_SIZE)
							.addGap(2)
							.addComponent(ensExtRadioButton, GroupLayout.PREFERRED_SIZE, 50, GroupLayout.PREFERRED_SIZE)
							.addGap(2)
							.addComponent(xundlExtRadioButton, GroupLayout.PREFERRED_SIZE, 57, GroupLayout.PREFERRED_SIZE)
							.addGap(3)
							.addComponent(otherExtRadioButton, GroupLayout.PREFERRED_SIZE, 20, GroupLayout.PREFERRED_SIZE)
							.addGap(2)
							.addComponent(otherExtTextField, GroupLayout.PREFERRED_SIZE, 60, GroupLayout.PREFERRED_SIZE)))
					.addContainerGap())
		);
		gl_setupPanel.setVerticalGroup(
			gl_setupPanel.createParallelGroup(Alignment.LEADING)
				.addGroup(gl_setupPanel.createSequentialGroup()
					.addContainerGap()
					.addGroup(gl_setupPanel.createParallelGroup(Alignment.LEADING)
						.addGroup(gl_setupPanel.createSequentialGroup()
							.addGroup(gl_setupPanel.createParallelGroup(Alignment.BASELINE)
								.addComponent(setupByMassChainRdBtn)
								.addComponent(loadMassChainButtonForSetup))
							.addGap(10)
							.addGroup(gl_setupPanel.createParallelGroup(Alignment.BASELINE)
								.addComponent(setupByNuclideListRdBtn)
								.addComponent(loadNuclideListButton)))
						.addGroup(gl_setupPanel.createSequentialGroup()
							.addGap(18)
							.addComponent(setButton)))
					.addPreferredGap(ComponentPlacement.RELATED, 15, Short.MAX_VALUE)
					.addGroup(gl_setupPanel.createParallelGroup(Alignment.TRAILING, false)
						.addGroup(gl_setupPanel.createSequentialGroup()
							.addComponent(otherExtTextField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
							.addPreferredGap(ComponentPlacement.RELATED))
						.addGroup(gl_setupPanel.createSequentialGroup()
							.addComponent(fileExtLabel)
							.addGap(12))
						.addGroup(gl_setupPanel.createSequentialGroup()
							.addComponent(oldExtRadioButton)
							.addGap(11))
						.addGroup(gl_setupPanel.createSequentialGroup()
							.addComponent(newExtRadioButton)
							.addGap(11))
						.addGroup(gl_setupPanel.createSequentialGroup()
							.addComponent(ensExtRadioButton)
							.addGap(11))
						.addGroup(gl_setupPanel.createSequentialGroup()
							.addComponent(xundlExtRadioButton)
							.addGap(11))
						.addGroup(gl_setupPanel.createSequentialGroup()
							.addComponent(otherExtRadioButton)
							.addGap(11)))
					.addGroup(gl_setupPanel.createParallelGroup(Alignment.BASELINE)
						.addComponent(pathButton)
						.addComponent(pathField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
					.addContainerGap())
		);
		setupPanel.setLayout(gl_setupPanel);
		GroupLayout groupLayout = new GroupLayout(getContentPane());
		groupLayout.setHorizontalGroup(
			groupLayout.createParallelGroup(Alignment.LEADING)
				.addGroup(groupLayout.createSequentialGroup()
					.addContainerGap()
					.addGroup(groupLayout.createParallelGroup(Alignment.LEADING)
						.addComponent(setupPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
						.addComponent(mergePanel, GroupLayout.PREFERRED_SIZE, 439, GroupLayout.PREFERRED_SIZE))
					.addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
		);
		groupLayout.setVerticalGroup(
			groupLayout.createParallelGroup(Alignment.LEADING)
				.addGroup(groupLayout.createSequentialGroup()
					.addGap(10)
					.addComponent(setupPanel, GroupLayout.PREFERRED_SIZE, 174, GroupLayout.PREFERRED_SIZE)
					.addGap(14)
					.addComponent(mergePanel, GroupLayout.PREFERRED_SIZE, 102, GroupLayout.PREFERRED_SIZE)
					.addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
		);
		buttonGroup2=new javax.swing.ButtonGroup();
	
		mergeInputDatasetsRdBtn = new JRadioButton("merge input datasets");
		mergeInputDatasetsRdBtn.setToolTipText("merge input files of datasets");
		buttonGroup2.add(mergeInputDatasetsRdBtn);

		mergeInputDatasetsRdBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mergeInputRadioButtonActionPerformed(e);
			}
		});

			
		mergeAllRdBtn = new JRadioButton("merge all in folders");
		mergeAllRdBtn.setToolTipText("merge all files of datasets in evaluation folders");
		buttonGroup2.add(mergeAllRdBtn);
		
		mergeAllRdBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mergeAllRadioButtonActionPerformed(e);
			}
		});

		
		loadDatasetsButtonForMerging = new JButton("Load ENSDFs");
		loadDatasetsButtonForMerging.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mergeInputDatasetsRdBtn.setSelected(true);
				loadENSDFButtonActionPerformed(e);
				mergeButton.setEnabled(true);
			}
		});
		loadDatasetsButtonForMerging.setToolTipText("load the ENSDF files of datasets to be merged");
		mergeButton = new JButton("Merge");
		mergeButton.setEnabled(false);
		mergeButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mergeButtonActionPerformed(e);
			}
		});
		
		selectFoldersButton = new JButton("Select Folders");
		selectFoldersButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mergeAllRdBtn.setSelected(true);
				selectFoldersButtonActionPerformed(e);
				mergeButton.setEnabled(true);
			}
		});
		selectFoldersButton.setToolTipText("<HTML>By default, the program will search for all ENSDF files with \".ens\" extensions<br>"
				                               + "in the \"new\" sub-folders under the folders for individual nuclides, like <br>"
				                               + "&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp\"A31\\Si31\\new\" for selected top folder=A31 <br>"
				                               + "If such folder structure doesn't exist, the program will just merge all \".ens\" files<br>"
				                               + "in the selected folder.</HTML>");


		GroupLayout gl_mergePanel = new GroupLayout(mergePanel);
		gl_mergePanel.setHorizontalGroup(
			gl_mergePanel.createParallelGroup(Alignment.LEADING)
				.addGroup(gl_mergePanel.createSequentialGroup()
					.addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
					.addGroup(gl_mergePanel.createParallelGroup(Alignment.LEADING)
						.addGroup(gl_mergePanel.createSequentialGroup()
							.addComponent(mergeAllRdBtn, GroupLayout.PREFERRED_SIZE, 160, GroupLayout.PREFERRED_SIZE)
							.addGap(21)
							.addComponent(selectFoldersButton, GroupLayout.PREFERRED_SIZE, 130, GroupLayout.PREFERRED_SIZE))
						.addGroup(gl_mergePanel.createSequentialGroup()
							.addComponent(mergeInputDatasetsRdBtn, GroupLayout.PREFERRED_SIZE, 160, GroupLayout.PREFERRED_SIZE)
							.addGap(21)
							.addComponent(loadDatasetsButtonForMerging, GroupLayout.PREFERRED_SIZE, 130, GroupLayout.PREFERRED_SIZE)))
					.addGap(34)
					.addComponent(mergeButton, GroupLayout.PREFERRED_SIZE, 70, GroupLayout.PREFERRED_SIZE)
					.addContainerGap())
		);
		gl_mergePanel.setVerticalGroup(
			gl_mergePanel.createParallelGroup(Alignment.LEADING)
				.addGroup(gl_mergePanel.createSequentialGroup()
					.addGroup(gl_mergePanel.createParallelGroup(Alignment.LEADING)
						.addGroup(gl_mergePanel.createSequentialGroup()
							.addGap(3)
							.addGroup(gl_mergePanel.createParallelGroup(Alignment.BASELINE)
								.addComponent(mergeInputDatasetsRdBtn)
								.addComponent(loadDatasetsButtonForMerging))
							.addGap(10)
							.addGroup(gl_mergePanel.createParallelGroup(Alignment.BASELINE)
								.addComponent(mergeAllRdBtn)
								.addComponent(selectFoldersButton)))
						.addGroup(gl_mergePanel.createSequentialGroup()
							.addGap(27)
							.addComponent(mergeButton)))
					.addContainerGap(14, Short.MAX_VALUE))
		);
		mergePanel.setLayout(gl_mergePanel);
		getContentPane().setLayout(groupLayout);
		
		pack();
	}
	

	protected void loadENSDFButtonActionPerformed(ActionEvent arg0) {
        try{
            //JFileChooser fc=new JFileChooser();
            JFileChooser fc;
            try {
                fc = new JFileChooser(".");
             }catch (Exception e) {
                fc = new JFileChooser(".", new RestrictedFileSystemView());
             }
            
            if(Setup.outdir!=null)
                fc.setCurrentDirectory(new File(Setup.outdir));
            
            fc.setMultiSelectionEnabled(true);
            
            int ret=fc.showOpenDialog(this);
            if(ret==JFileChooser.APPROVE_OPTION){
            	
                
                chain=new MassChain();
                
                File[] files=fc.getSelectedFiles();
                //dataFile=fc.getSelectedFile();
                
                          
                if(files.length==1){                 	
                	File dataFile=files[0];
      
                    run.printMessage("Loading file: "+dataFile.getAbsolutePath());
                    chain.load(dataFile,"LITTLE");
                }else if(files.length>1){
                	Vector<String> lines=new Vector<String>();
                   	 
                   	run.printMessage("Loading files:");
                   	for(int i=0;i<files.length;i++){
                   		run.printMessage("    "+files[i].getAbsolutePath());
                   		lines.addAll(Str.readFile(files[i]));
                   		if(!lines.lastElement().trim().isEmpty())
                   			lines.addElement("    \n");
                   		 
                   		 //System.out.println("In MasterFrame line 362: lastline new line="+lines.lastElement().trim().indexOf("\n")+"  *"+lines.lastElement()+"*");
                   	 }
                   	 chain.load(lines,"LITTLE");
                }
           
                                                
                run.printMessage("Done loading");
                 
                filesV.clear();
                for(int i=0;i<files.length;i++)
                	filesV.add(files[i]);
                
                Setup.outdir=fc.getCurrentDirectory().toString();
                Setup.save();
            }       
        }catch(Exception e){
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,e);         
        }
		
	}
	
	protected void loadNuclideButtonMouseClicked(java.awt.event.MouseEvent evt) {
		
		if(evt.getModifiers()==InputEvent.BUTTON1_MASK){//left-button, BUTTON2-middle, BUTTON3-right
	        try{
	            //JFileChooser fc=new JFileChooser();
	            JFileChooser fc;
	            try {
	                fc = new JFileChooser(".");
	             }catch (Exception e) {
	                fc = new JFileChooser(".", new RestrictedFileSystemView());
	             }
	            
	            if(Setup.outdir!=null)
	                fc.setCurrentDirectory(new File(Setup.outdir));
	            
	            int ret=fc.showOpenDialog(this);
	            if(ret==JFileChooser.APPROVE_OPTION){               
	                File file=fc.getSelectedFile();
	                
	                
	                run.printMessage("Loading nuclide list: "+file.getAbsolutePath());
	                
	                Vector<String> linesV=Str.readFile(file);
	                
	                boolean isWrongInput=false;
	                if(linesV.size()>30)
	                	isWrongInput=true;
	                else {
	                	for(String line:linesV) {
		    				String[] names=line.split(",|;|\\s");
		    				for(int i=0;i<names.length;i++){
		    					String name=names[i].trim();
		    					if(name.isEmpty())
		    						continue;
		    					
		    					try {
			    					Nucleus nuc=new Nucleus(name);
			    					if(nuc.A()<=0 && nuc.Z()<=0) {
			    						isWrongInput=true;
			    						break;
			    					}
		    					}catch(Exception e) {
		    						isWrongInput=true;
		    						break;
		    					}
	    						
		    					nucNames.add(name);
		    				}	
		    				
		    				if(isWrongInput)
		    					break;
	                	}
	                }
	                
	                if(isWrongInput) {
	                	String msg="***Error: wrong nuclide list ***";
	                	run.printMessage(msg);
	                	JOptionPane.showMessageDialog(this,msg);
	                }
	                run.printMessage("Done loading");
	                
	                Setup.outdir=fc.getCurrentDirectory().toString();
	                Setup.save();

	            }       
	        }catch(Exception e){
	            e.printStackTrace();
	            JOptionPane.showMessageDialog(this,e);         
	        }
		}else if(evt.getModifiers()==InputEvent.BUTTON3_MASK){
			 popupMenu=new JPopupMenu();
			 //popupMenu.setBorder(new BevelBorder(BevelBorder.RAISED));
			 //popupMenu.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
			 popupMenu.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.RAISED, SystemColor.windowText,UIManager.getColor("ArrowButton.background")));
	         popupMenu.setForeground(SystemColor.windowText);
	         popupMenu.setBackground(UIManager.getColor("ArrowButton.background"));
			 
			 JMenuItem typingList=new JMenuItem("type in nuclide names");
			 typingList.setToolTipText("Type in the nuclides names as folder names separated by space or comma");
			 typingList.addActionListener(new ActionListener(){
				 public void actionPerformed(ActionEvent evt){
					 openNuclideListFrame(evt,run);
				 }
			 });
			 popupMenu.add(typingList); 
	         popupMenu.show(evt.getComponent(),evt.getX(), evt.getY());
		}
		

		
	}
	
	protected void openNuclideListFrame(ActionEvent evt,Run run) {
		 try {
			 run.printMessage("Typing in nuclide list for setting up individual folders...");
			 NuclideListFrame listFrame=new NuclideListFrame(run);
		     listFrame.setLocation(this.getX()+this.getWidth(),this.getY());
		     listFrame.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);          
		     listFrame.setVisible(true);
		        
             nucNames=listFrame.getNucNames();
             
             
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			run.printMessage("Error when typing nuclide list.\n");
		}
		
	}

	private String getOutfileExt(){
		if(oldExtRadioButton.isSelected())
			fileExt="old";
		else if(newExtRadioButton.isSelected())
			fileExt="new";
		else if(ensExtRadioButton.isSelected())
			fileExt="ens";
		else if(xundlExtRadioButton.isSelected())
			fileExt="xundl";
		else if(otherExtRadioButton.isSelected()){
			try{
				fileExt=otherExtTextField.getText();
			}catch(Exception e){
				e.printStackTrace();
				fileExt="";
			}
		}
		
		if(fileExt.trim().isEmpty()){
			fileExt="old";
			run.printMessage("No extension is specified for output files. Default=old will be used.\n");
		}
		return fileExt;
	}
	
	
	protected void setButtonActionPerformed(ActionEvent arg0) {

		String msg="";
		if(this.setupByMassChainRdBtn.isSelected()){
			 try{
				 if(chain==null){						     
					 JOptionPane.showMessageDialog(this,"You haven't loaded a mass-chain ENSDF file.");						         
					 return;						        
				 }else if(chain.nENSDF()<5 || chain.nChains()!=1 || chain.nNucleus()<5){//not a mass-chain file
					 //msg+="There are not enough ENSDF datasets in the input file.\n";
					 //msg+="The input file is not a mass-chain file.\n";
					 //msg+="You must load a mass-chain file for setting-up evaluation folders.";
					 //JOptionPane.showMessageDialog(this,msg);
					 msg="";
					 //return;
				 }
				 
				 run.printMessage("\nSetting up and creating folders for a new mass-chain evaluation and");
				 run.printMessage(  "spliting mass-chain file into individual datasets and");
				 run.printMessage(  "saving datasets into created folders under path="+basePath+"\n");
				 
				 
				 getOutfileExt();
				 
				 msg=EnsdfUtil.setupEvaluation(chain, basePath,fileExt);
				 
				 if(msg.isEmpty())
					 run.printMessage("Done setting up evaluation folders from a mass-chain ENSDF file.\n\n");
				 else{
					 run.printMessage(msg+"\n\n");
					 //run.printMessage("Please check the output path setting.");
				 }
					 
			 }catch (IOException e) {
				 run.printMessage("Error when setting up evaluation folders from a mass chain.\n\n");
				 e.printStackTrace();
				 return;
			 }	
		}else if(this.setupByNuclideListRdBtn.isSelected()){//set evaluation folder from the input list of nuclides
			try{
				 run.printMessage("\nSetting up and creating folders for a new mass-chain evaluation...");	
				 
				 msg=EnsdfUtil.setupEvaluation(nucNames, basePath);
				 
				 if(msg.isEmpty())
					 run.printMessage("Done setting up evaluation folders from a nuclide list.\n\n");
				 else{
					 run.printMessage(msg+"\n\n");
					 //run.printMessage("Please check the output path setting.");
				 }
				 
			}catch (IOException e) {
				 run.printMessage("Error when setting up evaluation folders from a list of nuclides.\n\n");
				 e.printStackTrace();
				 return;
			 }	
			
		}else{
			run.printMessage("Something wrong when setting up evaluation folders.\n\n");
			return;
		}
				
	}
	
	
	protected void selectFoldersButtonActionPerformed(ActionEvent evt) {
	      try{
	            
	            //JFileChooser fc=new JFileChooser();
	            JFileChooser fc;
	            try {
	                fc = new JFileChooser(".");            
	             }catch (Exception e) {
	                fc = new JFileChooser(".", new RestrictedFileSystemView());
	             }

	            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
	            fc.setApproveButtonText("Select");
	            fc.setApproveButtonToolTipText("Select this directory");
	            
	            if(Setup.outdir!=null)
	                fc.setCurrentDirectory(new File(Setup.outdir));
	            
	            int ret=fc.showOpenDialog(this);
	            if(ret==JFileChooser.APPROVE_OPTION){
	                //nds.Setup.outdir=fc.getSelectedFile().toString();
	            	
	            	File file = fc.getSelectedFile();
	            	topDir=file.getAbsolutePath();
	            	
	            	Setup.outdir=topDir;
	                Setup.save();
	                
	            	run.printMessage("Selected evaluation foder for merging datasets:\n   "+topDir);
	            }
	            
	           
	        }catch(Exception e){
	            //e.printStackTrace();
	           	String message="Error when selecting evaluation folder path!";
	           	JOptionPane.showMessageDialog(this,message);
	           	run.printMessage(message);           	                         
	        }
		
	}
	
	protected void mergeButtonActionPerformed(ActionEvent evt) {
		String dirSeparator=EnsdfUtil.dirSeparator();
		String filename=Setup.outdir+dirSeparator+"merged.ens";
    	PrintWriter out=null;
    	try{
        	Vector<String> lines=new Vector<String>();        	
    		if(this.mergeInputDatasetsRdBtn.isSelected()){
    			lines.addAll(EnsdfUtil.mergeDatasets(filesV));
    		}else if(this.mergeAllRdBtn.isSelected()){
    			lines.addAll(mergeDatasetsInFolder(topDir));
    		}else{
    			run.printMessage("Something wrong when merging datasets.\n");
    			return;
    		}
        	
    		if(lines.size()>0){
    			out=new PrintWriter(new File(filename));
    			Str.write(out, lines);
    			out.close();
    			run.printMessage("Done merging datasets into a single file: \n");
    			run.printMessage("  "+filename+"\n");
    		}else{
    			run.printMessage("Nothing has been merged. Check if datasets exist.\n");
    		}
    	}catch(Exception e){
    		e.printStackTrace();
    	}
	
	}
	
	/*
	 * By default, it is assumed that each dataset is saved in one file which is put in a folder for each nuclide of a mass chain and the 
	 * datasets to be merged should be put in the "new" sub-folder under each nuclide folder. The cover page is put the under the top 
	 * folder with the name ending with "_cover.ens". If there is no "new" sub-folder, it will merge all files with the ".ens" extension 
	 * under the top folder only.
	 */
	private Vector<String> mergeDatasetsInFolder(String topDir){
		String dirSeparator=EnsdfUtil.dirSeparator();
		
    	Vector<String> tempLines=new Vector<String>();
    	Vector<String> lines=new Vector<String>();   
    	
    	Vector<File> extraFilesV=new Vector<File>();
    	
    	if(!Str.isFileExist(topDir))
    		return lines;
    	
    	boolean hasNewDatasets=false;
    	
    	try{
    		File[] subDirs=new File(topDir).listFiles();
    		
    		sortDirs(subDirs);
   		
    		for(int i=0;i<subDirs.length;i++){
    			File f=subDirs[i];
    			if(f.isFile()){
    				String name=f.getName();
    				if(name.endsWith("_cover.ens")){
    					tempLines=Str.readFile(f);
    					tempLines=Str.trimV(tempLines);
    					
    					if(tempLines.size()>0){
    						tempLines.add(Str.repeat(" ", 80));
    						lines.addAll(0, tempLines);
    					}
    				}else if(name.endsWith(".ens")) {
    					extraFilesV.add(f);		
    				}
    			}else if(f.isDirectory()){
    				String path=f.getAbsolutePath()+dirSeparator+"new";
    				if(!Str.isFileExist(path))
    					continue;
    				
    				File[] files=new File(path).listFiles(new FileFilter(){
    				    @Override
    				    public boolean accept(File file) {
    				    	String fn=file.getName();
    				    	if(file.isFile()&&fn.endsWith(".ens"))
    				    		return true;
    				    	else
    				    		return false;
    				    }
    				});
    				
    				if(files.length>0) {
    					hasNewDatasets=true;
    					
        				Vector<File> tempFilesV=new Vector<File>();
        				for(int j=0;j<files.length;j++)
        					tempFilesV.add(files[j]);
        				
        				lines.addAll(EnsdfUtil.mergeDatasets(tempFilesV));
        				//lines.add(Str.repeat(" ", 80)); 
    				}
  											
    			}
    		}
    		
    		if(!hasNewDatasets) {
    			lines.addAll(EnsdfUtil.mergeDatasets(extraFilesV));
    		}
    	}catch(Exception e){
    		e.printStackTrace();
    	}
	
		return lines;
	}
	
	private void sortDirs(File[] subDirs){
		int n=subDirs.length;
		Nucleus[] nuclei=new Nucleus[n];

		for(int i=0;i<n;i++){
			File f=subDirs[i];
			nuclei[i]=new Nucleus();
			nuclei[i].setZA(-1, -1);
			if(f.isDirectory()){
    			String name=f.getName();
    			String eName="";
    			int len=name.length();
    			int A=-1;
    			for(int j=0;j<len;j++){
    				if(j<len-1){
    					boolean isDigit1=Character.isDigit(name.charAt(j));
    					boolean isDigit2=Character.isDigit(name.charAt(j+1));
    					if(isDigit1!=isDigit2){
    						if(isDigit1){
    							A=Integer.parseInt(name.substring(0, j+1));
    							eName=name.substring(j+1);
    						}else{
    							try{
    								A=Integer.parseInt(name.substring(j+1));
    							}catch(Exception e){}
    							
    							eName=name.substring(0,j+1);
    						}
    						
    						break;
    					}
    				}
    			}
    			
    			nuclei[i].setZA(-1,A);
    			nuclei[i].setEN(eName);

			}
		}
		

		for(int i=0;i<n-1;i++){;
			for(int j=i+1;j<n;j++){
				if(EnsdfUtil.compareNucleus(nuclei[i], nuclei[j], "MASS")>0){
					File tempFile=subDirs[i];
					Nucleus tempNuc=nuclei[i];
					subDirs[i]=subDirs[j];
					nuclei[i]=nuclei[j];
					subDirs[j]=tempFile;
					nuclei[j]=tempNuc;
				}
			}
		}
	}
	
	private void setupByDatasetsRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {
    }
    
    private void setupByNuclideRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {

    }

    protected void mergeAllRadioButtonActionPerformed(ActionEvent e) {
	}

	protected void mergeInputRadioButtonActionPerformed(ActionEvent e) {
	}
	
    private void pathFieldPropertyChange(java.beans.PropertyChangeEvent evt) {
    }

    private void pathFieldKeyReleased(java.awt.event.KeyEvent evt) {
        basePath=pathField.getText();
    }
    
    private void pathButtonActionPerformed(java.awt.event.ActionEvent evt) {

        try{
            
            //JFileChooser fc=new JFileChooser();
            JFileChooser fc;
            try {
                fc = new JFileChooser(".");            
             }catch (Exception e) {
                fc = new JFileChooser(".", new RestrictedFileSystemView());
             }

            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setApproveButtonText("Select");
            fc.setApproveButtonToolTipText("Select this directory");
            
            
            if(Setup.outdir!=null)
                fc.setCurrentDirectory(new File(Setup.outdir));
            
            int ret=fc.showOpenDialog(this);
            if(ret==JFileChooser.APPROVE_OPTION){
                //nds.Setup.outdir=fc.getSelectedFile().toString();
            	
            	File file = fc.getSelectedFile();
            	basePath=file.getAbsolutePath();
                    
                pathField.setText(basePath);
                
                Setup.outdir=basePath;
                System.out.println(basePath);
                
                Setup.save();
            }
            
           
        }catch(Exception e){
            //e.printStackTrace();
           	String message="Error when selecting output path!";
           	JOptionPane.showMessageDialog(this,message);
           	run.printMessage(message);           	                         
        }

   }
}
