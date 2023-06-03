/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.annotatedapi.java;

import org.e2immu.annotation.*;
import org.e2immu.annotation.rare.AllowsInterrupt;

import java.io.File;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.nio.CharBuffer;

public class JavaIo {
    final static String PACKAGE_NAME = "java.io";


    /*
    No methods, so @ImmutableContainer is the only option. The current implementation of the
    analyser requires you to add immutability information.
     */
    @ImmutableContainer(hc = true)
    @Independent
    interface Serializable$ {

    }

    /*
     Note: the type throws IOException rather than Exception (in AutoCloseable).
     */
    @Container
    interface Closeable$ {

        @Modified
        void close();
    }

    /*
     Extending FilterOutputStream, PrintStream cannot be container, cannot be independent.

     The @AllowsInterrupt annotation marks that these methods present the JVM with an opportunity to interrupt
     the current thread, allowing non-final fields to be modified in the background.
     */
    interface PrintStream$ {
        @Modified
        @AllowsInterrupt
        void print(char c);

        @Modified
        @AllowsInterrupt
        void print(boolean b);

        @Modified
        @AllowsInterrupt
        void print(int i);

        @Modified
        @AllowsInterrupt
        void print(float f);

        @Modified
        @AllowsInterrupt
        void print(long l);

        @Modified
        @AllowsInterrupt
        void print(String s);

        @Modified
        @AllowsInterrupt
        void print(/*@Independent(hc=true)*/ Object obj);

        @Modified
        @AllowsInterrupt
        void println();

        @Modified
        @AllowsInterrupt
        void println(char c);

        @Modified
        @AllowsInterrupt
        void println(boolean b);

        @Modified
        @AllowsInterrupt
        void println(int i);

        @Modified
        @AllowsInterrupt
        void println(float f);

        @Modified
        @AllowsInterrupt
        void println(long l);

        @Modified
        @AllowsInterrupt
        void println(String s);

        @Modified
        @AllowsInterrupt
        void println(/*@Independent(hc=true)*/ Object obj);
    }

    /*
     The @Independent annotation prevents the implementation from storing the byte arrays it receives in the
     write methods. The @Container annotation prevents the implementation from modifying them.
     */
    @Independent
    @Container
    interface OutputStream$ {

        @Modified
        void write(@NotNull byte[] b);

        @Modified
        void write(@NotNull byte[] b, int off, int len);

        @Modified
        void flush();

        @Modified
        void write(int b);
    }

    /*
    Example of a non-annotated type, present here for testing (otherwise inspection hasn't taken place).
    See TestCommonJavaIO.testByteArrayOutputStreamToByteArray() and Basics_22
     */
    interface ByteArrayOutputStream$ {

    }

    // obviously not a container, it wraps an object it will modify
    @Independent(absent = true)
    static class FilterOutputStream$ {

        FilterOutputStream$(@Modified @Independent(absent = true) @NotNull OutputStream out) {

        }

    }

    interface BufferedOutputStream$ {

    }

    @Independent
    @Container
    interface Writer$ {
        @Modified
        void write(char[] cbuf);

        @Modified
        void write(char[] cbuf, int off, int len);
    }

    @Independent
    @Container
    interface OutputStreamWriter$ {

    }

    @Independent // because of Closeable, we can't do less
    interface BufferedWriter$ {

    }

    /*
     The @Independent here implies that the StringWriter will not keep a copy of the CharSequence it has to append.
     The @Container enforces that the char arrays in the parameters are not modified.
     */
    @Independent
    @Container
    interface StringWriter$ {
        @Modified
        void append(char c);

        @Modified
        void append(CharSequence csq);

        @Modified
        void write(char[] cbuf);

        @Modified
        void write(char[] cbuf, int off, int len);
    }

    /*
     The @Independent here implies that the Reader will not keep a dependent copy of the CharBuffer or Writer.
     */
    @Independent
    interface Reader$ {
        @Modified
        int read(@Modified CharBuffer target);

        @Modified
        long transferTo(@Modified Writer out);
    }

    @Independent
    interface StringReader$ {

    }

    /*
    The @Independent here implies that the InputStream will not keep a dependent copy of the CharBuffer or Writer.
    This type is obviously not a @Container, the 'transferTo' method modifies its arguments.
    */
    @Independent
    interface InputStream$ {
        @Modified
        long transferTo(@Modified OutputStream out);
    }

    @Independent
    interface FilterInputStream$ {

    }

    @Independent
    interface BufferedInputStream${

    }

    /*
    .exists() .delete() .exists(), when true first, must return false after the removal.
    Cannot be independent because getCanonicalFile() can return a File which "points to the same underlying file",
    which has implications for .exists(), .delete()
     */
    @Container
    interface File$ {
        boolean canRead();

        boolean exists();

        File getCanonicalFile();

        @NotNull
        String getName();

        @NotNull
        String getPath();

        @NotNull
        URI toURI();

        @NotNull
        URL toURL();

        @Modified
        boolean createNewFile();

        @Modified
        boolean delete();

        @Modified
        void deleteOnExit();
    }

    @Container
    @Independent
    interface ObjectStreamField$ {

        @Modified
        void setOffset(int offset);
    }

    @Container
    @Independent
    interface FileFilter$ {

    }

    @Container
    @Independent
    interface FilenameFilter$ {

    }
}
