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

package calculator.engine.validation;


import calculator.config.Config;
import calculator.engine.annotation.Internal;
import calculator.engine.metadata.Directives;
import graphql.language.IntValue;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.InputValueWithState;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


@Internal
public class BasicRule extends AbstractRule {

    private final Config config;

    BasicRule(Config config) {
        this.config = config;
    }

    @Override
    public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition fieldDefinition, TraverserContext<GraphQLSchemaElement> context) {

        Map<String, GraphQLArgument> argumentWithPartitionByName = fieldDefinition.getArguments().stream()
                .filter(argument -> argument.getDirective(Directives.PARTITION.getName()) != null)
                .collect(Collectors.toMap(
                        GraphQLArgument::getName,
                        Function.identity(),
                        (v1, v2) -> {
                            // todo debug error message;
                            return v1;
                        }
                ));
        if (argumentWithPartitionByName.isEmpty()) {
            return TraversalControl.CONTINUE;
        }

        GraphQLNamedType parentNode = (GraphQLNamedType) context.getParentNode();
        String fieldFullPath = parentNode.getName() + "." + fieldDefinition.getName();

        GraphQLType unwrapNonNullType = GraphQLTypeUtil.unwrapNonNull(fieldDefinition.getType());
        if (!GraphQLTypeUtil.isList(unwrapNonNullType)) {
            String errorMsg = String.format("@partition must be used on list type field, instead of {%s}.", fieldFullPath);
            addValidError(fieldDefinition.getDefinition().getSourceLocation(), errorMsg);
            return TraversalControl.CONTINUE;
        }

        if (argumentWithPartitionByName.size() > 1) {
            String errorMsg = String.format("more than one argument on {%s} use @partition.", fieldFullPath);
            addValidError(fieldDefinition.getDefinition().getSourceLocation(), errorMsg);
            return TraversalControl.CONTINUE;
        }

        GraphQLArgument argument = argumentWithPartitionByName.values().iterator().next();
        GraphQLType argumentType = GraphQLTypeUtil.unwrapNonNull(argument.getType());
        if (!GraphQLTypeUtil.isList(argumentType)) {
            String errorMsg = String.format("@partition must be used on list type argument, instead of {%s}.",
                    fieldFullPath + "." + argument.getName()
            );
            addValidError(fieldDefinition.getDefinition().getSourceLocation(), errorMsg);
            return TraversalControl.CONTINUE;
        }


        GraphQLDirective directive = argument.getDirective(Directives.PARTITION.getName());
        InputValueWithState sizeArgument = directive.getArgument("size").getArgumentValue();
        Object sizeArgumentValue = sizeArgument.getValue();
        if (!(sizeArgumentValue instanceof IntValue)) {
            String errorMsg = String.format("the size value of @partition on {%s} must be number.", fieldFullPath);
            addValidError(fieldDefinition.getDefinition().getSourceLocation(), errorMsg);
            return TraversalControl.CONTINUE;
        }

        return TraversalControl.CONTINUE;
    }
}
