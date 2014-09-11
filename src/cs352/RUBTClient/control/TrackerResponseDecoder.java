package cs352.RUBTClient.control;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import cs352.RUBTClient.resources.Bencoder2;
import cs352.RUBTClient.resources.BencodingException;

/** TrackerResponseDecoder contains decoded information received from the tracker:
 * peerURLs - a string array of peer URLs
 * trackerResponseMap - <String, Integer> hash map containing all other information
 * @author Yuriy Garnaev
 *
 */
public class TrackerResponseDecoder {
	public static String[] peerURLs;
	public static Map<String, Integer> trackerResponseMap;
	
	public int complete;
	public int downloaded;
	public int incomplete;
	public int interval;
	public int minInterval;
	
	public TrackerResponseDecoder(){}

	public static byte[] responseBytes = null;
	
	/**the decode method populates the trackerResponseMap with un-Bencoded 
	 * key-value pairs from the tracker response
	 * 
	 * @param rB - bytes returned by the tracker
	 */
	public static void decode(byte[] rB){
		responseBytes=rB;
		String response = new String(responseBytes);

		trackerResponseMap = new HashMap<String, Integer>();
		char start;
		String currentSegment;
		//boolean dictionary = false;
		int end = 0;
		int stringLength = 0;
		int value=0;
		String key;
		//Loop to read through the tracker response 
		while (!response.isEmpty()){
			start=response.charAt(0);
			switch (start){
			//case 'd' - if this is the start of a dictionary
				case 'd':
					response = response.substring(1);
					//dictionary = true;
					continue;
			//case 'e' - end of a dictionary
				case 'e':
					response = response.substring(1);
					//dictionary = false;	
					continue;
			//default case - elements within the dictionary
				default:
					
					currentSegment=response.substring(0,response.indexOf(':'));
					stringLength = Integer.parseInt(currentSegment);
					response = response.substring(response.indexOf(':')+1);
					currentSegment = response.substring(0,stringLength);
					key = currentSegment;				
					response = response.substring(stringLength);

					
					//acquire the integer associated with current segment
					if (response.charAt(0)=='i'){
						end = response.indexOf('e');
						currentSegment=response.substring(1,end);
						value = Integer.parseInt(currentSegment);					
						response = response.substring(end+1);

						//add to hash map
						trackerResponseMap.put(key, value);
					//key = peers - calls the decodePeerList method to decode the bytes
					} else if (key.equals("peers")){
						//currentSegment=response.substring(0,response.indexOf(':'));
						//stringLength = Integer.parseInt(currentSegment);
						//response = response.substring(response.indexOf(':')+1);
						//currentSegment = response.substring(0,stringLength);						
						//response = response.substring(stringLength);

						try {
							decodePeerList();
						}catch (BencodingException e){
							System.out.println("ERROR - Bencoding exception");
							System.exit(1);
						}
						response="";
					}
					//System.out.println(key +"|"+value);
					key = "";
					value = 0;
					continue;		
			}
			
			
		}
	}
	/**decodePeerList populates the peerURLs array with the peer URLs
	 * uses the Bencoder2.decode method
	 * @throws BencodingException
	 */
	private static void decodePeerList() throws BencodingException{
		Map peerMap = (Map<ByteBuffer,Object>)Bencoder2.decode(responseBytes);
		peerURLs = decodeCompressedPeers(peerMap);
	}
	
	/** Helper method provided by Jeff Ames
	 * Takes in a hash map tracker response and extracts the peer URLs
	 * 
	 * @param map
	 * @return string array of peer URLs
	 */
	 public static String[] decodeCompressedPeers(Map map)
	    {
	        ByteBuffer peers = (ByteBuffer)map.get(ByteBuffer.wrap("peers".getBytes()));
	        ArrayList<String> peerURLs = new ArrayList<String>();
	        try {
	            while (true) {
	                String ip = String.format("%d.%d.%d.%d",
	                    peers.get() & 0xff,
	                    peers.get() & 0xff,
	                    peers.get() & 0xff,
	                    peers.get() & 0xff);
	                int firstByte = (0x000000FF & ((int)peers.get()));
	                int secondByte = (0x000000FF & ((int)peers.get()));
	                int port  = (firstByte << 8 | secondByte);
	                //int port = peers.get() * 256 + peers.get();
	                peerURLs.add(ip + ":" + port);
	            }
	        } catch (BufferUnderflowException e) {
	            // done
	        }
	        return peerURLs.toArray(new String[peerURLs.size()]);
	    }
}
