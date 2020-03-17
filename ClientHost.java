import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.sql.Time;
import java.time.Clock;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ClientHost extends AbstractHost{
	
	public ClientHost(String ServerIP) throws UnknownHostException, IOException
	{
		this(ServerIP, DEFAULT_PORT);
	}
	
	public ClientHost(String ServerIP, int port) throws UnknownHostException, IOException
	{
		System.out.println("Tentative de connexion au serveur " + ServerIP + ":" + port);
		connectionSocket = new Socket(InetAddress.getByName(ServerIP), port);
		System.out.println("Connecté.");
		setIOStreams();
		connected.set(true);
	}
	
	//public static synchronized int listenUDPBroadcast() throws IOException
	public static synchronized ConnectionOffer listenUDPBroadcast() throws IOException
	{
		return listenUDPBroadcast(DEFAULT_PORT);
	}
	
	public static synchronized ConnectionOffer listenUDPBroadcast(int UDPPort) throws IOException
	{//Création de 10 Threads qui écoute sur 10 ports à partir de UDPPort
		AtomicReference<ConnectionOffer> offer = new AtomicReference<ConnectionOffer>(null);
		Thread[] listenThreads = new Thread[10];
		DatagramSocket[] ds_array = new DatagramSocket[listenThreads.length];
		
		for(int i = 0; i < listenThreads.length && UDPPort + i <= 65535; i++) {
			final int off = i;
			try {
				ds_array[off] = new DatagramSocket(UDPPort + off);
				
				listenThreads[off] = new Thread(new Runnable() {
					public void run() {
						byte[] dpBuffer = new byte[4096];
						DatagramSocket ds = ds_array[off];
						DatagramPacket packet = new DatagramPacket(dpBuffer, dpBuffer.length);
						int receivedPort = -1;
						
						try {
							ds.setSoTimeout(10000);
							ds.receive(packet);
							receivedPort = ByteBuffer.wrap(packet.getData(), 0, 4).getInt();
							offer.set(new ConnectionOffer(receivedPort, packet.getAddress()));
						} catch (SocketTimeoutException ste) {
						} catch (IOException e) {
							if(!ds.isClosed()) //Si fermé depuis le thread principal
								e.printStackTrace();
						} finally {
							if(!ds.isClosed())
								ds.close();
						}
					}
				});
				
				listenThreads[off].start();
			} catch(SocketException e) {
				ds_array[off] = null;
			}
		}
			
			//vérification si offre de port reçu chaque 200ms
			for(long timer = 0;  timer < 10000; timer += 200) {
				
				if(offer.get() != null) { //fermeture datagram sockets et sortie de la boucle
					for(int i = 0; i < ds_array.length; i++) {
						if(ds_array[i] != null)
							ds_array[i].close();
					}
					break;
				}
				
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
		if(offer.get() == null) throw new IOException("UDP Listen timed out");
		
		return offer.get();
	}
	
	protected void openDataConnection(int port)
	{
		try {
			dataSocket = new Socket(connectionSocket.getInetAddress(), port);
			setDataIOStreams();
			dataConnected.set(true);
			System.out.println("DataStream overt.");
		} catch (IOException e) {
			e.printStackTrace();
			close();
		}
	}
	
	@Override
	protected byte messageProcessor(byte[] buffer)
	{
		if(super.messageProcessor(buffer) == FLAG_DATA_STREAM_OFFER)
		{
			openDataConnection(Integer.valueOf(new String(buffer, 1, buffer.length - 1)));
		}
		
		return 0;
	}
	
	public static void main(String[] args) throws InterruptedException {
		ClientHost clientSocket = null;
		Scanner sc = new Scanner(System.in);
		try 
		{
			//écoute et attente des packets UDP d'offre de connexion
			ConnectionOffer offer = listenUDPBroadcast();
			
			System.out.println("Do you want to connect to: " + offer.getAddress().getHostName() +
					'/' + offer.getAddress().getHostAddress() + " ?");
			System.out.println("[Y]es \t [N]o");
			if(sc.nextLine().equalsIgnoreCase("Y")) {
				//Crée un Thread qui tente de se connecter au serveur
				//clientSocket = new ClientHost("localhost", offer.getPort());
				clientSocket = new ClientHost(offer.getAddress().getHostAddress(), offer.getPort());
				clientSocket.runMessageListener();
				//Lancer la boucle de lecture des entrées utilisateur
				clientSocket.commandReader();
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		finally
		{
			if(clientSocket != null)
				clientSocket.close();
		}
		sc.close();
		System.out.println("Finished");
	}

	public static void mainSave(String[] args) throws InterruptedException {
		ClientHost clientSocket = null;
		try 
		{
			//clientSocket = new ClientHost("192.168.204");
			clientSocket = new ClientHost("localhost");
			clientSocket.sendMessage("Hi I'm the client !");
			//clientSocket.writer.close();
			//clientSocket.writer = null;
			clientSocket.runMessageListener();
			Thread.sleep(8000);
		} catch (IOException e) {
			e.printStackTrace();
		}
		finally
		{
			if(clientSocket != null)
				clientSocket.close();
		}
		System.out.println("Finished");
	}

}
