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

package org.e2immu.annotatedapi.javaparser;

import com.github.javaparser.Problem;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NotNull1;

import java.util.List;
import java.util.Optional;

public class ComGithubJavaparser {

    public static final String PACKAGE_NAME = "com.github.javaparser";

    interface ParseResult$<T> {

        @NotNull
        Optional<T> getResult();

        @NotNull1
        List<Problem> getProblems();
    }
}
