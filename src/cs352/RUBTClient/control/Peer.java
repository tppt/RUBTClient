/**
 * @Authors Kyle Waranis, Thomas Travis, Yuriy Garnaev
 */


package cs352.RUBTClient.control;

import java.net.*;
import java.util.Arrays;
import java.io.*;
import java.nio.ByteBuffer;
import java.net.InetSocketAddress;

public class Peer 
{
	public Long downloaded; //This should be reset by the maintenance thread.
	public Long uploaded; //This should be reset by the maintenance thread.
	
	private String ip;
	private int port;
	private byte[] infoHash;
	private String peerID;
	//private String remotePeerID;
	
	private boolean amChoking;		//True when I have choked the remote peer
	private boolean peerChoking;	//True when the remote peer has choked me
	private boolean amInterested;	//True when I am interested in the remote peer
	private boolean peerInterested;	//True when the remote peer is interested in me
	private boolean disconnected;
	
	private Thread keepAliveThread;
	private Thread listenerThread;
	private DownloadManager DM;
	
	private Socket peerSock;
	private DataInputStream fromPeer;
	private DataOutputStream toPeer;
	
	/**
	 * Creates a peer with the given IP.
	 * 
	 * @param peerIPIn IP address of the peer to connect to.
	 * @param portIn The port for the given peer.
	 * @param InfoHashIn The info hash for the requested download.
	 * @param peerIDIn The ID of this client to be given to the remote peer.
	 */
	public Peer(String ipIn, String portIn, String remotePeerIDIn, byte[] infoHashIn, String peerIDIn, DownloadManager DMIn) throws IllegalArgumentException
	{
		//Validate the arguments.
		if(ipIn == null)
			throw new IllegalArgumentException();
		if(portIn == null)
			throw new IllegalArgumentException();
		if(remotePeerIDIn == null)
			throw new IllegalArgumentException();
		if(infoHashIn.length != 20)
			throw new IllegalArgumentException();
		if(peerIDIn.length() != 20)
			throw new IllegalArgumentException();
		if(DMIn == null)
			throw new IllegalArgumentException();
		
		//Initialize variables.
		ip = ipIn;
		port = Math.abs(Integer.parseInt(portIn));
		infoHash = infoHashIn;
		peerID = peerIDIn;
		DM = DMIn;
		//remotePeerID = remotePeerIDIn;
		downloaded = new Long(0);
		uploaded = new Long(0);
		
		amChoking = true;
		peerChoking = true;
		amInterested = false;
		peerInterested = false;
		disconnected = true;
	}
	
	/**
	 * Connects a peer to the incoming connection.
	 * @param sockIn The socket for the incoming peer.
	 * @return True if the connection was successfull.
	 */
	public boolean connect(Socket sockIn)
	{
		//Create the socket for the peer.
		if(sockIn == null)
			return false;
		
		peerSock = sockIn;
		
		try{peerSock.setSoTimeout(120000);} 
		catch(SocketException e){errorOut(e, "ERROR: Unable to set socket timeout.");}
		
		//Create the input/output streams.
		try{toPeer = new DataOutputStream(peerSock.getOutputStream());}
		catch(Exception e){errorOut(e, "ERROR: Unable to get output stream.");}
		
		try{fromPeer = new DataInputStream(peerSock.getInputStream());}
		catch(Exception e){errorOut(e, "ERROR: Unable to get input stream.");}
		
		//Listen for a handshake
		byte[] buffer = new byte[68];
		try{fromPeer.readFully(buffer);}
		catch(SocketTimeoutException e){disconnected = true; return false;}
		catch(EOFException e){disconnected = true; return false;}
		catch(IOException e){disconnected = true; return false;}
		
		//Check if the response is an acceptable handshaking message.
		String protocol = "BitTorrent protocol";
		try
		{
			if(buffer[0] == protocol.length())
			{
				if(new String(buffer, 1, protocol.length(), "US-ASCII").equals(protocol))
				{
					if(Arrays.equals(Arrays.copyOfRange(buffer, 28, infoHash.length + 28), infoHash));
					else
						return false;
				}
				else
					return false;
			}
			else
				return false;
		}
		catch(Exception e){errorOut(e, "ERROR: Invalid charset encoding.");}
		
		//Assemble the handshaking message.
		ByteArrayOutputStream byteOut= null;
		try 
		{
			byteOut = new ByteArrayOutputStream();
			byteOut.write(protocol.length());
			byteOut.write(protocol.getBytes("US-ASCII"));
			
			for(int i = 0; i < 8; i++)
				byteOut.write(0);
			
			byteOut.write(infoHash);
			byteOut.write(peerID.getBytes());
		} 
		catch (Exception e) {errorOut(e, "ERROR: Unable to generate handshake message.");}
		
		byte[] handshake = byteOut.toByteArray();
		
		//Send the handshaking message.
		try{toPeer.write(handshake);}
		catch(Exception e){errorOut(e, "ERROR: Unable to send handshake to peer.");}
		
		disconnected = false;
		
		//Start the listener thread.
		startListener();
		
		//Start the keep alive packets.
		keepAlive();
		
		return true;
	}
	
