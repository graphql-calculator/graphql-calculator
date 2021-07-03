
重点：@filter——对结果进行处理；@argumentTransform：对参数进行处理实现调度。

# TODO


3. 如果 field_2 执行失败了，则field会永远阻塞下去
所以最好的方式是也分析出 field_2，从顶层任务节点开始分析结束情况，但是在之上的任务节点失败则中断等待。
query{
     field_1 @link(fetchSource:"arg"")
     field_2{
            listField{
                # 依赖的任务节点[listField, listField.innerField]
                innerField @fetchSource(name:"arg")
            }
     }
}

4. 检查 ExecutionEngine 中的异步化；

5. @fetchSource的开发——计算指令支持 dependencySource 参数；
（参数转换指令的参数来源应该包括 source、这也符合大部分场景，必须支持）

6. 更新 README.md.

7. 兼容片段；

8. `@require`：即使不是非空字段，在这个查询里边也必须出现，否则所在类型为空；

9. 是不是这种自动化的异步就可以了，使用者不用在把所有自定义fetcher绑定了异步fetcher；

10. 检测如下环：核心思想仍然是分析 其开始执行前依赖的节点是否包括自己。
当前先作如下校验：使用了依赖fetchSource节点的指令、不可同时成为fetchSource（如果子节点成为fetchSource呢？）
query{
    a: userInfo
    @fetchSource(name: "a")
    // 不管是什么处理指令，都是依赖其fetchSource进行开始，都是转换后fetcher的前置逻辑
    @mapper(expression: "func(b)",dependencyNode: "b")
    {
        userId
    }
    
    b: userInfo
    @fetchSource(name: "b")
    @mapper(expression: "func(c)",dependencyNode: "c")
    {
        userId
    }
    
    c: userInfo
    @fetchSource(name: "c")
    @mapper(expression: "func(a)",dependencyNode: "a")
    {
        userId
    }
}

11. list路径中 父子字段都有 @fetchSource

12. support DataFetcherResult；

13. 打印环路径的时候、最好也打印出涉及的指令；

14. PreparedDocumentProvider；

15. 可以依赖多个 fetchSource

16. switch-case 指令

17. 是否所有构建 schema 和 GraphQL 的配置都交由该框架(Config)，比如 Instrumentation，像 spring-graphql一样；还是保留一定的拓展性、牺牲便捷？

# 注意

- ?执行引擎应该做层封装、屏蔽用户对其他组建的感知，提高其易用性、后续如果要作替换的话降低其成本?

- 注意没有指定线程池的地方大概率为调度线程；

- 功能：https://spectrum.chat/graphql-java?tab=posts；

- 如果依赖的节点任务异常，则获取的数据为null——一个节点有问题不应该影响另一个节点；

- 所有依赖了fetchSource节点字段的解析都是需要是异步的。？没有依赖了节点的解析不应该是异步的？

- 检查指令上fetchSource的用法是否正确，核心思想为 使用到了fetchSource节点的字段不能是该fetchSource节点所依赖字段，分为以下两种情况： 
    1. 当fetchSource节点不是在list元素是，使用到fetchSource节点的字段不能是其子节点。执行完成的顺序是由下到上的； 
    2. 当fetchSource节点是list元素的节点，则与使用到该fetchSource节点的字段不能在同一个list中，即不能是同一个top任务。

# 开发流程

1. 根据业务场景设计指令；
2. 校验；
3. ExecutionEngine开发；
4. 单元测试。