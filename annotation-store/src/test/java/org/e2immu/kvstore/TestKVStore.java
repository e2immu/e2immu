package org.e2immu.kvstore;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(VertxUnitRunner.class)
public class TestKVStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestKVStore.class);

    private static final String LOCALHOST = "localhost";
    private static final String PROJECT = "p1";
    private static final String JAVA_UTIL_SET = "java.util.Set";
    private static final String JAVA_UTIL_MAP = "java.util.Map";
    private static final String ORG_E2IMMU_KVSTORE_STORE_READ_WITHIN_MILLIS = "org.e2immu.kvstore.Store:readWithinMillis";

    private static final String CONTAINER = "container";
    private static final String E2IMMU = "e2immu";


    @Rule
    public RunTestOnContext rule = new RunTestOnContext();

    private static final Vertx vertxOfServer = Vertx.vertx();

    @BeforeClass
    public static void beforeClass() {
        new Store(vertxOfServer);
    }

    @AfterClass
    public static void afterClass() {
        vertxOfServer.close();
    }

    @Test
    public void test_01_putOneKey(TestContext ctx) {
        WebClient webClient = WebClient.create(rule.vertx());
        Async async = ctx.async();
        webClient.get(Store.DEFAULT_PORT, LOCALHOST, Store.API_VERSION + "/set/" + PROJECT + "/" + JAVA_UTIL_SET + "/" + CONTAINER)
                .send(ar -> {
                    if (ar.failed()) {
                        LOGGER.error("Failure: {}", ar.cause().getMessage());
                        ar.cause().printStackTrace();
                        ctx.fail();
                    } else {
                        ctx.assertTrue(ar.succeeded());
                        try {
                            JsonObject result = ar.result().bodyAsJsonObject();
                            if (ar.result().statusCode() != 200) {
                                LOGGER.error("ERROR: " + result);
                            }

                            ctx.assertEquals(200, ar.result().statusCode());
                            LOGGER.info("Got update summary: " + result);
                            int updated = result.getInteger("updated");
                            ctx.assertEquals(1, updated);
                        } catch (DecodeException decodeException) {
                            LOGGER.error("Received: " + ar.result().bodyAsString());
                            ctx.fail();
                        }
                    }
                    async.complete();
                });
    }

    @Test
    public void test_02_getOneKey(TestContext ctx) {
        WebClient webClient = WebClient.create(rule.vertx());
        Async async = ctx.async();
        webClient.get(Store.DEFAULT_PORT, LOCALHOST, Store.API_VERSION + "/get/" + PROJECT + "/" + JAVA_UTIL_SET)
                .send(ar -> {
                    if (ar.failed()) {
                        LOGGER.error("Failure: {}", ar.cause().getMessage());
                        ar.cause().printStackTrace();
                        ctx.fail();
                    } else {
                        ctx.assertTrue(ar.succeeded());
                        JsonObject result = ar.result().bodyAsJsonObject();
                        if (ar.result().statusCode() != 200) {
                            LOGGER.error("ERROR: " + result);
                        }
                        ctx.assertEquals(200, ar.result().statusCode());
                        LOGGER.info("Got value for java.util.set: " + result);
                        String value = result.getString(JAVA_UTIL_SET);
                        ctx.assertEquals(CONTAINER, value);
                    }
                    async.complete();
                });
    }

    @Test
    public void test_03_putMultipleKeys(TestContext ctx) {
        WebClient webClient = WebClient.create(rule.vertx());
        Async async = ctx.async();
        JsonObject putObject = new JsonObject()
                .put(JAVA_UTIL_SET, "new value")
                .put(JAVA_UTIL_MAP, CONTAINER)
                .put(ORG_E2IMMU_KVSTORE_STORE_READ_WITHIN_MILLIS, E2IMMU);
        Buffer body = Buffer.buffer(putObject.encode());
        webClient.put(Store.DEFAULT_PORT, LOCALHOST, Store.API_VERSION + "/set/" + PROJECT)
                .sendBuffer(body, ar -> {
                    if (ar.failed()) {
                        LOGGER.error("Failure: " + ar.cause().getMessage());
                        ar.cause().printStackTrace();
                        ctx.fail();
                    } else {
                        ctx.assertTrue(ar.succeeded());
                        ctx.assertEquals(200, ar.result().statusCode());

                        JsonObject result = ar.result().bodyAsJsonObject();
                        LOGGER.info("Got update summary: " + result);
                        int updated = result.getInteger("updated");
                        int ignored = result.getInteger("ignored");
                        int removed = result.getInteger("removed");
                        ctx.assertEquals(3, updated);
                        ctx.assertEquals(0, ignored);
                        ctx.assertEquals(0, removed);

                    }
                    async.complete();
                });
    }

    @Test
    public void test_04_getOneKey_Expect2(TestContext ctx) {
        WebClient webClient = WebClient.create(rule.vertx());
        Async async = ctx.async();
        webClient.get(Store.DEFAULT_PORT, LOCALHOST, Store.API_VERSION + "/get/" + PROJECT + "/" + JAVA_UTIL_MAP)
                .send(ar -> {
                    if (ar.failed()) {
                        LOGGER.error("Failure: {}", ar.cause().getMessage());
                        ar.cause().printStackTrace();
                        ctx.fail();
                    } else {
                        ctx.assertTrue(ar.succeeded());
                        JsonObject result = ar.result().bodyAsJsonObject();
                        if (ar.result().statusCode() != 200) {
                            LOGGER.error("ERROR: " + result);
                        }
                        ctx.assertEquals(200, ar.result().statusCode());
                        LOGGER.info("Got values: " + result);
                        ctx.assertEquals(2, result.size());
                        String set = result.getString(JAVA_UTIL_SET);
                        ctx.assertEquals("new value", set);
                        String map = result.getString(JAVA_UTIL_MAP);
                        ctx.assertEquals(CONTAINER, map);
                    }
                    async.complete();
                });
    }
}
