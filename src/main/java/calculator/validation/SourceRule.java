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
package calculator.validation;

import calculator.common.CommonUtil;
import calculator.engine.script.ScriptEvaluator;
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
import java.util.stream.Collectors;

import static calculator.common.CommonUtil.getDependenceSourceFromDirective;
import static calculator.common.VisitorUtil.pathForTraverse;
import static calculator.engine.metadata.Directives.ARGUMENT_TRANSFORM;
import static calculator.engine.metadata.Directives.INCLUDE_BY;
import static calculator.engine.metadata.Directives.MAP;
import static calculator.engine.metadata.Directives.SKIP_BY;
import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;


/**
 * Check whether the usage of @fetchSource on field is correct.
 *
 */
public class SourceRule extends AbstractRule {

    private final ScriptEvaluator scriptEvaluator;

    // <sourceName, annotatedFieldFullPath>
    private final Map<String, String> sourceWithAnnotatedField;

    // <fieldFullPath, topTaskFieldPath>
    private final Map<String, String> fieldWithTopTask;

    // <fieldFullPath, List<sourceName>>
    private final Map<String, List<String>> sourceUsedByField;

    // <fieldFullPath, List<ancestorFullPath>>
    private final Map<String, Set<String>> fieldWithAncestorPath;

    private final HashSet<String> unusedSource = new HashSet<>();


