package calculator.engine.cache;

import graphql.ExecutionResult;

import java.util.HashMap;
import java.util.Map;

public class DefaultCache implements Cache<String, String, ExecutionResult> {


    Map<String, String> dataHolder = new HashMap<>();

    @Override
    public String getName() {
        return "default";
    }

    @Override
    public String get(String key, GraphQLErrorsHandle<String, String, ExecutionResult> errorsHandle) {
        if (dataHolder.containsKey(key)) {
            return dataHolder.get(key);
        }

        ExecutionResult result = load(key);
        if (result.getErrors() != null && !result.getErrors().isEmpty()) {
            String val = errorsHandle.handle(key, result);
            dataHolder.put(key,val);
            return val;
        }

        return result.getData();
    }


    @Override
    public String get(String key) {
        if (dataHolder.containsKey(key)) {
            return dataHolder.get(key);
        }

        return load(key).getData();
    }

    @Override
    public ExecutionResult load(String key) {
        return null;
    }
}
