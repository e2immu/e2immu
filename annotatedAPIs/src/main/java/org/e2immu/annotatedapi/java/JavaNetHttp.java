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
import org.e2immu.annotation.rare.StaticSideEffects;
import org.e2immu.annotation.type.UtilityClass;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandlerFactory;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.jar.JarFile;

public class JavaNetHttp {
    final static String PACKAGE_NAME = "java.net.http";

    @ImmutableContainer
    interface HttpRequest$ {

        @NotNull
        Builder newBuilder();

        @Container(builds = HttpRequest.class)
        interface Builder {

            HttpRequest build();

            Builder copy();

            // in a container, @Fluent methods are @Modified, but not necessarily @Commutable
            @Fluent
            @Commutable
            Builder DELETE();

            @Fluent
            @Commutable
            Builder expectContinue(boolean enable);

            @Fluent
            @Commutable
            Builder GET();

            @Fluent
            @Commutable(seq = "header")
            Builder header(String name, String value);

            @Fluent
            @Commutable(seq = "header")
            Builder headers(String... headers);

            @Fluent
            @Commutable
            Builder method(String method, HttpRequest.BodyPublisher bodyPublisher);

            @Fluent
            @Commutable
            Builder POST(HttpRequest.BodyPublisher bodyPublisher);

            @Fluent
            @Commutable
            Builder PUT(HttpRequest.BodyPublisher bodyPublisher);

            @Fluent
            @Commutable(seq = "header")
            Builder setHeader(String name, String value);

            @Fluent
            @Commutable
            Builder timeout(Duration duration);

            @Fluent
            @Commutable
            Builder uri(URI uri);

            @Fluent
            @Commutable
            Builder version(HttpClient.Version version);
        }
    }
}
