package lab.dragon.power.handler.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lab.dragon.power.config.CollectorConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckFrameDecoder extends LengthFieldBasedFrameDecoder {
    private final Logger log = LoggerFactory.getLogger(CheckFrameDecoder.class);

    private ByteBuf previousFrame; // 用于保存上一次的数据帧

    public CheckFrameDecoder() {
        // 调用重载构造函数，使用默认值
        this(CollectorConstants.DATA_MAX_FRAME_LENGTH, CollectorConstants.DATA_LENGTH_OFFSET, CollectorConstants.DATA_LENGTH_FIELD_LENGTH, CollectorConstants.DATA_LENGTH_ADJUSTMENT, CollectorConstants.DATA_INITIAL_BYTES_TO_STRIP);
    }

    public CheckFrameDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment, int initialBytesToStrip) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf buffer) {
        try {
            if (buffer.readableBytes() < CollectorConstants.DATA_MIN_RECEIVED_LENGTH) {
                return null;
            }

            // 标记当前位置，以便稍后重置
            buffer.markReaderIndex();

            // 验证帧头和标识名称
            if (!validateFrameHeaderAndIdentifier(buffer)) {
                logAndHandleErrorFrame(buffer);
                findNextFrameStart(buffer);
                return null;
            }

            // 读取长度
            short lenLong = buffer.readUnsignedByte();
            if (lenLong > CollectorConstants.DATA_MAX_FRAME_LENGTH) {
                log.error(ByteBufUtil.hexDump(buffer));
                logAndHandleErrorFrame(buffer);
                findNextFrameStart(buffer);
                return null;
            }

            // 数据帧校验完毕，重置回读取指针
            buffer.resetReaderIndex();

            if (buffer.readableBytes() < (int) lenLong) {
                return null;
            }

            // 复制有效帧并跳过已读取的字节
            ByteBuf copy = buffer.copy(0, lenLong);
            buffer.skipBytes(lenLong);
            buffer.discardReadBytes();

            // 保存这次的数据帧
            if (previousFrame != null) {
                previousFrame.release();
            }
            previousFrame = copy.retainedDuplicate();

            return copy;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("CheckFrameDecoder catch a exception. message: {}, ctx: {}", cause.getMessage(), ctx, cause);
    }

    private void logAndHandleErrorFrame(ByteBuf buf) {
        buf.resetReaderIndex(); // 重置到错误帧的开始位置
        String errorFrameHex = ByteBufUtil.hexDump(buf);
        String lastFrameHex = previousFrame != null ? ByteBufUtil.hexDump(previousFrame) : "无";
        log.error("检测到错误的数据帧：{}", errorFrameHex);
        log.error("上一次的数据帧：{}", lastFrameHex);
    }

    private boolean validateFrameHeaderAndIdentifier(ByteBuf buf) {
        return buf.readByte() == CollectorConstants.DATA_FRAME_UP_HEADER_BYTE;
    }

    private void findNextFrameStart(ByteBuf buf) {
        while (buf.readableBytes() > 4) {
            buf.markReaderIndex(); // 标记可能的新帧头位置
            if (buf.readByte() == CollectorConstants.DATA_FRAME_UP_HEADER_BYTE) {
                log.info("找到下一个帧头；数据帧: {}", ByteBufUtil.hexDump(buf));
                return;
            }
        }
    }
}


