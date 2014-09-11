/**
 * @Authors Kyle Waranis, Thomas Travis, Yuriy Garnaev
 */

package cs352.RUBTClient.control;

import java.io.IOException;
import java.lang.Comparable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.BitSet;
import java.nio.ByteBuffer;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

import cs352.RUBTClient.model.FileManager;
import cs352.RUBTClient.utils.SessionInfo;

public class DownloadManager 
{
	//Download Manager constants across all instances.
	private static final int MAX_DOWNLOADERS = 4;
	private static final int MAX_OPTIMISTIC_PEERS = 1;
	private static final int MAINTENANCE_TIMER = 30000; //In milliseconds.
	private static final int MAX_CONCURRENT_DOWNLOADS = 25;
	
	//Helper Classes
	private ContactTracker tracker_connection = null;
	private FileManager file_manager = null;
	private SessionInfo info = null;
	
	//Connection information fields
	private final String my_peer_id = generatePeerID();
	private final int listener_port = getAvailablePort();
	private ServerSocket listenerSocket;
	
	//Peer Lists
	private String[][] remote_peer_array;
	private ArrayList<Peer> peers;
	private ArrayList<Peer> downloaders;
	private ArrayList<Peer> waitList;
	private ArrayList<Peer> chokedPeers;
	private ArrayList<Peer> optimisticPeers;
	private ArrayList<Piece> pieces;
	
	//Data Transfer Queues
	private LinkedBlockingQueue<ULRequest> ULQueue;
	private PriorityBlockingQueue<Piece> DLQueue;
	
	//State fields
	public boolean download_complete;
	public boolean paused;
	private Integer downloadingPieces;
	private Integer totalPendingPieces;
	private Integer totalDownloaded;
	private Integer totalUploaded;
	
	//Action threads
	Thread downloadThread;
	Thread uploadThread;
	Thread maintenanceThread;
	Thread listenerThread;
	
	private DownloadManager instance;
	
	/**
	 * Constructor for DownloadManager. It initializes all values and peers.
	 * 
	 * @Main_Author Kyle Waranis
	 * @Co_Author Yuriy Granaev and Thomas Travis
	 */
	public DownloadManager(String torrent_file_path, String download_file_path)
	{
		//Initialize Helper Classes
		info = new SessionInfo( torrent_file_path, download_file_path );
		file_manager = new FileManager( info );
		tracker_connection = new ContactTracker( my_peer_id, listener_port );
		
		//Initialize Peer Lists		
		downloaders = new ArrayList<Peer>();
		optimisticPeers = new ArrayList<Peer>();
		waitList = new ArrayList<Peer>();
		chokedPeers = new ArrayList<Peer>();
		
		//Initialize the data transfer queues
		ULQueue = new LinkedBlockingQueue<ULRequest>();
		DLQueue = new PriorityBlockingQueue<Piece>();
		
		//Initialize state fields
		paused = false;
		download_complete = false;
		totalPendingPieces = new Integer(0);
		downloadingPieces = new Integer(0);
		totalDownloaded = 0;
		totalUploaded = new Integer(0);
		
		//Initialize the pieces array for all but the final piece.
		int numPieces = info.number_of_pieces();
		pieces = new ArrayList<Piece>(numPieces);
		
		for(int i = 0; i < numPieces - 1; i++)
		{
			pieces.add(new Piece(i, info.piece_length() ));
		}
		
		//Add the final piece to the array.
		pieces.add(new Piece(numPieces - 1, info.last_piece_length() ));
		
		this.instance = this;
	}
	
	/**
	 * This directs the DM to start the download process on the provided list of peers.
	 * 
	 * @param resume If the file_manager needs to resume from a previous state then pass true, else false.
	 */
	public void start(boolean resume)
	{
		//Tell the piece assembler to resume if needed.
		Integer response = new Integer(-1);

		if(resume)
			response = file_manager.resume();
		
		//Set the proper download_complete value.
		if(response != null)
			if(response == 1)
				download_complete = true;
		
		tracker_connection.start( this );
		
		//Ensure we have peers to connect to before moving forward.
		while (tracker_connection.getPeerURLS() == null){
			try{
				Thread.sleep(100);
			}
			catch( InterruptedException e )
			{return;}
		}
		
		remote_peer_array = getPeerArray();
		peers = setRemotePeerList();
		
		//Start connecting all peers.
		for(int i = 0; i < peers.size(); i++)
		{
			connectPeer(peers.get(i), null);
		}
		
		//Start the download thread.
		startDownloader();
		
		//Start the upload thread.
		startUploader();
		
		//Start the maintenance thread.
		startMaintenance();
		
		//Start the listener thread.
		startListener();
		
	}
	
