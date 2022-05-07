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

package calculator.devtools.generator;


import graphql.language.Document;
import graphql.schema.GraphQLSchema;

import java.util.List;

/**
 * NotNull信息也需要打印。
 */
public class CodeGenerator {


    // --------------- String + GraphQLSchema

    public static String generator(String query, GraphQLSchema graphQLSchema) {

        return null;
    }

    /**
     * @param query
     * @param graphQLSchema
     * @param enableComment 默认为 true，当用户不需要注释的时候会在这里找方法
     * @return
     */
    public static String generator(String query, GraphQLSchema graphQLSchema, boolean enableComment) {

        return null;
    }

    public static String generator(String query, GraphQLSchema graphQLSchema, GeneratorSettings setting) {

        return null;
    }



    // --------------- Document + GraphQLSchema

    public static String generator(Document query, GraphQLSchema graphQLSchema) {

        return null;
    }

    public static String generator(Document query, GraphQLSchema graphQLSchema, boolean enableComment) {

        return null;
    }



    public static String generator(Document query, GraphQLSchema graphQLSchema, GeneratorSettings setting) {

        return null;
    }


    // --------------- Document + List<String>


    public static String generator(Document query, List<String> schemaDef) {

        return null;
    }

    public static String generator(Document query, List<String> schemaDef, boolean enableComment) {

        return null;
    }


    public static String generator(Document query, List<String> schemaDef, GeneratorSettings setting) {

        return null;
    }


}
