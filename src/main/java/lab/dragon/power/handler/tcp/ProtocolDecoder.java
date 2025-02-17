package lab.dragon.power.handler.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lab.dragon.common.util.ByteUtils;
import lab.dragon.power.DataCenter;
import lab.dragon.power.api.FirmwareUpgradeWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 协议解析策略 编码和解码
 *
 * @author 王猛
 */
public class ProtocolDecoder extends ByteToMessageDecoder {
    private final Logger log = LoggerFactory.getLogger(ProtocolDecoder.class);


    /**
     * 固定阵的上行 data 解析协议
     * 协议内容为：tag(2Bytes) + data bytes
     *
     * @param channelHandlerContext 连接频道的上下文信息
     * @param in                    data bytes
     * @param outList               解析出来的数据内容
     */
    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf in, List<Object> outList) throws Exception {
        try {
            byte[] buffer = new byte[in.readableBytes()];
            in.readBytes(buffer);

            String message = String.format("接收到下位机数据: %s", ByteUtils.toHexPrettyString(buffer));
            log.info(message);
            DataCenter.SEND_MESSAGE_QUEUE.add(message);

            if (!checkSum(buffer)) {
                log.error("校验和错误，跳过");
                DeviceUpgradeHandler.isContinued.set(false);
                return ;
            }

            if (buffer[0] == (byte) 0xA5) {
                decodeMsgA5(buffer);
            }

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private void decodeMsgA5(byte[] buffer) {
        byte length = buffer[1];
        byte code = buffer[3];
        /*
        帧头  0  ｜长度 1  ｜校验和 2｜返回码3 | 数据 4 |帧尾5
        --------------------------------------------------------------
        0xA5    ｜0x05   ｜0x00   ｜0x00   ｜       | 0x55
         */
        if (code == 0) {
            // 接收到A5回复，可以继续发包
            DeviceUpgradeHandler.isContinued.set(true);
            DeviceUpgradeHandler.current.set(System.currentTimeMillis());
        } else if (code == 1) {
            DeviceUpgradeHandler.isContinued.set(false);
            log.error("CRC错误");
        } else if (code == 2) {
            DeviceUpgradeHandler.isContinued.set(false);
            log.error("校验错误");
        }
        if (length < (byte) 0x09) {
            return;
        }
        byte[] tmpBuffer;
        if (length == (byte) 0x1A) {
            tmpBuffer = new byte[buffer.length - 7]; //  + 2
            System.arraycopy(buffer, 6, tmpBuffer, 0, tmpBuffer.length);
            String version = new String(tmpBuffer, StandardCharsets.US_ASCII);
            DataCenter.SEND_MESSAGE_QUEUE.add("VERSION;;" + version);
        }
    }

    private boolean checkSum(byte[] buffer) {
        if (buffer.length < 2) {
            return false;
        }
        byte[] bytes = new byte[buffer.length];
        System.arraycopy(buffer, 0, bytes, 0, bytes.length);
        bytes[2] = 0;
        long summed = ByteUtils.sum(bytes);
        boolean b = buffer[2] == (byte) ((byte) 0xFF & summed);
        if (!b)
            DataCenter.SEND_MESSAGE_QUEUE.add(String.format("校验和应为：%s，实际是：%s", ByteUtils.toHexPrettyString(new byte[]{(byte) ((byte) 0xFF & summed)}), ByteUtils.toHexPrettyString(new byte[]{buffer[2]})));
        return b;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String msg = String.format("检测到连接；ip: %s, port: %d",
                ((InetSocketAddress)ctx.channel().remoteAddress()).getAddress(),
                ((InetSocketAddress)ctx.channel().remoteAddress()).getPort());
        log.info(msg);

        DeviceUpgradeHandler.SERVER_CHANNEL = ctx.channel();
        DataCenter.SEND_MESSAGE_QUEUE.add(msg);

        ctx.fireChannelActive();
    }

    /**
     * 断开后移除连接状态
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        String msg = String.format("检测到连接断开；ip: %s, port: %d",
                ((InetSocketAddress)ctx.channel().remoteAddress()).getAddress(),
                ((InetSocketAddress)ctx.channel().remoteAddress()).getPort());
        log.info(msg);

        DeviceUpgradeHandler.SERVER_CHANNEL = ctx.channel();
        DataCenter.SEND_MESSAGE_QUEUE.add(msg);
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("protocol codec catch a exception. message: {}, ctx: {}", cause.getMessage(), ctx, cause);
    }
}
