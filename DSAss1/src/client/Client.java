package client;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.json.JSONObject;

public class Client {
	
	// IP and port
	private String serverIp = "localhost";
	private int serverPort = 8080;
	private boolean ack = false;
	private final long RTT = 200;
	
	public void main(String[] args) throws IOException {
		// read string from console
//		BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
//		String sentence = inFromUser.readLine();
		
		DatagramSocket clientSocket = new DatagramSocket();
		
		// prepare word
		byte[] sendMessage = new byte[1024];
		byte[] receiveMessage = new byte[1024];
		JSONObject requestData = genJSON("hello");
		sendMessage = requestData.toString().getBytes();
		
		// send word
		mySend(requestData, sendMessage, clientSocket);
		
		// receive answer
		DatagramPacket receivePacket = new DatagramPacket(receiveMessage, receiveMessage.length);
		clientSocket.receive(receivePacket);
		while (true) {
			if (receivePacket.getData().equals(sendMessage)) {
				clientSocket.receive(receivePacket);
			}else {
				break;
			}
		}
		
		// print answer
		String modifiedSentence = new String(receivePacket.getData());
		System.out.println("FROM SERVER:" + modifiedSentence);
		clientSocket.close();
	}
	public void mySend(JSONObject requestData, byte[] sendMessage, DatagramSocket clientSocket) throws IOException {
		// get server IP
		InetAddress IPAddress = InetAddress.getByName(serverIp);
		DatagramPacket sendPacket = new DatagramPacket(sendMessage, sendMessage.length, IPAddress, serverPort);
		
		// prepare listening ack
		byte[] receiveMessage = new byte[1024];
		DatagramPacket receivePacket = new DatagramPacket(receiveMessage, receiveMessage.length);
		
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
		while (true) {
			clientSocket.receive(receivePacket);
			String receiveString = new String(receivePacket.getData());
	        JSONObject receiveData = new JSONObject(receiveString);
	        JSONObject receiveObj = receiveData.getJSONObject("obj");
	        JSONObject requestObj = requestData.getJSONObject("obj");
			if (receiveData.has("client") && receiveObj.equals(requestObj)) {
		        ack = true;
		        break;
			}
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
	public JSONObject genJSON(String word) {
		JSONObject obj = new JSONObject();

	    obj.put("word", word);
	    
		JSONObject data = new JSONObject();
		data.put("obj", obj);
		data.put("hash", makehash(obj));
		data.put("client", "");
		return data;
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