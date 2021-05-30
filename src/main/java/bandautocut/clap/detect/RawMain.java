package bandautocut.clap.detect;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/**
 * The main class.
 */
public class RawMain {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            exitUsage();
        }
        
        byte[] buf = null;
        try {
            buf = Files.readAllBytes(Paths.get(args[0]));
        } catch (IOException ex) {
            System.err.println("I/O error: " + ex.getMessage());
            System.exit(2);
        }
        ShortBuffer samples = ByteBuffer.wrap(buf)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer();
        
        System.out.println("Read " + samples.limit() + " samples");
        System.out.println("Detecting claps...");
        ClapDetectResult clapDetectResult = new ACDRClapDetect(samples).clapDetect(4);
        if (clapDetectResult.getBestPosition() == -1) {
            System.out.println("No clap sequence found");
            return;
        }

        System.out.printf("Best clap position: %d (%.2f seconds)\n", clapDetectResult.getBestPosition(), clapDetectResult.getBestPosition() / 8000f);
        System.out.flush();
        
        AudioFormat audioFormat = new AudioFormat(8000, 16, 1, true, samples.order() == ByteOrder.BIG_ENDIAN);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
        try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
            line.open(audioFormat, 4096);
            line.start();
            for (int i = Math.max(0, clapDetectResult.getBestPosition() - 32000); i < clapDetectResult.getBestPosition(); i += 2048) {
                line.write(buf, i * 2, 4096);
            }
            line.drain();
            line.stop();
        }
    }
    
    public static void exitUsage() {
        System.err.println("Usage: java -jar clap-detect.jar <filename>");
        System.exit(64);
    }
}
