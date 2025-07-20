package kcp.highway.threadPool.netty;

import io.netty.channel.EventLoop;
import kcp.highway.threadPool.IMessageExecutor;
import kcp.highway.threadPool.ITask;

/**
 * Created by JinMiao
 * 2020/11/24.
 */
public class NettyMessageExecutor implements IMessageExecutor {

    private EventLoop eventLoop;


    public NettyMessageExecutor(EventLoop eventLoop) {
        this.eventLoop = eventLoop;
    }

    @Override
    public void stop() {

    }

    @Override
    public boolean isFull() {
        return false;
    }

    @Override
    public void execute(ITask iTask) {
        if (eventLoop.isShuttingDown() || eventLoop.isShutdown() || eventLoop.isTerminated()) {
            throw new java.util.concurrent.RejectedExecutionException(
                "EventLoop is shutting down or terminated. State: shutting down=" + 
                eventLoop.isShuttingDown() + ", shutdown=" + eventLoop.isShutdown() + 
                ", terminated=" + eventLoop.isTerminated());
        }
        
        //if(eventLoop.inEventLoop()){
        //    iTask.execute();
        //}else{
            this.eventLoop.execute(() -> iTask.execute());
        //}
    }
    
    /**
     * Check if this executor is still active and can accept tasks
     * @return true if the executor can accept tasks, false otherwise
     */
    @Override
    public boolean isActive() {
        return !eventLoop.isShuttingDown() && !eventLoop.isShutdown() && !eventLoop.isTerminated();
    }
}
