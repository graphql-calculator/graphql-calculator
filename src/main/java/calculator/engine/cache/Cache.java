package calculator.engine.cache;

import graphql.ExecutionResult;

public interface Cache<K, V, R extends ExecutionResult> {

    String getName();

    // 对错误进行处理
    V get(K key, GraphQLErrorsHandle<K, V, R> errorsHandle);

    // V包含错误
    V get(K key);

    // 执行一次实际的请求
    R load(K key);
}
