package consistency.ui;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import java.awt.Font;
import java.awt.Frame;
import java.awt.SystemColor;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Vector;

import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Style;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import javax.swing.text.Utilities;

import ame.ui.RestrictedFileSystemView;
import consistency.base.CheckControl;
import consistency.main.Run;
import ensdfparser.calc.GammaBranch;
import ensdfparser.calc.GammaBranchingCalculator;
import ensdfparser.calc.XDX;
import ensdfparser.ensdf.Comment;
import ensdfparser.ensdf.ContRecord;
import ensdfparser.ensdf.ENSDF;
import ensdfparser.ensdf.FindValue;
import ensdfparser.ensdf.Gamma;
import ensdfparser.ensdf.Level;
import ensdfparser.ensdf.SDS2XDX;
import ensdfparser.ensdf.XDX2SDS;
import ensdfparser.nds.ensdf.EnsdfUtil;
import ensdfparser.nds.ensdf.MassChain;
import ensdfparser.nds.latex.Translator;
import ensdfparser.nds.util.Str;
import ensdfparser.ui.CustomTextPane;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Color;
import javax.swing.JCheckBox;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class WidthToT12CalculatorFrame extends JFrame{

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    Style messengerStyle=null;

    private ButtonGroup limitButtonGroup,T12MRButtonGroup;

    private JRadioButton limit35RadioButton;

    private JRadioButton limit99RadioButton;

    private JRadioButton otherLimitRadioButton;

    private JTextField uncLimitTextField;
    static private final String newline = "\n";  
    

    private int defaultUncertaintyLimit=CheckControl.errorLimit;
    
    private boolean useAdoptedBranching=false;
    private ENSDF ens=null,adopted=null;
    
    public WidthToT12CalculatorFrame() {

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                CheckControl.errorLimit=defaultUncertaintyLimit;
            }
        });
        
        
        initComponents();
        
        
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 11);
        messenger.setFont(font);
        messengerStyle=StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);

        //inputTextPane.setFont(new Font("Courier New", Font.PLAIN, 12));

        inputTextPane.setTransferHandler(new TransferHandler() {
            /**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			public boolean canImport(TransferHandler.TransferSupport support) {
                if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    return false;
                } 
                return true;
            }
     
            @SuppressWarnings("unchecked")
    		public boolean importData(TransferHandler.TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                 
                Transferable t = support.getTransferable();
     
                try {
                    java.util.List<File> fileList=(java.util.List<File>)t.getTransferData(DataFlavor.javaFileListFlavor);
     
                    consistency.main.Setup.filedir=fileList.get(0).getAbsolutePath();
                    consistency.main.Setup.save();
                     
                    File dataFile=(File)fileList.get(0);
                    //File[] files=new File[fileList.size()];
                    //fileList.toArray(files);
                    
                    MassChain data=new MassChain();
                    data.load(dataFile);
                    
                    ens=data.getENSDF(0);
                    
                    String s="";
                    for(String line:ens.lines())
                        s+=line+"\n";
                            
                    clear(inputTextPane);
                    inputTextPane.setText(s);
                    
                } catch (UnsupportedFlavorException e) {
                	e.printStackTrace();
                    JOptionPane.showMessageDialog(inputTextPane,"Error","*Error*: unsupported file!", JOptionPane.ERROR_MESSAGE); 
                    return false;
                } catch (Exception e) {
                	e.printStackTrace();
                    JOptionPane.showMessageDialog(inputTextPane,"Error","*Error*: loading failed. Please check the file!", JOptionPane.ERROR_MESSAGE); 
                    return false;
                }
     
                return true;
            }
        });
        GroupLayout gl_inputPanel = new GroupLayout(inputPanel);
        gl_inputPanel.setHorizontalGroup(
        	gl_inputPanel.createParallelGroup(Alignment.LEADING)
        		.addGroup(gl_inputPanel.createSequentialGroup()
        			.addGap(1)
        			.addComponent(inputScrollPane, GroupLayout.DEFAULT_SIZE, 766, Short.MAX_VALUE)
        			.addGap(0))
        );
        gl_inputPanel.setVerticalGroup(
        	gl_inputPanel.createParallelGroup(Alignment.LEADING)
        		.addGroup(gl_inputPanel.createSequentialGroup()
        			.addComponent(inputScrollPane, GroupLayout.PREFERRED_SIZE, 304, GroupLayout.PREFERRED_SIZE)
        			.addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        inputPanel.setLayout(gl_inputPanel);
        
        messenger.setTransferHandler(new TransferHandler() {
            /**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			public boolean canImport(TransferHandler.TransferSupport support) {
                if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    return false;
                } 
                return true;
            }
     
            @SuppressWarnings("unchecked")
    		public boolean importData(TransferHandler.TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                 
                Transferable t = support.getTransferable();
            	String errMsg="*Error*: loading Adopted dataset failed. Please check the file!\n";
                try {
                    java.util.List<File> fileList=(java.util.List<File>)t.getTransferData(DataFlavor.javaFileListFlavor);
     
                    consistency.main.Setup.filedir=fileList.get(0).getAbsolutePath();
                    consistency.main.Setup.save();
                     
                    File dataFile=(File)fileList.get(0);
                    //File[] files=new File[fileList.size()];
                    //fileList.toArray(files);
                    
                    MassChain data=new MassChain();
                    data.load(dataFile);
                    
                    ENSDF temp=data.getENSDF(0);
                    
                    if(temp!=null) {
                    	String NUCID0=temp.nucleus().nameENSDF();
                    	String DSID0=temp.DSId0();
                    	if(!DSID0.contains("ADOPTED")) {
                    		JOptionPane.showMessageDialog(messenger,"Error","*Error*: the loaded dataset is "
                    				+ "NOT an Adopted dataset.\n Please load an Adopted dataset of the same nuclide", JOptionPane.ERROR_MESSAGE);  
         
                    	}else if(ens!=null && !NUCID0.equals(ens.nucleus().nameENSDF())) {
                    		JOptionPane.showMessageDialog(messenger,"*Error*: the loaded Adopted dataset is NOT for the same nuclide as the input ENSDF.\n Please load an Adopted dataset of the same nuclide");  
                    		return false;
                    	}
                    	
                    	adopted=temp;
                    	loadAdoptedCheckBox.setSelected(true);
                    	printMessage("**Adopted dataset <"+NUCID0.trim()+":"+adopted.DSId0()+"> has been loaded for calculating branching ratios");
                    	printMessage("    "+dataFile.getAbsolutePath()+"\n\n");
                    }else {
                    	
                    	JOptionPane.showMessageDialog(messenger,"Error",errMsg, JOptionPane.ERROR_MESSAGE);  
                    	return false;
                    }
                    
                } catch (UnsupportedFlavorException e) {
                	e.printStackTrace();
                    JOptionPane.showMessageDialog(messenger,"Error","*Error*: unsupported file!", JOptionPane.ERROR_MESSAGE); 
                    return false;
                } catch (Exception e) {
                	e.printStackTrace();
                    JOptionPane.showMessageDialog(messenger,"Error","*Error*: loading failed. Please check the file!", JOptionPane.ERROR_MESSAGE); 
                    return false;
                }
     
                return true;
            }
        });
    }

    private void initComponents(){
        inputPanel = new JPanel();
        
        controlPanel = new JPanel();
        
        resultPanel = new JPanel();
        
        loadButton = new JButton("Load an ENSDF");
        loadButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                loadButtonActionPerformed(e);
            }
        });
        
        topLabel = new JLabel("<HTML>or copy and paste part of an ENSDF dataset into the text area below. "
        		+ "Width G and gG0<sup>2</sup>/G in<br>comments will be extracted and used for calculations. "
        		+ "<span style=\"color:red;\">Click here for examples.</span></HTML>");
        topLabel.addMouseListener(new MouseAdapter() {
        	@Override
        	public void mouseClicked(MouseEvent e) {
        		showInstructionFrame(e);
        	}
        });
        topLabel.setForeground(new Color(0, 0, 0));
        
        clearInputButton = new JButton("clear");
        clearInputButton.setToolTipText("clear input ENSDF");
        clearInputButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    clear(inputTextPane);
                } catch (IOException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
            }
        });
        
        colNoLabel = new JLabel("Col");
        colNoLabel.setForeground(Color.BLUE);
        colNoLabel.setFont(new Font("SansSerif", Font.ITALIC, 10));
        updateStatus(1,1);
        
        loadAdoptedButton = new JButton("Load Aopted dataset");
        loadAdoptedButton.addActionListener(new ActionListener() {
        	public void actionPerformed(ActionEvent e) {
        		loadAdoptedButtonActionPerformed(e);
        	}
        });
        loadAdoptedButton.setToolTipText("clear input ENSDF");
        
        loadAdoptedCheckBox = new JCheckBox("use Adopted branchings by loading Adopted dataset");
        loadAdoptedCheckBox.setSelected(useAdoptedBranching);
        loadAdoptedCheckBox.addItemListener(new ItemListener() {
        	public void itemStateChanged(ItemEvent arg0) {
            	if(((JCheckBox)arg0.getSource()).isSelected())
            		useAdoptedBranching=true;
            	else
            		useAdoptedBranching=false;
        	}
        });
        
        GroupLayout groupLayout = new GroupLayout(getContentPane());
        groupLayout.setHorizontalGroup(
        	groupLayout.createParallelGroup(Alignment.LEADING)
        		.addGroup(groupLayout.createSequentialGroup()
        			.addGroup(groupLayout.createParallelGroup(Alignment.TRAILING, false)
        				.addGroup(groupLayout.createSequentialGroup()
        					.addContainerGap()
        					.addComponent(controlPanel, GroupLayout.PREFERRED_SIZE, 735, GroupLayout.PREFERRED_SIZE))
        				.addGroup(groupLayout.createSequentialGroup()
        					.addGroup(groupLayout.createParallelGroup(Alignment.TRAILING)
        						.addGroup(Alignment.LEADING, groupLayout.createSequentialGroup()
        							.addContainerGap()
        							.addComponent(loadButton, GroupLayout.PREFERRED_SIZE, 143, GroupLayout.PREFERRED_SIZE)
        							.addPreferredGap(ComponentPlacement.UNRELATED)
        							.addComponent(topLabel, GroupLayout.DEFAULT_SIZE, 574, Short.MAX_VALUE))
        						.addGroup(groupLayout.createSequentialGroup()
        							.addGap(14)
        							.addComponent(loadAdoptedCheckBox, GroupLayout.PREFERRED_SIZE, 335, GroupLayout.PREFERRED_SIZE)
        							.addPreferredGap(ComponentPlacement.RELATED)
        							.addComponent(loadAdoptedButton, GroupLayout.PREFERRED_SIZE, 162, GroupLayout.PREFERRED_SIZE)
        							.addGap(68)
        							.addComponent(colNoLabel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        							.addPreferredGap(ComponentPlacement.RELATED)
        							.addComponent(clearInputButton, GroupLayout.PREFERRED_SIZE, 56, GroupLayout.PREFERRED_SIZE)))
        					.addGap(6)))
        			.addContainerGap(16, Short.MAX_VALUE))
        		.addComponent(inputPanel, GroupLayout.DEFAULT_SIZE, 757, Short.MAX_VALUE)
        		.addComponent(resultPanel, GroupLayout.PREFERRED_SIZE, 757, Short.MAX_VALUE)
        );
        groupLayout.setVerticalGroup(
        	groupLayout.createParallelGroup(Alignment.TRAILING)
        		.addGroup(groupLayout.createSequentialGroup()
        			.addGroup(groupLayout.createParallelGroup(Alignment.LEADING)
        				.addGroup(groupLayout.createSequentialGroup()
        					.addContainerGap()
        					.addComponent(loadButton, GroupLayout.DEFAULT_SIZE, 45, Short.MAX_VALUE))
        				.addGroup(groupLayout.createSequentialGroup()
        					.addGap(5)
        					.addComponent(topLabel, GroupLayout.DEFAULT_SIZE, 46, Short.MAX_VALUE)))
        			.addGroup(groupLayout.createParallelGroup(Alignment.LEADING)
        				.addGroup(Alignment.TRAILING, groupLayout.createParallelGroup(Alignment.LEADING)
        					.addGroup(groupLayout.createSequentialGroup()
        						.addPreferredGap(ComponentPlacement.RELATED)
        						.addGroup(groupLayout.createParallelGroup(Alignment.BASELINE)
        							.addComponent(loadAdoptedCheckBox)
        							.addComponent(loadAdoptedButton, GroupLayout.PREFERRED_SIZE, 27, GroupLayout.PREFERRED_SIZE)))
        					.addGroup(groupLayout.createSequentialGroup()
        						.addGap(20)
        						.addComponent(colNoLabel, GroupLayout.PREFERRED_SIZE, 13, GroupLayout.PREFERRED_SIZE)))
        				.addComponent(clearInputButton, Alignment.TRAILING, GroupLayout.PREFERRED_SIZE, 27, GroupLayout.PREFERRED_SIZE))
        			.addPreferredGap(ComponentPlacement.RELATED)
        			.addComponent(inputPanel, GroupLayout.PREFERRED_SIZE, 303, GroupLayout.PREFERRED_SIZE)
        			.addPreferredGap(ComponentPlacement.RELATED)
        			.addComponent(controlPanel, GroupLayout.PREFERRED_SIZE, 35, GroupLayout.PREFERRED_SIZE)
        			.addGap(2)
        			.addComponent(resultPanel, GroupLayout.PREFERRED_SIZE, 399, GroupLayout.PREFERRED_SIZE))
        );
        
        inputScrollPane = new JScrollPane();
        
        inputTextPane = new CustomTextPane();
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        inputTextPane.setFont(font);
        
        inputTextPane.addCaretListener(new CaretListener() {
            // Each time the caret is moved, it will trigger the listener and its method caretUpdate.
            // It will then pass the event to the update method including the source of the event (which is our textarea control)
            public void caretUpdate(CaretEvent e) {
                CustomTextPane editArea = (CustomTextPane)e.getSource();

                // Lets start with some default values for the line and column.
                int rowNum = 1;
                int colNum = 1;

                // We create a try catch to catch any exceptions. We will simply ignore such an error for our demonstration.
                try {
                    // First we find the position of the caret. This is the number of where the caret is in relation to the start of the JTextArea
                    // in the upper left corner. We use this position to find offset values (eg what line we are on for the given position as well as
                    // what position that line starts on.
                    int caretPos = editArea.getCaretPosition();
                    
                    //linenum = editArea.getLigetLineOfOffset(caretPos);//for JTextArea
                    rowNum = (caretPos == 0) ? 1 : 0;
                    for (int offset = caretPos; offset > 0;) {
                        offset = Utilities.getRowStart(editArea, offset) - 1;
                        rowNum++;
                    }
                    

                    // We subtract the offset of where our line starts from the overall caret position.
                    // So lets say that we are on line 5 and that line starts at caret position 100, if our caret position is currently 106
                    // we know that we must be on column 6 of line 5.
                    //columnnum = caretPos - editArea.getLineStartOffset(linenum);//for JTextArea

                    int offset = Utilities.getRowStart(editArea, caretPos);
                    colNum = caretPos - offset + 1;
                    
                    // We have to add one here because line numbers start at 0 for getLineOfOffset and we want it to start at 1 for display.
                    rowNum += 1;
                }
                catch(Exception ex) { }

                // Once we know the position of the line and the column, pass it to a helper function for updating the status bar.
                updateStatus(rowNum, colNum);
            }
        });
        
        inputScrollPane.setViewportView(inputTextPane);
        
        resultScrollPane = new JScrollPane();
        GroupLayout gl_resultPanel = new GroupLayout(resultPanel);
        gl_resultPanel.setHorizontalGroup(
        	gl_resultPanel.createParallelGroup(Alignment.TRAILING)
        		.addComponent(resultScrollPane, Alignment.LEADING, GroupLayout.DEFAULT_SIZE, 757, Short.MAX_VALUE)
        );
        gl_resultPanel.setVerticalGroup(
        	gl_resultPanel.createParallelGroup(Alignment.LEADING)
        		.addGroup(gl_resultPanel.createSequentialGroup()
        			.addGap(1)
        			.addComponent(resultScrollPane, GroupLayout.DEFAULT_SIZE, 397, Short.MAX_VALUE)
        			.addGap(1))
        );
        
        
        ///
        
        JPanel uncertaintySettingPanel = new JPanel();
        uncertaintySettingPanel.setForeground(SystemColor.windowText);
        uncertaintySettingPanel.setBackground(UIManager.getColor("ArrowButton.background"));
         
        limitButtonGroup = new ButtonGroup();
        
        JLabel uncertaintylimitLabel = new JLabel("Uncertainty Limit");
        
        limit35RadioButton = new JRadioButton("35");
        limit35RadioButton.setToolTipText("default ENSDF uncertainty limit");
        CheckControl.errorLimit=35;
        limit35RadioButton.setSelected(true);
        
        limit35RadioButton.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if(((JRadioButton)e.getSource()).isSelected()){
                    CheckControl.errorLimit=35;
                }
            }
        });
        limitButtonGroup.add(limit35RadioButton);
        
        limit99RadioButton = new JRadioButton("99");
        limit99RadioButton.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if(((JRadioButton)e.getSource()).isSelected()){
                    CheckControl.errorLimit=99;
                }
            }
        });
        limitButtonGroup.add(limit99RadioButton);
        
        otherLimitRadioButton = new JRadioButton("other");
        otherLimitRadioButton.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                otherLimitItemStateChanged(e);
            }
        });
        
        limitButtonGroup.add(otherLimitRadioButton);
        
        uncLimitTextField = new JTextField();
        uncLimitTextField.setEnabled(false);
        uncLimitTextField.setColumns(10);
        uncLimitTextField.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent arg0) {
                uncLimitFieldKeyReleased(arg0);
            }
        });
        
        controlPanel.add(uncertaintySettingPanel);
        
        ///
        messenger = new CustomTextPane();
        messenger.setEditable(false);
        resultScrollPane.setViewportView(messenger);
        resultPanel.setLayout(gl_resultPanel);
        
        calculateButton = new JButton("calculate");
        calculateButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                calculateButtonActionPerformed(e);
            }
        });
        
        clearMessengerButton = new JButton("clear");
        clearMessengerButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    clear();
                } catch (IOException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
            }
        });
        clearMessengerButton.setToolTipText("clear results in the output area");
        
        T12MRButtonGroup = new ButtonGroup();
        
        T12RadioButton = new JRadioButton("T12");
        T12RadioButton.setToolTipText("calculate T1/2 from widths in level continuation records or comments");
        T12RadioButton.setSelected(true);
        T12RadioButton.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if(((JRadioButton)e.getSource()).isSelected()){
                }
            }
        });
        
        T12MRButtonGroup.add(T12RadioButton);
        
        widthRadioButton = new JRadioButton("Width");
        widthRadioButton.setEnabled(false);
        widthRadioButton.setToolTipText("calculate width from T12");
        //widthRadioButton.setSelected(true);
        
        widthRadioButton.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if(((JRadioButton)e.getSource()).isSelected()){
                }
            }
        });
        T12MRButtonGroup.add(widthRadioButton);
        
        GroupLayout gl_controlPanel = new GroupLayout(controlPanel);
        gl_controlPanel.setHorizontalGroup(
        	gl_controlPanel.createParallelGroup(Alignment.TRAILING)
        		.addGroup(Alignment.LEADING, gl_controlPanel.createSequentialGroup()
        			.addContainerGap()
        			.addComponent(calculateButton)
        			.addGap(18)
        			.addComponent(T12RadioButton, GroupLayout.PREFERRED_SIZE, 62, GroupLayout.PREFERRED_SIZE)
        			.addPreferredGap(ComponentPlacement.RELATED)
        			.addComponent(widthRadioButton, GroupLayout.PREFERRED_SIZE, 62, GroupLayout.PREFERRED_SIZE)
        			.addGap(74)
        			.addComponent(uncertaintySettingPanel, GroupLayout.PREFERRED_SIZE, 339, GroupLayout.PREFERRED_SIZE)
        			.addPreferredGap(ComponentPlacement.RELATED, 34, Short.MAX_VALUE)
        			.addComponent(clearMessengerButton)
        			.addContainerGap())
        );
        gl_controlPanel.setVerticalGroup(
        	gl_controlPanel.createParallelGroup(Alignment.LEADING)
        		.addGroup(gl_controlPanel.createSequentialGroup()
        			.addGroup(gl_controlPanel.createParallelGroup(Alignment.LEADING)
        				.addComponent(uncertaintySettingPanel, GroupLayout.PREFERRED_SIZE, 35, GroupLayout.PREFERRED_SIZE)
        				.addGroup(gl_controlPanel.createSequentialGroup()
        					.addGap(4)
        					.addGroup(gl_controlPanel.createParallelGroup(Alignment.BASELINE)
        						.addComponent(calculateButton)
        						.addComponent(T12RadioButton, GroupLayout.PREFERRED_SIZE, 18, GroupLayout.PREFERRED_SIZE)
        						.addComponent(widthRadioButton, GroupLayout.PREFERRED_SIZE, 18, GroupLayout.PREFERRED_SIZE)))
        				.addGroup(gl_controlPanel.createSequentialGroup()
        					.addGap(4)
        					.addComponent(clearMessengerButton)))
        			.addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        GroupLayout gl_uncertaintySettingPanel = new GroupLayout(uncertaintySettingPanel);
        gl_uncertaintySettingPanel.setHorizontalGroup(
        	gl_uncertaintySettingPanel.createParallelGroup(Alignment.LEADING)
        		.addGroup(Alignment.TRAILING, gl_uncertaintySettingPanel.createSequentialGroup()
        			.addContainerGap()
        			.addComponent(uncertaintylimitLabel)
        			.addGap(9)
        			.addComponent(limit35RadioButton)
        			.addGap(5)
        			.addComponent(limit99RadioButton)
        			.addGap(5)
        			.addComponent(otherLimitRadioButton)
        			.addGap(5)
        			.addComponent(uncLimitTextField, GroupLayout.DEFAULT_SIZE, 67, Short.MAX_VALUE)
        			.addContainerGap())
        );
        gl_uncertaintySettingPanel.setVerticalGroup(
        	gl_uncertaintySettingPanel.createParallelGroup(Alignment.LEADING)
        		.addGroup(gl_uncertaintySettingPanel.createSequentialGroup()
        			.addGap(3)
        			.addGroup(gl_uncertaintySettingPanel.createParallelGroup(Alignment.LEADING)
        				.addGroup(gl_uncertaintySettingPanel.createSequentialGroup()
        					.addGap(6)
        					.addComponent(uncertaintylimitLabel))
        				.addGroup(gl_uncertaintySettingPanel.createSequentialGroup()
        					.addGap(4)
        					.addComponent(limit35RadioButton))
        				.addGroup(gl_uncertaintySettingPanel.createSequentialGroup()
        					.addGap(4)
        					.addComponent(limit99RadioButton))
        				.addGroup(gl_uncertaintySettingPanel.createSequentialGroup()
        					.addGap(4)
        					.addComponent(otherLimitRadioButton))
        				.addComponent(uncLimitTextField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
        			.addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        uncertaintySettingPanel.setLayout(gl_uncertaintySettingPanel);
        controlPanel.setLayout(gl_controlPanel);
        getContentPane().setLayout(groupLayout);    
        
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        pack();
        //setVisible(true);
        
        setResizable(false);
    }
    
    // This helper function updates the status bar with the line number and column number.
    private void updateStatus(int rowNo, int colNo) {
        colNoLabel.setText("Col " + colNo);
    }
    
	private void loadAdoptedButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadButtonActionPerformed
		String errMsg="*Error*: loading Adopted dataset failed. Please check the file!\n";
		
        try{

            //JFileChooser fc=new JFileChooser();
            JFileChooser fc;
            try {
                fc = new JFileChooser(".");
             }catch (Exception e) {
                fc = new JFileChooser(".", new RestrictedFileSystemView());
             }
            
            
            if(consistency.main.Setup.filedir!=null)
                fc.setCurrentDirectory(new File(consistency.main.Setup.filedir));
            
            //fc.setMultiSelectionEnabled(true);
            
            int ret=fc.showOpenDialog(this);
            if(ret==JFileChooser.APPROVE_OPTION){
                consistency.main.Setup.filedir=fc.getCurrentDirectory().toString();
                consistency.main.Setup.save();
                
                //File[] files=fc.getSelectedFiles();
                File dataFile=fc.getSelectedFile();
                
                MassChain data=new MassChain();
                data.load(dataFile);
                
                ENSDF temp=data.getENSDF(0);
                
                if(temp!=null) {
                	String NUCID0=temp.nucleus().nameENSDF();
                	String DSID0=temp.DSId0();
                	if(!DSID0.contains("ADOPTED")) {
                		JOptionPane.showMessageDialog(this,"*Error*: the loaded dataset is NOT an Adopted dataset.\n Please load an Adopted dataset of the same nuclide");  
                		return;
                	}else if(ens!=null && !NUCID0.equals(ens.nucleus().nameENSDF())) {
                		JOptionPane.showMessageDialog(this,"*Error*: the loaded Adopted dataset is NOT for the same nuclide as the input ENSDF.\n Please load an Adopted dataset of the same nuclide");  
                		return;
                	}
                	
                	adopted=temp;
                	this.loadAdoptedCheckBox.setSelected(true);
                	this.printMessage("**Adopted dataset <"+NUCID0.trim()+":"+adopted.DSId0()+"> has been loaded for calculating branching ratios");
                	this.printMessage("    "+dataFile.getAbsolutePath()+"\n\n");
                }else {
                	JOptionPane.showMessageDialog(this,errMsg);  
                	return;
                }
                
            }       
        }catch(Exception e){
            e.printStackTrace();
            
            JOptionPane.showMessageDialog(this,errMsg);  
            
        }

    }//GEN-LAST:event_loadButtonActionPerformed
	
    private void loadButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadButtonActionPerformed

         try{
             
             //JFileChooser fc=new JFileChooser();
             JFileChooser fc;
             try {
                 fc = new JFileChooser(".");
              }catch (Exception e) {
                 fc = new JFileChooser(".", new RestrictedFileSystemView());
              }
             
             
             if(consistency.main.Setup.filedir!=null)
                 fc.setCurrentDirectory(new File(consistency.main.Setup.filedir));
             
             //fc.setMultiSelectionEnabled(true);
             
             int ret=fc.showOpenDialog(this);
             if(ret==JFileChooser.APPROVE_OPTION){
                 consistency.main.Setup.filedir=fc.getCurrentDirectory().toString();
                 consistency.main.Setup.save();
                 
                 //File[] files=fc.getSelectedFiles();
                 File dataFile=fc.getSelectedFile();
                 
                 MassChain data=new MassChain();
                 data.load(dataFile);
                 
                 ens=data.getENSDF(0);
                 
                 String s="";
                 for(String line:ens.lines())
                     s+=line+"\n";
                         
                 clear(inputTextPane);
                 inputTextPane.setText(s);
                 
             }       
         }catch(Exception e){
             e.printStackTrace();
             String msg="*Error*: loading failed. Please check the file!\n";
             JOptionPane.showMessageDialog(this,msg);  
             
         }

    }//GEN-LAST:event_loadButtonActionPerformed
        

    private void uncLimitFieldKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_pathFieldKeyReleased
        try{
            CheckControl.errorLimit=Integer.parseInt(uncLimitTextField.getText());
        }catch(NumberFormatException e){
            JOptionPane.showMessageDialog(this, "Error: wrong input for error limit! Please re-type an integer.");
        }
    }

    private void otherLimitItemStateChanged(ItemEvent e){
        if(((JRadioButton)e.getSource()).isSelected()){
            uncLimitTextField.setEnabled(true);
            try{
                String text=uncLimitTextField.getText().trim();
                if(!text.isEmpty())
                    CheckControl.errorLimit=Integer.parseInt(text);
            }catch(NumberFormatException e1){
                JOptionPane.showMessageDialog(this, "Error: wrong input for error limit! Please re-type an integer.");
            }
        }else{
            uncLimitTextField.setEnabled(false);
        }
    }
    
    public void clear() throws IOException{
        clear(messenger);
    }

    public void clear(JTextPane textPane) throws IOException{
        if(textPane!=null){
            textPane.setText("");
            textPane.setCaretPosition(textPane.getDocument().getLength());
            textPane.update(textPane.getGraphics());
        }
    }
    
    private boolean isInputChanged() {
        if(ens==null)
            return true;
        
        String inputENSDF="";
        for(String s:ens.lines())
            inputENSDF+=s.trim()+"\n";
        
        inputENSDF=Str.rtrim(inputENSDF);
        
        String text=inputTextPane.getText();
        
        String[] temp=text.split("\n");
        text="";
        for(int i=0;i<temp.length;i++)
            text+=temp[i].trim()+"\n";
        
        text=Str.rtrim(text);
        
        //System.out.println("*"+inputENSDF+"*"+inputENSDF.length()+"\n");
        //System.out.println("***\n");
        //System.out.println("*"+text+"*"+text.length()+"\n");
        
        if(!inputENSDF.equals(text))
            return true;
        
        return false;
        
    }
    
    /*
     * find widths in continuation records and comments of a level 
     */
	@SuppressWarnings("unused")
	private Vector<ContRecord> findWidthsOfLevel(Level lev){
        Vector<ContRecord> out=new Vector<ContRecord>();
      
      
        //look for widths in continuation record
    	//B(XL) in continuation record must be for transition to g.s., which has J(gs)=this J-2
        for(ContRecord cr:lev.contRecsV()) {           
            String name=cr.name();
            if(name.equals("WIDTH")) {
                out.add(cr);
            }
        }

        
        //look for widths in comments
        ArrayList<String> otherS=new ArrayList<String>(Arrays.asList(". OTHER",".OTHER","; OTHER",";OTHER"));
        for(Comment c:lev.commentV()) {
            //if(!c.head().isEmpty() && !c.head().equals("T"))
            //    continue;
            
        	String s0=c.rawBody();
            String s=s0.toUpperCase();
            int nWidths=s0.indexOf("|G");
            
            if(c.isBigC()) {
            	s0=c.cbody();
            	s=c.cbody().toUpperCase();
            	if(nWidths<0)
            		nWidths=s0.indexOf("WIDTH");
            }
            
            if(nWidths<0)
                continue;
            
            int n=-1;
            for(String os:otherS) {
                n=s.indexOf(os);
                if(n>0) {
                    s=s.substring(0,n).trim();
                    s0=s0.substring(0,n).trim();
                    break;
                }
            }
       
            n=s.indexOf("WEIGHTED AVERAGE");
            if(n<0)
            	n=s.indexOf("AVERAGE OF");
            if(n<0)
            	n=s.indexOf("WEIGHTED MEAN");
            
            if(n>0) {
            	s=s.substring(0,n).trim();
            	s0=s0.substring(0,n).trim();
            	n=s.lastIndexOf(",");
            	String s1="";
            	if(n>0) {
            		s1=s.substring(n+1).trim();
            		s1=s1.replace(" ","");
            		if(Str.isLetters(s1)) {
            			s=s.substring(0,n).trim();
            			s0=s0.substring(0,n).trim();
            		}
            	}
            }
            
            if(s.contains("|*10{+")) {
            	String s1=s,tempS="";
            	int n1=-1;
            	while((n1=s1.indexOf("|*10{+"))>=0){
            		int n2=s1.indexOf("}",n1+6);
            		if(n2<0) {
            			tempS+=s1.substring(0,n1)+"E"+s1.substring(n1+6);
            			break;
            		}else {
                		String ns=s1.substring(n1+6,n2).trim();
                		if(Str.isInteger(ns)) {
                    		tempS+=s1.substring(0,n1)+"E"+s1.substring(n1+6,n2);
                		}else {
                			tempS+=s1.substring(0,n2+1);
                		}

                		s1=s1.substring(n2+1);
            		}

            	}           		
            	
            	if(tempS.length()>0) {
            		if(s1.length()>0)
            			tempS+=s1;
            		
            		s=tempS;
            	}
            }
            
            Vector<FindValue> fvs=EnsdfUtil.findDataEntriesInLine(s);
            
          
            //System.out.println(" Width2T12 Calculator 731: s="+s+" \n  ### fvs size="+fvs.size());
 
            for(FindValue fv:fvs) {

                String entry="",s1="",s2="",s3="",es="",js="";
                
                String name=fv.name().trim();
                s=fv.txt();
                
                //System.out.println("CalculatorOfT12MRFrame 766: name="+name+"  fv symbol="+fv.symbol()+" s="+fv.s()+" ds="+fv.ds()+" text="+fv.txt()+" "+fv.canGetV()+" "+fv.units());
                
                name=name.replace("*","");
                
                if(!fv.canGetV() || (!name.startsWith("|G")&&!name.startsWith("G|G")&&!name.toUpperCase().startsWith("WIDTH")) || fv.units().isEmpty()) 
                	continue;
                
                name=name.replace("|G{-|G0}", "|G{-0}").replace("|G{-|G{-0}}", "|G{-0}").replace("|G(0)","|G{-0}").trim();
                if(name.toUpperCase().equals("WIDTH"))
                	name=name.toUpperCase().trim();
                
                //System.out.println("CalculatorT1/2 648: ###name="+name+" txt="+s+" v="+fv.v()+"  @@@ s="+fv.s()+" ds="+fv.ds()+" unit="+fv.units());               
                
                //System.out.println("CalculatorT1/2 658: name="+name+" s="+s+" type="+type+" neq="+neq+" fv.canGetV()="+fv.canGetV()); 
                               
            	//System.out.println(isME+"  name="+name+"   c="+s+" s3="+s3+" es="+es);
                
                
                //System.out.println("CalculatorOfT12MRFrame 868: c="+c.body()+" fv symbol="+fv.symbol()+" s="+fv.s()+" ds="+fv.ds()+" text="+fv.txt());
                
                ContRecord cr=new ContRecord();
                
                String crName=name;
                String crText=fv.txt();
               
                String unit=fv.units();
                if(unit.equals("MEV") && !s0.contains("MEV"))
                	unit="meV";
                
                if(crName.startsWith("G|G"))
                	crName="g|G"+crName.substring(3);
                		
                cr.setValues(crName,fv.symbol(),fv.s(),unit,fv.ds(),fv.ref(),crText,"2",false,false,false); 

                //System.out.println("CalculatorOfT12MRFrame 901: c="+c.body()+"\n   "
                //		+ "fv name="+name+"  symbol="+fv.symbol()+" s="+fv.s()+" ds="+fv.ds()+" text="+fv.txt()+" ### es="+es+" js="+js);
              
                out.add(cr);
            }          
        }
        
        return out;
    }
    
    private void calculateButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Output_ButtonActionPerformed

        try{
            if(isInputChanged()) {
                String text=inputTextPane.getText();
                
                if(text==null || text.trim().isEmpty()) {
                	this.printMessage("Nothing can be calculated. Please check input!\n");
                    return;
                }

                String[] temp=text.split("\n");
                Vector<String> lines=new Vector<String>();
                
                for(int i=0;i<temp.length;i++)
                    lines.add(temp[i]);
                
                ens=new ENSDF();
                ens.setValues(lines);    
                
            	//for(Level l:ens.levelsV())
            	//	System.out.println("CalculatorFrame 1000: L="+l.ES()+" T="+l.T12S());
            }

            if(this.loadAdoptedCheckBox.isSelected() && adopted==null) {
                JOptionPane.showMessageDialog(this, "Error: you haven't loaded an Adopted dataset for gamma branching ratios.");
                return;
            }
            
            calculate();
        }catch(Exception e) {
        	e.printStackTrace();
        }
    }
    @SuppressWarnings("unused")
    private void calculate(){//GEN-FIRST:event_Output_ButtonActionPerformed

        try{
           
            /*
            Vector<Level> levels=new Vector<Level>();
            String lineType="",line="";
            boolean hasLevel=false,hasGamma=false;
            for(int i=0;i<temp.length;i++) {
                line=temp[i];
                line=Str.fixLineLength(line,80);

                lineType=line.substring(5,8);
                
                if(lineType.equals("  L")) {
                    if(lines.size()>0 && hasLevel && hasGamma) {
                        Level lev=new Level();
                        lev.setValues(lines,levels.size());
                        levels.add(lev);
                        
                        hasGamma=false;
                    }
                    lines.clear();
                    lines.add(line);
                    hasLevel=true;
                }else if(hasLevel&&lineType.equals("  G")){
                    lines.add(line);
                    hasGamma=true;
                }else if(hasLevel) {              
                    lines.add(line);
                }
                
            }
            
            if(lines.size()>0 && hasLevel && hasGamma) {
                Level lev=new Level();
                
                lev.setValues(lines,levels.size());
                levels.add(lev);
            }
            */
            
            GammaBranchingCalculator BRCalc=new GammaBranchingCalculator(); 
            
            String msg="";
            String indent="    ";
            

            //Control.isGoodBriccs=BriccsWrap.checkInternalBrIccs(Setup.outdir);          
            //System.out.println("Calculator frame 756: isGoodBriccs="+Control.isGoodBriccs);
            
            if(widthRadioButton.isSelected()) {
            	Run run=new Run();
            	
            	//for(Level l:ens.levelsV())
            	//	System.out.println("CalculatorFrame 1000: L="+l.ES()+" T="+l.T12S());
            	
            	//msg=run.getReport();
            	//msg+=results.getReport();
            	
            }else if(T12RadioButton.isSelected()) {
            	
            	//System.out.println(ens.nLevels());
            	
                for(Level lev:ens.levelsV()) {
                    

                    //calculate T12 from measured transition strengths in level continuation record
                    BRCalc.reset();
 
                    Level adoptedLevel=EnsdfUtil.findAdoptedLevel(lev, adopted);

                    
                    boolean hasAdopted=false;
                    if(adoptedLevel==null)
                    	adoptedLevel=lev;
                    else
                    	hasAdopted=true;
                    
                	BRCalc.addGammaBranches(adoptedLevel);      
                    BRCalc.calculateBranchingsFromRI(false);//false for not including Monte-Carlo approach for uncertainty propagation
                    
                    Gamma gsGam=null;
                    if(adoptedLevel.GammasV().size()>0) {
                    	gsGam=adoptedLevel.GammasV().lastElement();
                    	
                    	//System.out.println(adoptedLevel.ES()+" "+gsGam.ES()+"  "+ EnsdfUtil.isComparableEnergyEntry(adoptedLevel, gsGam));
                    	
                    	if(!EnsdfUtil.isComparableEnergyEntry(adoptedLevel, gsGam) && !EnsdfUtil.isComparableEnergyEntry(adoptedLevel, gsGam,10,true))
                    		gsGam=null;
                    }
                    
                    boolean isBRLimit=false;
                    String dbrs="";
                                        
                	double br=-1,dbru=-1,dbrl=-1;
                	double g=-1;
                	double jiFactor=-1;//(2Jx+1)
                    double jfFactor=-1;//(2J0+1)
                    
                    String jis=adoptedLevel.JPiS();
                    String jfs="";
                    
                    float ji=EnsdfUtil.spinValue(jis);
                    float jf=-1;
                    
                    Level fl=null;
                    int fli=-1;
                    
                    boolean isAssumedBR=false;
                    String uncMethod="EX";
                    
                	if(gsGam!=null) {
                        int index=adoptedLevel.GammasV().indexOf(gsGam);
                        GammaBranch gb=BRCalc.branchAt(index);
                        
                        br=gb.br;//percentage value
                        dbru=dbru(gb,uncMethod);
                        dbrl=dbrl(gb,uncMethod);

                        
                        isBRLimit=gb.isBRLimit();
                        if(Str.isLetters(gsGam.RIS()))
                        	dbrs=gsGam.RIS();

                        jf=-1;
                        
                        fli=gsGam.FLI();
                        if(fli>=0) {
                        	if(hasAdopted)
                        		fl=adopted.levelAt(fli);
                        	else
                        		fl=ens.levelAt(fli);
                        	
                        	jfs=fl.JPiS();
                            jf=EnsdfUtil.spinValue(jfs);
                                           
                            if(ji>=0) {
                            	jiFactor=(2*ji+1);
                            	if(jf>=0) {
                                	g=(2*ji+1)/(2*jf+1);
                            	}
                            }
                            if(jf>=0)
                            	jfFactor=(2*jf+1);
                        }
                        if(br<0 &&adoptedLevel.nGammas()<=1) {
                        	br=100;
                        	dbru=0;
                        	dbrl=0;
                        	isAssumedBR=true;
                        }
                	}else {
                		try {
                    		if(hasAdopted)
                    			fl=adopted.levelAt(0);
                    		else
                    			fl=ens.levelAt(0);
                		}catch(Exception e) {}

                		if(fl!=null) {
                            jfs=fl.JPiS();
                            jf=EnsdfUtil.spinValue(jfs);
                                           
                            if(ji>=0) {
                            	jiFactor=(2*ji+1);
                            	if(jf>=0) {
                                	g=(2*ji+1)/(2*jf+1);
                            	}
                            }
                            if(jf>=0)
                            	jfFactor=(2*jf+1);
                		}

                        
                        if(br<0 && adoptedLevel.nGammas()==0) {//no adopted gammas from this level
                        	br=100;
                        	dbru=0;
                        	dbrl=0;
                        	isAssumedBR=true;
                        }
                	}
                    

                	//System.out.println("##### "+br+"  "+isAssumedBR+"  "+adoptedLevel.ES()+"  "+adoptedLevel.gammaAt(0).ES() +(gsGam!=null));
                	
                    LinkedHashMap<String,String> EMmap=null;

                    String s="";
                    String title="*** level="+lev.ES()+" ";
                    title=title+Str.repeat("*", 70-title.length());
                    
                    Vector<ContRecord> widthsV=findWidthsOfLevel(lev);
                	
                    //System.out.println(lev.ES()+" "+gamBXLsMap.size());
                    
                    //System.out.println("CalculatorOfT12 943: lev="+lev.ES()+"  size of cr="+widthsV.size());
                    
                    for(ContRecord cr:widthsV) {
                        String name=cr.name();
                        String type="";
                        
                        String valS=cr.s();//assumed as BXLUP
                        String uncS=cr.ds();
                        String unit=cr.units();
                        
                        //System.out.println("        CalculatorOfT12 953: width name="+name+" val="+valS+" unc="+uncS+" unit="+unit);
                        //System.out.println(!Str.isNumeric(valS)+"  "+unit.isEmpty()+"  "+EnsdfUtil.isWidthUnit(unit));
                        
                        if(!Str.isNumeric(valS) || unit.isEmpty() || !EnsdfUtil.isWidthUnit(unit)) {
                            continue;
                        }

                        boolean isWidthProduct=false;
                    	boolean hasGFactor=false;//g=(2Ji+1)/(2J0+1) 
                    	
                        SDS2XDX result=null,width0=null,width=null,lifetime=null;

                        if(name.equals("WIDTH") || name.equals("|G")) {
                        	
                        	result=EnsdfUtil.widthToT12(valS,uncS, unit,CheckControl.errorLimit);
                        	
                        }else if((name.startsWith("|G{")||name.startsWith("g|G{")) && name.contains("{+2}/|G")){
                        	

                        	//calculate width assuming g=1; g=(2Ji+1)/(2J0+1) will be processed later if existing
                        	isWidthProduct=true;

                        	if(name.startsWith("g|G"))
                        		hasGFactor=true;
                        	

                        	//now name should be |G{-0}{+2}/|G, |G{-0}/|G is the branching BR
                        	
                        	//result here is for valS as width, result=const/valS, but here valS= width0^2/width
                        	//to get actual result=const/width=const/valS*valS/width=const/valS*(width0/width)^2=const/valS*(BR)^2 
                        	
                        	//System.out.println(valS+"  "+uncS+"  "+unit);
                        	
                        	result=EnsdfUtil.widthToT12(valS,uncS, unit,CheckControl.errorLimit);//result in units of second
                        	      
                        	//System.out.println(" 1 "+result.x()+" "+result.dxu()+"  "+result.dxl());
                        	
                        	width0=new SDS2XDX();
                        	width0.setValues(valS, uncS);
                        	width0.setErrorLimit(CheckControl.errorLimit);
                        	if(dbru>0) {
                              	SDS2XDX temp=new SDS2XDX();
                              	temp.setErrorLimit(CheckControl.errorLimit);
                              	temp.setValues(br/100,dbru/100,dbrl/100);//br is percentage branching
                              	width0=width0.divided(temp);
                        	}else if(br==100) {
                        		//do nothing
                        	}else if(br>0){
                        		SDS2XDX temp=new SDS2XDX();
                        		temp.setErrorLimit(CheckControl.errorLimit);
                        		temp.setValues(br/100, -1);
                              	width0=width0.divided(temp.s(),dbrs);
                        	}
  
                        	if(g>0) {
                        		result=result.multiply(g,0);
                        		width0=width0.divided(g,0);
                        	}else if(jiFactor>0) {
                          		result=result.multiply(jiFactor,0);
                        		width0=width0.divided(jiFactor,0);
                        	}else if(jfFactor>0) {
                          		result=result.divided(jfFactor,0);
                        		width0=width0.multiply(jfFactor,0);
                        	}
                        	
                        	//System.out.println(" 2 "+result.x()+" "+result.dxu()+"  "+result.dxl());
                        	
                        	SDS2XDX brSQ=new SDS2XDX();
                        	brSQ.setErrorLimit(CheckControl.errorLimit);
                        	if(dbru>=0) {
                        		double tempDXU=((br+dbru)*(br+dbru)-br*br)/10000;
                        		double tempDXL=(br*br-(br-dbrl)*(br-dbrl))/10000;
      
                        		brSQ.setValues(br*br/10000, tempDXU, tempDXL);
                        	}else if(br==100) {
                        		//do nothing
                        	}else if(br>0){
                        		brSQ.setValues(br*br/10000, -1);
                        		brSQ.setDS(dbrs);
                        	}                       	
                       		result=result.multiply(brSQ);
                          	//System.out.println(result.x()+"  "+result.ds());
                       		
                       		//System.out.println(" brsQ="+brSQ.s()+" "+brSQ.dsu()+"  "+brSQ.dsl()+"  "+brSQ.ds());
                        	//System.out.println(" 3 "+result.x()+" "+result.dxu()+"  "+result.dxl());
                        }else {
            
                        	continue;
                        }
                        
                        
                        lifetime=result.divided(Math.log(2.0));//in units of second

                        s+="   ######## <T12 result at level="+lev.ES()+"> ########\n";
                        
   
                        if(isWidthProduct) {
                            if(br==100)
                            	width=width0.multiply(1.0,0);
                            else
                            	width=EnsdfUtil.lifetimeToWidth(lifetime, "s",CheckControl.errorLimit);

                        	if(gsGam!=null) {
                        		s+="   inputs: "+cr.txt()+"\n";
                        		if(hasAdopted) {
                            		
                            		s+=String.format("             for level=%-8s   JI=%-18s",lev.ES(),lev.JPiS())+"\n";
                            		s+=String.format("         adopted level=%-8s   JI=%-18s JF=%-18s EG(g.s.)=%-10s",adoptedLevel.ES(),jis,jfs,gsGam.ES())+"\n\n";                        			
                        		}else {
                               		s+=String.format("             for level=%-8s   JI=%-18s JF=%-18s EG(g.s.)=%-10s",lev.ES(),jis,jfs,gsGam.ES())+"\n\n";
                        		}
                        		if(adoptedLevel.nGammas()>0) {
                        			for(int i=0;i<adoptedLevel.GammasV().size();i++) {
                        				Gamma gam=adoptedLevel.gammaAt(i);
                        				GammaBranch gb1=BRCalc.branchAt(i);
                                        double br1=gb1.br;//percentage value
                                        double dbru1=dbru(gb1,uncMethod);
                                        double dbrl1=dbrl(gb1,uncMethod);
                                        
                                	    s+="             gamma#"+(i+1)+String.format(":  E=%10s %-2s   RI=%10s %-2s   %%BR=%-20s",gam.ES(),gam.DES(),gam.RIS(),gam.DRIS(),printXDX(br1,dbru1,dbrl1))+"\n";
                        			}
                                    s+="\n";
                        		}
                        	}else {
                     			s+="   inputs: "+cr.txt()+"\n";
                        		if(hasAdopted) {//has adopted level, but no gs transition
                       
                            		s+=String.format("             for level=%-8s   JI=%-18s",lev.ES(),lev.JPiS())+"\n";
                            		s+=String.format("         adopted level=%-8s   JI=%-18s JF=%-18s",adoptedLevel.ES(),jis,jfs)+"\n\n";
                        			
                        		}else {
                            		s+=String.format("             for level=%-8s   JI=%-18s JF=%-18s",lev.ES(),jis,jfs)+"\n\n";
                        		}
             
                        		if(br==100)
                        			s+="          ** No g.s. transition and %BR=100 is assumed\n";
                        		s+="\n";
                        	}
                        	if(br>0) {
                            	if(hasGFactor && g<0)
                            		s+=indent+String.format("%-20s %-14s %-18s %-18s %-20s", "*T1/2","%BR","*Width0","*Width","*Lifetime")+"\n";
                            	else
                            		s+=indent+String.format("%-20s %-14s %-18s %-18s %-20s", "T1/2","%BR","Width0","Width","Lifetime")+"\n";
                            	
                                s+=indent+"----------------------------------------------------------------------------------------\n";
                                s+=indent+String.format("%-20s %-14s %-18s %-18s %-20s",printT12(result),printXDX(br,dbru,dbrl),printWidth(width0),printWidth(width),printT12(lifetime))+"\n";

                                //System.out.println(br+"  "+dbru+"  "+dbrl+"  printXDX(br,dbru,dbrl)="+printXDX(br,dbru,dbrl));
                        	}else {
                        		s+="          *** no branching ratio for g.s. transition and nothing can be calculated ***\n";
                        	}
        
                            //System.out.println(lev.ES()+" ji="+ji+" jf="+jf+"    g="+g);
                        }else {                             
                            s+="     inputs: "+cr.txt()+" for level="+lev.ES()+", JI="+jis+" \n\n";
                            
                            s+=indent+String.format("%-20s %-18s", "T1/2","Lifetime")+"\n";
                            s+=indent+"-----------------------------------------------------------\n";
                            s+=indent+String.format("%-20s %-18s",printT12(result),printT12(result.divided(Math.log(2))))+"\n";
                        }

                        String s1="",s2="";
                        if(hasGFactor && g<0 && br>0) {
                        	//g>0 has been processed earlier
      
                        	if(jiFactor>0) {
                            	s1+="*** T1/2 result is for actual T*(2J0+1), since gs spin J0 is unknown or indefinite ***\n";
                            	s1+="    Width0 and Width values are for acutal width/(2J0+1)\n";
                        	}else if(jfFactor>0) {
                            	s1+="*** T1/2 result is for actual T/(2Jx+1), since level spin Jx is unknown or indefinite***\n";
                            	s1+="    Width0 and Width values are for acutal width*(2Jx+1)\n";
                        	}else {
                            	s1+="*** T1/2 result is for actual T/g with g=(2Jx+1)/(2J0+1), since g is unknown or indefinite ***\n";
                            	s1+="    Width0 and Width values are for acutal width/g\n";
                        	}

                        }
                        if(!isAssumedBR) {
                            if(hasAdopted && br>0) {
                            	s2="*** %BR is deduced from gamma intensities in the input Adopted dataset ***\n";
                            }else if(br>0) {
                            	s2="*** %BR is deduced from gamma intensities in the current dataset ***\n";
                            }
                        }

                        
                        if(s1.length()>0) {
                            s+="\n";
                        	s+=Str.addMarginHead(s1,indent+"NOTE:");
                        	
                        	//System.out.println(Str.addMarginHead(s1,indent+"NOTE:"));
                        	
                        	if(s2.length()>0)
                        		s+=Str.addMarginHead(s2,indent+"     ");
                        }else if(s2.length()>0) {
                            s+="\n";
                        	s+=Str.addMarginHead(s2,indent+"NOTE:");
                        }
    
     
                    }
                        
                    if(s.length()>0) {
                        msg+=title+"\n";
                        msg+=s+"\n";
                    }
                }
            }
            
            if(msg.length()==0)
            	msg="Nothing can be calculated. Please check input!\n";
            else
            	msg+="\nDone!";
            
            //System.out.println(msg);
            
            printMessage(msg);
        }catch(Exception e){
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,"Please check if the input text is in valid ENSDF-format.");
        }
    }//GEN-LAST:event_Output_ButtonActionPerformed

    protected void showInstructionFrame(MouseEvent e) {
        
	    int width=600;
	    int height=795;

        try {
            String title="Calculate T1/2 from width values in a level comment";
            WidthInstructionFrame frame=null;
            Frame[] frames=java.awt.Frame.getFrames();
            for(int i=0;i<frames.length;i++) {
                if(frames[i].getTitle().equals(title))
                    frame=(WidthInstructionFrame)frames[i];
                
                //System.out.println(frames[i].getName()+"  "+frames[i].getTitle());
            }
            
            if(frame==null) {
                frame=new WidthInstructionFrame(width,height);
                frame.setTitle(title);
                //frame.setLocation(this.getX()+this.getWidth()+5,this.getY()+5);
                frame.setLocation(this.getLocationOnScreen().x+this.getWidth()+2,this.getLocationOnScreen().y);
                
                frame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                frame.setResizable(false);
            }

            frame.setVisible(true);          
        }catch(Exception e1) {
            e1.printStackTrace();        
        }
    }
    
  /*
   * method: EX--max and min
   *         EP--Taylor expansions (normal Error Propagation)
   *         MC--Monte Carlo
   */
    private double dbru(GammaBranch gb,String method) {
    	double dx=-1;
    	try {
    		method=method.toUpperCase().trim();
    		switch(method) {
    		  case "EX":
    			  dx=gb.dbru_ex;
    			  break;
    		  case "EP":
    			  dx=gb.dbru_ep;
    			  break;
    		  case "MC":
    			  dx=gb.dbru_mc;
    			  break;
    		  default:
    			  dx=gb.dbru_ex;
    			  break;
    		}
    	}catch(Exception e) {
    		
    	}
    	return dx;
    }
    
    private double dbrl(GammaBranch gb,String method) {
    	double dx=-1;
    	try {
    		method=method.toUpperCase().trim();
    		switch(method) {
    		  case "EX":
    			  dx=gb.dbrl_ex;
    			  break;
    		  case "EP":
    			  dx=gb.dbrl_ep;
    			  break;
    		  case "MC":
    			  dx=gb.dbrl_mc;
    			  break;
    		  default:
    			  dx=gb.dbrl_ex;
    			  break;
    		}
    	}catch(Exception e) {
    		
    	}
    	return dx;
    }
    
    private String printWidth(SDS2XDX width) {//input width in units of eV
    	XDX2SDS temp=new XDX2SDS(width.x(),width.dxu(),width.dxl(),CheckControl.errorLimit);
    	
    	String s=XDX2SDS.printEntry(temp, "", "eV");
    	if(Str.isLetters(width.ds()) ) {
    		return s+" "+width.ds();
    	}
    	
    	return s;
    }
    
    private String printT12(SDS2XDX sx) {
    	XDX t12=new XDX(sx.x(),sx.dxu(),sx.dxl());
    	String s=printT12(t12);
    	
    	if(Str.isLetters(sx.ds())) {
    		return s+" "+sx.ds();
    	}

    	return s;
    }
    
    /*
     * 	By default, returned results are the symmetrized result if applicable. 
     *  Set useOriginal=true to use original unsymmetrized uncertainties
     */
    private String printT12(XDX t12) {
    	return printT12(t12,false);
    }
    private String printT12(XDX t12,boolean useOriginal) {
        String s="";
        double t=t12.x;//in unit of second
        double dtu=t12.dxu;
        double dtl=t12.dxl;
        if(t<=0)
            return s;
        
        String unit="s";
        double f=1.0;
        int p=-(int)Math.log10(t);
        if(p>=13) {
            unit="fs";
            f=1E15;
        }else if(p>=10) {
            unit="ps";
            f=1E12;
        }else if(p>=7) {
            unit="ns";
            f=1E9;
        }else if(p>=4) {
            unit="us";
            f=1E6;
        }else if(p>=1) {
            unit="ms";
            f=1E3;
        }else if(p>=-2) {
            unit="s";
        }else if(p>=-4) {
            unit="m";
            f=1.0/60;
        }
        
        t=t*f;
        dtu=dtu*f;
        dtl=dtl*f;
        
        XDX2SDS x2s=new XDX2SDS(t,dtu,dtl,CheckControl.errorLimit);
   
        double x0=Math.abs(t);
        double dxu0=dtu;
        double dxl0=dtl;
        if(x2s.isLimits() && dxu0>0 && dxl0>0) {
        	double r=Math.log10(dxu0/dxl0);
        	r=Math.abs(r);
        	
        	if(r<10) {
        		x2s=new XDX2SDS(x0,dxu0,dxl0,99);
        	}else if(r<100) {
        		x2s=new XDX2SDS(x0,dxu0,dxl0,999);
        	}else if(r<1000) {
        		x2s=new XDX2SDS(x0,dxu0,dxl0,9999);
        	}
        }      
        
        String name="T1/2";
        s=XDX2SDS.printEntry(x2s,name, unit,useOriginal);
        
        if(s.startsWith(name)) 
        	s=s.substring(name.length()).trim();
        
        if(s.startsWith("="))
        	s=s.substring(1).trim();
        
        //System.out.println(t+" "+dtu+" "+dtl+" "+CheckControl.errorLimit+" limit="+x2s.isLimits()+" "+ x2s.s()+" "+ x2s.dsu()+"  "+x2s.dsl()+" s="+s);
        //System.out.println("    xs0: "+x2s.s0()+"  "+x2s.dsu0()+"  "+x2s.dsl0());

        return s;
    }

    @SuppressWarnings("unused")
	private String printMR(XDX mr) {
        return printMR(mr,false);
    }   
    private String printMR(XDX mr,boolean useOriginal) {
        return printXDX(mr,useOriginal);
    }  
    
    @SuppressWarnings("unused")
	private String printXDX(XDX mr) {
        return printXDX(mr,false);
    }
    private String printXDX(XDX mr,boolean useOriginal) {
        return printXDX(mr.x,mr.dxu,mr.dxl,useOriginal);
    }
    private String printXDX(double x,double dxu,double dxl) {
    	return printXDX(x,dxu,dxl,false);
    }
    private String printXDX(double x,double dxu,double dxl,boolean useOriginal) {


        String s="",dsu="",dsl="";
        if(dxu<0 || dxl<0)
            return s;
        
        
        XDX2SDS x2s=new XDX2SDS(x,dxu,dxl,CheckControl.errorLimit);
        if(useOriginal && !x2s.s0().isEmpty()) {
            s=x2s.s0()+" ";      
            dsl=x2s.dsl0();
            dsu=x2s.dsu0();
        }else {
            s=x2s.s()+" ";      
            dsl=x2s.dsl();
            dsu=x2s.dsu();
        }

        if(dxu==0 && dxl==0) {
        	dsl="";
        	dsu="";
        }
        if(!x2s.isLimits()) {
            if(!dsl.isEmpty() && !dsu.isEmpty()) {
                if(dsl.equals(dsu))
                    s+=x2s.dsl();
                else
                    s+="+"+x2s.dsu()+"-"+x2s.dsl();            
            }else if(!dsl.isEmpty()) {
                s+="+0-"+dsl;
            }else if(!dsu.isEmpty()) {
                s+="+"+dsu+"-0";
            }        	
        }else if(!x2s.sl().isEmpty() && !x2s.su().isEmpty()){
        	if(dxu>dxl)
        		s=">"+x2s.sl();
        	else 
        		s="<"+x2s.su();
        }


            
        //System.out.println("s="+s+" x="+x+" dxu="+dxu+" dxl="+dxl+" isLimit="+x2s.isLimits());
        
        return s.trim();
    }
    
    @SuppressWarnings("unused")
	private String printXDXAsIs(XDX mr) {

        return printXDXAsIs(mr.x,mr.dxu,mr.dxl);
    }
    private String printXDXAsIs(double x,double dxu,double dxl) {


        String s="";
        if(dxu<0 || dxl<0)
            return s;
        
        XDX2SDS x2s=new XDX2SDS(x,dxu,dxl,CheckControl.errorLimit);
        s=x2s.s()+" ";
        
        String dsl=x2s.dsl();
        String dsu=x2s.dsu();
        if(!x2s.isLimits()) {
            if(!dsl.isEmpty() && !dsu.isEmpty()) {
                if(x2s.dsl().equals(x2s.dsu()))
                    s+=x2s.dsl();
                else
                    s+="+"+x2s.dsu()+"-"+x2s.dsl();            
            }else if(!dsl.isEmpty()) {
                s+="+0-"+dsl;
            }else {
                s+="+"+dsu+"-0";
            }           
        }else{
            int n=x2s.sl().indexOf(".");
            if(n>=0)
                n=x2s.sl().length()-n-1;
            
            s=Str.roundToNDigitsAfterDot(String.valueOf(x),n);
            dsl=Str.roundToNDigitsAfterDot(String.valueOf(dxl), n);
            dsu=Str.roundToNDigitsAfterDot(String.valueOf(dxu), n);
            
            s=s+" +"+dsu+"-"+dsl;
        }


            
        //System.out.println("s="+s+" x="+x+" dxu="+dxu+" dxl="+dxl+" isLimit="+x2s.isLimits());
        
        return s.trim();
    }    
    @SuppressWarnings("unused")
    private void copyCommentButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_include_ButtonActionPerformed
        
        try{
            
            String text=messenger.getSelectedText();
            if(text==null || text.isEmpty()){
                text="";
                //for(int i=0;i<commentsV.size();i++)
                //  System.out.println(commentsV.get(i));
            }else{
                messenger.select(0,0);
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
            
            //try {
            //  messenger.scrollRectToVisible(messenger.modelToView(messenger.getDocument().getLength()));
            //} catch (BadLocationException e) {
            //  System.out.println("Error");
            //  e.printStackTrace();
            //}
            

            
            //THIS DOES NOT AUTOMATICALLY SCROLL TEXT 
            DefaultCaret caret = (DefaultCaret)textPane.getCaret();
            //caret.setUpdatePolicy(DefaultCaret.OUT_BOTTOM);
            //caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);    
            caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
            //messenger.setCaretPosition(messenger.getDocument().getLength());
            
            //textPane.update(textPane.getGraphics());
            textPane.updateUI();
            
        }
    }
    
    public void printMessageAsIs(JTextPane textPane,String message){
        Style style=StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
        printMessageAsIs(textPane,message,style);
    }
    
    
    public static void startUI() {
        

        try{
            Translator.init();
            
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
            //UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
            //UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        }catch(Exception e1){
            try{
                for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                    else{
                        UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
                    }
                }
                
            }catch(Exception e2){
                
            }           
        }
        
        WidthToT12CalculatorFrame frame=new WidthToT12CalculatorFrame();
        frame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        //frame.setTitle("Java program for averaging values (update "+main.Control.version+")");
        frame.setVisible(true);
        frame.setResizable(false);

    }
    
    @SuppressWarnings("unused")
	private static void test() {
    	try {
    		Translator.init();
    		
            String filePath="",fileDir="";
            String os=System.getProperty("os.name").toLowerCase();
            if(os.contains("mac"))
                fileDir="/Users/chenj/work/evaluation/ENSDF/check/";
            else
                fileDir="H:\\work\\evaluation\\ENSDF\\check\\";
            
            filePath=fileDir+"check.ens";
            
            File f=new File(filePath);
            
            ENSDF ens=new ENSDF();
            ens.setValues(Str.readFile(f));
            
        	WidthToT12CalculatorFrame frame=new WidthToT12CalculatorFrame();
         	
        	frame.adopted=ens;
        	
        	String s1=" 65CU    65CU(G,G')         \r\n"
        			+ " 65CU  L 0           3/2-                                                        \r\n"
        			+ " 65CU cL J         From Adopted Levels.                                         \r\n"
        			+ " 65CU  L      770.6 21/2               99 FS     5                          A   \r\n"
        			+ " 65CU cL           |G=4.6|*10{+-3} eV {I2}. Weighted mean of 4.8|*10{+-3} eV    \r\n"
        			+ " 65CU2cL {I6} (1971ImZY), 4.8|*10{+-3} eV {I4} (1972ArZD) and 4.36|*10{+-3} eV  \r\n"
        			+ " 65CU3cL {I26} (from g|G(0)=2.18|*10{+-3} eV 13, 1981Ca10). Others:             \r\n"
        			+ " 65CUxcL 4.6|*10{+-3} eV {I14} (1969Ru01), 5.1|*10{+-3} eV {I24} (1972Wh08).    \r\n"
        			+ " 65CU  G 770.6     2                                                            \r\n"
        			+ " 65CU cG E         From adopted gammas.                                         \r\n"
        			+ " 65CU  L   1115.556 4(5/2)             0.285 PS  9                          A   \r\n"
        			+ " 65CU cL           |G=1.60|*10{+-3} eV {I5}. Weighted mean of 1.73|*10{+-3} eV  \r\n"
        			+ " 65CU2cL {I10} (1968Me09), 1.63|*10{+-3} eV {I9} (1972ArZD), 1.54|*10{+-3} eV   \r\n"
        			+ " 65CU3cL {I6} (1979DaZC) and 1.65|*10{+-3} eV {I19} (from g|G(0)=2.47|*10{+-3}  \r\n"
        			+ " 65CU4cL eV {I28}, 1981Ca10). Others: 1.0|*10{+-3} eV {I3} (1963Ka29),          \r\n"
        			+ " 65CU5cL 1.5|*10{+-3} eV {I4} (1964Be21), 1.8|*10{+-3} eV {I2} (1970Ka34),      \r\n"
        			+ " 65CUxcL 1.0|*10{+-3} eV {I10} (1972Wh08), 1.9|*10{+-3} eV {I7} (1973Ko31).     \r\n"
        			+ " 65CU cL J         from |g(|q) data of 1964Be21, 1968Me09.                      \r\n"
        			+ " 65CU  G 1115.546  4           D+Q       -0.437  15                             \r\n"
        			+ " 65CU cG E         from adopted gammas.                                         \r\n"
        			+ " 65CU cG           Multipolarity, |d: from |g(|q)                               \r\n"
        			+ " 65CU2cG 1968Me09. |d(D+Q)=-0.52 {I7} or                                        \r\n"
        			+ " 65CU2cG -1.09 {I+9-12} from 1964Be21.                                          \r\n"
        			+ " 65CU  L 1481.7    5 7/2               0.41 PS   7                              \r\n"
        			+ " 65CU cL           |G=1.1|*10{+-3} eV {I3}. Unweighted mean of 1.349|*10{+-3}   \r\n"
        			+ " 65CU2cL eV {I14} (from g|G(0){+2}/|G=1.95|*10{+-3} eV {I2}, 1976Sw01) and      \r\n"
        			+ " 65CU3cL 0.83|*10{+-3} eV {I14} (from gw(|q)|G(0){+2}/|G=1.13|*10{+-3} eV       \r\n"
        			+ " 65CU4cL {I19}, 1981Ca10). Other: >0.11|*10{+-3} eV (1972Wh08).                 \r\n"
        			+ " 65CU  L 1624      1 5/2               1.6 PS    5                              \r\n"
        			+ " 65CU cL           g|G(0){+2}/|G=0.24|*10{+-3} eV {I5} (1976Sw01).              \r\n"
        			+ " 65CU  L 1724.9    5 (3/2)             116 FS    15                             \r\n"
        			+ " 65CU cL           g|G(0){+2}/|G=2.9|*10{+-3} eV {I3} (1976Sw01).               \r\n"
        			+ " 65CU cL J         |g(|q) data rule out J=1/2 (1976Sw01, L=1 for p              \r\n"
        			+ " 65CU2cL stripping and pickup assumed).                                         \r\n"
        			+ " 65CU  G 1724.9    5           (D+Q)                                            \r\n"
        			+ " 65CU cG E         from level energy difference.                                \r\n"
        			+ " 65CU cG MR        0.15 {I5} or 1.8 {I8} (1976Sw01). Phase not available.       \r\n"
        			+ " 65CU cG           Multipolarity, |d: from |g(|q) data of 1976Sw01.             \r\n"
        			+ " 65CU  L 2094.32   14                  0.14 PS   GT                         X  ?\r\n"
        			+ " 65CU cL           g|G(0){+2}/|G<0.24|*10{+-3} eV (1976Sw01).                   \r\n"
        			+ " 65CU cL E         from adopted levels.                                         \r\n"
        			/*
        			+ " 65CU  L       2107 5                                                       A   \r\n"
        			+ " 65CU  L 2212.84   15                  0.18 PS   GT                         X  ?\r\n"
        			+ " 65CU cL           g|G(0){+2}/|G<0.24|*10{+-3} eV (1976Sw01).                   \r\n"
        			+ " 65CU cL E         from adopted levels.                                         \r\n"
        			+ " 65CU  L 2278.4    9                   10 FS     GT                         X  ?\r\n"
        			+ " 65CU cL           g|G(0){+2}/|G<0.24|*10{+-3} eV (1976Sw01).                   \r\n"
        			+ " 65CU cL E         from adopted levels.                                         \r\n"
        			+ " 65CU cL           T{-1/2}/(2J+1): adopted |g branching=0.022 {I8}.             \r\n"
        			+ " 65CU  L 2328.6    10(3/2)             33 FS     15                             \r\n"
        			+ " 65CU cL           g|G(0){+2}/|G=6.5|*10{+-3} eV {I7} (1976Sw01).               \r\n"
        			+ " 65CU cL J         |g(|q) data rule out J=1/2                                   \r\n"
        			+ " 65CU2cL (1976Sw01, L=1 for p stripping and pickup assumed).                    \r\n"
        			+ " 65CU  G 2328.6    10           (D+Q)                                           \r\n"
        			+ " 65CU cG E         from level energy difference.                                \r\n"
        			+ " 65CU cG MR        0.15 {I5} or 1.9 {I9} (1976Sw01). Phase not available.       \r\n"
        			+ " 65CU cG           Multipolarity, |d: from |g(|q) data of 1976Sw01.             \r\n"
        			+ " 65CU  L 2862.1    10                  9.7 FS    10                         X   \r\n"
        			+ " 65CU cL           g|G(0){+2}/|G=11.8|*10{+-3} eV {I12} (1976Sw01).             \r\n"
        			+ " 65CU  L 2875.1    10                  5.5 FS    20                         X   \r\n"
        			+ " 65CU cL           g|G(0){+2}/|G=13.5|*10{+-3} eV {I14} (1976Sw01).             \r\n"
        			+ " 65CU  L 2898      2                   5.0 FS    20                         X   \r\n"
        			+ " 65CU cL           g|G(0){+2}/|G=3.0|*10{+-3} eV {I9} (1976Sw01).               \r\n"
        			+ " 65CU  L 3086      2                   36 FS     10                         X   \r\n"
        			+ " 65CU cL           g|G(0){+2}/|G=3.2|*10{+-3} eV {I9} (1976Sw01).               \r\n"
        			+ " 65CU  L 3166.5    10                  5.5 FS    6                          X   \r\n"
        			+ " 65CU cL           g|G(0){+2}/|G=20.7|*10{+-3} eV {I21} (1976Sw01).             \r\n"
        			+ " 65CU  L 3265      2                   32 FS     11                         X   \r\n"
        			+ " 65CU cL           g|G(0){+2}/|G=3.6|*10{+-3} eV {I12} (1976Sw01).              \r\n"
        			+ " 65CU  L 3326.0    10(3/2,5/2)         5.5 FS    6                          X   \r\n"
        			+ " 65CU cL           g|G(0){+2}/|G=20.6|*10{+-3} eV {I21} (1976Sw01).             \r\n"
        			+ " 65CU cL J         from |g(|q) data of 1976Sw01.                                \r\n"
        			+ " 65CU  G 3326.0    10          (D+Q)                                            \r\n"
        			+ " 65CU cG E         from level energy difference.                                \r\n"
        			+ " 65CU cG M         from |g(|q) data of 1976Sw01. |d=0.9 {I6} if J(3326)=3/2.    "
                   */
                    ;
          	String s2=" 65CU    65CU(G,G')         \r\n"
        			+ " 65CU  L 0           3/2-                                                       \r\n"
        			+ " 65CU  L 2094.3       (7/2)-           0.31 PS                              B  ?\r\n"
        			+ " 65CU cL E$2090 {I6} from 1976Sw01\r\n"
        			+ " 65CU cL T$g|G(0){+2}/|G=0.24|*10{+-3} eV (1976Sw01), adopted |G(0)/|G=0.287 \r\n"
        			+ " 65CU2cL {I10}, if J(2094)=7/2       ";
        	
          	String s3=" 65CU    65CU(G,G')         \r\n"
          			+ " 65CU  L 3166.5    10                  5.5 FS    6                          Z   \r\n"
          			+ " 65CU cL $g|G(0){+2}/|G=20.7|*10{+-3} eV {I21} (1976Sw01).             \r\n"
          			+ " 65CU  G 3166.5    2                                                         ";
          		
          	String s4=" 65CU    65CU(G,G')         \r\n"
          			+ " 65CU  L 4099      2                   3.6 FS    5                          Z   \r\n"
          			+ " 65CU cL T$g|G(0){+2}/|G=18.2|*10{+-3} {I24} (1976Sw01).                \r\n"
          			+ " 65CU  G 4099      2                                                         ";
          			
          	String s5=" 65CU  L 6070        (3/2)             0.18 FS   7                              \r\n"
          			+ " 65CU cL E$from 1967Gi15                   \r\n"
          			+ " 65CU cL J$(3/2) from |g(|q) in 1967Gi15.                                \r\n"
          			+ " 65CU cL T$from |G=0.63 eV {I24}, weighted average of 0.67 eV {I35} (1967Gi15)  \r\n"
          			+ " 65CU2cL and 0.59 eV {I33} (1971Be22).                                          \r\n"
          			+ " 65CU cL $E|g-E(res)=9.3 eV {I8} (1967Gi15). ";
        	String s=s5;
        	
            String[] temp=s.split("\n");
            Vector<String> lines=new Vector<String>();
            
            for(int i=0;i<temp.length;i++)
                lines.add(temp[i]);
            
        	frame.ens=new ENSDF();
			frame.ens.setValues(lines);
			
	        for(Level lev:frame.ens.levelsV()) {
	        	Vector<ContRecord> widthsV=frame.findWidthsOfLevel(lev);
	        	
	        	System.out.println(lev.ES()+" size="+widthsV.size());
        		for(ContRecord cr:widthsV)
        			System.out.println("WidthToT12Calculator 1479: lev="+lev.ES()+"  cr: txt="+cr.txt()+" name="+cr.name()+" value="+cr.s()+" unc="+cr.ds());
	        }
	        
	        frame.loadAdoptedCheckBox.setSelected(true);
	        frame.T12RadioButton.setSelected(true);
	        frame.calculate();
    	}catch(Exception e) {
    		
    	}

        

        //System.exit(0);
    	
    }
    
    public static void main(String[] args) {
    	//test();
    	startUI();
    }
    
    private JPanel inputPanel;
    private JPanel controlPanel;
    private JPanel resultPanel;
    private JButton calculateButton;
    private JButton clearMessengerButton;
    private JScrollPane resultScrollPane;
    private JScrollPane inputScrollPane;
    private JTextPane messenger;
    private JTextPane inputTextPane;    
    private JRadioButton T12RadioButton;
    private JButton loadButton;
    private JButton clearInputButton;
    private JRadioButton widthRadioButton;
    private JLabel colNoLabel;
    private JButton loadAdoptedButton;
    private JCheckBox loadAdoptedCheckBox;
    private JLabel topLabel;
}
