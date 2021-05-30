package bandautocut.clap.detect;

import java.nio.ShortBuffer;

/**
 * Clap detection routines.
 */
public class ClapDetectSimple implements ClapDetect {
    private static final int SAMPLE_RATE = 8000;
    private static final int WINDOW_INITIAL = 5;
    private static final int WINDOW_ADVANCE = 1;
    private static final int WINDOW_LOOKBACK = WINDOW_INITIAL * 2;
    private static final int CLAPS_MAX = 128;
    private static final int COOLDOWN_THRESHOLD = 300;
    private static final float CLAP_THRESHOLD = 0.2f;
    private static final int LOOKBACK_SAMPLES = 1;
    
    private final ShortBuffer samples;

    public ClapDetectSimple(ShortBuffer samples) {
        this.samples = samples;
    }

    @Override
    public ClapDetectResult clapDetect(int numberOfClaps) {
        ClapDetectResult result = new ClapDetectResult();
        int bestJitter = Integer.MAX_VALUE;

        result.setTotalJitter(Integer.MAX_VALUE);
        result.setAverageJitter(Integer.MAX_VALUE);
        result.setClapIndex(-1);

        // Set a threshold distance for claps and expand it as necessary, to try to exclude out the main recording where possible.
        for (int thresholdSeconds = WINDOW_INITIAL; thresholdSeconds < samples.remaining() / SAMPLE_RATE; thresholdSeconds += WINDOW_ADVANCE) {
            int threshold = thresholdSeconds * SAMPLE_RATE;
            int lookStart = Math.max(LOOKBACK_SAMPLES, threshold + LOOKBACK_SAMPLES - SAMPLE_RATE * WINDOW_LOOKBACK);

            int numberOfFoundClaps = 0;
            int[] clapIdx = new int[CLAPS_MAX];
            for (int i = lookStart; i < threshold && numberOfFoundClaps < CLAPS_MAX; i++) {
                float[] magnitudes = new float[LOOKBACK_SAMPLES + 1];
                for (int j = 0; j <= LOOKBACK_SAMPLES; j++) {
                    magnitudes[j] = Math.abs(samples.get(i - LOOKBACK_SAMPLES + j) / (float) Short.MAX_VALUE);
                }

                double magnitudeDiff = Math.abs(magnitudes[magnitudes.length - 1]) - Math.abs(magnitudes[0]);

                if (magnitudeDiff > CLAP_THRESHOLD) {
                    clapIdx[numberOfFoundClaps++] = i;
                    double secondsSinceLastClap = 0;
                    if (numberOfFoundClaps > 1) {
                        secondsSinceLastClap = (i - clapIdx[numberOfFoundClaps - 2]) / (double)SAMPLE_RATE;
                    }
                    System.out.printf("Detected possible clap at %d (%.2f seconds) Magnitude diff: %.2f Seconds since last clap: %.2f\n", i, i / (float) SAMPLE_RATE, magnitudeDiff, secondsSinceLastClap);
                    // apply cooldown
                    i += COOLDOWN_THRESHOLD;
                }
            }
            
            LowestJitterResult jitterResult = new LowestJitter().findLowestJitter(clapIdx, numberOfFoundClaps, numberOfClaps);
            if (jitterResult.getAverageJitter() < bestJitter) {
                result.setAverageJitter(jitterResult.getAverageJitter());
                result.setBestPosition(jitterResult.getBestPosition());
                bestJitter = result.getAverageJitter();
            }
            
            if (bestJitter < 150) {
                return result;
            }
        }

        return result;
    }
}
