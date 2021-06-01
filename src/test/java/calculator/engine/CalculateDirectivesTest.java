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

import calculator.config.Config;
import calculator.engine.annotation.Beta;
import calculator.validate.Validator;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.ParseAndValidateResult;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static calculator.engine.CalculateSchemaHolder.getCalSchema;
import static calculator.engine.TestUtil.getFromNestedMap;
import static calculator.engine.ExecutionEngine.newInstance;
import static com.googlecode.aviator.AviatorEvaluator.execute;


public class CalculateDirectivesTest {

    private static final GraphQLSchema wrappedSchema;
    private static final GraphQL graphQL;

    static {
        wrappedSchema = SchemaWrapper.wrap(Config.DEFAULT_CONFIG, getCalSchema());
        graphQL = GraphQL.newGraphQL(wrappedSchema)
                .instrumentation(newInstance(Config.DEFAULT_CONFIG))
                .build();
    }

    @Test
    public void skipByTest() {
        String query = ""
                + "query($userId:Int) { "
                + "    userInfo(id: $userId) @skipBy(exp:\"id < 0\"){"
                + "        userId "
                + "        name"
                + "    }"
                + "}";

        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
        assert !validateResult.isFailure();

        ExecutionInput skipInput = ExecutionInput
                .newExecutionInput(query)
                .variables(Collections.singletonMap("userId", -1))
                .build();
        ExecutionResult skipRes = graphQL.execute(skipInput);
        assert ((Map<String, Object>) skipRes.getData()).get("userInfo") == null;

        ExecutionInput normalInput = ExecutionInput
                .newExecutionInput(query)
                .variables(Collections.singletonMap("userId", 11))
                .build();
        ExecutionResult invokeRes = graphQL.execute(normalInput);
        assert ((Map<String, Object>) invokeRes.getData()).get("userInfo") != null;
    }


    @Test
    public void mockTest() {
        String query = "query{\n" +
                "    userInfo(id:1){\n" +
                "        email @mock(value:\"dugk@foxmail.com\")\n" +
                "    }\n" +
                "}";
        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
        assert !validateResult.isFailure();

        ExecutionResult filterRes = graphQL.execute(query);
        assert filterRes.getErrors().isEmpty();
        assert Objects.equals(getFromNestedMap(filterRes.getData(), "userInfo.email"), "dugk@foxmail.com");
    }


    @Test
    public void mapTest() {
        String query = "query {\n" +
                "    userInfo(id:5){\n" +
                "        email\n" +
                "        netName: email @map(mapper:\"'netName:' + email\")\n" +
                "    }\n" +
                "}";

        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
        assert !validateResult.isFailure();

        ExecutionResult mapResult = graphQL.execute(query);
        assert mapResult != null;
        assert mapResult.getErrors().isEmpty();
        assert Objects.equals(getFromNestedMap(mapResult.getData(), "userInfo.email"), "5dugk@foxmail.com");
        assert Objects.equals(getFromNestedMap(mapResult.getData(), "userInfo.netName"), "netName:5dugk@foxmail.com");
    }

    @Test
    public void filterTest() {
        String query = "query {\n" +
                "    couponList(ids:[1,2,3,4]) @filter(predicate:\"couponId>=2\"){\n" +
                "        couponId\n" +
                "        price\n" +
                "        limitation \n" +
                "    }  \n" +
                "}";

        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
        assert !validateResult.isFailure();

        ExecutionResult result = graphQL.execute(query);
        assert result != null;
        assert result.getErrors().isEmpty();
        assert ((Map<String, List>) result.getData()).get("couponList").size() == 3;

    }

    @Test
    public void sortByDirective() {
        String query = "query {\n" +
                "    itemList(ids:[3,2,1,4,5]) @sortBy(key:\"itemId\"){\n" +
                "        itemId\n" +
                "        name\n" +
                "    }\n" +
                "}";

        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
        assert !validateResult.isFailure();

        ExecutionResult result = graphQL.execute(query);
        assert result != null;
        assert result.getErrors().isEmpty();
        assert Objects.equals(execute("seq.get(seq.get(itemList,0),'itemId')", result.getData()), 1);
        assert Objects.equals(execute("seq.get(seq.get(itemList,1),'itemId')", result.getData()), 2);
        assert Objects.equals(execute("seq.get(seq.get(itemList,2),'itemId')", result.getData()), 3);
        assert Objects.equals(execute("seq.get(seq.get(itemList,3),'itemId')", result.getData()), 4);
        assert Objects.equals(execute("seq.get(seq.get(itemList,4),'itemId')", result.getData()), 5);
    }


    @Test
    public void scheduleTest() {
        String query = ""
                + "query($userId:Int){\n" +
                "    userInfo(id:$userId){\n" +
                "        age\n" +
                "        name\n" +
                "        preferredItemIdList @node(name:\"itemIds\")\n" +
                "    }\n" +
                "\n" +
                "    itemList(ids:1) @link(argument:\"ids\",node:\"itemIds\"){\n" +
                "        itemId\n" +
                "        name\n" +
                "        salePrice\n" +
                "        withCouponIdList\n" +
                "    }\n" +
                "}";

        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
        assert !validateResult.isFailure();

        Map<String, Object> variables = new HashMap<>();
        variables.put("userId", 1);
        ExecutionInput input = ExecutionInput.newExecutionInput(query).variables(variables).build();
        ExecutionResult result = graphQL.execute(input);
        assert result != null;
        assert result.getErrors().isEmpty();
        assert Objects.equals(execute("seq.get(userInfo,'preferredItemIdList')", result.getData()), Arrays.asList(1, 2, 3));
        assert Objects.equals(execute("seq.get(seq.get(itemList,0),'itemId')", result.getData()), 1);
        assert Objects.equals(execute("seq.get(seq.get(itemList,1),'itemId')", result.getData()), 2);
        assert Objects.equals(execute("seq.get(seq.get(itemList,2),'itemId')", result.getData()), 3);

    }


