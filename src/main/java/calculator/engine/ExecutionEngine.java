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

import calculator.config.Config;
import calculator.engine.metadata.Directives;
import calculator.engine.metadata.FetchSourceTask;
import calculator.graphql.AsyncDataFetcher;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import graphql.ExecutionResult;
import graphql.analysis.QueryTraverser;
import graphql.execution.DataFetcherResult;
import graphql.execution.ResultPath;
import graphql.execution.ValueUnboxer;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.language.Directive;
import graphql.parser.Parser;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import static calculator.common.CommonUtil.arraySize;
import static calculator.common.CommonUtil.fieldPath;
import static calculator.common.CommonUtil.getArgumentFromDirective;
import static calculator.engine.function.ExpProcessor.calExp;
import static calculator.engine.metadata.Directives.ARGUMENT_TRANSFORM;
import static calculator.engine.metadata.Directives.FILTER;
import static calculator.engine.metadata.Directives.MAP;
import static calculator.engine.metadata.Directives.MOCK;
import static calculator.engine.metadata.Directives.SKIP_BY;
import static calculator.engine.metadata.Directives.SORT;
import static calculator.engine.metadata.Directives.SORT_BY;
import static calculator.graphql.AsyncDataFetcher.async;
import static java.util.stream.Collectors.toList;

public class ExecutionEngine extends SimpleInstrumentation {

    private final Executor executor;

    private final ObjectMapper objectMapper;

    private final AviatorEvaluatorInstance aviatorEvaluator;

    private ExecutionEngine(Executor executor, ObjectMapper objectMapper, AviatorEvaluatorInstance aviatorEvaluator) {
        this.executor = Objects.requireNonNull(executor);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.aviatorEvaluator = Objects.requireNonNull(aviatorEvaluator);
    }

    public static ExecutionEngine newInstance(Config config) {
        return new ExecutionEngine(config.getExecutor(), config.getObjectMapper(), config.getAviatorEvaluator());
    }

    // ============================================== create InstrumentationState for engine  ==============================================
    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {

        String query = parameters.getExecutionInput().getQuery();
        QueryTraverser traverser = QueryTraverser.newQueryTraverser()
                .schema(parameters.getSchema())
                .document(Parser.parse(query))
                .variables(Collections.emptyMap()).build();

        ExecutionEngineStateParser stateParser = new ExecutionEngineStateParser();
        traverser.visitDepthFirst(stateParser);
        return stateParser.getExecutionEngineState();
    }

