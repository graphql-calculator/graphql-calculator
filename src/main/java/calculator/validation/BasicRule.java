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

import calculator.engine.annotation.Internal;
import calculator.engine.metadata.Directives;
import calculator.engine.script.ScriptEvaluator;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorFragmentSpreadEnvironment;
import graphql.analysis.QueryVisitorInlineFragmentEnvironment;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.language.SourceLocation;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static calculator.common.CommonUtil.getArgumentFromDirective;
import static calculator.common.CommonUtil.getDependencySources;
import static calculator.common.CommonUtil.isValidEleName;
import static calculator.common.CommonUtil.parseValue;
import static calculator.common.GraphQLUtil.getTopTaskEnv;
import static calculator.common.GraphQLUtil.isLeafField;
import static calculator.common.GraphQLUtil.parentPathSet;
import static calculator.common.GraphQLUtil.pathForTraverse;
import static calculator.engine.metadata.Directives.ARGUMENT_TRANSFORM;
import static calculator.engine.metadata.Directives.FETCH_SOURCE;
import static calculator.engine.metadata.Directives.FILTER;
import static calculator.engine.metadata.Directives.INCLUDE_BY;
import static calculator.engine.metadata.Directives.MAP;
import static calculator.engine.metadata.Directives.MOCK;
import static calculator.engine.metadata.Directives.SKIP_BY;
import static calculator.engine.metadata.Directives.SORT;
import static calculator.engine.metadata.Directives.SORT_BY;
import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;


@Internal
public class BasicRule extends AbstractRule {

    private final ScriptEvaluator scriptEvaluator;

    // <sourceName, annotatedField>
    private final Map<String, String> sourceWithAnnotatedField = new LinkedHashMap<>();

    // <fieldFullPath, topTaskFieldPath>
    private final Map<String, String> fieldWithTopTask = new LinkedHashMap<>();

    // <fieldFullPath, List<sourceName>>
    private final Map<String, List<String>> sourceUsedByField = new LinkedHashMap<>();

    // <fieldFullPath, List<ancestorNode>>
    private final Map<String, Set<String>> fieldWithAncestorPath = new LinkedHashMap<>();

    public BasicRule(ScriptEvaluator scriptEvaluator) {
        this.scriptEvaluator = Objects.requireNonNull(scriptEvaluator);
    }


    public Map<String, String> getSourceWithAnnotatedField() {
        return sourceWithAnnotatedField;
    }

    public Map<String, String> getFieldWithTopTask() {
        return fieldWithTopTask;
    }

    public Map<String, List<String>> getSourceUsedByField() {
        return sourceUsedByField;
    }

    public Map<String, Set<String>> getFieldWithAncestorPath() {
        return fieldWithAncestorPath;
    }

