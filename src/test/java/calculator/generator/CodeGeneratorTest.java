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

package calculator.generator;

import calculator.util.GraphQLSourceHolder;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

public class CodeGeneratorTest {
    private static final GraphQLSchema graphQLSchema = GraphQLSourceHolder.getDefaultSchema();


    @Test
    public void TestGenerator(){
        String query  = "query nestedFetchSource_case01($itemIds:[Int]){\n" +
                "    commodityAlias: commodity{\n" +
                "        itemList(itemIds: $itemIds){\n" +
                "            skuList{\n" +
                "                itemId\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    consumer{\n" +
                "        userInfoList(userIds: 1)\n" +
                "        {\n" +
                "            name\n" +
                "        }\n" +
                "    }\n" +
                "}";
        try {
            CodeGenerator.generator(query,graphQLSchema);
        } catch (GeneratorException e) {

        }
    }

}
