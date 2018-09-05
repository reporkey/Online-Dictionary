// need to make a all thread visible blockquering, which contains all acks received from main
// when each thread see the ack to itself, it stops.
// the problem is that what if ack does not receive successfully? wait time 10s?

package server;
import java.io.*;
import java.util.*;
import java.net.*;
import org.json.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class Methods {
	public static final String Query = "Query";
	public static final String Add = "Add";
	public static final String PUT = "PUT";
	public static final String Remove = "Remove";
    
	public static final int success = 0;
    public static final int fail = -1;
    public static final int notFound = 1;
    public static final int emptyDefination = 2;
}

class Dictionary extends Methods {
	private JSONObject dict;
	
	public Dictionary(String dictPath) throws IOException {
		String dictStr = null;
		BufferedReader br = new BufferedReader(new FileReader(dictPath));
		if ((dictStr = br.readLine()) != null){
			dict = new JSONObject(dictStr);
	    }
		br.close();
	}
	
	public String Query(String word){
		word = word.toUpperCase();
		String defination = null;
		if (dict.has(word)){
			defination = dict.getString(word);
		}
		return defination;
	}
	
	public int Add(String word, String defination){
		word = word.toUpperCase();
		int status = fail;
		if (!dict.has(word)){ 
		    dict.put(word, defination);
		    status = success;
		    System.out.println("success insert " + word + defination);
		}
		return status;
	}
	
	public int PUT(String word, String defination){
		word = word.toUpperCase();
		int status = fail;
		if (!dict.has(word)){
			status = Remove(word);
			if (status == success) {
				dict.put(word, defination);
				status = success;
			}
		}else {
			status = notFound;
		}
		return status;
	}
	
	public int Remove(String word){
		word = word.toUpperCase();
		int status = fail;
		if (dict.has(word)){
		    dict.remove(word);
		    status = success;
		}else {
			status = notFound;
		}
		return status;
	}
}

class RequestObj {
	private JSONObject obj = new JSONObject();
	private String type = "request";
	private String method;
	private String word;
	private String defination;
	
	public void read(JSONObject obj) {
		this.obj = obj;
		JSONObject data = obj.getJSONObject("data");
		this.method = data.getString("method");
		this.word = data.getString("word");
		this.defination = data.getString("defination");
	}
	
	public JSONObject getObj() {
		return obj;
	}

	public String getMethod() {
		return method;
	}

	public String getWord() {
		return word;
	}

	public String getDefination() {
		return defination;
	}
}

class ResponseObj {
	JSONObject obj = new JSONObject();
	private String type = "response";
	private String method;
	private String word;
	private String defination;
	private int status;
	
	public ResponseObj(String method, String word, String defination, int status) {
		this.method = method;
		this.word = word;
		this.defination = defination;
		this.status = status;
		JSONObject data = new JSONObject();
		data.put("type", this.type);
		data.put("method", this.method);
		data.put("word", this.word);
		data.put("defination", this.defination);
		data.put("status", this.status);
		obj.put("data", data);
		obj.put("checksum", Hash.hash(data));
	}
	
	public void read(JSONObject obj) {
		this.obj = obj;
		JSONObject data = obj.getJSONObject("data");
		this.method = data.getString("method");
		this.word = data.getString("word");
		this.defination = data.getString("defination");
		this.status = status;
	}
	
	public JSONObject getObj() {
		return obj;
	}
}

class Response extends Methods implements Runnable {
    private byte[] sendDatagram = new byte[2048];
	private byte[] receiveDatagram = new byte[2048];
	private Dictionary dictionary;
	
	private DatagramSocket socket;
	private DatagramPacket requestPacket;
	private InetAddress clientIp;
	private int clientPort;
	private String requestStr;
	private RequestObj requestObj;
	private ResponseObj responseObj;
	
	private boolean ack = false;
	private final long RTT = 200;
	
	public Response(DatagramPacket requestPacket, DatagramSocket socket, Dictionary dictionary) {
		this.requestPacket = requestPacket;
		this.dictionary = dictionary;
		this.clientIp = requestPacket.getAddress();
		this.clientPort = requestPacket.getPort();
        this.socket = socket;
//        this.socket.connect(clientIp, clientPort);
		requestStr = new String(requestPacket.getData(), 0, requestPacket.getLength());
	}
	
