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


/**
 * When you create {@code DefaultSettings} instance and used in {@link CodeGenerator},
 * you shouldn't update {@code DefaultSettings} instance anymore, since {@code CodeGenerator}
 * will whether {@code DefaultSettings} instance is valid before use it to generatorCode.
 */
public interface GeneratorSettings {

    boolean enableComment();

    boolean enableQueryComment();

    boolean enableGetter();

    boolean enableSetter();

    boolean enableGetterAndSetter();

    boolean enableToString();

    boolean enableNoArgsConstructor();

    boolean enableAllArgsConstructor();

    boolean getCopyForCollection();

    ThreadModel threadModel();

    String notNullAnnotationPath();

    static Builder builder() {
        return new DefaultSettingsBuilder();
    }

    interface Builder {
        Builder enableComment(boolean enableComment);

        Builder enableQueryComment(boolean enableComment);

        Builder enableGetter(boolean enableGetter);

        Builder enableSetter(boolean enableSetter);

        Builder enableGetterAndSetter(boolean enableGetterAndSetter);

        Builder enableToString(boolean enableToString);

        Builder enableNoArgsConstructor(boolean enableNoArgsConstructor);

        Builder enableAllArgsConstructor(boolean enableAllArgsConstructor);

        Builder getCopyForCollection(boolean getCopyForCollection);

        Builder threadModel(ThreadModel threadModel);

        Builder notNullAnnotationPath(String notNullAnnotationPath);

        GeneratorSettings build();
    }

    enum ThreadModel {
        FINAL, SYNCHRONIZED;
    }

}
