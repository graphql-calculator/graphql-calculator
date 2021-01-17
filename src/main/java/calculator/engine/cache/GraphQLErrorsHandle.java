package calculator.engine.cache;

import graphql.ExecutionResult;

public interface GraphQLErrorsHandle<T, V, R extends ExecutionResult> {

    /**
     * 当有错误的时候，调用到这里
     *
     * @param key
     * @param result
     */
    V handle(T key, R result);
}
