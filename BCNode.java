package blockChain;

import java.util.ArrayList;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Scanner;
import java.net.Inet4Address;
import java.net.UnknownHostException;

public class BCNode {
	private ArrayList<Block> chain;

	private int myPort;

	private ArrayList<Socket> peerSockets;
	private ArrayList<ObjectOutputStream> peerOutputs;
	private ArrayList<ObjectInputStream> peerInputs;

	private ServerSocket serverSocket;

	private int difficulty = 5;
	private String prefixZeros = new String(new char[difficulty]).replace('\0', '0');

	public BCNode(int myPort, List<Integer> remoteNodes) {
		this.myPort = myPort;
		peerSockets = new ArrayList<Socket>();
		peerOutputs = new ArrayList<ObjectOutputStream>();
		peerInputs = new ArrayList<ObjectInputStream>();

		chain = new ArrayList<Block>();

		// set up server socket first
		try {
			serverSocket = new ServerSocket(myPort);
		} catch (IOException e) {
			throw new RuntimeException("Could not start server socket on port " + myPort, e);
		}

		// start thread to accept incoming connections
		Thread t = new Thread(new ConnectionHandler(this));
		t.start();

		// always start with a local genesis (may later be replaced by peer chain)
		Block genesis = new Block("GenesisBlock");
		chain.add(genesis);

		// connect to other remote nodes
		for (int remoteNode : remoteNodes) {
			try {
				Socket s = new Socket("localhost", remoteNode);

				ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
				out.flush();

				ObjectInputStream in = new ObjectInputStream(s.getInputStream());

				peerSockets.add(s);
				peerOutputs.add(out);
				peerInputs.add(in);

				Thread readerThread = new Thread(new ReadHandler(this, in));
				readerThread.start();

				// request chain from peer (synchronized write)
				synchronized (out) {
					out.writeObject("REQ_CHAIN");
					out.reset();
					out.flush();
				}

				System.out.println("Connected to remote port " + remoteNode);

			} catch (Exception e) {
				System.out.println("Could not connect to port " + remoteNode + ": " + e.getMessage());
			}
		}
	}

	public synchronized void addBlock(Block b) {
		Block lastBlock = chain.get(chain.size() - 1); // get last block hash
		b.setPrevHash(lastBlock.getHash());
		b.setHash(b.calculateBlockHash());

		// mining
		while (!b.getHash().substring(0, difficulty).equals(prefixZeros)) {
			b.setNonce(b.getNonce() + 1);
			b.setHash(b.calculateBlockHash());
		}

		// add block temporarily
		chain.add(b);

		// validate; if invalid remove it
		if (validateChain() == false) {
			chain.remove(chain.size() - 1);
			System.out.println("Local mined block rejected.");
		} else {
			// valid -> send to everyone else
			for (ObjectOutputStream o : peerOutputs) {
				try {
					synchronized (o) {
						o.writeObject(b);
						o.reset(); // required by teacher note
						o.flush();
					}
				} catch (IOException e) {
					System.out.println("Could not send mined block: " + e.getMessage());
				}
			}
			System.out.println("Local mined block accepted and broadcast.");
		}
	}

	// Validation Function
	public synchronized boolean validateChain() {
		for (int i = 0; i < chain.size(); i++) {
			Block current = chain.get(i);

			// block has correct hash stored in it
			if (!current.getHash().equals(current.calculateBlockHash())) {
				return false;
			}

			// checks after genesis
			if (i > 0) {
				Block previous = chain.get(i - 1);

				// correct previous hash link
				if (!current.getPrevHash().equals(previous.getHash())) {
					return false;
				}

				// PoW condition
				if (!current.getHash().substring(0, difficulty).equals(prefixZeros)) {
					return false;
				}
			}
		}
		return true;
	}

	public synchronized String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Blockchain:\n");

		for (int i = 0; i < chain.size(); i++) {
			sb.append("[").append(i).append("] ")
			  .append(chain.get(i))
			  .append("\n");
		}

