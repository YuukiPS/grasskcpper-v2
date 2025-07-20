package kcp.highway;

import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * Proxy Protocol v2 parser for extracting original client IP addresses
 * when running behind FRP proxy or other proxy servers.
 * 
 * Supports both IPv4 and IPv6 addresses as defined in the Proxy Protocol v2 specification.
 * 
 * @see <a href="https://www.haproxy.org/download/1.8/doc/proxy-protocol.txt">Proxy Protocol Specification</a>
 */
public class ProxyProtocolV2Parser {
    private static final Logger logger = LoggerFactory.getLogger(ProxyProtocolV2Parser.class);
    
    // Proxy Protocol v2 signature: "\r\n\r\n\0\r\nQUIT\n"
    private static final byte[] PROXY_V2_SIGNATURE = {
        0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A
    };
    
    // Protocol versions
    private static final int VERSION_2 = 0x20;
    
    // Commands
    private static final int CMD_LOCAL = 0x00;
    private static final int CMD_PROXY = 0x01;
    
    // Address families
    private static final int AF_UNSPEC = 0x00;
    private static final int AF_INET = 0x10;
    private static final int AF_INET6 = 0x20;
    private static final int AF_UNIX = 0x30;
    
    // Protocols
    private static final int PROTO_UNSPEC = 0x00;
    private static final int PROTO_STREAM = 0x01;
    private static final int PROTO_DGRAM = 0x02;
    
    /**
     * Result of parsing a Proxy Protocol v2 header
     */
    public static class ProxyInfo {
        private final InetSocketAddress originalSource;
        private final InetSocketAddress originalDestination;
        private final int headerLength;
        private final boolean isProxied;
        
        public ProxyInfo(InetSocketAddress originalSource, InetSocketAddress originalDestination, 
                        int headerLength, boolean isProxied) {
            this.originalSource = originalSource;
            this.originalDestination = originalDestination;
            this.headerLength = headerLength;
            this.isProxied = isProxied;
        }
        
        public InetSocketAddress getOriginalSource() {
            return originalSource;
        }
        
        public InetSocketAddress getOriginalDestination() {
            return originalDestination;
        }
        
        public int getHeaderLength() {
            return headerLength;
        }
        
        public boolean isProxied() {
            return isProxied;
        }
    }
    