	/**
	 * This records the receipt of the given block of data and notifies updates the pieces structure.
	 * 
	 * @param index The index that has been received.
	 * @param piece The data that has been received.
	 * @param peer The peer posting the data.
	 */
	public synchronized void registerPiece(int length, byte[] piece, Peer peer)
	{	
		//Pull out the index of the piece
		byte[] indexB = Arrays.copyOfRange(piece, 1, 5);
		int index = java.nio.ByteBuffer.wrap(indexB).order(java.nio.ByteOrder.BIG_ENDIAN).getInt();
		
		//Pass the piece off to the file_manager.
		Integer response = file_manager.storeDataBlock(length, piece);
		
		//Check if we already have this piece, and if so ignore it.
		if(response == null)
			return;
		
		//If the piece is complete then update the necessary things.
		if(response == 1)
		{	
			//Decrement the number of pending downloads.
			synchronized(downloadingPieces)
			{downloadingPieces--;}
			
			//Decrement the number of pending pieces.
			synchronized(totalPendingPieces)
			{totalPendingPieces--;}
			
			//Increment the total number of downloaded bytes.
			synchronized(totalDownloaded)
			{totalDownloaded += length;}
			synchronized(peer.downloaded)
			{peer.downloaded += length;}
			
			//Send a have notice to all connected peers.
			synchronized(peers)
			{
				for(int i = 0; i < peers.size(); i++)
				{
					if(peers.get(i) != null)
						if(!peers.get(i).getDisconnected())
							peers.get(i).have(index);
				}
			}
		}
		//If the piece fails SHA-1 hash re-add it to the DL queue.
		else if(response == -1)
		{
			synchronized(pieces)
			{
				DLQueue.offer(pieces.get(index));
			}
		}
	}
	
	/**
	 * This records that the given Peer knows the appropriate pieces from the bitfield.
	 * 
	 * @param bitfieldIn The bitfield received from the Peer.
	 * @param peer The peer that the bitfield originated from.
	 */
	public synchronized void registerBitfield(byte[] bitfieldIn, Peer peer)
	{
		//Update the pieces array. The Bitfield is read in reverse order.
		synchronized(pieces)
		{
			int index  = 0;
			for(byte b : bitfieldIn)
			{
				for(int mask = 0x100; mask != 0x01; mask >>= 1)
				{
					if(index >= pieces.size())
						break;
					if((b & mask) != 0)
					{
						if(!pieces.get(index).peersWhoHave.contains(peer))
							pieces.get(index).peersWhoHave.add(peer);
					}
					
					index++;
				}
			}
		}
	}
	
	/**
	 * Records that the given Peer has the given index.
	 * 
	 * @param index The index that the Peer has.
	 * @param peer The owner of said piece.
	 */
	public synchronized void registerHave(byte[] indexIn, Peer peer)
	{
		//Parse the index of the piece
		int index = java.nio.ByteBuffer.wrap(indexIn).order(java.nio.ByteOrder.BIG_ENDIAN).getInt();
		
		//If we didn't already know that this peer has this piece, then record it.
		synchronized(pieces)
		{
			if(!pieces.get(index).peersWhoHave.contains(peer))
			{
				pieces.get(index).peersWhoHave.add(peer);
				
				//If The peer has something that I dont, be interested.
				if(!file_manager.have(index))
					if(!peer.getAmInterested())
						peer.interestedInPeer();
			}
		}
	}
	
