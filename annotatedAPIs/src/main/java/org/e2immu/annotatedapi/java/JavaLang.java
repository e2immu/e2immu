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
import org.e2immu.annotation.Commutable;
import org.e2immu.annotation.rare.IgnoreModifications;
import org.e2immu.annotation.type.UtilityClass;

import java.io.PrintStream;
import java.nio.CharBuffer;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/*
Important: for efficiency reasons, the analysis of AnnotatedAPI types proceeds in the order type - fields - methods.
Annotations on the type need to be given -- the analyser does not compute them from the methods.
 */
class JavaLang {

    final static String PACKAGE_NAME = "java.lang";

    /*
     Implicitly @Dependent, because the iterator can change the iterable via the remove() method.
     */
    @Container
    interface Iterable$<T> {
        /*
         Because action is of functional interface type in java.util.function, it is @IgnoreModifications by default.
         The forEach method communicates hidden content to the outside world.
         Finally, we do not allow action to be null, but obviously action's single abstract method (accept()) is allowed
         to return null values.
         */
        @NotModified
        void forEach(@NotNull @Independent(hc = true) Consumer<? super T> action);

        // implicitly @Dependent, has `remove()`
        @NotNull
        Iterator<T> iterator();

        /*
        The spliterator cannot change the iterable, but it does communicate hidden content.
         */
        @NotNull
        @Independent(hc = true)
        Spliterator<T> spliterator();
    }

    /*
    The archetype for a non-abstract type with hidden content.
     */
    @ImmutableContainer(hc = true)
    @Independent
    interface Object$ {
        @NotNull
        Object clone();

        default boolean equals$Value(Object object, boolean retVal) {
            return object != null && (this == object || retVal);
        }

        default boolean equals$Invariant(Object object) {
            return (this.equals(object)) == object.equals(this);
        }

        // @NotModified implicit on method and parameter
        boolean equals(Object object);

        // final, cannot override to add annotations, so added a $ as a general convention that you can drop that at the end??
        @NotNull
        Class<?> getClass$();

        @NotNull
        String toString();
    }

    /*
    The analyser adds hc=true for an interface.
     */
    @ImmutableContainer
    @Independent
    interface Enum$<X extends Enum<X>> {

        int compareTo(@NotNull X x);
    }

    @ImmutableContainer
    interface StackTraceElement$ {
    }

    /*
     Not a container, modifying, dependent.
     */
    interface Throwable$ {

        String getMessage();

        String getLocalizedMessage();

        Throwable getCause();

        @Modified
        @Fluent
        Throwable initCause(Throwable cause);

        @Modified
        void addSuppressed(@NotNull Throwable exception);

        @Modified
        @Fluent
        Throwable fillInStackTrace();

        @NotNull
        StackTraceElement[] getStackTrace();

        @Modified
        void setStackTrace(@NotNull StackTraceElement[] stackTrace);

        void printStackTrace(@Modified PrintStream s);
    }

    /*
    Deeply immutable, no hidden content.
     */
    @ImmutableContainer
    interface Class$ {
        @NotNull
        String getCanonicalName();

        @NotNull
        String getName();

        @NotNull
        String getSimpleName();

        /*
         Technically, a class loader cannot be immutable: the load method changes its content.
         Semantically, to the application, this does not matter.
         */
        @NotNull
        ClassLoader getClassLoader();
    }

    @ImmutableContainer
    interface ClassLoader$ {
        @NotNull
        Module getUnnamedModule();

        @Independent
        ClassLoader getPlatformClassLoader();

        @Independent
        ClassLoader getSystemClassLoader();
    }

    /*
     implicitly, hc=true for immutable; however, the independent does not get this hc=true automatically;
     the type is independent, even if there is a subsequence() method
     */
    @ImmutableContainer
    @Independent
    interface CharSequence$ {
        char charAt(int index);

        default boolean length$Invariant$Len(int l) {
            return l >= 0;
        }

        default void length$Aspect$Len() {
        }

        int length();
    }

    /*
     @Container and @Independent implicit, all parameters are immutable.
     As explained in PrintStream, we disallow implementations of this interface to store the CharSequences,
     and force them to copy the chars in their own sequence.
     Override this choice by uncommenting the @Independent(hc=true).
     */
    @Independent
    interface Appendable$ {
        @Fluent
        Appendable append(char c);

        @Fluent
        Appendable append(/*@Independent(hc=true)*/CharSequence charSequence);

        @Fluent
        Appendable append(/*@Independent(hc=true)*/CharSequence charSequence, int start, int end);
    }

    @Container
    interface AutoCloseable$ {

        @Modified
        void close();
    }

