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

package calculator.engine.function;


import calculator.engine.annotation.Beta;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.googlecode.aviator.runtime.type.AviatorRuntimeJavaType;
import com.googlecode.aviator.runtime.type.AviatorString;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 将List_VO按照指定的key转换为map，重复的key则忽略。
 *
 * 示例见  calculator.directives.CalculateDirectivesTest#testNodeFunction。
 *
 * 注：传递个该函数的VO类型最好转换为Map类型，否则会执行反射代码。
 */
@Beta
public class ToMap extends AbstractFunction {

    private static final String FUNCTION_NAME = "toMap";

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    // 调用了三次
    @Override
    public AviatorObject call(Map<String, Object> env, AviatorObject listElement, AviatorObject fieldNameObj) {
        String fieldName = ((AviatorString) fieldNameObj).getLexeme(Collections.emptyMap());
        Collection<Object> list = (Collection<Object>) listElement.getValue(Collections.emptyMap());
        if (list == null || list.isEmpty()) {
            return AviatorRuntimeJavaType.valueOf(Collections.emptyMap());
        }

        Map<Object, Object> eleByKey = list.stream().collect(Collectors.toMap(
                ele -> toMap(ele).get(fieldName),
                ele -> ele,
                (v1, v2) -> v1
        ));
        return AviatorRuntimeJavaType.valueOf(eleByKey);
    }

    private Map<String, Object> toMap(Object object) {
        if (Map.class.isAssignableFrom(object.getClass())) {
            return (Map<String, Object>) object;
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
