package bandautocut.clap.detect;

/**
 * Detect the best clap position by finding the lowest jitter between claps.
 */
public class LowestJitter {
    
    public LowestJitterResult findLowestJitter(int[] claps, int numberOfClapsDetected, int numberOfClapsInSequence) {
        var result = new LowestJitterResult();
        int bestJitter = Integer.MAX_VALUE;
        int bestPosition = -1;
        
        for (int i = numberOfClapsInSequence; i < numberOfClapsDetected; i++) {
            int previousDistance = claps[i] - claps[i - numberOfClapsInSequence];
            int totalJitter = 0;
            for (int j = i - numberOfClapsInSequence + 1; j < i; j++) {
                int currentDistance = claps[j] - claps[j - 1];
                int currentJitter = Math.abs(currentDistance - previousDistance);
                totalJitter += currentJitter;
                previousDistance = currentDistance;
//                System.out.printf("Current distance at %d: %d clap1: %d clap2: %d\n", j, currentDistance, claps[j - 1], claps[j]);
            }
            
            if (totalJitter < bestJitter) {
                bestJitter = totalJitter;
                bestPosition = claps[i];
            }
        }
        
        result.setAverageJitter(bestJitter / numberOfClapsInSequence);
        result.setBestPosition(bestPosition);

        return result;
    }
}
