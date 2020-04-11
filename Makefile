JFLAGS = -d .
JC = javac
JAVA = java

G1GC_FLAGS = -XX:+UseG1GC \
	-XX:MaxGCPauseMillis=200 \
	-XX:G1HeapRegionSize=8m \
	-XX:+ParallelRefProcEnabled \
	-XX:-ResizePLAB \
	-XX:+UseThreadPriorities \
	-XX:ThreadPriorityPolicy=0 \
	-Xlog:gc*:file=g1gc.log
ZGC_FLAGS = -XX:+UnlockExperimentalVMOptions -XX:+UseZGC \
	-Xlog:gc*:file=zgc.log

.SUFFIXES: .java .class

.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
	src/com/yunkee/gctest/MemoryStat.java \
	src/com/yunkee/gctest/Test.java

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) -r com/

run-g1gc:
	$(JAVA) $(G1GC_FLAGS) com.yunkee.gctest.Test

run-zgc:
	$(JAVA) $(ZGC_FLAGS) com.yunkee.gctest.Test