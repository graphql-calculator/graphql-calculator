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
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.util.TraverserContext;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static calculator.CommonTools.getAliasOrName;
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

//        boolean isList = isList(environment);
        // TODO 指的仅仅是父亲吗？
        //      在tag 1 处重置该值应该可以，因为是深度递归遍历
//        environment.getTraverserContext().setAccumulate(isList);

        /**
         * 该节点被node注解，保存该节点及其父亲节点
         */
        List<Directive> directives = environment.getField().getDirectives(CalculateDirectives.node.getName());
        if (directives != null && !directives.isEmpty()) {
            Directive nodeDir = directives.get(0);
            String nodeName = getArgumentFromDirective(nodeDir, "name");

            Map<String, FutureTask<Object>> taskByPath = state.getTaskByPath();

            LinkedList<String> pathList = new LinkedList<>();
            handle(environment, pathList, taskByPath);
            state.getSequenceTaskByNode().put(nodeName, pathList);
        }
    }

    // 当前字段是否是list 并且不是叶子节点
    private boolean isList(QueryVisitorFieldEnvironment environment) {
        GraphQLOutputType fieldType = environment.getFieldDefinition().getType();

        return fieldType instanceof GraphQLList
                && !(unwrapAll(fieldType) instanceof GraphQLScalarType);
    }

    /**
     * 获取 environment 对应节点
     * @param environment
     * @param pathList 保存父节点、祖父节点... 的绝对路径。
     * @param taskByPath
     * @return
     */
    private void handle(QueryVisitorFieldEnvironment environment, LinkedList<String> pathList, Map<String, FutureTask<Object>> taskByPath) {
        Object currentAccumulate = environment.getTraverserContext().getCurrentAccumulate();
        if(currentAccumulate!=null && currentAccumulate instanceof FutureTask){
            return;
        }
        // Query root
        if (environment.getParentEnvironment() == null) {
            String resultKey = getAliasOrName(environment.getField());
            pathList.add(resultKey);
            // 现在可能会重复创建
            FutureTask task = FutureTask.newBuilder()
                    .isList(isList(environment))
                    .path(resultKey)
                    .future(new CompletableFuture())
                    .build();
            // kp: 将当前节点的任务作为其上下文累计值
            environment.getTraverserContext().setAccumulate(task);
            taskByPath.put(resultKey, task);
            return;
        }
        handle(environment.getParentEnvironment(), pathList, taskByPath);

        // handle不返回nestedKey因为
        String nestedKey = visitPath(environment);
        FutureTask task = FutureTask.newBuilder()
                .isList(isList(environment))
                .path(nestedKey)
                .parent(environment.getParentEnvironment().getTraverserContext().getCurrentAccumulate())
                .future(new CompletableFuture())
                .build();
        // kp: 将当前节点的任务作为其上下文累计值
        environment.getTraverserContext().setAccumulate(task);
        taskByPath.put(nestedKey, task);
        pathList.add(nestedKey);
    }


    @Override
    public void visitInlineFragment(QueryVisitorInlineFragmentEnvironment queryVisitorInlineFragmentEnvironment) {

    }

    @Override
    public void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment queryVisitorFragmentSpreadEnvironment) {

    }
}