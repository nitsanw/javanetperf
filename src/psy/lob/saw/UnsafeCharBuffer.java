package psy.lob.saw;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.CharBuffer;

import sun.misc.Unsafe;


public class UnsafeCharBuffer {
    private static final Unsafe unsafe;
    private static final long hbOffset;
    private static final long offsetOffset;
    private static final long capacityOffset;
    private static final long markOffset;

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
            hbOffset = unsafe.objectFieldOffset(CharBuffer.class.getDeclaredField("hb"));
            offsetOffset = unsafe.objectFieldOffset(CharBuffer.class.getDeclaredField("offset"));
            capacityOffset = unsafe.objectFieldOffset(Buffer.class.getDeclaredField("capacity"));
            markOffset = unsafe.objectFieldOffset(Buffer.class.getDeclaredField("mark"));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
    public static void wrap(CharBuffer buffy,char[] chars, int offset, int length){
        unsafe.putObject(buffy,hbOffset,chars);
        unsafe.putInt(buffy,offsetOffset,0); // see CharBuffer.wrap doc
        unsafe.putInt(buffy,capacityOffset,chars.length);
        unsafe.putInt(buffy,markOffset,-1);
        buffy.position(offset);
        buffy.limit(offset + length);
    }
}
