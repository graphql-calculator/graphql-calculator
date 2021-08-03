# graphql-java-calculator

![Build and Publish](https://github.com/dugenkui03/graphql-java-calculator/workflows/Build%20and%20Publish/badge.svg)
[![Latest Release](https://maven-badges.herokuapp.com/maven-central/com.graphql-java-calculator/graphql-java-calculator/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.graphql-java-calculator/graphql-java-calculator)

📖 English Documentation | [📖 中文文档](README_ZH.md) 

----------------------------------------

Based on [directive](https://spec.graphql.org/draft/#sec-Language.Directives), graphql-java-calculator provide graphql query with the ability of orchestration, field calculation and control flow.


# Features

- data orchestration: mark field fetched value with @fetchSource,then you could acquire the fetchedValue by argument `dependencySources` in directives.
- script calculation: according script, 
filter list result, sort list field result；generate new field value by script with parent source and fetchSource;
- control flow, `@skipBy` and `@includeBy`: the extended version of `@skip`和`@include`, skip/include field fetch by script with taking query variable as script argument;
- transform arguments: transform the field arguments, filter the list arguments, and transform the elements of the list arguments. The transform expression can take fetchSource as argument.


# Getting Started

#### 1. Adding Dependency

latest version [Maven repository](https://mvnrepository.com/artifact/com.graphql-java-calculator/graphql-java-calculator)。

```
implementation group: 'com.graphql-java-calculator', name: 'graphql-java-calculator', version: '1.1'
```

#### 2. Wrap GraphQL Engine

##### 2.1 Implements `AsyncDataFetcherInterface`

If async DataFetcher is used, then make it implements `AsyncDataFetcherInterface`, 
and return the wrapped DataFetcher and the thread pool used in async DataFetcher in override method.


##### 2.2 Create `GraphQLSource`

Create `GraphQLSource` by `Config`, which including wrapped graphql schema and GraphQL object.


##### 2.3 Validation

Validate the query with calculator directive by `Validator`, which including graphql syntax validation.

It is recommend to create `PreparsedDocumentProvider` by implementing {@link CalculatorDocumentCachedProvider}


## License

This project is released under version 2.0 of the [Apache License](https://www.apache.org/licenses/LICENSE-2.0).
