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

import calculator.engine.annotation.Internal;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLString;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD;
import static graphql.introspection.Introspection.DirectiveLocation.FRAGMENT_SPREAD;
import static graphql.introspection.Introspection.DirectiveLocation.INLINE_FRAGMENT;

/**
 * The customized directives which to be provided to describe runtime operation, including query execution, type validation.
 * <p>
 * Details in https://spec.graphql.org/draft/#sec-Language.Directives.
 */

@Internal
public class Directives {

    private static final Map<String, GraphQLDirective> CAL_DIRECTIVE_BY_NAME;

    public static Map<String, GraphQLDirective> getCalDirectiveByName() {
        return CAL_DIRECTIVE_BY_NAME;
    }

    // directive @skipBy(expression: String!, dependencySource: String) on FIELD
    public final static GraphQLDirective SKIP_BY = GraphQLDirective.newDirective()
            .name("skipBy")
            .description("determine whether the field would be skipped by expression, taking query variable as script arguments.")
            .validLocations(FIELD, INLINE_FRAGMENT,FRAGMENT_SPREAD)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("predicate")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .build();

    // directive @includeBy(predicate: String!, dependencySources:[String!]) on FIELD
    public final static GraphQLDirective INCLUDE_BY = GraphQLDirective.newDirective()
            .name("includeBy")
            .description("determine whether the field would be queried by expression, taking query variable as script arguments.")
            .validLocations(FIELD, INLINE_FRAGMENT, FRAGMENT_SPREAD)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("predicate")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .build();

    // directive @mock(value: String!) on FIELD
    public final static GraphQLDirective MOCK = GraphQLDirective.newDirective()
            .name("mock")
            .description("reset the annotated field by '@mock', just work for primitive type. it's easily replaced by '@map(expression)'.")
            .validLocation(FIELD)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("value")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .build();

    // directive @filter(predicate: String!) on FIELD
    public final static GraphQLDirective FILTER = GraphQLDirective.newDirective()
            .name("filter")
            .description("filter the list by predicate.")
            .validLocation(FIELD)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("predicate")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .build();

    public final static GraphQLDirective DISTINCT = GraphQLDirective.newDirective()
            .name("distinct")
            .description("returns a list consisting of the distinct elements of the annotated list.")
            .validLocation(FIELD)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("comparator")
                    .description("determine whether the elements are equal, invoking 'Objects.equal' if comparator not set.")
                    .type(GraphQLString))
            .build();


    // directive @sort(key: String!,reversed: Boolean = false) on FIELD
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


    // directive @sortBy(comparator: String!, reversed: Boolean = false) on FIELD
    public final static GraphQLDirective SORT_BY = GraphQLDirective.newDirective()
            .name("sortBy")
            .description("sort the list by expression result.")
            .validLocation(FIELD)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("comparator")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .argument(GraphQLArgument
                    .newArgument()
                    .name("reversed")
                    .defaultValue(false)
                    .type(GraphQLBoolean))
            .build();

    // directive @map(mapper:String!, dependencySource:String) on FIELD
    public final static GraphQLDirective MAP = GraphQLDirective.newDirective()
            .name("map")
            .description("transform the field value by expression.")
            .validLocation(FIELD)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("mapper")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            // 用依赖的节点对结果进行处理
            .argument(GraphQLArgument
                    .newArgument()
                    .name("dependencySources")
                    .description("the fetched value which the annotated field dependency.")
                    .type(GraphQLList.list(GraphQLNonNull.nonNull(GraphQLString))))
            .build();


    // directive @fetchSource(name: String!, mapper:String) on FIELD
    public final static GraphQLDirective FETCH_SOURCE = GraphQLDirective.newDirective()
            .name("fetchSource")
            .description("hold the fetched value which can be acquired by calculator directives, the name is unique in query.")
            .validLocation(FIELD)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("name")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .argument(GraphQLArgument
                    .newArgument()
                    .name("sourceConvert")
                    .description("'sourceConvert' is used to transform the value of annotated field, "
                            + "and all the directives using this @fetchSource get the data after 'sourceConvert' processed. "
                            + "'sourceConvert' is only used to read original field value, and shouldn't modify original field value.")
                    .type(GraphQLString))
            .build();


    public enum ParamTransformType {
        MAP("map"), LIST_MAP("listMap"), FILTER("filter");

        private final String name;

        ParamTransformType(String name) {
            this.name = name;
        }
    }

    public static final GraphQLEnumType ARGUMENT_TRANSFORM_TYPE = GraphQLEnumType.newEnum()
            .name("ParamTransformType")
            .value(
                    GraphQLEnumValueDefinition.newEnumValueDefinition()
                            .name("MAP")
                            .value(ParamTransformType.MAP)
                            .description("transform the argument by expression.").build()

            )
            .value(
                    GraphQLEnumValueDefinition.newEnumValueDefinition()
                            .name("LIST_MAP")
                            .value(ParamTransformType.LIST_MAP)
                            .description("transform each element of list argument by expression.").build()
            )
            .value(
                    GraphQLEnumValueDefinition.newEnumValueDefinition()
                            .name("FILTER")
                            .value(ParamTransformType.FILTER)
                            .description("filter the argument element by expression.").build()
            ).build();

    // directive @argumentTransform(argument:String!, operaType:ParamTransformType, expression:String, dependencySource:String) repeatable on FIELD
    public final static GraphQLDirective ARGUMENT_TRANSFORM = GraphQLDirective.newDirective()
            .name("argumentTransform")
            .description("transform the argument by expression.")
            .validLocation(FIELD)
            .repeatable(true)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("argumentName")
                    .description("the argument name defined on the annotated field.")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .argument(GraphQLArgument
                    .newArgument()
                    .name("operateType")
                    .defaultValueProgrammatic(ParamTransformType.MAP)
                    .type(GraphQLNonNull.nonNull(ARGUMENT_TRANSFORM_TYPE)))
            .argument(GraphQLArgument
                    .newArgument()
                    .name("expression")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .argument(GraphQLArgument
                    .newArgument()
                    .name("dependencySources")
                    .description("the fetched value which the annotated field dependency.")
                    .type(GraphQLList.list(GraphQLNonNull.nonNull(GraphQLString))))
            .build();

    static {
        Map<String, GraphQLDirective> tmpMap = new HashMap<>();
        tmpMap.put(SKIP_BY.getName(), SKIP_BY);
        tmpMap.put(INCLUDE_BY.getName(), INCLUDE_BY);
        tmpMap.put(MOCK.getName(), MOCK);
        tmpMap.put(FILTER.getName(), FILTER);
        tmpMap.put(DISTINCT.getName(), DISTINCT);
        tmpMap.put(SORT.getName(), SORT);
        tmpMap.put(SORT_BY.getName(), SORT_BY);
        tmpMap.put(MAP.getName(), MAP);
        tmpMap.put(FETCH_SOURCE.getName(), FETCH_SOURCE);
        tmpMap.put(ARGUMENT_TRANSFORM.getName(), ARGUMENT_TRANSFORM);
        CAL_DIRECTIVE_BY_NAME = Collections.unmodifiableMap(tmpMap);
    }
}
