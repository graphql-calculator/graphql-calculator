package calculator.config;

import graphql.schema.GraphQLDirective;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 默认配置实现
 */
public class ConfigImpl implements Config {

    private Set<GraphQLDirective> directives;

    private ConfigImpl(Set<GraphQLDirective> directives) {
        Objects.requireNonNull(directives);
        this.directives = directives;
    }

    @Override
    public Set<GraphQLDirective> getDirectives() {
        return directives;
    }

    public static Builder newConfig() {
        return new Builder();
    }

    public static class Builder {
        private Set<GraphQLDirective> directives = new HashSet<>();

        public Builder directive(GraphQLDirective directive) {
            Objects.requireNonNull(directive);
            this.directives.add(directive);
            return this;
        }

        public ConfigImpl build() {
            return new ConfigImpl(directives);
        }
    }
}