	/**
	 * Opens a socket to the remote peer and performs handshaking.
	 * 
	 * @return Returns true if the connection was successful, else false.
	 */
	public boolean connect()
	{
		//Create the socket for the peer.
		peerSock = new Socket();
		
		//Connect the newly created socket. This way the connection can be closed mid-connection.
		try{peerSock.connect(new InetSocketAddress(ip, port), 120000);;}
		catch(Exception e){disconnected = true; return false;}
		try{peerSock.setSoTimeout(120000);} 
		catch(SocketException e){errorOut(e, "ERROR: Unable to set socket timeout.");}
		
		//Create the input/output streams.
		try{toPeer = new DataOutputStream(peerSock.getOutputStream());}
		catch(Exception e){errorOut(e, "ERROR: Unable to get output stream.");}
		
		try{fromPeer = new DataInputStream(peerSock.getInputStream());}
		catch(Exception e){errorOut(e, "ERROR: Unable to get input stream.");}
		
		//Assemble the handshaking message.
		ByteArrayOutputStream byteOut= null;
		String protocol = "BitTorrent protocol";
		try 
		{
			byteOut = new ByteArrayOutputStream();
			byteOut.write(protocol.length());
			byteOut.write(protocol.getBytes("US-ASCII"));
			
			for(int i = 0; i < 8; i++)
				byteOut.write(0);
			
			byteOut.write(infoHash);
			byteOut.write(peerID.getBytes());
		} 
		catch (Exception e) {errorOut(e, "ERROR: Unable to generate handshake message.");}
		
		byte[] handshake = byteOut.toByteArray();
		
		//Send the handshaking message.
		try{toPeer.write(handshake);}
		catch(Exception e){errorOut(e, "ERROR: Unable to send handshake to peer.");}
		
		//Listen for a response
		byte[] buffer = new byte[68];
		try{fromPeer.readFully(buffer);}
		catch(SocketTimeoutException e){disconnected = true; return false;}
		catch(EOFException e){disconnected = true; return false;}
		catch(IOException e){disconnected = true; return false;}
		
		//Check if the response is an acceptable handshaking message.
		try
		{
			if(buffer[0] == protocol.length())
			{
				if(new String(buffer, 1, protocol.length(), "US-ASCII").equals(protocol))
				{
					if(Arrays.equals(Arrays.copyOfRange(buffer, 28, infoHash.length + 28), infoHash))
					{
						disconnected = false;
						
						//Start the listener thread.
						startListener();
						
						//Start the keep alive packets.
						keepAlive();
						
						return true;
					}
					else
						return false;
				}
				else
					return false;
			}
			else
				return false;
		}
		catch(Exception e){errorOut(e, "ERROR: Invalid charset encoding.");}
		
		return false;
	}
	
