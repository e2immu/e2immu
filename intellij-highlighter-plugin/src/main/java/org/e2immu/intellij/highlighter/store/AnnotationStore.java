package org.e2immu.intellij.highlighter.store;

import com.google.common.base.Charsets;
import com.google.gson.stream.JsonReader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.e2immu.annotation.Singleton;
import org.e2immu.intellij.highlighter.Constants;
import org.e2immu.intellij.highlighter.java.ConfigData;
import org.e2immu.intellij.highlighter.java.JavaConfig;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@Singleton
public class AnnotationStore {
    private static final Logger LOGGER = Logger.getInstance(AnnotationStore.class);

    public static final AnnotationStore INSTANCE = new AnnotationStore();

    private final LRUCache<String, String> cache = new LRUCache<>(500, 1000L * 60 * 30);

    public void mapAndMark(String element, String context, Consumer<String> uponResult) {
        String hardCoded = Constants.HARDCODED_ANNOTATION_MAP.get(element);
        if (hardCoded != null) {
            String result = hardCoded + context;
            LOGGER.warn("Found " + element + " hardcoded: '" + hardCoded + "', returning '" + result + "'");
            uponResult.accept(result);
            return;
        }
        String inCache = cache.get(element);
        if (inCache != null) {
            uponResult.accept(inCache);
            LOGGER.warn("Found " + element + " in cache: '" + inCache + "', hits " + cache.getHits() + ", misses " + cache.getMisses());
            return;
        }
        ProgressManager.checkCanceled();

        CloseableHttpClient httpClient = HttpClients.createDefault();
        String url = createUrl(element);
        HttpGet httpGet = new HttpGet(url);
        LOGGER.warn("Calling for " + url);
        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            if (response.getStatusLine().getStatusCode() == 200) {
                HttpEntity entity = response.getEntity();
                JsonReader reader = new JsonReader(new InputStreamReader(entity.getContent(), Charsets.UTF_8));
                reader.beginObject();
                while (reader.hasNext()) {
                    String elementInResult = reader.nextName();
                    String rawValue = reader.nextString();
                    String annotationInResult = select(rawValue) + context;
                    LOGGER.warn("Add '" + annotationInResult + "' to cache for " + elementInResult);
                    cache.put(elementInResult, annotationInResult);
                    if (elementInResult.equals(element)) {
                        uponResult.accept(annotationInResult);
                    }
                }
            } else {
                LOGGER.warn("Failed to call " + url);
            }
        } catch (IOException e) {
            LOGGER.warn("IOException when calling " + url + ": " + e.getMessage());
        }
    }

    /**
     * The system at the moment only expects one annotation per identifier (it's highlighting, very difficult
     * to highlight in 2 different colors!!
     *
     * @param nextString a CSV of annotations in LC
     * @return a single annotation name
     */
    static String select(String nextString) {
        int comma = nextString.indexOf(',');
        if (comma > 0) {
            return nextString.substring(0, comma);
        }
        return nextString;
    }

    /**
     * NOTE: we're using <code>JavaConfig.INSTANCE</code> directly here, rather than via
     * a constant field. Reason is that when running the tests, no such instance needs to be
     * created: it requires the IntelliJ service system to be up and running.
     * <p>
     * In this way of writing, the method is static. In the other way of writing, we're forcing it
     * not to be static; we're then simply expressing <code>JavaConfig</code>'s singleton status.
     *
     * @param element the annotation for which we'll do a request
     * @return an encoded url
     */
    static String createUrl(String element) {
        ConfigData state = JavaConfig.INSTANCE.getState();
        try {
            return state.getAnnotationServerUrl() + "/v1/get/" +
                    URLEncoder.encode(state.getAnnotationProject(), StandardCharsets.UTF_8.toString()) + "/" +
                    URLEncoder.encode(element, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex.getCause());
        }
    }

}
