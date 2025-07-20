package kcp.highway;

import io.netty.channel.Channel;

import java.net.InetSocketAddress;

/**
 * Created by JinMiao
 * 2018/11/2.
 */
public class User {

    private Channel channel;
    private InetSocketAddress remoteAddress;  // Address to send responses to (proxy or direct client)
    private InetSocketAddress originalClientAddress;  // Original client address (for proxy scenarios)
    private InetSocketAddress localAddress;

    private Object cache;

    public void setCache(Object cache) {
        this.cache = cache;
    }

    public <T>  T getCache() {
        return (T) cache;
    }

    public User(Channel channel, InetSocketAddress remoteAddress, InetSocketAddress localAddress) {
        this.channel = channel;
        this.remoteAddress = remoteAddress;
        this.originalClientAddress = remoteAddress;  // Default: same as remote address
        this.localAddress = localAddress;
    }
    
    public User(Channel channel, InetSocketAddress remoteAddress, InetSocketAddress originalClientAddress, InetSocketAddress localAddress) {
        this.channel = channel;
        this.remoteAddress = remoteAddress;
        this.originalClientAddress = originalClientAddress;
        this.localAddress = localAddress;
    }

    protected Channel getChannel() {
        return channel;
    }

    protected void setChannel(Channel channel) {
        this.channel = channel;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    protected void setRemoteAddress(InetSocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }
    
    public InetSocketAddress getOriginalClientAddress() {
        return originalClientAddress;
    }
    
    protected void setOriginalClientAddress(InetSocketAddress originalClientAddress) {
        this.originalClientAddress = originalClientAddress;
    }

    protected InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    protected void setLocalAddress(InetSocketAddress localAddress) {
        this.localAddress = localAddress;
    }


    @Override
    public String toString() {
        return "User{" +
                "remoteAddress=" + remoteAddress +
                ", localAddress=" + localAddress +
                '}';
    }

}
