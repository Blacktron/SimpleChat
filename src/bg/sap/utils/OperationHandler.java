package bg.sap.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;

/**
 * @Created by Terrax on 15.3.2015.
 */

public class OperationHandler {

    /**
     * Read data from the a selection key.
     * @param socketChannel the channel from which the data was sent.
     * @return the message that is in the channel.
     * @throws IOException
     */
    public static String readData(SocketChannel socketChannel) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(Constants.SINGLE_BYTE);

        StringBuilder stringBuilder = new StringBuilder();

        // Read byte-by-byte until \n is reached.
        for (int i = 0; i < Constants.BUFFER_SIZE; i++) {
            socketChannel.read(byteBuffer);
            byteBuffer.flip();

            String temp = new String(byteBuffer.array(), Charset.forName(Constants.UTF_ENCODING));

            if (temp.equals("\n")) break;
            else stringBuilder.append(temp);
        }

        return stringBuilder.toString();
    }

    /**
     * Send data on a socket channel.
     * @param socketChannel the channel on which to send the data.
     * @param data the data to send.
     * @throws IOException
     */
    public static void sendData(SocketChannel socketChannel, String data) throws IOException {
        data += "\n";

        ByteBuffer buffer = ByteBuffer.wrap(data.getBytes(Charset.forName(Constants.UTF_ENCODING)));

        socketChannel.write(buffer);
    }

    /**
     * Send file to a channel.
     * @param selectedFile the file to be sent.
     * @param fileSendingChannel the channel on which to send the file.
     * @throws IOException
     */
    public static void sendFile(File selectedFile, SocketChannel fileSendingChannel) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(selectedFile);
        FileChannel fileChannel = fileInputStream.getChannel();

        // Send the upload command, file name and file size.
        sendData(fileSendingChannel, Constants.FILE_UPLOAD + "-" + selectedFile.getName() + "-" + fileChannel.size());

        // Send the date.
        long size = fileChannel.size();
        long position = 0;
        while (position < size) {
            position += fileChannel.transferTo(position, Constants.FILE_FRAGMENT_SIZE, fileSendingChannel);
        }

        // Close the channel.
        fileSendingChannel.close();
    }

    /**
     * Download a file from a socket channel.
     * @param key the key with the data.
     * @param fileChannelHelper the file channel with the file's size.
     * @throws IOException
     */
    public static void getFile(SelectionKey key, FileChannelHelper fileChannelHelper) throws IOException {
        FileChannel fileChannel = fileChannelHelper.getFileChannel();
        SocketChannel socketChannel = (SocketChannel) key.channel();

        long readBytes = fileChannel.transferFrom(socketChannel, fileChannelHelper.getPosition(), Constants.FILE_FRAGMENT_SIZE);
        fileChannelHelper.incrementPosition(readBytes);

        // Close the file channel if all data has been sent.
        if (fileChannelHelper.getPosition() >= fileChannelHelper.getSize()) {
            fileChannel.force(false);
            key.cancel();

            fileChannel.close();
            socketChannel.close();
        }
    }
}