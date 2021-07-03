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

import calculator.common.CommonUtil;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorFragmentSpreadEnvironment;
import graphql.analysis.QueryVisitorInlineFragmentEnvironment;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.SourceLocation;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static calculator.common.CommonUtil.getArgumentFromDirective;
import static calculator.common.VisitorUtil.pathForTraverse;
import static calculator.engine.function.ExpProcessor.getExpArgument;
import static calculator.engine.metadata.Directives.ARGUMENT_TRANSFORM;
import static calculator.engine.metadata.Directives.FILTER;
import static calculator.engine.metadata.Directives.MAP;
import static calculator.engine.metadata.Directives.SKIP_BY;
import static calculator.engine.metadata.Directives.SORT_BY;
import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;


/**
 * Check whether the usage of @fetchSource on field is correct.
 *
 */
public class SourceRule extends AbstractRule {

    // <sourceName, annotatedFieldFullPath>
    private final Map<String, String> sourceWithAnnotatedField;

    // <sourceName, topTaskFieldPath>
    private final Map<String, String> sourceWithTopTask;

    // <sourceName, List<ancestorNode>>
    private final Map<String, Set<String>> sourceWithAncestorPath;

    // <fieldFullPath, topTaskFieldPath>
    private final Map<String, String> fieldWithTopTask;

    // <fieldFullPath, List<sourceName>>
    private final Map<String, List<String>> sourceUsedByField;

    // <fieldFullPath, List<ancestorFullPath>>
    private final Map<String, Set<String>> fieldWithAncestorPath;

    private final HashSet<String> unusedSource = new HashSet<>();


    public SourceRule(Map<String, String> sourceWithAnnotatedField,
                      Map<String, String> sourceWithTopTask,
                      Map<String, Set<String>> sourceWithAncestorPath,
                      Map<String, String> fieldWithTopTask,
                      Map<String, List<String>> sourceUsedByField,
                      Map<String, Set<String>> fieldWithAncestorPath) {
        this.sourceWithAnnotatedField = sourceWithAnnotatedField;
        this.sourceWithTopTask = sourceWithTopTask;
        this.sourceWithAncestorPath = sourceWithAncestorPath;
        this.fieldWithTopTask = fieldWithTopTask;
        this.sourceUsedByField = sourceUsedByField;
        this.fieldWithAncestorPath = fieldWithAncestorPath;

        unusedSource.addAll(sourceWithAnnotatedField.keySet());
    }

    public HashSet<String> getUnusedSource() {
        return unusedSource;
    }

