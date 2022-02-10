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

package calculator.engine;

import calculator.engine.annotation.Internal;
import calculator.engine.metadata.Directives;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitor;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Internal
public class PartitionDataFetcher implements DataFetcher<List<Object>> {

    private final int partitionSize;

    private final String argumentName;

    private final DataFetcher<List<Object>> delegate;

    private PartitionDataFetcher(int partitionSize, String argumentName, DataFetcher<List<Object>> delegate) {
        this.partitionSize = partitionSize;
        this.argumentName = argumentName;
        this.delegate = delegate;
    }

    @Override
    public List<Object> get(DataFetchingEnvironment environment) throws Exception {

        List argumentValue = environment.getArgument(argumentName);
        if (argumentValue == null || argumentValue.isEmpty()) {
            return delegate.get(environment);
        }

        List<Object> result = new ArrayList<>();
        for (int i = 0; i < argumentValue.size(); i += partitionSize) {
            int toIndex = Math.min(i + partitionSize, partitionSize);
            List partitionArgumentValue = argumentValue.subList(i, toIndex);

            Map<String, Object> newArguments = new LinkedHashMap<>(environment.getArguments());
            newArguments.put(argumentName, partitionArgumentValue);

            DataFetchingEnvironment partitionEnv = DataFetchingEnvironmentImpl
                    .newDataFetchingEnvironment(environment)
                    .arguments(newArguments)
                    .build();

            List<Object> partitionResult = delegate.get(partitionEnv);
            if (partitionResult != null) {
                result.addAll(partitionResult);
            }
        }

        return result;
    }

    static GraphQLTypeVisitor TYPE_VISITOR = new GraphQLTypeVisitorStub() {
        @Override
        public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition fieldDefinition, TraverserContext<GraphQLSchemaElement> context) {

            for (GraphQLArgument argument : fieldDefinition.getArguments()) {
                if (argument.getDirective(Directives.PARTITION.getName()) != null) {
                    GraphQLCodeRegistry.Builder codeRegistry = context.getVarFromParents(GraphQLCodeRegistry.Builder.class);
                    GraphQLFieldsContainer parent = (GraphQLFieldsContainer) context.getParentNode();
                    DataFetcher originalDataFetcher = codeRegistry.getDataFetcher(parent, fieldDefinition);

                    GraphQLDirective partitionDirective = argument.getDirective(Directives.PARTITION.getName());

                    GraphQLArgument directiveArgument = partitionDirective.getArgument("size");
                    int partitionSize = (int) directiveArgument.getArgumentValue().getValue();
                    DataFetcher<?> partition = new PartitionDataFetcher(partitionSize, argument.getName(), originalDataFetcher);

                    codeRegistry.dataFetcher(parent, fieldDefinition, partition);
                }
            }

            return TraversalControl.CONTINUE;
        }
    };
}
