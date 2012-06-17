/* Coffee Cam Control Client Application
 */

import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Properties;


public class CoffeeCamControl extends Frame implements WindowListener,ActionListener {

	private final int APP_REVISION = 4;
	private final int CCCP_REVISION = 1;
	private JFrame mainWindow;
	private JTextArea inputBox;
	private JTextArea outputBox;
	private JButton buttonSend;
	private JButton buttonRetrieve;
	private Socket camSocket;

	// Class Constructor
	public CoffeeCamControl() {
		try {
			Properties config = new Properties();
			// Load configuration from jar file
		    config.load(getClass().getClassLoader().getResourceAsStream("CoffeeCamControl.cfg"));
			String ip = config.getProperty("server_ip");
			String welcomeMessage = config.getProperty("welcome_message");
			if (ip != null) {
				setupGUI(welcomeMessage);
				openSocket(ip);
			}
			else {
				System.out.println("Missing value in CoffeeCamControl.cfg!"); 
			}
		}
		catch (Exception e) {
			System.out.println("Error parsing file CoffeeCamControl.cfg: "+e.toString());
		}
	}

	private void setupGUI(String welcomeMessage) {
		mainWindow = new JFrame("CoffeeCamControl - r" + APP_REVISION);
		mainWindow.setMinimumSize(new Dimension(600,200));

		inputBox = new JTextArea();
		inputBox.setMinimumSize(new Dimension(520, 100));
		
		// Map Ctrl-Enter and Shift-Enter of input Box to send and receive
		InputMap input = inputBox.getInputMap();
		input.put(KeyStroke.getKeyStroke("shift ENTER"), "sendMessageAction");
		input.put(KeyStroke.getKeyStroke("control ENTER"), "retrieveMessageAction");

		ActionMap actions = inputBox.getActionMap();
		actions.put("sendMessageAction", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				sendText();
			}
		});
		actions.put("retrieveMessageAction", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				retrieveText();
			}
		});

		buttonSend = new JButton("Send");
		buttonRetrieve = new JButton("Retrieve");
		buttonSend.setMinimumSize(new Dimension(200, 50));
		buttonSend.setPreferredSize(new Dimension(200, 50));
		buttonSend.setEnabled(false);
		buttonRetrieve.setMinimumSize(new Dimension(200, 50));
		buttonRetrieve.setPreferredSize(new Dimension(200, 50));
		buttonRetrieve.setEnabled(false);
		buttonSend.addActionListener(this);
		buttonRetrieve.addActionListener(this);
		
		outputBox = new JTextArea();
		outputBox.setMinimumSize(new Dimension(600, 100));
		outputBox.setEditable(false);
		outputBox.setBackground(mainWindow.getBackground());
		outputBox.setText(welcomeMessage);
		outputBox.setLineWrap(true);
		outputBox.setWrapStyleWord(true);

		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.PAGE_AXIS));
		JPanel buttonPanel = new JPanel(new GridLayout(2,1,4,4));
		buttonPanel.setPreferredSize(new Dimension(200,100));
		JPanel inputPanel = new JPanel();
		inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.LINE_AXIS));
		JScrollPane outputPanel = new JScrollPane(outputBox);
		outputPanel.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
	
		buttonPanel.add(buttonSend);
		buttonPanel.add(buttonRetrieve);
		inputPanel.add(inputBox);
		inputPanel.add(buttonPanel);
		mainPanel.add(inputPanel);
		mainPanel.add(outputPanel);

		//mainWindow.setResizable(false);
		mainWindow.add(mainPanel);
		mainWindow.setVisible(true);
		mainWindow.addWindowListener(this);
	} 

	private void openSocket(String ip) {
		try {
			outputBox.append("Trying to connect to " + ip + "...");
			camSocket = new Socket(ip, 1236);
			// Do handshake
  			BufferedReader fromServer = new BufferedReader(new InputStreamReader(camSocket.getInputStream()));
			String challenge = fromServer.readLine();
			if (challenge.equals("Coffee?")) {
				PrintStream toServer = new PrintStream(camSocket.getOutputStream(), true, "UTF-8");
				toServer.println("o-)");
				buttonSend.setEnabled(true);
				buttonRetrieve.setEnabled(true);
				outputBox.append("connected. Send /cccp for available commands.\n");
				outputBox.setCaretPosition(outputBox.getDocument().getLength());
			}
			else {
				camSocket.close();
				camSocket = null;
				outputBox.append("Cannot find a CoffeeCam server on the specified IP and port.\n");
				outputBox.setCaretPosition(outputBox.getDocument().getLength());
			}
		}
		catch (Exception e) {
			camSocket = null;
			outputBox.append("cannot connect. Please check server IP in CoffeeCamControl.cfg.\n");
			outputBox.setCaretPosition(outputBox.getDocument().getLength());
		}
	}

	// Send message and parse response.
	// TODO: Currently, the client will hang if nothing is received.	
	private void send(String text) {
			if (camSocket != null) {
				try {
					if (!text.startsWith("/")) {
						// Use /ccsay by default
						text = "/ccsay " + text;
					}
					else {
						// when the input is a command, clear the input box
						inputBox.setText("");
					}
					text = text.replaceAll("\\\n","\\$_");
					PrintStream camTextWriter = new PrintStream(camSocket.getOutputStream(), true, "UTF-8");
					camTextWriter.println(text);
  					BufferedReader response = new BufferedReader(new InputStreamReader(camSocket.getInputStream()));
					String responseText = response.readLine();
					if (responseText != null) {
						if (responseText.startsWith("[CCRET] ")) {
							responseText = responseText.replaceAll("\\$_","\\\n");
							inputBox.setText(responseText.substring(8));
						}
						else if (!responseText.equals("[CCOK]")) {
							outputBox.append(responseText+"\n");	
							outputBox.setCaretPosition(outputBox.getDocument().getLength());
						}
					}
					else {
						outputBox.append("No response from server - broken connection? Please restart and try again.");
						outputBox.setCaretPosition(outputBox.getDocument().getLength());
						buttonSend.setEnabled(false);
						buttonRetrieve.setEnabled(false);
					}
				}
				catch (IOException e) {
					outputBox.append("Cannot send message: "+e.toString());
					outputBox.setCaretPosition(outputBox.getDocument().getLength());
				}
			}
	}

	// Action Listeners for the buttons
	public void actionPerformed(ActionEvent event) {
		if (event.getSource() == buttonSend) {
			sendText();
		}
		else if (event.getSource() == buttonRetrieve) {
			retrieveText();
		}
	}

	private void sendText() {
		send(inputBox.getText());
	}

	private void retrieveText() {
		send("/ccget");
	}

	// Action Listeners for window events
	public void windowClosing(WindowEvent event) {
		try {
			if (camSocket != null) {
				send("/ccdisconnect");
				camSocket.close();
			}
		}
		catch (IOException e) {
		}
		dispose();
		System.exit(0);
	}

	// These do nothing at the moment
	public void windowOpened(WindowEvent event) {}
	public void windowActivated(WindowEvent event) {}
	public void windowIconified(WindowEvent event) {}
	public void windowDeiconified(WindowEvent event) {}
	public void windowDeactivated(WindowEvent event) {}
	public void windowClosed(WindowEvent event) {}

	// Main method
	// Just instantiate the application
	public static void main(String argv[]) {
		CoffeeCamControl ccc = new CoffeeCamControl();
	}
}

