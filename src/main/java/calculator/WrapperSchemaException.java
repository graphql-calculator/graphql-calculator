package calculator;

import graphql.GraphQLException;

public class WrapperSchemaException extends GraphQLException {
    public WrapperSchemaException(String message) {
        super(message);
    }
}