    @Test
    public void scheduleAndComputeTest() {
        String query = ""
                + "query($userId:Int){\n" +
                "    userInfo(id:$userId){\n" +
                "        age\n" +
                "        name\n" +
                "        preferredItemIdList @node(name:\"itemIds\")\n" +
                "        acquiredCouponIdList @node(name:\"couponIds\")\n" +
                "    }\n" +
                "\n" +
                "    itemList(ids:1) @link(argument:\"ids\",node:\"itemIds\"){\n" +
                "        itemId\n" +
                "        name\n" +
                "        salePrice\n" +
                "        withCouponIdList\n" +
                "    }\n" +
                "\n" +
                "    couponList(ids:1) @link(argument:\"ids\",node:\"couponIds\"){\n" +
                "        couponId\n" +
                "        price\n" +
                "        changedPrice: price @map(mapper: \"price +1\")\n" +
                "    }\n" +
                "}";

        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
        assert !validateResult.isFailure();

        Map<String, Object> variables = new HashMap<>();
        variables.put("userId", 1);
        ExecutionInput input = ExecutionInput.newExecutionInput(query).variables(variables).build();
        ExecutionResult result = graphQL.execute(input);

        assert result != null;
        assert result.getErrors().isEmpty();
        assert Objects.equals(execute("seq.get(userInfo,'preferredItemIdList')", result.getData()), Arrays.asList(1, 2, 3));
        assert Objects.equals(execute("seq.get(seq.get(itemList,0),'itemId')", result.getData()), 1);
        assert Objects.equals(execute("seq.get(seq.get(itemList,1),'itemId')", result.getData()), 2);
        assert Objects.equals(execute("seq.get(seq.get(itemList,2),'itemId')", result.getData()), 3);

        assert Objects.equals(execute("seq.get(userInfo,'acquiredCouponIdList')", result.getData()), Arrays.asList(10, 11, 12));
        assert Objects.equals(execute("seq.get(seq.get(couponList,0),'couponId')", result.getData()), 10);
        assert Objects.equals(execute("seq.get(seq.get(couponList,1),'couponId')", result.getData()), 11);
        assert Objects.equals(execute("seq.get(seq.get(couponList,2),'couponId')", result.getData()), 12);
    }

    @Test
    public void nestedScheduleTest() {
        String query = "" +
                "query($userIds: [Int]){\n" +
                "    itemList(ids: 1)@link(argument:\"ids\",node:\"itemIds\"){\n" +
                "        itemId\n" +
                "        name\n" +
                "    }\n" +
                "    userInfoList(ids:$userIds){\n" +
                "        userId  \n" +
                "        name\n" +
                "        favoriteItemId @node(name:\"itemIds\")\n" +
                "    }\n" +
                "}";

        ExecutionInput input = ExecutionInput.newExecutionInput(query).variables(Collections.singletonMap("userIds", Arrays.asList(1, 2, 3))).build();
        ExecutionResult result = graphQL.execute(input);
        assert result != null;
        assert result.getErrors().isEmpty();
        assert Objects.equals(execute("seq.get(seq.get(userInfoList,0),'userId')", result.getData()), 1);
        assert Objects.equals(execute("seq.get(seq.get(userInfoList,1),'userId')", result.getData()), 2);
        assert Objects.equals(execute("seq.get(seq.get(userInfoList,2),'userId')", result.getData()), 3);

        assert ((Map<String, List>) result.getData()).get("itemList").size() == 3;
        assert Objects.equals(execute("seq.get(seq.get(itemList,0),'itemId')", result.getData()), 2);
        assert Objects.equals(execute("seq.get(seq.get(itemList,1),'itemId')", result.getData()), 4);
        assert Objects.equals(execute("seq.get(seq.get(itemList,2),'itemId')", result.getData()), 6);
    }

    @Beta
    @Test
    public void testNodeFunction() {
        String query = "" +
                "query ($itemIds:[Int]){\n" +
                "    itemList(ids: $itemIds){\n" +
                "        itemId\n" +
                "        name\n" +
                "        stockAmount @map(mapper: \" seq.get(" +
                "                                           seq.get(" +
                "                                                   toMap(getByNode('stockInfoList'),'itemId')" +
                "                                           ,itemId)" +
                "                                     ,'stockAmount') \")\n" +
                "    }\n" +
                "    itemStockList(ids: $itemIds) @node(name:\"stockInfoList\")\n" +
                "    {\n" +
                "        itemId\n" +
                "        stockAmount\n" +
                "    }\n" +
                "\n" +
                "}";

//        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
//        unused node: [stockInfoList].
//        assert !validateResult.isFailure();
        ExecutionInput input = ExecutionInput.newExecutionInput(query).variables(Collections.singletonMap("itemIds", Arrays.asList(1, 2, 3))).build();
        ExecutionResult result = graphQL.execute(input);

        assert result.getErrors().isEmpty();
        assert ((LinkedHashMap<String, Object>) result.getData()).size() == 2;
    }

}