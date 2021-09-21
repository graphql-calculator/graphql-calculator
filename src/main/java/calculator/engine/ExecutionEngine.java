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

import calculator.common.CollectionUtil;
import calculator.common.CommonUtil;
import calculator.common.GraphQLUtil;
import calculator.config.Config;
import calculator.engine.annotation.Internal;
import calculator.engine.metadata.DataFetcherDefinition;
import calculator.engine.metadata.Directives;
import calculator.engine.metadata.FetchSourceTask;
import calculator.engine.script.ScriptEvaluator;
import graphql.ExecutionResult;
import graphql.analysis.QueryTraverser;
import graphql.com.google.common.collect.ImmutableList;
import graphql.com.google.common.collect.ImmutableMap;
import graphql.execution.DataFetcherResult;
import graphql.execution.ExecutionContext;
import graphql.execution.ResultPath;
import graphql.execution.ValueUnboxer;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.language.Directive;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static calculator.common.CommonUtil.fieldPath;
import static calculator.common.CommonUtil.getArgumentFromDirective;
import static calculator.common.CommonUtil.getDependenceSourceFromDirective;
import static calculator.engine.metadata.Directives.ARGUMENT_TRANSFORM;
import static calculator.engine.metadata.Directives.DISTINCT;
import static calculator.engine.metadata.Directives.FILTER;
import static calculator.engine.metadata.Directives.INCLUDE_BY;
import static calculator.engine.metadata.Directives.MAP;
import static calculator.engine.metadata.Directives.MOCK;
import static calculator.engine.metadata.Directives.SKIP_BY;
import static calculator.engine.metadata.Directives.SORT;
import static calculator.engine.metadata.Directives.SORT_BY;
import static graphql.schema.AsyncDataFetcher.async;
import static java.util.Comparator.nullsLast;
import static java.util.stream.Collectors.toList;

