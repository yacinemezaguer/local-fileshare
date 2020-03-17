import java.net.InetAddress;

public class ConnectionOffer {
	protected int port;
	protected InetAddress address;
	
	public ConnectionOffer(int port, InetAddress address) {
		this.port = port;
		this.address = address;
	}
	
	public synchronized int getPort() {
		return port;
	}
	
	public synchronized InetAddress getAddress() {
		return address;
	}
	
	public synchronized void setPort(int port) {
		this.port = port;
	}
	
	public synchronized void setAddress(InetAddress address) {
		this.address = address;
	}
	
	@Override
	public boolean equals(Object o) {
		ConnectionOffer co = (ConnectionOffer) o;
		return this.port == co.port && this.address.equals(co.address);
	}
}
