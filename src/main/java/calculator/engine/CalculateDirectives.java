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

    // todo 只能用在List<SomeObject>上
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

    /**
     * todo 1. 怎样将list<Object> 中的一个字段、作为参数链接到另外一个field上
     */
    public final static GraphQLDirective node = GraphQLDirective.newDirective()
            .name("node")
            .description("cal value for this field by trigger.")
            .validLocation(FIELD)
            .argument(GraphQLArgument
                    .newArgument()
                    // 可能是基本类型、因此key是可选的
                    .name("name")
                    .type(GraphQLNonNull.nonNull(GraphQLString)))
            .argument(GraphQLArgument
                    .newArgument()
                    // 可以从当前 实体/集合 通过表达式取值
                    .name("path")
                    .type(GraphQLString))
            .build();

    /**
     * todo 1. 怎样将list<Object> 中的一个字段、作为参数链接到另外一个field上
     * <p>
     * 1. 格式是 nodeNameX:argA;nodeNameY:argB;
     * 2. 一个node可以对应到两个参数上，例如：nodeNameX:argA,nodeNameX:argA'。但是不能两个node指向统一额参数、因为不知道使用哪个node；
     */
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

    //todo 确认下是不是放到下边、就可以保证调用的时候一定执行了 static{}
    public static Map<String, GraphQLDirective> getCalDirectiveByName() {
        return calDirectiveByName;
    }
}
