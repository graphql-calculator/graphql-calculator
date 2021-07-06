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

package engine.validation

import calculator.config.ConfigImpl
import calculator.engine.SchemaWrapper
import calculator.validation.Validator
import spock.lang.Specification

import static calculator.util.GraphQLSourceHolder.getSchema

class ValidationTest extends Specification {

    def wrapperConfig = ConfigImpl.newConfig().build()
    def wrappedSchema = SchemaWrapper.wrap(wrapperConfig, getSchema())


    def "empty expression for @skipBy"() {
        given:
        def query = """
            query{
                consumer{
                    userInfo(userId: 1)
                    @skipBy(expression: "")
                    {
                        userId
                        name
                    }
                }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "the expression for @skipBy on {consumer.userInfo} can not be empty."
    }


    def "invalid expression for @skipBy"() {
        given:
        def query = """
            query{
                consumer{
                    userInfo(userId: 1)
                    @skipBy(expression: "12_ab")
                    {
                        userId
                        name
                    }
                }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "invalid expression '12_ab' for @skipBy on {consumer.userInfo}."
    }


    def "invalid location for @skipBy"() {
        given:
        def query = """
            query{
                consumer{
                    userInfo(userId: 1)
                    @filter(predicate: "userId == 1")
                    {
                        userId
                        name
                    }
                }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "@filter must define on list type, instead {consumer.userInfo}."
    }


    def "empty expression for @filter"() {
        given:
        def query = """
            query{
                consumer{
                    userInfoList(userIds: [1,2,3])
                    @filter(predicate: "")
                    {
                        userId
                        name
                    }
                }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "the predicate for @filter on {consumer.userInfoList} can not be empty."
    }


    def "invalid expression for @filter"() {
        given:
        def query = """
            query{
                consumer{
                    userInfoList(userIds: [1,2,3])
                    @filter(predicate: "")
                    {
                        userId
                        name
                    }
                }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "the predicate for @filter on {consumer.userInfoList} can not be empty."
    }


    def "invalid location for @sort"() {
        given:
        def query = """
            query{
                consumer{
                    userInfo(userId: 1)
                    @sort(key: "userId")
                    {
                        userId
                        name
                    }
                }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "@sort must annotated on list type, instead of {consumer.userInfo}."
    }


    def "non-exist key for @sort"() {
        given:
        def query = """
            query{
                consumer{
                    userInfoList(userIds: [1,2,3])
                    @sort(key: "nonExistKey")
                    {
                        userId
                        name
                    }
                }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "non-exist key name 'nonExistKey' for @sort on {consumer.userInfoList}."
    }


    def "invalid expression for @sortBy"() {
        given:
        def query = """
            query{
                consumer{
                    userInfoList(userIds: [1,2,3])
                    @sortBy(expression: "12_ab")
                    {
                        userId
                        name
                    }
                }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "invalid expression '12_ab' for @skipBy on {consumer.userInfoList}."
    }

    def "invalid location for @sortBy"() {
        given:
        def query = """
            query{
                consumer{
                    userInfo(userId: 1)
                    @sortBy(expression: "userId%2==0")
                    {
                        userId
                        name
                    }
                }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "@sortBy must annotated on list type, instead of {consumer.userInfo}."
    }


    def "invalid expression for @argumentTransform"() {
        given:
        def query = """
                query{
                    consumer{
                        userInfoList(userIds: [1,2,3])
                        @argumentTransform(argumentName: "userIds",expression: "12_ab",operateType: LIST_MAP)
                        {
                            userId
                            name
                        }
                    }
                }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "invalid expression '12_ab' for @argumentTransform on {consumer.userInfoList}."
    }

    def "non-exist argument @argumentTransform"() {
        given:
        def query = """
                query{
                    consumer{
                        userInfoList(userIds: [1,2,3])
                        @argumentTransform(argumentName: "nonExistArgument",expression: "ele!=1",operateType: LIST_MAP)
                        {
                            userId
                            name
                        }
                    }
                }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "@argumentTransform on {consumer.userInfoList} use non-exist argument 'nonExistArgument'."
    }


    def "invalid location for @argumentTransform with 'LIST_MAP' operation "() {
        given:
        def query = """
            query{
                consumer{
                    userInfo(userId: 1)
                    @argumentTransform(argumentName: "userId",expression: "userId!=1",operateType: LIST_MAP)
                    {
                        userId
                        name
                    }
                }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "LIST_MAP operation for @argumentTransform can not used on basic field {consumer.userInfo}."
    }


    def "invalid location for @argumentTransform with 'FILTER' operation "() {
        given:
        def query = """
            query{
                consumer{
                    userInfo(userId: 1)
                    @argumentTransform(argumentName: "userId",expression: "userId!=1",operateType: FILTER)
                    {
                        userId
                        name
                    }
                }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "FILTER operation for @argumentTransform can not used on basic field {consumer.userInfo}."
    }

    def "duplicate source name for @fetchSource"() {
        given:
        def query = """
            query {
                consumer{
                    userInfo{
                        userId @fetchSource(name: "uSource")
                        name @fetchSource(name: "uSource")
                    }
                }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "duplicate source name 'uSource' for {consumer.userInfo.userId} and {consumer.userInfo.name}."
    }


    def "invalid source name for @fetchSource"() {
        given:
        def query = """
            query {
                consumer{
                    userInfo{
                        userId @fetchSource(name: "12uSource")
                    }
                }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "invalid source name '12uSource' for {consumer.userInfo.userId}."
    }


    def "dependencySource which @skipBy dependency must exist"() {
        given:
        def query = """
            query {
                consumer{
                    userInfo(userId: 1)
                    @skipBy(expression: "userId<sellerId",dependencySource: "sellerId")
                    {
                        userId
                        name
                    }
                }
            
            #    business{
            #        sellerInfo(sellerId: 2){
            #            sellerId @fetchSource(name: "sellerId")
            #        }
            #    }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "the fetchSource 'sellerId' used by @skipBy on {consumer.userInfo} do not exist."
    }


    def "dependencySource which @skipBy dependency has to be used"() {
        given:
        def query = """
            query {
                consumer{
                    userInfo(userId: 1)
                    @skipBy(expression: "userId!=0",dependencySource: "sellerId")
                    {
                        userId
                        name
                    }
                }
            
                    business{
                        sellerInfo(sellerId: 2){
                            sellerId @fetchSource(name: "sellerId")
                        }
                    }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "the fetchSource 'sellerId' do not used by @skipBy on {consumer.userInfo}."
    }


    def "dependencySource name used by @skipBy must be different to field argument name "() {
        given:
        def query = """
            query {
                consumer{
                    userInfo(userId: 1)
                    @skipBy(expression: "userId!=0",dependencySource: "userId")
                    {
                        userId
                        name
                    }
                }
        
                business{
                    sellerInfo(sellerId: 2){
                        sellerId @fetchSource(name: "userId")
                    }
                }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "the dependencySource name 'userId' on {consumer.userInfo} must be different to field argument name [userId]."
    }


    // map 依赖的字段必须存在、必须被使用

    def "dependencySource used by @mapper dependency must exist"() {
        given:
        def query = """
            query {
                consumer{
                    userInfo{
                        userId
                        name @mapper(expression: "concat(name,' can use ',couponText)",dependencySource: "couponText")
                    }
                }

                #marketing{
                #    coupon(couponId: 1){
                #        couponText @fetchSource(name: "couponText")
                #    }
                #}
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "the fetchSource 'couponText' used by @mapper on {consumer.userInfo.name} do not exist."
    }

    def "dependencySource which @mapper dependency has to be used"() {
        given:
        def query = """
                query {
                    consumer{
                        userInfo{
                            userId
                            name @mapper(expression: "concat(name,' can use ')",dependencySource: "couponText")
                        }
                    }
                
                    marketing{
                        coupon(couponId: 1){
                            couponText @fetchSource(name: "couponText")
                        }
                    }
                }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "the fetchSource 'couponText' do not used by @mapper on {consumer.userInfo.name}."
    }


    def "dependencySource used by @sortBy dependency must exist"() {
        given:
        def query = """
             query {
                consumer{
                    userInfoList(userIds: [1,2,3])
                    @sortBy(expression: "userId/sellerId",dependencySource: "sellerId")
                    {
                        userId
                        name
                    }
                }
            
            #    business{
            #        sellerInfo(sellerId: 2){
            #            sellerId @fetchSource(name: "sellerId")
            #        }
            #    }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "the fetchSource 'sellerId' used by @sortBy on {consumer.userInfoList} do not exist."
    }

    def "dependencySource used by @sortBy dependency has to be used"() {
        given:
        def query = """
            query {
                consumer{
                    userInfoList(userIds: [1,2,3])
                    @filter(predicate: "3>userId",dependencySource: "sellerId")
                    {
                        userId
                        name
                    }
                }
            
                business{
                    sellerInfo(sellerId: 2){
                        sellerId @fetchSource(name: "sellerId")
                    }
                }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "the fetchSource 'sellerId' do not used by @filter on {consumer.userInfoList}."
    }


    // @filter 依赖的fetchSource必须存在、不能没有被使用

    def "dependencySource used by @filter dependency must exist"() {
        given:
        def query = """
            query {
                consumer{
                    userInfoList(userIds: [1,2,3])
                    @filter(predicate: "sellerId>userId",dependencySource: "sellerId")
                    {
                        userId
                        name 
                    }
                }
                
            #    business{
            #        sellerInfo(sellerId: 2){
            #            sellerId @fetchSource(name: "sellerId")
            #        }
            #    }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "the fetchSource 'sellerId' used by @filter on {consumer.userInfoList} do not exist."
    }

    def "dependencySource used by @filter dependency has to be used"() {
        given:
        def query = """
            query {
                consumer{
                    userInfoList(userIds: [1,2,3])
                    @filter(predicate: "3>userId",dependencySource: "sellerId")
                    {
                        userId
                        name
                    }
                }
            
                business{
                    sellerInfo(sellerId: 2){
                        sellerId @fetchSource(name: "sellerId")
                    }
                }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "the fetchSource 'sellerId' do not used by @filter on {consumer.userInfoList}."
    }


    def "dependencySource used by @argumentTransform dependency must exist"() {
        given:
        def query = """
            query {
                consumer{
                    userInfoList(userIds: 1)
                    @argumentTransform(argumentName: "userIds",operateType: MAP,dependencySource: "sellerIdList", expression: "sellerIdList")
                    {
                        userId
                        name
                    }
                }
            
            #    business{
            #        sellerInfoList(sellerId: [1,2,3]){
            #            sellerId @fetchSource(name: "sellerIdList")
            #        }
            #    }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "the fetchSource 'sellerIdList' used by @argumentTransform on {consumer.userInfoList} do not exist."
    }

    def "dependencySource used by @argumentTransform dependency has to be used"() {
        given:
        def query = """
            query {
                consumer{
                    userInfoList(userIds: 1)
                    @argumentTransform(argumentName: "userIds",operateType: MAP,dependencySource: "sellerIdList", expression: "'[1,2,3]'")
                    {
                        userId
                        name
                    }
                }
            
                business{
                    sellerInfoList(sellerIds: [1,2,3]){
                        sellerId @fetchSource(name: "sellerIdList")
                    }
                }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "the fetchSource 'sellerIdList' do not used by @argumentTransform on {consumer.userInfoList}."
    }


    def "ancestor relationship exit in dependency path"() {
        given:
        def query = """
            query{
                consumer{
                    userInfoList(userIds: [1,2,3])
                    @argumentTransform(argumentName: "userIds", operateType: MAP, dependencySource: "sellerIdList", expression: "sellerIdList")
                    {
                        userId @fetchSource(name: "userIds")
                        name
                    }
                }
                business{
                    sellerInfoList(sellerIds: [2,3,4]){
                        sellerId
                        @fetchSource(name: "sellerIdList")
                        @mapper(expression: "userIds", dependencySource: "userIds")
                    }
                }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "there is an ancestor relationship between {consumer.userInfoList.userId} and {consumer.userInfoList}, " +
                "and they are in the dependency path [consumer.userInfoList, business.sellerInfoList.sellerId, consumer.userInfoList.userId]."
    }

    def "circular dependency path"() {
        given:
        def query = """
                query{
                    consumer{
                        userInfoList(userIds: [1,2,3])
                        @argumentTransform(argumentName: "userIds", operateType: MAP, dependencySource: "sellerIdList", expression: "sellerIdList")
                        @fetchSource(name: "userIdList")
                        {
                            userId
                            name
                        }
                    }
                
                    business{
                        sellerInfoList(sellerIds: [2,3,4]){
                            sellerId
                            @fetchSource(name: "sellerIdList")
                            @mapper(expression: "userIdList",dependencySource: "userIdList")
                        }
                    }
                }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 2
        validateResult.errors[0].description == "there is a circular dependency path [consumer.userInfoList, business.sellerInfoList.sellerId, consumer.userInfoList]."
        validateResult.errors[1].description == "there is a circular dependency path [business.sellerInfoList.sellerId, consumer.userInfoList, business.sellerInfoList.sellerId]."
    }




}
