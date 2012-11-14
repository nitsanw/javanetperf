package edu.mit.net;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.nio.CharBuffer;

public class ReflectionUtils {
    /**
     * There is no explicit Modifier constant for package-private, so 0 is used.
     */
    public static final int MODIFIER_PACKAGE_PRIVATE = 0x00000000;

    /** Field object for accessing Sting::value character storage. */
    public static final Field STRING_VALUE_FIELD = getFieldAccessible(
            String.class, "value");

    /**
     * Field object for accessing Sting::offset, the first index used in the
     * value[] char storage.
     */
    public static final Field STRING_OFFSET_FIELD = getFieldAccessible(
            String.class, "offset");

    /**
     * Package private String constructor which shares value array for speed.
     * 
     * Use when a number of String objects share the same char[] storage.
     * 
     * See String(int offset, int count, char value[]).
     */
    public static final Constructor<?> STRING_PP_CTOR = getConstructor(
            String.class, MODIFIER_PACKAGE_PRIVATE, int.class, int.class,
            char[].class);

    /**
     * To avoid violating final field semantics, take care to only _read_ the
     * char[] value returned.
     */
    public static char[] getChars(final String s) {
        try {
            // use reflection to read the char[] value from the string. . .
            return (char[]) STRING_VALUE_FIELD.get(s);
        } catch (Throwable t) {
            return null;
        }
    }

    public static CharBuffer getCharBuffer(final String s) {
        char[] value;
        try {
            value = (char[]) STRING_VALUE_FIELD.get(s);
            int offset = STRING_OFFSET_FIELD.getInt(s);
            CharBuffer buffer = CharBuffer.wrap(value);
            buffer.position(offset);
            assert buffer.remaining() == s.length();
            return buffer;
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static String sharedString(final char[] chars, final int offset,
            final int length) {
        try {
            return (String) STRING_PP_CTOR.newInstance(offset, length, chars);
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return null;
    }

    public static Field getFieldAccessible(final Class<?> clazz,
            final String fieldName) {
        Field fld = null;
        try {
            fld = clazz.getDeclaredField(fieldName);
            fld.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (SecurityException e) {
            throw new RuntimeException(e);
        }
        return fld;
    }

    public static Constructor<?> getConstructor(final Class<?> clazz,
            final int searchModifier, final Class<?>... paramTypes) {

        if (clazz == null) {
            throw new IllegalArgumentException("A class parameter is required");
        }

        try {
            //
            // There is no explicit Modifier accessor constant for
            // package-private, so 0 is used.
            //

            for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {

                if (searchModifier == (ctor.getModifiers() & (Modifier.PUBLIC
                        | Modifier.PRIVATE | Modifier.PROTECTED))) {
                    //
                    // access modifier matches. . .
                    //
                    final Class<?>[] parameterTypes = ctor.getParameterTypes();
                    if (parameterTypes.length == paramTypes.length) {
                        //
                        // same number of parameters. . .
                        //
                        for (int i = 0; i < parameterTypes.length; i++) {
                            if (!parameterTypes[i].equals(paramTypes[i])) {
                                // one parameter not of correct type, so bail. .
                                // .
                                // note ctor variable used as success marker
                                // below
                                ctor = null;
                                break;
                            } else {
                                // Type[] gpType =
                                // ctor.getGenericParameterTypes();
                                // for (int j = 0; j < gpType.length; j++) {
                                // char ch = (gpType[j].equals(paramTypes[i]) ?
                                // '*' : ' ');
                                // System.out.format("%7c%s[%d]: %s%n", ch,
                                // "GenericParameterType", j, gpType[j]);
                                // }
                            }
                        }
                        if (ctor != null) {
                            // all ctor parameter types match, so call
                            // ctor.setAccessible(true)
                            // System.out.format("%s%n",
                            // ctor.toGenericString());
                            // System.out.format("  [ synthetic=%-5b var_args=%-5b ]%n",
                            // ctor.isSynthetic(), ctor.isVarArgs());

                            ctor.setAccessible(true);
                            return ctor;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // will return null below
        }
        return null; // error or ctor not found
    }

    /**
     * The AccessibleObject class is the base class for Field, Method and
     * Constructor objects. It provides the ability to flag a reflected object
     * as suppressing default Java language access control checks when it is
     * used. The access checks--for public, default (package) access, protected,
     * and private members--are performed when Fields, Methods or Constructors
     * are used to set or get fields, to invoke methods, or to create and
     * initialize new instances of classes, respectively.
     * 
     * <p>
     * Setting the <tt>accessible</tt> flag in a reflected object permits
     * sophisticated applications with sufficient privilege, such as Java Object
     * Serialization or other persistence mechanisms, to manipulate objects in a
     * manner that would normally be prohibited.
     * 
     * @see AccessibleObject
     * 
     **/
    public static boolean setAccessible(final AccessibleObject o) {
        try {
            o.setAccessible(true);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

}
