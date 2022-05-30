[![Build](https://github.com/vladak/RandomAccessFileTrap/actions/workflows/build.yml/badge.svg)](https://github.com/vladak/RandomAccessFileTrap/actions/workflows/build.yml)

# RandomAccessFile trap

This repository contains code to demonstrate the pitfall of using 
[RandomAccessFile](https://docs.oracle.com/javase/8/docs/api/java/io/RandomAccessFile.html) 
on Windows.

The `main.o` file is just a random [ELF](https://en.wikipedia.org/wiki/Executable_and_Linkable_Format) file 
(okay, no so random, it comes from [OpenGrok](https://github.com/opengrok/opengrok) test data),
in fact any other non-empty file would do. It just happens to be the file I hit this issue for the first time with.

When using `RandomAccessFile`, the pattern usually looks like this:
```java
try (RandomAccessFile raf = new RandomAccessFile(file.getAbsolutePath(), "r")) {
    FileChannel fch = raf.getChannel();
    MappedByteBuffer buffer = fch.map(FileChannel.MapMode.READ_ONLY, 0, fch.size());
    var data = buffer.getInt();
}
```
Given the _try-with-resources_ block, the `raf` object will be closed. However, there will
be associated resources, that will still stick around.
This is visible in the [Process Explorer](https://docs.microsoft.com/en-us/sysinternals/downloads/process-explorer)
(from [Windows Sysinternals](https://docs.microsoft.com/en-us/sysinternals/)) as open file handle.
On Windows, this means that the file cannot be deleted.

This is reproducible with JDK 11 and Windows Server 2019 Standard and also when running
the test using Github actions. Click on the Github actions badge above and see how the build matrix
plays out for a particular workflow run.

There is a number of related questions on Stackoverflow, 
e.g. https://stackoverflow.com/questions/25238110/how-to-properly-close-mappedbytebuffer

The point is that Java should really do better. There is [JDK-6607535](https://bugs.openjdk.java.net/browse/JDK-6607535),
however this seems to be filed to sidestep the issue by loosening the file delete semantics rather
than invoking the cleanup.

The actual problem is tracked by [JDK-4724038](https://bugs.openjdk.java.net/browse/JDK-4724038). It is currently marked
as enhancement and seems that it was created in 2002. Is is quite interesting read: it seems that proper fix would require
changes to both the "memory model" and the compiler, there are hints at security implications
of certain implementation of the cleanup operation, there is strong warning against invoking the cleanup operation directly
(which most of the answers to the above SO question do).

Lastly, I think this paragraph from the JDK issue is just priceless:
> We at Sun have given this problem a lot of thought, both during the original
development of NIO and in the time since. We have yet to come up with a way to
implement an unmap() method that's safe, efficient, and plausibly portable
across operating systems. We've explored several other alternatives aside from
the two described above, but all of them were even more problematic. We'd be
thrilled if someone could come up with a workable solution, so we'll leave this
bug open in the hope that it will attract attention from someone more clever
than we are.
