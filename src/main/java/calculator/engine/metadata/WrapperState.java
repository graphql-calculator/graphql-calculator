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

import graphql.execution.instrumentation.InstrumentationState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keep task for schedule.
 **/
public class WrapperState implements InstrumentationState {

    public static final String FUNCTION_KEY = "wrapperState";

    /**
     * 字段路径，对应的异步任务
     *
     * task by field absolute path.
     */
    private final Map<String, FutureTask<Object>> taskByPath = new ConcurrentHashMap<>();

    /**
     * 一个节点依赖的字段绝对路径、从外到内<nodeName,List<absolutePath>>
     *
     * Keys of tasks which the node depends on.
     */
    private final Map<String, List<String>> sequenceTaskByNode = new HashMap<>();

    public Map<String, FutureTask<Object>> getTaskByPath() {
        return taskByPath;
    }

    public Map<String, List<String>> getSequenceTaskByNode() {
        return sequenceTaskByNode;
    }
}
