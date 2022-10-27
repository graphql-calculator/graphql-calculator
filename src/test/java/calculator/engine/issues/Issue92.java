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

package calculator.engine.issues;

import calculator.config.DefaultConfig;
import calculator.graphql.DefaultGraphQLSourceBuilder;
import calculator.graphql.GraphQLSource;
import calculator.util.GraphQLSourceHolder;
import calculator.validation.Validator;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ParseAndValidateResult;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static graphql.schema.AsyncDataFetcher.async;

public class Issue92 {

    @Test
    public void normalQuery() {

        Map<String, Map<String, DataFetcher>> dataFetcherInfo = new HashMap<>();
        Map<String, DataFetcher> queryFieldFetchers = new HashMap<>();
        queryFieldFetchers.put("countrys", async(environment -> {
            List<Integer> coutryIds = environment.getArgument("coutryIds");
            if (coutryIds == null) {
                return null;
            }

            return coutryIds.stream().map(id -> Collections.singletonMap("capital", Collections.singletonMap("capitalId", id + 10))).collect(Collectors.toList());
        }));
        queryFieldFetchers.put("capital", async(environment -> {
            List<Integer> capitalIds = environment.getArgument("capitalIds");
            if (capitalIds == null) {
                return null;
            }

            return capitalIds.stream().map(id -> Collections.singletonMap("capitalName", "capital_name_" + id)).collect(Collectors.toList());
        }));
        dataFetcherInfo.put("Query", queryFieldFetchers);

        GraphQLSchema originalSchema = GraphQLSourceHolder.configGraphQLSchema("Issue92.graphql", dataFetcherInfo);
        DefaultConfig config = DefaultConfig.newConfig().build();
        GraphQLSource graphqlSource = new DefaultGraphQLSourceBuilder().wrapperConfig(config).originalSchema(originalSchema).build();

        String query = ""
                + "\n" +
                "query queryCapitalByCountryIds($coutryIds:[Int]){\n" +
                "    countrys (coutryIds: $coutryIds){\n" +
                "        capital{\n" +
                "            capitalId @fetchSource(name:\"capitalIdList\")\n" +
                "            capitalName\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    capital (capitalIds: 1)\n" +
                "    @argumentTransform(argumentName:\"capitalIds\",operateType:MAP,expression:\"capitalIdList\",dependencySources:\"capitalIdList\")\n" +
                "    {\n" +
                "        capitalId\n" +
                "        capitalName\n" +
                "    }\n" +
                "}\n";

        ParseAndValidateResult validateResult = Validator.validateQuery(query, graphqlSource.getWrappedSchema(), config);
        assert !validateResult.isFailure();

        HashMap<String, Object> variables = new LinkedHashMap<>();
        variables.put("coutryIds", Arrays.asList(1, 2, 3));
        ExecutionInput input = ExecutionInput
                .newExecutionInput(query)
                .variables(variables)
                .build();
        ExecutionResult executionResult = graphqlSource.getGraphQL().execute(input);

        assert executionResult != null;
        assert executionResult.getErrors() == null || executionResult.getErrors().isEmpty();
        Map<String, Object> data = executionResult.getData();
        assert Objects.equals(data.get("capital").toString(),
                "[{capitalId=null, capitalName=capital_name_11}, {capitalId=null, capitalName=capital_name_12}, {capitalId=null, capitalName=capital_name_13}]"
        );
    }


