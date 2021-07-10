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

import static calculator.util.GraphQLSourceHolder.getDefaultSchema

class MultiDependencySourcesValidationTest extends Specification {

    def wrapperConfig = ConfigImpl.newConfig().build()
    def wrappedSchema = SchemaWrapper.wrap(wrapperConfig, getDefaultSchema())

    def "dependencyMultiSources"() {
        given:
        def query = """
            query dependencyMultiSources{
                consumer{
                    userInfo{
                        userId @fetchSource(name: "userId_a")
                        name @fetchSource(name: "name_a")
                    }
                    
                    userInfoList(userIds: 1)
                    @argumentTransform(argumentName: "userIds",dependencySources: ["userId_a","name_a"],operateType: MAP,expression: "ignored(userId_a,name_a)")
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
        validateResult.errors.size() == 0
    }


    def "dependencySingleSources"() {
        given:
        def query = """
            query dependencySingleSources{
                consumer{
                    userInfo{
                        userId @fetchSource(name: "userId_a")
                    }
            
                    userInfoList(userIds: 1)
                    @argumentTransform(argumentName: "userIds",dependencySources: "userId_a",operateType: MAP,expression: "ignored(userId_a)")
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
        validateResult.errors.size() == 0
    }

}