    /*
    Deeply immutable, no hidden content.

    Note that we use a class instead of an interface to annotate, because we need to annotate a constructor.
     */
    @ImmutableContainer
    static abstract class String$ implements CharSequence {

        boolean String$Modification$Len(int post) {
            return post == 0;
        }

        String$() {
        }

        public char charAt(int index) {
            return 0;
        }

        int chars$Transfer$Len(int len) {
            return len;
        }

        @NotNull
        public IntStream chars() {
            return null;
        }

        @NotNull
        public IntStream codePoints() {
            return null;
        }

        int concat$Transfer$Len(int len, String str) {
            return str.length() + len;
        }

        boolean concat$Postcondition(String str) {
            return startsWith(this) && endsWith(str) && contains(this) && contains(str);
        }

        @NotNull
        String concat(@NotNull String str) {
            return null;
        }

        boolean contains$Value$Len(int len, CharSequence s, boolean retVal) {
            return s.length() == 0 || (s.length() <= len && retVal);
        }

        boolean contains(CharSequence s) {
            return true;
        }

        boolean endsWith$Value$Len(int len, String s, boolean retVal) {
            return s.length() == 0 || (s.length() <= len && retVal);
        }

        boolean endsWith(String suffix) {
            return false;
        }

        @NotNull
            // could have been @NN1
        byte[] getBytes() {
            return null;
        }

        boolean isEmpty$Value$Len(int l) {
            return l == 0;
        }

        public boolean isEmpty() {
            return false;
        }

        int indexOf(int ch) {
            return 0;
        }

        int intern$Transfer$Len(int len) {
            return len;
        }

        // TODO try to write that the result equals this
        @NotNull
        String intern() {
            return null;
        }

        @NotNull
        static String join(CharSequence delimiter, CharSequence... elements) {
            return null;
        }

        int lastIndexOf(int ch) {
            return 0;
        }

        int repeat$Transfer$Len(int len, int count) {
            return len * count;
        }

        @NotNull
        String repeat(int count) {
            return null;
        }

        @NotNull
        String replaceAll(String regex, String replacement) {
            return null;
        }

        @NotNull
        String[] split(String regex) {
            return null;
        }

        boolean startsWith$Value$Len(int len, String s, boolean retVal) {
            return s.length() <= len && retVal;
        }

        boolean startsWith(@NotNull String$ s) {
            return true;
        } // we use the $ version because of post-condition in concat

        boolean startsWith$Value$Len(int len, String s, int i, boolean retVal) {
            return s.length() + i <= len && retVal;
        }

        boolean startsWith(@NotNull String s, int i) {
            return true;
        }

        boolean substring$Precondition$Len(int len, int beginIndex) {
            return beginIndex < len;
        }

        int substring$Transfer$Len(int len, int beginIndex) {
            return len - beginIndex;
        }

        @NotNull
        String substring(int beginIndex) {
            return null;
        }

        boolean substring$Precondition$Len(int len, int beginIndex, int endIndex) {
            return endIndex < len && beginIndex <= endIndex;
        }

        int substring$Transfer$Len(int len, int beginIndex, int endIndex) {
            return endIndex - beginIndex;
        }

        @NotNull
        String substring(int beginIndex, int endIndex) {
            return null;
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return null;
        }

        int toLowerCase$Transfer$Len(int len) {
            return len;
        }

        @NotNull
        String toLowerCase() {
            return null;
        }

        int toUpperCase$Transfer$Len(int len) {
            return len;
        }

        @NotNull
        String toUpperCase() {
            return null;
        }

        @NotNull
        String trim() {
            return null;
        }

        @NotNull
        String strip() {
            return null;
        }

        @NotNull
        char[] toCharArray(){ return null; }
    }

    /*
    @Independent forces the string representation to be stored, rather than the object itself in append(Object).
     */
    @Independent
    @Container
    static abstract class StringBuilder$ implements CharSequence {

        boolean StringBuilder$Modification$Len(int post) {
            return post == 0;
        }

        StringBuilder$() {
        }

        boolean StringBuilder$Modification$Len(int post, String string) {
            return post == string.length();
        }

        StringBuilder$(@NotNull String string) {
        }

        @Fluent
        StringBuilder append(boolean b) {
            return null;
        }

        @Fluent
        StringBuilder append(char c) {
            return null;
        }

        @Fluent
        StringBuilder append(float f) {
            return null;
        }

        @Fluent
        StringBuilder append(long l) {
            return null;
        }

        boolean append$Modification$Len(int post, int prev, int i) {
            return post == prev + Integer.toString(i).length();
        }

        @Fluent
        StringBuilder append(int i) {
            return null;
        }

        @Fluent
        StringBuilder append(char[] chars) {
            return null;
        }

