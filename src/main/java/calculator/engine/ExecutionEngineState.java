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


import calculator.engine.metadata.FetchSourceTask;
import graphql.execution.instrumentation.InstrumentationState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;


public class ExecutionEngineState implements InstrumentationState {

    private final Map<String, FetchSourceTask> fetchSourceTaskByPath;

    private final Map<String, List<String>> topTaskBySourceName;

    // <sourceName,List<fullFieldPath>>
    private final Map<String, List<String>> queryTaskBySourceName;

    private ExecutionEngineState(
            Map<String, FetchSourceTask> fetchSourceTaskByPath,
            Map<String, List<String>> topTaskByNode,
            Map<String, List<String>> queryTaskByNode
    ) {
        this.fetchSourceTaskByPath = Collections.unmodifiableMap(fetchSourceTaskByPath);
        this.topTaskBySourceName = Collections.unmodifiableMap(topTaskByNode);
        this.queryTaskBySourceName = Collections.unmodifiableMap(queryTaskByNode);
    }


    public Map<String, FetchSourceTask> getFetchSourceTaskByPath() {
        return fetchSourceTaskByPath;
    }

    public Map<String, List<String>> getTopTaskBySourceName() {
        return topTaskBySourceName;
    }

    public Map<String, List<String>> getQueryTaskBySourceName() {
        return queryTaskBySourceName;
    }

    public static Builder newExecutionState() {
        return new Builder();
    }

    public static class Builder {

        private Map<String, FetchSourceTask> fetchSourceTaskByPath = new ConcurrentHashMap<>();

        private Map<String, List<String>> topTaskBySourceName = new LinkedHashMap<>();

        private Map<String, List<String>> queryTaskBySourceName = new LinkedHashMap<>();

        public Builder fetchSourceTask(String fieldFullPath, FetchSourceTask fetchSourceTask) {
            fetchSourceTaskByPath.put(fieldFullPath, fetchSourceTask);
            return this;
        }

        public Builder taskByPathIfAbsent(String fieldFullPath, Supplier<FetchSourceTask> fetchSourceTaskSupplier) {
            if (!fetchSourceTaskByPath.containsKey(fieldFullPath)) {
                fetchSourceTaskByPath.put(fieldFullPath, fetchSourceTaskSupplier.get());
            }

            return this;
        }

        public Builder taskByPathIfAbsent(String fieldFullPath, FetchSourceTask fetchSourceTask) {
            if (!fetchSourceTaskByPath.containsKey(fieldFullPath)) {
                fetchSourceTaskByPath.put(fieldFullPath, fetchSourceTask);
            }

            return this;
        }


        public Builder topTaskList(String sourceName, List<String> topTaskList) {
            topTaskBySourceName.put(sourceName, topTaskList);
            return this;
        }

        public Builder queryTaskList(String sourceName, List<String> queryTaskList) {
            queryTaskBySourceName.put(sourceName, queryTaskList);
            return this;
        }

        public ExecutionEngineState build() {
            return new ExecutionEngineState(fetchSourceTaskByPath, topTaskBySourceName, queryTaskBySourceName);
        }
    }
}
