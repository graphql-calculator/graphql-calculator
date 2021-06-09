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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class FetchSourceTask {

    private final String sourceName;

    private final boolean isAnnotatedNode;

    private final boolean isListType;

    private final CompletableFuture<Object> taskFuture;

    private final List<Object> listElementResult = Collections.synchronizedList(new ArrayList<>());

    private final String mapper;

    private final FetchSourceTask parentTask;

    private final List<FetchSourceTask> childrenTaskList = new ArrayList<>();

    private FetchSourceTask(String sourceName,
                            boolean isAnnotatedNode,
                            Boolean isListType,
                            CompletableFuture<Object> taskFuture,
                            String mapper,
                            FetchSourceTask parentTask) {
        this.sourceName = sourceName;
        this.isAnnotatedNode = isAnnotatedNode;
        this.isListType = isListType;
        this.taskFuture = taskFuture;
        this.mapper = mapper;
        this.parentTask = parentTask;
    }


    public synchronized void childrenTaskList(FetchSourceTask sourceTask) {
        childrenTaskList.add(sourceTask);
    }

    public static Builder newFetchSourceTask() {
        return new Builder();
    }

    public static class Builder {

        private String sourceName;

        private Boolean isAnnotatedNode;

        private Boolean isListType;

        private Boolean isInList;

        private CompletableFuture<Object> taskFuture;

        private String mapper;

        private FetchSourceTask parentTask;

        public Builder sourceName(String sourceName) {
            this.sourceName = sourceName;
            return this;
        }

        public Builder isAnnotatedNode(boolean isAnnotatedNode) {
            this.isAnnotatedNode = isAnnotatedNode;
            return this;
        }

        public Builder isListType(boolean isListType) {
            this.isListType = isListType;
            return this;
        }

        public Builder isInList(boolean isInList) {
            this.isInList = isInList;
            return this;
        }

        public Builder taskFuture(CompletableFuture<Object> taskFuture) {
            this.taskFuture = taskFuture;
            return this;
        }

        public Builder parentTask(FetchSourceTask parentTask) {
            this.parentTask = parentTask;
            return this;
        }

        public Builder mapper(String mapper) {
            this.mapper = mapper;
            return this;
        }

        public FetchSourceTask build() {
            return new FetchSourceTask(
                    Objects.requireNonNull(sourceName),
                    Objects.requireNonNull(isAnnotatedNode),
                    Objects.requireNonNull(isListType),
                    Objects.requireNonNull(taskFuture),
                    mapper,parentTask
            );
        }
    }
}
