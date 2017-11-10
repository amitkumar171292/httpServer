/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package HttpNetty;

/**
 *
 * @author 610968
 */
import static io.netty.buffer.Unpooled.copiedBuffer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;

public class httpNetty
{
    private ChannelFuture channel;
    private final EventLoopGroup masterGroup;
    private final EventLoopGroup slaveGroup;
    
    public httpNetty()
    {
        masterGroup = new NioEventLoopGroup();
        slaveGroup = new NioEventLoopGroup();        
    }

    public void start()
    {
        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run() { shutdown(); }
        });
        
        try
        {
            final ServerBootstrap bootstrap =
                new ServerBootstrap()
                    .group(masterGroup, slaveGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>(){
                        @Override
                        public void initChannel(final SocketChannel ch) throws Exception
                        {
                            ch.pipeline().addLast("codec", new HttpServerCodec());
                            ch.pipeline().addLast("aggregator", new HttpObjectAggregator(512*1024));
                            ch.pipeline().addLast("request", new ChannelInboundHandlerAdapter()
                            {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg)
                                        throws Exception
                                {
                                    if (msg instanceof FullHttpRequest)
                                    {
                                        final FullHttpRequest request = (FullHttpRequest) msg;
                                        final String responseMessage = "Hello from Netty!";                                            
                                        FullHttpResponse response = new DefaultFullHttpResponse(
                                            HttpVersion.HTTP_1_1,
                                            HttpResponseStatus.OK,
                                            copiedBuffer(responseMessage.getBytes())
                                        );
    
                                        if (HttpHeaders.isKeepAlive(request))
                                        {
                                            response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
                                        }
                                        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/plain");
                                        response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, responseMessage.length());
                                        
                                        ctx.writeAndFlush(response);
                                    }
                                    else
                                    {
                                        super.channelRead(ctx, msg);
                                    }
                                }
    
                                @Override
                                public void channelReadComplete(ChannelHandlerContext ctx) throws Exception
                                {
                                    ctx.flush();
                                }
    
                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                                        throws Exception
                                {
                                    ctx.writeAndFlush(new DefaultFullHttpResponse(
                                        HttpVersion.HTTP_1_1,
                                        HttpResponseStatus.INTERNAL_SERVER_ERROR,
                                        copiedBuffer(cause.getMessage().getBytes())
                                    ));
                                }                                    
                            });
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);
            channel = bootstrap.bind(8080).sync();
            //channels.add(bootstrap.bind(8080).sync());
        }
        catch (final InterruptedException e) { }
    }
    
    public void shutdown()
    {
        slaveGroup.shutdownGracefully();
        masterGroup.shutdownGracefully();

        try
        {
            channel.channel().closeFuture().sync();
        }
        catch (InterruptedException e) { }
    }

    public static void main(String[] args)
    {
        new httpNetty().start();
    }
}
