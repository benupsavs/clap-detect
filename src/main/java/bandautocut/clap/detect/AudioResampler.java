package bandautocut.clap.detect;

import com.github.kokorin.jaffree.ffmpeg.ChannelOutput;
import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.FFmpegResult;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

/**
 *
 */
public class AudioResampler {
    
    public ByteBuffer resampleAudio(String inputPath, int numberOfChannels, int sampleRate) {
        SeekableInMemoryByteChannel byteChannel = new SeekableInMemoryByteChannel();
        FFmpegResult result = FFmpeg.atPath()
                .addInput(UrlInput.fromPath(Path.of(inputPath)))
                .addArgument("-vn") // no video
                .addArguments("-ac", String.valueOf(numberOfChannels))
                .addArguments("-ar", String.valueOf(sampleRate))
                .addArguments("-f", "s16le")
                .addArguments("-acodec", "pcm_s16le")
                .addOutput(ChannelOutput.toChannel("out.raw", byteChannel))
                .execute();
        System.out.println("Audio Size: " + result.getAudioSize());
        return ByteBuffer.wrap(byteChannel.array(), 0, (int) byteChannel.size());
    }
}
