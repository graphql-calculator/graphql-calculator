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

import calculator.engine.metadata.FutureTask;
import calculator.engine.metadata.WrapperState;
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
import graphql.schema.GraphQLFieldDefinition;
import graphql.validation.ValidationError;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import static calculator.CommonTools.fieldPath;
import static calculator.CommonTools.getAliasOrName;
import static calculator.CommonTools.getArgumentFromDirective;
import static calculator.engine.CalculateDirectives.filter;
import static calculator.engine.CalculateDirectives.map;
import static calculator.engine.CalculateDirectives.mock;
import static calculator.engine.CalculateDirectives.skipBy;
import static calculator.engine.CalculateDirectives.sortBy;
import static calculator.engine.ExpCalculator.calExp;
import static calculator.engine.metadata.WrapperState.FUNCTION_KEY;
import static graphql.execution.Async.toCompletableFuture;
import static graphql.execution.instrumentation.SimpleInstrumentationContext.noOp;
import static graphql.schema.AsyncDataFetcher.async;
import static java.util.stream.Collectors.toList;
import static calculator.engine.CalculateDirectives.link;

public class ExecutionEngineWrapper implements Instrumentation {

    // ============================================== getSingleInstance =================================================================
    private ExecutionEngineWrapper() {
    }

    private static final ExecutionEngineWrapper ENGINE_WRAPPER = new ExecutionEngineWrapper();

    public static ExecutionEngineWrapper getEngineWrapper() {
        return ENGINE_WRAPPER;
    }

    // ============================================== create dataHolder for engine wrapper ==============================================

    //  需要预分析，因为对于 person-> name @node("personName")，如果不预先分析、就不会知道person也是dag任务
    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {

        String query = parameters.getExecutionInput().getQuery();
        QueryTraverser traverser = QueryTraverser.newQueryTraverser()
                .schema(parameters.getSchema())
                .document(Parser.parse(query))
                .variables(Collections.emptyMap()).build();

        WrapperState state = new WrapperState();
        StateParseVisitor visitor = StateParseVisitor.newInstanceWithState(state);
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
        return new InstrumentationContext(){

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
    // todo 这里抛出异常了可能会影响调度执行计划
    @Override
    public InstrumentationContext<ExecutionResult> beginFieldComplete(InstrumentationFieldCompleteParameters parameters) {
        // fieldList 也会在这里切一下
        return getContextOpt(parameters).orElse(noOp());
    }

    private Optional<InstrumentationContext<ExecutionResult>> getContextOpt(InstrumentationFieldCompleteParameters parameters) {
        /**
         * 如果 state中证明、就没有node
         *
         * todo 或者node已经被标记使用、则可以不再进行如下操作了
         */
        // 每次分析都会耗时，后续可以确认该方法是否是热点方法、提供异步分析
        WrapperState scheduleState = parameters.getInstrumentationState();
        Map<String, FutureTask<Object>> taskByPath = scheduleState.getTaskByPath();
        if (taskByPath.isEmpty()) {
            return Optional.empty();
        }

        // 从根节点到当前节点的路径
        String fieldPath = fieldPath(parameters.getExecutionStepInfo().getPath());
        if (!taskByPath.containsKey(fieldPath)) {
            return Optional.empty();
        }

        FutureTask<Object> futureTask = taskByPath.get(fieldPath);
        InstrumentationContext<ExecutionResult> instrumentationContext = new InstrumentationContext<ExecutionResult>() {
            @Override
            public void onDispatched(CompletableFuture<ExecutionResult> future) {
                future.whenComplete((result, ex) -> {
                    // 对于[vo]的情况、已经有元素解析失败、则其余元素是否解析成功没有必要了
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
                        if (futureTask.isList()) {
                            if (futureTask.getFuture().isDone()) {
                                List prevRes = (List) futureTask.getFuture().join();
                                prevRes.add(result.getData());
                            } else {
                                List list = new LinkedList();
                                list.add(result.getData());
                                // todo 这里 complete 后影响
                                futureTask.getFuture().complete(list);
                            }
                        } else {
                            futureTask.getFuture().complete(result.getData());
                        }

                    } else {
                        // 对于没有结果的情况、仍然抛出异常，来终止程序运行
                        // 这里是否需要让调度器感知异常信息？不需要，包含在结果中了
                        futureTask.getFuture().completeExceptionally(new Throwable("empty result for " + fieldPath));
                    }
                });
            }

            @Override
            public void onCompleted(ExecutionResult result, Throwable t) {
            }
        };
        return Optional.of(instrumentationContext);
    }

    // 如果有link节点，则分析其每一个依赖的任务，并更新参数
    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
        // link 优先级比较高
        List<Directive> linkDirectiveList = parameters.getEnvironment().getField().getDirectives(link.getName());
        if (linkDirectiveList != null && !linkDirectiveList.isEmpty()) {
            return wrapDFEnvironment(dataFetcher, linkDirectiveList, parameters);
        }

        List<Directive> dirOnField = parameters.getEnvironment().getField().getDirectives();
        if (!dirOnField.isEmpty()) {
            // 如果 指令就是为了动态改变 的运行时行为，那么这里就有必要直接返回
            return wrappedDataFetcher(dirOnField, parameters.getEnvironment(), dataFetcher);
        }

        return dataFetcher;
    }

