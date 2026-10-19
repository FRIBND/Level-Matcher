package consistency.ui;

import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JFrame;

import javax.swing.JOptionPane;
import javax.swing.JPanel;

import javax.swing.JScrollPane;

import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.GroupLayout.Alignment;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Style;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

import consistency.base.CheckControl;
import ensdfparser.nds.ensdf.EnsdfUtil;
import ensdfparser.nds.latex.Translator;
import ensdfparser.ui.CustomTextPane;

import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.JLabel;


public class LineWrappingFrame extends JFrame {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    Style messengerStyle=null;

	static private final String newline = "\n";  
    
    private int defaultUncertaintyLimit=CheckControl.errorLimit;
    
    public LineWrappingFrame() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                CheckControl.errorLimit=defaultUncertaintyLimit;
            }
        });
        
        
        initComponents();
        
        
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        messenger.setFont(font);
        inputTextPane.setFont(font);
        
        tobLabel = new JLabel("Usage: paste a long ENSDF comment (with line>80 column) or edit it freely below, then wrap it to 80-column in bottom result area");
        GroupLayout gl_inputPanel = new GroupLayout(inputPanel);
        gl_inputPanel.setHorizontalGroup(
        	gl_inputPanel.createParallelGroup(Alignment.LEADING)
        		.addGroup(gl_inputPanel.createSequentialGroup()
        			.addGroup(gl_inputPanel.createParallelGroup(Alignment.LEADING)
        				.addGroup(gl_inputPanel.createSequentialGroup()
        					.addGap(1)
        					.addComponent(inputScrollPane, GroupLayout.PREFERRED_SIZE, 785, GroupLayout.PREFERRED_SIZE))
        				.addGroup(gl_inputPanel.createSequentialGroup()
        					.addContainerGap()
        					.addComponent(tobLabel, GroupLayout.DEFAULT_SIZE, 765, Short.MAX_VALUE)
        					.addGap(2)))
        			.addGap(1))
        );
        gl_inputPanel.setVerticalGroup(
        	gl_inputPanel.createParallelGroup(Alignment.TRAILING)
        		.addGroup(Alignment.LEADING, gl_inputPanel.createSequentialGroup()
        			.addGap(4)
        			.addComponent(tobLabel, GroupLayout.DEFAULT_SIZE, 17, Short.MAX_VALUE)
        			.addGap(3)
        			.addComponent(inputScrollPane, GroupLayout.DEFAULT_SIZE, 283, Short.MAX_VALUE))
        );
        inputPanel.setLayout(gl_inputPanel);
        
        messengerStyle=StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);

    }

    private void initComponents(){
        inputPanel = new JPanel();
        
        controlPanel = new JPanel();
        
        resultPanel = new JPanel();
        GroupLayout groupLayout = new GroupLayout(getContentPane());
        groupLayout.setHorizontalGroup(
        	groupLayout.createParallelGroup(Alignment.LEADING)
        		.addComponent(resultPanel, GroupLayout.DEFAULT_SIZE, 787, Short.MAX_VALUE)
        		.addGroup(groupLayout.createSequentialGroup()
        			.addContainerGap()
        			.addComponent(controlPanel, GroupLayout.DEFAULT_SIZE, 767, Short.MAX_VALUE)
        			.addContainerGap())
        		.addComponent(inputPanel, GroupLayout.PREFERRED_SIZE, 787, Short.MAX_VALUE)
        );
        groupLayout.setVerticalGroup(
        	groupLayout.createParallelGroup(Alignment.TRAILING)
        		.addGroup(groupLayout.createSequentialGroup()
        			.addGap(1)
        			.addComponent(inputPanel, GroupLayout.PREFERRED_SIZE, 309, GroupLayout.PREFERRED_SIZE)
        			.addPreferredGap(ComponentPlacement.RELATED)
        			.addComponent(controlPanel, GroupLayout.PREFERRED_SIZE, 36, GroupLayout.PREFERRED_SIZE)
        			.addGap(1)
        			.addComponent(resultPanel, GroupLayout.DEFAULT_SIZE, 355, Short.MAX_VALUE)
        			.addContainerGap())
        );
        
        inputScrollPane = new JScrollPane();
        
        inputTextPane = new CustomTextPane();
        inputScrollPane.setViewportView(inputTextPane);
        
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
         
        new ButtonGroup();
        
        
        CheckControl.errorLimit=35;
        
        ///
        messenger = new CustomTextPane();
        messenger.setEditable(false);
        resultScrollPane.setViewportView(messenger);
        resultPanel.setLayout(gl_resultPanel);
        
        wrapLineButton = new JButton("wrap lines");
        wrapLineButton.setToolTipText("wrap input lines to ENSDF 80-column format");
        wrapLineButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                wrapButtonActionPerformed(e);
            }
        });
        
        clearInputButton = new JButton("clear input");
        clearInputButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    clearInput();
                } catch (IOException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
            }
        });
        clearInputButton.setToolTipText("clear text in input window");
        
        copyOutputButton = new JButton("copy output");
        copyOutputButton.addActionListener(new ActionListener() {
        	public void actionPerformed(ActionEvent e) {
        		copyCommentButtonActionPerformed(e);
        	}
        });
        copyOutputButton.setToolTipText("copy output to system clipboard");
        
        clearOutputButton = new JButton("clear output");       
        clearOutputButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    clearOutput();
                } catch (IOException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
            }
        });
        clearOutputButton.setToolTipText("clear text in output window");
        
        GroupLayout gl_controlPanel = new GroupLayout(controlPanel);
        gl_controlPanel.setHorizontalGroup(
            gl_controlPanel.createParallelGroup(Alignment.LEADING)
                .addGroup(gl_controlPanel.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(wrapLineButton, GroupLayout.PREFERRED_SIZE, 92, GroupLayout.PREFERRED_SIZE)
                    .addGap(12)
                    .addComponent(copyOutputButton, GroupLayout.PREFERRED_SIZE, 100, GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(ComponentPlacement.RELATED, 365, Short.MAX_VALUE)
                    .addComponent(clearInputButton, GroupLayout.PREFERRED_SIZE, 92, GroupLayout.PREFERRED_SIZE)
                    .addGap(10)
                    .addComponent(clearOutputButton, GroupLayout.PREFERRED_SIZE, 100, GroupLayout.PREFERRED_SIZE)
                    .addContainerGap())
        );
        gl_controlPanel.setVerticalGroup(
            gl_controlPanel.createParallelGroup(Alignment.LEADING)
                .addGroup(gl_controlPanel.createSequentialGroup()
                    .addGap(4)
                    .addGroup(gl_controlPanel.createParallelGroup(Alignment.LEADING)
                        .addComponent(clearOutputButton, GroupLayout.PREFERRED_SIZE, 28, GroupLayout.PREFERRED_SIZE)
                        .addComponent(clearInputButton)
                        .addComponent(wrapLineButton, GroupLayout.PREFERRED_SIZE, 28, GroupLayout.PREFERRED_SIZE)
                        .addComponent(copyOutputButton, GroupLayout.PREFERRED_SIZE, 28, GroupLayout.PREFERRED_SIZE))
                    .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        controlPanel.setLayout(gl_controlPanel);
        getContentPane().setLayout(groupLayout);    
        
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        pack();
        //setVisible(true);
        
        setResizable(false);
    }
    
    public void clearOutput() throws IOException{
        if(messenger!=null){
            messenger.setText("");
            messenger.setCaretPosition(messenger.getDocument().getLength());
            messenger.update(messenger.getGraphics());
        }
    }
    public void clearInput() throws IOException{
        if(inputTextPane!=null){
        	inputTextPane.setText("");
        	inputTextPane.setCaretPosition(inputTextPane.getDocument().getLength());
        	inputTextPane.update(inputTextPane.getGraphics());
        }
    }
    private void wrapButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Output_ButtonActionPerformed

        try{
            String text=inputTextPane.getText();
            
            //System.out.println(" text="+text);
            
            if(text==null || text.trim().isEmpty())
                return;

            
            String str=EnsdfUtil.wrapENSDFLines(text);
            if(str.length()>0) {
                clearOutput();
                printMessage(str);
            }else {
            	printMessage("No ENSDF lines can be wrapped up from the input text.");
            }
        }catch(Exception e){
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,e);
        }
    }//GEN-LAST:event_Output_ButtonActionPerformed


    private void copyCommentButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_include_ButtonActionPerformed
        
        try{
            
            String text=messenger.getSelectedText();
            if(text==null || text.isEmpty()){
                text=messenger.getText();
                //for(int i=0;i<commentsV.size();i++)
                //  System.out.println(commentsV.get(i));
            }else{
                messenger.select(0,0);
            }
            
            //System.out.println(text);
            
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
        
        LineWrappingFrame frame=new LineWrappingFrame();
        frame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        //frame.setTitle("Java program for averaging values (update "+main.Control.version+")");
        frame.setVisible(true);
        frame.setResizable(false);

    }
    
    private JPanel inputPanel;
    private JPanel controlPanel;
    private JPanel resultPanel;
    private JButton wrapLineButton;
    private JButton clearInputButton;
    private JScrollPane resultScrollPane;
    private JScrollPane inputScrollPane;
    private JTextPane messenger;
    private JTextPane inputTextPane;    
    private JButton copyOutputButton;
    private JButton clearOutputButton;
    private JLabel tobLabel;
}
