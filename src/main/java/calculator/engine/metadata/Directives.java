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
package calculator.engine.metadata;

import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLNonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLString;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD;

/**
 * the customized directives which to be provided to describe runtime operation,
 *
 * including query execution, type validation.
 *
 * details in https://spec.graphql.org/draft/#sec-Language.Directives.
 */
public class Directives {

    private static final Map<String, GraphQLDirective> CAL_DIRECTIVE_BY_NAME;

    public static Map<String, GraphQLDirective> getCalDirectiveByName() {
        return CAL_DIRECTIVE_BY_NAME;
    }

    public final static GraphQLDirective SKIP_BY = GraphQLDirective.newDirective()
            .name("skipBy")
            .description("filter the field by exp.")
            .validLocation(FIELD)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("exp")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .build();

    public final static GraphQLDirective MOCK = GraphQLDirective.newDirective()
            .name("mock")
            .description("reset the annotated field by '@mock', just work for primitive type. it's easily replaced by '@map(exp)'.")
            .validLocation(FIELD)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("value")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .build();

    // 过滤list
    public final static GraphQLDirective FILTER = GraphQLDirective.newDirective()
            .name("filter")
            .description("filter the list by predicate.")
            .validLocation(FIELD)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("predicate")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .argument(GraphQLArgument
                    .newArgument()
                    .name("dependencyNode")
                    .description("the node which the annotated field dependency.")
                    .type(GraphQLString))
            .build();

    public final static GraphQLDirective MAP = GraphQLDirective.newDirective()
            .name("map")
            .description("transform the field value by exp.")
            .validLocation(FIELD)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("mapper")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .build();


    public final static GraphQLDirective SORT = GraphQLDirective.newDirective()
            .name("sort")
            .description("sort the list by specified key.")
            .validLocation(FIELD)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("key")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .argument(GraphQLArgument
                    .newArgument()
                    .name("reversed")
                    .defaultValue(false)
                    .type(GraphQLBoolean))
            .build();


    // 根据表达式进行排序
    // directive @sortBy(sortExp: String!, reversed: Boolean = false, dependencyNode: String) on FIELD

    public final static GraphQLDirective NODE = GraphQLDirective.newDirective()
            .name("node")
            .validLocation(FIELD)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("name")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .build();

    @Deprecated
    public final static GraphQLDirective LINK = GraphQLDirective.newDirective()
            .name("link")
            .description("replace argument with node value.")
            .validLocation(FIELD)
            .repeatable(true)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("node")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .argument(GraphQLArgument
                    .newArgument()
                    .name("argument")
                    .type(GraphQLString))
            .build();

    public enum ParamTransformType {
        MAP("map"), LIST_MAP("listMap"), FILTER("filter");

        String name;

        ParamTransformType(String name) {
            this.name = name;
        }
    }

    public static final GraphQLEnumType PARAM_TRANSFORM_TYPE = GraphQLEnumType.newEnum()
            .name("ParamTransformType")
            .value(
                    GraphQLEnumValueDefinition.newEnumValueDefinition()
                            .name("MAP")
                            .value(ParamTransformType.MAP)
                            .description("transform the argument by exp.").build()

            )
            .value(
                    GraphQLEnumValueDefinition.newEnumValueDefinition()
                            .name("LIST_MAP")
                            .value(ParamTransformType.LIST_MAP)
                            .description("transform each element of list argument by exp.").build()
            )
            .value(
                    GraphQLEnumValueDefinition.newEnumValueDefinition()
                            .name("FILTER")
                            .value(ParamTransformType.FILTER)
                            .description("filter the argument element by exp.").build()
            ).build();

    public final static GraphQLDirective ARGUMENT_TRANSFORM = GraphQLDirective.newDirective()
            .name("argumentTransform")
            .description("transform the argument by exp.")
            .validLocation(FIELD)
            .repeatable(true)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("argument")
                    .description("the argument name defined on the annotated field.")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .argument(GraphQLArgument
                    .newArgument()
                    .name("operateType")
                    .type(GraphQLNonNull.nonNull(PARAM_TRANSFORM_TYPE)))
            .argument(GraphQLArgument
                    .newArgument()
                    .name("exp")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .argument(GraphQLArgument
                    .newArgument()
                    .name("dependencyNode")
                    .description("the node which the annotated field dependency.")
                    .type(GraphQLString))
            .build();

    static {
        Map<String, GraphQLDirective> tmpMap = new HashMap<>();
        tmpMap.put(SKIP_BY.getName(), SKIP_BY);
        tmpMap.put(MAP.getName(), MAP);
        tmpMap.put(FILTER.getName(), FILTER);
        tmpMap.put(MOCK.getName(), MOCK);
        tmpMap.put(SORT.getName(), SORT);
        tmpMap.put(LINK.getName(), LINK);
        tmpMap.put(NODE.getName(), NODE);
        tmpMap.put(ARGUMENT_TRANSFORM.getName(), ARGUMENT_TRANSFORM);
        CAL_DIRECTIVE_BY_NAME = Collections.unmodifiableMap(tmpMap);
    }
}
