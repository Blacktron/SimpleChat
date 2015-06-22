package bg.sap.utils;

import java.nio.channels.FileChannel;

/**
 * @Created by Terrax on 17.05.2015.
 */
public class FileChannelHelper {
    private long size;
    private long position;
    private FileChannel fileChannel;

    public FileChannelHelper(long size, FileChannel fileChannel) {
        this.size = size;
        this.position = 0;
        this.fileChannel = fileChannel;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getPosition() {
        return position;
    }

    public void setPosition(long position) {
        this.position = position;
    }

    public void incrementPosition(long value) {
        position += value;
    }

    public FileChannel getFileChannel() {
        return fileChannel;
    }

    public void setFileChannel (FileChannel fileChannel) {
        this.fileChannel = fileChannel;
    }
}
