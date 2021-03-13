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
package calculator.directives;

import calculator.TestUtil;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeRuntimeWiring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static calculator.engine.AsyncDataFetcherWithGetter.async;


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
public class CalculateSchemaHolder {

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

        List<Map<String, Object>> userInfoList = new LinkedList<>();
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
        List<Integer> ids = (List<Integer>) arguments.get("ids");

        List<Map<String, Object>> itemInfoList = new LinkedList<>();
        for (Integer id : ids) {
            Map<String, Object> itemInfo = new HashMap<>();
            itemInfo.put("itemId", id);
            itemInfo.put("name", id + "_item_name");
            itemInfo.put("salePrice", id * 20);
            // 商品绑定的消费券id
            itemInfo.put("withCouponIdList", ids);

            itemInfoList.add(itemInfo);
        }

        return itemInfoList;
    };

    private static DataFetcher itemStockListDF = environment -> {
        Map<String, Object> arguments = environment.getArguments();
        List<Integer> ids = (List<Integer>) arguments.get("ids");

        List<Map<String, Object>> stockInfoList = new LinkedList<>();
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
            synchronized (CalculateSchemaHolder.class) {
                if (calSchema == null) {
                    Map<String, DataFetcher> queryFetcher = new HashMap<>();
                    queryFetcher.put("userInfo", async(userDF));
                    queryFetcher.put("userInfoList", async(userListDF));
                    queryFetcher.put("coupon", async(couponDF));
                    queryFetcher.put("couponList", async(couponListDF));
                    queryFetcher.put("itemList", async(itemListDF));
                    queryFetcher.put("itemStockList", async(itemStockListDF));
//                    queryFetcher.put("userInfo", userDF);
//                    queryFetcher.put("userInfoList", (userListDF));
//                    queryFetcher.put("coupon", (couponDF));
//                    queryFetcher.put("couponList", (couponListDF));
//                    queryFetcher.put("itemList", (itemListDF));

                    Map<String, Map<String, DataFetcher>> dataFetcherInfo = new HashMap<>();
                    dataFetcherInfo.put("Query", queryFetcher);

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
