package clink.impl.async;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;

import clink.core.Frame;
import clink.core.IoArgs;
import clink.core.SendPacket;
import clink.core.ds.BytePriorityNode;
import clink.frame.AbsSendPacketFrame;
import clink.frame.CancelSendFrame;
import clink.frame.SendEntityFrame;
import clink.frame.SendHeaderFrame;

/**
 * 负责帧级别的读取与发送。
 *
 * @author Ztiany
 * Email ztiany3@gmail.com
 * Date 2018/11/27 22:53
 */
public class AsyncPacketReader implements Closeable {

    private final PacketProvider mPacketProvider;

    private final IoArgs mIoArgs = new IoArgs();

    private volatile BytePriorityNode<Frame> mNode;//帧队列

    private volatile int mNodeSize = 0;//节点数量

    //包唯一标识从 1 开始，最大为 255，理论上最大并发支持  255 个 packet 同时发送
    private short mLastIdentifier = 0;//记录最后一次唯一标识

    AsyncPacketReader(PacketProvider packetProvider) {
        mPacketProvider = Objects.requireNonNull(packetProvider);
    }

    /**
     * 取消Packet对应的帧发送，如果当前Packet已发送部分数据（就算只是头数据）
     * 也应该在当前帧队列中发送一份取消发送的标志{@link CancelSendFrame}
     *
     * @param packet 待取消的packet
     */
    public synchronized void cancel(SendPacket packet) {
        if (mNodeSize == 0) {
            return;
        }
        //遍历找到那个需要取消的packet对应的frame
        for (BytePriorityNode<Frame> x = mNode, before = null; x != null; before = x, x = x.next) {
            Frame frame = x.item;
            if (frame instanceof AbsSendPacketFrame) {//是发包的frame才需要被取消

                AbsSendPacketFrame sendPacketFrame = (AbsSendPacketFrame) frame;

                if (sendPacketFrame.getPacket() == packet) {//找到对应的帧（队列中对于每一个包，同时最多只会有一个帧，因为帧是顺序发送的。）

                    boolean removable = sendPacketFrame.abort();
                    //removable表示是否完美中止
                    if (removable) {
                        //是完美中止就移除吧
                        removeFrame(x, before);
                        if (sendPacketFrame instanceof SendHeaderFrame) {
                            // 头帧，并且未被发送任何数据，直接取消后不需要添加取消发送帧
                            break;
                        }
                    }//removable end

                    //没有完美取消，或者完美取消的不是头帧，则需要发送一个取消帧告知接收方该包被取消了
                    CancelSendFrame cancelSendFrame = new CancelSendFrame(sendPacketFrame.getBodyIdentifier());
                    appendNewNode(cancelSendFrame);
                    // 取消则认为是意外终止，返回失败
                    mPacketProvider.completedPacket(packet, false);
                    break;

                }//endPacketFrame.getPacket() == packet end
            }
        }
    }

    private synchronized void removeFrame(BytePriorityNode<Frame> remove, BytePriorityNode<Frame> before) {
        if (before == null) {
            mNode = remove.next;
        } else {
            before.next = remove.next;
        }
        mNodeSize--;
        //如果队列空了，看看是否还有需要发送到包。
        if (mNode == null) {
            requestTakePacket();
        }
    }

    /**
     * 请求从 {@link #mPacketProvider}队列中拿一份Packet进行发送
     *
     * @return 如果当前 Reader 中有可以用于网络发送的数据，则返回 true。【返回 true 后，调用者将请求 IO 调度。】
     */
    boolean requestTakePacket() {
        //如果 mNodeSize>= 1，直接返回true，表示还有要发送的数据。
        //TODO：mNodeSize 可以再设置得大一点，用于支持多个线程并发发送。这里的多个线程是指同时处理多个 Package。而最终的 I/O 处理是 IoSelectorProvider 中的线程池执行的。
        synchronized (this) {
            if (mNodeSize >= 4) {
                return true;
            }
        }

        SendPacket sendPacket = mPacketProvider.takePacket();
        if (sendPacket != null) {
            short identifier = generateIdentifier();
            //根据新的包，构建一个头帧添加到节点中
            SendHeaderFrame sendHeaderFrame = new SendHeaderFrame(identifier, sendPacket);
            appendNewNode(sendHeaderFrame);
        }

        synchronized (this) {
            return mNodeSize > 0;
        }
    }

