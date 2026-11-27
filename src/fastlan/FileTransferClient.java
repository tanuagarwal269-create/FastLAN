package fastlan;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.List;

public class FileTransferClient {

    private final String host;
    private final int port;
    private final List<File> files;
    private final TransferListener listener;

    public FileTransferClient(String host, int port, List<File> files, TransferListener listener) {
        this.host = host;
        this.port = port;
        this.files = files;
        this.listener = listener;
    }

    public void sendFiles() {
        new Thread(() -> {

            if (files == null || files.isEmpty()) {
                notifyError("No files selected to send.");
                return;
            }

            try {
                Socket socket = new Socket();

                // Connect within 5 seconds
                socket.connect(new InetSocketAddress(host, port), 5000);

                try (Socket clientSocket = socket;
                     DataOutputStream out = new DataOutputStream(
                         new BufferedOutputStream(clientSocket.getOutputStream()))) {

                    notifyStatus("Connected to " + host + ":" + port);

                    int totalFiles = files.size();
                    out.writeInt(totalFiles);

                    for (int i = 0; i < totalFiles; i++) {

                        File file = files.get(i);

                        if (!file.exists() || !file.isFile()) {
                            throw new IOException("File not found: " + file.getAbsolutePath());
                        }

                        String fileName = file.getName();
                        long fileSize = file.length();

                        out.writeUTF(fileName);
                        out.writeLong(fileSize);

                        notifyFileStart(fileName, fileSize, i + 1, totalFiles);

                        long bytesSentTotal = 0;
                        long startTime = System.nanoTime();

                        byte[] buffer = new byte[8192];

                        try (BufferedInputStream fileIn =
                                 new BufferedInputStream(new FileInputStream(file))) {

                            int read;

                            while ((read = fileIn.read(buffer)) != -1) {

                                out.write(buffer, 0, read);
                                bytesSentTotal += read;

                                double elapsedSec =
                                    (System.nanoTime() - startTime) / 1_000_000_000.0;

                                double speedMBps = elapsedSec > 0
                                    ? (bytesSentTotal / (1024.0 * 1024.0)) / elapsedSec
                                    : 0.0;

                                notifyProgress(bytesSentTotal, fileSize, speedMBps);
                            }
                        }

                        out.flush();
                        notifyFileComplete(fileName);
                    }

                    out.flush();

                    notifyStatus("All " + totalFiles + " file(s) sent successfully.");
                }

            } catch (IOException e) {
                notifyError("Send failed: " + e.getMessage());
            }

        }, "FastLAN-Client-Thread").start();
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