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

import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.language.Directive;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static calculator.CommonTools.getAliasOrName;
import static calculator.CommonTools.getArgumentFromDirective;
import static calculator.engine.CalculateDirectives.filter;
import static calculator.engine.CalculateDirectives.map;
import static calculator.engine.CalculateDirectives.mock;
import static calculator.engine.CalculateDirectives.skipBy;
import static calculator.engine.CalculateDirectives.sortBy;
import static calculator.engine.ExpCalculator.calExp;

public class CalculateInstrumentation extends SimpleInstrumentation {

    private static final CalculateInstrumentation CAL_INSTANCE = new CalculateInstrumentation();

    public static CalculateInstrumentation getCalInstance() {
        return CAL_INSTANCE;
    }

    // 改变字段的计算行为，可以搞 filter 和 mock
    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
        List<Directive> dirOnField = parameters.getEnvironment().getField().getDirectives();
        if (!dirOnField.isEmpty()) {
            // 如果 指令就是为了动态改变 的运行时行为，那么这里就有必要直接返回
            return wrappedDataFetcher(dirOnField, parameters.getEnvironment(), dataFetcher);
        }


        return super.instrumentDataFetcher(dataFetcher, parameters);
    }

    /**
     * 对dataFetcher进行一次包装，如果有 mock、则直接返回mock值，如果有filter、则直接根据filter过滤
     * 根据出现顺序进行判断：根据顺序判断是为了提高灵活性
     *
     * @param dirOnField
     * @param environment
     * @return
     */
    private DataFetcher<?> wrappedDataFetcher(List<Directive> dirOnField, DataFetchingEnvironment environment, DataFetcher<?> defaultDF) {

        // fixme 不应该立即return、可能使用了多个呢
        Directive skip = null;
        for (Directive directive : dirOnField) {
            // skipBy
            if (Objects.equals(skipBy.getName(), directive.getName())) {
                skip = directive;
                continue;
            }

            // mock
            if (Objects.equals(mock.getName(), directive.getName())) {
                defaultDF = ignore -> getArgumentFromDirective(directive, "value");
                continue;
            }

            // map 转换
            if (Objects.equals(map.getName(), directive.getName())) {
                String mapper = getArgumentFromDirective(directive, "mapper");
                defaultDF = getMapperDF(defaultDF, mapper);
            }

            // 过滤列表
            if (Objects.equals(filter.getName(), directive.getName())) {
                String predicate = getArgumentFromDirective(directive, "predicate");
                defaultDF = getFilterDF(defaultDF, predicate);
            }

            if (Objects.equals(sortBy.getName(), directive.getName())) {
                String key = getArgumentFromDirective(directive, "key");
                // todo 其实获取不到默认值
                Boolean reversed = getArgumentFromDirective(directive, "reversed");
                defaultDF = wrapSortByDF(defaultDF, key, reversed);
            }
        }

        // skipBy放在最外层包装，因为如果skipBy后，其他逻辑也不必在执行
        if (skip != null) {
            if (Objects.equals(skipBy.getName(), skip.getName())) {
                DataFetcher finalDefaultDF = defaultDF;
                Directive finalSkip = skip;
                defaultDF = env -> {
                    Map<String, Object> arguments = environment.getArguments();
                    String exp = getArgumentFromDirective(finalSkip, "exp");
                    Boolean isSkip = (Boolean) calExp(exp, arguments);

                    if (true == isSkip) {
                        return null;
                    }
                    return finalDefaultDF.get(env);
                };
            }
        }

        return defaultDF;
    }

    // map：转换
    private DataFetcher<?> getMapperDF(DataFetcher defaultDF, String mapper) {
        return environment -> {
            /**
             * 也可能是普通值：不能用在list上、但是可以用在list的元素上
             */
            Object oriVal = defaultDF.get(environment);
            Map<String, Object> variable = environment.getSource();
            variable.put(getAliasOrName(environment.getField()), oriVal);
            return calExp(mapper, variable);
        };
    }


    /**
     * 过滤list中的元素
     */
    private DataFetcher<?> getFilterDF(DataFetcher defaultDF, String predicate) {
        return environment -> {
            // 元素必须是序列化为Map的数据
            // todo 对于 Collection<基本类型> 有没有坑
            // todo 非常重要：考虑到使用的是 AsyncDataFetcher，结果可能包装成CompletableFuture中了
            Collection<Map<String, Object>> collection = (Collection) defaultDF.get(environment);
            return collection.stream().filter(ele -> (Boolean) calExp(predicate, ele)).collect(Collectors.toList());
        };
    }

    /**
     * 按照指定key对元素进行排列
     */
    private DataFetcher<?> wrapSortByDF(DataFetcher<?> defaultDF, String key, Boolean reversed) {
        return environment -> {
            Collection<Map<String, Object>> collection = (Collection<Map<String, Object>>) defaultDF.get(environment);
            if (reversed != null && reversed) {
                return collection.stream().sorted(
                        Comparator.comparing(entry -> (Comparable) ((Map) entry).get(key)).reversed()
                ).collect(Collectors.toList());
            } else {
                return collection.stream().sorted(
                        Comparator.comparing(entry -> (Comparable) entry.get(key))
                ).collect(Collectors.toList());
            }
        };
    }

}