	/**
	 * Adds the given request to the UL queue.
	 * 
	 * @param indexIn The index of the requested piece.
	 * @param offsetIn The offset of the requested data.
	 * @param lengthIn The length requested from the peer.
	 * @param peer The peer that the request originated from.
	 */
	public synchronized void registerRequest(byte[] indexIn, byte[] offsetIn, byte[] lengthIn, Peer peer)
	{
		//Parse the arguments of the piece
		int index = java.nio.ByteBuffer.wrap(indexIn).order(java.nio.ByteOrder.BIG_ENDIAN).getInt();
		int offset = java.nio.ByteBuffer.wrap(offsetIn).order(java.nio.ByteOrder.BIG_ENDIAN).getInt();
		int length = java.nio.ByteBuffer.wrap(lengthIn).order(java.nio.ByteOrder.BIG_ENDIAN).getInt();
		
		//Add the request to the upload queue.
		ULQueue.offer(new ULRequest(index, offset, length, peer));
	}
	
	/**
	 * Removes the given request from the UL queue.
	 * 
	 * @param indexIn The index of the requested piece.
	 * @param offsetIn The offset of the requested data.
	 * @param lengthIn The length requested from the peer.
	 * @param peer The peer that the request originated from.
	 */
	public synchronized void registerCancel(byte[] indexIn, byte[] offsetIn, byte[] lengthIn, Peer peer)
	{
		//Parse the arguments of the piece
		int index = java.nio.ByteBuffer.wrap(indexIn).order(java.nio.ByteOrder.BIG_ENDIAN).getInt();
		int offset = java.nio.ByteBuffer.wrap(offsetIn).order(java.nio.ByteOrder.BIG_ENDIAN).getInt();
		int length = java.nio.ByteBuffer.wrap(lengthIn).order(java.nio.ByteOrder.BIG_ENDIAN).getInt();
		
		//Remove the request from the queue.
		ULQueue.remove(new ULRequest(index, offset, length, peer));
	}
	
	/**
	 * Updates the peer list and disconnects peers that are no longer present.
	 * 
	 * @param peersIn The updated list of peers.
	 */
	@SuppressWarnings("unchecked")
	public void setPeers()
	{
		//Obtain a new list of remote peers from the tracker.
		remote_peer_array = getPeerArray();
		
		//Create the new array list of Peers.
		ArrayList<Peer> peersTmp = setRemotePeerList();
		
		synchronized(peers)
		{
			//Recover the removed peers and close connections with all of them.
			ArrayList<Peer> oldPeers = (ArrayList<Peer>) peers.clone();
			oldPeers.removeAll(peersTmp);
			
			for(int i = 0; i < oldPeers.size(); i++)
				oldPeers.get(i).close();
			
			oldPeers.clear();
			
			//Recover the new peers and open connections to all of them.
			ArrayList<Peer> newPeers = (ArrayList<Peer>) peersTmp.clone();
			newPeers.removeAll(peers);
			
			for(int i = 0; i < newPeers.size(); i++)
				connectPeer(newPeers.get(i), null);
			
			newPeers.clear();
			
			peers = peersTmp;
		}
	}
	
	public SessionInfo getSessionInfo(){
		return info;
	}
	
	/**
	 * This returns the percentage complete of the current download.
	 * @return The percentage complete. This is accurate, but not absolute.
	 */
	public double getPercentComplete()
	{
		return ( info.file_length() - getLeft() ) / (double)info.file_length();
	}
	
	/**
	 * This gets the total amount downloaded so far this session.
	 * @return The total downloaded this session in bytes.
	 */
	public int getDownloaded()
	{
		synchronized(totalDownloaded)
		{
			return totalDownloaded;
		}
	}
	
	/**
	 * This gets the total amount left to download in this session.
	 * @return The total amount left to download in bytes.
	 */
	public int getLeft()
	{
		int remaining = info.file_length() - file_manager.getTotalBytesSavedToDisk();
		return remaining;
	}
	
	/**
	 * This gets the total amount uploaded so far this session.
	 * @return The total uploaded this session in bytes.
	 */
	public int getUploaded()
	{
		synchronized(totalUploaded)
		{
			return totalUploaded;
		}
	}
	