    /**
     * 添加一个新的帧都队列中
     */
    private synchronized void appendNewNode(Frame frame) {
        BytePriorityNode<Frame> newNode = new BytePriorityNode<>(frame);
        if (mNode != null) {
            mNode.appendWithPriority(newNode);
        } else {
            mNode = newNode;
        }
        mNodeSize++;
    }

    /**
     * 关闭当前 Reader，关闭时应关闭所有的 Frame 对应的 Packet
     */
    @Override
    public synchronized void close() {
        BytePriorityNode<Frame> node = mNode;
        while (node != null) {
            Frame frame = mNode.item;
            if (frame instanceof AbsSendPacketFrame) {
                ((AbsSendPacketFrame) frame).abort();
                mPacketProvider.completedPacket(((AbsSendPacketFrame) frame).getPacket(), false);
            }
            node = node.next;
        }
        mNode = null;
        mNodeSize = 0;
    }

    /**
     * 构建一份Packet惟一标志
     *
     * @return 标志为：1～255
     */
    private short generateIdentifier() {
        short identifier = ++mLastIdentifier;
        if (identifier == 255) {
            mLastIdentifier = 0;
        }
        return identifier;
    }

    /**
     * 可以进行 IO 操作了，于是提供可以发送的数据，即填充数据到 IoArgs 中并返回。
     *
     * @return 如果当前有可用于发送的帧，则填充数据并返回，如果填充失败可返回null
     */
    IoArgs fillData() {
        //没有数据了，则返回null
        Frame currentFrame = getCurrentFrame();
        if (currentFrame == null) {
            return null;
        }

        try {
            //返回 true 表示该帧的数据消费完了，handle 方法是同步的。
            if (currentFrame.handle(mIoArgs)) {
                //因为 handle 方法是同步的，不可能有两个线程同时进入到该条件块内。
                //TODO：感觉这里无法保证线程安全，以为 handle 即使是同步的，但是有可能很大线程在 handle 方法上排队，然后某个线程在 handle 方法是返回 true，后续所有线程都会返回 true。

                Frame nextFrame = currentFrame.nextFrame(); //nextFrame 方法是同步的

                if (nextFrame != null) {
                    appendNewNode(nextFrame);
                } else if (currentFrame instanceof SendEntityFrame) {//是实体帧，且它的 nextFrame 为 null，则说明其对应的包发送完了。
                    mPacketProvider.completedPacket(((SendEntityFrame) currentFrame).getPacket(), true);
                }

                //既然当前帧发完了，就弹出来
                popCurrentFrame();
            }

            return mIoArgs;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private synchronized void popCurrentFrame() {
        //换到下一帧
        mNode = mNode.next;
        mNodeSize--;
        //如果队列空了，看看是否还有需要发送到包。
        if (mNode == null) {
            requestTakePacket();
        }
    }

    private synchronized Frame getCurrentFrame() {
        if (mNode != null) {
            return mNode.item;
        }
        return null;
    }

    /**
     * Packet提供者
     */
    interface PacketProvider {
        /**
         * 拿Packet操作
         *
         * @return 如果队列有可以发送的Packet则返回不为null
         */
        SendPacket takePacket();

        /**
         * 结束一份Packet
         *
         * @param sendPacket 发送包
         * @param isSucceed  是否成功发送完成
         */
        void completedPacket(SendPacket sendPacket, boolean isSucceed);
    }

}
