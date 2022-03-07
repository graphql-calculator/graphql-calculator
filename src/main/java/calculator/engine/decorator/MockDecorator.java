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

package calculator.engine.decorator;

import calculator.engine.annotation.Internal;
import graphql.language.Directive;
import graphql.schema.DataFetcher;

import java.util.Objects;

import static calculator.common.CommonUtil.getArgumentFromDirective;
import static calculator.engine.metadata.Directives.MOCK;

@Internal
public class MockDecorator implements Decorator {

    @Override
    public boolean supportDirective(Directive directive, DecorateEnvironment environment) {
        return Objects.equals(MOCK.getName(), environment.getDirective().getName());
    }

    @Override
    public DataFetcher<?> decorate(Directive directive, DecorateEnvironment environment) {
        return ignore -> getArgumentFromDirective(environment.getDirective(), "value");
    }
}
