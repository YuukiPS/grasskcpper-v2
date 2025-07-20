package kcp.highway;

import io.netty.buffer.Unpooled;
import kcp.highway.erasure.fec.Fec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.HashedWheelTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kcp.highway.threadPool.IMessageExecutor;
import kcp.highway.threadPool.IMessageExecutorPool;
import kcp.highway.threadPool.ITask;

import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * Created by JinMiao
 * 2018/9/20.
 */
public class ServerChannelHandler extends ChannelInboundHandlerAdapter {
    record HandshakeWaiter(long convId, InetSocketAddress address){

    }
    private static final Logger logger = LoggerFactory.getLogger(ServerChannelHandler.class);

    private final ServerConvChannelManager channelManager;

    private final ChannelConfig channelConfig;

    private final IMessageExecutorPool iMessageExecutorPool;

    private final KcpListener kcpListener;

    private final HashedWheelTimer hashedWheelTimer;
    private final ConcurrentLinkedQueue<HandshakeWaiter> handshakeWaiters = new ConcurrentLinkedQueue<>();
    // Cache for faster handshake waiter lookups by address
    private final java.util.concurrent.ConcurrentHashMap<InetSocketAddress, HandshakeWaiter> handshakeWaitersByAddress = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, HandshakeWaiter> handshakeWaitersByConv = new java.util.concurrent.ConcurrentHashMap<>();

    private final SecureRandom secureRandom = new SecureRandom();

    public void handshakeWaitersAppend(HandshakeWaiter handshakeWaiter){
        // Optimize: Check size outside synchronized block to reduce contention
        if(handshakeWaiters.size() > 10){
            synchronized (handshakeWaiters){
                // Double-check inside synchronized block
                if(handshakeWaiters.size() > 10) {
                    HandshakeWaiter removed = handshakeWaiters.poll();
                    if(removed != null) {
                        handshakeWaitersByAddress.remove(removed.address());
                        handshakeWaitersByConv.remove(removed.convId());
                    }
                }
            }
        }
        
        // Add new waiter (ConcurrentHashMap and ConcurrentLinkedQueue are thread-safe)
        handshakeWaiters.add(handshakeWaiter);
        handshakeWaitersByAddress.put(handshakeWaiter.address(), handshakeWaiter);
        handshakeWaitersByConv.put(handshakeWaiter.convId(), handshakeWaiter);
    }
    public void handshakeWaitersRemove(HandshakeWaiter handshakeWaiter){
        handshakeWaiters.remove(handshakeWaiter);
        handshakeWaitersByAddress.remove(handshakeWaiter.address());
        handshakeWaitersByConv.remove(handshakeWaiter.convId());
    }
    public HandshakeWaiter handshakeWaitersFind(long conv){
        return handshakeWaitersByConv.get(conv);
    }
    public HandshakeWaiter handshakeWaitersFind(InetSocketAddress address){
        return handshakeWaitersByAddress.get(address);
    }
    // Handle handshake
    public static void handleEnet(ByteBuf data, Ukcp ukcp, User user, long conv) {
        if (data == null || data.readableBytes() != 20) {
            return;
        }
        // Get
        int code = data.readInt();
        data.readUnsignedIntLE(); // Empty
        data.readUnsignedIntLE(); // Empty
        int enet = data.readInt();
        data.readUnsignedInt();
        try{
            switch (code) {
                case 255 -> { // Connect + Handshake
                    if(user!=null) {
                        Ukcp.sendHandshakeRsp(user, enet, conv);
                    }
                }
                case 404 -> { // Disconnect
                    if(ukcp!=null) {
                        ukcp.close(false);
                    }
                }
            }
        }catch (Throwable ignore){
        }
    }


