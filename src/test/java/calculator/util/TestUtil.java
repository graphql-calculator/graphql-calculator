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
package calculator.util;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class TestUtil {

    public static GraphQLSchema schemaByInputFile(String inputPath, RuntimeWiring runtimeWiring) {
        InputStream inputStream = TestUtil.class.getClassLoader().getResourceAsStream(inputPath);
        InputStreamReader inputReader = new InputStreamReader(inputStream);
        TypeDefinitionRegistry registry = new SchemaParser().parse(inputReader);
        return new SchemaGenerator().makeExecutableSchema(registry, runtimeWiring);
    }

    public static GraphQLSchema schemaBySpec(String spec, RuntimeWiring runtimeWiring) {
        StringReader stringReader = new StringReader(spec);
        TypeDefinitionRegistry registry = new SchemaParser().parse(stringReader);
        return new SchemaGenerator().makeExecutableSchema(
                SchemaGenerator.Options.defaultOptions(), registry, runtimeWiring
        );
    }

    public static Object getFromNestedMap(Map map, String path) {
        String[] split = path.split("\\.");

        Object tmpRes = null;
        Map tmpMap = map;
        for (String key : split) {
            tmpRes = tmpMap.get(key);
            if (tmpRes == null) {
                return null;
            }

            if (tmpRes instanceof Map) {
                tmpMap = (Map) tmpRes;
            }
        }

        return tmpRes;
    }

    // 判断两个list是否含有相同的元素、忽略顺序
    public static boolean listsWithSameElements(List l1, List l2) {
        if (l1 == l2) {
            return true;
        }

        if (l1 == null || l2 == null) {
            return false;
        }

        if (l1.size() != l2.size()) {
            return false;
        }

        ArrayList new1List = new ArrayList(l1);
        ArrayList new2List = new ArrayList(l2);


        try {
            for (Object l1Ele : new1List) {
                if (new2List.contains(l1Ele)) {
                    // remove: Removes the first occurrence of the specified element from this list.
                    new2List.remove(l1Ele);
                } else {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }

        // 对于重复的
        return new2List.size() == 0;
    }

    public static void sleepWithTry(int low, int up) {

        try {
            int mils = new Random().nextInt(up - low) + low;
            TimeUnit.MILLISECONDS.sleep(mils);
        } catch (InterruptedException e) {
        }
    }

}
