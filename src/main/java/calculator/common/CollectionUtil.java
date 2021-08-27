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

package calculator.common;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CollectionUtil {

    /**
     * Get the size of collection or array.
     *
     * @param listOrArray the collection/array
     * @return the size of collection or array
     */
    public static int arraySize(Object listOrArray) {
        if (listOrArray == null) {
            return 0;
        }

        if (listOrArray instanceof Collection) {
            return ((Collection<?>) listOrArray).size();
        }

        if (listOrArray.getClass().isArray()) {
            return Array.getLength(listOrArray);
        }

        throw new IllegalArgumentException("Unsupported object type: " + listOrArray.getClass().getName());
    }


    /**
     * Sort the collection or array.
     *
     * @param listOrArray the collection/array
     * @param comparator  the comparator to determine the order of the collection/array
     */
    public static void sortListOrArray(Object listOrArray, Comparator<Object> comparator) {
        if (listOrArray instanceof List) {
            Collections.sort((List<Object>) listOrArray, comparator);
        } else if (listOrArray instanceof Collection) {
            List<Object> list = new ArrayList<>((Collection) listOrArray);
            Collections.sort(list, comparator);
            Collection<Object> collection = (Collection) listOrArray;
            collection.clear();
            collection.addAll(list);
        } else if (listOrArray.getClass().isArray()) {
            Arrays.sort((Object[]) listOrArray, comparator);
        } else {
            throw new IllegalArgumentException("Unsupported object type: " + listOrArray.getClass().getName());
        }
    }


    /**
     * Just keep the element that satisfy the given predicate.
     *
     * @param collection the list to be filtered
     * @param willKeep    a predicate which returns {@code true} for elements to be keep
     */
    public static void filterCollection(Collection collection, Predicate<Object> willKeep) {
        if (collection instanceof Collection) {
            collection.removeIf(ele -> !willKeep.test(ele));
        } else {
            throw new IllegalArgumentException("Unsupported object type: " + collection.getClass().getName());
        }
    }

    /**
     * Distinct the list by comparator.
     *
     * @param collection        the list will be handled
     * @param comparator        the function to determine whether the element is equal
     */
    public static void distinctCollection(Collection collection, Function<Object, Integer> comparator) {
        if (collection == null) {
            return;
        }

        Map<Integer, Object> resultValue = new LinkedHashMap(collection.size());
        for (Object element : collection) {
            Integer comparatorValue = comparator.apply(element);
            resultValue.putIfAbsent(comparatorValue, element);
        }

        collection.clear();
        collection.addAll(resultValue.values());
    }


    /**
     * Convert array or collection to List which support filter operation.
     *
     * @param listOrArray the object to be convert
     * @return the List which can be filtered
     */
    public static List<Object> arrayToList(Object listOrArray) {
        if (listOrArray == null) {
            return null;
        } else if (listOrArray instanceof List) {
            return (List) listOrArray;
        } else if (listOrArray.getClass().isArray()) {
            Object[] array = (Object[]) listOrArray;
            return Arrays.stream(array).collect(Collectors.toList());
        } else if (listOrArray instanceof Collection) {
            return new ArrayList<>((Collection<?>) listOrArray);
        } else {
            throw new IllegalArgumentException("Unsupported object type: " + listOrArray.getClass().getName());
        }
    }


    /**
     * Convert array or collection to List which support sort operation.
     *
     * @param collectionOrArray the object to be sorted
     * @return the List which can be sorted
     */
    public static Object collectionToListOrArray(Object collectionOrArray) {
        if (collectionOrArray == null) {
            return null;
        }

        if (collectionOrArray.getClass().isArray()) {
            return collectionOrArray;
        } else if (collectionOrArray instanceof List) {
            return collectionOrArray;
        } else if (collectionOrArray instanceof Collection) {
            return new ArrayList<>((List) collectionOrArray);
        } else {
            throw new IllegalArgumentException("Unsupported object type: " + collectionOrArray.getClass().getName());
        }
    }

}