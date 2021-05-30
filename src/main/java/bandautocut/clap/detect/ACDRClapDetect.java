package bandautocut.clap.detect;

import java.nio.ShortBuffer;


/**
 * Amplitude to clap duration ratio with an adaptive threshold.
 * @see <a href="https://pub.tik.ee.ethz.ch/students/2013-FS/GA-2013-03.pdf">inspired by</a>
 */
public class ACDRClapDetect implements ClapDetect {
    
    public static final int THRESHOLD_CONSTANT = (int) (Short.MAX_VALUE / 2.9);
    public static final int DECISION_THRESHOLD = 10000000;
    public static final int SHORT_TERM_DURATION = 5;
    public static final int LONG_TERM_DURATION = 8000 * 20;
    public static final int MAX_ALLOWED_CLAP_DURATION = 3;
    public static final int CLAPS_MAX = 128;
    public static final int CLAP_ADVANCE = 8000 / 6;
    
    private final ShortBuffer samples;

    public ACDRClapDetect(ShortBuffer samples) {
        this.samples = samples;
    }

    @Override
    public ClapDetectResult clapDetect(int numberOfClaps) {
        ClapDetectResult result = new ClapDetectResult();
        int[] clapPositions = new int[CLAPS_MAX];
        int numberOfFoundClaps = 0;
        
        int maxVal = 0;
        int clapDuration = 0;
        int shortTermSamples = 0;
        int shortTermSum = 0;
        int longTermSamples = 0;
        int longTermSum = 0;
        for (int i = 0; i < samples.remaining() && numberOfFoundClaps < CLAPS_MAX; i++) {
            int magnitude = Math.abs(samples.get(i));
            
            if (longTermSamples == LONG_TERM_DURATION) {
                longTermSum -= Math.abs(samples.get(i - longTermSamples));
                longTermSamples--;
            }
            longTermSum += magnitude;
            longTermSamples++;
            int longTermMean = longTermSum / longTermSamples;

            if (shortTermSamples == SHORT_TERM_DURATION) {
                shortTermSum -= Math.abs(samples.get(i - shortTermSamples));
                shortTermSamples--;
            }
            shortTermSum += magnitude;
            shortTermSamples++;
            int shortTermMean = shortTermSum / shortTermSamples;

            int threshold = THRESHOLD_CONSTANT + longTermMean;
            
//            System.out.format("Means: %d s %d l\r", shortTermMean, longTermMean);

            if (shortTermMean > threshold) {
                if (maxVal == 0) {
//                    System.out.format("Detect start: %d (%.2fs)\r", i, i / (float) 8000);
                }
                maxVal = Math.max(maxVal, shortTermMean - threshold);
                if (++clapDuration > MAX_ALLOWED_CLAP_DURATION) {
//                    System.out.println("Clap too long at " + i);
                    maxVal = 0;
                    clapDuration = 0;
                } else {
                    int clapLikeliness = (maxVal * maxVal) / clapDuration;
//                    System.out.format("Clap likeliness: %.2f %d (%.2fs)\r", clapLikeliness, i, i / (float) 8000);
                    if (clapLikeliness > DECISION_THRESHOLD) {
                        clapPositions[numberOfFoundClaps++] = i;
                        System.out.format("Detected clap: %d (%.2fs) likeliness=%d\r", i, i / (float) 8000, clapLikeliness);
                        
                        if (shortTermSamples > (clapDuration + 2)) {
                            // Remove the clap's samples from the averages, to avoid interfering with future claps
                            for (int j = i - (clapDuration + 2); j < i; j++) {
                                int previousMagnitude = Math.abs(samples.get(j));
                                longTermSum -= previousMagnitude;
                                longTermSamples--;
                                shortTermSum -= previousMagnitude;
                                shortTermSamples--;
                            }
                        }
                        
                        i += CLAP_ADVANCE;
                        clapDuration = 0;
                        maxVal = 0;
                    }
                }
            } else {
                if (maxVal > 0) {
//                    System.out.format("Detect end: %d (%.2fs)\r", i, i / (float) 8000);
                }

                maxVal = 0;
                clapDuration = 0;
            }
        }
        
        if (numberOfFoundClaps < numberOfClaps) {
            return result;
        }
        
        LowestJitterResult jitterResult = new LowestJitter().findLowestJitter(clapPositions, numberOfFoundClaps, numberOfClaps);
        result.setAverageJitter(jitterResult.getAverageJitter());
        result.setBestPosition(jitterResult.getBestPosition());
        
        return result;
    }
    
}
