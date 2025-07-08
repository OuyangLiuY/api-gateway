# G1情况下的JVM参数推荐

JVM 启动参数：

当前系统  : 6G Memory 、2C 



```sh
-XX:+UseG1GC
-XX:MaxGCPauseMillis=100
-XX:G1NewSizePercent=60
-XX:G1MaxNewSizePercent=80
# -XX:NewRatio=1
-XX:SurvivorRatio=8
-XX:ParallelGCThreads=2
-XX:ConcGCThreads=1
-XX:+AlwaysPreTouch
-XX:+UseStringDeduplication
-Xms4g -Xmx5g
```

* `-XX:+UseG1GC`
  启用 G1 垃圾回收器，适合大堆、低延迟场景。
* `-XX:MaxGCPauseMillis=100`
  期望每次 GC 停顿不超过 100 毫秒，G1 会尽量满足。
* `-XX:G1NewSizePercent=60`
  新生代最小占整个堆的 60%。
* `-XX:G1MaxNewSizePercent=80`
  新生代最大占整个堆的 80%。
* `-XX:NewRatio=1`
  老年代与新生代的比例为 1:1（对 G1 影响较小，主要用于其他 GC）。
* `-XX:SurvivorRatio=8`
  Eden 区与每个 Survivor 区的比例为 8:1:1。
* `-XX:ParallelGCThreads=2`
  年轻代 GC 时使用的并行线程数为 8。
* `-XX:ConcGCThreads=1`
  G1 并发阶段（如并发标记）使用的线程数为 1。
* `-XX:+AlwaysPreTouch`
  JVM 启动时预先分配并触碰所有堆内存，避免运行时分配带来的延迟。
* `-XX:+UseStringDeduplication`
  启用字符串去重，减少内存中重复字符串的占用（G1 GC 专属）。
* `-Xms4g`
  堆初始大小为 4GB。
* `-Xmx6g`
  堆最大大小为 5GB。
* `-XX:NewRatio`
  设置老年代与新生代的比例（不推荐与G1一起用）。
* `-XX:MaxNewSize` 和 `-XX:NewSize`
  这两个参数对G1影响有限，G1主要根据堆的使用情况动态调整新生代大小。
* `-XX:G1NewSizePercent`
  新生代最小占整个堆的百分比（默认5%）。
* `-XX:G1MaxNewSizePercent`
  新生代最大占整个堆的百分比（默认60%）。

JVM启动参数：

`java -jar -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:G1NewSizePercent=60 -XX:G1MaxNewSizePercent=80 -XX:NewRatio=1 -XX:SurvivorRatio=8 -XX:ParallelGCThreads=2 -XX:ConcGCThreads=1 -XX:+AlwaysPreTouch -XX:+UseStringDeduplication -Xms4g -Xmx5g gateway-0.1.jar`

## Jcmd

jcmd 6542 GC.heap\_info 输出内容的详细解释：

```
garbage-first heap   total 4194304K, used 127374K [0x00000006c0000000, 0x0000000800000000)
 region size 4096K, 31 young (126976K), 5 survivors (20480K)
Metaspace       used 45597K, committed 46336K, reserved 1114112K
 class space    used 6130K, committed 6464K, reserved 1048576K
```

1. garbage-first heap

   * total 4194304K：JVM 堆的总大小（约 4GB，对应你的 -Xms4g 设置）。

   * used 127374K：当前已使用的堆内存（约 124MB）。

   * [0x00000006c0000000, 0x0000000800000000)：堆的内存地址范围。


2. region size 4096K：G1 垃圾回收器将堆划分为 4MB 的小块（region）。

   * 31 young (126976K)：当前有 31 个年轻代 region，总共约 124MB。

   * 5 survivors (20480K)：有 5 个 survivor 区 region，总共约 20MB。


