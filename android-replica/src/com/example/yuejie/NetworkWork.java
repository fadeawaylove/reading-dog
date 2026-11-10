package com.example.yuejie;
/** Upstream request scheduling is independent of sync/resource availability. */
final class NetworkWork {
 static final java.util.concurrent.atomic.AtomicInteger readingRequests=new java.util.concurrent.atomic.AtomicInteger();
 static final ThreadLocal<Integer> commentPriority=new ThreadLocal<>();
}
