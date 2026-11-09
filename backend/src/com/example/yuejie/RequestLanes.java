package com.example.yuejie;

import java.util.*;import java.util.concurrent.locks.*;

/** A slow comment response never owns the reading lane or its content-session key. */
final class RequestLanes {
 interface Lease extends AutoCloseable{void close();}
 final Lane reading=new Lane(),comments=new Lane();
 Lease enter(boolean comment,int priority)throws InterruptedException{return (comment?comments:reading).enter(comment?Math.max(0,Math.min(2,priority)):0);}
 static final class Lane{
  final ReentrantLock lock=new ReentrantLock();final Condition changed=lock.newCondition();final TreeSet<Ticket> waiting=new TreeSet<>();long sequence;boolean busy;
  final class Ticket implements Comparable<Ticket>{final int priority;final long sequence;Ticket(int p){priority=p;sequence=Lane.this.sequence++;}public int compareTo(Ticket other){int c=Integer.compare(priority,other.priority);return c==0?Long.compare(sequence,other.sequence):c;}}
  Lease enter(int priority)throws InterruptedException{lock.lockInterruptibly();Ticket ticket=new Ticket(priority);try{waiting.add(ticket);while(busy||waiting.first()!=ticket)changed.await();waiting.remove(ticket);busy=true;return ()->{lock.lock();try{busy=false;changed.signalAll();}finally{lock.unlock();}};}catch(InterruptedException e){waiting.remove(ticket);changed.signalAll();throw e;}finally{lock.unlock();}}
 }
}