	/**
	 * Closes all peers that are currently downloading.
	 */
	public void shutdown()
	{
		//Interrupt all threads.
		downloadThread.interrupt();
		uploadThread.interrupt();
		maintenanceThread.interrupt();
		listenerThread.interrupt();
		tracker_connection.stop();
		
		//Close the server socket.
		try{listenerSocket.close();}
		catch(Exception e){;}
		
		//Close connections with all peers.
		synchronized(peers)
		{
			for(int i = 0; i < peers.size(); i++)
				peers.get(i).close();
		}
		
		//Notify the Piece Assembler to save its state.
		file_manager.save();
	}
	
	/**
	 * Sets the DM state to paused.
	 */
	public synchronized void pause()
	{
		paused = true;
	}
	
	/**
	 * Sets the DM state to unpaused.
	 */
	public synchronized void unPause()
	{
		paused = false;
	}
	
	/**
	 * This directs the DM to start the download process.
	 */
	private void startDownloader()
	{
		//Construct the download thread.
		downloadThread = new Thread(new Runnable()
		{
			public void run()
			{	
				//Check to see if the download has download_complete, and if so just escape.
				if(download_complete)
					return;

				//Sleep until at least one peer has unchoked me.
				boolean unChoked = false;
				while(true)
				{
					synchronized(peers)
					{
						//Check for at least one unchoked peer.
						for(int i = 0; i < peers.size(); i++)
							if(!peers.get(i).getDisconnected())
								if(unChoked = !peers.get(i).getPeerChoking())
									break;
						
						//Break out of the while loop when one is found.
						if(unChoked)
							break;
					}
					
					try{Thread.sleep(100);}
					catch(InterruptedException e){return;}
				}
				
				//Add all needed pieces to the download queue
				for(int i = 0; i < pieces.size(); i++)
				{
					if(!file_manager.have(i))
					{
						DLQueue.offer(pieces.get(i));
						
						//Increment the total pending pieces.
						synchronized(totalPendingPieces)
						{
							totalPendingPieces++;
						}
					}
				}
				
				//Request pieces until the download is download_complete, 
				//while capping the total number of concurrent downloading pieces.
				Piece tmpPiece = null;
				int downloadingPiecesEst = 0;
				while(!download_complete)
				{
					synchronized(totalPendingPieces)
					{
						//Check if I'm download_complete.
						if(totalPendingPieces == 0)
						{
							download_complete = true;
							break;
						}
					}
					
					//Get an estimate if I'm capped. Since this MUST be synchronized it can't be used directly in an if statement.
					synchronized(downloadingPieces)
					{
						downloadingPiecesEst = downloadingPieces;
					}
					
					if(downloadingPiecesEst < MAX_CONCURRENT_DOWNLOADS)
					{
						//Check if I'm supposed to be paused.
						synchronized(this)
						{
							if(paused)
							{
								try{Thread.sleep(100);}
								catch(InterruptedException e){return;}
								continue;
							}
						}
						
						tmpPiece = DLQueue.poll();
						
						//If I'm not download_complete but the queue is empty, wait for it to be full again.
						if(tmpPiece == null)
						{
							//See if I should be in an end game strategy.
							if(totalPendingPieces < (info.number_of_pieces() * .1))
							{
								for(int i = 0; i < pieces.size(); i++)
								{
									if(!file_manager.have(i))
									{
										for(int j = 0; j < pieces.get(i).peersWhoHave.size(); j++)
											pieces.get(i).peersWhoHave.get(j).request(i, 0, pieces.get(i).length);
									}
								}
							}
							try{Thread.sleep(100);}
							catch(InterruptedException e){return;}
							continue;
						}
						
						//Send a request packet for the desired piece to all connected peers that have it.
						for(int i = 0; i < tmpPiece.peersWhoHave.size(); i++)
							tmpPiece.peersWhoHave.get(i).request(tmpPiece.index, 0, tmpPiece.length);
						
						synchronized(downloadingPieces)
						{
							downloadingPieces++;
						}
					}
					else
					{
						try{Thread.sleep(100);}
						catch(InterruptedException e){return;}
						continue;
					}
				}
				
				//When download_complete, tell the PA to save the file.
				file_manager.save();
			}
		});
		
		downloadThread.start();
	}
	
