package cs352.RUBTClient.control;

import java.io.InputStream;
import java.net.URLConnection;
import java.net.URL;
import java.util.Map;

/**
 * The ContactTracker method contacts the tracker based on the information provided
 * in the torrent file, and retrieves the list of peer URLs/port numbers
 * and any other relevant information.
 * 
 * @Main_Author Yuriy Granaev
 * @Co_Authors Kyle Waranis and Thomas Travis
 */
public class ContactTracker
{
	private String my_peer_id;
	private int listener_port;
	private String [] remote_peer_URLs;
	public Thread ContactTrackerThread;
	int uploaded;
	int downloaded;
	int left;
	boolean initialGet;
	private DownloadManager dm;
	
	/**
	 * Constructor - performs the initial get request
	 * 
	 * @param my_peer_id The peer ID that this client will be using for this download session.
	 * @param listener_port The port that this client will be accepting connections on.
	 */
	
	public ContactTracker(String my_peer_id, int listener_port)
	{
		
		//Initialize
		this.my_peer_id = my_peer_id;
		this.listener_port = listener_port;
		this.uploaded = 0;
		this.downloaded = 0;
		this.left = 0;
		this.initialGet = true;
		
		remote_peer_URLs = null;
		
	}
	
	public String[] getPeerURLS(){
		
		if( remote_peer_URLs == null )
			return null;
		
		synchronized( remote_peer_URLs){
			return remote_peer_URLs;
		}
	}

	/**
	 * Provide to this ContactTracker object a reference to the DownloadManager it is supporting,
	 * then begin its thread action.
	 * @param dm - The DownloadManager instance this ContactTracker is supporting.
	 */
	public void start( DownloadManager dm ){
		
		this.dm = dm;
		
		ContactTrackerThread = new Thread( new Runnable(){
		
			public void run(){
				while (true){
					
					//Create the URL connection as a get request.
					URLConnection connection;
					if(initialGet)
						connection = notifyTracker("started");
					else
						connection = notifyTracker("");
					initialGet = false;
					
					//create input stream and read in bytes from connection
					byte[] responseBytes = null;
					try{
						InputStream is = connection.getInputStream();
						long length = is.available();
						responseBytes = new byte[(int) length];
						is.read(responseBytes);
						is.close();
					}catch(Exception e){errorOut(e, "ERROR: Unable to open input stream");}
					
					//Decode the response.
					TrackerResponseDecoder.decode(responseBytes);

					Map<String, Integer>trackerResponseMap = TrackerResponseDecoder.trackerResponseMap;
					remote_peer_URLs = TrackerResponseDecoder.peerURLs;
			        
			        int waitTime = 0;
			        //Determine interval to contact tracker again
			        if (trackerResponseMap.containsKey("interval")){
			        	waitTime = trackerResponseMap.get("interval");
			        }else if (trackerResponseMap.containsKey("min_interval")){
			        	waitTime = trackerResponseMap.get("min_interval");
			        }
			        
			        //Wait the specified time.
			        try{
			        	Thread.sleep(waitTime * 1000); //Convert to seconds.
			        } catch (InterruptedException e){return;}    
				}		
			}
		});
		
		ContactTrackerThread.start();
	}
	
	public void stop(){
		
		if( ContactTrackerThread.isAlive() )
			ContactTrackerThread.interrupt();
	}
	
	/**
	 * Sends a notification to the tracker with the correct event.
	 * @param eventIn
	 */
	private URLConnection notifyTracker(String eventIn){		
		
		//Set the event, if applicable.
		String event;
		if(eventIn == null)
			event = "";
		else if(eventIn.equals("started"))
			event = "started";
		else if(eventIn.equals("stopped"))
			event = "stopped";
		else if(eventIn.equals("completed"))
			event = "completed";
		else
			event = "";
		
		//Escape the info hash.
		String escaped_info_hash = escapeByteArray( dm.getSessionInfo().info_hash().array() );
		
		if( event.equals("started") ){
			downloaded = 0;
			uploaded = 0;
			left = dm.getSessionInfo().file_length();
		}
		else{
			downloaded = dm.getDownloaded();
			uploaded = dm.getUploaded();
			left = dm.getLeft();
		}
		
		//Construct the query as a string.
		String query = "peer_id=" + my_peer_id + "&" +
						"info_hash=" + escaped_info_hash + "&" +
						"port=" + Integer.toString(listener_port) + "&" +
						"uploaded="+uploaded+"&" +
						"downloaded="+downloaded+"&" +
						"left=" + left;
		
		if(!event.equals(""))
			query = query + "event=" + event;
		
		//Create the URL connection as a get request.
		URLConnection connection = null;
		try{connection = new URL(dm.getSessionInfo().announce_url() + "?" + query).openConnection();}
		catch(Exception e){errorOut(e, "ERROR: Unable to create URL connection.");}
		
		return connection;
	}
	
	/**
	 * Converts a byte array into a URL safe hex string.
	 * 
	 * @param data This is the binary data to be converted into a URL safe hex string.
	 * @return 
	 */
	private String escapeByteArray(byte[] data)
	{	
		//Lookup table: character for a half-byte
	    char[] validChars = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
	    
	    //Check args
	    if(data == null || data.length==0)
	    	return "";
	    
		// Encode byte data as a hex string.
	    char[] store = new char[data.length*2];
	    for(int i=0; i < data.length; i++)
	    {
	    	final int val = (data[i]&0xFF);
	    	final int charLoc = i << 1;
	    	store[charLoc] = validChars[val>>>4];
	    	store[charLoc+1] = validChars[val&0x0F];
	    }
	    String hexString = new String(store);
		
		//Parse the hex string in a URL encoding safe format.
		int length = hexString.length();
		char[] output = new char[length + (length / 2)];
		int i = 0;
		int j = 0;
		while(i < length)
		{
			output[j++] = '%';
			output[j++] = hexString.charAt(i++);
			output[j++] = hexString.charAt(i++);
		}
		
		//Return the output.
		return new String(output);
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