        boolean append$Modification$Len(int post, int prev, String str) {
            return post == prev + (str == null ? 4 : str.length());
        }

        @Fluent
        StringBuilder append(String str) {
            return null;
        }

        @Fluent
        StringBuilder append(Object o) {
            return null;
        }

        @Fluent
        StringBuilder append(char[] buffer, int i, int j) {
            return null;
        }

        @Fluent
        StringBuilder delete(int i, int j) { return null; }

        @Fluent
        StringBuilder reverse() { return null; }

        int toString$Transfer$Len(int len) {
            return len;
        }

        @Override
        public String toString() {
            return "x";
        }
    }

    /*
     See StringBuilder for an explanation of the annotations.
     TODO expand!
     */
    @Independent
    @Container
    static abstract class StringBuffer$ implements CharSequence {

    }

    /*
    no hidden content, deeply immutable.
     */
    @ImmutableContainer
    interface Integer$ {

        @NotNull
        String toString(int i);
    }

    /*
    no hidden content, deeply immutable.
     */
    @ImmutableContainer
    interface Long$ {

        @NotNull
        String toString(long l);
    }

    @ImmutableContainer
    @Independent
    interface Number$ {

    }


    @ImmutableContainer
    interface Float$ {

        @NotNull
        String toString(float f);
    }

    @ImmutableContainer
    interface Byte$ {

        @NotNull
        String toString(byte b);
    }

    @ImmutableContainer
    interface Character$ {

        @NotNull
        String toString(char c);

        @ImmutableContainer
        interface Subset {

        }
    }

    @ImmutableContainer
    interface Short$ {

        @NotNull
        String toString(short s);
    }

    @ImmutableContainer
    interface Boolean$ {
        boolean parseBoolean(@NotNull String string);
    }

    @UtilityClass
    interface Math$ {
        static int max(@Commutable int a, @Commutable int b) {
            return 0;
        }

        static int max(@Commutable float a, @Commutable float b) {
            return 0;
        }

        static int max(@Commutable double a, @Commutable double b) {
            return 0;
        }

        static int max(@Commutable long a, @Commutable long b) {
            return 0;
        }

        static int min(@Commutable int a, @Commutable int b) {
            return 0;
        }

        static int min(@Commutable float a, @Commutable float b) {
            return 0;
        }

        static int min(@Commutable double a, @Commutable double b) {
            return 0;
        }

        static int min(@Commutable long a, @Commutable long b) {
            return 0;
        }
    }

    /*
     Cannot be @Container, see arraycopy.
     Modifications to System are outside the scope of the semantics of the application: the output and error streams
     have @IgnoreModifications.
     */
    @UtilityClass
    interface System$ {
        @IgnoreModifications
        @NotNull
        PrintStream out = null;

        @NotNull
        @IgnoreModifications
        PrintStream err = null;

        void arraycopy(@NotNull @NotModified Object src,
                       int srcPos,
                       @NotNull @Modified @Independent(hcParameters = {0}) Object dest,
                       int destPos,
                       int length);

        @Independent
        @Container
        interface Logger {

        }
    }

    /*
    hc=true automatically added.
     */
    @ImmutableContainer
    @Independent // implementations will NOT store the object being compared to
    interface Comparable$<T> {
        default int compareTo$Value(T t, int retVal) {
            return equals(t) || t.equals(this) ? 0 : retVal;
        }

        int compareTo(@NotNull T t);
    }

    /*
     Note: even though it is not formally "final", we'll treat it as such, and make this class deeply immutable.
     */
    @ImmutableContainer
    interface Package$ {

    }

    /*
     Deeply immutable class.
     */
    @ImmutableContainer
    interface Module$ {

    }

    @Container
    interface ProcessHandle$ {

        @Modified
        boolean destroy();

        boolean isAlive();

        @ImmutableContainer // no hidden content
        interface Info {

        }
    }

    @Container
    @Independent(hc = true)
    interface ClassValue$<T> {
        @Modified
        T computeValue(Class<?> type);

        T get(Class<?> type);

        @Modified
        void remove(Class<?> type);
    }

    /*
    the implementation of Readable will not store the CharBuffer in its fields, nor link to it
     */
    @Independent
    interface Readable$ {
        @Modified
        int read(@NotNull @Modified CharBuffer cb);
    }

    @ImmutableContainer
    @Independent
    interface Record$ {

    }

    interface Thread$ {

        interface UncaughtExceptionHandler {
            @Modified
            void uncaughtException(Thread t, Throwable e);
        }

        @Modified
        void setName(String name);

        int enumerate(@Modified Thread[] tArray);
    }

    @Independent
    interface Runnable$ {
        @Modified
        void run();
    }
}