	/**
	 * This directs the DM to start the upload process.
	 */
	private void startUploader()
	{
		//Construct the upload thread.
		uploadThread = new Thread(new Runnable()
		{
			public void run()
			{
				//Loop and process all upload requests.
				ULRequest tmpULRequest = null;
				ByteBuffer block = null;
				while(true)
				{
					//Check if I'm supposed to be paused.
					synchronized(this)
					{
						if(paused)
						{
							try{Thread.sleep(100);}
							catch(InterruptedException e){return;}
							continue;
						}
					}
					
					//Get the request from the queue. This is a blocking call.
					try{tmpULRequest = ULQueue.take();}
					catch(InterruptedException e){return;}
					
					//Check if the peer is still valid, and approved for uploading.
					if(tmpULRequest.peer == null)
						continue;
					if(tmpULRequest.peer.getDisconnected())
						continue;
					synchronized(downloaders)
					{
						synchronized(optimisticPeers)
						{
							if(!downloaders.contains(tmpULRequest.peer) && !optimisticPeers.contains(tmpULRequest.peer))
								continue;
						}
					}
					
					//Get the requested block from the PA.
					block = file_manager.retrieveDataBlock(tmpULRequest.index, tmpULRequest.offset, tmpULRequest.length);
					
					//Make sure we have the block.
					if (block == null)
						continue;
					
					//Send the requested block.
					tmpULRequest.peer.sendBlock(tmpULRequest.index, tmpULRequest.offset, block.array());
					
					//Update the uploaded counts.
					synchronized(totalUploaded)
					{totalUploaded += tmpULRequest.length;}
					synchronized(tmpULRequest.peer.uploaded)
					{tmpULRequest.peer.uploaded += tmpULRequest.length;}
				}
			}
		});
		
		uploadThread.start();
	}
	
