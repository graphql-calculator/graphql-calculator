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
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.util.TraverserContext;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static calculator.common.Tools.getArgumentFromDirective;
import static calculator.common.VisitorUtils.getTopTaskEnv;
import static calculator.common.VisitorUtils.parentPathSet;
import static calculator.common.VisitorUtils.pathForTraverse;
import static calculator.engine.function.ExpEvaluator.getExpArgument;
import static calculator.engine.metadata.Directives.ARGUMENT_TRANSFORM;
import static calculator.engine.metadata.Directives.FILTER;
import static calculator.engine.metadata.Directives.LINK;
import static calculator.engine.metadata.Directives.SORT_BY;
import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;


/**
 * 检查指令上node的用法是否正确，核心思想为 使用到了node节点的字段不能是该node节点所依赖字段，分为以下两种情况：
 *  1. 当node节点不是在list元素是，使用到node节点的字段不能是其子节点。执行完成的顺序是由下到上的；
 *  2. 当node节点是list元素的节点，则与使用到该node节点的字段不能在同一个list中，即不能是同一个top任务。
 *
 * check whether the usage of node on directives is correct.
 */
public class NodeRule extends AbstractRule {

    // <node名称, 注解的字段>
    private final Map<String, String> nodeWithAnnotatedField;

    // <node名称, 依赖的顶层节点>
    private final Map<String, String> nodeWithTopTask;

    // <node名称, List<祖先节点>>
    private final Map<String, Set<String>> nodeWithAncestorPath;

    // 没有被使用的节点，每当有指令使用到该节点都要移除对应的元素
    private final HashSet<String> unusedNode = new HashSet<>();

    public NodeRule(Map<String, String> nodeWithAnnotatedField, Map<String, String> nodeWithTopTask, Map<String, Set<String>> nodeWithAncestorPath) {
        this.nodeWithAnnotatedField = nodeWithAnnotatedField;
        this.nodeWithTopTask = nodeWithTopTask;
        this.nodeWithAncestorPath = nodeWithAncestorPath;
        unusedNode.addAll(nodeWithAnnotatedField.keySet());
    }


    public HashSet<String> getUnusedNode() {
        return unusedNode;
    }

