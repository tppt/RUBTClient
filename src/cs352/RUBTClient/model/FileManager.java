package cs352.RUBTClient.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import cs352.RUBTClient.utils.RUBTClientLogger;
import cs352.RUBTClient.utils.SessionInfo;

/*************************************************************************
 * Store pieces as they are download, save them to disk on request, and 
 * resume downloading unfinished pieces.
 * 
 * @author Thomas Travis
 * @since 7/23/2013
 * @version 2.0
 * For CS352 2013 Summer Session
 * 
 *************************************************************************/
public class FileManager {

	protected Logger paLog = null;
	protected SessionInfo info;
	protected int piece_length;
	protected int number_of_pieces;
	protected ByteBuffer[] pieces;
	protected int[] total_bytes_downloaded;
	protected BitSet bitfield;
	protected int final_piece_length;
	
	public FileManager( SessionInfo info ){
		
		paLog = RUBTClientLogger.getInstance().getLogger(this.getClass().getName(), "palog");
		this.info = info;
		
		piece_length = info.piece_length();
		number_of_pieces = info.number_of_pieces();
		pieces = new ByteBuffer[ number_of_pieces ];
		total_bytes_downloaded = new int[ number_of_pieces ];
		
		int extraBits = 8 - ( info.number_of_pieces() % 8);
		bitfield = new BitSet( info.number_of_pieces() + extraBits );
		
		final_piece_length = info.last_piece_length();
		
		createDownloadFile();
	}
	
	/**
	 * Check to see if the piece at the given index has been completely downloaded.
	 * @param index
	 * @return true if the piece has been completely downloaded; false otherwise
	 * @author Thomas Travis
	 */
	public boolean have( int index ){
		synchronized( bitfield ){
			return bitfield.get( index );
		}
	}
	
	/**
	 * This implementation utilizes a BitSet with size 
	 * 
	 * (number_of_pieces + (8 - (number_of_pieces mod 8)))
	 * 
	 * to represent this file's bitfield.  If a bit is set, we have completely downloaded this piece.
	 * The extra trailing bits, if any, are set to zero.  If any trailing bits are set, then an error 
	 * has occurred.  Only those bits from index 0 to number_of_pieces-1 are valid.
	 * The leading bit in the MSB of the byte array represents index 0.
	 * 
	 * @return a ByteBuffer wrapping the byte array representation of this BitSet.  ByteBuffer endianess
	 * is BIG_ENDIAN by default; that is, bytes are ordered from MSB to LSB.
	 * @author Thomas Travis
	 */
	public ByteBuffer getBitField(){
		synchronized(bitfield){
			return ByteBuffer.wrap( bitfield.toByteArray() );
		}
	}
	
	/**
	 * Check the status of the overall download.
	 * @return -1 if no pieces have been downloaded; 0 if some, but not all, pieces have been downloaded;
	 * 1 if all piece have been downloaded
	 * @author Thomas Travis
	 */
	public int getDownloadStatus(){
		
		int have = 0;
		int haveNot = 0;
		
		synchronized(bitfield){
			
			for( int i = 0; i < number_of_pieces; i++ ){
				if( bitfield.get(i) == true )
					have++;
				else
					haveNot++;
			}
		}
		
		if( have == number_of_pieces)
			return 1;
		if( haveNot == number_of_pieces )
			return -1;
		return 0;
	}
	
	/**
	 * Obtain the total number of bytes saved to disk so far.
	 * @return the number of bytes saved to disk so far
	 * @author Thomas Travis
	 */
	public int getTotalBytesSavedToDisk(){
		
		synchronized(bitfield){
			int total = 0;
			for( int i = 0; i < number_of_pieces; i++ ){
				if( bitfield.get( i ) == true ){
					if( i == number_of_pieces - 1 )
						total += final_piece_length;
					else
						total += piece_length;
				}
			}
			return total;
		}
	}
	
	/**
	 * Obtain the quantity of bytes downloaded for the overall file, but not necessarily save to disk.
	 * @return the number of bytes downloaded so far
	 * @author Thomas Travis
	 */
	public int getTotalFileBytesDownloaded(){
		
		synchronized(total_bytes_downloaded){
			int total = 0;
			for( int i = 0; i < number_of_pieces; i++ ){
				total += total_bytes_downloaded[ i ];
			}
			return total;
		}
	}
	
