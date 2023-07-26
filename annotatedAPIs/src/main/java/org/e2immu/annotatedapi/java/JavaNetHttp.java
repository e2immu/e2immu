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
import org.e2immu.annotation.method.GetSet;


import java.net.URI;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

public class JavaNetHttp {
    final static String PACKAGE_NAME = "java.net.http";

    @ImmutableContainer
    interface HttpRequest$ {

        @Independent
        @NotNull
        Builder newBuilder();

        @Independent
        @GetSet // indicating that we can convert to newBuilder().uri(uri)
        @NotNull
        Builder newBuilder(URI uri);

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
            @Commutable(seq = "header,0", multi = "headers")
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
            @Commutable(seq = "header,0")
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


        @Independent
        interface BodyPublishers {

            // FIXME we cannot find this method
            //   HttpRequest.BodyPublisher fromPublisher(Flow.Publisher<? extends ByteBuffer> publisher, long contentLength);
        }

    }

    interface HttpResponse$ {

        @Independent
        interface BodySubscribers {

            HttpResponse.BodySubscriber<Void> fromSubscriber(Flow.Subscriber<? super List<ByteBuffer>> subscriber);
        }

        @Independent
        interface BodyHandlers {
            HttpResponse.BodyHandler<Void> fromSubscriber(Flow.Subscriber<? super List<ByteBuffer>> subscriber);
        }
    }

    @ImmutableContainer
    interface HttpClient$ {

        @Container(builds = HttpClient.class)
        interface Builder {

            @Fluent
            @Commutable
            HttpRequest.Builder connectTimeout(Duration duration);

            @Fluent
            @Commutable
            HttpRequest.Builder followRedirects(HttpClient.Redirect policy);

            @Fluent
            @Commutable
            HttpRequest.Builder version(HttpClient.Version version);
        }

        @NotNull
        @Independent
        HttpClient.Builder newBuilder();

        @Independent
        HttpClient newHttpClient();

        @NotModified
        <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler);
    }
}
