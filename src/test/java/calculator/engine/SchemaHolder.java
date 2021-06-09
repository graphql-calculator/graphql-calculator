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

import static calculator.engine.TestUtil.sleepWithTry;
import static calculator.graphql.AsyncDataFetcher.async;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeRuntimeWiring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;


/**
 * 缓存：
 * 1. 某个字段级别的缓存；
 * 2. 总体层级的缓存；
 * 3. 出错时的异常处理策略。
 * <p>
 * 用户可以输入一个Map<String,CacheInterface> 指定缓存key及其具体的实现；
 * <p>
 * directive @cache(strategy:Enum,Time)
 * <p>
 * interface CacheInterface<T,R>{
 * // todo 参考 ThreadPoolExecutor 中的AbortPolicy
 * <p>
 * // todo afterExecute、terminated 等都是在ThreadPoolExecutor中的，借鉴借鉴
 * R get(T, AbortPolicy<List<Error>,R> )
 * }
 */
public class SchemaHolder {

    private static GraphQLSchema calSchema;

    private static DataFetcher userDF = environment -> {
        int id = (int) environment.getArguments().get("id");
        Map<String, Object> person = new HashMap<>();
        person.put("userId", id);
        person.put("age", id * 10);
        person.put("name", id + "_name");
        person.put("email", id + "dugk@foxmail.com");
        person.put("favoriteItemId", id * 2);
        person.put("preferredItemIdList", IntStream.range(id, id + 3).toArray());
        person.put("acquiredCouponIdList", IntStream.range(id * 10, id * 10 + 3).toArray());
        return person;
    };

    private static DataFetcher userListDF = environment -> {
        Map<String, Object> arguments = environment.getArguments();
        List<Integer> ids = (List<Integer>) arguments.get("ids");

        List<Map<String, Object>> userInfoList = new ArrayList<>();
        for (Integer id : ids) {
            Map<String, Object> person = new HashMap<>();
            person.put("userId", id);
            person.put("age", id * 10);
            person.put("name", id + "_name");
            person.put("email", id + "dugk@foxmail.com");
            person.put("favoriteItemId", id * 2);
            person.put("preferredItemIdList", IntStream.range(id, id + 3).toArray());
            person.put("acquiredCouponIdList", IntStream.range(id * 10, id * 10 + 3).toArray());
            userInfoList.add(person);
        }

        return userInfoList;
    };


    private static DataFetcher itemListDF = environment -> {
        Map<String, Object> arguments = environment.getArguments();
        List<Number> ids = (List<Number>) arguments.get("ids");

        List<Map<String, Object>> itemInfoList = new ArrayList<>();
        for (Number id : ids) {
            Map<String, Object> itemInfo = new HashMap<>();
            itemInfo.put("itemId", id);
            itemInfo.put("name", id + "_item_name");
            itemInfo.put("salePrice", id.longValue() * 20);
            itemInfo.put("withCouponIdList", ids);

            itemInfoList.add(itemInfo);
        }
        return itemInfoList;
    };

    private static DataFetcher itemStockListDF = environment -> {
        Map<String, Object> arguments = environment.getArguments();
        List<Integer> ids = (List<Integer>) arguments.get("ids");

        List<Map<String, Object>> stockInfoList = new ArrayList<>();
        for (Integer id : ids) {
            Map<String, Object> itemInfo = new HashMap<>();
            itemInfo.put("itemId", id);
            itemInfo.put("stockAmount", id * 20);
            stockInfoList.add(itemInfo);
        }
        return stockInfoList;
    };

    private static DataFetcher couponDF = environment -> {
        Map<String, Object> arguments = environment.getArguments();
        Integer id = (Integer) arguments.get("id");

        Map<String, Object> couponInfo = new HashMap<>();
        couponInfo.put("couponId", id);
        couponInfo.put("base", id * 10 + 1);
        couponInfo.put("price", id * 10);
        couponInfo.put("couponText", "面额" + id * 10 + "元");
        return couponInfo;
    };

    private static DataFetcher couponListDF = environment -> {
        Map<String, Object> arguments = environment.getArguments();
        List<Integer> ids = (List<Integer>) arguments.get("ids");

        List<Map> couponListInfo = new ArrayList<>();
        for (Integer id : ids) {
            Map<String, Object> couponInfo = new HashMap<>();
            couponInfo.put("couponId", id);
            couponInfo.put("price", id * 10);
            couponInfo.put("couponText", "面额" + id * 10 + "元");

            couponListInfo.add(couponInfo);
        }

        return couponListInfo;
    };


    public static GraphQLSchema getCalSchema() {
        if (calSchema == null) {
            synchronized (SchemaHolder.class) {
                if (calSchema == null) {
                    Map<String, Map<String, DataFetcher>> dataFetcherInfo = new HashMap<>();

                    Map<String, DataFetcher> queryFieldFetchers = new HashMap<>();
                    queryFieldFetchers.put("userInfo", async(userDF));
                    queryFieldFetchers.put("userInfoList", async(userListDF));
                    queryFieldFetchers.put("coupon", async(couponDF));
                    queryFieldFetchers.put("couponList", async(couponListDF));
                    queryFieldFetchers.put("itemList", async(itemListDF));
                    queryFieldFetchers.put("itemStockList", async(itemStockListDF));
                    dataFetcherInfo.put("Query", queryFieldFetchers);

                    Map<String, DataFetcher> itemFieldFetcher = new HashMap<>();
                    itemFieldFetcher.put("couponList", async(environment -> {
                        if(1 == 1){
                            return new RuntimeException("test");
                        }
                        List<Number> couponIds = environment.getArgumentOrDefault(
                                "couponIds", Collections.emptyList()
                        );

                        List<Map> coupons = new ArrayList<>();
                        for (Number couponId : couponIds) {
                            Map<String, Object> couponInfo = new HashMap<>();
                            couponInfo.put("couponId", couponId.longValue());
                            couponInfo.put("base", couponId.longValue() * 10 + 1);
                            couponInfo.put("price", couponId.longValue() * 10);
                            couponInfo.put("couponText", "面额" + couponId.longValue() * 10 + "元");
                            coupons.add(couponInfo);
                        }
                        // 模拟请求耗时
                        sleepWithTry(5000,5200);
                        return coupons;
                    }));
                    dataFetcherInfo.put("ItemBaseInfo", itemFieldFetcher);

                    RuntimeWiring.Builder runtimeWiring = RuntimeWiring.newRuntimeWiring();
                    for (Map.Entry<String, Map<String, DataFetcher>> entry : dataFetcherInfo.entrySet()) {
                        TypeRuntimeWiring.Builder typeWiring = TypeRuntimeWiring.newTypeWiring(entry.getKey()).dataFetchers(entry.getValue());
                        runtimeWiring.type(typeWiring);
                    }
                    calSchema = TestUtil.schemaByInputFile("eCommerce.graphqls", runtimeWiring.build());
                }
            }
        }
        return calSchema;
    }
}