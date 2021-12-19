package org.github.admin.scheduler;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;
import org.github.admin.model.entity.Point;
import org.github.admin.service.TaskGroupService;
import org.github.admin.util.SpringApplicationContextUtil;
import org.github.common.*;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author zengchzh
 * @date 2021/12/11
 */
@Slf4j
public class TaskInvocation implements Invocation {

    private final EventLoopGroup workGroup = new NioEventLoopGroup(1);

    Bootstrap bootstrap = new Bootstrap();

    private final Promise<Channel> cp = ImmediateEventExecutor.INSTANCE.newPromise();;

    private Channel channel;

    private final Point point;

    private final TaskScheduler taskScheduler;

    public TaskInvocation(Point point, TaskScheduler taskScheduler) {
        this.point = point;
        this.taskScheduler = taskScheduler;
        config();
    }

    private void config() {
        bootstrap.group(workGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline cp = ch.pipeline();
                        cp.addLast(new IdleStateHandler(0, 0, 30, TimeUnit.SECONDS));
                        cp.addLast(new MsgEncoder());
                        cp.addLast(new LengthFieldBasedFrameDecoder(8 * 1024 * 1024,
                                1,
                                4,
                                -5,
                                0));
                        cp.addLast(new MsgDecoder());
                        cp.addLast(new InvocationHandler());
                    }
                });
    }

    @Override
    public synchronized void connnect() {
        String threadName = Thread.currentThread().getName();
        log.info(threadName + " connect " + point + " , available state is " + isAvailable());
        if (isAvailable()) {
            return;
        }
        try {
            bootstrap.connect(point.getIp(), point.getPort()).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        cp.trySuccess(future.channel());
                    } else {
                        log.error(threadName + " connect " + point + " fail");
                        cp.tryFailure(future.cause());
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void invoke(TaskReq req) {
        getChannel().writeAndFlush(TaskMsg.builder().msgType(MsgType.REQ).data(req).build());
    }

    private Channel getChannel() {
        if (Objects.isNull(channel)) {
            try {
                channel = cp.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
        return channel;
    }

    @Override
    public void disconnect() {
        if (Objects.nonNull(channel)) {
            channel.close();
        }
        workGroup.shutdownGracefully();
    }

    @Override
    public boolean isAvailable() {
        return cp.isSuccess();
    }

    public void preRead() {
        getChannel().writeAndFlush(TaskMsg.builder().msgType(MsgType.PRE_REQ).build());
    }

    class InvocationHandler extends SimpleChannelInboundHandler<TaskMsg> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, TaskMsg msg) throws Exception {
            if (MsgType.PRE_RES == msg.getMsgType()) {
                TaskAppInfo info = (TaskAppInfo) msg.getData();
                TaskGroupService taskGroupService = SpringApplicationContextUtil.getBean(TaskGroupService.class);
                taskGroupService.addGroup(info);
            } else {
//                log.info(msg.toString());
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                ctx.channel().writeAndFlush(TaskMsg.builder().msgType(MsgType.BEAT).build());
            } else {
                super.userEventTriggered(ctx, evt);
            }
        }
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("InvocationHandler exceptionCaught", cause);
            Invocation invocation = taskScheduler.remove(TaskInvocation.this.point);
            invocation.disconnect();
            ctx.close();
        }
    }
}