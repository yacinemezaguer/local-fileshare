import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

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
	
	@Override
	public synchronized void close()
	{
		super.close();
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
	
	public static void main(String[] args) throws InterruptedException {
		ClientHost clientSocket = null;
		try 
		{
			//Crée un Thread qui tente de se connecter au serveur
			clientSocket = new ClientHost("localhost");
			clientSocket.runMessageListener();
			//Lancer la boucle de lecture des entrées utilisateur
			clientSocket.commandReader();
			
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
