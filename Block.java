package blockChain;
import java.io.Serializable;
import java.security.MessageDigest;
import java.util.Date;
import java.nio.charset.StandardCharsets;



public class Block implements Serializable{
	
	private String data;
	private long timestamp;
	private int nonce;
	private String currentHash;
	private String prevHash;
	
	public Block() {
		this("Gensis block");
	}

	public Block(String data) {
		// TODO Auto-generated constructor stub
		this.data = data;
		this.timestamp = new Date().getTime();
		this.nonce = 0;
		this.prevHash = "";
		this.currentHash = calculateBlockHash();

	}
	
	public String calculateBlockHash() {
		String info = "";
		String inputData = data + Long.toString(timestamp) + Integer.toString(nonce) + prevHash;
		
		try {
			MessageDigest myDigest = MessageDigest.getInstance("SHA-256");
			byte[] hashBytes = myDigest.digest(inputData.getBytes("UTF-8"));
			
			
			StringBuffer buffer = new StringBuffer();
			for (byte b: hashBytes) {
			      buffer.append(String.format("%02x", b));
			}
			
			String hashStr = buffer.toString();
			
			return hashStr;
		}catch(Exception e){
			throw new RuntimeException("Error calculating block hash.", e);
		}
	}
	
	//Getter methods
	public String getHash() {
		return currentHash;
	}
	
	public String getPrevHash() {
		return prevHash;
	}
	
	public int getNonce() {
		return nonce;
	}
	
	//Setters
	
	public void setHash(String hash) {
		this.currentHash = hash;
	}
	
	public void setPrevHash(String prevhash) {
		this.prevHash = prevhash;
	}
	
	public void setNonce(int nonce) {
	    this.nonce = nonce;
	}
	
	//ToString method
	
	public String toString() {
		return "Block{" +
	            "data='" + data + '\'' +
	            ", timestamp=" + timestamp +
	            ", nonce=" + nonce +
	            ", hash='" + currentHash + '\'' +
	            ", Previous hash='" + prevHash + '\'' +
	            '}';
	}
		
}