    @Override
    public void visitField(QueryVisitorFieldEnvironment environment) {
        if (environment.getTraverserContext().getPhase() != TraverserContext.Phase.ENTER) {
            return;
        }

        List<Directive> directives = environment.getField().getDirectives();
        if (directives == null || directives.isEmpty()) {
            return;
        }

        String fieldFullPath = pathForTraverse(environment);
        Set<String> argumentsOnField = environment.getField().getArguments().stream().map(Argument::getName).collect(toSet());

        for (Directive directive : directives) {

            if (Objects.equals(directive.getName(), SKIP_BY.getName())) {

                String dependencySourceName = getArgumentFromDirective(directive, "dependencySource");
                if (dependencySourceName == null) {
                    continue;
                }

                // source 必须存在，存在这从unusedNode中删除
                if (!validateExist(fieldFullPath, directive, dependencySourceName)) {
                    continue;
                }

                // source 必须被使用了
                String expression = (String) CommonUtil.parseValue(
                        directive.getArgument("expression").getValue()
                );
                if (!validateNodeUsageOnExp(fieldFullPath, directive, dependencySourceName, expression)) {
                    continue;
                }

                // 不和参数名称冲突
                if (!validateNodeNameNotSameWithArgument(fieldFullPath, argumentsOnField, directive, dependencySourceName)) {
                    continue;
                }

                // circular check
                if (circularReferenceCheck(directive.getSourceLocation(), fieldFullPath, dependencySourceName)) {
                    continue;
                }
                
            } else if (Objects.equals(directive.getName(), MAP.getName())) {

                String dependencySourceName = getArgumentFromDirective(directive, "dependencySource");
                if (dependencySourceName == null) {
                    continue;
                }

                // source 必须存在，存在这从unusedNode中删除
                if (!validateExist(fieldFullPath, directive, dependencySourceName)) {
                    continue;
                }

                // source 必须被使用了
                String expression = (String) CommonUtil.parseValue(
                        directive.getArgument("expression").getValue()
                );
                if (!validateNodeUsageOnExp(fieldFullPath, directive, dependencySourceName, expression)) {
                    continue;
                }

                // circular check
                if (circularReferenceCheck(directive.getSourceLocation(), fieldFullPath, dependencySourceName)) {
                    continue;
                }

            } else if (Objects.equals(directive.getName(), SORT_BY.getName())) {
                String dependencySourceName = getArgumentFromDirective(directive, "dependencySource");
                // emptyMap.containsKey(null)结果为true
                if (dependencySourceName == null) {
                    continue;
                }

                // 依赖的节点是否存在，存在这从unusedNode中删除
                if (!validateExist(fieldFullPath, directive, dependencySourceName)) {
                    continue;
                }
                
                // 依赖的source必须被使用了
                String expression = (String) CommonUtil.parseValue(
                        directive.getArgument("expression").getValue()
                );
                if (!validateNodeUsageOnExp(fieldFullPath, directive, dependencySourceName, expression)) {
                    continue;
                }

                // circular check
                if (circularReferenceCheck(directive.getSourceLocation(), fieldFullPath, dependencySourceName)) {
                    continue;
                }

            } else if (Objects.equals(directive.getName(), FILTER.getName())) {

                String dependencySourceName = getArgumentFromDirective(directive, "dependencySource");
                // emptyMap.containsKey(null)结果为true
                if (dependencySourceName == null) {
                    continue;
                }

                // 依赖的节点是否存在，存在这从unusedNode中删除
                if (!validateExist(fieldFullPath, directive, dependencySourceName)) {
                    continue;
                }

                // 依赖的source必须被使用了
                String expression = (String) CommonUtil.parseValue(
                        directive.getArgument("predicate").getValue()
                );
                if (!validateNodeUsageOnExp(fieldFullPath, directive, dependencySourceName, expression)) {
                    continue;
                }

                // circular check
                if (circularReferenceCheck(directive.getSourceLocation(), fieldFullPath, dependencySourceName)) {
                    continue;
                }


            } else if (Objects.equals(directive.getName(), ARGUMENT_TRANSFORM.getName())) {

                String dependencySourceName = getArgumentFromDirective(directive, "dependencySource");
                if (dependencySourceName == null) {
                    continue;
                }

                // 依赖的节点是否存在，存在这从unusedNode中删除
                if (!validateExist(fieldFullPath, directive, dependencySourceName)) {
                    continue;
                }

                // source 必须被使用了
                String expression = (String) CommonUtil.parseValue(
                        directive.getArgument("expression").getValue()
                );
                if (!validateNodeUsageOnExp(fieldFullPath, directive, dependencySourceName, expression)) {
                    continue;
                }

                // 不和参数名称冲突
                if (!validateNodeNameNotSameWithArgument(fieldFullPath, argumentsOnField, directive, dependencySourceName)) {
                    continue;
                }

                // circular check
                if (circularReferenceCheck(directive.getSourceLocation(), fieldFullPath, dependencySourceName)) {
                    continue;
                }

            }

        }

    }

    @Override
    public void visitInlineFragment(QueryVisitorInlineFragmentEnvironment environment) {

    }

