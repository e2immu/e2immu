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

package org.e2immu.analyser.model;

public enum SideEffect {

    /**
     * Only used for expressions and statements, not for methods! Not marked anywhere.
     * <p>
     * A statement is local when it involves NO method calls, only consults parameters and
     * reads and writes local variables.
     */
    LOCAL(0),

    /**
     * A method has static side effects only when:
     * 1. it either returns this, or nothing (void, Void).
     * 2. none of its statements are SIDE_EFFECT
     * <p>
     * A statement has static side effects only when:
     * - it is a throws statement
     * - it is an STATIC_ONLY expression (could be local variable assignment, e.g., or STATIC_ONLY method call)
     * - if it is an IfElse statement, both its blocks should be STATIC_ONLY, and the expression must not have side effects.
     * - if it is a block, all its containing statements should be STATIC_ONLY
     * - if it is a forEach loop, its block should be STATIC_ONLY; the iterator expression must not be SIDE_EFFECT
     * <p>
     * An expression is 'static side effects only' when only parameters and local variables are involved, and all
     * method calls are also marked SSEO. The only difference with LOCAL is the use of methods that are also
     * marked as SSEO.
     */
    STATIC_ONLY(1),

    THREAD_LOCAL_ONLY(2),

    /**
     * A method/statement/expression with side effect (not marked) either changes a field,
     * or calls a method that changes a field or a parameter.
     */
    SIDE_EFFECT(5),

    /**
     * Static method without side effects: we mark with `@PureFunction`; uses its arguments to compute some value.
     * Does not read or write fields; as a consequence, must be a static function (even though the keyword may be missing).
     * It must return a value.
     * <p>
     * Statement: we could regard local variable creation and assignment (potentially split across multiple statements) as
     * a pure function, if all expressions involved are pure expressions. A block remains pure as long as all statements are
     * pure or have static side effects only (there should be at least one pure statement).
     * <p>
     * Expression: as long as only pure and static_only functions, arguments, and local variables are used, the expression is pure.
     * Should not be used: fields, this, context functions, ... The difference with LOCAL is the use of other NONE_PURE methods
     * or STATIC_ONLY methods; note that there must be at least one NONE_PURE method, otherwise it is called STATIC_ONLY :-)
     */
    NONE_PURE(3),

    /**
     * A class is called a context class when all its methods are @PureFunction or @ContextFunction or @ContextSupplier.
     * Its constructors must not modify their parameters, i.e. they are either primitive or @NotModified.
     * Public fields must be final and immutable at the level of the field (i.e. if the field is a set, then the
     * set must be immutable).
     * <p>
     * Method without side effects: we mark with `@ContextFunction` or `@ContextSupplier`. Uses its arguments and the value of fields
     * the compute some value. It cannot be a static function; it must return a value. It must not change the content of
     * any of its fields or arguments (i.e. all arguments should be either primitive or marked @NotModified; all methods used
     * must also be marked @ContextFunction, @ContextSupplier, @StaticSideEffectsOnly, @PureFunction).
     * Pure functions and static side effect only functions can be called at any time, but to differentiate there must be some use of fields.
     * <p>
     * An expression is a context expression as long as no fields are modified, only @ContextFunction, @ContextSupplier, @PureFunction,
     * or @StaticSideEffectsOnly methods are called.
     */
    NONE_CONTEXT(4),

    /**
     * in the computation we may encounter other methods of the same class which have not been analysed yet...
     * this one has top priority: once delayed, we have to wait
     */
    DELAYED(6);

    private final int level;

    private SideEffect(int level) {
        this.level = level;
    }

    public SideEffect combine(SideEffect other) {
        if (level >= other.level) return this;
        return other;
    }

    public boolean atMost(SideEffect other) {
        return level <= other.level;
    }

    public boolean lessThan(SideEffect other) {
        return level < other.level;
    }
}