    @Override
    public void visitField(QueryVisitorFieldEnvironment environment) {
        if (environment.getTraverserContext().getPhase() != TraverserContext.Phase.ENTER) {
            return;
        }

        String fieldFullPath = pathForTraverse(environment);
        SourceLocation location = environment.getField().getSourceLocation();

        Set<String> argumentsOnField = environment.getField().getArguments().stream().map(Argument::getName).collect(toSet());


        for (Directive directive : environment.getField().getDirectives()) {
            String directiveName = directive.getName();

            if (Objects.equals(directiveName, SKIP_BY.getName())) {
                String predicate = (String) parseValue(
                        directive.getArgument("predicate").getValue()
                );

                if (predicate == null || predicate.isEmpty()) {
                    String errorMsg = String.format("the expression for @skipBy on {%s} can not be empty.", fieldFullPath);
                    addValidError(location, errorMsg);
                    continue;
                }

                if (!scriptEvaluator.isValidScript(predicate)) {
                    String errorMsg = String.format("invalid expression '%s' for @skipBy on {%s}.", predicate, fieldFullPath);
                    addValidError(location, errorMsg);
                    continue;
                }

                checkAndSetFieldWithTopTask(fieldFullPath, directive, environment);
                checkAndSetSourceUsedByFieldInfo(fieldFullPath,directive);
                fieldWithAncestorPath.put(fieldFullPath,parentPathSet(environment));

            } else if (Objects.equals(directiveName, INCLUDE_BY.getName())) {
                String predicate = (String) parseValue(
                        directive.getArgument("predicate").getValue()
                );

                if (predicate == null || predicate.isEmpty()) {
                    String errorMsg = String.format("the expression for @includeBy on {%s} can not be empty.", fieldFullPath);
                    addValidError(location, errorMsg);
                    continue;
                }

                if (!scriptEvaluator.isValidScript(predicate)) {
                    String errorMsg = String.format("invalid expression '%s' for @includeBy on {%s}.", predicate, fieldFullPath);
                    addValidError(location, errorMsg);
                    continue;
                }

                checkAndSetFieldWithTopTask(fieldFullPath, directive, environment);
                checkAndSetSourceUsedByFieldInfo(fieldFullPath, directive);
                fieldWithAncestorPath.put(fieldFullPath, parentPathSet(environment));

            } else if (Objects.equals(directiveName, MOCK.getName())) {
                //注意，value可以为空串、模拟返回结果为空的情况；

            } else if (Objects.equals(directiveName, FILTER.getName())) {

                GraphQLType innerType = GraphQLTypeUtil.unwrapNonNull(
                        environment.getFieldDefinition().getType()
                );
                if (!GraphQLTypeUtil.isList(innerType)) {
                    String errorMsg = String.format("@filter must define on list type, instead {%s}.", fieldFullPath);
                    addValidError(location, errorMsg);
                    continue;
                }

                String predicate = (String) parseValue(
                        directive.getArgument("predicate").getValue()
                );
                if (predicate == null || predicate.isEmpty()) {
                    String errorMsg = String.format("the predicate for @filter on {%s} can not be empty.", fieldFullPath);
                    addValidError(location, errorMsg);
                    continue;
                }

                if (!scriptEvaluator.isValidScript(predicate)) {
                    String errorMsg = String.format("invalid predicate '%s' for @filter on {%s}.", predicate, fieldFullPath);
                    addValidError(location, errorMsg);
                    continue;
                }

                if (!validateExpressionArgumentExist(environment.getField(), directive, predicate, fieldFullPath,environment)) {
                    continue;
                }

                checkAndSetFieldWithTopTask(fieldFullPath, directive, environment);
                checkAndSetSourceUsedByFieldInfo(fieldFullPath, directive);
                fieldWithAncestorPath.put(fieldFullPath, parentPathSet(environment));

            } else if (Objects.equals(directiveName, SORT.getName())) {

                GraphQLType innerType = GraphQLTypeUtil.unwrapNonNull(
                        environment.getFieldDefinition().getType()
                );

                if (!GraphQLTypeUtil.isList(innerType)) {
                    // 使用'{}'，和 graphql 中的数组表示 '[]' 作区分
                    String errorMsg = String.format("@sort must annotated on list type, instead of {%s}.", fieldFullPath);
                    addValidError(location, errorMsg);
                    continue;
                }

                String key = (String) parseValue(
                        directive.getArgument("key").getValue()
                );
                if (key == null || key.isEmpty()) {
                    String errorMsg = String.format("sort key used on {%s} can not be null.", fieldFullPath);
                    addValidError(location, errorMsg);
                    continue;
                }

                boolean validKey = environment.getField().getSelectionSet().getSelections().stream()
                        .map(selection -> ((Field) selection).getResultKey())
                        .anyMatch(key::equals);

                if (!validKey) {
                    String errorMsg = String.format("non-exist key name '%s' for @sort on {%s}.", key, fieldFullPath);
                    addValidError(location, errorMsg);
                    continue;
                }

            } else if (Objects.equals(directiveName, SORT_BY.getName())) {
                String comparator = (String) parseValue(
                        directive.getArgument("comparator").getValue()
                );

                if (!scriptEvaluator.isValidScript(comparator)) {
                    String errorMsg = String.format("invalid comparator '%s' for @skipBy on {%s}.", comparator, fieldFullPath);
                    addValidError(location, errorMsg);
                    continue;
                }

                GraphQLType innerType = GraphQLTypeUtil.unwrapNonNull(
                        environment.getFieldDefinition().getType()
                );

                if (!GraphQLTypeUtil.isList(innerType)) {
                    // 使用'{}'，和 graphql 中的数组表示 '[]' 作区分
                    String errorMsg = String.format("@sortBy must annotated on list type, instead of {%s}.", fieldFullPath);
                    addValidError(location, errorMsg);
                    continue;
                }

                if (!validateExpressionArgumentExist(environment.getField(), directive, comparator, fieldFullPath,environment)) {
                    continue;
                }

                checkAndSetFieldWithTopTask(fieldFullPath, directive, environment);
                checkAndSetSourceUsedByFieldInfo(fieldFullPath,directive);
                fieldWithAncestorPath.put(fieldFullPath,parentPathSet(environment));

            } else if (Objects.equals(directiveName, MAP.getName())) {

                String mapper = getArgumentFromDirective(directive, "mapper");
                if (!scriptEvaluator.isValidScript(mapper)) {
                    String errorMsg = String.format("invalid mapper '%s' for @map on {%s}.", mapper, fieldFullPath);
                    addValidError(location, errorMsg);
                    continue;
                }

                checkAndSetFieldWithTopTask(fieldFullPath, directive, environment);
                checkAndSetSourceUsedByFieldInfo(fieldFullPath,directive);
                fieldWithAncestorPath.put(fieldFullPath,parentPathSet(environment));

            } else if (Objects.equals(directiveName, ARGUMENT_TRANSFORM.getName())) {

                String argumentName = getArgumentFromDirective(directive, "argumentName");
                // argument必须存在
                if (!argumentsOnField.contains(argumentName)) {
                    String errorMsg = format(
                            "@argumentTransform on {%s} use non-exist argument '%s'.", fieldFullPath, argumentName
                    );
                    addValidError(directive.getSourceLocation(), errorMsg);
                    continue;
                }

                String expression = getArgumentFromDirective(directive, "expression");
                if (!scriptEvaluator.isValidScript(expression)) {
                    String errorMsg = String.format("invalid expression '%s' for @argumentTransform on {%s}.", expression, fieldFullPath);
                    addValidError(location, errorMsg);
                    continue;
                }

                // filter 或者 list_map 用在了非list上
                GraphQLType innerType = GraphQLTypeUtil.unwrapNonNull(
                        environment.getFieldDefinition().getArgument(argumentName).getType()
                );
                String operateType = getArgumentFromDirective(directive, "operateType");
                if ((Objects.equals(operateType, Directives.ParamTransformType.LIST_MAP.name())
                        || Objects.equals(operateType, Directives.ParamTransformType.FILTER.name()))
                        && !(innerType instanceof GraphQLList)
                ) {
                    String errorMsg = String.format("%s operation for @argumentTransform can not used on basic field {%s}.", operateType, fieldFullPath);
                    addValidError(location, errorMsg);
                    continue;
                }

                checkAndSetFieldWithTopTask(fieldFullPath, directive, environment);
                checkAndSetSourceUsedByFieldInfo(fieldFullPath,directive);
                fieldWithAncestorPath.put(fieldFullPath,parentPathSet(environment));

            } else if (Objects.equals(directiveName, FETCH_SOURCE.getName())) {
                String sourceName = (String) parseValue(
                        directive.getArgument("name").getValue()
                );

                // 验证节点名称是否已经被其他字段使用
                if (sourceWithAnnotatedField.containsKey(sourceName)) {
                    String errorMsg = String.format("duplicate source name '%s' for {%s} and {%s}.",
                            sourceName, sourceWithAnnotatedField.get(sourceName), fieldFullPath
                    );
                    addValidError(location, errorMsg);
                } else {
                    sourceWithAnnotatedField.put(sourceName, fieldFullPath);
                    if (!isValidEleName(sourceName)) {
                        String errorMsg = String.format("invalid source name '%s' for {%s}.", sourceName, fieldFullPath);
                        addValidError(location, errorMsg);
                    }
                }

                String sourceConvert = getArgumentFromDirective(directive, "sourceConvert");
                if (sourceConvert != null && !scriptEvaluator.isValidScript(sourceConvert)) {
                    String errorMsg = String.format("invalid sourceConvert '%s' for @fetchSource on {%s}.", sourceConvert, fieldFullPath);
                    addValidError(location, errorMsg);
                }


                // 获取其父类节点路径
                Set<String> parentPathSet = parentPathSet(environment);
                checkAndSetFieldWithTopTask(fieldFullPath, directive, environment);
                checkAndSetSourceUsedByFieldInfo(fieldFullPath,directive);
                fieldWithAncestorPath.put(fieldFullPath,parentPathSet);
            }
        }
    }

