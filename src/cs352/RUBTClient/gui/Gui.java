package cs352.RUBTClient.gui;

import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import cs352.RUBTClient.main.RUBTClient;

/**The gui class extends JFrame and contains the main frame that the user interface is built on
 * 
 * @author Yuriy Garnaev
 *
 */
public class Gui extends JFrame {
	static MainPanel main;

	
	public Gui(String title){
		super(title);
		
		
		setBounds(100, 100, 800, 405);
		setResizable(false);
		setVisible(true);
		
		
		//Create the menu bar and add menu items
		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);	
		
		JMenu menu = new JMenu("File");
		menuBar.add(menu);
		
		JMenuItem mnOpen = new JMenuItem("Open");
		menu.add(mnOpen);
		
		JMenuItem mnHelp = new JMenuItem("Help");
		menu.add(mnHelp);
		
		JMenuItem mnBattle = new JMenuItem("To Battle!");
		menu.add(mnBattle);
		
		JMenuItem mnExit = new JMenuItem("Exit");
		menu.add(mnExit);
		
		

		
		//Create the MainPanel panel which contains the majority of the elements
		main = new MainPanel();
		setContentPane(main);
		main.setBorder(new EmptyBorder(5, 10, 5, 5));
		
		//Enables/disables buttons as needed on start
		main.setVisible(true);
		main.btnStart.setEnabled(false);
		main.btnPause.setEnabled(false);
		main.btnResume.setEnabled(false);
		main.btnExit.setEnabled(true);
		
		final JTextArea textArea = new JTextArea();
		textArea.setBounds(10, 11, 0, 0);
		textArea.setPreferredSize(new Dimension(100,20));
		textArea.setVisible(true);
		main.add(textArea);
		
		
		
		//Create listeners for the buttons and menu items
		ButtonListener b = new ButtonListener(this);
		
		main.btnStart.addActionListener(b);
		main.btnPause.addActionListener(b);
		main.btnResume.addActionListener(b);
		main.btnExit.addActionListener(b);
		

		//Listener for the File-> Open menu option
		mnOpen.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				JFileChooser chooser= new JFileChooser(System.getProperty("user.dir"));

				int choice = chooser.showOpenDialog(textArea);
				if (choice != JFileChooser.APPROVE_OPTION) return;

				File file = chooser.getSelectedFile();

