package cs352.RUBTClient.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.MatteBorder;

/** The MainPanel is a JPanel containing the elements of the user interface
 *  and their appearance attributes
 *  
 * @author Yuriy Garnaev
 *
 */
public class MainPanel extends JPanel {
	public JButton btnStart;
	public JButton btnPause;
	public JButton btnResume;
	public JButton btnExit;
	public JProgressBar progress;
	public JLabel fileName;
	public JLabel fileLabel;
	private JLabel thor;
	public JButton btnSave;
	public JLabel saveFileName;
	
	public MainPanel(){
		setForeground(Color.WHITE);
		setBackground(Color.BLACK);
		setLayout(null);
		//setMinimumSize(new Dimension(600, 550));
		//setPreferredSize(new Dimension(1000, 600));
		
		btnStart = new JButton("Start");
		//btnStart.setPreferredSize(new Dimension(90, 24));
		btnStart.setBorder(new MatteBorder(1, 1, 1, 1, (Color) Color.WHITE));
		btnStart.setContentAreaFilled(false);
		btnStart.setForeground(Color.WHITE);
		btnStart.setBounds(85,155,90,20);
		add(btnStart);
		
		btnPause = new JButton("Pause");
		//btnPause.setPreferredSize(new Dimension(90, 24));
		btnPause.setBorder(new MatteBorder(1, 1, 1, 1, (Color) Color.WHITE));
		btnPause.setContentAreaFilled(false);
		btnPause.setForeground(Color.WHITE);
		btnPause.setBounds(185,155,90,20);
		add(btnPause);
		
		btnResume = new JButton("Resume");
		//btnResume.setPreferredSize(new Dimension(90, 24));
		btnResume.setBorder(new MatteBorder(1, 1, 1, 1, (Color) Color.WHITE));
		btnResume.setContentAreaFilled(false);
		btnResume.setForeground(Color.WHITE);
		btnResume.setBounds(285,155,90,20);
		add(btnResume);
		
		btnExit = new JButton("Exit");
		//btnExit.setPreferredSize(new Dimension(90, 24));
		btnExit.setBorder(new MatteBorder(1, 1, 1, 1, (Color) Color.WHITE));
		btnExit.setContentAreaFilled(false);
		btnExit.setForeground(Color.WHITE);
		btnExit.setBounds(385,155,90,20);
		add(btnExit);
		
		progress = new JProgressBar();
		progress.setBounds(25, 135, 515, 15);
		progress.setValue(0);
		progress.setStringPainted(true);
		progress.setString(progress.getValue()+"%");
		add(progress);
		
		fileName = new JLabel("");
		fileName.setBounds(75,75,465,24);
		//fileName.setPreferredSize(new Dimension(150,24));
		fileName.setBorder(new MatteBorder(1, 0, 1, 1, (Color) Color.WHITE));
		fileName.setForeground(Color.WHITE);
		fileName.setHorizontalAlignment(SwingConstants.CENTER );
		add(fileName);
		
		fileLabel = new JLabel("File:");
		//fileLabel.setPreferredSize(new Dimension(50, 24));
		fileLabel.setBorder(new MatteBorder(1, 1, 1, 1, (Color) Color.WHITE));
		fileLabel.setForeground(Color.WHITE);
		fileLabel.setBounds(25,75,50,24);
		fileLabel.setHorizontalAlignment(SwingConstants.CENTER );
		add(fileLabel);
		
		saveFileName = new JLabel("");
		saveFileName.setBounds(175,105,365,24);
		//fileName.setPreferredSize(new Dimension(150,24));
		saveFileName.setBorder(new MatteBorder(1, 0, 1, 1, (Color) Color.WHITE));
		saveFileName.setForeground(Color.WHITE);
		saveFileName.setHorizontalAlignment(SwingConstants.CENTER );
		add(saveFileName);
		
		btnSave = new JButton("Save as(click to change):");
		//fileLabel.setPreferredSize(new Dimension(50, 24));
		btnSave.setBorder(new MatteBorder(1, 1, 1, 1, (Color) Color.WHITE));
		btnSave.setContentAreaFilled(false);
		btnSave.setForeground(Color.WHITE);
		btnSave.setBounds(25,105,150,24);
		//btnSave.setHorizontalAlignment(SwingConstants.CENTER );
		add(btnSave);
		
		
		ImageIcon icon = new ImageIcon("data/thor2.jpg"); 
		thor = new JLabel(icon);
		thor.setBounds(560,0,234,400);
		add(thor);
		
		ImageIcon logo = new ImageIcon("data/logo.jpg"); 
		JLabel lblLogo = new JLabel(logo);
		lblLogo.setBounds(10,0,550,74);
		add(lblLogo);
		
		
	}
}