    private void checkAndSetFieldWithTopTask(String fieldFullPath, Directive directive, QueryVisitorFieldEnvironment visitorFieldEnvironment) {
        Argument sourceArgument = directive.getArgument("dependencySources");

        if (sourceArgument != null && sourceArgument.getValue() != null) {
            QueryVisitorFieldEnvironment topTaskEnv = getTopTaskEnv(visitorFieldEnvironment);
            String topTaskFieldPath = pathForTraverse(topTaskEnv);
            fieldWithTopTask.put(fieldFullPath, topTaskFieldPath);
        }
    }

    //    // <fieldFullPath, List<sourceName>>
    //    private final Map<String, List<String>> sourceUsedByField = new LinkedHashMap<>();
    private void checkAndSetSourceUsedByFieldInfo(String fieldFullPath,Directive directive) {
        Argument sourceArgument = directive.getArgument("dependencySources");

        if (sourceArgument != null && sourceArgument.getValue() != null) {
            List<String> dependencySources = getDependencySources(sourceArgument.getValue());

            if (sourceUsedByField.containsKey(fieldFullPath)) {
                sourceUsedByField.get(fieldFullPath).addAll(dependencySources);
            } else {
                ArrayList<String> sourceNames = new ArrayList<>();
                sourceNames.addAll(dependencySources);
                sourceUsedByField.put(fieldFullPath, sourceNames);
            }
        }
    }

