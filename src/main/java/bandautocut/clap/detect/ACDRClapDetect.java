package bandautocut.clap.detect;

import java.nio.ShortBuffer;


/**
 * Amplitude to clap duration ratio with an adaptive threshold.
 * @see <a href="https://pub.tik.ee.ethz.ch/students/2013-FS/GA-2013-03.pdf">inspired by</a>
 */
public class ACDRClapDetect implements ClapDetect {
    
    private final ShortBuffer samples;
    private Parameters parameters;

    public ACDRClapDetect(ShortBuffer samples) {
        this.samples = samples;
    }
    
    public ACDRClapDetect(ShortBuffer samples, Parameters parameters) {
        this.samples = samples;
        this.parameters = parameters;
    }
    
    public static class Parameters {
        private int thresholdConstant = (int) (Short.MAX_VALUE / 2.9);
        private int decisionThreshold = 10_000_000;
        private int shortTermDuration = 5;
        private int longTermDuration = 8000 * 20;
        private int maxAllowedClapDuraton = 3;
        private int clapsMax = 128;
        private int clapAdvance = 8000 / 6;

        public int getThresholdConstant() {
            return thresholdConstant;
        }

        public void setThresholdConstant(int thresholdConstant) {
            this.thresholdConstant = thresholdConstant;
        }

        public int getDecisionThreshold() {
            return decisionThreshold;
        }

        public void setDecisionThreshold(int decisionThreshold) {
            this.decisionThreshold = decisionThreshold;
        }

        public int getShortTermDuration() {
            return shortTermDuration;
        }

        public void setShortTermDuration(int shortTermDuration) {
            this.shortTermDuration = shortTermDuration;
        }

        public int getLongTermDuration() {
            return longTermDuration;
        }

        public void setLongTermDuration(int longTermDuration) {
            this.longTermDuration = longTermDuration;
        }

        public int getMaxAllowedClapDuraton() {
            return maxAllowedClapDuraton;
        }

        public void setMaxAllowedClapDuraton(int maxAllowedClapDuraton) {
            this.maxAllowedClapDuraton = maxAllowedClapDuraton;
        }

        public int getClapsMax() {
            return clapsMax;
        }

        public void setClapsMax(int clapsMax) {
            this.clapsMax = clapsMax;
        }

        public int getClapAdvance() {
            return clapAdvance;
        }

        public void setClapAdvance(int clapAdvance) {
            this.clapAdvance = clapAdvance;
        }
    }

    @Override
    public ClapDetectResult clapDetect(int numberOfClaps) {
        ClapDetectResult result = new ClapDetectResult();
        int[] clapPositions = new int[parameters.getClapsMax()];
        int numberOfFoundClaps = 0;
        
        int maxVal = 0;
        int clapDuration = 0;
        int shortTermSamples = 0;
        int shortTermSum = 0;
        int longTermSamples = 0;
        int longTermSum = 0;
        for (int i = 0; i < samples.remaining() && numberOfFoundClaps < parameters.getClapsMax(); i++) {
            int magnitude = Math.abs(samples.get(i));
            
            if (longTermSamples == parameters.getLongTermDuration()) {
                longTermSum -= Math.abs(samples.get(i - longTermSamples));
                longTermSamples--;
            }
            longTermSum += magnitude;
            longTermSamples++;
            int longTermMean = longTermSum / longTermSamples;

            if (shortTermSamples == parameters.getShortTermDuration()) {
                shortTermSum -= Math.abs(samples.get(i - shortTermSamples));
                shortTermSamples--;
            }
            shortTermSum += magnitude;
            shortTermSamples++;
            int shortTermMean = shortTermSum / shortTermSamples;

            int threshold = parameters.getThresholdConstant() + longTermMean;

//            System.out.format("Means: %d s %d l\r", shortTermMean, longTermMean);

            if (shortTermMean > threshold) {
                if (maxVal == 0) {
//                    System.out.format("Detect start: %d (%.2fs)\r", i, i / (float) 8000);
                }
                maxVal = Math.max(maxVal, shortTermMean - threshold);
                if (++clapDuration > parameters.getMaxAllowedClapDuraton()) {
//                    System.out.println("Clap too long at " + i);
                    maxVal = 0;
                    clapDuration = 0;
                } else {
                    int clapLikeliness = (maxVal * maxVal) / clapDuration;
//                    System.out.format("Clap likeliness: %.2f %d (%.2fs)\r", clapLikeliness, i, i / (float) 8000);
                    if (clapLikeliness > parameters.getDecisionThreshold()) {
                        clapPositions[numberOfFoundClaps++] = i;
//                        System.out.format("Detected clap: %d (%.2fs) likeliness=%d\r", i, i / (float) 8000, clapLikeliness);
                        
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
                        
                        i += parameters.getClapAdvance();
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

    public Parameters getParameters() {
        return parameters;
    }

    public void setParameters(Parameters parameters) {
        this.parameters = parameters;
    }
}
