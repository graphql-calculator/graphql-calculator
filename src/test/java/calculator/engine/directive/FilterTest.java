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

import calculator.config.ConfigImpl;
import calculator.engine.script.AviatorScriptEvaluator;
import calculator.engine.script.ListContain;
import calculator.graphql.GraphQLSource;
import calculator.util.GraphQLSourceHolder;
import calculator.validation.Validator;
import com.googlecode.aviator.AviatorEvaluator;
import graphql.ExecutionResult;
import graphql.ParseAndValidateResult;
import graphql.schema.DataFetcher;
import org.junit.Test;

import java.util.Map;
import java.util.Objects;

public class FilterTest {

    @Test
    public void filter_case01() {
        Map<String, Map<String, DataFetcher>> dataFetcherInfoMap = GraphQLSourceHolder.defaultDataFetcherInfo();
        AviatorEvaluator.addFunction(new ListContain());

        GraphQLSource graphQLSource = GraphQLSourceHolder.getGraphQLByDataFetcherMap(
                dataFetcherInfoMap,
                ConfigImpl.newConfig().scriptEvaluator(new AviatorScriptEvaluator()).build()
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
        ParseAndValidateResult validateResult = Validator.validateQuery(query, graphQLSource.getWrappedSchema(), ConfigImpl.newConfig().build());
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
                ConfigImpl.newConfig().scriptEvaluator(new AviatorScriptEvaluator()).build()
        );

        String query = "" +
                "query filterPrimitiveType_case01{\n" +
                "    marketing{\n" +
                "        coupon(couponId: 1){\n" +
                "            bindingItemIds @filter(predicate: \"ele%2 == 0\")\n" +
                "        }\n" +
                "    }\n" +
                "}";
        ParseAndValidateResult validateResult = Validator.validateQuery(query, graphQLSource.getWrappedSchema(), ConfigImpl.newConfig().build());
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