	/**
	 * Obtain the quantity of bytes downloaded for the piece at the given index.
	 * This piece may not yet have been saved to disk.
	 * 
	 * @param index
	 * @return the quantity of bytes downloaded for the piece at the given index
	 * @author Thomas Travis
	 */
	public int getTotalPieceBytesDownloaded( int index ){
		synchronized(total_bytes_downloaded){
			return total_bytes_downloaded[ index ];
		}
	}
	
	/**
	 * Process a requested data block, and store its contents.
	 * 
	 * @param length - the length (quantity) of bytes passed.  This is the size of the data_block + 9 bytes
	 * for the message ID, piece index, and block offset (begin index).
	 * 
	 * @param data - this should have the length parameter stripped from the byte array,
	 * so that only the remaining bytes are passed.  Send the length as an integer, as per the above param.
	 * 
	 * @return 0 if some, but not all, of the data for this piece has been downloaded, 1 if the 
	 * entire piece has finished downloading.  Returns null if we are trying to add data to an 
	 * already completed piece.  Returns -1 if the piece's SHA-1 hash does not match that provided by
	 * the .torrent MetaInfo object, or if the piece couldn't be written due to an I/O error.  In either "-1" case,
	 * all progress on the piece is reset.
	 * 
	 * @author Thomas Travis
	 */
	public synchronized Integer storeDataBlock( int length, byte[] data){
		
		//Parse through data, separating out necessary fields.
		ByteBuffer data_buffer = ByteBuffer.wrap(data);
		byte message_id = data_buffer.get();
		int index = data_buffer.order(ByteOrder.BIG_ENDIAN).getInt();
		int offset = data_buffer.order(ByteOrder.BIG_ENDIAN).getInt();
		length = length-9;
		byte[] data_block = new byte[ length ];
		data_buffer.get(data_block, 0, length);
		
		if( have( index ) ){
			
			//Obtain the block at the given index from disk.
			ByteBuffer retrieved = retrieveDataBlock( index, offset, length );
			
			//If we don't actually have the block, update global fields and save state.
			if( retrieved == null ){
				pieces[ index ] = null;
				total_bytes_downloaded[ index ] = 0;
				bitfield.set( index, false );
				save();
			}
			//Else inform caller that we already have the piece.
			else{
				return null;
			}
		}
		
		//Create ByteBuffer for piece if necessary.
		if( pieces[index] == null )
			if( index == number_of_pieces - 1 )
				pieces[index] = ByteBuffer.allocate( final_piece_length );
			else
				pieces[index] = ByteBuffer.allocate( piece_length );
		
		//Copy data into the piece's ByteBuffer, and then update the tally of how many bytes of this piece have been downloaded.
		pieces[ index ].position( offset );
		pieces[ index ].put(data_block, 0, length);
		total_bytes_downloaded[ index ] += length;
		
		//Check to see if the piece has been completely downloaded.
		if( total_bytes_downloaded[ index ] == pieces[ index ].array().length ){
			
			//If so, verify its hash.
			boolean verified = verifyHash( index, pieces[ index ] );
			if( verified ){
				//Write the piece to disk if its hash has been verified.
				boolean written = writePieceToDisk( index );
				if( written ){
					//If the piece is successfully written to disk, set the bitfield, clear its buffer, and save state.
					bitfield.set( index, true );
					pieces[ index ] = null;
					save();
					return 1;
				}
				else{
					//If the piece was not successfully written to disk, reset all progress for the piece and save state.
					total_bytes_downloaded[ index ] = 0;
					bitfield.set( index, false );
					pieces[ index ] = null;
					save();
					return -1;
				}
			}
			else{
				//If the piece's hash was not verified, reset all progress for the piece and save state.
				total_bytes_downloaded[ index ] = 0;
				bitfield.set( index, false );
				pieces[ index ] = null;
				save();
				return -1;
			}
		}
		
		return 0;
	}
	
