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

import calculator.config.ConfigImpl;
import calculator.engine.script.AviatorScriptEvaluator;
import calculator.graphql.DefaultGraphQLSourceBuilder;
import calculator.graphql.GraphQLSource;
import calculator.util.GraphQLSourceHolder;
import graphql.execution.ValueUnboxer;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConfigTest {


    @Test
    public void testNormalConfig(){
        ExecutorService executorService = Executors.newCachedThreadPool();
        DefaultObjectMapper objectMapper = new DefaultObjectMapper();
        AviatorScriptEvaluator scriptEvaluator = new AviatorScriptEvaluator();

        ConfigImpl config = ConfigImpl.newConfig()
                .threadPool(executorService)
                .objectMapper(objectMapper)
                .scriptEvaluator(scriptEvaluator)
                .build();

        GraphQLSchema originalSchema = GraphQLSourceHolder.getDefaultSchema();

        ValueUnboxer valueUnboxer = originalValue -> originalValue;

        DefaultGraphQLSourceBuilder graphQLSourceBuilder = new DefaultGraphQLSourceBuilder();
        GraphQLSource ignored = graphQLSourceBuilder.wrapperConfig(config)
                .originalSchema(originalSchema)
                .graphQLTransform(builder -> builder.valueUnboxer(valueUnboxer))
                .build();

        assert config.getExecutor() == executorService;
        assert config.getObjectMapper() == objectMapper;
        assert config.getScriptEvaluator() == scriptEvaluator;
    }

}
