import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.ArrayList;

import psy.lob.saw.CustomUtf8Encoder;
import psy.lob.saw.UnsafeString;

public class StringEncodingTest {
    private final static boolean DIRECT_BB = false;

    private interface UTF8Encoder {
	/** Assumes destination is large enough to hold encoded source. */
	int encode(String source);

	byte[] encodeToArray(String source);

	ByteBuffer encodeToNewBuffer(String source);

	UTF8Encoder newInstance();

	byte[] getBytes(int bytes);
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static abstract class UTF8CharsetEncoder implements UTF8Encoder {
	private final byte[] destination;
	protected final ByteBuffer wrapper;
	protected final CharsetEncoder encoder;

	protected UTF8CharsetEncoder(byte[] destination) {
	    this(destination, UTF8.newEncoder());
	}

	protected UTF8CharsetEncoder(byte[] destination, CharsetEncoder encoder) {
	    this.destination = destination;
	    if (DIRECT_BB)
		wrapper = ByteBuffer.allocateDirect(destination.length);
	    else
		wrapper = ByteBuffer.wrap(destination);
	    this.encoder = encoder;
	}

	public byte[] encodeToArray(String source) {
	    int length = encode(source);
	    byte[] out = new byte[length];
	    System.arraycopy(destination, 0, out, 0, length);
	    return out;
	}

	public ByteBuffer encodeToNewBuffer(String source) {
	    return ByteBuffer.wrap(encodeToArray(source));
	}
	public byte[] getBytes(int bytes){
	    byte[] dst = new byte[bytes];
	    wrapper.flip();
	    wrapper.get(dst);
	    return dst;
	}
    }

    private static final class DirectEncoder extends UTF8CharsetEncoder {
	public DirectEncoder(byte[] destination) {
	    super(destination);
	}

	public int encode(String source) {
	    wrapper.clear();
	    encoder.reset();

	    CharBuffer in = CharBuffer.wrap(source);
	    CoderResult result = encoder.encode(in, wrapper, true);
	    assert result == CoderResult.UNDERFLOW;
	    result = encoder.flush(wrapper);
	    assert result == CoderResult.UNDERFLOW;

	    return wrapper.position();
	}

	public DirectEncoder newInstance() {
	    return new DirectEncoder(wrapper.array());
	}
    }

    private static final class CharBufferCopyEncoder extends UTF8CharsetEncoder {
	private final CharBuffer tempWrapper = CharBuffer.allocate(1024);
	private final char[] tempChars = tempWrapper.array();

	public CharBufferCopyEncoder(byte[] destination) {
	    super(destination);
	}

	public int encode(String source) {
	    wrapper.clear();
	    encoder.reset();

	    // ~ final CharBuffer tempWrapper = CharBuffer.allocate(1024);
	    // ~ final char[] tempChars = tempWrapper.array();

	    int readOffset = 0;
	    boolean done = false;
	    while (!done) {
		int readLength = source.length() - readOffset;
		if (readLength > tempChars.length) {
		    readLength = tempChars.length;
		}

		// Copy the chunk into our temporary buffer
		source.getChars(0, readLength, tempChars, 0);
		tempWrapper.clear();
		tempWrapper.limit(readLength);
		readOffset += readLength;

		done = readOffset == source.length();
		CoderResult result = encoder.encode(tempWrapper, wrapper, done);
		assert result == CoderResult.UNDERFLOW;
	    }
	    CoderResult result = encoder.flush(wrapper);
	    assert result == CoderResult.UNDERFLOW;

	    return wrapper.position();
	}

	public CharBufferCopyEncoder newInstance() {
	    return new CharBufferCopyEncoder(wrapper.array());
	}
    }

    private static final class CharBufferCopyEncoder2 extends
	    UTF8CharsetEncoder {
	private final CharBuffer tempWrapper = CharBuffer.allocate(1024);
	private final char[] tempChars = tempWrapper.array();

	public CharBufferCopyEncoder2(byte[] destination) {
	    super(destination);
	}

	public int encode(String source) {
	    wrapper.clear();
	    encoder.reset();

	    // ~ final CharBuffer tempWrapper = CharBuffer.allocate(1024);
	    // ~ final char[] tempChars = tempWrapper.array();

	    // Copy the chunk into our temporary buffer
	    source.getChars(0, source.length(), tempChars, 0);
	    tempWrapper.clear();
	    tempWrapper.limit(source.length());

	    CoderResult result = encoder.encode(tempWrapper, wrapper, true);
	    assert result == CoderResult.UNDERFLOW;

	    return wrapper.position();
	}

	public CharBufferCopyEncoder2 newInstance() {
	    return new CharBufferCopyEncoder2(wrapper.array());
	}
    }

