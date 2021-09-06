package clink.core;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

/**
 * IO参数，用于执行实际的异步读写操作，读写操作状态将会以异步回调的形式通知。
 *
 * @author Ztiany
 * Email ztiany3@gmail.com
 * Date 2018/11/8 22:34
 */
public class IoArgs {

    private int limit = 256;
    private final byte[] byteBuffer = new byte[256];
    private final ByteBuffer buffer = ByteBuffer.wrap(byteBuffer);

    /**
     * 从 SocketChannel 读取数据，，保证将 IoArgs 读满。
     */
    public int readFrom(SocketChannel socketChannel) throws IOException {
        startWriting();
        int bytesProduced = 0;
        //TODO：这种死循环的方式应该是不合理的。
        while (buffer.hasRemaining()) {
            int readLength = socketChannel.read(buffer);
            if (readLength < 0) {//无法读取到更多的数据
                throw new EOFException();
            }
            bytesProduced += readLength;
        }
        finishWriting();
        return bytesProduced;
    }

    /**
     * 写数据到 SocketChannel，保证将 IoArgs 中现有数据全部写出去。
     */
    public int writeTo(SocketChannel socketChannel) throws IOException {
        int bytesProduced = 0;
        //TODO：这种死循环的方式应该是不合理的。
        while (buffer.hasRemaining()) {
            int writeLength = socketChannel.write(buffer);
            if (writeLength < 0) {//无法读取到更多的数据
                throw new EOFException();
            }
            bytesProduced += writeLength;
        }
        return bytesProduced;
    }

    public int capacity() {
        return buffer.capacity();
    }

    /**
     * 设置本次读取数据的大小
     */
    public void limit(int receiveSize) {
        limit = receiveSize;
    }

    /**
     * 把数据写入到 writableByteChannel 中
     */
    public int writeTo(WritableByteChannel writableByteChannel) throws IOException {
        int bytesProduced = 0;
        while (buffer.hasRemaining()) {
            int writeLength = writableByteChannel.write(buffer);
            if (writeLength < 0) {//无法读取到更多的数据
                throw new EOFException();
            }
            bytesProduced += writeLength;
        }
        return bytesProduced;
    }

    /**
     * 从 readableByteChannel 中读取数据
     */
    public int readFrom(ReadableByteChannel readableByteChannel) throws IOException {
        startWriting();
        int bytesProduced = 0;
        while (buffer.hasRemaining()) {
            int writeLength = readableByteChannel.read(buffer);
            if (writeLength < 0) {//无法读取到更多的数据
                throw new EOFException();
            }
            bytesProduced += writeLength;
        }
        finishWriting();
        return bytesProduced;
    }

    public void writeLength(int length) {
        startWriting();
        buffer.putInt(length);
        finishWriting();
    }

    public int readLength() {
        return buffer.getInt();
    }

    public void startWriting() {
        //清理，开始写入数据
        buffer.clear();
        // 定义容纳区间
        buffer.limit(limit);
    }

    public void finishWriting() {
        //切换到读取模式
        buffer.flip();
    }

    /**
     * IoArgs 提供者、处理者；数据的生产或消费者。定义为这种形式，用于异步处理IO。
     */
    public interface IoArgsEventProcessor {

        /**
         * 当可发送或者可接收数据时，提供一份可消费的 IoArgs。
         * <pre>
         *     <li>当用于发送数据时，IoArgs 内部存储的应该是待发送的数据</li>
         *     <li>当用于接收数据时，IoArgs 应该是待写状态</li>
         * </pre>
         */
        IoArgs provideIoArgs();

        /**
         * IoArgs 消费失败时回调
         *
         * @param ioArgs IoArgs
         * @param e      异常信息
         */
        void consumeFailed(IoArgs ioArgs, Exception e);

        /**
         * IoArgs 消费成功时回调
         *
         * @param args IoArgs
         */
        void onConsumeCompleted(IoArgs args);
    }

    @Override
    public String toString() {
        return "IoArgs{" +
                "limit=" + limit +
                ", buffer=" + buffer +
                '}';
    }

}
