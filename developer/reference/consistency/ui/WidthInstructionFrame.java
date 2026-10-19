package consistency.ui;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.io.IOException;
import java.net.URISyntaxException;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

public class WidthInstructionFrame extends JFrame{
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private JPanel panel;
    private JScrollPane scrollPane;
    private JTextPane textPane;

    public WidthInstructionFrame() {
        showInstruction(600,700);
    }
    public WidthInstructionFrame(int width,int height) {
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
      
        
        String text="";
                
        text+=    " ### Example input 1:        \r\n"
    			+ " 65CU  L 0           3/2-                                                       \r\n"
    			+ " 65CU  L 2094.3       (7/2)-           0.31 PS                              B  ?\r\n"
    			+ " 65CU cL E$2090 {I6} from 1976Sw01\r\n"
    			+ " 65CU cL T$g|G(0){+2}/|G=0.24|*10{+-3} eV (1976Sw01), adopted |G(0)/|G=0.287 \r\n"
            			+ " 65CU2cL {I10}, if J(2094)=7/2       \r\n";
            	
        text+="\n";
      	text+=    " ### Example input 2:       \r\n"
      			+ " 65CU  L 3166.5    10                  5.5 FS    6                          Z   \r\n"
      			+ " 65CU cL $g|G(0){+2}/|G=20.7|*10{+-3} eV {I21} (1976Sw01).             \r\n"
      			+ " 65CU  G 3166.5    2                                                         \r\n";


        text+="\n";
      	text+=   " ### Example input 3:       \r\n"
      			+ " 65CU  L 6070        (3/2)             0.18 FS   7                              \r\n"
      			+ " 65CU cL E$from 1967Gi15                   \r\n"
      			+ " 65CU cL J$(3/2) from |g(|q) in 1967Gi15.                                \r\n"
      			+ " 65CU cL T$from |G=0.63 eV {I24}, weighted average of 0.67 eV {I35} (1967Gi15)  \r\n"
      			+ " 65CU2cL and 0.59 eV {I33} (1971Be22).                                          \r\n"
      			+ " 65CU cL $E|g-E(res)=9.3 eV {I8} (1967Gi15). \r\n";
        
        
       
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
        //setVisible(true);

    
    }
}
