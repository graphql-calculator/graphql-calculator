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
package calculator.example;

import calculator.config.Config;
import calculator.config.DefaultConfig;
import calculator.graphql.AsyncDataFetcherInterface;
import calculator.graphql.CalculatorDocumentCachedProvider;
import calculator.graphql.DefaultGraphQLSourceBuilder;
import calculator.graphql.GraphQLSource;
import calculator.util.GraphQLSourceHolder;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.execution.preparsed.PreparsedDocumentEntry;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class Example {

    static class DocumentParseAndValidationCache extends CalculatorDocumentCachedProvider {

        private final Map<String, PreparsedDocumentEntry> cache = new ConcurrentHashMap<>();

        @Override
        public PreparsedDocumentEntry getDocumentFromCache(ExecutionInput executionInput,
                                                           Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
            return cache.get(executionInput.getQuery());
        }

        @Override
        public void setDocumentCache(ExecutionInput executionInput,
                                     PreparsedDocumentEntry cachedValue) {
            cache.put(executionInput.getQuery(), cachedValue);
        }
    }

    public static void main(String[] args) {
        
        /**
         * step 1
         * Create {@link GraphQLSource} by {@link Config}, which including wrapped graphql schema and GraphQL object.
         *
         * step 2:
         * Validate the query: {@code ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema).}
         * It is recommend to create `PreparsedDocumentProvider` by implementing {@link CalculatorDocumentCachedProvider}.
         */
        Config wrapperConfig = DefaultConfig.newConfig().build();

        DefaultGraphQLSourceBuilder graphqlSourceBuilder = new DefaultGraphQLSourceBuilder();
        GraphQLSource graphqlSource = graphqlSourceBuilder
                .wrapperConfig(wrapperConfig)
                .originalSchema(
                        //  create Schema like you always do
                        GraphQLSourceHolder.getDefaultSchema()
                )
                .preparsedDocumentProvider(new DocumentParseAndValidationCache()).build();

        String query = ""
                + "query mapListArgument($itemIds: [Int]){ \n" +
                "    commodity{\n" +
                "        itemList(itemIds: $itemIds)\n" +
                "        @argumentTransform(argumentName: \"itemIds\",operateType: LIST_MAP,expression: \"ele*10\")\n" +
                "        {\n" +
                "            itemId\n" +
                "            name\n" +
                "        }\n" +
                "    }\n" +
                "}";
        ExecutionInput input = ExecutionInput
                .newExecutionInput(query)
                .variables(Collections.singletonMap("itemIds", Arrays.asList(1, 2, 3)))
                .build();

        ExecutionResult result = graphqlSource.getGraphQL().execute(input);
    }
}
