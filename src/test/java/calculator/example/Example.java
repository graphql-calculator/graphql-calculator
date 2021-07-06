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
import calculator.config.ConfigImpl;
import calculator.engine.DefaultObjectMapper;
import calculator.graphql.AsyncDataFetcherInterface;
import calculator.graphql.CalculatorDocumentCachedProvider;
import calculator.graphql.DefaultGraphQLSourceBuilder;
import calculator.graphql.GraphQLSource;
import calculator.engine.SchemaHolder;
import calculator.engine.script.AviatorScriptEvaluator;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.schema.GraphQLSchema;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Function;

public class Example {

    static class DocumentParseAndValidationCache extends CalculatorDocumentCachedProvider {

        private final Map<String, PreparsedDocumentEntry> cache = new LinkedHashMap<>();

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
         * Make async dataFetcher implements {@link AsyncDataFetcherInterface}
         *
         * step 2
         * Create {@link GraphQLSource} by {@link Config}: including wrapped graphql schema and GraphQL object.
         * create Config, and get wrapped schema with the ability of
         * orchestrate and dynamic calculator and control flow, powered by directives,
         * and create GraphQL with wrapped schema and ExecutionEngine.
         *
         * step 3:
         * validate the query: {@code ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema).}
         * It is recommend to create `PreparsedDocumentProvider` by implementing {@link DocumentParseAndValidationCache}.
         */

        GraphQLSchema schema = SchemaHolder.getSchema();


        Config wrapperConfig = ConfigImpl.newConfig()
                .scriptEvaluator(AviatorScriptEvaluator.getDefaultInstance())
                .objectMapper(new DefaultObjectMapper())
                .threadPool(Executors.newCachedThreadPool())
                .build();


        DefaultGraphQLSourceBuilder graphqlSourceBuilder = new DefaultGraphQLSourceBuilder();
        GraphQLSource graphqlSource = graphqlSourceBuilder
                .wrapperConfig(wrapperConfig)
                .originalSchema(schema)
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

        ExecutionResult result = graphqlSource.graphQL().execute(input);
        // consumer result
    }
}