    private static final class CharBufferUnsafeEncoder extends
	    UTF8CharsetEncoder {
	public CharBufferUnsafeEncoder(byte[] destination) {
	    super(destination);
	}

	public int encode(String source) {
	    wrapper.clear();
	    encoder.reset();
	    CoderResult result = encoder.encode(
		    UnsafeString.getStringAsCharBuffer(source), wrapper, true);
	    assert result == CoderResult.UNDERFLOW;
	    return wrapper.position();
	}

	public CharBufferUnsafeEncoder newInstance() {
	    return new CharBufferUnsafeEncoder(wrapper.array());
	}
    }

    private static final class CharBufferUnsafeEncoder2 extends
	    UTF8CharsetEncoder {
	private final CharBuffer tempWrapper = CharBuffer.allocate(0);

	public CharBufferUnsafeEncoder2(byte[] destination) {
	    super(destination);
	}

	public int encode(String source) {
	    UnsafeString.wrapStringWithCharBuffer(source, tempWrapper);
	    wrapper.clear();
	    encoder.reset();
	    CoderResult result = encoder.encode(tempWrapper, wrapper, true);
	    assert result == CoderResult.UNDERFLOW;
	    return wrapper.position();
	}

	public CharBufferUnsafeEncoder2 newInstance() {
	    return new CharBufferUnsafeEncoder2(wrapper.array());
	}
    }

    private static final class CharBufferUnsafeEncoder3 extends
	    UTF8CharsetEncoder {
	private CustomUtf8Encoder customEncoder = new CustomUtf8Encoder();

	public CharBufferUnsafeEncoder3(byte[] destination) {
	    super(destination);
	}

	public int encode(String source) {
	    wrapper.clear();
	    CoderResult result = customEncoder.encodeString(source, wrapper);
	    assert result == CoderResult.UNDERFLOW;
	    return wrapper.position();
	}

	public CharBufferUnsafeEncoder3 newInstance() {
	    return new CharBufferUnsafeEncoder3(wrapper.array());
	}
    }

    private static final class StringEncoder implements UTF8Encoder {
	private final byte[] destination;
	private final ByteBuffer wrapper;
	public StringEncoder(byte[] destination) {
	    this.destination = destination;
	    if(DIRECT_BB){
		wrapper = ByteBuffer.allocateDirect(destination.length);
	    }
	    else{
		wrapper = ByteBuffer.wrap(destination);
	    }
	}

	public int encode(String source) {
	    byte[] array = encodeToArray(source);
	    if(DIRECT_BB){
		wrapper.clear();
		wrapper.put(array);
	    }
	    else{
		System.arraycopy(array, 0, destination, 0, array.length);
		wrapper.position(array.length);
	    }
	    return array.length;
	}

	public byte[] encodeToArray(String source) {
	    try {
		return source.getBytes("UTF-8");
	    } catch (UnsupportedEncodingException e) {
		throw new RuntimeException(e);
	    }
	}

	public ByteBuffer encodeToNewBuffer(String source) {
	    return ByteBuffer.wrap(encodeToArray(source));
	}

	public StringEncoder newInstance() {
	    return new StringEncoder(destination);
	}
	public byte[] getBytes(int bytes){
	    byte[] dst = new byte[bytes];
	    System.arraycopy(destination, 0, dst, 0, bytes);
	    return dst;
	}
    }

    // private static final class StringEncoder2 implements UTF8Encoder {
    // private final byte[] destination;

    // public StringEncoder2(byte[] destination) {
    // this.destination = destination;
    // }
    //
    // public int encode(String source) {
    // byte[] array = encodeToArray(source);
    // System.arraycopy(array, 0, destination, 0, array.length);
    // return array.length;
    // }
    //
    // public byte[] encodeToArray(String source) {
    // return source.getBytes(UTF8);
    // }
    //
    // public ByteBuffer encodeToNewBuffer(String source) {
    // return ByteBuffer.wrap(encodeToArray(source));
    // }
    //
    // public StringEncoder2 newInstance() {
    // return new StringEncoder2(destination);
    // }
    // }

    private static final class CustomEncoder implements UTF8Encoder {
	private final ByteBuffer destination;
	private final edu.mit.net.StringEncoder encoder = new edu.mit.net.StringEncoder();

	public CustomEncoder(byte[] destination) {
	    this.destination = ByteBuffer.wrap(destination);
	}

	public int encode(String source) {
	    destination.clear();
	    boolean result = encoder.encode(source, destination);
	    assert result;
	    return destination.position();
	}

	public byte[] encodeToArray(String source) {
	    return encoder.toNewArray(source);
	}

	public ByteBuffer encodeToNewBuffer(String source) {
	    return encoder.toNewByteBuffer(source);
	}

	public CustomEncoder newInstance() {
	    return new CustomEncoder(destination.array());
	}
	public byte[] getBytes(int bytes){
	    byte[] dst = new byte[bytes];
	    destination.get(dst);
	    return dst;
	}
    }

