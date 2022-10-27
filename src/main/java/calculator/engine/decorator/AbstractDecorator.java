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

package calculator.engine.decorator;

import calculator.engine.ExecutionEngineState;
import calculator.engine.annotation.Internal;
import calculator.engine.metadata.FetchSourceTask;
import graphql.execution.DataFetcherResult;
import graphql.execution.ValueUnboxer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static java.util.stream.Collectors.toList;

@Internal
public abstract class AbstractDecorator implements Decorator {

    private static final Object DUMMY_VALUE = new Object();

    protected Object unWrapDataFetcherResult(Object originalResult, ValueUnboxer valueUnboxer) {
        Object nonFutureResult = originalResult instanceof CompletionStage
                ? ((CompletionStage<?>) originalResult).toCompletableFuture().join()
                : originalResult;

        Object fetchData = nonFutureResult instanceof DataFetcherResult
                ? ((DataFetcherResult<?>) nonFutureResult).getData()
                : nonFutureResult;

        return valueUnboxer.unbox(fetchData);
    }

    protected Object wrapResult(Object originalResult, Object data) {
        if (originalResult instanceof DataFetcherResult) {
            return DataFetcherResult.newResult()
                    .errors(((DataFetcherResult<?>) originalResult).getErrors())
                    .localContext(((DataFetcherResult<?>) originalResult).getLocalContext())
                    .data(data)
                    .build();
        }

        return data;
    }

    protected FetchSourceTask getFetchSourceFromState(ExecutionEngineState engineState, String sourceName) {
        Map<String, FetchSourceTask> fetchSourceTaskByPath = engineState.getFetchSourceTaskByPath();
        Map<String, List<String>> queryTaskBySourceName = engineState.getQueryTaskBySourceName();
        List<String> queryTaskNameList = queryTaskBySourceName.get(sourceName);

        List<CompletableFuture<Object>> queryTaskList = queryTaskNameList.stream()
                .map(fieldPath -> fetchSourceTaskByPath.get(fieldPath).getTaskFuture())
                .collect(toList());

        Map<String, List<String>> topTaskBySourceName = engineState.getTopTaskBySourceName();
        List<String> topTaskNameList = topTaskBySourceName.get(sourceName);
        List<FetchSourceTask> topTaskList = topTaskNameList.stream().map(fetchSourceTaskByPath::get).collect(toList());
        FetchSourceTask valueTask = topTaskList.get(topTaskList.size() - 1);

        for (CompletableFuture<Object> queryTask : queryTaskList) {
            queryTask.handle((result, ex) -> {
                if (ex != null) {
                    valueTask.getTaskFuture().completeExceptionally(ex);
                }

                if (result == null) {
                    valueTask.getTaskFuture().complete(null);
                }
                return DUMMY_VALUE;
            }).join();

            if (valueTask.getTaskFuture().isDone()) {
                return valueTask;
            }
        }

        for (FetchSourceTask taskInValuePath : topTaskList) {
            taskInValuePath.getTaskFuture().handle((result, ex) -> {
                        if (ex != null) {
                            valueTask.getTaskFuture().completeExceptionally(ex);
                        }

                        if (result == null) {
                            valueTask.getTaskFuture().complete(null);
                        }
                        return DUMMY_VALUE;
                    }
            ).join();

            if (valueTask.getTaskFuture().isDone()) {
                return valueTask;
            }
        }

        return valueTask;
    }
}
