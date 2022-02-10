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

import calculator.config.DefaultConfig
import calculator.engine.SchemaWrapper
import calculator.validation.Validator
import spock.lang.Ignore
import spock.lang.Specification

import static calculator.util.GraphQLSourceHolder.getDefaultSchema

class ValidationTest extends Specification {

    def wrapperConfig = DefaultConfig.newConfig().build()
    def wrappedSchema = SchemaWrapper.wrap(wrapperConfig, getDefaultSchema())


    def "empty expression for @skipBy"() {
        given:
        def query = """
            query{
                consumer{
                    userInfo(userId: 1)
                    @skipBy(predicate: "")
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
                    @skipBy(predicate: "12_ab")
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
        validateResult.errors[0].description == "invalid expression '12_ab' for @skipBy on {consumer.userInfo}: " +
                "Syntax error: unexpect token '_ab', maybe forget to insert ';' to complete last expression  at 2, lineNumber: 1, token : [type='variable',lexeme='_ab',index=2],\n" +
                "while parsing expression: `\n" +
                "12_ab^^^\n" +
                "`"
    }


    def "empty expression for @includeBy"() {
        given:
        def query = """
            query{
                consumer{
                    userInfo(userId: 1)
                    @includeBy(predicate: "")
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
        validateResult.errors[0].description == "the expression for @includeBy on {consumer.userInfo} can not be empty."
    }

    def "invalid expression for @includeBy"() {
        given:
        def query = """
            query{
                consumer{
                    userInfo(userId: 1)
                    @includeBy(predicate: "12_ab")
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
        validateResult.errors[0].description == "invalid expression '12_ab' for @includeBy on {consumer.userInfo}: Syntax error: unexpect token '_ab', maybe forget to insert ';' to complete last expression  at 2, lineNumber: 1, token : [type='variable',lexeme='_ab',index=2],\n" +
                "while parsing expression: `\n" +
                "12_ab^^^\n" +
                "`"
    }


    def "invalid location for @filter"() {
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
        validateResult.errors[0].description == "@filter must define on list type, instead of {consumer.userInfo}."
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
                    @sortBy(comparator: "12_ab")
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

        validateResult.errors[0].description == "invalid comparator '12_ab' for @skipBy on {consumer.userInfoList}: Syntax error: unexpect token '_ab', maybe forget to insert ';' to complete last expression  at 2, lineNumber: 1, token : [type='variable',lexeme='_ab',index=2],\n" +
                "while parsing expression: `\n" +
                "12_ab^^^\n" +
                "`"
    }

    def "invalid location for @sortBy"() {
        given:
        def query = """
            query{
                consumer{
                    userInfo(userId: 1)
                    @sortBy(comparator: "userId%2==0")
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
        validateResult.errors[0].description == "invalid expression '12_ab' for @argumentTransform on {consumer.userInfoList}: Syntax error: unexpect token '_ab', maybe forget to insert ';' to complete last expression  at 2, lineNumber: 1, token : [type='variable',lexeme='_ab',index=2],\n" +
                "while parsing expression: `\n" +
                "12_ab^^^\n" +
                "`"
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

    def "invalid sourceConvert"() {
        given:
        def query = """
            query invalid_sourceConvert (\$itemIds: [Int]){
            
                commodity{
                    itemList(itemIds: \$itemIds){
                        itemId
                        sellerId @fetchSource(name: "sellerIds",sourceConvert: "filter(nonExistValue,seq.gt(3))")
                        name
                        salePrice
                    }
                }
            
                consumer{
                    userInfoList(userIds: 1)
                    @argumentTransform(argumentName: "userIds",operateType: MAP,expression: "sellerIds", dependencySources: ["sellerIds"])
                    {
                        userId
                        age
                        name
                    }
                }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "only resultKey 'sellerId' can be used for the 'sourceConvert' of @fetchSource on {commodity.itemList.sellerId}."
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


    @Ignore
    def "dependencySources which @skipBy dependency must exist"() {
        given:
        def query = """
            query {
                consumer{
                    userInfo(userId: 1)
                    @skipBy(predicate: "userId<sellerId",dependencySources: "sellerId")
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
        validateResult.errors[0].description == "the fetchSource [sellerId] used by @skipBy on {consumer.userInfo} do not exist."
    }


    @Ignore
    def "dependencySources which @skipBy dependency has to be used"() {
        given:
        def query = """
            query {
                consumer{
                    userInfo(userId: 1)
                    @skipBy(predicate: "userId!=0",dependencySources: "sellerId")
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
        validateResult.errors[0].description == "the fetchSource [sellerId] do not used by @skipBy on {consumer.userInfo}."
    }


    @Ignore
    def "dependencySources name used by @skipBy must be different to field argument name "() {
        given:
        def query = """
            query validateNodeNameNotSameWithVariable(\$userId: Int){
                consumer{
                    userInfo(userId: \$userId)
                    @skipBy(predicate: "userId!=0",dependencySources: "userId")
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
        validateResult.errors[0].description == "the dependencySources [userId] on {consumer.userInfo} must be different to variable name [userId]."
    }


    // map 依赖的字段必须存在、必须被使用

    def "dependencySources used by @map dependency must exist"() {
        given:
        def query = """
            query {
                consumer{
                    userInfo{
                        userId
                        name @map(mapper: "concat(name,' can use ',couponText)",dependencySources: "couponText")
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
        validateResult.errors[0].description == "the fetchSource [couponText] used by @map on {consumer.userInfo.name} do not exist."
    }

    def "dependencySources which @map dependency has to be used"() {
        given:
        def query = """
                query {
                    consumer{
                        userInfo{
                            userId
                            name @map(mapper: "concat(name,' can use ')",dependencySources: "couponText")
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
        validateResult.errors[0].description == "the fetchSource [couponText] do not used by @map on {consumer.userInfo.name}."
    }


    def "validation for non-exist argument for @SortBy"() {
        given:
        def query = """
             query validationForNonExistArgumentForSortBy{
                consumer{
                    userInfoList
                    @sortBy(comparator: "nonExistKey")
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
        validateResult.errors[0].description == "non-exist argument 'nonExistKey' for @sortBy on {consumer.userInfoList}."
    }

    def "validation for non-exist argument for @filter"() {
        given:
        def query = """
             query validationForNonExistArgumentForSortBy{
                consumer{
                    userInfoList
                    @filter(predicate: "nonExistKey")
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
        validateResult.errors[0].description == "non-exist argument 'nonExistKey' for @filter on {consumer.userInfoList}."
    }

    def "only 'ele' can be used for @filter on leaf field "() {
        given:
        def query = """
            query filterPrimitiveType_case01{
                marketing{
                    coupon(couponId: 1){
                        bindingItemIds @filter(predicate: "nonExistKey%2 == 0")
                    }
                }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "only 'ele' can be used for @filter on leaf field {marketing.coupon.bindingItemIds}."
    }


    def "only 'ele' can be used for @sortBy on leaf field "() {
        given:
        def query = """
            query filterPrimitiveType_case01{
                marketing{
                    coupon(couponId: 1){
                        bindingItemIds @sortBy(comparator: "nonExistKey%2 == 0")
                    }
                }
            }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "only 'ele' can be used for @sortBy on leaf field {marketing.coupon.bindingItemIds}."
    }


    def "dependencySources used by @argumentTransform dependency must exist"() {
        given:
        def query = """
            query {
                consumer{
                    userInfoList(userIds: 1)
                    @argumentTransform(argumentName: "userIds",operateType: MAP,dependencySources: "sellerIdList", expression: "sellerIdList")
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
        validateResult.errors[0].description == "the fetchSource [sellerIdList] used by @argumentTransform on {consumer.userInfoList} do not exist."
    }

    def "dependencySources used by @argumentTransform dependency has to be used"() {
        given:
        def query = """
            query {
                consumer{
                    userInfoList(userIds: 1)
                    @argumentTransform(argumentName: "userIds",operateType: MAP,dependencySources: "sellerIdList", expression: "'[1,2,3]'")
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
        validateResult.errors[0].description == "the fetchSource [sellerIdList] do not used by @argumentTransform on {consumer.userInfoList}."
    }


    def "ancestor relationship exit in dependency path"() {
        given:
        def query = """
            query{
                consumer{
                    userInfoList(userIds: [1,2,3])
                    @argumentTransform(argumentName: "userIds", operateType: MAP, dependencySources: "sellerIdList", expression: "sellerIdList")
                    {
                        userId @fetchSource(name: "userIds")
                        name
                    }
                }
                business{
                    sellerInfoList(sellerIds: [2,3,4]){
                        sellerId
                        @fetchSource(name: "sellerIdList")
                        @map(mapper: "userIds", dependencySources: "userIds")
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
                        @argumentTransform(argumentName: "userIds", operateType: MAP, dependencySources: "sellerIdList", expression: "sellerIdList")
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
                            @map(mapper: "userIdList",dependencySources: "userIdList")
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


    def "validateDistinct_notDefineOnList"() {
        given:
        def query = """
                query validateDistinct_notdefineOnList{
                    consumer{
                        userInfo
                        @distinct(comparator: "userId")
                        {
                            userId
                        }
                    }
                }
        """

        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "@distinct must annotated on list type, instead of {consumer.userInfo}."
    }

    def "validateDistinct_argumentNotExist"() {
        given:
        def query = """
                 query validateDistinct_argumentNotExist{
                    consumer{
                        userInfoList
                        @distinct(comparator: "age")
                        {
                            userId
                        }
                    }
                }
        """
        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 1
        validateResult.errors[0].description == "non-exist argument 'age' for @distinct on {consumer.userInfoList}."
    }

    def "validateDistinct_defineOnLeafField"() {
        given:
        def query = """
            query validateDistinct_defineOnLeafField{
                marketing{
                    coupon{
                        couponId
                        bindingItemIds @distinct
                    }
                }
            }
        """
        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 0
    }

    def "validateDistinct_determineByEqual"() {
        given:
        def query = """
            query validateDistinct_determineByEqual{
                consumer{
                    userInfoList
                    @distinct
                    {
                        userId
                    }
                }
            }
        """
        when:
        def validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig)

        then:
        validateResult.errors.size() == 0
    }

}