    private static void error() {
	System.err
		.println("(bytebuffer|string|chars|custom|unsafe|unsafe2) (once|reuse) (buffer|array|bytebuffer) (input strings)");
	System.exit(1);
    }

    private static enum OutputMode {
	ARRAY, REUSE_BUFFER, NEW_BYTEBUFFER,
    }

    public static void main(String[] args) throws IOException {
	if (args.length != 4) {
	    error();
	    return;
	}

	byte[] destination = new byte[4096];

	UTF8Encoder encoder;
	if (args[0].equals("bytebuffer")) {
	    encoder = new DirectEncoder(destination);
	} else if (args[0].equals("string")) {
	    encoder = new StringEncoder(destination);
	}
	// else if (args[0].equals("string2")) {
	// encoder = new StringEncoder2(destination);
	// }
	else if (args[0].equals("chars")) {
	    encoder = new CharBufferCopyEncoder(destination);
	} else if (args[0].equals("chars2")) {
	    encoder = new CharBufferCopyEncoder2(destination);
	}
	else if (args[0].equals("custom")) {
	    encoder = new CustomEncoder(destination);
	} else if (args[0].equals("unsafe")) {
	    encoder = new CharBufferUnsafeEncoder(destination);
	} else if (args[0].equals("unsafe2")) {
	    encoder = new CharBufferUnsafeEncoder2(destination);
	} else if (args[0].equals("unsafe3")) {
	    encoder = new CharBufferUnsafeEncoder3(destination);
	} else {
	    error();
	    return;
	}

	boolean reuseEncoder = true;
	if (args[1].equals("once")) {
	    reuseEncoder = false;
	} else if (!args[1].equals("reuse")) {
	    error();
	    return;
	}

	OutputMode outputMode;
	if (args[2].equals("array")) {
	    outputMode = OutputMode.ARRAY;
	} else if (args[2].equals("buffer")) {
	    outputMode = OutputMode.REUSE_BUFFER;
	} else if (args[2].equals("bytebuffer")) {
	    outputMode = OutputMode.NEW_BYTEBUFFER;
	} else {
	    error();
	    return;
	}

	ArrayList<String> strings = new ArrayList<String>();
	BufferedReader reader = new BufferedReader(new InputStreamReader(
		new FileInputStream(args[3]), "UTF-8"));
	String line;
	while ((line = reader.readLine()) != null) {
	    strings.add(line);
	}

	// our main interest here is the encoding of the full set of strings
	// I split the loops into separate methos to let the JIT do it's work
	// more easily. Not made much of a difference to results, but I prefer
	// it this way anyways...
	final int ITERATIONS = 1000;
	for (int j = 0; j < 50; ++j) {
	    long start = System.nanoTime();
	    testLoop(destination, encoder, reuseEncoder, outputMode, strings,
		    ITERATIONS);
	    long end = System.nanoTime();
	    System.out.println(((double) end - start) / 1000000. + " millis");
	    System.gc();
	}
    }

    private static void testLoop(byte[] destination, UTF8Encoder encoder,
	    boolean reuseEncoder, OutputMode outputMode,
	    ArrayList<String> strings, final int ITERATIONS)
	    throws UnsupportedEncodingException {
	for (int i = 0; i < ITERATIONS; ++i) {
	    encodeLoop(destination, encoder, reuseEncoder, outputMode, strings);
	}
    }

    private static void encodeLoop(byte[] destination, UTF8Encoder encoder,
	    boolean reuseEncoder, OutputMode outputMode,
	    ArrayList<String> strings) throws UnsupportedEncodingException {
	for (String value : strings) {
	    encodeAction(destination, encoder, reuseEncoder, outputMode, value);

	}
    }

    private static void encodeAction(byte[] destination, UTF8Encoder encoder,
	    boolean reuseEncoder, OutputMode outputMode, String value)
	    throws UnsupportedEncodingException {
	UTF8Encoder temp = encoder;
	if (!reuseEncoder) {
	    temp = encoder.newInstance();
	}

	if (outputMode == OutputMode.REUSE_BUFFER) {
	    int bytes = temp.encode(value);
	    // This did not work for direct buffers in the original and also exposed
	    // a slight bias towards the string implementation as it didn't bother
	    // bringing the buffer to the right position. 
	    assert compareToOriginal(encoder.getBytes(bytes), value, bytes);
	} else if (outputMode == OutputMode.ARRAY) {
	    byte[] out = temp.encodeToArray(value);
	    assert new String(out, "UTF-8").equals(value);
	} else {
	    assert outputMode == OutputMode.NEW_BYTEBUFFER;
	    ByteBuffer out = temp.encodeToNewBuffer(value);
	    assert new String(out.array(), 0, out.remaining(), "UTF-8")
		    .equals(value);
	}
    }

    private static boolean compareToOriginal(byte[] destination, String value,
	    int bytes) throws UnsupportedEncodingException {
	return new String(destination, 0, bytes, "UTF-8").equals(value);
    }
}
