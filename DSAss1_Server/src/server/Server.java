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

class Utilities {
	public static final String Query = "Query";
	public static final String Add = "Add";
	public static final String Remove = "Remove";
    
	public static final int success = 0;
    public static final int fail = -1;
    public static final int notFound = 1;
    public static final int emptyDefinition = 2;
    public static final int wordExisted = 3;

	public final long RTT = 2000;
	public final int retransmissionCount = 5;

	public static String hash(JSONObject obj) {
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
			e.printStackTrace();
		}
		return MD5;
	}
}

class Dictionary extends Utilities {
	private JSONObject dict;
	
	public Dictionary(String dictPath) {
		String dictStr = null;
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(dictPath));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(-1);
		}
		try {
			if ((dictStr = br.readLine()) != null){
				dict = new JSONObject(dictStr);
			}
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public String Query(String word){
		word = word.toUpperCase();
		String definition = "";
		if (dict.has(word)){
			definition = dict.getString(word);
		}
		return definition;
	}
	
	synchronized public int Add(String word, String definition){
		word = word.toUpperCase();
		int status = fail;
		if (definition.equals("")) {
			status = emptyDefinition;
		}else {
			if (!dict.has(word)){ 
			    dict.put(word, definition);
			    status = success;
			    System.out.println("success insert " + word + definition);
			}else {
				status = wordExisted;
			}
		}
		return status;
	}
	
	synchronized public int Remove(String word){
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

class RequestObj extends Utilities{
	private JSONObject obj = new JSONObject();
	private String type = "request";
	private String method;
	private String word;
	private String definition;
	
	public void read(JSONObject obj) {
		this.obj = obj;
		JSONObject data = obj.getJSONObject("data");
		this.method = data.getString("method");
		this.word = data.getString("word");
		this.definition = data.getString("definition");
		if (data.has("type")){
			this.type = data.getString("type");
		}
	}

	public String getMethod() {
		return method;
	}

	public String getWord() {
		return word;
	}

	public String getDefinition() {
		return definition;
	}
}

class ResponseObj extends Utilities{
	private JSONObject obj = new JSONObject();
	private String type = "response";
	private String method = "";
	private String word = "";
	private String definition = "";
	private int status = fail;
	
	public ResponseObj(String method, String word, String definition, int status) {
		this.method = method;
		this.word = word;
		this.definition = definition;
		this.status = status;
		JSONObject data = new JSONObject();
		data.put("type", this.type);
		data.put("method", this.method);
		data.put("word", this.word);
		data.put("definition", this.definition);
		data.put("status", this.status);
		obj.put("data", data);
		obj.put("checksum", hash(data));
	}
	
	public JSONObject getObj() {
		return obj;
	}
}

class AckQueue {
	private HashMap queue;

	public AckQueue(){
		queue = new HashMap();
	}

	public synchronized void addAck(InetAddress address, int port, DatagramPacket requestPacket) {
		String destination = address.getHostAddress() + port;
		queue.put(destination, requestPacket);
	}

	public DatagramPacket hasAck(InetAddress address, int port){
		String destination = address.getHostAddress() + port;
		System.out.println("QUEUE = " + queue);
		if (queue.containsKey(destination)) {
			return (DatagramPacket) queue.get(destination);
		}else {
			return null;
		}
	}

	public synchronized void removeAck(InetAddress address, int port) {
		String destination = address.getHostAddress() + port;
		queue.remove(destination);
	}

	public HashMap getQueue(){
		return queue;
	}
}

class Response extends Utilities implements Runnable {

	private Dictionary dictionary;
	private AckQueue queue;
	private DatagramSocket socket;
	private InetAddress clientIp;
	private int clientPort;
	private DatagramPacket requestPacket;
	private String requestStr;
	private ResponseObj responseObj;

	public Response(DatagramPacket requestPacket, DatagramSocket socket, Dictionary dictionary, AckQueue queue) {
		this.requestPacket = requestPacket;
		this.dictionary = dictionary;
		this.queue = queue;
		this.clientIp = requestPacket.getAddress();
		this.clientPort = requestPacket.getPort();
		this.socket = socket;
		requestStr = new String(requestPacket.getData(), 0, requestPacket.getLength());
	}

	public void run() {
		int isverify = verify(new JSONObject(requestStr));
		if (isverify == -1) {
			return;
		} else if (isverify == 1) {
			// add to ack list
			queue.addAck(clientIp, clientPort, requestPacket);
			System.out.println("RECEIVE = " + requestStr);
			return;
		}
		System.out.println("RECEIVE = " + requestStr);
		RequestObj requestObj = new RequestObj();
		requestObj.read(new JSONObject(requestStr));

		int status = fail;
		switch (requestObj.getMethod()) {
			case Query:
				String definition = dictionary.Query(requestObj.getWord());
				status = definition == "" ? notFound : success;
				responseObj = new ResponseObj(requestObj.getMethod(), requestObj.getWord(), definition, status);
				break;

			case Add:
				status = dictionary.Add(requestObj.getWord(), requestObj.getDefinition());
				responseObj = new ResponseObj(requestObj.getMethod(), requestObj.getWord(), requestObj.getDefinition(), status);
				break;

			case Remove:
				status = dictionary.Remove(requestObj.getWord());
				responseObj = new ResponseObj(requestObj.getMethod(), requestObj.getWord(), requestObj.getDefinition(), status);
				break;
		}
		byte[] sendDatagram = responseObj.getObj().toString().getBytes();

		// send packet
		DatagramPacket sendPacket = new DatagramPacket(sendDatagram, sendDatagram.length, clientIp, clientPort);
		try {
			System.out.println("SEND RESPONSE = " + responseObj.getObj().toString());
			socket.send(sendPacket);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// if method is Add or Remove
		if (requestObj.getMethod().equals(Add) || requestObj.getMethod().equals(Remove)) {
			retransmission(sendPacket);
		}
	}

	// check if ack, correct hash
	public int verify(JSONObject obj) {
		int isverify = -1;    // hash != content
		if (obj.has("data") && obj.has("checksum")) {
			JSONObject data = obj.getJSONObject("data");
			String checksum = obj.getString("checksum");
			if (checksum.equals(hash(data))) {
				if (data.getString("type").equals("request")) {
					isverify = 0;    // normal request
				} else {
					isverify = 1;    // ack
				}
			}
		}
		return isverify;
	}

	public void retransmission(DatagramPacket sendPacket) {
		int count = 1;
		System.out.println("start retransmission");
		while (true) {
			try {
				Thread.sleep(RTT);
			} catch (InterruptedException e) {
				e.printStackTrace();
				return;
			}
			DatagramPacket ack = queue.hasAck(clientIp, clientPort);
			if (ack != null) {
				queue.removeAck(clientIp, clientPort);
				System.out.println("FINISH ACK, QUEUE = " + queue.getQueue());
				return;
			}else {
				if (count > retransmissionCount) {
					System.out.println("Not receive ack from " + clientIp + ":" + clientPort);
					return;
				} else {
					try {
						System.out.println("SEND RETRANSMISSION = " + responseObj.getObj().toString());
						socket.send(sendPacket);
					} catch (IOException e) {
						e.printStackTrace();
					}
					count++;
				}
			}
		}
	}
}

public class Server extends Utilities {

	private static int port;
    private static String dictPath;

	public static void main(String[] args) throws IOException {

		if (args.length == 2){
			port = Integer.parseInt(args[0]);
			dictPath = args[1];
		}else{
			System.out.println("Incorrect number of arguments");
			return;
		}

		// setup
		Dictionary dictionary = new Dictionary(dictPath);
		AckQueue queue = new AckQueue();

		// Wait for connections.
		DatagramSocket socket = new DatagramSocket(port);
        System.out.println("Waiting for connections");
        
		while(true){

			// get message
        	byte[] receiveDatagram = new byte[2048];
			DatagramPacket receivePacket = new DatagramPacket(receiveDatagram, receiveDatagram.length);
			socket.receive(receivePacket);
			System.out.println("");

            // Start a new thread for a request
            Thread myThread = new Thread(new Response(receivePacket, socket, dictionary, queue));
            myThread.start();
		}
	}
}