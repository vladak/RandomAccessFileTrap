package org.trap;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public class RandomAccessFileTrap {

    /**
     * Free the resources related to {@link MappedByteBuffer}, in particular freeing the
     * <a href="https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/nio/Direct-X-Buffer.java.template#L79">mapped memory</a>.
     * This is workaround from <a href="https://stackoverflow.com/a/48821002/11582827">Stackoverflow</a>.
     * The problem with this is that it uses reflection to access a method in {@code sun.misc.Unsafe},
     * however it should not be necessary to call a cleanup method explicitly.
     * @param buffer MappedByteBuffer instance
     * @throws ClassNotFoundException
     * @throws NoSuchFieldException
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    private static void cleanup(MappedByteBuffer buffer)
            throws ClassNotFoundException, NoSuchFieldException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Method invokeCleaner = unsafeClass.getMethod("invokeCleaner", ByteBuffer.class);
        invokeCleaner.invoke(unsafe, buffer);
    }

    /**
     * This method uses {@link RandomAccessFile} and derived {@link FileChannel} to access the contents of a file.
     * Specifically, it uses the {@link RandomAccessFile} with try-with-resources so that it is closed.
     * However, on Windows this does not free all the resources so the file is still considered to be open.
     * When a file is open on Windows by any process, it cannot be deleted. To overcome this limitation,
     * the {@link #cleanup(MappedByteBuffer)} can be called. This works, however it relies on reflection to invoke
     * a cleanup method from {@code sun.misc.Unsafe}. This poses a trap for those who assume that closing
     * the {@link RandomAccessFile} is enough to free all resources associated.
     * @param file File object
     * @param doCleanup whether to use the workaround for closing the resources
     * @return a String representation of the first integer in the file
     * @throws IOException on error
     */
    private static String analyze(File file, boolean doCleanup) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.getAbsolutePath(), "r")) {
            FileChannel fch = raf.getChannel();
            MappedByteBuffer buffer = fch.map(FileChannel.MapMode.READ_ONLY, 0, fch.size());
            String retval = String.valueOf(buffer.getInt());

            if (doCleanup) {
                try {
                    cleanup(buffer);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            return retval;
        }
    }

    public static void main(String[] args) {
        try {
            if (args.length != 2) {
                System.err.println("need: <0|1> <file_path>");
                System.exit(1);
            }
            System.out.println(analyze(new File(args[1]), args[0].equals("1")));
            Files.delete(Path.of(args[0]));
        } catch (Exception e) {
            System.err.println("got exception: " + e);
        }
    }
}