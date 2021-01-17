# graphql-java-calculate

基于`graphql-java`的`Instrumentation`系统和指令机制，为graphql提供了更加丰富的动态计算和调度能力。

TODO 社区。


# 特性介绍

1. 更加丰富的计算能力；
2. 实现了数据之间的依赖，dag；
3. 任务的异步执行；


# 使用举例

// 把maven贴上来
// 介绍重点的指令
// 然后放一个 test 的链接。

 `@trigger`
类似 fieldCal，但是是对字段定义的补全

`@require`
即使不是非空字段，在这个查询里边也必须出现，否则就...

`@mock(value)`

`@cacheControl`

`@runnable(exp)`
表达式可获取到当前层级获取到的参数，该表达式作为一个异步任务执行，可用作数据的异步上报，或者数据更新时的对比分析。


# 参考资料

- apollo-graphql：https://www.apollographql.com/docs/apollo-server/federation/errors/
- cacheControl：https://github.com/apollographql/apollo-server/blob/main/packages/apollo-cache-control/src/__tests__/cacheControlDirective.test.ts
- todo：https://github.com/apollographql/apollo-server/blob/main/docs/source/schema/creating-directives.md#uppercasing-strings
- https://spectrum.chat/graphql-java?tab=posts。