		return sb.toString();
	}

	// ---------------------------
	// Helper methods for handlers
	// ---------------------------

	public ServerSocket getServerSocket() {
		return serverSocket;
	}

	public synchronized void registerNewPeer(Socket n, ObjectOutputStream out, ObjectInputStream in) {
		peerSockets.add(n);
		peerOutputs.add(out);
		peerInputs.add(in);

		Thread readerThread = new Thread(new ReadHandler(this, in));
		readerThread.start();

		// request chain from the new peer too
		try {
			synchronized (out) {
				out.writeObject("REQ_CHAIN");
				out.reset();
				out.flush();
			}
		} catch (IOException e) {
			System.out.println("Could not request chain from new peer: " + e.getMessage());
		}
	}

	public synchronized void handleChainRequest(ObjectInputStream input) {
		try {
			ArrayList<Block> chainCopy = new ArrayList<Block>(chain);

			int idx = peerInputs.indexOf(input);
			if (idx >= 0) {
				ObjectOutputStream out = peerOutputs.get(idx);

				synchronized (out) {
					out.writeObject(chainCopy);
					out.reset();
					out.flush();
				}
			}
		} catch (IOException e) {
			System.out.println("Could not send chain: " + e.getMessage());
		}
	}

	public synchronized void handleIncomingChain(ArrayList<Block> incomingChain) {
		if (incomingChain == null || incomingChain.size() == 0) {
			return;
		}

		// Save old chain
		ArrayList<Block> oldChain = new ArrayList<Block>(chain);

		boolean shouldTryAdopt = false;

		// normal case: longer chain
		if (incomingChain.size() > chain.size()) {
			shouldTryAdopt = true;
		}
		// startup sync case: both length 1 (genesis only) but hashes differ
		else if (incomingChain.size() == 1 && chain.size() == 1) {
			String myGenesisHash = chain.get(0).getHash();
			String theirGenesisHash = incomingChain.get(0).getHash();
			if (!myGenesisHash.equals(theirGenesisHash)) {
				shouldTryAdopt = true;
			}
		}

		if (shouldTryAdopt) {
			chain = new ArrayList<Block>(incomingChain);

			if (!validateChain()) {
				chain = oldChain; // revert if invalid
			} else {
				System.out.println("Adopted chain from peer (" + chain.size() + " blocks).");
			}
		}
	}

	public synchronized void handleIncomingBlock(Block b) {
		// duplicate check
		for (Block existing : chain) {
			if (existing.getHash().equals(b.getHash())) {
				return; // already have it
			}
		}

		// tip check first (prevents adding stale/incompatible block)
		String localTipHash = chain.get(chain.size() - 1).getHash();
		if (!b.getPrevHash().equals(localTipHash)) {
			// ask peers for chain in case we are behind
			for (ObjectOutputStream out : peerOutputs) {
				try {
					synchronized (out) {
						out.writeObject("REQ_CHAIN");
						out.reset();
						out.flush();
					}
				} catch (IOException e) {
					System.out.println("Could not request chain after stale block: " + e.getMessage());
				}
			}
			return;
		}

		// add and validate
		chain.add(b);

		if (validateChain()) {
			System.out.println("Accepted incoming block.");

			// forward to others
			for (ObjectOutputStream out : peerOutputs) {
				try {
					synchronized (out) {
						out.writeObject(b);
						out.reset();
						out.flush();
					}
				} catch (IOException e) {
					System.out.println("Could not forward block: " + e.getMessage());
				}
			}
		} else {
			chain.remove(chain.size() - 1);
			System.out.println("Rejected incoming block (validation failed).");
		}
	}

	public synchronized void removePeerByInput(ObjectInputStream input) {
		int idx = peerInputs.indexOf(input);
		if (idx >= 0) {
			try { peerInputs.get(idx).close(); } catch (Exception e) {}
			try { peerOutputs.get(idx).close(); } catch (Exception e) {}
			try { peerSockets.get(idx).close(); } catch (Exception e) {}

			peerInputs.remove(idx);
			peerOutputs.remove(idx);
			peerSockets.remove(idx);

			System.out.println("Removed disconnected peer.");
		}
	}

	// ---------------------------
	// Main (must stay in BCNode)
	// ---------------------------
	public static void main(String[] args) {
		Scanner keyScan = new Scanner(System.in);

		// Grab my port number on which to start this node
		System.out.print("Enter port to start (on current IP): ");
		int myPort = keyScan.nextInt();

		// Need to get what other Nodes to connect to
		System.out.print("Enter remote ports (current IP is assumed): ");
		keyScan.nextLine(); // skip NL after nextInt
		String line = keyScan.nextLine();

		List<Integer> remotePorts = new ArrayList<Integer>();
		if (!line.trim().isEmpty()) {
			String[] splitLine = line.trim().split("\\s+");
			for (int i = 0; i < splitLine.length; i++) {
				remotePorts.add(Integer.parseInt(splitLine[i]));
			}
		}

		// Create the Node
		BCNode n = new BCNode(myPort, remotePorts);

		String ip = "";
		try {
			ip = Inet4Address.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(1);
		}

		System.out.println("Node started on " + ip + ": " + myPort);

		// Node command line interface
		while (true) {
			System.out.println("\nNODE on port: " + myPort);
			System.out.println("1. Display Node's blockchain");
			System.out.println("2. Create/mine new Block");
			System.out.println("3. Kill Node");
			System.out.print("Enter option: ");
			int in = keyScan.nextInt();

			if (in == 1) {
				System.out.println(n);

			} else if (in == 2) {
				// Grab the information to put in the block
				System.out.print("Enter information for new Block: ");
				String blockInfo = keyScan.next();
				Block b = new Block(blockInfo);
				n.addBlock(b);

			} else if (in == 3) {
				keyScan.close();
				System.exit(0);
			}
		}
	}
}