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

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class FutureTask<T> {

    // 是否是query下的字段
    // 当前字段所表示的任务执行完毕、则其子实体(数组实体)所表示的任务则也一定完成
    private boolean isTopNode;

    // for debug
    private String path;

    // 是否是list下的元素
    private boolean isList;

    private List tmpResult;

    private CompletableFuture<T> future;

    private FutureTask parent;

    private List<FutureTask> subTaskList = new LinkedList<>();

    public void addSubTaskList(FutureTask subTask) {
        subTaskList.add(subTask);
    }

    public List<FutureTask> getSubTaskList() {
        return subTaskList;
    }

    private FutureTask(boolean isTopNode, String path, boolean isList, CompletableFuture<T> future, FutureTask parent) {
        this.path = path;
        this.isList = isList;
        tmpResult = new LinkedList();
        this.future = future;
        this.parent = parent;
    }


    public boolean isTopNode() {
        return isTopNode;
    }

    public String getPath() {
        return path;
    }

    public boolean isList() {
        return isList;
    }

    public List getTmpResult() {
        return tmpResult;
    }

    public CompletableFuture<T> getFuture() {
        return future;
    }

    public FutureTask getParent() {
        return parent;
    }

    public static FutureTaskBuilder newBuilder() {
        return new FutureTaskBuilder();
    }

    public static class FutureTaskBuilder<T> {

        private boolean isTopNode;

        private String path;

        private Boolean isList;

        private CompletableFuture future;

        private FutureTask parent;


        public FutureTaskBuilder isTopNode(boolean isTopNode) {
            this.isTopNode = isTopNode;
            return this;
        }

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

        public FutureTaskBuilder parent(FutureTask parent) {
            this.parent = parent;
            return this;
        }

        public FutureTask build() {
            Objects.requireNonNull(path);
            Objects.requireNonNull(isList);
            Objects.requireNonNull(future);

            return new FutureTask(isTopNode, path, isList, future, parent);
        }

    }
}
