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
import org.github.common.types.Point;
import org.github.admin.service.TaskGroupService;
import org.github.admin.util.SpringApplicationContextUtil;
import org.github.common.coder.MsgDecoder;
import org.github.common.coder.MsgEncoder;
import org.github.common.protocol.MsgType;
import org.github.common.protocol.TaskMsg;
import org.github.common.protocol.TaskReq;
import org.github.common.req.TaskAppInfo;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * @author zengchzh
 * @date 2021/12/11
 */
@Slf4j
public class TaskInvocation implements Invocation {

    private final EventLoopGroup workGroup = new NioEventLoopGroup(1);

    Bootstrap bootstrap = new Bootstrap();

    private Promise<Channel> cp;

    private Channel channel;

    private final Point point;

    private static final AtomicIntegerFieldUpdater<TaskInvocation> UPDATER
            = AtomicIntegerFieldUpdater.newUpdater(TaskInvocation.class, "state");

    private volatile int state;

    private static final int INIT = 0;

    private static final int DO_CONNECT = 1;

    private static final int RUNNING = 2;

    private static final int SHUTDOWN = 3;

    public TaskInvocation(Point point) {
        this.point = point;
        this.state = INIT;
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
    public void connnect() {
        if (isAvailable()) {
            return;
        }
        if (UPDATER.compareAndSet(this, INIT, DO_CONNECT)) {
            doConnect();
        }
    }

    private void doConnect() {
        try {
            cp = ImmediateEventExecutor.INSTANCE.newPromise();
            bootstrap.connect(point.getIp(), point.getPort()).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    UPDATER.compareAndSet(TaskInvocation.this, DO_CONNECT, RUNNING);
                    cp.trySuccess(future.channel());
                    invokeTaskAppInfo();
                } else {
                    UPDATER.compareAndSet(TaskInvocation.this, DO_CONNECT, INIT);
                    log.error(Thread.currentThread().getName() + " connect " + point + " fail");
                    cp.tryFailure(future.cause());
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void invokeTaskAppInfo() {
        getChannel().writeAndFlush(TaskMsg.builder().msgType(MsgType.PRE_REQ).build());
    }

    @Override
    public void invoke(TaskReq req) {
        getChannel().writeAndFlush(TaskMsg.builder().msgType(MsgType.REQ).data(req).build());
    }

    private Channel getChannel() {
        if (Objects.isNull(channel) && state != SHUTDOWN) {
            try {
                channel = cp.get(5, TimeUnit.SECONDS);
                return channel;
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException("get channel fail", e);
            }
        } else {
            if (state == SHUTDOWN) {
                throw new IllegalStateException("connection closed");
            }
            return channel;
        }
    }

    @Override
    public void disconnect() {
        UPDATER.set(this, SHUTDOWN);
        if (Objects.nonNull(channel)) {
            channel.close();
        }
        workGroup.shutdownGracefully();
    }

    @Override
    public boolean isAvailable() {
        return Objects.nonNull(cp) && cp.isSuccess() && state == RUNNING;
    }

    @Override
    public Point getPoint() {
        return this.point;
    }

    class InvocationHandler extends SimpleChannelInboundHandler<TaskMsg> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, TaskMsg msg) throws Exception {
            try {
                if (MsgType.PRE_RES == msg.getMsgType()) {
                    TaskAppInfo info = (TaskAppInfo) msg.getData();
                    TaskGroupService taskGroupService = SpringApplicationContextUtil.getBean(TaskGroupService.class);
                    taskGroupService.addGroup(info);
                } else {
//                log.info(msg.toString());
                }
            } catch (Exception e) {
                e.printStackTrace();
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
            TaskInvocation.this.disconnect();
            ctx.close();
        }
    }
}
