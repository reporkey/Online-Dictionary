package client;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.json.JSONObject;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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

class RequestObj {
	private JSONObject obj = new JSONObject();
	private String type = "request";
	private String method;
	private String word;
	private String defination;
	
	public RequestObj(String method, String word, String defination) {
		this.method = method;
		this.word = word;
		this.defination = defination;
		JSONObject data = new JSONObject();
		data.put("type", this.type);
		data.put("method", this.method);
		data.put("word", this.word);
		data.put("defination", this.defination);
		obj.put("data", data);
		obj.put("checksum", Hash.hash(data));
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
	private JSONObject obj;
	private String type = "response";
	private String method;
	private String word;
	private String defination;
	private int status;
	
	public void read(JSONObject obj) {
		this.obj = obj;
		JSONObject data = obj.getJSONObject("data");
		this.method = data.getString("method");
		this.word = data.getString("word");
		this.defination = data.getString("defination");
		this.status = data.getInt("status");
		System.out.println(obj.toString());
	}
	
	public JSONObject getObj() {
		return obj;
	}

	public String getWord() {
		return word;
	}

	public String getDefination() {
		return defination;
	}

	public int getStatus() {
		return status;
	}

	public String getMethod() {
		return method;
	}
	
}

public class Client extends Methods{
	
	// IP and port
	private static String serverIp = "localhost";
	private static int serverPort = 8080;
	private boolean ack = false;
	private final long RTT = 200;
	static RequestObj requestObj;
	public static void main(String[] args) 
			throws IOException {
		
		DatagramSocket socket = new DatagramSocket();
		InetAddress ip = InetAddress.getByName(serverIp);
		GUI myGui = new GUI();
		
		// prepare word
		while(true) {
			while(true) {
				if (myGui.getRequestObj() != null) {
					System.out.println("success break");
					break;
				}
			}
			requestObj = myGui.getRequestObj();
			System.out.println(requestObj.getObj().toString());
			byte[] sendDatagram = requestObj.getObj().toString().getBytes();
			DatagramPacket sendPacket = new DatagramPacket(sendDatagram, sendDatagram.length, ip, serverPort);
			socket.send(sendPacket);
			
			// receive answer
			byte[] buf = new byte[2048];
			DatagramPacket responsePacket = new DatagramPacket(buf, buf.length);
			socket.receive(responsePacket);
			String responseStr = new String(responsePacket.getData(), 0, responsePacket.getLength());
	
			ResponseObj responseObj = new ResponseObj();
			responseObj.read(new JSONObject(responseStr));
			switch (responseObj.getMethod()) {
			case Query:
				myGui.setStatus(responseObj.getStatus());
				myGui.setDefination(responseObj.getDefination());
			case Add:
				myGui.setStatus(responseObj.getStatus());
			case Remove:
				myGui.setStatus(responseObj.getStatus());
			}
			
			myGui.setRequestObj(null);
		}
		
		
//		while (true) {
//			if (receivePacket.getData().equals(sendMessage)) {
//				clientSocket.receive(receivePacket);
//			}else {
//				break;
//			}
//		}
	}
	public void sendRequest(JSONObject requestData, byte[] sendDatagram, DatagramSocket clientSocket) 
			throws IOException {
		// send message
		InetAddress ip = InetAddress.getByName(serverIp);
		DatagramPacket sendPacket = new DatagramPacket(sendDatagram, sendDatagram.length, ip, serverPort);
		
//		// prepare listening ack
//		byte[] receiveMessage = new byte[1024];
//		DatagramPacket receivePacket = new DatagramPacket(receiveMessage, receiveMessage.length);
//		
//		// retransmission
//		Thread t = new Thread(() -> {
//			try {
//				retransmission(clientSocket, sendPacket);
//			} catch (InterruptedException | IOException e) {
//				e.printStackTrace();
//				return;
//			}
//		});
//		t.start();
//		// listen ack
//		while (true) {
//			clientSocket.receive(receivePacket);
//			String receiveString = new String(receivePacket.getData());
//	        JSONObject receiveData = new JSONObject(receiveString);
//	        JSONObject receiveObj = receiveData.getJSONObject("obj");
//	        JSONObject requestObj = requestData.getJSONObject("obj");
//			if (receiveData.has("client") && receiveObj.equals(requestObj)) {
//		        ack = true;
//		        break;
//			}
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

class GUI extends Frame{
	
	private RequestObj requestObj;
	Choice methodItem;
	TextField wordInput;
	TextField definationInput;
	PopupMenu popup = new PopupMenu("Result");
	
	public GUI() {
		setLayout(new FlowLayout());
		
		methodItem = new Choice();
		methodItem.add("Query");
		methodItem.add("Add");
		methodItem.add("Remove");
		methodItem.add("Query");
		add(methodItem);
		
		wordInput = new TextField("Word", 20);
		add(wordInput);
		
		definationInput = new TextField("Defination", 55);
		definationInput.setColumns(55);
		add(definationInput);
		
		Button submit = new Button("Submit");
		add(submit);
		
		add(popup);
		
		submit.addActionListener(new ActionListener() {
	        @Override
	        public void actionPerformed(ActionEvent evt) {
	        	String method = methodItem.getSelectedItem();
	        	String word = wordInput.getText();
	        	String defination = definationInput.getText();
	        	requestObj = new RequestObj(method, word, defination);
	        }
		});
		
        setVisible(true);
		setTitle("Dictionary");
		setSize(800, 800);
		setVisible(true);
	}

	public RequestObj getRequestObj() {
		System.out.println(requestObj);
		return requestObj;
	}
	
	

	public void setRequestObj(RequestObj requestObj) {
		this.requestObj = requestObj;
	}

	public void setStatus(int status) {
		popup.show(getParent(), 400, 400);
	}
	
	public void setDefination(String defination) {
		definationInput.setText(defination);
		repaint();
	}

}