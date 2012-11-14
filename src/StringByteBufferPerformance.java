import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import edu.mit.net.StringByteBufferEncoder;
import edu.mit.net.StringEncoder;
import edu.mit.net.StringReflectByteBufferEncoder;

public class StringByteBufferPerformance {
    private interface Encoder {
        ByteBuffer encodeToNewBuffer(String source);
    }

    private static final class JDKEncoder implements Encoder {
        public ByteBuffer encodeToNewBuffer(String source) {
            try {
                byte[] array = source.getBytes("UTF-8");
                return ByteBuffer.wrap(array);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final class GenericEncoder implements Encoder {
        private final StringEncoder encoder = new StringEncoder();

        public ByteBuffer encodeToNewBuffer(String source) {
            return encoder.toNewByteBuffer(source);
        }
    }

    private static final class ByteBufferEncoder implements Encoder {
        private final StringByteBufferEncoder encoder = new StringByteBufferEncoder();

        public ByteBuffer encodeToNewBuffer(String source) {
            return encoder.toNewByteBuffer(source);
        }
    }

    private static final class ReflectEncoder implements Encoder {
        private final StringReflectByteBufferEncoder encoder = new StringReflectByteBufferEncoder();

        public ByteBuffer encodeToNewBuffer(String source) {
            return encoder.toNewByteBuffer(source);
        }
    }

    private static void error() {
        System.err.println("(jdk|generic|bytebuffer|reflect) (input strings)");
        System.exit(1);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            error();
            return;
        }

        Encoder encoder;
        if (args[0].equals("jdk")) {
            encoder = new JDKEncoder();
        } else if (args[0].equals("generic")) {
            encoder = new GenericEncoder();
        } else if (args[0].equals("bytebuffer")) {
            encoder = new ByteBufferEncoder();
        } else if (args[0].equals("reflect")) {
            encoder = new ReflectEncoder();
        } else {
            error();
            return;
        }

        ArrayList<String> strings = new ArrayList<String>();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(new FileInputStream(args[1]), "UTF-8"));
        long totalCharacters = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            strings.add(line);
            totalCharacters += line.length();
        }

        // Calibrate the loop to run in ~5 seconds
        System.out.println("calibrating number of iterations ...");
        final long TARGET_SECONDS = 5;
        final long TARGET_NS = TARGET_SECONDS * 1000000000L;
        int iterations = 0;
        long ns = 1;
        do {
            iterations = (int) (iterations / (ns * 0.92) * TARGET_NS) + 16;
            ns = runBenchmark(iterations, encoder, strings);
            //System.out.println("calibrating " + iterations + " " + ns);
        } while (ns < TARGET_NS);
        System.out.println("calibrated " + iterations + " iterations in " + ns + " ns");

        for (int j = 0; j < 10; ++j) {
            ns = runBenchmark(iterations, encoder, strings);
            double charsPerUS = (totalCharacters * iterations) / (double) ns * 1000.;
            System.out.println(charsPerUS + " UTF-16 characters encoded per microsecond");
        }
    }

    private static long runBenchmark(final int iterations, Encoder encoder, ArrayList<String> strings) {
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            for (String value : strings) {
                ByteBuffer out = encoder.encodeToNewBuffer(value);
                try {
                    assert new String(out.array(), 0, out.remaining(), "UTF-8").equals(value);
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        long end = System.nanoTime();
        return end - start;
    }
}
