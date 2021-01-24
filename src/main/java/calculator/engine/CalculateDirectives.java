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

import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLNonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLString;
import static graphql.introspection.Introspection.DirectiveLocation.*;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD;

/**
 * todo
 * 1. 非空参数
 * 2. 自定义的指令实现一个mask接口？不要了，将这些指令收集起来是最简单的
 * 3. 校验指令；
 */
public class CalculateDirectives {

    private static final Map<String, GraphQLDirective> calDirectiveByName;

    public static Map<String, GraphQLDirective> getCalDirectiveByName() {
        return calDirectiveByName;
    }

    // skip 的升级版
    public final static GraphQLDirective skipBy = GraphQLDirective.newDirective()
            .name("skipBy")
            .description("filter the field by exp.")
            .validLocation(FIELD)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("exp")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .build();

    public final static GraphQLDirective mock = GraphQLDirective.newDirective()
            .name("mock")
            .description("cal value for this field by trigger.")
            .validLocation(FIELD)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("value")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .build();

    // 过滤list
    public final static GraphQLDirective filter = GraphQLDirective.newDirective()
            .name("filter")
            .description("filter the field by exp.")
            .validLocation(FIELD)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("predicate")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .build();

    public final static GraphQLDirective map = GraphQLDirective.newDirective()
            .name("map")
            .description("mapped the field value by exp.")
            .validLocation(FIELD)
            .argument(GraphQLArgument
                    .newArgument()
                    .name("mapper")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .build();

    public final static GraphQLDirective sortBy = GraphQLDirective.newDirective()
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


    // todo 当 node 放在嵌套的元素上时，value打平为list
    public final static GraphQLDirective node = GraphQLDirective.newDirective()
            .name("node")
            .description("cal value for this field by trigger.")
            .validLocation(FIELD)
            .argument(GraphQLArgument
                    .newArgument()
                    // 可能是基本类型、因此key是可选的
                    .name("name")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .build();

    public final static GraphQLDirective link = GraphQLDirective.newDirective()
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
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .build();


    static {
        Map<String, GraphQLDirective> tmpMap = new HashMap<>();
        tmpMap.put(skipBy.getName(), skipBy);
        tmpMap.put(map.getName(), map);
        tmpMap.put(filter.getName(), filter);
        tmpMap.put(mock.getName(), mock);
        tmpMap.put(sortBy.getName(), sortBy);
        tmpMap.put(link.getName(), link);
        tmpMap.put(node.getName(), node);
        calDirectiveByName = Collections.unmodifiableMap(tmpMap);
    }
}
