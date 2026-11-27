package fastlan;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class FileTransferServer {
    private final int port;
    private final File saveDirectory;
    private final TransferListener listener;
    private volatile boolean running = false;
    private ServerSocket serverSocket;

    public FileTransferServer(int port, File saveDirectory, TransferListener listener) {
        this.port = port;
        this.saveDirectory = saveDirectory;
        this.listener = listener;
    }

    public void start() {
        running = true;
        new Thread(() -> {
            try {
                if (!saveDirectory.exists()) {
                    saveDirectory.mkdirs();
                }
                serverSocket = new ServerSocket(port);
                notifyStatus("Listening on port " + port + "...");

                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        handleClient(clientSocket);
                    } catch (IOException e) {
                        if (running) {
                            notifyError("Client connection error: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                if (running) {
                    notifyError("Could not start server on port " + port + ": " + e.getMessage());
                }
            } finally {
                stop();
            }
        }, "FastLAN-Server-Thread").start();
    }

    private void handleClient(Socket socket) {
        try (Socket client = socket;
             DataInputStream in = new DataInputStream(new BufferedInputStream(client.getInputStream()))) {

            notifyStatus("Connected to " + client.getInetAddress().getHostAddress());
            int totalFiles = in.readInt();

            for (int i = 1; i <= totalFiles; i++) {
                String fileName = sanitizeFileName(in.readUTF());
                long fileSize = in.readLong();
                File outFile = new File(saveDirectory, fileName);

                notifyFileStart(fileName, fileSize, i, totalFiles);

                long bytesReadTotal = 0;
                long startTime = System.nanoTime();
                byte[] buffer = new byte[8192];

                try (BufferedOutputStream fileOut = new BufferedOutputStream(new FileOutputStream(outFile))) {
                    long remaining = fileSize;
                    while (remaining > 0) {
                        int toRead = (int) Math.min(buffer.length, remaining);
                        int read = in.read(buffer, 0, toRead);
                        if (read == -1) {
                            throw new IOException("Unexpected end of stream while receiving " + fileName);
                        }
                        fileOut.write(buffer, 0, read);
                        remaining -= read;
                        bytesReadTotal += read;

                        double elapsedSec = (System.nanoTime() - startTime) / 1_000_000_000.0;
                        double speedMBps = elapsedSec > 0 ? (bytesReadTotal / (1024.0 * 1024.0)) / elapsedSec : 0.0;
                        notifyProgress(bytesReadTotal, fileSize, speedMBps);
                    }
                    fileOut.flush();
                }

                notifyFileComplete(fileName);
            }

            notifyStatus("All " + totalFiles + " file(s) received.");
        } catch (IOException e) {
            if (running) {
                notifyError("Transfer failed: " + e.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        notifyStatus("Server stopped.");
    }

    private String sanitizeFileName(String name) {
        return new File(name).getName();
    }

    private void notifyStatus(String msg) {
        if (listener != null) listener.onStatus(msg);
    }

    private void notifyFileStart(String name, long size, int index, int total) {
        if (listener != null) listener.onFileStart(name, size, index, total);
    }

    private void notifyProgress(long current, long total, double speed) {
        if (listener != null) listener.onProgress(current, total, speed);
    }

    private void notifyFileComplete(String name) {
        if (listener != null) listener.onFileComplete(name);
    }

    private void notifyError(String err) {
        if (listener != null) listener.onError(err);
    }
}