	/**
	 * Starts the maintenance thread that prunes disconnected peers and performs the next step in the choking algorithm.
	 */
	private void startMaintenance()
	{
		//Construct the maintenance thread.
		maintenanceThread = new Thread(new Runnable()
		{
			public void run()
			{
				while(true)
				{
					//Wait for 10 seconds.
					try{Thread.sleep(MAINTENANCE_TIMER);}
					catch(InterruptedException e){return;}
					
					//Check the peers list for disconnected nodes and remove them.
					synchronized(peers)
					{
						for(int i = 0; i < peers.size(); i++)
						{
							//If the peer is disconnected, remove it.
							if(peers.get(i).getDisconnected())
							{
								Peer tmpPeer = peers.get(i);
								peers.remove(i);
								
								//If the peer was unchoked, remove it from the appropriate list.
								if(!tmpPeer.getAmChoking())
								{
									synchronized(downloaders)
									{downloaders.remove(tmpPeer);}
									synchronized(optimisticPeers)
									{optimisticPeers.remove(tmpPeer);}
									synchronized(waitList)
									{waitList.remove(tmpPeer);}
								}
								else
									synchronized(chokedPeers)
									{chokedPeers.remove(tmpPeer);}
								
								tmpPeer.close();
							}
						}
					}
					
					//Gather the current lowest download rate from peers in the downloaders list.
					long[] lowest = getLowestMetric();

					//Check if anyone should be removed from the waitList.
					boolean move = false;
					synchronized(waitList)
					{
						for(int i = 0; i < waitList.size(); i++)
						{
							if(!download_complete)
								synchronized(waitList.get(i).downloaded)
								{move = waitList.get(i).downloaded < lowest[0];}
							else
								synchronized(waitList.get(i).uploaded)
								{move = waitList.get(i).uploaded < lowest[0];}
							
							if(move)
							{
								waitList.get(i).chokePeer();
								synchronized(chokedPeers)
								{chokedPeers.add(waitList.get(i));}
								waitList.remove(i);
							}
						}
					}
					
					//Check if anyone should be moved to the waitList from the chokedPeers list.
					synchronized(waitList)
					{
						synchronized(chokedPeers)
						{
							move = false;
							for(int i = 0; i < chokedPeers.size(); i++)
							{
								if(!download_complete)
									synchronized(chokedPeers.get(i).downloaded)
									{move = chokedPeers.get(i).downloaded > lowest[0];}
								else
									synchronized(chokedPeers.get(i).uploaded)
									{move = chokedPeers.get(i).uploaded > lowest[0];}
								
								if(move)
								{
									chokedPeers.get(i).unchokePeer();
									waitList.add(chokedPeers.get(i));
									chokedPeers.remove(i);
								}
							}
						}
					}
					
					//See if anyone should be moved to the waitList from the optimisticPeers list.
					synchronized(optimisticPeers)
					{
						move = false;
						for(int i = 0; i < optimisticPeers.size(); i++)
						{
								if(!download_complete)
									synchronized(optimisticPeers.get(i).downloaded)
									{move = optimisticPeers.get(i).downloaded > lowest[0];}
								else
									synchronized(optimisticPeers.get(i).uploaded)
									{move = optimisticPeers.get(i).uploaded > lowest[0];}
									
								if(move)
								{
									synchronized(waitList)
									{waitList.add(optimisticPeers.get(i));}
									optimisticPeers.remove(i);
								}
						}
					}
					
					//See if anyone from the waitList should be moved to the downloaders list.
					synchronized(downloaders)
					{
						synchronized(waitList)
						{
							move = false;
							for(int i = 0; i < waitList.size(); i++)
							{
								if(!download_complete)
									synchronized(waitList.get(i).downloaded)
									{move = waitList.get(i).downloaded > lowest[0];}
								else
									synchronized(waitList.get(i).uploaded)
									{move = waitList.get(i).uploaded > lowest[0];}
								
								if(move)
								{
									//Remove the old downloader.
									downloaders.get(i).chokePeer();
									synchronized(chokedPeers)
									{chokedPeers.add(downloaders.get(i));}
									
									//Add the new downloader.
									downloaders.set((int)lowest[1], waitList.get(i));
									waitList.remove(i);
									
									//Recompute the lowest downloader.
									lowest = getLowestMetric();
								}
							}
						}
					}
					
					//Re-fill the optimisticPeer list every 3 steps.
					synchronized(optimisticPeers)
					{
						synchronized(chokedPeers)
						{
							if(chokedPeers.size() > 0)
							{
								//Reset state.
								optimisticPeers.clear();
								
								//Re-fill the optimisticPeers list.
								Random rand = new Random();

								for(int i = 0; i < MAX_OPTIMISTIC_PEERS; i++)
								{
									//Choose a random peer thats choked and unchoke it.
									int index = rand.nextInt(chokedPeers.size());
									chokedPeers.get(index).unchokePeer();
									chokedPeers.get(index).interestedInPeer();
									
									optimisticPeers.add(chokedPeers.get(index));
									
									chokedPeers.remove(index);
								}
							}
						}
					}
					
					//Rest the uploaded/downloaded counts for the next maintenance pass.
					synchronized(peers)
					{
						for(int i = 0; i < peers.size(); i++)
						{
							synchronized(peers.get(i).downloaded)
							{peers.get(i).downloaded = (long)0;}
							synchronized(peers.get(i).uploaded)
							{peers.get(i).uploaded = (long)0;}
						}
					}
				}
			}
		});
		
		maintenanceThread.start();
	}
	
	/**
	 * Starts a listener thread that accepts connections from new peers.
	 */
	private void startListener()
	{
		//Construct the maintenance thread.
		listenerThread = new Thread(new Runnable()
		{
			public void run()
			{
				listenerSocket = null;
				try {listenerSocket = new ServerSocket(listener_port);} 
				catch (Exception e) {System.err.println("ERROR: Unalbe to liste on provided port.");}
				
				while(true)
				{
					Socket tmpSocket = null;
					try{tmpSocket = listenerSocket.accept();}
					catch(Exception e){return;}
					
					String port_string = Integer.toString( listener_port );
					byte[] infoHash = info.info_hash().array();
					Peer tmpPeer = new Peer(tmpSocket.getInetAddress().getHostAddress(), port_string, "tmp", infoHash, my_peer_id, instance);
					peers.add(tmpPeer);
					connectPeer(tmpPeer, tmpSocket);
				}
				
			}
		});
		
		listenerThread.start();
	}
	
