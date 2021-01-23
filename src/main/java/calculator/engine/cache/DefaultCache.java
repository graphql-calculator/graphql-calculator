/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
