package bandautocut.clap.detect;

import java.nio.ShortBuffer;

/**
 *
 */
public class LoudnessDetection {

    public float suggestMultiplier(ShortBuffer buf) {
        float maxMagnitude = 0;
        for (int i = 0; i < buf.limit() && i < 8000 * 30; i++) {
            short sample = buf.get(i);
            float magnitude = Math.abs(sample / (float) Short.MAX_VALUE);
            maxMagnitude = Math.max(maxMagnitude, magnitude);
        }
        
        if (maxMagnitude < 0.5f) {
            return (1 / maxMagnitude) * 0.95f;
        }
        
        return 1f;
    }
}
