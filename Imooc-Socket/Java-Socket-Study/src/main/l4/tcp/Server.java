package tcp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * TCP 服务端
 */
public class Server {

    private static final int PORT = 20000;

    public static void main(String[] args) throws IOException {
        ServerSocket server = createServerSocket();

        initServerSocket(server);

        // 绑定到本地端口上
        // backlog: The backlog argument is the requested maximum number of pending connections on the socket.
        server.bind(new InetSocketAddress(Inet4Address.getLocalHost(), PORT), 50);

        System.out.println("服务器准备就绪～");
        System.out.println("服务器信息：" + server.getInetAddress() + " P:" + server.getLocalPort());

        // 等待客户端连接
        for (; ; ) {
            // 得到客户端
            Socket client = server.accept();
            // 客户端构建异步线程
            ClientHandler clientHandler = new ClientHandler(client);
            // 启动线程
            clientHandler.start();
        }

    }

    private static ServerSocket createServerSocket() throws IOException {
        // 方式1：创建基础的ServerSocket
        ServerSocket serverSocket = new ServerSocket();

        //创建ServerSocket并指定端口号
        //serverSocket = new ServerSocket(PORT);

        // 方式2：等效于上面的方案，并且设置当前可允许等待链接的队列为50个。
        // 这里的 50 指示（对连接的请求）的最大队列长度被设置为 50 。如果队列满时收到连接指示，则拒绝该连接。
        // 允许等待连接的队列即在创建 ServerSocket 并调用 bind 后，在调用 accept 取获取连接的客户端之前，如果已经有了 50 个客户端在等待，此时第 51 个客户端尝试连接时将会被触发异常。
        // serverSocket = new ServerSocket(PORT, 50);

        // 方式3：与上面等同
        // serverSocket = new ServerSocket(PORT, 50, Inet4Address.getLocalHost());

        return serverSocket;
    }

    private static void initServerSocket(ServerSocket serverSocket) throws IOException {
        // 是否复用未完全关闭的地址端口
        serverSocket.setReuseAddress(true);

        // 等效于拿到客户端后调用 Socket#setReceiveBufferSize
        serverSocket.setReceiveBufferSize(64 * 1024 * 1024);

        // 设置 serverSocket#accept 超时时间，如果 accept 超时则会触发异常。
        // serverSocket.setSoTimeout(2000);

        // 设置性能参数：短链接，延迟，带宽的相对重要性（服务端需要在ServerSocket上设置）
        serverSocket.setPerformancePreferences(1, 1, 1);
    }

    /**
     * 客户端消息处理
     */
    private static class ClientHandler extends Thread {
        private Socket socket;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            super.run();
            System.out.println("新客户端连接：" + socket.getInetAddress() + " P:" + socket.getPort());

            try {
                // 得到套接字流
                OutputStream outputStream = socket.getOutputStream();
                InputStream inputStream = socket.getInputStream();

                byte[] buffer = new byte[256];
                int readCount = inputStream.read(buffer);
                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, readCount);

                // byte
                byte be = byteBuffer.get();

                // char
                char c = byteBuffer.getChar();

                // int
                int i = byteBuffer.getInt();

                // bool
                boolean b = byteBuffer.get() == 1;

                // Long
                long l = byteBuffer.getLong();

                // float
                float f = byteBuffer.getFloat();

                // double
                double d = byteBuffer.getDouble();

                // String
                int pos = byteBuffer.position();
                String str = new String(buffer, pos, readCount - pos - 1);/*读取的总数-已读数，- 1 是因为 pos 从 0 开始*/

                System.out.println("收到数量：" + readCount + " 数据："
                        + be + "\n"
                        + c + "\n"
                        + i + "\n"
                        + b + "\n"
                        + l + "\n"
                        + f + "\n"
                        + d + "\n"
                        + str + "\n");

                outputStream.write(buffer, 0, readCount);
                outputStream.close();
                inputStream.close();

            } catch (Exception e) {
                System.out.println("连接异常断开");
            } finally {
                // 连接关闭
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            System.out.println("客户端已退出：" + socket.getInetAddress() + " P:" + socket.getPort());

        }
    }
}
