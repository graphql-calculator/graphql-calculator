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

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static calculator.common.Tools.getArgumentFromDirective;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;


/**
 * 校验规则：
 *      link使用的node节点必须存在；
 *      node指向的参数必须存在；
 *      两个node不能链接到同一个参数上；
 *
 * todo
 *     node指向的参数类型必须兼容注册的节点类型;
 */
public class LinkRules extends AbstractTraverRule {

    private Map<String, String> nodeNameMap;

    // 在link中使用的node
    private Set<String> usedNodeName;

    // todo 是否需要考虑并发
    public LinkRules() {
        super();
        usedNodeName = new LinkedHashSet<>();
    }

    public static LinkRules newInstance() {
        return new LinkRules();
    }

    public void setNodeNameMap(Map<String, String> nodeNameMap) {
        this.nodeNameMap = nodeNameMap;
    }

    public Set<String> getUsedNodeName() {
        return usedNodeName;
    }

    @Override
    public void visitField(QueryVisitorFieldEnvironment environment) {
        // 不是进入该节点则返回
        if (environment.getTraverserContext().getPhase() != TraverserContext.Phase.ENTER) {
            return;
        }

        List<Directive> linkDirList = environment.getField().getDirectives().stream()
                .filter(dir -> Objects.equals("link", dir.getName())).collect(toList());
        if (!linkDirList.isEmpty()) {
            String aliasOrName = environment.getField().getResultKey();
            Set<String> argumentsName = environment.getField().getArguments().stream().map(Argument::getName).collect(toSet());

            Map<String, String> argByNodeName = new HashMap<>();
            for (Directive linkDir : linkDirList) {
                // node必须存在
                String nodeName =getArgumentFromDirective(linkDir, "node");
                if (!nodeNameMap.containsKey(nodeName)) {
                    String errorMsg = format("the node '%s' used by '%s'@%s do not exist.", nodeName, aliasOrName, environment.getField().getSourceLocation());
                    addValidError(linkDir.getSourceLocation(), errorMsg);
                    continue;
                }
                usedNodeName.add(nodeName);


                // argument 必须定义在查询语句中
                String argumentName = getArgumentFromDirective(linkDir, "argument");
                if (!argumentsName.contains(argumentName)) {
                    String errorMsg = format("'%s' do not defined on '%s'@%s.",
                            argumentName,
                            aliasOrName,
                            environment.getField().getSourceLocation()
                    );
                    addValidError(linkDir.getSourceLocation(), errorMsg);
                    continue;
                }


                // 两个node不能同一个参数
                if (argByNodeName.containsKey(argumentName)) {
                    String errorMsg = format("node must not linked to the same argument: '%s', @link defined on '%s'@%s is invalid.",
                            argumentName,
                            aliasOrName,
                            environment.getField().getSourceLocation()
                    );
                    addValidError(linkDir.getSourceLocation(), errorMsg);
                    continue;
                } else {
                    argByNodeName.put(argumentName, nodeName);
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
