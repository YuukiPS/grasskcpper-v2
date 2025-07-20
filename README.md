# grasskcpper-v2
A kcp library built for a certain cute game

### Modification
* 4.6+ protocol support
* Kcp version detection support
* **Proxy Protocol v2 support** - Extract original client IP addresses when running behind proxy servers like FRP

### How to add it to your project
Add the AnimeGameServers maven repo to your build file:
```gradle
repositories {
    ...
    maven {
        name = "ags-mvn-Releases"
        url = uri("https://mvn.animegameservers.org/releases")
    }
}
```

Then add this to your dependencies in the gradle build file:

```gradle
implementation "org.anime_game_servers:grasskcpper:0.1"
```

### Proxy Protocol v2 Integration

This library now supports Proxy Protocol v2, enabling extraction of original client IP addresses when running behind proxy servers like FRP (Fast Reverse Proxy).

#### Quick Setup
```java
// Enable proxy protocol support
ChannelConfig config = new ChannelConfig();
config.setProxyProtocolV2Enabled(true);

// Your KCP listener will receive the original client IP
KcpListener listener = new KcpListener() {
    @Override
    public void onConnected(Ukcp ukcp) {
        // ukcp.user().getRemoteAddress() now contains the ORIGINAL client IP
        InetSocketAddress originalClient = ukcp.user().getRemoteAddress();
        System.out.println("Client connected from: " + originalClient.getAddress().getHostAddress());
    }
    // ... other methods
};

KcpServer server = new KcpServer();
server.init(listener, config, new InetSocketAddress(9999));
```

#### Features
- **Automatic Header Detection**: Gracefully handles both proxied and direct connections
- **IPv4 and IPv6 Support**: Works with both address families
- **Clean Payload Processing**: Strips proxy headers and processes original game data
- **FRP Compatible**: Tested with Fast Reverse Proxy configurations
- **Zero Configuration Fallback**: Works normally when no proxy headers are present

For detailed configuration and troubleshooting, see [PROXY_PROTOCOL_INTEGRATION.md](PROXY_PROTOCOL_INTEGRATION.md).

### Credits

Anime game changes: [Simplxss](https://github.com/Simplxss)

Version 4.6+ Changes: [OcenWang-GI](https://github.com/OcenWang-GI) [Commit](https://github.com/OcenWang-GI/AyakaPS-KCP/commit/921187d53f3cbab040699fb115e49a27efa5761a)

Original library: https://github.com/l42111996/java-Kcp
