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
import calculator.engine.metadata.NodeTask;
import calculator.engine.metadata.WrapperState;
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

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import static calculator.common.Tools.fieldPath;
import static calculator.common.Tools.getArgumentFromDirective;
import static calculator.common.Tools.isInListPath;
import static calculator.engine.metadata.CalculateDirectives.FILTER;
import static calculator.engine.metadata.CalculateDirectives.MAP;
import static calculator.engine.metadata.CalculateDirectives.MOCK;
import static calculator.engine.metadata.CalculateDirectives.SKIP_BY;
import static calculator.engine.metadata.CalculateDirectives.SORT_BY;
import static calculator.engine.function.ExpEvaluator.calExp;
import static calculator.engine.metadata.WrapperState.FUNCTION_KEY;
import static graphql.execution.Async.toCompletableFuture;
import static graphql.execution.instrumentation.SimpleInstrumentationContext.noOp;
import static graphql.schema.AsyncDataFetcher.async;
import static java.util.stream.Collectors.toList;
import static calculator.engine.metadata.CalculateDirectives.LINK;

public class ExecutionEngine implements Instrumentation {

    private final AviatorEvaluatorInstance aviatorEvaluator;

    private final ObjectMapper objectMapper;

    private ExecutionEngine(AviatorEvaluatorInstance aviatorEvaluator, ObjectMapper objectMapper) {
        Objects.requireNonNull(aviatorEvaluator);
        Objects.requireNonNull(objectMapper);
        this.aviatorEvaluator = aviatorEvaluator;
        this.objectMapper = objectMapper;
    }

    @Deprecated
    public static ExecutionEngine newInstance(AviatorEvaluatorInstance aviatorEvaluator, ObjectMapper objectMapper) {
        return new ExecutionEngine(aviatorEvaluator, objectMapper);
    }

