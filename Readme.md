# OraBench - Oracle 数据库性能测试工具

OraBench 是一个简单的工具，旨在帮助测试 Oracle 数据库的性能，特别是针对并发插入操作的性能测试。它使用 HikariCP 作为数据库连接池，支持从外部文件加载 SQL 语句和参数，以便进行灵活的性能测试。



Oracle压测程序 这个Java程序是一个Oracle数据库性能压力测试工具，它从配置文件中读取数据库信息和测试参数，使用多线程并发执行指定的数据库存储过程，旨在模拟高并发环境下的数据库操作，以此来测试Oracle数据库的性能。以下是程序的主要功能和组件的详细解析：

1. **读取配置文件** (`db.properties`):
   - 从`db.properties`文件中读取数据库配置信息，包括数据库地址、端口、数据库名、用户名和密码，以及性能测试的配置参数如执行次数限制`MAX_EXECUTIONS`、线程数量`CONNECTIONS_PER_BATCH`和每次执行后的延时时间`DELAY_SECONDS`。
2. **多线程执行**:
   - 使用Java的`ExecutorService`来管理线程池，根据配置文件中指定的`CONNECTIONS_PER_BATCH`来设置线程池的大小，以此来模拟多个并发连接到数据库。
   - 程序循环执行，直到达到`MAX_EXECUTIONS`指定的执行次数或者检测到执行失败。
3. **执行数据库存储过程**:
   - 在每个线程中，尝试建立到Oracle数据库的连接，并执行指定的存储过程`PROCEDURE_NAME`。
   - 如果存储过程执行成功，会记录一条成功的日志信息；如果执行过程中出现`SQLException`，则记录错误信息，并通过原子变量`failureDetected`标记执行失败。
4. **错误处理和线程终止**:
   - 如果任何一个线程在执行存储过程时遇到失败（即捕获到`SQLException`），则设置原子变量`failureDetected`的值为`true`，以此作为信号通知其他线程停止执行。
   - 程序检查`failureDetected`的值，如果为`true`，则后续的线程不再尝试执行存储过程，即使还没有达到`MAX_EXECUTIONS`指定的次数。
5. **延时与资源清理**:
   - 在每一批次的执行完成后（即每`CONNECTIONS_PER_BATCH`个线程尝试执行一次存储过程后），程序会按照`DELAY_SECONDS`指定的秒数进行延时，然后再继续下一批次的执行。
   - 最终，程序尝试正常关闭线程池，并等待直到所有任务完成或达到超时限制（1小时），然后退出。

这个程序提供了一个基础框架，通过模拟多用户并发执行数据库存储过程的场景，来帮助评估和测试Oracle数据库的性能和稳定性。通过调整配置文件中的参数，可以灵活地控制测试的规模和强度。



## 功能

- 从 `db.properties` 文件中读取数据库配置信息。
- 支持从 `sql.txt` 文件中加载需要执行的 SQL 语句。
- 支持从 `params.txt` 文件中加载 SQL 语句的参数，支持动态参数传递。
- 使用 HikariCP 连接池管理数据库连接。
- 支持配置执行次数、并发线程数和执行间隔时间。
- 在测试完成后输出测试汇总信息，包括平均执行时间和每秒事务数（TPS）。

## 如何使用

### 设置

1. 确保 Java 环境已安装。
2. 克隆仓库或下载源代码。
3. 根据你的数据库环境编辑 `db.properties` 文件，设置正确的数据库连接信息。
4. 根据需要编辑 `sql.txt` 和 `params.txt` 文件，以包含要执行的 SQL 语句和参数。

### 编译

在项目根目录下，运行以下命令来编译程序：

```
javac -cp path/to/hikaricp.jar:path/to/oracle-jdbc.jar myz/orabench/OraBench.java
```


请将 `path/to/hikaricp.jar` 和 `path/to/oracle-jdbc.jar` 替换为实际的 HikariCP 和 Oracle JDBC 驱动程序的路径。

### 运行

编译成功后，在项目根目录下运行以下命令来启动测试：

```
java -cp .:path/to/hikaricp.jar:path/to/oracle-jdbc.jar myz.orabench.OraBench
```


同样，记得替换 `path/to/hikaricp.jar` 和 `path/to/oracle-jdbc.jar` 为实际路径。

## 注意事项

- 本程序仅用于测试目的，不建议在生产环境中直接使用。
- 在进行性能测试时，请确保对数据库有足够的了解，避免对生产数据造成意外的损失或影响。

## 贡献

欢迎通过 GitHub 提交问题或请求，以帮助改进这个工具。

## 许可证

本项目采用 MIT 许可证，详情请见 `LICENSE` 文件。

请确保在实际运行和部署前，根据你的实际环境和需求调整 README 中的内容，包括但不限于路径设置、依赖管理以及运行命令等。