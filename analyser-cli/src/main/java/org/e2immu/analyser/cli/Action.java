package org.e2immu.analyser.cli;

import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.e2immu.analyser.bytecode.tools.ExtractTypesFromClassFile;
import org.e2immu.analyser.util.Resources;
import org.e2immu.graph.G;
import org.e2immu.graph.analyser.PackedInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.util.*;

public class Action {
    private static final Logger LOGGER = LoggerFactory.getLogger(Action.class);

    public static int execAction(String action, String[] actionParameters) {
        if ("ExtractTypesFromClassFile".equals(action)) {
            try {
                return extractTypesFromClassFile(actionParameters);
            } catch (IOException e) {
                LOGGER.error("Caught exception", e);
            }
        }
        LOGGER.error("Action '{}' not recognized", action);
        return 1;
    }

    private static int extractTypesFromClassFile(String[] actionParameters) throws IOException {
        ExtractTypesFromClassFile e = new ExtractTypesFromClassFile();
        LOGGER.info("ExtractTypesFromClassFile");
        for (String actionParameter : actionParameters) {
            File[] expanded = expandWildcards(actionParameter);
            LOGGER.info("ExtractTypesFromClassFile parameter {} results in {} files", actionParameter, expanded.length);
            for (File path : expanded) {
                Resources resources = new Resources();
                URL url = new URL("jar:file:" + path.getAbsolutePath() + "!/");
                LOGGER.info("Adding resource {} to action ExtractTypesFromClassFile", url);
                resources.addJar(url);
                e.go(resources);
            }
        }
        G<String> g = e.build();
        Map<String, Long> external = new TreeMap<>();
        g.edgeStream().forEach(edge -> external.merge(edge.to().someElement(), edge.weight(), PackedInt::longSum));
        LOGGER.info("***** have {} *****", external.size());
        for (Map.Entry<String, Long> entry : external.entrySet()) {
            LOGGER.info("{} {}", entry.getKey(), PackedInt.nice((int) (long) entry.getValue()));
        }
        return 0;// success
    }

    private static File[] expandWildcards(String path) {
        File fileFromPath = new File(path);
        FileFilter fileFilter = WildcardFileFilter.builder().setWildcards(fileFromPath.getName()).get();
        File dir = fileFromPath.getParentFile();
        return dir.listFiles(fileFilter);
    }
}
