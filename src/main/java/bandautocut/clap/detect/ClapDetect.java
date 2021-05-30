package bandautocut.clap.detect;

/**
 * Contract for a clap detection algorithm.
 */
public interface ClapDetect {

    /**
     * Detects the last clap of {@code numberOfClaps} in the audio stream.
     * @param numberOfClaps the number of claps to find the nth clap
     * @return a result containing the clap position, if found
     */
    ClapDetectResult clapDetect(int numberOfClaps);
}
