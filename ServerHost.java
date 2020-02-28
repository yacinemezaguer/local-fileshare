import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

/*à rajouter : sémaphores pour le contrôle des variables partagées*/

public class ServerHost extends AbstractHost{

	private ServerSocket server = null;
	private ServerSocket dataServerSocket = null;
	
	public ServerHost() throws UnknownHostException, IOException
	{
		this(getLocalAddress(), DEFAULT_PORT);
	}
	
	public ServerHost(int port) throws UnknownHostException, IOException
	{
		this(getLocalAddress(), port);
	}
	
	public ServerHost(String hostIP) throws UnknownHostException, IOException
	{
		this(hostIP, DEFAULT_PORT);
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
	
	public void listen()
	{
		//Déclaration du Thread attendant la connexion
		Thread t = new Thread(new Runnable() {
			public void run()
			{
				System.out.println("En attente de connexion sur le port " + server.getLocalPort() + "...");
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
			server = new ServerHost("localhost");
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