    public static ExecutionEngine newInstance(Config config) {
        return new ExecutionEngine(config.getAviatorEvaluator(), config.getObjectMapper());
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

        WrapperState state = new WrapperState();
        StateParser visitor = StateParser.newInstanceWithState(state);
        traverser.visitDepthFirst(visitor);
        return state;
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
        return new InstrumentationContext() {

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

    // 如果是 调度任务节点，则在完成时更新state中对应的任务状态
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

            @Override
            public void onCompleted(ExecutionResult result, Throwable ex) {

                WrapperState state = parameters.getInstrumentationState();
                ResultPath resultPath = parameters.getExecutionStepInfo().getPath();
                NodeTask<Object> futureTask = parseNodeTask(state, resultPath);
                if (futureTask == null) {
                    return;
                }

                // 对于[VO]的情况、已经有元素解析失败、则其余元素是否解析成功没有必要了
                // TODO 应该是分情况：如果 [VO!] 则全部忽略，如果是 [VO] 则聚合成功的部分返回
                //      参考graphql sec: https://spec.graphql.org/draft/#sec-Combining-List-and-Non-Null
                if (futureTask.getFuture().isCompletedExceptionally()) {
                    // 保存已有的异常信息
                    if (ex != null) {
                        futureTask.getFuture().whenComplete((ignore, existEx) -> {
                            existEx.addSuppressed(ex);
                        });
                    }
                    return;
                }

                if (ex != null) {
                    futureTask.getFuture().completeExceptionally(ex);
                    return;
                }

                if (result.getData() != null) {
                    // kp 防止取一半的数据：见 tag_completeSubTask
                    if (isInListPath(futureTask)) {
                        futureTask.getTmpResult().add(result.getData());
                    } else {
                        futureTask.getFuture().complete(result.getData());
                        // kp tag_completeSubTask
                        completeSubTask(futureTask);
                    }
                } else {
                    // 对于没有结果的情况、仍然抛出异常，来终止程序运行
                    // 这里是否需要让调度器感知异常信息？不需要，包含在结果中了
                    futureTask.getFuture().completeExceptionally(new Throwable("empty result for " + fieldPath(resultPath)));
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
    private NodeTask<Object> parseNodeTask(WrapperState state, ResultPath resultPath) {
        if (state.getTaskByPath().isEmpty()) {
            return null;
        }

        String fieldPath = fieldPath(resultPath);
        if (!state.getTaskByPath().containsKey(fieldPath)) {
            return null;
        }

        return state.getTaskByPath().get(fieldPath);
    }

    private void completeSubTask(NodeTask<Object> futureTask) {
        if (futureTask == null || futureTask.getSubTaskList().isEmpty()) {
            return;
        }

        for (NodeTask subTask : futureTask.getSubTaskList()) {
            if (isInListPath(subTask) && !subTask.getFuture().isDone()) {
                subTask.getFuture().complete(subTask.getTmpResult());
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
            return wrappedDataFetcher(dirOnField, parameters.getEnvironment(), dataFetcher, parameters);
        }

        return dataFetcher;
    }

    private DataFetcher<?> wrapDFEnvironment(DataFetcher<?> dataFetcher,
                                             List<Directive> linkDirectiveList,
                                             InstrumentationFieldFetchParameters parameters) {
        WrapperState scheduleState = parameters.getInstrumentationState();
        Map<String, List<String>> sequenceTaskByNode = scheduleState.getSequenceTaskByNode();
        Map<String, NodeTask<Object>> taskByPath = scheduleState.getTaskByPath();
        DataFetchingEnvironment oldDFEnvironment = parameters.getEnvironment();
        Map<String, Object> newArguments = new HashMap<>(oldDFEnvironment.getArguments());

        Executor threadPool = ForkJoinPool.commonPool();
        final DataFetcher finalFetcher;
        if (dataFetcher instanceof AsyncDataFetcher) {
            finalFetcher = ((AsyncDataFetcher) dataFetcher).getWrappedDataFetcher();
            threadPool = ((AsyncDataFetcher) dataFetcher).getExecutor();
        } else {
            finalFetcher = dataFetcher;
        }

        return async(environment -> {
            for (Directive linkDir : linkDirectiveList) {
                // 获取当前依赖的任务列表
                String nodeName = getArgumentFromDirective(linkDir, "node");
                List<String> taskNameForNode = sequenceTaskByNode.get(nodeName);
                List<NodeTask<Object>> taskList = taskNameForNode.stream().map(taskByPath::get).collect(toList());
                NodeTask<Object> valueTask = getValueFromTasks(taskList);
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
     * todo 抽象成公共方法。
     *
     * @param taskForNodeValue tasks which the node rely on
     * @return
     */
    private NodeTask<Object> getValueFromTasks(List<NodeTask<Object>> taskForNodeValue) {
        NodeTask<Object> futureTask = taskForNodeValue.get(taskForNodeValue.size() - 1);

        // kp 每次join的时候都要考虑下、是哪个线程执行的这个任务
        for (NodeTask<Object> task : taskForNodeValue) {
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
     * @param dirOnField
     * @param environment
     * @param instrumentationParameters
     * @return
     */
    private DataFetcher<?> wrappedDataFetcher(List<Directive> dirOnField,
                                              DataFetchingEnvironment environment,
                                              DataFetcher<?> defaultDF,
                                              InstrumentationFieldFetchParameters instrumentationParameters) {

        // kp skip在最外层执行、防止冗余的操作
        Directive skip = null;
        for (Directive directive : dirOnField) {
            if (Objects.equals(SKIP_BY.getName(), directive.getName())) {
                skip = directive;
                continue;
            }

            if (Objects.equals(MOCK.getName(), directive.getName())) {
                defaultDF = ignore -> getArgumentFromDirective(directive, "value");
                continue;
            }

            if (Objects.equals(MAP.getName(), directive.getName())) {
                String mapper = getArgumentFromDirective(directive, "mapper");
                defaultDF = getMapperDF(aviatorEvaluator, mapper, instrumentationParameters.getInstrumentationState());
                continue;
            }

            // 过滤列表
            if (Objects.equals(FILTER.getName(), directive.getName())) {
                String predicate = getArgumentFromDirective(directive, "predicate");
                defaultDF = getFilterDF(aviatorEvaluator, defaultDF, predicate);
                continue;
            }

            if (Objects.equals(SORT_BY.getName(), directive.getName())) {
                String key = getArgumentFromDirective(directive, "key");
                // todo 其实获取不到默认值
                Boolean reversed = getArgumentFromDirective(directive, "reversed");
                defaultDF = wrapSortByDF(defaultDF, key, reversed);
                continue;
            }
        }

        // kp skipBy放在最外层包装，因为如果skipBy后，其他逻辑也不必在执行
        if (skip != null) {
            if (Objects.equals(SKIP_BY.getName(), skip.getName())) {
                DataFetcher finalDefaultDF = defaultDF;
                Directive finalSkip = skip;
                defaultDF = env -> {
                    Map<String, Object> arguments = environment.getArguments();
                    String exp = getArgumentFromDirective(finalSkip, "exp");
                    Boolean isSkip = (Boolean) calExp(aviatorEvaluator, exp, arguments);

                    if (true == isSkip) {
                        return null;
                    }
                    return finalDefaultDF.get(env);
                };
            }
        }

        return defaultDF;
    }

    /**
     * 过滤list中的元素
     */
    private DataFetcher<?> getFilterDF(AviatorEvaluatorInstance aviatorEvaluator, DataFetcher defaultDF, String predicate) {
        return environment -> {
            // 元素必须是序列化为Map的数据
            // kp 对于 Collection<基本类型> 有没有坑
            // kp 非常重要：考虑到使用的是 AsyncDataFetcher，结果可能包装成CompletableFuture中了
            CompletableFuture<Object> resultFuture = toCompletableFuture(defaultDF.get(environment));

            return resultFuture.handle((res, ex) -> {
                if (ex != null) {
                    throw new RuntimeException(ex);
                }
                Collection<Map<String, Object>> collection = (Collection) res;
                return collection.stream().filter(ele -> (Boolean) calExp(aviatorEvaluator, predicate, ele)).collect(Collectors.toList());
            });
        };
    }

    // 按照指定key对元素进行排列
    private DataFetcher<?> wrapSortByDF(DataFetcher<?> defaultDF, String key, Boolean reversed) {
        return environment -> {
            CompletableFuture<?> resultFuture = toCompletableFuture(defaultDF.get(environment));
            return resultFuture.handle((res, ex) -> {
                if (ex != null) {
                    throw new RuntimeException(ex);
                }

                Collection<Map<String, Object>> collection = (Collection<Map<String, Object>>) res;
                if (reversed != null && reversed) {
                    return collection.stream().sorted(
                            Comparator.comparing(entry -> (Comparable) ((Map) entry).get(key)).reversed()
                    ).collect(Collectors.toList());
                } else {
                    return collection.stream().sorted(
                            Comparator.comparing(entry -> (Comparable) entry.get(key))
                    ).collect(Collectors.toList());
                }
            });
        };
    }

    // map：转换
    private DataFetcher<?> getMapperDF(AviatorEvaluatorInstance aviatorEvaluator, String mapper, WrapperState state) {
        // kp 如果 mapper 的函数中使用main函数执行可能阻塞的任务、则会影响graphql引擎的计划执行，因此此处使用线程池
        return async(environment -> {
            // todo 兼容source不是map呢
            Map<String, Object> variable = environment.getSource();
            variable.put(FUNCTION_KEY, state);
            return calExp(aviatorEvaluator, mapper, variable);
        });
    }

}