	/**
	 * Waits for a response from the remote peer and parses it.
	 */
	public void startListener()
	{
		//Construct the listener thread.
		listenerThread = new Thread(new Runnable()
		{
			public void run()
			{				
				//Loop and parse responses received for this peer.
				byte[] lengthBA = new byte[4];
				byte[] buffer = null;
				while(!disconnected)
				{
					int length = 0;
					
						if(peerSock.isConnected())
						{
							//Read the length of the incoming message from the input stream
							try{fromPeer.readFully(lengthBA);}
							catch(SocketTimeoutException e){disconnected = true; return;}
							catch(EOFException e){disconnected = true; return;}
							catch(IOException e){disconnected = true; return;}
						}

						//Cast the length from a byte array to an integer
						length = java.nio.ByteBuffer.wrap(lengthBA).order(java.nio.ByteOrder.BIG_ENDIAN).getInt();
						
						//Ignore keep alive packets.
						if(length == 0)
							continue;
						
						//Disconnect if I'm given a negative length
						if(length < 0)
						{
							disconnected = true;
							return;
						}
						
						//Create a buffer to read the incoming message
						buffer = new byte[length];
						
						if(peerSock.isConnected())
						{
							//Read the incoming message.
							try{fromPeer.readFully(buffer);}
							catch(SocketTimeoutException e){disconnected = true; return;}
							catch(EOFException e){disconnected = true; return;}
							catch(IOException e){disconnected = true; return;}
						}
					
					//Pass the response off to be parsed.
					parseResponse(length, buffer);
				}
			}
		});
		
		//Start the listener thread.
		listenerThread.start();
	}
	
	/**
	 * Records that the peer has unchoked this client.
	 */
	public synchronized void unchokeMe()
	{
		peerChoking = false;
	}
	
	/**
	 * Sends an unchoke packet to the peer.
	 */
	public void unchokePeer()
	{
		//Make sure I'm still connected.
		if(disconnected)
			return;
		
		//Assemble and send the unchoke packet.
		ByteArrayOutputStream byteOut= null;
		try 
		{
			byteOut = new ByteArrayOutputStream();
			byteOut.write(0); //length
			byteOut.write(0); //length
			byteOut.write(0); //length
			byteOut.write(1); //length
			byteOut.write(1); //id
		} 
		catch (Exception e) {errorOut(e, "ERROR: Unable to generate unchoke message.");}
				
		byte[] message = byteOut.toByteArray();
				
		try{toPeer.write(message);}
		catch(SocketException e){return;}
		catch(Exception e){errorOut(e, "ERROR: Unable to send unchoke to peer.");}
		
		synchronized(this)
		{
			this.amChoking = false;
		}
	}
	
	/**
	 * Records that the peer has choked this client.
	 */
	public synchronized void chokeMe()
	{
		peerChoking = true;
	}
	
	/**
	 * Sends an choke packet to the peer.
	 */
	public void chokePeer()
	{
		//Make sure I'm still connected.
		if(disconnected)
			return;
		
		//Assemble and send the choke packet.
		ByteArrayOutputStream byteOut= null;
		try 
		{
			byteOut = new ByteArrayOutputStream();
			byteOut.write(0); //length
			byteOut.write(0); //length
			byteOut.write(0); //length
			byteOut.write(1); //length
			byteOut.write(0); //id
		} 
		catch (Exception e) {errorOut(e, "ERROR: Unable to generate choke message.");}
				
		byte[] message = byteOut.toByteArray();
				
		try{toPeer.write(message);}
		catch(SocketException e){return;}
		catch(Exception e){errorOut(e, "ERROR: Unable to send choke to peer.");}
		
		synchronized(this)
		{
			this.amChoking = true;
		}
	}
	
