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

# 跨数据源计算todo
1. 环校验；
2. 互换位置后的线程阻塞；
3. list元素的n+1次解析（函数+instrument）；
4. findOne定义细节、函数边界条件；