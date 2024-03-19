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

package org.e2immu.analyser.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Trie contains the full name of the resource: the class file for java.util.List
 * is in [java, util, List.class], three levels deep. At the List.class node is a list
 * of URLs; currently only the first is used.
 * The URL is the _full_ URL for this resource.
 */

public class Resources {
    private static final Logger LOGGER = LoggerFactory.getLogger(Resources.class);

    static class ResourceAccessException extends RuntimeException {
        public ResourceAccessException(String msg) {
            super(msg);
        }
    }

    private final Trie<URI> data = new Trie<>();

    public record JarSize(int entries, int bytes) {
    }

    private final Map<String, JarSize> jarSizes = new HashMap<>();

    public Map<String, JarSize> getJarSizes() {
        return jarSizes;
    }

    public void visit(String[] prefix, BiConsumer<String[], List<URI>> visitor) {
        data.visit(prefix, visitor);
    }

    public List<String[]> expandPaths(String path) {
        List<String[]> expansions = new LinkedList<>();
        String[] prefix = path.split("\\.");
        data.visit(prefix, (s, list) -> expansions.add(s));
        return expansions;
    }

    public void expandPaths(String path, String extension, BiConsumer<String[], List<URI>> visitor) {
        String[] prefix = path.split("\\.");
        data.visit(prefix, (s, list) -> {
            if (s.length > 0 && s[s.length - 1].endsWith(extension)) {
                visitor.accept(s, list);
            }
        });
    }

    public void expandLeaves(String path, String extension, BiConsumer<String[], List<URI>> visitor) {
        String[] prefix = path.split("\\.");
        data.visitLeaves(prefix, (s, list) -> {
            if (s.length > 0 && s[s.length - 1].endsWith(extension)) {
                visitor.accept(s, list);
            }
        });
    }

    public List<URI> expandURLs(String extension) {
        List<URI> expansions = new LinkedList<>();
        data.visit(new String[0], (s, list) -> {
            if (s[s.length - 1].endsWith(extension)) {
                expansions.addAll(list);
            }
        });
        return expansions;
    }

    /**
     * Load a resource as a byte array
     *
     * @param path the path, separated by /
     * @return a byte[] containing the content of the resource
     */

