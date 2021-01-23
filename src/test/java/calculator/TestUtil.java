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
package calculator;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

public class TestUtil {

    public static GraphQLSchema schemaByInputFile(String inputPath, RuntimeWiring runtimeWiring) {
        InputStream inputStream = TestUtil.class.getClassLoader().getResourceAsStream(inputPath);
        InputStreamReader inputReader = new InputStreamReader(inputStream);
        TypeDefinitionRegistry registry = new SchemaParser().parse(inputReader);
        return new SchemaGenerator().makeExecutableSchema(registry, runtimeWiring);
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

}
