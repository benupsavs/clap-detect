package bandautocut.clap.detect;

/**
 *
 */
public interface SampleSource {
    
    int size();
    
    short sampleAt(int position);
}