    public byte[] loadBytes(String path) {
        String[] prefix = path.split("/");
        List<URI> urls = data.get(prefix);
        if (urls != null) {
            for (URI uri : urls) {
                try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                     InputStream inputStream = uri.toURL().openStream()) {
                    inputStream.transferTo(byteArrayOutputStream);
                    return byteArrayOutputStream.toByteArray();
                } catch (IOException e) {
                    throw new ResourceAccessException("URL = " + uri + ", Cannot read? " + e.getMessage());
                }
            }
        }
        LOGGER.debug("{} not found in class path", path);
        return null;
    }


    /**
     * @param prefix adds the jars that contain the package denoted by the prefix
     * @return a map containing the number of entries per jar
     * @throws IOException when the jar handling fails somehow
     */
    public Map<String, Integer> addJarFromClassPath(String prefix) throws IOException {
        Enumeration<URL> roots = getClass().getClassLoader().getResources(prefix);
        Map<String, Integer> result = new HashMap<>();
        while (roots.hasMoreElements()) {
            URL url = roots.nextElement();
            String urlString = url.toString();
            int bangSlash = urlString.indexOf("!/");
            String strippedUrlString = urlString.substring(0, bangSlash + 2);
            URL strippedURL = new URL(strippedUrlString);
            LOGGER.debug("Stripped URL is {}", strippedURL);
            if ("jar".equals(strippedURL.getProtocol())) {
                int entries = addJar(strippedURL);
                result.put(strippedUrlString, entries);
            } else {
                throw new MalformedURLException("Protocol not implemented in URL: " + strippedURL.getProtocol());
            }
        }
        return result;
    }

    private static final Pattern JAR_FILE = Pattern.compile("/([^/]+\\.jar)");

    /**
     * Add a jar to the trie
     *
     * @param jarUrl must be a correct JAR url, as described in the class JarURLConnection
     * @return the number of entries added to the classpath
     * @throws IOException when jar handling fails somehow.
     */
    public int addJar(URL jarUrl) throws IOException {
        JarURLConnection jarConnection = (JarURLConnection) jarUrl.openConnection();
        JarFile jarFile = jarConnection.getJarFile();
        AtomicInteger entries = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        jarFile.stream().forEach(je -> {
            String realName = je.getRealName();
            // let's exclude XML files, etc., anything not Java-related
            if (realName.endsWith(".class") || realName.endsWith(".java")) {
                LOGGER.trace("Adding {}", realName);
                String[] split = je.getRealName().split("/");
                try {
                    URI fullUrl = new URL(jarUrl, je.getRealName()).toURI();
                    data.add(split, fullUrl);
                    entries.incrementAndGet();
                } catch (MalformedURLException | URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        String jarName;
        Matcher m = JAR_FILE.matcher(jarFile.getName());
        if (m.find()) {
            jarName = m.group(1);
        } else {
            jarName = jarUrl.toString();
        }
        jarSizes.put(jarName, new JarSize(entries.get(), 0));
        if (errors.get() > 0) {
            throw new IOException("Got " + errors.get() + " errors while adding from jar to classpath");
        }
        return entries.get();
    }

    public static URL constructJModURL(String part, String altJREDirectory) throws MalformedURLException {
        if (part.startsWith("/")) {
            return new URL("jar:file:" + part + "!/");
        }
        String jre;
        if (altJREDirectory == null) {
            jre = System.getProperty("java.home");
        } else {
            jre = altJREDirectory;
        }
        if (!jre.endsWith("/")) jre = jre + "/";
        return new URL("jar:file:" + jre + part + "!/");
    }

    /**
     * Add a java module to the trie. Format should be like
     * jar:file:/Library/Java/JavaVirtualMachines/adoptopenjdk-13.jdk/Contents/Home/jmods/java.base.jmod!/
     *
     * @param jmodUrl must be a correct jmod url, as described in the class JarURLConnection
     * @return the number of entries added to the classpath
     * @throws IOException when jar handling fails somehow.
     */
    public int addJmod(URL jmodUrl) throws IOException {
        JarURLConnection jarConnection = (JarURLConnection) jmodUrl.openConnection();
        JarFile jarFile = jarConnection.getJarFile();
        AtomicInteger entries = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        jarFile.stream()
                .filter(je -> je.getRealName().startsWith("classes/"))
                .forEach(je -> {
                    String realName = je.getRealName().substring("classes/".length());
                    LOGGER.trace("Adding {}", realName);
                    String[] split = realName.split("/");
                    try {
                        URL fullUrl = new URL(jmodUrl, je.getRealName());
                        data.add(split, fullUrl.toURI());
                        entries.incrementAndGet();
                    } catch (MalformedURLException | URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                });
        if (errors.get() > 0) {
            throw new IOException("Got " + errors.get() + " errors while adding from jar to classpath");
        }
        return entries.get();
    }

    public void addDirectoryFromFileSystem(File base) {
        File file = new File("");
        try {
            recursivelyAddFiles(base, file);
        } catch (MalformedURLException e) {
            throw new UnsupportedOperationException("??");
        }
    }

    private void recursivelyAddFiles(File baseDirectory, File dirRelativeToBase) throws MalformedURLException {
        File dir = new File(baseDirectory, dirRelativeToBase.getPath());
        if (dir.isDirectory()) {
            File[] subDirs = dir.listFiles(File::isDirectory);
            if (subDirs != null) {
                for (File subDir : subDirs) { // 1.0.1.0.0
                    recursivelyAddFiles(baseDirectory, new File(dirRelativeToBase, subDir.getName()));
                }
            }
            File[] files = dir.listFiles(f -> !f.isDirectory());
            if (files != null) { // 1.0.3
                String pathString = dirRelativeToBase.getPath(); // 1.0.3.0.0
                String[] packageParts =
                        pathString.isEmpty() ? new String[0] :
                                (pathString.startsWith("/") ? pathString.substring(1) : pathString).split("/");
                for (File file : files) {
                    String name = file.getName();
                    if (packageParts.length == 0 && name.endsWith(".annotated_api")) {
                        String[] partsFromFile = name.split("\\.");
                        LOGGER.debug("File {} in path from file {}", name, String.join("/", partsFromFile));
                        data.add(partsFromFile, file.toURI());
                    } else {
                        LOGGER.debug("File {} in path {}", name, String.join("/", packageParts));
                        data.add(StringUtil.concat(packageParts, new String[]{name}), file.toURI());
                    }
                }
            }
        }
    }

    /**
     * Go from java.util.Map.Entry, as it occurs in import statements,
     * to java/util/Map$Entry.class
     *
     * @param fqn       dot separated
     * @param extension to be added to the final part
     * @return the correct path name
     */

    public Source fqnToPath(String fqn, String extension) {
        String[] splitDot = fqn.split("\\.");
        for (int i = 1; i < splitDot.length; i++) {
            String[] parts = new String[i + 1];
            System.arraycopy(splitDot, 0, parts, 0, i);
            parts[i] = splitDot[i];
            for (int j = i + 1; j < splitDot.length; j++) {
                parts[i] += "$" + splitDot[j];
            }
            parts[i] += extension;
            List<URI> uris = data.get(parts);
            if (uris != null && !uris.isEmpty()) {
                return new Source(String.join("/", parts), uris.get(0));
            }
        }
        LOGGER.debug("Cannot find {} with extension {} in classpath", fqn, extension);
        return null;
    }

    public static String pathToFqn(String path) {
        String stripDotClass = StringUtil.stripDotClass(path);
        if (stripDotClass.endsWith("$")) {
            // scala
            return stripDotClass.substring(0, stripDotClass.length() - 1).replaceAll("[/$]", ".") + ".object";
        }
        if (stripDotClass.endsWith("$class")) {
            // scala; keep it as is, ending in .class
            return stripDotClass.replaceAll("[/$]", ".");
        }
        int anon;
        if ((anon = stripDotClass.indexOf("$$anonfun")) > 0) {
            // scala
            String random = Integer.toString(Math.abs(stripDotClass.hashCode()));
            return stripDotClass.substring(0, anon).replaceAll("[/$]", ".") + "." + random;
        }
        return stripDotClass.replaceAll("[/$]", ".");
    }

}
