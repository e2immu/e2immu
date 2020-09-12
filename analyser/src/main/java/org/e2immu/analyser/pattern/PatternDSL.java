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

package org.e2immu.analyser.pattern;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class PatternDSL {

    public static final Restriction RESTRICTION = new Restriction();

    public static final class Restriction {

    }

    public static final Statement SOME_STATEMENT = new Statement();

    public static final class Statement {

    }

    public static class TypeWithMethod<T> {
        public void accept(T t) {
        }

        public T someMethodWithoutParameters() {
            return null;
        }

        public static <T> void staticMethod(T t) {
        }
    }

    public static <T> TypeWithMethod<T> someTypeWithMethod(Restriction... restrictions) {
        return new TypeWithMethod<>();
    }

    public static void replace(Statement statement, boolean... booleans) {
    }

    public static void detect(Statement statement, Restriction... booleans) {
    }


    public static <T> Class<T> classOfTypeParameter(int index) {
        return (Class<T>) Object.class;
    }

    public static Restriction noModificationOf(Object... objects) {
        return RESTRICTION;
    }

    public static <T> void expressionPartOfStatement(T t, int index) {

    }

    public static <T> void replaceExpressionPartOfStatement(T t, int index) {

    }

    public static <T> T someVariable(Class<T> clazz, Restriction... restrictions) {
        return (T) new Object();
    }

    public static <T> T someExpression(Class<T> clazz, Restriction... restrictions) {
        return (T) new Object();
    }

    public static <U, T> Function<U, T> someExpressionDependentOn(Class<T> clazz, U variable) {
        return null;
    }

    public static Statement someStatements(Restriction... restrictions) {
        return SOME_STATEMENT;
    }

    public static void pattern(Runnable pattern, Runnable replacement) {
    }

    public static void pattern(Quality quality, Runnable pattern, Runnable replacement) {
    }

    public static void warn(Runnable r) {
    }

    public static void partOfExpressionPattern(Runnable pattern, Runnable replacement) {
    }

    public static <T> void partOfExpressionPattern(T t, Consumer<T> pattern, Consumer<T> replacement) {
    }

    public static <T> void partOfExpressionPattern(T t, Supplier<T> pattern, Supplier<T> replacement) {
    }

    public static <T> void returnPattern(Supplier<T> pattern, Supplier<T> replacement) {
    }

    // *********************************************************************

    // restrictions to be added to someStatements, someVariable, someExpression

    public static Restriction avoid(Object... objects) {
        return RESTRICTION;
    }

    public static Restriction untilEndOfBlock() {
        return RESTRICTION;
    }

    public static Restriction atLeast(int n) {
        return RESTRICTION;
    }

    public static Restriction occurs(Object objects, int min, int max) {
        return RESTRICTION;
    }

    public static Restriction occurs(Object objects, int min) {
        return RESTRICTION;
    }

    public static Restriction dependsOn(Object... objects) {
        return RESTRICTION;
    }

    public static Restriction nonModifying() {
        return RESTRICTION;
    }

    public enum Quality {
        WARN, BAD, OK
    }
}
