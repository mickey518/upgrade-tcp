package lab.dragon.util;

import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PreDestroy;
import java.util.concurrent.*;

/**
 * 线程池工具类
 *
 * @author xuejingbao
 * @create 2022-01-10 14:05
 */
@Slf4j
public class ThreadPoolUtil {

    /**
     * 一般执行线程池
     */
    private static ThreadPoolExecutor threadPool;
    /**
     * 定时任务相关线程池
     */
    private static ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    /**
     * 关闭线程池
     */
    @PreDestroy
    public void close() {
        if (threadPool != null) {
            if (!threadPool.isShutdown()) {
                threadPool.shutdown();
            }
        }
        if (scheduledThreadPoolExecutor != null) {
            if (!scheduledThreadPoolExecutor.isShutdown()) {
                scheduledThreadPoolExecutor.shutdown();
            }
        }
    }

    /**
     * 无返回值直接执行
     *
     * @param runnable
     */
    public static void execute(Runnable runnable) {
        getThreadPool().execute(runnable);
    }

    /**
     * 返回值直接执行
     *
     * @param callable
     */
    public static <T> Future<T> submit(Callable<T> callable) {
        return getThreadPool().submit(callable);
    }

    public static ThreadFactory getThreadFactory() {
        if (threadPool == null) {
            getThreadPool();
        }
        return threadPool.getThreadFactory();
    }

    /**
     * dcs获取线程池
     *
     * @return 线程池对象
     */
    public static ThreadPoolExecutor getThreadPool() {
        if (threadPool != null) {
            return threadPool;
        } else {
            synchronized (ThreadPoolUtil.class) {
                if (threadPool == null) {
                    log.error("thread pool is null");
                    threadPool = new ThreadPoolExecutor(10,
                            10,
                            60,
                            TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(100),
                            new DefaultThreadFactory("DEFAULT"),
                            new ThreadPoolExecutor.CallerRunsPolicy());
                }
                return threadPool;
            }
        }
    }

    public static ScheduledExecutorService getScheduledExecutor() {
        if (scheduledThreadPoolExecutor != null) {
            return scheduledThreadPoolExecutor;
        } else {
            synchronized (ThreadPoolUtil.class) {
                if (scheduledThreadPoolExecutor == null) {
                    scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(10, getThreadFactory());
                }
                return scheduledThreadPoolExecutor;
            }
        }
    }

}
