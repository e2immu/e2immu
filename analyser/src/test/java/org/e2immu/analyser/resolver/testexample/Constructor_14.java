package org.e2immu.analyser.resolver.testexample;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class Constructor_14 {


    static class TempFile {
        TempFile(File file, boolean b) {
        }

        private List<TempFile> toTempFiles(File[] files) {
            // problem: the type produced by Arrays.asList(files).stream() is not good enough
            // Arrays.stream(files)  == OK
            // files.stream()        == OK if we make files of type List<File>
            // ==> this is all about the scope of map() producing sufficient information for the Lambda to work with
            return Arrays.asList(files).stream().map(f -> new TempFile(f, true)).toList();
        }
    }
}
