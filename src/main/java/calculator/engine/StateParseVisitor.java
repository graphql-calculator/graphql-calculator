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
import graphql.Internal;
import graphql.analysis.QueryVisitor;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorFragmentSpreadEnvironment;
import graphql.analysis.QueryVisitorInlineFragmentEnvironment;
import graphql.language.Directive;
import graphql.language.Node;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.util.TraverserContext;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static calculator.CommonTools.getArgumentFromDirective;
import static calculator.CommonTools.visitPath;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;


@Internal
public class StateParseVisitor implements QueryVisitor {

    private WrapperState state;

    private StateParseVisitor(WrapperState state) {
        this.state = state;
    }

    public static StateParseVisitor newInstanceWithState(WrapperState state) {
        return new StateParseVisitor(state);
    }

    @Override
    public void visitField(QueryVisitorFieldEnvironment environment) {
        if (environment.getTraverserContext().getPhase() != TraverserContext.Phase.ENTER) {
            return;
        }

        /**
         * 该节点被node注解，保存该节点及其父亲节点
         */
        List<Directive> directives = environment.getField().getDirectives(CalculateDirectives.node.getName());
        if (directives != null && !directives.isEmpty()) {
            Directive nodeDir = directives.get(0);
            String nodeName = getArgumentFromDirective(nodeDir, "name");

            Map<String, FutureTask<Object>> taskByPath = state.getTaskByPath();

            LinkedList<String> pathList = new LinkedList<>();
            handle(false, environment, pathList, taskByPath);
            environment.getTraverserContext().setAccumulate(null);
            state.getSequenceTaskByNode().put(nodeName, pathList);
        }
    }

    /**
     * 获取 environment 对应节点
     *
     * @param environment
     * @param pathList    保存父节点、祖父节点... 的绝对路径。
     * @param taskByPath
     * @return
     */
    private void handle(boolean isRecursive,QueryVisitorFieldEnvironment environment, LinkedList<String> pathList, Map<String, FutureTask<Object>> taskByPath) {
        // 当前节点是否已经在注册为 FutureTask
        TraverserContext<Node> traverserContext = environment.getTraverserContext();
        if (traverserContext.getNewAccumulate() != null) {
            String nestedKey = visitPath(environment);
            pathList.add(nestedKey);
            return;
        }

        QueryVisitorFieldEnvironment parentEnv = environment.getParentEnvironment();

        // Query root
        if (parentEnv == null) {
            String resultKey = environment.getField().getResultKey();
            FutureTask task = FutureTask.newBuilder()
                    .isTopNode(true)
                    .path(resultKey)
                    .isList(isListAndNotScalar(environment))
                    .future(new CompletableFuture())
                    .build();
            // kp: 将当前节点的任务作为其上下文累计值
            environment.getTraverserContext().setAccumulate(task);
            taskByPath.put(resultKey, task);
            pathList.add(resultKey);
            return;
        }

        // 不是topQuery下节点、不再list路径下、不是node注解的节点(pathList为空)
        // 将当前节点注册为异步任务，并返回
        if (isRecursive && !isListAndNotScalar(parentEnv)) {
            String nestedKey = visitPath(environment);
            FutureTask task = FutureTask.newBuilder()
                    .isTopNode(false)
                    .path(nestedKey)
                    .isList(isListAndNotScalar(environment))
                    // .parent(parentEnv.getTraverserContext().getNewAccumulate()) 肯定是null
                    .future(new CompletableFuture())
                    .build();
            environment.getTraverserContext().setAccumulate(task);
            taskByPath.put(nestedKey, task);
            pathList.add(nestedKey);
            return;
        }
        handle(true, parentEnv, pathList, taskByPath);

        // handle不返回nestedKey因为
        FutureTask parentTask = parentEnv.getTraverserContext().getNewAccumulate();
        String nestedKey = visitPath(environment);
        FutureTask task = FutureTask.newBuilder()
                .isTopNode(false)
                .path(nestedKey)
                .isList(isListAndNotScalar(environment))
                .parent(parentTask)
                .future(new CompletableFuture())
                .build();
        parentTask.addSubTaskList(task);
        // kp: 将当前节点的任务作为其上下文累计值
        environment.getTraverserContext().setAccumulate(task);
        taskByPath.put(nestedKey, task);
        pathList.add(nestedKey);
    }

    // 当前字段是否是list 并且不是叶子节点
    private boolean isListAndNotScalar(QueryVisitorFieldEnvironment environment) {
        GraphQLOutputType fieldType = environment.getFieldDefinition().getType();

        return fieldType instanceof GraphQLList
                && !(unwrapAll(fieldType) instanceof GraphQLScalarType);
    }


    @Override
    public void visitInlineFragment(QueryVisitorInlineFragmentEnvironment queryVisitorInlineFragmentEnvironment) {

    }

    @Override
    public void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment queryVisitorFragmentSpreadEnvironment) {

    }
}