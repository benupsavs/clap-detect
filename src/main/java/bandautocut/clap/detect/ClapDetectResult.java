package bandautocut.clap.detect;

/**
 * Result of a clap detection operation.
 */
public class ClapDetectResult {

    /** The total jitter across the chosen claps. */
    private int totalJitter;

    /** The average jitter across the chosen claps. */
    private int averageJitter;

    /** The index of the clap, measured in samples. */
    private int clapIndex;
    
    /** The most likely sample index of the last clap in the series. */
    private int bestPosition;

    public int getTotalJitter() {
        return totalJitter;
    }

    public void setTotalJitter(int totalJitter) {
        this.totalJitter = totalJitter;
    }

    public int getAverageJitter() {
        return averageJitter;
    }

    public void setAverageJitter(int averageJitter) {
        this.averageJitter = averageJitter;
    }

    public int getClapIndex() {
        return clapIndex;
    }

    public void setClapIndex(int clapIndex) {
        this.clapIndex = clapIndex;
    }

    public int getBestPosition() {
        return bestPosition;
    }

    public void setBestPosition(int bestPosition) {
        this.bestPosition = bestPosition;
    }
}
