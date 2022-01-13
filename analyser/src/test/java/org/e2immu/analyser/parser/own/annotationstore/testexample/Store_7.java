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

package org.e2immu.analyser.parser.own.annotationstore.testexample;

import org.e2immu.annotation.Fluent;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

public class Store_7 {

    public static HttpServer initServer() {
        Vertx vertx = new Vertx();
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router();

        // removing the next statement breaks the infinite loop
        return server.requestHandler(router);
    }

    interface HttpServerRequest {
    }

    interface HttpServer {
        @Fluent
        @Modified // removing the @NotModified on the parameter causes an infinite loop
        HttpServer requestHandler(@NotModified @NotNull Handler<HttpServerRequest> handler);
    }

    static class Vertx {
        @NotNull
        static HttpServer createHttpServer() {
            return new HttpServer() {
                private Handler<HttpServerRequest> myHandler;
                @Override
                public HttpServer requestHandler(Handler<HttpServerRequest> handler) {
                    this.myHandler = handler;
                    return this; // PART OF @Fluent test: anonymous is assignable to HttpServer
                }

                public Handler<HttpServerRequest> getMyHandler() {
                    return myHandler;
                }
            };
        }
    }

    interface Handler<E> {
        void handle(E e);
    }

    static class Router implements Handler<HttpServerRequest> {
        static Router router() {
            return new Router();
        }

        @Override
        public void handle(HttpServerRequest httpServerRequest) {
            // nothing here
        }
    }
}