    public ServerChannelHandler(IChannelManager channelManager, ChannelConfig channelConfig, IMessageExecutorPool iMessageExecutorPool, KcpListener kcpListener,HashedWheelTimer hashedWheelTimer) {
        this.channelManager = (ServerConvChannelManager) channelManager;
        this.channelConfig = channelConfig;
        this.iMessageExecutorPool = iMessageExecutorPool;
        this.kcpListener = kcpListener;
        this.hashedWheelTimer = hashedWheelTimer;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("", cause);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object object) {
        final ChannelConfig channelConfig = this.channelConfig;
        DatagramPacket msg = (DatagramPacket) object;
        ByteBuf originalByteBuf = msg.content();
        
        logger.debug("Received UDP packet from {} to {} with {} bytes", 
                    msg.sender(), msg.recipient(), originalByteBuf.readableBytes());
        
        // Process proxy protocol if enabled
        final ByteBuf cleanPayload;
        final InetSocketAddress originalSender;
        ProxyProtocolV2Parser.ProxyStrippedResult proxyResult = null;
        
        // Cache proxy protocol enabled check to avoid repeated method calls
        final boolean isProxyEnabled = channelConfig.isProxyProtocolV2Enabled();
        
        if (isProxyEnabled) {
            logger.debug("Proxy protocol v2 is enabled, processing headers...");
            proxyResult = ProxyProtocolV2Parser.stripProxyProtocolV2(originalByteBuf, msg.sender());
            cleanPayload = proxyResult.getCleanPayload();
            originalSender = proxyResult.getOriginalClient();
            
            logger.debug("Proxy processing result - Was proxied: {}, Original client: {}, Clean payload size: {} bytes", 
                        proxyResult.wasProxied(), originalSender, cleanPayload.readableBytes());
        } else {
            logger.debug("Proxy protocol v2 is disabled, using original packet");
            cleanPayload = originalByteBuf.retain();
            originalSender = msg.sender();
        }
        
        try {
            // Cache frequently accessed values
            final int payloadSize = cleanPayload.readableBytes();
            
            // Create User object with proxy address for responses and original client for application logic
            User user = new User(ctx.channel(), msg.sender(), originalSender, msg.recipient());
            logger.debug("Created user object - Response target: {}, Original client: {}", msg.sender(), originalSender);
            
            // Create a new DatagramPacket with the clean payload and original sender
             DatagramPacket processedMsg = new DatagramPacket(cleanPayload.retain(), msg.recipient(), originalSender);
             Ukcp ukcp = channelManager.get(processedMsg);
             processedMsg.release(); // Release the retained buffer
            
            logger.debug("Channel manager lookup result - Found existing connection: {}", ukcp != null);
            
            if(payloadSize == 20){
                logger.debug("Processing handshake packet (20 bytes) from client: {}", originalSender);
                // send handshake
                HandshakeWaiter waiter = handshakeWaitersFind(user.getOriginalClientAddress());
                long convId;
                if(waiter==null) {
                    logger.debug("No existing handshake waiter found, generating new convId");
                    //generate unique convId
                    synchronized (channelManager) {
                        do {
                            convId = secureRandom.nextLong();
                        } while (channelManager.convExists(convId) || handshakeWaitersFind(convId) != null);
                    }
                    logger.info("Generated new convId: {} for client: {}", convId, user.getOriginalClientAddress());
                    handshakeWaitersAppend(new HandshakeWaiter(convId, user.getOriginalClientAddress()));
                }else{
                    logger.debug("Using existing handshake waiter with convId: {}", waiter.convId);
                    convId = waiter.convId;
                }
                logger.debug("Sending handshake response with convId: {} to client: {} via proxy: {}", convId, originalSender, msg.sender());
                handleEnet(cleanPayload, ukcp, user, convId);
                return;
            }
            
            logger.debug("Processing data packet ({} bytes) from client: {}", cleanPayload.readableBytes(), originalSender);
            boolean newConnection = false;
            IMessageExecutor iMessageExecutor = iMessageExecutorPool.getIMessageExecutor();
            
            if (ukcp == null) {// finished handshake
                logger.debug("No existing connection found, checking for handshake completion");
                if (payloadSize < 8) {
                    logger.warn("Packet too small for handshake completion from {}, size: {}", originalSender, payloadSize);
                    return;
                }
                
                long convId = cleanPayload.getLong(0);
                HandshakeWaiter waiter = handshakeWaitersFind(convId);
                if (waiter == null) {
                    logger.warn("Establishing handshake to {} failure, Conv id {} not found in waiters", user.getOriginalClientAddress(), convId);
                    return;
                } else {
                    logger.debug("Found handshake waiter for convId: {}, completing handshake for client: {}", convId, originalSender);
                    handshakeWaitersRemove(waiter);
                    int sn = getSn(cleanPayload, channelConfig);
                    if (sn != 0) {
                        logger.warn("Establishing handshake to {} failure, SN!=0 (SN={})", user.getOriginalClientAddress(), sn);
                        return;
                    }
                    logger.info("Established handshake to {} ,Conv convId={}", user.getOriginalClientAddress(), waiter.convId);
                    KcpOutput kcpOutput = new KcpOutPutImp();
                    Ukcp newUkcp = new Ukcp(kcpOutput, kcpListener, iMessageExecutor, channelConfig, channelManager);
                    newUkcp.user(user);
                    newUkcp.setConv(waiter.convId);
                    
                    // Create a new DatagramPacket with clean payload for channel registration
                    DatagramPacket cleanMsg = new DatagramPacket(cleanPayload.retain(), msg.recipient(), originalSender);
                    channelManager.New(originalSender, newUkcp, cleanMsg);
                    cleanMsg.release();
                    
                    hashedWheelTimer.newTimeout(new ScheduleTask(iMessageExecutor, newUkcp, hashedWheelTimer),
                            newUkcp.getInterval(),
                            TimeUnit.MILLISECONDS);
                    ukcp = newUkcp;
                    newConnection = true;
                    logger.debug("Successfully created new KCP connection for client: {} with convId: {}", originalSender, waiter.convId);
                }
            } else {
                logger.debug("Using existing KCP connection for client: {}", originalSender);
            }
            
            // established tunnel
            logger.debug("Executing KCP event sender for client: {}, new connection: {}", originalSender, newConnection);
            
            // Check if executor is still active before attempting to use it
            if (!iMessageExecutor.isActive()) {
                logger.warn("Message executor is not active for client: {} - server may be shutting down. Dropping packet.", originalSender);
                // Don't retain the buffer since we won't process it
                if (ukcp != null) {
                    ukcp.close(false);
                }
                return;
            }
            
            try {
                iMessageExecutor.execute(new UkcpEventSender(newConnection, ukcp, cleanPayload.retain(), originalSender));
            } catch (java.util.concurrent.RejectedExecutionException e) {
                logger.error("Failed to execute KCP event sender for client: {} - executor has been shut down. " +
                           "This may indicate the server is shutting down or thread pool has been terminated.", originalSender, e);
                // Release the retained buffer since the task won't be executed
                cleanPayload.release();
                // Optionally close the connection
                if (ukcp != null) {
                    ukcp.close(false);
                }
            } catch (Exception e) {
                logger.error("Unexpected error executing KCP event sender for client: {}", originalSender, e);
                // Release the retained buffer since the task won't be executed
                cleanPayload.release();
            }
            
        } finally {
            // Clean up resources
            if (cleanPayload != null) {
                cleanPayload.release();
            }
            if (proxyResult != null) {
                proxyResult.release();
            }
        }
    }

