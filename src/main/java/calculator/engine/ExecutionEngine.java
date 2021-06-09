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
import calculator.engine.metadata.NodeTask;
import calculator.graphql.AsyncDataFetcher;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import graphql.ExecutionResult;
import graphql.analysis.QueryTraverser;
import graphql.execution.ResultPath;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.language.Directive;
import graphql.language.Document;
import graphql.parser.Parser;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.validation.ValidationError;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import static calculator.common.Tools.arraySize;
import static calculator.common.Tools.fieldPath;
import static calculator.common.Tools.getArgumentFromDirective;
import static calculator.common.Tools.isInListPath;
import static calculator.engine.function.ExpEvaluator.calExp;
import static calculator.engine.metadata.Directives.ARGUMENT_TRANSFORM;
import static calculator.engine.metadata.Directives.FETCH_SOURCE;
import static calculator.engine.metadata.Directives.FILTER;
import static calculator.engine.metadata.Directives.LINK;
import static calculator.engine.metadata.Directives.MAP;
import static calculator.engine.metadata.Directives.MOCK;
import static calculator.engine.metadata.Directives.SKIP_BY;
import static calculator.engine.metadata.Directives.SORT;
import static calculator.engine.metadata.Directives.SORT_BY;
import static calculator.graphql.AsyncDataFetcher.async;
import static graphql.execution.instrumentation.SimpleInstrumentationContext.noOp;
import static java.util.stream.Collectors.toList;

public class ExecutionEngine implements Instrumentation {

    private final Executor executor;

    private final ObjectMapper objectMapper;

    private final AviatorEvaluatorInstance aviatorEvaluator;


    private ExecutionEngine(Executor executor, ObjectMapper objectMapper, AviatorEvaluatorInstance aviatorEvaluator) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(objectMapper);
        Objects.requireNonNull(aviatorEvaluator);

