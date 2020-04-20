### JVM Flags

```Makefile
ZGC_FLAGS = -XX:+UnlockExperimentalVMOptions -XX:+UseZGC \
	-XX:SoftMaxHeapSize=4G -Xmx6G \
	-XX:ConcGCThreads=10 \
	-Xlog:gc*:file=zgc.log
```

### Test size
```java
// Test.java
int n = 1024 * 512;
```