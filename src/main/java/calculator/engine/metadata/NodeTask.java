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

public class NodeTask {

    // 当前字段所表示的任务执行完毕、则其子实体(数组实体)所表示的任务则也一定完成
    private final boolean isTopTaskNode;

    // for debug
    private final String path;

    // 当前节点是否是 list节点、并且不是叶子节点
    private final boolean isList;

    // 当前节点是否是被 @node 注视的节点
    private final boolean isAnnotated;

    // 需要保证线程安全
    // 如果是list路径下的节点，则在最后一个节点计算完成前，将结果暂存在 tmpResult 中
    private final List<Object> tmpResult = Collections.synchronizedList(new ArrayList<>());

    private final CompletableFuture<Object> future;

    private final NodeTask parent;

    private final List<NodeTask> subTaskList = new ArrayList<>();

    public void addSubTaskList(NodeTask subTask) {
        subTaskList.add(subTask);
    }

    public List<NodeTask> getSubTaskList() {
        return subTaskList;
    }

    private NodeTask(boolean isTopTaskNode, String path, boolean isList, boolean isAnnotated, CompletableFuture<Object> future, NodeTask parent) {
        this.isTopTaskNode = isTopTaskNode;
        this.path = path;
        this.isList = isList;
        this.isAnnotated = isAnnotated;
        this.future = future;
        this.parent = parent;
    }


    public boolean isTopTaskNode() {
        return isTopTaskNode;
    }

    public String getPath() {
        return path;
    }

    public boolean isList() {
        return isList;
    }

    public boolean isAnnotated() {
        return isAnnotated;
    }

    public List getTmpResult() {
        return tmpResult;
    }

    public CompletableFuture<Object> getFuture() {
        return future;
    }

    public NodeTask getParent() {
        return parent;
    }

    public static FutureTaskBuilder newBuilder() {
        return new FutureTaskBuilder();
    }

    public static class FutureTaskBuilder {

        private Boolean isTopTaskNode;

        private String path;

        private Boolean isList;

        private Boolean isAnnotated;

        private CompletableFuture<Object> future;

        private NodeTask parent;


        public FutureTaskBuilder isTopTaskNode(boolean isTopTaskNode) {
            this.isTopTaskNode = isTopTaskNode;
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

        public FutureTaskBuilder isAnnotated(boolean val) {
            this.isAnnotated = val;
            return this;
        }

        public FutureTaskBuilder future(CompletableFuture<Object> future) {
            this.future = future;
            return this;
        }

        public FutureTaskBuilder parent(NodeTask parent) {
            this.parent = parent;
            return this;
        }

        public NodeTask build() {
            Objects.requireNonNull(isTopTaskNode);
            Objects.requireNonNull(path);
            Objects.requireNonNull(isList);
            Objects.requireNonNull(isAnnotated);
            Objects.requireNonNull(future);

            return new NodeTask(isTopTaskNode, path, isList, isAnnotated, future, parent);
        }
    }
}