    /**
     * Checks if the buffer starts with a Proxy Protocol v2 header
     * 
     * @param buffer The buffer to check
     * @return true if the buffer starts with a valid Proxy Protocol v2 signature
     */
    public static boolean hasProxyProtocolV2Header(ByteBuf buffer) {
        if (buffer.readableBytes() < PROXY_V2_SIGNATURE.length) {
            return false;
        }
        
        // Check signature without modifying reader index
        for (int i = 0; i < PROXY_V2_SIGNATURE.length; i++) {
            if (buffer.getByte(buffer.readerIndex() + i) != PROXY_V2_SIGNATURE[i]) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Parses a Proxy Protocol v2 header from the buffer
     * 
     * @param buffer The buffer containing the proxy protocol header
     * @return ProxyInfo containing the parsed information, or null if parsing fails
     */
    public static ProxyInfo parseProxyProtocolV2(ByteBuf buffer) {
        if (!hasProxyProtocolV2Header(buffer)) {
            return null;
        }
        
        try {
            // Skip signature
            buffer.skipBytes(PROXY_V2_SIGNATURE.length);
            
            // Read version and command
            int versionCommand = buffer.readUnsignedByte();
            int version = versionCommand & 0xF0;
            int command = versionCommand & 0x0F;
            
            if (version != VERSION_2) {
                logger.warn("Unsupported proxy protocol version: {}", version >> 4);
                return null;
            }
            
            // Read address family and protocol
            int familyProtocol = buffer.readUnsignedByte();
            int family = familyProtocol & 0xF0;
            int protocol = familyProtocol & 0x0F;
            
            // Read address length
            int addressLength = buffer.readUnsignedShort();
            
            // Calculate total header length
            int headerLength = PROXY_V2_SIGNATURE.length + 4 + addressLength;
            
            // If command is LOCAL, no address information follows
            if (command == CMD_LOCAL) {
                buffer.skipBytes(addressLength);
                return new ProxyInfo(null, null, headerLength, false);
            }
            
            // Parse address information based on family
            InetSocketAddress originalSource = null;
            InetSocketAddress originalDestination = null;
            
            if (family == AF_INET && addressLength >= 12) {
                // IPv4: 4 bytes src + 4 bytes dst + 2 bytes src port + 2 bytes dst port
                byte[] srcBytes = new byte[4];
                byte[] dstBytes = new byte[4];
                
                buffer.readBytes(srcBytes);
                buffer.readBytes(dstBytes);
                
                int srcPort = buffer.readUnsignedShort();
                int dstPort = buffer.readUnsignedShort();
                
                try {
                    InetAddress srcAddr = InetAddress.getByAddress(srcBytes);
                    InetAddress dstAddr = InetAddress.getByAddress(dstBytes);
                    
                    originalSource = new InetSocketAddress(srcAddr, srcPort);
                    originalDestination = new InetSocketAddress(dstAddr, dstPort);
                } catch (UnknownHostException e) {
                    logger.warn("Failed to parse IPv4 addresses from proxy protocol header", e);
                }
                
                // Skip any remaining bytes
                int remaining = addressLength - 12;
                if (remaining > 0) {
                    buffer.skipBytes(remaining);
                }
                
            } else if (family == AF_INET6 && addressLength >= 36) {
                // IPv6: 16 bytes src + 16 bytes dst + 2 bytes src port + 2 bytes dst port
                byte[] srcBytes = new byte[16];
                byte[] dstBytes = new byte[16];
                
                buffer.readBytes(srcBytes);
                buffer.readBytes(dstBytes);
                
                int srcPort = buffer.readUnsignedShort();
                int dstPort = buffer.readUnsignedShort();
                
                try {
                    InetAddress srcAddr = InetAddress.getByAddress(srcBytes);
                    InetAddress dstAddr = InetAddress.getByAddress(dstBytes);
                    
                    originalSource = new InetSocketAddress(srcAddr, srcPort);
                    originalDestination = new InetSocketAddress(dstAddr, dstPort);
                } catch (UnknownHostException e) {
                    logger.warn("Failed to parse IPv6 addresses from proxy protocol header", e);
                }
                
                // Skip any remaining bytes
                int remaining = addressLength - 36;
                if (remaining > 0) {
                    buffer.skipBytes(remaining);
                }
                
            } else {
                // Unsupported family or insufficient data, skip the address data
                logger.debug("Unsupported address family {} or insufficient data length {}", 
                           family >> 4, addressLength);
                buffer.skipBytes(addressLength);
            }
            
            return new ProxyInfo(originalSource, originalDestination, headerLength, 
                               command == CMD_PROXY && originalSource != null);
            
        } catch (Exception e) {
            logger.error("Error parsing proxy protocol v2 header", e);
            return null;
        }
    }
    
    /**
     * Convenience method to extract original client address from a buffer
     * 
     * @param buffer The buffer that may contain a proxy protocol header
     * @param fallbackAddress The address to use if no proxy protocol header is found
     * @return The original client address or the fallback address
     */
    public static InetSocketAddress extractOriginalClientAddress(ByteBuf buffer, InetSocketAddress fallbackAddress) {
        if (!hasProxyProtocolV2Header(buffer)) {
            logger.debug("No proxy protocol v2 header found, using fallback address: {}", fallbackAddress);
            return fallbackAddress;
        }
        
        // Save reader index to restore if parsing fails
        int originalReaderIndex = buffer.readerIndex();
        
        try {
            ProxyInfo proxyInfo = parseProxyProtocolV2(buffer);
            if (proxyInfo != null && proxyInfo.isProxied() && proxyInfo.getOriginalSource() != null) {
                logger.info("Extracted original client address: {} (was: {})", 
                           proxyInfo.getOriginalSource(), fallbackAddress);
                return proxyInfo.getOriginalSource();
            } else {
                logger.debug("Proxy protocol header found but no valid client address extracted, using fallback: {}", fallbackAddress);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse proxy protocol header, using fallback address", e);
            // Restore reader index on error
            buffer.readerIndex(originalReaderIndex);
        }
        
        return fallbackAddress;
    }
    
    /**
     * Strips proxy protocol v2 headers from the buffer and returns the clean payload
     * along with the original client information.
     * 
     * @param buffer The buffer that may contain a proxy protocol header
     * @param fallbackAddress The address to use if no proxy protocol header is found
     * @return ProxyStrippedResult containing the clean payload and original client info
     */
    public static ProxyStrippedResult stripProxyProtocolV2(ByteBuf buffer, InetSocketAddress fallbackAddress) {
        logger.debug("Processing buffer with {} readable bytes from address: {}", 
                    buffer.readableBytes(), fallbackAddress);
        
        if (!hasProxyProtocolV2Header(buffer)) {
            logger.debug("No proxy protocol v2 header detected, returning original buffer");
            return new ProxyStrippedResult(buffer.retain(), fallbackAddress, false);
        }
        
        logger.debug("Proxy protocol v2 header detected, parsing...");
        
        // Save reader index to restore if parsing fails
        int originalReaderIndex = buffer.readerIndex();
        
        try {
            // Parse without modifying the original buffer's reader index
            ByteBuf parseBuffer = buffer.duplicate();
            ProxyInfo proxyInfo = parseProxyProtocolV2(parseBuffer);
            if (proxyInfo == null) {
                logger.warn("Failed to parse proxy protocol header, returning original buffer");
                return new ProxyStrippedResult(buffer.retain(), fallbackAddress, false);
            }
            
            logger.debug("Proxy protocol parsed successfully - Header length: {}, Is proxied: {}, Original source: {}", 
                        proxyInfo.getHeaderLength(), proxyInfo.isProxied(), proxyInfo.getOriginalSource());
            
            // Create a new buffer with just the payload (skip proxy headers)
            int payloadStartIndex = originalReaderIndex + proxyInfo.getHeaderLength();
            int payloadLength = buffer.readableBytes() - proxyInfo.getHeaderLength();
            
            if (payloadLength <= 0) {
                logger.warn("No payload data after proxy headers, header length: {}, total buffer size: {}", 
                           proxyInfo.getHeaderLength(), buffer.readableBytes());
                return new ProxyStrippedResult(buffer.alloc().buffer(0), fallbackAddress, proxyInfo.isProxied());
            }
            
            ByteBuf cleanPayload = buffer.slice(payloadStartIndex, payloadLength).retain();
            
            InetSocketAddress originalClient = proxyInfo.isProxied() && proxyInfo.getOriginalSource() != null 
                ? proxyInfo.getOriginalSource() 
                : fallbackAddress;
                
            logger.debug("Stripped proxy headers, payload size: {} bytes, using client address: {}", 
                        cleanPayload.readableBytes(), originalClient);
            
            return new ProxyStrippedResult(cleanPayload, originalClient, proxyInfo.isProxied());
            
        } catch (Exception e) {
            logger.error("Error stripping proxy protocol header, returning original buffer", e);
            return new ProxyStrippedResult(buffer.retain(), fallbackAddress, false);
        }
    }
    
    /**
     * Result of stripping proxy protocol headers
     */
    public static class ProxyStrippedResult {
        private final ByteBuf cleanPayload;
        private final InetSocketAddress originalClient;
        private final boolean wasProxied;
        
        public ProxyStrippedResult(ByteBuf cleanPayload, InetSocketAddress originalClient, boolean wasProxied) {
            this.cleanPayload = cleanPayload;
            this.originalClient = originalClient;
            this.wasProxied = wasProxied;
        }
        
        public ByteBuf getCleanPayload() {
            return cleanPayload;
        }
        
        public InetSocketAddress getOriginalClient() {
            return originalClient;
        }
        
        public boolean wasProxied() {
            return wasProxied;
        }
        
        public void release() {
            if (cleanPayload != null) {
                cleanPayload.release();
            }
        }
    }
}