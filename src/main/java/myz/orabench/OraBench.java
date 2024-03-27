package myz.orabench;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class OraBench {
    // 配置文件和SQL文件路径
    private static final String DB_PROPERTIES_FILE = "db.properties";
    private static final String SQL_FILE = "sql.txt";
    private static final String PARAMS_FILE = "params.txt";

    // 执行配置参数
    private static int MAX_EXECUTIONS;
    private static int CONNECTIONS_PER_BATCH;
    private static int DELAY_SECONDS;

    // 使用HikariCP作为数据源
    private static HikariDataSource dataSource; // 使用HikariCP数据源

    // 统计执行情况的变量
    private static AtomicInteger failureCount = new AtomicInteger(0);
    private static AtomicInteger successCount = new AtomicInteger(0);
    private static AtomicLong totalExecutionTime = new AtomicLong(0);
    
    // 存储SQL语句的列表
    private static List<String> sqlStatements;

    // 测试开始和结束时间
    private static long testStartTime;
    private static long testEndTime;

    public static void main(String[] args) throws IOException {
        // 加载数据库配置
        Properties properties = loadProperties(DB_PROPERTIES_FILE);
        if (properties == null) {
            logMessage("Unable to load database configuration information");
            return;
        }

        // 初始化HikariCP数据源
        initializeDataSource(properties); 

        // 加载SQL语句
        sqlStatements = loadSqlStatements(SQL_FILE);
        if (sqlStatements.isEmpty()) {
            logMessage("No SQL statements found in " + SQL_FILE);
            return;
        }

        // 读取执行配置
        MAX_EXECUTIONS = Integer.parseInt(properties.getProperty("MAX_EXECUTIONS", "100"));
        CONNECTIONS_PER_BATCH = Integer.parseInt(properties.getProperty("CONNECTIONS_PER_BATCH", "10"));
        DELAY_SECONDS = Integer.parseInt(properties.getProperty("DELAY_SECONDS", "10"));

        // 初始化线程池
        ExecutorService executorService = Executors.newFixedThreadPool(CONNECTIONS_PER_BATCH);      

        // 从文件加载参数 
        Object[] params = loadParams(PARAMS_FILE); // 从文件加载参数
 
        // 记录测试开始时间
        testStartTime = System.currentTimeMillis();

        // 执行SQL语句
        for (int i = 1; i <= MAX_EXECUTIONS; i++) {
            logMessage("Batch execution number: " + i);
            for (int j = 0; j < CONNECTIONS_PER_BATCH; j++) {
                for (String sql : sqlStatements) {                                 
                        // sql = "INSERT INTO tbl_tst2 (col_string,col_integer, col_double, col_boolean, col_date) VALUES (?, ?, ?, ?, ?)";
                        // Object[] params = {
                        //     "测试字符串", // col_string
                        //     123,          // col_integer
                        //     45.67,        // col_double
                        //     1,            // col_boolean (true)，因为Oracle不直接支持布尔，所以使用1表示true，0表示false
                        //     new java.sql.Date(new java.util.Date().getTime()) // col_date，转换java.util.Date到java.sql.Date
                        // };
                    if (params != null && params.length > 0) {
                        executorService.execute(() -> executeSqlStatement(sql,params));}
                    else{
                        executorService.execute(() -> executeSqlStatement(sql,null));
                    }
                }
            }
            try {
                // Thread.sleep(DELAY_SECONDS * 1000);
                Thread.sleep(DELAY_SECONDS * 1);
            } catch (InterruptedException e) {
                logMessage("Thread delay is interrupted: " + e.getMessage());
            }
        }
  
        // 等待所有任务完成
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.HOURS)) {
                //部分任务未在预定时间内完成
                logMessage("Some tasks did not complete within the allotted time");
            }
        } catch (InterruptedException e) {
            //等待任务完成时被中断
            logMessage("Waiting for tasks to complete was interrupted: " + e.getMessage());
        }

        // 关闭数据源
        if (dataSource != null) {
            dataSource.close();
        }

        // 记录测试结束时间
        testEndTime = System.currentTimeMillis();

        long averageExecutionTime = successCount.get() > 0 ? totalExecutionTime.get() / successCount.get() : 0;
        logMessage("Average execution time: " + averageExecutionTime + " milliseconds.");
        // logMessage("Total successes: " + successCount.get() + ", Total failures: " + failureCount.get());
        double throughput = (double) (successCount.get() + failureCount.get()) / (totalExecutionTime.get() / 1000.0);
        logMessage("Throughput0: " + throughput + " operations per second.");
        
        // 输出测试汇总信息
        printSummary();

    }

    // 初始化HikariCP数据源
    private static void initializeDataSource(Properties dbProperties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbProperties.getProperty("jdbcUrl"));
        config.setUsername(dbProperties.getProperty("username"));
        config.setPassword(dbProperties.getProperty("password"));
    
        // 应用HikariCP特定配置
        if (dbProperties.containsKey("maximumPoolSize")) {
            config.setMaximumPoolSize(Integer.parseInt(dbProperties.getProperty("maximumPoolSize")));
        }
        if (dbProperties.containsKey("minimumIdle")) {
            config.setMinimumIdle(Integer.parseInt(dbProperties.getProperty("minimumIdle")));
        }
        if (dbProperties.containsKey("idleTimeout")) {
            config.setIdleTimeout(Long.parseLong(dbProperties.getProperty("idleTimeout")));
        }
        if (dbProperties.containsKey("connectionTimeout")) {
            config.setConnectionTimeout(Long.parseLong(dbProperties.getProperty("connectionTimeout")));
        }
        if (dbProperties.containsKey("maxLifetime")) {
            config.setMaxLifetime(Long.parseLong(dbProperties.getProperty("maxLifetime")));
        }
        if (dbProperties.containsKey("cachePrepStmts")) {
            config.addDataSourceProperty("cachePrepStmts", dbProperties.getProperty("cachePrepStmts"));
        }
        if (dbProperties.containsKey("prepStmtCacheSize")) {
            config.addDataSourceProperty("prepStmtCacheSize", dbProperties.getProperty("prepStmtCacheSize"));
        }
        if (dbProperties.containsKey("prepStmtCacheSqlLimit")) {
            config.addDataSourceProperty("prepStmtCacheSqlLimit", dbProperties.getProperty("prepStmtCacheSqlLimit"));
        }
    
        dataSource = new HikariDataSource(config);
    }
    
    // 执行SQL语句，支持带参数的情况
    private static void executeSqlStatement(String sql, Object[] params) {
        //接收一个Object[]数组作为参数，并自动根据参数的实际类型来调用适当的PreparedStatement设置方法
            long startTime = System.currentTimeMillis();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement pstmt = connection.prepareStatement(sql)) {
            // 如果提供了参数，则设置参数
            if (params != null && params.length > 0) {
            // 遍历参数数组并根据对象类型设置PreparedStatement的参数
                for (int i = 0; i < params.length; i++) {
                        if (params[i] instanceof String) {
                            pstmt.setString(i + 1, (String) params[i]);
                        } else if (params[i] instanceof Integer) {
                            pstmt.setInt(i + 1, (Integer) params[i]);
                        } else if (params[i] instanceof Long) {
                            pstmt.setLong(i + 1, (Long) params[i]);
                        } else if (params[i] instanceof Double) {
                            pstmt.setDouble(i + 1, (Double) params[i]);
                        } else if (params[i] instanceof Float) {
                            pstmt.setFloat(i + 1, (Float) params[i]);
                        } else if (params[i] instanceof Boolean) {
                            pstmt.setBoolean(i + 1, (Boolean) params[i]);
                        } else if (params[i] instanceof Date) {
                            pstmt.setTimestamp(i + 1, new Timestamp(((Date) params[i]).getTime()));
                        }
                    // 可以根据需要继续添加更多的类型判断和处理
                }
            }
            // 执行SQL语句
            pstmt.executeUpdate();
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;
    
            totalExecutionTime.addAndGet(executionTime);
            successCount.incrementAndGet();
    
            logMessage("SQL executed successfully in " + executionTime + " milliseconds.");
        } catch (SQLException e) {
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;
            totalExecutionTime.addAndGet(executionTime);
    
            failureCount.incrementAndGet();
            logMessage("Database execute failed: " + e.getMessage() + " after " + executionTime + " milliseconds.");
        }
    }
    

