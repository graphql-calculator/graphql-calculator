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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static calculator.common.Tools.isValidEleName;
import static calculator.common.Tools.pathForTraverse;
import static calculator.engine.metadata.CalculateDirectives.FILTER;
import static calculator.engine.metadata.CalculateDirectives.MOCK;
import static calculator.engine.metadata.CalculateDirectives.NODE;
import static calculator.engine.metadata.CalculateDirectives.SKIP_BY;
import static calculator.engine.metadata.CalculateDirectives.SORT_BY;
import static calculator.engine.function.ExpEvaluator.isValidExp;


/**
 * 基本校验：
 * skipBy 表达式不为空且合法；
 * filter 表达式不为空且合法；
 * filter 必须放在list节点；
 * sortBy 必须定义在list上；
 * sortBy 的key必须存在于子元素中；
 * node 名称必须有效；
 * node 名称不能重复；
 * todo 别名的判断和出错信息打印；
 * 片段的判断和出错信息打印；
 * sortBy支持自定义函数；
 */
public class BaseRules extends AbstractTraverRule {

    // 1. @node是否重名;
    private Map<String, String> nodeNameMap;

    public BaseRules() {
        super();
        this.nodeNameMap = new HashMap<>();
    }


    public Map<String, String> getNodeNameMap() {
        return nodeNameMap;
    }

    public static BaseRules newInstance() {
        return new BaseRules();
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

            } else if (Objects.equals(directiveName, SORT_BY.getName())) {
                boolean isListType = GraphQLTypeUtil.isList(environment.getFieldDefinition().getType());
                if (!isListType) {
                    String errorMsg = String.format("key must define on list type, instead @%s.", fieldPath);
                    addValidError(location, errorMsg);
                    continue;
                }

                String key = (String) Tools.parseValue(
                        directive.getArgument("key").getValue()
                );
                if (key == null || key.isEmpty()) {
                    String errorMsg = String.format("key can't be null, @%s.", fieldPath);
                    addValidError(location, errorMsg);
                    continue;
                }

                boolean validKey = environment.getField().getSelectionSet().getSelections().stream()
                        .map(selection -> ((Field) selection).getResultKey())
                        .anyMatch(key::equals);

                // todo 报错信息；兼容片段
                if (!validKey) {
                    String errorMsg = String.format("invalid key name, @%s.", fieldPath);
                    addValidError(location, errorMsg);
                    continue;
                }
            } else if (Objects.equals(directiveName, NODE.getName())) {
                String nodeName = (String) Tools.parseValue(
                        directive.getArgument("name").getValue()
                );

                if (nodeNameMap.containsKey(nodeName)) {
                    String errorMsg = String.format("duplicate node name '%s' for %s and %s.",
                            nodeName, nodeNameMap.get(nodeName), fieldPath
                    );
                    addValidError(location, errorMsg);
                } else {
                    nodeNameMap.put(nodeName, fieldPath);
                    if (!isValidEleName(nodeName)) {
                        String errorMsg = String.format("invalid node name 'nodeName' for %s.", fieldPath);
                        addValidError(location, errorMsg);
                    }
                }
            }
        }
    }


    @Override
    public void visitInlineFragment(QueryVisitorInlineFragmentEnvironment environment) {
        if (environment.getTraverserContext().getPhase() != TraverserContext.Phase.ENTER) {
            return;
        }

        // todo

    }

    @Override
    public void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment environment) {
        if (environment.getTraverserContext().getPhase() != TraverserContext.Phase.ENTER) {
            return;
        }

        // todo
    }
}
