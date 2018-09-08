package client;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.json.JSONObject;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.*;

class Utilities {
	public static final String Query = "Query";
	public static final String Add = "Add";
	public static final String Remove = "Remove";
    
	public static final int success = 0;
    public static final int notFound = 1;
    public static final int emptyDefinition = 2;
    public static final int wordExisted = 3;

	public static String serverIp ;
	public static int serverPort;
	public static final int RTT = 200;
	public static final int retransmissionCount = 7;
    
    public static String translate(int status, String word, String method) {
    	String error = ""; 
    	switch (status) {
    	case success:
    		error = "Success " + method + " '" + word + "'.";
    		break;
    	case notFound:
			error = "Not Found Word '" + word + "'.";
    		break;
    	case emptyDefinition:
    		error = "Definition is empty.";
    		break;
    	case wordExisted:
    		error = "The word '" + word + "' is existed.";
    		break;
		default:
			error = "Unknow error.";
    	}
		return error;
    }

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

class RequestObj extends Utilities {
	private JSONObject obj = new JSONObject();
	private String type = "request";
	private String method;
	private String word;
	private String definition;
	
	public RequestObj(String method, String word, String definition) {
		this.method = method;
		this.word = word;
		this.definition = definition;
		JSONObject data = new JSONObject();
		data.put("type", this.type);
		data.put("method", this.method);
		data.put("word", this.word);
		data.put("definition", this.definition);
		obj.put("data", data);
		obj.put("checksum", hash(data));
	}
	
	public JSONObject getObj() {
		return obj;
	}

	public String getMethod() {
		return method;
	}
}

class ResponseObj extends Utilities{
	private JSONObject obj;
	private String method;
	private String word;
	private String definition;
	private int status;
	
	public void read(JSONObject obj) {
		this.obj = obj;
		JSONObject data = obj.getJSONObject("data");
		this.method = data.getString("method");
		this.word = data.getString("word");
		this.definition = data.getString("definition");
		this.status = data.getInt("status");
	}
	
	public JSONObject getObj() {
		return obj;
	}

	public String getWord() {
		return word;
	}

	public String getDefinition() {
		return definition;
	}

	public int getStatus() {
		return status;
	}

	public String getMethod() {
		return method;
	}
	
}

class GUI extends Frame{
	private JFrame frame = new JFrame();
	private RequestObj requestObj = null;
	private Choice methodItem;
	private TextField wordInput;
	private TextField definitionInput;

	public GUI() {
		frame.setLayout(new FlowLayout());

		methodItem = new Choice();
		methodItem.add("Query");
		methodItem.add("Add");
		methodItem.add("Remove");
		frame.add(methodItem);

		wordInput = new TextField("Word", 20);
		frame.add(wordInput);

		definitionInput = new TextField("Definition", 55);
		definitionInput.setColumns(55);
		frame.add(definitionInput);

		JButton submit = new JButton("Submit");
		frame.add(submit);

		submit.addActionListener(new ActionListener() {
	        @Override
	        public void actionPerformed(ActionEvent evt) {
	        	String method = methodItem.getSelectedItem();
	        	String word = wordInput.getText();
	        	String definition = definitionInput.getText();
	        	requestObj = new RequestObj(method, word, definition);
	        }
		});
		frame.setTitle("Dictionary");
		frame.setSize(800, 200);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
	}

	public RequestObj getRequestObj() {
		return requestObj;
	}

	public void setRequestObj(RequestObj requestObj) {
		this.requestObj = requestObj;
	}

	public void setStatus(String error) {
		JOptionPane.showMessageDialog(null, error);
	}

	public void setDefinition(String definition) {
		definitionInput.setText(definition);
		repaint();
	}
}

public class Client extends Utilities {

	private static volatile Boolean flag = false;

	public static void main(String[] args) throws IOException {

		if (args.length == 2){
			serverIp = args[0];
			serverPort = Integer.parseInt(args[1]);
		}else{
			System.out.println("Incorrect number of arguments");
			return;
		}

		DatagramSocket socket = new DatagramSocket();
		socket.setSoTimeout(retransmissionCount * RTT);
		InetAddress ip = InetAddress.getByName(serverIp);
		GUI myGui = new GUI();

		// prepare word
		while (true) {
			while (true) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				if (myGui.getRequestObj() != null) {
					System.out.println("");
					break;
				}
			}

			RequestObj requestObj = myGui.getRequestObj();
			byte[] sendDatagram = requestObj.getObj().toString().getBytes();
			System.out.println("SEND REQUEST = " + requestObj.getObj().toString());
			DatagramPacket sendPacket = new DatagramPacket(sendDatagram, sendDatagram.length, ip, serverPort);
			socket.send(sendPacket);

			// receive answer
			byte[] buf = new byte[2048];
			DatagramPacket responsePacket = new DatagramPacket(buf, buf.length);
			String responseStr = null;
			Thread retransmission = new Thread(new Retransmission(socket, sendPacket, myGui));
			retransmission.start();
			try {
				socket.receive(responsePacket);
				responseStr = new String(responsePacket.getData(), 0, responsePacket.getLength());
				flag = true;
			} catch (SocketTimeoutException e) {
				myGui.setRequestObj(null);
				continue;
			}

			ResponseObj responseObj = new ResponseObj();
			responseObj.read(new JSONObject(responseStr));

			// send ack if method is Add or Remove
			System.out.println("RECEIVE = " + responseObj.getObj().toString());
			if (requestObj.getMethod().equals(Add) || requestObj.getMethod().equals(Remove)) {
				sendDatagram = responseStr.getBytes();
				System.out.println("SEND ack = " + responseStr);
				sendPacket = new DatagramPacket(sendDatagram, sendDatagram.length, ip, serverPort);
				socket.send(sendPacket);
			}

			String status = "";
			switch (requestObj.getMethod()) {
				case Query:
					if (responseObj.getStatus() != success) {
						status = translate(responseObj.getStatus(), responseObj.getWord(), responseObj.getMethod());
						myGui.setStatus(status);
					}
					myGui.setDefinition(responseObj.getDefinition());
					break;

				case Add:
					status = translate(responseObj.getStatus(), responseObj.getWord(), responseObj.getMethod());
					System.out.println(status);
					myGui.setStatus(status);
					break;

				case Remove:
					status = translate(responseObj.getStatus(), responseObj.getWord(), responseObj.getMethod());
					System.out.println(status);
					myGui.setStatus(status);
					break;
			}

			myGui.setRequestObj(null);
			requestObj = null;
		}
	}

	static class Retransmission implements Runnable {

		private DatagramSocket socket;
		private DatagramPacket sendPacket;
		private int count = 1;
		private GUI myGui;

		public Retransmission(DatagramSocket socket, DatagramPacket sendPacket, GUI myGui) {
			this.socket = socket;
			this.sendPacket = sendPacket;
			this.myGui = myGui;
			System.out.println("start retransmission");
		}

		public void run() {
			while (true) {
				try {
					Thread.sleep(RTT);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				if (flag) {
					flag = false;
					return;
				}else if (count > retransmissionCount) {
					myGui.setStatus("Bad connection, please try later");
					return;
				}else {
					try {
						System.out.println("SEND times = " + count + sendPacket.getData().toString());
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