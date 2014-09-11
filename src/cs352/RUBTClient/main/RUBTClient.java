package cs352.RUBTClient.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import cs352.RUBTClient.control.DownloadManager;
import cs352.RUBTClient.gui.Gui;
import cs352.RUBTClient.resources.TorrentInfo;
import cs352.RUBTClient.utils.TorrentInfoDecoder;

/**
 * The RUBTClient is the main class of the RUBT application.
 * The class opens the user interface gui.java, and starts/pauses a download & exits the program
 * upon user command via the gui.
 * The .torrent file must be in the project folder. The downloaded file is saved in the /downloads folder
 * within the project folder.
 * 
 * @Authors Kyle Waranis, Thomas Travis, Yuriy Garnaev
 */
public class RUBTClient{

	private static Thread userInputThread;
	
	private static boolean paused = false;
	private static boolean exit = false;
	
	private static DownloadManager dm;
	private static TorrentInfo torrent;
	private static String saveTorrentName;
	public static String saveFileName;
 
	/**
	 * The validate method returns true if the input arguments are valid, and false otherwise.
	 * 
	 * @param torrentFile - the name of the torrent file
	 * @param saveFile - name of the file to be downloaded
	 */
	public static boolean validate (String torrentFile, String saveFile) {
		if( saveFile.isEmpty() ){
			return false;
		}
		if (!validateTorrentName(torrentFile)){
			return false;
		}
		return true;
	}
	
	/** This method returns true of the input torrent file is a valid .torrent file, and false otherwise
	 * 
	 * @param torrentFile - name of the torrent file
	 */
	public static boolean validateTorrentName(String torrentFile){
		if (torrentFile.isEmpty()){
			return false;
		}
		
		if( !torrentFile.endsWith(".torrent") ){
			return false;
		}
		
		File f = new File( torrentFile );
		if( !f.exists() ){
			return false;
		}
		torrent = getTorrentInfo(torrentFile);
		saveTorrentName = torrentFile;
		saveFileName = torrent.file_name;
		return true;
	}
	
	/**Returns the torrent info for the input torrent file
	 * 
	 * @param fileName
	 * @return
	 */
	public static TorrentInfo getTorrentInfo(String fileName){
		return TorrentInfoDecoder.generateTorrentInfoObject(fileName);
	}
	

	/** The start method is called from the GUI by the user. It creates an instance of DownloadManager,
	 * and calls DownloadManager.start to begin the download process.
	 * 
	 * @param torrent_file_path - torrent file name
	 * @param download_file_path - file name of the file to download
	 * @return
	 */
	public static boolean start(String torrent_file_path, String download_file_path){
		saveTorrentName = torrent_file_path;
		saveFileName = download_file_path;
		
		dm = new DownloadManager( saveTorrentName, saveFileName );
		dm.start(false);
		return true;
	}
	/** The pause method pauses the download manager to pause the download process
	 * 
	 */
	public static void pause(){
		paused = true;
		//dm.shutdown();
		dm.pause();
	}
	/** The resume method resumes the download process
	 * 
	 */
	public static void resume(){
		paused = false;
		//dm = new DownloadManager( saveTorrentName, saveFileName );
		//dm.start(true);
		dm.unPause();
	}
	
	/**The exit method shuts down the download manager
	 * 
	 */
	public static void exit(){
		if (dm!=null)
			dm.shutdown();
		exit = true;
	}
	
	
	
	
	
	

	public static void main(String[] args) {

		Gui gui = new Gui("Thorrent - brought to you by the Thunder God");
		
		// Sets the progress value every 100 ms until exit is set to true
		// Once exit is set to true, exits the program
		while (!exit){
			if (paused || dm==null){
				try{Thread.sleep(100);}catch(InterruptedException e){}
				continue;
			}
			gui.setProgressValue(dm.getPercentComplete());
			
			
			try{Thread.sleep(100);}catch(InterruptedException e){}
		}
		
		System.exit(1);
	}
}
