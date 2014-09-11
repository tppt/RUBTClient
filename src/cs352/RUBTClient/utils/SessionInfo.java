package cs352.RUBTClient.utils;

import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Map;

import cs352.RUBTClient.resources.TorrentInfo;


/******************************************************************************
 * Session info encapsulates common info shared across all classes in
 * RUBTClienTProject.  It provides safe access to information stored in
 * this session's .torrent metainfo object and the values derived thereof.  
 * 
 * This class also provided universal access to the torrent_file_path and 
 * download_file_path, which combined uniquely identify this particular 
 * session.
 * 
 * @author Thomas Travis
 * @since 8/4/2013
 * @version 1.0
 *
 ******************************************************************************/
public class SessionInfo {

	protected TorrentInfo info;
	protected String download_file_path;
	protected String torrent_file_path;
	
	protected URL announce_url;
	protected String announce_url_string;
	protected String default_file_name;
	protected int file_length;
	protected ByteBuffer info_hash;
	protected Map<ByteBuffer, Object> info_map;
	protected int last_piece_length;
	protected int number_of_pieces;
	protected ByteBuffer[] piece_hashes;
	protected int piece_length;
	protected TorrentInfo torrent_info;
	
	public SessionInfo( String torrent_file_path, String download_file_path ){
		
		this.torrent_file_path = torrent_file_path;
		this.download_file_path = download_file_path;
		
		this.info = generateTorrentInfoObject( torrent_file_path );
		this.announce_url = info.announce_url;
		this.announce_url_string = info.announce_url.toString();
		this.default_file_name = info.file_name;
		this.file_length = info.file_length;
		this.info_hash = info.info_hash;
		this.info_map = info.info_map;
		this.piece_length = info.piece_length;
		this.last_piece_length = getLastPieceLength();
		this.number_of_pieces = (int)Math.ceil( info.file_length / (double)info.piece_length );
		this.piece_hashes = info.piece_hashes;
		this.torrent_info = info;
	}
	
	/** The base URL of the tracker for client scrapes. */
	public URL announce_url(){
		return announce_url;
	}
	
	/** The String of the base URL of the tracker for client scrapes. */
	public String announce_url_string(){
		return announce_url_string;
	}
	
	/** The optional file_name of the file referenced in the .torrent file. */
	public String default_file_name(){
		return default_file_name;
	}
	
	/** The number of bytes comprising the total file. */
	public int file_length(){
		return file_length;
	}
	
	/** The SHA-1 hash of the bencoded form of the info dictionary from the torrent metainfo file. */
	public ByteBuffer info_hash(){
		return info_hash;
	}
	
	/** The unbencoded info dictionary of the torrent metainfo file.  
	 * See http://www.bittorrent.org/beps/bep_0003.html for an explanation of 
	 * what keys are available and how they map.
	 */
	public Map<ByteBuffer, Object> info_map(){
		return info_map;
	}
	
	/** The number of bytes comprising the last piece.  This is either less than or equal to piece_length." */
	public int last_piece_length(){
		return last_piece_length;
	}
	
	/** The total number of pieces (of length up to piece_length) comprising the file. */
	public int number_of_pieces(){
		return number_of_pieces;
	}
	
	/** The SHA-1 hashes of each piece of the file. */
	public ByteBuffer[] piece_hashes(){
		return piece_hashes;
	}
	
	/** The nominal size (in bytes) of the pieces comprising the file. */
	public int piece_length(){
		return piece_length;
	}
	
	/** The underlying TorrentInfo object backing this SessionInfo object. */
	public TorrentInfo torrent_info(){
		return info;
	}
	
	/** The target location in the file system where the downloaded file is to be saved and named. */
	public String getDownloadFilePath(){
		return download_file_path;
	}
	
	/** The location in the file system of the .torrent metainfo file. */
	public String getTorrentFilePath(){
		return torrent_file_path;
	}
	
	/**
	 * Generate the TorrentInfo object backing this SessionInfo object.
	 * @param torrent_file_path
	 * @return a TorrentInfo object; null if the .torrent file doesn't exist.
	 */
	protected static TorrentInfo generateTorrentInfoObject( String torrent_file_path ){
		return TorrentInfoDecoder.generateTorrentInfoObject(torrent_file_path);
	}
	
	/**
	 * Calculate the length of the last piece, which may be less than the nominal piece_length
	 * provided by the .torrent meta info object.
	 * @return the length of the last piece of the file
	 */
	protected int getLastPieceLength(){
		int length = info.file_length % info.piece_length;
		if( length == 0 )
			length = info.piece_length;
		return length;
	}
}