	public void run() {
//		int isverify = verify(receiveObj);
//		if (isverify == -1) {
//        	return;
//        }else if (isverify == 1) {
//        	// add to ack list
//        	return;
//        }
		requestObj = new RequestObj();
		requestObj.read(new JSONObject(requestStr));
		
		int status = fail;
		switch (requestObj.getMethod()) {
		case Query:
			String defination = dictionary.Query(requestObj.getWord());
			status = defination == null ? notFound : success;
			status = defination == "" ? emptyDefination : success;
			responseObj = new ResponseObj(requestObj.getMethod(), requestObj.getWord(), defination, status);
			break;
		
		case Add:
			status = dictionary.Add(requestObj.getWord(), requestObj.getDefination());
			responseObj = new ResponseObj(requestObj.getMethod(), requestObj.getWord(), requestObj.getDefination(), status);
			break;
		
		case Remove:
			status = dictionary.Remove(requestObj.getWord());
			responseObj = new ResponseObj(requestObj.getMethod(), requestObj.getWord(), requestObj.getDefination(), status);
			break;
		}
		sendDatagram = responseObj.getObj().toString().getBytes();
		
		// send packet
		DatagramPacket sendPacket = new DatagramPacket(sendDatagram, sendDatagram.length, clientIp, clientPort);
		try {
			socket.send(sendPacket);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
//		// prepare listening ack
//		DatagramPacket receivePacket = new DatagramPacket(receiveDatagram, receiveDatagram.length);
//		
//		// retransmission and listen ack
//		Thread t = new Thread(() -> {retransmission(socket, sendPacket);});
//		t.start();
//		try {
//			socket.receive(receivePacket);
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		if (receivePacket.getData().equals(sendDatagram)) {
//			ack = true;
//		}
	}
//	public void retransmission(DatagramSocket clientSocket, DatagramPacket sendPacket) 
//			throws InterruptedException, IOException {
//		int times = 1;
//		while(true) {
//			Thread.sleep(RTT);
//			if(ack == true) {
//				return;
////			}else if (times > 10) {
////				return error;
//			}else {
//				clientSocket.send(sendPacket);
//				times++;
//			}
//		}
//	}
	// check if ack, correct hash 
	public int verify(JSONObject data) {
		int isverify = -1;	// hash != content
		JSONObject obj = data.getJSONObject("obj");
		String checksum = data.getString("checksum");
		if (checksum.equals(Hash.hash(data))) {
			if (obj.getString("type").equals("client")){
				isverify = 0;	// normal request
			}else {
				isverify = 1;	// ack
			}
		}
		return isverify;
	}
}

class Hash {
	private static String MD5 = null;
	public static String hash(JSONObject obj) {
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

public class Server {
	
	// Declare the port number
	private static int port = 8080;
	
	// Identifies the user number connected
	private static int counter = 0;
	    
    private static final String dictPath = "./src/server/dictionary.json";

	public static void main(String[] args) throws IOException {
		
		// setup
		Dictionary dictionary = new Dictionary(dictPath);
				   
		// Wait for connections.
		DatagramSocket socket = new DatagramSocket(port);
        System.out.println("Waiting for connections");
        
        
		while(true){
			// get message
        	
        	byte[] receiveDatagram = new byte[2048];
			DatagramPacket receivePacket = new DatagramPacket(receiveDatagram, receiveDatagram.length);
			socket.receive(receivePacket);	
            // Start a new thread for a connection
            Thread myThread = new Thread(new Response(receivePacket, socket, dictionary));
            myThread.start();
            
//            // read as a string
//            String sentence = new String(receiveDatagram);
//            System.out.println("RECEIVED string: " + sentence);
//            
//            
//            
            // send
//            String capitalizedSentence = sentence.toUpperCase();
//            sendDatagram = capitalizedSentence.getBytes();
//            DatagramPacket sendPacket = new DatagramPacket(sendDatagram, sendDatagram.length, clientIp, clientPort);
//            serverSocket.send(sendPacket);		
		}
	}
}