3. Metaspace

   * used 45597K：Metaspace（元空间）已使用约 45MB。

   * committed 46336K：已分配给 Metaspace 的内存约 46MB。

   * reserved 1114112K：为 Metaspace 预留的最大空间约 1.06GB。


4. class space

   * used 6130K：用于类元数据的空间已使用约 6MB。

   * committed 6464K：已分配给 class space 的内存约 6.3MB。

   * reserved 1048576K：为 class space 预留的最大空间约 1GB。

# 4C，6G下的加解密线程

## 线程池容量估算

- **单次加解密耗时**：20ms
- **目标QPS**：1000
- **平均响应时间目标**：≤200ms
- **CPU核心数**：4

### 并发数估算

- 理论最大并发 = QPS × 平均响应时间（秒）
- 最大并发 = 1000 × 0.2 = **200**

### 单线程每秒可处理请求数

- 1s/20ms = 50次
- 8核理论最大TPS = 4 × 50 = **200 TPS**（单线程/核）

### 线程池需要多少线程？

- 理论上，**线程数 = 并发数 / (1 - CPU利用率目标)**
- 但加解密为CPU密集型，线程数不宜远大于核心数，否则上下文切换反而拖慢性能。
- 经验公式：
  - 线程数 ≈ 核心数 × (1 + 阻塞系数)
  - 阻塞系数 = 等待时间/计算时间（加解密几乎无I/O，阻塞系数≈0）

### 实际建议

- **线程池核心数**：4 - 6（建议先用4，压测后可适当提升到5~6）
- **最大线程数**：8~10（防止极端突发）
- **队列长度**：200~400（与最大并发数匹配）

在这个场景下（4核CPU、加解密耗时20ms、追求最大QPS、减少上下文切换），Netty主线程和业务线程池建议如下：

## 1. Netty 事件循环线程数（bossGroup/workerGroup）

- **推荐配置**：`workerGroup` 线程数 = CPU核心数 = **4**
- **理由**：Netty 的 workerGroup 线程数默认是 `CPU核心数 * 2`，但在有大量耗时操作（如加解密）时，建议与核心数一致，避免线程过多导致频繁上下文切换。

## 2. 业务线程池（加解密线程池）

- **推荐配置**：线程数 = `CPU核心数 * (1 + 等待时间/计算时间)`
- **计算公式**（[参考阿里巴巴Java开发手册](https://developer.aliyun.com/article/30198)）：

  ```
  线程数 = CPU核心数 * (1 + 等待时间/计算时间)
  ```

  - 假设加解密为纯CPU操作，等待时间≈0，线程数≈4
  - 如果加解密有IO等待（如远程调用），可适当放大线程数
- **实际建议**：**4~6** 之间，先设为4，压测后根据CPU利用率和TPS调整。

## 3. 线程池类型

- 使用**固定线程池**（`Executors.newFixedThreadPool` 或 `ThreadPoolExecutor`），避免线程频繁创建销毁。

- **Netty workerGroup**：6
- **加解密线程池**：4~6（建议先4，压测后调整）
- **目标**：减少上下文切换，充分利用CPU，提升TPS

## 4. 监控与调优建议

- **监控线程池**：活跃线程数、队列长度、任务等待时间
- **监控GC**：GC次数、GC暂停时间、Full GC频率
- **监控内存**：堆使用率、Old区占用、Metaspace
- **监控业务**：加解密平均耗时、99线耗时、TPS、超时/降级次数

> **压测建议**：用JMeter/Locust等工具模拟200+ QPS，观察平均耗时、最大耗时、GC暂停、线程池队列长度等，动态调整线程池和JVM参数。

## 5. 其他优化建议

- **避免大对象频繁创建**：加解密时尽量重用byte[]、StringBuilder等，减少GC压力。
- **密钥/证书本地缓存**：避免每次加解密都加载密钥，减少I/O和对象创建。
- **批量加解密**：如有可能，合并小包为大包批量处理，提升吞吐。
- **降级策略**：线程池队列满/超时时，快速降级，避免雪崩。