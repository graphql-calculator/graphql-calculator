/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package calculator.devtools.generator;


public class DefaultSettingsBuilder implements GeneratorSettings.Builder {

    private boolean enableComment = true;

    private boolean enableQueryComment;

    private boolean enableGetter = true;

    private boolean enableSetter = true;

    private boolean enableToString;

    private boolean enableNoArgsConstructor;

    private boolean enableAllArgsConstructor;

    private boolean getCopyForCollection;

    private GeneratorSettings.ThreadModel threadModel;

    private String notNullAnnotationPath;

    @Override
    public DefaultSettingsBuilder enableComment(boolean enableComment) {
        this.enableComment = enableComment;
        return this;
    }

    @Override
    public DefaultSettingsBuilder enableQueryComment(boolean enableQueryComment) {
        this.enableQueryComment = enableQueryComment;
        return this;
    }

    @Override
    public GeneratorSettings.Builder enableGetter(boolean enableGetter) {
        this.enableGetter = enableGetter;
        return this;
    }

    @Override
    public DefaultSettingsBuilder enableSetter(boolean enableSetter) {
        this.enableSetter = enableSetter;
        return this;
    }

    @Override
    public DefaultSettingsBuilder enableGetterAndSetter(boolean enableGetterAndSetter) {
        this.enableSetter = enableGetterAndSetter;
        this.enableGetter = enableGetterAndSetter;
        return this;
    }

    @Override
    public DefaultSettingsBuilder enableToString(boolean enableToString) {
        this.enableToString = enableToString;
        return this;
    }

    @Override
    public DefaultSettingsBuilder enableNoArgsConstructor(boolean enableNoArgsConstructor) {
        this.enableNoArgsConstructor = enableNoArgsConstructor;
        return this;
    }

    @Override
    public DefaultSettingsBuilder enableAllArgsConstructor(boolean enableAllArgsConstructor) {
        this.enableAllArgsConstructor = enableAllArgsConstructor;
        return this;
    }

    @Override
    public DefaultSettingsBuilder getCopyForCollection(boolean getCopyForCollection) {
        this.getCopyForCollection = getCopyForCollection;
        return this;
    }

    @Override
    public DefaultSettingsBuilder threadModel(GeneratorSettings.ThreadModel threadModel) {
        this.threadModel = threadModel;
        return this;
    }

    @Override
    public DefaultSettingsBuilder notNullAnnotationPath(String notNullAnnotationPath) {
        this.notNullAnnotationPath = notNullAnnotationPath;
        return this;
    }

    @Override
    public GeneratorSettings build() {
        return new UnModifiedSettings(enableComment, enableQueryComment, enableGetter,
                enableSetter, enableToString, enableNoArgsConstructor,
                enableAllArgsConstructor, getCopyForCollection, threadModel,
                notNullAnnotationPath
        );
    }

    private static class UnModifiedSettings implements GeneratorSettings {

        private final boolean enableComment;

        private final boolean enableQueryComment;

        private final boolean enableGetter;

        private final boolean enableSetter;

        private final boolean enableToString;

        private final boolean enableNoArgsConstructor;

        private final boolean enableAllArgsConstructor;

        private final ThreadModel threadModel;

        private final boolean getCopyForCollection;

        private final String notNullAnnotationPath;

        public UnModifiedSettings(boolean enableComment, boolean enableQueryComment, boolean enableGetter,
                                  boolean enableSetter, boolean enableToString, boolean enableNoArgsConstructor,
                                  boolean enableAllArgsConstructor, boolean getCopyForCollection, ThreadModel threadModel,
                                  String notNullAnnotationPath) {
            this.enableComment = enableComment;
            this.enableQueryComment = enableQueryComment;
            this.enableGetter = enableGetter;
            this.enableSetter = enableSetter;
            this.enableToString = enableToString;
            this.enableNoArgsConstructor = enableNoArgsConstructor;
            this.enableAllArgsConstructor = enableAllArgsConstructor;
            this.getCopyForCollection = getCopyForCollection;
            this.threadModel = threadModel;
            this.notNullAnnotationPath = notNullAnnotationPath;
        }

        @Override
        public boolean enableComment() {
            return enableComment;
        }

        @Override
        public boolean enableQueryComment() {
            return enableQueryComment;
        }

        @Override
        public boolean enableGetter() {
            return enableGetter;
        }

        @Override
        public boolean enableSetter() {
            return enableSetter;
        }

        @Override
        public boolean enableGetterAndSetter() {
            return enableGetter && enableSetter;
        }

        @Override
        public boolean enableToString() {
            return enableToString;
        }

        @Override
        public boolean enableNoArgsConstructor() {
            return enableNoArgsConstructor;
        }

        @Override
        public boolean enableAllArgsConstructor() {
            return enableAllArgsConstructor;
        }

        @Override
        public boolean getCopyForCollection() {
            return getCopyForCollection;
        }

        @Override
        public ThreadModel threadModel() {
            return threadModel;
        }

        @Override
        public String notNullAnnotationPath() {
            return notNullAnnotationPath;
        }
    }

}
