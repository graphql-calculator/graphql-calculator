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
import graphql.schema.GraphQLNonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLString;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD;

public class CalculateDirectives {

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
            .description("cal value for this field by trigger.")
            .validLocation(FIELD)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("value")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .build();

    // 过滤list
    public final static GraphQLDirective FILTER = GraphQLDirective.newDirective()
            .name("filter")
            .description("filter the field by exp.")
            .validLocation(FIELD)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("predicate")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .build();

    public final static GraphQLDirective MAP = GraphQLDirective.newDirective()
            .name("map")
            .description("mapped the field value by exp.")
            .validLocation(FIELD)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("mapper")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .build();

    public final static GraphQLDirective SORT_BY = GraphQLDirective.newDirective()
            .name("sortBy")
            .description("cal value for this field by trigger.")
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


    public final static GraphQLDirective NODE = GraphQLDirective.newDirective()
            .name("node")
            .description("cal value for this field by trigger.")
            .validLocation(FIELD)
            .argument(GraphQLArgument
                    .newArgument()
                    // 可能是基本类型、因此key是可选的 TODO delete
                    .name("name")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .build();

    public final static GraphQLDirective LINK = GraphQLDirective.newDirective()
            .name("link")
            .description("cal value for this field by trigger.")
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


    static {
        Map<String, GraphQLDirective> tmpMap = new HashMap<>();
        tmpMap.put(SKIP_BY.getName(), SKIP_BY);
        tmpMap.put(MAP.getName(), MAP);
        tmpMap.put(FILTER.getName(), FILTER);
        tmpMap.put(MOCK.getName(), MOCK);
        tmpMap.put(SORT_BY.getName(), SORT_BY);
        tmpMap.put(LINK.getName(), LINK);
        tmpMap.put(NODE.getName(), NODE);
        CAL_DIRECTIVE_BY_NAME = Collections.unmodifiableMap(tmpMap);
    }
}
