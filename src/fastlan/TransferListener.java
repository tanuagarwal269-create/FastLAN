package fastlan;

public interface TransferListener {
    void onStatus(String message);
    void onFileStart(String fileName, long fileSize, int index, int total);
    void onProgress(long bytesTransferred, long fileSize, double speedMBps);
    void onFileComplete(String fileName);
    void onError(String error);
}