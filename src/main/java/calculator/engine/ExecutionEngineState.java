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
package calculator.engine;

import calculator.engine.metadata.NodeTask;
import graphql.execution.instrumentation.InstrumentationState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Keep task for schedule.
 */
public class ExecutionEngineState implements InstrumentationState {

    public static final String FUNCTION_KEY = "wrapperState";

    /**
     * 一个节点依赖的字段绝对路径、从外到内<nodeName,List<absolutePath>>
     * <p>
     * Keys of tasks which the node depends on.
     */
    private final Map<String, List<String>> sequenceTaskByNode;

    /**
     * 字段路径对应的异步任务
     * <p>
     * task by field absolute path.
     */
    private final Map<String, NodeTask> taskByPath;

    public ExecutionEngineState(Map<String, List<String>> sequenceTaskByNode, Map<String, NodeTask> taskByPath) {
        this.sequenceTaskByNode = Collections.unmodifiableMap(sequenceTaskByNode);
        this.taskByPath = Collections.unmodifiableMap(taskByPath);
    }

    public Map<String, List<String>> getSequenceTaskByNode() {
        return sequenceTaskByNode;
    }

    public Map<String, NodeTask> getTaskByPath() {
        return taskByPath;
    }

    public static Builder newExecutionState() {
        return new Builder();
    }

    public static class Builder {
        private Map<String, List<String>> sequenceTaskByNode = new LinkedHashMap<>();

        private Map<String, NodeTask> taskByPath = new ConcurrentHashMap<>();

        public Builder sequenceTaskByNode(String nodeName, List<String> taskList) {
            sequenceTaskByNode.put(nodeName, taskList);
            return this;
        }

        public Builder taskByPath(String nodeName, NodeTask task) {
            taskByPath.put(nodeName, task);
            return this;
        }

        public ExecutionEngineState build() {
            return new ExecutionEngineState(sequenceTaskByNode, taskByPath);
        }
    }
}
