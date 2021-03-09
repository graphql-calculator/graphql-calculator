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

package calculator.engine.metadata;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class FutureTask<T> {

    // for debug
    private String path;

    // 是否是list下的元素
    private boolean isList;

    private CompletableFuture<T> future;

    private String transform;

    private FutureTask(String path, boolean isList, CompletableFuture<T> future, String transform) {
        this.path = path;
        this.isList = isList;
        this.future = future;
        this.transform = transform;
    }


    public String getPath() {
        return path;
    }

    public boolean isList() {
        return isList;
    }

    public CompletableFuture<T> getFuture() {
        return future;
    }


    public static FutureTaskBuilder newBuilder() {
        return new FutureTaskBuilder();
    }

    public static class FutureTaskBuilder<T> {
        private String path;

        private Boolean isList;

        private CompletableFuture future;

        private String transform;


        public FutureTaskBuilder path(String path) {
            this.path = path;
            return this;
        }

        public FutureTaskBuilder isList(boolean val) {
            this.isList = val;
            return this;
        }

        public FutureTaskBuilder future(CompletableFuture future) {
            this.future = future;
            return this;
        }

        public FutureTaskBuilder transform(String transform) {
            this.transform = transform;
            return this;
        }

        public FutureTask build() {
            Objects.requireNonNull(path);
            Objects.requireNonNull(isList);
            Objects.requireNonNull(future);

            return new FutureTask(path, isList, future, transform);
        }

    }
}