    private boolean validateExpressionArgumentExist(Field field, Directive directive, String expression, String fieldFullPath, QueryVisitorFieldEnvironment environment) {

        if (isLeafField(environment.getFieldDefinition())) {
            List<String> scriptArgument = scriptEvaluator.getScriptArgument(expression);
            if (scriptArgument == null || scriptArgument.size() != 1 || !Objects.equals(scriptArgument.get(0), "ele")) {
                String errorMsg = String.format("only 'ele' can be used for @%s on leaf field {%s}.", directive.getName(), fieldFullPath);
                addValidError(field.getSourceLocation(), errorMsg);
                return false;
            }
        } else {
            List<String> scriptArgument = scriptEvaluator.getScriptArgument(expression);
            if (scriptArgument != null && !scriptArgument.isEmpty()) {
                for (String key : scriptArgument) {
                    boolean validKey = field.getSelectionSet().getSelections().stream()
                            .map(selection -> ((Field) selection).getResultKey())
                            .anyMatch(key::equals);

                    if (!validKey) {
                        String errorMsg = String.format("non-exist argument '%s' for @%s on {%s}.", key, directive.getName(), fieldFullPath);
                        addValidError(field.getSourceLocation(), errorMsg);
                        return false;
                    }
                }
            }
        }

        return true;
    }


    @Override
    public void visitInlineFragment(QueryVisitorInlineFragmentEnvironment environment) {
    }

    @Override
    public void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment environment) {
    }
}
