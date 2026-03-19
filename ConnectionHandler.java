package blockChain;

import java.net.Socket;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class ConnectionHandler implements Runnable {

	private BCNode node;

	public ConnectionHandler(BCNode node) {
		this.node = node;
	}

	public void run() {
		while (true) {
			try {
				Socket n = node.getServerSocket().accept();

				ObjectOutputStream out = new ObjectOutputStream(n.getOutputStream());
				out.flush();
				ObjectInputStream in = new ObjectInputStream(n.getInputStream());

				node.registerNewPeer(n, out, in);

				System.out.println("Accepted connection from " + n.getRemoteSocketAddress());

			} catch (IOException e) {
				System.out.println("ConnectionHandler error: " + e.getMessage());
			}
		}
	}
}