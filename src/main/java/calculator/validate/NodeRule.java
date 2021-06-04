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
import static calculator.common.VisitorUtils.pathForTraverse;
import static calculator.engine.metadata.CalculateDirectives.FILTER;
import static calculator.engine.metadata.CalculateDirectives.LINK;
import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;

/**
 * - 环校验：是否都在一个list里边 / 是否有相同的list父节点；也不能依赖父亲节点；TODO：多看几个sql
 *         // todo @node节点的完成不依赖使用到node的节点，所以只有上述两种情况：父亲节点和同一个顶层任务节点下的节点、包括顶层任务节点本身。
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
                String nodeName = getArgumentFromDirective(directive, "node");
                if (!nodeWithAnnotatedField.containsKey(nodeName)) {
                    // 错误信息中，名称使用单引号''，路径使用花括号{}
                    String errorMsg = format(
                            "the node '%s' used by {%s} do not exist.", nodeName, fieldFullPath
                    );
                    addValidError(directive.getSourceLocation(), errorMsg);
                    continue;
                }
                unusedNode.remove(nodeName);


                // argument 必须定义在查询语句中
                String argumentName = getArgumentFromDirective(directive, "argument");
                if (!argumentsOnField.contains(argumentName)) {
                    String errorMsg = format(
                            "'%s' do not defined on {%s}.", argumentName, fieldFullPath
                    );
                    addValidError(directive.getSourceLocation(), errorMsg);
                    continue;
                }

                // 两个node不能指向同一个参数
                if (nodeByArgument.containsKey(argumentName)) {
                    String errorMsg = format(
                            "node must not linked to the same argument: '%s', @link defined on '%s'@%s is invalid.",
                            argumentName, fieldFullPath
                    );
                    addValidError(directive.getSourceLocation(), errorMsg);
                    continue;
                } else {
                    nodeByArgument.put(argumentName, nodeName);
                }
            }

            if (Objects.equals(directive.getName(), FILTER.getName())) {
                String dependencyNodeName = getArgumentFromDirective(directive, "dependencyNode");
                if (!nodeWithAnnotatedField.containsKey(dependencyNodeName)) {
                    // 错误信息中，名称使用单引号''，路径使用花括号{}
                    String errorMsg = format(
                            "the node '%s' used by {%s} do not exist.", dependencyNodeName, fieldFullPath
                    );
                    addValidError(directive.getSourceLocation(), errorMsg);
                    continue;
                }
                unusedNode.remove(dependencyNodeName);
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
