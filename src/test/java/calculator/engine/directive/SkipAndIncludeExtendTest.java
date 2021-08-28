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

package calculator.engine.directive;

import calculator.config.DefaultConfig;
import calculator.graphql.GraphQLSource;
import calculator.util.GraphQLSourceHolder;
import calculator.validation.Validator;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ParseAndValidateResult;
import graphql.schema.DataFetcher;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class SkipAndIncludeExtendTest {


    @Test
    public void skipBy_case01() {
        Map<String, Map<String, DataFetcher>> dataFetcherInfoMap = GraphQLSourceHolder.defaultDataFetcherInfo();
        GraphQLSource graphQLSource = GraphQLSourceHolder.getGraphQLByDataFetcherMap(dataFetcherInfoMap);

        String query = "" +
                "query skipBy_case01($userId:Int){\n" +
                "    consumer{\n" +
                "        userInfo(userId: $userId)\n" +
                "        # the userInfo field would not be queried if 'userId>100' is true\n" +
                "        @skipBy(predicate: \"userId>100\")\n" +
                "        {\n" +
                "            userId\n" +
                "            name\n" +
                "        }\n" +
                "    }\n" +
                "}";
        ParseAndValidateResult validateResult = Validator.validateQuery(query, graphQLSource.getWrappedSchema(), DefaultConfig.newConfig().build());
        assert !validateResult.isFailure();

        ExecutionInput input = ExecutionInput.newExecutionInput(query).variables(Collections.singletonMap("userId", 3)).build();
        ExecutionResult executionResult = graphQLSource.getGraphQL().execute(input);
        assert executionResult.getErrors().isEmpty();
        assert Objects.equals(
                executionResult.getData().toString(),
                "{consumer={userInfo={userId=3, name=3_name}}}"
        );

        ExecutionInput skipInput = ExecutionInput.newExecutionInput(query).variables(Collections.singletonMap("userId", 1001)).build();
        ExecutionResult skipResult = graphQLSource.getGraphQL().execute(skipInput);
        assert skipResult.getErrors().isEmpty();
        assert Objects.equals(
                skipResult.getData().toString(),
                "{consumer={userInfo=null}}"
        );
    }


    @Test
    public void includeBy_case01() {
        Map<String, Map<String, DataFetcher>> dataFetcherInfoMap = GraphQLSourceHolder.defaultDataFetcherInfo();
        GraphQLSource graphQLSource = GraphQLSourceHolder.getGraphQLByDataFetcherMap(dataFetcherInfoMap);

        String query = "" +
                "query includeBy_case01($userId:Int){\n" +
                "    consumer{\n" +
                "        userInfo(userId: $userId)\n" +
                "        # the userInfo field will be queried if 'userId>100' is true\n" +
                "        @includeBy(predicate: \"userId>100\")\n" +
                "        {\n" +
                "            userId\n" +
                "            name\n" +
                "        }\n" +
                "    }\n" +
                "}";
        ParseAndValidateResult validateResult = Validator.validateQuery(query, graphQLSource.getWrappedSchema(), DefaultConfig.newConfig().build());
        assert !validateResult.isFailure();

        ExecutionInput input = ExecutionInput.newExecutionInput(query).variables(Collections.singletonMap("userId", 3)).build();
        ExecutionResult executionResult = graphQLSource.getGraphQL().execute(input);
        assert executionResult.getErrors().isEmpty();
        assert Objects.equals(
                executionResult.getData().toString(),
                "{consumer={userInfo=null}}"
        );

        ExecutionInput skipInput = ExecutionInput.newExecutionInput(query).variables(Collections.singletonMap("userId", 1001)).build();
        ExecutionResult skipResult = graphQLSource.getGraphQL().execute(skipInput);
        assert skipResult.getErrors().isEmpty();
        assert Objects.equals(
                skipResult.getData().toString(),
                "{consumer={userInfo={userId=1001, name=1001_name}}}"
        );
    }


    @Test
    public void simpleIncludeBy_case01() {
        Map<String, Map<String, DataFetcher>> dataFetcherInfoMap = GraphQLSourceHolder.defaultDataFetcherInfo();
        GraphQLSource graphQLSource = GraphQLSourceHolder.getGraphQLByDataFetcherMap(dataFetcherInfoMap);

        String query = "" +
                "query simpleIncludeBy_case01($userId:Int){\n" +
                "    consumer{\n" +
                "        userInfo(userId: $userId){\n" +
                "            userId\n" +
                "            name @includeBy(predicate: \"userId!=2\")\n" +
                "        }\n" +
                "    }\n" +
                "}";
        ParseAndValidateResult validateResult = Validator.validateQuery(query, graphQLSource.getWrappedSchema(), DefaultConfig.newConfig().build());
        assert !validateResult.isFailure();

        ExecutionInput input = ExecutionInput.newExecutionInput(query).variables(Collections.singletonMap("userId", 1)).build();
        ExecutionResult executionResult = graphQLSource.getGraphQL().execute(input);
        assert executionResult.getErrors().isEmpty();
        assert Objects.equals(
                executionResult.getData().toString(),
                "{consumer={userInfo={userId=1, name=1_name}}}"
        );

        ExecutionInput skipInput = ExecutionInput.newExecutionInput(query).variables(Collections.singletonMap("userId", 2)).build();
        ExecutionResult skipResult = graphQLSource.getGraphQL().execute(skipInput);
        assert skipResult.getErrors().isEmpty();
        assert Objects.equals(
                skipResult.getData().toString(),
                "{consumer={userInfo={userId=2, name=null}}}"
        );
    }

    @Test
    public void queryMoreDetail_case01() {
        GraphQLSource graphQLSource = GraphQLSourceHolder.getGraphQLByDataFetcherMap(
                GraphQLSourceHolder.defaultDataFetcherInfo()
        );

        String query = "" +
                "query queryMoreDetail_case01($userId:Int,$clientVersion:String){\n" +
                "    consumer{\n" +
                "        userInfo(userId: $userId,clientVersion: $clientVersion){\n" +
                "            userId\n" +
                "            age\n" +
                "            name\n" +
                "            email @includeBy(predicate: \"clientVersion == 'v2'\")\n" +
                "        }\n" +
                "    }\n" +
                "}";
        ParseAndValidateResult validateResult = Validator.validateQuery(query, graphQLSource.getWrappedSchema(), DefaultConfig.newConfig().build());
        assert !validateResult.isFailure();


        HashMap<String,Object> variable = new LinkedHashMap<>();
        variable.put("userId", 1);
        variable.put("clientVersion","v2");
        ExecutionInput input = ExecutionInput.newExecutionInput(query).variables(variable).build();
        ExecutionResult executionResult = graphQLSource.getGraphQL().execute(input);
        assert executionResult.getErrors().isEmpty();
        assert Objects.equals(
                executionResult.getData().toString(),
                "{consumer={userInfo={userId=1, age=10, name=1_name, email=1dugk@foxmail.com}}}"
        );


        ExecutionInput transform = input.transform(inputBuilder -> {
            variable.put("clientVersion", "v1");
            inputBuilder.variables(variable);
        });

        ExecutionResult skipResult = graphQLSource.getGraphQL().execute(transform);
        assert skipResult.getErrors().isEmpty();
        assert Objects.equals(
                skipResult.getData().toString(),
                "{consumer={userInfo={userId=1, age=10, name=1_name, email=null}}}"
        );
    }

    @Test
    public void skipByTest_inlineFragmentTest01() {
        GraphQLSource graphQLSource = GraphQLSourceHolder.getGraphQLByDataFetcherMap(
                GraphQLSourceHolder.defaultDataFetcherInfo()
        );

        String query = "" +
                "query skipByTest_inlineFragmentTest01($userId: Int) {\n" +
                "    consumer{\n" +
                "        userInfo(userId: $userId) {\n" +
                "            userId\n" +
                "            ... @skipBy(predicate: \"userId>18\") {\n" +
//                "            ... {\n" +
                "                age\n" +
                "                name\n" +
                "                email\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}";
        ParseAndValidateResult validateResult = Validator.validateQuery(query, graphQLSource.getWrappedSchema(), DefaultConfig.newConfig().build());
        assert !validateResult.isFailure();


        HashMap<String, Object> variable = new LinkedHashMap<>();
        variable.put("userId", 1);
        ExecutionInput input = ExecutionInput.newExecutionInput(query).variables(variable).build();
        ExecutionResult executionResult = graphQLSource.getGraphQL().execute(input);
        assert executionResult.getErrors().isEmpty();

        Map<String,Map<String,Object>> data = executionResult.getData();
        System.out.println(data.get("consumer").get("userInfo"));

        assert Objects.equals(
                executionResult.getData().toString(),
                "{consumer={userInfo={userId=1, age=10, name=1_name, email=1dugk@foxmail.com}}}"
        );
    }


}
