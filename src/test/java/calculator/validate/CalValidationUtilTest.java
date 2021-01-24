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
package calculator.validate;

import calculator.config.ConfigImpl;
import calculator.engine.Wrapper;
import graphql.ParseAndValidateResult;
import graphql.schema.GraphQLSchema;
import org.junit.Test;


import static calculator.directives.CalculateSchemaHolder.getCalSchema;

public class CalValidationUtilTest {

    private ConfigImpl scheduleConfig = ConfigImpl.newConfig().isScheduleEnabled(true).build();


    // 验证 不能有同名的node
    // todo 解析片段
    @Test
    public void unusedNodeTest() {
        GraphQLSchema wrappedSchema = Wrapper.wrap(scheduleConfig, getCalSchema());

        String query = "query { \n" +
                "            userInfo @node(name: \"X\") {\n" +
                "                id\n" +
                "                name\n" +
                "            } \n" +
                "        }";
        ParseAndValidateResult parseAndValidateResult = CalValidation.validateQuery(
                query, wrappedSchema
        );

        assert parseAndValidateResult.getValidationErrors().size() == 1;
        assert parseAndValidateResult.getValidationErrors().get(0).getMessage().equals(
                "Validation error of type null:  unused node: [X]."
        );
    }


    @Test
    public void duplicateNodeTest() {
        GraphQLSchema wrappedSchema = Wrapper.wrap(scheduleConfig, getCalSchema());
        String query = "query($userId:Int){\n" +
                "    userInfo(id:$userId){\n" +
                "        preferredItemIdList @node(name:\"ids\")\n" +
                "        acquiredCouponIdList @node(name:\"ids\")\n" +
                "    }\n" +
                "\n" +
                "    itemList(ids: 1) @link(argument:\"ids\",node:\"ids\"){\n" +
                "        name\n" +
                "    }\n" +
                "    \n" +
                "    couponList(ids: 1) @link(argument:\"ids\",node:\"ids\"){\n" +
                "        price\n" +
                "    }\n" +
                "}";

        ParseAndValidateResult parseAndValidateResult = CalValidation.validateQuery(
                query, wrappedSchema
        );

        assert parseAndValidateResult.getValidationErrors().size() == 1;
        assert parseAndValidateResult.getValidationErrors().get(0).getMessage().equals(
                "Validation error of type null: duplicate node name 'ids' for userInfo#preferredItemIdList and userInfo#acquiredCouponIdList."
        );
    }

}
