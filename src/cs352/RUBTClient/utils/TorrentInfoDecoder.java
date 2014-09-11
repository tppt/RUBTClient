package cs352.RUBTClient.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import cs352.RUBTClient.resources.BencodingException;
import cs352.RUBTClient.resources.TorrentInfo;

/******************************************************************************
 * A static utility class that decodes a .torrent file and returns its
 * meta info encapsulated by a TorrentInfo object.  This is done via the 
 * classes single generateTorrentInfoObject method.
 * 
 * Input: a String representing the abstract path name to the .torrent file.
 * Output: a TorrentInfo object encapsulating the .torrent files meta info.
 * 
 * @author Thomas Travis
 * @since 08/09/2013
 * @version 1.0
 ******************************************************************************/
public class TorrentInfoDecoder {
		
	/**
	 * Generates a TorrentInfo object from the file specified by torrent_file_path.
	 * This method should be accessed in a static way.
	 * @param torrent_file_path
	 * @return the TorrentInfo object, if it exists; returns null otherwise.
	 */
	public static TorrentInfo generateTorrentInfoObject( String torrent_file_path ){
		
		//Get File object for torrent file point to by the path.
		File file = new File( torrent_file_path );
		
		//If the file doesn't exist, report it.
		if( !file.exists() ){
			return null;
		}
	
		//Create a byte array to read the file bytes in from disk.
		int torrent_file_size = (int)file.length();
		byte[] torrent_file_bytes = new byte[ torrent_file_size ];
		
		//Read the file bytes into the created byte array.
		try{	
			FileInputStream fis = new FileInputStream( torrent_file_path );
			fis.read( torrent_file_bytes );
			fis.close();
		}
		catch( FileNotFoundException e ){
			return null;
		}
		catch( IOException e ){
			System.err.println("An I/O error occurred while reading the torrent file bytes in from disk.");
			e.printStackTrace();
			System.exit( 1 );
		}
		
		//Generate a new TorrentInfo object from the file bytes in the byte array.
		TorrentInfo info = null;
		try{
			info = new TorrentInfo( torrent_file_bytes );
		}
		catch( BencodingException e ){
			System.err.println(" An error occurred while trying to unbencode the torrent file bytes.");
			e.printStackTrace();
			System.exit( 1 );
		}
		
		return info;
	}
	
	protected TorrentInfoDecoder(){}
}
