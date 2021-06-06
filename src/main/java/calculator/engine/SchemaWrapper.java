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
import calculator.exception.WrapperSchemaException;
import com.googlecode.aviator.runtime.type.AviatorFunction;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLSchema;

import java.util.List;
import java.util.Set;

import static calculator.engine.metadata.Directives.PARAM_TRANSFORM_TYPE;
import static calculator.engine.metadata.Directives.FILTER;
import static calculator.engine.metadata.Directives.LINK;
import static calculator.engine.metadata.Directives.MAP;
import static calculator.engine.metadata.Directives.MOCK;
import static calculator.engine.metadata.Directives.NODE;
import static calculator.engine.metadata.Directives.SKIP_BY;
import static calculator.engine.metadata.Directives.SORT;
import static calculator.engine.metadata.Directives.getCalDirectiveByName;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * 将已有的schema封装为具有运行时计算行为的schema。
 */
public class SchemaWrapper {

    /**
     * 包装schema
     *
     * @param config         使用的配置
     * @param existingSchema 已有的graphqlSchema
     * @return 包装后的schema
     */
    public static GraphQLSchema wrap(Config config, GraphQLSchema existingSchema) {
        check(config, existingSchema);

        GraphQLSchema.Builder wrappedSchemaBuilder = GraphQLSchema.newSchema(existingSchema);

        // add calculator directives to schema
        for (GraphQLDirective calDirective : getCalDirectiveByName().values()) {
            wrappedSchemaBuilder.additionalDirective(calDirective);
        }

        // add calculator type to schema
        // 将配置中的指令放到schema中
        wrappedSchemaBuilder = wrappedSchemaBuilder.additionalDirective(SKIP_BY);
        wrappedSchemaBuilder = wrappedSchemaBuilder.additionalDirective(MOCK);
        wrappedSchemaBuilder = wrappedSchemaBuilder.additionalDirective(FILTER);
        wrappedSchemaBuilder = wrappedSchemaBuilder.additionalDirective(MAP);
        wrappedSchemaBuilder = wrappedSchemaBuilder.additionalDirective(SORT);
        wrappedSchemaBuilder = wrappedSchemaBuilder.additionalDirective(NODE);
        wrappedSchemaBuilder = wrappedSchemaBuilder.additionalDirective(LINK);
        wrappedSchemaBuilder.additionalType(PARAM_TRANSFORM_TYPE);

        for (AviatorFunction function : config.functions()) {
            config.getAviatorEvaluator().addFunction(function);
        }

        return wrappedSchemaBuilder.build();
    }

    /**
     * 检查
     * 1. 指定的指令是否是 计算指令；
     * 2. schema中是否有已经有同名的指令；
     */
    private static void check(Config config, GraphQLSchema existingSchema) {
        Set<String> schemaDirsName = existingSchema.getDirectives().stream().map(GraphQLDirective::getName).collect(toSet());

        List<String> duplicateDir = getCalDirectiveByName().keySet().stream().filter(schemaDirsName::contains).collect(toList());

        if (!duplicateDir.isEmpty()) {
            String errorMsg = String.format("directive named %s is already exist in schema.", duplicateDir);
            throw new WrapperSchemaException(errorMsg);
        }

        List<String> duplicateFuncNames = config.functions().stream()
                .filter(function ->
                        config.getAviatorEvaluator().containsFunction(function.getName())
                )
                .map(AviatorFunction::getName).collect(toList());

        if (!duplicateFuncNames.isEmpty()) {
            String errorMsg = String.format("function named %s is already exist in Aviator Engine or engineFunc.", duplicateFuncNames);
            throw new WrapperSchemaException(errorMsg);
        }
    }
}
