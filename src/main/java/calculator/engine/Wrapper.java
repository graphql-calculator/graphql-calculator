package calculator.engine;

import calculator.WrapperSchemaException;
import calculator.config.Config;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLSchema;

import java.util.Set;

import static calculator.engine.CalculateDirectives.calDirectiveByName;
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
        for (GraphQLDirective calDirective : config.getDirectives()) {
            wrappedSchemaBuilder = wrappedSchemaBuilder.additionalDirective(calDirective);
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
        Set<String> schemaDirectivesName = existingSchema.getDirectives().stream().map(GraphQLDirective::getName).collect(toSet());

        for (GraphQLDirective calDir : config.getDirectives()) {
            if (calDir != calDirectiveByName.get(calDir.getName())) {
                String errorMsg = String.format("directive named %s is not defined in cal engine.", calDir.getName());
                throw new WrapperSchemaException(errorMsg);
            }

            String dirName = calDir.getName();
            if (schemaDirectivesName.contains(dirName)) {
                String errorMsg = String.format("directive named %s is already exist in schema.", dirName);
                throw new WrapperSchemaException(errorMsg);
            }
        }
    }
}