	/**
	 * This launches a thread that walks the given peer through connecting process.
	 * 
	 * @param peer The peer to be connected.
	 */
	private void connectPeer(final Peer peer, final Socket socket)
	{
		//Construct the connection thread.
		Thread connectPeer = new Thread(new Runnable()
		{
			public void run()
			{
				//Have the peer attempt connection and act accordingly.
				boolean connected = false;
				if(socket == null)
					connected = peer.connect();
				else
					connected = peer.connect(socket);
				
				if(connected)
				{	
					//Get the bitfield.
					byte[] bitfield = (byte[])file_manager.getBitField().array();
							
					
					for(int i  = 0; i < bitfield.length; i++)
					{
					    byte out = 0;
					    for (int j = 0 ; j < 8 ; j++) 
					    {
					        byte bit = (byte)(bitfield[i] & 1);
					        out = (byte)((out << 1) | bit);
					        bitfield[i] = (byte)(bitfield[i] >> 1);
					    }
					    
					    bitfield[i] = out;
					}
					
					//Check to see if it needs to be 0'd out.
					if(bitfield.length == 0)
					{
						bitfield = new byte[(int)Math.ceil(info.number_of_pieces() / (double)8)];
						
						for(int i = 0; i < bitfield.length; i++)
							bitfield[i] = 0;
					}
					
					//Send the bitfield.
					peer.sendBitfield(bitfield);
					
					//Notify the peer that I'm interested.
					peer.interestedInPeer();
					
					//If the downloaders list isn't full, add this peer to it.

					synchronized(downloaders)
					{
						if(downloaders.size() < MAX_DOWNLOADERS)
						{
							peer.unchokePeer();
							downloaders.add(peer);
							return;
						}
					}
					
					//If the optimisticPeers list isn't full, add this peer to it.
					synchronized(optimisticPeers)
					{
						if(optimisticPeers.size() < MAX_OPTIMISTIC_PEERS)
						{
							peer.unchokePeer();
							optimisticPeers.add(peer);
							return;
						}
					}
					
					//Otherwise, choke the peer and add it to the unchoked list.
					peer.chokePeer();
					synchronized(chokedPeers)
					{chokedPeers.add(peer);}
				}
				else
				{
					peers.remove(peer);
					peer.close();
				}
			}
		});
		
		//Start the connection thread.
		connectPeer.start();
	}
	
	/**
	 * Generates a 20 byte peer ID for downloading.
	 *  
	 * @return A String representation of the 20 byte peer ID.
	 */
	private static String generatePeerID()
	{
		Random rand = new Random();
		String characters = "1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
		
		char[] id = new char[20];
	    for (int i = 0; i < 20; i++)
	    {
	        id[i] = characters.charAt(rand.nextInt(characters.length()));
	    }
		
	    return new String(id);
	}
	
	/**
	 * Check to see if there is a port available between 6881 & 6889.
	 * @return the first available port onto which a socket is openable; null if no port is available.
	 * @author Thomas Travis
	 */
	private static Integer getAvailablePort(){
		
		ServerSocket socket = null;
		for( int i = 6881; i < 6890; i++ ){
			
			try{
				socket = new ServerSocket( i );
				socket.setReuseAddress(true);
				socket.close();
				return i;
			}
			catch( IOException e )
			{
			}
		}
		
		return null;
	}
	
	/**
	 * Create a 2D String array representing the contact information of the remote peers.  Index 0 corresponds
	 * to the IP, index 1 to the port, and index 2 to the remote peers self-selected peerID.
	 * 
	 * @author Thomas Travis
	 */
	private String[][] getPeerArray(){
		
		String [] IPPortConcat = tracker_connection.getPeerURLS();
		String[][] peer_IP_port_array = new String[ IPPortConcat.length ][ 3 ];
		for(int i = 0; i < IPPortConcat.length; i++ ){
			String concat = IPPortConcat[i];
			String IP = concat.substring(0, concat.indexOf(':'));
			String Port = concat.substring( concat.indexOf(':') + 1 );
			peer_IP_port_array[i][0] = IP;
			peer_IP_port_array[i][1] = Port;
			peer_IP_port_array[i][2] = ""; //Currently placeholder for peer_id
		}
		return peer_IP_port_array;
	}
	