    // ============================================== alter InstrumentationState for engine  ================================================
    @Override
    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        return saveFetchedValueContext(
                parameters.getInstrumentationState(),
                parameters.getExecutionStepInfo().getPath(),
                parameters.getEnvironment().getField().getResultKey()
        );
    }


    @Override
    public ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        return new ExecutionStrategyInstrumentationContext() {
            @Override
            public void onDispatched(CompletableFuture<ExecutionResult> result) {
                String fieldFullPath = fieldPath(parameters.getExecutionStrategyParameters().getPath());
                FetchSourceTask fetchSourceTask = parseFetchSourceTask(
                        parameters.getInstrumentationState(), fieldFullPath
                );
                if (fetchSourceTask == null) {
                    return;
                }

                if (fetchSourceTask.isTopTask()) {
                    completeChildrenTask(fetchSourceTask);
                }
            }

            @Override
            public void onCompleted(ExecutionResult result, Throwable t) {

            }
        };
    }

    private InstrumentationContext<Object> saveFetchedValueContext(ExecutionEngineState engineState, ResultPath resultPath, String resultKey) {
        return new InstrumentationContext<Object>() {

            @Override
            public void onDispatched(CompletableFuture<Object> future) {
                String fieldFullPath = fieldPath(resultPath);
                FetchSourceTask sourceTask = parseFetchSourceTask(engineState, fieldFullPath);
                if (sourceTask == null) {
                    return;
                }

                if (sourceTask.isInList()) {
                    sourceTask.addListElementResultFuture(future);
                } else {
                    future.whenComplete((result, ex) -> {
                        if (ex != null) {
                            sourceTask.getTaskFuture().completeExceptionally(ex);
                            return;
                        }

                        if (sourceTask.getMapper() == null) {
                            sourceTask.getTaskFuture().complete(result);
                        } else {
                            try {
                                Object mappedValue = aviatorEvaluator.execute(
                                        sourceTask.getMapper(), getCalMap(result), true
                                );
                                sourceTask.getTaskFuture().complete(mappedValue);
                            } catch (Throwable t) {
                                sourceTask.getTaskFuture().completeExceptionally(t);
                            }
                        }
                    });
                }

            }

            @Override
            public void onCompleted(Object result, Throwable t) {
            }
        };
    }

    private FetchSourceTask parseFetchSourceTask(ExecutionEngineState engineState, String fieldFullPath) {
        return engineState.getFetchSourceTaskByPath().get(fieldFullPath);
    }

    private void completeChildrenTask(FetchSourceTask sourceTask) {
        for (FetchSourceTask child : sourceTask.getChildrenTaskList()) {
            completeChildrenTask(child);

            if (child.getTaskFuture().isDone()) {
                continue;
            }

            if (!child.isAnnotatedNode()) {
                child.completeWithDummyValue();
            }

            ArrayList<CompletableFuture<Object>> elementResultFuture = child.getListElementFutures();
            CompletableFuture[] elementFutureArray = elementResultFuture.toArray(new CompletableFuture[0]);
            CompletableFuture.allOf(elementFutureArray).whenComplete((ignore, ex) -> {
                if (ex != null) {
                    child.getTaskFuture().completeExceptionally(ex);
                    return;
                }

                ArrayList<Object> listResult = new ArrayList<>(elementResultFuture.size());
                for (CompletableFuture<Object> elementFuture : elementResultFuture) {
                    listResult.add(elementFuture.join());
                }

                if (child.getMapper() == null) {
                    child.getTaskFuture().complete(listResult);
                } else {
                    try {
                        Object mappedValue = aviatorEvaluator.execute(
                                child.getMapper(), Collections.singletonMap(sourceTask.getMapperKey(), listResult)
                        );
                        child.getTaskFuture().complete(mappedValue);
                    } catch (Throwable t) {
                        child.getTaskFuture().completeExceptionally(t);
                    }
                }
            });
        }
    }


    // ============================================== alter runtime execution for engine  ================================================


    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
        List<Directive> directives = parameters.getEnvironment().getField().getDirectives();
        if (!directives.isEmpty()) {
            return wrappedDataFetcher(
                    directives,
                    parameters.getEnvironment(),
                    dataFetcher,
                    parameters.getInstrumentationState(),
                    parameters.getExecutionContext().getValueUnboxer()
            );
        }

        return dataFetcher;
    }

    private DataFetcher<?> wrappedDataFetcher(List<Directive> dirOnField,
                                              DataFetchingEnvironment environment,
                                              DataFetcher<?> dataFetcher,
                                              ExecutionEngineState engineState,
                                              ValueUnboxer valueUnboxer) {

        for (Directive directive : dirOnField) {
            if (Objects.equals(SKIP_BY.getName(), directive.getName())) {
                String expression = getArgumentFromDirective(directive, "expression");
                String dependencySource = getArgumentFromDirective(directive, "dependencySource");
                dataFetcher = wrapSkipDataFetcher(dataFetcher, expression, engineState,dependencySource);
                continue;
            }

            if (Objects.equals(MOCK.getName(), directive.getName())) {
                dataFetcher = ignore -> getArgumentFromDirective(directive, "value");
                continue;
            }

            if (Objects.equals(FILTER.getName(), directive.getName())) {
                String predicate = getArgumentFromDirective(directive, "predicate");
                String dependencySource = getArgumentFromDirective(directive, "dependencySource");
                dataFetcher = wrapFilterDataFetcher(
                        dataFetcher, predicate, dependencySource, engineState
                );
                continue;
            }

            if (Objects.equals(SORT.getName(), directive.getName())) {
                Supplier<Boolean> defaultReversed = () -> (Boolean) SORT.getArgument("reversed").getDefaultValue();
                String sortKey = getArgumentFromDirective(directive, "key");
                Boolean reversed = getArgumentFromDirective(directive, "reversed");
                reversed = reversed != null ? reversed : defaultReversed.get();
                dataFetcher = wrapSortDataFetcher(dataFetcher, sortKey, reversed);
                continue;
            }

            if (Objects.equals(SORT_BY.getName(), directive.getName())) {
                String expression = getArgumentFromDirective(directive, "expression");
                Boolean reversed = getArgumentFromDirective(directive, "reversed");
                reversed = reversed != null
                        ? reversed
                        : (Boolean) SORT_BY.getArgument("reversed").getDefaultValue();
                String dependencySource = getArgumentFromDirective(directive, "dependencySource");
                dataFetcher = wrapSortByDataFetcher(
                        dataFetcher, expression, reversed, dependencySource, engineState, valueUnboxer
                );
                continue;
            }

            if (Objects.equals(MAP.getName(), directive.getName())) {
                String expression = getArgumentFromDirective(directive, "expression");
                String dependencySource = getArgumentFromDirective(directive, "dependencySource");
                dataFetcher = wrapMapDataFetcher(
                        dataFetcher, expression, dependencySource, engineState
                );
                continue;
            }

            if (Objects.equals(ARGUMENT_TRANSFORM.getName(), directive.getName())) {
                String argumentName = getArgumentFromDirective(directive, "argumentName");
                String operateType = getArgumentFromDirective(directive, "operateType");
                String expression = getArgumentFromDirective(directive, "expression");
                String dependencySource = getArgumentFromDirective(directive, "dependencySource");
                dataFetcher = wrapArgumentTransformDataFetcher(
                        argumentName, operateType, expression, dependencySource, dataFetcher, engineState
                );
                continue;
            }

        }

        return dataFetcher;
    }


    private DataFetcher<?> wrapSkipDataFetcher(DataFetcher<?> dataFetcher, String expression, ExecutionEngineState engineState, String dependencySource) {
        boolean isAsyncFetcher = dataFetcher instanceof AsyncDataFetcher;
        Executor innerExecutor = isAsyncFetcher ? ((AsyncDataFetcher<?>) dataFetcher).getExecutor() : executor;
        DataFetcher<?> innerDataFetcher = isAsyncFetcher ? ((AsyncDataFetcher<?>) dataFetcher).getWrappedDataFetcher() : dataFetcher;


        DataFetcher<?> wrappedDataFetcher = environment -> {
            Map<String, Object> env = new LinkedHashMap<>(environment.getArguments());
            if (dependencySource != null) {
                FetchSourceTask sourceTask = getFetchSourceFromState(engineState, dependencySource);
                if (sourceTask.getTaskFuture().isCompletedExceptionally()) {
                    env.put(dependencySource, null);
                } else {
                    env.put(dependencySource, sourceTask.getTaskFuture().join());
                }
            }

            Boolean isSkip = (Boolean) calExp(aviatorEvaluator, expression, env);
            if (isSkip) {
                return null;
            }

            return innerDataFetcher.get(environment);
        };

        if (isAsyncFetcher) {
            return async(wrappedDataFetcher, innerExecutor);
        }
        return innerDataFetcher;
    }


    private DataFetcher<?> wrapFilterDataFetcher(DataFetcher<?> defaultDF,
                                                 String predicate,
                                                 String dependencySource,
                                                 ExecutionEngineState engineState) {

        boolean isAsyncFetcher = defaultDF instanceof AsyncDataFetcher;
        Executor innerExecutor = isAsyncFetcher ? ((AsyncDataFetcher<?>) defaultDF).getExecutor() : executor;
        DataFetcher<?> innerDataFetcher = isAsyncFetcher ? ((AsyncDataFetcher<?>) defaultDF).getWrappedDataFetcher() : defaultDF;

        DataFetcher<?> wrappedFetcher = environment -> {
            Object originalResult = innerDataFetcher.get(environment);

            if (originalResult == null) {
                return null;
            }

            Map<String, Object> sourceEnv = null;
            if (dependencySource != null) {
                FetchSourceTask sourceTask = getFetchSourceFromState(engineState, dependencySource);
                if (sourceTask.getTaskFuture().isCompletedExceptionally()) {
                    sourceEnv = Collections.singletonMap(dependencySource, null);
                } else {
                    sourceEnv = Collections.singletonMap(dependencySource, sourceTask.getTaskFuture().join());
                }
            }

            List<Object> filteredList = new ArrayList<>();
            for (Object ele : (Collection<?>) originalResult) {
                Map<String, Object> fieldMap = getCalMap(ele);
                // 如果为null则表示没有依赖的节点
                // 此时不应该在进行putAll操作
                if (sourceEnv != null) {
                    fieldMap.putAll(sourceEnv);
                }
                if ((Boolean) calExp(aviatorEvaluator, predicate, fieldMap)) {
                    filteredList.add(ele);
                }
            }
            return filteredList;
        };

        if (isAsyncFetcher || dependencySource != null) {
            return async(wrappedFetcher, innerExecutor);
        }
        return wrappedFetcher;
    }

    private DataFetcher<?> wrapSortDataFetcher(DataFetcher<?> defaultDF, String sortKey, Boolean reversed) {
        boolean isAsyncFetcher = defaultDF instanceof AsyncDataFetcher;
        Executor innerExecutor = isAsyncFetcher ? ((AsyncDataFetcher<?>) defaultDF).getExecutor() : executor;
        DataFetcher<?> innerDataFetcher = isAsyncFetcher ? ((AsyncDataFetcher<?>) defaultDF).getWrappedDataFetcher() : defaultDF;

        DataFetcher<?> wrappedDataFetcher = environment -> {
            Object originalResult = innerDataFetcher.get(environment);

            if (originalResult == null) {
                return null;
            }

            Collection<?> collection = (Collection<?>) originalResult;
            if (reversed) {
                return collection.stream().sorted(
                        Comparator.comparing(ele -> (Comparable) getCalMap(ele).get(sortKey)).reversed()
                ).collect(toList());
            }

            return collection.stream().sorted(
                    Comparator.comparing(ele -> (Comparable) getCalMap(ele).get(sortKey))
            ).collect(toList());
        };

        if (isAsyncFetcher) {
            return async(wrappedDataFetcher, innerExecutor);
        }
        return wrappedDataFetcher;
    }


    private DataFetcher<?> wrapSortByDataFetcher(DataFetcher<?> defaultDF,
                                                 String sortExp,
                                                 Boolean reversed,
                                                 String dependencySource,
                                                 ExecutionEngineState engineState,
                                                 ValueUnboxer valueUnboxer) {

        boolean isAsyncFetcher = defaultDF instanceof AsyncDataFetcher;
        Executor innerExecutor = isAsyncFetcher ? ((AsyncDataFetcher<?>) defaultDF).getExecutor() : executor;
        DataFetcher<?> innerDataFetcher = isAsyncFetcher ? ((AsyncDataFetcher<?>) defaultDF).getWrappedDataFetcher() : defaultDF;

        DataFetcher<?> wrappedDataFetcher = environment -> {
            Object originalResult = innerDataFetcher.get(environment);

            Collection<Object> collectionData;
            if (originalResult instanceof DataFetcherResult) {
                collectionData = (Collection<Object>) valueUnboxer.unbox(((DataFetcherResult) originalResult).getData());
            } else {
                collectionData = (Collection<Object>) valueUnboxer.unbox(originalResult);
            }

            if (arraySize(collectionData) == 0) {
                return originalResult;
            }

            final Map<String, Object> sourceEnv;
            if (dependencySource != null) {
                FetchSourceTask sourceTask = getFetchSourceFromState(engineState, dependencySource);
                if (sourceTask.getTaskFuture().isCompletedExceptionally()) {
                    sourceEnv = Collections.singletonMap(dependencySource, null);
                } else {
                    sourceEnv = Collections.singletonMap(dependencySource, sourceTask.getTaskFuture().join());
                }
            } else {
                sourceEnv = null;
            }

            List<Object> sortedList;
            if (reversed) {
                sortedList = collectionData.stream().sorted(Comparator.comparing(ele -> {
                    Map<String, Object> env = getCalMap(ele);
                    if (sourceEnv != null) {
                        env.putAll(sourceEnv);
                    }
                    return (Comparable<Object>) aviatorEvaluator.execute(sortExp, env);
                }).reversed()).collect(toList());
            } else {
                sortedList = collectionData.stream().sorted(
                        Comparator.comparing(ele -> {
                            Map<String, Object> env = getCalMap(ele);
                            if (sourceEnv != null) {
                                env.putAll(sourceEnv);
                            }
                            return (Comparable<Object>) aviatorEvaluator.execute(sortExp, env);
                        })
                ).collect(toList());
            }

            if (originalResult instanceof DataFetcherResult) {
                return DataFetcherResult.newResult()
                        .errors(((DataFetcherResult<?>) originalResult).getErrors())
                        .localContext(((DataFetcherResult<?>) originalResult).getLocalContext())
                        .data(sortedList)
                        .build();
            }

            return sortedList;

        };

        if (isAsyncFetcher || dependencySource != null) {
            return async(wrappedDataFetcher, innerExecutor);
        }

        return wrappedDataFetcher;
    }

    // directive @map(mapper:String!, dependencySource:String) on FIELD
    private DataFetcher<?> wrapMapDataFetcher(DataFetcher<?> defaultDF,
                                              String expression,
                                              String dependencySource,
                                              ExecutionEngineState engineState) {

        boolean isAsyncFetcher = defaultDF instanceof AsyncDataFetcher;
        Executor innerExecutor = isAsyncFetcher ? ((AsyncDataFetcher<?>) defaultDF).getExecutor() : executor;
        DataFetcher<?> innerDataFetcher = isAsyncFetcher ? ((AsyncDataFetcher<?>) defaultDF).getWrappedDataFetcher() : defaultDF;

        DataFetcher<?> wrappedDataFetcher = environment -> {
            Object fieldValue = innerDataFetcher.get(environment);

            Map<String, Object> sourceEnv = null;
            if (dependencySource != null) {
                FetchSourceTask sourceTask = getFetchSourceFromState(engineState, dependencySource);
                if (sourceTask.getTaskFuture().isCompletedExceptionally()) {
                    sourceEnv = Collections.singletonMap(dependencySource, null);
                } else {
                    sourceEnv = Collections.singletonMap(dependencySource, sourceTask.getTaskFuture().join());
                }
            }

            HashMap<String, Object> expEnv = new HashMap<>(getCalMap(environment.getSource()));
            expEnv.put(environment.getField().getResultKey(), fieldValue);
            if (sourceEnv != null) {
                expEnv.putAll(sourceEnv);
            }

            return calExp(aviatorEvaluator, expression, expEnv);
        };

        if (isAsyncFetcher || dependencySource != null) {
            return async(wrappedDataFetcher, innerExecutor);
        }

        return wrappedDataFetcher;
    }

    // TODO ele常量需要有说明
    //  typeName: String -> Enum
    private DataFetcher<?> wrapArgumentTransformDataFetcher(String argumentName,
                                                            String operateType,
                                                            String expression,
                                                            String dependencySource,
                                                            DataFetcher<?> defaultDF,
                                                            ExecutionEngineState engineState) {
        boolean isAsyncFetcher = defaultDF instanceof AsyncDataFetcher;
        Executor innerExecutor = isAsyncFetcher ? ((AsyncDataFetcher<?>) defaultDF).getExecutor() : executor;
        DataFetcher<?> innerDataFetcher = isAsyncFetcher ? ((AsyncDataFetcher<?>) defaultDF).getWrappedDataFetcher() : defaultDF;

        DataFetcher<?> wrappedFetcher = environment -> {

            final Map<String, Object> sourceEnv;
            if (dependencySource != null) {
                FetchSourceTask sourceTask = getFetchSourceFromState(engineState, dependencySource);
                if (sourceTask.getTaskFuture().isCompletedExceptionally()) {
                    sourceEnv = Collections.singletonMap(dependencySource, null);
                } else {
                    sourceEnv = Collections.singletonMap(dependencySource, sourceTask.getTaskFuture().join());
                }
            } else {
                sourceEnv = null;
            }

            // filter list element of list argument
            if (Objects.equals(operateType, Directives.ParamTransformType.FILTER.name())) {
                List<Object> argument = environment.getArgument(argumentName);
                if (argument == null || argument.isEmpty()) {
                    return innerDataFetcher.get(environment);
                }

                argument = argument.stream().filter(ele -> {
                            HashMap<String, Object> env = new HashMap<>();
                            env.put("ele", ele);
                            if (sourceEnv != null) {
                                env.putAll(sourceEnv);
                            }

                            return (Boolean) aviatorEvaluator.execute(expression, env);
                        }
                ).collect(toList());

                Map<String, Object> newArguments = new HashMap<>(environment.getArguments());
                newArguments.put(argumentName, argument);
                DataFetchingEnvironment newEnvironment = DataFetchingEnvironmentImpl
                        .newDataFetchingEnvironment(environment).arguments(newArguments).build();
                return innerDataFetcher.get(newEnvironment);
            }

            // map each element of list argument
            if (Objects.equals(operateType, Directives.ParamTransformType.LIST_MAP.name())) {
                List<Object> argument = environment.getArgument(argumentName);
                if (argument == null || argument.isEmpty()) {
                    return innerDataFetcher.get(environment);
                }

                argument = argument.stream().map(ele -> {
                    Map<String, Object> transformEnv = new HashMap<>();
                    if (sourceEnv != null) {
                        transformEnv.putAll(sourceEnv);
                    }
                    transformEnv.put("ele", ele);
                    return aviatorEvaluator.execute(expression, transformEnv);
                }).collect(toList());

                Map<String, Object> newArguments = new HashMap<>(environment.getArguments());
                newArguments.put(argumentName, argument);
                DataFetchingEnvironment newEnvironment = DataFetchingEnvironmentImpl
                        .newDataFetchingEnvironment(environment).arguments(newArguments).build();

                return innerDataFetcher.get(newEnvironment);
            }

            // map argument by expression
            if (Objects.equals(operateType, Directives.ParamTransformType.MAP.name())) {

                Map<String, Object> transformEnv = new HashMap<>(environment.getArguments());
                if (sourceEnv != null) {
                    transformEnv.putAll(sourceEnv);
                }
                Object newParam = aviatorEvaluator.execute(expression, transformEnv);

                Map<String, Object> newArguments = new HashMap<>(environment.getArguments());
                newArguments.put(argumentName, newParam);

                DataFetchingEnvironment newEnvironment = DataFetchingEnvironmentImpl
                        .newDataFetchingEnvironment(environment).arguments(newArguments).build();

                return innerDataFetcher.get(newEnvironment);
            }

            throw new RuntimeException("can not invoke here.");
        };

        if (defaultDF instanceof AsyncDataFetcher || dependencySource != null) {
            return async(wrappedFetcher, innerExecutor);
        }

        return wrappedFetcher;
    }

    private FetchSourceTask getFetchSourceFromState(ExecutionEngineState engineState, String sourceName) {
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
            queryTask.whenComplete((result, ex) -> {
                if (ex != null) {
                    valueTask.getTaskFuture().completeExceptionally(ex);
                    return;
                }

                if (result == null) {
                    valueTask.getTaskFuture().complete(null);
                    return;
                }
            }).join();

            if (valueTask.getTaskFuture().isDone()) {
                return valueTask;
            }
        }

        for (FetchSourceTask taskInValuePath : topTaskList) {
            taskInValuePath.getTaskFuture().whenComplete((result, ex) -> {
                        if (ex != null) {
                            valueTask.getTaskFuture().completeExceptionally(ex);
                            return;
                        }

                        if (result == null) {
                            valueTask.getTaskFuture().complete(null);
                        }
                    }
            ).join();

            if (valueTask.getTaskFuture().isDone()) {
                return valueTask;
            }
        }

        throw new RuntimeException("can not invoke here");
    }

    private Map<String, Object> getCalMap(Object res) {
        if (res == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        if (res.getClass().isPrimitive()) {
            result.put("ele", res);
        } else {
            Map<String, Object> objectMap = objectMapper.toMap(res);
            objectMap = objectMap != null ? objectMap : Collections.emptyMap();
            result.putAll(objectMap);
        }
        return result;
    }


}