// 加载配置文件
    private static Properties loadProperties(String filename) {
        try (InputStream input = new FileInputStream(filename)) {
            Properties properties = new Properties();
            properties.load(input);
            return properties;
        } catch (IOException e) {
            logMessage("Unable to load configuration file: " + e.getMessage());
            return null;
        }
    }
    
    // 从文件加载SQL语句
    private static List<String> loadSqlStatements(String filePath) {
        try {
            // 直接读取每一行作为一条完整的SQL语句
            return Files.lines(Paths.get(filePath))
                        .filter(line -> !line.trim().isEmpty()) // 过滤掉空行
                        .collect(Collectors.toList());
        } catch (IOException e) {
            logMessage("Error reading SQL file: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // 日志输出方法
    private static void logMessage(String message) {
        System.out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " - " + message);
    }

    // 输出测试汇总信息
    private static void printSummary() {
        // 计算总执行时间（秒）
        double totalTestTimeSeconds = (testEndTime - testStartTime) / 1000.0;
    
        // 计算TPS
        double tps = successCount.get() / totalTestTimeSeconds;
    
        logMessage("---------- Test Summary ----------");
        logMessage("Concurrency Level (Threads): " + CONNECTIONS_PER_BATCH);
        logMessage("Total Executions (Successes + Failures): " + (successCount.get() + failureCount.get()));
        logMessage("Success Count: " + successCount.get());
        logMessage("Failure Count: " + failureCount.get());
        logMessage("Total Test Time (Seconds): " + totalTestTimeSeconds);
        logMessage("TPS (Transactions Per Second): " + tps);
    }
    
     // 从文件加载参数
    private static Object[] loadParams(String filePath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(filePath))).trim();
        String[] parts = content.split(",");
        Object[] params = new Object[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.startsWith("\"") && part.endsWith("\"")) {
                params[i] = part.substring(1, part.length() - 1); // 去除引号，处理为字符串
            } else if (part.matches("\\d+")) {
                params[i] = Integer.parseInt(part); // 处理为整数
            } else if (part.matches("\\d+\\.\\d+")) {
                params[i] = Double.parseDouble(part); // 处理为双精度浮点数
            } else if (part.isEmpty()) {
                params[i] = ""; // 空字符串
            } else {
                params[i] = part; // 直接作为字符串处理
            }
        }
        return params;
    }
    
}