    @Override
    public void visitField(QueryVisitorFieldEnvironment environment) {
        // 不是进入该节点则返回
        if (environment.getTraverserContext().getPhase() != TraverserContext.Phase.ENTER) {
            return;
        }

        List<Directive> directives = environment.getField().getDirectives();
        if (directives == null || directives.isEmpty()) {
            return;
        }

        String fieldFullPath = pathForTraverse(environment);
        Set<String> argumentsOnField = environment.getField().getArguments().stream().map(Argument::getName).collect(toSet());
        // <argument, nodeName>：同一个参数不能被两个node挂上
        // kp 是否应该放开？不应该，因为 link 没有指令表达式，
        //    不会依赖前值，重复定位到一个参数前者会无效。
        //    如果不能的话，其他参数转换指令是否可以指向该参数：可以，使用前值。
        Map<String, String> nodeByArgument = new LinkedHashMap<>();

        for (Directive directive : directives) {
            if (Objects.equals(directive.getName(), LINK.getName())) {

                // node必须存在
                String dependencyNodeName = getArgumentFromDirective(directive, "node");
                // 依赖的节点是否存在，存在这从unusedNode中删除
                if (!validateExist(fieldFullPath, directive, dependencyNodeName)) {
                    continue;
                }


                // argument 必须定义在查询语句中
                String argumentName = getArgumentFromDirective(directive, "argument");
                if (!argumentsOnField.contains(argumentName)) {
                    String errorMsg = format(
                            "'%s' do not exist on {%s}.", argumentName, fieldFullPath
                    );
                    addValidError(directive.getSourceLocation(), errorMsg);
                    continue;
                }

                // 两个node不能指向同一个参数
                if (nodeByArgument.containsKey(argumentName)) {
                    String errorMsg = format(
                            "node must not linked to the same argument '%s', @link defined on '%s' is invalid.",
                            argumentName, fieldFullPath
                    );
                    addValidError(directive.getSourceLocation(), errorMsg);
                    continue;
                } else {
                    nodeByArgument.put(argumentName, dependencyNodeName);
                }
            } else if (Objects.equals(directive.getName(), SORT_BY.getName())) {
                String dependencyNodeName = getArgumentFromDirective(directive, "dependencyNode");
                // emptyMap.containsKey(null)结果为true
                if(dependencyNodeName == null){
                    continue;
                }

                // 依赖的节点是否存在，存在这从unusedNode中删除
                if (!validateExist(fieldFullPath, directive, dependencyNodeName)) {
                    continue;
                }


                // node节点名称不能和字段上参数名称一样，因为都会作为环境变量的key执行表达式计算
                if(argumentsOnField.contains(dependencyNodeName)){
                    String errorMsg = format(
                            "the node name '%s' must be different to field argument name %s.",
                            dependencyNodeName, argumentsOnField
                    );
                    addValidError(directive.getSourceLocation(), errorMsg);
                    continue;
                }

                // 依赖的node必须被 @filter 使用了
                String predicate = (String) Tools.parseValue(
                        directive.getArgument("exp").getValue()
                );
                List<String> arguments = getExpArgument(predicate);
                if (!arguments.contains(dependencyNodeName)) {
                    String errorMsg = format(
                            "the node '%s' do not used by {%s}.", dependencyNodeName, fieldFullPath
                    );
                    addValidError(directive.getSourceLocation(), errorMsg);
                    continue;
                }

                // 不能在同一个节点上，这种情况是 两个节点共享同一个祖先节点的特殊情况，判断成本小。
                String nodeAnnotatedFieldPath = nodeWithAnnotatedField.get(dependencyNodeName);
                if (Objects.equals(fieldFullPath, nodeAnnotatedFieldPath)) {
                    String errorMsg = format(
                            "the node '%s' and filter can not annotated on the same field {%s}.",
                            dependencyNodeName, fieldFullPath
                    );
                    addValidError(directive.getSourceLocation(), errorMsg);
                    continue;
                }

                // 判断filter不在node依赖的任务节点中（同时也会校验到两者不能放到同一个节点上）
                QueryVisitorFieldEnvironment filterTopTaskEnv = getTopTaskEnv(environment);
                String filterTopTask = pathForTraverse(filterTopTaskEnv);
                String nodeTopTask = nodeWithTopTask.get(dependencyNodeName);
                if (Objects.equals(nodeTopTask, filterTopTask)) {
                    String errorMsg = format(
                            "the node '%s' and field '%s' is in the same ancestor list field '%s'.",
                            dependencyNodeName, fieldFullPath, nodeTopTask
                    );
                    addValidError(directive.getSourceLocation(), errorMsg);
                    continue;
                }

            } else if (Objects.equals(directive.getName(), FILTER.getName())) {

                String dependencyNodeName = getArgumentFromDirective(directive, "dependencyNode");
                // emptyMap.containsKey(null)结果为true
                if(dependencyNodeName == null){
                    continue;
                }

                // 依赖的节点是否存在，存在这从unusedNode中删除
                if (!validateExist(fieldFullPath, directive, dependencyNodeName)) {
                    continue;
                }

                // node节点名称不能和字段上参数名称一样，因为都会作为环境变量的key执行表达式计算
                if(argumentsOnField.contains(dependencyNodeName)){
                    String errorMsg = format(
                            "the node name '%s' must be different to field argument name %s.",
                            dependencyNodeName, argumentsOnField
                    );
                    addValidError(directive.getSourceLocation(), errorMsg);
                    continue;
                }

                // 依赖的node必须被 @filter 使用了
                String predicate = (String) Tools.parseValue(
                        directive.getArgument("predicate").getValue()
                );
                List<String> arguments = getExpArgument(predicate);
                if (!arguments.contains(dependencyNodeName)) {
                    String errorMsg = format(
                            "the node '%s' do not used by {%s}.", dependencyNodeName, fieldFullPath
                    );
                    addValidError(directive.getSourceLocation(), errorMsg);
                    continue;
                }

                // 不能在同一个节点上，这种情况是 两个节点共享同一个祖先节点的特殊情况，判断成本小。
                String nodeAnnotatedFieldPath = nodeWithAnnotatedField.get(dependencyNodeName);
                if (Objects.equals(fieldFullPath, nodeAnnotatedFieldPath)) {
                    String errorMsg = format(
                            "the node '%s' and filter can not annotated on the same field {%s}.",
                            dependencyNodeName, fieldFullPath
                    );
                    addValidError(directive.getSourceLocation(), errorMsg);
                    continue;
                }


                // 判断filter不在node依赖的任务节点中（同时也会校验到两者不能放到同一个节点上）
                QueryVisitorFieldEnvironment filterTopTaskEnv = getTopTaskEnv(environment);
                String filterTopTask = pathForTraverse(filterTopTaskEnv);
                String nodeTopTask = nodeWithTopTask.get(dependencyNodeName);
                if (Objects.equals(nodeTopTask, filterTopTask)) {
                    String errorMsg = format(
                            "the node '%s' and field '%s' is in the same ancestor list field '%s'.",
                            dependencyNodeName, fieldFullPath, nodeTopTask
                    );
                    addValidError(directive.getSourceLocation(), errorMsg);
                    continue;
                }

                // node 不是该指令注解节点的父亲节点。
                // 对于没有list元素的单节点链路，两者不共享同一个topTaskNode，但是父子关系也会使其依赖关系有循环。
                Set<String> parentPathSet = parentPathSet(environment);
                if (parentPathSet.contains(nodeAnnotatedFieldPath)) {
                    String errorMsg = format(
                            "the field {%s} can not depend on its ancestor {%s} (node '%s').",
                            fieldFullPath, nodeAnnotatedFieldPath,dependencyNodeName
                    );
                    addValidError(directive.getSourceLocation(), errorMsg);
                    continue;
                }

            }

            if (Objects.equals(directive.getName(), ARGUMENT_TRANSFORM.getName())) {

                String dependencyNodeName = getArgumentFromDirective(directive, "dependencyNode");
                if(dependencyNodeName == null){
                    continue;
                }

                // 依赖的节点是否存在，存在这从unusedNode中删除
                if (!validateExist(fieldFullPath, directive, dependencyNodeName)) {
                    continue;
                }

                // node节点名称不能和字段上参数名称一样，因为都会作为环境变量的key执行表达式计算
                if(argumentsOnField.contains(dependencyNodeName)){
                    String errorMsg = format(
                            "the node name '%s' must be different to field argument name {%s}.",
                            dependencyNodeName, argumentsOnField
                    );
                    addValidError(directive.getSourceLocation(), errorMsg);
                    continue;
                }


                // node必须被使用了
                String predicate = (String) Tools.parseValue(
                        directive.getArgument("exp").getValue()
                );
                List<String> arguments = getExpArgument(predicate);
                if (!arguments.contains(dependencyNodeName)) {
                    String errorMsg = format(
                            "the node '%s' do not used by {%s}.", dependencyNodeName, fieldFullPath
                    );
                    addValidError(directive.getSourceLocation(), errorMsg);
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
     * @param dependencyNodeName 指令依赖的节点名称
     *
     * @return 是否校验成功
     */
    private boolean validateExist(String fieldFullPath, Directive directive, String dependencyNodeName) {
        // 依赖的node必须存在。
        if (!nodeWithAnnotatedField.containsKey(dependencyNodeName)) {
            // 错误信息中，名称使用单引号''，路径使用花括号{}
            String errorMsg = format(
                    "the node '%s' used by {%s} do not exist.", dependencyNodeName, fieldFullPath
            );
            addValidError(directive.getSourceLocation(), errorMsg);
            return false;
        }
        unusedNode.remove(dependencyNodeName);
        return true;
    }
}
