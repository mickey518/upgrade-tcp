package lab.dragon.power.config;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lab.dragon.power.handler.tcp.CheckFrameDecoder;
import lab.dragon.power.handler.tcp.ByteEncoder;
import lab.dragon.power.handler.tcp.ProtocolDecoder;

/**
 *
 */
public class CollectAgreement {

    private static final CollectAgreement INSTANCE = new CollectAgreement();

    private NioEventLoopGroup eventLoopGroup;

    private ServerBootstrap serverBootstrap;

    private CollectAgreement() {
        init();
    }

    public static CollectAgreement getInstance() {
        return INSTANCE;
    }

    public void init() {
        eventLoopGroup = new NioEventLoopGroup();

        initTcpServerBootstrap();
    }

    private void initTcpServerBootstrap() {
        serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(eventLoopGroup);
        serverBootstrap.channel(NioServerSocketChannel.class);

        serverBootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000 * 5);
        serverBootstrap.option(ChannelOption.SO_RCVBUF, 1024);

        serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addFirst(new CheckFrameDecoder(), new ProtocolDecoder());
                pipeline.addLast(new ByteEncoder());
            }
        });
    }

    public ServerBootstrap getServerBootstrap() {
        return serverBootstrap;
    }

}
