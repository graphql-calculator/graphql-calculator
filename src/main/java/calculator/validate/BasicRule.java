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
package calculator.validate;

import calculator.common.Tools;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorFragmentSpreadEnvironment;
import graphql.analysis.QueryVisitorInlineFragmentEnvironment;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.language.SourceLocation;
import graphql.schema.GraphQLTypeUtil;
import graphql.util.TraverserContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static calculator.common.Tools.isValidEleName;
import static calculator.common.VisitorUtils.getTopTaskEnv;
import static calculator.common.VisitorUtils.parentPathSet;
import static calculator.common.VisitorUtils.pathForTraverse;
import static calculator.engine.metadata.Directives.FILTER;
import static calculator.engine.metadata.Directives.MOCK;
import static calculator.engine.metadata.Directives.NODE;
import static calculator.engine.metadata.Directives.SKIP_BY;
import static calculator.engine.metadata.Directives.SORT;
import static calculator.engine.function.ExpEvaluator.isValidExp;


public class BasicRule extends AbstractRule {

    // <node名称, 注解的字段>
    private final Map<String, String> nodeWithAnnotatedField = new LinkedHashMap<>();

    // <node名称, 依赖的顶层节点>
    private final Map<String, String> nodeWithTopTask = new LinkedHashMap<>();

    // <node名称, List<祖先节点>>
    private final Map<String, Set<String>> nodeWithAncestorPath = new LinkedHashMap<>();


    public Map<String, String> getNodeWithAnnotatedField() {
        return nodeWithAnnotatedField;
    }

    public Map<String, String> getNodeWithTopTask() {
        return nodeWithTopTask;
    }

    public Map<String, Set<String>> getNodeWithAncestorPath() {
        return nodeWithAncestorPath;
    }

    @Override
    public void visitField(QueryVisitorFieldEnvironment environment) {
        // 不是进入该节点则返回
        if (environment.getTraverserContext().getPhase() != TraverserContext.Phase.ENTER) {
            return;
        }

        String fieldPath = pathForTraverse(environment);
        SourceLocation location = environment.getField().getSourceLocation();

        for (Directive directive : environment.getField().getDirectives()) {
            String directiveName = directive.getName();
            // 如果是node、则查看是否已经在中保存过

            if (Objects.equals(directiveName, SKIP_BY.getName())) {
                String exp = (String) Tools.parseValue(
                        directive.getArgument("exp").getValue()
                );

                if (exp == null || exp.isEmpty()) {
                    String errorMsg = String.format("exp can't be empty, @%s.", fieldPath);
                    addValidError(location, errorMsg);
                }

                if (!isValidExp(exp)) {
                    String errorMsg = String.format("invalid exp for %s on %s.", exp, fieldPath);
                    addValidError(location, errorMsg);
                }

            } else if (Objects.equals(directiveName, MOCK.getName())) {
                //注意，value可以为空串、模拟返回结果为空的情况；

            } else if (Objects.equals(directiveName, FILTER.getName())) {
                boolean isListType = GraphQLTypeUtil.isList(environment.getFieldDefinition().getType());
                if (!isListType) {
                    String errorMsg = String.format("predicate must define on list type, instead @%s.", fieldPath);
                    addValidError(location, errorMsg);
                    continue;
                }

                String predicate = (String) Tools.parseValue(
                        directive.getArgument("predicate").getValue()
                );
                if (predicate == null || predicate.isEmpty()) {
                    String errorMsg = String.format("script can't be empty, @%s.", fieldPath);
                    addValidError(location, errorMsg);
                    continue;
                }

                if (!isValidExp(predicate)) {
                    String errorMsg = String.format("invalidate script for %s on %s.", predicate, fieldPath);
                    addValidError(location, errorMsg);
                }

            } else if (Objects.equals(directiveName, SORT.getName())) {
                boolean isListType = GraphQLTypeUtil.isList(environment.getFieldDefinition().getType());
                if (!isListType) {
                    // 使用'{}'，和 graphql 中的数组表示 '[]' 作区分
                    String errorMsg = String.format("sort key must define on list type, instead of {%s}.", fieldPath);
                    addValidError(location, errorMsg);
                    continue;
                }

                String key = (String) Tools.parseValue(
                        directive.getArgument("key").getValue()
                );
                if (key == null || key.isEmpty()) {
                    String errorMsg = String.format("sort key used on {%s} can not be null.", fieldPath);
                    addValidError(location, errorMsg);
                    continue;
                }

                boolean validKey = environment.getField().getSelectionSet().getSelections().stream()
                        .map(selection -> ((Field) selection).getResultKey())
                        .anyMatch(key::equals);

                if (!validKey) {
                    String errorMsg = String.format("non-exist key name on {%s}.", fieldPath);
                    addValidError(location, errorMsg);
                    continue;
                }

            } else if (Objects.equals(directiveName, NODE.getName())) {
                String nodeName = (String) Tools.parseValue(
                        directive.getArgument("name").getValue()
                );

                // 验证节点名称是否已经被其他字段使用
                if (nodeWithAnnotatedField.containsKey(nodeName)) {
                    String errorMsg = String.format("duplicate node name '%s' for %s and %s.",
                            nodeName, nodeWithAnnotatedField.get(nodeName), fieldPath
                    );
                    addValidError(location, errorMsg);
                } else {
                    nodeWithAnnotatedField.put(nodeName, fieldPath);
                    if (!isValidEleName(nodeName)) {
                        String errorMsg = String.format("invalid node name 'nodeName' for %s.", fieldPath);
                        addValidError(location, errorMsg);
                    }
                }

                // 获取其顶层任务节点路径
                QueryVisitorFieldEnvironment topTaskEnv = getTopTaskEnv(environment);
                String topTaskFieldPath = pathForTraverse(topTaskEnv);
                nodeWithTopTask.put(nodeName, topTaskFieldPath);

                // 获取其父类节点路径
                Set<String> parentPathSet = parentPathSet(environment);
                nodeWithAncestorPath.put(nodeName, parentPathSet);
            }
        }
    }


    @Override
    public void visitInlineFragment(QueryVisitorInlineFragmentEnvironment environment) {
    }

    @Override
    public void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment environment) {
    }
}
