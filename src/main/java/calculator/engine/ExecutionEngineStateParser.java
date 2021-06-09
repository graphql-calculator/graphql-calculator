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

import calculator.engine.metadata.Directives;
import calculator.engine.metadata.FetchSourceTask;
import calculator.engine.metadata.NodeTask;
import graphql.Internal;
import graphql.analysis.QueryVisitor;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorFragmentSpreadEnvironment;
import graphql.analysis.QueryVisitorInlineFragmentEnvironment;
import graphql.language.Directive;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static calculator.common.Tools.getArgumentFromDirective;
import static calculator.common.Tools.visitPath;
import static calculator.common.VisitorUtils.isInList;
import static calculator.common.VisitorUtils.isListNode;
import static calculator.common.VisitorUtils.parentPathList;


/**
 * TODO 有些操作可以作为func、用到的时候在执行
 */
@Internal
public class ExecutionEngineStateParser implements QueryVisitor {

    private ExecutionEngineState.Builder builder = new ExecutionEngineState.Builder();


    public ExecutionEngineState getExecutionEngineState() {
        return builder.build();
    }

    @Override
    public void visitField(QueryVisitorFieldEnvironment environment) {
        if (environment.getTraverserContext().getPhase() != TraverserContext.Phase.ENTER) {
            return;
        }

        // 该节点被node注解，保存该节点及其父亲节点
        List<Directive> directives = environment.getField().getDirectives(Directives.FETCH_SOURCE.getName());
        if (directives != null && !directives.isEmpty()) {
            // non-repeatable directive
            Directive nodeDir = directives.get(0);
            String sourceName = getArgumentFromDirective(nodeDir, "name");
            String mapper = getArgumentFromDirective(nodeDir, "mapper");

            ArrayList<String> topTaskPathList = new ArrayList<>();
            ArrayList<String> queryTaskPathList = new ArrayList<>();
            parseFetchSourceInfo(sourceName, true, mapper, environment, topTaskPathList, queryTaskPathList, builder);
            // traverserContext is shared in a visitor-operation.
            environment.getTraverserContext().setAccumulate(null);
            builder.topTaskList(sourceName, topTaskPathList);
            builder.queryTaskList(sourceName,queryTaskPathList);
        }
    }

    /**
     * 获取 @fetchSource 注释的节点相关数据保存在 ExecutionEngineState 中：
     * 1. 代表该节点的 FetchSourceTask；
     * 2. 该节点所代表的异步任务结束所依赖的父节点列表 topTaskPathList ；
     * 3. topTask节点的父亲节点列表——这些节点失败则topTaskPathList中所有的异步任务都不会执行。
     *
     * @param isAnnotatedNode 是否是递归调用该方法，kp 是否是被 @fetchSource 注解的节点
     * @param mapper 获取 fetchSource 后进行数据转换的表达式
     * @param visitorEnv 请求变量
     * @param topTaskPathList  @fetchSource 注解的节点完成所依赖的顶层节点。
     * @param queryTaskPathList 顶层节点的父亲节点。这部分节点如果解析失败，则获取@fetchSource值的异步任务不会执行。
     *                          todo 但是获取这些异步任务有不依赖这些顶层节点完成——开始就可以，注意怎么判断。
     * @param engineStateBuilder
     */
    private void parseFetchSourceInfo(String sourceName,
                                      boolean isAnnotatedNode,
                                      String mapper,
                                      QueryVisitorFieldEnvironment visitorEnv,
                                      ArrayList<String> topTaskPathList,
                                      ArrayList<String> queryTaskPathList,
                                      ExecutionEngineState.Builder engineStateBuilder) {

        String fieldFullPath = visitPath(visitorEnv);

        /**
         * step_1边界条件，如果是topTaskNode，则直接包装、返回
         */
        if (!isInList(visitorEnv)) {
            topTaskPathList.add(fieldFullPath);

            // 对于已经解析过、放到 taskByPath 的任务不可以在重复创建任务
            if (visitorEnv.getTraverserContext().getNewAccumulate() == null) {
                FetchSourceTask task = FetchSourceTask.newFetchSourceTask()
                        .sourceName(sourceName)
                        .isAnnotatedNode(isAnnotatedNode)
                        .isListType(isListNode(visitorEnv))
                        .isInList(true)
                        .taskFuture(new CompletableFuture<>())
                        .mapper(mapper)
                        .build();
                visitorEnv.getTraverserContext().setAccumulate(task);
                engineStateBuilder.taskByPath(fieldFullPath, task);
            }
            queryTaskPathList.addAll(parentPathList(visitorEnv));
            return;
        }

        // 先递归解析父节点的原因：在创建自节点对应的NodeTask时需要设置parentTask，
        // 并将当前节点代表的任务设置为parentTask的子任务。
        parseFetchSourceInfo(
                sourceName, false, null,
                visitorEnv.getParentEnvironment(), topTaskPathList, queryTaskPathList, engineStateBuilder
        );
        // 递归执行该逻辑，因此 topTaskPathList 中的节点顺序也是从上到下的
        topTaskPathList.add(fieldFullPath);

        FetchSourceTask parentTask = visitorEnv.getParentEnvironment().getTraverserContext().getNewAccumulate();
        FetchSourceTask currentTask;
        if (visitorEnv.getTraverserContext().getNewAccumulate() == null) {
            currentTask = FetchSourceTask.newFetchSourceTask()
                    .sourceName(sourceName)
                    .isAnnotatedNode(isAnnotatedNode)
                    .isListType(isListNode(visitorEnv))
                    .isInList(true)
                    .taskFuture(new CompletableFuture<>())
                    .mapper(mapper)
                    .build();
            visitorEnv.getTraverserContext().setAccumulate(currentTask);
            engineStateBuilder.taskByPath(fieldFullPath, currentTask);
        } else {
            // 对于 [list-a,[b,[c,d]]] 这种情况，先解析c、然后解析d的时候递归会执行到这里
            currentTask = visitorEnv.getTraverserContext().getNewAccumulate();
        }
        parentTask.childrenTaskList(currentTask);
    }


    @Override
    public void visitInlineFragment(QueryVisitorInlineFragmentEnvironment queryVisitorInlineFragmentEnvironment) {

    }

    @Override
    public void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment queryVisitorFragmentSpreadEnvironment) {

    }

}