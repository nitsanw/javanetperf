import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class ByteBufferTest {
    private interface Tester {
        public void fillInt();
        public void fillByte();
        public void fillArray(byte[] source);
        public void write(FileOutputStream out) throws IOException;
        public int bufferLength();
    }

    public static double throughput(long start, long end, int iterations, int bytesPerIteration) {
        double seconds = (double) (end - start)/ 1000000000.;
        double megabytes = iterations * (double) bytesPerIteration / (double) (1<<20);
        return megabytes / seconds;
    }

    private static class ByteBufferTester implements Tester {
        // HACK: MappedByteBuffer is a base for the private DirectByteBuffer class.
        // using this for direct ByteBuffers seems to make put and putInt go slightly faster. It
        // seems to make no difference for putting arrays. From:
        // http://www.javalobby.org/java/forums/m5276.html#5278
        //~ private final MappedByteBuffer buffer;
        private final ByteBuffer buffer;
        //~ private final byte[] cache;

        public ByteBufferTester(ByteBuffer buffer) {
            //~ this.buffer = (MappedByteBuffer) buffer;
            this.buffer = buffer;
            //~ cache = new byte[buffer.capacity()];
        }

        public void fillInt() {
            buffer.clear();

            int i = 0;
            while (buffer.remaining() >= Integer.SIZE / 8) {
                buffer.putInt(i);
                i += 1;
            }
            assert i == buffer.capacity() / (Integer.SIZE / 8);
        }

        public void fillByte() {
            buffer.clear();

            byte i = 0;
            while (buffer.remaining()> 0) {
                buffer.put(i);
                i += 1;
            }
            assert i == (byte) buffer.capacity();

            // This is an alternative implementation: fill the bytes into an array, copy that into
            // the ByteBuffer. This is faster than using byte[] with OutputStream
            // length >= 2048.
            //~ for (int offset = 0; offset < cache.length; offset++) {
                //~ cache[offset] = (byte) offset;
            //~ }
            //~ buffer.clear();
            //~ buffer.put(cache);
        }

        public void fillArray(byte[] source) {
            buffer.clear();

            while (buffer.remaining() >= source.length) {
                buffer.put(source);
            }
            assert buffer.remaining() < source.length;
        }

        public void write(FileOutputStream out) throws IOException {
            buffer.position(0);
            buffer.limit(buffer.capacity());

            int written = out.getChannel().write(buffer);
            assert written == buffer.capacity();
            assert buffer.remaining() == 0;
        }

        public int bufferLength() { return buffer.capacity(); }
    }

    private static abstract class ByteArrayTester implements Tester {
        private final byte[] buffer;

        public ByteArrayTester(byte[] buffer) {
            this.buffer = buffer;
        }

        public void fillInt() {
            int i = 0;
            int offset = 0;
            while (offset < buffer.length) {
                copyIntBytes(i, buffer, offset);
                i += 1;
                offset += 4;
            }
        }

        protected abstract void copyIntBytes(int value, byte[] destination, int offset);

        public void fillByte() {
            byte i = 0;
            int offset = 0;
            while (offset < buffer.length) {
                buffer[offset] = i;
                i += 1;
                offset += 1;
            }
        }

        public void fillArray(byte[] source) {
            int offset = 0;
            final int terminate_limit = buffer.length - source.length;
            while (offset <= terminate_limit) {
                System.arraycopy(source, 0, buffer, offset, source.length);
                offset += source.length;
            }
        }

        public void write(FileOutputStream out) throws IOException {
            out.write(buffer);
        }

        public int bufferLength() { return buffer.length; }
    }

    public static class LEByteArrayTester extends ByteArrayTester {
        public LEByteArrayTester(byte[] buffer) { super(buffer); }

        protected void copyIntBytes(int value, byte[] destination, int offset) {
            destination[offset] = (byte) (value & 0xff);
            destination[offset+1] = (byte) ((value >> 8) & 0xff);
            destination[offset+2] = (byte) ((value >> 16) & 0xff);
            destination[offset+3] = (byte) ((value >> 24) & 0xff);
        }
    }

    public static class BEByteArrayTester extends ByteArrayTester {
        public BEByteArrayTester(byte[] buffer) { super(buffer); }

        protected void copyIntBytes(int value, byte[] destination, int offset) {
            destination[offset] = (byte) ((value >> 24) & 0xff);
            destination[offset+1] = (byte) ((value >> 16) & 0xff);
            destination[offset+2] = (byte) ((value >> 8) & 0xff);
            destination[offset+3] = (byte) (value & 0xff);
        }
    }

    private static final int ITERATIONS = 40000;

    private static final int NUM_TRIALS = 5;

    private static void testFillInt(Tester tester) {
        for (int j = 0; j < NUM_TRIALS; ++j) {
            long start = System.nanoTime();
            for (int k = 0; k < ITERATIONS; ++k) {
                tester.fillInt();
            }
            long end = System.nanoTime();

            System.out.print(throughput(start, end, ITERATIONS, tester.bufferLength()) + " ");
            System.out.flush();
        }
        System.out.println();
    }

    private static void testFillByte(Tester tester) {
        for (int j = 0; j < NUM_TRIALS; ++j) {
            long start = System.nanoTime();
            for (int k = 0; k < ITERATIONS; ++k) {
                tester.fillByte();
            }
            long end = System.nanoTime();

            System.out.print(throughput(start, end, ITERATIONS, tester.bufferLength()) + " ");
            System.out.flush();
        }
        System.out.println();
    }

    private void testFillArray(Tester tester, byte[] source) throws IOException {
        for (int j = 0; j < NUM_TRIALS; ++j) {
            long start = System.nanoTime();
            for (int k = 0; k < ITERATIONS/2; ++k) {
                tester.fillArray(source);
            }
            long end = System.nanoTime();

            System.out.print(throughput(start, end, ITERATIONS, tester.bufferLength()) + " ");
            System.out.flush();
        }
        System.out.println();
    }

    private void testWrite(Tester tester) throws IOException {
        for (int j = 0; j < NUM_TRIALS; ++j) {
            long start = System.nanoTime();
            for (int k = 0; k < ITERATIONS; ++k) {
                tester.write(out);
            }
            long end = System.nanoTime();

            System.out.print(throughput(start, end, ITERATIONS, tester.bufferLength()) + " ");
            System.out.flush();
        }
        System.out.println();
    }

    private void testFillWrite(Tester tester) throws IOException {
        for (int j = 0; j < NUM_TRIALS; ++j) {
            long start = System.nanoTime();
            for (int k = 0; k < ITERATIONS; ++k) {
                tester.fillByte();
                tester.write(out);
            }
            long end = System.nanoTime();

            System.out.print(throughput(start, end, ITERATIONS, tester.bufferLength()) + " ");
            System.out.flush();
        }
        System.out.println();
    }

    public static void error() {
        System.out.println("[heap|direct|array] [be|le] [size]");
    }

    public ByteBufferTest() throws IOException {
        out = new FileOutputStream("/dev/null");
    }

    private FileOutputStream out;
    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            error();
            return;
        }

        int size = Integer.parseInt(args[2]);

        ByteBufferTest test = new ByteBufferTest();

        Tester tester;
        if (args[0].equals("array")) {
            byte[] buffer = new byte[size];
            if (args[1].equals("be")) {
                tester = new BEByteArrayTester(buffer);
            } else if (args[1].equals("le")) {
                tester = new LEByteArrayTester(buffer);
            } else {
                error(); return;
            }
        } else {
            ByteBuffer buffer;
            if (args[0].equals("heap")) {
                buffer = ByteBuffer.allocate(size);
            } else if (args[0].equals("direct")) {
                buffer = ByteBuffer.allocateDirect(size);
            } else {
                error(); return;
            }

            if (args[1].equals("be")) {
                buffer.order(ByteOrder.BIG_ENDIAN);
            } else if (args[1].equals("le")) {
                buffer.order(ByteOrder.LITTLE_ENDIAN);
            } else {
                error(); return;
            }
            tester = new ByteBufferTester(buffer);
        }

        for (int i = 0; i < 5; ++i) {
            System.out.print("write: ");
            test.testWrite(tester);
            System.out.print("fill int: ");
            testFillInt(tester);
            System.out.print("fill byte: ");
            testFillByte(tester);
            System.out.print("fill array 1: ");
            test.testFillArray(tester, new byte[1]);
            System.out.print("fill array 8: ");
            test.testFillArray(tester, new byte[8]);
            System.out.print("fill array 64: ");
            test.testFillArray(tester, new byte[64]);
            System.out.print("fill array 1024: ");
            test.testFillArray(tester, new byte[1024]);
            System.out.print("fill array 4096: ");
            test.testFillArray(tester, new byte[4096]);
            System.out.print("fill write: ");
            test.testFillWrite(tester);

            System.out.println();
        }
    }
}
