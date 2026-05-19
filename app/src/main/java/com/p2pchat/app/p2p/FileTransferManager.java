package com.p2pchat.app.p2p;

import android.content.Context;
import android.util.Log;
import com.google.gson.Gson;
import com.p2pchat.app.model.SignalMessage;
import com.p2pchat.app.util.PrefsUtil;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/**
 * 文件传输管理器（TCP 直连 + 断点续传）
 * 协议：先通过信令协商，然后建立 TCP 连接传输文件
 * 支持断点续传：接收方告知已接收字节数，发送方从该偏移量继续
 */
public class FileTransferManager {

    private static final String TAG = "FileTransferMgr";
    public  static final int FILE_PORT = 37893;
    private static final int CHUNK_SIZE = 64 * 1024; // 64KB

    private final Context context;
    private final Gson gson = new Gson();
    private final SignalingServer signalingServer;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    private FileTransferListener listener;

    public interface FileTransferListener {
        void onTransferProgress(String transferId, long transferred, long total);
        void onTransferComplete(String transferId, String localPath);
        void onTransferFailed(String transferId, String reason);
        void onIncomingFile(FileMeta meta);
    }

    /** 文件元数据 */
    public static class FileMeta {
        public String transferId;
        public String fileName;
        public long   fileSize;
        public String mimeType;
        public String fromPeerId;
        public String senderIp;
    }

    /** 断点确认 ACK */
    public static class FileAck {
        public String transferId;
        public long   receivedBytes;  // 已接收字节数（续传起点）
        public boolean accepted;
    }

    public FileTransferManager(Context ctx, SignalingServer server) {
        this.context = ctx;
        this.signalingServer = server;
    }

    public void setListener(FileTransferListener l) { this.listener = l; }

    public void startServer() {
        running = true;
        executor.execute(this::runServer);
    }

    public void stopServer() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        executor.shutdownNow();
    }

    private void runServer() {
        try {
            serverSocket = new ServerSocket(FILE_PORT);
            Log.i(TAG, "文件传输服务器启动，端口 " + FILE_PORT);
            while (running) {
                Socket client = serverSocket.accept();
                executor.execute(() -> handleIncoming(client));
            }
        } catch (Exception e) {
            if (running) Log.e(TAG, "文件服务器异常: " + e.getMessage());
        }
    }

    /** 接收端：处理传入的文件流 */
    private void handleIncoming(Socket socket) {
        try {
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // 读取元数据头（JSON 长度 + JSON 数据）
            int metaLen = in.readInt();
            byte[] metaBytes = new byte[metaLen];
            in.readFully(metaBytes);
            FileMeta meta = gson.fromJson(new String(metaBytes, "UTF-8"), FileMeta.class);
            meta.senderIp = socket.getInetAddress().getHostAddress();

            Log.i(TAG, "收到文件请求: " + meta.fileName + " (" + meta.fileSize + " bytes)");

            // 检查断点续传：本地是否已有临时文件
            File tmpFile = getTempFile(meta.transferId, meta.fileName);
            long existingBytes = tmpFile.exists() ? tmpFile.length() : 0;

            // 发送 ACK（告知断点位置）
            FileAck ack = new FileAck();
            ack.transferId = meta.transferId;
            ack.receivedBytes = existingBytes;
            ack.accepted = true;
            byte[] ackBytes = gson.toJson(ack).getBytes("UTF-8");
            out.writeInt(ackBytes.length);
            out.write(ackBytes);
            out.flush();

            // 通知 UI 有新文件
            if (listener != null) listener.onIncomingFile(meta);

            // 追加写入文件（断点续传）
            try (RandomAccessFile raf = new RandomAccessFile(tmpFile, "rw")) {
                raf.seek(existingBytes);
                byte[] buf = new byte[CHUNK_SIZE];
                long totalReceived = existingBytes;
                int read;
                while (totalReceived < meta.fileSize && (read = in.read(buf)) != -1) {
                    raf.write(buf, 0, read);
                    totalReceived += read;
                    final long progress = totalReceived;
                    if (listener != null)
                        listener.onTransferProgress(meta.transferId, progress, meta.fileSize);
                }
            }

            // 完成：移动到下载目录
            File finalFile = getFinalFile(meta.fileName);
            tmpFile.renameTo(finalFile);
            Log.i(TAG, "文件接收完成: " + finalFile.getAbsolutePath());
            if (listener != null) listener.onTransferComplete(meta.transferId, finalFile.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "文件接收异常: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    /** 发送端：发送文件（支持断点续传） */
    public void sendFile(FileMeta meta, String remoteIp, File file) {
        executor.execute(() -> {
            try (Socket socket = new Socket(remoteIp, FILE_PORT)) {
                socket.setSoTimeout(30000);
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                DataInputStream  in  = new DataInputStream(socket.getInputStream());

                // 发送元数据头
                byte[] metaBytes = gson.toJson(meta).getBytes("UTF-8");
                out.writeInt(metaBytes.length);
                out.write(metaBytes);
                out.flush();

                // 读取对方的 ACK（获取断点位置）
                int ackLen = in.readInt();
                byte[] ackBytes = new byte[ackLen];
                in.readFully(ackBytes);
                FileAck ack = gson.fromJson(new String(ackBytes, "UTF-8"), FileAck.class);

                if (!ack.accepted) {
                    Log.i(TAG, "对方拒绝接收文件");
                    if (listener != null) listener.onTransferFailed(meta.transferId, "对方拒绝");
                    return;
                }

                long startOffset = ack.receivedBytes;
                Log.i(TAG, "从偏移 " + startOffset + " 开始发送文件: " + file.getName());

                // 从断点开始发送
                try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                    raf.seek(startOffset);
                    byte[] buf = new byte[CHUNK_SIZE];
                    long sent = startOffset;
                    int read;
                    while ((read = raf.read(buf)) != -1) {
                        out.write(buf, 0, read);
                        sent += read;
                        final long progress = sent;
                        if (listener != null)
                            listener.onTransferProgress(meta.transferId, progress, meta.fileSize);
                    }
                    out.flush();
                }
                Log.i(TAG, "文件发送完成: " + file.getName());
                if (listener != null) listener.onTransferComplete(meta.transferId, file.getAbsolutePath());

            } catch (Exception e) {
                Log.e(TAG, "文件发送异常: " + e.getMessage());
                if (listener != null) listener.onTransferFailed(meta.transferId, e.getMessage());
            }
        });
    }

    private File getTempFile(String transferId, String fileName) {
        File dir = new File(context.getExternalFilesDir(null), "transfers");
        dir.mkdirs();
        return new File(dir, transferId + "_" + fileName + ".tmp");
    }

    private File getFinalFile(String fileName) {
        File dir = new File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "P2PChat");
        dir.mkdirs();
        return new File(dir, fileName);
    }
}
