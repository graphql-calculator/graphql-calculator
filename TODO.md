
- 执行引擎应该做层封装、屏蔽用户对其他组建的感知，提高其易用性、后续如果要作替换的话降低其成本。
- 更新 README.md.
- 当前最大的问题是预期所有流转的数据都是Map类型的；
- 兼容片段；
- `@require`：即使不是非空字段，在这个查询里边也必须出现，否则所在实体为空；
- 是否是用到了某个函数、其参数是什么——校验；

- TODO：参数转换指令的参数来源应该包括 source、这也符合大部分场景，必须支持；

- TODO：dependency等这次代码的单元测试；

- TODO
list-map, list-filter不能用在基本类型上

- TODO:
如果 field_2 执行失败了，则field会永远阻塞下去
所以最好的方式是也分析出 field_2，从顶层任务节点开始分析结束情况，但是在之上的任务节点失败则中断等待。
query{
     field_1 @link(node:"arg"")
     field_2{
            listField{
                # 依赖的任务节点[listField, listField.innerField]
                innerField @node(name:"arg")
            }
     }
}


# 注意

- 注意没有指定线程池的地方大概率为调度线程；

- 功能：https://spectrum.chat/graphql-java?tab=posts；

- 如果依赖的节点任务异常，则获取的数据为null——一个节点有问题不应该影响另一个节点。


# 线程池的使用

所有依赖了node节点字段的解析都是异步的。

TODO 是不是这种自动化的异步就可以了，使用者不用在把所有自定义fetcher绑定了异步fetcher。
