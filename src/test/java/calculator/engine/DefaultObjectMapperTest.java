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

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("unused")
public class DefaultObjectMapperTest {

    static class DemoClass {
        private long longField;
        private DemoSubClass demoSubClass;
        private List<Long> longList;
        private List<DemoSubClass> demoSubClassList;
        private DemoSubClass[] subClassesArray;
        private Map<String, DemoSubClass> demoSubClassMap;
        private Map<String, List<DemoSubClass>> demoSubClassMapList;


        public DemoClass(long longField,
                         DemoSubClass demoSubClass,
                         List<Long> longList,
                         List<DemoSubClass> demoSubClassList,
                         DemoSubClass[] subClassesArray,
                         Map<String, DemoSubClass> demoSubClassMap,
                         Map<String, List<DemoSubClass>> demoSubClassMapList) {
            this.longField = longField;
            this.demoSubClass = demoSubClass;
            this.longList = longList;
            this.demoSubClassList = demoSubClassList;
            this.subClassesArray = subClassesArray;
            this.demoSubClassMap = demoSubClassMap;
            this.demoSubClassMapList = demoSubClassMapList;
        }

        public long getLongField() {
            return longField;
        }

        public void setLongField(long longField) {
            this.longField = longField;
        }

        public DemoSubClass getDemoSubClass() {
            return demoSubClass;
        }

        public void setDemoSubClass(DemoSubClass demoSubClass) {
            this.demoSubClass = demoSubClass;
        }

        public List<Long> getLongList() {
            return longList;
        }

        public void setLongList(List<Long> longList) {
            this.longList = longList;
        }

        public List<DemoSubClass> getDemoSubClassList() {
            return demoSubClassList;
        }

        public void setDemoSubClassList(List<DemoSubClass> demoSubClassList) {
            this.demoSubClassList = demoSubClassList;
        }

        public DemoSubClass[] getSubClassesArray() {
            return subClassesArray;
        }

        public void setSubClassesArray(DemoSubClass[] subClassesArray) {
            this.subClassesArray = subClassesArray;
        }

        public Map<String, DemoSubClass> getDemoSubClassMap() {
            return demoSubClassMap;
        }

        public void setDemoSubClassMap(Map<String, DemoSubClass> demoSubClassMap) {
            this.demoSubClassMap = demoSubClassMap;
        }

        public Map<String, List<DemoSubClass>> getDemoSubClassMapList() {
            return demoSubClassMapList;
        }

        public void setDemoSubClassMapList(Map<String, List<DemoSubClass>> demoSubClassMapList) {
            this.demoSubClassMapList = demoSubClassMapList;
        }
    }

    static class DemoSubClass {
        private String subClassField;

        public DemoSubClass(String subClassField) {
            this.subClassField = subClassField;
        }

        public String getSubClassField() {
            return subClassField;
        }

        public void setSubClassField(String subClassField) {
            this.subClassField = subClassField;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DemoSubClass that = (DemoSubClass) o;
            return Objects.equals(subClassField, that.subClassField);
        }

        @Override
        public int hashCode() {
            return Objects.hash(subClassField);
        }
    }


    @Test
    public void toSimpleCollectionTest() {
        DemoClass demoClass = new DemoClass(
                123L, new DemoSubClass("subClassFieldValue"),
                Arrays.asList(1L, 2L, 3L),
                Arrays.asList(new DemoSubClass("a"), new DemoSubClass("b")),
                new DemoSubClass[]{new DemoSubClass("aa"), new DemoSubClass("bb")},
                Collections.singletonMap("mapKey", new DemoSubClass("aaa")),
                Collections.singletonMap("mapListKey", Arrays.asList(new DemoSubClass("aaaa"), new DemoSubClass("bbbb")))
        );

        Object toSimpleCollection = new DefaultObjectMapper().toSimpleCollection(demoClass);

        assert toSimpleCollection instanceof LinkedHashMap;
        LinkedHashMap mapValue = (LinkedHashMap) toSimpleCollection;
        assert mapValue.size() == 7;
        assert mapValue.get("longField").equals(123L);
        assert ((Map) mapValue.get("demoSubClass")).get("subClassField").equals("subClassFieldValue");
        assert Objects.equals(mapValue.get("longList"),
                Arrays.asList(1L, 2L, 3L)
        );
        assert Objects.equals(
                mapValue.get("demoSubClassList"),
                Arrays.asList(Collections.singletonMap("subClassField", "a"), Collections.singletonMap("subClassField", "b"))
        );
        assert Objects.equals(
                mapValue.get("subClassesArray"),
                Arrays.asList(Collections.singletonMap("subClassField", "aa"), Collections.singletonMap("subClassField", "bb"))
        );

        assert Objects.equals(
                mapValue.get("demoSubClassMap"),
                Collections.singletonMap("mapKey", Collections.singletonMap("subClassField", "aaa"))
        );


        assert Objects.equals(
                mapValue.get("demoSubClassMapList"),
                Collections.singletonMap("mapListKey", Arrays.asList(Collections.singletonMap("subClassField", "aaaa"), Collections.singletonMap("subClassField", "bbbb")))
        );

    }

}
