# Grasskcpper - KCP Library Documentation

## What is Grasskcpper?

Grasskcpper is a Java implementation of the KCP (KCP Reliable UDP) protocol, specifically modified for use with "a certain cute game" (likely Genshin Impact based on the context). It's built on top of the original [java-Kcp](https://github.com/l42111996/java-Kcp) library with enhancements for protocol version 4.6+ support and KCP version detection.

## What is KCP?

KCP is a fast and reliable ARQ (Automatic Repeat reQuest) protocol that aims to achieve the transmission effect of a reduction of 30% to 40% in the average latency at the cost of 10% to 20% more bandwidth wasted than TCP. It's designed to be:

- **Fast**: Lower latency than TCP
- **Reliable**: Ensures packet delivery like TCP
- **Efficient**: Better performance for real-time applications
- **UDP-based**: Built on top of UDP for speed

## Key Features

### Core Features
- **4.6+ Protocol Support**: Enhanced for newer game protocol versions
- **KCP Version Detection**: Automatically detects and handles different KCP versions
- **Netty Integration**: Built using Netty for high-performance networking
- **FEC Support**: Forward Error Correction for improved reliability
- **CRC32 Validation**: Optional packet integrity checking
- **Stream Mode**: Support for both message and stream modes

### Performance Features
- **Fast Flush**: Immediate packet sending for low latency
- **No Delay Mode**: Configurable for minimal latency
- **Congestion Control**: Advanced window management
- **Buffer Management**: Configurable read/write buffer sizes
- **Thread Pool**: Efficient message processing with thread pools

## Architecture Overview

### Core Components

1. **Kcp Class**: The main KCP protocol implementation
   - Handles packet segmentation, acknowledgments, and retransmission
   - Manages send/receive windows and congestion control
   - Supports different KCP versions (BASE, HOYO_V1)

2. **Ukcp Class**: User-facing KCP connection wrapper
   - Provides high-level API for sending/receiving data
   - Manages connection lifecycle and buffers
   - Handles FEC encoding/decoding if enabled

3. **KcpServer**: Server-side KCP implementation
   - Listens for incoming connections
   - Manages multiple client connections
   - Supports both address-based and conversation-based channel management

4. **KcpClient**: Client-side KCP implementation
   - Connects to KCP servers
   - Supports reconnection functionality
   - Handles connection management

5. **ChannelConfig**: Configuration class
   - Contains all tunable parameters
   - Controls performance characteristics
   - Manages feature enablement

### Key Interfaces

- **KcpListener**: Callback interface for connection events
- **KcpOutput**: Interface for sending packets
- **IChannelManager**: Manages multiple KCP connections

## How It Works

### Connection Flow

1. **Server Setup**:
   ```java
   KcpServer server = new KcpServer();
   ChannelConfig config = new ChannelConfig();
   KcpListener listener = new MyKcpListener();
   server.init(listener, config, new InetSocketAddress(port));
   ```

2. **Client Connection**:
   ```java
   KcpClient client = new KcpClient();
   client.init(config, listener);
   Ukcp connection = client.connect(serverAddress, config);
   ```

3. **Data Exchange**:
   ```java
   // Sending data
   ByteBuf data = Unpooled.buffer();
   data.writeBytes("Hello World".getBytes());
   connection.write(data);
   
   // Receiving data (via KcpListener)
   public void handleReceive(ByteBuf data, Ukcp ukcp) {
       // Process received data
   }
   ```

### Protocol Versions

The library supports multiple KCP versions:

- **KCP_BASE**: Standard KCP protocol (28-byte overhead)
- **KCP_HOYO_V1**: Enhanced version with additional features (32-byte overhead)
- **KCP_UNKNOWN**: Auto-detection mode

### Packet Structure

KCP packets contain:
- **Header**: Protocol information, sequence numbers, acknowledgments
- **Data**: Actual payload
- **Optional**: CRC32 checksum, FEC data

### Reliability Mechanisms

1. **Acknowledgments**: Receivers send ACKs for received packets
2. **Retransmission**: Unacknowledged packets are retransmitted
3. **Sequence Numbers**: Ensure packet ordering
4. **Window Management**: Flow control and congestion avoidance
5. **Fast Retransmit**: Quick recovery from packet loss

## Configuration Options

### Basic Configuration
```java
ChannelConfig config = new ChannelConfig();
config.setConv(12345);                    // Conversation ID
config.setMtu(1400);                      // Maximum Transmission Unit
config.setTimeoutMillis(30000);           // Connection timeout
```

