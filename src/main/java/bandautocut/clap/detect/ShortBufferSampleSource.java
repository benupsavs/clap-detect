package bandautocut.clap.detect;

import java.nio.ShortBuffer;

/**
 *
 */
public class ShortBufferSampleSource implements SampleSource {
    
    private final ShortBuffer buf;

    public ShortBufferSampleSource(ShortBuffer buf) {
        this.buf = buf;
    }

    @Override
    public int size() {
        return buf.remaining();
    }

    @Override
    public short sampleAt(int position) {
        return buf.get(position);
    }
}
