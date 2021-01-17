package calculator.config;

import graphql.schema.GraphQLDirective;

import java.util.Set;

public interface Config {

    /**
     * @return 使用的指令列表
     */
    Set<GraphQLDirective> getDirectives();

}
