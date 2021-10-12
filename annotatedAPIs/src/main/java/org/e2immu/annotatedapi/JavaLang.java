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

package org.e2immu.annotatedapi;

import org.e2immu.annotation.*;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.IntStream;

class JavaLang {

    final static String PACKAGE_NAME = "java.lang";

    // implicitly @Dependent
    @Container
    interface Iterable$<T> {
        void forEach(@NotNull @Independent1 Consumer<? super T> action);

        // implicitly @Dependent, has `remove()`
        @NotNull
        Iterator<T> iterator();

        @NotNull
        @Independent1
        Spliterator<T> spliterator();
    }

    @ERContainer
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

    @E2Container
    interface StackTraceElement$ {
    }

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
    }

    @ERContainer
    interface Class$ {
        @NotNull
        String getCanonicalName();

        @NotNull
        String getName();

        @NotNull
        String getSimpleName();
    }

    @ERContainer
    interface CharSequence$ {
        char charAt(int index);

        default boolean length$Invariant$Len(int l) {
            return l >= 0;
        }

        default void length$Aspect$Len() {
        }

        int length();
    }

    @Independent
    @Container
    interface Appendable$ {
        @Fluent
        Appendable append(char c);

        @Fluent
        Appendable append(CharSequence charSequence);

        @Fluent
        Appendable append(CharSequence charSequence, int start, int end);
    }

    @Independent
    @Container
    interface AutoCloseable$ {

        @Modified
        void close();
    }

    // a class, because we need to annotate the constructor

    @ERContainer
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
    }

    // a class, because we want to annotate the constructor

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
        @Modified
        StringBuilder append(boolean b) {
            return null;
        }

        @Fluent
        @Modified
        StringBuilder append(char c) {
            return null;
        }

        @Fluent
        @Modified
        StringBuilder append(float f) {
            return null;
        }

        @Fluent
        @Modified
        StringBuilder append(long l) {
            return null;
        }

        boolean append$Modification$Len(int post, int prev, int i) {
            return post == prev + Integer.toString(i).length();
        }

        @Fluent
        @Modified
        StringBuilder append(int i) {
            return null;
        }

        @Fluent
        @Modified
        StringBuilder append(char[] chars) {
            return null;
        }

        boolean append$Modification$Len(int post, int prev, String str) {
            return post == prev + (str == null ? 4 : str.length());
        }

        @Fluent
        @Modified
        StringBuilder append(String str) {
            return null;
        }

        @Fluent
        @Modified
        StringBuilder append(Object o) {
            return null;
        }

        int toString$Transfer$Len(int len) {
            return len;
        }

        @Override
        public String toString() {
            return "x";
        }
    }

    @Independent
    @Container
    static abstract class StringBuffer$ implements CharSequence {

    }

    @ERContainer
    interface Integer$ {

        @NotNull
        String toString(int i);
    }


    @ERContainer
    interface Float$ {

        @NotNull
        String toString(float f);
    }

    @ERContainer
    interface Byte$ {

        @NotNull
        String toString(byte b);
    }

    @ERContainer
    interface Character$ {

        @NotNull
        String toString(char c);
    }

    @ERContainer
    interface Short$ {

        @NotNull
        String toString(short s);
    }

    @ERContainer
    interface Boolean$ {
        boolean parseBoolean(@NotNull String string);
    }

    @UtilityClass
    interface Math$ {
        static int max(int a, int b) {
            return 0;
        }
    }

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
                       @NotNull @Modified Object dest,
                       int destPos,
                       int length);
    }

    @ERContainer
    interface Comparable$<T> {
        default int compareTo$Value(T t, int retVal) {
            return equals(t) || t.equals(this) ? 0 : retVal;
        }

        int compareTo(@NotNull T t);
    }

    @ERContainer
    interface Package$ {

    }

    @ERContainer
    interface Module$ {

    }
}