        this.executor = executor;
        this.objectMapper = objectMapper;
        this.aviatorEvaluator = aviatorEvaluator;
    }

    public static ExecutionEngine newInstance(Config config) {
        return new ExecutionEngine(config.getExecutor(), config.getObjectMapper(), config.getAviatorEvaluator());
    }

    // ============================================== create InstrumentationState for engine  ==============================================

    //  需要预分析，因为对于 person-> name @node("personName")，如果不预先分析、就不会知道person也是dag任务
    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {

        String query = parameters.getExecutionInput().getQuery();
        QueryTraverser traverser = QueryTraverser.newQueryTraverser()
                .schema(parameters.getSchema())
                .document(Parser.parse(query))
                .variables(Collections.emptyMap()).build();

        ExecutionEngineStateParserOld visitor = new ExecutionEngineStateParserOld();
        traverser.visitDepthFirst(visitor);
        return visitor.getExecutionEngineState();
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters) {
        return new SimpleInstrumentationContext<>();
    }

    @Override
    public InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters) {
        return new SimpleInstrumentationContext<>();
    }

    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
        return new SimpleInstrumentationContext<>();
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters) {
        return new SimpleInstrumentationContext<>();
    }

    @Override
    public ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        return new ExecutionStrategyInstrumentationContext() {
            @Override
            public void onDispatched(CompletableFuture<ExecutionResult> result) {
            }

            @Override
            public void onCompleted(ExecutionResult result, Throwable t) {
            }
        };
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters) {
        return new SimpleInstrumentationContext<>();
    }

    @Override
    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        return new InstrumentationContext<Object>() {

            @Override
            public void onDispatched(CompletableFuture result) {

            }

            @Override
            public void onCompleted(Object result, Throwable t) {

            }
        };
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginFieldListComplete(InstrumentationFieldCompleteParameters parameters) {
        // kp fieldList 肯定也不是叶子结点，并且会在 beginFieldComplete 处被切一下
        return noOp();
    }

    // ============================================== instrument operation =================================================

    @Override
    public InstrumentationContext<ExecutionResult> beginFieldComplete(InstrumentationFieldCompleteParameters parameters) {
        // fieldList 也会在这里切一下
        return getContextOpt(parameters).orElse(noOp());
    }

    private Optional<InstrumentationContext<ExecutionResult>> getContextOpt(InstrumentationFieldCompleteParameters parameters) {

        InstrumentationContext<ExecutionResult> instrumentationContext = new InstrumentationContext<ExecutionResult>() {
            @Override
            public void onDispatched(CompletableFuture<ExecutionResult> future) {
            }

            // 所有子节点都解析完成是才会执行这里的逻辑
            @Override
            public void onCompleted(ExecutionResult result, Throwable ex) {

                ExecutionEngineState state = parameters.getInstrumentationState();
                ResultPath resultPath = parameters.getExecutionStepInfo().getPath();
                NodeTask nodeTask = parseNodeTask(state, resultPath);

                if(Objects.equals("itemList",nodeTask.getResultKey())){
                    System.out.println(111);
                }

                if (nodeTask == null) {
                    return;
                }

                // 对于[VO]的情况、已经有元素解析失败、则其余元素是否解析成功没有必要了
                // TODO 应该是分情况：如果 [VO!] 则全部忽略，如果是 [VO] 则聚合成功的部分返回
                //      参考graphql sec: https://spec.graphql.org/draft/#sec-Combining-List-and-Non-Null
                if (nodeTask.getFuture().isCompletedExceptionally()) {
                    // 保存已有的异常信息
                    if (ex != null) {
                        nodeTask.getFuture().whenComplete(
                                (ignore, existEx) -> existEx.addSuppressed(ex)
                        );
                    }
                    return;
                }

                if (ex != null) {
                    nodeTask.getFuture().completeExceptionally(ex);
                    return;
                }

                if (result.getData() != null) {
                    // kp 防止取一半的数据：见 tag_completeSubTask
                    if (isInListPath(nodeTask)) {
                        nodeTask.getTmpResult().add(result.getData());
                    } else {
                        // 给当前节点代表的任务设置数据
                        if (nodeTask.getMapper() != null) {
                            Object mappedValue = aviatorEvaluator.execute(nodeTask.getMapper(),
                                    Collections.singletonMap(
                                            nodeTask.getResultKey(), result.getData()
                                    )
                            );
                            nodeTask.getFuture().complete(mappedValue);
                        } else {
                            nodeTask.getFuture().complete(result.getData());
                        }

                        // kp tag_completeSubTask
                        completeSubTask(nodeTask);
                    }
                } else {
                    // 对于没有结果的情况、仍然抛出异常，来终止程序运行
                    // 这里是否需要让调度器感知异常信息？不需要，包含在结果中了
                    nodeTask.getFuture().completeExceptionally(new Throwable("empty result for " + fieldPath(resultPath)));
                }
            }
        };
        return Optional.of(instrumentationContext);
    }

    /**
     * @param state      全局状态
     * @param resultPath 当前节点
     * @return 当前节点对应的异步任务定义
     */
    private NodeTask parseNodeTask(ExecutionEngineState state, ResultPath resultPath) {
        if (state.getTaskByPath().isEmpty()) {
            return null;
        }

        String fieldPath = fieldPath(resultPath);
        if (!state.getTaskByPath().containsKey(fieldPath)) {
            return null;
        }

        return state.getTaskByPath().get(fieldPath);
    }

    private void completeSubTask(NodeTask nodeTask) {
        if (nodeTask == null || nodeTask.getSubTaskList().isEmpty()) {
            return;
        }

        for (NodeTask subTask : nodeTask.getSubTaskList()) {
            if (isInListPath(subTask) && !subTask.getFuture().isDone()) {
                if (nodeTask.getMapper() != null) {
                    Object mappedValue = aviatorEvaluator.execute(
                            nodeTask.getMapper(), Collections.singletonMap(nodeTask.getResultKey(), subTask.getTmpResult())
                    );
                    nodeTask.getFuture().complete(mappedValue);
                } else {
                    subTask.getFuture().complete(subTask.getTmpResult());
                }
            }
            completeSubTask(subTask);
        }
    }

    // 如果有link节点，则分析其每一个依赖的任务，并更新参数
    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
        // link 优先级比较高
        List<Directive> linkDirectiveList = parameters.getEnvironment().getField().getDirectives(LINK.getName());
        if (linkDirectiveList != null && !linkDirectiveList.isEmpty()) {
            return wrapDFEnvironment(dataFetcher, linkDirectiveList, parameters);
        }

        List<Directive> dirOnField = parameters.getEnvironment().getField().getDirectives();
        if (!dirOnField.isEmpty()) {
            // 如果 指令就是为了动态改变 的运行时行为，那么这里就有必要直接返回
            return wrappedDataFetcher(dirOnField, parameters.getEnvironment(), dataFetcher, parameters.getInstrumentationState());
        }

        return dataFetcher;
    }

    private DataFetcher<?> wrapDFEnvironment(DataFetcher<?> dataFetcher,
                                             List<Directive> linkDirectiveList,
                                             InstrumentationFieldFetchParameters parameters) {
        ExecutionEngineState scheduleState = parameters.getInstrumentationState();
        Map<String, List<String>> sequenceTaskByNode = scheduleState.getSequenceTaskByNode();
        Map<String, NodeTask> taskByPath = scheduleState.getTaskByPath();
        DataFetchingEnvironment oldDFEnvironment = parameters.getEnvironment();
        Map<String, Object> newArguments = new HashMap<>(oldDFEnvironment.getArguments());

        Executor threadPool = executor;
        final DataFetcher<?> finalFetcher;
        if (dataFetcher instanceof AsyncDataFetcher) {
            finalFetcher = ((AsyncDataFetcher<?>) dataFetcher).getWrappedDataFetcher();
            threadPool = ((AsyncDataFetcher<?>) dataFetcher).getExecutor();
        } else {
            finalFetcher = dataFetcher;
        }

        return async(environment -> {
            for (Directive linkDir : linkDirectiveList) {
                // 获取当前依赖的任务列表
                String nodeName = getArgumentFromDirective(linkDir, "node");
                List<String> taskNameForNode = sequenceTaskByNode.get(nodeName);
                List<NodeTask> taskList = taskNameForNode.stream().map(taskByPath::get).collect(toList());
                NodeTask valueTask = getValueFromTasks(taskList);
                if (valueTask.getFuture().isCompletedExceptionally()) {
                    // 当前逻辑是如果参数获取失败，则该数据也不再进行解析
                    return null;
                } else {
                    String argumentName = getArgumentFromDirective(linkDir, "argument");
                    newArguments.put(argumentName, valueTask.getFuture().join());
                }
            }
            DataFetchingEnvironment newEnvironment = DataFetchingEnvironmentImpl
                    .newDataFetchingEnvironment(oldDFEnvironment).arguments(newArguments).build();
            return finalFetcher.get(newEnvironment);
        }, threadPool);
    }

    /**
     * get result from node task.
     *
     * @param taskForNodeValue tasks which the node rely on
     * @return 包含结果并且已经完成的 NodeTask 节点
     */
    private NodeTask getValueFromTasks(List<NodeTask> taskForNodeValue) {
        NodeTask futureTask = taskForNodeValue.get(taskForNodeValue.size() - 1);

        for (NodeTask task : taskForNodeValue) {
            task.getFuture().whenComplete((ignore, ex) -> {
                if (ex != null) {
                    futureTask.getFuture().completeExceptionally(ex);
                }
            }).join();

            // 如果有异常，则中断执行
            if (futureTask.getFuture().isCompletedExceptionally()) {
                return futureTask;
            }
        }
        return futureTask;
    }

    /**
     * 对dataFetcher进行一次包装，如果有 mock、则直接返回mock值，如果有filter、则直接根据filter过滤
     * 根据出现顺序进行判断：根据顺序判断是为了提高灵活性
     *
     * @param dirOnField 字段上的指令
     * @param environment df环境变量
     * @param instrumentationParameters 包含执行上下文信息
     * @return
     */
    private DataFetcher<?> wrappedDataFetcher(List<Directive> dirOnField,
                                              DataFetchingEnvironment environment,
                                              DataFetcher<?> defaultDataFetcher,
                                              ExecutionEngineState engineState) {

        // kp skip在最外层执行、防止冗余的操作
        Directive skip = null;
        for (Directive directive : dirOnField) {
            if (Objects.equals(SKIP_BY.getName(), directive.getName())) {
                skip = directive;
                continue;
            }

            if (Objects.equals(MOCK.getName(), directive.getName())) {
                defaultDataFetcher = ignore -> getArgumentFromDirective(directive, "value");
                continue;
            }

            // 结果处理
            if (Objects.equals(MAP.getName(), directive.getName())) {
                String mapper = getArgumentFromDirective(directive, "mapper");
                String dependencyNode = getArgumentFromDirective(directive, "dependencyNode");
                defaultDataFetcher = wrapMapDataFetcher(
                        defaultDataFetcher, mapper, dependencyNode, engineState
                );
                continue;
            }

            // 结果处理：过滤列表
            if (Objects.equals(FILTER.getName(), directive.getName())) {
                String predicate = getArgumentFromDirective(directive, "predicate");
                String dependencyNode = getArgumentFromDirective(directive, "dependencyNode");
                defaultDataFetcher = wrapFilterDataFetcher(
                        defaultDataFetcher, predicate, dependencyNode, engineState
                );
                continue;
            }

            if (Objects.equals(SORT.getName(), directive.getName())) {
                Supplier<Boolean> defaultReversed = () -> (Boolean) SORT.getArgument("reversed").getDefaultValue();
                String sortKey = getArgumentFromDirective(directive, "key");
                Boolean reversed = getArgumentFromDirective(directive, "reversed");
                reversed = reversed != null ? reversed : defaultReversed.get();
                defaultDataFetcher = wrapSortDataFetcher(defaultDataFetcher, sortKey, reversed);
                continue;
            }

            if (Objects.equals(SORT_BY.getName(), directive.getName())) {
                Supplier<Boolean> defaultReversed = () -> (Boolean) SORT_BY.getArgument("reversed").getDefaultValue();
                String exp = getArgumentFromDirective(directive, "exp");
                Boolean reversed = getArgumentFromDirective(directive, "reversed");
                reversed = reversed != null ? reversed : defaultReversed.get();
                String dependencyNode = getArgumentFromDirective(directive, "dependencyNode");
                defaultDataFetcher = wrapSortByDataFetcher(
                        defaultDataFetcher, exp, reversed, dependencyNode, engineState
                );
                continue;
            }

            if (Objects.equals(ARGUMENT_TRANSFORM.getName(), directive.getName())) {
                String argument = getArgumentFromDirective(directive, "argument");
                String operateType = getArgumentFromDirective(directive, "operateType");
                String exp = getArgumentFromDirective(directive, "exp");
                String dependencyNode = getArgumentFromDirective(directive, "dependencyNode");
                defaultDataFetcher = wrapArgumentTransformDataFetcher(
                        argument, operateType, exp, dependencyNode, defaultDataFetcher, engineState
                );
                continue;
            }

            if (Objects.equals(FETCH_SOURCE.getName(), directive.getName())) {
                String name = getArgumentFromDirective(directive, "name");
                String mapper = getArgumentFromDirective(directive, "mapper");
                defaultDataFetcher = wrapFetchSourceDataFetcher(name,mapper,defaultDataFetcher,engineState);
                continue;
            }

        }

        //  skipBy放在最外层包装，因为如果跳过该字段，其他逻辑也不必在执行
        if (skip != null) {
            if (Objects.equals(SKIP_BY.getName(), skip.getName())) {
                DataFetcher<?> finalDefaultDF = defaultDataFetcher;
                Directive finalSkip = skip;
                defaultDataFetcher = env -> {
                    Map<String, Object> arguments = environment.getArguments();
                    String exp = getArgumentFromDirective(finalSkip, "exp");
                    Boolean isSkip = (Boolean) calExp(aviatorEvaluator, exp, arguments);

                    if (isSkip) {
                        return null;
                    }
                    return finalDefaultDF.get(env);
                };
            }
        }

        return defaultDataFetcher;
    }

    /**
     * 过滤list中的元素
     */
    private DataFetcher<?> wrapFilterDataFetcher(DataFetcher<?> defaultDF,
                                                 String predicate,
                                                 String dependencyNode,
                                                 ExecutionEngineState engineState) {

        boolean isAsyncFetcher = defaultDF instanceof AsyncDataFetcher;
        Executor innerExecutor = isAsyncFetcher ? ((AsyncDataFetcher<?>) defaultDF).getExecutor() : executor;
        DataFetcher<?> innerDataFetcher = isAsyncFetcher ? ((AsyncDataFetcher<?>) defaultDF).getWrappedDataFetcher() : defaultDF;

        return async(environment -> {
            Object originalResult = innerDataFetcher.get(environment);

            if (originalResult == null) {
                return null;
            }

            Map<String, Object> nodeEnv = null;
            if (dependencyNode != null) {
                nodeEnv = new HashMap<>();
                NodeTask nodeTask = getNodeValueFromState(engineState, dependencyNode);
                if (nodeTask.getFuture().isCompletedExceptionally()) {
                    nodeEnv.put(dependencyNode, null);
                } else {
                    nodeEnv.put(dependencyNode, nodeTask.getFuture().join());
                }
            }

            List<Object> filteredList = new ArrayList<>();
            for (Object ele : (Collection<?>) originalResult) {
                Map<String, Object> fieldMap = getCalMap(ele);
                // 如果为null则表示没有依赖的节点
                // 此时不应该在进行putAll操作
                if (nodeEnv != null) {
                    fieldMap.putAll(nodeEnv);
                }
                if ((Boolean) calExp(aviatorEvaluator, predicate, fieldMap)) {
                    filteredList.add(ele);
                }
            }
            return filteredList;
        }, innerExecutor);
    }

    // 按照指定key对元素进行排列
    private DataFetcher<?> wrapSortDataFetcher(DataFetcher<?> defaultDF, String sortKey, Boolean reversed) {
        boolean isAsyncFetcher = defaultDF instanceof AsyncDataFetcher;
        Executor innerExecutor = isAsyncFetcher ? ((AsyncDataFetcher<?>) defaultDF).getExecutor() : executor;
        DataFetcher<?> innerDataFetcher = isAsyncFetcher ? ((AsyncDataFetcher<?>) defaultDF).getWrappedDataFetcher() : defaultDF;

        return async(environment -> {
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
        }, innerExecutor);
    }


    private DataFetcher<?> wrapSortByDataFetcher(DataFetcher<?> defaultDF,
                                                 String sortExp,
                                                 Boolean reversed,
                                                 String dependencyNode,
                                                 ExecutionEngineState engineState) {

        boolean isAsyncFetcher = defaultDF instanceof AsyncDataFetcher;
        Executor innerExecutor = isAsyncFetcher ? ((AsyncDataFetcher<?>) defaultDF).getExecutor() : executor;
        DataFetcher<?> innerDataFetcher = isAsyncFetcher ? ((AsyncDataFetcher<?>) defaultDF).getWrappedDataFetcher() : defaultDF;

        return async(environment -> {
            Object originalResult = innerDataFetcher.get(environment);

            if (arraySize(originalResult) == 0) {
                return originalResult;
            }

            final Map<String, Object> nodeEnv;
            if (dependencyNode != null) {
                nodeEnv = new HashMap<>();
                NodeTask nodeTask = getNodeValueFromState(engineState, dependencyNode);
                if (!nodeTask.getFuture().isCompletedExceptionally()) {
                    nodeEnv.put(dependencyNode, nodeTask.getFuture().join());
                } else {
                    nodeEnv.put(dependencyNode, null);
                }
            } else {
                nodeEnv = null;
            }

            Collection<?> collection = (Collection<?>) originalResult;
            if (reversed) {
                return collection.stream().sorted(
                        Comparator.comparing(ele -> {
                            Map<String, Object> env = getCalMap(ele);
                            if (nodeEnv != null) {
                                env.putAll(nodeEnv);
                            }
                            return (Comparable) aviatorEvaluator.execute(sortExp, env);
                        }).reversed()
                ).collect(toList());
            }

            return collection.stream().sorted(
                    Comparator.comparing(ele -> {
                        Map<String, Object> env = getCalMap(ele);
                        if (nodeEnv != null) {
                            env.putAll(nodeEnv);
                        }
                        return (Comparable) aviatorEvaluator.execute(sortExp, env);
                    })
            ).collect(toList());
        }, innerExecutor);
    }

    // directive @map(mapper:String!, dependencyNode:String) on FIELD
    private DataFetcher<?> wrapMapDataFetcher(DataFetcher<?> defaultDF, String mapper, String dependencyNode, ExecutionEngineState engineState) {
        boolean isAsyncFetcher = defaultDF instanceof AsyncDataFetcher;
        Executor innerExecutor = isAsyncFetcher ? ((AsyncDataFetcher<?>) defaultDF).getExecutor() : executor;
        DataFetcher<?> innerDataFetcher = isAsyncFetcher ? ((AsyncDataFetcher<?>) defaultDF).getWrappedDataFetcher() : defaultDF;


        return async(environment -> {
            final Map<String, Object> nodeEnv;
            if (dependencyNode != null) {
                nodeEnv = new HashMap<>();
                NodeTask nodeTask = getNodeValueFromState(engineState, dependencyNode);
                if (nodeTask.getFuture().isCompletedExceptionally()) {
                    nodeEnv.put(dependencyNode, null);
                } else {
                    nodeEnv.put(dependencyNode, nodeTask.getFuture().join());
                }
            } else {
                nodeEnv = null;
            }


            Object fieldValue = innerDataFetcher.get(environment);
            HashMap<String, Object> expEnv = new HashMap<>(getCalMap(environment.getSource()));
            expEnv.put(environment.getField().getResultKey(), fieldValue);
            if (nodeEnv != null) {
                expEnv.putAll(nodeEnv);
            }

            return calExp(aviatorEvaluator, mapper, expEnv);
        }, innerExecutor);
    }

    private DataFetcher<?> wrapFetchSourceDataFetcher(String sourceName, String mapper, DataFetcher<?> defaultDataFetcher, ExecutionEngineState engineState) {
        boolean isAsyncFetcher = defaultDataFetcher instanceof AsyncDataFetcher;
        Executor innerExecutor = isAsyncFetcher ? ((AsyncDataFetcher<?>) defaultDataFetcher).getExecutor() : executor;
        DataFetcher<?> innerDataFetcher = isAsyncFetcher ? ((AsyncDataFetcher<?>) defaultDataFetcher).getWrappedDataFetcher() : defaultDataFetcher;

        // 串行执行数据的获取和返回
        DataFetcher<?> wrappedFetcher = environment -> {
            final Object fetchedValue = innerDataFetcher.get(environment);

            Object sourceValue = fetchedValue;
            if (mapper != null) {
                Map<String, Object> env = Collections.singletonMap(
                        environment.getField().getResultKey(), fetchedValue
                );
                sourceValue = aviatorEvaluator.execute(mapper, env);
            }
            FetchSourceTask fetchSourceTask = engineState.getFetchSourceByName().get(sourceName);
            if (fetchSourceTask.isList()) {
                fetchSourceTask.getListElementResult().add(sourceValue);
            }

            return fetchedValue;
        };

        return async(wrappedFetcher, innerExecutor);
    }

    // TODO ele这个常量也需要有说明
    // typeName: String -> Enum
    private DataFetcher<?> wrapArgumentTransformDataFetcher(String argumentName,
                                                            String operateType,
                                                            String exp,
                                                            String dependencyNode,
                                                            DataFetcher<?> defaultDF,
                                                            ExecutionEngineState engineState) {
        boolean isAsyncFetcher = defaultDF instanceof AsyncDataFetcher;
        Executor innerExecutor = isAsyncFetcher ? ((AsyncDataFetcher<?>) defaultDF).getExecutor() : executor;
        DataFetcher<?> innerDataFetcher = isAsyncFetcher ? ((AsyncDataFetcher<?>) defaultDF).getWrappedDataFetcher() : defaultDF;

        DataFetcher<?> wrappedFetcher = environment -> {

            final Map<String, Object> nodeEnv;
            if (dependencyNode != null) {
                nodeEnv = new HashMap<>();
                NodeTask nodeTask = getNodeValueFromState(engineState, dependencyNode);
                if (nodeTask.getFuture().isCompletedExceptionally()) {
                    nodeEnv.put(dependencyNode, null);
                } else {
                    nodeEnv.put(dependencyNode, nodeTask.getFuture().join());
                }
            } else {
                nodeEnv = null;
            }

            // 如果是列表元素过滤
            if (Objects.equals(operateType, Directives.ParamTransformType.FILTER.name())) {
                List<Object> argument = environment.getArgumentOrDefault(argumentName, Collections.emptyList());
                if (argument == null) {
                    return innerDataFetcher.get(environment);
                }

                argument = argument.stream().filter(ele -> {
                    Boolean isValid = (Boolean) aviatorEvaluator.execute(exp, Collections.singletonMap("ele", ele));
                    return isValid != null && isValid;
                }).collect(toList());

                Map<String, Object> newArguments = new HashMap<>(environment.getArguments());
                newArguments.put(argumentName, argument);
                DataFetchingEnvironment newEnvironment = DataFetchingEnvironmentImpl
                        .newDataFetchingEnvironment(environment).arguments(newArguments).build();
                return innerDataFetcher.get(newEnvironment);
            }

            // 如果是参数列表元素转换
            if (Objects.equals(operateType, Directives.ParamTransformType.LIST_MAP.name())) {
                List<Object> argument = environment.getArgumentOrDefault(argumentName, Collections.emptyList());
                if (argument == null) {
                    return innerDataFetcher.get(environment);
                }

                argument = argument.stream().map(ele -> {
                    Map<String, Object> transformEnv = new HashMap<>();
                    if (nodeEnv != null) {
                        transformEnv.putAll(nodeEnv);
                    }
                    // todo node节点名称也不能跟ele一样。TODO 取一个合适的名字
                    transformEnv.put("ele", ele);
                    return aviatorEvaluator.execute(exp, transformEnv);
                }).collect(toList());

                Map<String, Object> newArguments = new HashMap<>(environment.getArguments());
                newArguments.put(argumentName, argument);
                DataFetchingEnvironment newEnvironment = DataFetchingEnvironmentImpl
                        .newDataFetchingEnvironment(environment).arguments(newArguments).build();

                return innerDataFetcher.get(newEnvironment);
            }

            // 如果是 元素类型参数 转换
            if (Objects.equals(operateType, Directives.ParamTransformType.MAP.name())) {

                Map<String, Object> transformEnv = new HashMap<>(environment.getArguments());
                if (nodeEnv != null) {
                    transformEnv.putAll(nodeEnv);
                }
                Object newParam = aviatorEvaluator.execute(exp, transformEnv);

                Map<String, Object> newArguments = new HashMap<>(environment.getArguments());
                newArguments.put(argumentName, newParam);

                DataFetchingEnvironment newEnvironment = DataFetchingEnvironmentImpl
                        .newDataFetchingEnvironment(environment).arguments(newArguments).build();

                return innerDataFetcher.get(newEnvironment);
            }

            return innerDataFetcher.get(environment);
        };

        // 如果当前节点依赖了其他节点，也将其异步化，
        // 比如：
        // query{
        //      directiveField @dir(dependencyNode:"nodeX")
        //      nodeField @node(name:"nodeX")
        // }
        //
        // 如果串行执行则线程会阻塞在 directiveField 的解析中
        if (defaultDF instanceof AsyncDataFetcher || dependencyNode != null) {
            return async(wrappedFetcher, innerExecutor);
        } else {
            return wrappedFetcher;
        }
    }

    // todo logDebug
    private NodeTask getNodeValueFromState(ExecutionEngineState state, String nodeName) {
        Map<String, List<String>> sequenceTaskByNode = state.getSequenceTaskByNode();
        List<String> taskNameForNode = sequenceTaskByNode.get(nodeName);

        Map<String, NodeTask> taskByPath = state.getTaskByPath();
        List<NodeTask> taskForNodeValue = taskNameForNode.stream().map(taskByPath::get).collect(toList());

        NodeTask futureTask = taskForNodeValue.get(taskForNodeValue.size() - 1);

//        for (CompletableFuture<Object> future : Arrays.asList(new CompletableFuture<>())) {
//            future.whenCompleteAsync((ignored,ex)->{
//                if(ex!=null){
//                    for (NodeTask nodeTask : taskForNodeValue) {
//                        nodeTask.getFuture().completeExceptionally(ex);
//                    }
//                }
//            },executor);
//        }
//
        //

        // 从最上层
        for (NodeTask task : taskForNodeValue) {
            task.getFuture().whenComplete((ignore, ex) -> {
                if (ex != null) {
                    futureTask.getFuture().completeExceptionally(ex);
                }
            }).join();

            // 如果有异常，则中断执行
            if (futureTask.getFuture().isCompletedExceptionally()) {
                return futureTask;
            }
        }
        return futureTask;
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