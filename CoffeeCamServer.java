/* CoffeeCamServer
*/
import java.io.*;
import java.net.*;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.HashMap;

// CoffeeCamServer
// Threadable class that is spawned once for each connection
public class CoffeeCamServer implements Runnable {

	private static final int CCCP_REVISION = 1;

	private Socket clientSocket = null;
	private String ip = "";

	private static Socket camSocket = null;
	private static PrintWriter logFileWriter = null;
	private static HashMap connectionCounter;
	private static String currentText = "";

	// Class Constructor
	public CoffeeCamServer(Socket inSocket){
		clientSocket = inSocket;
		ip = clientSocket.getInetAddress().toString();
		// Count incoming connections per IP
		if (!connectionCounter.containsKey(ip)) {
			logFileWriter.println("New connection from "+ip);
			connectionCounter.put(ip,1);
		}
		else {
			logFileWriter.println("An additional connection has been opened from "+ip);
			connectionCounter.put(ip,((Number)connectionCounter.get(ip)).intValue()+1);
		}
	}

	public void run() { 
		try {
			// Set reader and writer for communication with the client
			//BufferedWriter toClient = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
			PrintStream toClient = new PrintStream(clientSocket.getOutputStream(), true, "UTF-8");
			BufferedReader fromClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			String incomingMessage = "";
			// Do the handshake
			toClient.println("Coffee?");
			String handShakeResponse = fromClient.readLine();
			if (handShakeResponse != null && handShakeResponse.equals("o-)")) {
				// listen to client
				while ((incomingMessage = fromClient.readLine()) != null && !incomingMessage.equals("/ccdisconnect")) {
					logFileWriter.println("Received: " + incomingMessage);
					if (incomingMessage.equals("/ccget")) {
						// Return currently displayed message
						toClient.println("[CCRET] "+currentText);
					}
					else if (incomingMessage.equals("/ccaud")) {
						// Return number of connected users
						String audienceRate = "Audience Rate: "+connectionCounter.size();
						toClient.println(audienceRate);
					}
					else if (incomingMessage.equals("/ccsnap")) {
						toClient.println(sendToCam("snapshot"));
					}
					else if (incomingMessage.equals("/cccp")) {
						String protocolInfo = "This server implements the CoffeeCam Control Protocol (CCCP) revision " +
							CCCP_REVISION + ". Builtin commands: " +
							"/ccsay, /ccget, /ccaud, /cccp (obviously), /ccsnap (still buggy), /ccdisconnect";
						toClient.println(protocolInfo);
					}
					else if (incomingMessage.startsWith("/ccsay ")) {
						// Prevent empty or all-whitespace messages
						if (incomingMessage.length() > 7 &&
							incomingMessage.startsWith("/ccsay ") &&
							!incomingMessage.replaceAll("\\\r\\\n|\\\r|\\\n", " ").trim().equals("/ccsay")
						) {
							// Display message (without /ccsay), log it,
							// and send the return statement back to the client
							String messageText = incomingMessage.substring(7);
							String returnText = sendText(messageText);
							logFileWriter.println(getTimeString() + ": " + messageText + " --- " + returnText);
							toClient.println(returnText);
						}
						else {
							toClient.println("Message too short!");
						}
					}
					else {
						toClient.println("Unknown command. Please get the latest version of CoffeeCamControl.");
					}
					toClient.flush();
				}
			}
			else {
				logFileWriter.println("Invalid handshake response received.");
				toClient.println("Invalid handshake response. Please stick to the CoffeeCam Control Protocol.");
			}

			// Client has closed the connection (or sent a wrong handshake response) so we close ours, too
			toClient.close();
			fromClient.close();
			if (connectionCounter.containsKey(ip)) {
				if (((Number)connectionCounter.get(ip)).intValue() <= 1) {
					logFileWriter.println("All connections closed from "+ip);
					connectionCounter.remove(ip);
				}
				else {
					logFileWriter.println("One connection closed from "+ip);
					connectionCounter.put(ip,((Number)connectionCounter.get(ip)).intValue()-1);
				}
			}
			clientSocket.close();

		}
		catch (IOException e) {
			System.out.println("Connection error: " + e.toString());
		}
	}

	// Send text message to VLC (via /ccsay) and issue a return statement
	// The default [CCOK] means that the text was displayed correctly.
	private String sendText(String text) {
		String response = "[CCOK]";
		// Not really necessary, just for fun :)
		if (text.toLowerCase().equals("quit") || text.toLowerCase().contains("; drop table")) {
			response = "Nice try.";
		}
		// Too much text on the stream causes the codec to produce ugly artefacts
		else if (text.length() > 255) {
			response = "Please keep it short, thank you.";
		}
		else {
			response = sendToCam("@marq marq-marquee " + text);
			if (response.equals("[CCOK]")) {
				// Save the text so it can be retrieved via /ccget
				currentText = text;
			}
		}
		return response;
	}

	// Sends a command to the VLC rc interface
	private static String sendToCam(String message) {
		String response = "[CCERR]";
		if (camSocket != null) {
			try {
				PrintWriter camTextWriter = new PrintWriter(camSocket.getOutputStream(), true);
				camTextWriter.println(message);
				response = "[CCOK]";
			}
			catch (IOException e) {
				response = "[CCERR] Cannot send message: "+e.toString();
			}
		}
		else {
			response = "[CCERR] Cam not running (apparently).";
		}
		return response;
	}

	private static String getTimeString() {
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
		return sdf.format(cal.getTime());
	}

	// Main method
	// Connects to VLC and waits for incoming connections.
	// Every incoming connection spawns a new CoffeeCamServer thread	
	// TODO: Add connection limit to prevent flooding
	public static void main(String[] args) {
		try {
			// Connect to VLC on localhost, port 6321
			camSocket = new Socket("127.0.0.1", 6321);
			// Set VLC marquee size, position and initial message
			sendToCam("@marq marq-size 20");
			sendToCam("@marq marq-position 9");
			sendToCam("@marq marq-marquee Welcome to the CoffeeCam.");

			// Open a new log file and connection counter
			connectionCounter = new HashMap();
			logFileWriter = new PrintWriter(new FileWriter("CoffeeCam_"+getTimeString()+".log"), true);

			// Wait for incoming connections
			ServerSocket serverSocket = new ServerSocket(1236);
			while (true) {
				Socket clientSocket = serverSocket.accept();
				CoffeeCamServer server = new CoffeeCamServer(clientSocket);
				Thread serverThread = new Thread(server);
				serverThread.start();
			}
		}
		catch (IOException e) {
			System.err.println(e.toString());
		}
	}
}
