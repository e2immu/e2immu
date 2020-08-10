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

package org.e2immu.analyser.testexample.withannotatedapi;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import org.e2immu.annotation.E2Container;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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

    //@Override
    //public void start(Promise<Void> startPromise) {
        // do nothing here
    //}

    //@Override
    public void stop() {
        LOGGER.info("Closed http server");
    }

    @E2Container
    static class Configuration {
        public final String a;

        public Configuration() {
            a = "abc";
        }
    }
}
