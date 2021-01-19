package calculator.engine;

import calculator.WrapperSchemaException;
import calculator.config.Config;
import calculator.engine.function.NodeFunction;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.runtime.type.AviatorFunction;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLSchema;

import java.util.List;
import java.util.Set;

import static calculator.engine.CalculateDirectives.getCalDirectiveByName;
import static calculator.engine.CalculateDirectives.filter;
import static calculator.engine.CalculateDirectives.link;
import static calculator.engine.CalculateDirectives.map;
import static calculator.engine.CalculateDirectives.mock;
import static calculator.engine.CalculateDirectives.node;
import static calculator.engine.CalculateDirectives.skipBy;
import static calculator.engine.CalculateDirectives.sortBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * 将已有的schema封装为具有运行时计算行为的schema。
 */
public class Wrapper {

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

        // 将配置中的指令放到schema中
        wrappedSchemaBuilder = wrappedSchemaBuilder.additionalDirective(skipBy);
        wrappedSchemaBuilder = wrappedSchemaBuilder.additionalDirective(mock);
        wrappedSchemaBuilder = wrappedSchemaBuilder.additionalDirective(filter);
        wrappedSchemaBuilder = wrappedSchemaBuilder.additionalDirective(map);
        wrappedSchemaBuilder = wrappedSchemaBuilder.additionalDirective(sortBy);

        if (config.isScheduleEnable()) {
            wrappedSchemaBuilder = wrappedSchemaBuilder.additionalDirective(node);
            wrappedSchemaBuilder = wrappedSchemaBuilder.additionalDirective(link);
            config.getAviatorEvaluator().addFunction(new NodeFunction());
        }

        for (AviatorFunction function : config.calFunctions()) {
            config.getAviatorEvaluator().addFunction(function);
        }

        return wrappedSchemaBuilder.build();
    }

    /**
     * 检查
     * 1. 指定的指令是否是 计算指令；
     * 2. schema中是否有已经有同名的指令；
     *
     * @param config
     * @param existingSchema
     */
    private static void check(Config config, GraphQLSchema existingSchema) {
        Set<String> schemaDirsName = existingSchema.getDirectives().stream().map(GraphQLDirective::getName).collect(toSet());

        List<String> duplicateDir = getCalDirectiveByName().keySet().stream().filter(schemaDirsName::contains).collect(toList());

        if (!duplicateDir.isEmpty()) {
            String errorMsg = String.format("directive named %s is already exist in schema.", duplicateDir);
            throw new WrapperSchemaException(errorMsg);
        }

        /**
         * 使用的是全局唯一执行器 {@link AviatorEvaluator.StaticHolder}
         */
        Set<String> engineFunctions = config.getAviatorEvaluator().getFuncMap().keySet();
        List<AviatorFunction> duplicateFunc = config.calFunctions().stream().filter(engineFunctions::contains).collect(toList());
        if (!duplicateDir.isEmpty()) {
            String errorMsg = String.format("function named %s is already exist in Aviator Engine.", duplicateFunc);
            throw new WrapperSchemaException(errorMsg);
        }


        // @node 和 @link
        if (config.isScheduleEnable()) {
            // todo 应该也不需要做什么特殊的校验
        }
    }
}
