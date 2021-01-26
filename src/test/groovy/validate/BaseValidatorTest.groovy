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

package validate

import calculator.config.ConfigImpl
import calculator.engine.Wrapper
import calculator.validate.CalValidation
import spock.lang.Specification

import static calculator.directives.CalculateSchemaHolder.getCalSchema

/**
 * 基本校验：
 * @skipBy 表达式不为空且合法；
 * @filter 表达式不为空且合法；
 * @filter 必须放在list节点；
 * @sortBy 必须定义在list上；
 * @sortBy 的key必须存在于子元素中；
 * @node 名称必须有效；
 * @node 名称不能重复；
 * todo 别名的判断和出错信息打印；
 *      片段的判断和出错信息打印；
 *      sortBy支持自定义函数；
 */
class BaseValidatorTest extends Specification {

    def config = ConfigImpl.newConfig().isScheduleEnabled(true).build()
    def wrappedSchema = Wrapper.wrap(config, getCalSchema())

    def "filter on nonList"() {
        given:
        def query = """
            query(\$itemId: Int) {
                item(id: \$itemId){
                    id @filter(predicate:"1==1")
                    name
                }
            }
        """
        when:
        def result = CalValidation.validateQuery(query, wrappedSchema)

        then:
        result.errors.size() == 1
        result.errors[0].description == "predicate must define on list type, instead @item#id."
    }
}
