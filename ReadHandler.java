package blockChain;

import java.io.EOFException;
import java.io.ObjectInputStream;
import java.net.SocketException;
import java.util.ArrayList;

public class ReadHandler implements Runnable {

	private BCNode node;
	private ObjectInputStream input;

	public ReadHandler(BCNode node, ObjectInputStream input) {
		this.node = node;
		this.input = input;
	}

	public void run() {
		try {
			while (true) {
				Object l = input.readObject();

				if (l instanceof String) {
					String msg = (String) l;
					if (msg.equals("REQ_CHAIN")) {
						node.handleChainRequest(input);
					}

				} else if (l instanceof ArrayList<?>) {
					ArrayList<?> incomingList = (ArrayList<?>) l;
					if (incomingList.size() > 0 && incomingList.get(0) instanceof Block) {
						@SuppressWarnings("unchecked")
						ArrayList<Block> incomingChain = (ArrayList<Block>) incomingList;
						node.handleIncomingChain(incomingChain);
					}

				} else if (l instanceof Block) {
					Block b = (Block) l;
					node.handleIncomingBlock(b);
				}
			}
		} catch (EOFException | SocketException e) {
			System.out.println("ReadHandler stopped - peer disconnected.");
			node.removePeerByInput(input);
		} catch (Exception e) {
			System.out.println("ReadHandler Stopped - Peer disconnected " + e.getMessage());
			node.removePeerByInput(input);
		}
	}
}