    @Override
    public void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment environment) {

    }

    /**
     * 校验依赖的节点是否存在，存在这从unusedNode中删除。
     *
     * @param fieldFullPath 字段全路径
     * @param directive 字段上的指令
     * @param dependencySourceName 指令依赖的节点名称
     *
     * @return 是否校验成功
     */
    private boolean validateExist(String fieldFullPath, Directive directive, String dependencySourceName) {
        // 依赖的source必须存在。
        if (!sourceWithAnnotatedField.containsKey(dependencySourceName)) {
            // 错误信息中，名称使用单引号''，路径使用花括号{}
            String errorMsg = format(
                    "the fetchSource '%s' used by @%s on {%s} do not exist.", dependencySourceName, directive.getName(), fieldFullPath
            );
            addValidError(directive.getSourceLocation(), errorMsg);
            return false;
        }
        unusedSource.remove(dependencySourceName);
        return true;
    }


    /**
     * 校验指令依赖的节点是否在表达式中被使用了
     *
     * @param fieldFullPath 字段全路径
     * @param directive 字段上的指令
     * @param dependencySourceName 指令依赖的节点名称
     * @param expression 指令表达式
     * @return 校验结果
     */
    private boolean validateNodeUsageOnExp(String fieldFullPath, Directive directive, String dependencySourceName, String expression) {
        List<String> arguments = getExpArgument(expression);
        if (!arguments.contains(dependencySourceName)) {
            String errorMsg = format(
                    "the fetchSource '%s' do not used by @%s on {%s}.", dependencySourceName, directive.getName(), fieldFullPath
            );
            addValidError(directive.getSourceLocation(), errorMsg);
            return false;
        }

        return true;
    }

    /**
     * 校验节点名称是否跟参数名称一样，因为参数和节点都会作表达式计算的key
     *
     * @param fieldFullPath 字段全路径
     * @param argumentsOnField 请求字段上的参数
     * @param directive 字段上的指令
     * @param dependencySourceName 指令依赖的节点名称
     * @return 校验结果
     */
    private boolean validateNodeNameNotSameWithArgument(String fieldFullPath, Set<String> argumentsOnField, Directive directive, String dependencySourceName) {
        if (argumentsOnField.contains(dependencySourceName)) {
            String errorMsg = format(
                    "the dependencySource name '%s' on {%s} must be different to field argument name %s.",
                    dependencySourceName, fieldFullPath, argumentsOnField
            );
            addValidError(directive.getSourceLocation(), errorMsg);
            return false;
        }

        return true;
    }



    private boolean circularReferenceCheck(SourceLocation sourceLocation, String fieldFullPath, String dependencySourceName) {
        ArrayList<String> pathList = new ArrayList<>();
        pathList.add(fieldFullPath);
        pathList.add(sourceWithAnnotatedField.get(dependencySourceName));
        return doCircularReferenceCheck(sourceLocation, pathList);
    }

    /**
     * Determine whether fields in dependency path list share same topTask field, or ancestor depend on descendant field.
     *
     * @param sourceLocation the field which dependency path list start
     * @param pathList dependency pathList
     * @return true if there is an error
     */
    private boolean doCircularReferenceCheck(SourceLocation sourceLocation, ArrayList<String> pathList) {

        for (int i = 0; i < pathList.size() - 1; i++) {
            String fromPath = pathList.get(i);
            ArrayList<String> tmpTraversedPath = new ArrayList();
            tmpTraversedPath.add(fromPath);

            for (int j = i + 1; j < pathList.size(); j++) {
                String toPath = pathList.get(j);
                tmpTraversedPath.add(toPath);
                if (Objects.equals(fromPath, toPath)) {
                    String errorMsg = format(
                            "there is a circular dependency path %s.", tmpTraversedPath
                    );
                    addValidError(sourceLocation, errorMsg);
                    return true;
                }
                if (fieldWithAncestorPath.get(toPath).contains(fromPath)) {
                    String errorMsg = format(
                            "there is an ancestor relationship between {%s} and {%s}, " +
                                    "and they are in the dependency path %s.",
                            toPath, fromPath, tmpTraversedPath
                    );
                    addValidError(sourceLocation, errorMsg);
                    return true;
                }
            }
        }


        LinkedHashMap<String, String> fieldFullByTopTaskPath = new LinkedHashMap<>();
        ArrayList traversedPath = new ArrayList();
        for (String fieldFullPath : pathList) {
            String topTaskPath = fieldWithTopTask.get(fieldFullPath);
            if (fieldFullByTopTaskPath.containsValue(topTaskPath)) {
                String errorMsg = format(
                        "the %s and %s share same ancestor '%s', and they are in the dependency path %s.",
                        fieldFullByTopTaskPath.get(topTaskPath), fieldFullPath, topTaskPath, traversedPath
                );

                addValidError(sourceLocation, errorMsg);
                return true;
            }

            traversedPath.add(fieldFullPath);
            fieldFullByTopTaskPath.put(topTaskPath, fieldFullPath);
        }

        String sourceField = pathList.get(pathList.size() - 1);
        for (String sourceName : sourceUsedByField.getOrDefault(sourceField, Collections.emptyList())) {
            String dependencyField = sourceWithAnnotatedField.get(sourceName);
            pathList.add(dependencyField);
            if (doCircularReferenceCheck(sourceLocation, pathList)) {
                return true;
            }
            pathList.remove(dependencyField);
        }

        return false;
    }


}
