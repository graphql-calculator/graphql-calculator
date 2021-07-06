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

package calculator.engine.parser;


import calculator.config.Config;
import calculator.config.ConfigImpl;
import calculator.engine.ExecutionEngineState;
import calculator.engine.ExecutionEngineStateParser;
import calculator.util.GraphQLSourceHolder;
import calculator.engine.SchemaWrapper;
import calculator.engine.metadata.FetchSourceTask;
import calculator.validation.Validator;
import graphql.ParseAndValidateResult;
import graphql.analysis.QueryTraverser;
import graphql.com.google.common.base.Objects;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import java.util.Collections;

public class ExecutionEngineStateParserTest {

    private static final GraphQLSchema originalSchema = GraphQLSourceHolder.getSchema();
    private static final Config wrapperConfig = ConfigImpl.newConfig().build();
    private static final GraphQLSchema wrappedSchema = SchemaWrapper.wrap(wrapperConfig, originalSchema);

    @Test
    public void getItemListBindingCouponIdAndFilterUnSaleItems() {
        String query = ""
                + "query( $couponId: Int){\n" +
                "    commodity{\n" +
                "        itemList(itemIds: 1)\n" +
                "        @argumentTransform(argumentName: \"itemIds\", operateType: MAP,dependencySource: \"itemIdList\",expression: \"itemIdList\")\n" +
                "        @filter(predicate: \"onSale\")\n" +
                "        {\n" +
                "           itemId\n" +
                "            name\n" +
                "            salePrice\n" +
                "            onSale\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    marketing{\n" +
                "        coupon(couponId: $couponId){\n" +
                "            bindingItemIds\n" +
                "            @fetchSource(name: \"itemIdList\")\n" +
                "        }\n" +
                "    }\n" +
                "}";

        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig);
        assert !validateResult.isFailure();

        QueryTraverser traverser = QueryTraverser.newQueryTraverser()
                .schema(wrappedSchema)
                .document(Parser.parse(query))
                .variables(Collections.emptyMap()).build();

        ExecutionEngineStateParser stateParser = new ExecutionEngineStateParser();
        traverser.visitDepthFirst(stateParser);
        ExecutionEngineState engineState = stateParser.getExecutionEngineState();

        assert Objects.equal(engineState.getQueryTaskBySourceName().toString(),"{itemIdList=[marketing, marketing.coupon]}");
        assert Objects.equal(engineState.getTopTaskBySourceName().toString(),"{itemIdList=[marketing.coupon.bindingItemIds]}");
        assert Objects.equal(engineState.getFetchSourceTaskByPath().keySet().toString(),"[marketing, marketing.coupon, marketing.coupon.bindingItemIds]");

        FetchSourceTask bindingItemIdsTask = engineState.getFetchSourceTaskByPath().get("marketing.coupon.bindingItemIds");
        assert bindingItemIdsTask.getSourceName().equals("itemIdList");
        assert bindingItemIdsTask.isAnnotatedNode();
        assert bindingItemIdsTask.isListType();
        assert !bindingItemIdsTask.isInList();
        assert bindingItemIdsTask.isTopTask();
        assert bindingItemIdsTask.getMapperKey().equals("bindingItemIds");
    }
}
