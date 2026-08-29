package net.skinmc.client.http;

import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared, bounded HTTP executor that cannot delay Minecraft shutdown. */
public final class SkinMCHttpClients {

    private static final int HTTP_THREADS = 4;
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();
    private static final ThreadFactory THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "SkinMC HTTP-" + THREAD_NUMBER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    };
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(HTTP_THREADS, THREAD_FACTORY);

    private SkinMCHttpClients() {}

    public static HttpClient.Builder newBuilder() {
        return HttpClient.newBuilder().executor(EXECUTOR);
    }
}
