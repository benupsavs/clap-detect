package bandautocut.clap.detect;

import java.nio.ShortBuffer;

/**
 *
 */
public class ShortBufferSampleSource implements SampleSource {
    
    private final String name;
    private final ShortBuffer buf;
    private final int size;
    private final float volumeMultiplier;
    
    private int offset;

    public ShortBufferSampleSource(String name, ShortBuffer buf, float volumeMultiplier) {
        this.name = name;
        this.buf = buf;
        this.size = buf.limit();
        this.volumeMultiplier = volumeMultiplier;
    }

    @Override
    public int size() {
        return buf.remaining();
    }

    @Override
    public short sampleAt(int position) {
        int offsetPosition = position + offset;

        if (offsetPosition < 0 || offsetPosition > size) {
            return 0;
        }

        if (volumeMultiplier == 1) {
            return buf.get(offsetPosition);
        }

        return (short) (buf.get(offsetPosition) * volumeMultiplier);
    }

    @Override
    public String name() {
        return name;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }
}
