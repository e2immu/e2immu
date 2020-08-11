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

package org.e2immu.analyser.config;

import com.google.common.collect.ImmutableList;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.Fluent;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.e2immu.analyser.cli.Main.*;
import static org.e2immu.analyser.config.Configuration.*;

@E2Immutable
public class UploadConfiguration {
    public static final String DEFAULT_ANNOTATION_SERVER_URL = "http://localhost:8281/v1";
    public static final String DEFAULT_PROJECT = "default";

    // upload
    public final boolean upload;
    public final List<String> uploadPackages;
    public final String annotationServerUrl;
    public final String projectName;

    public UploadConfiguration(boolean upload,
                               String annotationServerUrl,
                               String projectName,
                               List<String> uploadPackages) {

        this.annotationServerUrl = annotationServerUrl;
        this.projectName = projectName;
        this.upload = upload;
        this.uploadPackages = uploadPackages;
    }

    @Override
    public String toString() {
        return new StringJoiner("\n")
                .add("upload: " + upload)
                .add("uploadPackages: " + uploadPackages)
                .add("annotationServerUrl: " + annotationServerUrl)
                .add("projectName: " + projectName)
                .toString() + "\n";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UploadConfiguration that = (UploadConfiguration) o;
        return upload == that.upload &&
                uploadPackages.equals(that.uploadPackages) &&
                annotationServerUrl.equals(that.annotationServerUrl) &&
                projectName.equals(that.projectName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(upload, uploadPackages, annotationServerUrl, projectName);
    }

    public static UploadConfiguration fromProperties(Map<String, String> analyserProperties) {
        Builder builder = new Builder();
        setBooleanProperty(analyserProperties, UPLOAD, builder::setUpload);
        setStringProperty(analyserProperties, UPLOAD_PROJECT, builder::setProjectName);
        setStringProperty(analyserProperties, UPLOAD_URL, builder::setAnnotationServerUrl);
        setSplitStringProperty(analyserProperties, COMMA, UPLOAD_PACKAGES, builder::addUploadPackage);
        return builder.build();
    }

    public String createUrlWithProjectName(String action) {
        try {
            return annotationServerUrl + "/" + action + "/" + URLEncoder.encode(projectName, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex.getCause());
        }
    }

    public boolean accept(String packageOfType) {
        if (uploadPackages.isEmpty()) return true;
        for (String prefix : uploadPackages) {
            if (prefix.endsWith(".")) {
                String withoutDot = prefix.substring(0, prefix.length() - 1);
                if (packageOfType.startsWith(withoutDot)) {
                    return true;
                }
            } else if (prefix.equals(packageOfType)) return true;
        }
        return false;
    }

    @Container
    public static class Builder {
        private String projectName;
        private String annotationServerUrl;
        private boolean upload;
        private final List<String> uploadPackages = new ArrayList<>();

        public UploadConfiguration build() {
            return new UploadConfiguration(upload,
                    annotationServerUrl == null ? DEFAULT_ANNOTATION_SERVER_URL : annotationServerUrl,
                    projectName == null ? DEFAULT_PROJECT : projectName,
                    ImmutableList.copyOf(uploadPackages)
            );
        }


        @Fluent
        public Builder setAnnotationServerUrl(String annotationServerUrl) {
            this.annotationServerUrl = annotationServerUrl;
            return this;
        }

        @Fluent
        public Builder setProjectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        @Fluent
        public Builder setUpload(boolean upload) {
            this.upload = upload;
            return this;
        }

        @Fluent
        public Builder addUploadPackage(String... packages) {
            this.uploadPackages.addAll(Arrays.asList(packages));
            return this;
        }

    }
}
