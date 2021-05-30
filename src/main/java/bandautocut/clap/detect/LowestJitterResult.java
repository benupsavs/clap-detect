package bandautocut.clap.detect;

public class LowestJitterResult {

    private int averageJitter;
    private int bestPosition;

    public int getAverageJitter() {
        return averageJitter;
    }

    public void setAverageJitter(int averageJitter) {
        this.averageJitter = averageJitter;
    }

    public int getBestPosition() {
        return bestPosition;
    }

    public void setBestPosition(int bestPosition) {
        this.bestPosition = bestPosition;
    }
}