    @Test
    public void exceptionQuery() {

        Map<String, Map<String, DataFetcher>> dataFetcherInfo = new HashMap<>();
        Map<String, DataFetcher> queryFieldFetchers = new HashMap<>();
        queryFieldFetchers.put("countrys", async(environment -> {
            throw new RuntimeException("mock exception");
        }));
        queryFieldFetchers.put("capital", async(environment -> {
            List<Integer> capitalIds = environment.getArgument("capitalIds");
            if (capitalIds == null) {
                return null;
            }

            return capitalIds.stream().map(id -> Collections.singletonMap("capitalName", "capital_name_" + id)).collect(Collectors.toList());
        }));
        dataFetcherInfo.put("Query", queryFieldFetchers);

        GraphQLSchema originalSchema = GraphQLSourceHolder.configGraphQLSchema("Issue92.graphql", dataFetcherInfo);
        DefaultConfig config = DefaultConfig.newConfig().build();
        GraphQLSource graphqlSource = new DefaultGraphQLSourceBuilder().wrapperConfig(config).originalSchema(originalSchema).build();

        String query = ""
                + "\n" +
                "query queryCapitalByCountryIds($coutryIds:[Int]){\n" +
                "    countrys (coutryIds: $coutryIds){\n" +
                "        capital{\n" +
                "            capitalId @fetchSource(name:\"capitalIdList\")\n" +
                "            capitalName\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    capital (capitalIds: 1)\n" +
                "    @argumentTransform(argumentName:\"capitalIds\",operateType:MAP,expression:\"capitalIdList\",dependencySources:\"capitalIdList\")\n" +
                "    {\n" +
                "        capitalId\n" +
                "        capitalName\n" +
                "    }\n" +
                "}\n";

        ParseAndValidateResult validateResult = Validator.validateQuery(query, graphqlSource.getWrappedSchema(), config);
        assert !validateResult.isFailure();

        HashMap<String, Object> variables = new LinkedHashMap<>();
        variables.put("coutryIds", Arrays.asList(1, 2, 3));
        ExecutionInput input = ExecutionInput
                .newExecutionInput(query)
                .variables(variables)
                .build();
        ExecutionResult executionResult = graphqlSource.getGraphQL().execute(input);

        assert executionResult != null;
        assert executionResult.getErrors().size() == 1;
        assert Objects.equals(executionResult.getErrors().get(0).getMessage(), "Exception while fetching data (/countrys) : mock exception");
        assert Objects.equals(executionResult.getData().toString(), "{countrys=null, capital=null}");
    }


    @Test
    public void nullQuery() {

        Map<String, Map<String, DataFetcher>> dataFetcherInfo = new HashMap<>();
        Map<String, DataFetcher> queryFieldFetchers = new HashMap<>();
        queryFieldFetchers.put("countrys", async(environment -> null));
        queryFieldFetchers.put("capital", async(environment -> {
            List<Integer> capitalIds = environment.getArgument("capitalIds");
            if (capitalIds == null) {
                return null;
            }

            return capitalIds.stream().map(id -> Collections.singletonMap("capitalName", "capital_name_" + id)).collect(Collectors.toList());
        }));
        dataFetcherInfo.put("Query", queryFieldFetchers);

        GraphQLSchema originalSchema = GraphQLSourceHolder.configGraphQLSchema("Issue92.graphql", dataFetcherInfo);
        DefaultConfig config = DefaultConfig.newConfig().build();
        GraphQLSource graphqlSource = new DefaultGraphQLSourceBuilder().wrapperConfig(config).originalSchema(originalSchema).build();

        String query = ""
                + "\n" +
                "query queryCapitalByCountryIds($coutryIds:[Int]){\n" +
                "    countrys (coutryIds: $coutryIds){\n" +
                "        capital{\n" +
                "            capitalId @fetchSource(name:\"capitalIdList\")\n" +
                "            capitalName\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    capital (capitalIds: 1)\n" +
                "    @argumentTransform(argumentName:\"capitalIds\",operateType:MAP,expression:\"capitalIdList\",dependencySources:\"capitalIdList\")\n" +
                "    {\n" +
                "        capitalId\n" +
                "        capitalName\n" +
                "    }\n" +
                "}\n";

        ParseAndValidateResult validateResult = Validator.validateQuery(query, graphqlSource.getWrappedSchema(), config);
        assert !validateResult.isFailure();

        HashMap<String, Object> variables = new LinkedHashMap<>();
        variables.put("coutryIds", Arrays.asList(1, 2, 3));
        ExecutionInput input = ExecutionInput
                .newExecutionInput(query)
                .variables(variables)
                .build();
        ExecutionResult executionResult = graphqlSource.getGraphQL().execute(input);

        assert executionResult != null;
        assert executionResult.getErrors() == null || executionResult.getErrors().isEmpty();
        Map<String, Object> data = executionResult.getData();
        assert data.get("countrys") == null;
        assert data.get("capital") == null;
    }
}
