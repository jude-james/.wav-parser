import java.io.*;

public class Main {
    public static void main(String[] args) {
        String path = "Demo.wav";
        File file = new File(path);

        WavParser parser = new WavParser(file);

        if (parser.read()) {
            System.out.println("Success");

            System.out.println(STR."Audio format: \{parser.audioFormat}");
            System.out.println(STR."Number of channels: \{parser.numChannels}");
            System.out.println(STR."Sample rate (Hz): \{parser.sampleRate}");
            System.out.println(STR."Byte rate: \{parser.byteRate}");
            System.out.println(STR."BlockAlign (Frame size): \{parser.blockAlign}");
            System.out.println(STR."Bits per sample: \{parser.bitsPerSample}");

            System.out.println(STR."Signed: \{parser.signed}");
            System.out.println(STR."Big endian: \{parser.endianness}");
            System.out.println(STR."Duration (seconds): \{parser.duration}");

            // raw sound data
            byte[] sound = parser.data;

            // samples per channel, e.g. samples[0][1] is the 2nd sample in the 1st channel
            float[][] samples = WavParser.getSamples(parser.data, parser.numChannels, parser.blockAlign, parser.bitsPerSample);

            // Creates a copy of the Demo file in my desktop, change path name to whatever you want
            File newFile = new File("/Users/judejames/desktop/Save Test.wav");
            WavParser.write(newFile, parser.data, parser.audioFormat, parser.numChannels, parser.sampleRate, parser.byteRate, parser.blockAlign, parser.bitsPerSample);
        }
    }
}


