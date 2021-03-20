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

package org.e2immu.intellij.highlighter.store;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.e2immu.annotation.Singleton;
import org.e2immu.intellij.highlighter.Constants;
import org.e2immu.intellij.highlighter.ElementName;
import org.e2immu.intellij.highlighter.ElementType;
import org.e2immu.intellij.highlighter.java.ConfigData;
import org.e2immu.intellij.highlighter.java.JavaConfig;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Singleton
public class AnnotationStore {
    private static final Logger LOGGER = Logger.getInstance(AnnotationStore.class);

    public static final AnnotationStore INSTANCE = new AnnotationStore();

    private final LRUCache<String, String> cache = new LRUCache<>(500, 1000L * 60 * 30);

    /**
     * @param elementNames FQNs of the element their element type, according to the rules in the manual.
     * @param uponResult   The annotation name in lower case + context (e2immutable_f)
     */
    public void mapAndMark(List<ElementName> elementNames, Consumer<String> uponResult) {
        for (ElementName elementName : elementNames) {
            String hardCoded = Constants.HARDCODED_ANNOTATION_MAP.get(elementName.elementQn);
            if (hardCoded != null) {
                String result = hardCoded + elementName.elementType;
                LOGGER.warn("Found " + elementName + " hardcoded: '" + hardCoded + "', returning '" + result + "'");
                uponResult.accept(result);
                return;
            }
            String inCache = cache.get(elementName.elementQn);
            if (inCache != null) {
                uponResult.accept(inCache);
                LOGGER.warn("Found " + elementName + " in cache: '" + inCache + "', hits " + cache.getHits() + ", misses " + cache.getMisses());
                return;
            }
        }
        ProgressManager.checkCanceled();

        CloseableHttpClient httpClient = HttpClients.createDefault();
        if (elementNames.size() == 1) {
            ElementName elementName = elementNames.get(0);
            String url = createGetUrl(elementName.elementQn);
            HttpGet httpGet = new HttpGet(url);
            LOGGER.warn("Calling for " + url);
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                if (response.getStatusLine().getStatusCode() == 200) {
                    parseHttpResponse(response, ImmutableSet.of(elementName.elementQn), uponResult);
                } else {
                    LOGGER.warn("Failed to GET " + url);
                }
            } catch (IOException e) {
                LOGGER.warn("IOException when calling " + url + ": " + e.getMessage());
            }
        } else {
            String url = createPostUrl();
            HttpPost httpPost = new HttpPost(url);
            Set<String> elementsToReactTo = new HashSet<>();

            try (StringWriter stringWriter = new StringWriter();
                 JsonWriter writer = new JsonWriter(stringWriter)) {
                writer.beginArray();
                for (ElementName elementName : elementNames) {
                    elementsToReactTo.add(elementName.elementQn);
                    writer.value(elementName.elementQn);
                }
                writer.endArray();
                String jsonString = stringWriter.toString();
                HttpEntity entity = new StringEntity(jsonString, Charsets.UTF_8);
                LOGGER.warn("Calling for " + url + " with " + jsonString);
                httpPost.setEntity(entity);
            } catch (IOException ioe) {
                LOGGER.warn("Cannot write json: " + ioe.getMessage());
                return;
            }
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                if (response.getStatusLine().getStatusCode() == 200) {
                    parseHttpResponse(response, elementsToReactTo, uponResult);
                } else {
                    LOGGER.warn("Failed to POST " + url);
                }
            } catch (IOException e) {
                LOGGER.warn("IOException when calling " + url + ": " + e.getMessage());
            }
        }
    }

    private void parseHttpResponse(CloseableHttpResponse response, Set<String> elementsToReactTo, Consumer<String> uponResult) throws IOException {
        HttpEntity entity = response.getEntity();
        JsonReader reader = new JsonReader(new InputStreamReader(entity.getContent(), Charsets.UTF_8));
        reader.beginObject();
        while (reader.hasNext()) {
            String elementInResult = reader.nextName();
            String rawValue = reader.nextString();
            String annotationInResult = select(rawValue);
            LOGGER.warn("Add '" + annotationInResult + "' to cache for " + elementInResult);
            cache.put(elementInResult, annotationInResult);
            if (elementsToReactTo.contains(elementInResult)) {
                uponResult.accept(annotationInResult);
            }
        }
    }

    /**
     * The system at the moment only expects one annotation per identifier
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
     * @param elementQn the annotation for which we'll do a request
     * @return an encoded url
     */
    static String createGetUrl(String elementQn) {
        ConfigData state = JavaConfig.INSTANCE.getState();
        try {
            return state.getAnnotationServerUrl() + "/v1/get/" +
                    URLEncoder.encode(state.getAnnotationProject(), StandardCharsets.UTF_8.toString()) + "/" +
                    URLEncoder.encode(elementQn, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex.getCause());
        }
    }

    static String createPostUrl() {
        ConfigData state = JavaConfig.INSTANCE.getState();
        try {
            return state.getAnnotationServerUrl() + "/v1/get/" +
                    URLEncoder.encode(state.getAnnotationProject(), StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex.getCause());
        }
    }

    public void clearCache() {
        LOGGER.warn("Clearing cache");
        cache.clear();
    }
}
