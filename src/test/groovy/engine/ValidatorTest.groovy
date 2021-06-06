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

package engine

import calculator.config.ConfigImpl
import calculator.engine.SchemaWrapper
import calculator.validate.Validator
import spock.lang.Specification

import static calculator.engine.CalculateSchemaHolder.getCalSchema

class ValidatorTest extends Specification {

    def wrappedSchema = SchemaWrapper.wrap(ConfigImpl.newConfig().build(), getCalSchema())

    def "filter on nonList"() {
        given:
        def query = """
            query(\$itemId: Int) {
                item(id: \$itemId){
                    itemId @filter(predicate:"1==1")
                    name
                }
            }
        """

        when:
        def result = Validator.validateQuery(query, wrappedSchema)

        then:
        result.errors.size() == 1
        result.errors[0].description == "predicate must define on list type, instead @item#itemId."
    }


    def "sortBy on non exist key"() {
        given:
        def query = """
            query{
                userInfoList(ids: [1,2,3]) @sort(key: "idx"){
                    userId
                    name
                    favoriteItemId
                }
            }
        """

        when:
        def result = Validator.validateQuery(query, wrappedSchema)

        print(result.errors[0])

        then:
        result.errors.size() == 1
        result.errors[0].description == "non-exist key name on {userInfoList}."
    }

    def "sortBy on nonList"() {
        given:
        def query = """
            query(\$itemId: Int) {
                item(id: \$itemId){
                    itemId @sort(key:"id")
                    name
                }
            }
        """

        when:
        def result = Validator.validateQuery(query, wrappedSchema)

        then:
        result.errors.size() == 1
        result.errors[0].description == "sort key must define on list type, instead of {item#itemId}."
    }

    def "the node used by @link must exist"() {
        given:
        def query = """
        query(\$userIds: [Int]){
            userInfoList(ids:\$userIds){
                userId
                name
                favoriteItemId
            }
            itemList(ids: 1)@link(argument:"ids",node:"nonExist"){
                itemId
                name
            }
        }
        """

        when:
        def result = Validator.validateQuery(query, wrappedSchema)

        then:
        result.errors.size() == 1
        result.errors[0].description == "the node 'nonExist' used by {itemList} do not exist."
    }

    def "the argument which node linked must exist"() {
        given:
        def query = """
        query(\$userIds: [Int]){
            userInfoList(ids:\$userIds){
                userId
                name
                favoriteItemId  @node(name:"itemIds")
            }
            itemList(ids: 1)@link(argument:"nonExistArgument",node:"itemIds"){
                itemId
                name
            }
        }
        """

        when:
        def result = Validator.validateQuery(query, wrappedSchema)

        then:
        result.errors.size() == 1
        result.errors[0].description == "'nonExistArgument' do not exist on {itemList}."
    }


    def "node must not linked to the same argument"() {
        given:
        def query = """
            query(\$userIds: [Int]){
                userInfoList(ids:\$userIds){
                    userId
                    name 
                    favoriteItemId  @node(name:"itemId_x")
                    fId: favoriteItemId  @node(name:"itemId_y")
                }
                itemList(ids: 1)
                @link(argument:"ids",node:"itemId_x")
                @link(argument:"ids",node:"itemId_y")
                {
                    itemId
                    name
                }
            }
        """

        when:
        def result = Validator.validateQuery(query, wrappedSchema)

        then:
        result.isFailure()
        result.errors[0].description == "node must not linked to the same argument 'ids', @link defined on 'itemList' is invalid."
    }


    def "keep healthy: node must not linked to the same argument"() {
        given:
        def query = """
            query(\$userIds: [Int]){
                userInfoList(ids:\$userIds){
                    userId
                    name @node(name:"userName")
                    favoriteItemId  @node(name:"itemIds")
                }
                itemList(ids: 1, ignore:"unUsed")
                @link(argument:"ids",node:"itemIds")
                @link(argument:"ignore",node:"userName")
                {
                    itemId
                    name
                }
            }
        """

        when:
        def result = Validator.validateQuery(query, wrappedSchema)

        then:
        !result.isFailure()
    }


}
