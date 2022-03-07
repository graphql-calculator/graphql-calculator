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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Internal
public class DecoratorComposite implements Decorator {

    private final List<Decorator> decorators = new ArrayList<>();

    private final Map<String, Decorator> DECORATOR_CACHE = new ConcurrentHashMap<>(256);

    public void addStrategy(Decorator decorator){
        Objects.requireNonNull(decorator, "decorator can not be null.");
        decorators.add(decorator);
    }

    @Override
    public boolean supportDirective(Directive directive, WrapperEnvironment environment) {
        return getDecorator(directive,environment) != null;
    }


    @SuppressWarnings("ConstantConditions")
    public DataFetcher<?> decorate(Directive directive, WrapperEnvironment environment) {
        Decorator decorator = getDecorator(directive, environment);
        return decorator.decorate(directive, environment);
    }


    private Decorator getDecorator(Directive directive, WrapperEnvironment environment) {
        String directiveName = directive.getName();
        Decorator result = DECORATOR_CACHE.get(directiveName);
        if (result != null) {
            return result;
        }

        for (Decorator decorator : decorators) {
            if (decorator.supportDirective(directive, environment)) {
                DECORATOR_CACHE.put(directiveName, decorator);
                return decorator;
            }
        }

        return null;
    }
}