	/**
	 * This computes the lowest downloader based on either upload rate or download rate depending on what state the DM is in.
	 * 
	 * @return A 2d array where the first element in the amound downloaded/uploaded and the second is the index of the offending peer.
	 */
	private long[] getLowestMetric()
	{
		long[] response = new long[2];
		long lowestMetric;
		
		if(!download_complete)
			lowestMetric = totalDownloaded;
		else
			lowestMetric = totalUploaded;
		
		synchronized(downloaders)
		{
			for(int i = 0; i < downloaders.size(); i++)
			{
				//Check if I should be comparing based on uploaded or downloaded rates.
				if(!download_complete)
				{
					synchronized(downloaders.get(i).downloaded)
					{
						//Compare based on the found rate.
						if(downloaders.get(i).downloaded < lowestMetric)
						{
							response[0] = downloaders.get(i).downloaded;
							response[1] = i;
						}
					}
				}
				else
				{
					synchronized(downloaders.get(i).uploaded)
					{
						//Compare based on the found rate.
						if(downloaders.get(i).uploaded < lowestMetric)
						{
							response[0] = downloaders.get(i).uploaded;
							response[1] = i;
						}
					}
				}
				

			}
		}
		
		return response;
	}
	
	private ArrayList<Peer> setRemotePeerList(){
		
		ArrayList<Peer> list = new ArrayList<Peer>();
		for( int i = 0; i < remote_peer_array.length; i++ ){
			
			String remote_IP = remote_peer_array[i][0];
			String remote_port = remote_peer_array[i][1];
			String remote_peer_id = remote_peer_array[i][2];
			byte[] info_hash = info.info_hash().array();
			
			//if(remote_IP.equals("172.31.145.89"))
			//{
				Peer p = new Peer( remote_IP, remote_port, remote_peer_id, info_hash, my_peer_id, this );
				list.add(p);
			//}
		}
		
		return list;
	}
	
	/**
	 * This is used to bundle all of the data the uploader needs to track for each request.
	 * In addition it supports equality to test for removal and insertion.
	 * I would make this a struct if I could.
	 */
	private class ULRequest 
	{
		public int index;
		public int offset;
		public int length;
		public Peer peer;
		
		/**
		 * A constructor for the ULRequest class, it does basic initialization and no argument checking.
		 * @param indexIn
		 * @param offsetIn
		 * @param lengthIn
		 * @param peerIn
		 */
		public ULRequest(int indexIn, int offsetIn, int lengthIn, Peer peerIn)
		{
			index = indexIn;
			offset = offsetIn;
			length = lengthIn;
			peer = peerIn;
		}
		
		/**
		 * An equality tester to support insertion and removal of ULRequests into queue's and array lists.
		 */
		public boolean equals(Object obj)
		{
			if(obj == null)
				return false;
			if(!(obj instanceof ULRequest))
				return false;
			if(obj == this)
				return true;
			
			ULRequest UL = (ULRequest)obj;
			
			synchronized(UL)
			{
				if(UL.peer == this.peer)
					if(UL.index == this.index)
						if(UL.offset == this.offset)
							if(UL.length == this.length)
								return true;
			}

			return false;
		}
	}
	
	/**
	 * This is used to track what pieces exist and some basic attributes about them.
	 * Most importantly it tracks a list of peers that claim to have a given piece.
	 * I would make this a struct if I could.
	 */
	private class Piece implements Comparable<Piece>
	{
		public int index;
		public int length;
		public ArrayList<Peer> peersWhoHave;
		
		/**
		 * A basic constructor for the Piece class that does initialization but no argument checking.
		 * @param i
		 */
		public Piece(int i, int lengthIn)
		{
			index = i;
			length = lengthIn;
			peersWhoHave = new ArrayList<Peer>();
		}
		
		/**
		 * Provides a way for the DLQueue to prioritize rarer pieces.
		 */
		public int compareTo(Piece DLIn)
		{
			int difference = 0;
			synchronized(DLIn.peersWhoHave)
			{
				difference =  peersWhoHave.size() - DLIn.peersWhoHave.size();
			}
			return difference;
		}
	}
}
