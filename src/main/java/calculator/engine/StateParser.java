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

import calculator.engine.metadata.CalculateDirectives;
import calculator.engine.metadata.NodeTask;
import calculator.engine.metadata.WrapperState;
import graphql.Internal;
import graphql.analysis.QueryVisitor;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorFragmentSpreadEnvironment;
import graphql.analysis.QueryVisitorInlineFragmentEnvironment;
import graphql.language.Directive;
import graphql.language.Node;
import graphql.schema.GraphQLList;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static calculator.common.Tools.getArgumentFromDirective;
import static calculator.common.Tools.visitPath;


@Internal
public class StateParser implements QueryVisitor {

    private WrapperState state;

    private StateParser(WrapperState state) {
        this.state = state;
    }

    public static StateParser newInstanceWithState(WrapperState state) {
        return new StateParser(state);
    }

    @Override
    public void visitField(QueryVisitorFieldEnvironment environment) {
        if (environment.getTraverserContext().getPhase() != TraverserContext.Phase.ENTER) {
            return;
        }

        // 该节点被node注解，保存该节点及其父亲节点
        List<Directive> directives = environment.getField().getDirectives(CalculateDirectives.NODE.getName());
        if (directives != null && !directives.isEmpty()) {

            Directive nodeDir = directives.get(0);
            String nodeName = getArgumentFromDirective(nodeDir, "name");
            Map<String, NodeTask<Object>> taskByPath = state.getTaskByPath();

            // 保存该节点及其父节点路径，例如a、a.b、a.b.c
            // 同时保存每个路径：1.是否是list下的节点；2.父节点和子节点；
            ArrayList<String> pathList = new ArrayList<>();
            handle(false, environment, pathList, taskByPath);
            // kp traverserContext 是某一次visitor中所有节点共用的
            environment.getTraverserContext().setAccumulate(null);
            state.getSequenceTaskByNode().put(nodeName, pathList);
        }
    }

    /**
     * 获取 environment对应节点 及其递归上层节点对应的路径、保存在pathList，
     * 表示了解@node注解的节点必须指导哪些路径节点的完成情况，以及每个路径节点对应的
     * 异步任务、保存在taskByPath中。
     *
     * @param isRecursive 是否是递归调用
     * @param environment 当前节点visit上下文
     * @param pathList    kp 保存父节点、祖父节点... 的绝对路径。
     * @param taskByPath  kp 保存字段路径对应的异步任务
     */
    private void handle(boolean isRecursive, QueryVisitorFieldEnvironment environment, ArrayList<String> pathList, Map<String, NodeTask<Object>> taskByPath) {
        if (environment == null) {
            return;
        }

        // 当前节点是否已经在注册为 FutureTask
        TraverserContext<Node> traverserContext = environment.getTraverserContext();
        QueryVisitorFieldEnvironment parentEnv = environment.getParentEnvironment();

        // 对于一些共用的父路径可能会走到这个逻辑，例如
        // query {
        //      userInfo{
        //          id @node(name:"uId")
        //          name @node(name:"uName")
        //      }
        // }
        // 解析 @uId 和 @uName 的时候都会解析到 userInfo，后者、例如 @uName 在此判断为true，
        // 此时需要将 userInfo 作为 @uName 依赖的绝对路径之一，但是已经不再需要向 taskByPath 中添加任务
        //
        // kp 对于以下情况，注意解析@uName时不要忘记：
        //          1. 将 nameFuture 作为 userInfoFuture 的subTask/addSubTaskList;
        //          2. 将 baseInfo 也放进 @uName节点依赖的任务路径列表。
        // query {
        //      baseInfo{
        //          userInfo{
        //              id @node(name:"uId")
        //              name @node(name:"uName")
        //          }
        //      }
        // }
        //
        if (traverserContext.getNewAccumulate() != null) {
            String absoluteResultPath = visitPath(environment);
            pathList.add(absoluteResultPath);
            // 递归回去的时候 parentEnv 可能为null
            handle(true, parentEnv, pathList, taskByPath);
            return;
        }

        // Query root
        if (parentEnv == null) {
            String absoluteResultPath = environment.getField().getResultKey();
            NodeTask<Object> task = NodeTask.newBuilder()
                    .isTopNode(true)
                    .path(absoluteResultPath)
                    .isList(isList(environment))
                    .isAnnotated(!isRecursive)
                    .future(new CompletableFuture<Object>())
                    .build();
            // kp: 将当前节点的任务作为其上下文累计值
            environment.getTraverserContext().setAccumulate(task);
            taskByPath.put(absoluteResultPath, task);
            pathList.add(absoluteResultPath);
            return;
        }

        // kp 先递归解析父节点的原因是、在创建自节点对应的FutureTask时
        //    需要设置parent、并且为父节点设置 addSubTaskList
        handle(true, parentEnv, pathList, taskByPath);

        NodeTask<Object> parentTask = parentEnv.getTraverserContext().getNewAccumulate();
        String nestedKey = visitPath(environment);
        NodeTask<Object> task = NodeTask.newBuilder()
                .isTopNode(false)
                .path(nestedKey)
                .isList(isList(environment))
                .isAnnotated(!isRecursive)
                .parent(parentTask)
                .future(new CompletableFuture<Object>())
                .build();
        parentTask.addSubTaskList(task);
        // kp: 将当前节点的任务作为其上下文累计值
        environment.getTraverserContext().setAccumulate(task);
        taskByPath.put(nestedKey, task);
        pathList.add(nestedKey);
    }

    // 当前字段是否是list 并且不是叶子节点
    private boolean isList(QueryVisitorFieldEnvironment environment) {
        return environment.getFieldDefinition().getType() instanceof GraphQLList;
    }


    @Override
    public void visitInlineFragment(QueryVisitorInlineFragmentEnvironment queryVisitorInlineFragmentEnvironment) {

    }

    @Override
    public void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment queryVisitorFragmentSpreadEnvironment) {

    }
}