    private DataFetcher<?> wrapDFEnvironment(DataFetcher<?> dataFetcher, List<Directive> linkDirectiveList, InstrumentationFieldFetchParameters parameters) {
        WrapperState scheduleState = parameters.getInstrumentationState();
        Map<String, List<String>> sequenceTaskByNode = scheduleState.getSequenceTaskByNode();
        Map<String, FutureTask<Object>> taskByPath = scheduleState.getTaskByPath();
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
                List<FutureTask<Object>> taskList = taskNameForNode.stream().map(taskByPath::get).collect(toList());
                // todo 线程阻塞
                FutureTask<Object> valueTask = getValueFromTasks(taskList);
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
    private FutureTask<Object> getValueFromTasks(List<FutureTask<Object>> taskForNodeValue) {
        FutureTask<Object> futureTask = taskForNodeValue.get(taskForNodeValue.size() - 1);

        // todo 每次join的时候都要考虑下、是哪个线程执行的这个任务
        for (FutureTask<Object> task : taskForNodeValue) {
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
     * @return
     */
    private DataFetcher<?> wrappedDataFetcher(List<Directive> dirOnField, DataFetchingEnvironment environment, DataFetcher<?> defaultDF) {

        // kp：skip在最外层执行、防止冗余的操作
        Directive skip = null;
        for (Directive directive : dirOnField) {
            if (Objects.equals(skipBy.getName(), directive.getName())) {
                skip = directive;
                continue;
            }

            if (Objects.equals(mock.getName(), directive.getName())) {
                defaultDF = ignore -> getArgumentFromDirective(directive, "value");
                continue;
            }

            if (Objects.equals(map.getName(), directive.getName())) {
                String mapper = getArgumentFromDirective(directive, "mapper");
                defaultDF = getMapperDF(defaultDF, mapper);
                continue;
            }

            // 过滤列表
            if (Objects.equals(filter.getName(), directive.getName())) {
                String predicate = getArgumentFromDirective(directive, "predicate");
                defaultDF = getFilterDF(defaultDF, predicate);
                continue;
            }

            if (Objects.equals(sortBy.getName(), directive.getName())) {
                String key = getArgumentFromDirective(directive, "key");
                // todo 其实获取不到默认值
                Boolean reversed = getArgumentFromDirective(directive, "reversed");
                defaultDF = wrapSortByDF(defaultDF, key, reversed);
                continue;
            }
        }

        // kp：skipBy放在最外层包装，因为如果skipBy后，其他逻辑也不必在执行
        if (skip != null) {
            if (Objects.equals(skipBy.getName(), skip.getName())) {
                DataFetcher finalDefaultDF = defaultDF;
                Directive finalSkip = skip;
                defaultDF = env -> {
                    Map<String, Object> arguments = environment.getArguments();
                    String exp = getArgumentFromDirective(finalSkip, "exp");
                    Boolean isSkip = (Boolean) calExp(exp, arguments);

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
    private DataFetcher<?> getFilterDF(DataFetcher defaultDF, String predicate) {
        return environment -> {
            // 元素必须是序列化为Map的数据
            // todo 对于 Collection<基本类型> 有没有坑
            // todo 非常重要：考虑到使用的是 AsyncDataFetcher，结果可能包装成CompletableFuture中了
            CompletableFuture<Object> resultFuture = toCompletableFuture(defaultDF.get(environment));

            return resultFuture.handle((res, ex) -> {
                if (ex != null) {
                    throw new RuntimeException(ex);
                }
                Collection<Map<String, Object>> collection = (Collection) res;
                return collection.stream().filter(ele -> (Boolean) calExp(predicate, ele)).collect(Collectors.toList());
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
    private DataFetcher<?> getMapperDF(DataFetcher defaultDF, String mapper) {
        return environment -> {
            /**
             * 也可能是普通值：不能用在list上、但是可以用在list的元素上
             */
            Object oriVal = defaultDF.get(environment);
            Map<String, Object> variable = environment.getSource();
            variable.put(getAliasOrName(environment.getField()), oriVal);
            return calExp(mapper, variable);
        };
    }
}