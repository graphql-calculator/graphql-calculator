package calculator.engine.cache;

import graphql.ExecutionResult;

public interface GraphQLErrorsHandle<T, V, R extends ExecutionResult> {

    /**
     * 当有错误的时候，调用到这里
     *
     * @param key val
     * @param result val
     * @return val
     */
    V handle(T key, R result);
}
