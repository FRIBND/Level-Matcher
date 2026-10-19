package consistency.ui;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import java.awt.Font;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.SystemColor;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

import averagingAlgorithms.averagingMethods;
import averagingAlgorithms.averagingReport;
import consistency.base.AverageReport;
import consistency.base.CheckControl;
import consistency.base.DataInComment;
import consistency.base.Util;
import ensdf_datapoint.dataPt;
import ensdfparser.calc.DataPoint;
import ensdfparser.nds.latex.Translator;
import ensdfparser.nds.util.Str;
import ensdfparser.ui.CustomTextPane;
import visualaveraginglibrary.VAveLib_GUI_methods;

import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.SwingConstants;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;

public class AverageCommentValuesFrame extends JFrame{

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    Style messengerStyle=null;

    private ButtonGroup limitButtonGroup;

    private JRadioButton limit25RadioButton;

    private JRadioButton limit35RadioButton;

    private JRadioButton limit99RadioButton;

    private JRadioButton otherLimitRadioButton;

    private JTextField uncLimitTextField;
    static private final String newline = "\n";  
    
    private int defaultUncertaintyLimit=CheckControl.errorLimit;
    
    double[] paramArray = new double[8];//for other methods
    
    public AverageCommentValuesFrame() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                CheckControl.errorLimit=defaultUncertaintyLimit;
            }
        });
        
        
        initComponents();
        
        
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        messenger.setFont(font);
        
        messengerStyle=StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
        StyleConstants.setFontFamily(messengerStyle,Font.MONOSPACED);
        StyleConstants.setFontSize(messengerStyle, 12);
        
        //System.out.println("AvgComFrame 745: style="+StyleConstants.getFontFamily(messengerStyle));

    }

    private void initComponents(){
        inputPanel = new JPanel();
        
        controlPanel = new JPanel();
        
        resultPanel = new JPanel();
        GroupLayout groupLayout = new GroupLayout(getContentPane());
        groupLayout.setHorizontalGroup(
        	groupLayout.createParallelGroup(Alignment.LEADING)
        		.addGroup(groupLayout.createSequentialGroup()
        			.addComponent(controlPanel, GroupLayout.DEFAULT_SIZE, 738, Short.MAX_VALUE)
        			.addGap(1))
        		.addComponent(inputPanel, GroupLayout.PREFERRED_SIZE, 739, Short.MAX_VALUE)
        		.addComponent(resultPanel, GroupLayout.DEFAULT_SIZE, 739, Short.MAX_VALUE)
        );
        groupLayout.setVerticalGroup(
        	groupLayout.createParallelGroup(Alignment.TRAILING)
        		.addGroup(groupLayout.createSequentialGroup()
        			.addComponent(inputPanel, GroupLayout.PREFERRED_SIZE, 169, Short.MAX_VALUE)
        			.addGap(4)
        			.addComponent(controlPanel, GroupLayout.PREFERRED_SIZE, 35, GroupLayout.PREFERRED_SIZE)
        			.addGap(1)
        			.addComponent(resultPanel, GroupLayout.PREFERRED_SIZE, 535, GroupLayout.PREFERRED_SIZE)
        			.addGap(1))
        );
        
        inputScrollPane = new JScrollPane();
        
        inputTextPane = new CustomTextPane();
        inputScrollPane.setViewportView(inputTextPane);
        
        topLabel = new JLabel("Usage: type data values or paste an average comment of data values. Value format: 12 2, 12(2) or 12 {I2} (2000AbCD)");
        topLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        
        qLabel = new JLabel("?");
        qLabel.addMouseListener(new MouseAdapter() {
        	@Override
        	public void mouseClicked(MouseEvent e) {
        		showInstructionFrame(e);
        	}
        });
        qLabel.setToolTipText("click for examples");
        qLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        qLabel.setForeground(new Color(0, 0, 255));
        qLabel.setHorizontalAlignment(SwingConstants.CENTER);
        GroupLayout gl_inputPanel = new GroupLayout(inputPanel);
        gl_inputPanel.setHorizontalGroup(
        	gl_inputPanel.createParallelGroup(Alignment.LEADING)
        		.addGroup(gl_inputPanel.createSequentialGroup()
        			.addGroup(gl_inputPanel.createParallelGroup(Alignment.LEADING)
        				.addGroup(gl_inputPanel.createSequentialGroup()
        					.addGap(1)
        					.addComponent(inputScrollPane, GroupLayout.PREFERRED_SIZE, 737, GroupLayout.PREFERRED_SIZE))
        				.addGroup(Alignment.TRAILING, gl_inputPanel.createSequentialGroup()
        					.addContainerGap()
        					.addComponent(topLabel, GroupLayout.PREFERRED_SIZE, 702, GroupLayout.PREFERRED_SIZE)
        					.addPreferredGap(ComponentPlacement.RELATED, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        					.addComponent(qLabel, GroupLayout.PREFERRED_SIZE, 19, GroupLayout.PREFERRED_SIZE)
        					.addGap(20)))
        			.addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        gl_inputPanel.setVerticalGroup(
        	gl_inputPanel.createParallelGroup(Alignment.LEADING)
        		.addGroup(gl_inputPanel.createSequentialGroup()
        			.addGap(7)
        			.addGroup(gl_inputPanel.createParallelGroup(Alignment.BASELINE)
        				.addComponent(topLabel)
        				.addComponent(qLabel))
        			.addPreferredGap(ComponentPlacement.RELATED)
        			.addComponent(inputScrollPane, GroupLayout.PREFERRED_SIZE, 144, GroupLayout.PREFERRED_SIZE))
        );
        inputPanel.setLayout(gl_inputPanel);
        
        resultScrollPane = new JScrollPane();
        GroupLayout gl_resultPanel = new GroupLayout(resultPanel);
        gl_resultPanel.setHorizontalGroup(
            gl_resultPanel.createParallelGroup(Alignment.TRAILING)
                .addGroup(gl_resultPanel.createSequentialGroup()
                    .addGap(1)
                    .addComponent(resultScrollPane, GroupLayout.DEFAULT_SIZE, 660, Short.MAX_VALUE)
                    .addGap(1))
        );
        gl_resultPanel.setVerticalGroup(
            gl_resultPanel.createParallelGroup(Alignment.LEADING)
                .addGroup(gl_resultPanel.createSequentialGroup()
                    .addGap(1)
                    .addComponent(resultScrollPane, GroupLayout.DEFAULT_SIZE, 391, Short.MAX_VALUE)
                    .addGap(1))
        );
        
        
        ///
        
        JPanel uncertaintySettingPanel = new JPanel();
        uncertaintySettingPanel.setForeground(SystemColor.windowText);
        uncertaintySettingPanel.setBackground(UIManager.getColor("ArrowButton.background"));
         
        limitButtonGroup = new ButtonGroup();
        
        JLabel uncertaintylimitLabel = new JLabel("Uncertainty Limit");
        
        limit25RadioButton = new JRadioButton("25");
        limit25RadioButton.setToolTipText("default ENSDF uncertainty limit");

        limit25RadioButton.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if(((JRadioButton)e.getSource()).isSelected()){
                    CheckControl.errorLimit=25;
                }
            }
        });
        

        
        limitButtonGroup.add(limit25RadioButton);
        
        limit35RadioButton = new JRadioButton("35");
        limit35RadioButton.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if(((JRadioButton)e.getSource()).isSelected()){
                    CheckControl.errorLimit=35;
                }
            }
        });
        
        
        CheckControl.errorLimit=35;
        limit35RadioButton.setSelected(true);
        
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
        
        averageButton = new JButton("average");
        /*
        averageButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                averageButtonActionPerformed(e);
            }
        });
        */
        averageButton.addMouseListener(new MouseAdapter(){
        	public void mouseClicked(MouseEvent e){
        		averageButtonMouseClicked(e);
        	}
        });
        
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
        
        GroupLayout gl_controlPanel = new GroupLayout(controlPanel);
        gl_controlPanel.setHorizontalGroup(
            gl_controlPanel.createParallelGroup(Alignment.LEADING)
                .addGroup(gl_controlPanel.createSequentialGroup()
                    .addGap(37)
                    .addComponent(averageButton)
                    .addPreferredGap(ComponentPlacement.UNRELATED)
                    .addComponent(clearButton)
                    .addPreferredGap(ComponentPlacement.RELATED, 29, Short.MAX_VALUE)
                    .addComponent(uncertaintySettingPanel, GroupLayout.PREFERRED_SIZE, 547, GroupLayout.PREFERRED_SIZE)
                    .addContainerGap())
        );
        gl_controlPanel.setVerticalGroup(
            gl_controlPanel.createParallelGroup(Alignment.LEADING)
                .addGroup(gl_controlPanel.createSequentialGroup()
                    .addGap(4)
                    .addGroup(gl_controlPanel.createParallelGroup(Alignment.BASELINE)
                        .addComponent(averageButton)
                        .addComponent(clearButton))
                    .addContainerGap())
                .addGroup(gl_controlPanel.createSequentialGroup()
                    .addGap(1)
                    .addComponent(uncertaintySettingPanel, GroupLayout.PREFERRED_SIZE, 32, Short.MAX_VALUE)
                    .addGap(2))
        );
        GroupLayout gl_uncertaintySettingPanel = new GroupLayout(uncertaintySettingPanel);
        gl_uncertaintySettingPanel.setHorizontalGroup(
            gl_uncertaintySettingPanel.createParallelGroup(Alignment.LEADING)
                .addGroup(gl_uncertaintySettingPanel.createSequentialGroup()
                    .addGap(69)
                    .addComponent(uncertaintylimitLabel)
                    .addGap(5)
                    .addComponent(limit25RadioButton)
                    .addGap(5)
                    .addComponent(limit35RadioButton)
                    .addGap(5)
                    .addComponent(limit99RadioButton)
                    .addGap(5)
                    .addComponent(otherLimitRadioButton)
                    .addGap(5)
                    .addComponent(uncLimitTextField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
        );
        gl_uncertaintySettingPanel.setVerticalGroup(
            gl_uncertaintySettingPanel.createParallelGroup(Alignment.LEADING)
                .addGroup(gl_uncertaintySettingPanel.createSequentialGroup()
                    .addGap(11)
                    .addComponent(uncertaintylimitLabel))
                .addGroup(gl_uncertaintySettingPanel.createSequentialGroup()
                    .addGap(9)
                    .addComponent(limit25RadioButton))
                .addGroup(gl_uncertaintySettingPanel.createSequentialGroup()
                    .addGap(9)
                    .addComponent(limit35RadioButton))
                .addGroup(gl_uncertaintySettingPanel.createSequentialGroup()
                    .addGap(9)
                    .addComponent(limit99RadioButton))
                .addGroup(gl_uncertaintySettingPanel.createSequentialGroup()
                    .addGap(9)
                    .addComponent(otherLimitRadioButton))
                .addGroup(gl_uncertaintySettingPanel.createSequentialGroup()
                    .addGap(5)
                    .addComponent(uncLimitTextField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
        );
        uncertaintySettingPanel.setLayout(gl_uncertaintySettingPanel);
        controlPanel.setLayout(gl_controlPanel);
        getContentPane().setLayout(groupLayout);    
        

        pack();
        //setVisible(true);
        
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
    }
    
    private void uncLimitFieldKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_pathFieldKeyReleased
        try{
            CheckControl.errorLimit=Integer.parseInt(uncLimitTextField.getText());
        }catch(NumberFormatException e){
            JOptionPane.showMessageDialog(this, "Error: wrong input for error limit! Please re-type a integer <100.");
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
                JOptionPane.showMessageDialog(this, "Error: wrong input for error limit! Please re-type a integer <100.");
            }
        }else{
            uncLimitTextField.setEnabled(false);
        }
    }
    
    public void clear() throws IOException{
        if(messenger!=null){
            messenger.setText("");
            messenger.setCaretPosition(messenger.getDocument().getLength());
            messenger.update(messenger.getGraphics());
        }
    }

    @SuppressWarnings("unused")
	private void averageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Output_ButtonActionPerformed

        try{
            String text=inputTextPane.getText();
            
            //System.out.println(" text="+text);
            
            if(text==null || text.trim().isEmpty())
                return;
            
            DataInComment data=Util.parseDatapointsInComments(text);
            Vector<DataPoint> dpsV=data.dpsV();
            
            String lineType="cX";
            String entryName="X";
            String NUCID="xxxxx";
            if(!data.recordType().isEmpty())
                lineType="c"+data.recordType();
            if(!data.NUCID().isEmpty()) 
                NUCID=data.NUCID();
            if(!data.entryType().isEmpty())
                entryName=data.entryType();
            
            String prefix=Str.makeENSDFLinePrefix(NUCID, lineType);
            
            //System.out.println(" prefix="+prefix+" lineType="+lineType+"*");
            
            AverageReport ar=new AverageReport(dpsV,entryName,prefix,0);
            String str=ar.getReport();
            
            
            //System.out.println(" size="+dpsV.size()+" str="+str);
            
            //average report
            if(str.length()>0){  
                clear();

                
                //report from other methods
                resetParams();
                String s=printReportOfOtherMethods(dpsV);
                if(s.length()>0)
                	str+=s;
                
                
                printMessage(str);
            }else {
            	printMessage("No data values can be extracted from the input text.");
            }
        }catch(Exception e){
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,e);
        }
    }//GEN-LAST:event_Output_ButtonActionPerformed

	private void averageButtonMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_Output_ButtonActionPerformed

        try{
            String text=inputTextPane.getText();
            
            //System.out.println(" text="+text);
            
            if(text==null || text.trim().isEmpty())
                return;
            
            boolean toCalculateOthers=false;
    		if(evt.getModifiers()==InputEvent.BUTTON1_MASK){//BUTTON1-left, BUTTON2-middle, BUTTON3-right
    			toCalculateOthers=false;
    		}else if(evt.getModifiers()==InputEvent.BUTTON3_MASK){
    			toCalculateOthers=true;
    		}
    		
            DataInComment data=Util.parseDatapointsInComments(text);
            Vector<DataPoint> dpsV=data.dpsV();
            
            String lineType="cX";
            String entryName="X";
            String NUCID="xxxxx";
            if(!data.recordType().isEmpty())
                lineType="c"+data.recordType();
            if(!data.NUCID().isEmpty()) 
                NUCID=data.NUCID();
            if(!data.entryType().isEmpty())
                entryName=data.entryType();
            
            String prefix=Str.makeENSDFLinePrefix(NUCID, lineType);
            
            //System.out.println(" prefix="+prefix+" lineType="+lineType+"*");
            
            AverageReport ar=new AverageReport(dpsV,entryName,prefix,0);
            String str=ar.getReport();
            
            //System.out.println(" size="+dpsV.size()+" str="+str);
            
            //average report
            if(str.length()>0){  
                clear();

                
                //report from other methods
                if(toCalculateOthers) {
                    resetParams();
                    String s=printReportOfOtherMethods(dpsV);
                    if(s.length()>0)
                    	str+=s;
                    
                    
                }
                printMessage(str);
            }else {
            	printMessage("No data values can be extracted from the input text.");
            }
        }catch(Exception e){
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,e);
        }
    }
	
    private void resetParams() {
        paramArray = new double[8];
        paramArray[0] = 95d; //critical chi^2 confidence
        paramArray[1] = 50d; //LWM max weight
        paramArray[2] = 0; //LWM outlier method
        paramArray[3] = 99d; //NRM outlier conf
        paramArray[4] = 1; //RT outlier confidence (0 = 95, 1 = 99, 2 = 99.99)
        paramArray[5] = 800000; //number of bootstrap sub-sample medians
        paramArray[6] = 0.010d; //mandel-pauli numeric tolerance
        paramArray[7] = 1000; //mandel-pauli max iterations
    }
    
    public void setCriticalChi2CL(double cl) {paramArray[0]=cl;}
    public void setLWMMaxWeight(double weightLimit) {paramArray[1]=weightLimit;}
    public void setLWMOutlierMethod(int methodIndex) {paramArray[2]=methodIndex;}
    public void setNRMOutlierCL(double cl) {paramArray[3]=cl;}
    public void setRTOutlierCL(double cl) {paramArray[4]=cl;}
    public void setBootstrapNumber(int nb) {paramArray[5]=nb;}
    public void setMPTolerance(double mp) {paramArray[6]=mp;}
    public void setMPIterationNum(int num) {paramArray[7]=num;}
    
    public double getCriticalChi2CL() {return paramArray[0];}
    public double getLWMMaxWeight() {return paramArray[1];}
    public double getLWMOutlierMethod() {return paramArray[2];}
    public double getNRMOutlierCL() {return paramArray[3];}
    public double getRTOutlierCL() {return paramArray[4];}
    public double getBootstrapNumber() {return paramArray[5];}
    public double getMPTolerance() {return paramArray[6];}
    public double getMPIterationNum() {return paramArray[7];}
  
    @SuppressWarnings("unused")
	public averagingReport getReportOfOtherMethod(Vector<DataPoint> dpsV, String methodName) {
		String data="";
		averagingReport rpt=null;
        dataPt[] dataset;
        dataPt result;
        
        double weightLimit;
        int outlierMethod;
        double criticalChi2CL,nrmCL;
        int outlierConfidenceLevel;
        int NUM_MEDIANS;
        int maxIt;
        double precision;
        
        precision = paramArray[6];
        maxIt = (int) paramArray[7];       
        NUM_MEDIANS = (int) paramArray[5];       
        outlierConfidenceLevel = 1 + (int) paramArray[4];      
        weightLimit = paramArray[1]/100d;
        outlierMethod = (int) paramArray[2];
        criticalChi2CL = paramArray[0];
        nrmCL=paramArray[3]/100;
        
        try {
            for(DataPoint dp:dpsV) {
            	String s=dp.s();
            	String ds=dp.ds();
            	if(s.isEmpty() || !Str.isNumeric(s))
            		continue;
            	if(!ds.isEmpty() && !Str.isNumeric(ds) && !ds.contains("+"))
            		continue;
            	
            	if(ds.isEmpty())
            		ds="0";
            	
            	data+=s+"("+ds+")\n";
            }
            
            dataset = VAveLib_GUI_methods.createDataset(data);
            
            rpt=new averagingReport();;

            methodName=methodName.toUpperCase();
            
            if(methodName.startsWith("WEIGH") || methodName.equals("WM")){
            	result = averagingMethods.weightedAverage(dataset, rpt);
            }else if(methodName.startsWith("UNWEI") || methodName.equals("UWM")) {
            	result = averagingMethods.unweightedAverage(dataset, rpt);
            }else if(methodName.startsWith("LIMITATION") || methodName.equals("LWM")) {
            	result = averagingMethods.lwm(dataset, weightLimit, outlierMethod, criticalChi2CL, rpt);
            }else if(methodName.startsWith("NORMALIZED") || methodName.equals("NRM")) {
            	result = averagingMethods.nrm(dataset, nrmCL, rpt);
            }else if(methodName.startsWith("RAJEVAL") || methodName.equals("RT")) {
            	result = averagingMethods.rt(dataset, outlierConfidenceLevel, rpt);
            }else if(methodName.startsWith("EXPECTED") || methodName.equals("EVM")) {
            	result = averagingMethods.evm(dataset, rpt);
            }else if(methodName.startsWith("BOOTSTRAP") || methodName.equals("BS")) {
            	result = averagingMethods.bootstrap(dataset, NUM_MEDIANS, rpt);
            }else if(methodName.startsWith("MANDEL") || methodName.equals("MP")) {
            	result = averagingMethods.mp(dataset, precision, maxIt, rpt);
            }else
            	return null;
            
    		
        }catch(Exception e) {

        }

		return rpt;
    }
    
    private String printFullReport(List<String>list) {
    	String out="";
    	try {
    		for(String line:list)
    			out+=line+"\n";
    		
    	}catch(Exception e) {
    		
    	}
    	
    	return out;
    }
        
    public String printReportOfOtherMethod(Vector<DataPoint> dpsV, String methodName) {
    	averagingReport rpt=getReportOfOtherMethod(dpsV, methodName);
    	return printFullReport(rpt.fullReport());
    }
    
	public String printReportOfOtherMethods(Vector<DataPoint> dpsV) {
		String out="";
		out+="\n *** Averaging results using V.AveLib by M.Birch and B.Singh ***\n\n";
		out+=printReportOfOtherMethod(dpsV,"LWM")+"\n";
		out+=printReportOfOtherMethod(dpsV,"NRM")+"\n";
		out+=printReportOfOtherMethod(dpsV,"RT")+"\n";
		out+=printReportOfOtherMethod(dpsV,"EVM")+"\n";
		out+=printReportOfOtherMethod(dpsV,"BS")+"\n";
		out+=printReportOfOtherMethod(dpsV,"MP")+"\n";
		
		return out;
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
    
    public static void main(String[] args) {
        

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
        
        AverageCommentValuesFrame frame=new AverageCommentValuesFrame();
        frame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        //frame.setTitle("Java program for averaging values (update "+main.Control.version+")");
        frame.setVisible(true);
        frame.setResizable(false);

    }
    protected void showInstructionFrame(MouseEvent e) {
        
	    int width=600;
	    int height=795;

        try {
            String title="Example inputs for the averaging tool";
            InstructionFrame frame=null;
            Frame[] frames=java.awt.Frame.getFrames();
            for(int i=0;i<frames.length;i++) {
                if(frames[i].getTitle().equals(title))
                    frame=(InstructionFrame)frames[i];
                
                //System.out.println(frames[i].getName()+"  "+frames[i].getTitle());
            }
            
            if(frame==null) {
                frame=new InstructionFrame(width,height);
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
    
    public class InstructionFrame extends JFrame{
        /**
         * 
         */
        private static final long serialVersionUID = 1L;
        private JPanel panel;
        private JScrollPane scrollPane;
        private JTextPane textPane;

        public InstructionFrame() {
            showInstruction(600,700);
        }
        public InstructionFrame(int width,int height) {
            showInstruction(width,height);
        }
        
        @SuppressWarnings("unused")
		protected void showInstruction(int width,int height) {
            final Container cp=getContentPane();
            
            cp.setLayout(new BorderLayout());
            setPreferredSize(new Dimension(width,height));
            
            
            panel=new JPanel();
            panel.setPreferredSize(new Dimension(width,height));
            panel.setBorder(BorderFactory.createEmptyBorder());
            panel.setLayout(new BorderLayout(0, 0));
            
            textPane=new JTextPane();
            textPane.addHyperlinkListener(new HyperlinkListener() {
                public void hyperlinkUpdate(HyperlinkEvent e) {
                    if (e.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
                        if (Desktop.isDesktopSupported()) {
                            try {
                              Desktop.getDesktop().browse(e.getURL().toURI());
                            } catch (IOException | URISyntaxException e1) {
                              e1.printStackTrace();
                            }                     
                        }
                    }
                }
            });

            textPane.setEditable(false);
            textPane.setEditorKit(JTextPane.createEditorKitForContentType("text/html"));
            textPane.setMargin(new Insets(1, 1, 1, 1));
            //textPane.setBorder(BorderFactory.createEmptyBorder());
            //textPane.setSize(new Dimension(300, 200));

            //DefaultCaret caret = (DefaultCaret)textArea.getCaret();
            //caret.setUpdatePolicy(DefaultCaret.OUT_TOP);
            //caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
            //caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
            
            //PrintStream printStream = new PrintStream(new CustomOutputStream(textArea));
            //System.setOut(printStream);
            scrollPane = new JScrollPane();
            //scrollPane.setViewportBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null), "message", TitledBorder.LEADING, TitledBorder.TOP, null, new Color(0, 0, 0)));
            //scrollPane.setBorder(BorderFactory.createEmptyBorder());
            scrollPane.setBorder(BorderFactory.createEtchedBorder(javax.swing.border.EtchedBorder.LOWERED, null, null));
            scrollPane.setPreferredSize(new Dimension(width,height));
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.add(textPane);
            scrollPane.setViewportView(textPane);
            scrollPane.setVisible(true);
          
            
            String text="Note: input text is not required to be in ENSDF format and any free text is\n"
            		+   "      acceptable as long as data points are separated by \",\"\n\n";
                    
            text+=    " ### Example input 1 (ENSDF average comment):        \r\n"
        			+ " 65ZN cL T$unweighted average of 244.0 d {I2} (1972De24), 244.3 d {I4}          \n"
        			+ " 65ZN2cL (1974Cr05), 243.75 d {I12} (1975La16),244.164 d {I99} (2002Un02),      \n"
        			+ " 65ZN3cL 244.15 d {I10} (2003Lu06), 243.8 d {I3} (2004Va02), 243.66 d {I9}      \n"
        			+ " 65ZN4cL (2004Sc04), and 243.62 d {I9} (2006Ko31). Others: 210 d {I30}          \n"
        			+ " 65ZN5cL (1939Sa02), 245.0 d {I8} (1953To17), 243.5 d {I8} (1957Ge07), 246.4 d  \n"
        			+ " 65ZN6cL {I22} (1957Wr37), 245.7 d {I11} (1960Ea02), 243.1 d {I7}, 244.12 d     \n"
        			+ " 65ZN7cL {I12} and 242.78 d {I19} (1965An07), 243.7 d {I4} (1968An01), 243 d    \n"
        			+ " 65ZN8cL {I4} (1968Ha47), 244.52 d {I7} (1973Vi13), 244.16 d {I10} (1992Un01).  \n"
        			+ " 65ZN9cL The weighted average is 243.87 {I9} with a reduced |h{+2}=4.64. Note   \n"
        			+ " 65ZNacL that results in 1965An07, 1968An01, 1982HoZJ, and 1992Un01 is          \n"
        			+ " 65ZNbcL superseded by 2002Un02, and 1983Wa26 is superseded by 2004Sc04.        \n"
        			+ " 65ZNccL Uncertainty in 244.52 d {I7} from 1973Vi13 seems unrealistically small \n"
        			+ " 65ZNdcL making its value discrepant with most of other values, and therefore it\n"
        			+ " 65ZNecL is not included in the average.  \r\n";
                	
            text+="\n";
          	text+=    " ### Example input 2 (asymmetric uncertainy):       \r\n"
          			+ " 65CU cL T$weighted average of 18 fs {I+8-6} (2000Ko51) and 24 fs {I+9-6}       \n"
          			+ " 65CU2cL (1987DoZX)    \r\n";


            text+="\n";
          	text+=   " ### Example input 3: (free text with ENSDF-format uncertainties) \r\n"
          			+ " -0.24 {I13} (1964El03), -0.22 {I6}  (1964Ro10), -0.19 {I6} (1966Gu10) and -0.28 {I5} (1972Ro21)\r\n";
            
            
            text+="\n";
          	text+=   " ### Example input 4 (mixed-style uncertainty format):       \r\n"
          			+ " -0.24 13, -0.22 6, -0.19 {I6} (1966Gu10), 0.28 {I5} (1972Ro21)\r\n";
          	
            text+="\n";
          	text+=   " ### Example input 5 (number input):       \r\n"
          			+ " -0.24 13, -0.22 6, -0.19 6 , 0.28 5\r\n";
          	
            StyledDocument doc=textPane.getStyledDocument();
            
            SimpleAttributeSet style=new SimpleAttributeSet(); 
            StyleConstants.setFontFamily(style,Font.MONOSPACED);
            //StyleConstants.setForeground(style, new Color(65,105,225));
            //StyleConstants.setBold(style, true);
            StyleConstants.setFontSize(style, 13);
            //int fontSize=StyleConstants.getFontSize(style);
            
            SimpleAttributeSet refStyle=new SimpleAttributeSet(); 
            StyleConstants.setFontFamily(refStyle,Font.MONOSPACED);
            //StyleConstants.setForeground(refStyle, new Color(65,105,225));
            //StyleConstants.setBold(refStyle, true);
            StyleConstants.setFontSize(refStyle, 12);
            
            try {
                doc.insertString(doc.getLength(),text,style);
                
                HTMLEditorKit editorKit = (HTMLEditorKit)textPane.getEditorKit();
                HTMLDocument htmldoc = (HTMLDocument)textPane.getDocument();
                
                doc.insertString(doc.getLength(),"\n\n\n",refStyle);
                
                //textPane.setText(text+refText);//the whole text have to be in HTML format
            }catch(BadLocationException ex) {
                ex.printStackTrace();
            }catch (Exception ex) {
                // TODO Auto-generated catch block
                ex.printStackTrace();
            }
            
            //THIS DOES NOT AUTOMATICALLY SCROLL TEXT 
            //DefaultCaret caret = (DefaultCaret)textPane.getCaret();
            //caret.setUpdatePolicy(DefaultCaret.OUT_BOTTOM);
            //caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);    
            //caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
            textPane.setCaretPosition(0);
      
            panel.add(scrollPane);     
            cp.add(panel,BorderLayout.NORTH);
            
            //frame.setUndecorated(true);//remove title bar
            
            //setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            
            pack();
            setResizable(false);
            setVisible(true);

        
        }
    }
    
    private JPanel inputPanel;
    private JPanel controlPanel;
    private JPanel resultPanel;
    private JButton averageButton;
    private JButton clearButton;
    private JScrollPane resultScrollPane;
    private JScrollPane inputScrollPane;
    private JTextPane messenger;
    private JTextPane inputTextPane;    
    private JLabel topLabel;
    private JLabel qLabel;
}