	/**
	 * Records that the peer is interested in downloading from me.
	 */
	public synchronized void interestedInMe()
	{
		peerInterested = true;
	}
	
	/**
	 * Sends an interested packet to the peer.
	 */
	public void interestedInPeer()
	{
		//Make sure I'm still connected.
		if(disconnected)
			return;
		
		//Have I already notified the peer of my interest?
		if(amInterested)
			return;
		
		//Assemble and send the interested packet.
		ByteArrayOutputStream byteOut= null;
		try 
		{
			byteOut = new ByteArrayOutputStream();
			byteOut.write(0); //length
			byteOut.write(0); //length
			byteOut.write(0); //length
			byteOut.write(1); //length
			byteOut.write(2); //id
		} 
		catch (Exception e) {errorOut(e, "ERROR: Unable to generate interested message.");}
				
		byte[] message = byteOut.toByteArray();
				
		try{toPeer.write(message);}
		catch(SocketException e){return;}
		catch(Exception e){errorOut(e, "ERROR: Unable to send interested to peer.");}
		
		synchronized(this)
		{
			this.amInterested = true;
		}
	}
	
	/**
	 * Records that the peer is not interested in downloading from me.
	 */
	public synchronized void notInterestedInMe()
	{
		peerInterested = false;
	}
	
	/**
	 * Sends a  not interested packet to the peer.
	 */
	public void notInterestedInPeer()
	{
		//Make sure I'm still connected.
		if(disconnected)
			return;
		
		//Assemble and send the not interested packet.
		ByteArrayOutputStream byteOut= null;
		try 
		{
			byteOut = new ByteArrayOutputStream();
			byteOut.write(0); //length
			byteOut.write(0); //length
			byteOut.write(0); //length
			byteOut.write(1); //length
			byteOut.write(3); //id
		} 
		catch (Exception e) {errorOut(e, "ERROR: Unable to generate not interested message.");}
				
		byte[] message = byteOut.toByteArray();
				
		try{toPeer.write(message);}
		catch(SocketException e){return;}
		catch(Exception e){errorOut(e, "ERROR: Unable to send not interested to peer.");}
		
		synchronized(this)
		{
			this.amInterested = false;
		}
	}
	
	/**
	 * Notifies the peer that it has the given index. This should be called after receiving a piece of the file.
	 */
	public void have(int index)
	{
		//Make sure I'm still connected.
		if(disconnected)
			return;
		
		//Assemble and send the have packet.
		ByteArrayOutputStream byteOut= null;
		try 
		{
			byteOut = new ByteArrayOutputStream();
			byteOut.write(0); //length
			byteOut.write(0); //length
			byteOut.write(0); //length
			byteOut.write(5); //length
			byteOut.write(4); //id
			byteOut.write(toBytes(index));
		} 
		catch (Exception e) {errorOut(e, "ERROR: Unable to generate have message.");}
				
		byte[] message = byteOut.toByteArray();
				
		try{toPeer.write(message);}
		catch(SocketException e){return;}
		catch(Exception e){errorOut(e, "ERROR: Unable to send have message to peer.");}
	}
	
	/**
	 * Requests the data at the given index beginning at an offset for the given length.
	 */
	public void request(int index, int begin, int length)
	{	
		//Make sure I'm still connected.
		if(disconnected)
			return;
		
		//make sure I'm unchoked and interested.
		if(peerChoking || !amInterested)
			return;
		
		//Assemble and send the request packet.
		ByteArrayOutputStream byteOut= null;
		try 
		{
			byteOut = new ByteArrayOutputStream();
			byteOut.write(0); //length
			byteOut.write(0); //length
			byteOut.write(0); //length
			byteOut.write(13); //length
			byteOut.write(6); //id
			byteOut.write(toBytes(index));
			byteOut.write(toBytes(begin));
			byteOut.write(toBytes(length));
		} 
		catch (Exception e) {errorOut(e, "ERROR: Unable to generate request message.");}
				
		byte[] message = byteOut.toByteArray();
				
		try{toPeer.write(message);}
		catch(SocketException e){return;}
		catch(Exception e){errorOut(e, "ERROR: Unable to send request to peer.");}
	}
	
