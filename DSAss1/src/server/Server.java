package server;
import java.io.*;
import java.net.*;
import org.json.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
public class Server {
	
	// Declare the port number
	private static int port = 8080;
	
	// Identifies the user number connected
	private static int counter = 0;
	
	private static byte[] receiveData = new byte[1024];
    private static byte[] sendData = new byte[1024];
    
	private boolean ack = false;
	private final long RTT = 200;

	public void main(String[] args) throws IOException {
		
		// setup
		DatagramSocket serverSocket = new DatagramSocket(port);
		
		// Wait for connections.
        System.out.println("Waiting for connections");
        
		while(true){
			// get message
			DatagramPacket receivePacket = new DatagramPacket( receiveData, receiveData.length);
            serverSocket.receive(receivePacket);
            
            // Start a new thread for a connection
			Thread t = new Thread(() -> response(receivePacket));
			t.start();
            
            
            // read as a string
            String sentence = new String(receiveData);
            System.out.println("RECEIVED string: " + sentence);
            
            
            
            // send
//            String capitalizedSentence = sentence.toUpperCase();
//            sendData = capitalizedSentence.getBytes();
//            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientIp, clientPort);
//            serverSocket.send(sendPacket);		
		}
	}
	public void response(DatagramPacket receivePacket) throws IOException {
		// get client info
        InetAddress clientIp = receivePacket.getAddress();
        int clientPort = receivePacket.getPort();
        String receiveMessage = new String(receivePacket.getData());
        JSONObject receiveData = new JSONObject(receiveMessage);
        if (!verify(receiveData)) {
        	return;
        }
        
        
        
        
        
        
        // search dictionary
        
        
        
        
        
        
        
        // send answer
		DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientIp, clientPort);
		
		// prepare listening ack
		byte[] receiveData = new byte[1024];
		DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
		
		// retransmission and listen ack
		Thread t = new Thread(() -> {
			try {
				retransmission(clientSocket, sendPacket);
			} catch (InterruptedException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
		t.start();
		clientSocket.receive(receivePacket);
		if (receivePacket.getData().equals(sendData)) {
			ack = true;
		}
	}
	public void retransmission(DatagramSocket clientSocket, DatagramPacket sendPacket) throws InterruptedException, IOException {
		int times = 1;
		while(true) {
			Thread.sleep(RTT);
			if(ack == true) {
				return;
//			}else if (times > 10) {
//				return error;
			}else {
				clientSocket.send(sendPacket);
				times++;
			}
		}
	}
	public void genJSON(String word, String meaning) {
		JSONObject obj = new JSONObject();

	    obj.put("word", word);
	    obj.put("meaning", meaning);
	    
	    System.out.println(obj);
	    
		JSONObject data = new JSONObject();
		data.put("obj", obj);
		data.put("hash", makehash(obj));
		data.put("server", "");
		System.out.println(data);
	    deJSON(data);
	}
	public void deJSON(JSONObject data) {
		JSONObject obj = data.getJSONObject("obj");
		String hash = data.getString("hash");
		String word = obj.getString("word");
		String meaning = obj.getString("meaning");
		System.out.println("word = " + word);
		System.out.println("meaning = " + meaning);
		System.out.println("hash = " + hash);
		boolean val = hash.equals(makehash(data));
		System.out.println("val = " + val);
		
	}
	// check if ack, correct hash 
	public boolean verify(JSONObject data) {
		JSONObject obj = data.getJSONObject("obj");
		String hash = data.getString("hash");
		return data.has("ack") && hash.equals(makehash(data));
	}
	public String makehash(JSONObject obj) {
		String MD5 = null;
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(obj.toString().getBytes());
			byte[] byteData = md.digest();
			
			// byte to hex to string
			StringBuffer sb = new StringBuffer();
	        for (int i = 0; i < byteData.length; i++) {
	        	sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
	        }
	        MD5 = sb.toString();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return MD5;
	}
	
}