	/**
	 * Obtain a ByteBuffer wrapping the requested byte block
	 * 
	 * @param index
	 * @param offset
	 * @param requested_block_length
	 * @return a ByteBuffer wrapping the requested bytes; returns null if we do not yet have 
	 * the piece at the given index, or if there was an error reading from disk.
	 * @author Thomas Travis
	 */
	public ByteBuffer retrieveDataBlock( int index, int offset, int requested_block_length ){
		
		//Check to see if the piece has already been downloaded.
		if( !have( index ) )
			return null;
		
		//Obtain the correct piece length.
		int length;
		if( index == number_of_pieces -1 )
			length = final_piece_length;
		else
			length = piece_length;
		
		ByteBuffer piece = ByteBuffer.allocate( length );
	
		try{
			//Open a File Channel to the download file to read bytes.
			Path path = Paths.get(System.getProperty("user.dir")+File.separator+"downloads"+File.separator+info.getDownloadFilePath());
			FileChannel fc = FileChannel.open(path, StandardOpenOption.READ);
			
			//Read the bytes of a full piece in from disk.
			fc.position( index*piece_length );
			int bytes_read = 0;
			while( bytes_read < length ){
				bytes_read = fc.read( piece );
				//If the File Channel reaches the end of the stream, throw an IOException.
				if( bytes_read == -1 )
					throw new IOException();
			}
			fc.close();
		}
		catch( IOException e ){
			paLog.log(Level.WARNING, "An I/O Exception occurred while retrieving a data block from disk.", e);
			return null;
		}
		
		//Verify the piece's hash.
		boolean verified = verifyHash( index, piece );
		
		//If the piece's hash is verified, return the requested byte block from within the piece.
		if( verified ){
			ByteBuffer block = ByteBuffer.allocate(requested_block_length);
			block.put(piece.array(), offset, requested_block_length);
			return block;
		}
		//Else update the bitfield, save state, and report that the requested block hasn't actually been downloaded yet.
		else{
			synchronized(bitfield){
				bitfield.set( index, false );
			}
			save();
			return null;
		}
	}
	
	/**
	 * Save the progress of this download by serializing the bitfield.
	 * @author Thomas Travis
	 */
	public void save(){
	
		//Obtain the path to the state saving file.
		String save_file = info.getDownloadFilePath()+".data";
		String path = System.getProperty("user.dir")+File.separator+"data"+File.separator+save_file;
		
		//Create the file, if necessary.
		File file = null;
		try{
			file = new File( path );
			if( !file.exists() ){
				file.getParentFile().mkdirs();
				file.createNewFile();
			}
		}
		catch( IOException e ){
			paLog.log(Level.WARNING, "An I/O error occurred while trying to create the save file.", e);
			return;
		}
		
		//Serialize and write the bitfield to the save file.
		synchronized(bitfield){
			
			try{
				FileOutputStream fos = new FileOutputStream( file );
				ObjectOutputStream oos = new ObjectOutputStream( fos );
				oos.writeObject( bitfield );
				oos.flush();
				oos.close();
				fos.close();
			}
			catch( IOException e ){
				paLog.log(Level.WARNING, "An I/O error occurred while trying to save client progress.", e);
				return;
			}
		}
	}
	
	/**
	 * Resume the download by restoring the bitfield.
	 * 
	 * @return 1 if we have previously downloaded all pieces; -1 if we have downloaded no pieces; 
	 * 0 if we have downloaded some pieces.  Returns null if the save-state file does
	 * not exist, or if an I/O error occurred in reading the file.
	 * @author Thomas Travis
	 */
	public Integer resume(){
		
		//Obtain the path to the state saving file.
		String save_file = info.getDownloadFilePath()+".data";
		String path = System.getProperty("user.dir")+File.separator+"data"+File.separator+save_file;
		
		try{
			//Open streams to the serialized BitSet's file.
			FileInputStream fis = new FileInputStream( path );
			ObjectInputStream ois = new ObjectInputStream( fis );
			
			//Deserialize BitSet, and set bitfield to its value.
			synchronized(bitfield){
				bitfield = (BitSet)ois.readObject();
			}
			
			ois.close();
			fis.close();
		}
		catch( ClassNotFoundException e ){
			//As BitSet is a Java class, we should never arrive here.
			paLog.log(Level.SEVERE, "The BitSet class could not be found.", e);
		}
		catch( FileNotFoundException e ){
			paLog.log(Level.WARNING, "The download progress save file was not found.", e);
			return null;
		}
		catch( IOException e ){
			paLog.log(Level.WARNING, "An I/O error occurred while reading in the bitfield from disk.", e);
			return null;
		}
		
		//Reset the total_bytes_downloaded values as per the bitfield.
		resetTotalBytesDownloaded();
		
		//Check to see how far along we are in the download, and return.
		int status = getDownloadStatus();
		return status;
	}
	