	/**
	 * Sends the given data at the given index beginning at an offset.
	 */
	public void sendBlock(int index, int begin, byte[] block)
	{	
		//Make sure I'm still connected.
		if(disconnected)
			return;
		
		//make sure I'm not choking them and they're interested.
		if(amChoking || !peerInterested)
			return;
		
		//Assemble and send the block packet.
		ByteArrayOutputStream byteOut= null;
		try 
		{
			byteOut = new ByteArrayOutputStream();
			byteOut.write(ByteBuffer.allocate(4).putInt(block.length + 9).array()); //length
			byteOut.write(7); //id
			byteOut.write(toBytes(index));
			byteOut.write(toBytes(begin));
			byteOut.write(block);
		} 
		catch (Exception e) {errorOut(e, "ERROR: Unable to generate request message.");}
				
		byte[] message = byteOut.toByteArray();
				
		try{toPeer.write(message);}
		catch(SocketException e){return;}
		catch(Exception e){errorOut(e, "ERROR: Unable to send block to peer.");}
	}
	
	/**
	 * Requests the data at the given index beginning at an offset for the given length.
	 */
	public void sendBitfield(byte[] bitfield)
	{	
		//Make sure I'm still connected.
		if(disconnected)
			return;
		
		//Assemble and send the bitfield packet.
		ByteArrayOutputStream byteOut= null;
		try 
		{
			byteOut = new ByteArrayOutputStream();
			byteOut.write(ByteBuffer.allocate(4).putInt(bitfield.length + 1).array()); //length
			byteOut.write(5); //id
			byteOut.write(bitfield);
		} 
		catch (Exception e) {errorOut(e, "ERROR: Unable to generate request message.");}
				
		byte[] message = byteOut.toByteArray();
				
		try{toPeer.write(message);}
		catch(SocketException e){return;}
		catch(Exception e){errorOut(e, "ERROR: Unable to send bitfield to peer.");}
	}
	
	/**
	 *  Gets the amChoking value.
	 * @return True when this peer has choked the remote peer.
	 */
	public synchronized boolean getAmChoking()
	{
		return amChoking;
	}
	
	/**
	 *  Gets the peerChoking value.
	 * @return True when the remote peer has choked this peer.
	 */
	public synchronized boolean getPeerChoking()
	{
		return peerChoking;
	}
	
	/**
	 *  Gets the amInterested value.
	 * @return True when this peer is interested in the remote peer.
	 */
	public synchronized boolean getAmInterested()
	{
		return amInterested;
	}
	
	/**
	 *  Gets the peerInterested value.
	 * @return True when the remote peer is interested in this peer.
	 */
	public synchronized boolean getPeerInterested()
	{
		return peerInterested;
	}
	
	/**
	 *  Gets the disconnected value.
	 * @return True when this peer has been disconnected from the remote peer.
	 */
	public synchronized boolean getDisconnected()
	{
		return disconnected;
	}
	
	/**
	 * Calling this will interrupt and close all child threads that are active for the peer.
	 */
	public void close()
	{
		//Record that the peer is disconnected.
		disconnected = true;
		
		//Close the threads
		if(keepAliveThread != null)
			keepAliveThread.interrupt();
		if(listenerThread != null)
			listenerThread.interrupt();
			
		if(peerSock != null)
		{
			synchronized(peerSock)
			{
				//Close the socket and input/output streams.
				try
				{
					if(toPeer != null)
						toPeer.close();
					if(fromPeer != null)
						fromPeer.close();

					peerSock.close();
				}
				catch(Exception e){System.err.println("ERROR: Unable to properly close socket.");}
			}
		}
	}
	
