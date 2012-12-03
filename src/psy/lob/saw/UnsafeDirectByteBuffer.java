package psy.lob.saw;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import sun.misc.Unsafe;


public class UnsafeDirectByteBuffer {
    private static final Unsafe unsafe;
    private static final long addressOffset;

    static
    {
        try
        {
            // This is a bit of voodoo to force the unsafe object into visibility and acquire it.
            // This is not playing nice, but as an established back door it is not likely to be
            // taken away.
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe)field.get(null);
            addressOffset = unsafe.objectFieldOffset(Buffer.class.getDeclaredField("address"));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
    public static long getAddress(ByteBuffer buffy){
	return unsafe.getLong(buffy, addressOffset);
    }
    /**
     * put byte and skip position update and boundary checks
     * @param buffy
     * @param b
     */
    public static void putByte(long address,int position,byte b){
        unsafe.putByte(address + (position << 0),b);
    }
}
