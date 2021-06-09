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

import calculator.engine.annotation.Internal;
import calculator.engine.metadata.Directives;
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
import static calculator.common.CommonUtil.isValidEleName;
import static calculator.common.CommonUtil.parseValue;
import static calculator.common.VisitorUtil.getTopTaskEnv;
import static calculator.common.VisitorUtil.parentPathSet;
import static calculator.common.VisitorUtil.pathForTraverse;
import static calculator.engine.metadata.Directives.ARGUMENT_TRANSFORM;
import static calculator.engine.metadata.Directives.FETCH_SOURCE;
import static calculator.engine.metadata.Directives.FILTER;
import static calculator.engine.metadata.Directives.MAP;
import static calculator.engine.metadata.Directives.MOCK;
import static calculator.engine.metadata.Directives.SKIP_BY;
import static calculator.engine.metadata.Directives.SORT;
import static calculator.engine.function.ExpProcessor.isValidExp;
import static calculator.engine.metadata.Directives.SORT_BY;
import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;


@Internal
public class BasicRule extends AbstractRule {

    // <sourceName, annotatedField>
    private final Map<String, String> sourceWithAnnotatedField = new LinkedHashMap<>();

    // <sourceName, topTaskFieldPath>
    private final Map<String, String> sourceWithTopTask = new LinkedHashMap<>();

    // <sourceName, List<ancestorNode>>
    private final Map<String, Set<String>> sourceWithAncestorPath = new LinkedHashMap<>();

    // <fieldFullPath, topTaskFieldPath>
    private final Map<String, String> fieldWithTopTask = new LinkedHashMap<>();

    // <fieldFullPath, List<sourceName>>
    private final Map<String, List<String>> sourceUsedByField = new LinkedHashMap<>();

    // <fieldFullPath, List<ancestorNode>>
    private final Map<String, Set<String>> fieldWithAncestorPath = new LinkedHashMap<>();


    public Map<String, String> getSourceWithAnnotatedField() {
        return sourceWithAnnotatedField;
    }

    public Map<String, String> getSourceWithTopTask() {
        return sourceWithTopTask;
    }

    public Map<String, Set<String>> getSourceWithAncestorPath() {
        return sourceWithAncestorPath;
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
                String expression = (String) parseValue(
                        directive.getArgument("expression").getValue()
                );

                if (expression == null || expression.isEmpty()) {
                    String errorMsg = String.format("the expression for @skipBy on {%s} can not be empty.", fieldFullPath);
                    addValidError(location, errorMsg);
                    continue;
                }

                if (!isValidExp(expression)) {
                    String errorMsg = String.format("invalid expression '%s' for @skipBy on {%s}.", expression, fieldFullPath);
                    addValidError(location, errorMsg);
                    continue;
                }

                checkAndSetFieldWithTopTask(fieldFullPath, directive, environment);
                checkAndSetSourceUsedByFieldInfo(fieldFullPath,directive);
                fieldWithAncestorPath.put(fieldFullPath,parentPathSet(environment));

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

                if (!isValidExp(predicate)) {
                    String errorMsg = String.format("invalid predicate '%s' for @filter on {%s}.", predicate, fieldFullPath);
                    addValidError(location, errorMsg);
                }

                checkAndSetFieldWithTopTask(fieldFullPath, directive, environment);
                checkAndSetSourceUsedByFieldInfo(fieldFullPath,directive);
                fieldWithAncestorPath.put(fieldFullPath,parentPathSet(environment));

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
                String expression = (String) parseValue(
                        directive.getArgument("expression").getValue()
                );

                if (!isValidExp(expression)) {
                    String errorMsg = String.format("invalid expression '%s' for @skipBy on {%s}.", expression, fieldFullPath);
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

                checkAndSetFieldWithTopTask(fieldFullPath, directive, environment);
                checkAndSetSourceUsedByFieldInfo(fieldFullPath,directive);
                fieldWithAncestorPath.put(fieldFullPath,parentPathSet(environment));

            } else if (Objects.equals(directiveName, MAP.getName())) {

                String mapper = getArgumentFromDirective(directive, "expression");
                if (!isValidExp(mapper)) {
                    String errorMsg = String.format("invalid expression '%s' for @map on {%s}.", mapper, fieldFullPath);
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
                if (!isValidExp(expression)) {
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
                if (sourceConvert != null && !isValidExp(sourceConvert)) {
                    String errorMsg = String.format("invalid sourceConvert '%s' for @fetchSource on {%s}.", sourceConvert, fieldFullPath);
                    addValidError(location, errorMsg);
                }

                // 获取其顶层任务节点路径
                QueryVisitorFieldEnvironment topTaskEnv = getTopTaskEnv(environment);
                String topTaskFieldPath = pathForTraverse(topTaskEnv);
                sourceWithTopTask.put(sourceName, topTaskFieldPath);

                // 获取其父类节点路径
                Set<String> parentPathSet = parentPathSet(environment);
                sourceWithAncestorPath.put(sourceName, parentPathSet);

                checkAndSetFieldWithTopTask(fieldFullPath, directive, environment);
                checkAndSetSourceUsedByFieldInfo(fieldFullPath,directive);
                fieldWithAncestorPath.put(fieldFullPath,parentPathSet);
            }
        }
    }

    private void checkAndSetFieldWithTopTask(String fieldFullPath, Directive directive, QueryVisitorFieldEnvironment visitorFieldEnvironment) {
        Argument sourceArgument = directive.getArgument("dependencySource");

        if (sourceArgument != null && sourceArgument.getValue() != null) {
            QueryVisitorFieldEnvironment topTaskEnv = getTopTaskEnv(visitorFieldEnvironment);
            String topTaskFieldPath = pathForTraverse(topTaskEnv);
            fieldWithTopTask.put(fieldFullPath, topTaskFieldPath);
        }
    }

    //    // <fieldFullPath, List<sourceName>>
    //    private final Map<String, List<String>> sourceUsedByField = new LinkedHashMap<>();
    private void checkAndSetSourceUsedByFieldInfo(String fieldFullPath,Directive directive) {
        Argument sourceArgument = directive.getArgument("dependencySource");

        if (sourceArgument != null && sourceArgument.getValue() != null) {
            String dependencySource = (String) parseValue(sourceArgument.getValue());

            if (sourceUsedByField.containsKey(fieldFullPath)) {
                sourceUsedByField.get(fieldFullPath).add(dependencySource);
            } else {
                ArrayList<String> sourceNames = new ArrayList<>();
                sourceNames.add(dependencySource);
                sourceUsedByField.put(fieldFullPath, sourceNames);
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
