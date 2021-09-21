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

import calculator.engine.annotation.Internal;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;


@Internal
public class FetchSourceTask {

    private static final Object DUMMY_VALUE = new Object();

    private final String sourceName;

    private final boolean isAnnotatedNode;

    private final boolean isListType;

    private final boolean isInList;

    private final boolean isTopTask;

    private final CompletableFuture<Object> taskFuture;

    private final ArrayList<CompletableFuture<Object>> listElementFutures = new ArrayList<>();

    private final String mapper;

    private final String resultKey;

    private final FetchSourceTask parentTask;

    private final ArrayList<FetchSourceTask> childrenTaskList = new ArrayList<>();

    private FetchSourceTask(String sourceName,
                            boolean isAnnotatedNode,
                            boolean isListType,
                            boolean isInList,
                            boolean isTopTask,
                            CompletableFuture<Object> taskFuture,
                            String mapper,
                            String resultKey,
                            FetchSourceTask parentTask) {
        this.sourceName = sourceName;
        this.isAnnotatedNode = isAnnotatedNode;
        this.isListType = isListType;
        this.isInList = isInList;
        this.isTopTask = isTopTask;
        this.taskFuture = Objects.requireNonNull(taskFuture);
        this.mapper = mapper;
        this.resultKey = resultKey;
        this.parentTask = parentTask;
    }

    public String getSourceName() {
        return sourceName;
    }

    public boolean isAnnotatedNode() {
        return isAnnotatedNode;
    }

    public boolean isListType() {
        return isListType;
    }

    public boolean isInList() {
        return isInList;
    }

    public boolean isTopTask() {
        return isTopTask;
    }

    public CompletableFuture<Object> getTaskFuture() {
        return taskFuture;
    }

    public String getMapper() {
        return mapper;
    }

    public String getResultKey() {
        return resultKey;
    }

    public FetchSourceTask getParentTask() {
        return parentTask;
    }

    public ArrayList<FetchSourceTask> getChildrenTaskList() {
        return childrenTaskList;
    }

    public ArrayList<CompletableFuture<Object>> getListElementFutures() {
        return listElementFutures;
    }

    public void completeWithDummyValue() {
        taskFuture.complete(DUMMY_VALUE);
    }

    public synchronized void addListElementResultFuture(CompletableFuture<Object> elementFuture) {
        listElementFutures.add(elementFuture);
    }

    public synchronized void addChildrenTaskList(FetchSourceTask sourceTask) {
        childrenTaskList.add(sourceTask);
    }

    public static Builder newFetchSourceTask() {
        return new Builder();
    }

    public static class Builder {

        private String sourceName;

        private boolean isAnnotatedNode;

        private boolean isListType;

        private boolean isInList;

        private boolean isTopTask;

        private CompletableFuture<Object> taskFuture;

        private String mapper;

        private String resultKey;

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

        public Builder isTopTask(boolean isTopTask) {
            this.isTopTask = isTopTask;
            return this;
        }

        public Builder taskFuture(CompletableFuture<Object> taskFuture) {
            this.taskFuture = Objects.requireNonNull(taskFuture);
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

        public Builder resultKey(String resultKey) {
            this.resultKey = resultKey;
            return this;
        }

        public FetchSourceTask build() {
            return new FetchSourceTask(
                    sourceName,
                    isAnnotatedNode,
                    isListType,
                    isInList,
                    isTopTask,
                    taskFuture,
                    mapper,
                    resultKey,
                    parentTask
            );
        }
    }
}