				main.fileName.setText(file.getName());
				if (RUBTClient.validateTorrentName(main.fileName.getText().trim())){
					main.btnStart.setEnabled(true);
					main.saveFileName.setText(RUBTClient.saveFileName);
				}else{
					main.btnStart.setEnabled(false);
					main.saveFileName.setText("");
				}
			} 
		});
		
		//Listener for the Exit button in the Menu
		mnExit.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				guiShutdown();
			}		
		});
		
		//Listener for To Battle! 
		mnBattle.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				try {File f = new File("aa.wav");
		        Clip clip = AudioSystem.getClip();
		        AudioInputStream ais = AudioSystem.getAudioInputStream(f);
		        clip.open(ais);
		        clip.loop(Clip.LOOP_CONTINUOUSLY);
		        String start = "<html>";
		        start+= "<p>There comes Fenris's twin</p>";
		        start+= "<p>His jaws are open wide</p>";
		        start+= "<p>The serpent rises from the waves</p>";
		        start+= "<p>Jormungandr twists and turns</p>";
		        start+= "<p>Mighty in his wrath</p>";
		        start+= "<p>The eyes are full of primal hate</p>";
		        start+= "<p>Thor! Odin's son</p>";
		        start+= "<p>Protector of mankind</p>";
		        start+= "<p>Ride to meet your fate</p>";
		        start+= "<p>Your destiny awaits</p>";
		        start+= "<p>Thor! Hl�dyn's son</p>";
		        start+= "<p>Protector of mankind</p>";
		        start+= "<p>Ride to meet your fate</p>";
		        start+= "<p>Ragnar�k awaits</p>";
		        start+= "<p>Vingtor rise to face</p>";
		        start+= "<p>The snake with hammer high</p>";
		        start+= "<p>At the edge of the world</p>";
		        start+= "<p>Bolts of lightning fills the air</p>";
		        start+= "<p>as Mj�lnir does its work</p>";
		        start+= "<p>the dreadful serpent roars in pain</p>";
		        start+= "<p>Thor! Odin's son</p>";
		        start+= "<p>Protector of mankind</p>";
		        start+= "<p>Ride to meet your fate</p>";
		        start+= "<p>Your destiny awaits</p>";
		        start+= "<p>Thor! Hl�dyn's son</p>";
		        start+= "<p>Protector of mankind</p>";
		        start+= "<p>Ride to meet your fate</p>";
		        start+= "<p>Ragnar�k awaits</p>";
		        start+= "<p>Mighty Thor grips the snake</p>";
		        start+= "<p>Firmly by its tongue</p>";
		        start+= "<p>Lifts his hammer high to strike</p>";
		        start+= "<p>Soon his work is done</p>";
		        start+= "<p>Vingtor sends the giant snake</p>";
		        start+= "<p>Bleeding to the depth</p>";
		        start+= "<p>Twilight of the thunder god</p>";
		        start+= "<p>Ragnar�k awaits</p>";
		        start+= "<p>Twilight of the thunder god</p>";
		        start+= "<p>Twilight of the thunder god</p>";
		        start+= "<p>Twilight of the thunder god</p>";
		        start+= "<p>Twilight of the thunder god</p>";
		        start+= "<p>Thor! Odin's son</p>";
		        start+= "<p>Protector of mankind</p>";
		        start+= "<p>Ride to meet your fate</p>";
		        start+= "<p>Your destiny awaits</p>";
		        start+= "<p>Thor! Hl�dyn's son</p>";
		        start+= "<p>Protector of mankind</p>";
		        start+= "<p>Ride to meet your fate</p>";
		        start+= "<p>Ragnar�k awaits</p>";
		        start+= "</html>";
		        
		        JOptionPane.showMessageDialog(null, start);
		        clip.close();
		        ais.close();
			}catch (Exception exc){System.out.println("hmm");}
		}});
		
		//Listener for the Help menu item - displays a help dialog
		mnHelp.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				String part1 = "<html><h2>Help</h2><p>Usage:</p>";
				String part2 = "<p>File->Open->Select .torrent file in the project directory</p>";
				String part3 = "<p>Click save as to change target file name(if desired)</p>";
				String part4 = "<p>Click start. Specified file will be saved in the /Downloads folder in the project directory</p>";
				String part5 = "<p>Note: Ensure that your volume is turned all the way up prior to selecting To Battle!</p></html>";
				String help = part1+part2+part3+part4+part5;
				JOptionPane.showMessageDialog(new JFrame(), 
						help, "Dialog",
				        JOptionPane.INFORMATION_MESSAGE);
			}		
		});
		
		//Listener for the Save as button - sets the save to file accordingly
		main.btnSave.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				JFileChooser chooser= new JFileChooser(System.getProperty("user.dir"));

				int choice = chooser.showOpenDialog(textArea);
				if (choice != JFileChooser.APPROVE_OPTION) return;

				File file = chooser.getSelectedFile();

				main.saveFileName.setText(file.getName());
			} 
		});
		

		//Sets listener for the X at the top-right corner to exit properly.
		WindowListener exitListener = new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
            	guiShutdown();
            }
        };
		addWindowListener(exitListener);
	}
	
	
	/**
	 * Listener for the start, pause, resume, and exit buttons
	 *
	 */
	public class ButtonListener implements ActionListener {
		Gui frame;
		
		public ButtonListener(Gui frame) {
			this.frame = frame;
		}
		
		public void actionPerformed(ActionEvent e) {
			
			JButton source = (JButton)e.getSource();
			
			if (source == frame.main.btnStart) { 
				if (RUBTClient.start(main.fileName.getText().trim(),main.saveFileName.getText().trim())){
					main.btnStart.setEnabled(false);
					main.btnPause.setEnabled(true);
				} else {
					JOptionPane.showMessageDialog(new JFrame(), 
							"Silly mortal, that input/save file name is invalid!", "Dialog",
					        JOptionPane.ERROR_MESSAGE);
					System.out.println(main.fileName.getText());
					System.out.println(main.saveFileName.getText());
				}
				
				
			} else if (source == frame.main.btnPause) { 
				main.btnPause.setEnabled(false);
				main.btnResume.setEnabled(true);
				RUBTClient.pause();
			} else if (source == frame.main.btnResume) { 
				main.btnPause.setEnabled(true);
				main.btnResume.setEnabled(false);
				RUBTClient.resume();
			} else if (source == frame.main.btnExit) { 
				guiShutdown();
			} 
		}
	}
	
	/**
	 * Called when user clicks exit or closed the program
	 */
	public void guiShutdown(){
		RUBTClient.exit();
	}
	
	/**Sets the progress bar value
	 * 
	 * @param percentComplete % of download completed
	 */
	public void setProgressValue(double percentComplete){
		percentComplete = percentComplete*100;
		main.progress.setValue((int)percentComplete);
		main.progress.setString(main.progress.getValue()+"%");
	}

}
