package clink.impl.async;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import clink.box.StringReceivePacket;
import clink.core.IoArgs;
import clink.core.ReceiveDispatcher;
import clink.core.ReceivePacket;
import clink.core.Receiver;
import clink.utils.CloseUtils;

/**
 * @author Ztiany
 * Email ztiany3@gmail.com
 * Date 2018/11/18 17:00
 */
public class AsyncReceiveDispatcher implements ReceiveDispatcher {

    private final AtomicBoolean mIsClosed = new AtomicBoolean(false);

    private final Receiver mReceiver;
    private final ReceivePacketCallback mReceivePacketCallback;

    private final IoArgs mIoArgs = new IoArgs();

    /**
     * 临时存储当前正在接受的包
     */
    private ReceivePacket mPacketTemp;

    /**
     * 临时存储当前正在接受的包的数据
     */
    private byte[] mByteBuffer;

    /**
     * 临时存储当前正在接受的包的总长度
     */
    private int mTotal;

    /**
     * 临时存储当前正在接受的包的已读长度
     */
    private int mPosition;

    public AsyncReceiveDispatcher(Receiver receiver, ReceivePacketCallback receivePacketCallback) {
        mReceiver = receiver;
        //设置回调，然后在调用 receiveAsync 方法并传入 IoArgs 后，mReceiver 就会将 IoArgs 读满，然后通过 ioArgsEventListener 回调回来。
        mReceiver.setReceiveCallback(ioArgsEventListener);
        mReceivePacketCallback = receivePacketCallback;
    }

    @Override
    public void stop() {
        //no op
    }

    @Override
    public void start() {
        registerReceive();
    }

    private void registerReceive() {
        try {
            /*注册接收监听*/
            mReceiver.receiveAsync(mIoArgs);
        } catch (IOException e) {
            e.printStackTrace();
            closeAndNotify();
        }
    }

    /**
     * 解析包
     */
    private void assemblePacket(IoArgs args) {
        if (mPacketTemp == null) {//说明是一条新的消息
            int length = args.readLength();
            System.out.println("new packet length = " + length);
            //根据包长度需求，创建一个StringReceivePacket
            mPacketTemp = new StringReceivePacket(length);
            //初始化容器和位置标识
            mByteBuffer = new byte[length];
            mTotal = length;
            mPosition = 0;
        }

        int readCount = args.writeTo(mByteBuffer, 0);//写入到我们的容器中

        //读取到了数据
        if (readCount > 0) {
            //读取到的数据保持到包中
            mPacketTemp.save(mByteBuffer, readCount);
            mPosition += readCount;
            // 检查是否已完成一份Packet接收
            if (mPosition >= mTotal) {
                completePacket();
                mPacketTemp = null;
            }
        }
    }

    private void completePacket() {
        ReceivePacket receivePacket = mPacketTemp;
        mReceivePacketCallback.onReceivePacketCompleted(receivePacket);
        CloseUtils.close(receivePacket);
    }

    private final IoArgs.IoArgsEventListener ioArgsEventListener = new IoArgs.IoArgsEventListener() {

        /** 当开始读取时，Receiver 会回调 onStarted 方法，我们在 onStarted 方法中，调用  args.limit(receiveSize) 来设置此次要读取多长的数据。*/
        @Override
        public void onStarted(IoArgs args) {
            int receiveSize;
            if (mPacketTemp == null) {//说明是一个新的消息的读取，先获取长度
                receiveSize = 4;//按照约定，用4个字节表示长度
            } else {//说明还是读取之前没有读完的消息，则接收的长度应该是，总长度-已读取的长度，同时还要考虑 args 的容量
                receiveSize = Math.min(mTotal - mPosition, args.capacity());
            }
            //设置本次接收数据的长度
            args.limit(receiveSize);
        }

        /**当 Receiver 读取到 args.limit(receiveSize) 方法设置的长度后，就会回调 onCompleted 方法。*/
        @Override
        public void onCompleted(IoArgs args) {
            //完成了单次（非阻塞）接收，则解析包
            assemblePacket(args);
            //然后继续接收下一个数据包
            registerReceive();
        }

    };//ioArgsEventListener end

    private void closeAndNotify() {
        CloseUtils.close(this);
    }

    @Override
    public void close() {
        if (mIsClosed.compareAndSet(false, true)) {
            ReceivePacket receivePacket = mPacketTemp;
            if (receivePacket != null) {
                mPacketTemp = null;
                CloseUtils.close(receivePacket);
            }
        }
    }

}
