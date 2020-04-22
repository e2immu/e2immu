/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.intellij.highlighter.example.vertx;

import com.google.common.collect.ImmutableList;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.*;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.e2immu.annotation.E1Immutable;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.Mark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Class that derives from {@link io.vertx.core.AbstractVerticle()}. This super class is eventually final, after
 * a call to its {@code init()} method. Here we override the {@link #start()} method; this method acts as our local
 * eventually final marker. The Vertx.io framework is responsible for both initialisation calls.
 * <p>
 * The fact that we mark this class @EventuallyFinal also means that we're willing to make it a container,
 * in other words, all parameters of public methods will be marked @NotModified. The only parameter we have to study
 * is {@code startPromise}, of a @Container type. Two of its methods are called, and both cause modifications of the container.
 * This is not a problem because the parameter is an @Output parameter!
 * <p>
 * The assignment to {@link #configuration} happens in a handler which will be executed some time after
 * the assignment of references to two methods that refer to it, {@link #handleRequestA} and {@link #handleRequestList}.
 * These two methods are not explicitly invoked anywhere in the class.
 * Importantly, it will be executed before the method 'logically' completes by calling
 * {@code startPromise.complete()}.
 * We still need the guarantee that {@code route(...).handler()} does not actually calls the handler, but simply
 * sets it.
 * <p>
 * We can view the {@link #start} method as a constructor which sets up the values of the fields.
 */
@E1Immutable(after = "start")
public class Verticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(Verticle.class);

    public static void main(String[] args) {
        Verticle myVerticle = new Verticle();
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(myVerticle);
        LOGGER.info("Deployed verticle, current value of config should be null: {}",
                myVerticle.configuration);
    }

    private Configuration configuration;
    private HttpServer server;

    @Override
    @Mark("start")
    public void start(Promise<Void> startPromise) {
        ConfigStoreOptions store = new ConfigStoreOptions()
                .setType("file")
                .setFormat("yaml")
                .setConfig(new JsonObject()
                        .put("path", "src/test/resources/my-config.yaml")
                );

        ConfigRetriever retriever = ConfigRetriever.create(vertx,
                new ConfigRetrieverOptions().addStore(store));
        Future<JsonObject> configFuture = Future.future(retriever::getConfig);

        server = vertx.createHttpServer();
        Router router = Router.router(vertx);
        router.route("/a").handler(this::handleRequestA);
        router.route("/list").handler(this::handleRequestList);
        Future<HttpServer> httpServerFuture = Future.future(promise ->
                server.requestHandler(router).listen(8080, promise));
        CompositeFuture.all(httpServerFuture, configFuture)
                .onSuccess(ar -> {
                    JsonObject config = ar.result().resultAt(1);
                    LOGGER.info("Received config, started web server");
                    configuration = new Configuration(config);
                    startPromise.complete();
                })
                .onFailure(ar -> startPromise.fail(ar.getCause()));
    }

    @Override
    public void stop() {
        server.close(); // not interested when this happens...
        LOGGER.info("Closed http server");
    }

    @E2Immutable
    static class Configuration {
        public final List<String> theList;
        public final String a;

        public Configuration(JsonObject jsonObject) {
            a = jsonObject.getString("string_a");
            ImmutableList.Builder<String> builder = new ImmutableList.Builder<>();
            for (Object o : jsonObject.getJsonArray("list_a")) {
                builder.add(o.toString());
            }
            theList = builder.build();
        }
    }

    private static void textResult(RoutingContext routingContext, String msg) {
        HttpServerResponse response = routingContext.response();
        response.putHeader("content-type", "text/plain");
        response.end(msg);
    }

    private void handleRequestA(RoutingContext routingContext) {
        textResult(routingContext, "a=" + configuration.a);
    }

    private void handleRequestList(RoutingContext routingContext) {
        textResult(routingContext, "list=" + configuration.theList);
    }
}
