package info.hannes.dicom.app;

import java.io.IOException;
import java.io.InputStream;

import com.imebra.*;

public class PushToImebraPipe implements Runnable {
    private PipeStream mImebraPipe;
    private InputStream mStream;

    public PushToImebraPipe(PipeStream pipe, InputStream stream) {
        mImebraPipe = pipe;
        mStream = stream;
    }

    @Override
    public void run() {
        StreamWriter pipeWriter = new StreamWriter(mImebraPipe.getStreamOutput());

        try {
            // Buffer used to read from the stream
            byte[] buffer = new byte[128000];
            MutableMemory mutableMemory = new MutableMemory();

            // Read until reaching the end
            for (int readBytes = mStream.read(buffer); readBytes >= 0; readBytes = mStream.read(buffer)) {
                // Push the data to the Pipe
                if (readBytes > 0) {
                    mutableMemory.assign(buffer);
                    mutableMemory.resize(readBytes);
                    pipeWriter.write(mutableMemory);
                }
            }
        } catch(IOException e) {

        } finally {
            pipeWriter.delete();
            mImebraPipe.close(50000);
        }
    }
}
