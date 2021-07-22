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
import calculator.engine.script.ScriptEvaluator;
import calculator.graphql.AsyncDataFetcherInterface;
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
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.language.Directive;
import graphql.language.Document;
import graphql.parser.InvalidSyntaxException;
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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static calculator.common.CommonUtil.arraySize;
import static calculator.common.CommonUtil.fieldPath;
import static calculator.common.CommonUtil.getArgumentFromDirective;
import static calculator.common.CommonUtil.getDependenceSourceFromDirective;
import static calculator.engine.metadata.Directives.ARGUMENT_TRANSFORM;
import static calculator.engine.metadata.Directives.FILTER;
import static calculator.engine.metadata.Directives.INCLUDE_BY;
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

    private final ScriptEvaluator scriptEvaluator;

    private final ConcurrentHashMap<String, PreparsedDocumentEntry> documentCache = new ConcurrentHashMap<>();

    private ExecutionEngine(Executor executor, ObjectMapper objectMapper, ScriptEvaluator scriptEvaluator) {
        this.executor = Objects.requireNonNull(executor);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.scriptEvaluator = Objects.requireNonNull(scriptEvaluator);
    }

    public static ExecutionEngine newInstance(Config config) {
        return new ExecutionEngine(config.getExecutor(), config.getObjectMapper(), config.getScriptEvaluator());
    }

    // ============================================== create InstrumentationState for engine  ==============================================
    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        PreparsedDocumentEntry documentEntry = documentCache.compute(parameters.getExecutionInput().getQuery(), (key, oldValue) -> {
            if (oldValue != null) {
                return oldValue;
            }
            try {
                Document document = Parser.parse(key);
                return new PreparsedDocumentEntry(document);
            } catch (InvalidSyntaxException e) {
                return new PreparsedDocumentEntry(e.toInvalidSyntaxError());
            }
        });

        if (documentEntry.hasErrors()) {
            return ExecutionEngineState.newExecutionState().build();
        }

        QueryTraverser traverser = QueryTraverser.newQueryTraverser()
                .schema(parameters.getSchema())
                .document(documentEntry.getDocument())
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
                                Object mappedValue = scriptEvaluator.evaluate(
                                        sourceTask.getMapper(), Collections.singletonMap(sourceTask.getMapperKey(), getCalMap(result))
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
                        Object mappedValue = scriptEvaluator.evaluate(
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
                String predicate = getArgumentFromDirective(directive, "predicate");
                List<String> dependencySources = getDependenceSourceFromDirective(directive);
                dataFetcher = wrapSkipDataFetcher(dataFetcher, engineState, predicate, dependencySources);
                continue;
            }

            if (Objects.equals(INCLUDE_BY.getName(), directive.getName())) {
                String predicate = getArgumentFromDirective(directive, "predicate");
                List<String> dependencySources = getDependenceSourceFromDirective(directive);
                dataFetcher = wrapIncludeDataFetcher(dataFetcher, engineState, predicate, dependencySources);
                continue;
            }

            if (Objects.equals(MOCK.getName(), directive.getName())) {
                dataFetcher = ignore -> getArgumentFromDirective(directive, "value");
                continue;
            }

            if (Objects.equals(FILTER.getName(), directive.getName())) {
                String predicate = getArgumentFromDirective(directive, "predicate");
                List<String> dependencySources = getDependenceSourceFromDirective(directive);
                dataFetcher = wrapFilterDataFetcher(
                        dataFetcher, engineState, predicate, dependencySources, valueUnboxer
                );
                continue;
            }

            if (Objects.equals(SORT.getName(), directive.getName())) {
                Supplier<Boolean> defaultReversed = () -> (Boolean) SORT.getArgument("reversed").getDefaultValue();
                String sortKey = getArgumentFromDirective(directive, "key");
                Boolean reversed = getArgumentFromDirective(directive, "reversed");
                reversed = reversed != null ? reversed : defaultReversed.get();
                dataFetcher = wrapSortDataFetcher(dataFetcher, sortKey, reversed, valueUnboxer);
                continue;
            }

            if (Objects.equals(SORT_BY.getName(), directive.getName())) {
                String comparator = getArgumentFromDirective(directive, "comparator");
                Boolean reversed = getArgumentFromDirective(directive, "reversed");
                reversed = reversed != null
                        ? reversed
                        : (Boolean) SORT_BY.getArgument("reversed").getDefaultValue();
                List<String> dependencySources = getDependenceSourceFromDirective(directive);
                dataFetcher = wrapSortByDataFetcher(
                        dataFetcher, comparator, reversed, dependencySources, engineState, valueUnboxer
                );
                continue;
            }

            if (Objects.equals(MAP.getName(), directive.getName())) {
                String mapper = getArgumentFromDirective(directive, "mapper");
                List<String> dependencySources = getDependenceSourceFromDirective(directive);
                dataFetcher = wrapMapDataFetcher(
                        dataFetcher, mapper, dependencySources, engineState
                );
                continue;
            }

            if (Objects.equals(ARGUMENT_TRANSFORM.getName(), directive.getName())) {
                String argumentName = getArgumentFromDirective(directive, "argumentName");
                String operateType = getArgumentFromDirective(directive, "operateType");
                String expression = getArgumentFromDirective(directive, "expression");
                List<String> dependencySources = getDependenceSourceFromDirective(directive);
                dataFetcher = wrapArgumentTransformDataFetcher(
                        argumentName, operateType, expression, dependencySources, dataFetcher, engineState
                );
                continue;
            }

        }

        return dataFetcher;
    }


    private DataFetcher<?> wrapSkipDataFetcher(DataFetcher<?> dataFetcher,
                                               ExecutionEngineState engineState,
                                               String predicate,
                                               List<String> dependencySources) {
        boolean isAsyncFetcher = dataFetcher instanceof AsyncDataFetcherInterface;
        Executor innerExecutor = isAsyncFetcher ? ((AsyncDataFetcherInterface<?>) dataFetcher).getExecutor() : executor;
        DataFetcher<?> innerDataFetcher = isAsyncFetcher ? ((AsyncDataFetcherInterface<?>) dataFetcher).getWrappedDataFetcher() : dataFetcher;


        DataFetcher<?> wrappedDataFetcher = environment -> {
            Map<String, Object> env = new LinkedHashMap<>(environment.getArguments());
            if (dependencySources != null && !dependencySources.isEmpty()) {
                for (String dependencySource : dependencySources) {
                    FetchSourceTask sourceTask = getFetchSourceFromState(engineState, dependencySource);
                    if (sourceTask.getTaskFuture().isCompletedExceptionally()) {
                        env.put(dependencySource, null);
                    } else {
                        env.put(dependencySource, sourceTask.getTaskFuture().join());
                    }
                }
            }

            Boolean isSkip = (Boolean) scriptEvaluator.evaluate(predicate, env);
            if (isSkip) {
                return null;
            }

            Object innerResult = innerDataFetcher.get(environment);
            if (innerResult instanceof CompletionStage && (isAsyncFetcher || dependencySources != null)) {
                return ((CompletionStage<?>) innerResult).toCompletableFuture().join();
            }
            return innerResult;
        };

        if (isAsyncFetcher || dependencySources != null) {
            return async(wrappedDataFetcher, innerExecutor);
        }
        return wrappedDataFetcher;
    }


    private DataFetcher<?> wrapIncludeDataFetcher(DataFetcher<?> dataFetcher,
                                                 ExecutionEngineState engineState,
                                                 String predicate,
                                                 List<String> dependencySources) {
        boolean isAsyncFetcher = dataFetcher instanceof AsyncDataFetcherInterface;
        Executor innerExecutor = isAsyncFetcher ? ((AsyncDataFetcherInterface<?>) dataFetcher).getExecutor() : executor;
        DataFetcher<?> innerDataFetcher = isAsyncFetcher ? ((AsyncDataFetcherInterface<?>) dataFetcher).getWrappedDataFetcher() : dataFetcher;

        DataFetcher<?> wrappedDataFetcher = environment -> {
            Map<String, Object> env = new LinkedHashMap<>(environment.getArguments());
            if (dependencySources != null && !dependencySources.isEmpty()) {
                for (String dependencySource : dependencySources) {
                    FetchSourceTask sourceTask = getFetchSourceFromState(engineState, dependencySource);
                    if (sourceTask.getTaskFuture().isCompletedExceptionally()) {
                        env.put(dependencySource, null);
                    } else {
                        env.put(dependencySource, sourceTask.getTaskFuture().join());
                    }
                }
            }

            Boolean isInclude = (Boolean) scriptEvaluator.evaluate(predicate, env);
            if (!isInclude) {
                return null;
            }

            Object innerResult = innerDataFetcher.get(environment);
            if (innerResult instanceof CompletionStage && (isAsyncFetcher || dependencySources != null)) {
                return ((CompletionStage<?>) innerResult).toCompletableFuture().join();
            }
            return innerResult;
        };

        if (isAsyncFetcher || dependencySources != null) {
            return async(wrappedDataFetcher, innerExecutor);
        }
        return wrappedDataFetcher;
    }


    private DataFetcher<?> wrapFilterDataFetcher(DataFetcher<?> defaultDF,
                                                 ExecutionEngineState engineState,
                                                 String predicate,
                                                 List<String> dependencySources,
                                                 ValueUnboxer valueUnboxer) {

        AtomicBoolean isAsyncFetcher = new AtomicBoolean(defaultDF instanceof AsyncDataFetcherInterface);
        Executor innerExecutor = isAsyncFetcher.get() ? ((AsyncDataFetcherInterface<?>) defaultDF).getExecutor() : executor;
        DataFetcher<?> innerDataFetcher = isAsyncFetcher.get() ? ((AsyncDataFetcherInterface<?>) defaultDF).getWrappedDataFetcher() : defaultDF;

        DataFetcher<?> wrappedFetcher = environment -> {
            Object originalResult = innerDataFetcher.get(environment);
            if(originalResult instanceof CompletionStage){
                originalResult = ((CompletionStage<?>) originalResult).toCompletableFuture().join();
                isAsyncFetcher.set(true);
            }
            Object unWrapResult = unWrapDataFetcherResult(originalResult, valueUnboxer);

            if (unWrapResult == null) {
                return originalResult;
            }

            Map<String, Object> sourceEnv = new LinkedHashMap<>();
            if (dependencySources != null && !dependencySources.isEmpty()) {
                for (String dependencySource : dependencySources) {
                    FetchSourceTask sourceTask = getFetchSourceFromState(engineState, dependencySource);
                    if (sourceTask.getTaskFuture().isCompletedExceptionally()) {
                        sourceEnv.put(dependencySource, null);
                    } else {
                        sourceEnv.put(dependencySource, sourceTask.getTaskFuture().join());
                    }
                }
            }

            List<Object> filteredList = new ArrayList<>();
            for (Object ele : (Collection<?>) unWrapResult) {
                Map<String, Object> fieldMap = (Map<String, Object>)getCalMap(ele);
                fieldMap.putAll(sourceEnv);
                if ((Boolean) scriptEvaluator.evaluate(predicate, fieldMap)) {
                    filteredList.add(ele);
                }
            }
            return wrapResult(originalResult, filteredList);
        };

        if (isAsyncFetcher.get() || dependencySources != null) {
            return async(wrappedFetcher, innerExecutor);
        }
        return wrappedFetcher;
    }

    private DataFetcher<?> wrapSortDataFetcher(DataFetcher<?> defaultDF, String sortKey, Boolean reversed, ValueUnboxer valueUnboxer) {
        AtomicBoolean isAsyncFetcher = new AtomicBoolean(defaultDF instanceof AsyncDataFetcherInterface);
        Executor innerExecutor = isAsyncFetcher.get() ? ((AsyncDataFetcherInterface<?>) defaultDF).getExecutor() : executor;
        DataFetcher<?> innerDataFetcher = isAsyncFetcher.get() ? ((AsyncDataFetcherInterface<?>) defaultDF).getWrappedDataFetcher() : defaultDF;

        DataFetcher<?> wrappedDataFetcher = environment -> {
            Object originalResult = innerDataFetcher.get(environment);
            if(originalResult instanceof CompletionStage){
                isAsyncFetcher.set(true);
                originalResult = ((CompletionStage<?>) originalResult).toCompletableFuture().join();
            }
            Object unWrapDataFetcherResult = unWrapDataFetcherResult(originalResult, valueUnboxer);

            if (unWrapDataFetcherResult == null) {
                return originalResult;
            }

            Collection<Object> collection = (Collection<Object>) originalResult;

            List<Object> sortedCollection;
            if (reversed) {
                sortedCollection = collection.stream().sorted(
                        Comparator.comparing(ele -> (Comparable<Object>) ((Map<String, Object>) getCalMap(ele)).get(sortKey)).reversed()
                ).collect(toList());
            } else {
                sortedCollection = collection.stream().sorted(
                        Comparator.comparing(ele -> (Comparable<Object>) ((Map<String, Object>) getCalMap(ele)).get(sortKey))
                ).collect(toList());
            }

            return wrapResult(originalResult, sortedCollection);
        };

        if (isAsyncFetcher.get()) {
            return async(wrappedDataFetcher, innerExecutor);
        }
        return wrappedDataFetcher;
    }


    private DataFetcher<?> wrapSortByDataFetcher(DataFetcher<?> defaultDF,
                                                 String comparator,
                                                 Boolean reversed,
                                                 List<String> dependencySources,
                                                 ExecutionEngineState engineState,
                                                 ValueUnboxer valueUnboxer) {

        AtomicBoolean isAsyncFetcher = new AtomicBoolean(defaultDF instanceof AsyncDataFetcherInterface);
        Executor innerExecutor = isAsyncFetcher.get() ? ((AsyncDataFetcherInterface<?>) defaultDF).getExecutor() : executor;
        DataFetcher<?> innerDataFetcher = isAsyncFetcher.get() ? ((AsyncDataFetcherInterface<?>) defaultDF).getWrappedDataFetcher() : defaultDF;

        DataFetcher<?> wrappedDataFetcher = environment -> {
            Object originalResult = innerDataFetcher.get(environment);
            if(originalResult instanceof CompletionStage){
                originalResult = ((CompletionStage<?>) originalResult).toCompletableFuture().join();
                isAsyncFetcher.set(true);
            }
            Collection<Object> collectionData = (Collection<Object>) unWrapDataFetcherResult(originalResult, valueUnboxer);

            if (arraySize(collectionData) == 0) {
                return originalResult;
            }

            Map<String, Object> sourceEnv = new LinkedHashMap<>();
            if (dependencySources != null && !dependencySources.isEmpty()) {
                for (String dependencySource : dependencySources) {
                    FetchSourceTask sourceTask = getFetchSourceFromState(engineState, dependencySource);
                    if (sourceTask.getTaskFuture().isCompletedExceptionally()) {
                        sourceEnv.put(dependencySource, null);
                    } else {
                        sourceEnv.put(dependencySource, sourceTask.getTaskFuture().join());
                    }
                }
            }

            List<Object> sortedList;
            if (reversed) {
                sortedList = collectionData.stream().sorted(Comparator.comparing(ele -> {
                    Map<String, Object> env = (Map<String, Object>) getCalMap(ele);
                    env.putAll(sourceEnv);
                    return (Comparable<Object>) scriptEvaluator.evaluate(comparator, env);
                }).reversed()).collect(toList());
            } else {
                sortedList = collectionData.stream().sorted(
                        Comparator.comparing(ele -> {
                            Map<String, Object> env = (Map<String, Object>) getCalMap(ele);
                            env.putAll(sourceEnv);
                            return (Comparable<Object>) scriptEvaluator.evaluate(comparator, env);
                        })
                ).collect(toList());
            }

            return wrapResult(originalResult, sortedList);
        };

        if (isAsyncFetcher.get() || dependencySources != null) {
            return async(wrappedDataFetcher, innerExecutor);
        }

        return wrappedDataFetcher;
    }


    // directive @map(mapper:String!, dependencySources:String) on FIELD
    private DataFetcher<?> wrapMapDataFetcher(DataFetcher<?> defaultDF,
                                              String mapper,
                                              List<String> dependencySources,
                                              ExecutionEngineState engineState) {

        boolean isAsyncFetcher = defaultDF instanceof AsyncDataFetcherInterface;
        Executor innerExecutor = isAsyncFetcher ? ((AsyncDataFetcherInterface<?>) defaultDF).getExecutor() : executor;

        DataFetcher<?> wrappedDataFetcher = environment -> {

            Map<String, Object> sourceEnv = new LinkedHashMap<>();
            if (dependencySources != null && !dependencySources.isEmpty()) {
                for (String dependencySource : dependencySources) {
                    FetchSourceTask sourceTask = getFetchSourceFromState(engineState, dependencySource);
                    if (sourceTask.getTaskFuture().isCompletedExceptionally()) {
                        sourceEnv.put(dependencySource, null);
                    } else {
                        sourceEnv.put(dependencySource, sourceTask.getTaskFuture().join());
                    }
                }
            }

            // new Map, do not alter original Map info.
            HashMap<String, Object> expEnv = new HashMap<>();
            Object sourceInfo = getCalMap(environment.getSource());
            if (sourceInfo instanceof Map) {
                expEnv.putAll((Map) sourceInfo);
            } else {
                // FIXME ignored
            }

            expEnv.putAll(sourceEnv);

            return scriptEvaluator.evaluate(mapper, expEnv);
        };

        if (isAsyncFetcher || dependencySources != null) {
            return async(wrappedDataFetcher, innerExecutor);
        }

        return wrappedDataFetcher;
    }

    // TODO ele常量需要有说明
    //  typeName: String -> Enum
    private DataFetcher<?> wrapArgumentTransformDataFetcher(String argumentName,
                                                            String operateType,
                                                            String expression,
                                                            List<String> dependencySources,
                                                            DataFetcher<?> defaultDF,
                                                            ExecutionEngineState engineState) {
        boolean isAsyncFetcher = defaultDF instanceof AsyncDataFetcherInterface;
        Executor innerExecutor = isAsyncFetcher ? ((AsyncDataFetcherInterface<?>) defaultDF).getExecutor() : executor;
        DataFetcher<?> innerDataFetcher = isAsyncFetcher ? ((AsyncDataFetcherInterface<?>) defaultDF).getWrappedDataFetcher() : defaultDF;

        DataFetcher<?> wrappedFetcher = environment -> {
            Map<String, Object> sourceEnv = new LinkedHashMap<>();
            if (dependencySources != null && !dependencySources.isEmpty()) {
                for (String dependencySource : dependencySources) {
                    FetchSourceTask sourceTask = getFetchSourceFromState(engineState, dependencySource);
                    if (sourceTask.getTaskFuture().isCompletedExceptionally()) {
                        sourceEnv.put(dependencySource, null);
                    } else {
                        sourceEnv.put(dependencySource, sourceTask.getTaskFuture().join());
                    }
                }
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
                            env.putAll(sourceEnv);

                            return (Boolean) scriptEvaluator.evaluate(expression, env);
                        }
                ).collect(toList());

                Map<String, Object> newArguments = new HashMap<>(environment.getArguments());
                newArguments.put(argumentName, argument);
                DataFetchingEnvironment newEnvironment = DataFetchingEnvironmentImpl
                        .newDataFetchingEnvironment(environment).arguments(newArguments).build();
                Object innerResult = innerDataFetcher.get(newEnvironment);
                if (innerResult instanceof CompletionStage && (isAsyncFetcher || dependencySources != null)) {
                    return ((CompletionStage<?>) innerResult).toCompletableFuture().join();
                }
                return innerResult;
            }

            // map each element of list argument
            if (Objects.equals(operateType, Directives.ParamTransformType.LIST_MAP.name())) {
                List<Object> argument = environment.getArgument(argumentName);
                if (argument == null || argument.isEmpty()) {
                    return innerDataFetcher.get(environment);
                }

                argument = argument.stream().map(ele -> {
                    Map<String, Object> transformEnv = new HashMap<>();
                    transformEnv.put("ele", ele);
                    transformEnv.putAll(sourceEnv);
                    return scriptEvaluator.evaluate(expression, transformEnv);
                }).collect(toList());

                Map<String, Object> newArguments = new HashMap<>(environment.getArguments());
                newArguments.put(argumentName, argument);
                DataFetchingEnvironment newEnvironment = DataFetchingEnvironmentImpl
                        .newDataFetchingEnvironment(environment).arguments(newArguments).build();

                Object innerResult = innerDataFetcher.get(newEnvironment);
                if (innerResult instanceof CompletionStage && (isAsyncFetcher || dependencySources != null)) {
                    return ((CompletionStage<?>) innerResult).toCompletableFuture().join();
                }
                return innerResult;
            }

            // map argument by expression
            if (Objects.equals(operateType, Directives.ParamTransformType.MAP.name())) {

                Map<String, Object> transformEnv = new HashMap<>(environment.getArguments());
                transformEnv.putAll(sourceEnv);
                Object newParam = scriptEvaluator.evaluate(expression, transformEnv);

                Map<String, Object> newArguments = new HashMap<>(environment.getArguments());
                newArguments.put(argumentName, newParam);

                DataFetchingEnvironment newEnvironment = DataFetchingEnvironmentImpl
                        .newDataFetchingEnvironment(environment).arguments(newArguments).build();

                Object innerResult = innerDataFetcher.get(newEnvironment);
                if (innerResult instanceof CompletionStage && (isAsyncFetcher || dependencySources != null)) {
                    return ((CompletionStage<?>) innerResult).toCompletableFuture().join();
                }
                return innerResult;
            }

            throw new RuntimeException("can not invoke here.");
        };

        if (isAsyncFetcher || dependencySources != null) {
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

    private Object getCalMap(Object res) {
        if (res == null) {
            return null;
        }

        if (res.getClass().isPrimitive()) {
            return Collections.singletonMap("ele", res);
        } else {
            return objectMapper.toSimpleCollection(res);
        }
    }


    private Object unWrapDataFetcherResult(Object originalResult, ValueUnboxer valueUnboxer) {

        Object nonFutureResult;
        if (originalResult instanceof CompletionStage) {
            nonFutureResult = ((CompletionStage) originalResult).toCompletableFuture().join();
        } else {
            nonFutureResult = originalResult;
        }

        Object fetchData;
        if (nonFutureResult instanceof DataFetcherResult) {
            fetchData = ((DataFetcherResult<?>) nonFutureResult).getData();
        } else {
            fetchData = nonFutureResult;
        }

        return valueUnboxer.unbox(fetchData);
    }

    private Object wrapResult(Object originalResult, Object data) {
        if (originalResult instanceof DataFetcherResult) {
            return DataFetcherResult.newResult()
                    .errors(((DataFetcherResult<?>) originalResult).getErrors())
                    .localContext(((DataFetcherResult<?>) originalResult).getLocalContext())
                    .data(data)
                    .build();
        }

        return data;
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginFieldListComplete(InstrumentationFieldCompleteParameters parameters) {
        return new InstrumentationContext<ExecutionResult>(){

            @Override
            public void onDispatched(CompletableFuture<ExecutionResult> result) {
            }

            @Override
            public void onCompleted(ExecutionResult result, Throwable t) {
            }
        };
    }
}