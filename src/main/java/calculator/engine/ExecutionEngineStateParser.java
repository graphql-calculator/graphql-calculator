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
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static calculator.common.Tools.getArgumentFromDirective;
import static calculator.common.Tools.visitPath;
import static calculator.common.VisitorUtils.isInListPath;
import static calculator.common.VisitorUtils.isListNode;


@Internal
public class ExecutionEngineStateParser implements QueryVisitor {

    private WrapperState state;

    public ExecutionEngineStateParser(WrapperState state) {
        this.state = state;
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
            Map<String, NodeTask> taskByPath = state.getTaskByPath();

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
     * 表示了解@node注解的节点必须知道哪些路径节点的完成情况，以及每个路径节点对应的
     * 异步任务、保存在taskByPath中。
     *
     * @param isRecursive 是否是递归调用
     * @param visitorEnv 当前节点visit上下文
     * @param pathList    kp 保存父节点、祖父节点... 的绝对路径。
     * @param taskByPath  kp 保存字段路径对应的异步任务
     */
    private void handle(boolean isRecursive,
                        QueryVisitorFieldEnvironment visitorEnv,
                        ArrayList<String> pathList,
                        Map<String, NodeTask> taskByPath) {

        // 1. 一个node依赖任务列表的 "根节点任务"应该是在 其与 root 路径中、
        // 和 Query root 距离最远的 不在list中的节点，如下 uid 和 uName 依赖的上层任务从 levelOneList 开始算起，
        // 因为 levelTwoList 也在list中，即 levelOneList 中：
        // query {
        //       levelOne
        //          levelTwo{
        //              levelOneList{
        //                  fieldInLevelOne
        //                  levelTwoList{
        //                      # [ levelOne#levelTwo#levelOneList,
        //                      #   levelOne#levelTwo#levelOneList#levelTwoList,
        //                      #   levelOne#levelTwo#levelOneList#levelTwoList#id
        //                      # ]
        //                      id @node(name:"uId")
        //
        //                      # [ levelOne#levelTwo#levelOneList,
        //                      #   levelOne#levelTwo#levelOneList#levelTwoList,
        //                      #   levelOne#levelTwo#levelOneList#levelTwoList#name
        //                      # ]
        //                      name @node(name:"uName")
        //                  }
        //              }
        //          }
        //      }
        // }
        //
        // 2. 对于如下情况，该方法算法可能出现parentEnv为null的情况。
        // query {
        //       userInfoList{
        //              # [userInfoList, userInfoList#id]
        //              id @node(name: "uId")
        //              # [userInfoList, userInfoList#name]
        //              name @node(name: "name")
        //       }
        // }
        //
        // 3. 对于如下情况，直接解析当前节点即可
        // query {
        //       userInfo{
        //              # [ userInfo#id ]
        //              id @node(name: "uId")
        //              # [ userInfo#name ]
        //              name @node(name: "name")
        //       }
        // }
        //
        // tips：如果一个节点可以作其某个子节点的父亲任务，则肯定也可以做起另外一个子节点的父亲任务。反之也成立。
        //

        /**
         * 1. 如果不是list元素，则解析后不递归返回;
         * 2. 如果是list元素，先递归解析父亲元素，然后在解析当前任务。
         */
        if (!isInListPath(visitorEnv)) {
            String absolutePath = visitPath(visitorEnv);
            // 当前节点依赖的任务
            pathList.add(absolutePath);

            // 对于已经解析过、放到 taskByPath 的任务不可以在重复创建任务
            if (visitorEnv.getTraverserContext().getNewAccumulate() == null) {
                NodeTask task = NodeTask.newBuilder()
                        .isTopTaskNode(true)
                        .path(absolutePath)
                        .isList(isListNode(visitorEnv))
                        .isAnnotated(!isRecursive)
                        .future(new CompletableFuture<>())
                        .build();
                visitorEnv.getTraverserContext().setAccumulate(task);
                taskByPath.put(absolutePath, task);
            }
            return;
        }

        // 先递归解析父节点的原因：在创建自节点对应的NodeTask时需要设置parentTask，
        // 并将当前节点代表的任务设置为parentTask的子任务。
        handle(true, visitorEnv.getParentEnvironment(), pathList, taskByPath);

        // 当前节点代表的 Node Task
        String absolutePath = visitPath(visitorEnv);
        pathList.add(absolutePath);
        // 对于情况一中的任务 levelOne#levelTwo#levelOneList#levelTwoList
        // uId 和 uName 都会分析此节点，第二次分析的时候已经不需要为此节点创建任务和设置父亲节点了，只设置子节点即可
        NodeTask parentTask = visitorEnv.getParentEnvironment().getTraverserContext().getNewAccumulate();
        NodeTask currentNodeTask;
        if (visitorEnv.getTraverserContext().getNewAccumulate() == null) {
            currentNodeTask = NodeTask.newBuilder()
                    .isTopTaskNode(false)
                    .path(absolutePath)
                    .isList(isListNode(visitorEnv))
                    .isAnnotated(!isRecursive)
                    .parent(parentTask)
                    .future(new CompletableFuture<>())
                    .build();
            visitorEnv.getTraverserContext().setAccumulate(currentNodeTask);
            taskByPath.put(absolutePath, currentNodeTask);
        } else {
            currentNodeTask = visitorEnv.getTraverserContext().getNewAccumulate();
        }
        parentTask.addSubTaskList(currentNodeTask);
    }


    @Override
    public void visitInlineFragment(QueryVisitorInlineFragmentEnvironment queryVisitorInlineFragmentEnvironment) {

    }

    @Override
    public void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment queryVisitorFragmentSpreadEnvironment) {

    }

}