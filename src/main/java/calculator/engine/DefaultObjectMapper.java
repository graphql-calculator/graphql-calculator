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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Internal
public class DefaultObjectMapper implements ObjectMapper {

    @Override
    public Object toSimpleCollection(Object object) {
        if (object == null) {
            return null;
        }

        if (object instanceof Collection) {
            return toCollection((Collection) object);
        } else if (object instanceof Object[]) {
            return arrayToCollection((Object[]) object);
        } else if (object instanceof Map) {
            return toMap((Map) object);
        } else if (object instanceof Iterator) {
            return iteratorToCollection((Iterator) object);
        } else if (object instanceof Enumeration) {
            return enumerationToCollection((Enumeration) object);
        } else {
            return simpleObject(object);
        }
    }

    private Object simpleObject(Object object) {
        if (object.getClass().isPrimitive() || isWrapperType((object)) || object instanceof CharSequence) {
            return object;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        Field[] declaredFields = object.getClass().getDeclaredFields();
        for (Field declaredField : declaredFields) {
            declaredField.setAccessible(true);
            try {
                Object fieldValue = declaredField.get(object);
                Object simpleCollection = toSimpleCollection(fieldValue);
                result.put(declaredField.getName(), simpleCollection);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    private Object enumerationToCollection(Enumeration<Object> enumeration) {
        List<Object> result = new ArrayList<>();
        while (enumeration.hasMoreElements()) {
            Object object = enumeration.nextElement();
            Object toSimpleCollection = toSimpleCollection(object);
            result.add(toSimpleCollection);
        }

        return result;
    }

    private Object arrayToCollection(Object[] objectArray) {
        List<Object> result = new ArrayList<>(objectArray.length);

        for (Object object : objectArray) {
            Object toSimpleCollection = toSimpleCollection(object);
            result.add(toSimpleCollection);
        }
        return result;
    }

    private Object iteratorToCollection(Iterator<Object> iterator) {
        List<Object> result = new ArrayList<>();

        while (iterator.hasNext()) {
            Object next = iterator.next();
            Object toSimpleCollection = toSimpleCollection(next);
            result.add(toSimpleCollection);
        }
        return result;
    }


    private Collection<Object> toCollection(Collection<Object> collection) {

        List<Object> result = new ArrayList<>(collection.size());

        for (Object object : collection) {
            Object toSimpleCollection = toSimpleCollection(object);
            result.add(toSimpleCollection);
        }
        return result;
    }

    private Map<Object, Object> toMap(Map<Object, Object> map) {
        Map<Object, Object> result = new LinkedHashMap<>();

        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                return map;
            }

            String fieldName = (String) entry.getKey();
            result.put(fieldName, toSimpleCollection(entry.getValue()));
        }
        return result;
    }

    private static final Set<Class<?>> WRAPPER_TYPES = createWrapperTypes();

    private static Set<Class<?>> createWrapperTypes() {
        Set<Class<?>> wrapperTypes = new LinkedHashSet<>(8);
        wrapperTypes.add(Boolean.class);
        wrapperTypes.add(Byte.class);
        wrapperTypes.add(Character.class);
        wrapperTypes.add(Short.class);
        wrapperTypes.add(Integer.class);
        wrapperTypes.add(Long.class);
        wrapperTypes.add(Float.class);
        wrapperTypes.add(Double.class);
        return Collections.unmodifiableSet(wrapperTypes);
    }

    private boolean isWrapperType(Object object) {
        return WRAPPER_TYPES.contains(object.getClass());
    }

}
