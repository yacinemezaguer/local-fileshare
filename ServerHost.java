import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

/*à rajouter : sémaphores pour le contrôle des variables partagées*/
/*Utiliser addresse de broadcast du sous-réseau au lieu de 255.255.255.255*/

public class ServerHost extends AbstractHost{

	private ServerSocket server = null;
	private ServerSocket dataServerSocket = null;
	
	public ServerHost() throws UnknownHostException, IOException
	{
		this(getLocalAddress(), 0);
		//this(getLocalAddress(), DEFAULT_PORT);
	}
	
	public ServerHost(int port) throws UnknownHostException, IOException
	{
		this(getLocalAddress(), port);
	}
	
	public ServerHost(String hostIP) throws UnknownHostException, IOException
	{
		this(hostIP, 0);
	}
	
	public ServerHost(String hostingIP, int port) throws UnknownHostException, IOException
	{
		localIP = hostingIP;
		server = new ServerSocket(port, 1, InetAddress.getByName(hostingIP));
	}
	
	public ServerSocket getServerSocket()
	{
		return server;
	}
	
	public void UDPBroadcastPort(int port) throws IOException
	{
		UDPBroadcastPort(port, DEFAULT_PORT);
	}
	
	public void UDPBroadcastPort(int port, int UDPPort) throws IOException
	{//Broadcast le port de connexion dans des packet UDP sur les ports de UDPPort à UDPPort + 10 (pour s'assurer qu'il y ait au moins un port de disponible)
		Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					DatagramSocket UDPSocket = new DatagramSocket(0, InetAddress.getByName(getLocalAddress()) );
					DatagramPacket[] dpArray = new DatagramPacket[10];
					byte[] headerBytes = ("UDPBroadcast;" + InetAddress.getLocalHost().getHostName()).getBytes();
					
					System.out.println("Broadcast header : " + new String(headerBytes));
					
					ByteBuffer bytesPortBuffer = ByteBuffer.allocate(headerBytes.length + 4);
					bytesPortBuffer.put(ByteBuffer.allocate(4).putInt(port).array()); //écriture du port dans le packet
					bytesPortBuffer.put(headerBytes); //écriture du message (UDPBroadcast;<nomDeLaMachine>)
					byte[] bytesPort = bytesPortBuffer.array();
					
					for(int i = 0; i < dpArray.length && UDPPort + i <= 65535; i++)
					{
						dpArray[i] = new DatagramPacket(bytesPort, bytesPort.length, InetAddress.getByName("255.255.255.255"), UDPPort+i);
						//Packet : [port"UDPBroadcast;<nomDelaMachine>"]
					}
					
					//Boucle de broadcast du packet (broadcast du packet chaque 500ms) jusqu'à connexion ou interruption volontaire
					while(!connected.get() && !USER_INTERRUPTED.get())
					{
						for(DatagramPacket dp : dpArray) {
							if(dp == null) continue;
							UDPSocket.send(dp);
						}
						
						try {
							Thread.sleep(500);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						//System.out.println("new turn !");
					}
					
					System.out.println("Closing UDP socket");
					
					UDPSocket.close();
				}catch(IOException e) {
					e.printStackTrace();
				}
			}
		});
		
		t.start();
	}
	
	public void listen()
	{
		//Déclaration du Thread attendant la connexion
		Thread t = new Thread(new Runnable() {
			public void run()
			{
				System.out.println("En attente de connexion sur l'addresse : " + server.getLocalSocketAddress() +  "...");
				try
				{
					connectionSocket = server.accept();
					System.out.println("Hôte " + connectionSocket.getInetAddress().getHostName() + " / " + 
							connectionSocket.getInetAddress().getHostAddress() +
							":" + connectionSocket.getPort() + " connecté.");
					setIOStreams();
					connected.set(true);
					runMessageListener();
					openDataConnection();
				}
				catch(IOException e)
				{
					//détecte si connexion impossible ou bien arrêtée par 
					//le thread principal
					if(!USER_INTERRUPTED.get()) {
						System.err.println("Impossible de se connecter au client.");
						e.printStackTrace();
					}
				}
			}
		});
		
		t.start();
	}
	
	protected void openDataConnection() throws IOException
	{
		dataServerSocket = new ServerSocket(0, 1, InetAddress.getByName(localIP));
		//Message contenant le port de connection du dataStream à envoyer au client (contient
		//le port de connexion sous forme de String)
		String offerMessage = Integer.toString(dataServerSocket.getLocalPort());
		System.out.println("OfferMessage = " + offerMessage);
		sendMessage(FLAG_DATA_STREAM_OFFER, offerMessage);
		
		dataSocket = dataServerSocket.accept();
		setDataIOStreams();
		dataConnected.set(true);
		System.out.println("DataStream overt.");
	}

	public synchronized void close()
	{
		super.close();
		if(server != null)
		{
			try 
			{
				server.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Server closed");
	}
	
	public static void main(String[] args) throws InterruptedException {
		ServerHost server = null;
		try 
		{
			//server = new ServerHost("localhost");
			server = new ServerHost();
			//Crée un Thread qui broadcast le port de connexion du server dans des packet UDP
			server.UDPBroadcastPort(server.getServerSocket().getLocalPort()); //ctr: execute in a cleaner way (thread or something)
			//Crée un Thread qui attend et accepte une connexion cliente
			server.listen();
			//Lancer la boucle de lecture des entrées utilisateur
			server.commandReader();
		} catch (IOException e) {
			e.printStackTrace();
		}finally {
			if(server != null && server.connected.get())
				server.close();
		}
		
		System.out.println("Finished");
	}
	
	public static void mainSave(String[] args) {
		ServerHost s = null;
		try 
		{
			s = new ServerHost("localhost");
			//Crée un Thread qui attend et accepte une connexion cliente
			s.listen();
			try {
				Thread.sleep(8000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			s.sendMessage("Hi I'm the server.");
			try {
				Thread.sleep(8000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}finally {
			if(s != null && s.connected.get())
				s.close();
		}
		
		System.out.println("Finished");
	}

}