	/**
	 * An equality tester to support insertion and removal of Peers into array lists.
	 */
	public boolean equals(Object obj)
	{
		if(obj == null)
			return false;
		if(!(obj instanceof Peer))
			return false;
		if(obj == this)
			return true;
		
		Peer peer = (Peer)obj;
		
		if(this.ip.equals(peer.ip) && this.port == peer.port)
			return true;
		else
			return false;
	}
	
	/**
	 * Provides a way for the peerQueue to prioritize better peers.
	 */
	public int compareTo(Peer peer)
	{
		int difference = 0;

		if(!DM.download_complete)
		{
			synchronized(downloaded)
			{
				synchronized(peer.downloaded)
				{
					difference =  (int)(downloaded - peer.downloaded);
				}
			}
		}
		else
		{
			synchronized(uploaded)
			{
				synchronized(peer.uploaded)
				{
					difference =  (int)(uploaded - peer.uploaded);
				}
			}
		}
		
		return difference;
	}
	
	/**
	 * Sends a keep alive command every two minutes.
	 */
	private void keepAlive()
	{
		//Create the keep alive thread.
		keepAliveThread = new Thread(new Runnable()
		{
			public void run()
			{
				while(!disconnected)
				{
					//Wait two minutes between keep alive packets.
					try{Thread.sleep(120000);}
					catch(InterruptedException e){return;}
					
					//Send the packet
					try
					{
						if(peerSock.isConnected())
							toPeer.write(0);
					}
					catch(Exception e)
					{return;}
				}
			}
		});
		
		//Start the keep alive thread.
		keepAliveThread.start();
	}
	
	/**
	 * Parses the response from the peer and performs the correct action.
	 * If the response was a 'piece' then it will return the payload.
	 * NOTE: Methods called from this MUST be a-sync safe since this is called from the
	 * getResponse method.
	 *
	 * @param The length of the response.
	 * @param The response the peer.
	 */
	private void parseResponse(int length, byte[] response)
	{
		//Gather the ID.
		byte responseID = response[0];
		
		//Perform some operation based on the ID.
		switch(responseID)
		{
			case 0: chokeMe();
				break;
			case 1: unchokeMe();
				break;
			case 2: interestedInMe();
				break;
			case 3: notInterestedInMe();
				break;
			case 4: DM.registerHave(Arrays.copyOfRange(response, 1, length), this);
				break;
			case 5: DM.registerBitfield(Arrays.copyOfRange(response, 1, length), this);
				break;
			case 6: DM.registerRequest(Arrays.copyOfRange(response, 1, 5), Arrays.copyOfRange(response, 5, 9), Arrays.copyOfRange(response, 9, 13), this);
				break;
			case 7: DM.registerPiece(length, response, this);
				break;
			case 8: DM.registerCancel(Arrays.copyOfRange(response, 1, 5), Arrays.copyOfRange(response, 5, 9), Arrays.copyOfRange(response, 9, 13), this);
				break;
		}
		
		return;
	}
	
	/**
	 * Converts the given byte array in big-endian form to an integer.
	 * @param i The byte array in big-endian form.
	 * @return An integer representation of the byte array.
	 */
	private byte[] toBytes(int i)
	{
		  byte[] result = new byte[4];

		  result[0] = (byte) (i >> 24);
		  result[1] = (byte) (i >> 16);
		  result[2] = (byte) (i >> 8);
		  result[3] = (byte) (i /*>> 0*/);

		  return result;
	}
	
	/**
	 * This force closes the application and prints debugging information.
	 * 
	 * @param e The exception forcing the halt of the application.
	 * @param error A string to be printed to standard error.
	 */
	private void errorOut(Exception e, String error)
	{
		System.err.println(error);
		
		if(e != null)
			e.printStackTrace();
		
		System.exit(1);
	}

}
