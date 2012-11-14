import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.util.ArrayList;

public class StringDecodingTest {
    private interface UTF8Decoder {
        String decode(byte[] source, int offset, int length);

        UTF8Decoder newInstance();
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static final class UTF8CharsetDecoder implements UTF8Decoder {
        private final static int BUFFER_SIZE = 1024;
        private final CharBuffer outBuffer = CharBuffer.allocate(BUFFER_SIZE);
        private final CharsetDecoder decoder = UTF8.newDecoder();

        public String decode(byte[] source, int offset, int length) {
            if (length == 0) {
                // CharsetDecoder throws an exception when flushing an empty
                // buffer, so we need to special case the empty string
                return "";
            }

            outBuffer.clear();
            decoder.reset();

            final ByteBuffer input = ByteBuffer.wrap(source, offset, length);
            while (input.hasRemaining()) {
                CoderResult result = decoder.decode(input, outBuffer, true);
                if (result == CoderResult.OVERFLOW) {
                    throw new RuntimeException("TODO: Handle overflow?");
                } else if (result != CoderResult.UNDERFLOW) {
                    throw new RuntimeException("TODO: Handle errors?");
                }
            }

            CoderResult result = decoder.flush(outBuffer);
            if (result != CoderResult.UNDERFLOW) {
                throw new RuntimeException("TODO: Handle errors.");
            }

            return new String(outBuffer.array(), 0, outBuffer.position());
        }

        public UTF8CharsetDecoder newInstance() {
            return new UTF8CharsetDecoder();
        }
    }

    private static final class StringDecoder implements UTF8Decoder {
        public String decode(byte[] source, int offset, int length) {
            return new String(source, offset, length, UTF8);
        }

        public StringDecoder newInstance() {
            return new StringDecoder();
        }
    }

    private static final class CustomDecoder implements UTF8Decoder {
        private final edu.mit.net.StringDecoder decoder = new edu.mit.net.StringDecoder();

        public String decode(byte[] source, int offset, int length) {
            return decoder.finish(source, offset, length);
        }

        public CustomDecoder newInstance() {
            return new CustomDecoder();
        }
    }


    private static void error() {
        System.err.println("(chardecoder|string|custom) (once|reuse) (input strings)");
        System.exit(1);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            error();
            return;
        }

        UTF8Decoder decoder;
        if (args[0].equals("chardecoder")) {
            decoder = new UTF8CharsetDecoder();
        } else if (args[0].equals("string")) {
            decoder = new StringDecoder();
        } else if (args[0].equals("custom")) {
            decoder = new CustomDecoder();
        } else {
            error();
            return;
        }

        boolean reuseDecoder = true;
        if (args[1].equals("once")) {
            reuseDecoder = false;
        } else if (!args[1].equals("reuse")) {
            error();
            return;
        }

        ArrayList<byte[]> lines = new ArrayList<byte[]>();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(new FileInputStream(args[2]), "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            lines.add(line.getBytes("UTF-8"));
        }

        //~ final int ITERATIONS = 5000000;
        //~ final int ITERATIONS = 1000000;
        //~ final int ITERATIONS = 10000;
        final int ITERATIONS = 400;
        for (int j = 0; j < 10; ++j) {
            long start = System.nanoTime();
            for (int i = 0; i < ITERATIONS; ++i) {
                for (byte[] bytes : lines) {
                    UTF8Decoder temp = decoder;
                    if (!reuseDecoder) {
                        temp = decoder.newInstance();
                    }

                    String out = temp.decode(bytes, 0, bytes.length);
                    assert out.equals(new String(bytes, "UTF-8"));
                }
            }
            long end = System.nanoTime();
            System.out.println(((double) end-start)/1000000000. + " seconds");
        }
    }
}