    static class UkcpEventSender implements ITask {
        private final boolean newConnection;
        private final Ukcp ukcp;
        private final ByteBuf byteBuf;
        private final InetSocketAddress sender;
        UkcpEventSender(boolean newConnection,Ukcp ukcp,ByteBuf byteBuf,InetSocketAddress sender){
            this.newConnection=newConnection;
            this.ukcp=ukcp;
            this.byteBuf=byteBuf;
            this.sender=sender;
        }
        @Override
        public void execute() {
            // Cache user object to avoid repeated method calls
            final User user = ukcp.user();
            final InetSocketAddress originalClient = user.getOriginalClientAddress();
            final InetSocketAddress proxyAddress = user.getRemoteAddress();
            
            logger.debug("Executing UkcpEventSender for original client: {}, via proxy: {}, new connection: {}, data size: {} bytes", 
                        originalClient, proxyAddress, newConnection, byteBuf.readableBytes());
            
            try {
                if(newConnection) {
                    logger.info("Triggering onConnected callback for new client: {} (via proxy: {})", originalClient, proxyAddress);
                    try {
                        ukcp.getKcpListener().onConnected(ukcp);
                        logger.debug("onConnected callback completed successfully for client: {}", originalClient);
                    } catch (Throwable throwable) {
                        logger.error("Error in onConnected callback for client: {}", originalClient, throwable);
                        ukcp.getKcpListener().handleException(throwable, ukcp);
                    }
                }
                
                logger.debug("Reading data for client: {} (proxy responses will go to: {})", originalClient, proxyAddress);
                // Note: We do NOT call setRemoteAddress here as it should remain the proxy address for responses
                ukcp.read(byteBuf);
                logger.debug("Data read completed for client: {}", originalClient);
                
            } catch (Exception e) {
                logger.error("Error processing KCP data for client: {}", originalClient, e);
                try {
                    ukcp.getKcpListener().handleException(e, ukcp);
                } catch (Exception ex) {
                    logger.error("Error in exception handler for client: {}", originalClient, ex);
                }
                // Release the ByteBuf only if ukcp.read() failed and didn't add it to the queue
                if (byteBuf != null) {
                    byteBuf.release();
                    logger.debug("Released ByteBuf for client: {} due to processing error", originalClient);
                }
            }
            // Note: ByteBuf is NOT released in the normal flow here because ukcp.read() adds it to the readBuffer queue,
            // and ReadTask will handle the release when processing the queue. Releasing here would cause IllegalReferenceCountException.
        }
    }
    private int getSn(ByteBuf byteBuf,ChannelConfig channelConfig){
        int headerSize = 0;
        if(channelConfig.getFecAdapt()!=null){
            headerSize+= Fec.fecHeaderSizePlus2;
        }
        return byteBuf.getIntLE(byteBuf.readerIndex()+Kcp.IKCP_SN_OFFSET+headerSize);
    }

}
