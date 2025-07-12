package com.lbry.globe.util;

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TimeoutFutureManager<K,V>{

    private final ScheduledExecutorService executorService;

    private final ConcurrentHashMap<K,CompletableFuture<V>> futures = new ConcurrentHashMap<>();

    public TimeoutFutureManager(ScheduledExecutorService executorService){
        this.executorService = executorService;
    }

    public CompletableFuture<V> createFuture(K key,long delay,TimeUnit unit){
        CompletableFuture<V> future = new CompletableFuture<>();
        this.futures.put(key,future);

        executorService.schedule(() -> {
            if(!future.isDone()){
                this.futures.remove(key,future);
                future.completeExceptionally(new TimeoutException());
            }
        },delay,unit);

        return future;
    }

    public void finishFuture(K key,V value){
        if(this.futures.containsKey(key)){
            this.futures.get(key).complete(value);
            this.futures.remove(key);
        }
    }

    public static <V> CompletableFuture<List<V>> getBulk(CompletableFuture<V>[] futures){
        return CompletableFuture.allOf(futures).exceptionally((t) -> null).thenApply((v) -> Stream.of(futures).map(future -> {
            try{
                return future.join();
            }catch(Exception e){
                return null;
            }
        }).collect(Collectors.toList()));
    }

}