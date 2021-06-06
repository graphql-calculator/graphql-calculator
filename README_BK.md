# graphql-java-calculator


基于[指令系统](https://spec.graphql.org/draft/#sec-Language.Directives)，`graphql-java-calculator`为`graphql`查询提供了动态计算和依赖编排的能力。


# 特性介绍

`graphql-java-calculator`提供了诸多指令用于不同业务场景下的数据处理、调度。



# 快速开始

#### 1、引入依赖
```
<dependency>
  <groupId>com.graphql-java-calculator</groupId>
  <artifactId>graphql-java-calculator</artifactId>
  <version>todo</version>
</dependency>
```

#### 2、包装引擎

```
     
```

#### 3、查询示例


**注**：
1. **要求自定义的`DataFetcher`均异步化，避免串行调度顺序和数据依赖之间形成循环死锁，异步化方式参考[`AsyncDataFetcher`](https://github.com/graphql-java/graphql-java/blob/master/src/main/java/graphql/schema/AsyncDataFetcher.java);**
2. 查询校验；
3. **推荐自己实现[`ObjectMapper`](https://github.com/dugenkui03/graphql-java-calculator/blob/main/src/main/java/calculator/engine/ObjectMapper.java)，`ObjectMapperImpl`在处理`非java.util.Map`类型时会调用反射**;



# 其他信息

- `graphql-java`社区论坛：https://spectrum.chat/graphql-java
- `graphql`规范：https://spec.graphql.org/draft/
- `aviator`语法：https://www.yuque.com/boyan-avfmj/aviatorscript/cpow90
- 作者邮箱：dugk@foxmail.com



# issue


## 1. sortBy支持表达式

## 2. @sort