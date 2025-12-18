package com.zhh.handsome.并发包中与CAS有关的类;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class unsafe对象 {
    public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Unsafe unsafe = (Unsafe) theUnsafe.get(null);
    }
}


















/*

超详细清晰的讲解一下Unsafe类的获取，相关重要方法
你希望我超详细、清晰地讲解 Java 中的 Unsafe 类 —— 包括它的获取方式（因为 Unsafe 不能直接实例化）、核心定位，以及各类重要方法的用途、参数和实际应用场景，同时结合你之前学过的 CAS、原子类等知识，让你理解它的底层作用。
一、Unsafe 类的核心定位
sun.misc.Unsafe 是 Java 中最 “危险” 也最强大的底层工具类，它绕过了 JVM 的安全检查和封装，直接提供了对内存、CPU 指令、线程的底层操作能力。
设计初衷：不是给普通开发者使用的，而是为 JDK 内部类（如 AtomicInteger、ConcurrentHashMap、LockSupport 等）提供底层支持；
核心价值：是 Java 并发包（JUC）、NIO 等核心组件的 “基石”，CAS 操作、堆外内存、线程挂起 / 唤醒等能力都依赖它；
风险：直接操作内存 / 线程，使用不当会导致内存泄漏、JVM 崩溃、数据错乱等问题，生产环境应尽量避免直接使用。
二、Unsafe 类的获取方式（关键：反射）
Unsafe 类被设计为禁止普通开发者直接获取：
构造方法是 private，无法 new Unsafe()；
静态方法 Unsafe.getUnsafe() 会检查类加载器，只有 “引导类加载器（Bootstrap ClassLoader）” 加载的类才能调用，普通应用类调用会抛 SecurityException。
唯一可行方式：反射获取 Unsafe 实例
        java
运行
import sun.misc.Unsafe;
import java.lang.reflect.Field;

public class UnsafeGetDemo {
    public static void main(String[] args) throws Exception {
        // 步骤1：通过反射获取Unsafe类的静态字段 "theUnsafe"
        Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        // 步骤2：设置字段可访问（绕过private修饰符）
        theUnsafeField.setAccessible(true);
        // 步骤3：获取字段值（Unsafe的单例实例）
        Unsafe unsafe = (Unsafe) theUnsafeField.get(null);

        // 验证：输出Unsafe实例，说明获取成功
        System.out.println("Unsafe实例：" + unsafe);
    }
}
关键解释：
theUnsafe 是 Unsafe 类的私有静态字段，存储了唯一的 Unsafe 实例；
反射打破了 private 访问限制，是普通类获取 Unsafe 的唯一方式；
注意：不同 JDK 版本（如 JDK 9+）对 Unsafe 的限制更严格，部分方法可能被禁用。
三、Unsafe 类的重要方法（分模块讲解）
Unsafe 的方法可分为 5 大类，下面结合场景和代码示例讲解核心方法：
        1. CAS 相关方法（最核心，原子类的底层）
CAS 是 Unsafe 最核心的能力，AtomicInteger、AtomicReference 的 compareAndSet 本质都是调用这些方法。
方法名	作用
compareAndSwapObject	对对象引用执行 CAS 操作（AtomicReference 的底层）
compareAndSwapInt	对 int 类型执行 CAS 操作（AtomicInteger 的底层）
compareAndSwapLong	对 long 类型执行 CAS 操作（AtomicLong 的底层）
方法参数说明（以 compareAndSwapInt 为例）：
java
        运行
// 参数说明：
// 1. obj：要操作的对象（比如AtomicInteger实例）
// 2. offset：字段在对象中的内存偏移量（通过objectFieldOffset获取）
// 3. expect：预期值
// 4. update：要更新的值
// 返回值：boolean - CAS是否成功
public final native boolean compareAndSwapInt(Object obj, long offset, int expect, int update);
示例：用 Unsafe 实现自定义原子整数
        java
运行
public class CustomAtomicInteger {
    // 1. 定义要原子操作的字段（volatile保证可见性）
    private volatile int value;
    // 2. 获取Unsafe实例
    private static final Unsafe UNSAFE;
    // 3. 获取value字段的内存偏移量
    private static final long VALUE_OFFSET;

    static {
        try {
            // 反射获取Unsafe
            Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafeField.get(null);
            // 获取value字段的内存偏移量
            VALUE_OFFSET = UNSAFE.objectFieldOffset(CustomAtomicInteger.class.getDeclaredField("value"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    public CustomAtomicInteger(int initialValue) {
        this.value = initialValue;
    }

    // 自定义CAS方法（底层调用Unsafe的compareAndSwapInt）
    public boolean compareAndSet(int expect, int update) {
        return UNSAFE.compareAndSwapInt(this, VALUE_OFFSET, expect, update);
    }

    // 获取当前值
    public int get() {
        return value;
    }

    // 测试
    public static void main(String[] args) {
        CustomAtomicInteger atomicInt = new CustomAtomicInteger(10);
        // CAS更新：预期10，更新为20
        boolean success = atomicInt.compareAndSet(10, 20);
        System.out.println("CAS是否成功：" + success); // true
        System.out.println("当前值：" + atomicInt.get()); // 20
        // 再次CAS（预期值不匹配，失败）
        success = atomicInt.compareAndSet(10, 30);
        System.out.println("CAS是否成功：" + success); // false
    }
}
2. 字段 / 对象偏移量获取方法
要操作对象的字段（比如 CAS 修改字段值），必须先获取字段在对象中的内存偏移量（offset）——JVM 中对象的字段在内存中的位置是固定的，offset 就是字段相对于对象起始地址的偏移值。
方法名	作用
objectFieldOffset	获取非静态字段在对象中的内存偏移量（最常用）
staticFieldOffset	获取静态字段在类中的内存偏移量
staticBase	获取静态字段所属类的基地址（配合 staticFieldOffset 使用）
示例：获取字段偏移量
        java
运行
public class UnsafeFieldOffsetDemo {
    static class User {
        private String name; // 非静态字段
        private static int age; // 静态字段
    }

    public static void main(String[] args) throws Exception {
        Unsafe unsafe = getUnsafe(); // 复用之前的反射获取方法

        // 1. 获取非静态字段name的偏移量
        Field nameField = User.class.getDeclaredField("name");
        long nameOffset = unsafe.objectFieldOffset(nameField);
        System.out.println("name字段偏移量：" + nameOffset); // 输出具体数字（如16）

        // 2. 获取静态字段age的偏移量和基地址
        Field ageField = User.class.getDeclaredField("age");
        long ageOffset = unsafe.staticFieldOffset(ageField);
        Object ageBase = unsafe.staticBase(ageField);
        System.out.println("age字段偏移量：" + ageOffset);
        System.out.println("age字段基地址：" + ageBase);

        // 3. 通过偏移量修改静态字段值（绕过private）
        unsafe.putInt(ageBase, ageOffset, 25);
        System.out.println("修改后age值：" + User.age); // 25
    }

    // 封装反射获取Unsafe的方法
    private static Unsafe getUnsafe() throws Exception {
        Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafeField.setAccessible(true);
        return (Unsafe) theUnsafeField.get(null);
    }
}
3. 内存操作方法（堆外内存 / 直接内存）
Unsafe 支持直接操作堆外内存（不受 JVM 垃圾回收管理），这是 NIO DirectByteBuffer 的底层实现（堆外内存避免了堆内存和堆外内存的拷贝，提升 IO 性能）。
方法名	作用
allocateMemory	分配堆外内存（返回内存地址，long 类型）
freeMemory	释放堆外内存（必须手动释放，否则内存泄漏）
putInt/getInt	向堆外内存地址写入 / 读取 int 值（还有 putLong、getLong 等重载方法）
copyMemory	内存拷贝（高效复制内存数据）
示例：操作堆外内存
        java
运行
public class UnsafeMemoryDemo {
    public static void main(String[] args) throws Exception {
        Unsafe unsafe = getUnsafe();

        // 1. 分配1024字节的堆外内存
        long memoryAddr = unsafe.allocateMemory(1024);
        try {
            // 2. 向内存地址写入数据（偏移量0的位置写入int值100）
            unsafe.putInt(memoryAddr, 100);
            // 3. 从内存地址读取数据
            int value = unsafe.getInt(memoryAddr);
            System.out.println("堆外内存读取的值：" + value); // 100

            // 4. 向偏移量4的位置写入long值200L
            unsafe.putLong(memoryAddr + 4, 200L);
            long longValue = unsafe.getLong(memoryAddr + 4);
            System.out.println("堆外内存读取的long值：" + longValue); // 200
        } finally {
            // 5. 必须手动释放堆外内存（否则内存泄漏）
            unsafe.freeMemory(memoryAddr);
        }
    }

    private static Unsafe getUnsafe() throws Exception {
        Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafeField.setAccessible(true);
        return (Unsafe) theUnsafeField.get(null);
    }
}
关键注意：堆外内存不受 GC 管理，必须调用 freeMemory 释放，否则会导致系统级内存泄漏（JVM 无法回收）。
        4. 线程操作方法（park/unpark）
LockSupport 类的 park()（线程挂起）和 unpark()（线程唤醒）底层就是调用 Unsafe 的对应方法，是实现锁、线程池的核心。
方法名	作用
park	挂起当前线程（可指定超时时间，无参数则一直挂起）
unpark	唤醒指定线程（即使线程还没调用 park，也会标记为 “已唤醒”，避免丢失）
示例：用 Unsafe 实现线程挂起 / 唤醒
        java
运行
public class UnsafeParkUnparkDemo {
    public static void main(String[] args) throws Exception {
        Unsafe unsafe = getUnsafe();

        // 线程1：挂起自己，等待被唤醒
        Thread t1 = new Thread(() -> {
            System.out.println("线程1：开始执行，准备挂起");
            unsafe.park(false, 0L); // 无超时挂起（false表示相对时间，0L表示无限期）
            System.out.println("线程1：被唤醒，继续执行");
        });

        t1.start();
        Thread.sleep(1000); // 等待线程1挂起

        // 主线程：唤醒线程1
        System.out.println("主线程：准备唤醒线程1");
        unsafe.unpark(t1);
        t1.join();
    }

    private static Unsafe getUnsafe() throws Exception {
        Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafeField.setAccessible(true);
        return (Unsafe) theUnsafeField.get(null);
    }
}
执行结果：
plaintext
线程1：开始执行，准备挂起
主线程：准备唤醒线程1
线程1：被唤醒，继续执行
核心优势：unpark 可以先于 park 调用（“先唤醒后挂起” 不会丢失唤醒信号），这是 Object.wait()/notify() 不具备的（notify 必须在 wait 之后调用，否则信号丢失）。
        5. 类 / 对象操作方法（跳过构造方法）
Unsafe 可以绕过构造方法创建对象（即使构造方法是 private），这在一些框架（如反序列化）中有用。
方法名	作用
allocateInstance	跳过构造方法，直接创建对象实例（不调用构造方法、初始化块）
示例：跳过构造方法创建对象
        java
运行
public class UnsafeAllocateInstanceDemo {
    static class User {
        private String name;

        // 私有构造方法
        private User() {
            this.name = "默认名称";
            System.out.println("User构造方法执行");
        }

        public String getName() {
            return name;
        }
    }

    public static void main(String[] args) throws Exception {
        Unsafe unsafe = getUnsafe();

        // 1. 正常反射创建对象（会调用构造方法）
        User user1 = User.class.getDeclaredConstructor().newInstance();
        System.out.println("正常创建的name：" + user1.getName()); // 输出“默认名称”

        // 2. Unsafe创建对象（跳过构造方法）
        User user2 = (User) unsafe.allocateInstance(User.class);
        System.out.println("Unsafe创建的name：" + user2.getName()); // 输出null（构造方法没执行）
    }

    private static Unsafe getUnsafe() throws Exception {
        Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafeField.setAccessible(true);
        return (Unsafe) theUnsafeField.get(null);
    }
}
执行结果：
plaintext
        User构造方法执行
正常创建的name：默认名称
Unsafe创建的name：null
说明：allocateInstance 只分配内存、创建对象，不执行构造方法、初始化块，因此字段是默认值（String 为 null）。*/
