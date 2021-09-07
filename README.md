# graphql-calculator

![Build and Publish](https://github.com/dugenkui03/graphql-java-calculator/workflows/Build%20and%20Publish/badge.svg)
[![Latest Release](https://maven-badges.herokuapp.com/maven-central/com.graphql-java-calculator/graphql-java-calculator/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.graphql-java-calculator/graphql-java-calculator)

📖 English Documentation | [📖 中文文档](README_ZH.md) 

----------------------------------------

GraphQL Calculator is a lightweight graphql query calculation engine. 
Based on [directive](https://spec.graphql.org/draft/#sec-Language.Directives), graphql-calculator provide graphql query with the ability of field transform, field skip and orchestration.

# Features

The name and semantics of directives are inspired from the [`java.util.stream.Stream`](https://docs.oracle.com/javase/8/docs/api/java/util/stream/Stream.html), so they are easy to understand and use. 

The argument of directive must be static string value. query variables can't be used, because variable will make the semantics and validity of query ambiguous.

- **processing field value**: processing the fetched values and transform them to a new value; 
- **processing list field**: the collection can be easily filtered, sorted and de-duplicated;
- **processing field argument**: transform field arguments to new values before use them to fetch the field value, and the arguments of transform operation can be query variables and the fetched value of other fields;
- **flow control**: provide the extended version of `@skip` and `@include`, and the value of expression decided whether skip the operation of field-fetched;
- **data orchestration**: a field's fetched value will always could be the arguments of other fields.

# Getting Started

#### 1. Add dependency

```
implementation group: 'com.graphql-java-calculator', name: 'graphql-java-calculator', version: '1.1'
```

#### 2. Wrap graphql engine

##### 2.1 Make async Fetcher implements `AsyncDataFetcherInterface`

If async `DataFetcher` is used, then make it implements `AsyncDataFetcherInterface`, 
return the wrapped `DataFetcher` and the `Executor` used in async `DataFetcher` by override method.

##### 2.2 Create `GraphQLSource`

Create `GraphQLSource` by `Config`, which including wrapped graphql schema and `GraphQL` object.


##### 2.3 Validation

Validate the query by `Validator`, which including graphql syntax validation.

It is recommend to create `PreparsedDocumentProvider` by implementing `CalculatorDocumentCachedProvider`.

*More details in [`Example.java`](/src/test/java/calculator/example/Example.java) and [examples.graphql](/src/test/resources/examples.graphql)*

## License

This project is released under version 2.0 of the [Apache License](https://www.apache.org/licenses/LICENSE-2.0).
