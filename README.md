# BlockChain
This project is composed of a simplified blockchain network in Java, starting with a single node that can create, mine, and validate blocks using SHA-256 hashing, then expanding it into a peer-to-peer distributed network where multiple nodes communicate over sockets, share mined blocks, and each maintain their own validated copy of the chain.



## Compile & Run

```bash
javac Block.java BCNode.java
java BCNode
```

---

## Starting a Network

### First Node (no peers)
```
Enter port to start (on current IP): 5000
Enter remote ports (current IP is assumed): 
```

### Second Node (connect to first)
```
Enter port to start (on current IP): 5001
Enter remote ports (current IP is assumed): 5000
```

### Third Node (connect to both)
```
Enter port to start (on current IP): 5002
Enter remote ports (current IP is assumed): 5000 5001
```

---

## Node Commands

| Option | Description |
|--------|-------------|
| `1` | Display this node's blockchain |
| `2` | Create and mine a new block |
| `3` | Kill the node |

---

## How It Works

### Mining (Proof of Work)
Each block is mined by repeatedly incrementing a `nonce` and recomputing the SHA-256 hash until the result has a required number of leading zeros. More leading zeros = longer mine time.

### Chain Validation
Before any block is added, the full chain is validated by checking:
- Each block's stored hash matches its recomputed hash
- Each block's `previousHash` matches the prior block's hash
- Each block's hash meets the Proof of Work condition

### Block Propagation
When a node mines a block, it sends it to all connected peers. Each peer validates it and if valid, adds it to their chain and forwards it onward.
