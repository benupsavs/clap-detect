package bandautocut.clap.detect;

import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.NullOutput;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

public class ConvertMain {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            exitUsage();
        }
        
        ByteBuffer buf = new AudioResampler().resampleAudio(args[0], 1, 8000);
        System.out.println("Buf capacity: " + buf.remaining());
        ShortBuffer samples = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        ClapDetectResult clapDetectResult = new ACDRClapDetect(samples).clapDetect(4);
        if (clapDetectResult.getBestPosition() == 0) {
            System.err.println("Unable to discover clap position");
            return;
        }
        System.out.printf("Best clap position: %d (%.2f seconds)\n", clapDetectResult.getBestPosition(), clapDetectResult.getBestPosition() / 8000f);
        System.out.flush();

        AudioFormat audioFormat = new AudioFormat(8000, 16, 1, true, samples.order() == ByteOrder.BIG_ENDIAN);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
        try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
            line.open(audioFormat, 4096);
            line.start();
            for (int i = Math.max(0, clapDetectResult.getBestPosition() - 20000); i < clapDetectResult.getBestPosition(); i += 2048) {
                line.write(buf.array(), i * 2, 4096);
            }
            line.drain();
            line.stop();
        }
        
        buf = null; // Allow free of buf
        
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
            boolean done = false;
            while (!done) {
                System.err.printf("Did this sound correct? (y/N) ");
                System.err.flush();
                String line = in.readLine().toLowerCase();
                if (line.isEmpty() || line.startsWith("n")) {
                    System.err.println("Quitting");
                    System.exit(0);
                } else if (line.startsWith("y")) {
                    break;
                }
            }
        }

        final AtomicLong atomicDuration = new AtomicLong();
        FFmpeg.atPath()
                .addInput(UrlInput.fromPath(Path.of(args[0])))
                .setOverwriteOutput(true)
                .addOutput(new NullOutput())
                .setProgressListener(progress -> {
                    atomicDuration.set(progress.getTimeMillis());
                })
                .execute();

        long duration = atomicDuration.get() - clapDetectResult.getBestPosition() / 8;
        FFmpeg.atPath()
                .addInput(
                        UrlInput.fromPath(Path.of(args[0]))
                        .setPosition(clapDetectResult.getBestPosition() / 8)
                )
                .setOverwriteOutput(true)
                .addArguments("-movflags", "faststart")
                .setFilter("v", "scale='min(1920,iw)':min'(1080,ih)':force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2")
                .addOutput(UrlOutput.toUrl(args[1]))
                .setProgressListener(progress -> {
                    double percent = 100 * progress.getTimeMillis() / (double) duration;
                    System.out.printf("Progress: %.2f%%\n", percent);
                })
                .execute();
    }
    
    private static void exitUsage() {
        System.err.println("Usage: java -jar clap-detect.jar <input file> <output file>");
        System.exit(64); // EX_USAGE
    }
}