### Performance Tuning
```java
// Low latency configuration
config.nodelay(true, 10, 2, true);
config.setFastFlush(true);
config.setAckNoDelay(true);

// Window sizes
config.setSndwnd(256);                    // Send window
config.setRcvwnd(256);                    // Receive window

// Buffer sizes
config.setReadBufferSize(1024 * 1024);   // 1MB read buffer
config.setWriteBufferSize(1024 * 1024);   // 1MB write buffer
```

### Advanced Features
```java
// Enable CRC32 checking
config.setCrc32Check(true);

// Enable FEC (Forward Error Correction)
FecAdapt fecAdapt = new FecAdapt(10, 3);  // 10 data + 3 parity
config.setFecAdapt(fecAdapt);

// Use conversation-based channels
config.setUseConvChannel(true);
```

## Usage Examples

### Simple Echo Server
```java
public class EchoServer {
    public static void main(String[] args) {
        KcpServer server = new KcpServer();
        ChannelConfig config = new ChannelConfig();
        
        KcpListener listener = new KcpListener() {
            @Override
            public void onConnected(Ukcp ukcp) {
                System.out.println("Client connected: " + ukcp.user().getRemoteAddress());
            }
            
            @Override
            public void handleReceive(ByteBuf data, Ukcp ukcp) {
                // Echo back the received data
                ukcp.write(data.copy());
            }
            
            @Override
            public void handleException(Throwable ex, Ukcp ukcp) {
                ex.printStackTrace();
            }
            
            @Override
            public void handleClose(Ukcp ukcp) {
                System.out.println("Client disconnected");
            }
        };
        
        server.init(listener, config, new InetSocketAddress(9999));
    }
}
```

### Simple Client
```java
public class SimpleClient {
    public static void main(String[] args) {
        KcpClient client = new KcpClient();
        ChannelConfig config = new ChannelConfig();
        
        KcpListener listener = new KcpListener() {
            @Override
            public void onConnected(Ukcp ukcp) {
                // Send initial message
                ByteBuf data = Unpooled.buffer();
                data.writeBytes("Hello Server!".getBytes());
                ukcp.write(data);
            }
            
            @Override
            public void handleReceive(ByteBuf data, Ukcp ukcp) {
                byte[] bytes = new byte[data.readableBytes()];
                data.readBytes(bytes);
                System.out.println("Received: " + new String(bytes));
            }
            
            @Override
            public void handleException(Throwable ex, Ukcp ukcp) {
                ex.printStackTrace();
            }
            
            @Override
            public void handleClose(Ukcp ukcp) {
                System.out.println("Connection closed");
            }
        };
        
        client.init(config, listener);
        Ukcp connection = client.connect(new InetSocketAddress("localhost", 9999), config);
    }
}
```

## Dependencies

The library requires:
- **Netty 4.1.90.Final**: For networking infrastructure
- **SLF4J 2.0.5**: For logging
- **Zero Allocation Hashing 0.26ea0**: For efficient hashing

## Integration

Add to your `build.gradle`:
```gradle
repositories {
    maven {
        name = "ags-mvn-Releases"
        url = uri("https://mvn.animegameservers.org/releases")
    }
}

dependencies {
    implementation "org.anime_game_servers:grasskcpper:0.1"
}
```

## Performance Considerations

### Latency Optimization
- Enable `nodelay` mode for minimum latency
- Use `fastFlush` for immediate packet sending
- Set small `interval` values (10-20ms)
- Enable `ackNoDelay` for faster acknowledgments

### Throughput Optimization
- Increase window sizes (`sndwnd`, `rcvwnd`)
- Use larger MTU values if network supports it
- Disable `fastFlush` for better CPU efficiency
- Configure appropriate buffer sizes

### Memory Management
- Set buffer size limits to prevent memory exhaustion
- Use object pooling for frequent allocations
- Monitor and tune garbage collection

## Troubleshooting

### Common Issues
1. **High CPU Usage**: Disable `fastFlush`, increase `interval`
2. **Memory Leaks**: Ensure proper buffer release and connection cleanup
3. **Connection Timeouts**: Adjust `timeoutMillis` and network parameters
4. **Packet Loss**: Enable FEC, tune retransmission parameters

### Debugging
- Enable detailed logging for network events
- Monitor connection statistics via `Ukcp.srtt()`
- Use network analysis tools to inspect packet flow

## Credits

- **Original Library**: [java-Kcp](https://github.com/l42111996/java-Kcp)
- **Anime Game Changes**: [Simplxss](https://github.com/Simplxss)
- **Version 4.6+ Changes**: [OcenWang-GI](https://github.com/OcenWang-GI)

This documentation provides a comprehensive overview of the Grasskcpper KCP library, its architecture, and usage patterns for building reliable, low-latency UDP-based applications.