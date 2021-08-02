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
import calculator.engine.script.AviatorScriptEvaluator;
import calculator.engine.script.ListContain;
import calculator.graphql.DefaultGraphQLSourceBuilder;
import calculator.graphql.GraphQLSource;
import calculator.util.GraphQLSourceHolder;
import calculator.validation.Validator;
import com.googlecode.aviator.AviatorEvaluator;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ParseAndValidateResult;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class FilterTest {

    private static final GraphQLSchema originalSchema = GraphQLSourceHolder.getDefaultSchema();
    private static final Config wrapperConfig = DefaultConfig.newConfig().scriptEvaluator(AviatorScriptEvaluator.getDefaultInstance()).build();
    private static final GraphQLSource graphqlSource = new DefaultGraphQLSourceBuilder().wrapperConfig(wrapperConfig).originalSchema(originalSchema).build();

    @Test
    public void simpleTest_case01() {
        String query = "" +
                "query filterUnSaleCommodity($ItemIds:[Int]){\n" +
                "    commodity{\n" +
                "        filteredItemList: itemList(itemIds: $ItemIds)\n" +
                "        @filter(predicate: \"onSale\")\n" +
                "        {\n" +
                "            itemId\n" +
                "            onSale\n" +
                "            name\n" +
                "            salePrice\n" +
                "        }\n" +
                "        \n" +
                "        allItemList : itemList(itemIds: $ItemIds)\n" +
                "        {\n" +
                "            itemId\n" +
                "            onSale\n" +
                "            name\n" +
                "            salePrice\n" +
                "        }\n" +
                "    }\n" +
                "}";
        ParseAndValidateResult validateResult = Validator.validateQuery(
                query, graphqlSource.getWrappedSchema(), DefaultConfig.newConfig().build()
        );
        assert !validateResult.isFailure();

        ExecutionInput input = ExecutionInput.newExecutionInput(query)
                .variables(Collections.singletonMap("ItemIds", Arrays.asList(1, 2, 3)))
                .build();

        ExecutionResult executionResult = graphqlSource.getGraphQL().execute(input);
        assert executionResult.getErrors().isEmpty();
        Map<String, Map<String, Object>> data = executionResult.getData();

        assert Objects.equals(
                data.get("commodity").get("filteredItemList").toString(),
                "[{itemId=1, onSale=true, name=item_name_1, salePrice=11}, {itemId=2, onSale=true, name=item_name_2, salePrice=21}]"
        );

        assert Objects.equals(
                data.get("commodity").get("allItemList").toString(),
                "[{itemId=1, onSale=true, name=item_name_1, salePrice=11}, " +
                        "{itemId=2, onSale=true, name=item_name_2, salePrice=21}, " +
                        "{itemId=3, onSale=false, name=item_name_3, salePrice=31}]"
        );
    }

    @Test
    public void filter_case01() {
        Map<String, Map<String, DataFetcher>> dataFetcherInfoMap = GraphQLSourceHolder.defaultDataFetcherInfo();
        AviatorEvaluator.addFunction(new ListContain());

        GraphQLSource graphQLSource = GraphQLSourceHolder.getGraphQLByDataFetcherMap(
                dataFetcherInfoMap,
                DefaultConfig.newConfig().scriptEvaluator(new AviatorScriptEvaluator()).build()
        );

        String query = "" +
                "query filter_case01{\n" +
                "    commodity{\n" +
                "        itemList(itemIds: [9,11,10,12])\n" +
                "        @filter(predicate: \"isContainBindingItemIds\")\n" +
                "        {\n" +
                "            isContainBindingItemIds:onSale @map(mapper: \"listContain(bindingItemIds,itemId)\",dependencySources: \"bindingItemIds\")\n" +
                "            itemId\n" +
                "            name\n" +
                "            salePrice\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    marketing{\n" +
                "        coupon(couponId: 1){\n" +
                "            bindingItemIds @fetchSource(name: \"bindingItemIds\")\n" +
                "        }\n" +
                "    }\n" +
                "}";
        ParseAndValidateResult validateResult = Validator.validateQuery(query, graphQLSource.getWrappedSchema(), DefaultConfig.newConfig().build());
        assert !validateResult.isFailure();

        ExecutionResult executionResult = graphQLSource.getGraphQL().execute(query);
        assert executionResult.getErrors().isEmpty();
        Map<String, Map<String, Object>> data = executionResult.getData();
        System.out.println(data.get("commodity").get("itemList").toString());
        assert Objects.equals(
                data.get("commodity").get("itemList").toString(),
                "[{isContainBindingItemIds=true, itemId=9, name=item_name_9, salePrice=91}, {isContainBindingItemIds=true, itemId=10, name=item_name_10, salePrice=101}]"
        );
    }

    @Test
    public void filterPrimitiveType_case01() {
        Map<String, Map<String, DataFetcher>> dataFetcherInfoMap = GraphQLSourceHolder.defaultDataFetcherInfo();
        AviatorEvaluator.addFunction(new ListContain());

        GraphQLSource graphQLSource = GraphQLSourceHolder.getGraphQLByDataFetcherMap(
                dataFetcherInfoMap,
                DefaultConfig.newConfig().scriptEvaluator(new AviatorScriptEvaluator()).build()
        );

        String query = "" +
                "query filterPrimitiveType_case01{\n" +
                "    marketing{\n" +
                "        coupon(couponId: 1){\n" +
                "            bindingItemIds @filter(predicate: \"ele%2 == 0\")\n" +
                "        }\n" +
                "    }\n" +
                "}";
        ParseAndValidateResult validateResult = Validator.validateQuery(query, graphQLSource.getWrappedSchema(), DefaultConfig.newConfig().build());
        assert !validateResult.isFailure();

        ExecutionResult executionResult = graphQLSource.getGraphQL().execute(query);
        assert executionResult.getErrors().isEmpty();
        Map<String, Map<String, Object>> data = executionResult.getData();
        assert Objects.equals(
                data.get("marketing").get("coupon").toString(),
                "{bindingItemIds=[2, 4, 6, 8, 10]}"
        );
    }

}
