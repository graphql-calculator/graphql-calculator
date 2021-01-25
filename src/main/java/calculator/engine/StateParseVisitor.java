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
import calculator.engine.metadata.ScheduleState;
import graphql.analysis.QueryVisitor;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorFragmentSpreadEnvironment;
import graphql.analysis.QueryVisitorInlineFragmentEnvironment;
import graphql.language.Directive;
import graphql.schema.GraphQLList;
import graphql.util.TraverserContext;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static calculator.CommonTools.PATH_SEPARATOR;
import static calculator.CommonTools.getAliasOrName;
import static calculator.CommonTools.getArgumentFromDirective;


public class StateParseVisitor implements QueryVisitor {

    private ScheduleState state;

    private StateParseVisitor(ScheduleState state) {
        this.state = state;
    }

    public static StateParseVisitor newInstanceWithState(ScheduleState state) {
        return new StateParseVisitor(state);
    }

    @Override
    public void visitField(QueryVisitorFieldEnvironment environment) {
        if (environment.getTraverserContext().getPhase() != TraverserContext.Phase.ENTER) {
            return;
        }

        boolean isList = isList(environment);
        // TODO 指的仅仅是父亲吗？
        //      在tag 1 处重置该值应该可以，因为是深度递归遍历
        environment.getTraverserContext().setAccumulate(isList);

        /**
         * 该节点被node注解，保存该节点及其父亲节点
         */
        List<Directive> directives = environment.getField().getDirectives(CalculateDirectives.node.getName());
        if (directives != null && !directives.isEmpty()) {
            Directive nodeDir = directives.get(0);
            String nodeName = getArgumentFromDirective(nodeDir, "name");

            Map<String, FutureTask<Object>> taskByPath = state.getTaskByPath();

            List<String> pathList = new LinkedList<>();
            handle(environment, pathList, taskByPath);
            state.getSequenceTaskByNode().put(nodeName, pathList);
        }
    }

    // 当前字段是否是嵌套在list中
    private boolean isList(QueryVisitorFieldEnvironment environment) {
        QueryVisitorFieldEnvironment parentEnv = environment.getParentEnvironment();

        // query 的子字段
        if (parentEnv == null) {
            environment.getTraverserContext().setAccumulate(false);
            return false;
        }

        if (parentEnv.getFieldDefinition().getType() instanceof GraphQLList) {
            return true;
        }

        return environment.getTraverserContext().getCurrentAccumulate();
    }

    /**
     * fixme 直接嵌套便利o(N^2)算法，
     * 该优化为o(N)算法(因为一般层级很浅、所以此优化也没必要)
     */
    private String handle(QueryVisitorFieldEnvironment environment, List<String> pathList, Map<String, FutureTask<Object>> taskByPath) {
        if (environment.getParentEnvironment() == null) {
            String resultKey = getAliasOrName(environment.getField());
            pathList.add(resultKey);
            FutureTask task = FutureTask.newBuilder()
                    .isList(isList(environment))
                    .path(resultKey)
                    .future(new CompletableFuture())
                    .build();
            taskByPath.put(resultKey, task);
            return resultKey;
        }

        String nestedKey = handle(environment.getParentEnvironment(), pathList, taskByPath)
                + PATH_SEPARATOR + getAliasOrName(environment.getField());

        FutureTask task = FutureTask.newBuilder()
                .isList(isList(environment))
                .path(nestedKey)
                .future(new CompletableFuture())
                .build();
        taskByPath.put(nestedKey, task);

        pathList.add(nestedKey);
        return nestedKey;
    }

    @Override
    public void visitInlineFragment(QueryVisitorInlineFragmentEnvironment queryVisitorInlineFragmentEnvironment) {

    }

    @Override
    public void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment queryVisitorFragmentSpreadEnvironment) {

    }
}