@Internal
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
    public ExecutionContext instrumentExecutionContext(ExecutionContext executionContext, InstrumentationExecutionParameters parameters) {
        ExecutionEngineState engineState = parameters.getInstrumentationState();
        if (!engineState.isContainSkipByOrIncludeBy()) {
            return super.instrumentExecutionContext(executionContext, parameters);
        }

        Document document = executionContext.getDocument();
        OperationDefinition operationDefinition = (OperationDefinition) document.getDefinitions().get(0);

        Map<String, FragmentDefinition> transformedFragmentByName = transformFragmentByName(executionContext.getFragmentsByName(), executionContext.getVariables());
        SelectionSet transformedSelectionSet = transformSelectionForSkipAndInclude(
                operationDefinition.getSelectionSet(), executionContext.getVariables()
        );

        return executionContext.transform(executionContextBuilder -> {
            executionContextBuilder.fragmentsByName(transformedFragmentByName);

            OperationDefinition newOperationDefinition = operationDefinition.transform(
                    builder -> builder.selectionSet(transformedSelectionSet)
            );

            executionContextBuilder.operationDefinition(newOperationDefinition);
            Document newDocument = document.transform(builder -> {
                builder.definitions(Collections.singletonList(newOperationDefinition));
            });
            executionContextBuilder.document(newDocument);
        });
    }

    private Map<String, FragmentDefinition> transformFragmentByName(Map<String, FragmentDefinition> fragmentsByName, Map<String, Object> variables) {
        ImmutableMap.Builder<String, FragmentDefinition> fragmentsByNameBuilder = ImmutableMap.builder();
        for (Map.Entry<String, FragmentDefinition> entry : fragmentsByName.entrySet()) {
            SelectionSet selectionSet = entry.getValue().getSelectionSet();
            SelectionSet transformedSelectionSet = transformSelectionForSkipAndInclude(selectionSet, variables);
            FragmentDefinition transformedFragmentDef = entry.getValue().transform(builder -> builder.selectionSet(transformedSelectionSet));
            fragmentsByNameBuilder.put(entry.getKey(), transformedFragmentDef);
        }
        return fragmentsByNameBuilder.build();
    }

    private SelectionSet transformSelectionForSkipAndInclude(SelectionSet selectionSet, Map<String, Object> variables) {
        if (selectionSet == null || selectionSet.getSelections() == null) {
            return selectionSet;
        }

        ImmutableList.Builder<Selection> selectionBuilder = ImmutableList.builder();
        for (Selection selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                Field field = (Field) selection;
                if (shouldIncludeBy(field.getDirectives(), variables)) {
                    SelectionSet subSelectionSet = field.getSelectionSet();
                    SelectionSet newSubSelectionSet = transformSelectionForSkipAndInclude(subSelectionSet, variables);
                    Field transformedField = field.transform(builder -> builder.selectionSet(newSubSelectionSet));
                    selectionBuilder.add(transformedField);
                }
            } else if (selection instanceof InlineFragment) {
                InlineFragment inlineFragment = (InlineFragment) selection;
                if (shouldIncludeBy(inlineFragment.getDirectives(), variables)) {
                    SelectionSet subSelectionSet = inlineFragment.getSelectionSet();
                    SelectionSet newSubSelectionSet = transformSelectionForSkipAndInclude(subSelectionSet, variables);
                    InlineFragment transformedField = inlineFragment.transform(builder -> builder.selectionSet(newSubSelectionSet));
                    selectionBuilder.add(transformedField);
                }
            } else if (selection instanceof FragmentSpread) {
                FragmentSpread fragmentSpread = (FragmentSpread) selection;
                if (shouldIncludeBy(fragmentSpread.getDirectives(), variables)) {
                    selectionBuilder.add(fragmentSpread);
                }
            }
        }

        return selectionSet.transform(builder -> builder.selections(selectionBuilder.build()));
    }


    // If an exception is thrown, the query will be failed and throw this exception.
    //
    // If @skip use wrong argument, the query will throw graphql.AssertException.
    // query skipByTest_exceptionQueryTest01x($userId: Int) {
    //    consumer{
    //        userInfo(userId: $userId)
    //        @skip(if: $userId)
    //        {
    //            userId
    //        }
    //    }
    //}
    //
    // TODO custom exception for Instrumentation.
    private boolean shouldIncludeBy(List<Directive> directives, Map<String, Object> variables) {
        boolean skipBy = false;
        Directive skipByDirective = CommonUtil.findNodeByName(directives, SKIP_BY.getName());
        if (skipByDirective != null) {
            String predicate = getArgumentFromDirective(skipByDirective, "predicate");
            skipBy = (Boolean) scriptEvaluator.evaluate(predicate, variables);
        }
        if (skipBy) {
            return false;
        }

        boolean includeBy = true;
        Directive includeByDirective = CommonUtil.findNodeByName(directives, INCLUDE_BY.getName());
        if (includeByDirective != null) {
            String predicate = getArgumentFromDirective(includeByDirective, "predicate");
            includeBy = (Boolean) scriptEvaluator.evaluate(predicate, variables);
        }
        return includeBy;
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
                                        sourceTask.getMapper(), Collections.singletonMap(sourceTask.getResultKey(), getScriptEnv(result))
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
                                child.getMapper(), Collections.singletonMap(child.getResultKey(), listResult)
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
            if (Objects.equals(MOCK.getName(), directive.getName())) {
                dataFetcher = ignore -> getArgumentFromDirective(directive, "value");
                continue;
            }

            if (Objects.equals(FILTER.getName(), directive.getName())) {
                dataFetcher = wrapFilterDataFetcher(dataFetcher, valueUnboxer);
                continue;
            }

            if (Objects.equals(SORT.getName(), directive.getName())) {
                dataFetcher = wrapSortDataFetcher(dataFetcher, valueUnboxer);
                continue;
            }

            if (Objects.equals(SORT_BY.getName(), directive.getName())) {
                dataFetcher = wrapSortByDataFetcher(dataFetcher, valueUnboxer);
                continue;
            }

            if (Objects.equals(DISTINCT.getName(), directive.getName())) {
                dataFetcher = wrapDistinctDataFetcher(dataFetcher, valueUnboxer);
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


    private DataFetcher<?> wrapFilterDataFetcher(DataFetcher<?> defaultDF, ValueUnboxer valueUnboxer) {
        DataFetcherDefinition dataFetcherDefinition = GraphQLUtil.getDataFetcherDefinition(defaultDF);

        DataFetcher<?> wrappedFetcher = environment -> {
            Object originalResult = dataFetcherDefinition.getActionFetcher().get(environment);
            if (originalResult instanceof CompletionStage) {
                originalResult = ((CompletionStage<?>) originalResult).toCompletableFuture().join();
            }
            Object unWrappedData = unWrapDataFetcherResult(originalResult, valueUnboxer);
            if (CollectionUtil.arraySize(unWrappedData) == 0) {
                return originalResult;
            }

            List<Object> listResult = CollectionUtil.arrayToList(unWrappedData);
            return wrapResult(originalResult, listResult);
        };

        if (dataFetcherDefinition.isAsyncFetcher()) {
            return async(wrappedFetcher, dataFetcherDefinition.getExecutor());
        }
        return wrappedFetcher;
    }

    private DataFetcher<?> wrapSortDataFetcher(DataFetcher<?> defaultDF, ValueUnboxer valueUnboxer) {

        DataFetcherDefinition dataFetcherDefinition = GraphQLUtil.getDataFetcherDefinition(defaultDF);

        DataFetcher<?> wrappedDataFetcher = environment -> {
            Object originalResult = dataFetcherDefinition.getActionFetcher().get(environment);
            if (originalResult instanceof CompletionStage) {
                originalResult = ((CompletionStage<?>) originalResult).toCompletableFuture().join();
            }

            Object unWrappedData = unWrapDataFetcherResult(originalResult, valueUnboxer);
            if (CollectionUtil.arraySize(unWrappedData) == 0) {
                return originalResult;
            }

            Object listOrArray = CollectionUtil.collectionToListOrArray(unWrappedData);
            return wrapResult(originalResult, listOrArray);
        };

        if (dataFetcherDefinition.isAsyncFetcher()) {
            return async(wrappedDataFetcher, dataFetcherDefinition.getExecutor());
        }
        return wrappedDataFetcher;
    }


    private DataFetcher<?> wrapSortByDataFetcher(DataFetcher<?> defaultDF, ValueUnboxer valueUnboxer) {

        DataFetcherDefinition dataFetcherDefinition = GraphQLUtil.getDataFetcherDefinition(defaultDF);

        DataFetcher<?> wrappedDataFetcher = environment -> {
            Object originalResult = dataFetcherDefinition.getActionFetcher().get(environment);
            if (originalResult instanceof CompletionStage) {
                originalResult = ((CompletionStage<?>) originalResult).toCompletableFuture().join();
            }
            Object unWrappedData = unWrapDataFetcherResult(originalResult, valueUnboxer);
            if (CollectionUtil.arraySize(unWrappedData) == 0) {
                return originalResult;
            }

            Object listOrArray = CollectionUtil.collectionToListOrArray(unWrappedData);
            return wrapResult(originalResult, listOrArray);
        };

        if (dataFetcherDefinition.isAsyncFetcher()) {
            return async(wrappedDataFetcher, dataFetcherDefinition.getExecutor());
        }

        return wrappedDataFetcher;
    }


    private DataFetcher<?> wrapDistinctDataFetcher(DataFetcher<?> defaultDF, ValueUnboxer valueUnboxer) {

        DataFetcherDefinition dataFetcherDefinition = GraphQLUtil.getDataFetcherDefinition(defaultDF);

        DataFetcher<?> wrappedFetcher = environment -> {
            Object originalResult = dataFetcherDefinition.getActionFetcher().get(environment);
            if (originalResult instanceof CompletionStage) {
                originalResult = ((CompletionStage<?>) originalResult).toCompletableFuture().join();
            }
            Object unWrappedData = unWrapDataFetcherResult(originalResult, valueUnboxer);
            if (CollectionUtil.arraySize(unWrappedData) == 0) {
                return originalResult;
            }

            List<Object> listResult = CollectionUtil.arrayToList(unWrappedData);
            return wrapResult(originalResult, listResult);
        };

        if (dataFetcherDefinition.isAsyncFetcher()) {
            return async(wrappedFetcher, dataFetcherDefinition.getExecutor());
        }
        return wrappedFetcher;
    }

    // directive @map(mapper:String!, dependencySources:String) on FIELD
    private DataFetcher<?> wrapMapDataFetcher(DataFetcher<?> defaultDF,
                                              String mapper,
                                              List<String> dependencySources,
                                              ExecutionEngineState engineState) {

        DataFetcherDefinition dataFetcherDefinition = GraphQLUtil.getDataFetcherDefinition(defaultDF);

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
            Map<String, Object> expEnv = new LinkedHashMap<>();
            Object sourceInfo = getScriptEnv(environment.getSource());
            if (sourceInfo != null) {
                expEnv.putAll((Map) sourceInfo);
            }

            expEnv.putAll(sourceEnv);

            return scriptEvaluator.evaluate(mapper, expEnv);
        };

        if (dataFetcherDefinition.isAsyncFetcher()) {
            return async(wrappedDataFetcher, dataFetcherDefinition.getExecutor());
        }

        // e.g. PropertyDataFetcher with @map, and dependencies is not empty.
        if (dependencySources != null && dependencySources.size() > 0) {
            return async(wrappedDataFetcher, executor);
        }

        return wrappedDataFetcher;
    }

    private DataFetcher<?> wrapArgumentTransformDataFetcher(String argumentName,
                                                            String operateType,
                                                            String expression,
                                                            List<String> dependencySources,
                                                            DataFetcher<?> defaultDF,
                                                            ExecutionEngineState engineState) {

        DataFetcherDefinition dataFetcherDefinition = GraphQLUtil.getDataFetcherDefinition(defaultDF);

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

            // filter list element of list argument
            if (Objects.equals(operateType, Directives.ParamTransformType.FILTER.name())) {
                List<Object> argument = environment.getArgument(argumentName);
                if (argument == null || argument.isEmpty()) {
                    return dataFetcherDefinition.getActionFetcher().get(environment);
                }

                argument = argument.stream().filter(ele -> {
                            Map<String, Object> filterEnv = new LinkedHashMap<>(environment.getVariables());
                            filterEnv.put("ele", ele);
                            filterEnv.putAll(sourceEnv);
                            return (Boolean) scriptEvaluator.evaluate(expression, filterEnv);
                        }
                ).collect(toList());

                Map<String, Object> newArguments = new LinkedHashMap<>(environment.getArguments());
                newArguments.put(argumentName, argument);
                DataFetchingEnvironment newEnvironment = DataFetchingEnvironmentImpl
                        .newDataFetchingEnvironment(environment).arguments(newArguments).build();
                Object innerResult = dataFetcherDefinition.getActionFetcher().get(newEnvironment);
                if (innerResult instanceof CompletionStage
                        && (dataFetcherDefinition.isAsyncFetcher() || (dependencySources != null && dependencySources.size() > 0))
                ) {
                    return ((CompletionStage<?>) innerResult).toCompletableFuture().join();
                }
                return innerResult;
            }

            // map each element of list argument
            if (Objects.equals(operateType, Directives.ParamTransformType.LIST_MAP.name())) {
                List<Object> argument = environment.getArgument(argumentName);
                if (argument == null || argument.isEmpty()) {
                    return dataFetcherDefinition.getActionFetcher().get(environment);
                }

                argument = argument.stream().map(ele -> {
                    Map<String, Object> transformEnv = new LinkedHashMap<>(environment.getVariables());
                    transformEnv.put("ele", ele);
                    transformEnv.putAll(sourceEnv);
                    return scriptEvaluator.evaluate(expression, transformEnv);
                }).collect(toList());

                Map<String, Object> newArguments = new LinkedHashMap<>(environment.getArguments());
                newArguments.put(argumentName, argument);
                DataFetchingEnvironment newEnvironment = DataFetchingEnvironmentImpl
                        .newDataFetchingEnvironment(environment).arguments(newArguments).build();

                Object innerResult = dataFetcherDefinition.getActionFetcher().get(newEnvironment);
                if (innerResult instanceof CompletionStage
                        && (dataFetcherDefinition.isAsyncFetcher() || (dependencySources != null && dependencySources.size() > 0))
                ) {
                    return ((CompletionStage<?>) innerResult).toCompletableFuture().join();
                }
                return innerResult;
            }

            // map argument by expression
            if (Objects.equals(operateType, Directives.ParamTransformType.MAP.name())) {

                Map<String, Object> transformEnv = new LinkedHashMap<>(environment.getVariables());
                transformEnv.putAll(sourceEnv);
                Object newParam = scriptEvaluator.evaluate(expression, transformEnv);

                Map<String, Object> newArguments = new LinkedHashMap<>(environment.getArguments());
                newArguments.put(argumentName, newParam);

                DataFetchingEnvironment newEnvironment = DataFetchingEnvironmentImpl
                        .newDataFetchingEnvironment(environment).arguments(newArguments).build();

                Object innerResult = dataFetcherDefinition.getActionFetcher().get(newEnvironment);
                if (innerResult instanceof CompletionStage
                        && (dataFetcherDefinition.isAsyncFetcher() || (dependencySources != null && dependencySources.size() > 0))
                ) {
                    return ((CompletionStage<?>) innerResult).toCompletableFuture().join();
                }
                return innerResult;
            }

            throw new RuntimeException("can not invoke here.");
        };

        if (dataFetcherDefinition.isAsyncFetcher()) {
            return async(wrappedDataFetcher, dataFetcherDefinition.getExecutor());
        }

        // e.g. PropertyDataFetcher with @map, and dependencies is not empty.
        if (dependencySources != null && dependencySources.size() > 0) {
            return async(wrappedDataFetcher, executor);
        }

        return wrappedDataFetcher;
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

    private Object getScriptEnv(Object res) {
        if (res == null) {
            return null;
        }

        if (CommonUtil.isBasicType(res)) {
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
        return new InstrumentationContext<ExecutionResult>() {

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
                if (result == null || result.getData() == null) {
                    return;
                }

                if (CollectionUtil.arraySize(result.getData()) == 0) {
                    return;
                }

                List<Directive> directives = parameters.getExecutionStepInfo().getField().getSingleField().getDirectives();
                if (directives != null && !directives.isEmpty()) {
                    transformListResultByDirectives(result, directives, parameters);
                }
            }
        };
    }

    // @filter @sort @sortBy
    private void transformListResultByDirectives(ExecutionResult result, List<Directive> directives, InstrumentationFieldCompleteParameters parameters) {
        // ExecutionResult中已经是解析后的结果了
        Object listOrArray = result.getData();

        for (Directive directive : directives) {
            if (Objects.equals(FILTER.getName(), directive.getName())) {
                String predicate = getArgumentFromDirective(directive, "predicate");
                filterCollectionData((Collection) listOrArray, predicate);
                continue;
            }

            if (Objects.equals(DISTINCT.getName(), directive.getName())) {
                String comparator = getArgumentFromDirective(directive, "comparator");
                distinctCollectionData((Collection)listOrArray, comparator);
                continue;
            }

            if (Objects.equals(SORT.getName(), directive.getName())) {
                Supplier<Boolean> defaultReversed = () -> (Boolean) SORT.getArgument("reversed").getArgumentDefaultValue().getValue();
                String sortKey = getArgumentFromDirective(directive, "key");
                Boolean reversed = getArgumentFromDirective(directive, "reversed");
                reversed = reversed != null ? reversed : defaultReversed.get();
                sortCollectionData(listOrArray, sortKey, reversed);
                continue;
            }

            if (Objects.equals(SORT_BY.getName(), directive.getName())) {
                String comparator = getArgumentFromDirective(directive, "comparator");
                Boolean reversed = getArgumentFromDirective(directive, "reversed");
                reversed = reversed != null
                        ? reversed
                        : (Boolean) SORT_BY.getArgument("reversed").getArgumentDefaultValue().getValue();
                sortByCollectionData(listOrArray, comparator, reversed);
                continue;
            }
        }
    }

    private void filterCollectionData(Collection collection, String predicate) {
        Predicate<Object> willKeep = ele -> {
            Map<String, Object> fieldMap = (Map<String, Object>) getScriptEnv(ele);
            Map<String, Object> sourceEnv = new LinkedHashMap<>();
            fieldMap.putAll(sourceEnv);
            return (Boolean) scriptEvaluator.evaluate(predicate, fieldMap);
        };

        CollectionUtil.filterCollection(collection, willKeep);
    }


    private void distinctCollectionData(Collection collection, String comparatorExpression) {
        boolean emptyComparator = comparatorExpression == null;

        Function<Object, Integer> comparator = ele -> {
            if (ele == null) {
                return 0;
            }

            if (emptyComparator) {
                return System.identityHashCode(ele);
            }

            Map<String, Object> scriptEnv = new LinkedHashMap<>();
            Map<String, Object> calMap = (Map<String, Object>) getScriptEnv(ele);
            if (calMap != null) {
                scriptEnv.putAll(calMap);
            }
            Object evaluate = scriptEvaluator.evaluate(comparatorExpression, scriptEnv);
            return Objects.hashCode(evaluate);
        };

        CollectionUtil.distinctCollection(collection, comparator);
    }

    private void sortCollectionData(Object listOrArray, String sortKey, Boolean reversed) {
        Comparator<Object> comparator = Comparator.comparing(
                ele -> {
                    Map<String, Object> calMap = (Map<String, Object>) getScriptEnv(ele);
                    if (calMap == null) {
                        return null;
                    }
                    return ((Map<String, Comparable<Object>>) getScriptEnv(ele)).get(sortKey);
                },
                // always nullLast
                nullsLast((v1, v2) -> {
                            if (reversed) {
                                return v2.compareTo(v1);
                            } else {
                                return v1.compareTo(v2);
                            }
                        }
                )
        );

        CollectionUtil.sortListOrArray(listOrArray, comparator);
    }

    private void sortByCollectionData(Object listOrArray,
                                      String comparatorExpression,
                                      Boolean reversed) {

        Comparator<Object> comparator = Comparator.comparing(
                ele -> {
                    Map<String, Object> scriptEnv = new LinkedHashMap<>();
                    Map<String, Object> calMap = (Map<String, Object>) getScriptEnv(ele);
                    if (calMap != null) {
                        scriptEnv.putAll(calMap);
                    }
                    return (Comparable<Object>) scriptEvaluator.evaluate(comparatorExpression, scriptEnv);
                },
                // always nullLast
                nullsLast((v1, v2) -> {
                            if (reversed) {
                                return v2.compareTo(v1);
                            } else {
                                return v1.compareTo(v2);
                            }
                        }
                )
        );

        CollectionUtil.sortListOrArray(listOrArray, comparator);
    }
}