	/**
	 * Reset the values in total_bytes_downloaded.  Sets the values of items we have
	 * (as per the bitfield), to the respective piece's total byte size; items the bitfield
	 * states we don't have are set to zero.
	 * @author Thomas Travis
	 */
	protected void resetTotalBytesDownloaded(){
		
		synchronized(this){
			for( int i = 0; i < number_of_pieces; i++ ){
				if( have( i ) ){
					if( i == number_of_pieces - 1 )
						total_bytes_downloaded[ i ] = final_piece_length;
					else
						total_bytes_downloaded[ i ] = piece_length;
				}
				else{
					total_bytes_downloaded[ i ] = 0;
				}
			}
		}
	}
	
	/**
	 * Write the bytes of the piece at the given index to disk.
	 * @param index
	 * @return true if the piece has been successfully written to the disk; false otherwise.
	 * @author Thomas Travis
	 */
	protected boolean writePieceToDisk( int index ){
		
		try {
			
			//Open a FileChannel to the download file to write bytes.
			Path path = Paths.get(System.getProperty("user.dir")+File.separator+"downloads"+File.separator+info.getDownloadFilePath());
			FileChannel fc = FileChannel.open(path, StandardOpenOption.WRITE);
			
			synchronized( pieces ){
				
				//Prepare ByteBuffer for write.
				pieces[ index ].flip();
				
				//Set to the correct position within the target file, and then write bytes.
				fc.position( index*piece_length );
				while( pieces[ index ].hasRemaining() ){
					fc.write( pieces[ index ]);
				}				
			}
			
			//Force channel to write remaining bytes in channel to disk (Equivalent to a stream's flush() method).
			fc.force(false);
			fc.close();
			
		}
		catch (IOException e) {
			paLog.log(Level.WARNING, "An I/O error occurred while writing a piece to the disk.", e);
			return false;
		}
		
		return true;
	}
	
	/**
	 * Verify the hash of the given piece.
	 * @param index - corresponds to the position (index*piece_length) of the piece within the file.
	 * @param piece - the ByteBuffer wrapping the array of the completed pieces bytes.
	 * @return true if the the piece's hash matches that found in the .torrent MetaInfo object; false otherwise
	 * @author Thomas Travis
	 */
	protected boolean verifyHash( int index, ByteBuffer piece ){
		
		//Obtain bytes for comparison
		byte[] expected_bytes = info.piece_hashes()[ index ].array();
		byte [] actual_bytes = piece.array();
		
		try{
			//Obtain the correct MessageDigest hashing algorithm.
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			//Digest the piece's bytes (that is, run them through the hashing algorithm).
			byte [] digestedActualBytes = md.digest( actual_bytes );
			//Compare the digested piece's bytes to those from the .torrent MetaInfo object.
			if( !Arrays.equals(expected_bytes, digestedActualBytes) ){
				return false;
			}
		}
		catch( NoSuchAlgorithmException e ){
			//SHA-1 is an algorithm supported by the JVM, so we should never reach here.
			paLog.log(Level.SEVERE, "The MessageDigest algorithm (SHA-1) is unsupported.", e);
			return false;
		}
		
		return true;
	}
	
	/**
	 * Create the file to which the download is saved.
	 * @return true if the file is successfully created; false otherwise.
	 * @author Thomas Travis
	 */
	protected boolean createDownloadFile(){
		
		String path = System.getProperty("user.dir")+File.separator+"downloads"+File.separator+info.getDownloadFilePath();
		
		File file = new File( path );
		if( !file.exists() ){
			
			try{
				//Create file.
				file.getParentFile().mkdirs();
				file.createNewFile();
				
				//Allocate the space necessary to download the total file.
				FileOutputStream fos = new FileOutputStream( file );
				fos.write(new byte[ info.file_length() ]);
				fos.flush();
				fos.close();
			}
			catch( IOException e ){
				if( file.exists() )
					file.delete();
				paLog.log(Level.SEVERE, "An I/O error occurred while creating the download file.", e);
				return false;
			}
		}
		return true;
	}
}