    public SourceRule(ScriptEvaluator scriptEvaluator,
                      Map<String, String> sourceWithAnnotatedField,
                      Map<String, String> fieldWithTopTask,
                      Map<String, List<String>> sourceUsedByField,
                      Map<String, Set<String>> fieldWithAncestorPath) {
        this.scriptEvaluator = Objects.requireNonNull(scriptEvaluator);
        this.sourceWithAnnotatedField = sourceWithAnnotatedField;
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

                List<String> dependencySources = getDependenceSourceFromDirective(directive);
                if (dependencySources == null || dependencySources.isEmpty()) {
                    continue;
                }

                if (!validateSourceExist(fieldFullPath, directive, dependencySources)) {
                    continue;
                }

                String predicate = (String) CommonUtil.parseValue(
                        directive.getArgument("predicate").getValue()
                );
                if (!validateSourceUsageOnExp(fieldFullPath, directive, dependencySources, predicate)) {
                    continue;
                }

                // 不和参数名称冲突
                if (!validateNodeNameNotSameWithArgument(fieldFullPath, argumentsOnField, directive, dependencySources)) {
                    continue;
                }

                // circular check
                if (circularReferenceCheck(directive.getSourceLocation(), fieldFullPath, dependencySources)) {
                    continue;
                }
                
            }else if (Objects.equals(directive.getName(), INCLUDE_BY.getName())) {

                List<String> dependencySources = getDependenceSourceFromDirective(directive);
                if (dependencySources == null || dependencySources.isEmpty()) {
                    continue;
                }

                if (!validateSourceExist(fieldFullPath, directive, dependencySources)) {
                    continue;
                }

                String predicate = (String) CommonUtil.parseValue(
                        directive.getArgument("predicate").getValue()
                );
                if (!validateSourceUsageOnExp(fieldFullPath, directive, dependencySources, predicate)) {
                    continue;
                }

                // 不和参数名称冲突
                if (!validateNodeNameNotSameWithArgument(fieldFullPath, argumentsOnField, directive, dependencySources)) {
                    continue;
                }

                // circular check
                if (circularReferenceCheck(directive.getSourceLocation(), fieldFullPath, dependencySources)) {
                    continue;
                }

            } else if (Objects.equals(directive.getName(), MAP.getName())) {

                List<String> dependencySources = getDependenceSourceFromDirective(directive);
                if (dependencySources == null || dependencySources.isEmpty()) {
                    continue;
                }

                if (!validateSourceExist(fieldFullPath, directive, dependencySources)) {
                    continue;
                }

                String mapper = (String) CommonUtil.parseValue(
                        directive.getArgument("mapper").getValue()
                );
                if (!validateSourceUsageOnExp(fieldFullPath, directive, dependencySources, mapper)) {
                    continue;
                }

                // circular check
                if (circularReferenceCheck(directive.getSourceLocation(), fieldFullPath, dependencySources)) {
                    continue;
                }

            } else if (Objects.equals(directive.getName(), ARGUMENT_TRANSFORM.getName())) {

                List<String> dependencySources = getDependenceSourceFromDirective(directive);
                if (dependencySources == null || dependencySources.isEmpty()) {
                    continue;
                }

                if (!validateSourceExist(fieldFullPath, directive, dependencySources)) {
                    continue;
                }

                String expression = (String) CommonUtil.parseValue(
                        directive.getArgument("expression").getValue()
                );
                if (!validateSourceUsageOnExp(fieldFullPath, directive, dependencySources, expression)) {
                    continue;
                }

                // 不和参数名称冲突
                if (!validateNodeNameNotSameWithArgument(fieldFullPath, argumentsOnField, directive, dependencySources)) {
                    continue;
                }

                // circular check
                if (circularReferenceCheck(directive.getSourceLocation(), fieldFullPath, dependencySources)) {
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
     * Determine whether the dependency sources exists, and delete it from unusedSource.
     *
     * @param fieldFullPath fieldFullPath
     * @param directive directive
     * @param dependencySources dependencySources
     *
     * @return true if all dependency sources exist, otherwise false.
     */
    private boolean validateSourceExist(String fieldFullPath, Directive directive, List<String> dependencySources) {
        // 依赖的source必须存在。
        if (!sourceWithAnnotatedField.keySet().containsAll(dependencySources)) {

            List<String> unExistSource = dependencySources.stream()
                    .filter(sourceName -> !sourceWithAnnotatedField.containsKey(sourceName))
                    .collect(Collectors.toList());

            // 错误信息中，名称使用单引号''，路径使用花括号{}
            String errorMsg = format(
                    "the fetchSource %s used by @%s on {%s} do not exist.", unExistSource, directive.getName(), fieldFullPath
            );
            addValidError(directive.getSourceLocation(), errorMsg);
            return false;
        }
        unusedSource.removeAll(dependencySources);
        return true;
    }


    /**
     * Determine whether all dependency sources are used by directive expression.
     *
     * @param fieldFullPath fieldFullPath
     * @param directive directive
     * @param dependencySources dependencySources
     * @param expression expression
     * @return true if all dependency sources is used by expression, otherwise false
     */
    private boolean validateSourceUsageOnExp(String fieldFullPath, Directive directive, List<String> dependencySources, String expression) {
        List<String> arguments = scriptEvaluator.getScriptArgument(expression);
        if (!arguments.containsAll(dependencySources)) {

            List<String> unUsedSource = dependencySources.stream()
                    .filter(sourceName -> !arguments.contains(sourceName))
                    .collect(Collectors.toList());

            String errorMsg = format(
                    "the fetchSource %s do not used by @%s on {%s}.", unUsedSource, directive.getName(), fieldFullPath
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
     * @param dependencySources 指令依赖的节点名称
     * @return 校验结果
     */
    private boolean validateNodeNameNotSameWithArgument(String fieldFullPath, Set<String> argumentsOnField, Directive directive, List<String> dependencySources) {
        List<String> sourcesWithSameArgumentName = dependencySources.stream().filter(argumentsOnField::contains).collect(Collectors.toList());
        if (!sourcesWithSameArgumentName.isEmpty()) {
            String errorMsg = format(
                    "the dependencySources %s on {%s} must be different to field argument name %s.",
                    sourcesWithSameArgumentName, fieldFullPath, argumentsOnField
            );
            addValidError(directive.getSourceLocation(), errorMsg);
            return false;
        }

        return true;
    }



    private boolean circularReferenceCheck(SourceLocation sourceLocation, String fieldFullPath, List<String> dependencySources) {
        for (String dependencySource : dependencySources) {
            ArrayList<String> pathList = new ArrayList<>();
            pathList.add(fieldFullPath);
            pathList.add(sourceWithAnnotatedField.get(dependencySource));
            if (doCircularReferenceCheck(sourceLocation, pathList)) {
                return true;
            }
        }
        return false;
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
            List<String> tmpTraversedPath = new ArrayList();
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
        List<String> traversedPath = new ArrayList();
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
