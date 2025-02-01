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


import calculator.config.Config;
import calculator.config.DefaultConfig;
import calculator.graphql.DefaultGraphQLSourceBuilder;
import calculator.graphql.GraphQLSource;
import calculator.util.GraphQLSourceHolder;
import calculator.engine.script.AviatorScriptEvaluator;
import calculator.validation.Validator;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ParseAndValidateResult;
import graphql.schema.GraphQLSchema;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CommonTest {

    private static final GraphQLSchema originalSchema = GraphQLSourceHolder.getDefaultSchema();

    private static final Config wrapperConfig = DefaultConfig.newConfig().scriptEvaluator(AviatorScriptEvaluator.getDefaultInstance()).build();
    private static final GraphQLSource graphqlSource = new DefaultGraphQLSourceBuilder().wrapperConfig(wrapperConfig).originalSchema(originalSchema).build();


    @Test
    public void mockTest() {
        String query = "" +
                "query mockEmail{\n" +
                "    consumer{\n" +
                "        userInfo(userId: 1){\n" +
                "            userId\n" +
                "            email @mock(value: \"mockedValue@foxmail.com\")\n" +
                "        }\n" +
                "    }\n" +
                "}";
        ParseAndValidateResult validateResult = Validator.validateQuery(query, graphqlSource.getWrappedSchema(), wrapperConfig);
        assert !validateResult.isFailure();

        ExecutionResult executionResult = graphqlSource.getGraphQL().execute(query);
        assert executionResult.getErrors().isEmpty();
        Map<String, Map<String, Map<String, Object>>> data = executionResult.getData();
        assert Objects.equals(data.get("consumer").get("userInfo").get("email"), "mockedValue@foxmail.com");
    }


    @Ignore
    public void abUserForCouponAcquire() {
        String query = "" +
                "query abUserForCouponAcquire($userId: Int, $couponId: Int,$abKey:String){\n" +
                "\n" +
                "    marketing\n" +
                "    @skipBy(predicate: \"abValue <= 3\",dependencySources: \"abValue\")\n" +
                "    {\n" +
                "        coupon(couponId: $couponId){\n" +
                "            couponId\n" +
                "            couponText\n" +
                "            price\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    toolInfo{\n" +
                "        abInfo(userId: $userId,abKey:$abKey) @fetchSource(name: \"abValue\")\n" +
                "    }\n" +
                "\n" +
                "}";
        ParseAndValidateResult validateResult = Validator.validateQuery(query, graphqlSource.getWrappedSchema(), wrapperConfig);
        assert !validateResult.isFailure();

        HashMap<String, Object> invalidVariable = new LinkedHashMap<>();
        invalidVariable.put("userId", 2);
        invalidVariable.put("couponId", 1);
        invalidVariable.put("abKey", "demoAb");
        ExecutionInput inValidInput = ExecutionInput
                .newExecutionInput(query)
                .variables(invalidVariable)
                .build();
        ExecutionResult invalidResult = graphqlSource.getGraphQL().execute(inValidInput);
        assert invalidResult.getErrors().isEmpty();
        assert ((Map<String, Object>) invalidResult.getData()).get("marketing") == null;


        HashMap<String, Object> validVariable = new LinkedHashMap<>();
        validVariable.put("userId", 6);
        validVariable.put("couponId", 1);
        validVariable.put("abKey", "demoAb");
        ExecutionInput validInput = ExecutionInput
                .newExecutionInput(query)
                .variables(validVariable)
                .build();
        ExecutionResult validResult = graphqlSource.getGraphQL().execute(validInput);
        assert validResult.getErrors().isEmpty();
        assert Objects.equals(
                ((Map<String, Map<String, Object>>) validResult.getData()).get("marketing").get("coupon").toString(),
                "{couponId=1, couponText=优惠券_1, price=13}"
        );
    }


    // 依赖的节点为null时、程序可以正常运行
    @Test
    public void sourceIsNullCase_01() {
        String query = ""
                + "query sourceIsNullCase_01 ( $couponId: Int) {\n" +
                "    commodity\n" +
                "    {\n" +
                "        itemList(itemIds: 1)\n" +
                "        @argumentTransform(argumentName: \"itemIds\", operateType: MAP,dependencySources: \"itemIdList\",expression: \"itemIdList\")\n" +
                "        {\n" +
                "            itemId\n" +
                "            name\n" +
                "            salePrice\n" +
                "            onSale\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    marketing\n" +
                "    @map(mapper: \"nil\")\n" +
                "    {\n" +
                "        coupon(couponId: $couponId){\n" +
                "            bindingItemIds\n" +
                "            @fetchSource(name: \"itemIdList\")\n" +
                "        }\n" +
                "    }\n" +
                "}";


        ParseAndValidateResult validateResult = Validator.validateQuery(query, graphqlSource.getWrappedSchema(), wrapperConfig);
        assert !validateResult.isFailure();

        ExecutionInput skipInput = ExecutionInput
                .newExecutionInput(query)
                .variables(Collections.singletonMap("couponId", 1L))
                .build();
        ExecutionResult executionResult = graphqlSource.getGraphQL().execute(skipInput);

        assert executionResult != null;
        assert executionResult.getErrors() == null || executionResult.getErrors().isEmpty();
        Map<String, Map<String, List>> data = executionResult.getData();
        assert data.get("commodity").get("itemList").isEmpty();
    }


    @Test
    public void mathType() {
        String query = "" +
                "query mathType{\n" +
                "    consumer{\n" +
                "        userInfo(userId: 1){\n" +
                "            userId\n" +
                "            email @map(mapper:\"'Test'\")\n" +
                "            account" +
                "            hair" +
                "        }\n" +
                "    }\n" +
                "}";
        ParseAndValidateResult validateResult = Validator.validateQuery(query, graphqlSource.getWrappedSchema(), wrapperConfig);
        assert !validateResult.isFailure();

        ExecutionResult executionResult = graphqlSource.getGraphQL().execute(query);
        assert executionResult.getErrors().isEmpty();
        Map<String, Map<String, Map<String, Object>>> data = executionResult.getData();
        assert Objects.equals(data.get("consumer").get("userInfo").get("email"), "Test");
    }
}