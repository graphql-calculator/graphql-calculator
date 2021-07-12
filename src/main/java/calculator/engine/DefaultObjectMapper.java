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

package calculator.engine;

import calculator.engine.annotation.Internal;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Internal
public class DefaultObjectMapper implements ObjectMapper{

    @Override
    public Object toSimpleCollection(Object object) {
        if (object == null) {
            return null;
        }

        if (Map.class.isAssignableFrom(object.getClass())) {
            return object;
        }

        if (Collection.class.isAssignableFrom(object.getClass()) || object.getClass().isArray()) {
            return object;
        }

        Map<String, Object> result = new HashMap<>();
        Field[] declaredFields = object.getClass().getDeclaredFields();
        for (Field declaredField : declaredFields) {
            declaredField.setAccessible(true);
            try {
                Object fieldValue = declaredField.get(object);
                result.put(declaredField.getName(), fieldValue);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        return result;
    }

}
