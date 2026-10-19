package consistency.ui;
import javax.swing.JTextField;

import java.util.Vector;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.LayoutStyle.ComponentPlacement;

import consistency.main.Run;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class NuclideListFrame  extends javax.swing.JFrame {

	private static final long serialVersionUID = 1L;
	private JTextField textField;
	private String text="";
	private Vector<String> nucNames=new Vector<String>();
	private Run run;
	
	public NuclideListFrame(Run run){
		this.run=run;
		initComponents();

	}
	private void initComponents() {
		setTitle("type in nuclide names");
		textField = new JTextField();
		textField.setColumns(40);
		
		JButton okButton = new JButton("OK");
		okButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				text=textField.getText().trim();
				nucNames.clear();
				String[] names=text.split(",|;|\\s");
				for(int i=0;i<names.length;i++){
					String name=names[i].trim();
					if(!name.isEmpty())
						nucNames.add(name);
				}
				
				for(String s:nucNames)
					run.printMessage(s.toUpperCase());
				
				run.printMessage("Done typing");
			}
		});
		GroupLayout groupLayout = new GroupLayout(getContentPane());
		groupLayout.setHorizontalGroup(
			groupLayout.createParallelGroup(Alignment.TRAILING)
				.addGroup(groupLayout.createSequentialGroup()
					.addGap(1)
					.addComponent(textField, GroupLayout.PREFERRED_SIZE, 293, GroupLayout.PREFERRED_SIZE)
					.addPreferredGap(ComponentPlacement.RELATED, 2, Short.MAX_VALUE)
					.addComponent(okButton)
					.addContainerGap())
		);
		groupLayout.setVerticalGroup(
			groupLayout.createParallelGroup(Alignment.LEADING)
				.addGroup(groupLayout.createSequentialGroup()
					.addGap(0, 0, Short.MAX_VALUE)
					.addGroup(groupLayout.createParallelGroup(Alignment.LEADING)
						.addComponent(okButton, GroupLayout.PREFERRED_SIZE, 30, GroupLayout.PREFERRED_SIZE)
						.addGroup(groupLayout.createSequentialGroup()
							.addGap(1)
							.addComponent(textField, GroupLayout.PREFERRED_SIZE, 30, GroupLayout.PREFERRED_SIZE)))
					.addGap(1))
		);
		getContentPane().setLayout(groupLayout);
		pack();
	}
	public Vector<String> getNucNames(){
		return nucNames;
	}
}
