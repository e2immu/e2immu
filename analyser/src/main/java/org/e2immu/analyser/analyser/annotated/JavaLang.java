/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.analyser.annotated;

import org.e2immu.annotation.*;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.IntStream;

class JavaLang {

    @E2Container
    static class JavaLangObject {
        @NotNull
        protected Object clone() { return null; }

        boolean equals$Value(Object object, boolean retVal) { return object != null && (this == object || retVal); }
        boolean equals$Invariant(Object object) { return (this.equals(object)) == object.equals(this); }
        public boolean equals(@NotModified Object object) { return true; }

        // final, cannot override to add annotations, so added a $ as a general convention that you can drop that at the end??
        @NotNull
        public Class<?> getClass$() { return null; }

        @NotNull
        public String toString() { return "x"; }
    }


    @E2Container
    static class JavaLangStackTraceElement {}

    static class JavaLangThrowable {

        @NotModified
        JavaLangString getMessage() { return null; }

        @NotModified
        JavaLangString getLocalizedMessage() { return null; }

        @NotModified
        java.lang.Throwable getCause() { return null; }

        @Fluent
        java.lang.Throwable initCause(java.lang.Throwable cause) { return null; }

        void setStackTrace(@NotNull java.lang.StackTraceElement[] stackTrace) { }

        void addSuppressed(@NotNull java.lang.Throwable exception) { }

        @Fluent
        java.lang.Throwable fillInStackTrace() { return null; }
    }


    @E2Container
    static class JavaLangClass_NOT_PRESENT {
        // because it introduces a giant dependency circle which complicates resolution
    }

    interface JavaLangCharSequence {
        char charAt(int index);

        int length();
    }

    @E2Container
    static class JavaLangString implements JavaLangCharSequence {
        JavaLangString() { }

        public char charAt(int index) { return 0; }

        int chars$Transfer$Len(int len) { return len; }
        @NotNull
        IntStream chars() { return null; }

        int concat$Transfer$Len(int len, JavaLangString str) { return str.length() + len; }
        boolean concat$Postcondition(JavaLangString str) { return startsWith(this) && endsWith(str) && contains(this) && contains(str); }
        @NotNull
        JavaLangString concat(@NotNull JavaLangString str) { return null; }

        boolean contains$Value$Len(int len, JavaLangCharSequence s, boolean retVal) { return s.length() == 0 || (s.length() <= len && retVal); }
        boolean contains(JavaLangCharSequence s) { return true; }

        boolean endsWith$Value$Len(int len, JavaLangString s, boolean retVal) { return s.length() == 0 || (s.length() <= len && retVal); }
        boolean endsWith(JavaLangString suffix) { return false; }

        boolean length$Invariant$Len(int l) { return l >= 0; }
        void length$Aspect$Len() { }
        public int length() { return 0; }

        boolean isEmpty$Value$Len(int l) { return l == 0; }
        boolean isEmpty() { return false; }

        boolean indexOf$Postcondition$Len(int len, int retVal) { return retVal >= -1 && retVal < len; }
        int indexOf(int ch) { return 0; }

        int intern$Transfer$Len(int len) { return len; }
        // TODO try to write that the result equals this
        @NotNull
        JavaLangString intern() { return null; }

        boolean lastIndexOf$Postcondition$Len(int len, int retVal) { return retVal >= -1 && retVal < len; }
        int lastIndexOf(int ch) { return 0; }

        int repeat$Transfer$Len(int len, int count) { return len * count; }
        @NotNull
        JavaLangString repeat(int count) { return null; }

        boolean startsWith$Value$Len(int len, JavaLangString s, boolean retVal) { return s.length() <= len && retVal; }
        boolean startsWith(@NotNull JavaLangString s) { return true; }

        boolean startsWith$Value$Len(int len, JavaLangString s, int i, boolean retVal) { return s.length() + i <= len && retVal; }
        boolean startsWith(@NotNull JavaLangString s, int i) { return true; }

        boolean substring$Precondition$Len(int len, int beginIndex) { return beginIndex < len; }
        int substring$Transfer$Value(int len, int beginIndex) { return len - beginIndex; }
        @NotNull
        JavaLangString substring(int beginIndex) { return null; }

        boolean substring$Precondition$Len(int len, int beginIndex, int endIndex) { return endIndex < len && beginIndex <= endIndex; }
        int substring$Transfer$Value(int len, int beginIndex, int endIndex) { return endIndex - beginIndex; }
        @NotNull
        JavaLangString substring(int beginIndex, int endIndex) { return null; }

        int toLowerCase$Transfer$Len(int len) { return len; }
        @NotNull
        JavaLangString toLowerCase() { return null; }

        int toUpperCase$Transfer$Len(int len) { return len; }
        @NotNull
        JavaLangString toUpperCase() { return null; }

        boolean trim$Postcondition$Len(int post, int pre) { return post <= pre; }
        @NotNull
        JavaLangString trim() { return null; }

        boolean strip$Postcondition$Len(int post, int pre) { return post <= pre; }
        @NotNull
        JavaLangString strip() { return null; }
    }

    @Container
    static class JavaLangStringBuilder {
        JavaLangStringBuilder() {
        }

        JavaLangStringBuilder(JavaLangString string) { }

        @Fluent
        JavaLangStringBuilder append(boolean b) { return this; }

        @Fluent
        JavaLangStringBuilder append(char c) { return this; }

        @Fluent
        JavaLangStringBuilder append(float f) { return this; }

        @Fluent
        JavaLangStringBuilder append(long l) { return this; }

        @Fluent
        JavaLangStringBuilder append(int i) { return this; }

        @Fluent
        JavaLangStringBuilder append(char[] chars) { return this; }

        @Fluent
        JavaLangStringBuilder append(JavaLangString str) { return this; }

        @Fluent
        JavaLangStringBuilder append(Object o) { return this; }
    }

    @E2Container
    static class JavaLangInteger {

    }

    @E2Container
    static class JavaLangBoolean {

    }

    @UtilityClass
    static class JavaLangMath {
        @NotModified
        static int max(int a, int b) { return 0; }
    }

    static class JavaLangSystem {
        @IgnoreModifications
        @NotNull
        static final PrintStream out = null;
        @NotNull
        @IgnoreModifications
        static final PrintStream err = null;

        static void arraycopy(@NotNull @NotModified Object src, int srcPos, @NotNull Object dest, int destPos, int length) {
        }
    }

    interface JavaLangIterable<T> {
        // looping over the collection should not not change it!
        @NotModified
        void forEach(@NotNull Consumer<? super T> action);

        @NotNull
        Iterator<T> iterator();

        @NotNull
        Spliterator<T> spliterator();
    }

    static class JavaLangComparable<T> {
        int compareTo$Value(T t, int retVal) { return equals(t) || t.equals(this) ? 0 : retVal; }
        @NotModified
        int compareTo(@NotNull @NotModified T t) { return 0; }
    }

}
