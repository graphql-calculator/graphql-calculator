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
import calculator.config.Config;
import calculator.engine.annotation.Internal;
import calculator.engine.decorator.DecorateEnvironment;
import calculator.engine.handler.DistinctHandler;
import calculator.engine.handler.FieldValueHandlerComposite;
import calculator.engine.handler.FilterHandler;
import calculator.engine.handler.HandleEnvironment;
import calculator.engine.handler.SortByHandler;
import calculator.engine.handler.SortHandler;
import calculator.engine.metadata.FetchSourceTask;
import calculator.engine.script.ScriptEvaluator;
import calculator.engine.decorator.ArgumentTransformDecorator;
import calculator.engine.decorator.DistinctDecorator;
import calculator.engine.decorator.FilterDecorator;
import calculator.engine.decorator.MapDecorator;
import calculator.engine.decorator.MockDecorator;
import calculator.engine.decorator.SortByDecorator;
import calculator.engine.decorator.SortDecorator;
import calculator.engine.decorator.DecoratorComposite;
import graphql.ExecutionResult;
import graphql.analysis.QueryTraverser;
import graphql.com.google.common.collect.ImmutableList;
import graphql.com.google.common.collect.ImmutableMap;
import graphql.execution.ExecutionContext;
import graphql.execution.ResultPath;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import static calculator.common.CommonUtil.fieldPath;
import static calculator.common.CommonUtil.getArgumentFromDirective;
import static calculator.common.CommonUtil.getScriptEnv;
import static calculator.engine.metadata.Directives.INCLUDE_BY;
import static calculator.engine.metadata.Directives.SKIP_BY;

@Internal
public class ExecutionEngine extends SimpleInstrumentation {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionEngine.class);

    private final Executor executor;

    private final ObjectMapper objectMapper;

    private final ScriptEvaluator scriptEvaluator;

    // FIXME
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
            Document newDocument = document.transform(builder ->
                    builder.definitions(Collections.singletonList(newOperationDefinition))
            );
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
                                        sourceTask.getMapper(), Collections.singletonMap(sourceTask.getResultKey(), getScriptEnv(objectMapper, result))
                                );
                                sourceTask.getTaskFuture().complete(mappedValue);
                            } catch (Throwable t) {
                                if (logger.isDebugEnabled()) {
                                    logger.debug("evaluate throw Throwable, sourceTask.getMapper() is {}, resultKey = {},  result is {}.",
                                            sourceTask.getMapper(), sourceTask.getResultKey(), result, t);
                                }
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
            CompletableFuture<Object>[] elementFutureArray = elementResultFuture.toArray(new CompletableFuture[0]);
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
                        if (logger.isDebugEnabled()) {
                            logger.debug("evaluate throw Throwable, sourceTask.getMapper() is {}, resultKey = {},  listResult is {}.",
                                    sourceTask.getMapper(), sourceTask.getResultKey(), listResult, t);
                        }
                        child.getTaskFuture().completeExceptionally(t);
                    }
                }
            });
        }
    }


    // ============================================== alter runtime execution for engine  ================================================

    private static final DecoratorComposite strategyComposite = new DecoratorComposite();
    static {
        strategyComposite.addStrategy(new MockDecorator());
        strategyComposite.addStrategy(new FilterDecorator());
        strategyComposite.addStrategy(new SortDecorator());
        strategyComposite.addStrategy(new SortByDecorator());
        strategyComposite.addStrategy(new DistinctDecorator());
        strategyComposite.addStrategy(new MapDecorator());
        strategyComposite.addStrategy(new ArgumentTransformDecorator());
    }

    private static final FieldValueHandlerComposite fieldValueHandlerComposite = new FieldValueHandlerComposite();
    static {
        fieldValueHandlerComposite.addFieldValueHandler(new FilterHandler());
        fieldValueHandlerComposite.addFieldValueHandler(new DistinctHandler());
        fieldValueHandlerComposite.addFieldValueHandler(new SortHandler());
        fieldValueHandlerComposite.addFieldValueHandler(new SortByHandler());
    }

    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
        List<Directive> directives = parameters.getEnvironment().getField().getDirectives();
        return wrapDataFetcher(dataFetcher, directives, parameters);
    }

    private DataFetcher<?> wrapDataFetcher(DataFetcher<?> originalDataFetcher, List<Directive> directivesOnField,
                                           InstrumentationFieldFetchParameters parameters) {
        for (Directive directive : directivesOnField) {
            DataFetchingEnvironment fetchingEnvironment = parameters.getEnvironment();
            DecorateEnvironment wrapperEnvironment = new DecorateEnvironment(
                    fetchingEnvironment.getField(),
                    originalDataFetcher, fetchingEnvironment.getFieldDefinition(),
                    directive, fetchingEnvironment.getFieldDefinition().getDirectives(),
                    fetchingEnvironment, parameters.getInstrumentationState(), parameters.getExecutionContext().getValueUnboxer(),
                    executor,objectMapper,scriptEvaluator

            );

            if (strategyComposite.supportDirective(directive, wrapperEnvironment)) {
                originalDataFetcher = strategyComposite.decorate(directive, wrapperEnvironment);
            }
        }

        return originalDataFetcher;
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

    private void transformListResultByDirectives(ExecutionResult result,
                                                 List<Directive> directives,
                                                 InstrumentationFieldCompleteParameters parameters) {
        for (Directive directive : directives) {

            HandleEnvironment handleEnvironment = new HandleEnvironment(
                    directive, result, parameters, executor, objectMapper, scriptEvaluator
            );

            if (fieldValueHandlerComposite.supportDirective(directive)) {
                fieldValueHandlerComposite.transformListResultByDirectives(handleEnvironment);
            }
        }
    }

}