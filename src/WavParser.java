import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

public class WavParser {
    private final File file;

    // format
    public int audioFormat;
    public int numChannels;
    public float sampleRate;
    public float byteRate;
    public int blockAlign;
    public int bitsPerSample;

    public byte[] data;
    public boolean signed;
    public boolean endianness = false; // WAV uses little-endian order (false = little)
    public float duration;

    public WavParser(File file) {
        this.file = file;
    }

    public boolean read() {
        try {
            DataInputStream inputStream = new DataInputStream(new FileInputStream(file));

            LinkedHashMap<String, Long> chunkLookup = readSubChunks(inputStream);

            if (chunkLookup == null) {
                return false;
            }

            System.out.println(chunkLookup);

            if (chunkLookup.get("fmt ") == null || chunkLookup.get("data") == null) {
                System.out.println("Perverse wav file. Possible odd length intermediate chunk");
                return false;
            }

            long fmtChunkOffset = chunkLookup.get("fmt ");
            long dataChunkOffset = chunkLookup.get("data");

            inputStream = new DataInputStream(new FileInputStream(file));
            inputStream.skip(fmtChunkOffset + 8);
            readFmtChunk(inputStream);

            inputStream = new DataInputStream(new FileInputStream(file));
            inputStream.skip(dataChunkOffset + 4);
            readDataChunk(inputStream);

            inputStream.close();

            return true;
        }
        catch (IOException e) {
            System.out.println("Invalid file");
            return false;
        }
    }

    private LinkedHashMap<String, Long> readSubChunks(DataInputStream in) {
        try {
            byte[] bytes = new byte[4];

            // Reads the "RIFF" chunk descriptor
            if (in.read(bytes) < 0) {
                return null;
            }
            String chunkID = new String(bytes, StandardCharsets.ISO_8859_1);

            int chunkSize = Integer.reverseBytes(in.readInt());

            if (in.read(bytes) < 0) {
                return null;
            }
            String format = new String(bytes, StandardCharsets.ISO_8859_1);

            if (!chunkID.equals("RIFF") || !format.equals("WAVE")) {
                System.out.println("Invalid .wav file");
                return null;
            }

            if (file.length() - 8 != chunkSize) {
                System.out.println(STR."Invalid file size: \{file.length()}. Expected: \{chunkSize + 8}");
                return null;
            }

            LinkedHashMap<String, Long> chunkLookup = new LinkedHashMap<>();
            chunkLookup.put(chunkID, 0L);

            long chunkOffset = 12; // First sub-chunk always starts at byte 12

            // Reads every other chunk descriptor
            while (in.read(bytes) != -1) {
                chunkID = new String(bytes, StandardCharsets.ISO_8859_1);
                chunkLookup.put(chunkID, chunkOffset);

                // read chunk size & skip to next sub-chunk
                chunkSize = Integer.reverseBytes(in.readInt());
                long bytesSkipped = in.skip(chunkSize);
                chunkOffset += bytesSkipped + 8;
            }

            return chunkLookup;
        }
        catch (IOException e) {
            System.out.println("Invalid file");
            return null;
        }
    }

    private void readFmtChunk(DataInputStream in) {
        try {
            audioFormat = Short.reverseBytes(in.readShort());
            numChannels = Short.reverseBytes(in.readShort());
            sampleRate = Integer.reverseBytes(in.readInt());
            byteRate = Integer.reverseBytes(in.readInt());
            blockAlign = Short.reverseBytes(in.readShort());
            bitsPerSample = Short.reverseBytes(in.readShort());

            // 8-bit (or lower) sample sizes are always unsigned. 9 bits or higher are always signed
            signed = bitsPerSample >= 9;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void readDataChunk(DataInputStream in) {
        try {
            int numBytesData = Integer.reverseBytes(in.readInt());

            byte[] data = new byte[numBytesData];
            in.readFully(data);
            this.data = data;

            int numSamplesPerChannel = numBytesData / blockAlign;
            duration = numSamplesPerChannel / sampleRate;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static float[][] getSamples(byte[] data, int numChannels, int blockAlign, int bitsPerSample) {
        int numSamplesPerChannel = data.length / blockAlign;
        float[][] samples = new float[numChannels][numSamplesPerChannel];

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        if (bitsPerSample != 8 && bitsPerSample != 16 && bitsPerSample != 24 && bitsPerSample != 32) {
            System.out.println(STR."Unsupported bits per sample: \{bitsPerSample}");
            return samples;
        }

        for (int sample = 0; sample < numSamplesPerChannel; sample++) {
            for (int channel = 0; channel < numChannels; channel++) {
                if (buffer.position() < data.length) {
                    if (bitsPerSample == 8) {
                        samples[channel][sample] = (buffer.get() & 0xff); // converting to unsigned 0-255
                    }
                    else if (bitsPerSample == 16) {
                        samples[channel][sample] = buffer.getShort();
                    }
                    else if (bitsPerSample == 24) {
                        samples[channel][sample] = (
                                (buffer.get())
                                        | ((buffer.get()) <<  8)
                                        | ((buffer.get()) << 16)
                        );

                    }
                    else {
                        samples[channel][sample] = buffer.getInt();
                    }
                }
            }
        }

        return samples;
    }

    public static void write(File file, byte[] data, int audioFormat, int numChannels, float sampleRate, float byteRate, int blockAlign, int bitsPerSample) {
        try {
            DataOutputStream outputStream = new DataOutputStream(new FileOutputStream(file));

            // Writes RIFF chunk
            outputStream.writeBytes("RIFF");
            outputStream.write(intToByteArray(36 + data.length)); // chunkSize (= 36 + subChunk2Size)
            outputStream.writeBytes("WAVE");

            // Format chunk
            outputStream.writeBytes("fmt ");
            outputStream.write(intToByteArray(16)); // subChunk1Size (=16)
            outputStream.write(shortToByteArray((short) audioFormat));
            outputStream.write(shortToByteArray((short) numChannels));
            outputStream.write(intToByteArray((int) sampleRate));
            outputStream.write(intToByteArray((int) byteRate));
            outputStream.write(shortToByteArray((short) blockAlign));
            outputStream.write(shortToByteArray((short) bitsPerSample));

            // Data chunk
            outputStream.writeBytes("data");
            outputStream.write(intToByteArray(data.length));
            outputStream.write(data);

            outputStream.flush();
            outputStream.close();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] intToByteArray(int value) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(value);
        return buffer.array();
    }

    private static byte[] shortToByteArray(short value) {
        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(value);
        return buffer.array();
    }
}
