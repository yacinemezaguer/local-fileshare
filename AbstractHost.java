//ctr: code to revise / code à revoir / code à corriger / code to correct / à supprimer / à ajouter
//prochaine étape: ajout de la découverte réseau automatique (broadcast UDP)

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.Clock;
import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractHost {
	
	protected final static int DEFAULT_PORT = 42999;
	protected BufferedInputStream reader = null;
	protected BufferedOutputStream bos = null;
	protected BufferedInputStream dataBis = null;
	protected BufferedOutputStream dataBos = null;
	protected PrintWriter writer = null;
	protected Socket connectionSocket = null;
	protected Socket dataSocket = null;
	protected volatile AtomicBoolean connected = new AtomicBoolean(false);
	protected volatile AtomicBoolean dataConnected = new AtomicBoolean(false);
	protected volatile AtomicBoolean loopInput = new AtomicBoolean(true);
	protected volatile AtomicBoolean fileTransfert = new AtomicBoolean(false);
	protected volatile AtomicBoolean approvedSend = new AtomicBoolean(false);
	protected BufferedReader userInputReader = new BufferedReader(new InputStreamReader(System.in));
	protected volatile Semaphore semConnected = new Semaphore(1, true);
	protected volatile Semaphore semWaitApproval = new Semaphore(0, true);
	protected volatile String localIP = null;
	
	protected volatile AtomicBoolean USER_INTERRUPTED = new AtomicBoolean(false);
	
	protected final byte FLAG_MESSAGE = (byte) 0x01;
	protected final byte FLAG_DATA_STREAM_OFFER = (byte) 0x02;
	protected final byte FLAG_FILE_SEND_RQST = (byte) 0x03;
	protected final byte FLAG_FILE_SEND_ACCEPT = (byte) 0x04;
	protected final byte FLAG_FILE_SEND_DENY = (byte) 0x05;
		
	protected void runMessageListener()
	{
		Thread t = new Thread(new Runnable() {
			public void run()
			{
				//Boucle d'écoute des messages reçus
				while(connected.get())
				{
					try 
					{
						messageProcessor(readStream());
					}catch (IOException e) {
						if(connected.get())
						{
							e.printStackTrace();
							close();
						}
					}
				}
			}
		});
		
		t.start();
	}
	
	/*public synchronized boolean isConnected()
	{
		try {
			semConnected.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		boolean valeur = connected.get();
		semConnected.release();
		return valeur;
	}*/
	
	protected void setIOStreams() throws IOException
	{
		bos = new BufferedOutputStream(connectionSocket.getOutputStream());
		reader = new BufferedInputStream(connectionSocket.getInputStream());
		writer = new PrintWriter(connectionSocket.getOutputStream(), true);
	}
	
	protected void setDataIOStreams() throws IOException
	{
		dataBos = new BufferedOutputStream(dataSocket.getOutputStream());
		dataBis = new BufferedInputStream(dataSocket.getInputStream());
	}
	
	protected void sendMessage3(String message)
	{
		writer.write(message);
		writer.flush();
	}
	
	protected String readStream3() throws IOException
	{
		String message = null;
		byte[] buffer = new byte[4096];
		int size = reader.read(buffer);
		if(size < 0) //Si outputStream de l'autre pair est fermé alors fermer ce socket
		{
			System.out.println("Connexion interrompue");
			close();
			return null;
		}
		message = new String(buffer, 0, size);
		return message;
	}
	
	protected byte[] readStream() throws IOException
	{
		byte[] buffer = new byte[4096];
		int size = reader.read(buffer);
		if(size < 0) //Si outputStream de l'autre pair est fermé alors fermer ce socket
		{
			System.out.println("Connexion interrompue");
			close();
			return null;
		}
		
		return Arrays.copyOf(buffer, size);
	}
	
	protected void messageProcessor3(String message)
	{
		if(message == null)
			return;
		String out = "\n";
		out += connectionSocket.getInetAddress().getHostName() + ":" + 
				connectionSocket.getPort() + " :> ";
		out += message;
		System.out.println(out);
	}
	
	protected byte messageProcessor(byte[] buffer)
	{
		if(buffer == null || buffer.length < 1)
			return -1;
		String out = "\n";
		out += connectionSocket.getInetAddress().getHostName() + ":" + 
				connectionSocket.getPort() + " :> ";
		switch(buffer[0])
		{
			case FLAG_MESSAGE:
				out += new String(buffer, 1, buffer.length - 1);
				break;
				
			case FLAG_DATA_STREAM_OFFER: 
				out+= "Open data socket offer received, port : " + 
						Integer.valueOf(new String(buffer, 1, buffer.length - 1));
				System.out.println(out);
				return FLAG_DATA_STREAM_OFFER;
				
			case FLAG_FILE_SEND_RQST:
				if(!fileTransfert.get()) { 
					StringTokenizer st = new StringTokenizer(new String(buffer, 1, buffer.length - 1));
					receiveFile(st.nextToken(), Long.valueOf(st.nextToken()));
				}
				else denyFileShare();
				break;
				
			case FLAG_FILE_SEND_ACCEPT:
				approvedSend.set(true);
				semWaitApproval.release();
				break;
				
			case FLAG_FILE_SEND_DENY:
				approvedSend.set(false);
				semWaitApproval.release();
				break;
		}
		System.out.println(out);
		
		return 0;
	}
	
	/*public synchronized void setConnected(boolean c)
	{
		try {
			semConnected.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		connected.set(c);
		semConnected.release();
	}*/
	
	private void denyFileShare() {
		sendMessage(new byte[] {FLAG_FILE_SEND_DENY});
	}

	public synchronized void close()
	{
		loopInput.set(false);
		if(!connected.get()) return;
		
		System.err.println("Fermeture de la connexion...");
		connected.set(false);
		dataConnected.set(false);
		if(bos != null) {
			try {
				bos.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
		if(writer != null)
			writer.close(); //à vérifier (ferme l'outputStream pour envoyer -1 à l'inputStream du socket recepteur)
		if(reader != null) {
			try 
			{
				reader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		if(dataBis != null) {
			try 
			{
				dataBis.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		if(dataBos != null) {
			try 
			{
				dataBos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		if(dataSocket != null)
		{
			try 
			{
				dataSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		if(connectionSocket != null)
		{
			try 
			{
				connectionSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void commandParse(String command)
	{
		if(command == null || command.isEmpty())
			return;
		
		StringTokenizer st = new StringTokenizer(command);
		String commandKey = null;
		if(st.hasMoreTokens())
			commandKey = st.nextToken();
		
		if(commandKey != null && commandKey.length() > 1 && commandKey.charAt(0) == '/' && 
				commandKey.charAt(1) != '/') {
			switch(commandKey.toUpperCase()) {
				case "/EXIT":
				case "/CLOSE":
					USER_INTERRUPTED.set(true);
					close();
					break;
				case "/SENDFILE":
					if(connected.get() && !fileTransfert.get()) {
						/*while(st.hasMoreTokens()) { //ctr: Multiple files
							sendFile(st.nextToken());
						}*/
						sendFile(command.substring(10));
					}
					else System.err.println("Not connected or currently transferring files.");
					break;
				default: System.err.println("Commande inconnue.");
					break;
			}
		}
		else {
			if(connected.get())
				sendMessage(command);
		}
	}
	
	protected synchronized void receiveFile(String name, long fileSize)
	{
		Thread t = new Thread(new Runnable() {
			public void run() {
				System.out.println("Receiving : " + name);
				long size = fileSize;
				
				String directoryName = "received files";
				File dossier = new File(directoryName);
				if(!dossier.exists() || dossier.isFile())
					dossier.mkdir();
				
				int extension = name.lastIndexOf('.');
				File f = new File(directoryName + "/" + name.substring(0, extension) + "_" + 
						System.currentTimeMillis() + name.substring(extension));
				fileTransfert.set(true);
				
				sendMessage(new byte[] {FLAG_FILE_SEND_ACCEPT});
				
				try {
					BufferedOutputStream fileBos = new BufferedOutputStream(new FileOutputStream(f));
					int b = 0;
					float avancement = size*100/fileSize;
					
					while(connected.get() && size > 0 && b != -1)
					{
						while(dataBis.available() > 0 && size > 0 && (b = dataBis.read()) != -1)
						{
							fileBos.write(b);
							--size;
							
							if(avancement != (avancement = size*100/fileSize))
							{
								System.out.println("Avancement : " + (100.0 - avancement) + "%");
							}
							
						}
						
						/*
						try {
							Thread.sleep(50);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						*/
					}
					
					fileBos.close();
				} catch(IOException e) {
					e.printStackTrace();
				} finally { fileTransfert.set(false); }
				System.out.println("Finished receiving");
			}
		});
		
		t.start();
	}
	
	protected synchronized void sendFile(String filePath)
	{
		System.out.println("attempting to send : " + filePath);
		
		File f = new File(filePath);
		if(!f.exists()) {
			System.err.println("Fichier ou dossier inexistant : " + filePath);
			return;
		}
		
		if(f.isDirectory()) { //Si dossier (à rajouter: envoyer tous les fichier du dossier)
			System.out.println("Ceci est un dossier (l'envoie de dossiers n'est pas encore disponible)");
			throw new UnsupportedOperationException();
		}
		
		fileTransfert.set(true);
		
		//send file-share request
		String request = f.getName() + " " + f.length();
		System.out.println("size : " + f.length());
		sendMessage(FLAG_FILE_SEND_RQST, request);		
		try {
				//wait for approval
			if(semWaitApproval.tryAcquire(5, TimeUnit.SECONDS)) {
				//if approved then start sending
				if(approvedSend.get()) {
					//Read file and send it
					BufferedInputStream fileBis = new BufferedInputStream(new FileInputStream(f));
					int b;
					int count = 0;
					while((b = fileBis.read()) != -1) {
						dataBos.write((byte) b);
						++count;
					}
					
					dataBos.flush(); //Envoie des derniers bytes restants dans le buffer
					fileBis.close();
					System.out.println("Finished sending");
					System.out.println("bytes written : " + count);
					if(count > 1024) { System.out.println(count/1024 + "KB"); count /= 1024;};
					if(count > 1024) { System.out.println(count/1024.0 + "MB"); count /= 1024;};
				}
				else {
					System.err.println("File share denied by peer");
				}
			}
			else {	//if timed out
				System.err.println("Request Timed out");
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		} finally { 
			approvedSend.set(false); //ctr: Maybe useless
			fileTransfert.set(false);
		}
	}
	
	protected void sendMessage(byte[] message) //Envoie un tableau de Byte brute
	{
		try {
			bos.write(message);
			bos.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected void sendMessage(byte command, String message)
	{
		//Envoie une commande 'command' contenant les données dans 'message'
		
		try {
			bos.write(command);
			sendMessage(message.getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	protected void sendMessage(String message) //Envoie un message texte (interperété comme message discussion)
	{
		sendMessage(FLAG_MESSAGE, message);
	}
	
	public void commandReader() throws IOException, InterruptedException
	{		
		String input;
		while(loopInput.get())
		{
			commandParse(userInputReader.readLine());
			/*
			while(!userInputReader.ready() && loopInput.get())
			{				
				Thread.sleep(200); //Varier cette valeur
			}
			
			if(loopInput.get()) {
				input = userInputReader.readLine();
				commandParse(input);
			}*/
		}
	}
	
	public static String getLocalAddress() throws SocketException, UnknownHostException
	{
		try(final DatagramSocket socket = new DatagramSocket())
		{
			socket.connect(InetAddress.getByName("192.168.1.1"), 80);
			return socket.getLocalAddress().getHostAddress();
		}
	}
	
	public static void showInformations(InetAddress address){
	      System.out.println("-----------------------------------------------");
	      System.out.println("Nom  : " + address.getHostName());
	      System.out.println("Adresse : " + address.getHostAddress());
	      System.out.println("Nom canonique : " + address.getCanonicalHostName());
	   }
}
