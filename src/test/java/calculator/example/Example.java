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
import calculator.engine.ExecutionEngine;
import calculator.engine.SchemaHolder;
import calculator.engine.SchemaWrapper;
import calculator.engine.script.AviatorScriptEvaluator;
import calculator.validation.Validator;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.ParseAndValidateResult;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.schema.GraphQLSchema;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class Example {

    static class DocumentParseAndValidationCache implements PreparsedDocumentProvider {

        private final Map<String, PreparsedDocumentEntry> cache = new LinkedHashMap<>();

        private final Config wrapperConfig;
        private final GraphQLSchema wrappedSchema;


        public DocumentParseAndValidationCache(Config wrapperConfig, GraphQLSchema wrappedSchema) {
            this.wrapperConfig = wrapperConfig;
            this.wrappedSchema = wrappedSchema;
        }


        @Override
        public PreparsedDocumentEntry getDocument(ExecutionInput executionInput,
                                                  Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
            if (cache.get(executionInput.getQuery()) != null) {
                return cache.get(executionInput.getQuery());
            }

            ParseAndValidateResult validateResult = Validator.validateQuery(
                    executionInput.getQuery(), wrappedSchema, wrapperConfig
            );

            PreparsedDocumentEntry preparsedDocumentEntry;
            if (validateResult.isFailure()) {
                preparsedDocumentEntry = new PreparsedDocumentEntry(validateResult.getErrors());
            } else {
                preparsedDocumentEntry = new PreparsedDocumentEntry(validateResult.getDocument());
            }
            cache.put(executionInput.getQuery(), preparsedDocumentEntry);
            return preparsedDocumentEntry;
        }
    }

    public static void main(String[] args) {

        /**
         * step 1
         *
         * make async dataFetcher implements xxx
         */
        GraphQLSchema schema = SchemaHolder.getSchema();


        /**
         * step 2
         *
         * create Config, and get wrapped schema with the ability of
         * orchestrate and dynamic calculator and control flow, powered by directives,
         * and create GraphQL with wrapped schema and ExecutionEngine.
         *
         * It is recommend to create `PreparsedDocumentProvider` to cache the result of parse and validate.
         */
        Config config = ConfigImpl.newConfig()
                .scriptEvaluator(AviatorScriptEvaluator.getDefaultInstance())
                .build();

        GraphQLSchema wrappedSchema = SchemaWrapper.wrap(config, schema);

        GraphQL graphQL = GraphQL.newGraphQL(wrappedSchema)
                .instrumentation(ExecutionEngine.newInstance(config))
                .preparsedDocumentProvider(new DocumentParseAndValidationCache(config, wrappedSchema))
                .build();

        /**
         * step 3:
         *
         * validate the query: ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema).
         *
         * It is recommend to create `PreparsedDocumentProvider` to cache the result of parse and validate.
         * Reference {@link DocumentParseAndValidationCache}
         */
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

//        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
//        if(validateResult.isFailure()){
//            List<GraphQLError> errors = validateResult.getErrors();
//            // do some thing
//        }else{
//            Document document = validateResult.getDocument();
//            // do some thing or ignored
//        }

        ExecutionInput input = ExecutionInput
                .newExecutionInput(query)
                .variables(Collections.singletonMap("itemIds", Arrays.asList(1, 2, 3)))
                .build();

        ExecutionResult result = graphQL.execute(input);
        // consumer result
    }

}
