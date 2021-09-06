package foo.handler;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SocketChannel;

import clink.box.StringReceivePacket;
import clink.core.Connector;
import clink.core.IoContext;
import clink.core.Packet;
import clink.core.ReceivePacket;
import clink.utils.CloseUtils;
import foo.Foo;


/**
 * 用于处理客户端连接，读取客户端信息，向客户端发送消息。
 *
 * @author Ztiany
 * Email ztiany3@gmail.com
 * Date 2018/11/1 23:15
 */
public class ConnectorHandler extends Connector {

    static class PrintConnectorStringPacketChain extends ConnectorStringPacketChain {

        @Override
        protected boolean consume(ConnectorHandler handler, StringReceivePacket packet) {

            return false;
        }

    }

    //用于处理关闭
    private final ConnectorCloseChain mCloseChain = new DefaultPrintConnectorCloseChain();

    //用于处理字符串消息
    private final ConnectorStringPacketChain mStringPacketChain = new PrintConnectorStringPacketChain();

    private final String mClientInfo;
    private final File mCachePath;

    public ConnectorHandler(SocketChannel client, File cachePath) throws IOException {
        //初始化客户端信息
        mClientInfo = client.getLocalAddress().toString();
        mCachePath = cachePath;
        setup(client);
    }

    @Override
    public void onChannelClosed(SocketChannel channel) {
        super.onChannelClosed(channel);
        mCloseChain.handle(this, this);
    }

    @Override
    protected File createNewReceiveFile() {
        return Foo.createRandomTemp(mCachePath);
    }

    public String getClientInfo() {
        return mClientInfo;
    }

    public void exit() {
        CloseUtils.close(this);
    }

    @Override
    protected void onReceiveNewPacket(ReceivePacket packet) {
        System.out.println("ConnectorHandler.onReceiveNewPacket running at " + Thread.currentThread());
        super.onReceiveNewPacket(packet);
        switch (packet.getType()) {
            case Packet.TYPE_MEMORY_STRING: {
                deliveryStringPacket((StringReceivePacket) packet);
                break;
            }
            default: {
                System.out.println(key.toString() + " : [New Packet]-Type : " + packet.getType() + ", Length:" + packet.getLength());
            }
        }
    }

    private void deliveryStringPacket(StringReceivePacket packet) {
        IoContext.get()
                .scheduler()
                .delivery(() -> mStringPacketChain.handle(this, packet));
    }

    /**
     * 获取当前链接的消息处理责任链【链头】
     *
     * @return ConnectorStringPacketChain
     */
    public ConnectorStringPacketChain getStringPacketChain() {
        return mStringPacketChain;
    }

    /**
     * 获取当前链接的关闭链接处理责任链【链头】
     *
     * @return ConnectorCloseChain
     */
    public ConnectorCloseChain getCloseChain() {
        return mCloseChain;
    }

}
