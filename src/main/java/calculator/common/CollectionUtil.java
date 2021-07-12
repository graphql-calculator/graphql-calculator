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
import java.util.List;
import java.util.function.Predicate;

public class CollectionUtil {

    /**
     * Get the size of collection or array.
     *
     * @param object the collection/array
     * @return the size of collection or array
     */
    public static int arraySize(Object object) {
        if (object instanceof Collection) {
            return ((Collection<?>) object).size();
        }

        if (object.getClass().isArray()) {
            return Array.getLength(object);
        }

        return 0;
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
            return;
        } else if (listOrArray.getClass().isArray()) {
            Arrays.sort((Object[]) listOrArray, comparator);
            return;
        }

        throw new IllegalArgumentException("Unsupported object type: " + listOrArray.getClass().getName());
    }


    /**
     * Just keep the element that satisfy the given predicate.
     *
     * @param listOrArray the list to be filtered
     * @param willKeep a predicate which returns {@code true} for elements to be keep
     */
    public static void filterListOrArray(Object listOrArray, Predicate<Object> willKeep) {

        if (listOrArray instanceof Collection) {
            ((Collection) listOrArray).removeIf(ele -> !willKeep.test(ele));
        }

        throw new IllegalArgumentException("Unsupported object type: " + listOrArray.getClass().getName());
    }

}
